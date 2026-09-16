package com.opensocket.aievent.core.flowmatch;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** A0-R3 operator read model for canonical FlowMatchDecision + externalized evaluation evidence. */
@Service
public class FlowMatchAuthorityQueryService {
    private static final TypeReference<List<Map<String,Object>>> MAP_LIST = new TypeReference<>(){};
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>(){};
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public FlowMatchAuthorityQueryService(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}

    @Transactional(readOnly=true)
    public View view(String taskId){
        var c=IamTenantContextHolder.require();bind(c.tenantId(),c.actorId());
        Decision decision=jdbc.query("""
          select decision_id,task_id,decision_source,flow_id,flow_version,evaluated_rule_count,matched_rule_id,matched_rule_priority,
                 evaluated_rules_json::text,output_service_code,output_capability_requirements_json::text,match_result,
                 flow_evaluation_set_ref,flow_evaluation_set_digest,closest_rule_id,closest_rule_priority,closest_rule_failed_criterion,
                 closest_rule_match_ratio,decision_authority_version,decided_at,evaluator_version
            from flow_match_decisions
           where tenant_id=? and task_id=? and decision_source='A0_R3_FLOW_MATCH_AUTHORITY'
           order by decided_at desc limit 1
          """,rs->rs.next()?new Decision(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getInt(6),rs.getString(7),(Integer)rs.getObject(8),readMaps(rs.getString(9)),rs.getString(10),readStrings(rs.getString(11)),rs.getString(12),rs.getString(13),rs.getString(14),rs.getString(15),(Integer)rs.getObject(16),rs.getString(17),numberOrNull(rs.getObject(18)),rs.getString(19),rs.getObject(20,OffsetDateTime.class),rs.getString(21)):null,c.tenantId(),taskId);
        if(decision==null)throw new IllegalArgumentException("Authoritative Flow Match decision not found for Task: "+taskId);
        EvaluationSet set=decision.evaluationSetRef()==null?null:jdbc.query("""
          select evaluation_set_id,flow_id,flow_version,input_snapshot_json::text,evaluated_rules_json::text,closest_rules_json::text,
                 evaluated_rule_count,match_result,minimum_matched_priority,evaluation_digest,evaluator_version,created_at
            from flow_evaluation_sets where tenant_id=? and evaluation_set_id=?
          """,rs->rs.next()?new EvaluationSet(rs.getString(1),rs.getString(2),rs.getString(3),readMap(rs.getString(4)),readMaps(rs.getString(5)),readMaps(rs.getString(6)),rs.getInt(7),rs.getString(8),(Integer)rs.getObject(9),rs.getString(10),rs.getString(11),rs.getObject(12,OffsetDateTime.class)):null,c.tenantId(),decision.evaluationSetRef());
        IssuePolicyAuthority issuePolicy = issuePolicyAuthority(c.tenantId(), decision.flowId(), decision.matchedRuleId());
        return new View(c.tenantId(),decision,set,issuePolicy);
    }

    private IssuePolicyAuthority issuePolicyAuthority(String tenantId,String flowId,String ruleId){
        if(flowId==null||flowId.isBlank())return null;
        return jdbc.query("""
          select f.issue_sync_policy,p.issue_sync_policy
            from dispatch_flows f
            left join dispatch_policies p
              on p.tenant_id=f.tenant_id and p.flow_id=f.flow_id and p.policy_id=?
           where f.tenant_id=? and f.flow_id=?
          """,rs->{
              if(!rs.next())return null;
              String flowPolicy=rs.getString(1);
              String rulePolicy=rs.getString(2);
              String source=rulePolicy!=null&&!rulePolicy.isBlank()?"RULE_OVERRIDE":flowPolicy!=null&&!flowPolicy.isBlank()?"FLOW_DEFAULT":"SYSTEM_FALLBACK";
              String effective="RULE_OVERRIDE".equals(source)?rulePolicy:"FLOW_DEFAULT".equals(source)?flowPolicy:"OPTIONAL";
              return new IssuePolicyAuthority(rulePolicy,flowPolicy,source,effective);
          },ruleId,tenantId,flowId);
    }

    private void bind(String tenant,String actor){jdbc.queryForObject("select set_config('app.current_tenant_id',?,true)",String.class,tenant);jdbc.queryForObject("select set_config('app.current_actor_id',?,true)",String.class,actor);}
    private Double numberOrNull(Object value){return value instanceof Number n?n.doubleValue():null;}
    private List<Map<String,Object>> readMaps(String raw){try{return raw==null?List.of():json.readValue(raw,MAP_LIST);}catch(Exception ex){return List.of(Map.of("parseError",ex.getClass().getSimpleName()));}}
    private List<String> readStrings(String raw){try{return raw==null?List.of():json.readValue(raw,STRING_LIST);}catch(Exception ex){return List.of();}}
    @SuppressWarnings("unchecked") private Map<String,Object> readMap(String raw){try{return raw==null?Map.of():json.readValue(raw,Map.class);}catch(Exception ex){return Map.of("parseError",ex.getClass().getSimpleName());}}

    public record View(String tenantId,Decision decision,EvaluationSet evaluationSet,IssuePolicyAuthority issuePolicyAuthority){}
    public record IssuePolicyAuthority(String ruleIssueSyncPolicy,String flowIssueSyncPolicy,String issueSyncPolicySource,String effectiveIssueSyncPolicy){}
    public record Decision(String decisionId,String taskId,String decisionSource,String flowId,String flowVersion,int evaluatedRuleCount,String matchedRuleId,Integer matchedRulePriority,List<Map<String,Object>> evaluatedRuleSummary,String outputServiceCode,List<String> outputCapabilityRequirements,String matchResult,String evaluationSetRef,String evaluationSetDigest,String closestRuleId,Integer closestRulePriority,String closestRuleFailedCriterion,Double closestRuleMatchRatio,String decisionAuthorityVersion,OffsetDateTime decidedAt,String evaluatorVersion){}
    public record EvaluationSet(String evaluationSetId,String flowId,String flowVersion,Map<String,Object> inputSnapshot,List<Map<String,Object>> evaluatedRules,List<Map<String,Object>> closestRules,int evaluatedRuleCount,String matchResult,Integer minimumMatchedPriority,String evaluationDigest,String evaluatorVersion,OffsetDateTime createdAt){}
}
