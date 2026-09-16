package com.opensocket.aievent.core.capability;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * C0-B7 governed per-interface conformance authority.
 *
 * <p>Human Admin may define/activate a non-weakenable profile and request an automated run,
 * but there is deliberately no API for a human to write PASS/FAIL check evidence or the final
 * conformance outcome. Only this platform runner creates append-only evidence and the interface
 * PASS projection. Runtime eligibility independently revalidates an unexpired PASS run under the
 * currently ACTIVE profile.</p>
 */
@Service
public class A2AInterfaceConformanceService {
    public static final List<String> BASELINE_CHECKS=List.of(
            "AGENT_CARD_INTERFACE_MATCH","PROTOCOL_BINDING","VERSION_POLICY","ENDPOINT_REGION",
            "OUTBOUND_DESTINATION_POLICY","EXTENSIONS","SECURITY_SCHEMES");
    private static final Set<String> KNOWN_CHECKS=Set.copyOf(BASELINE_CHECKS);
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public A2AInterfaceConformanceService(NamedParameterJdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}

    @Transactional(readOnly=true)
    public List<Map<String,Object>> profiles(String tenant){bind(tenant,"a2a-interface-conformance-profile-read");return jdbc.queryForList("select * from a2a_interface_conformance_profiles where tenant_id=:tenant order by created_at desc,profile_id",new MapSqlParameterSource("tenant",tenant));}

    @Transactional(readOnly=true)
    public Map<String,Object> profile(String tenant,String profileId){bind(tenant,"a2a-interface-conformance-profile-read");return requireProfile(tenant,profileId,false);}

    @Transactional
    public Map<String,Object> upsertDraft(String tenant,String profileId,String displayName,String description,List<String> requiredChecks,Integer validForSeconds,Integer maxAgentCardAgeSeconds){
        bind(tenant,"a2a-interface-conformance-profile-write");required(profileId,"profileId");required(displayName,"displayName");
        List<String> checks=normalized(requiredChecks);
        if(!checks.containsAll(BASELINE_CHECKS))throw new IllegalArgumentException("A2A_CONFORMANCE_BASELINE_CHECKS_REQUIRED");
        for(String check:checks)if(!KNOWN_CHECKS.contains(check))throw new IllegalArgumentException("A2A_CONFORMANCE_CHECK_NOT_SUPPORTED: "+check);
        int valid=validForSeconds==null?86400:validForSeconds;int maxAge=maxAgentCardAgeSeconds==null?86400:maxAgentCardAgeSeconds;
        if(valid<60||valid>604800)throw new IllegalArgumentException("A2A_CONFORMANCE_VALIDITY_INVALID");
        if(maxAge<60||maxAge>604800)throw new IllegalArgumentException("A2A_CONFORMANCE_AGENT_CARD_AGE_INVALID");
        int n=jdbc.update("""
          insert into a2a_interface_conformance_profiles(
            tenant_id,profile_id,display_name,description,required_checks_json,valid_for_seconds,max_agent_card_age_seconds,
            profile_status,profile_version,created_by,created_at,updated_at)
          values(:tenant,:id,:name,:description,cast(:checks as jsonb),:valid,:maxAge,'DRAFT',1,'a2a-interface-conformance',now(),now())
          on conflict(tenant_id,profile_id) do update set
            display_name=excluded.display_name,description=excluded.description,required_checks_json=excluded.required_checks_json,
            valid_for_seconds=excluded.valid_for_seconds,max_agent_card_age_seconds=excluded.max_agent_card_age_seconds,
            profile_version=a2a_interface_conformance_profiles.profile_version+1,updated_at=now()
          where a2a_interface_conformance_profiles.profile_status='DRAFT'
          """,new MapSqlParameterSource("tenant",tenant).addValue("id",profileId.trim()).addValue("name",displayName.trim())
                .addValue("description",blankToNull(description)).addValue("checks",write(checks)).addValue("valid",valid).addValue("maxAge",maxAge));
        if(n!=1)throw new IllegalArgumentException("A2A_CONFORMANCE_PROFILE_NOT_EDITABLE");
        return requireProfile(tenant,profileId,false);
    }

    @Transactional
    public Map<String,Object> activateProfile(String tenant,String profileId){
        bind(tenant,"a2a-interface-conformance-profile-activate");Map<String,Object> target=requireProfile(tenant,profileId,true);
        if(!"DRAFT".equals(String.valueOf(target.get("profile_status"))))throw new IllegalArgumentException("A2A_CONFORMANCE_PROFILE_NOT_ACTIVATABLE");
        jdbc.queryForList("select profile_id from a2a_interface_conformance_profiles where tenant_id=:tenant and profile_status='ACTIVE' for update",new MapSqlParameterSource("tenant",tenant),String.class);
        jdbc.update("update a2a_interface_conformance_profiles set profile_status='RETIRED',retired_at=now(),updated_at=now() where tenant_id=:tenant and profile_status='ACTIVE'",new MapSqlParameterSource("tenant",tenant));
        int n=jdbc.update("update a2a_interface_conformance_profiles set profile_status='ACTIVE',activated_at=now(),updated_at=now() where tenant_id=:tenant and profile_id=:id and profile_status='DRAFT'",
                new MapSqlParameterSource("tenant",tenant).addValue("id",profileId));
        if(n!=1)throw new IllegalStateException("A2A_CONFORMANCE_PROFILE_ACTIVATION_CONFLICT");
        // A profile cutover invalidates every previous PASS. Re-certification is explicit and provider links stay suspended until relinked.
        jdbc.update("update a2a_peer_interfaces set conformance_status='UNKNOWN',conformance_profile_id=null,conformance_run_id=null,conformance_valid_until=null,contract_version=contract_version+1,updated_at=now() where tenant_id=:tenant and status<>'RETIRED'",new MapSqlParameterSource("tenant",tenant));
        jdbc.update("update a2a_peer_provider_links set status='SUSPENDED',updated_at=now() where tenant_id=:tenant and status='ACTIVE'",new MapSqlParameterSource("tenant",tenant));
        return requireProfile(tenant,profileId,false);
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> runs(String tenant,String interfaceId){bind(tenant,"a2a-interface-conformance-read");requireInterface(tenant,interfaceId,false);return jdbc.queryForList("select * from a2a_interface_conformance_runs where tenant_id=:tenant and interface_id=:interface order by started_at desc,run_id desc",params(tenant,interfaceId));}

    @Transactional(readOnly=true)
    public Map<String,Object> explain(String tenant,String interfaceId){
        bind(tenant,"a2a-interface-conformance-read");Map<String,Object> i=requireInterface(tenant,interfaceId,false);
        String effective=jdbc.queryForObject("select a2a_interface_conformance_effective_status(:tenant,:interface)",params(tenant,interfaceId),String.class);
        List<Map<String,Object>> recent=jdbc.queryForList("select * from a2a_interface_conformance_runs where tenant_id=:tenant and interface_id=:interface order by started_at desc,run_id desc limit 10",params(tenant,interfaceId));
        Map<String,Object> out=new LinkedHashMap<>(i);out.put("effectiveConformanceStatus",effective);out.put("currentConformancePass","PASS".equals(effective));out.put("recentRuns",recent);out.put("adminMayWritePass",false);out.put("evidenceWriter","PLATFORM_AUTOMATED");return out;
    }

    /** Executes the current F0 baseline deterministically from server-side evidence; there is no Human API to supply check results. */
    @Transactional
    public Map<String,Object> run(String tenant,String interfaceId){
        bind(tenant,"a2a-interface-conformance-runner");Map<String,Object> i=requireInterface(tenant,interfaceId,true);
        if("RETIRED".equals(String.valueOf(i.get("status"))))throw new IllegalArgumentException("A2A_INTERFACE_NOT_CONFORMANCE_TESTABLE");
        Map<String,Object> p=requireActiveProfile(tenant);
        List<String> checks=readStrings(p.get("required_checks_json"));
        if(!checks.containsAll(BASELINE_CHECKS))throw new IllegalStateException("A2A_ACTIVE_CONFORMANCE_PROFILE_BASELINE_INCOMPLETE");
        String runId="a2a-conf-run-"+UUID.randomUUID();
        int valid=((Number)p.get("valid_for_seconds")).intValue();int maxAge=((Number)p.get("max_agent_card_age_seconds")).intValue();
        jdbc.update("""
          insert into a2a_interface_conformance_runs(
            tenant_id,run_id,interface_id,peer_id,profile_id,profile_version,required_checks_json,valid_for_seconds,max_agent_card_age_seconds,
            run_status,outcome,started_at,summary_json,created_by)
          values(:tenant,:run,:interface,:peer,:profile,:profileVersion,cast(:checks as jsonb),:valid,:maxAge,'RUNNING','UNKNOWN',now(),'{}'::jsonb,'PLATFORM_AUTOMATED')
          """,new MapSqlParameterSource("tenant",tenant).addValue("run",runId).addValue("interface",interfaceId)
                .addValue("peer",String.valueOf(i.get("peer_id"))).addValue("profile",String.valueOf(p.get("profile_id")))
                .addValue("profileVersion",((Number)p.get("profile_version")).longValue()).addValue("checks",write(checks)).addValue("valid",valid).addValue("maxAge",maxAge));
        List<Map<String,Object>> results=new ArrayList<>();boolean pass=true;
        for(String check:checks){CheckResult r=evaluate(tenant,i,check,maxAge);persistEvidence(tenant,runId,interfaceId,check,r);results.add(r.asMap());pass&=r.pass();}
        String outcome=pass?"PASS":"FAIL";OffsetDateTime completed=OffsetDateTime.now(ZoneOffset.UTC);OffsetDateTime validUntil=pass?completed.plusSeconds(valid):null;
        Map<String,Object> summary=Map.of("outcome",outcome,"checks",List.copyOf(results),"runner","PLATFORM_AUTOMATED");
        int completedRows=jdbc.update("""
          update a2a_interface_conformance_runs set run_status='COMPLETED',outcome=:outcome,completed_at=:completed,
            valid_until=:validUntil,summary_json=cast(:summary as jsonb)
           where tenant_id=:tenant and run_id=:run and run_status='RUNNING'
          """,new MapSqlParameterSource("tenant",tenant).addValue("run",runId).addValue("outcome",outcome).addValue("completed",completed)
                .addValue("validUntil",validUntil).addValue("summary",write(summary)));
        if(completedRows!=1)throw new IllegalStateException("A2A_CONFORMANCE_RUN_COMPLETE_CONFLICT");
        int projected=jdbc.update("""
          update a2a_peer_interfaces set conformance_status=:outcome,conformance_profile_id=:profile,
            conformance_run_id=:run,conformance_valid_until=:validUntil,contract_version=contract_version+1,updated_at=now()
           where tenant_id=:tenant and interface_id=:interface
          """,new MapSqlParameterSource("tenant",tenant).addValue("interface",interfaceId).addValue("outcome",outcome)
                .addValue("profile",String.valueOf(p.get("profile_id"))).addValue("run",runId).addValue("validUntil",validUntil));
        if(projected!=1)throw new IllegalStateException("A2A_CONFORMANCE_INTERFACE_PROJECTION_FAILED");
        if(!pass)jdbc.update("update a2a_peer_provider_links set status='SUSPENDED',updated_at=now() where tenant_id=:tenant and interface_id=:interface and status='ACTIVE'",params(tenant,interfaceId));
        return explainWithinTransaction(tenant,interfaceId,runId);
    }

    private CheckResult evaluate(String tenant,Map<String,Object> i,String check,int maxAge){
        String peer=String.valueOf(i.get("peer_id"));String interfaceId=String.valueOf(i.get("interface_id"));
        return switch(check){
            case "PROTOCOL_BINDING" -> result(check,"HTTP+JSON".equals(String.valueOf(i.get("protocol_binding"))),Map.of("protocolBinding","HTTP+JSON"),Map.of("protocolBinding",String.valueOf(i.get("protocol_binding"))));
            case "VERSION_POLICY" -> {boolean ok=Boolean.TRUE.equals(jdbc.queryForObject("select a2a_version_policy_allowed(:tenant,:peer,:interface,:version)",params(tenant,interfaceId).addValue("peer",peer).addValue("version",String.valueOf(i.get("protocol_version"))),Boolean.class));yield result(check,ok,Map.of("decision","SUPPORTED_OR_DEPRECATED_ALLOWED"),Map.of("protocolVersion",String.valueOf(i.get("protocol_version")),"allowed",ok));}
            case "ENDPOINT_REGION" -> {boolean ok=!blank(i.get("endpoint_region"));yield result(check,ok,Map.of("endpointRegion","REQUIRED"),Map.of("endpointRegion",safe(i.get("endpoint_region"))));}
            case "OUTBOUND_DESTINATION_POLICY" -> {String ref=safeString(i.get("outbound_destination_policy_ref"));boolean ok=!ref.isBlank();yield result(check,ok,Map.of("outboundDestinationPolicyRef","GOVERNED_ACTIVE_RUNTIME_READY"),Map.of("outboundDestinationPolicyRef",ref));}
            case "EXTENSIONS" -> {boolean ok=Boolean.TRUE.equals(jdbc.queryForObject("select supported_extensions_json @> required_extensions_json from a2a_peer_interfaces where tenant_id=:tenant and interface_id=:interface",params(tenant,interfaceId),Boolean.class));yield result(check,ok,Map.of("requiredExtensionsSubsetOfSupported",true),Map.of("compatible",ok));}
            case "SECURITY_SCHEMES" -> {Integer n=jdbc.queryForObject("select jsonb_array_length(security_scheme_refs_json) from a2a_peer_interfaces where tenant_id=:tenant and interface_id=:interface",params(tenant,interfaceId),Integer.class);boolean ok=n!=null&&n>0;yield result(check,ok,Map.of("securitySchemeRefs","NON_EMPTY"),Map.of("count",n==null?0:n));}
            case "AGENT_CARD_INTERFACE_MATCH" -> cardMatch(tenant,peer,i,maxAge);
            default -> result(check,false,Map.of("supported",true),Map.of("error","UNSUPPORTED_CHECK_CODE"));
        };
    }

    private CheckResult cardMatch(String tenant,String peer,Map<String,Object> i,int maxAge){
        List<Map<String,Object>> rows=jdbc.queryForList("""
          select s.snapshot_id,s.fetched_at,
                 s.fetched_at > now() - (:maxAge * interval '1 second') as fresh,
                 exists(select 1 from jsonb_array_elements(coalesce(s.card_json->'supportedInterfaces','[]'::jsonb)) x
                         where x->>'url'=:url and x->>'protocolBinding'=:binding and x->>'protocolVersion'=:version) as matched
            from a2a_agent_card_snapshots s
           where s.tenant_id=:tenant and s.peer_id=:peer
           order by s.fetched_at desc,s.snapshot_id desc limit 1
          """,new MapSqlParameterSource("tenant",tenant).addValue("peer",peer).addValue("maxAge",maxAge)
                .addValue("url",String.valueOf(i.get("url"))).addValue("binding",String.valueOf(i.get("protocol_binding"))).addValue("version",String.valueOf(i.get("protocol_version"))));
        if(rows.isEmpty())return result("AGENT_CARD_INTERFACE_MATCH",false,Map.of("freshAgentCard",true,"interfaceMatch",true),Map.of("snapshot","MISSING"));
        Map<String,Object> row=rows.getFirst();boolean fresh=Boolean.TRUE.equals(row.get("fresh"));boolean matched=Boolean.TRUE.equals(row.get("matched"));
        return result("AGENT_CARD_INTERFACE_MATCH",fresh&&matched,Map.of("freshAgentCard",true,"interfaceMatch",true),Map.of("snapshotId",safe(row.get("snapshot_id")),"fresh",fresh,"matched",matched));
    }

    private void persistEvidence(String tenant,String runId,String interfaceId,String check,CheckResult r){
        String expected=write(r.expected());String observed=write(r.observed());String evidenceId="a2a-conf-evidence-"+UUID.randomUUID();String digest=sha(runId+"|"+check+"|"+r.status()+"|"+expected+"|"+observed);
        jdbc.update("""
          insert into a2a_interface_conformance_evidence(
            tenant_id,evidence_id,run_id,interface_id,check_code,check_status,expected_json,observed_json,evidence_ref,evidence_digest,evidence_source,created_at)
          values(:tenant,:id,:run,:interface,:check,:status,cast(:expected as jsonb),cast(:observed as jsonb),:ref,:digest,'PLATFORM_AUTOMATED',now())
          """,new MapSqlParameterSource("tenant",tenant).addValue("id",evidenceId).addValue("run",runId).addValue("interface",interfaceId)
                .addValue("check",check).addValue("status",r.status()).addValue("expected",expected).addValue("observed",observed)
                .addValue("ref","platform://c0-b7/"+runId+"/"+check).addValue("digest",digest));
    }

    private Map<String,Object> explainWithinTransaction(String tenant,String interfaceId,String runId){
        Map<String,Object> out=new LinkedHashMap<>(requireInterface(tenant,interfaceId,false));
        out.put("effectiveConformanceStatus",jdbc.queryForObject("select a2a_interface_conformance_effective_status(:tenant,:interface)",params(tenant,interfaceId),String.class));
        out.put("run",jdbc.queryForMap("select * from a2a_interface_conformance_runs where tenant_id=:tenant and run_id=:run",new MapSqlParameterSource("tenant",tenant).addValue("run",runId)));
        out.put("evidence",jdbc.queryForList("select * from a2a_interface_conformance_evidence where tenant_id=:tenant and run_id=:run order by check_code",new MapSqlParameterSource("tenant",tenant).addValue("run",runId)));
        out.put("adminMayWritePass",false);return out;
    }

    private Map<String,Object> requireActiveProfile(String tenant){try{return jdbc.queryForMap("select * from a2a_interface_conformance_profiles where tenant_id=:tenant and profile_status='ACTIVE'",new MapSqlParameterSource("tenant",tenant));}catch(EmptyResultDataAccessException ex){throw new IllegalArgumentException("A2A_ACTIVE_CONFORMANCE_PROFILE_REQUIRED");}}
    private Map<String,Object> requireProfile(String tenant,String id,boolean lock){try{return jdbc.queryForMap("select * from a2a_interface_conformance_profiles where tenant_id=:tenant and profile_id=:id"+(lock?" for update":""),new MapSqlParameterSource("tenant",tenant).addValue("id",id));}catch(EmptyResultDataAccessException ex){throw new IllegalArgumentException("A2A_CONFORMANCE_PROFILE_NOT_FOUND");}}
    private Map<String,Object> requireInterface(String tenant,String id,boolean lock){try{return jdbc.queryForMap("select * from a2a_peer_interfaces where tenant_id=:tenant and interface_id=:interface"+(lock?" for update":""),params(tenant,id));}catch(EmptyResultDataAccessException ex){throw new IllegalArgumentException("A2A_INTERFACE_NOT_FOUND");}}
    private void bind(String tenant,String actor){required(tenant,"tenantId");jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)",String.class,tenant);jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)",String.class,actor);}
    private static MapSqlParameterSource params(String tenant,String interfaceId){return new MapSqlParameterSource("tenant",tenant).addValue("interface",interfaceId);}
    private List<String> readStrings(Object v){try{Object decoded=json.readValue(String.valueOf(v),List.class);if(!(decoded instanceof List<?> list))return List.of();List<String> out=new ArrayList<>();for(Object x:list)if(x!=null)out.add(String.valueOf(x));return List.copyOf(out);}catch(Exception ex){throw new IllegalStateException("A2A_CONFORMANCE_PROFILE_CHECKS_INVALID",ex);}}
    private String write(Object v){try{return json.writeValueAsString(v);}catch(Exception ex){throw new IllegalArgumentException("A2A_CONFORMANCE_JSON_INVALID",ex);}}
    private static List<String> normalized(List<String> values){LinkedHashSet<String> out=new LinkedHashSet<>();if(values!=null)for(String v:values)if(v!=null&&!v.isBlank())out.add(v.trim().toUpperCase());return out.stream().sorted().toList();}
    private static CheckResult result(String code,boolean pass,Map<String,Object> expected,Map<String,Object> observed){return new CheckResult(code,pass?"PASS":"FAIL",Map.copyOf(expected),Map.copyOf(observed));}
    private static String sha(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException(ex);}}
    private static boolean blank(Object v){return v==null||String.valueOf(v).isBlank();}
    private static Object safe(Object v){return v==null?"":v;}
    private static String safeString(Object v){return v==null?"":String.valueOf(v);}
    private static String blankToNull(String v){return v==null||v.isBlank()?null:v.trim();}
    private static void required(String v,String n){if(v==null||v.isBlank())throw new IllegalArgumentException(n+" is required");}
    private record CheckResult(String code,String status,Map<String,Object> expected,Map<String,Object> observed){boolean pass(){return "PASS".equals(status);}Map<String,Object> asMap(){return Map.of("checkCode",code,"status",status,"expected",expected,"observed",observed);}}
}
