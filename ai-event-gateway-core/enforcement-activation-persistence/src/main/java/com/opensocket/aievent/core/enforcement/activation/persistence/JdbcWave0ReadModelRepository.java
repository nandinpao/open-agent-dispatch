package com.opensocket.aievent.core.enforcement.activation.persistence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import com.opensocket.aievent.core.enforcement.activation.application.Wave0ReadModelRepository;
import com.opensocket.aievent.core.enforcement.activation.contract.ReadinessEvidenceType;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0AdminSummaryView;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0PermissionCatalogView;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotSource;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadinessEvidenceView;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0RuntimeNodeView;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0RuntimeStatusView;

public final class JdbcWave0ReadModelRepository implements Wave0ReadModelRepository {
    private final JdbcTemplate jdbc;
    private final JdbcInstanceControlPlaneTransaction controlPlane;
    private final Clock clock;
    private final Duration staleAfter;

    public JdbcWave0ReadModelRepository(
            JdbcTemplate jdbc,
            TransactionTemplate transactions,
            Clock clock,
            Duration staleAfter) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.controlPlane = new JdbcInstanceControlPlaneTransaction(jdbc, transactions);
        this.clock = Objects.requireNonNull(clock, "clock");
        this.staleAfter = Objects.requireNonNull(staleAfter, "staleAfter");
    }

    @Override public Wave0RuntimeStatusView runtimeStatus(Wave0ReadPilotSource source) {
        return controlPlane.execute(() -> source == Wave0ReadPilotSource.LEGACY
                ? runtimeFromTables() : runtimeFromTargetViews());
    }

    @Override public Optional<Wave0ReadinessEvidenceView> readinessEvidence(
            Wave0ReadPilotSource source,
            ReadinessEvidenceType type,
            UUID evidenceId) {
        return controlPlane.execute(() -> source == Wave0ReadPilotSource.LEGACY
                ? readinessFromTables(type, evidenceId) : readinessFromTargetView(type, evidenceId));
    }

    @Override public Optional<Wave0PermissionCatalogView> activePermissionCatalog(Wave0ReadPilotSource source) {
        return controlPlane.execute(() -> catalog(source == Wave0ReadPilotSource.LEGACY
                ? "select r.revision_id,r.revision_code,r.revision_number,r.status,r.content_hash,"
                        + "(select count(*) from permission_catalog_revision_entries e where e.revision_id=r.revision_id) entry_count,"
                        + "(select count(*) from permission_catalog_revision_aliases a where a.revision_id=r.revision_id) alias_count,r.published_at "
                        + "from permission_catalog_active_revision active join permission_catalog_revisions r on r.revision_id=active.revision_id where active.singleton_id='ACTIVE'"
                : "select revision_id,revision_code,revision_number,status,content_hash,entry_count,alias_count,published_at from enforcement_wave0_permission_catalog_read_v1"));
    }

    @Override public Wave0AdminSummaryView nonSensitiveAdminSummary(Wave0ReadPilotSource source) {
        return controlPlane.execute(() -> {
            String sql = source == Wave0ReadPilotSource.LEGACY
                    ? "select (select count(*) from enforcement_authority_revisions where status='PUBLISHED') published_authority_revisions,"
                            + "(select count(*) from enforcement_cutover_plans) cutover_plans,"
                            + "(select count(*) from permission_definitions where active=true) active_permissions,"
                            + "(select count(*) from enforcement_snapshot_node_status) runtime_nodes,"
                            + "(select count(*) from enforcement_snapshot_node_status where state='FAILED') failed_runtime_nodes"
                    : "select published_authority_revisions,cutover_plans,active_permissions,runtime_nodes,failed_runtime_nodes from enforcement_wave0_admin_summary_read_v1";
            List<Wave0AdminSummaryView> rows = jdbc.query(sql, (rs, row) -> new Wave0AdminSummaryView(
                    rs.getLong("published_authority_revisions"), rs.getLong("cutover_plans"),
                    rs.getLong("active_permissions"), rs.getLong("runtime_nodes"),
                    rs.getLong("failed_runtime_nodes"), clock.instant()));
            return rows.isEmpty() ? new Wave0AdminSummaryView(0, 0, 0, 0, 0, clock.instant()) : rows.getFirst();
        });
    }

    private Wave0RuntimeStatusView runtimeFromTables() {
        return runtime(
                "select target_revision,target_checksum,generation from enforcement_authority_activation_target where singleton_id='ACTIVE'",
                "select node_id,state,active_revision,last_known_good_revision,active_checksum,updated_at from enforcement_snapshot_node_status order by node_id");
    }

    private Wave0RuntimeStatusView runtimeFromTargetViews() {
        return runtime(
                "select target_revision,target_checksum,generation from enforcement_wave0_runtime_target_read_v1",
                "select node_id,state,active_revision,last_known_good_revision,active_checksum,updated_at from enforcement_wave0_runtime_node_read_v1 order by node_id");
    }

    private Wave0RuntimeStatusView runtime(String targetSql, String nodeSql) {
        List<TargetRow> targets = jdbc.query(targetSql, (rs, row) -> new TargetRow(
                rs.getLong("target_revision"), rs.getString("target_checksum"), rs.getLong("generation")));
        TargetRow target = targets.isEmpty() ? new TargetRow(0, "BOOTSTRAP_LEGACY_ONLY", 0) : targets.getFirst();
        Instant now = clock.instant();
        Instant staleBefore = now.minus(staleAfter);
        List<Wave0RuntimeNodeView> nodes = jdbc.query(nodeSql, (rs, row) -> {
            Instant updatedAt = rs.getObject("updated_at", OffsetDateTime.class).toInstant();
            return new Wave0RuntimeNodeView(rs.getString("node_id"), rs.getString("state"),
                    rs.getLong("active_revision"), rs.getLong("last_known_good_revision"),
                    rs.getString("active_checksum"), updatedAt.isBefore(staleBefore), updatedAt);
        });
        int stale = (int) nodes.stream().filter(Wave0RuntimeNodeView::stale).count();
        int failed = (int) nodes.stream().filter(node -> "FAILED".equals(node.state())).count();
        int healthy = (int) nodes.stream().filter(node -> !node.stale() && "APPLIED".equals(node.state())
                && node.activeRevision() == target.revision() && node.activeChecksum().equals(target.checksum())).count();
        boolean converged = !nodes.isEmpty() && stale == 0 && failed == 0 && healthy == nodes.size();
        return new Wave0RuntimeStatusView(target.revision(), target.checksum(), target.generation(), nodes,
                healthy, failed, stale, converged, now);
    }

    private Optional<Wave0ReadinessEvidenceView> readinessFromTables(ReadinessEvidenceType type, UUID evidenceId) {
        String sql = switch (type) {
            case PHASE6_ELIGIBILITY -> "select evidence_id,source_tenant_id tenant_id,'' domain_code,status,evaluated_at,evaluated_at+interval '24 hours' expires_at,catalog_revision_id::text source_revision,jsonb_build_object('requiredDomains',required_domains,'domainEvidenceRefs',domain_evidence_refs,'catalogRevisionId',catalog_revision_id,'manifestId',manifest_id,'blockers',blockers)::text payload from permission_phase6_eligibility_evidence where evidence_id=?";
            case DOMAIN_READINESS -> "select evidence_id,source_tenant_id tenant_id,domain_code,status,evaluated_at,evaluated_at+interval '24 hours' expires_at,catalog_revision_id::text source_revision,jsonb_build_object('windowStartedAt',window_started_at,'windowEndedAt',window_ended_at,'sampleCount',sample_count,'catalogRevisionId',catalog_revision_id,'manifestId',manifest_id,'blockers',blockers)::text payload from permission_domain_readiness_evidence where evidence_id=?";
            case RUNTIME_CERTIFICATION -> "select evidence_id,'INSTANCE' tenant_id,'' domain_code,status,generated_at evaluated_at,generated_at+interval '72 hours' expires_at,source_version source_revision,jsonb_build_object('sourceVersion',source_version,'catalogRevisionId',catalog_revision_id,'manifestId',manifest_id,'evidence',evidence,'statuses',jsonb_build_array(postgresql_clean_status,postgresql_upgrade_status,application_context_status,admin_ui_build_status,playwright_status,load_test_status,pipeline_status))::text payload from permission_phase5_runtime_certification_evidence where evidence_id=?";
        };
        return readiness(sql, type, evidenceId);
    }

    private Optional<Wave0ReadinessEvidenceView> readinessFromTargetView(ReadinessEvidenceType type, UUID evidenceId) {
        return readiness(
                "select evidence_id,tenant_id,domain_code,status,evaluated_at,expires_at,source_revision,payload "
                        + "from enforcement_wave0_readiness_evidence_read_v1 where evidence_type=? and evidence_id=?",
                type, type.name(), evidenceId);
    }

    private Optional<Wave0ReadinessEvidenceView> readiness(String sql, ReadinessEvidenceType type, Object... args) {
        List<Wave0ReadinessEvidenceView> rows = jdbc.query(sql, (rs, row) -> mapEvidence(rs, type), args);
        return rows.stream().findFirst();
    }

    private static Wave0ReadinessEvidenceView mapEvidence(ResultSet rs, ReadinessEvidenceType type) throws SQLException {
        UUID id = rs.getObject("evidence_id", UUID.class);
        String tenant = rs.getString("tenant_id");
        String domain = rs.getString("domain_code");
        String status = rs.getString("status");
        OffsetDateTime evaluated = rs.getObject("evaluated_at", OffsetDateTime.class);
        OffsetDateTime expires = rs.getObject("expires_at", OffsetDateTime.class);
        String revision = rs.getString("source_revision");
        String payload = rs.getString("payload");
        String canonical = type + "|" + id + "|" + tenant + "|" + domain + "|" + status + "|" + evaluated + "|" + revision + "|" + payload;
        return new Wave0ReadinessEvidenceView(id, type, tenant, domain, status, sha256(canonical),
                evaluated.toInstant(), expires == null ? null : expires.toInstant(), revision);
    }

    private Optional<Wave0PermissionCatalogView> catalog(String sql) {
        List<Wave0PermissionCatalogView> rows = jdbc.query(sql, (rs, row) -> new Wave0PermissionCatalogView(
                rs.getObject("revision_id", UUID.class), rs.getString("revision_code"),
                rs.getLong("revision_number"), rs.getString("status"), rs.getString("content_hash"),
                rs.getInt("entry_count"), rs.getInt("alias_count"), instant(rs, "published_at")));
        return rows.stream().findFirst();
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private record TargetRow(long revision, String checksum, long generation) {}
}
