package com.opensocket.aievent.core.events;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Accepted Agent callback projected after the callback inbox and Task/dispatch
 * transition have been committed. Token values are one-way fingerprints; raw
 * dispatch/fencing secrets must never leave Execution Control.
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
        OffsetDateTime occurredAt,
        String agentSessionId,
        Integer attemptNo,
        String dispatchTokenHash,
        String fencingTokenHash,
        String payloadHash,
        String correlationId,
        String causationId,
        String traceId,
        String spanId,
        String actorType,
        String actorId) implements ModuleEvent {
    public static final String TYPE = "task.callback.accepted.v1";

    public TaskCallbackAcceptedEvent {
        payload = payload == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }

    /** Compatibility constructor for the full pre-Phase-3 callback event. */
    public TaskCallbackAcceptedEvent(
            String eventId, String callbackId, String callbackType, String taskId, String tenantId,
            String dispatchRequestId, String assignmentId, String agentId, String taskStatus,
            String dispatchStatus, String idempotencyKey, String callbackFingerprint,
            String resultStatus, String errorCode, String errorMessage, String message,
            Integer progressPercent, Map<String, Object> payload, OffsetDateTime acceptedAt,
            OffsetDateTime occurredAt, String agentSessionId, Integer attemptNo,
            String dispatchTokenHash, String fencingTokenHash, String payloadHash) {
        this(eventId,callbackId,callbackType,taskId,tenantId,dispatchRequestId,assignmentId,agentId,
                taskStatus,dispatchStatus,idempotencyKey,callbackFingerprint,resultStatus,errorCode,errorMessage,
                message,progressPercent,payload,acceptedAt,occurredAt,agentSessionId,attemptNo,dispatchTokenHash,
                fencingTokenHash,payloadHash,null,null,null,null,null,null);
    }

    /** Compatibility constructor for earlier module-event tests and handlers. */
    public TaskCallbackAcceptedEvent(
            String eventId, String callbackId, String callbackType, String taskId, String tenantId,
            String dispatchRequestId, String assignmentId, String agentId, String taskStatus,
            String dispatchStatus, String idempotencyKey, String callbackFingerprint,
            String resultStatus, String errorCode, String errorMessage, String message,
            Integer progressPercent, Map<String, Object> payload, OffsetDateTime acceptedAt,
            OffsetDateTime occurredAt) {
        this(eventId, callbackId, callbackType, taskId, tenantId, dispatchRequestId, assignmentId,
                agentId, taskStatus, dispatchStatus, idempotencyKey, callbackFingerprint,
                resultStatus, errorCode, errorMessage, message, progressPercent, payload,
                acceptedAt, occurredAt, null, null, null, null, null,
                null,null,null,null,null,null);
    }

    @Override public String eventType() { return TYPE; }
    @Override public String aggregateType() { return "TASK"; }
    @Override public String rootTaskId() { return taskId; }
    @Override public String aggregateId() { return taskId; }
}
