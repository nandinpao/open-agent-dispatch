package com.opensocket.aievent.gateway.netty.authorization;

import java.util.List;

public record AgentConnectionAuthorizationResponse(
        AgentAuthorizationDecision decision,
        String reason,
        String agentId,
        String tenantId,
        String approvalStatus,
        Boolean enabled,
        String riskStatus,
        List<String> capabilities,
        List<String> allowedTaskTypes,
        List<String> allowedSystemCodes,
        Integer credentialVersion,
        Integer policyVersion
) {
    public AgentConnectionAuthorizationResponse {
        if (decision == null) {
            decision = AgentAuthorizationDecision.DENY;
        }
        if (capabilities == null) {
            capabilities = List.of();
        }
        if (allowedTaskTypes == null) {
            allowedTaskTypes = List.of();
        }
        if (allowedSystemCodes == null) {
            allowedSystemCodes = List.of();
        }
    }

    public boolean allowed() {
        return decision == AgentAuthorizationDecision.ALLOW;
    }

    public static AgentConnectionAuthorizationResponse allow(String agentId) {
        return new AgentConnectionAuthorizationResponse(
                AgentAuthorizationDecision.ALLOW,
                "ALLOW",
                agentId,
                null,
                "APPROVED",
                true,
                "NORMAL",
                List.of(),
                List.of(),
                List.of(),
                null,
                null
        );
    }

    public static AgentConnectionAuthorizationResponse deny(String agentId, String reason) {
        return new AgentConnectionAuthorizationResponse(
                AgentAuthorizationDecision.DENY,
                reason == null || reason.isBlank() ? "DENY" : reason,
                agentId,
                null,
                null,
                false,
                null,
                List.of(),
                List.of(),
                List.of(),
                null,
                null
        );
    }
}
