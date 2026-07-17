package com.opensocket.aievent.gateway.netty.agent.dto;

import java.util.Map;

/**
 * Immutable DTO for agent-related protocol payloads. These records represent data received from
 * or returned to Agent clients over TCP, WebSocket, or REST APIs.
 */
public record AgentSummaryResponse(
        long total,
        long online,
        long idle,
        long busy,
        long offline,
        long timeout,
        long error,
        Map<String, Long> byStatus
) {
}
