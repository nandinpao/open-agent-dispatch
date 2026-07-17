package com.opensocket.aievent.gateway.netty.cluster.sync;

import java.time.OffsetDateTime;

/**
 * Peer relation and heartbeat view for Admin UI.
 *
 * <p>This DTO intentionally separates peer health from cluster node status. A node may be present
 * in /api/cluster/nodes, while this record explains whether the local node can synchronize and
 * heartbeat against that peer.</p>
 */
public record ClusterPeerRelationDto(
        String nodeId,
        String relation,
        String syncStatus,
        String heartbeatStatus,
        OffsetDateTime lastSyncAt,
        OffsetDateTime lastHeartbeatAt,
        Long heartbeatLatencyMs,
        int missedHeartbeatCount,
        String lastError
) {
}
