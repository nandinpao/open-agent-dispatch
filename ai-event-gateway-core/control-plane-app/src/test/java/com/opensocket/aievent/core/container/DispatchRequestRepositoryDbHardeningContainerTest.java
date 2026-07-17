package com.opensocket.aievent.core.container;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.dispatch.DispatchRequest;
import com.opensocket.aievent.core.dispatch.DispatchRequestRepository;
import com.opensocket.aievent.core.dispatch.DispatchRequestStatus;
import com.opensocket.aievent.core.dispatch.DispatchStatusTransition;
import com.opensocket.aievent.core.kernel.persistence.ClaimRequest;
import com.opensocket.aievent.core.kernel.persistence.PersistenceWriteOutcome;

class DispatchRequestRepositoryDbHardeningContainerTest extends P25RepositoryDbContainerSupport {

    @Test
    void claimExecutableMustSkipLockedRowsAndFenceConditionalCompletion() throws Exception {
        seedDispatchParents("incident-db-1", "task-db-1", "assignment-db-1");
        DispatchRequestRepository repository = dispatchRequestRepository();
        repository.save(dispatch("dispatch-db-1", "incident-db-1", "task-db-1", "assignment-db-1"));

        var claimTime = now();
        List<ThrowingSupplier<List<DispatchRequest>>> workers = List.of(
                () -> repository.claimExecutable(ClaimRequest.forLease("worker-a", claimTime, Duration.ofSeconds(5), 10)),
                () -> repository.claimExecutable(ClaimRequest.forLease("worker-b", claimTime, Duration.ofSeconds(5), 10)));

        List<List<DispatchRequest>> claimBatches = runConcurrent(workers);
        List<DispatchRequest> claims = claimBatches.stream()
                .filter(Objects::nonNull)
                .flatMap(batch -> batch.stream().filter(Objects::nonNull))
                .toList();

        assertThat(claims).hasSize(1);
        DispatchRequest firstClaim = claims.getFirst();
        assertThat(firstClaim.getStatus()).isEqualTo(DispatchRequestStatus.DISPATCHING);
        assertThat(firstClaim.getAttemptCount()).isEqualTo(1);
        assertThat(firstClaim.getClaimedBy()).isIn("worker-a", "worker-b");

        DispatchRequest blockedBeforeExpiry = repository.claimById(
                "dispatch-db-1",
                ClaimRequest.forLease("worker-c", firstClaim.getClaimUntil().minusSeconds(1), Duration.ofSeconds(30), 1))
                .orElse(null);
        assertThat(blockedBeforeExpiry).isNull();

        DispatchRequest reclaimed = repository.claimById(
                "dispatch-db-1",
                ClaimRequest.forLease("worker-c", firstClaim.getClaimUntil().plusSeconds(1), Duration.ofSeconds(30), 1))
                .orElseThrow();
        assertThat(reclaimed.getClaimedBy()).isEqualTo("worker-c");
        assertThat(reclaimed.getAttemptCount()).isEqualTo(2);

        DispatchStatusTransition staleAttempt = transition(
                "dispatch-db-1",
                DispatchRequestStatus.DISPATCHING,
                DispatchRequestStatus.COMPLETED,
                1,
                "dispatch-token-1",
                "callback-stale");
        assertThat(repository.transitionStatus(staleAttempt).outcome()).isEqualTo(PersistenceWriteOutcome.CONFLICT);

        DispatchStatusTransition validAttempt = transition(
                "dispatch-db-1",
                DispatchRequestStatus.DISPATCHING,
                DispatchRequestStatus.COMPLETED,
                2,
                "dispatch-token-1",
                "callback-ok");
        assertThat(repository.transitionStatus(validAttempt).applied()).isTrue();

        DispatchRequest completed = repository.findById("dispatch-db-1").orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(DispatchRequestStatus.COMPLETED);
        assertThat(completed.getLastCallbackId()).isEqualTo("callback-ok");
        assertThat(completed.getClaimedBy()).isNull();
        assertThat(completed.getClaimUntil()).isNull();
    }

    @Test
    void openAssignmentLookupMustIgnoreTerminalDispatches() {
        seedDispatchParents("incident-db-2", "task-db-2", "assignment-db-2");
        DispatchRequestRepository repository = dispatchRequestRepository();
        DispatchRequest terminal = dispatch("dispatch-db-2a", "incident-db-2", "task-db-2", "assignment-db-2");
        terminal.setStatus(DispatchRequestStatus.COMPLETED);
        repository.save(terminal);

        assertThat(repository.findOpenByAssignmentId("assignment-db-2")).isEmpty();

        DispatchRequest retryWaiting = dispatch("dispatch-db-2b", "incident-db-2", "task-db-2", "assignment-db-2");
        retryWaiting.setStatus(DispatchRequestStatus.RETRY_WAITING);
        retryWaiting.setNextRetryAt(now().minusSeconds(1));
        repository.save(retryWaiting);

        assertThat(repository.findOpenByAssignmentId("assignment-db-2"))
                .hasValueSatisfying(request -> assertThat(request.getDispatchRequestId()).isEqualTo("dispatch-db-2b"));
    }

    private DispatchStatusTransition transition(
            String dispatchRequestId,
            DispatchRequestStatus allowedCurrent,
            DispatchRequestStatus next,
            int expectedAttemptNo,
            String expectedDispatchToken,
            String callbackId) {
        DispatchStatusTransition transition = new DispatchStatusTransition();
        transition.setDispatchRequestId(dispatchRequestId);
        transition.setAllowedCurrentStatuses(List.of(allowedCurrent));
        transition.setNewStatus(next);
        transition.setExpectedAttemptNo(expectedAttemptNo);
        transition.setExpectedDispatchToken(expectedDispatchToken);
        transition.setLastCallbackId(callbackId);
        transition.setReason("P25 transition guard");
        transition.setCompletedAt(now());
        transition.setUpdatedAt(now());
        transition.setClearClaim(true);
        return transition;
    }
}
