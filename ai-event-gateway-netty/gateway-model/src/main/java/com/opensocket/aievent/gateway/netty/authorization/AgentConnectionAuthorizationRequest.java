package com.opensocket.aievent.gateway.netty.authorization;

import com.opensocket.aievent.gateway.netty.agent.AgentType;
import com.opensocket.aievent.gateway.netty.agent.ConnectionType;
import java.util.List;
import java.util.Map;

public record AgentConnectionAuthorizationRequest(
        String agentId,
        AgentType agentType,
        ConnectionType connectionType,
        String gatewayNodeId,
        String connectionId,
        String sessionId,
        String remoteAddress,
        List<String> claimedCapabilities,
        Map<String, Object> metadata,
        String credentialToken,
        String publicKeyFingerprint
) {
    public AgentConnectionAuthorizationRequest {
        if (claimedCapabilities == null) {
            claimedCapabilities = List.of();
        }
        if (metadata == null) {
            metadata = Map.of();
        }
    }
}
