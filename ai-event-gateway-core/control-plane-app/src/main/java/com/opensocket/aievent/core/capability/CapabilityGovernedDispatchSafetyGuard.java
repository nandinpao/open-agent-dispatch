package com.opensocket.aievent.core.capability;

import com.opensocket.aievent.core.dispatch.DispatchExecutionSafetyDecision;
import com.opensocket.aievent.core.dispatch.DispatchExecutionSafetyGuard;
import com.opensocket.aievent.core.dispatch.DispatchRequest;
import java.time.OffsetDateTime;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Stage 5 fail-closed pre-network safety guard for CAPABILITY_GOVERNED assignments only. */
@Component
public class CapabilityGovernedDispatchSafetyGuard implements DispatchExecutionSafetyGuard {
    private final NamedParameterJdbcTemplate jdbc;

    public CapabilityGovernedDispatchSafetyGuard(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public int order() { return 100; }

    @Override
    @Transactional(readOnly=true)
    public DispatchExecutionSafetyDecision evaluate(DispatchRequest request, OffsetDateTime now) {
        if (request == null || blank(request.getTenantId()) || blank(request.getAssignmentId())) {
            return DispatchExecutionSafetyDecision.allow("No capability-governed assignment context");
        }
        bind(request.getTenantId());
        AssignmentSafety assignment;
        try {
            assignment = jdbc.queryForObject("""
                select routing_policy,binding_id,execution_safety_mode,lease_id,fencing_token,lease_expires_at
                  from task_assignments where tenant_id=:tenant and assignment_id=:assignment
                """, new MapSqlParameterSource("tenant", request.getTenantId()).addValue("assignment", request.getAssignmentId()),
                    (rs,row) -> new AssignmentSafety(rs.getString("routing_policy"), rs.getString("binding_id"),
                            rs.getString("execution_safety_mode"), rs.getString("lease_id"), rs.getString("fencing_token"),
                            rs.getObject("lease_expires_at", OffsetDateTime.class)));
        } catch (EmptyResultDataAccessException ignored) {
            return DispatchExecutionSafetyDecision.reassignRequired("ASSIGNMENT_NOT_FOUND", "Claimed dispatch has no assignment");
        }
        if (!"CAPABILITY_GOVERNED".equalsIgnoreCase(assignment.routingPolicy())) {
            return DispatchExecutionSafetyDecision.allow("Legacy assignment remains governed by existing dispatch controls");
        }
        OffsetDateTime at = now == null ? OffsetDateTime.now() : now;
        if (!"LOCAL_FENCED".equalsIgnoreCase(assignment.executionSafetyMode())) {
            return DispatchExecutionSafetyDecision.blockPolicy("EXECUTION_SAFETY_MODE_REQUIRED", "Capability-governed managed Agent requires LOCAL_FENCED");
        }
        if (blank(assignment.leaseId()) || blank(assignment.fencingToken())) {
            return DispatchExecutionSafetyDecision.terminal("LOCAL_FENCE_INCOMPLETE", "Lease and fencing token are required before network send");
        }
        if (assignment.leaseExpiresAt() == null || !assignment.leaseExpiresAt().isAfter(at)) {
            return DispatchExecutionSafetyDecision.reassignRequired("ASSIGNMENT_LEASE_EXPIRED", "Assignment lease expired before network send");
        }
        try {
            Integer count = jdbc.queryForObject("""
                select count(*)
                  from capability_runtime_authorization_envelopes e
                 where e.tenant_id=:tenant and e.assignment_id=:assignment and e.status='ACTIVE'
                   and e.binding_id=:binding and e.revoked_at is null
                   and (e.valid_until is null or e.valid_until>:at)
                """, new MapSqlParameterSource("tenant", request.getTenantId()).addValue("assignment", request.getAssignmentId())
                    .addValue("binding", assignment.bindingId()).addValue("at", at), Integer.class);
            if (count == null || count != 1) {
                return DispatchExecutionSafetyDecision.blockSecurity("AUTHORIZATION_ENVELOPE_NOT_ACTIVE", "Capability authorization was revoked, expired, or is missing");
            }
        } catch (RuntimeException ex) {
            return DispatchExecutionSafetyDecision.retryInfrastructure("AUTHORIZATION_ENVELOPE_CHECK_FAILED", "Authorization envelope safety check failed closed");
        }
        return DispatchExecutionSafetyDecision.allow("Capability-governed LOCAL_FENCED assignment is authorized and lease-valid");
    }

    private void bind(String tenantId) {
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)", String.class, tenantId);
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)", String.class, "capability-dispatch-safety-guard");
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private record AssignmentSafety(String routingPolicy,String bindingId,String executionSafetyMode,String leaseId,String fencingToken,OffsetDateTime leaseExpiresAt) {}
}
