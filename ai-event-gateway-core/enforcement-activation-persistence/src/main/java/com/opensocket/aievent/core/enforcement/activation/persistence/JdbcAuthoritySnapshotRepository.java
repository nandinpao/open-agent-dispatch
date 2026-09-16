package com.opensocket.aievent.core.enforcement.activation.persistence;

import java.sql.Array;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityMode;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityRouteDefinition;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityRouteKey;
import com.opensocket.aievent.core.enforcement.activation.core.AuthoritySnapshotData;
import com.opensocket.aievent.core.enforcement.activation.core.AuthoritySnapshotRepository;

public final class JdbcAuthoritySnapshotRepository implements AuthoritySnapshotRepository {
    private final JdbcTemplate jdbc;
    private final JdbcInstanceControlPlaneTransaction controlPlane;

    public JdbcAuthoritySnapshotRepository(JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.controlPlane = new JdbcInstanceControlPlaneTransaction(jdbc, transactions);
    }

    @Override
    public Optional<AuthoritySnapshotData> findLatestPublished() {
        return controlPlane.execute(this::findLatestPublishedInternal);
    }

    @Override
    public Optional<AuthoritySnapshotData> findPublished(long revision) {
        return controlPlane.execute(() -> findPublishedInternal(revision));
    }

    private Optional<AuthoritySnapshotData> findLatestPublishedInternal() {
        List<RevisionRow> revisions = jdbc.query(
                "select revision_id,snapshot_checksum,published_at "
                        + "from enforcement_authority_revisions "
                        + "where status='PUBLISHED' order by revision_id desc limit 1",
                (rs, row) -> new RevisionRow(
                        rs.getLong("revision_id"),
                        rs.getString("snapshot_checksum"),
                        rs.getObject("published_at", OffsetDateTime.class)));
        return revisions.isEmpty() ? Optional.empty() : load(revisions.getFirst());
    }

    private Optional<AuthoritySnapshotData> findPublishedInternal(long revision) {
        List<RevisionRow> revisions = jdbc.query(
                "select revision_id,snapshot_checksum,published_at "
                        + "from enforcement_authority_revisions "
                        + "where status='PUBLISHED' and revision_id=?",
                (rs, row) -> new RevisionRow(
                        rs.getLong("revision_id"),
                        rs.getString("snapshot_checksum"),
                        rs.getObject("published_at", OffsetDateTime.class)),
                revision);
        return revisions.isEmpty() ? Optional.empty() : load(revisions.getFirst());
    }

    private Optional<AuthoritySnapshotData> load(RevisionRow revision) {
        List<AuthorityRouteDefinition> routes = jdbc.query(
                "select tenant_id,domain_code,unit_code,risk_lane,entry_point_id,authority_mode,"
                        + "target_basis_points,include_cohorts,exclude_cohorts,reason_code "
                        + "from enforcement_authority_routes where revision_id=? "
                        + "order by route_order,tenant_id,domain_code,unit_code,risk_lane,entry_point_id",
                (rs, row) -> new AuthorityRouteDefinition(
                        new AuthorityRouteKey(
                                rs.getString("tenant_id"),
                                rs.getString("domain_code"),
                                rs.getString("unit_code"),
                                rs.getString("risk_lane"),
                                rs.getString("entry_point_id")),
                        AuthorityMode.valueOf(rs.getString("authority_mode")),
                        rs.getInt("target_basis_points"),
                        sqlArray(rs.getArray("include_cohorts")),
                        sqlArray(rs.getArray("exclude_cohorts")),
                        rs.getString("reason_code")),
                revision.id());
        return Optional.of(new AuthoritySnapshotData(
                revision.id(),
                revision.publishedAt().toInstant(),
                revision.checksum(),
                routes));
    }

    private static Set<String> sqlArray(Array array) throws SQLException {
        if (array == null) {
            return Set.of();
        }
        Object raw = array.getArray();
        if (!(raw instanceof Object[] values)) {
            return Set.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Object value : values) {
            if (value != null && !String.valueOf(value).isBlank()) {
                result.add(String.valueOf(value));
            }
        }
        return Set.copyOf(result);
    }

    private record RevisionRow(long id, String checksum, OffsetDateTime publishedAt) {}
}
