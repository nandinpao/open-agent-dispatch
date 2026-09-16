package com.opensocket.aievent.core.dispatch;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.events.A2ADispatchProgressedEvent;
import com.opensocket.aievent.core.kernel.persistence.PersistenceWriteResult;
import com.opensocket.aievent.core.outbox.ModuleEventPublisher;

/** Deterministically repairs expired worker leases and gateway-accepted/local-write-uncertain outcomes. */
@Service
public class DispatchBridgeReconciliationService {
    private final DispatchRequestRepository requests;
    private final DispatchAssignmentEvidenceService evidence;
    private final DispatchProperties properties;
    private final ModuleEventPublisher events;
    @Autowired(required = false)
    private List<DispatchRecoveryAuthority> recoveryAuthorities = List.of();

    public DispatchBridgeReconciliationService(
            DispatchRequestRepository requests,
            DispatchAssignmentEvidenceService evidence,
            DispatchProperties properties,
            ModuleEventPublisher events) {
        this.requests = requests;
        this.evidence = evidence;
        this.properties = properties;
        this.events = events;
    }

    public int reconcileDue(int limit) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int repaired = 0;
        for (DispatchRequest request : requests.findRecoveryCandidates(now, Math.max(1, Math.min(limit, 500)))) {
            if (reconcile(request, now)) {
                repaired++;
            }
        }
        return repaired;
    }

    public boolean reconcileById(String dispatchRequestId) {
        if (dispatchRequestId == null || dispatchRequestId.isBlank()) {
            throw new IllegalArgumentException("dispatchRequestId is required");
        }
        return requests.findById(dispatchRequestId)
                .map(value -> reconcile(value, OffsetDateTime.now(ZoneOffset.UTC)))
                .orElse(false);
    }

    private boolean reconcile(DispatchRequest request, OffsetDateTime now) {
        DispatchStatusTransition transition = transitionFor(request, now);
        if (transition == null) {
            return false;
        }
        transition.setLastReconciledAt(now);
        transition.setReconciliationCountIncrement(1);
        PersistenceWriteResult result = requests.transitionStatus(transition);
        if (!result.applied()) {
            return false;
        }
        DispatchRequest repaired = requests.findById(request.getDispatchRequestId()).orElse(request);
        evidence.record(repaired, "RECONCILED", null, null,
                "{\"result\":\"" + transition.newStatusName() + "\"}");
        publishProgress(repaired, transition, now);
        return true;
    }

    private DispatchStatusTransition transitionFor(DispatchRequest request, OffsetDateTime now) {
        DispatchRecoveryAuthorityDecision authorityDecision = recoveryAuthorities.stream()
                .filter(java.util.Objects::nonNull)
                .sorted(java.util.Comparator.comparingInt(DispatchRecoveryAuthority::order))
                .map(authority -> authority.reconcile(request, now))
                .filter(java.util.Objects::nonNull)
                .filter(DispatchRecoveryAuthorityDecision::applicable)
                .findFirst()
                .orElse(DispatchRecoveryAuthorityDecision.notApplicable());
        if (authorityDecision.action() == DispatchRecoveryAuthorityAction.HOLD_DELIVERY_UNKNOWN) {
            DispatchStatusTransition transition = base(request, now);
            transition.setNewStatus(DispatchRequestStatus.DELIVERY_UNKNOWN);
            transition.setOutboxStatus(DispatchOutboxStatus.RECOVERY_PENDING);
            transition.setRecoveryClassification(DispatchRecoveryClassification.RESPONSE_LOST);
            transition.setUncertainSince(now);
            transition.setReason(authorityDecision.message());
            return transition;
        }
        if (authorityDecision.action() == DispatchRecoveryAuthorityAction.CONFIRM_DELIVERED) {
            DispatchStatusTransition transition = base(request, now);
            transition.setNewStatus(DispatchRequestStatus.DISPATCHED);
            transition.setOutboxStatus(DispatchOutboxStatus.ACKNOWLEDGED);
            transition.setRecoveryClassification(DispatchRecoveryClassification.NONE);
            transition.setReason(authorityDecision.message());
            return transition;
        }
        boolean gatewayAccepted = evidence.hasGatewayAccepted(
                request.getDispatchRequestId(), request.getAttemptCount());
        if (gatewayAccepted
                && request.getRecoveryClassification() == DispatchRecoveryClassification.ACK_PERSISTENCE_UNCERTAIN) {
            DispatchStatusTransition transition = base(request, now);
            transition.setNewStatus(DispatchRequestStatus.DISPATCHED);
            transition.setOutboxStatus(DispatchOutboxStatus.ACKNOWLEDGED);
            transition.setRecoveryClassification(DispatchRecoveryClassification.NONE);
            transition.setReason("Reconciliation restored gateway-accepted dispatch from append-only evidence");
            return transition;
        }
        boolean expiredLease = (request.getOutboxStatus() == DispatchOutboxStatus.CLAIMED
                || request.getOutboxStatus() == DispatchOutboxStatus.DISPATCHING)
                && request.getClaimUntil() != null
                && !request.getClaimUntil().isAfter(now);
        if (!expiredLease) {
            return null;
        }
        boolean retry = request.getAttemptCount() < properties.getRetry().getMaxAttempts();
        DispatchStatusTransition transition = base(request, now);
        transition.setNewStatus(retry ? DispatchRequestStatus.RETRY_WAITING : DispatchRequestStatus.DEAD_LETTER);
        transition.setOutboxStatus(retry ? DispatchOutboxStatus.FAILED_RETRYABLE : DispatchOutboxStatus.DEAD_LETTER);
        transition.setRecoveryClassification(retry
                ? DispatchRecoveryClassification.LEASE_EXPIRED
                : DispatchRecoveryClassification.RETRY_EXHAUSTED);
        transition.setRetryWaitingAt(retry ? now : null);
        transition.setNextRetryAt(retry ? now.plus(properties.getRetry().getInitialBackoff()) : null);
        transition.setFailedAt(now);
        transition.setReason(retry
                ? "Expired worker lease recovered to retry waiting"
                : "Expired worker lease exhausted retry budget");
        return transition;
    }

    private DispatchStatusTransition base(DispatchRequest request, OffsetDateTime now) {
        DispatchStatusTransition transition = new DispatchStatusTransition();
        transition.setDispatchRequestId(request.getDispatchRequestId());
        transition.setAllowedCurrentStatuses(List.of(
                DispatchRequestStatus.DISPATCHING,
                DispatchRequestStatus.DELIVERY_UNKNOWN,
                DispatchRequestStatus.RETRY_WAITING,
                DispatchRequestStatus.DISPATCHED));
        transition.setExpectedAttemptNo(request.getAttemptCount());
        transition.setExpectedDispatchToken(request.getDispatchToken());
        transition.setUpdatedAt(now);
        transition.setClearClaim(true);
        return transition;
    }

    private void publishProgress(
            DispatchRequest request,
            DispatchStatusTransition transition,
            OffsetDateTime now) {
        String stage = transition.getNewStatus() == DispatchRequestStatus.DEAD_LETTER
                ? "DEAD_LETTER"
                : transition.getNewStatus() == DispatchRequestStatus.RETRY_WAITING
                        ? "FAILED_RETRYABLE"
                        : "RECOVERED";
        events.publish(new A2ADispatchProgressedEvent(
                "a2a-dispatch-reconcile-" + UUID.randomUUID(),
                request.getTenantId(),
                request.getTaskId(),
                request.getDispatchRequestId(),
                stage,
                transition.getNewStatus() == DispatchRequestStatus.DEAD_LETTER
                        ? "DISPATCH_DEAD_LETTER"
                        : null,
                transition.getReason(),
                "dispatch-reconcile:" + request.getDispatchRequestId() + ":" + request.getAttemptCount(),
                now));
    }
}
