package com.opensocket.aievent.core.capability;

import com.opensocket.aievent.core.dispatch.DispatchAuthorityProvenance;
import com.opensocket.aievent.core.dispatch.DispatchPreSendAdmission;
import com.opensocket.aievent.core.dispatch.DispatchPreSendAdmissionDecision;
import com.opensocket.aievent.core.dispatch.DispatchRequest;
import com.opensocket.aievent.core.dispatch.DispatchSendPermit;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * C0-A2 final A0-R7 send admission.
 *
 * <p>This component is the last Core authority boundary before Netty I/O. The transaction locks
 * the current dispatch claim plus canonical assignment/lease/flow/envelope/intent rows, revalidates
 * the complete A0-R7 authority set, atomically transitions the durable intent from HANDED_OFF to
 * SEND_STARTED, appends evidence, and returns an immutable {@link DispatchSendPermit}. The method
 * always commits or rolls back before network I/O begins.</p>
 */
@Component
public class A0R7AtomicPreSendAdmission implements DispatchPreSendAdmission {
    private static final String AUTHORITY_VERSION = "A0-R7-V206";
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public A0R7AtomicPreSendAdmission(NamedParameterJdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public int order() { return 50; }

    @Override
    public DispatchPreSendAdmissionDecision admit(DispatchRequest request, OffsetDateTime now) {
        DispatchPreSendAdmissionDecision decision = transactions.execute(status -> admitInTransaction(request, now));
        if (decision == null) throw new IllegalStateException("A0_R7_PRE_SEND_TRANSACTION_RETURNED_NULL");
        return decision;
    }

    private DispatchPreSendAdmissionDecision admitInTransaction(DispatchRequest request, OffsetDateTime now) {
        if (request == null) return DispatchPreSendAdmissionDecision.notApplicable("No dispatch request context");
        if (request.getAuthorityProvenance() == DispatchAuthorityProvenance.LEGACY_COMPATIBILITY) {
            if (AUTHORITY_VERSION.equals(request.getExecutionAuthorityVersion()) || !blank(request.getCanonicalExecutionAssignmentId())) {
                return block("C0_A5_AUTHORITY_PROVENANCE_MISMATCH");
            }
            return DispatchPreSendAdmissionDecision.notApplicable("Explicit legacy DispatchRequest provenance; A0-R7 admission not applicable");
        }
        if (request.getAuthorityProvenance() != DispatchAuthorityProvenance.A0_R7_CANONICAL
                || !AUTHORITY_VERSION.equals(request.getExecutionAuthorityVersion())
                || blank(request.getTenantId()) || blank(request.getAssignmentId())
                || blank(request.getDispatchRequestId()) || blank(request.getCanonicalExecutionAssignmentId())) {
            return block("C0_A5_CURRENT_AUTHORITY_PROVENANCE_INCOMPLETE");
        }
        bind(request.getTenantId());

        AdmissionRow row;
        try {
            row = jdbc.queryForObject("""
                select dr.claimed_by,dr.claim_token,dr.claim_until as dispatch_claim_until,dr.outbox_status,
                       dr.execution_authority_version as dispatch_authority_version,
                       dr.canonical_execution_assignment_id as dispatch_canonical_assignment_id,
                       dr.authority_provenance,
                       ta.execution_authority_version,ta.fencing_token as mirror_fencing_token,
                       ea.assignment_id as canonical_assignment_id,ea.flow_id,ea.envelope_id,ea.binding_id,
                       ea.execution_safety_mode,ea.lease_id,ea.fencing_token,ea.lease_until,ea.status as assignment_status,
                       l.status as lease_status,l.lease_until as canonical_lease_until,
                       f.migration_state,
                       e.status as envelope_status,e.valid_until as envelope_valid_until,
                       jsonb_exists(e.admitted_binding_ids_json,ea.binding_id) as binding_admitted,
                       i.intent_id,i.status as intent_status,i.external_execution_ref
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
                  join execution_dispatch_intents_v206 i
                    on i.tenant_id=ea.tenant_id and i.assignment_id=ea.assignment_id
                 where dr.tenant_id=:tenant
                   and dr.dispatch_request_id=:dispatch
                   and dr.assignment_id=:assignment
                   and dr.authority_provenance='A0_R7_CANONICAL'
                   and dr.execution_authority_version=:authority
                   and dr.canonical_execution_assignment_id=:canonical
                   and ta.execution_authority_version=:authority
                   and ta.canonical_execution_assignment_id=:canonical
                 for update of dr,ta,ea,l,f,e,i
                """,
                    new MapSqlParameterSource("tenant", request.getTenantId())
                            .addValue("dispatch", request.getDispatchRequestId())
                            .addValue("assignment", request.getAssignmentId())
                            .addValue("authority", request.getExecutionAuthorityVersion())
                            .addValue("canonical", request.getCanonicalExecutionAssignmentId()),
                    (rs, n) -> new AdmissionRow(
                            rs.getString("claimed_by"),
                            rs.getString("claim_token"),
                            rs.getObject("dispatch_claim_until", OffsetDateTime.class),
                            rs.getString("outbox_status"),
                            rs.getString("dispatch_authority_version"),
                            rs.getString("dispatch_canonical_assignment_id"),
                            rs.getString("authority_provenance"),
                            rs.getString("execution_authority_version"),
                            rs.getString("mirror_fencing_token"),
                            rs.getString("canonical_assignment_id"),
                            rs.getString("flow_id"),
                            rs.getString("envelope_id"),
                            rs.getString("binding_id"),
                            rs.getString("execution_safety_mode"),
                            rs.getString("lease_id"),
                            rs.getLong("fencing_token"),
                            rs.getObject("lease_until", OffsetDateTime.class),
                            rs.getString("assignment_status"),
                            rs.getString("lease_status"),
                            rs.getObject("canonical_lease_until", OffsetDateTime.class),
                            rs.getString("migration_state"),
                            rs.getString("envelope_status"),
                            rs.getObject("envelope_valid_until", OffsetDateTime.class),
                            rs.getBoolean("binding_admitted"),
                            rs.getString("intent_id"),
                            rs.getString("intent_status"),
                            rs.getString("external_execution_ref")));
        } catch (EmptyResultDataAccessException missingAuthority) {
            return block("A0_R7_ATOMIC_AUTHORITY_EVIDENCE_MISSING");
        }

        DispatchPreSendAdmissionDecision invalid = validate(request, row, now);
        if (invalid != null) return invalid;

        String permitId = "send-permit-" + UUID.randomUUID();
        String externalRef = "dispatch:" + request.getDispatchRequestId();
        int updated = jdbc.update("""
            update execution_dispatch_intents_v206
               set status='SEND_STARTED',
                   send_started_at=coalesce(send_started_at,:now),
                   updated_at=:now
             where tenant_id=:tenant
               and intent_id=:intent
               and assignment_id=:canonicalAssignment
               and lease_id=:lease
               and fencing_token=:fence
               and status='HANDED_OFF'
               and external_execution_ref=:externalRef
            """,
                new MapSqlParameterSource("tenant", request.getTenantId())
                        .addValue("intent", row.intentId())
                        .addValue("canonicalAssignment", row.canonicalAssignmentId())
                        .addValue("lease", row.leaseId())
                        .addValue("fence", row.fencingToken())
                        .addValue("externalRef", externalRef)
                        .addValue("now", now));
        if (updated != 1) {
            return DispatchPreSendAdmissionDecision.reassignRequired(
                    "A0_R7_ATOMIC_SEND_STARTED_CAS_CONFLICT",
                    "Atomic SEND_STARTED compare-and-set lost current dispatch ownership");
        }

        jdbc.update("""
            insert into execution_dispatch_intent_events_v206(
                tenant_id,event_id,intent_id,assignment_id,from_status,to_status,reason_code,
                actor_ref,evidence_json,occurred_at)
            values(
                :tenant,:event,:intent,:assignment,'HANDED_OFF','SEND_STARTED','C0_A2_ATOMIC_PRE_SEND_ADMITTED',
                :actor,jsonb_build_object(
                    'permitId',:permit,
                    'dispatchRequestId',:dispatch,
                    'leaseId',:lease,
                    'fencingToken',:fence,
                    'envelopeId',:envelope,
                    'bindingId',:binding,
                    'externalExecutionRef',:externalRef),
                :now)
            """,
                new MapSqlParameterSource("tenant", request.getTenantId())
                        .addValue("event", "dispatch-intent-event-" + UUID.randomUUID())
                        .addValue("intent", row.intentId())
                        .addValue("assignment", row.canonicalAssignmentId())
                        .addValue("actor", actor())
                        .addValue("permit", permitId)
                        .addValue("dispatch", request.getDispatchRequestId())
                        .addValue("lease", row.leaseId())
                        .addValue("fence", row.fencingToken())
                        .addValue("envelope", row.envelopeId())
                        .addValue("binding", row.bindingId())
                        .addValue("externalRef", externalRef)
                        .addValue("now", now));

        OffsetDateTime expiresAt = earliest(row.canonicalLeaseUntil(), row.leaseUntil(), row.envelopeValidUntil(), row.dispatchClaimUntil());
        DispatchSendPermit permit = new DispatchSendPermit(
                permitId,
                request.getTenantId(),
                request.getDispatchRequestId(),
                row.canonicalAssignmentId(),
                request.getTaskId(),
                request.getAttemptCount(),
                AUTHORITY_VERSION,
                row.leaseId(),
                row.fencingToken(),
                row.envelopeId(),
                row.bindingId(),
                row.intentId(),
                externalRef,
                now,
                expiresAt);
        return DispatchPreSendAdmissionDecision.allow(
                "A0_R7_ATOMIC_PRE_SEND_ADMITTED",
                "A0-R7 authority revalidated and SEND_STARTED committed before network I/O",
                permit);
    }

    private DispatchPreSendAdmissionDecision validate(DispatchRequest request, AdmissionRow row, OffsetDateTime now) {
        if (!AUTHORITY_VERSION.equals(row.dispatchAuthorityVersion())
                || !request.getCanonicalExecutionAssignmentId().equals(row.dispatchCanonicalAssignmentId())
                || !DispatchAuthorityProvenance.A0_R7_CANONICAL.name().equals(row.authorityProvenance()))
            return block("C0_A5_DISPATCH_AUTHORITY_SNAPSHOT_MISMATCH");
        if (!AUTHORITY_VERSION.equals(row.authorityVersion())) return block("A0_R7_AUTHORITY_VERSION_MISMATCH");
        if (!same(request.getClaimedBy(), row.claimedBy()) || !same(request.getClaimToken(), row.claimToken())) return block("A0_R7_DISPATCH_CLAIM_OWNERSHIP_LOST");
        if (!"DISPATCHING".equals(row.outboxStatus()) || row.dispatchClaimUntil() == null || !row.dispatchClaimUntil().isAfter(now)) return block("A0_R7_DISPATCH_CLAIM_EXPIRED");
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
        if (!"HANDED_OFF".equals(row.intentStatus()) || !expectedExternalRef.equals(row.externalExecutionRef())) return block("A0_R7_DISPATCH_INTENT_NOT_HANDED_OFF");
        return null;
    }

    private DispatchPreSendAdmissionDecision block(String code) {
        String message = "A0-R7 atomic pre-send admission rejected stale or unauthorized dispatch";
        return switch (code) {
            case "A0_R7_DISPATCH_CLAIM_OWNERSHIP_LOST", "A0_R7_DISPATCH_CLAIM_EXPIRED",
                    "A0_R7_EXECUTION_LEASE_EXPIRED", "A0_R7_ASSIGNMENT_LEASE_EXPIRED",
                    "A0_R7_ASSIGNMENT_NOT_ACTIVE", "A0_R7_COMPATIBILITY_FENCE_MISMATCH",
                    "A0_R7_DISPATCH_COMMAND_FENCE_MISMATCH", "A0_R7_DISPATCH_INTENT_NOT_HANDED_OFF"
                    -> DispatchPreSendAdmissionDecision.reassignRequired(code, message);
            case "A0_R7_BINDING_ENVELOPE_NOT_ACTIVE"
                    -> DispatchPreSendAdmissionDecision.blockSecurity(code, message);
            case "A0_R7_FLOW_NOT_NEW_AUTHORITATIVE", "A0_R7_BINDING_OUTSIDE_ENVELOPE",
                    "A0_R7_MANAGED_SEND_REQUIRES_LOCAL_FENCED"
                    -> DispatchPreSendAdmissionDecision.blockPolicy(code, message);
            case "A0_R7_ATOMIC_AUTHORITY_EVIDENCE_MISSING", "A0_R7_AUTHORITY_VERSION_MISMATCH",
                    "C0_A5_AUTHORITY_PROVENANCE_MISMATCH", "C0_A5_CURRENT_AUTHORITY_PROVENANCE_INCOMPLETE",
                    "C0_A5_DISPATCH_AUTHORITY_SNAPSHOT_MISMATCH"
                    -> DispatchPreSendAdmissionDecision.terminal(code, message);
            default -> DispatchPreSendAdmissionDecision.terminal(code, message);
        };
    }

    private void bind(String tenant) {
        IamTenantExecutionContext context = IamTenantContextHolder.current().orElse(null);
        if (context != null && !"INSTANCE".equalsIgnoreCase(context.tenantId()) && !tenant.equals(context.tenantId())) {
            throw new IllegalArgumentException("Tenant context mismatch for A0-R7 atomic pre-send admission");
        }
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)", String.class, tenant);
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)", String.class, actor());
    }

    private String actor() {
        IamTenantExecutionContext context = IamTenantContextHolder.current().orElse(null);
        return context == null || blank(context.actorId()) ? "c0-a2-pre-send-admission" : context.actorId();
    }

    private static OffsetDateTime earliest(OffsetDateTime... values) {
        OffsetDateTime result = null;
        if (values == null) return null;
        for (OffsetDateTime value : values) {
            if (value != null && (result == null || value.isBefore(result))) result = value;
        }
        return result;
    }

    private static boolean same(String a, String b) { return a == null ? b == null : a.equals(b); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }

    private record AdmissionRow(
            String claimedBy,
            String claimToken,
            OffsetDateTime dispatchClaimUntil,
            String outboxStatus,
            String dispatchAuthorityVersion,
            String dispatchCanonicalAssignmentId,
            String authorityProvenance,
            String authorityVersion,
            String mirrorFencingToken,
            String canonicalAssignmentId,
            String flowId,
            String envelopeId,
            String bindingId,
            String executionSafetyMode,
            String leaseId,
            long fencingToken,
            OffsetDateTime leaseUntil,
            String assignmentStatus,
            String leaseStatus,
            OffsetDateTime canonicalLeaseUntil,
            String migrationState,
            String envelopeStatus,
            OffsetDateTime envelopeValidUntil,
            boolean bindingAdmitted,
            String intentId,
            String intentStatus,
            String externalExecutionRef) {}
}
