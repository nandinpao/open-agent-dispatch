package com.opensocket.aievent.worker;

import com.opensocket.aievent.service.adapter.AdapterWorkItem;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

/**
 * Cancels a lease heartbeat scheduled on the shared Spring TaskScheduler.
 *
 * <p>Each heartbeat creates a short root Observation. It deliberately does not retain the original
 * adapter execution span, which may have ended before a later renewal runs.</p>
 */
final class LeaseHeartbeatGuard implements AutoCloseable {
    private final ScheduledFuture<?> future;

    LeaseHeartbeatGuard(
            TaskScheduler scheduler,
            ObservationRegistry observationRegistry,
            CoreAdapterActionClient client,
            AdapterWorkItem item,
            long leaseSeconds
    ) {
        this(scheduler, observationRegistry, client, item, leaseSeconds,
                Duration.ofSeconds(Math.max(5, leaseSeconds / 3)));
    }

    LeaseHeartbeatGuard(
            TaskScheduler scheduler,
            ObservationRegistry observationRegistry,
            CoreAdapterActionClient client,
            AdapterWorkItem item,
            long leaseSeconds,
            Duration period
    ) {
        this.future = scheduler.scheduleAtFixedRate(
                () -> runHeartbeat(observationRegistry, client, item, leaseSeconds),
                Instant.now().plus(period),
                period);
    }

    void runHeartbeat(
            ObservationRegistry observationRegistry,
            CoreAdapterActionClient client,
            AdapterWorkItem item,
            long leaseSeconds
    ) {
        Observation observation = Observation.createNotStarted("adapter.lease.heartbeat", observationRegistry)
                .parentObservation(null)
                .contextualName("adapter lease heartbeat")
                .lowCardinalityKeyValue("adapter.type", safe(item.adapterType()))
                .highCardinalityKeyValue("adapter.action.id", safe(item.actionId()))
                .highCardinalityKeyValue("task.id", safe(item.taskId()))
                .highCardinalityKeyValue("worker.id", safe(client.workerId()))
                .highCardinalityKeyValue("lease.seconds", Long.toString(leaseSeconds))
                .start();
        try (Observation.Scope ignored = observation.openScope()) {
            client.heartbeat(item);
            observation.lowCardinalityKeyValue("adapter.lease.heartbeat.result", "success");
        }
        catch (RuntimeException failure) {
            observation.lowCardinalityKeyValue("adapter.lease.heartbeat.result", "failed");
            observation.error(failure);
            // Completion/failure callback remains authoritative; Core lease recovery handles a
            // worker that can no longer renew its claim.
        }
        finally {
            observation.stop();
        }
    }

    private String safe(String value) { return value == null || value.isBlank() ? "unknown" : value; }

    @Override
    public void close() {
        if (future != null) {
            future.cancel(false);
        }
    }
}
