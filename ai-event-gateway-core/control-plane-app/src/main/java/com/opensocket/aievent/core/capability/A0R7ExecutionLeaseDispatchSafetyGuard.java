package com.opensocket.aievent.core.capability;

import com.opensocket.aievent.core.dispatch.DispatchAuthorityProvenance;
import com.opensocket.aievent.core.dispatch.DispatchExecutionSafetyDecision;
import com.opensocket.aievent.core.dispatch.DispatchExecutionSafetyGuard;
import com.opensocket.aievent.core.dispatch.DispatchRequest;
import java.time.OffsetDateTime;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * A0-R7 pre-network guard.
 *
 * <p>Legacy assignments remain untouched. For assignments promoted through V206 the worker must
 * prove, immediately before network delivery, that the per-Flow cutover is still active, the R5
 * BindingAuthorizationEnvelope is still valid, and the canonical lease/fencing token still owns
 * the step. A stale node therefore cannot use an old compatibility dispatch row to send.</p>
 */
@Component
public class A0R7ExecutionLeaseDispatchSafetyGuard implements DispatchExecutionSafetyGuard {
    private final NamedParameterJdbcTemplate jdbc;

    public A0R7ExecutionLeaseDispatchSafetyGuard(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public int order() { return 50; }

    @Override
    @Transactional(readOnly = true)
    public DispatchExecutionSafetyDecision evaluate(DispatchRequest request, OffsetDateTime now) {
        if (request == null) return DispatchExecutionSafetyDecision.allow("No dispatch request context");
        if (request.getAuthorityProvenance() == DispatchAuthorityProvenance.LEGACY_COMPATIBILITY) {
            if ("A0-R7-V206".equals(request.getExecutionAuthorityVersion()) || !blank(request.getCanonicalExecutionAssignmentId())) {
                return block("C0_A5_AUTHORITY_PROVENANCE_MISMATCH");
            }
            return DispatchExecutionSafetyDecision.allow("Explicit legacy DispatchRequest provenance; A0-R7 guard not applicable");
        }
        if (request.getAuthorityProvenance() != DispatchAuthorityProvenance.A0_R7_CANONICAL
                || !"A0-R7-V206".equals(request.getExecutionAuthorityVersion())
                || blank(request.getTenantId()) || blank(request.getAssignmentId())
                || blank(request.getDispatchRequestId()) || blank(request.getCanonicalExecutionAssignmentId())) {
            return block("C0_A5_CURRENT_AUTHORITY_PROVENANCE_INCOMPLETE");
        }
        bind(request.getTenantId());

        GuardRow row;
        try {
            row = jdbc.queryForObject("""
                select ta.execution_authority_version, ta.fencing_token as mirror_fencing_token,
                       ea.flow_id,ea.envelope_id,ea.binding_id,ea.execution_safety_mode,
                       ea.lease_id,ea.fencing_token,ea.lease_until,ea.status as assignment_status,
                       l.status as lease_status,l.lease_until as canonical_lease_until,
                       f.migration_state,
                       e.status as envelope_status,e.valid_until as envelope_valid_until,
                       jsonb_exists(e.admitted_binding_ids_json, ea.binding_id) as binding_admitted,
                       i.status as intent_status,i.external_execution_ref
                  from dispatch_requests dr
                  join task_assignments ta
                    on ta.tenant_id=dr.tenant_id and ta.assignment_id=dr.assignment_id
                  join execution_assignments_v206 ea
                    on ea.tenant_id=dr.tenant_id and ea.assignment_id=dr.canonical_execution_assignment_id
                  join execution_leases_v206 l
                    on l.tenant_id=ea.tenant_id and l.lease_id=ea.lease_id and l.assignment_id=ea.assignment_id
                  join flow_routing_migration_state f
                    on f.tenant_id=ea.tenant_id and f.flow_id=ea.flow_id
                  join binding_authorization_envelopes e
                    on e.tenant_id=ea.tenant_id and e.envelope_id=ea.envelope_id
                  left join execution_dispatch_intents_v206 i
                    on i.tenant_id=ea.tenant_id and i.assignment_id=ea.assignment_id
                 where dr.tenant_id=:tenant
                   and dr.dispatch_request_id=:dispatch
                   and dr.assignment_id=:assignment
                   and dr.authority_provenance='A0_R7_CANONICAL'
                   and dr.execution_authority_version=:authority
                   and dr.canonical_execution_assignment_id=:canonical
                   and ta.execution_authority_version=:authority
                   and ta.canonical_execution_assignment_id=:canonical
                 order by i.created_at desc nulls last
                 limit 1
                """, new MapSqlParameterSource("tenant", request.getTenantId())
                        .addValue("dispatch", request.getDispatchRequestId())
                        .addValue("assignment", request.getAssignmentId())
                        .addValue("authority", request.getExecutionAuthorityVersion())
                        .addValue("canonical", request.getCanonicalExecutionAssignmentId()),
                (rs,n) -> new GuardRow(
                        rs.getString("execution_authority_version"), rs.getString("mirror_fencing_token"),
                        rs.getString("flow_id"), rs.getString("envelope_id"), rs.getString("binding_id"),
                        rs.getString("execution_safety_mode"), rs.getString("lease_id"), rs.getLong("fencing_token"),
                        rs.getObject("lease_until", OffsetDateTime.class), rs.getString("assignment_status"),
                        rs.getString("lease_status"), rs.getObject("canonical_lease_until", OffsetDateTime.class),
                        rs.getString("migration_state"), rs.getString("envelope_status"),
                        rs.getObject("envelope_valid_until", OffsetDateTime.class), rs.getBoolean("binding_admitted"),
                        rs.getString("intent_status"), rs.getString("external_execution_ref")));
        } catch (EmptyResultDataAccessException missingCanonicalEvidence) {
            return block("A0_R7_CANONICAL_AUTHORITY_EVIDENCE_MISSING");
        }

        if (!"A0-R7-V206".equals(row.authorityVersion())) return block("A0_R7_AUTHORITY_VERSION_MISMATCH");
        if (!"NEW_AUTHORITATIVE".equals(row.migrationState())) return block("A0_R7_FLOW_NOT_NEW_AUTHORITATIVE");
        if (!"ASSIGNED".equals(row.assignmentStatus())) return block("A0_R7_ASSIGNMENT_NOT_ACTIVE");
        if (!"ACTIVE".equals(row.leaseStatus()) || row.canonicalLeaseUntil() == null || !row.canonicalLeaseUntil().isAfter(now)) return block("A0_R7_EXECUTION_LEASE_EXPIRED");
        if (row.leaseUntil() == null || !row.leaseUntil().isAfter(now)) return block("A0_R7_ASSIGNMENT_LEASE_EXPIRED");
        if (!"ACTIVE".equals(row.envelopeStatus()) || row.envelopeValidUntil() == null || !row.envelopeValidUntil().isAfter(now)) return block("A0_R7_BINDING_ENVELOPE_NOT_ACTIVE");
        if (!row.bindingAdmitted()) return block("A0_R7_BINDING_OUTSIDE_ENVELOPE");
        if (!"LOCAL_FENCED".equals(row.executionSafetyMode())) return block("A0_R7_MANAGED_SEND_REQUIRES_LOCAL_FENCED");
        String fence = String.valueOf(row.fencingToken());
        if (!fence.equals(row.mirrorFencingToken())) return block("A0_R7_COMPATIBILITY_FENCE_MISMATCH");
        if (request.getCommand() == null || !fence.equals(request.getCommand().getFencingToken())) return block("A0_R7_DISPATCH_COMMAND_FENCE_MISMATCH");
        String expectedExternalRef = "dispatch:" + request.getDispatchRequestId();
        if (!"HANDED_OFF".equals(row.intentStatus()) || !expectedExternalRef.equals(row.externalExecutionRef())) {
            return block("A0_R7_DISPATCH_INTENT_NOT_HANDED_OFF");
        }
        return DispatchExecutionSafetyDecision.allow("A0-R7 lease/fence/envelope/intent revalidation passed");
    }

    private DispatchExecutionSafetyDecision block(String code) {
        String message = "A0-R7 pre-network execution safety rejected stale or unauthorized dispatch";
        return switch (code) {
            case "A0_R7_EXECUTION_LEASE_EXPIRED", "A0_R7_ASSIGNMENT_LEASE_EXPIRED",
                    "A0_R7_ASSIGNMENT_NOT_ACTIVE", "A0_R7_COMPATIBILITY_FENCE_MISMATCH",
                    "A0_R7_DISPATCH_COMMAND_FENCE_MISMATCH", "A0_R7_DISPATCH_INTENT_NOT_HANDED_OFF"
                    -> DispatchExecutionSafetyDecision.reassignRequired(code, message);
            case "A0_R7_BINDING_ENVELOPE_NOT_ACTIVE"
                    -> DispatchExecutionSafetyDecision.blockSecurity(code, message);
            case "A0_R7_FLOW_NOT_NEW_AUTHORITATIVE", "A0_R7_BINDING_OUTSIDE_ENVELOPE",
                    "A0_R7_MANAGED_SEND_REQUIRES_LOCAL_FENCED"
                    -> DispatchExecutionSafetyDecision.blockPolicy(code, message);
            case "A0_R7_CANONICAL_AUTHORITY_EVIDENCE_MISSING", "A0_R7_AUTHORITY_VERSION_MISMATCH",
                    "C0_A5_AUTHORITY_PROVENANCE_MISMATCH", "C0_A5_CURRENT_AUTHORITY_PROVENANCE_INCOMPLETE"
                    -> DispatchExecutionSafetyDecision.terminal(code, message);
            default -> DispatchExecutionSafetyDecision.terminal(code, message);
        };
    }
    private void bind(String tenant) {
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)", String.class, tenant);
    }
    private boolean blank(String v) { return v == null || v.isBlank(); }
    private record GuardRow(String authorityVersion,String mirrorFencingToken,String flowId,String envelopeId,String bindingId,
                            String executionSafetyMode,String leaseId,long fencingToken,OffsetDateTime leaseUntil,String assignmentStatus,
                            String leaseStatus,OffsetDateTime canonicalLeaseUntil,String migrationState,String envelopeStatus,
                            OffsetDateTime envelopeValidUntil,boolean bindingAdmitted,String intentStatus,String externalExecutionRef) {}
}
