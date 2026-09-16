package com.opensocket.aievent.core.intake;

import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A0-R1 governed 1:N ingress registrations beneath the existing SourceSystem master. */
@Service
public class WorkloadSourceRegistrationService {
    private final NamedParameterJdbcTemplate jdbc;

    public WorkloadSourceRegistrationService(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public List<WorkloadSourceRegistrationView> list(String tenantId, String sourceSystemId) {
        String tenant=required(tenantId,"tenantId"), source=required(sourceSystemId,"sourceSystemId");
        bind(tenant);
        return jdbc.query("""
                select * from workload_source_registrations
                 where tenant_id=:tenant and source_system_id=:source
                 order by is_default desc, channel_type, registration_name, source_registration_id
                """, new MapSqlParameterSource("tenant",tenant).addValue("source",source), MAPPER);
    }

    @Transactional(readOnly = true)
    public Optional<WorkloadSourceRegistrationView> find(String tenantId,String sourceSystemId,String registrationId){
        String tenant=required(tenantId,"tenantId"),source=required(sourceSystemId,"sourceSystemId"),registration=required(registrationId,"sourceRegistrationId"); bind(tenant);
        try{return Optional.ofNullable(jdbc.queryForObject("""
                select * from workload_source_registrations
                 where tenant_id=:tenant and source_system_id=:source and source_registration_id=:registration
                """,new MapSqlParameterSource("tenant",tenant).addValue("source",source).addValue("registration",registration),MAPPER));}
        catch(EmptyResultDataAccessException ex){return Optional.empty();}
    }

    @Transactional
    public WorkloadSourceRegistrationView create(String tenantId,String sourceSystemId,WorkloadSourceRegistrationCommand request){
        String tenant=required(tenantId,"tenantId"),source=required(sourceSystemId,"sourceSystemId"); bind(tenant); requireSource(tenant,source);
        SourceOwnership sourceOwnership=sourceOwnership(tenant,source);
        WorkloadSourceRegistrationCommand raw=request==null?WorkloadSourceRegistrationCommand.empty():request;
        WorkloadSourceRegistrationCommand r=new WorkloadSourceRegistrationCommand(raw.sourceRegistrationId(),raw.registrationName(),raw.channelType(),raw.principalBindingMode(),raw.allowedPrincipalTypes(),raw.staticPrincipalRef(),
                sourceOwnership.departmentId(),sourceOwnership.groupId(),
                raw.allowedEventTypes(),raw.allowedObjectTypes(),raw.inputSchemas(),raw.idempotencyStrategy(),raw.idempotencyRetentionSeconds(),raw.orderingStrategy(),raw.acknowledgementMode(),
                raw.rateLimitPerMinute(),raw.quotaPerDay(),raw.dataClassificationProfile(),raw.residencyProfile(),raw.status(),raw.effectiveFrom(),raw.effectiveTo(),raw.defaultRegistration());
        String id=clean(r.sourceRegistrationId()); if(id.isBlank()) id="registration-"+UUID.randomUUID();
        String name=required(r.registrationName(),"registrationName"); String channel=upper(defaultValue(r.channelType(),"EVENT"));
        String mode=upper(defaultValue(r.principalBindingMode(),"DYNAMIC")); String idem=upper(defaultValue(r.idempotencyStrategy(),"OPTIONAL_KEY"));
        String ordering=upper(defaultValue(r.orderingStrategy(),"NONE")); String ack=upper(defaultValue(r.acknowledgementMode(),"SYNC_RESPONSE"));
        String status=upper(defaultValue(r.status(),"ACTIVE")); long retention=r.idempotencyRetentionSeconds()==null?86400L:r.idempotencyRetentionSeconds();
        OffsetDateTime effectiveFrom=r.effectiveFrom()==null?OffsetDateTime.now(ZoneOffset.UTC):r.effectiveFrom(); boolean isDefault=Boolean.TRUE.equals(r.defaultRegistration());
        validate(channel,mode,r.staticPrincipalRef(),idem,retention,ordering,ack,status,r.effectiveTo(),effectiveFrom);
        validateDefault(isDefault,status);
        if(isDefault) demoteExistingDefault(tenant,source,channel,id);
        jdbc.update("""
                insert into workload_source_registrations(
                  tenant_id,source_registration_id,source_system_id,registration_name,channel_type,principal_binding_mode,
                  allowed_principal_types,static_principal_ref,owner_department_id,owner_group_id,allowed_event_types,allowed_object_types,input_schemas,
                  idempotency_strategy,idempotency_retention_seconds,ordering_strategy,acknowledgement_mode,rate_limit_per_minute,quota_per_day,
                  data_classification_profile,residency_profile,status,effective_from,effective_to,is_default,version,created_at,updated_at)
                values(:tenant,:id,:source,:name,:channel,:mode,
                  case when :principalTypes='' then array[]::varchar[] else string_to_array(:principalTypes,',')::varchar[] end,
                  :staticPrincipal,:department,:group,
                  case when :eventTypes='' then array[]::varchar[] else string_to_array(:eventTypes,',')::varchar[] end,
                  case when :objectTypes='' then array[]::varchar[] else string_to_array(:objectTypes,',')::varchar[] end,
                  case when :schemas='' then array[]::varchar[] else string_to_array(:schemas,',')::varchar[] end,
                  :idem,:retention,:ordering,:ack,:rate,:quota,:classification,:residency,:status,:effectiveFrom,:effectiveTo,:defaultRegistration,1,now(),now())
                """,params(tenant,source,id,name,channel,mode,r,idem,retention,ordering,ack,status,effectiveFrom,isDefault));
        return find(tenant,source,id).orElseThrow();
    }

    @Transactional
    public WorkloadSourceRegistrationView update(String tenantId,String sourceSystemId,String registrationId,WorkloadSourceRegistrationCommand request){
        String tenant=required(tenantId,"tenantId"),source=required(sourceSystemId,"sourceSystemId"),id=required(registrationId,"sourceRegistrationId");bind(tenant);
        WorkloadSourceRegistrationView current=find(tenant,source,id).orElseThrow(()->new IllegalArgumentException("Workload Source Registration not found: "+id));
        SourceOwnership sourceOwnership=sourceOwnership(tenant,source);
        WorkloadSourceRegistrationCommand r=request==null?WorkloadSourceRegistrationCommand.empty():request;
        String name=defaultValue(r.registrationName(),current.registrationName());String channel=upper(defaultValue(r.channelType(),current.channelType()));
        String mode=upper(defaultValue(r.principalBindingMode(),current.principalBindingMode()));String staticPrincipal=r.staticPrincipalRef()==null?current.staticPrincipalRef():clean(r.staticPrincipalRef());
        String idem=upper(defaultValue(r.idempotencyStrategy(),current.idempotencyStrategy()));long retention=r.idempotencyRetentionSeconds()==null?current.idempotencyRetentionSeconds():r.idempotencyRetentionSeconds();
        String ordering=upper(defaultValue(r.orderingStrategy(),current.orderingStrategy()));String ack=upper(defaultValue(r.acknowledgementMode(),current.acknowledgementMode()));String status=upper(defaultValue(r.status(),current.status()));
        OffsetDateTime effectiveFrom=r.effectiveFrom()==null?current.effectiveFrom():r.effectiveFrom();OffsetDateTime effectiveTo=r.effectiveTo()==null?current.effectiveTo():r.effectiveTo();boolean isDefault=r.defaultRegistration()==null?current.defaultRegistration():r.defaultRegistration();
        validate(channel,mode,staticPrincipal,idem,retention,ordering,ack,status,effectiveTo,effectiveFrom);validateDefault(isDefault,status);if(isDefault)demoteExistingDefault(tenant,source,channel,id);
        WorkloadSourceRegistrationCommand merged=new WorkloadSourceRegistrationCommand(id,name,channel,mode,r.allowedPrincipalTypes()==null?current.allowedPrincipalTypes():r.allowedPrincipalTypes(),staticPrincipal,
                sourceOwnership.departmentId(),sourceOwnership.groupId(),
                r.allowedEventTypes()==null?current.allowedEventTypes():r.allowedEventTypes(),r.allowedObjectTypes()==null?current.allowedObjectTypes():r.allowedObjectTypes(),r.inputSchemas()==null?current.inputSchemas():r.inputSchemas(),
                idem,retention,ordering,ack,r.rateLimitPerMinute()==null?current.rateLimitPerMinute():r.rateLimitPerMinute(),r.quotaPerDay()==null?current.quotaPerDay():r.quotaPerDay(),
                r.dataClassificationProfile()==null?current.dataClassificationProfile():r.dataClassificationProfile(),r.residencyProfile()==null?current.residencyProfile():r.residencyProfile(),status,effectiveFrom,effectiveTo,isDefault);
        jdbc.update("""
                update workload_source_registrations set registration_name=:name,channel_type=:channel,principal_binding_mode=:mode,
                  allowed_principal_types=case when :principalTypes='' then array[]::varchar[] else string_to_array(:principalTypes,',')::varchar[] end,
                  static_principal_ref=:staticPrincipal,owner_department_id=:department,owner_group_id=:group,
                  allowed_event_types=case when :eventTypes='' then array[]::varchar[] else string_to_array(:eventTypes,',')::varchar[] end,
                  allowed_object_types=case when :objectTypes='' then array[]::varchar[] else string_to_array(:objectTypes,',')::varchar[] end,
                  input_schemas=case when :schemas='' then array[]::varchar[] else string_to_array(:schemas,',')::varchar[] end,
                  idempotency_strategy=:idem,idempotency_retention_seconds=:retention,ordering_strategy=:ordering,acknowledgement_mode=:ack,
                  rate_limit_per_minute=:rate,quota_per_day=:quota,data_classification_profile=:classification,residency_profile=:residency,
                  status=:status,effective_from=:effectiveFrom,effective_to=:effectiveTo,is_default=:defaultRegistration,version=version+1,updated_at=now()
                 where tenant_id=:tenant and source_system_id=:source and source_registration_id=:id
                """,params(tenant,source,id,name,channel,mode,merged,idem,retention,ordering,ack,status,effectiveFrom,isDefault));
        return find(tenant,source,id).orElseThrow();
    }

    @Transactional
    public void retire(String tenantId,String sourceSystemId,String registrationId){String tenant=required(tenantId,"tenantId"),source=required(sourceSystemId,"sourceSystemId"),id=required(registrationId,"sourceRegistrationId");bind(tenant);int n=jdbc.update("update workload_source_registrations set status='RETIRED',is_default=false,version=version+1,updated_at=now() where tenant_id=:tenant and source_system_id=:source and source_registration_id=:id",new MapSqlParameterSource("tenant",tenant).addValue("source",source).addValue("id",id));if(n==0)throw new IllegalArgumentException("Workload Source Registration not found: "+id);}

    private MapSqlParameterSource params(String tenant,String source,String id,String name,String channel,String mode,WorkloadSourceRegistrationCommand r,String idem,long retention,String ordering,String ack,String status,OffsetDateTime effectiveFrom,boolean isDefault){
        return new MapSqlParameterSource("tenant",tenant).addValue("source",source).addValue("id",id).addValue("name",name).addValue("channel",channel).addValue("mode",mode)
                .addValue("principalTypes",csv(r.allowedPrincipalTypes())).addValue("staticPrincipal",nullable(r.staticPrincipalRef())).addValue("department",nullable(r.ownerDepartmentId())).addValue("group",nullable(r.ownerGroupId()))
                .addValue("eventTypes",csv(r.allowedEventTypes())).addValue("objectTypes",csv(r.allowedObjectTypes())).addValue("schemas",csv(r.inputSchemas())).addValue("idem",idem).addValue("retention",retention)
                .addValue("ordering",ordering).addValue("ack",ack).addValue("rate",r.rateLimitPerMinute()).addValue("quota",r.quotaPerDay()).addValue("classification",nullable(r.dataClassificationProfile())).addValue("residency",nullable(r.residencyProfile()))
                .addValue("status",status).addValue("effectiveFrom",effectiveFrom).addValue("effectiveTo",r.effectiveTo()).addValue("defaultRegistration",isDefault);
    }
    private SourceOwnership sourceOwnership(String tenant,String source){try{return jdbc.queryForObject("select owner_department_id,owner_group_id from source_systems where tenant_id=:tenant and source_system_id=:source",new MapSqlParameterSource("tenant",tenant).addValue("source",source),(rs,n)->new SourceOwnership(rs.getString(1),rs.getString(2)));}catch(EmptyResultDataAccessException ex){return new SourceOwnership(null,null);}}
    private static void validateDefault(boolean isDefault,String status){if(isDefault&&!"ACTIVE".equals(status))throw new IllegalArgumentException("Only an ACTIVE registration may be the default");}
    private void demoteExistingDefault(String tenant,String source,String channel,String except){jdbc.update("update workload_source_registrations set is_default=false,version=version+1,updated_at=now() where tenant_id=:tenant and source_system_id=:source and channel_type=:channel and source_registration_id<>:except and is_default=true and status='ACTIVE'",new MapSqlParameterSource("tenant",tenant).addValue("source",source).addValue("channel",channel).addValue("except",except));}
    private void requireSource(String tenant,String source){Integer count=jdbc.queryForObject("select count(*) from source_systems where tenant_id=:tenant and source_system_id=:source and status<>'RETIRED'",new MapSqlParameterSource("tenant",tenant).addValue("source",source),Integer.class);if(count==null||count==0)throw new IllegalArgumentException("Source System not found or retired: "+source);}
    private void bind(String tenant){String actor=IamTenantContextHolder.current().map(c->c.actorId()).orElse("a0-r1-source-registration");jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)",String.class,tenant);jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)",String.class,actor);}
    private static void validate(String channel,String mode,String staticPrincipal,String idem,long retention,String ordering,String ack,String status,OffsetDateTime to,OffsetDateTime from){
        allowed(channel,List.of("EVENT","API","HUMAN","SCHEDULE","A2A_INBOUND","REPLAY"),"channelType");allowed(mode,List.of("STATIC","DYNAMIC"),"principalBindingMode");if("STATIC".equals(mode)&&clean(staticPrincipal).isBlank())throw new IllegalArgumentException("staticPrincipalRef is required for STATIC principal binding");
        allowed(idem,List.of("NONE","OPTIONAL_KEY","REQUIRED_KEY","SOURCE_EVENT_ID"),"idempotencyStrategy");if(retention<60)throw new IllegalArgumentException("idempotencyRetentionSeconds must be >= 60");allowed(ordering,List.of("NONE","SOURCE_SEQUENCE","AGGREGATE_SEQUENCE"),"orderingStrategy");allowed(ack,List.of("SYNC_RESPONSE","CALLBACK","OUTBOX_EVENT","POLL","NONE"),"acknowledgementMode");allowed(status,List.of("ACTIVE","DISABLED","RETIRED"),"status");if(to!=null&&!to.isAfter(from))throw new IllegalArgumentException("effectiveTo must be later than effectiveFrom");
    }
    private static void allowed(String value,List<String> values,String field){if(!values.contains(value))throw new IllegalArgumentException("Unsupported "+field+": "+value);}
    private static String csv(List<String> values){if(values==null||values.isEmpty())return "";return values.stream().map(WorkloadSourceRegistrationService::clean).filter(v->!v.isBlank()).map(v->v.toUpperCase(Locale.ROOT)).distinct().sorted().reduce((a,b)->a+","+b).orElse("");}
    private static String nullable(String v){String s=clean(v);return s.isBlank()?null:s;}private static String required(String v,String n){String s=clean(v);if(s.isBlank())throw new IllegalArgumentException(n+" is required");return s;}private static String clean(String v){return v==null?"":v.trim();}private static String defaultValue(String v,String d){return clean(v).isBlank()?d:clean(v);}private static String upper(String v){return v.toUpperCase(Locale.ROOT);}
    private static List<String> array(ResultSet rs,String column)throws SQLException{Array a=rs.getArray(column);if(a==null)return List.of();Object raw=a.getArray();if(!(raw instanceof Object[] values))return List.of();List<String> out=new ArrayList<>();for(Object v:values)if(v!=null&&!String.valueOf(v).isBlank())out.add(String.valueOf(v));return List.copyOf(out);}
    private record SourceOwnership(String departmentId,String groupId){}
    private static final RowMapper<WorkloadSourceRegistrationView> MAPPER=(rs,n)->new WorkloadSourceRegistrationView(rs.getString("tenant_id"),rs.getString("source_registration_id"),rs.getString("source_system_id"),rs.getString("registration_name"),rs.getString("channel_type"),rs.getString("principal_binding_mode"),array(rs,"allowed_principal_types"),rs.getString("static_principal_ref"),rs.getString("owner_department_id"),rs.getString("owner_group_id"),array(rs,"allowed_event_types"),array(rs,"allowed_object_types"),array(rs,"input_schemas"),rs.getString("idempotency_strategy"),rs.getLong("idempotency_retention_seconds"),rs.getString("ordering_strategy"),rs.getString("acknowledgement_mode"),(Integer)rs.getObject("rate_limit_per_minute"),(Long)rs.getObject("quota_per_day"),rs.getString("data_classification_profile"),rs.getString("residency_profile"),rs.getString("status"),rs.getObject("effective_from",OffsetDateTime.class),rs.getObject("effective_to",OffsetDateTime.class),rs.getBoolean("is_default"),rs.getLong("version"),rs.getObject("created_at",OffsetDateTime.class),rs.getObject("updated_at",OffsetDateTime.class));
}
