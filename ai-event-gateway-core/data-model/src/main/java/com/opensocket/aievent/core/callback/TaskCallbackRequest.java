package com.opensocket.aievent.core.callback;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class TaskCallbackRequest {
    private String callbackId;
    private String dispatchRequestId;
    private String assignmentId;
    private String taskId;
    private String agentId;
    private String ownerGatewayNodeId;
    private String agentSessionId;
    private Integer attemptNo;
    private String dispatchToken;
    private String fencingToken;
    private Integer progressPercent;
    private String message;
    private String resultStatus;
    private String errorCode;
    private String errorMessage;
    private Map<String, Object> payload = new LinkedHashMap<>();
    private OffsetDateTime occurredAt;
    public void setPayload(Map<String, Object> payload) { this.payload = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload); }
}
