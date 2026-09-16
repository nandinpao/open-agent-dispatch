package com.opensocket.aievent.core.enforcement.activation.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import com.opensocket.aievent.core.enforcement.activation.application.Wave0ReadPilotException;
import com.opensocket.aievent.core.enforcement.activation.application.Wave0ReadPilotRepository;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityMode;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityPlane;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotEntryPoint;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotGateState;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotGateStatus;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotMetricSummary;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotMismatchCategory;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotObservation;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotPolicy;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotSource;

public final class JdbcWave0ReadPilotRepository implements Wave0ReadPilotRepository {
    private final JdbcTemplate jdbc;
    private final JdbcInstanceControlPlaneTransaction controlPlane;

    public JdbcWave0ReadPilotRepository(JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.controlPlane = new JdbcInstanceControlPlaneTransaction(jdbc, transactions);
    }

    @Override public Wave0ReadPilotPolicy policy(Wave0ReadPilotEntryPoint entryPoint) {
        return controlPlane.execute(() -> onePolicy(entryPoint));
    }

    @Override public List<Wave0ReadPilotPolicy> policies() {
        return controlPlane.execute(() -> jdbc.query(
                "select entry_point_id,enabled,compare_legacy_canary,minimum_samples,"
                        + "maximum_mismatch_basis_points,maximum_target_error_basis_points,"
                        + "maximum_target_p95_ms,observation_window_seconds,auto_pause,version "
                        + "from enforcement_wave0_read_policies order by entry_point_id",
                JdbcWave0ReadPilotRepository::mapPolicy));
    }

    @Override public Wave0ReadPilotGateStatus gate(Wave0ReadPilotEntryPoint entryPoint) {
        return controlPlane.execute(() -> oneGate(entryPoint));
    }

    @Override public List<Wave0ReadPilotGateStatus> gates() {
        return controlPlane.execute(() -> jdbc.query(
                "select entry_point_id,state,reason_code,changed_by,changed_at,version "
                        + "from enforcement_wave0_read_gate_state order by entry_point_id",
                JdbcWave0ReadPilotRepository::mapGate));
    }

    @Override public void saveObservation(Wave0ReadPilotObservation value) {
        Objects.requireNonNull(value, "value");
        controlPlane.execute(() -> {
            jdbc.update(
                    "insert into enforcement_wave0_read_observations("
                            + "observation_id,entry_point_id,tenant_id,authority_revision,authority_mode,"
                            + "selected_plane,served_by,shadow_compared,fallback_used,mismatch_category,"
                            + "legacy_fingerprint,target_fingerprint,legacy_duration_micros,target_duration_micros,"
                            + "legacy_error_code,target_error_code,correlation_id,observed_at) "
                            + "values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    value.observationId(), value.entryPoint().name(), value.tenantId(), value.authorityRevision(),
                    value.authorityMode().name(), value.selectedPlane().name(), value.servedBy().name(),
                    value.shadowCompared(), value.fallbackUsed(), value.mismatchCategory().name(),
                    value.legacyFingerprint(), value.targetFingerprint(), value.legacyDurationMicros(),
                    value.targetDurationMicros(), value.legacyErrorCode(), value.targetErrorCode(),
                    value.correlationId(), OffsetDateTime.ofInstant(value.observedAt(), ZoneOffset.UTC));
            jdbc.update(
                    "insert into enforcement_wave0_read_metrics("
                            + "bucket_start,entry_point_id,sample_count,compared_count,mismatch_count,"
                            + "target_error_count,legacy_duration_micros_total,target_duration_micros_total,"
                            + "target_duration_micros_max,updated_at) values(date_trunc('minute',?::timestamptz),?,?,?,?,?,?,?,?,now()) "
                            + "on conflict(bucket_start,entry_point_id) do update set "
                            + "sample_count=enforcement_wave0_read_metrics.sample_count+excluded.sample_count,"
                            + "compared_count=enforcement_wave0_read_metrics.compared_count+excluded.compared_count,"
                            + "mismatch_count=enforcement_wave0_read_metrics.mismatch_count+excluded.mismatch_count,"
                            + "target_error_count=enforcement_wave0_read_metrics.target_error_count+excluded.target_error_count,"
                            + "legacy_duration_micros_total=enforcement_wave0_read_metrics.legacy_duration_micros_total+excluded.legacy_duration_micros_total,"
                            + "target_duration_micros_total=enforcement_wave0_read_metrics.target_duration_micros_total+excluded.target_duration_micros_total,"
                            + "target_duration_micros_max=greatest(enforcement_wave0_read_metrics.target_duration_micros_max,excluded.target_duration_micros_max),"
                            + "updated_at=excluded.updated_at",
                    OffsetDateTime.ofInstant(value.observedAt(), ZoneOffset.UTC), value.entryPoint().name(), 1L,
                    value.shadowCompared() ? 1L : 0L,
                    isMismatch(value.mismatchCategory()) ? 1L : 0L,
                    isTargetError(value.mismatchCategory()) ? 1L : 0L,
                    value.legacyDurationMicros(), value.targetDurationMicros(), value.targetDurationMicros());
        });
    }

    @Override public List<Wave0ReadPilotObservation> observations(Wave0ReadPilotEntryPoint entryPoint, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return controlPlane.execute(() -> jdbc.query(
                "select observation_id,entry_point_id,tenant_id,authority_revision,authority_mode,selected_plane,"
                        + "served_by,shadow_compared,fallback_used,mismatch_category,legacy_fingerprint,"
                        + "target_fingerprint,legacy_duration_micros,target_duration_micros,legacy_error_code,"
                        + "target_error_code,correlation_id,observed_at from enforcement_wave0_read_observations "
                        + "where entry_point_id=? order by observed_at desc limit ?",
                JdbcWave0ReadPilotRepository::mapObservation, entryPoint.name(), safeLimit));
    }

    @Override public Wave0ReadPilotMetricSummary metrics(
            Wave0ReadPilotEntryPoint entryPoint,
            Instant windowStartedAt,
            Instant evaluatedAt) {
        Objects.requireNonNull(windowStartedAt, "windowStartedAt");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        return controlPlane.execute(() -> {
            List<MetricRow> rows = jdbc.query(
                    "select count(*) sample_count,"
                            + "count(*) filter(where shadow_compared) compared_count,"
                            + "count(*) filter(where mismatch_category not in('MATCH','NOT_COMPARED')) mismatch_count,"
                            + "count(*) filter(where mismatch_category in('TARGET_ERROR','BOTH_ERROR')) target_error_count,"
                            + "coalesce(percentile_cont(0.95) within group(order by target_duration_micros) "
                            + "filter(where target_duration_micros>0),0)::bigint target_p95_micros "
                            + "from enforcement_wave0_read_observations where entry_point_id=? and observed_at>=? and observed_at<=?",
                    (rs, row) -> new MetricRow(
                            rs.getLong("sample_count"), rs.getLong("compared_count"),
                            rs.getLong("mismatch_count"), rs.getLong("target_error_count"),
                            rs.getLong("target_p95_micros")),
                    entryPoint.name(), OffsetDateTime.ofInstant(windowStartedAt, ZoneOffset.UTC),
                    OffsetDateTime.ofInstant(evaluatedAt, ZoneOffset.UTC));
            MetricRow row = rows.isEmpty() ? new MetricRow(0, 0, 0, 0, 0) : rows.getFirst();
            return new Wave0ReadPilotMetricSummary(entryPoint, row.samples(), row.compared(), row.mismatches(),
                    row.targetErrors(), basisPoints(row.mismatches(), row.samples()),
                    basisPoints(row.targetErrors(), row.samples()), row.targetP95Micros() / 1_000L,
                    windowStartedAt, evaluatedAt);
        });
    }

    @Override public Wave0ReadPilotGateStatus transition(
            Wave0ReadPilotEntryPoint entryPoint,
            long expectedVersion,
            Wave0ReadPilotGateState state,
            String reasonCode,
            String actorId,
            String correlationId,
            Instant changedAt) {
        return controlPlane.execute(() -> {
            Wave0ReadPilotGateStatus previous = oneGate(entryPoint);
            if (previous.version() != expectedVersion) {
                throw new Wave0ReadPilotException("WAVE0_GATE_VERSION_CONFLICT", "Wave 0 gate version conflict");
            }
            int updated = jdbc.update(
                    "update enforcement_wave0_read_gate_state set state=?,reason_code=?,changed_by=?,"
                            + "changed_at=?,version=version+1 where entry_point_id=? and version=?",
                    state.name(), reasonCode, actorId, OffsetDateTime.ofInstant(changedAt, ZoneOffset.UTC),
                    entryPoint.name(), expectedVersion);
            if (updated != 1) throw new Wave0ReadPilotException("WAVE0_GATE_VERSION_CONFLICT", "Wave 0 gate version conflict");
            jdbc.update(
                    "insert into enforcement_wave0_read_gate_events(entry_point_id,event_type,from_state,to_state,"
                            + "reason_code,actor_id,correlation_id,occurred_at) values(?,?,?,?,?,?,?,?)",
                    entryPoint.name(), "STATE_TRANSITION", previous.state().name(), state.name(), reasonCode, actorId,
                    correlationId, OffsetDateTime.ofInstant(changedAt, ZoneOffset.UTC));
            return oneGate(entryPoint);
        });
    }

    private Wave0ReadPilotPolicy onePolicy(Wave0ReadPilotEntryPoint entryPoint) {
        List<Wave0ReadPilotPolicy> rows = jdbc.query(
                "select entry_point_id,enabled,compare_legacy_canary,minimum_samples,"
                        + "maximum_mismatch_basis_points,maximum_target_error_basis_points,"
                        + "maximum_target_p95_ms,observation_window_seconds,auto_pause,version "
                        + "from enforcement_wave0_read_policies where entry_point_id=?",
                JdbcWave0ReadPilotRepository::mapPolicy, entryPoint.name());
        if (rows.isEmpty()) throw new Wave0ReadPilotException("WAVE0_POLICY_NOT_FOUND", "Wave 0 read policy was not found");
        return rows.getFirst();
    }

    private Wave0ReadPilotGateStatus oneGate(Wave0ReadPilotEntryPoint entryPoint) {
        List<Wave0ReadPilotGateStatus> rows = jdbc.query(
                "select entry_point_id,state,reason_code,changed_by,changed_at,version "
                        + "from enforcement_wave0_read_gate_state where entry_point_id=?",
                JdbcWave0ReadPilotRepository::mapGate, entryPoint.name());
        if (rows.isEmpty()) throw new Wave0ReadPilotException("WAVE0_GATE_NOT_FOUND", "Wave 0 read gate was not found");
        return rows.getFirst();
    }

    private static Wave0ReadPilotPolicy mapPolicy(ResultSet rs, int row) throws SQLException {
        return new Wave0ReadPilotPolicy(
                Wave0ReadPilotEntryPoint.valueOf(rs.getString("entry_point_id")),
                rs.getBoolean("enabled"), rs.getBoolean("compare_legacy_canary"),
                rs.getLong("minimum_samples"), rs.getInt("maximum_mismatch_basis_points"),
                rs.getInt("maximum_target_error_basis_points"), rs.getLong("maximum_target_p95_ms"),
                Duration.ofSeconds(rs.getLong("observation_window_seconds")), rs.getBoolean("auto_pause"),
                rs.getLong("version"));
    }

    private static Wave0ReadPilotGateStatus mapGate(ResultSet rs, int row) throws SQLException {
        return new Wave0ReadPilotGateStatus(
                Wave0ReadPilotEntryPoint.valueOf(rs.getString("entry_point_id")),
                Wave0ReadPilotGateState.valueOf(rs.getString("state")), rs.getString("reason_code"),
                rs.getString("changed_by"), rs.getObject("changed_at", OffsetDateTime.class).toInstant(),
                rs.getLong("version"));
    }

    private static Wave0ReadPilotObservation mapObservation(ResultSet rs, int row) throws SQLException {
        return new Wave0ReadPilotObservation(
                rs.getObject("observation_id", java.util.UUID.class),
                Wave0ReadPilotEntryPoint.valueOf(rs.getString("entry_point_id")), rs.getString("tenant_id"),
                rs.getLong("authority_revision"), AuthorityMode.valueOf(rs.getString("authority_mode")),
                AuthorityPlane.valueOf(rs.getString("selected_plane")),
                Wave0ReadPilotSource.valueOf(rs.getString("served_by")), rs.getBoolean("shadow_compared"),
                rs.getBoolean("fallback_used"), Wave0ReadPilotMismatchCategory.valueOf(rs.getString("mismatch_category")),
                rs.getString("legacy_fingerprint"), rs.getString("target_fingerprint"),
                rs.getLong("legacy_duration_micros"), rs.getLong("target_duration_micros"),
                rs.getString("legacy_error_code"), rs.getString("target_error_code"),
                rs.getString("correlation_id"), rs.getObject("observed_at", OffsetDateTime.class).toInstant());
    }

    private static boolean isMismatch(Wave0ReadPilotMismatchCategory category) {
        return category != Wave0ReadPilotMismatchCategory.MATCH && category != Wave0ReadPilotMismatchCategory.NOT_COMPARED;
    }

    private static boolean isTargetError(Wave0ReadPilotMismatchCategory category) {
        return category == Wave0ReadPilotMismatchCategory.TARGET_ERROR || category == Wave0ReadPilotMismatchCategory.BOTH_ERROR;
    }

    private static int basisPoints(long numerator, long denominator) {
        if (denominator <= 0) return 0;
        return (int) Math.min(10_000L, Math.round((numerator * 10_000.0d) / denominator));
    }

    private record MetricRow(long samples, long compared, long mismatches, long targetErrors, long targetP95Micros) {}
}
