package com.opensocket.aievent.core.events;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Accepted Agent callback projected after the callback inbox and Task/dispatch
 * transition have been committed. Consumers must still enforce their own
 * idempotency because Module Events are delivered at least once.
 */
public record TaskCallbackAcceptedEvent(
        String eventId,
        String callbackId,
        String callbackType,
        String taskId,
        String tenantId,
        String dispatchRequestId,
        String assignmentId,
        String agentId,
        String taskStatus,
        String dispatchStatus,
        String idempotencyKey,
        String callbackFingerprint,
        String resultStatus,
        String errorCode,
        String errorMessage,
        String message,
        Integer progressPercent,
        Map<String, Object> payload,
        OffsetDateTime acceptedAt,
        OffsetDateTime occurredAt) implements ModuleEvent {
    public static final String TYPE = "task.callback.accepted.v1";

    public TaskCallbackAcceptedEvent {
        payload = payload == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }

    @Override public String eventType() { return TYPE; }
    @Override public String aggregateType() { return "TASK"; }
    @Override public String aggregateId() { return taskId; }
}
