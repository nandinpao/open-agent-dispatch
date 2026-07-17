package com.opensocket.aievent.core.dispatch;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GatewayDispatchResponse {
    private String taskId;
    private String assignmentId;
    private String targetAgentId;
    private String ownerGatewayNodeId;
    private boolean accepted;
    private String status;
    private String message;

    public GatewayDispatchResponse() {
    }

    public GatewayDispatchResponse(String taskId, String assignmentId, String targetAgentId, String ownerGatewayNodeId, boolean accepted, String status, String message) {
        this.taskId = taskId;
        this.assignmentId = assignmentId;
        this.targetAgentId = targetAgentId;
        this.ownerGatewayNodeId = ownerGatewayNodeId;
        this.accepted = accepted;
        this.status = status;
        this.message = message;
    }
}
