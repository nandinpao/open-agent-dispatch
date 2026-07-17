package com.opensocket.aievent.worker;

import com.opensocket.aievent.service.adapter.AdapterWorkItem;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class LeaseHeartbeatGuardTest {
    private ThreadPoolTaskScheduler scheduler;
    private HttpServer server;

    @AfterEach
    void cleanup() {
        if (scheduler != null) scheduler.shutdown();
        if (server != null) server.stop(0);
    }

    @Test
    void shouldUseSharedSchedulerCreateShortObservationAndCancelWithoutOwningPool() throws Exception {
        CountDownLatch heartbeatReceived = new CountDownLatch(1);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/adapter-actions/action-1/heartbeat", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("X-Internal-Token")).isEqualTo("worker-token");
            assertThat(exchange.getRequestHeaders().getFirst("X-OpenSocket-Contract")).isNotBlank();
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
            heartbeatReceived.countDown();
        });
        server.start();

        AdapterWorkerProperties properties = new AdapterWorkerProperties();
        properties.setCoreBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setToken("worker-token");
        CoreAdapterActionClient client = new CoreAdapterActionClient(properties, JsonMapper.builder().build(),
                RestClient.builder().baseUrl(properties.getCoreBaseUrl()).build());

        ObservationRegistry registry = ObservationRegistry.create();
        AtomicInteger heartbeatObservations = new AtomicInteger();
        registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
            @Override public boolean supportsContext(Observation.Context context) { return true; }
            @Override public void onStop(Observation.Context context) {
                if ("adapter.lease.heartbeat".equals(context.getName())) heartbeatObservations.incrementAndGet();
            }
        });
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("heartbeat-test-");
        scheduler.initialize();

        LeaseHeartbeatGuard guard = new LeaseHeartbeatGuard(
                scheduler, registry, client, item(), 30, Duration.ofMillis(20));
        assertThat(heartbeatReceived.await(3, TimeUnit.SECONDS)).isTrue();
        guard.close();
        assertThat(heartbeatObservations.get()).isGreaterThanOrEqualTo(1);
        assertThat(scheduler.getScheduledThreadPoolExecutor().isShutdown()).isFalse();
    }

    @Test
    void shouldStartHeartbeatAsRootEvenWhenCallerObservationIsCurrent() {
        ObservationRegistry registry = ObservationRegistry.create();
        AtomicInteger stopped = new AtomicInteger();
        registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
            @Override public boolean supportsContext(Observation.Context context) { return true; }
            @Override public void onStop(Observation.Context context) {
                if ("adapter.lease.heartbeat".equals(context.getName())) stopped.incrementAndGet();
            }
        });
        AdapterWorkerProperties properties = new AdapterWorkerProperties();
        CountingClient client = new CountingClient(properties);
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.initialize();
        LeaseHeartbeatGuard guard = new LeaseHeartbeatGuard(
                scheduler, registry, client, item(), 30, Duration.ofDays(1));

        Observation caller = Observation.start("adapter.execution", registry);
        try (Observation.Scope ignored = caller.openScope()) {
            guard.runHeartbeat(registry, client, item(), 30);
        }
        finally {
            caller.stop();
            guard.close();
        }

        assertThat(client.heartbeats.get()).isEqualTo(1);
        assertThat(stopped.get()).isEqualTo(1);
    }

    private AdapterWorkItem item() {
        return new AdapterWorkItem(
                "action-1",       // actionId
                "idem-1",         // idempotencyKey
                "incident-1",     // incidentId
                "task-1",         // taskId
                "dispatch-1",     // dispatchRequestId
                "assignment-1",   // assignmentId
                "agent-1",        // agentId
                "adapter",        // adapterName
                "MCP",            // adapterType
                "EXECUTE",        // actionType
                "EXECUTING",      // status
                null,              // reason
                null,              // requestHash
                null,              // responseRef
                Map.of(),          // payload
                null,              // createdAt
                null,              // updatedAt
                null,              // executingAt
                null,              // completedAt
                null,              // failedAt
                null,              // nextAttemptAt
                null,              // retryWaitingAt
                null,              // executorUnavailableAt
                null,              // claimedBy
                null,              // claimedAt
                null,              // leaseExpiresAt
                null,              // workerHeartbeatAt
                1,                 // attemptCount
                3,                 // maxAttempts
                "worker",         // executorName
                null);             // lastError
    }

    private static final class CountingClient extends CoreAdapterActionClient {
        private final AtomicInteger heartbeats = new AtomicInteger();
        CountingClient(AdapterWorkerProperties properties) {
            super(properties, JsonMapper.builder().build(), RestClient.builder().build());
        }
        @Override public void heartbeat(AdapterWorkItem item) { heartbeats.incrementAndGet(); }
        @Override String workerId() { return "worker-test"; }
    }
}
