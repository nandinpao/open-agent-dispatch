package com.opensocket.aievent.gateway.netty.delivery;

import com.opensocket.aievent.gateway.netty.agent.ConnectionType;
import com.opensocket.aievent.gateway.netty.config.GatewayProperties;
import com.opensocket.aievent.gateway.netty.protocol.MessageType;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory transport-level command delivery tracker.
 *
 * <p>This is intentionally local and bounded. It is not a task store, audit database, retry queue,
 * or distributed delivery ledger. It only supports Admin UI diagnostics for the current gateway
 * process.</p>
 */
@Component
public class CommandDeliveryTracker {

    private static final int DEFAULT_HISTORY_LIMIT = 500;

    private final GatewayProperties gatewayProperties;
    private final int historyLimit;
    private final Object historyLock = new Object();
    private final ArrayDeque<CommandDeliveryAttemptRecord> history = new ArrayDeque<>();
    private final AtomicLong totalAttempts = new AtomicLong();
    private final AtomicLong activeDeliveries = new AtomicLong();
    private final Map<DeliveryStatus, AtomicLong> counters = new EnumMap<>(DeliveryStatus.class);

    public CommandDeliveryTracker(GatewayProperties gatewayProperties) {
        this.gatewayProperties = gatewayProperties;
        this.historyLimit = Integer.getInteger("ai.gateway.delivery.history.limit", DEFAULT_HISTORY_LIMIT);
        for (DeliveryStatus status : DeliveryStatus.values()) {
            counters.put(status, new AtomicLong());
        }
    }

    public DeliveryAttempt begin(
            String commandId,
            String traceId,
            String agentId,
            MessageType messageType,
            String issuedBy,
            String taskId,
            String assignmentId,
            String dispatchRequestId,
            Integer attemptNo
    ) {
        totalAttempts.incrementAndGet();
        activeDeliveries.incrementAndGet();
        return new DeliveryAttempt(
                "delivery-" + UUID.randomUUID(),
                commandId,
                traceId,
                agentId,
                gatewayProperties.nodeId(),
                messageType,
                issuedBy,
                taskId,
                assignmentId,
                dispatchRequestId,
                attemptNo,
                OffsetDateTime.now()
        );
    }

    public CommandDeliveryAttemptRecord complete(
            DeliveryAttempt attempt,
            ConnectionType connectionType,
            DeliveryStatus status,
            String message
    ) {
        var completedAt = OffsetDateTime.now();
        counters.computeIfAbsent(status, key -> new AtomicLong()).incrementAndGet();
        activeDeliveries.updateAndGet(value -> Math.max(0L, value - 1L));
        var record = new CommandDeliveryAttemptRecord(
                attempt.attemptId(),
                attempt.commandId(),
                attempt.traceId(),
                attempt.agentId(),
                attempt.gatewayNodeId(),
                attempt.messageType(),
                attempt.issuedBy(),
                attempt.taskId(),
                attempt.assignmentId(),
                attempt.dispatchRequestId(),
                attempt.attemptNo(),
                connectionType,
                status,
                attempt.requestedAt(),
                completedAt,
                Math.max(0L, java.time.Duration.between(attempt.requestedAt(), completedAt).toMillis()),
                message == null ? "" : message
        );
        synchronized (historyLock) {
            history.addFirst(record);
            while (history.size() > historyLimit) {
                history.removeLast();
            }
        }
        return record;
    }

    public List<CommandDeliveryAttemptRecord> recent(int limit) {
        var safeLimit = normalizeLimit(limit);
        synchronized (historyLock) {
            return history.stream().limit(safeLimit).toList();
        }
    }

    public CommandDeliveryHistoryResponse historyResponse(int limit) {
        var records = recent(limit);
        return new CommandDeliveryHistoryResponse(
                gatewayProperties.nodeId(),
                normalizeLimit(limit),
                historySize(),
                records,
                OffsetDateTime.now()
        );
    }

    public CommandDeliveryMetrics metrics() {
        var byStatus = new EnumMap<DeliveryStatus, Long>(DeliveryStatus.class);
        for (DeliveryStatus status : DeliveryStatus.values()) {
            byStatus.put(status, counters.getOrDefault(status, new AtomicLong()).get());
        }
        var delivered = byStatus.getOrDefault(DeliveryStatus.DELIVERED, 0L);
        var failed = Math.max(0L, totalAttempts.get() - delivered - activeDeliveries.get());
        return new CommandDeliveryMetrics(
                gatewayProperties.nodeId(),
                totalAttempts.get(),
                delivered,
                failed,
                byStatus.getOrDefault(DeliveryStatus.AGENT_NOT_CONNECTED, 0L),
                byStatus.getOrDefault(DeliveryStatus.CONNECTION_NOT_WRITABLE, 0L),
                byStatus.getOrDefault(DeliveryStatus.DELIVERY_TIMEOUT, 0L),
                byStatus.getOrDefault(DeliveryStatus.INVALID_COMMAND, 0L),
                byStatus.getOrDefault(DeliveryStatus.MISSING_DISPATCH_CONTEXT, 0L),
                byStatus.getOrDefault(DeliveryStatus.AGENT_NOT_AUTHORIZED, 0L),
                activeDeliveries.get(),
                historySize(),
                Map.copyOf(byStatus),
                OffsetDateTime.now()
        );
    }

    public long activeDeliveries() {
        return activeDeliveries.get();
    }

    public long historySize() {
        synchronized (historyLock) {
            return history.size();
        }
    }

    public long failedAttempts() {
        return metrics().failedAttempts();
    }

    public long deliveredAttempts() {
        return counters.getOrDefault(DeliveryStatus.DELIVERED, new AtomicLong()).get();
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 100;
        }
        return Math.min(limit, historyLimit);
    }

    public record DeliveryAttempt(
            String attemptId,
            String commandId,
            String traceId,
            String agentId,
            String gatewayNodeId,
            MessageType messageType,
            String issuedBy,
            String taskId,
            String assignmentId,
            String dispatchRequestId,
            Integer attemptNo,
            OffsetDateTime requestedAt
    ) {
    }
}
