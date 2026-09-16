package com.opensocket.aievent.core.enforcement.activation.persistence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import com.opensocket.aievent.core.enforcement.activation.application.ReadinessEvidenceRepository;
import com.opensocket.aievent.core.enforcement.activation.contract.ReadinessEvidenceBinding;
import com.opensocket.aievent.core.enforcement.activation.contract.ReadinessEvidenceType;

public final class JdbcReadinessEvidenceRepository implements ReadinessEvidenceRepository {
    private final JdbcTemplate jdbc;
    private final JdbcInstanceControlPlaneTransaction controlPlane;

    public JdbcReadinessEvidenceRepository(JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.controlPlane = new JdbcInstanceControlPlaneTransaction(jdbc, transactions);
    }

    @Override
    public Optional<ReadinessEvidenceBinding> resolve(ReadinessEvidenceType type, UUID evidenceId) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(evidenceId, "evidenceId");
        return controlPlane.execute(() -> resolveInternal(type, evidenceId));
    }

    private Optional<ReadinessEvidenceBinding> resolveInternal(
            ReadinessEvidenceType type,
            UUID evidenceId) {
        return switch (type) {
            case PHASE6_ELIGIBILITY -> one(
                    "select evidence_id,source_tenant_id tenant_id,'' domain_code,status,evaluated_at,"
                            + "evaluated_at + interval '24 hours' expires_at,"
                            + "catalog_revision_id::text source_revision,"
                            + "jsonb_build_object('requiredDomains',required_domains,"
                            + "'domainEvidenceRefs',domain_evidence_refs,'catalogRevisionId',"
                            + "catalog_revision_id,'manifestId',manifest_id,'blockers',blockers)::text payload "
                            + "from permission_phase6_eligibility_evidence where evidence_id=?",
                    type,
                    evidenceId);
            case DOMAIN_READINESS -> one(
                    "select evidence_id,source_tenant_id tenant_id,domain_code,status,evaluated_at,"
                            + "evaluated_at + interval '24 hours' expires_at,"
                            + "catalog_revision_id::text source_revision,"
                            + "jsonb_build_object('windowStartedAt',window_started_at,"
                            + "'windowEndedAt',window_ended_at,'sampleCount',sample_count,"
                            + "'catalogRevisionId',catalog_revision_id,'manifestId',manifest_id,"
                            + "'blockers',blockers)::text payload "
                            + "from permission_domain_readiness_evidence where evidence_id=?",
                    type,
                    evidenceId);
            case RUNTIME_CERTIFICATION -> one(
                    "select evidence_id,'INSTANCE' tenant_id,'' domain_code,status,"
                            + "generated_at evaluated_at,generated_at + interval '72 hours' expires_at,"
                            + "source_version source_revision,jsonb_build_object('sourceVersion',source_version,"
                            + "'catalogRevisionId',catalog_revision_id,'manifestId',manifest_id,"
                            + "'evidence',evidence,'statuses',jsonb_build_array(postgresql_clean_status,"
                            + "postgresql_upgrade_status,application_context_status,admin_ui_build_status,"
                            + "playwright_status,load_test_status,pipeline_status))::text payload "
                            + "from permission_phase5_runtime_certification_evidence where evidence_id=?",
                    type,
                    evidenceId);
        };
    }

    private Optional<ReadinessEvidenceBinding> one(
            String sql,
            ReadinessEvidenceType type,
            UUID id) {
        List<ReadinessEvidenceBinding> rows = jdbc.query(sql, (rs, row) -> map(rs, type), id);
        return rows.stream().findFirst();
    }

    private static ReadinessEvidenceBinding map(ResultSet rs, ReadinessEvidenceType type)
            throws SQLException {
        UUID id = rs.getObject("evidence_id", UUID.class);
        String tenant = rs.getString("tenant_id");
        String domain = rs.getString("domain_code");
        String status = rs.getString("status");
        OffsetDateTime evaluated = rs.getObject("evaluated_at", OffsetDateTime.class);
        OffsetDateTime expires = rs.getObject("expires_at", OffsetDateTime.class);
        String revision = rs.getString("source_revision");
        String payload = rs.getString("payload");
        String canonical = type + "|" + id + "|" + tenant + "|" + domain + "|" + status
                + "|" + evaluated + "|" + revision + "|" + payload;
        return new ReadinessEvidenceBinding(
                id,
                type,
                tenant,
                domain,
                status,
                sha256(canonical),
                evaluated.toInstant(),
                expires == null ? null : expires.toInstant(),
                revision,
                payload);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
