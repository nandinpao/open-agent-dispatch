package com.opensocket.aievent.gateway.netty.agent.dto;

import com.opensocket.aievent.gateway.netty.agent.AgentStatus;
import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;
import java.util.Map;

public record AgentHeartbeatPayload(
        @NotBlank String agentId,
        AgentStatus status,
        String currentTaskId,
        OffsetDateTime heartbeatAt,
        String heartbeatId,
        Long sequence,
        String connectionId,
        Map<String, Object> metrics,
        Map<String, Object> runtimeLoad,
        String capabilityRevision,
        Map<String, Object> plugin,
        Map<String, Object> capabilityProfile,
        Map<String, Object> cluster
) {
    public AgentHeartbeatPayload(String agentId, AgentStatus status, String currentTaskId) {
        this(agentId, status, currentTaskId, null, null, null, null, Map.of(), Map.of(), null, Map.of(), Map.of(), Map.of());
    }

    public AgentHeartbeatPayload(String agentId, AgentStatus status, String currentTaskId, OffsetDateTime heartbeatAt) {
        this(agentId, status, currentTaskId, heartbeatAt, null, null, null, Map.of(), Map.of(), null, Map.of(), Map.of(), Map.of());
    }

    public AgentHeartbeatPayload {
        if (metrics == null) {
            metrics = Map.of();
        }
        if (runtimeLoad == null) {
            runtimeLoad = Map.of();
        }
        if (plugin == null) {
            plugin = Map.of();
        }
        if (capabilityProfile == null) {
            capabilityProfile = Map.of();
        }
        if (cluster == null) {
            cluster = Map.of();
        }
    }
}
