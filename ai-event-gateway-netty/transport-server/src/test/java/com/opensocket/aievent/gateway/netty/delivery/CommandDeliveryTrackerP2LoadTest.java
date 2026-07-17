package com.opensocket.aievent.gateway.netty.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.gateway.netty.agent.ConnectionType;
import com.opensocket.aievent.gateway.netty.config.GatewayProperties;
import com.opensocket.aievent.gateway.netty.protocol.MessageType;

class CommandDeliveryTrackerP2LoadTest {

    @Test
    void concurrentDeliveryCompletionsShouldLeaveNoActiveLeaksAndBoundHistory() throws Exception {
        String previousLimit = System.getProperty("ai.gateway.delivery.history.limit");
        System.setProperty("ai.gateway.delivery.history.limit", "128");
        try {
            CommandDeliveryTracker tracker = new CommandDeliveryTracker(
                    new GatewayProperties("gateway-node-p2", "p2", "p2-test", "P2 test gateway"));
            int attemptCount = 1_000;
            int workerCount = 32;
            CountDownLatch ready = new CountDownLatch(workerCount);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(workerCount);
            try {
                List<Future<Void>> futures = new ArrayList<>();
                for (int workerIndex = 0; workerIndex < workerCount; workerIndex++) {
                    final int currentWorkerIndex = workerIndex;
                    Callable<Void> task = () -> {
                        ready.countDown();
                        start.await(5, TimeUnit.SECONDS);
                        for (int index = currentWorkerIndex; index < attemptCount; index += workerCount) {
                            var attempt = tracker.begin(
                                    "cmd-p2-" + index,
                                    "trace-p2-" + index,
                                    "agent-p2-" + index,
                                    MessageType.TASK_DISPATCH,
                                    "core-control-plane",
                                    "task-p2-" + index,
                                    "assign-p2-" + index,
                                    "dispatch-p2-" + index,
                                    1);
                            tracker.complete(attempt, ConnectionType.WEBSOCKET, DeliveryStatus.DELIVERED, "ok");
                        }
                        return null;
                    };
                    futures.add(executor.submit(task));
                }

                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                for (Future<Void> future : futures) {
                    future.get(10, TimeUnit.SECONDS);
                }

                CommandDeliveryMetrics metrics = tracker.metrics();
                assertThat(metrics.totalAttempts()).isEqualTo(attemptCount);
                assertThat(metrics.deliveredAttempts()).isEqualTo(attemptCount);
                assertThat(metrics.failedAttempts()).isZero();
                assertThat(metrics.activeDeliveries()).isZero();
                assertThat(metrics.historySize()).isEqualTo(128);
                assertThat(tracker.historyResponse(1_000).records()).hasSize(128);
            } finally {
                executor.shutdownNow();
            }
        } finally {
            if (previousLimit == null) {
                System.clearProperty("ai.gateway.delivery.history.limit");
            } else {
                System.setProperty("ai.gateway.delivery.history.limit", previousLimit);
            }
        }
    }
}
