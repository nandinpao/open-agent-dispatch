package com.opensocket.aievent.core.capability;

import com.opensocket.aievent.core.security.outbound.OutboundDestinationPolicy;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * C0-B8 credential-binding and outbound-destination-policy authority.
 *
 * <p>New executions snapshot exact IDs. Later Send/Poll/SSE/Push/Cancel operations reload those IDs,
 * never a newly-active binding/policy. Retired snapshots remain usable only for already-admitted
 * executions; they are never eligible for new execution admission.</p>
 */
@Service
public class A2AExternalF0SecurityService {
    private final NamedParameterJdbcTemplate jdbc; private final ObjectMapper json; private final A2APeerCredentialSecretResolver secrets; private final A2AResidencyAuthorizationService residency;
    @Autowired
    public A2AExternalF0SecurityService(NamedParameterJdbcTemplate jdbc,ObjectMapper json,A2APeerCredentialSecretResolver secrets,A2AResidencyAuthorizationService residency){this.jdbc=jdbc;this.json=json;this.secrets=secrets;this.residency=residency;}
    A2AExternalF0SecurityService(NamedParameterJdbcTemplate jdbc,ObjectMapper json,A2APeerCredentialSecretResolver secrets){this(jdbc,json,secrets,null);}

    @Transactional(readOnly=true)
    public List<Map<String,Object>> credentialBindings(String tenant,String interfaceId){bind(tenant,"a2a-credential-binding-read");requireInterface(tenant,interfaceId);return jdbc.queryForList("select tenant_id,binding_id,interface_id,security_scheme_ref,credential_type,secret_ref,header_name,auth_scheme,binding_status,validation_status,validation_message,validated_at,valid_until,binding_version,created_by,activated_at,retired_at,created_at,updated_at from a2a_interface_credential_bindings where tenant_id=:tenant and interface_id=:interface order by created_at desc,binding_id",params(tenant,interfaceId));}

    @Transactional
    public Map<String,Object> upsertCredentialDraft(String tenant,String interfaceId,String bindingId,String securitySchemeRef,String credentialType,String secretRef,String headerName,String authScheme){
        bind(tenant,"a2a-credential-binding-write");requireInterface(tenant,interfaceId);String id=required(bindingId,"bindingId"),scheme=required(securitySchemeRef,"securitySchemeRef"),type=credentialType(credentialType),ref=required(secretRef,"secretRef");
        if("API_KEY_HEADER".equals(type))headerName=required(headerName,"headerName");
        int n=jdbc.update("""
          insert into a2a_interface_credential_bindings(tenant_id,binding_id,interface_id,security_scheme_ref,credential_type,secret_ref,header_name,auth_scheme,binding_status,validation_status,binding_version,created_by,created_at,updated_at)
          values(:tenant,:binding,:interface,:scheme,:type,:secretRef,:header,:authScheme,'DRAFT','UNKNOWN',1,'a2a-credential-admin',now(),now())
          on conflict(tenant_id,binding_id) do update set interface_id=excluded.interface_id,security_scheme_ref=excluded.security_scheme_ref,credential_type=excluded.credential_type,secret_ref=excluded.secret_ref,header_name=excluded.header_name,auth_scheme=excluded.auth_scheme,validation_status='UNKNOWN',validation_message=null,validated_at=null,valid_until=null,binding_version=a2a_interface_credential_bindings.binding_version+1,updated_at=now()
          where a2a_interface_credential_bindings.binding_status='DRAFT'
          """,new MapSqlParameterSource("tenant",tenant).addValue("binding",id).addValue("interface",interfaceId).addValue("scheme",scheme).addValue("type",type).addValue("secretRef",ref).addValue("header",blankToNull(headerName)).addValue("authScheme",blankToNull(authScheme)));
        if(n!=1)throw new IllegalArgumentException("A2A_CREDENTIAL_BINDING_NOT_DRAFT");return credentialBinding(tenant,id);
    }

    @Transactional
    public Map<String,Object> validateCredential(String tenant,String bindingId){
        bind(tenant,"a2a-credential-binding-validate");Map<String,Object> row=requireCredential(tenant,bindingId,false);boolean valid=secrets.canResolve(str(row,"credential_type"),str(row,"secret_ref"),str(row,"header_name"),str(row,"auth_scheme"));String message=valid?"RUNTIME_SECRET_RESOLVABLE":"RUNTIME_SECRET_NOT_RESOLVABLE_OR_TYPE_UNSUPPORTED";
        jdbc.update("update a2a_interface_credential_bindings set validation_status=:status,validation_message=:message,validated_at=now(),updated_at=now() where tenant_id=:tenant and binding_id=:binding and binding_status='DRAFT'",new MapSqlParameterSource("tenant",tenant).addValue("binding",bindingId).addValue("status",valid?"VALID":"INVALID").addValue("message",message));return credentialBinding(tenant,bindingId);
    }

    @Transactional
    public Map<String,Object> activateCredential(String tenant,String bindingId){
        bind(tenant,"a2a-credential-binding-activate");Map<String,Object> row=requireCredential(tenant,bindingId,true);if(!"VALID".equals(str(row,"validation_status")))throw new IllegalArgumentException("A2A_CREDENTIAL_BINDING_VALIDATION_REQUIRED");if("MTLS".equals(str(row,"credential_type")))throw new IllegalArgumentException("A2A_CREDENTIAL_TYPE_NOT_RUNTIME_SUPPORTED:MTLS");String interfaceId=str(row,"interface_id");
        jdbc.update("update a2a_interface_credential_bindings set binding_status='RETIRED',retired_at=now(),updated_at=now() where tenant_id=:tenant and interface_id=:interface and binding_status='ACTIVE'",params(tenant,interfaceId));
        int n=jdbc.update("update a2a_interface_credential_bindings set binding_status='ACTIVE',activated_at=now(),retired_at=null,updated_at=now() where tenant_id=:tenant and binding_id=:binding and binding_status='DRAFT'",new MapSqlParameterSource("tenant",tenant).addValue("binding",bindingId));if(n!=1)throw new IllegalArgumentException("A2A_CREDENTIAL_BINDING_NOT_ACTIVATABLE");suspendIfIneligible(tenant,interfaceId);return credentialBinding(tenant,bindingId);
    }

    @Transactional
    public Map<String,Object> retireCredential(String tenant,String bindingId){bind(tenant,"a2a-credential-binding-retire");Map<String,Object> row=requireCredential(tenant,bindingId,false);jdbc.update("update a2a_interface_credential_bindings set binding_status='RETIRED',retired_at=now(),updated_at=now() where tenant_id=:tenant and binding_id=:binding and binding_status<>'RETIRED'",new MapSqlParameterSource("tenant",tenant).addValue("binding",bindingId));suspendIfIneligible(tenant,str(row,"interface_id"));return credentialBinding(tenant,bindingId);}

    @Transactional(readOnly=true)
    public List<Map<String,Object>> outboundPolicies(String tenant){bind(tenant,"a2a-outbound-policy-read");return jdbc.queryForList("select * from a2a_outbound_destination_policies where tenant_id=:tenant order by created_at desc,policy_id",new MapSqlParameterSource("tenant",tenant));}

    @Transactional
    public Map<String,Object> upsertOutboundPolicyDraft(String tenant,String policyId,String displayName,String description,List<String> allowedSchemes,List<String> hostSuffixes,List<String> cidrs,Boolean denyPrivate,Boolean denyLoopback,Boolean denyLinkLocal,Boolean denyMetadata,Integer maxRedirects,Boolean sameOriginOnly,Boolean dnsRebinding,Integer connectTimeoutMs,Integer readTimeoutMs,String requiredTlsVersion,List<String> certificatePins){
        bind(tenant,"a2a-outbound-policy-write");String id=required(policyId,"policyId"),name=required(displayName,"displayName");List<String> schemes=normalized(allowedSchemes);if(schemes.isEmpty())schemes=List.of("https");for(String s:schemes)if(!List.of("http","https").contains(s))throw new IllegalArgumentException("A2A_OUTBOUND_SCHEME_NOT_SUPPORTED");int redirects=maxRedirects==null?0:maxRedirects;if(redirects<0||redirects>10)throw new IllegalArgumentException("A2A_OUTBOUND_MAX_REDIRECTS_INVALID");boolean rebind=bool(dnsRebinding,false);String tls=normalizeTlsVersion(requiredTlsVersion);List<String> pins=normalizedPins(certificatePins);validatePins(pins);boolean runtimeReady=redirects==0&&rebind;String runtimeReason=runtimeReady?"PC-S4_SECURE_TRANSPORT_RUNTIME_READY":"DNS_REBINDING_PROTECTION_REQUIRED_OR_REDIRECT_NOT_SUPPORTED";
        int n=jdbc.update("""
          insert into a2a_outbound_destination_policies(tenant_id,policy_id,display_name,description,allowed_schemes_json,allowlist_host_suffixes_json,allowlist_cidrs_json,deny_private_addresses,deny_loopback_addresses,deny_link_local_addresses,deny_cloud_metadata_endpoints,max_redirects,follow_redirect_same_origin_only,dns_rebinding_protection,connect_timeout_ms,read_timeout_ms,required_tls_version,certificate_pins_json,policy_status,runtime_status,runtime_status_reason,policy_version,created_by,created_at,updated_at)
          values(:tenant,:policy,:name,:description,cast(:schemes as jsonb),cast(:hosts as jsonb),cast(:cidrs as jsonb),:denyPrivate,:denyLoopback,:denyLinkLocal,:denyMetadata,:redirects,:sameOrigin,:dnsRebinding,:connectTimeout,:readTimeout,:tls,cast(:pins as jsonb),'DRAFT',:runtimeStatus,:runtimeReason,1,'a2a-outbound-admin',now(),now())
          on conflict(tenant_id,policy_id) do update set display_name=excluded.display_name,description=excluded.description,allowed_schemes_json=excluded.allowed_schemes_json,allowlist_host_suffixes_json=excluded.allowlist_host_suffixes_json,allowlist_cidrs_json=excluded.allowlist_cidrs_json,deny_private_addresses=excluded.deny_private_addresses,deny_loopback_addresses=excluded.deny_loopback_addresses,deny_link_local_addresses=excluded.deny_link_local_addresses,deny_cloud_metadata_endpoints=excluded.deny_cloud_metadata_endpoints,max_redirects=excluded.max_redirects,follow_redirect_same_origin_only=excluded.follow_redirect_same_origin_only,dns_rebinding_protection=excluded.dns_rebinding_protection,connect_timeout_ms=excluded.connect_timeout_ms,read_timeout_ms=excluded.read_timeout_ms,required_tls_version=excluded.required_tls_version,certificate_pins_json=excluded.certificate_pins_json,runtime_status=excluded.runtime_status,runtime_status_reason=excluded.runtime_status_reason,policy_version=a2a_outbound_destination_policies.policy_version+1,updated_at=now()
          where a2a_outbound_destination_policies.policy_status='DRAFT'
          """,new MapSqlParameterSource("tenant",tenant).addValue("policy",id).addValue("name",name).addValue("description",blankToNull(description)).addValue("schemes",write(schemes)).addValue("hosts",write(normalized(hostSuffixes))).addValue("cidrs",write(normalized(cidrs))).addValue("denyPrivate",bool(denyPrivate,true)).addValue("denyLoopback",bool(denyLoopback,true)).addValue("denyLinkLocal",bool(denyLinkLocal,true)).addValue("denyMetadata",bool(denyMetadata,true)).addValue("redirects",redirects).addValue("sameOrigin",bool(sameOriginOnly,true)).addValue("dnsRebinding",rebind).addValue("connectTimeout",connectTimeoutMs==null?10000:connectTimeoutMs).addValue("readTimeout",readTimeoutMs==null?45000:readTimeoutMs).addValue("tls",tls).addValue("pins",write(pins)).addValue("runtimeStatus",runtimeReady?"RUNTIME_READY":"NOT_RUNTIME_SUPPORTED").addValue("runtimeReason",runtimeReason));
        if(n!=1)throw new IllegalArgumentException("A2A_OUTBOUND_POLICY_NOT_DRAFT");return outboundPolicy(tenant,id);
    }

    @Transactional
    public Map<String,Object> activateOutboundPolicy(String tenant,String policyId){bind(tenant,"a2a-outbound-policy-activate");Map<String,Object> row=requireOutboundPolicy(tenant,policyId,true);if(!"RUNTIME_READY".equals(str(row,"runtime_status")))throw new IllegalArgumentException("A2A_OUTBOUND_POLICY_NOT_RUNTIME_READY");int n=jdbc.update("update a2a_outbound_destination_policies set policy_status='ACTIVE',activated_at=now(),retired_at=null,updated_at=now() where tenant_id=:tenant and policy_id=:policy and policy_status='DRAFT'",new MapSqlParameterSource("tenant",tenant).addValue("policy",policyId));if(n!=1)throw new IllegalArgumentException("A2A_OUTBOUND_POLICY_NOT_ACTIVATABLE");return outboundPolicy(tenant,policyId);}

    @Transactional
    public Map<String,Object> retireOutboundPolicy(String tenant,String policyId){bind(tenant,"a2a-outbound-policy-retire");jdbc.update("update a2a_outbound_destination_policies set policy_status='RETIRED',retired_at=now(),updated_at=now() where tenant_id=:tenant and policy_id=:policy and policy_status<>'RETIRED'",new MapSqlParameterSource("tenant",tenant).addValue("policy",policyId));jdbc.update("update a2a_peer_provider_links l set status='SUSPENDED',updated_at=now() from a2a_peer_interfaces i where l.tenant_id=:tenant and l.tenant_id=i.tenant_id and l.interface_id=i.interface_id and i.outbound_destination_policy_ref=:policy and l.status='ACTIVE'",new MapSqlParameterSource("tenant",tenant).addValue("policy",policyId));return outboundPolicy(tenant,policyId);}

    /** Admission snapshot for a new execution. Only current ACTIVE/VALID/RUNTIME_READY authorities are accepted. */
    @Transactional(readOnly=true)
    public SecuritySnapshot snapshotForInterface(String tenant,String interfaceId){bind(tenant,"a2a-f0-security-admission");String bindingId=jdbc.queryForObject("select a2a_interface_current_credential_binding_id(:tenant,:interface)",params(tenant,interfaceId),String.class);String policyId=jdbc.queryForObject("select a2a_interface_current_outbound_policy_id(:tenant,:interface)",params(tenant,interfaceId),String.class);if(bindingId==null||bindingId.isBlank())throw new IllegalArgumentException("A2A_CREDENTIAL_BINDING_REQUIRED");if(policyId==null||policyId.isBlank())throw new IllegalArgumentException("A2A_OUTBOUND_POLICY_REQUIRED");String residencyDecisionId=residency==null?null:residency.authorizeAdmission(tenant,interfaceId).decisionId();return new SecuritySnapshot(bindingId,policyId,residencyDecisionId);}

    /** Exact execution snapshot. Retired authorities may serve existing work, but runtime never switches IDs. */
    @Transactional(readOnly=true)
    public RuntimeSecurity runtimeSecurityForExecution(String tenant,String executionId){bind(tenant,"a2a-f0-security-runtime");Map<String,Object> ex;try{ex=jdbc.queryForMap("select interface_id,credential_binding_id,outbound_destination_policy_id,residency_decision_id from a2a_remote_read_executions where tenant_id=:tenant and execution_id=:execution",new MapSqlParameterSource("tenant",tenant).addValue("execution",executionId));}catch(EmptyResultDataAccessException e){throw new IllegalArgumentException("A2A_EXECUTION_NOT_FOUND");}String interfaceId=str(ex,"interface_id"),bindingId=str(ex,"credential_binding_id"),policyId=str(ex,"outbound_destination_policy_id"),residencyDecisionId=str(ex,"residency_decision_id");if(bindingId.isBlank()||policyId.isBlank())throw new IllegalArgumentException("A2A_EXECUTION_SECURITY_SNAPSHOT_REQUIRED");if(residency!=null){if(residencyDecisionId.isBlank())throw new IllegalArgumentException("A2A_EXECUTION_RESIDENCY_SNAPSHOT_REQUIRED");residency.requireCurrentAllowed(tenant,interfaceId);}Map<String,Object> binding=requireCredential(tenant,bindingId,false);if(!"VALID".equals(str(binding,"validation_status")))throw new IllegalArgumentException("A2A_EXECUTION_CREDENTIAL_NOT_VALID");Object until=binding.get("valid_until");if(until instanceof OffsetDateTime dt&&dt.isBefore(OffsetDateTime.now()))throw new IllegalArgumentException("A2A_EXECUTION_CREDENTIAL_EXPIRED");A2APeerCredentialSecretResolver.ResolvedCredential credential=secrets.resolve(str(binding,"credential_type"),str(binding,"secret_ref"),str(binding,"header_name"),str(binding,"auth_scheme"));Map<String,Object> policy=requireOutboundPolicy(tenant,policyId,false);if(!List.of("ACTIVE","RETIRED").contains(str(policy,"policy_status"))||!"RUNTIME_READY".equals(str(policy,"runtime_status")))throw new IllegalArgumentException("A2A_EXECUTION_OUTBOUND_POLICY_NOT_RUNTIME_READY");return new RuntimeSecurity(bindingId,policyId,credential,policyFrom(policy),(Integer)policy.get("connect_timeout_ms"),(Integer)policy.get("read_timeout_ms"));}

    @Transactional(readOnly=true) public Map<String,Object> credentialBinding(String tenant,String bindingId){bind(tenant,"a2a-credential-binding-read");return requireCredential(tenant,bindingId,false);}
    @Transactional(readOnly=true) public Map<String,Object> outboundPolicy(String tenant,String policyId){bind(tenant,"a2a-outbound-policy-read");return requireOutboundPolicy(tenant,policyId,false);}

    private OutboundDestinationPolicy policyFrom(Map<String,Object> row){return new OutboundDestinationPolicy(java.util.Set.copyOf(readList(row.get("allowed_schemes_json"))),readList(row.get("allowlist_host_suffixes_json")),readList(row.get("allowlist_cidrs_json")),boolObj(row.get("deny_private_addresses"),true),boolObj(row.get("deny_loopback_addresses"),true),boolObj(row.get("deny_link_local_addresses"),true),boolObj(row.get("deny_cloud_metadata_endpoints"),true),intObj(row.get("max_redirects"),0),boolObj(row.get("follow_redirect_same_origin_only"),true),boolObj(row.get("dns_rebinding_protection"),false),blankToNull(str(row,"required_tls_version")),readListPreserveCase(row.get("certificate_pins_json")),intObj(row.get("max_request_bytes"),1048576),intObj(row.get("max_response_bytes"),4194304),intObj(row.get("max_sse_event_bytes"),262144),intObj(row.get("max_sse_stream_bytes"),8388608),intObj(row.get("max_sse_events"),2048),intObj(row.get("sse_idle_timeout_ms"),30000),intObj(row.get("sse_overall_timeout_ms"),300000));}
    private void suspendIfIneligible(String tenant,String interfaceId){Boolean eligible=jdbc.queryForObject("select a2a_interface_current_contract_eligible(:tenant,:interface)",params(tenant,interfaceId),Boolean.class);if(!Boolean.TRUE.equals(eligible))jdbc.update("update a2a_peer_provider_links set status='SUSPENDED',updated_at=now() where tenant_id=:tenant and interface_id=:interface and status='ACTIVE'",params(tenant,interfaceId));}
    private void requireInterface(String tenant,String interfaceId){Integer n=jdbc.queryForObject("select count(*) from a2a_peer_interfaces where tenant_id=:tenant and interface_id=:interface",params(tenant,interfaceId),Integer.class);if(n==null||n!=1)throw new IllegalArgumentException("A2A_INTERFACE_NOT_FOUND");}
    private Map<String,Object> requireCredential(String tenant,String bindingId,boolean draft){try{Map<String,Object> row=jdbc.queryForMap("select * from a2a_interface_credential_bindings where tenant_id=:tenant and binding_id=:binding",new MapSqlParameterSource("tenant",tenant).addValue("binding",bindingId));if(draft&&!"DRAFT".equals(str(row,"binding_status")))throw new IllegalArgumentException("A2A_CREDENTIAL_BINDING_NOT_DRAFT");return row;}catch(EmptyResultDataAccessException ex){throw new IllegalArgumentException("A2A_CREDENTIAL_BINDING_NOT_FOUND");}}
    private Map<String,Object> requireOutboundPolicy(String tenant,String policyId,boolean draft){try{Map<String,Object> row=jdbc.queryForMap("select * from a2a_outbound_destination_policies where tenant_id=:tenant and policy_id=:policy",new MapSqlParameterSource("tenant",tenant).addValue("policy",policyId));if(draft&&!"DRAFT".equals(str(row,"policy_status")))throw new IllegalArgumentException("A2A_OUTBOUND_POLICY_NOT_DRAFT");return row;}catch(EmptyResultDataAccessException ex){throw new IllegalArgumentException("A2A_OUTBOUND_POLICY_NOT_FOUND");}}
    private MapSqlParameterSource params(String tenant,String interfaceId){return new MapSqlParameterSource("tenant",tenant).addValue("interface",interfaceId);}
    private void bind(String tenant,String actor){jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)",String.class,tenant);jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)",String.class,actor);}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception ex){throw new IllegalArgumentException("A2A_F0_SECURITY_JSON_FAILED",ex);}}
    private List<String> readList(Object value){if(value==null)return List.of();try{return json.readValue(String.valueOf(value),new TypeReference<List<String>>(){});}catch(Exception ex){return List.of();}}
    private List<String> readListPreserveCase(Object value){if(value==null)return List.of();try{return json.readValue(String.valueOf(value),new TypeReference<List<String>>(){});}catch(Exception ex){return List.of();}}
    private static List<String> normalized(List<String> values){if(values==null)return List.of();return values.stream().filter(v->v!=null&&!v.isBlank()).map(v->v.trim().toLowerCase(Locale.ROOT)).distinct().sorted().toList();}
    private static List<String> normalizedPins(List<String> values){if(values==null)return List.of();return values.stream().filter(v->v!=null&&!v.isBlank()).map(String::trim).distinct().sorted().toList();}
    private static String normalizeTlsVersion(String value){if(value==null||value.isBlank())return null;String v=value.trim().toUpperCase(Locale.ROOT);return switch(v){case "TLSV1.2","TLS1.2","1.2"->"TLSv1.2";case "TLSV1.3","TLS1.3","1.3"->"TLSv1.3";default->throw new IllegalArgumentException("A2A_TLS_VERSION_NOT_SUPPORTED");};}
    private static void validatePins(List<String> pins){for(String pin:pins)if(!(pin.startsWith("sha256/")||pin.toLowerCase(Locale.ROOT).startsWith("sha256:")))throw new IllegalArgumentException("A2A_CERTIFICATE_PIN_FORMAT_INVALID");}
    private static String credentialType(String value){String v=required(value,"credentialType").toUpperCase(Locale.ROOT);if(!List.of("BEARER_TOKEN","API_KEY_HEADER","MTLS").contains(v))throw new IllegalArgumentException("A2A_CREDENTIAL_TYPE_INVALID");return v;}
    private static String required(String value,String field){if(value==null||value.isBlank())throw new IllegalArgumentException("A2A_F0_"+field.toUpperCase(Locale.ROOT)+"_REQUIRED");return value.trim();}
    private static String str(Map<String,Object> row,String key){Object v=row.get(key);return v==null?"":String.valueOf(v);}
    private static String blankToNull(String v){return v==null||v.isBlank()?null:v.trim();}
    private static boolean bool(Boolean value,boolean d){return value==null?d:value;}
    private static boolean boolObj(Object value,boolean d){return value instanceof Boolean b?b:d;}
    private static int intObj(Object value,int d){return value instanceof Number n?n.intValue():d;}

    public record SecuritySnapshot(String credentialBindingId,String outboundDestinationPolicyId,String residencyDecisionId){}
    public record RuntimeSecurity(String credentialBindingId,String outboundDestinationPolicyId,A2APeerCredentialSecretResolver.ResolvedCredential credential,OutboundDestinationPolicy outboundPolicy,int connectTimeoutMs,int readTimeoutMs){}
}
