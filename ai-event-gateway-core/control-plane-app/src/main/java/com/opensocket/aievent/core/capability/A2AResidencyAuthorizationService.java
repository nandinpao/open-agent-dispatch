package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** PC-S5 residency hard-filter authority. Region facts remain owned by Interface and verified Processing Attestation. */
@Service
public class A2AResidencyAuthorizationService {
    private final NamedParameterJdbcTemplate jdbc; private final ObjectMapper json;
    public A2AResidencyAuthorizationService(NamedParameterJdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}

    @Transactional
    public Decision authorizeAdmission(String tenant,String interfaceId){bind(tenant,"a2a-residency-admission");Decision d=evaluateBound(tenant,interfaceId,true);if(!d.allowed())throw denied(d);return d;}

    @Transactional(readOnly=true)
    public Decision current(String tenant,String interfaceId){bind(tenant,"a2a-residency-runtime");return evaluateBound(tenant,interfaceId,false);}

    @Transactional(readOnly=true)
    public void requireCurrentAllowed(String tenant,String interfaceId){Decision d=current(tenant,interfaceId);if(!d.allowed())throw denied(d);}

    @Transactional
    public void propagateIfInvalid(String tenant,String interfaceId,String reason){bind(tenant,"a2a-revocation-propagation");Decision d=evaluateBound(tenant,interfaceId,false);if(!d.allowed())jdbc.queryForObject("select pc_s5_revoke_a2a_authority(:tenant,:interface,:reason)",new MapSqlParameterSource("tenant",tenant).addValue("interface",interfaceId).addValue("reason",reason),Object.class);}

    @Transactional(readOnly=true)
    public List<Map<String,Object>> policies(String tenant){bind(tenant,"a2a-residency-policy-read");return jdbc.queryForList("select * from a2a_residency_policies where tenant_id=:tenant order by case policy_status when 'ACTIVE' then 0 when 'DRAFT' then 1 else 2 end,updated_at desc,policy_id",new MapSqlParameterSource("tenant",tenant));}

    @Transactional
    public Map<String,Object> upsertDraft(String tenant,String policyId,String displayName,List<String> endpointRegions,List<String> processingRegions,List<String> storageRegions){
        bind(tenant,"a2a-residency-policy-write");String id=required(policyId,"policyId"),name=required(displayName,"displayName");List<String> ep=regions(endpointRegions),pr=regions(processingRegions),sr=regions(storageRegions);if(ep.isEmpty()||pr.isEmpty()||sr.isEmpty())throw new IllegalArgumentException("A2A_RESIDENCY_ALLOWED_REGIONS_REQUIRED");
        int n=jdbc.update("""
          insert into a2a_residency_policies(tenant_id,policy_id,display_name,allowed_endpoint_regions_json,allowed_processing_regions_json,allowed_storage_regions_json,unknown_fact_policy,emergency_kill_switch,policy_status,policy_version,created_by,created_at,updated_at)
          values(:tenant,:policy,:name,cast(:endpoint as jsonb),cast(:processing as jsonb),cast(:storage as jsonb),'DENY',false,'DRAFT',1,'a2a-residency-admin',now(),now())
          on conflict(tenant_id,policy_id) do update set display_name=excluded.display_name,allowed_endpoint_regions_json=excluded.allowed_endpoint_regions_json,allowed_processing_regions_json=excluded.allowed_processing_regions_json,allowed_storage_regions_json=excluded.allowed_storage_regions_json,updated_at=now()
          where a2a_residency_policies.policy_status='DRAFT'
          """,new MapSqlParameterSource("tenant",tenant).addValue("policy",id).addValue("name",name).addValue("endpoint",write(ep)).addValue("processing",write(pr)).addValue("storage",write(sr)));
        if(n!=1)throw new IllegalArgumentException("A2A_RESIDENCY_POLICY_NOT_DRAFT");return policy(tenant,id);
    }

    @Transactional public Map<String,Object> activate(String tenant,String policyId){bind(tenant,"a2a-residency-policy-activate");String id=required(policyId,"policyId");String status=jdbc.queryForObject("select policy_status from a2a_residency_policies where tenant_id=:tenant and policy_id=:policy",new MapSqlParameterSource("tenant",tenant).addValue("policy",id),String.class);if(!"DRAFT".equals(status))throw new IllegalArgumentException("A2A_RESIDENCY_POLICY_NOT_ACTIVATABLE");jdbc.update("update a2a_residency_policies set policy_status='RETIRED',retired_at=now(),updated_at=now() where tenant_id=:tenant and policy_status='ACTIVE'",new MapSqlParameterSource("tenant",tenant));int n=jdbc.update("update a2a_residency_policies set policy_status='ACTIVE',activated_at=now(),updated_at=now() where tenant_id=:tenant and policy_id=:policy and policy_status='DRAFT'",new MapSqlParameterSource("tenant",tenant).addValue("policy",id));if(n!=1)throw new IllegalArgumentException("A2A_RESIDENCY_POLICY_NOT_ACTIVATABLE");return policy(tenant,id);}
    @Transactional public Map<String,Object> retire(String tenant,String policyId){bind(tenant,"a2a-residency-policy-retire");String id=required(policyId,"policyId");jdbc.update("update a2a_residency_policies set policy_status='RETIRED',retired_at=now(),updated_at=now() where tenant_id=:tenant and policy_id=:policy and policy_status<>'RETIRED'",new MapSqlParameterSource("tenant",tenant).addValue("policy",id));return policy(tenant,id);}
    @Transactional public Map<String,Object> setEmergencyKillSwitch(String tenant,String policyId,boolean enabled){bind(tenant,"a2a-residency-kill-switch");String id=required(policyId,"policyId");int n=jdbc.update("update a2a_residency_policies set emergency_kill_switch=:enabled,updated_at=now() where tenant_id=:tenant and policy_id=:policy and policy_status='ACTIVE'",new MapSqlParameterSource("tenant",tenant).addValue("policy",id).addValue("enabled",enabled));if(n!=1)throw new IllegalArgumentException("A2A_RESIDENCY_ACTIVE_POLICY_REQUIRED");return policy(tenant,id);}
    @Transactional(readOnly=true) public Map<String,Object> policy(String tenant,String policyId){bind(tenant,"a2a-residency-policy-read");try{return jdbc.queryForMap("select * from a2a_residency_policies where tenant_id=:tenant and policy_id=:policy",new MapSqlParameterSource("tenant",tenant).addValue("policy",policyId));}catch(EmptyResultDataAccessException ex){throw new IllegalArgumentException("A2A_RESIDENCY_POLICY_NOT_FOUND");}}

    private Decision evaluateBound(String tenant,String interfaceId,boolean persist){
        Map<String,Object> row;
        try{row=jdbc.queryForMap("""
          select i.peer_id,i.endpoint_region,p.policy_id,p.policy_version,p.emergency_kill_switch,
                 p.allowed_endpoint_regions_json::text as allowed_endpoint_regions_json,
                 p.allowed_processing_regions_json::text as allowed_processing_regions_json,
                 p.allowed_storage_regions_json::text as allowed_storage_regions_json,
                 a.attestation_id,a.processing_region,a.storage_region
            from a2a_peer_interfaces i
            left join a2a_residency_policies p on p.tenant_id=i.tenant_id and p.policy_status='ACTIVE'
            left join lateral(
              select attestation_id,processing_region,storage_region from a2a_peer_processing_attestations a
               where a.tenant_id=i.tenant_id and a.peer_id=i.peer_id and a.attestation_status='VERIFIED'
                 and a.valid_from<=now() and (a.valid_until is null or a.valid_until>now())
               order by a.verified_at desc nulls last,a.attestation_id limit 1
            ) a on true
           where i.tenant_id=:tenant and i.interface_id=:interface
          """,params(tenant,interfaceId));}catch(EmptyResultDataAccessException ex){return decision(tenant,interfaceId,null,null,null,null,null,null,"UNKNOWN",List.of("A2A_INTERFACE_NOT_FOUND"),persist);}
        String peer=str(row,"peer_id"),policy=str(row,"policy_id"),endpoint=str(row,"endpoint_region"),attestation=str(row,"attestation_id"),processing=str(row,"processing_region"),storage=str(row,"storage_region");Long version=num(row.get("policy_version"));List<String> reasons=new ArrayList<>();String result;
        if(blank(policy))reasons.add("A2A_RESIDENCY_ACTIVE_POLICY_REQUIRED");
        if(Boolean.TRUE.equals(row.get("emergency_kill_switch")))reasons.add("A2A_RESIDENCY_KILL_SWITCH");
        if(blank(endpoint))reasons.add("A2A_ENDPOINT_REGION_REQUIRED");
        if(blank(attestation)){reasons.add("A2A_PROCESSING_ATTESTATION_REQUIRED");result="UNKNOWN";}
        else result="DENIED";
        if(reasons.isEmpty()){
            if(!read(row,"allowed_endpoint_regions_json").contains(endpoint))reasons.add("A2A_ENDPOINT_REGION_DENIED");
            if(!read(row,"allowed_processing_regions_json").contains(processing))reasons.add("A2A_PROCESSING_REGION_DENIED");
            if(!read(row,"allowed_storage_regions_json").contains(storage))reasons.add("A2A_STORAGE_REGION_DENIED");
            result=reasons.isEmpty()?"ALLOWED":"DENIED";
        }else if(!reasons.contains("A2A_PROCESSING_ATTESTATION_REQUIRED")&&reasons.stream().anyMatch(r->r.endsWith("REQUIRED")))result="UNKNOWN";
        return decision(tenant,interfaceId,peer,policy,version,attestation,new Regions(endpoint,processing,storage),row,result,reasons,persist);
    }

    private Decision decision(String tenant,String interfaceId,String peer,String policy,Long version,String attestation,Regions regions,Map<String,Object> ignored,String result,List<String> reasons,boolean persist){
        String id="a2a-residency-"+UUID.randomUUID();Regions r=regions==null?new Regions(null,null,null):regions;
        if(persist)jdbc.update("""
          insert into a2a_residency_authorization_decisions(tenant_id,decision_id,peer_id,interface_id,policy_id,policy_version,processing_attestation_id,endpoint_region,processing_region,storage_region,decision,reason_codes_json,decided_at)
          values(:tenant,:id,:peer,:interface,:policy,:version,:attestation,:endpoint,:processing,:storage,:decision,cast(:reasons as jsonb),:at)
          """,new MapSqlParameterSource("tenant",tenant).addValue("id",id).addValue("peer",peer==null?"UNKNOWN":peer).addValue("interface",interfaceId).addValue("policy",policy).addValue("version",version).addValue("attestation",attestation).addValue("endpoint",r.endpoint()).addValue("processing",r.processing()).addValue("storage",r.storage()).addValue("decision",result).addValue("reasons",write(reasons)).addValue("at",OffsetDateTime.now()));
        return new Decision(id,policy,version,attestation,r.endpoint(),r.processing(),r.storage(),result,List.copyOf(reasons));
    }
    private Decision decision(String tenant,String interfaceId,String peer,String policy,Long version,String attestation,String endpoint,String result,List<String> reasons,boolean persist){return decision(tenant,interfaceId,peer,policy,version,attestation,new Regions(endpoint,null,null),Map.of(),result,reasons,persist);}
    private IllegalArgumentException denied(Decision d){return new IllegalArgumentException(("UNKNOWN".equals(d.decision())?"A2A_RESIDENCY_UNKNOWN":"A2A_RESIDENCY_DENIED")+":"+String.join(",",d.reasonCodes()));}
    private List<String> read(Map<String,Object> row,String key){try{return json.readValue(str(row,key),new TypeReference<List<String>>(){});}catch(Exception ex){return List.of();}}
    private String write(Object v){try{return json.writeValueAsString(v);}catch(Exception ex){throw new IllegalStateException("A2A_RESIDENCY_EVIDENCE_JSON_FAILED",ex);}}
    private void bind(String tenant,String actor){jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)",String.class,tenant);jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)",String.class,actor);}
    private static MapSqlParameterSource params(String t,String i){return new MapSqlParameterSource("tenant",t).addValue("interface",i);} private static boolean blank(String v){return v==null||v.isBlank();} private static String str(Map<String,Object> r,String k){Object v=r.get(k);return v==null?null:String.valueOf(v);} private static Long num(Object v){return v instanceof Number n?n.longValue():null;} private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();} private static List<String> regions(List<String> v){if(v==null)return List.of();return v.stream().filter(x->x!=null&&!x.isBlank()).map(x->x.trim().toUpperCase(java.util.Locale.ROOT)).distinct().sorted().toList();}
    private record Regions(String endpoint,String processing,String storage){}
    public record Decision(String decisionId,String policyId,Long policyVersion,String processingAttestationId,String endpointRegion,String processingRegion,String storageRegion,String decision,List<String> reasonCodes){public boolean allowed(){return "ALLOWED".equals(decision);}}
}
