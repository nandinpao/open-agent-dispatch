package com.opensocket.aievent.core.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.kernel.persistence.ClaimOwnership;
import com.opensocket.aievent.core.kernel.persistence.ClaimRequest;
import com.opensocket.aievent.core.kernel.persistence.LeaseRenewalRequest;
import com.opensocket.aievent.core.kernel.persistence.PersistenceWriteOutcome;

class InMemoryAdapterActionLeaseTest {

    @Test
    void concurrentExternalWorkersMustNotClaimTheSameAdapterAction() throws Exception {
        InMemoryAdapterActionRepository repository = new InMemoryAdapterActionRepository();
        repository.save(action("act-lease-race"));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        List<Optional<AdapterAction>> claims = runConcurrent(List.of(
                () -> repository.claimNext(AdapterType.ISSUE_TRACKING, ClaimRequest.forLease("worker-a", now, Duration.ofSeconds(30), 1)),
                () -> repository.claimNext(AdapterType.ISSUE_TRACKING, ClaimRequest.forLease("worker-b", now, Duration.ofSeconds(30), 1))));

        List<AdapterAction> successfulClaims = claims.stream().flatMap(Optional::stream).toList();
        assertThat(successfulClaims).hasSize(1);
        assertThat(successfulClaims.getFirst().getStatus()).isEqualTo(AdapterActionStatus.CLAIMED);
        assertThat(successfulClaims.getFirst().getClaimedBy()).isIn("worker-a", "worker-b");
    }

    @Test
    void staleWorkerMustNotFinalizeActionAfterLeaseIsReclaimed() {
        InMemoryAdapterActionRepository repository = new InMemoryAdapterActionRepository();
        repository.save(action("act-stale-fence"));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        AdapterAction first = repository.claimNext(
                AdapterType.ISSUE_TRACKING,
                ClaimRequest.forLease("worker-a", now, Duration.ofSeconds(5), 1)).orElseThrow();
        ClaimOwnership staleOwnership = new ClaimOwnership(first.getClaimedBy(), first.getLeaseExpiresAt());

        OffsetDateTime afterExpiry = first.getLeaseExpiresAt().plusSeconds(1);
        AdapterAction reclaimed = repository.claimNext(
                AdapterType.ISSUE_TRACKING,
                ClaimRequest.forLease("worker-b", afterExpiry, Duration.ofSeconds(30), 1)).orElseThrow();
        ClaimOwnership currentOwnership = new ClaimOwnership(reclaimed.getClaimedBy(), reclaimed.getLeaseExpiresAt());

        reclaimed.setStatus(AdapterActionStatus.COMPLETED);
        reclaimed.setCompletedAt(afterExpiry.plusSeconds(1));
        reclaimed.setUpdatedAt(afterExpiry.plusSeconds(1));
        assertThat(repository.saveClaimed(reclaimed, currentOwnership, afterExpiry.plusSeconds(1)).applied()).isTrue();

        first.setStatus(AdapterActionStatus.RETRY_WAITING);
        first.setUpdatedAt(afterExpiry.plusSeconds(2));
        assertThat(repository.saveClaimed(first, staleOwnership, afterExpiry.plusSeconds(2)).outcome())
                .isEqualTo(PersistenceWriteOutcome.OWNERSHIP_LOST);
        assertThat(repository.findById("act-stale-fence").orElseThrow().getStatus()).isEqualTo(AdapterActionStatus.COMPLETED);
    }

    @Test
    void leaseRenewalShouldFenceWrongOwnerAndAllowCurrentOwner() {
        InMemoryAdapterActionRepository repository = new InMemoryAdapterActionRepository();
        repository.save(action("act-renewal-fence"));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        AdapterAction claimed = repository.claimNext(
                AdapterType.ISSUE_TRACKING,
                ClaimRequest.forLease("worker-a", now, Duration.ofSeconds(30), 1)).orElseThrow();
        ClaimOwnership owner = new ClaimOwnership(claimed.getClaimedBy(), claimed.getLeaseExpiresAt());
        ClaimOwnership wrongOwner = new ClaimOwnership("worker-b", claimed.getLeaseExpiresAt());

        assertThat(repository.extendLease(new LeaseRenewalRequest(
                claimed.getActionId(),
                wrongOwner,
                now.plusSeconds(5),
                now.plusSeconds(60)))).isEmpty();

        Optional<AdapterAction> renewed = repository.extendLease(new LeaseRenewalRequest(
                claimed.getActionId(),
                owner,
                now.plusSeconds(5),
                now.plusSeconds(60)));

        assertThat(renewed).isPresent();
        assertThat(renewed.orElseThrow().getLeaseExpiresAt()).isEqualTo(now.plusSeconds(60));
    }

    private AdapterAction action(String actionId) {
        AdapterAction action = new AdapterAction();
        action.setActionId(actionId);
        action.setIdempotencyKey("idem-" + actionId);
        action.setIncidentId("incident-" + actionId);
        action.setTaskId("task-" + actionId);
        action.setAdapterName("issue-tracking");
        action.setAdapterType(AdapterType.ISSUE_TRACKING);
        action.setActionType(AdapterActionType.ISSUE_CREATE);
        action.setStatus(AdapterActionStatus.PENDING);
        action.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(5));
        action.setUpdatedAt(action.getCreatedAt());
        return action;
    }

    private <T> List<T> runConcurrent(List<ThrowingSupplier<T>> suppliers) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(suppliers.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (ThrowingSupplier<T> supplier : suppliers) {
            futures.add(executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return supplier.get();
            }));
        }
        start.countDown();
        List<T> results = new ArrayList<>();
        for (Future<T> future : futures) {
            results.add(future.get(15, TimeUnit.SECONDS));
        }
        executor.shutdownNow();
        return results;
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
