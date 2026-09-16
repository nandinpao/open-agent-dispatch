package com.opensocket.aievent.core.resourceaccess.persistence;

import com.opensocket.aievent.core.resourceaccess.contract.TaskScopeQueryAuditPort;
import com.opensocket.aievent.core.resourceaccess.contract.TaskScopeQueryPlan;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Append-only P4RA-E Task scope-plan and SHADOW mismatch evidence. */
@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix = "resource-access", name = {"enabled", "task-enabled"}, havingValue = "true")
public class JdbcTaskScopeQueryAuditAdapter implements TaskScopeQueryAuditPort {
    private final JdbcTemplate jdbc;
    public JdbcTaskScopeQueryAuditAdapter(JdbcTemplate jdbc) { this.jdbc = Objects.requireNonNull(jdbc, "jdbc"); }

    @Override
    @Transactional
    public void recordPlan(TaskScopeQueryPlan plan, String purpose, Instant createdAt) {
        jdbc.update("""
                insert into resource_task_scope_query_audits(
                  tenant_id,scope_query_audit_id,principal_type,principal_id,permission_code,purpose,
                  strategy,plan_hash,maximum_visibility,exact_department_count,subtree_root_count,
                  group_count,explicit_resource_count,excluded_resource_count,policy_catalog_version,
                  policy_revision,global_security_epoch,tenant_security_epoch,principal_security_epoch,
                  resource_security_epoch,department_tree_revision,created_at)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                on conflict(tenant_id,plan_hash,purpose) do nothing
                """, plan.tenantId(), UUID.randomUUID().toString(), plan.principalType(), plan.principalId(),
                plan.permissionCode(), required(purpose), plan.strategy().name(), plan.planHash(),
                plan.maximumVisibility().name(), plan.exactDepartmentIds().size(),
                plan.subtreeDepartmentRootIds().size(), plan.groupIds().size(), plan.explicitTaskIds().size(),
                plan.excludedTaskIds().size(), plan.policyVersion().catalogVersion(),
                plan.policyVersion().policyRevision(), plan.securityEpoch().globalEpoch(),
                plan.securityEpoch().tenantEpoch(), plan.securityEpoch().principalEpoch(),
                plan.securityEpoch().resourceEpoch(), plan.securityEpoch().departmentTreeRevision(),
                Timestamp.from(createdAt));
    }

    @Override
    @Transactional
    public void recordShadowMismatch(TaskScopeQueryPlan plan, String purpose,
            Set<String> legacyOnlyTaskIds, Set<String> scopedOnlyTaskIds, Instant createdAt) {
        jdbc.update("""
                insert into resource_task_scope_shadow_mismatches(
                  tenant_id,mismatch_id,principal_type,principal_id,permission_code,purpose,plan_hash,
                  legacy_only_count,scoped_only_count,legacy_only_sample,scoped_only_sample,created_at)
                values (?,?,?,?,?,?,?,?,?,?,?,?)
                """, plan.tenantId(), UUID.randomUUID().toString(), plan.principalType(), plan.principalId(),
                plan.permissionCode(), required(purpose), plan.planHash(), legacyOnlyTaskIds.size(),
                scopedOnlyTaskIds.size(), sample(legacyOnlyTaskIds), sample(scopedOnlyTaskIds),
                Timestamp.from(createdAt));
    }

    private static String sample(Set<String> values) {
        return String.join(",", new TreeSet<>(values).stream().limit(50).toList());
    }
    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("purpose is required");
        return value.trim();
    }
}
