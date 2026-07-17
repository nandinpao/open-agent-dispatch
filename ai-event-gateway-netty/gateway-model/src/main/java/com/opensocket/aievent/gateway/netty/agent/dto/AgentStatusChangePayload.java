package com.opensocket.aievent.gateway.netty.agent.dto;

import com.opensocket.aievent.gateway.netty.agent.AgentStatus;
import jakarta.validation.constraints.NotBlank;

public record AgentStatusChangePayload(
        @NotBlank String agentId,
        AgentStatus status,
        String reason,
        String currentTaskId
) {
    public AgentStatus toStatus() {
        return status == null ? AgentStatus.IDLE : status;
    }
}
