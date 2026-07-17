package com.opensocket.aievent.database.persistence.dispatch.cutover;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.opensocket.aievent.core.routing.cutover.DispatchCutoverDecision;
import com.opensocket.aievent.core.routing.cutover.DispatchCutoverMode;
import com.opensocket.aievent.core.routing.cutover.DispatchCutoverOutcome;
import com.opensocket.aievent.core.routing.cutover.DispatchCutoverPolicy;
import com.opensocket.aievent.core.routing.cutover.DispatchCutoverPolicyStatus;
import com.opensocket.aievent.core.routing.cutover.DispatchCutoverReadiness;
import com.opensocket.aievent.core.routing.cutover.DispatchCutoverRepository;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;

@DatabaseRepositoryAdapter
public class JdbcDispatchCutoverRepository implements DispatchCutoverRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public JdbcDispatchCutoverRepository(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}

    @Override
    public Optional<DispatchCutoverPolicy> findEffectivePolicy(String tenantId,String flowId){
        List<DispatchCutoverPolicy> values=jdbc.query(POLICY_SELECT+"""
            where tenant_id=:tenantId
              and ((flow_id=:flowId and status in ('ACTIVE','ROLLED_BACK'))
                   or (flow_id='*' and status='ACTIVE'))
            order by case when flow_id=:flowId then 0 else 1 end, updated_at desc
            limit 1
            """,new MapSqlParameterSource().addValue("tenantId",require(tenantId,"tenantId")).addValue("flowId",require(flowId,"flowId")),POLICY_MAPPER);
        return values.stream().findFirst();
    }
    @Override public Optional<DispatchCutoverPolicy> findPolicy(String tenantId,String policyId){
        return jdbc.query(POLICY_SELECT+" where tenant_id=:tenantId and policy_id=:policyId",new MapSqlParameterSource()
                .addValue("tenantId",require(tenantId,"tenantId")).addValue("policyId",require(policyId,"policyId")),POLICY_MAPPER).stream().findFirst();
    }
    @Override public List<DispatchCutoverPolicy> listPolicies(String tenantId,int limit){
        return jdbc.query(POLICY_SELECT+" where tenant_id=:tenantId order by flow_id,updated_at desc limit :limit",new MapSqlParameterSource()
                .addValue("tenantId",require(tenantId,"tenantId")).addValue("limit",bounded(limit)),POLICY_MAPPER);
    }
    @Override public DispatchCutoverPolicy savePolicy(DispatchCutoverPolicy p){
        p.validate();
        Optional<DispatchCutoverPolicy> existingPolicy = findPolicy(p.getTenantId(), p.getPolicyId());
        if (existingPolicy.isPresent() && existingPolicy.get().getVersion() != p.getVersion()) {
            throw new IllegalStateException("Cutover policy version conflict: expected "
                    + existingPolicy.get().getVersion() + " but received " + p.getVersion());
        }
        if (existingPolicy.isEmpty() && p.getVersion() != 1) {
            throw new IllegalArgumentException("New cutover policy version must be 1");
        }
        Optional<String> scopeOwner = jdbc.query("""
            select policy_id from dispatch_cutover_policies
             where tenant_id=:tenantId and flow_id=:flowId
            """, new MapSqlParameterSource().addValue("tenantId", p.getTenantId()).addValue("flowId", p.getFlowId()),
                (rs, row) -> rs.getString("policy_id")).stream().findFirst();
        if (scopeOwner.isPresent() && !scopeOwner.get().equals(p.getPolicyId())) {
            throw new IllegalArgumentException("Cutover scope already belongs to policy " + scopeOwner.get());
        }
        int updated = jdbc.update("""
            insert into dispatch_cutover_policies(tenant_id,policy_id,flow_id,mode,canary_percentage,minimum_sample_size,
              maximum_requirement_blocked_rate,maximum_no_candidate_rate,maximum_selection_difference_rate,
              auto_rollback_enabled,status,version,
              rolled_back_at,rollback_reason,created_at,created_by,updated_at,updated_by)
            values(:tenantId,:policyId,:flowId,:mode,:canary,:sample,:reqRate,:noCandidateRate,:selectionRate,
              :autoRollback,:status,:version,:rolledBackAt,:rollbackReason,
              coalesce(:createdAt,now()),:createdBy,now(),:updatedBy)
            on conflict(tenant_id,policy_id) do update set
              flow_id=excluded.flow_id,mode=excluded.mode,canary_percentage=excluded.canary_percentage,
              minimum_sample_size=excluded.minimum_sample_size,maximum_requirement_blocked_rate=excluded.maximum_requirement_blocked_rate,
              maximum_no_candidate_rate=excluded.maximum_no_candidate_rate,maximum_selection_difference_rate=excluded.maximum_selection_difference_rate,
              auto_rollback_enabled=excluded.auto_rollback_enabled,status=excluded.status,
              version=dispatch_cutover_policies.version+1,rolled_back_at=excluded.rolled_back_at,
              rollback_reason=excluded.rollback_reason,updated_at=now(),updated_by=excluded.updated_by
            where dispatch_cutover_policies.version=:version
            """,params(p));
        if (updated == 0) {
            throw new IllegalStateException("Cutover policy changed while it was being saved: " + p.getPolicyId());
        }
        return findPolicy(p.getTenantId(),p.getPolicyId()).orElseThrow();
    }
    @Override public DispatchCutoverDecision appendDecision(DispatchCutoverDecision d){
        d.validate();
        jdbc.update("""
          insert into dispatch_cutover_task_decisions(tenant_id,decision_id,task_id,flow_id,policy_id,configured_mode,authoritative,deterministic_bucket,reason_code,created_at)
          values(:tenantId,:decisionId,:taskId,:flowId,:policyId,:mode,:authoritative,:bucket,:reasonCode,coalesce(:createdAt,now()))
          on conflict(tenant_id,task_id,flow_id) do nothing
          """,new MapSqlParameterSource().addValue("tenantId",d.getTenantId()).addValue("decisionId",d.getDecisionId())
                .addValue("taskId",d.getTaskId()).addValue("flowId",d.getFlowId()).addValue("policyId",d.getPolicyId())
                .addValue("mode",d.getConfiguredMode().name()).addValue("authoritative",d.isAuthoritative())
                .addValue("bucket",d.getDeterministicBucket()).addValue("reasonCode",d.getReasonCode()).addValue("createdAt",d.getCreatedAt()));
        return d;
    }
    @Override public DispatchCutoverOutcome appendOutcome(DispatchCutoverOutcome o){
        o.validate();
        jdbc.update("""
          insert into dispatch_cutover_outcomes(tenant_id,outcome_id,task_id,flow_id,policy_id,authoritative,
            requirement_blocked,no_candidate,selected_agent_different,selected_agent_id,legacy_selected_agent_id,reason_code,created_at)
          values(:tenantId,:outcomeId,:taskId,:flowId,:policyId,:authoritative,:requirementBlocked,:noCandidate,
            :selectedDifferent,:selectedAgentId,:legacySelectedAgentId,:reasonCode,coalesce(:createdAt,now()))
          on conflict(tenant_id,task_id,flow_id,authoritative) do nothing
          """,new MapSqlParameterSource().addValue("tenantId",o.getTenantId()).addValue("outcomeId",o.getOutcomeId())
                .addValue("taskId",o.getTaskId()).addValue("flowId",o.getFlowId()).addValue("policyId",o.getPolicyId())
                .addValue("authoritative",o.isAuthoritative()).addValue("requirementBlocked",o.isRequirementBlocked())
                .addValue("noCandidate",o.isNoCandidate()).addValue("selectedDifferent",o.isSelectedAgentDifferent())
                .addValue("selectedAgentId",o.getSelectedAgentId()).addValue("legacySelectedAgentId",o.getLegacySelectedAgentId())
                .addValue("reasonCode",o.getReasonCode()).addValue("createdAt",o.getCreatedAt()));
        return o;
    }
    @Override public DispatchCutoverReadiness readiness(String tenantId,String flowId){
        return jdbc.query("""
          select tenant_id,flow_id,sample_size,authoritative_sample_size,control_sample_size,
                 requirement_blocked_count,no_candidate_count,selection_difference_count,
                 requirement_blocked_rate,no_candidate_rate,selection_difference_rate,authoritative_metrics_available
            from dispatch_p10_cutover_readiness where tenant_id=:tenantId and flow_id=:flowId
          """,new MapSqlParameterSource().addValue("tenantId",require(tenantId,"tenantId")).addValue("flowId",require(flowId,"flowId")),(rs,row)->new DispatchCutoverReadiness(
                rs.getString("tenant_id"),rs.getString("flow_id"),rs.getLong("sample_size"),
                rs.getLong("authoritative_sample_size"),rs.getLong("control_sample_size"),
                rs.getLong("requirement_blocked_count"),rs.getLong("no_candidate_count"),rs.getLong("selection_difference_count"),
                rs.getDouble("requirement_blocked_rate"),rs.getDouble("no_candidate_rate"),rs.getDouble("selection_difference_rate"),
                rs.getBoolean("authoritative_metrics_available"))).stream().findFirst().orElse(DispatchCutoverReadiness.empty(tenantId,flowId));
    }
    @Override public boolean markRolledBack(String tenantId,String policyId,String reason,String actor){
        return jdbc.update("""
          update dispatch_cutover_policies set mode='ROLLED_BACK',status='ROLLED_BACK',rolled_back_at=now(),rollback_reason=:reason,
                 version=version+1,updated_at=now(),updated_by=:actor
           where tenant_id=:tenantId and policy_id=:policyId and status='ACTIVE'
          """,new MapSqlParameterSource().addValue("tenantId",require(tenantId,"tenantId")).addValue("policyId",require(policyId,"policyId"))
                .addValue("reason",require(reason,"reason")).addValue("actor",require(actor,"actor")))>0;
    }

    private static final String POLICY_SELECT="""
      select tenant_id,policy_id,flow_id,mode,canary_percentage,minimum_sample_size,maximum_requirement_blocked_rate,
             maximum_no_candidate_rate,maximum_selection_difference_rate,auto_rollback_enabled,
             status,version,rolled_back_at,rollback_reason,created_at,created_by,updated_at,updated_by
        from dispatch_cutover_policies
      """;
    private static final RowMapper<DispatchCutoverPolicy> POLICY_MAPPER=new RowMapper<>(){
      @Override public DispatchCutoverPolicy mapRow(ResultSet rs,int rowNum)throws SQLException{
        DispatchCutoverPolicy p=new DispatchCutoverPolicy();p.setTenantId(rs.getString("tenant_id"));p.setPolicyId(rs.getString("policy_id"));p.setFlowId(rs.getString("flow_id"));
        p.setMode(DispatchCutoverMode.valueOf(rs.getString("mode")));p.setCanaryPercentage(rs.getInt("canary_percentage"));p.setMinimumSampleSize(rs.getInt("minimum_sample_size"));
        p.setMaximumRequirementBlockedRate(rs.getDouble("maximum_requirement_blocked_rate"));p.setMaximumNoCandidateRate(rs.getDouble("maximum_no_candidate_rate"));
        p.setMaximumSelectionDifferenceRate(rs.getDouble("maximum_selection_difference_rate"));p.setAutoRollbackEnabled(rs.getBoolean("auto_rollback_enabled"));
        p.setStatus(DispatchCutoverPolicyStatus.valueOf(rs.getString("status")));p.setVersion(rs.getInt("version"));p.setRolledBackAt(rs.getObject("rolled_back_at",OffsetDateTime.class));
        p.setRollbackReason(rs.getString("rollback_reason"));p.setCreatedAt(rs.getObject("created_at",OffsetDateTime.class));p.setCreatedBy(rs.getString("created_by"));
        p.setUpdatedAt(rs.getObject("updated_at",OffsetDateTime.class));p.setUpdatedBy(rs.getString("updated_by"));return p;
      }};
    private static MapSqlParameterSource params(DispatchCutoverPolicy p){return new MapSqlParameterSource().addValue("tenantId",p.getTenantId()).addValue("policyId",p.getPolicyId()).addValue("flowId",p.getFlowId())
      .addValue("mode",p.getMode().name()).addValue("canary",p.getCanaryPercentage()).addValue("sample",p.getMinimumSampleSize())
      .addValue("reqRate",p.getMaximumRequirementBlockedRate()).addValue("noCandidateRate",p.getMaximumNoCandidateRate()).addValue("selectionRate",p.getMaximumSelectionDifferenceRate())
      .addValue("autoRollback",p.isAutoRollbackEnabled())
      .addValue("status",p.getStatus().name()).addValue("version",p.getVersion()).addValue("rolledBackAt",p.getRolledBackAt()).addValue("rollbackReason",p.getRollbackReason())
      .addValue("createdAt",p.getCreatedAt()).addValue("createdBy",p.getCreatedBy()).addValue("updatedBy",p.getUpdatedBy());}
    private static int bounded(int v){return Math.max(1,Math.min(v,1000));}
    private static String require(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v;}
}
