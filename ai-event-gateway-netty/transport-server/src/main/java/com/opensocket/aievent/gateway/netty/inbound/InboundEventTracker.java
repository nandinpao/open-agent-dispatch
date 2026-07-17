package com.opensocket.aievent.gateway.netty.inbound;

import com.opensocket.aievent.gateway.netty.agent.ConnectionType;
import com.opensocket.aievent.gateway.netty.config.CoreForwardProperties;
import com.opensocket.aievent.gateway.netty.config.GatewayProperties;
import com.opensocket.aievent.gateway.netty.config.InboundEventCategory;
import com.opensocket.aievent.gateway.netty.protocol.AiEventEnvelope;
import com.opensocket.aievent.gateway.netty.protocol.MessageType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded in-memory inbound transport tracker.
 *
 * <p>This is local-node only and intentionally not durable. Core / Control Plane owns dedup,
 * persistence, task decisions, and event stores.</p>
 */
@Component
public class InboundEventTracker {

    private final GatewayProperties gatewayProperties;
    private final CoreForwardProperties coreForwardProperties;
    private final Object historyLock = new Object();
    private final ArrayDeque<InboundEventRecord> history = new ArrayDeque<>();
    private final AtomicLong totalInboundEvents = new AtomicLong();
    private final AtomicLong activeForwards = new AtomicLong();
    private final Map<InboundForwardStatus, AtomicLong> counters = new EnumMap<>(InboundForwardStatus.class);
    private final Map<InboundEventCategory, AtomicLong> categoryCounters = new EnumMap<>(InboundEventCategory.class);

    public InboundEventTracker(GatewayProperties gatewayProperties, CoreForwardProperties coreForwardProperties) {
        this.gatewayProperties = gatewayProperties;
        this.coreForwardProperties = coreForwardProperties;
        for (InboundForwardStatus status : InboundForwardStatus.values()) {
            counters.put(status, new AtomicLong());
        }
        for (InboundEventCategory category : InboundEventCategory.values()) {
            categoryCounters.put(category, new AtomicLong());
        }
    }

    public InboundAttempt begin(
            AiEventEnvelope<JsonNode> envelope,
            ConnectionType connectionType,
            String connectionId,
            String agentId
    ) {
        var category = InboundEventClassifier.classify(envelope.messageType());
        totalInboundEvents.incrementAndGet();
        categoryCounters.computeIfAbsent(category, key -> new AtomicLong()).incrementAndGet();
        return new InboundAttempt(
                "inbound-" + UUID.randomUUID(),
                envelope.messageId(),
                envelope.messageType(),
                envelope.eventType(),
                category,
                envelope.source(),
                envelope.target(),
                gatewayProperties.nodeId(),
                gatewayProperties.siteId(),
                connectionType,
                connectionId,
                agentId,
                OffsetDateTime.now()
        );
    }

    public InboundEventRecord complete(InboundAttempt attempt, InboundForwardStatus status, String message) {
        return complete(attempt, status, message, true);
    }

    public InboundEventRecord complete(
            InboundAttempt attempt,
            InboundForwardStatus status,
            String message,
            boolean storeHistory
    ) {
        var completedAt = OffsetDateTime.now();
        counters.computeIfAbsent(status, key -> new AtomicLong()).incrementAndGet();
        var record = new InboundEventRecord(
                attempt.inboundId(),
                attempt.messageId(),
                attempt.messageType(),
                attempt.eventType(),
                attempt.category(),
                attempt.source(),
                attempt.target(),
                attempt.gatewayNodeId(),
                attempt.siteId(),
                attempt.connectionType(),
                attempt.connectionId(),
                attempt.agentId(),
                status,
                attempt.receivedAt(),
                completedAt,
                Math.max(0L, java.time.Duration.between(attempt.receivedAt(), completedAt).toMillis()),
                message == null ? "" : message
        );
        if (storeHistory) {
            synchronized (historyLock) {
                history.addFirst(record);
                while (history.size() > coreForwardProperties.historyLimit()) {
                    history.removeLast();
                }
            }
        }
        return record;
    }

    public void forwardStarted() {
        activeForwards.incrementAndGet();
    }

    public void forwardCompleted() {
        activeForwards.updateAndGet(value -> Math.max(0L, value - 1L));
    }

    public List<InboundEventRecord> recent(int limit) {
        var safeLimit = normalizeLimit(limit);
        synchronized (historyLock) {
            return history.stream().limit(safeLimit).toList();
        }
    }

    public InboundEventHistoryResponse historyResponse(int limit) {
        var records = recent(limit);
        return new InboundEventHistoryResponse(
                gatewayProperties.nodeId(),
                normalizeLimit(limit),
                historySize(),
                records,
                OffsetDateTime.now()
        );
    }

    public InboundEventMetrics metrics() {
        var byStatus = new EnumMap<InboundForwardStatus, Long>(InboundForwardStatus.class);
        for (InboundForwardStatus status : InboundForwardStatus.values()) {
            byStatus.put(status, counters.getOrDefault(status, new AtomicLong()).get());
        }
        var byCategory = new EnumMap<InboundEventCategory, Long>(InboundEventCategory.class);
        for (InboundEventCategory category : InboundEventCategory.values()) {
            byCategory.put(category, categoryCounters.getOrDefault(category, new AtomicLong()).get());
        }
        return new InboundEventMetrics(
                gatewayProperties.nodeId(),
                coreForwardProperties.enabled(),
                coreForwardProperties.enabled() ? coreForwardProperties.endpointUrl() : "",
                totalInboundEvents.get(),
                byStatus.getOrDefault(InboundForwardStatus.FORWARDED, 0L),
                byStatus.getOrDefault(InboundForwardStatus.FORWARD_DISABLED, 0L),
                byStatus.getOrDefault(InboundForwardStatus.FORWARD_SKIPPED_BY_CATEGORY, 0L),
                byStatus.getOrDefault(InboundForwardStatus.FORWARD_FAILED, 0L),
                byStatus.getOrDefault(InboundForwardStatus.FORWARD_TIMEOUT, 0L),
                activeForwards.get(),
                historySize(),
                Map.copyOf(byStatus),
                Map.copyOf(byCategory),
                OffsetDateTime.now()
        );
    }

    public long historySize() {
        synchronized (historyLock) {
            return history.size();
        }
    }

    public long activeForwards() {
        return activeForwards.get();
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 100;
        }
        return Math.min(limit, coreForwardProperties.historyLimit());
    }

    public record InboundAttempt(
            String inboundId,
            String messageId,
            MessageType messageType,
            String eventType,
            InboundEventCategory category,
            String source,
            String target,
            String gatewayNodeId,
            String siteId,
            ConnectionType connectionType,
            String connectionId,
            String agentId,
            OffsetDateTime receivedAt
    ) {
    }
}
