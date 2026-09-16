package com.opensocket.aievent.core.resourceaccess.runtime;

import com.opensocket.aievent.core.agent.governance.AgentApprovalStatus;
import com.opensocket.aievent.core.agent.governance.AgentGovernanceService;
import com.opensocket.aievent.core.agent.governance.AgentProfile;
import com.opensocket.aievent.core.api.StandardApiErrorCode;
import com.opensocket.aievent.core.api.StandardApiException;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceListScopeQueryPlan;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** RS3 SQL-first Agent data-scope adapter. Organization membership never grants Agent visibility by itself. */
@Component
@ConditionalOnProperty(prefix="resource-access", name={"enabled","business-data-enabled"}, havingValue="true")
public final class ScopedAgentQueryService {
    private final NamedParameterJdbcTemplate jdbc;
    private final AgentGovernanceService governance;

    public ScopedAgentQueryService(NamedParameterJdbcTemplate jdbc, AgentGovernanceService governance) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.governance = Objects.requireNonNull(governance);
    }

    public List<AgentProfile> search(AgentApprovalStatus approvalStatus, int limit, ResourceListScopeQueryPlan plan) {
        int safeLimit = Math.max(1, Math.min(5000, limit));
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", plan.tenantId())
                .addValue("approvalStatus", approvalStatus == null ? null : approvalStatus.name())
                .addValue("limit", safeLimit);
        ScopedResourceSql.bind(params, plan);
        String predicate = ScopedResourceSql.predicate(plan, "p", "agent_id", "owner_department_id", "owner_group_id");
        String sql = "select p.agent_id from agent_profiles p "
                + "where p.tenant_id=:tenantId and (:approvalStatus is null or p.approval_status=:approvalStatus) and "
                + predicate + " order by p.updated_at desc nulls last limit :limit";
        return jdbc.query(sql, params, (rs, rowNum) -> rs.getString(1)).stream()
                .map(governance::getProfile)
                .toList();
    }

    public void requireAssignableOwner(ResourceListScopeQueryPlan plan, String tenantId, String agentId,
            String ownerDepartmentId, String ownerGroupId) {
        if (!plan.tenantId().equals(tenantId)) {
            throw new StandardApiException(StandardApiErrorCode.FORBIDDEN, "Agent ownership must remain inside the active Tenant.");
        }
        String department = normalizedOwner(ownerDepartmentId);
        String group = normalizedOwner(ownerGroupId);
        if (!ScopedResourceSql.allowsOwnership(jdbc, plan, tenantId, agentId, department, group)) {
            throw new StandardApiException(StandardApiErrorCode.FORBIDDEN,
                    "The requested Agent owner is outside your effective Responsibility scope.");
        }
    }

    private static String normalizedOwner(String value) {
        if (value == null || value.isBlank() || "UNASSIGNED".equalsIgnoreCase(value)) return null;
        return value.trim();
    }
}
