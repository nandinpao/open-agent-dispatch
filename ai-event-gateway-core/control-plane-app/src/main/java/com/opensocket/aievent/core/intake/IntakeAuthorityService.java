package com.opensocket.aievent.core.intake;

import com.opensocket.aievent.core.decision.DecisionType;
import com.opensocket.aievent.core.decision.EventIntakeDecisionResponse;
import com.opensocket.aievent.core.event.EventIntakeRequest;
import com.opensocket.aievent.core.workload.WorkloadContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * A0-R1 authoritative Intake boundary. It is deliberately placed before the legacy DecisionEngine.
 * The service never trusts caller-provided organization or origin identity as authorization authority.
 */
@Service
public class IntakeAuthorityService {
    private static final Logger log=LoggerFactory.getLogger(IntakeAuthorityService.class);
    private static final String DIGEST_ALGORITHM="SHA256";
    private static final String CANONICALIZATION_VERSION="A0R1_JSON_V2";
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public IntakeAuthorityService(NamedParameterJdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}

    /** Persist the Intake decision independently before any Task/Incident materialization begins. */
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public IntakeAdmission admit(EventIntakeRequest request){
        if(request==null)throw new IllegalArgumentException("Event Intake request is required");
        WorkloadContext workload=request.serverWorkloadContext();
        String tenant=workload==null?required(request.getTenantId(),"tenantId"):workload.tenantId();
        String source=required(request.getSourceSystem(),"sourceSystem");
        bind(tenant,workload==null?"a0-r1-intake":workload.actorPrincipalId());
        String digest=payloadDigest(request);
        PrincipalContext principal=principal(workload,tenant,source);
        Registration registration=resolveRegistration(tenant,source,request.getSourceRegistrationId());
        if(registration==null){
            return terminal(request,tenant,source,null,principal,digest,IntakeDisposition.REJECTED,"SOURCE_REGISTRATION_NOT_RESOLVED","NOT_EVALUATED",false,false);
        }
        String registrationFailure=validateRegistration(registration,principal,request);
        if(registrationFailure!=null){
            return terminal(request,tenant,source,registration,principal,digest,IntakeDisposition.REJECTED,registrationFailure,"NOT_EVALUATED",false,false);
        }

        String idempotencyKey=resolveIdempotencyKey(registration,request);
        boolean keyExpired=false;
        int generation=1;
        if(!idempotencyKey.isBlank()){
            advisoryLock(tenant,registration.sourceRegistrationId(),idempotencyKey);
            IdempotencyEntry latest=latestIdempotency(tenant,registration.sourceRegistrationId(),idempotencyKey);
            OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC);
            if(latest!=null&&!latest.expiresAt().isBefore(now)){
                jdbc.update("""
                    update ingestion_idempotency_records set last_seen_at=now(),seen_count=seen_count+1
                     where tenant_id=:tenant and source_registration_id=:registration and idempotency_key=:key and generation=:generation
                    """,new MapSqlParameterSource("tenant",tenant).addValue("registration",registration.sourceRegistrationId()).addValue("key",idempotencyKey).addValue("generation",latest.generation()));
                if(latest.payloadDigest().equals(digest)){
                    EventIntakeDecisionResponse replay=loadResponse(tenant,latest.intakeId()).orElseGet(()->intakeOnly(loadAuthority(tenant,latest.intakeId()).orElse(
                            new IntakeAuthorityView(latest.intakeId(),registration.sourceRegistrationId(),"ACCEPTED","INTAKE_ACCEPTED","IDEMPOTENT_REPLAY",true,false,principal.authenticatedRef(),principal.originRef(),now)),request.getCorrelationId()));
                    IntakeAuthorityView replayView=replay.intakeAuthority().asReplay();
                    log.info("intake_idempotent_replay tenantId={} registrationId={} ingestionId={} key={}",tenant,registration.sourceRegistrationId(),latest.intakeId(),safe(idempotencyKey));
                    return new IntakeAdmission(tenant,latest.intakeId(),registration.view(),replayView,false,replay.withIntakeAuthority(replayView));
                }
                IntakeAdmission conflict=terminal(request,tenant,source,registration,principal,digest,IntakeDisposition.QUARANTINED,"IDEMPOTENCY_KEY_REUSE_CONFLICT","KEY_REUSE_CONFLICT",false,false);
                insertSecurityEvidence(conflict,"IDEMPOTENCY_KEY_REUSE_CONFLICT","HIGH","Same idempotency key was reused with a different canonical payload digest.",Map.of("existingIntakeId",latest.intakeId(),"existingDigest",latest.payloadDigest(),"newDigest",digest,"idempotencyKey",idempotencyKey));
                return conflict;
            }
            if(latest!=null){keyExpired=true;generation=latest.generation()+1;}
        }

        String admissionLimitFailure=consumeAdmissionCapacity(tenant,registration);
        if(admissionLimitFailure!=null){
            return terminal(request,tenant,source,registration,principal,digest,IntakeDisposition.THROTTLED,admissionLimitFailure,"NOT_RECORDED_THROTTLED",false,keyExpired);
        }

        String ingestionId="intake-"+UUID.randomUUID();
        OffsetDateTime recordedAt=OffsetDateTime.now(ZoneOffset.UTC);
        String idemStatus=idempotencyKey.isBlank()?"NOT_REQUESTED":keyExpired?"KEY_EXPIRED_NEW":"NEW";
        insertIntake(tenant,ingestionId,registration,source,request,idempotencyKey,digest,principal,IntakeDisposition.ACCEPTED,"INTAKE_ACCEPTED","PENDING",keyExpired,recordedAt);
        insertHistory(tenant,ingestionId,null,"ACCEPTED","INTAKE_ACCEPTED",null);
        if(!idempotencyKey.isBlank()){
            jdbc.update("""
                insert into ingestion_idempotency_records(tenant_id,source_registration_id,idempotency_key,generation,payload_digest,digest_algorithm,canonicalization_version,intake_id,first_seen_at,last_seen_at,seen_count,expires_at)
                values(:tenant,:registration,:key,:generation,:digest,:algorithm,:canonicalization,:intake,now(),now(),1,now()+(:retention * interval '1 second'))
                """,new MapSqlParameterSource("tenant",tenant).addValue("registration",registration.sourceRegistrationId()).addValue("key",idempotencyKey).addValue("generation",generation).addValue("digest",digest).addValue("algorithm",DIGEST_ALGORITHM).addValue("canonicalization",CANONICALIZATION_VERSION).addValue("intake",ingestionId).addValue("retention",registration.idempotencyRetentionSeconds()));
        }
        IntakeAuthorityView authority=new IntakeAuthorityView(ingestionId,registration.sourceRegistrationId(),"ACCEPTED","INTAKE_ACCEPTED",idemStatus,false,keyExpired,principal.authenticatedRef(),principal.originRef(),recordedAt);
        log.info("intake_authority_accepted tenantId={} sourceSystem={} registrationId={} ingestionId={} idempotencyStatus={}",tenant,source,registration.sourceRegistrationId(),ingestionId,idemStatus);
        return new IntakeAdmission(tenant,ingestionId,registration.view(),authority,true,null);
    }

    /** Joins the caller's materialization transaction so Task persistence and the Intake->Task link commit together. */
    @Transactional
    public EventIntakeDecisionResponse complete(IntakeAdmission admission,EventIntakeDecisionResponse response){
        if(admission==null||response==null)return response;
        bind(admission.tenantId(),"a0-r1-intake-complete");
        String materialization=response.taskCreated()?"MATERIALIZED":"DECIDED_NO_TASK";
        IntakeAuthorityView view=admission.authorityView();
        EventIntakeDecisionResponse decorated=response.withIntakeAuthority(view);
        String responseJson=write(decorated);
        jdbc.update("""
            update intake_records set materialized_task_id=:task,materialization_status=:status,decision_response_json=cast(:response as jsonb),updated_at=now()
             where tenant_id=:tenant and ingestion_id=:intake and disposition='ACCEPTED'
            """,new MapSqlParameterSource("tenant",admission.tenantId()).addValue("intake",admission.ingestionId()).addValue("task",blankToNull(response.taskId())).addValue("status",materialization).addValue("response",responseJson));
        if(response.taskCreated()){
            jdbc.update("update intake_disposition_history set materialized_task_id=:task where tenant_id=:tenant and ingestion_id=:intake and to_disposition='ACCEPTED' and materialized_task_id is null",
                    new MapSqlParameterSource("tenant",admission.tenantId()).addValue("intake",admission.ingestionId()).addValue("task",response.taskId()));
        }
        return decorated;
    }

    /** Called only after the materialization transaction has rolled back. Intake remains durable and becomes DEFERRED. */
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void deferAfterMaterializationFailure(IntakeAdmission admission,RuntimeException failure){
        if(admission==null||!admission.proceedToLegacyMaterialization())return;
        bind(admission.tenantId(),"a0-r1-intake-defer");
        int updated=jdbc.update("""
            update intake_records set disposition='DEFERRED',disposition_reason='TASK_MATERIALIZATION_FAILED',disposition_at=now(),
                   materialization_status='FAILED_RETRYABLE',deferred_until=now()+interval '30 seconds',updated_at=now()
             where tenant_id=:tenant and ingestion_id=:intake and disposition='ACCEPTED'
            """,new MapSqlParameterSource("tenant",admission.tenantId()).addValue("intake",admission.ingestionId()));
        if(updated>0)insertHistory(admission.tenantId(),admission.ingestionId(),"ACCEPTED","DEFERRED","TASK_MATERIALIZATION_FAILED",null);
        log.error("intake_materialization_deferred tenantId={} ingestionId={} errorType={} message={}",admission.tenantId(),admission.ingestionId(),failure==null?"unknown":failure.getClass().getSimpleName(),failure==null?"":safe(failure.getMessage()));
    }

    private IntakeAdmission terminal(EventIntakeRequest request,String tenant,String source,Registration registration,PrincipalContext principal,String digest,IntakeDisposition disposition,String reason,String idemStatus,boolean replay,boolean keyExpired){
        String ingestionId="intake-"+UUID.randomUUID();OffsetDateTime at=OffsetDateTime.now(ZoneOffset.UTC);
        insertIntake(tenant,ingestionId,registration,source,request,clean(request.getIdempotencyKey()),digest,principal,disposition,reason,"NOT_APPLICABLE",keyExpired,at);insertHistory(tenant,ingestionId,null,disposition.name(),reason,null);
        IntakeAuthorityView authority=new IntakeAuthorityView(ingestionId,registration==null?"":registration.sourceRegistrationId(),disposition.name(),reason,idemStatus,replay,keyExpired,principal.authenticatedRef(),principal.originRef(),at);
        return new IntakeAdmission(tenant,ingestionId,registration==null?null:registration.view(),authority,false,intakeOnly(authority,request.getCorrelationId()));
    }

    private void insertIntake(String tenant,String ingestionId,Registration registration,String source,EventIntakeRequest request,String idempotencyKey,String digest,PrincipalContext principal,IntakeDisposition disposition,String reason,String materializationStatus,boolean keyExpired,OffsetDateTime at){
        String metadata=write(Map.of("eventStage",defaultValue(request.getEventStage(),"EXTERNAL"),"eventType",defaultValue(request.getEventType(),"UNKNOWN"),"objectType",defaultValue(request.getObjectType(),"UNKNOWN"),"severity",defaultValue(request.getSeverity(),"UNKNOWN"),"correlationId",defaultValue(request.getCorrelationId(),"")));
        jdbc.update("""
            insert into intake_records(tenant_id,ingestion_id,source_registration_id,source_system_id,channel_type,source_event_id,idempotency_key,payload_digest,digest_algorithm,canonicalization_version,envelope_metadata_json,
              authenticated_principal_type,authenticated_principal_ref,origin_principal_type,origin_principal_ref,asserted_origin_principal,owner_department_id,owner_group_id,
              disposition,disposition_reason,disposition_at,materialization_status,schema_version,key_expired,created_at,updated_at)
            values(:tenant,:intake,:registration,:source,'EVENT',:sourceEvent,:key,:digest,:algorithm,:canonicalization,cast(:metadata as jsonb),
              :authType,:authRef,:originType,:originRef,:asserted,:department,:group,:disposition,:reason,:at,:materialization,'1',:keyExpired,:at,:at)
            """,new MapSqlParameterSource("tenant",tenant).addValue("intake",ingestionId).addValue("registration",registration==null?null:registration.sourceRegistrationId()).addValue("source",source).addValue("sourceEvent",blankToNull(request.getSourceEventId())).addValue("key",blankToNull(idempotencyKey)).addValue("digest",digest).addValue("algorithm",DIGEST_ALGORITHM).addValue("canonicalization",CANONICALIZATION_VERSION).addValue("metadata",metadata).addValue("authType",principal.authenticatedType()).addValue("authRef",principal.authenticatedRef()).addValue("originType",principal.originType()).addValue("originRef",principal.originRef()).addValue("asserted",blankToNull(request.getAssertedOriginPrincipal())).addValue("department",registration==null?blankToNull(principal.departmentId()):blankToNull(registration.ownerDepartmentId())).addValue("group",registration==null?blankToNull(principal.groupId()):blankToNull(registration.ownerGroupId())).addValue("disposition",disposition.name()).addValue("reason",reason).addValue("at",at).addValue("materialization",materializationStatus).addValue("keyExpired",keyExpired));
    }

    private void insertHistory(String tenant,String ingestion,String from,String to,String reason,String task){jdbc.update("insert into intake_disposition_history(tenant_id,history_id,ingestion_id,from_disposition,to_disposition,reason,materialized_task_id,occurred_at) values(:tenant,:id,:intake,:fromDisposition,:toDisposition,:reason,:task,now())",new MapSqlParameterSource("tenant",tenant).addValue("id","intake-history-"+UUID.randomUUID()).addValue("intake",ingestion).addValue("fromDisposition",from).addValue("toDisposition",to).addValue("reason",reason).addValue("task",task));}
    private void insertSecurityEvidence(IntakeAdmission admission,String eventType,String severity,String reason,Map<String,Object> evidence){jdbc.update("insert into intake_security_evidence(tenant_id,security_event_id,ingestion_id,source_registration_id,event_type,severity,reason,evidence_json,created_at) values(:tenant,:id,:intake,:registration,:type,:severity,:reason,cast(:evidence as jsonb),now())",new MapSqlParameterSource("tenant",admission.tenantId()).addValue("id","intake-security-"+UUID.randomUUID()).addValue("intake",admission.ingestionId()).addValue("registration",admission.registration()==null?null:admission.registration().sourceRegistrationId()).addValue("type",eventType).addValue("severity",severity).addValue("reason",reason).addValue("evidence",write(evidence)));}

    private Registration resolveRegistration(String tenant,String source,String requested){
        try{
            if(!clean(requested).isBlank())return jdbc.queryForObject("select * from workload_source_registrations where tenant_id=:tenant and source_system_id=:source and source_registration_id=:registration",new MapSqlParameterSource("tenant",tenant).addValue("source",source).addValue("registration",clean(requested)),REGISTRATION_MAPPER);
            List<Registration> defaults=jdbc.query("select * from workload_source_registrations where tenant_id=:tenant and source_system_id=:source and channel_type='EVENT' and is_default=true and status='ACTIVE' and effective_from<=now() and (effective_to is null or effective_to>now()) order by source_registration_id",new MapSqlParameterSource("tenant",tenant).addValue("source",source),REGISTRATION_MAPPER);
            return defaults.size()==1?defaults.get(0):null;
        }catch(EmptyResultDataAccessException ex){return null;}
    }
    private String validateRegistration(Registration r,PrincipalContext p,EventIntakeRequest request){
        OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC);if(!"EVENT".equals(r.channelType()))return "SOURCE_REGISTRATION_CHANNEL_MISMATCH";if(!"ACTIVE".equals(r.status()))return "SOURCE_REGISTRATION_INACTIVE";if(r.effectiveFrom()!=null&&r.effectiveFrom().isAfter(now))return "SOURCE_REGISTRATION_NOT_YET_EFFECTIVE";if(r.effectiveTo()!=null&&!r.effectiveTo().isAfter(now))return "SOURCE_REGISTRATION_EXPIRED";
        if(!r.allowedPrincipalTypes().isEmpty()&&!r.allowedPrincipalTypes().contains(p.authenticatedType().toUpperCase(Locale.ROOT)))return "SOURCE_REGISTRATION_PRINCIPAL_TYPE_DENIED";
        if("STATIC".equals(r.principalBindingMode())&&!r.staticPrincipalRef().equals(p.authenticatedRef()))return "SOURCE_REGISTRATION_STATIC_PRINCIPAL_MISMATCH";
        if(!r.allowedEventTypes().isEmpty()&&!r.allowedEventTypes().contains(upper(defaultValue(request.getEventType(),"UNKNOWN"))))return "SOURCE_REGISTRATION_EVENT_TYPE_DENIED";
        if(!r.allowedObjectTypes().isEmpty()&&!r.allowedObjectTypes().contains(upper(defaultValue(request.getObjectType(),"UNKNOWN"))))return "SOURCE_REGISTRATION_OBJECT_TYPE_DENIED";
        if("REQUIRED_KEY".equals(r.idempotencyStrategy())&&clean(request.getIdempotencyKey()).isBlank())return "IDEMPOTENCY_KEY_REQUIRED";
        if("SOURCE_EVENT_ID".equals(r.idempotencyStrategy())&&clean(request.getSourceEventId()).isBlank())return "SOURCE_EVENT_ID_REQUIRED";
        return null;
    }
    private String consumeAdmissionCapacity(String tenant,Registration registration){
        if(registration.rateLimitPerMinute()!=null){
            Long count=jdbc.queryForObject("""
                insert into intake_registration_rate_buckets(tenant_id,source_registration_id,bucket_type,bucket_start,request_count,updated_at)
                values(:tenant,:registration,'MINUTE',date_trunc('minute',now()),1,now())
                on conflict(tenant_id,source_registration_id,bucket_type,bucket_start)
                do update set request_count=intake_registration_rate_buckets.request_count+1,updated_at=now()
                returning request_count
                """,new MapSqlParameterSource("tenant",tenant).addValue("registration",registration.sourceRegistrationId()),Long.class);
            if(count!=null&&count>registration.rateLimitPerMinute())return "SOURCE_REGISTRATION_RATE_LIMIT_EXCEEDED";
        }
        if(registration.quotaPerDay()!=null){
            Long count=jdbc.queryForObject("""
                insert into intake_registration_rate_buckets(tenant_id,source_registration_id,bucket_type,bucket_start,request_count,updated_at)
                values(:tenant,:registration,'DAY',date_trunc('day',now()),1,now())
                on conflict(tenant_id,source_registration_id,bucket_type,bucket_start)
                do update set request_count=intake_registration_rate_buckets.request_count+1,updated_at=now()
                returning request_count
                """,new MapSqlParameterSource("tenant",tenant).addValue("registration",registration.sourceRegistrationId()),Long.class);
            if(count!=null&&count>registration.quotaPerDay())return "SOURCE_REGISTRATION_DAILY_QUOTA_EXCEEDED";
        }
        return null;
    }
    private String resolveIdempotencyKey(Registration r,EventIntakeRequest request){return switch(r.idempotencyStrategy()){case "NONE"->"";case "SOURCE_EVENT_ID"->clean(request.getSourceEventId());default->clean(request.getIdempotencyKey());};}
    private void advisoryLock(String tenant,String registration,String key){jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(:lockKey,0))",new MapSqlParameterSource("lockKey",tenant+"|"+registration+"|"+key));}
    private IdempotencyEntry latestIdempotency(String tenant,String registration,String key){try{return jdbc.queryForObject("select generation,payload_digest,intake_id,expires_at from ingestion_idempotency_records where tenant_id=:tenant and source_registration_id=:registration and idempotency_key=:key order by generation desc limit 1",new MapSqlParameterSource("tenant",tenant).addValue("registration",registration).addValue("key",key),(rs,n)->new IdempotencyEntry(rs.getInt("generation"),rs.getString("payload_digest"),rs.getString("intake_id"),rs.getObject("expires_at",OffsetDateTime.class)));}catch(EmptyResultDataAccessException ex){return null;}}
    private Optional<EventIntakeDecisionResponse> loadResponse(String tenant,String intake){try{String raw=jdbc.queryForObject("select decision_response_json::text from intake_records where tenant_id=:tenant and ingestion_id=:intake",new MapSqlParameterSource("tenant",tenant).addValue("intake",intake),String.class);return raw==null||raw.isBlank()?Optional.empty():Optional.of(json.readValue(raw,EventIntakeDecisionResponse.class));}catch(EmptyResultDataAccessException ex){return Optional.empty();}catch(Exception ex){throw new IllegalStateException("Stored Intake decision response cannot be read",ex);}}
    private Optional<IntakeAuthorityView> loadAuthority(String tenant,String intake){try{return Optional.ofNullable(jdbc.queryForObject("select ingestion_id,coalesce(source_registration_id,''),disposition,disposition_reason,authenticated_principal_ref,origin_principal_ref,key_expired,created_at from intake_records where tenant_id=:tenant and ingestion_id=:intake",new MapSqlParameterSource("tenant",tenant).addValue("intake",intake),(rs,n)->new IntakeAuthorityView(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),"IDEMPOTENT_REPLAY",true,rs.getBoolean(7),rs.getString(5),rs.getString(6),rs.getObject(8,OffsetDateTime.class))));}catch(EmptyResultDataAccessException ex){return Optional.empty();}}

    private String payloadDigest(EventIntakeRequest r){Map<String,Object> payload=new TreeMap<>();payload.put("tenantId",clean(r.getTenantId()));payload.put("sourceSystem",clean(r.getSourceSystem()));payload.put("sourceEventId",clean(r.getSourceEventId()));payload.put("eventStage",defaultValue(r.getEventStage(),"EXTERNAL"));payload.put("originSourceSystem",clean(r.getOriginSourceSystem()));payload.put("targetSystem",clean(r.getTargetSystem()));payload.put("requestedSkill",clean(r.getRequestedSkill()));payload.put("handoffMode",clean(r.getHandoffMode()));payload.put("parentTaskId",clean(r.getParentTaskId()));payload.put("siteId",clean(r.getSiteId()));payload.put("plantId",clean(r.getPlantId()));payload.put("objectType",clean(r.getObjectType()));payload.put("objectId",clean(r.getObjectId()));payload.put("eventType",clean(r.getEventType()));payload.put("errorCode",clean(r.getErrorCode()));payload.put("severity",clean(r.getSeverity()));payload.put("message",clean(r.getMessage()));payload.put("occurredAt",r.getOccurredAt()==null?"":r.getOccurredAt().toString());payload.put("assertedOriginPrincipal",clean(r.getAssertedOriginPrincipal()));payload.put("attributes",normalize(r.getAttributes()==null?Map.of():r.getAttributes()));return sha256(write(payload));}
    @SuppressWarnings("unchecked") private Object normalize(Object value){if(value instanceof Map<?,?> map){Map<String,Object> out=new TreeMap<>();map.forEach((k,v)->out.put(String.valueOf(k),normalize(v)));return out;}if(value instanceof Iterable<?> items){List<Object> out=new ArrayList<>();for(Object item:items)out.add(normalize(item));return out;}return value;}
    private PrincipalContext principal(WorkloadContext w,String tenant,String source){if(w==null)return new PrincipalContext("INTEGRATION","legacy:"+source,"INTEGRATION","legacy:"+source,"UNASSIGNED","");String authType=upper(defaultValue(w.actorPrincipalType(),"INTEGRATION"));String authRef=defaultValue(w.actorPrincipalId(),"legacy:"+source);String originType=upper(defaultValue(w.originPrincipalType(),authType));String originRef=defaultValue(w.originPrincipalId(),authRef);return new PrincipalContext(authType,authRef,originType,originRef,w.departmentId(),w.groupId());}
    private void bind(String tenant,String actor){jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)",String.class,tenant);jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)",String.class,defaultValue(actor,"a0-r1-intake"));}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception ex){throw new IllegalStateException("A0-R1 JSON serialization failed",ex);}}
    private static String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException(ex);}}
    private static EventIntakeDecisionResponse intakeOnly(IntakeAuthorityView authority,String correlation){
        String reason=authority.dispositionReason();
        return new EventIntakeDecisionResponse(
                "","","",DecisionType.IGNORED,false,0,"",List.of(),
                false,"","",true,reason,
                false,"","","","","","","",
                false,"","","","","","",
                true,false,false,reason,authority.recordedAt(),
                "INTAKE","","","","",clean(correlation),"",
                "INTAKE_"+authority.disposition(),reason,
                authority.disposition().equals("DEFERRED")?"RETRY_LATER":"REVIEW_INTAKE",authority);
    }

    private static List<String> array(ResultSet rs,String column)throws SQLException{Array a=rs.getArray(column);if(a==null)return List.of();Object raw=a.getArray();if(!(raw instanceof Object[] values))return List.of();List<String> out=new ArrayList<>();for(Object v:values)if(v!=null&&!String.valueOf(v).isBlank())out.add(String.valueOf(v).trim().toUpperCase(Locale.ROOT));return List.copyOf(out);}
    private static final RowMapper<Registration> REGISTRATION_MAPPER=(rs,n)->new Registration(rs.getString("tenant_id"),rs.getString("source_registration_id"),rs.getString("source_system_id"),rs.getString("registration_name"),rs.getString("channel_type"),rs.getString("principal_binding_mode"),array(rs,"allowed_principal_types"),clean(rs.getString("static_principal_ref")),clean(rs.getString("owner_department_id")),clean(rs.getString("owner_group_id")),array(rs,"allowed_event_types"),array(rs,"allowed_object_types"),array(rs,"input_schemas"),rs.getString("idempotency_strategy"),rs.getLong("idempotency_retention_seconds"),rs.getString("ordering_strategy"),rs.getString("acknowledgement_mode"),(Integer)rs.getObject("rate_limit_per_minute"),(Long)rs.getObject("quota_per_day"),clean(rs.getString("data_classification_profile")),clean(rs.getString("residency_profile")),rs.getString("status"),rs.getObject("effective_from",OffsetDateTime.class),rs.getObject("effective_to",OffsetDateTime.class),rs.getBoolean("is_default"),rs.getLong("version"),rs.getObject("created_at",OffsetDateTime.class),rs.getObject("updated_at",OffsetDateTime.class));
    private record Registration(String tenantId,String sourceRegistrationId,String sourceSystemId,String registrationName,String channelType,String principalBindingMode,List<String> allowedPrincipalTypes,String staticPrincipalRef,String ownerDepartmentId,String ownerGroupId,List<String> allowedEventTypes,List<String> allowedObjectTypes,List<String> inputSchemas,String idempotencyStrategy,long idempotencyRetentionSeconds,String orderingStrategy,String acknowledgementMode,Integer rateLimitPerMinute,Long quotaPerDay,String dataClassificationProfile,String residencyProfile,String status,OffsetDateTime effectiveFrom,OffsetDateTime effectiveTo,boolean defaultRegistration,long version,OffsetDateTime createdAt,OffsetDateTime updatedAt){WorkloadSourceRegistrationView view(){return new WorkloadSourceRegistrationView(tenantId,sourceRegistrationId,sourceSystemId,registrationName,channelType,principalBindingMode,allowedPrincipalTypes,staticPrincipalRef,ownerDepartmentId,ownerGroupId,allowedEventTypes,allowedObjectTypes,inputSchemas,idempotencyStrategy,idempotencyRetentionSeconds,orderingStrategy,acknowledgementMode,rateLimitPerMinute,quotaPerDay,dataClassificationProfile,residencyProfile,status,effectiveFrom,effectiveTo,defaultRegistration,version,createdAt,updatedAt);}}
    private record PrincipalContext(String authenticatedType,String authenticatedRef,String originType,String originRef,String departmentId,String groupId){}
    private record IdempotencyEntry(int generation,String payloadDigest,String intakeId,OffsetDateTime expiresAt){}
    private static String safe(String v){return v==null?"":v.replaceAll("[\\r\\n]"," ");}private static String required(String v,String n){String s=clean(v);if(s.isBlank())throw new IllegalArgumentException(n+" is required");return s;}private static String clean(String v){return v==null?"":v.trim();}private static String defaultValue(String v,String d){return clean(v).isBlank()?d:clean(v);}private static String upper(String v){return v.toUpperCase(Locale.ROOT);}private static String blankToNull(String v){String s=clean(v);return s.isBlank()?null:s;}
}
