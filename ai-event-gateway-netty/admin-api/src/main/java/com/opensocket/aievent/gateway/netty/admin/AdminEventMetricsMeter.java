package com.opensocket.aievent.gateway.netty.admin;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;

/**
 * Lightweight in-memory event throughput meter.
 *
 * <p>The meter keeps timestamp buckets for the last minute only. It is intentionally simple and
 * local-node scoped; production-grade cross-node metrics should later be exported to Prometheus,
 * OpenTelemetry, or ai-event-gateway-core.</p>
 */
@Component
public class AdminEventMetricsMeter implements AdminEventMetricsRecorder {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final ArrayDeque<Instant> inboundEvents = new ArrayDeque<>();
    private final ArrayDeque<Instant> routedEvents = new ArrayDeque<>();
    private final ArrayDeque<Instant> failedEvents = new ArrayDeque<>();

    public void recordInbound() {
        record(inboundEvents);
    }

    @Override
    public void recordRouted() {
        record(routedEvents);
    }

    @Override
    public void recordFailed() {
        record(failedEvents);
    }

    public long inboundEventsPerMinute() {
        return count(inboundEvents);
    }

    public long routedEventsPerMinute() {
        return count(routedEvents);
    }

    public long failedEventsPerMinute() {
        return count(failedEvents);
    }

    private synchronized void record(ArrayDeque<Instant> events) {
        events.addLast(Instant.now());
        trim(events);
    }

    private synchronized long count(ArrayDeque<Instant> events) {
        trim(events);
        return events.size();
    }

    private void trim(ArrayDeque<Instant> events) {
        var cutoff = Instant.now().minus(WINDOW);
        while (!events.isEmpty() && events.peekFirst().isBefore(cutoff)) {
            events.removeFirst();
        }
    }
}
