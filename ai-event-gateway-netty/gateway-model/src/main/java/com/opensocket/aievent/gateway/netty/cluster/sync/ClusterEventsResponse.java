package com.opensocket.aievent.gateway.netty.cluster.sync;

import com.opensocket.aievent.gateway.netty.admin.dto.AdminEventPayload;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Cluster-wide Admin event aggregation response. Events remain locally owned by each Gateway
 * node; this response aggregates recent snapshots for Admin UI visibility.
 */
public record ClusterEventsResponse(
        String localNodeId,
        long totalEvents,
        Map<String, Long> eventCountsByNode,
        Map<String, Map<String, Long>> eventTypeCountsByNode,
        Map<String, List<AdminEventPayload>> recentEventsByNode,
        List<ClusterEventNodeGroupResponse> nodes,
        OffsetDateTime generatedAt
) {
}
