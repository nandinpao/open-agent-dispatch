package com.opensocket.aievent.core.a2a.core;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Set;
import com.opensocket.aievent.core.a2a.*;

/** Pure A2A coordination state machine. It owns no persistence and no Task/Assignment state. */
public final class A2AStateMachine {
    private static final Set<A2ARequestStatus> TERMINAL = EnumSet.of(
            A2ARequestStatus.REJECTED,
            A2ARequestStatus.COMPLETED,
            A2ARequestStatus.FAILED,
            A2ARequestStatus.CANCELLED_CONFIRMED,
            A2ARequestStatus.EXPIRED);

    public A2ATransitionDecision decide(
            A2ARequest request,
            A2ATransitionCommand command,
            A2ARequestStatus target,
            Long expectedVersion,
            String auditReason,
            OffsetDateTime now) {
        return decide(request, command, target, A2ABlockerCode.NONE,
                expectedVersion, auditReason, "compatibility:" + command.name(), now);
    }

    public A2ATransitionDecision decide(
            A2ARequest request,
            A2ATransitionCommand command,
            A2ARequestStatus target,
            A2ABlockerCode requestedBlocker,
            Long expectedVersion,
            String auditReason,
            OffsetDateTime now) {
        return decide(request, command, target, requestedBlocker, expectedVersion,
                auditReason, "compatibility:" + command.name(), now);
    }

    public A2ATransitionDecision decide(
            A2ARequest request,
            A2ATransitionCommand command,
            A2ARequestStatus target,
            A2ABlockerCode requestedBlocker,
            Long expectedVersion,
            String auditReason,
            String evidenceReference,
            OffsetDateTime now) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (command == null || target == null) {
            throw new IllegalArgumentException("command and target are required");
        }
        if (expectedVersion == null) {
            throw new A2ATransitionException(
                    A2ATransitionFailureReason.EXPECTED_VERSION_REQUIRED,
                    "expectedVersion is required");
        }
        if (request.getVersion() != expectedVersion.longValue()) {
            throw new A2ATransitionException(
                    A2ATransitionFailureReason.RESOURCE_VERSION_CONFLICT,
                    "RESOURCE_VERSION_CONFLICT");
        }
        A2ARequestStatus fromStatus = request.getRequestStatus();
        A2AOperationalStage fromStage = request.getOperationalStage() == null
                ? A2AOperationalStage.defaultFor(fromStatus)
                : request.getOperationalStage();
        if (TERMINAL.contains(fromStatus)) {
            throw new A2ATransitionException(
                    A2ATransitionFailureReason.TERMINAL_STATE_IMMUTABLE,
                    "Terminal A2A Request is immutable: " + fromStatus);
        }
        if (fromStatus == A2ARequestStatus.WAITING_APPROVAL
                && request.getExpiresAt() != null
                && now != null
                && !now.isBefore(request.getExpiresAt())
                && target != A2ARequestStatus.EXPIRED) {
            throw new A2ATransitionException(
                    A2ATransitionFailureReason.APPROVAL_EXPIRED,
                    "A2A approval has expired");
        }
        A2ATransitionRule rule = A2ATransitionCatalog.find(fromStatus, fromStage, command, target)
                .orElseThrow(() -> new A2ATransitionException(
                        A2ATransitionFailureReason.COMMAND_TARGET_MISMATCH,
                        "No A2A transition rule for " + fromStatus + "/" + fromStage
                                + " + " + command + " -> " + target));
        if (evidenceReference == null || evidenceReference.isBlank()) {
            throw new A2ATransitionException(
                    A2ATransitionFailureReason.EVIDENCE_REQUIRED,
                    "Evidence reference is required for " + rule.evidenceType());
        }
        A2ABlockerCode blocker = requestedBlocker == null ? A2ABlockerCode.NONE : requestedBlocker;
        if (rule.targetStage() == A2AOperationalStage.BLOCKED && blocker == A2ABlockerCode.NONE) {
            throw new A2ATransitionException(
                    A2ATransitionFailureReason.BLOCKER_REQUIRED,
                    "A non-NONE blockerCode is required for BLOCKED stage");
        }
        if (rule.targetStage() != A2AOperationalStage.BLOCKED) {
            blocker = A2ABlockerCode.NONE;
        }
        return new A2ATransitionDecision(
                rule.id(),
                fromStatus,
                fromStage,
                rule.targetStatus(),
                rule.targetStage(),
                blocker,
                command,
                rule.permission(),
                rule.idempotencyScope(),
                rule.evidenceType(),
                evidenceReference.trim(),
                expectedVersion,
                expectedVersion + 1L,
                auditReason == null || auditReason.isBlank()
                        ? rule.defaultAuditReason()
                        : auditReason,
                rule.domainEventCode(),
                rule.failureHandling(),
                rule.timeoutPolicy(),
                rule.recovery());
    }

    public boolean isTerminal(A2ARequestStatus status) {
        return TERMINAL.contains(status);
    }
}
