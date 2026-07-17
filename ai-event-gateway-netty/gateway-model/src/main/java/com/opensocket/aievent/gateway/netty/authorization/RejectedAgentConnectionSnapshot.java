package com.opensocket.aievent.gateway.netty.authorization;

import com.opensocket.aievent.gateway.netty.agent.ConnectionType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record RejectedAgentConnectionSnapshot(
        String rejectedConnectionId,
        String claimedAgentId,
        ConnectionType connectionType,
        String connectionId,
        String sessionId,
        String remoteAddress,
        String reason,
        List<String> claimedCapabilities,
        Map<String, Object> metadata,
        OffsetDateTime rejectedAt
) {
}
