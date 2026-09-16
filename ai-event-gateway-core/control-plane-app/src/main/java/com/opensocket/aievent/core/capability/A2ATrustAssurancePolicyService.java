package com.opensocket.aievent.core.capability;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * C0-B2 TrustAssurancePolicy authority. Effective PeerTrustEvidence is evaluated
 * against versioned required-evidence sets to compute assurance grants. Admins
 * manage evidence and policy inputs; they never assign grants directly.
 */
@Service
public class A2ATrustAssurancePolicyService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public A2ATrustAssurancePolicyService(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public List<Map<String,Object>> policies(String tenant) {
        bind(tenant,"a2a-trust-assurance-policy-read");
        return jdbc.queryForList("""
            select p.tenant_id,p.policy_id,p.display_name,p.description,p.policy_status,p.policy_version,
                   p.created_by,p.created_at,p.activated_at,p.retired_at,p.updated_at,
                   coalesce((select count(*) from a2a_trust_assurance_rules r where r.tenant_id=p.tenant_id and r.policy_id=p.policy_id),0) as rule_count
              from a2a_trust_assurance_policies p
             where p.tenant_id=:tenant
             order by case p.policy_status when 'ACTIVE' then 0 when 'DRAFT' then 1 else 2 end,p.updated_at desc,p.policy_id
            """, new MapSqlParameterSource("tenant",tenant));
    }

    @Transactional(readOnly = true)
    public Map<String,Object> policy(String tenant,String policyId) {
        bind(tenant,"a2a-trust-assurance-policy-read");
        return policyView(tenant,policyId);
    }

    @Transactional
    public Map<String,Object> upsertDraft(
            String tenant,String policyId,String displayName,String description,List<Map<String,Object>> rules) {
        bind(tenant,"a2a-trust-assurance-policy-write");
        required(policyId,"policyId"); required(displayName,"displayName");
        Integer existing=jdbc.queryForObject("select count(*) from a2a_trust_assurance_policies where tenant_id=:tenant and policy_id=:policy",
                params(tenant,policyId),Integer.class);
        if(existing!=null&&existing>0){
            String status=jdbc.queryForObject("select policy_status from a2a_trust_assurance_policies where tenant_id=:tenant and policy_id=:policy",params(tenant,policyId),String.class);
            if(!"DRAFT".equals(status))throw new IllegalArgumentException("A2A_TRUST_ASSURANCE_POLICY_NOT_DRAFT");
            jdbc.update("update a2a_trust_assurance_policies set display_name=:name,description=:description,updated_at=now() where tenant_id=:tenant and policy_id=:policy",
                    params(tenant,policyId).addValue("name",displayName.trim()).addValue("description",blankToNull(description)));
            jdbc.update("delete from a2a_trust_assurance_rules where tenant_id=:tenant and policy_id=:policy",params(tenant,policyId));
        }else{
            jdbc.update("""
                insert into a2a_trust_assurance_policies(tenant_id,policy_id,display_name,description,policy_status,policy_version,created_by,created_at,updated_at)
                values(:tenant,:policy,:name,:description,'DRAFT',1,'a2a-trust-assurance-policy',now(),now())
                """,params(tenant,policyId).addValue("name",displayName.trim()).addValue("description",blankToNull(description)));
        }
        if(rules==null||rules.isEmpty())throw new IllegalArgumentException("A2A_TRUST_ASSURANCE_POLICY_RULE_REQUIRED");
        Set<String> uniqueness=new LinkedHashSet<>();
        for(Map<String,Object> rule:rules){
            PeerTrustAssuranceGrant grant=PeerTrustAssuranceGrant.require(str(rule,"grantType"));
            PeerTrustEvidenceSubjectType subject=PeerTrustEvidenceSubjectType.require(str(rule,"subjectType"));
            List<List<PeerTrustEvidenceType>> sets=parseEvidenceSets(rule.get("requiredEvidenceSets"));
            String unique=subject.name()+":"+grant.name();
            if(!uniqueness.add(unique))throw new IllegalArgumentException("A2A_TRUST_ASSURANCE_DUPLICATE_GRANT_RULE");
            String ruleId=blankToNull(str(rule,"ruleId")); if(ruleId==null)ruleId="tar-"+UUID.randomUUID();
            jdbc.update("""
                insert into a2a_trust_assurance_rules(tenant_id,policy_id,rule_id,grant_type,subject_type,required_evidence_sets_json,description,created_at)
                values(:tenant,:policy,:rule,:grant,:subject,cast(:sets as jsonb),:description,now())
                """,params(tenant,policyId).addValue("rule",ruleId).addValue("grant",grant.name()).addValue("subject",subject.name())
                    .addValue("sets",writeEvidenceSets(sets)).addValue("description",blankToNull(str(rule,"description"))));
        }
        return policyView(tenant,policyId);
    }

    @Transactional
    public Map<String,Object> activate(String tenant,String policyId) {
        bind(tenant,"a2a-trust-assurance-policy-activate");
        required(policyId,"policyId");
        Integer ruleCount=jdbc.queryForObject("select count(*) from a2a_trust_assurance_rules where tenant_id=:tenant and policy_id=:policy",params(tenant,policyId),Integer.class);
        if(ruleCount==null||ruleCount<1)throw new IllegalArgumentException("A2A_TRUST_ASSURANCE_POLICY_RULE_REQUIRED");
        String status=jdbc.queryForObject("select policy_status from a2a_trust_assurance_policies where tenant_id=:tenant and policy_id=:policy",params(tenant,policyId),String.class);
        if(!"DRAFT".equals(status))throw new IllegalArgumentException("A2A_TRUST_ASSURANCE_POLICY_NOT_ACTIVATABLE");
        jdbc.update("update a2a_trust_assurance_policies set policy_status='RETIRED',retired_at=now(),updated_at=now() where tenant_id=:tenant and policy_status='ACTIVE'",
                new MapSqlParameterSource("tenant",tenant));
        int n=jdbc.update("update a2a_trust_assurance_policies set policy_status='ACTIVE',activated_at=now(),updated_at=now() where tenant_id=:tenant and policy_id=:policy and policy_status='DRAFT'",params(tenant,policyId));
        if(n!=1)throw new IllegalArgumentException("A2A_TRUST_ASSURANCE_POLICY_NOT_ACTIVATABLE");
        return policyView(tenant,policyId);
    }

    @Transactional(readOnly = true)
    public TrustAssuranceEvaluation evaluate(String tenant,String peerId,String interfaceId,PeerTrustEvidenceSubjectType subject) {
        bind(tenant,"a2a-trust-assurance-evaluate");
        required(peerId,"peerId");
        if(subject==PeerTrustEvidenceSubjectType.INTERFACE)required(interfaceId,"interfaceId");
        List<Map<String,Object>> active=jdbc.queryForList("""
            select policy_id,policy_version from a2a_trust_assurance_policies
             where tenant_id=:tenant and policy_status='ACTIVE'
            """,new MapSqlParameterSource("tenant",tenant));
        if(active.isEmpty())return new TrustAssuranceEvaluation(null,0,subject,peerId,interfaceId,Set.of(),effectiveEvidence(tenant,peerId,interfaceId,subject),Map.of(),false);
        if(active.size()!=1)throw new IllegalStateException("A2A_TRUST_ASSURANCE_ACTIVE_POLICY_AMBIGUOUS");
        String policyId=String.valueOf(active.getFirst().get("policy_id"));
        long version=((Number)active.getFirst().get("policy_version")).longValue();
        Set<PeerTrustEvidenceType> evidence=effectiveEvidence(tenant,peerId,interfaceId,subject);
        List<Map<String,Object>> rules=jdbc.queryForList("""
            select grant_type,required_evidence_sets_json::text as required_evidence_sets_json
              from a2a_trust_assurance_rules
             where tenant_id=:tenant and policy_id=:policy and subject_type=:subject
             order by grant_type
            """,params(tenant,policyId).addValue("subject",subject.name()));
        EnumSet<PeerTrustAssuranceGrant> grants=EnumSet.noneOf(PeerTrustAssuranceGrant.class);
        Map<PeerTrustAssuranceGrant,List<List<PeerTrustEvidenceType>>> requirements=new LinkedHashMap<>();
        for(Map<String,Object> row:rules){
            PeerTrustAssuranceGrant grant=PeerTrustAssuranceGrant.require(String.valueOf(row.get("grant_type")));
            List<List<PeerTrustEvidenceType>> sets=readEvidenceSets(String.valueOf(row.get("required_evidence_sets_json")));
            requirements.put(grant,sets);
            if(hasGrant(tenant,peerId,interfaceId,subject,grant))grants.add(grant);
        }
        return new TrustAssuranceEvaluation(policyId,version,subject,peerId,interfaceId,Set.copyOf(grants),Set.copyOf(evidence),Map.copyOf(requirements),true);
    }

    @Transactional(readOnly = true)
    public Map<String,Object> explain(String tenant,String peerId,String interfaceId) {
        TrustAssuranceEvaluation peer=evaluate(tenant,peerId,null,PeerTrustEvidenceSubjectType.PEER);
        TrustAssuranceEvaluation iface=interfaceId==null||interfaceId.isBlank()?null:evaluate(tenant,peerId,interfaceId,PeerTrustEvidenceSubjectType.INTERFACE);
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("peerId",peerId); out.put("interfaceId",blankToNull(interfaceId));
        out.put("activePolicyId",peer.policyId()); out.put("activePolicyPresent",peer.activePolicyPresent());
        out.put("peer",view(peer)); if(iface!=null)out.put("interface",view(iface));
        out.put("legacyTrustStatusIsAuthority",false); out.put("grantsAreComputed",true);
        return out;
    }

    @Transactional(readOnly = true)
    public void requireReadAllowed(String tenant,String peerId,String interfaceId) {
        TrustAssuranceEvaluation peer=evaluate(tenant,peerId,null,PeerTrustEvidenceSubjectType.PEER);
        TrustAssuranceEvaluation iface=evaluate(tenant,peerId,interfaceId,PeerTrustEvidenceSubjectType.INTERFACE);
        if(!peer.activePolicyPresent())throw new IllegalArgumentException("A2A_TRUST_ASSURANCE_ACTIVE_POLICY_REQUIRED");
        if(!peer.grants(PeerTrustAssuranceGrant.READ_ALLOWED))throw new IllegalArgumentException("A2A_PEER_READ_ASSURANCE_REQUIRED");
        if(!iface.grants(PeerTrustAssuranceGrant.READ_ALLOWED))throw new IllegalArgumentException("A2A_INTERFACE_READ_ASSURANCE_REQUIRED");
    }

    @Transactional(readOnly = true)
    public void requirePeerReadAllowed(String tenant,String peerId) {
        TrustAssuranceEvaluation peer=evaluate(tenant,peerId,null,PeerTrustEvidenceSubjectType.PEER);
        if(!peer.activePolicyPresent())throw new IllegalArgumentException("A2A_TRUST_ASSURANCE_ACTIVE_POLICY_REQUIRED");
        if(!peer.grants(PeerTrustAssuranceGrant.READ_ALLOWED))throw new IllegalArgumentException("A2A_PEER_READ_ASSURANCE_REQUIRED");
    }

    private boolean hasGrant(String tenant,String peerId,String interfaceId,PeerTrustEvidenceSubjectType subject,PeerTrustAssuranceGrant grant){
        Boolean allowed=jdbc.queryForObject("select a2a_has_assurance_grant(:tenant,:peer,:interface,:subject,:grant)",
                new MapSqlParameterSource("tenant",tenant).addValue("peer",peerId).addValue("interface",interfaceId)
                    .addValue("subject",subject.name()).addValue("grant",grant.name()),Boolean.class);
        return Boolean.TRUE.equals(allowed);
    }

    private Set<PeerTrustEvidenceType> effectiveEvidence(String tenant,String peerId,String interfaceId,PeerTrustEvidenceSubjectType subject){
        String sql="""
            select evidence_type from a2a_peer_trust_evidence
             where tenant_id=:tenant and peer_id=:peer and subject_type=:subject
               and evidence_status='ACTIVE' and valid_from<=now() and (valid_until is null or valid_until>now())
            """+(subject==PeerTrustEvidenceSubjectType.INTERFACE?" and interface_id=:interface":" and interface_id is null");
        MapSqlParameterSource p=new MapSqlParameterSource("tenant",tenant).addValue("peer",peerId).addValue("subject",subject.name()).addValue("interface",interfaceId);
        EnumSet<PeerTrustEvidenceType> out=EnumSet.noneOf(PeerTrustEvidenceType.class);
        for(String value:jdbc.queryForList(sql,p,String.class))out.add(PeerTrustEvidenceType.require(value));
        return out;
    }

    private Map<String,Object> policyView(String tenant,String policyId){
        List<Map<String,Object>> rows=jdbc.queryForList("select * from a2a_trust_assurance_policies where tenant_id=:tenant and policy_id=:policy",params(tenant,policyId));
        if(rows.isEmpty())throw new IllegalArgumentException("A2A_TRUST_ASSURANCE_POLICY_NOT_FOUND");
        Map<String,Object> out=new LinkedHashMap<>(rows.getFirst());
        out.put("rules",jdbc.queryForList("""
            select rule_id,grant_type,subject_type,required_evidence_sets_json,description,created_at
              from a2a_trust_assurance_rules where tenant_id=:tenant and policy_id=:policy order by subject_type,grant_type,rule_id
            """,params(tenant,policyId)));
        return out;
    }

    private List<List<PeerTrustEvidenceType>> parseEvidenceSets(Object raw){
        if(!(raw instanceof List<?> outer)||outer.isEmpty())throw new IllegalArgumentException("A2A_TRUST_ASSURANCE_REQUIRED_EVIDENCE_SET_INVALID");
        List<List<PeerTrustEvidenceType>> result=new ArrayList<>();
        for(Object item:outer){
            if(!(item instanceof List<?> inner)||inner.isEmpty())throw new IllegalArgumentException("A2A_TRUST_ASSURANCE_REQUIRED_EVIDENCE_SET_INVALID");
            LinkedHashSet<PeerTrustEvidenceType> set=new LinkedHashSet<>();
            for(Object token:inner)set.add(PeerTrustEvidenceType.require(token==null?null:String.valueOf(token)));
            result.add(List.copyOf(set));
        }
        return List.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private List<List<PeerTrustEvidenceType>> readEvidenceSets(String raw){
        try{
            List<Object> outer=json.readValue(raw,List.class);
            List<Map<String,Object>> fake=List.of(Map.of("requiredEvidenceSets",outer));
            return parseEvidenceSets(fake.getFirst().get("requiredEvidenceSets"));
        }catch(IllegalArgumentException ex){throw ex;}catch(Exception ex){throw new IllegalStateException("A2A_TRUST_ASSURANCE_POLICY_JSON_INVALID",ex);}
    }

    private String writeEvidenceSets(List<List<PeerTrustEvidenceType>> sets){
        List<List<String>> names=sets.stream().map(s->s.stream().map(Enum::name).toList()).toList();
        try{return json.writeValueAsString(names);}catch(Exception ex){throw new IllegalArgumentException("A2A_TRUST_ASSURANCE_REQUIRED_EVIDENCE_SET_INVALID",ex);}
    }

    private static Map<String,Object> view(TrustAssuranceEvaluation e){
        Map<String,Object> m=new LinkedHashMap<>();
        m.put("subjectType",e.subjectType().name());
        m.put("grants",e.grants().stream().map(Enum::name).sorted().toList());
        m.put("effectiveEvidence",e.effectiveEvidence().stream().map(Enum::name).sorted().toList());
        Map<String,Object> req=new LinkedHashMap<>();
        e.requiredEvidenceSets().forEach((grant,sets)->req.put(grant.name(),sets.stream().map(s->s.stream().map(Enum::name).toList()).toList()));
        m.put("requiredEvidenceSets",req); return m;
    }

    private void bind(String tenant,String actor){required(tenant,"tenantId");jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)",String.class,tenant);jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)",String.class,actor);}
    private static MapSqlParameterSource params(String tenant,String policy){return new MapSqlParameterSource("tenant",tenant).addValue("policy",policy);}
    private static String str(Map<String,Object> m,String k){Object v=m==null?null:m.get(k);return v==null?null:String.valueOf(v);}
    private static String blankToNull(String v){return v==null||v.isBlank()?null:v.trim();}
    private static void required(String v,String n){if(v==null||v.isBlank())throw new IllegalArgumentException(n+" is required");}
}
