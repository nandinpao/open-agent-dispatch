package com.opensocket.aievent.core.events;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record TaskTerminalEvent(
        String eventId,
        String taskId,
        String incidentId,
        String sourceEventId,
        String taskStatus,
        String taskType,
        String priority,
        String tenantId,
        String siteId,
        String plantId,
        String objectType,
        String objectId,
        String sourceEventType,
        String errorCode,
        String routingPolicy,
        List<String> requiredCapabilities,
        String dispatchRequestId,
        String assignmentId,
        String agentId,
        String ownerGatewayNodeId,
        String agentSessionId,
        String callbackId,
        String callbackType,
        String callbackMessage,
        String resultStatus,
        String callbackErrorCode,
        String callbackErrorMessage,
        Map<String, Object> payload,
        OffsetDateTime occurredAt) implements ModuleEvent {
    public static final String TYPE = "task.terminal.v1";
    public TaskTerminalEvent {
        requiredCapabilities = requiredCapabilities == null ? List.of() : List.copyOf(requiredCapabilities);
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
    @Override public String eventType() { return TYPE; }
    @Override public String aggregateType() { return "TASK"; }
    @Override public String aggregateId() { return taskId; }
}
