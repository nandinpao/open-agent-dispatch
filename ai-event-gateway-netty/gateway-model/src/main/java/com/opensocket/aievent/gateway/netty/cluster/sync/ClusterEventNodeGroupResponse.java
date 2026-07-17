package com.opensocket.aievent.gateway.netty.cluster.sync;

import com.opensocket.aievent.gateway.netty.admin.dto.AdminEventPayload;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Cluster-wide Admin event node group for React Admin UI. It preserves local ownership while
 * making recent events visible from a single Admin entry node.
 */
public record ClusterEventNodeGroupResponse(
        String nodeId,
        boolean self,
        ClusterNodeSyncStatus syncStatus,
        String lastSyncError,
        long eventCount,
        Map<String, Long> eventCountsByType,
        List<AdminEventPayload> events,
        OffsetDateTime capturedAt,
        OffsetDateTime lastSyncAt
) {
    public static ClusterEventNodeGroupResponse local(ClusterStateSnapshotResponse state) {
        var events = safeEvents(state.recentEvents());
        return new ClusterEventNodeGroupResponse(
                state.nodeId(),
                true,
                ClusterNodeSyncStatus.SYNCED,
                null,
                events.size(),
                countByType(events),
                events,
                state.capturedAt(),
                state.capturedAt()
        );
    }

    public static ClusterEventNodeGroupResponse remote(RemoteClusterStateSnapshot state) {
        var events = safeEvents(state.recentEvents());
        return new ClusterEventNodeGroupResponse(
                state.nodeId(),
                false,
                state.syncStatus(),
                state.lastSyncError(),
                events.size(),
                countByType(events),
                events,
                state.capturedAt(),
                state.lastSyncAt()
        );
    }

    private static List<AdminEventPayload> safeEvents(List<AdminEventPayload> events) {
        return events == null ? List.of() : List.copyOf(events);
    }

    private static Map<String, Long> countByType(List<AdminEventPayload> events) {
        return events.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        event -> event.eventType() == null ? "UNKNOWN" : event.eventType(),
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.counting()
                ));
    }
}
