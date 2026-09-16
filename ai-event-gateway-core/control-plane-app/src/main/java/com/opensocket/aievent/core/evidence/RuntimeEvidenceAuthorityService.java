package com.opensocket.aievent.core.evidence;

import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * A0-R8 canonical evidence/payload authority.
 *
 * <p>This service never routes, assigns or dispatches work. Sensitive payloads fail closed unless
 * both tenant HMAC key resolution and external envelope protection are available.</p>
 */
@Service
public class RuntimeEvidenceAuthorityService {
    private static final String CANONICALIZATION = "A0R8_JSON_CANONICAL_V1";
    private static final List<String> SENSITIVE = List.of("PERSONAL","SENSITIVE","RESTRICTED","SECRET");
    private static final List<String> FORBIDDEN_DETAIL_KEYS = List.of("payload","rawpayload","raw_payload","body","content","secret","token","password");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ObjectProvider<EvidenceDigestKeyResolver> digestKeys;
    private final ObjectProvider<EvidencePayloadCryptoPort> payloadCrypto;

    public RuntimeEvidenceAuthorityService(NamedParameterJdbcTemplate jdbc, ObjectMapper json,
                                           ObjectProvider<EvidenceDigestKeyResolver> digestKeys,
                                           ObjectProvider<EvidencePayloadCryptoPort> payloadCrypto) {
        this.jdbc=jdbc; this.json=json; this.digestKeys=digestKeys; this.payloadCrypto=payloadCrypto;
    }

    @Transactional
    public ExecutionEvidenceAppendResult append(ExecutionEvidenceAppendRequest request) {
        String tenant=required(request.tenantId(),"tenantId"); bind(tenant);
        String classification=upper(defaulted(request.classification(),"INTERNAL"));
        validateClassification(classification);
        Map<String,Object> details=safeDetails(request.details());
        OffsetDateTime occurred=request.occurredAt()==null?OffsetDateTime.now(ZoneOffset.UTC):request.occurredAt();
        String evidenceId="ev-"+UUID.randomUUID();
        String handle=null,digest=null,digestMode=null,digestAlgorithm=null,keyRef=null,keyVersion=null;

        if(request.payload()!=null) {
            byte[] canonical=canonicalBytes(request.payload());
            handle="ep-"+UUID.randomUUID();
            boolean sensitive=SENSITIVE.contains(classification);
            byte[] storedBytes=canonical;
            String encryptionMode="NONE", encryptionKeyRef=null, encryptionKeyVersion=null;
            if(sensitive) {
                keyRef=required(request.digestKeyRef(),"digestKeyRef");
                keyVersion=required(request.digestKeyVersion(),"digestKeyVersion");
                assertActiveDigestKey(tenant,keyRef,keyVersion);
                EvidenceDigestKeyResolver resolver=digestKeys.getIfAvailable();
                if(resolver==null) throw new IllegalStateException("A0_R8_DIGEST_KEY_RESOLVER_UNAVAILABLE");
                try(EvidenceDigestKeyResolver.ResolvedEvidenceDigestKey key=resolver.resolve(tenant,keyRef,keyVersion)) {
                    digest=hmacSha256(key.keyBytes(),canonical);
                }
                digestMode="HMAC_SHA256_TENANT_KEY"; digestAlgorithm="SHA256";
                EvidencePayloadCryptoPort crypto=payloadCrypto.getIfAvailable();
                if(crypto==null) throw new IllegalStateException("A0_R8_SENSITIVE_PAYLOAD_CRYPTO_UNAVAILABLE");
                EvidencePayloadCryptoPort.ProtectedPayload protectedPayload=crypto.protect(tenant,canonical);
                storedBytes=protectedPayload.ciphertext();
                encryptionMode="EXTERNAL_ENVELOPE";
                encryptionKeyRef=required(protectedPayload.keyRef(),"encryptionKeyRef");
                encryptionKeyVersion=required(protectedPayload.keyVersion(),"encryptionKeyVersion");
            } else {
                digest=sha256(canonical); digestMode="SHA256_CANONICAL"; digestAlgorithm="SHA256";
            }
            jdbc.update("""
                insert into evidence_payload_store_v207(
                    tenant_id,payload_handle,content_type,content_encoding,encryption_mode,encryption_key_ref,encryption_key_version,
                    payload_bytes,payload_size_bytes,classification,storage_tier,disposition_state,retention_policy_ref)
                values(:tenant,:handle,:contentType,'UTF-8',:encryptionMode,:encryptionKeyRef,:encryptionKeyVersion,
                    :bytes,:size,:classification,'HOT','ACTIVE',:retention)
                """, new MapSqlParameterSource("tenant",tenant).addValue("handle",handle)
                    .addValue("contentType",defaulted(request.contentType(),"application/json"))
                    .addValue("encryptionMode",encryptionMode).addValue("encryptionKeyRef",encryptionKeyRef)
                    .addValue("encryptionKeyVersion",encryptionKeyVersion).addValue("bytes",storedBytes)
                    .addValue("size",canonical.length).addValue("classification",classification)
                    .addValue("retention",trim(request.retentionPolicyRef())));
        }

        jdbc.update("""
            insert into execution_evidence_ledger_v207(
              tenant_id,evidence_id,evidence_type,source_family,source_ref,task_id,plan_id,plan_revision,step_id,
              assignment_id,dispatch_intent_id,decision_id,policy_snapshot_ref,actor_type,actor_id,classification,
              digest_algorithm,digest_mode,canonicalization_version,digest_key_ref,digest_key_version,payload_digest,
              opaque_payload_handle,payload_disposition_state,retention_policy_ref,details_json,occurred_at,schema_version)
            values(:tenant,:evidence,:type,:family,:source,:task,:plan,:revision,:step,:assignment,:intent,:decision,:policy,
              :actorType,:actorId,:classification,:algorithm,:mode,:canonicalization,:keyRef,:keyVersion,:digest,:handle,
              :payloadState,:retention,cast(:details as jsonb),:occurred,'V207')
            """, new MapSqlParameterSource("tenant",tenant).addValue("evidence",evidenceId)
                .addValue("type",required(request.evidenceType(),"evidenceType")).addValue("family",required(request.sourceFamily(),"sourceFamily"))
                .addValue("source",required(request.sourceRef(),"sourceRef")).addValue("task",trim(request.taskId()))
                .addValue("plan",trim(request.planId())).addValue("revision",request.planRevision()).addValue("step",trim(request.stepId()))
                .addValue("assignment",trim(request.assignmentId())).addValue("intent",trim(request.dispatchIntentId()))
                .addValue("decision",trim(request.decisionId())).addValue("policy",trim(request.policySnapshotRef()))
                .addValue("actorType",trim(request.actorType())).addValue("actorId",trim(request.actorId()))
                .addValue("classification",classification).addValue("algorithm",digestAlgorithm).addValue("mode",digestMode)
                .addValue("canonicalization",handle==null?null:CANONICALIZATION).addValue("keyRef",keyRef).addValue("keyVersion",keyVersion)
                .addValue("digest",digest).addValue("handle",handle).addValue("payloadState",handle==null?"NONE":"ACTIVE")
                .addValue("retention",trim(request.retentionPolicyRef())).addValue("details",write(details)).addValue("occurred",occurred));
        return new ExecutionEvidenceAppendResult(evidenceId,handle,digest,digestMode,keyVersion,"APPENDED");
    }

    @Transactional
    public Map<String,Object> dispose(EvidencePayloadDispositionRequest request) {
        String tenant=required(request.tenantId(),"tenantId"); bind(tenant);
        String handle=required(request.payloadHandle(),"payloadHandle"); String target=upper(required(request.disposition(),"disposition"));
        if(!List.of("DELETED","CRYPTO_SHREDDED","LEGAL_HOLD","ARCHIVED").contains(target)) throw new IllegalArgumentException("Unsupported disposition: "+target);
        Map<String,Object> row=jdbc.queryForMap("""
            select disposition_state,encryption_mode,encryption_key_ref,encryption_key_version,classification
              from evidence_payload_store_v207 where tenant_id=:tenant and payload_handle=:handle
            """,new MapSqlParameterSource("tenant",tenant).addValue("handle",handle));
        if("CRYPTO_SHREDDED".equals(target)) {
            if(!"EXTERNAL_ENVELOPE".equals(String.valueOf(row.get("encryption_mode")))) throw new IllegalStateException("A0_R8_CRYPTO_SHRED_REQUIRES_EXTERNAL_ENVELOPE_KEY");
            EvidencePayloadCryptoPort crypto=payloadCrypto.getIfAvailable();
            if(crypto==null) throw new IllegalStateException("A0_R8_PAYLOAD_CRYPTO_UNAVAILABLE");
            crypto.cryptoShred(tenant,String.valueOf(row.get("encryption_key_ref")),String.valueOf(row.get("encryption_key_version")),required(request.reason(),"reason"));
        }
        String sql="LEGAL_HOLD".equals(target)
                ? "update evidence_payload_store_v207 set disposition_state='LEGAL_HOLD',legal_hold_ref=:hold,disposition_reason=:reason where tenant_id=:tenant and payload_handle=:handle"
                : "update evidence_payload_store_v207 set payload_bytes=null,disposition_state=:target,disposed_at=now(),disposition_reason=:reason where tenant_id=:tenant and payload_handle=:handle";
        int n=jdbc.update(sql,new MapSqlParameterSource("tenant",tenant).addValue("handle",handle).addValue("target",target)
                .addValue("reason",required(request.reason(),"reason")).addValue("hold",trim(request.legalHoldRef())));
        if(n!=1) throw new IllegalStateException("A0_R8_PAYLOAD_DISPOSITION_NOT_APPLIED");
        appendDispositionEvidence(tenant,handle,target,request.reason());
        return Map.of("payloadHandle",handle,"disposition",target,"ledgerImmutable",true);
    }

    @Transactional
    public Map<String,Object> registerDigestKey(String tenantId, Map<String,Object> body) {
        String tenant=required(tenantId,"tenantId"); bind(tenant); String keyRef=req(body,"keyRef"),version=req(body,"keyVersion"),backend=req(body,"secretBackendRef");
        if(backend.startsWith("literal:")) throw new IllegalArgumentException("Secret material cannot be stored in evidence key metadata");
        jdbc.update("update evidence_digest_key_registry_v207 set status='RETIRED' where tenant_id=:tenant and key_ref=:ref and status='ACTIVE'",
                new MapSqlParameterSource("tenant",tenant).addValue("ref",keyRef));
        jdbc.update("""
          insert into evidence_digest_key_registry_v207(tenant_id,key_ref,key_version,digest_mode,secret_backend_ref,status,rotated_from_version,created_by)
          values(:tenant,:ref,:version,'HMAC_SHA256_TENANT_KEY',:backend,'ACTIVE',:rotated,:actor)
          """,new MapSqlParameterSource("tenant",tenant).addValue("ref",keyRef).addValue("version",version).addValue("backend",backend)
                .addValue("rotated",obj(body,"rotatedFromVersion")).addValue("actor",actor()));
        return Map.of("keyRef",keyRef,"keyVersion",version,"status","ACTIVE","secretMaterialStored",false);
    }

    @Transactional
    public Map<String,Object> recordAcceptance(RuntimeAcceptanceEvidenceRequest request) {
        String tenant=required(request.tenantId(),"tenantId"); bind(tenant); String scenario=required(request.scenarioCode(),"scenarioCode");
        String result=upper(required(request.result(),"result")); if(!List.of("PASS","FAIL","BLOCKED","NOT_RUN").contains(result)) throw new IllegalArgumentException("Unsupported acceptance result");
        Long exists=jdbc.queryForObject("select count(*) from a0_r8_acceptance_scenario_catalog_v207 where scenario_code=:scenario",new MapSqlParameterSource("scenario",scenario),Long.class);
        if(exists==null||exists!=1) throw new IllegalArgumentException("Unknown A0-R8 scenario: "+scenario);
        String runId="r8run-"+UUID.randomUUID();
        jdbc.update("""
          insert into a0_r8_acceptance_runs_v207(tenant_id,run_id,scenario_code,result,environment_ref,evidence_ref,details_json,executed_by)
          values(:tenant,:run,:scenario,:result,:environment,:evidence,cast(:details as jsonb),:actor)
          """,new MapSqlParameterSource("tenant",tenant).addValue("run",runId).addValue("scenario",scenario).addValue("result",result)
                .addValue("environment",required(request.environmentRef(),"environmentRef")).addValue("evidence",required(request.evidenceRef(),"evidenceRef"))
                .addValue("details",write(safeDetails(request.details()))).addValue("actor",actor()));
        return Map.of("runId",runId,"scenarioCode",scenario,"result",result,"selfCertified",false);
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> evidence(String tenantId,String taskId,int limit) {
        String tenant=required(tenantId,"tenantId"); bind(tenant); MapSqlParameterSource p=new MapSqlParameterSource("tenant",tenant).addValue("limit",bound(limit,1,2000));
        String sql="select * from canonical_execution_evidence_projection_v207 where tenant_id=:tenant"+(blank(taskId)?"":" and task_id=:task")+" order by occurred_at,evidence_id limit :limit";
        if(!blank(taskId))p.addValue("task",taskId.trim()); return jdbc.queryForList(sql,p);
    }

    @Transactional(readOnly=true)
    public Map<String,Object> acceptanceSummary(String tenantId) {
        String tenant=required(tenantId,"tenantId"); bind(tenant);
        List<Map<String,Object>> gate=jdbc.queryForList("select * from a0_r8_release_gate_v207 where tenant_id=:tenant",new MapSqlParameterSource("tenant",tenant));
        List<Map<String,Object>> scenarios=jdbc.queryForList("""
          select c.scenario_code,c.category,c.required,c.evidence_requirement,coalesce(r.result,'NOT_RUN') result,r.environment_ref,r.evidence_ref,r.executed_at
            from a0_r8_acceptance_scenario_catalog_v207 c left join a0_r8_latest_acceptance_result_v207 r
              on r.tenant_id=:tenant and r.scenario_code=c.scenario_code order by c.category,c.scenario_code
          """,new MapSqlParameterSource("tenant",tenant));
        Map<String,Object> g=gate.isEmpty()?Map.of("tenant_id",tenant,"required_count",scenarios.size(),"passed_count",0,"blocking_count",scenarios.size(),"gate_status","NOT_CERTIFIED"):gate.getFirst();
        return Map.of("gate",g,"scenarios",scenarios,"productionReady",false,"authorityExpansion",false);
    }

    private void appendDispositionEvidence(String tenant,String handle,String disposition,String reason) {
        jdbc.update("""
          insert into execution_evidence_ledger_v207(tenant_id,evidence_id,evidence_type,source_family,source_ref,classification,
            payload_disposition_state,details_json,occurred_at,schema_version)
          values(:tenant,:id,'PAYLOAD_DISPOSITION_CHANGED','EVIDENCE_PAYLOAD',:source,'INTERNAL',:state,cast(:details as jsonb),now(),'V207')
          """,new MapSqlParameterSource("tenant",tenant).addValue("id","ev-"+UUID.randomUUID()).addValue("source",handle+":"+disposition+":"+UUID.randomUUID())
                .addValue("state",disposition).addValue("details",write(Map.of("payloadHandle",handle,"disposition",disposition,"reason",defaulted(reason,"")))));
    }

    private void assertActiveDigestKey(String tenant,String ref,String version) {
        Long n=jdbc.queryForObject("select count(*) from evidence_digest_key_registry_v207 where tenant_id=:tenant and key_ref=:ref and key_version=:version and status='ACTIVE'",
                new MapSqlParameterSource("tenant",tenant).addValue("ref",ref).addValue("version",version),Long.class);
        if(n==null||n!=1) throw new IllegalStateException("A0_R8_DIGEST_KEY_NOT_ACTIVE");
    }
    private byte[] canonicalBytes(Object value){try{return json.writeValueAsBytes(canonicalize(value));}catch(Exception e){throw new IllegalArgumentException("Canonical JSON serialization failed",e);}}
    private Object canonicalize(Object value) {
        if(value==null||value instanceof Boolean)return value;
        if(value instanceof String s)return Normalizer.normalize(s,Normalizer.Form.NFC);
        if(value instanceof Number n){try{return new BigDecimal(String.valueOf(n)).stripTrailingZeros();}catch(Exception ignored){return String.valueOf(n);}}
        if(value instanceof Map<?,?> map){Map<String,Object> out=new TreeMap<>();for(var e:map.entrySet())out.put(Normalizer.normalize(String.valueOf(e.getKey()),Normalizer.Form.NFC),canonicalize(e.getValue()));return out;}
        if(value instanceof Collection<?> c){List<Object> out=new ArrayList<>();for(Object o:c)out.add(canonicalize(o));return out;}
        return canonicalize(json.convertValue(value,Map.class));
    }
    private Map<String,Object> safeDetails(Map<String,Object> input){Map<String,Object> out=new TreeMap<>();if(input==null)return out;for(var e:input.entrySet()){String k=e.getKey()==null?"":e.getKey().trim();String l=k.toLowerCase(Locale.ROOT);if(FORBIDDEN_DETAIL_KEYS.contains(l))throw new IllegalArgumentException("Raw payload/secret material is forbidden in evidence details: "+k);out.put(k,canonicalize(e.getValue()));}return out;}
    private String sha256(byte[] b){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}catch(Exception e){throw new IllegalStateException(e);}}
    private String hmacSha256(byte[] key,byte[] b){try{Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(key,"HmacSHA256"));return HexFormat.of().formatHex(m.doFinal(b));}catch(Exception e){throw new IllegalStateException("HMAC-SHA256 failed",e);}}
    private void validateClassification(String c){if(!List.of("PUBLIC","INTERNAL","CONFIDENTIAL","PERSONAL","SENSITIVE","RESTRICTED","SECRET").contains(c))throw new IllegalArgumentException("Unsupported classification: "+c);}
    private void bind(String tenant){IamTenantExecutionContext c=IamTenantContextHolder.current().orElse(null);if(c!=null&&!"INSTANCE".equalsIgnoreCase(c.tenantId())&&!tenant.equals(c.tenantId()))throw new IllegalArgumentException("Tenant context mismatch for A0-R8 evidence authority");jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)",String.class,tenant);jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)",String.class,actor());}
    private String actor(){IamTenantExecutionContext c=IamTenantContextHolder.current().orElse(null);return c==null||blank(c.actorId())?"a0-r8-evidence-authority":c.actorId();}
    private String write(Object v){try{return json.writeValueAsString(v);}catch(Exception e){throw new IllegalArgumentException("JSON serialization failed",e);}}
    private static Object obj(Map<String,Object>b,String k){return b==null?null:b.get(k);} private static String req(Map<String,Object>b,String k){Object v=obj(b,k);return required(v==null?null:String.valueOf(v),k);}
    private static String required(String v,String f){if(blank(v))throw new IllegalArgumentException(f+" is required");return v.trim();} private static String defaulted(String v,String d){return blank(v)?d:v.trim();}
    private static String trim(String v){return blank(v)?null:v.trim();} private static boolean blank(String v){return v==null||v.isBlank();} private static String upper(String v){return required(v,"value").toUpperCase(Locale.ROOT).replace(' ','_');} private static int bound(int v,int min,int max){return Math.max(min,Math.min(max,v));}
}
