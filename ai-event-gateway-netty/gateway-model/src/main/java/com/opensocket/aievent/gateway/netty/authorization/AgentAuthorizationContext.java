package com.opensocket.aievent.gateway.netty.authorization;

import com.opensocket.aievent.gateway.netty.agent.ConnectionType;
import java.time.OffsetDateTime;
import java.util.List;

public record AgentAuthorizationContext(
        String agentId,
        String tenantId,
        String approvalStatus,
        boolean enabled,
        String riskStatus,
        List<String> capabilities,
        List<String> allowedTaskTypes,
        List<String> allowedSystemCodes,
        Integer credentialVersion,
        Integer policyVersion,
        ConnectionType connectionType,
        String connectionId,
        String sessionId,
        String remoteAddress,
        OffsetDateTime authorizedAt
) {
    public static AgentAuthorizationContext from(
            AgentConnectionAuthorizationResponse response,
            AgentConnectionAuthorizationRequest request
    ) {
        return new AgentAuthorizationContext(
                response.agentId() == null || response.agentId().isBlank() ? request.agentId() : response.agentId(),
                response.tenantId(),
                response.approvalStatus(),
                Boolean.TRUE.equals(response.enabled()),
                response.riskStatus(),
                response.capabilities(),
                response.allowedTaskTypes(),
                response.allowedSystemCodes(),
                response.credentialVersion(),
                response.policyVersion(),
                request.connectionType(),
                request.connectionId(),
                request.sessionId(),
                request.remoteAddress(),
                OffsetDateTime.now()
        );
    }
}
