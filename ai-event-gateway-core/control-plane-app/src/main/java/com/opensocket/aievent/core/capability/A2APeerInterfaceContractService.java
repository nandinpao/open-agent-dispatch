package com.opensocket.aievent.core.capability;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** C0-B4 interface-scoped contract authority. Interface facts are eligibility inputs, never residency authorization. */
@Service
public class A2APeerInterfaceContractService {
    public static final String CURRENT_OUTBOUND_POLICY = "EXTERNAL_HTTP_DEFAULT";
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public A2APeerInterfaceContractService(NamedParameterJdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}

    @Transactional(readOnly=true)
    public Map<String,Object> contract(String tenant,String interfaceId){
        bind(tenant,"a2a-peer-interface-contract-read");
        return require(tenant,interfaceId);
    }

    @Transactional
    public Map<String,Object> configure(String tenant,String interfaceId,String endpointRegion,List<String> securitySchemeRefs,
                                        List<String> requiredExtensions,List<String> supportedExtensions,String outboundPolicyRef,Integer priority){
        bind(tenant,"a2a-peer-interface-contract-write");
        String region=required(endpointRegion,"endpointRegion");
        String policy=required(outboundPolicyRef,"outboundDestinationPolicyRef");
        int p=priority==null?100:priority;
        if(p<0||p>10000)throw new IllegalArgumentException("A2A_INTERFACE_PRIORITY_INVALID");
        List<String> security=normalized(securitySchemeRefs);List<String> required=normalized(requiredExtensions);List<String> supported=normalized(supportedExtensions);
        if(!supported.containsAll(required))throw new IllegalArgumentException("A2A_REQUIRED_EXTENSION_NOT_SUPPORTED");
        int n=jdbc.update("""
          update a2a_peer_interfaces set endpoint_region=:region,security_scheme_refs_json=cast(:security as jsonb),
            required_extensions_json=cast(:requiredExt as jsonb),supported_extensions_json=cast(:supportedExt as jsonb),
            outbound_destination_policy_ref=:policy,priority=:priority,contract_version=contract_version+1,updated_at=now()
           where tenant_id=:tenant and interface_id=:interface and status<>'RETIRED'
          """,new MapSqlParameterSource("tenant",tenant).addValue("interface",interfaceId).addValue("region",region)
                .addValue("security",write(security)).addValue("requiredExt",write(required)).addValue("supportedExt",write(supported))
                .addValue("policy",policy).addValue("priority",p));
        if(n!=1)throw new IllegalArgumentException("A2A_INTERFACE_NOT_CONFIGURABLE");
        return require(tenant,interfaceId);
    }

    @Transactional
    public Map<String,Object> operationalState(String tenant,String interfaceId,String healthStatus,String conformanceStatus,String circuitState){
        bind(tenant,"a2a-peer-interface-operational-state");
        String health=oneOf(healthStatus,"healthStatus",List.of("UNKNOWN","HEALTHY","DEGRADED","UNHEALTHY"));
        if(conformanceStatus!=null&&!conformanceStatus.isBlank())throw new IllegalArgumentException("A2A_INTERFACE_CONFORMANCE_WRITE_REQUIRES_C0_B7");
        String circuit=oneOf(circuitState,"circuitState",List.of("CLOSED","OPEN","HALF_OPEN"));
        int n=jdbc.update("""
          update a2a_peer_interfaces set health_status=:health,circuit_state=:circuit,
            contract_version=contract_version+1,updated_at=now()
           where tenant_id=:tenant and interface_id=:interface and status<>'RETIRED'
          """,new MapSqlParameterSource("tenant",tenant).addValue("interface",interfaceId).addValue("health",health).addValue("circuit",circuit));
        if(n!=1)throw new IllegalArgumentException("A2A_INTERFACE_NOT_CONFIGURABLE");
        String effectiveConformance=jdbc.queryForObject("select a2a_interface_conformance_effective_status(:tenant,:interface)",
                new MapSqlParameterSource("tenant",tenant).addValue("interface",interfaceId),String.class);
        if(!"CLOSED".equals(circuit)||!"HEALTHY".equals(health)||!"PASS".equals(effectiveConformance)){
            jdbc.update("update a2a_peer_provider_links set status='SUSPENDED',updated_at=now() where tenant_id=:tenant and interface_id=:interface and status='ACTIVE'",
                    new MapSqlParameterSource("tenant",tenant).addValue("interface",interfaceId));
        }
        return require(tenant,interfaceId);
    }

    @Transactional(readOnly=true)
    public Map<String,Object> explain(String tenant,String interfaceId){
        bind(tenant,"a2a-peer-interface-contract-read");Map<String,Object> row=require(tenant,interfaceId);boolean eligible=Boolean.TRUE.equals(jdbc.queryForObject(
                "select a2a_interface_current_contract_eligible(:tenant,:interface)",new MapSqlParameterSource("tenant",tenant).addValue("interface",interfaceId),Boolean.class));
        List<String> reasons=new ArrayList<>();
        if(blank(row.get("endpoint_region")))reasons.add("ENDPOINT_REGION_REQUIRED");
        if(row.get("outbound_destination_policy_ref")==null||String.valueOf(row.get("outbound_destination_policy_ref")).isBlank())reasons.add("OUTBOUND_DESTINATION_POLICY_REQUIRED");
        if(!"HEALTHY".equals(String.valueOf(row.get("health_status"))))reasons.add("INTERFACE_HEALTH_NOT_HEALTHY");
        String effectiveConformance=jdbc.queryForObject("select a2a_interface_conformance_effective_status(:tenant,:interface)",
                new MapSqlParameterSource("tenant",tenant).addValue("interface",interfaceId),String.class);
        if(!"PASS".equals(effectiveConformance))reasons.add("INTERFACE_CONFORMANCE_NOT_PASS:"+effectiveConformance);
        if(!"CLOSED".equals(String.valueOf(row.get("circuit_state"))))reasons.add("INTERFACE_CIRCUIT_NOT_CLOSED");
        Map<String,Object> out=new LinkedHashMap<>(row);out.put("effectiveConformanceStatus",effectiveConformance);out.put("currentContractEligible",eligible);out.put("eligibilityReasons",List.copyOf(reasons));out.put("residencyAuthorizationDecidedHere",false);return out;
    }

    private Map<String,Object> require(String tenant,String interfaceId){try{return jdbc.queryForMap("""
      select interface_id,peer_id,url,protocol_binding,protocol_version,interface_tenant,streaming_supported,push_notifications_supported,status,trust_status,
             endpoint_region,security_scheme_refs_json,required_extensions_json,supported_extensions_json,outbound_destination_policy_ref,priority,
             health_status,conformance_status,circuit_state,conformance_profile_id,conformance_run_id,conformance_valid_until,contract_version,created_at,updated_at
        from a2a_peer_interfaces where tenant_id=:tenant and interface_id=:interface
      """,new MapSqlParameterSource("tenant",tenant).addValue("interface",interfaceId));}catch(EmptyResultDataAccessException ex){throw new IllegalArgumentException("A2A_INTERFACE_NOT_FOUND");}}
    private void bind(String tenant,String actor){jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)",String.class,tenant);jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)",String.class,actor);}
    private String write(Object v){try{return json.writeValueAsString(v);}catch(Exception ex){throw new IllegalArgumentException("A2A_INTERFACE_CONTRACT_JSON_FAILED",ex);}}
    private static List<String> normalized(List<String> values){if(values==null)return List.of();return values.stream().filter(v->v!=null&&!v.isBlank()).map(String::trim).distinct().sorted().toList();}
    private static String required(String value,String name){if(value==null||value.isBlank())throw new IllegalArgumentException(name+" is required");return value.trim();}
    private static String oneOf(String value,String name,List<String> allowed){String v=required(value,name).toUpperCase();if(!allowed.contains(v))throw new IllegalArgumentException("A2A_INTERFACE_"+name.toUpperCase()+"_INVALID");return v;}
    private static boolean blank(Object v){return v==null||String.valueOf(v).isBlank();}
}
