package com.opensocket.aievent.gateway.netty.admin.dto;

import com.opensocket.aievent.gateway.netty.agent.dto.AgentResponse;
import com.opensocket.aievent.gateway.netty.agent.dto.AgentSummaryResponse;
import com.opensocket.aievent.gateway.netty.cluster.sync.ClusterOverviewResponse;

import java.time.OffsetDateTime;
import java.util.List;

/** Dashboard response for ai-event-gateway-netty transport runtime observability. */
public record AdminDashboardResponse(
        GatewayStatusResponse gateway,
        AgentSummaryResponse agentSummary,
        List<AgentResponse> agents,
        ClusterOverviewResponse clusterOverview,
        List<AdminEventPayload> recentEvents,
        OffsetDateTime serverTime
) {
}
