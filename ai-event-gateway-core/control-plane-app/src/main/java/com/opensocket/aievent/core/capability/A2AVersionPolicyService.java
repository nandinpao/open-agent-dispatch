package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** C0-B5 A2AVersionPolicy authority. It evaluates declared versions; it never negotiates or silently downgrades them. */
@Service
public class A2AVersionPolicyService {
    private final NamedParameterJdbcTemplate jdbc; private final ObjectMapper json;
    public A2AVersionPolicyService(NamedParameterJdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}

    @Transactional(readOnly=true) public List<Map<String,Object>> policies(String tenant){bind(tenant,"a2a-version-policy-read");return jdbc.queryForList("select policy_id,display_name,description,policy_status,preferred_version,min_accepted_version,max_accepted_version,deprecated_versions_json,compatibility_mode,policy_version,activated_at,retired_at,created_at,updated_at from a2a_version_policies where tenant_id=:tenant order by case policy_status when 'ACTIVE' then 0 when 'DRAFT' then 1 else 2 end,updated_at desc",new MapSqlParameterSource("tenant",tenant));}
    @Transactional(readOnly=true) public Map<String,Object> policy(String tenant,String policyId){bind(tenant,"a2a-version-policy-read");Map<String,Object> out=new LinkedHashMap<>(requirePolicy(tenant,policyId));out.put("legacyAllowlist",allowlist(tenant,policyId));return out;}

    @Transactional
    public Map<String,Object> upsertDraft(String tenant,String policyId,String displayName,String description,String preferredVersion,String minAcceptedVersion,String maxAcceptedVersion,List<String> deprecatedVersions,List<Map<String,Object>> legacyAllowlist){
        bind(tenant,"a2a-version-policy-write");required(policyId,"policyId");String preferred=version(preferredVersion,"preferredVersion"),min=version(minAcceptedVersion,"minAcceptedVersion"),max=version(maxAcceptedVersion,"maxAcceptedVersion");
        if(compare(min,preferred)>0||compare(preferred,max)>0)throw new IllegalArgumentException("A2A_VERSION_POLICY_RANGE_INVALID");List<String> deprecated=normalizedVersions(deprecatedVersions);if(deprecated.contains(preferred))throw new IllegalArgumentException("A2A_VERSION_POLICY_PREFERRED_DEPRECATED");
        MapSqlParameterSource p=new MapSqlParameterSource("tenant",tenant).addValue("policy",policyId).addValue("name",requiredValue(displayName,"displayName")).addValue("description",blankToNull(description)).addValue("preferred",preferred).addValue("min",min).addValue("max",max).addValue("deprecated",write(deprecated));
        int n=jdbc.update("""
          insert into a2a_version_policies(tenant_id,policy_id,display_name,description,policy_status,preferred_version,min_accepted_version,max_accepted_version,deprecated_versions_json,compatibility_mode,policy_version,created_by,created_at,updated_at)
          values(:tenant,:policy,:name,:description,'DRAFT',:preferred,:min,:max,cast(:deprecated as jsonb),'EXPLICIT_LEGACY_ALLOWLIST',1,'a2a-version-policy-admin',now(),now())
          on conflict(tenant_id,policy_id) do update set display_name=excluded.display_name,description=excluded.description,preferred_version=excluded.preferred_version,min_accepted_version=excluded.min_accepted_version,max_accepted_version=excluded.max_accepted_version,deprecated_versions_json=excluded.deprecated_versions_json,policy_version=a2a_version_policies.policy_version+1,updated_at=now()
          where a2a_version_policies.policy_status='DRAFT'
          """,p);if(n!=1)throw new IllegalArgumentException("A2A_VERSION_POLICY_NOT_DRAFT");
        jdbc.update("delete from a2a_version_policy_legacy_allowlist where tenant_id=:tenant and policy_id=:policy",new MapSqlParameterSource("tenant",tenant).addValue("policy",policyId));
        for(Map<String,Object> row:legacyAllowlist==null?List.<Map<String,Object>>of():legacyAllowlist)insertAllowlist(tenant,policyId,row);
        return policy(tenant,policyId);
    }

    @Transactional public Map<String,Object> activate(String tenant,String policyId){bind(tenant,"a2a-version-policy-activate");Map<String,Object> candidate=requirePolicy(tenant,policyId);if(!"DRAFT".equals(String.valueOf(candidate.get("policy_status"))))throw new IllegalArgumentException("A2A_VERSION_POLICY_NOT_DRAFT");
        jdbc.update("update a2a_version_policies set policy_status='RETIRED',retired_at=now(),updated_at=now() where tenant_id=:tenant and policy_status='ACTIVE'",new MapSqlParameterSource("tenant",tenant));
        int n=jdbc.update("update a2a_version_policies set policy_status='ACTIVE',activated_at=now(),retired_at=null,updated_at=now() where tenant_id=:tenant and policy_id=:policy and policy_status='DRAFT'",new MapSqlParameterSource("tenant",tenant).addValue("policy",policyId));if(n!=1)throw new IllegalArgumentException("A2A_VERSION_POLICY_ACTIVATION_FAILED");
        jdbc.update("update a2a_peer_provider_links l set status='SUSPENDED',updated_at=now() where l.tenant_id=:tenant and l.status='ACTIVE' and not a2a_interface_current_contract_eligible(l.tenant_id,l.interface_id)",new MapSqlParameterSource("tenant",tenant));return policy(tenant,policyId);}

    @Transactional(readOnly=true) public Evaluation evaluate(String tenant,String peerId,String interfaceId,String protocolVersion){bind(tenant,"a2a-version-policy-evaluate");String v=version(protocolVersion,"protocolVersion");String policyId=null;try{policyId=jdbc.queryForObject("select policy_id from a2a_version_policies where tenant_id=:tenant and policy_status='ACTIVE'",new MapSqlParameterSource("tenant",tenant),String.class);}catch(EmptyResultDataAccessException ignored){}
        String decision=jdbc.queryForObject("select a2a_version_policy_decision(:tenant,:peer,:interface,:version)",new MapSqlParameterSource("tenant",tenant).addValue("peer",peerId).addValue("interface",interfaceId).addValue("version",v),String.class);return new Evaluation(policyId,v,decision,decision!=null&&!"REJECTED".equals(decision));}
    @Transactional(readOnly=true) public Evaluation requireAllowed(String tenant,String peerId,String interfaceId,String protocolVersion){Evaluation e=evaluate(tenant,peerId,interfaceId,protocolVersion);if(!e.allowed())throw new IllegalArgumentException("A2A_PROTOCOL_VERSION_REJECTED:"+e.protocolVersion());return e;}
    @Transactional(readOnly=true) public Evaluation evaluateInterface(String tenant,String interfaceId){bind(tenant,"a2a-version-policy-evaluate");try{Map<String,Object> row=jdbc.queryForMap("select peer_id,protocol_version from a2a_peer_interfaces where tenant_id=:tenant and interface_id=:interface",new MapSqlParameterSource("tenant",tenant).addValue("interface",interfaceId));return evaluate(tenant,String.valueOf(row.get("peer_id")),interfaceId,String.valueOf(row.get("protocol_version")));}catch(EmptyResultDataAccessException ex){throw new IllegalArgumentException("A2A_INTERFACE_NOT_FOUND");}}

    private void insertAllowlist(String tenant,String policyId,Map<String,Object> row){String subject=requiredValue(str(row,"subjectType"),"subjectType").toUpperCase();if(!List.of("PEER","INTERFACE").contains(subject))throw new IllegalArgumentException("A2A_VERSION_LEGACY_ALLOWLIST_SUBJECT_INVALID");String peer=requiredValue(str(row,"peerId"),"peerId"),iface=blankToNull(str(row,"interfaceId")),v=version(str(row,"protocolVersion"),"protocolVersion"),reason=requiredValue(str(row,"reason"),"reason");if("PEER".equals(subject)&&iface!=null||"INTERFACE".equals(subject)&&iface==null)throw new IllegalArgumentException("A2A_VERSION_LEGACY_ALLOWLIST_SUBJECT_INVALID");OffsetDateTime until=parseTime(row.get("validUntil"));if(until!=null&&!until.isAfter(OffsetDateTime.now()))throw new IllegalArgumentException("A2A_VERSION_LEGACY_ALLOWLIST_ALREADY_EXPIRED");
        Integer peerCount=jdbc.queryForObject("select count(*) from a2a_peer_registrations where tenant_id=:tenant and peer_id=:peer",new MapSqlParameterSource("tenant",tenant).addValue("peer",peer),Integer.class);if(peerCount==null||peerCount!=1)throw new IllegalArgumentException("A2A_PEER_NOT_FOUND");
        if(iface!=null){Integer interfaceCount=jdbc.queryForObject("select count(*) from a2a_peer_interfaces where tenant_id=:tenant and interface_id=:interface and peer_id=:peer",new MapSqlParameterSource("tenant",tenant).addValue("interface",iface).addValue("peer",peer),Integer.class);if(interfaceCount==null||interfaceCount!=1)throw new IllegalArgumentException("A2A_VERSION_LEGACY_ALLOWLIST_INTERFACE_PEER_MISMATCH");}
        jdbc.update("insert into a2a_version_policy_legacy_allowlist(tenant_id,policy_id,allowlist_id,subject_type,peer_id,interface_id,protocol_version,reason,valid_until,created_by,created_at) values(:tenant,:policy,:id,:subject,:peer,:interface,:version,:reason,:until,'a2a-version-policy-admin',now())",new MapSqlParameterSource("tenant",tenant).addValue("policy",policyId).addValue("id","a2a-version-allow-"+UUID.randomUUID()).addValue("subject",subject).addValue("peer",peer).addValue("interface",iface).addValue("version",v).addValue("reason",reason).addValue("until",until));}
    private List<Map<String,Object>> allowlist(String tenant,String policy){return jdbc.queryForList("select allowlist_id,subject_type,peer_id,interface_id,protocol_version,reason,valid_until,created_at from a2a_version_policy_legacy_allowlist where tenant_id=:tenant and policy_id=:policy order by created_at,allowlist_id",new MapSqlParameterSource("tenant",tenant).addValue("policy",policy));}
    private Map<String,Object> requirePolicy(String tenant,String policy){try{return jdbc.queryForMap("select policy_id,display_name,description,policy_status,preferred_version,min_accepted_version,max_accepted_version,deprecated_versions_json,compatibility_mode,policy_version,activated_at,retired_at,created_at,updated_at from a2a_version_policies where tenant_id=:tenant and policy_id=:policy",new MapSqlParameterSource("tenant",tenant).addValue("policy",policy));}catch(EmptyResultDataAccessException ex){throw new IllegalArgumentException("A2A_VERSION_POLICY_NOT_FOUND");}}
    private void bind(String tenant,String actor){required(tenant,"tenantId");jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)",String.class,tenant);jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)",String.class,actor);}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception ex){throw new IllegalArgumentException("A2A_VERSION_POLICY_JSON_INVALID",ex);}}
    private static List<String> normalizedVersions(List<String> values){if(values==null)return List.of();List<String> out=new ArrayList<>();for(String v:values){String x=version(v,"deprecatedVersion");if(!out.contains(x))out.add(x);}out.sort(A2AVersionPolicyService::compare);return List.copyOf(out);}
    private static String version(String value,String field){String v=requiredValue(value,field);if(!v.matches("[0-9]{1,6}\\.[0-9]{1,6}"))throw new IllegalArgumentException("A2A_PROTOCOL_VERSION_INVALID:"+field);return v;}
    private static int compare(String a,String b){String[] x=a.split("\\."),y=b.split("\\.");int c=Long.compare(Long.parseLong(x[0]),Long.parseLong(y[0]));return c!=0?c:Long.compare(Long.parseLong(x[1]),Long.parseLong(y[1]));}
    private static String requiredValue(String v,String field){if(v==null||v.isBlank())throw new IllegalArgumentException(field+" is required");return v.trim();}
    private static void required(String v,String field){requiredValue(v,field);}
    private static String blankToNull(String v){return v==null||v.isBlank()?null:v.trim();}
    private static String str(Map<String,Object> m,String k){Object v=m==null?null:m.get(k);return v==null?null:String.valueOf(v);}
    private static OffsetDateTime parseTime(Object v){if(v==null||String.valueOf(v).isBlank())return null;return OffsetDateTime.parse(String.valueOf(v));}
    public record Evaluation(String policyId,String protocolVersion,String decision,boolean allowed){}
}
