package com.opensocket.aievent.core.enforcement.activation.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import com.opensocket.aievent.core.enforcement.activation.application.SnapshotRefreshStatusRepository;
import com.opensocket.aievent.core.enforcement.activation.contract.SnapshotNodeStatus;
import com.opensocket.aievent.core.enforcement.activation.contract.SnapshotRefreshState;
import com.opensocket.aievent.core.enforcement.activation.contract.SnapshotRefreshStatus;

public final class JdbcSnapshotRefreshStatusRepository implements SnapshotRefreshStatusRepository {
    private final JdbcTemplate jdbc;
    private final JdbcInstanceControlPlaneTransaction controlPlane;
    private final String nodeId;

    public JdbcSnapshotRefreshStatusRepository(
            JdbcTemplate jdbc,
            TransactionTemplate transactions,
            String nodeId) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.controlPlane = new JdbcInstanceControlPlaneTransaction(jdbc, transactions);
        this.nodeId = normalizeNodeId(nodeId);
    }

    @Override
    public Optional<SnapshotRefreshStatus> current() {
        return controlPlane.execute(this::currentInternal);
    }

    @Override
    public void save(SnapshotRefreshStatus status, String actor, String correlation) {
        Objects.requireNonNull(status, "status");
        controlPlane.execute(() -> saveInternal(status, actor, correlation));
    }

    @Override
    public List<SnapshotNodeStatus> nodes(Instant staleBefore) {
        Objects.requireNonNull(staleBefore, "staleBefore");
        return controlPlane.execute(() -> jdbc.query(
                "select node_id,state,active_revision,attempted_revision,last_known_good_revision,"
                        + "active_checksum,failure_code,failure_message,updated_at "
                        + "from enforcement_snapshot_node_status order by node_id",
                (rs, row) -> {
                    Instant updatedAt = rs.getObject("updated_at", OffsetDateTime.class).toInstant();
                    return new SnapshotNodeStatus(
                            rs.getString("node_id"),
                            SnapshotRefreshState.valueOf(rs.getString("state")),
                            rs.getLong("active_revision"),
                            rs.getLong("attempted_revision"),
                            rs.getLong("last_known_good_revision"),
                            rs.getString("active_checksum"),
                            rs.getString("failure_code"),
                            rs.getString("failure_message"),
                            updatedAt,
                            updatedAt.isBefore(staleBefore));
                }));
    }

    @Override
    public String nodeId() {
        return nodeId;
    }

    private Optional<SnapshotRefreshStatus> currentInternal() {
        List<SnapshotRefreshStatus> rows = jdbc.query(
                "select state,active_revision,attempted_revision,last_known_good_revision,"
                        + "active_checksum,failure_code,failure_message,updated_at "
                        + "from enforcement_snapshot_node_status where node_id=?",
                (rs, row) -> new SnapshotRefreshStatus(
                        SnapshotRefreshState.valueOf(rs.getString("state")),
                        rs.getLong("active_revision"),
                        rs.getLong("attempted_revision"),
                        rs.getLong("last_known_good_revision"),
                        rs.getString("active_checksum"),
                        rs.getString("failure_code"),
                        rs.getString("failure_message"),
                        rs.getObject("updated_at", OffsetDateTime.class).toInstant()),
                nodeId);
        return rows.stream().findFirst();
    }

    private void saveInternal(SnapshotRefreshStatus status, String actor, String correlation) {
        OffsetDateTime updatedAt = OffsetDateTime.ofInstant(status.updatedAt(), ZoneOffset.UTC);
        jdbc.update(
                "insert into enforcement_snapshot_node_status("
                        + "node_id,state,active_revision,attempted_revision,last_known_good_revision,"
                        + "active_checksum,failure_code,failure_message,updated_at,updated_by,"
                        + "correlation_id,version) values(?,?,?,?,?,?,?,?,?,?,?,1) "
                        + "on conflict(node_id) do update set state=excluded.state,"
                        + "active_revision=excluded.active_revision,attempted_revision=excluded.attempted_revision,"
                        + "last_known_good_revision=excluded.last_known_good_revision,"
                        + "active_checksum=excluded.active_checksum,failure_code=excluded.failure_code,"
                        + "failure_message=excluded.failure_message,updated_at=excluded.updated_at,"
                        + "updated_by=excluded.updated_by,correlation_id=excluded.correlation_id,"
                        + "version=enforcement_snapshot_node_status.version+1",
                nodeId,
                status.state().name(),
                status.activeRevision(),
                status.attemptedRevision(),
                status.lastKnownGoodRevision(),
                status.activeChecksum(),
                status.failureCode(),
                status.failureMessage(),
                updatedAt,
                actor,
                correlation);
        jdbc.update(
                "insert into enforcement_snapshot_node_events("
                        + "node_id,state,active_revision,attempted_revision,last_known_good_revision,"
                        + "active_checksum,failure_code,failure_message,actor_id,correlation_id) "
                        + "values(?,?,?,?,?,?,?,?,?,?)",
                nodeId,
                status.state().name(),
                status.activeRevision(),
                status.attemptedRevision(),
                status.lastKnownGoodRevision(),
                status.activeChecksum(),
                status.failureCode(),
                status.failureMessage(),
                actor,
                correlation);
        // Backward-compatible singleton for older diagnostics. It is not cluster authority.
        jdbc.update(
                "insert into enforcement_snapshot_refresh_status("
                        + "singleton_id,state,active_revision,attempted_revision,last_known_good_revision,"
                        + "active_checksum,failure_code,failure_message,updated_at,updated_by,"
                        + "correlation_id,version) values('ACTIVE',?,?,?,?,?,?,?,?,?,?,1) "
                        + "on conflict(singleton_id) do update set state=excluded.state,"
                        + "active_revision=excluded.active_revision,attempted_revision=excluded.attempted_revision,"
                        + "last_known_good_revision=excluded.last_known_good_revision,"
                        + "active_checksum=excluded.active_checksum,failure_code=excluded.failure_code,"
                        + "failure_message=excluded.failure_message,updated_at=excluded.updated_at,"
                        + "updated_by=excluded.updated_by,correlation_id=excluded.correlation_id,"
                        + "version=enforcement_snapshot_refresh_status.version+1",
                status.state().name(),
                status.activeRevision(),
                status.attemptedRevision(),
                status.lastKnownGoodRevision(),
                status.activeChecksum(),
                status.failureCode(),
                status.failureMessage(),
                updatedAt,
                actor,
                correlation);
    }

    private static String normalizeNodeId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException("enforcement activation nodeId is required");
        if (normalized.length() > 128) throw new IllegalArgumentException("enforcement activation nodeId exceeds 128 characters");
        return normalized;
    }
}
