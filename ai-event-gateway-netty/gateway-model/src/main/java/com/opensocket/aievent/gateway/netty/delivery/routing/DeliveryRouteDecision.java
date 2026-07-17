package com.opensocket.aievent.gateway.netty.delivery.routing;

import java.time.OffsetDateTime;
import java.util.List;

/** Runtime decision returned by the cluster delivery router for diagnostics and Admin UI use. */
public record DeliveryRouteDecision(
        String agentId,
        String routedByGatewayNodeId,
        String targetGatewayNodeId,
        String targetAdminEndpoint,
        String routeType,
        String reason,
        List<String> candidateGatewayNodeIds,
        OffsetDateTime generatedAt
) {
}
