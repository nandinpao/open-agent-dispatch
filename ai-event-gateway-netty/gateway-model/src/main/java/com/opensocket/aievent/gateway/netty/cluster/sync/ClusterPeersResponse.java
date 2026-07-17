package com.opensocket.aievent.gateway.netty.cluster.sync;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Peer relation response returned by /api/cluster/peers.
 */
public record ClusterPeersResponse(
        String localNodeId,
        OffsetDateTime generatedAt,
        List<ClusterPeerRelationDto> peers
) {
}
