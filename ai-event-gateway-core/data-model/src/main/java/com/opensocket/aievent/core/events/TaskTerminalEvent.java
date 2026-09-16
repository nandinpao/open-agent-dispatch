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
        OffsetDateTime occurredAt,
        String correlationId,
        String causationId,
        String traceId,
        String spanId,
        String actorType,
        String actorId) implements ModuleEvent {
    public static final String TYPE = "task.terminal.v1";
    public TaskTerminalEvent {
        requiredCapabilities = requiredCapabilities == null ? List.of() : List.copyOf(requiredCapabilities);
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    /** Compatibility constructor for pre-Phase-3 publishers and persisted payload tests. */
    public TaskTerminalEvent(
            String eventId, String taskId, String incidentId, String sourceEventId, String taskStatus,
            String taskType, String priority, String tenantId, String siteId, String plantId,
            String objectType, String objectId, String sourceEventType, String errorCode,
            String routingPolicy, List<String> requiredCapabilities, String dispatchRequestId,
            String assignmentId, String agentId, String ownerGatewayNodeId, String agentSessionId,
            String callbackId, String callbackType, String callbackMessage, String resultStatus,
            String callbackErrorCode, String callbackErrorMessage, Map<String,Object> payload,
            OffsetDateTime occurredAt) {
        this(eventId,taskId,incidentId,sourceEventId,taskStatus,taskType,priority,tenantId,siteId,plantId,
                objectType,objectId,sourceEventType,errorCode,routingPolicy,requiredCapabilities,dispatchRequestId,
                assignmentId,agentId,ownerGatewayNodeId,agentSessionId,callbackId,callbackType,callbackMessage,
                resultStatus,callbackErrorCode,callbackErrorMessage,payload,occurredAt,null,null,null,null,null,null);
    }

    @Override public String eventType() { return TYPE; }
    @Override public String aggregateType() { return "TASK"; }
    @Override public String rootTaskId() { return taskId; }
    @Override public String aggregateId() { return taskId; }
}
