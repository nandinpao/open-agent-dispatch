package com.opensocket.aievent.gateway.netty.cluster.sync;

import java.time.OffsetDateTime;
import java.util.List;

/** Site-level transport cluster summary for multi-site Admin UI dashboards. */
public record ClusterSiteOverviewResponse(
        String siteId,
        String siteName,
        String region,
        String zone,
        String status,
        long gatewayTotal,
        long gatewayUp,
        long gatewaySuspect,
        long gatewayOffline,
        long agentTotal,
        long agentIdle,
        long agentBusy,
        long recentEventCount,
        List<String> nodeIds,
        OffsetDateTime generatedAt
) {
}
