package com.opensocket.aievent.core.enforcement.activation.persistence;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.transaction.support.TransactionTemplate;
import com.opensocket.aievent.core.enforcement.activation.application.CutoverPlan;
import com.opensocket.aievent.core.enforcement.activation.application.CutoverPlanException;
import com.opensocket.aievent.core.enforcement.activation.application.CutoverPlanRepository;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityMode;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityRouteDefinition;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityRouteKey;
import com.opensocket.aievent.core.enforcement.activation.contract.CutoverPlanRoute;
import com.opensocket.aievent.core.enforcement.activation.contract.CutoverPlanStatus;
import com.opensocket.aievent.core.enforcement.activation.contract.ReadinessEvidenceBinding;
import com.opensocket.aievent.core.enforcement.activation.contract.ReadinessEvidenceType;

public final class JdbcCutoverPlanRepository implements CutoverPlanRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public JdbcCutoverPlanRepository(JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    @Override
    public CutoverPlan create(
            UUID planId,
            String title,
            String description,
            String actor,
            String reason,
            String correlation,
            String idempotencyKey,
            String requestHash,
            Instant now) {
        return inTransaction(() -> idempotent("CREATE", idempotencyKey, requestHash, () -> {
            jdbc.update(
                    "insert into enforcement_cutover_plans("
                            + "plan_id,title,description,status,created_by,created_at,"
                            + "last_audit_reason,correlation_id,version) "
                            + "values(?,?,?,'DRAFT',?,?,?,?,1)",
                    planId,
                    title,
                    description,
                    actor,
                    at(now),
                    reason,
                    correlation);
            event(planId, "PLAN_CREATED", actor, reason, correlation, "{}");
            return planId;
        }));
    }

    @Override
    public Optional<CutoverPlan> find(UUID planId) {
        return inTransaction(() -> findInternal(planId));
    }

    private Optional<CutoverPlan> findInternal(UUID planId) {
        List<CutoverPlan> rows = jdbc.query(
                "select * from enforcement_cutover_plans where plan_id=?",
                (rs, row) -> mapPlan(rs),
                planId);
        if (rows.isEmpty()) return Optional.empty();
        return Optional.of(withChildren(rows.getFirst()));
    }

    @Override
    public List<CutoverPlan> list(CutoverPlanStatus status, int limit) {
        return inTransaction(() -> listInternal(status, limit));
    }

    private List<CutoverPlan> listInternal(CutoverPlanStatus status, int limit) {
        List<CutoverPlan> rows;
        if (status == null) {
            rows = jdbc.query(
                    "select * from enforcement_cutover_plans order by created_at desc limit ?",
                    (rs, row) -> mapPlan(rs),
                    limit);
        } else {
            rows = jdbc.query(
                    "select * from enforcement_cutover_plans where status=? order by created_at desc limit ?",
                    (rs, row) -> mapPlan(rs),
                    status.name(),
                    limit);
        }
        return rows.stream().map(this::withChildren).toList();
    }

    @Override
    public CutoverPlan replaceRoutes(
            UUID planId,
            long expectedVersion,
            List<CutoverPlanRoute> routes,
            String actor,
            String reason,
            String correlation,
            String idempotencyKey,
            String requestHash,
            Instant now) {
        return mutate("REPLACE_ROUTES", planId, idempotencyKey, requestHash, () -> {
            requireDraft(planId, expectedVersion);
            jdbc.update("delete from enforcement_cutover_plan_routes where plan_id=?", planId);
            for (CutoverPlanRoute route : routes) {
                insertRoute(planId, route, now);
            }
            touchDraft(planId, expectedVersion, reason, correlation);
            event(planId, "ROUTES_REPLACED", actor, reason, correlation,
                    "{\"routeCount\":" + routes.size() + "}");
        });
    }

    @Override
    public CutoverPlan bindEvidence(
            UUID planId,
            long expectedVersion,
            ReadinessEvidenceBinding binding,
            String actor,
            String reason,
            String correlation,
            String idempotencyKey,
            String requestHash,
            Instant now) {
        return mutate("BIND_EVIDENCE", planId, idempotencyKey, requestHash, () -> {
            requireDraft(planId, expectedVersion);
            int inserted = jdbc.update(
                    "insert into enforcement_cutover_evidence_bindings("
                            + "plan_id,evidence_type,evidence_id,tenant_id,domain_code,evidence_status,"
                            + "evidence_checksum,evaluated_at,expires_at,source_revision,immutable_payload,"
                            + "bound_by,bound_at) values(?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?) "
                            + "on conflict(plan_id,evidence_type,evidence_id) do nothing",
                    planId,
                    binding.evidenceType().name(),
                    binding.evidenceId(),
                    binding.tenantId(),
                    binding.domainCode(),
                    binding.status(),
                    binding.checksum(),
                    at(binding.evaluatedAt()),
                    binding.expiresAt() == null ? null : at(binding.expiresAt()),
                    binding.sourceRevision(),
                    binding.immutablePayload(),
                    actor,
                    at(now));
            if (inserted != 1) {
                throw new CutoverPlanException(
                        "CUTOVER_EVIDENCE_ALREADY_BOUND",
                        "The readiness evidence is already bound to this plan.");
            }
            touchDraft(planId, expectedVersion, reason, correlation);
            event(planId, "EVIDENCE_BOUND", actor, reason, correlation,
                    "{\"evidenceId\":\"" + binding.evidenceId() + "\"}");
        });
    }

    @Override
    public CutoverPlan submit(
            UUID planId,
            long expectedVersion,
            String actor,
            String reason,
            String correlation,
            String idempotencyKey,
            String requestHash,
            Instant now) {
        return transition(
                "SUBMIT", planId, expectedVersion, CutoverPlanStatus.DRAFT, CutoverPlanStatus.IN_REVIEW,
                actor, reason, correlation, idempotencyKey, requestHash, now,
                "submitted_by", "submitted_at", "PLAN_SUBMITTED", "");
    }

    @Override
    public CutoverPlan approve(
            UUID planId,
            long expectedVersion,
            String actor,
            String reason,
            String correlation,
            String idempotencyKey,
            String requestHash,
            Instant now) {
        return transition(
                "APPROVE", planId, expectedVersion, CutoverPlanStatus.IN_REVIEW, CutoverPlanStatus.APPROVED,
                actor, reason, correlation, idempotencyKey, requestHash, now,
                "approved_by", "approved_at", "PLAN_APPROVED", "");
    }

    @Override
    public CutoverPlan reject(
            UUID planId,
            long expectedVersion,
            String actor,
            String rejectionReason,
            String reason,
            String correlation,
            String idempotencyKey,
            String requestHash,
            Instant now) {
        return transition(
                "REJECT", planId, expectedVersion, CutoverPlanStatus.IN_REVIEW, CutoverPlanStatus.REJECTED,
                actor, reason, correlation, idempotencyKey, requestHash, now,
                "rejected_by", "rejected_at", "PLAN_REJECTED", rejectionReason);
    }

    private CutoverPlan transition(
            String operation,
            UUID planId,
            long expectedVersion,
            CutoverPlanStatus from,
            CutoverPlanStatus to,
            String actor,
            String reason,
            String correlation,
            String idempotencyKey,
            String requestHash,
            Instant now,
            String actorColumn,
            String timeColumn,
            String eventType,
            String rejectionReason) {
        return mutate(operation, planId, idempotencyKey, requestHash, () -> {
            int changed = jdbc.update(
                    "update enforcement_cutover_plans set status=?," + actorColumn + "=?," + timeColumn + "=?,"
                            + "rejection_reason=?,last_audit_reason=?,correlation_id=?,version=version+1 "
                            + "where plan_id=? and status=? and version=?",
                    to.name(),
                    actor,
                    at(now),
                    rejectionReason,
                    reason,
                    correlation,
                    planId,
                    from.name(),
                    expectedVersion);
            if (changed != 1) conflict();
            event(planId, eventType, actor, reason, correlation, "{}");
        });
    }

    private CutoverPlan mutate(
            String operation,
            UUID planId,
            String idempotencyKey,
            String requestHash,
            Runnable action) {
        return inTransaction(() -> idempotent(operation, idempotencyKey, requestHash, () -> {
            action.run();
            return planId;
        }));
    }

    private CutoverPlan idempotent(
            String operation,
            String idempotencyKey,
            String requestHash,
            Callable<UUID> action) {
        List<Receipt> receipts = jdbc.query(
                "select request_hash,result_plan_id from enforcement_cutover_idempotency "
                        + "where operation_code=? and idempotency_key=?",
                (rs, row) -> new Receipt(rs.getString(1), rs.getObject(2, UUID.class)),
                operation,
                idempotencyKey);
        if (!receipts.isEmpty()) {
            Receipt receipt = receipts.getFirst();
            if (!receipt.requestHash().equals(requestHash)) {
                throw new CutoverPlanException(
                        "CUTOVER_IDEMPOTENCY_CONFLICT",
                        "Idempotency key was reused with a different request.");
            }
            return findInternal(receipt.planId()).orElseThrow(() -> new CutoverPlanException(
                    "CUTOVER_PLAN_NOT_FOUND",
                    "Idempotency receipt references a missing cutover plan."));
        }

        try {
            UUID resultPlanId = action.call();
            jdbc.update(
                    "insert into enforcement_cutover_idempotency("
                            + "operation_code,idempotency_key,request_hash,result_plan_id) values(?,?,?,?)",
                    operation,
                    idempotencyKey,
                    requestHash,
                    resultPlanId);
            return findInternal(resultPlanId).orElseThrow(() -> new CutoverPlanException(
                    "CUTOVER_PLAN_NOT_FOUND",
                    "Cutover plan disappeared after mutation."));
        } catch (CutoverPlanException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Cutover plan transaction failed", exception);
        }
    }

    private <T> T inTransaction(Callable<T> work) {
        T result = transactions.execute(status -> {
            jdbc.queryForObject(
                    "select set_config('app.current_tenant_id','INSTANCE',true)",
                    String.class);
            try {
                return work.call();
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalStateException("Cutover plan transaction failed", exception);
            }
        });
        return Objects.requireNonNull(result, "cutover plan transaction returned null");
    }

    private void requireDraft(UUID planId, long expectedVersion) {
        Integer count = jdbc.queryForObject(
                "select count(*) from enforcement_cutover_plans "
                        + "where plan_id=? and status='DRAFT' and version=?",
                Integer.class,
                planId,
                expectedVersion);
        if (count == null || count != 1) conflict();
    }

    private void touchDraft(UUID planId, long expectedVersion, String reason, String correlation) {
        int changed = jdbc.update(
                "update enforcement_cutover_plans "
                        + "set last_audit_reason=?,correlation_id=?,version=version+1 "
                        + "where plan_id=? and status='DRAFT' and version=?",
                reason,
                correlation,
                planId,
                expectedVersion);
        if (changed != 1) conflict();
    }

    private static void conflict() {
        throw new CutoverPlanException(
                "CUTOVER_PLAN_VERSION_CONFLICT",
                "Cutover plan state or version conflict.");
    }

    private void insertRoute(UUID planId, CutoverPlanRoute route, Instant now) {
        AuthorityRouteDefinition definition = route.definition();
        PreparedStatementCreator statementCreator = connection -> {
            var statement = connection.prepareStatement(
                    "insert into enforcement_cutover_plan_routes("
                            + "plan_id,route_order,tenant_id,domain_code,unit_code,risk_lane,entry_point_id,"
                            + "authority_mode,target_basis_points,include_cohorts,exclude_cohorts,reason_code,created_at) "
                            + "values(?,?,?,?,?,?,?,?,?,?,?,?,?)");
            statement.setObject(1, planId);
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

    private void event(
            UUID planId,
            String eventType,
            String actor,
            String reason,
            String correlation,
            String details) {
        jdbc.update(
                "insert into enforcement_cutover_plan_events("
                        + "plan_id,event_type,actor_id,audit_reason,correlation_id,details) "
                        + "values(?,?,?,?,?,?::jsonb)",
                planId,
                eventType,
                actor,
                reason,
                correlation,
                details);
    }

    private CutoverPlan withChildren(CutoverPlan plan) {
        return new CutoverPlan(
                plan.planId(),
                plan.title(),
                plan.description(),
                plan.status(),
                plan.version(),
                routes(plan.planId()),
                bindings(plan.planId()),
                plan.createdBy(),
                plan.createdAt(),
                plan.submittedBy(),
                plan.submittedAt(),
                plan.approvedBy(),
                plan.approvedAt(),
                plan.rejectedBy(),
                plan.rejectedAt(),
                plan.rejectionReason(),
                plan.publishedRevision(),
                plan.publishedBy(),
                plan.publishedAt(),
                plan.lastAuditReason(),
                plan.correlationId());
    }

    private List<CutoverPlanRoute> routes(UUID planId) {
        return jdbc.query(
                "select * from enforcement_cutover_plan_routes where plan_id=? order by route_order",
                (rs, row) -> new CutoverPlanRoute(
                        rs.getInt("route_order"),
                        new AuthorityRouteDefinition(
                                new AuthorityRouteKey(
                                        rs.getString("tenant_id"),
                                        rs.getString("domain_code"),
                                        rs.getString("unit_code"),
                                        rs.getString("risk_lane"),
                                        rs.getString("entry_point_id")),
                                AuthorityMode.valueOf(rs.getString("authority_mode")),
                                rs.getInt("target_basis_points"),
                                array(rs.getArray("include_cohorts")),
                                array(rs.getArray("exclude_cohorts")),
                                rs.getString("reason_code"))),
                planId);
    }

    private List<ReadinessEvidenceBinding> bindings(UUID planId) {
        return jdbc.query(
                "select * from enforcement_cutover_evidence_bindings "
                        + "where plan_id=? order by evidence_type,evidence_id",
                (rs, row) -> new ReadinessEvidenceBinding(
                        rs.getObject("evidence_id", UUID.class),
                        ReadinessEvidenceType.valueOf(rs.getString("evidence_type")),
                        rs.getString("tenant_id"),
                        rs.getString("domain_code"),
                        rs.getString("evidence_status"),
                        rs.getString("evidence_checksum"),
                        rs.getObject("evaluated_at", OffsetDateTime.class).toInstant(),
                        instant(rs, "expires_at"),
                        rs.getString("source_revision"),
                        rs.getString("immutable_payload")),
                planId);
    }

    private CutoverPlan mapPlan(ResultSet resultSet) throws SQLException {
        Number publishedRevision = (Number) resultSet.getObject("published_revision");
        return new CutoverPlan(
                resultSet.getObject("plan_id", UUID.class),
                resultSet.getString("title"),
                resultSet.getString("description"),
                CutoverPlanStatus.valueOf(resultSet.getString("status")),
                resultSet.getLong("version"),
                List.of(),
                List.of(),
                resultSet.getString("created_by"),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
                resultSet.getString("submitted_by"),
                instant(resultSet, "submitted_at"),
                resultSet.getString("approved_by"),
                instant(resultSet, "approved_at"),
                resultSet.getString("rejected_by"),
                instant(resultSet, "rejected_at"),
                resultSet.getString("rejection_reason"),
                publishedRevision == null ? null : publishedRevision.longValue(),
                resultSet.getString("published_by"),
                instant(resultSet, "published_at"),
                resultSet.getString("last_audit_reason"),
                resultSet.getString("correlation_id"));
    }

    private static Instant instant(ResultSet resultSet, String name) throws SQLException {
        OffsetDateTime value = resultSet.getObject(name, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime at(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static Set<String> array(Array array) throws SQLException {
        if (array == null) return Set.of();
        Object raw = array.getArray();
        if (!(raw instanceof Object[] values)) return Set.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Object value : values) {
            if (value != null) result.add(String.valueOf(value));
        }
        return Set.copyOf(result);
    }

    private record Receipt(String requestHash, UUID planId) {}
}
