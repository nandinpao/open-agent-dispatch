package com.opensocket.aievent.core.enforcement.activation.persistence;

import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.transaction.support.TransactionTemplate;
import com.opensocket.aievent.core.enforcement.activation.application.AuthorityRevisionPublisher;
import com.opensocket.aievent.core.enforcement.activation.application.CutoverPlan;
import com.opensocket.aievent.core.enforcement.activation.application.CutoverPlanException;
import com.opensocket.aievent.core.enforcement.activation.application.PublishedAuthorityRevision;
import com.opensocket.aievent.core.enforcement.activation.contract.CutoverPlanRoute;
import com.opensocket.aievent.core.enforcement.activation.core.AuthoritySnapshotFactory;

public final class JdbcAuthorityRevisionPublisher implements AuthorityRevisionPublisher {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final AuthoritySnapshotFactory snapshots;

    public JdbcAuthorityRevisionPublisher(
            JdbcTemplate jdbc,
            TransactionTemplate transactions,
            AuthoritySnapshotFactory snapshots) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
    }

    @Override
    public PublishedAuthorityRevision publish(
            CutoverPlan plan,
            String actor,
            String reason,
            String correlation,
            String idempotencyKey,
            String requestHash,
            Instant now) {
        PublishedAuthorityRevision result = transactions.execute(status -> publishInTransaction(
                plan, actor, reason, correlation, idempotencyKey, requestHash, now));
        return Objects.requireNonNull(result, "publication transaction returned null");
    }

    private PublishedAuthorityRevision publishInTransaction(
            CutoverPlan plan,
            String actor,
            String reason,
            String correlation,
            String idempotencyKey,
            String requestHash,
            Instant now) {
        jdbc.queryForObject(
                "select set_config('app.current_tenant_id','INSTANCE',true)",
                String.class);
        List<Receipt> receipts = jdbc.query(
                "select request_hash,result_revision_id from enforcement_cutover_idempotency "
                        + "where operation_code='PUBLISH' and idempotency_key=?",
                (rs, row) -> new Receipt(rs.getString(1), rs.getLong(2)),
                idempotencyKey);
        if (!receipts.isEmpty()) {
            Receipt receipt = receipts.getFirst();
            if (!receipt.requestHash().equals(requestHash)) {
                throw new CutoverPlanException(
                        "CUTOVER_IDEMPOTENCY_CONFLICT",
                        "Idempotency key was reused with a different publish request.");
            }
            return published(receipt.revision());
        }

        Long revisionValue = jdbc.queryForObject(
                "select nextval('enforcement_authority_revision_seq')",
                Long.class);
        long revision = Objects.requireNonNull(
                revisionValue,
                "authority revision sequence returned null");

        var definitions = plan.routes().stream()
                .sorted(Comparator.comparingInt(CutoverPlanRoute::routeOrder))
                .map(CutoverPlanRoute::definition)
                .toList();
        String checksum = snapshots.checksum(revision, definitions);

        jdbc.update(
                "insert into enforcement_authority_revisions("
                        + "revision_id,status,snapshot_checksum,readiness_evidence_refs,reason,"
                        + "created_by,created_at,plan_id) "
                        + "values(?,'DRAFT',?,?::jsonb,?,?,?,?)",
                revision,
                checksum,
                evidenceJson(plan),
                reason,
                plan.createdBy(),
                at(now),
                plan.planId());

        for (CutoverPlanRoute route : plan.routes()) {
            insertRoute(revision, route, now);
        }

        int approved = jdbc.update(
                "update enforcement_authority_revisions "
                        + "set status='APPROVED',approved_by=?,approved_at=? "
                        + "where revision_id=? and status='DRAFT'",
                plan.approvedBy(),
                at(plan.approvedAt()),
                revision);
        if (approved != 1) {
            throw new CutoverPlanException(
                    "CUTOVER_REVISION_APPROVAL_FAILED",
                    "Unable to materialize approved authority revision.");
        }

        int published = jdbc.update(
                "update enforcement_authority_revisions "
                        + "set status='PUBLISHED',published_by=?,published_at=? "
                        + "where revision_id=? and status='APPROVED'",
                actor,
                at(now),
                revision);
        if (published != 1) {
            throw new CutoverPlanException(
                    "CUTOVER_REVISION_PUBLISH_FAILED",
                    "Unable to publish authority revision.");
        }

        int targetUpdated = jdbc.update(
                "insert into enforcement_authority_activation_target("
                        + "singleton_id,target_revision,target_checksum,generation,updated_at,updated_by,correlation_id) "
                        + "values('ACTIVE',?,?,1,?,?,?) "
                        + "on conflict(singleton_id) do update set "
                        + "target_revision=excluded.target_revision,target_checksum=excluded.target_checksum,"
                        + "generation=enforcement_authority_activation_target.generation+1,"
                        + "updated_at=excluded.updated_at,updated_by=excluded.updated_by,"
                        + "correlation_id=excluded.correlation_id "
                        + "where excluded.target_revision>enforcement_authority_activation_target.target_revision",
                revision,
                checksum,
                at(now),
                actor,
                correlation);
        if (targetUpdated != 1) {
            throw new CutoverPlanException(
                    "CUTOVER_ACTIVATION_TARGET_UPDATE_FAILED",
                    "Unable to advance the cluster Authority Activation target.");
        }

        int planUpdated = jdbc.update(
                "update enforcement_cutover_plans "
                        + "set status='PUBLISHED',published_revision=?,published_by=?,published_at=?,"
                        + "last_audit_reason=?,correlation_id=?,version=version+1 "
                        + "where plan_id=? and status='APPROVED' and version=?",
                revision,
                actor,
                at(now),
                reason,
                correlation,
                plan.planId(),
                plan.version());
        if (planUpdated != 1) {
            throw new CutoverPlanException(
                    "CUTOVER_PLAN_VERSION_CONFLICT",
                    "Cutover plan changed before publication.");
        }

        jdbc.update(
                "insert into enforcement_authority_events("
                        + "revision_id,event_type,previous_revision_id,actor_id,audit_reason,correlation_id,details) "
                        + "values(?,'REVISION_PUBLISHED',"
                        + "(select max(revision_id) from enforcement_authority_revisions "
                        + "where status='PUBLISHED' and revision_id<>?),?,?,?,"
                        + "jsonb_build_object('planId',?::text,'checksum',?))",
                revision,
                revision,
                actor,
                reason,
                correlation,
                plan.planId(),
                checksum);
        jdbc.update(
                "insert into enforcement_cutover_plan_events("
                        + "plan_id,event_type,actor_id,audit_reason,correlation_id,details) "
                        + "values(?,'PLAN_PUBLISHED',?,?,?,jsonb_build_object('revision',?,'checksum',?))",
                plan.planId(),
                actor,
                reason,
                correlation,
                revision,
                checksum);
        jdbc.update(
                "insert into enforcement_cutover_idempotency("
                        + "operation_code,idempotency_key,request_hash,result_plan_id,result_revision_id) "
                        + "values('PUBLISH',?,?,?,?)",
                idempotencyKey,
                requestHash,
                plan.planId(),
                revision);

        return new PublishedAuthorityRevision(revision, checksum, now);
    }

    private PublishedAuthorityRevision published(long revision) {
        return jdbc.queryForObject(
                "select revision_id,snapshot_checksum,published_at "
                        + "from enforcement_authority_revisions "
                        + "where revision_id=? and status='PUBLISHED'",
                (rs, row) -> new PublishedAuthorityRevision(
                        rs.getLong(1),
                        rs.getString(2),
                        rs.getObject(3, OffsetDateTime.class).toInstant()),
                revision);
    }

    private void insertRoute(long revision, CutoverPlanRoute route, Instant now) {
        var definition = route.definition();
        PreparedStatementCreator statementCreator = connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "insert into enforcement_authority_routes("
                            + "revision_id,route_order,tenant_id,domain_code,unit_code,risk_lane,entry_point_id,"
                            + "authority_mode,target_basis_points,include_cohorts,exclude_cohorts,reason_code,created_at) "
                            + "values(?,?,?,?,?,?,?,?,?,?,?,?,?)");
            statement.setLong(1, revision);
            statement.setInt(2, route.routeOrder());
            statement.setString(3, definition.key().tenantId());
            statement.setString(4, definition.key().domain());
            statement.setString(5, definition.key().unit());
            statement.setString(6, definition.key().riskLane());
            statement.setString(7, definition.key().entryPoint());
            statement.setString(8, definition.mode().name());
            statement.setInt(9, definition.targetBasisPoints());
            statement.setArray(10, connection.createArrayOf("text", definition.includeCohorts().toArray()));
            statement.setArray(11, connection.createArrayOf("text", definition.excludeCohorts().toArray()));
            statement.setString(12, definition.reasonCode());
            statement.setObject(13, at(now));
            return statement;
        };
        jdbc.execute(statementCreator, PreparedStatement::executeUpdate);
    }

    private static String evidenceJson(CutoverPlan plan) {
        StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < plan.evidenceBindings().size(); index++) {
            var evidence = plan.evidenceBindings().get(index);
            if (index > 0) result.append(',');
            result.append("{\"evidenceType\":\"")
                    .append(evidence.evidenceType())
                    .append("\",\"evidenceId\":\"")
                    .append(evidence.evidenceId())
                    .append("\",\"checksum\":\"")
                    .append(evidence.checksum())
                    .append("\"}");
        }
        return result.append(']').toString();
    }

    private static OffsetDateTime at(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private record Receipt(String requestHash, long revision) {}
}
