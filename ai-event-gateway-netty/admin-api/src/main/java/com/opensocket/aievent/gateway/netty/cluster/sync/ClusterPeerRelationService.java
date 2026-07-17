package com.opensocket.aievent.gateway.netty.cluster.sync;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Read model service for Admin UI peer relation and heartbeat APIs.
 */
@Service
public class ClusterPeerRelationService {

    private final ClusterPeerRelationRegistry clusterPeerRelationRegistry;

    public ClusterPeerRelationService(ClusterPeerRelationRegistry clusterPeerRelationRegistry) {
        this.clusterPeerRelationRegistry = clusterPeerRelationRegistry;
    }

    public ClusterPeersResponse peers() {
        return clusterPeerRelationRegistry.response();
    }

    public List<ClusterPeerRelationDto> peerRelations() {
        return clusterPeerRelationRegistry.snapshot();
    }
}
