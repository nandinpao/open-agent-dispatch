package com.opensocket.aievent.core.enforcement.activation.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import com.opensocket.aievent.core.enforcement.activation.application.TaskReadCertificationRepository;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityMode;
import com.opensocket.aievent.core.enforcement.activation.contract.TaskReadCertificationEvidence;
import com.opensocket.aievent.core.enforcement.activation.contract.TaskReadCertificationStatus;

public final class JdbcTaskReadCertificationRepository implements TaskReadCertificationRepository {
    private final JdbcTemplate jdbc;
    private final JdbcInstanceControlPlaneTransaction controlPlane;

    public JdbcTaskReadCertificationRepository(JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.controlPlane = new JdbcInstanceControlPlaneTransaction(jdbc, transactions);
    }

    @Override
    public TaskReadCertificationEvidence save(TaskReadCertificationEvidence value) {
        Objects.requireNonNull(value, "value");
        return controlPlane.execute(() -> {
            int inserted = jdbc.update(
                    "insert into enforcement_task_read_certification_runs("
                            + "certification_id,tenant_id,authority_revision,route_mode,sample_count,"
                            + "deterministic_order_status,cursor_pagination_status,n_plus_one_status,"
                            + "force_rls_status,sensitive_field_masking_status,fallback_pause_status,"
                            + "load_test_status,overall_status,evidence,idempotency_key,request_hash,"
                            + "certified_by,correlation_id,certified_at) "
                            + "values(?,?,?,?,?,?,?,?,?,?,?,?,?,cast(? as jsonb),?,?,?,?,?) "
                            + "on conflict(tenant_id,idempotency_key) do nothing",
                    value.certificationId(), value.tenantId(), value.authorityRevision(), value.routeMode().name(),
                    value.sampleCount(), value.deterministicOrderStatus().name(),
                    value.cursorPaginationStatus().name(), value.nPlusOneStatus().name(),
                    value.forceRlsStatus().name(), value.sensitiveFieldMaskingStatus().name(),
                    value.fallbackPauseStatus().name(), value.loadTestStatus().name(), value.overallStatus().name(),
                    value.evidenceJson(), value.idempotencyKey(), value.requestHash(), value.certifiedBy(),
                    value.correlationId(), OffsetDateTime.ofInstant(value.certifiedAt(), ZoneOffset.UTC));
            TaskReadCertificationEvidence persisted = inserted == 1 ? one(value.certificationId())
                    : one(value.tenantId(), value.idempotencyKey());
            if (!persisted.requestHash().equals(value.requestHash())) {
                throw new IllegalStateException("TASK_READ_CERTIFICATION_IDEMPOTENCY_CONFLICT");
            }
            return persisted;
        });
    }

    @Override
    public List<TaskReadCertificationEvidence> recent(String tenantId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return controlPlane.execute(() -> jdbc.query(
                "select certification_id,tenant_id,authority_revision,route_mode,sample_count,"
                        + "deterministic_order_status,cursor_pagination_status,n_plus_one_status,force_rls_status,"
                        + "sensitive_field_masking_status,fallback_pause_status,load_test_status,overall_status,"
                        + "evidence::text evidence_json,idempotency_key,request_hash,certified_by,correlation_id,certified_at "
                        + "from enforcement_task_read_certification_runs where tenant_id=? "
                        + "order by certified_at desc limit ?",
                JdbcTaskReadCertificationRepository::map, tenantId, safeLimit));
    }

    private TaskReadCertificationEvidence one(java.util.UUID id) {
        return jdbc.query(
                "select certification_id,tenant_id,authority_revision,route_mode,sample_count,"
                        + "deterministic_order_status,cursor_pagination_status,n_plus_one_status,force_rls_status,"
                        + "sensitive_field_masking_status,fallback_pause_status,load_test_status,overall_status,"
                        + "evidence::text evidence_json,idempotency_key,request_hash,certified_by,correlation_id,certified_at "
                        + "from enforcement_task_read_certification_runs where certification_id=?",
                JdbcTaskReadCertificationRepository::map, id).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Task read certification was not persisted"));
    }

    private TaskReadCertificationEvidence one(String tenantId, String idempotencyKey) {
        return jdbc.query(
                "select certification_id,tenant_id,authority_revision,route_mode,sample_count,"
                        + "deterministic_order_status,cursor_pagination_status,n_plus_one_status,force_rls_status,"
                        + "sensitive_field_masking_status,fallback_pause_status,load_test_status,overall_status,"
                        + "evidence::text evidence_json,idempotency_key,request_hash,certified_by,correlation_id,certified_at "
                        + "from enforcement_task_read_certification_runs where tenant_id=? and idempotency_key=?",
                JdbcTaskReadCertificationRepository::map, tenantId, idempotencyKey).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Task read certification idempotency receipt was not found"));
    }

    private static TaskReadCertificationEvidence map(ResultSet rs, int row) throws SQLException {
        return new TaskReadCertificationEvidence(
                rs.getObject("certification_id", java.util.UUID.class), rs.getString("tenant_id"),
                rs.getLong("authority_revision"), AuthorityMode.valueOf(rs.getString("route_mode")),
                rs.getLong("sample_count"),
                TaskReadCertificationStatus.valueOf(rs.getString("deterministic_order_status")),
                TaskReadCertificationStatus.valueOf(rs.getString("cursor_pagination_status")),
                TaskReadCertificationStatus.valueOf(rs.getString("n_plus_one_status")),
                TaskReadCertificationStatus.valueOf(rs.getString("force_rls_status")),
                TaskReadCertificationStatus.valueOf(rs.getString("sensitive_field_masking_status")),
                TaskReadCertificationStatus.valueOf(rs.getString("fallback_pause_status")),
                TaskReadCertificationStatus.valueOf(rs.getString("load_test_status")),
                TaskReadCertificationStatus.valueOf(rs.getString("overall_status")),
                rs.getString("evidence_json"), rs.getString("idempotency_key"), rs.getString("request_hash"),
                rs.getString("certified_by"), rs.getString("correlation_id"),
                rs.getObject("certified_at", OffsetDateTime.class).toInstant());
    }
}
