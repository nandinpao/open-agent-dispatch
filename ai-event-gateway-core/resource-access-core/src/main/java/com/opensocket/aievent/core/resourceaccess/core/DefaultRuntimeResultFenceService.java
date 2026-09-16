package com.opensocket.aievent.core.resourceaccess.core;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** P4RA-J result-commit fence. Any stale, terminal or binding-mismatched result is quarantined. */
public final class DefaultRuntimeResultFenceService implements RuntimeResultFencePort {
    private final RuntimeAuthorizationLeasePort leases;
    private final RuntimeLateResultQuarantinePort quarantines;
    private final Clock clock;

    public DefaultRuntimeResultFenceService(
            RuntimeAuthorizationLeasePort leases,
            RuntimeLateResultQuarantinePort quarantines,
            Clock clock) {
        this.leases = Objects.requireNonNull(leases, "leases");
        this.quarantines = Objects.requireNonNull(quarantines, "quarantines");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public RuntimeResultFenceResult acceptOrQuarantine(RuntimeResultSubmission submission) {
        Objects.requireNonNull(submission, "submission");
        RuntimeAuthorizationLease lease = leases.check(new RuntimeAuthorizationCheckpoint(
                submission.tenantId(), submission.leaseId(), OperationPhase.BEFORE_RESULT_COMMIT,
                submission.presentedFencingVersion(), submission.correlationId()));

        String reason = reason(lease, submission);
        if (reason == null) {
            return new RuntimeResultFenceResult(RuntimeResultDisposition.ACCEPTED, lease, null, "RUNTIME_RESULT_ACCEPTED");
        }

        RuntimeLateResultQuarantine quarantine = quarantines.findBySubmission(
                        submission.tenantId(), submission.submissionId())
                .orElseGet(() -> quarantines.append(new RuntimeLateResultQuarantine(
                        submission.tenantId(),
                        "rlrq-" + UUID.randomUUID(),
                        submission,
                        lease.status(),
                        reason,
                        RuntimeLateResultQuarantineStatus.OPEN,
                        "",
                        "",
                        now(submission.observedAt()),
                        null,
                        0)));
        return new RuntimeResultFenceResult(
                RuntimeResultDisposition.QUARANTINED,
                lease,
                quarantine,
                ResourceDecisionReasonCodes.RUNTIME_LATE_RESULT_QUARANTINED);
    }

    private String reason(RuntimeAuthorizationLease lease, RuntimeResultSubmission submission) {
        if (lease.fencingVersion() != submission.presentedFencingVersion()) {
            return "RUNTIME_RESULT_FENCING_VERSION_MISMATCH";
        }
        if (lease.status() != RuntimeLeaseStatus.ACTIVE) {
            return "RUNTIME_RESULT_LEASE_NOT_ACTIVE";
        }
        if (!lease.resourceRef().equals(submission.resourceRef())) {
            return "RUNTIME_RESULT_BINDING_MISMATCH";
        }
        if (!lease.assignmentId().isBlank() && !lease.assignmentId().equals(submission.assignmentId())) {
            return "RUNTIME_RESULT_BINDING_MISMATCH";
        }
        if (lease.attemptNo() != null && !lease.attemptNo().equals(submission.attemptNo())) {
            return "RUNTIME_RESULT_BINDING_MISMATCH";
        }
        return null;
    }

    private Instant now(Instant observedAt) {
        Instant current = clock.instant();
        return observedAt.isAfter(current) ? current : observedAt;
    }
}
