package com.opensocket.aievent.core.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.kernel.persistence.ClaimRequest;

class AdapterActionHighConcurrencyP2Test {

    @Test
    void saveNewOrGetByIdempotencyKeyShouldRemainSingleUnderConcurrentWriters() throws Exception {
        InMemoryAdapterActionRepository repository = new InMemoryAdapterActionRepository();
        int writerCount = 64;
        CountDownLatch ready = new CountDownLatch(writerCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(writerCount);
        try {
            List<Future<AdapterAction>> futures = IntStream.range(0, writerCount)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        start.await(5, TimeUnit.SECONDS);
                        return repository.saveNewOrGetByIdempotencyKey(newAction("action-idem-" + index, "idem:task-p2"));
                    }))
                    .toList();

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            Set<String> returnedIds = ConcurrentHashMap.newKeySet();
            for (Future<AdapterAction> future : futures) {
                returnedIds.add(future.get(5, TimeUnit.SECONDS).getActionId());
            }

            assertThat(returnedIds).hasSize(1);
            assertThat(repository.recent(10)).hasSize(1);
            assertThat(repository.findByIdempotencyKey("idem:task-p2")).isPresent();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentWorkersShouldClaimEachActionAtMostOnce() throws Exception {
        InMemoryAdapterActionRepository repository = new InMemoryAdapterActionRepository();
        int actionCount = 200;
        IntStream.range(0, actionCount)
                .forEach(index -> repository.save(newAction("action-claim-" + index, "idem:claim:" + index)));

        int workerCount = 32;
        CountDownLatch ready = new CountDownLatch(workerCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        Set<String> claimedIds = ConcurrentHashMap.newKeySet();
        AtomicInteger duplicates = new AtomicInteger();

        try {
            List<Future<Integer>> futures = IntStream.range(0, workerCount)
                    .mapToObj(workerIndex -> executor.submit(() -> {
                        int localClaims = 0;
                        ready.countDown();
                        start.await(5, TimeUnit.SECONDS);
                        while (true) {
                            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                            var claimed = repository.claimNext(
                                    AdapterType.ISSUE_TRACKING,
                                    ClaimRequest.forLease("worker-" + workerIndex, now, Duration.ofMinutes(1), 1));
                            if (claimed.isEmpty()) {
                                return localClaims;
                            }
                            localClaims++;
                            if (!claimedIds.add(claimed.orElseThrow().getActionId())) {
                                duplicates.incrementAndGet();
                            }
                        }
                    }))
                    .toList();

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int totalClaims = 0;
            for (Future<Integer> future : futures) {
                totalClaims += future.get(10, TimeUnit.SECONDS);
            }

            assertThat(totalClaims).isEqualTo(actionCount);
            assertThat(claimedIds).hasSize(actionCount);
            assertThat(duplicates).hasValue(0);
            assertThat(repository.findByStatus(AdapterActionStatus.CLAIMED, actionCount + 1)).hasSize(actionCount);
        } finally {
            executor.shutdownNow();
        }
    }

    private AdapterAction newAction(String actionId, String idempotencyKey) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        AdapterAction action = new AdapterAction();
        action.setActionId(actionId);
        action.setIdempotencyKey(idempotencyKey);
        action.setIncidentId("incident-p2");
        action.setTaskId("task-p2");
        action.setAdapterType(AdapterType.ISSUE_TRACKING);
        action.setActionType(AdapterActionType.ISSUE_CREATE);
        action.setAdapterName("redmine");
        action.setStatus(AdapterActionStatus.PENDING);
        action.setReason("p2 high concurrency test");
        action.setCreatedAt(now);
        action.setUpdatedAt(now);
        return action;
    }
}
