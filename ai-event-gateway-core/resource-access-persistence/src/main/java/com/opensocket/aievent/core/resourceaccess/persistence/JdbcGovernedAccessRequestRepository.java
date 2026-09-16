package com.opensocket.aievent.core.resourceaccess.persistence;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.core.resourceaccess.core.GovernedAccessRequestRepository;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL adapter for governed access-request metadata. Scope grants remain canonical policy records. */
@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix = "resource-access", name = "enabled", havingValue = "true")
public class JdbcGovernedAccessRequestRepository implements GovernedAccessRequestRepository {
    private final JdbcTemplate jdbc;
    public JdbcGovernedAccessRequestRepository(JdbcTemplate jdbc) { this.jdbc = Objects.requireNonNull(jdbc); }

    @Override public Optional<GovernedAccessRequestRecord> find(String tenantId, String requestId) {
        return one("tenant_id=? and access_request_id=?", tenantId, requestId);
    }
    @Override public Optional<GovernedAccessRequestRecord> findByIdempotencyKey(String tenantId, String key) {
        return one("tenant_id=? and idempotency_key=?", tenantId, key);
    }
    private Optional<GovernedAccessRequestRecord> one(String predicate, Object... args) {
        List<GovernedAccessRequestRecord> values = jdbc.query(
                "select * from resource_access_requests where " + predicate + " limit 1",
                (rs, row) -> map(rs), args);
        return values.stream().findFirst();
    }

    @Override @Transactional
    public GovernedAccessRequestRecord insert(GovernedAccessRequestRecord request, String correlationId) {
        jdbc.update("""
            insert into resource_access_requests(
              tenant_id,access_request_id,scope_grant_id,ui_action_id,resource_type,resource_id,
              resource_version_at_request,requested_visibility,requester_id,business_purpose,
              valid_from,valid_to,request_state,approved_by,idempotency_key,version,created_at,updated_at)
            values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """, request.tenantId(), request.requestId(), request.scopeGrantId(), request.uiActionId(),
                request.resourceType().name(), request.resourceId(), request.resourceVersionAtRequest(),
                request.requestedVisibility().name(), request.requesterId(), request.businessPurpose(),
                ts(request.validFrom()), ts(request.validTo()), request.state().name(), blankToNull(request.approvedBy()),
                request.idempotencyKey(), request.version(), ts(request.createdAt()), ts(request.updatedAt()));
        audit(request.tenantId(), request.requestId(), null, request.state(), request.requesterId(),
                "ACCESS_REQUEST_CREATED", correlationId, request.idempotencyKey() + ":audit:create",
                request.version(), request.createdAt());
        return request;
    }

    @Override @Transactional
    public GovernedAccessRequestRecord transition(GovernedAccessRequestRecord current,
                                                   GovernedAccessRequestState targetState,
                                                   String approvedBy,
                                                   String actorId,
                                                   String reason,
                                                   String correlationId,
                                                   String idempotencyKey,
                                                   Instant changedAt) {
        int updated = jdbc.update("""
            update resource_access_requests
               set request_state=?,approved_by=?,version=version+1,updated_at=?
             where tenant_id=? and access_request_id=? and version=? and request_state=?
            """, targetState.name(), blankToNull(approvedBy), ts(changedAt), current.tenantId(),
                current.requestId(), current.version(), current.state().name());
        if (updated != 1) throw new IllegalStateException("RESOURCE_VERSION_CONFLICT");
        GovernedAccessRequestRecord result = find(current.tenantId(), current.requestId()).orElseThrow();
        audit(current.tenantId(), current.requestId(), current.state(), targetState, actorId, reason,
                correlationId, idempotencyKey, result.version(), changedAt);
        return result;
    }

    private void audit(String tenantId, String requestId, GovernedAccessRequestState previous,
                       GovernedAccessRequestState target, String actorId, String reason,
                       String correlationId, String idempotencyKey, long version, Instant at) {
        jdbc.update("""
            insert into resource_access_request_audits(
              tenant_id,audit_id,access_request_id,previous_state,resulting_state,actor_id,reason,
              correlation_id,idempotency_key,resulting_version,occurred_at)
            values (?,?,?,?,?,?,?,?,?,?,?) on conflict(tenant_id,idempotency_key) do nothing
            """, tenantId, "access-request-audit-" + UUID.nameUUIDFromBytes((tenantId + ":" + idempotencyKey).getBytes()),
                requestId, previous == null ? null : previous.name(), target.name(), actorId, reason,
                correlationId, idempotencyKey, version, ts(at));
    }

    private static GovernedAccessRequestRecord map(ResultSet rs, int ignored) throws SQLException { return map(rs); }
    private static GovernedAccessRequestRecord map(ResultSet rs) throws SQLException {
        return new GovernedAccessRequestRecord(rs.getString("tenant_id"), rs.getString("access_request_id"),
                rs.getString("scope_grant_id"), rs.getString("ui_action_id"),
                ResourceType.valueOf(rs.getString("resource_type")), rs.getString("resource_id"),
                rs.getLong("resource_version_at_request"), VisibilityLevel.valueOf(rs.getString("requested_visibility")),
                rs.getString("requester_id"), rs.getString("business_purpose"),
                rs.getTimestamp("valid_from").toInstant(), rs.getTimestamp("valid_to").toInstant(),
                GovernedAccessRequestState.valueOf(rs.getString("request_state")), nullToBlank(rs.getString("approved_by")),
                rs.getString("idempotency_key"), rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }
    private static Timestamp ts(Instant value) { return Timestamp.from(value); }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String nullToBlank(String value) { return value == null ? "" : value; }
}
