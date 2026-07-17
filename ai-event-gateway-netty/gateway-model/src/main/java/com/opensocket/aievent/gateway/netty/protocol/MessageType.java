package com.opensocket.aievent.gateway.netty.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum MessageType {
    AGENT_REGISTER,
    AGENT_HEARTBEAT,
    AGENT_STATUS_CHANGE,
    TASK_SUBMIT,
    TASK_DISPATCH,
    TASK_ACK,
    TASK_PROGRESS,
    TASK_RESULT,
    TASK_ERROR,
    GATEWAY_ACK,
    ERROR,
    ADMIN_EVENT,
    CLUSTER_HELLO,
    CLUSTER_HEARTBEAT;

    @JsonCreator
    public static MessageType fromWireValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var normalized = value.trim();
        for (MessageType type : values()) {
            if (type.name().equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        var fromDomain = fromDomainEventName(normalized);
        if (fromDomain != null) {
            return fromDomain;
        }
        throw new IllegalArgumentException("Unsupported messageType/eventType: " + value);
    }

    public static MessageType fromDomainEventName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.trim()) {
            case "ai.agent.registered", "agent.registered" -> AGENT_REGISTER;
            case "ai.agent.heartbeat", "agent.heartbeat" -> AGENT_HEARTBEAT;
            case "ai.agent.status.changed", "agent.status.changed" -> AGENT_STATUS_CHANGE;
            case "ai.task.requested", "task.submit" -> TASK_SUBMIT;
            case "ai.task.dispatch", "ai.task.dispatched", "task.dispatch" -> TASK_DISPATCH;
            case "ai.task.ack", "task.ack" -> TASK_ACK;
            case "ai.task.progress", "task.progress" -> TASK_PROGRESS;
            case "ai.task.result", "task.result" -> TASK_RESULT;
            case "ai.task.error", "task.error" -> TASK_ERROR;
            case "gateway.ack" -> GATEWAY_ACK;
            case "gateway.error" -> ERROR;
            case "admin.event" -> ADMIN_EVENT;
            case "cluster.hello" -> CLUSTER_HELLO;
            case "cluster.heartbeat" -> CLUSTER_HEARTBEAT;
            default -> null;
        };
    }

    public static String toDomainEventName(MessageType messageType) {
        if (messageType == null) {
            return null;
        }
        return switch (messageType) {
            case AGENT_REGISTER -> "ai.agent.registered";
            case AGENT_HEARTBEAT -> "ai.agent.heartbeat";
            case AGENT_STATUS_CHANGE -> "ai.agent.status.changed";
            case TASK_SUBMIT -> "ai.task.requested";
            case TASK_DISPATCH -> "ai.task.dispatch";
            case TASK_ACK -> "ai.task.ack";
            case TASK_PROGRESS -> "ai.task.progress";
            case TASK_RESULT -> "ai.task.result";
            case TASK_ERROR -> "ai.task.error";
            case GATEWAY_ACK -> "gateway.ack";
            case ERROR -> "gateway.error";
            case ADMIN_EVENT -> "admin.event";
            case CLUSTER_HELLO -> "cluster.hello";
            case CLUSTER_HEARTBEAT -> "cluster.heartbeat";
        };
    }
}
