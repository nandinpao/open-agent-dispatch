package com.opensocket.aievent.core.resourceaccess.persistence;

import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.core.resourceaccess.core.RuntimeAuthorizationLeaseRepository;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL authority for expiring and fenceable Runtime Authorization Leases. */
@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix = "resource-access", name = {"enabled", "runtime-lease-enabled"}, havingValue = "true")
public class JdbcRuntimeAuthorizationLeaseRepository implements RuntimeAuthorizationLeaseRepository {
    private final JdbcTemplate jdbc;

    public JdbcRuntimeAuthorizationLeaseRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public Optional<RuntimeAuthorizationLease> find(String tenantId, String leaseId) {
        return jdbc.query(
                        "select * from resource_runtime_authorization_leases where tenant_id=? and lease_id=?",
                        (rs, row) -> map(rs),
                        tenantId,
                        leaseId)
                .stream()
                .findFirst();
    }

    @Override
    @Transactional
    public RuntimeAuthorizationLease insert(RuntimeAuthorizationLease draft, String correlationId) {
        String lockKey = "p4ra-runtime:" + draft.resourceRef().tenantId() + ":"
                + draft.resourceRef().resourceType() + ":" + draft.resourceRef().resourceId();
        jdbc.query("select pg_advisory_xact_lock(hashtext(?))", (rs, row) -> 0, lockKey);
        validateDecisionBinding(draft);
        Long next = jdbc.queryForObject(
                "select coalesce(max(fencing_version),0)+1 from resource_runtime_authorization_leases "
                        + "where tenant_id=? and resource_type=? and resource_id=?",
                Long.class,
                draft.resourceRef().tenantId(),
                draft.resourceRef().resourceType().name(),
                draft.resourceRef().resourceId());
        long fencingVersion = next == null ? 1 : next;
        RuntimeAuthorizationLease lease = withFencing(draft, fencingVersion);
        int inserted = jdbc.update(
                """
                insert into resource_runtime_authorization_leases(
                 tenant_id,lease_id,authorization_decision_id,descriptor_hash,resource_type,resource_id,
                 principal_type,principal_id,permission_code,assignment_id,attempt_no,
                 policy_catalog_version,policy_revision,policy_content_hash,global_epoch,tenant_epoch,principal_epoch,resource_epoch,
                 epoch_policy_catalog_version,department_tree_revision,fencing_version,issued_at,expires_at,maximum_stale_until,recheck_after,
                 lease_status,version,created_at,updated_at)
                values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                lease.resourceRef().tenantId(),
                lease.leaseId(),
                lease.authorizationDecisionId(),
                lease.descriptorHash(),
                lease.resourceRef().resourceType().name(),
                lease.resourceRef().resourceId(),
                lease.principal().principalType().name(),
                lease.principal().principalId(),
                lease.permissionCode(),
                blank(lease.assignmentId()),
                lease.attemptNo(),
                lease.issuedPolicyVersion().catalogVersion(),
                lease.issuedPolicyVersion().policyRevision(),
                lease.issuedPolicyVersion().contentHash(),
                lease.issuedEpoch().globalEpoch(),
                lease.issuedEpoch().tenantEpoch(),
                lease.issuedEpoch().principalEpoch(),
                lease.issuedEpoch().resourceEpoch(),
                lease.issuedEpoch().policyCatalogVersion(),
                lease.issuedEpoch().departmentTreeRevision(),
                lease.fencingVersion(),
                ts(lease.issuedAt()),
                ts(lease.expiresAt()),
                ts(lease.maximumStaleUntil()),
                ts(lease.recheckAfter()),
                lease.status().name(),
                lease.version(),
                ts(lease.issuedAt()),
                ts(lease.issuedAt()));
        if (inserted != 1) throw new IllegalStateException("RUNTIME_LEASE_INSERT_FAILED");
        event(lease, "ISSUED", "RUNTIME_AUTHORIZATION_LEASE_ISSUED", correlationId, lease.issuedAt());
        return lease;
    }

    @Override
    @Transactional
    public RuntimeAuthorizationLease update(
            RuntimeAuthorizationLease current,
            RuntimeLeaseStatus target,
            SecurityEpoch epoch,
            Instant maximumStaleUntil,
            Instant recheckAfter,
            String reason,
            String correlationId,
            Instant at) {
        int changed = jdbc.update(
                """
                update resource_runtime_authorization_leases
                   set lease_status=?,global_epoch=?,tenant_epoch=?,principal_epoch=?,resource_epoch=?,
                       epoch_policy_catalog_version=?,department_tree_revision=?,maximum_stale_until=?,recheck_after=?,
                       version=version+1,updated_at=?
                 where tenant_id=? and lease_id=? and version=?
                   and lease_status not in ('REVOKED','EXPIRED','FENCED','COMPLETED')
                """,
                target.name(),
                epoch.globalEpoch(),
                epoch.tenantEpoch(),
                epoch.principalEpoch(),
                epoch.resourceEpoch(),
                epoch.policyCatalogVersion(),
                epoch.departmentTreeRevision(),
                ts(maximumStaleUntil),
                ts(recheckAfter),
                ts(at),
                current.resourceRef().tenantId(),
                current.leaseId(),
                current.version());
        if (changed != 1) throw new IllegalStateException("RUNTIME_LEASE_VERSION_CONFLICT_OR_TERMINAL");
        RuntimeAuthorizationLease next = new RuntimeAuthorizationLease(
                current.leaseId(),
                current.authorizationDecisionId(),
                current.descriptorHash(),
                current.resourceRef(),
                current.principal(),
                current.permissionCode(),
                current.assignmentId(),
                current.attemptNo(),
                current.issuedPolicyVersion(),
                epoch,
                current.fencingVersion(),
                current.issuedAt(),
                current.expiresAt(),
                maximumStaleUntil,
                recheckAfter,
                target,
                current.version() + 1);
        event(next, target.name(), reason, correlationId, at);
        return next;
    }

    @Override
    public void recordCheckpoint(
            RuntimeAuthorizationLease current,
            OperationPhase phase,
            String reasonCode,
            String correlationId,
            Instant at) {
        event(current, "CHECKPOINT_" + phase.name(), reasonCode, correlationId, at);
    }

    private void validateDecisionBinding(RuntimeAuthorizationLease lease) {
        Long count = jdbc.queryForObject(
                """
                select count(*) from resource_authorization_decisions
                 where tenant_id=? and decision_id=?
                   and principal_type=? and principal_id=?
                   and permission_code=? and resource_type=? and resource_id=?
                   and effect='ALLOW' and decision_mode='FORMAL' and not shadow_only
                   and descriptor_hash=?
                   and policy_catalog_version=? and policy_revision=? and policy_content_hash=?
                   and global_security_epoch=? and tenant_security_epoch=? and principal_security_epoch=?
                   and resource_security_epoch=? and department_tree_revision=?
                """,
                Long.class,
                lease.resourceRef().tenantId(),
                lease.authorizationDecisionId(),
                lease.principal().principalType().name(),
                lease.principal().principalId(),
                lease.permissionCode(),
                lease.resourceRef().resourceType().name(),
                lease.resourceRef().resourceId(),
                lease.descriptorHash(),
                lease.issuedPolicyVersion().catalogVersion(),
                lease.issuedPolicyVersion().policyRevision(),
                lease.issuedPolicyVersion().contentHash(),
                lease.issuedEpoch().globalEpoch(),
                lease.issuedEpoch().tenantEpoch(),
                lease.issuedEpoch().principalEpoch(),
                lease.issuedEpoch().resourceEpoch(),
                lease.issuedEpoch().departmentTreeRevision());
        if (count == null || count != 1L) {
            throw new IllegalStateException("RUNTIME_LEASE_DECISION_BINDING_MISMATCH");
        }
    }

    private void event(
            RuntimeAuthorizationLease lease,
            String eventType,
            String reason,
            String correlation,
            Instant at) {
        jdbc.update(
                """
                insert into resource_runtime_authorization_lease_events(
                 tenant_id,event_id,lease_id,authorization_decision_id,event_type,lease_status,fencing_version,
                 security_epoch_fingerprint,reason_code,correlation_id,occurred_at,created_at)
                values(?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                lease.resourceRef().tenantId(),
                "rale-" + UUID.randomUUID(),
                lease.leaseId(),
                lease.authorizationDecisionId(),
                eventType,
                lease.status().name(),
                lease.fencingVersion(),
                fingerprint(lease.issuedEpoch()),
                required(reason),
                required(correlation),
                ts(at),
                ts(at));
    }

    private static RuntimeAuthorizationLease map(ResultSet result) throws SQLException {
        ResourceRef ref = new ResourceRef(
                result.getString("tenant_id"),
                ResourceType.valueOf(result.getString("resource_type")),
                result.getString("resource_id"));
        PrincipalRef principal = new PrincipalRef(
                PrincipalRef.PrincipalType.valueOf(result.getString("principal_type")),
                result.getString("principal_id"));
        PolicyVersion policy = new PolicyVersion(
                result.getLong("policy_catalog_version"),
                result.getLong("policy_revision"),
                result.getString("policy_content_hash"));
        SecurityEpoch epoch = new SecurityEpoch(
                result.getLong("global_epoch"),
                result.getLong("tenant_epoch"),
                result.getLong("principal_epoch"),
                result.getLong("resource_epoch"),
                result.getLong("epoch_policy_catalog_version"),
                result.getLong("department_tree_revision"));
        Integer attempt = (Integer) result.getObject("attempt_no");
        return new RuntimeAuthorizationLease(
                result.getString("lease_id"),
                result.getString("authorization_decision_id"),
                result.getString("descriptor_hash"),
                ref,
                principal,
                result.getString("permission_code"),
                nullBlank(result.getString("assignment_id")),
                attempt,
                policy,
                epoch,
                result.getLong("fencing_version"),
                instant(result, "issued_at"),
                instant(result, "expires_at"),
                instant(result, "maximum_stale_until"),
                instant(result, "recheck_after"),
                RuntimeLeaseStatus.valueOf(result.getString("lease_status")),
                result.getLong("version"));
    }

    private static RuntimeAuthorizationLease withFencing(RuntimeAuthorizationLease draft, long fencingVersion) {
        return new RuntimeAuthorizationLease(
                draft.leaseId(),
                draft.authorizationDecisionId(),
                draft.descriptorHash(),
                draft.resourceRef(),
                draft.principal(),
                draft.permissionCode(),
                draft.assignmentId(),
                draft.attemptNo(),
                draft.issuedPolicyVersion(),
                draft.issuedEpoch(),
                fencingVersion,
                draft.issuedAt(),
                draft.expiresAt(),
                draft.maximumStaleUntil(),
                draft.recheckAfter(),
                draft.status(),
                draft.version());
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        return result.getTimestamp(column).toInstant();
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String nullBlank(String value) {
        return value == null ? "" : value;
    }

    private static String required(String value) {
        return value == null || value.isBlank() ? "unspecified" : value.trim();
    }

    private static String fingerprint(SecurityEpoch epoch) {
        return epoch.globalEpoch() + ":" + epoch.tenantEpoch() + ":" + epoch.principalEpoch() + ":"
                + epoch.resourceEpoch() + ":" + epoch.policyCatalogVersion() + ":"
                + epoch.departmentTreeRevision();
    }
}
