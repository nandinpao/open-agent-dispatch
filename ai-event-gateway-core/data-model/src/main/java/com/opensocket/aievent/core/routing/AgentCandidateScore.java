package com.opensocket.aievent.core.routing;

import java.util.List;
import java.util.Map;

public record AgentCandidateScore(
        String agentId,
        String ownerGatewayNodeId,
        String agentSessionId,
        String siteId,
        String status,
        int score,
        List<String> matchedCapabilities,
        List<String> missingCapabilities,
        String reason,
        Map<String, Object> scoreBreakdown
) {}
