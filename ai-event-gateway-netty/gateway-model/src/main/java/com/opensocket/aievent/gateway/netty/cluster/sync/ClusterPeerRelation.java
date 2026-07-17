package com.opensocket.aievent.gateway.netty.cluster.sync;

/**
 * Describes how the local node knows about a peer.
 */
public enum ClusterPeerRelation {
    SELF,
    STATIC_SEED,
    DISCOVERED,
    MANUAL,
    UNKNOWN
}
