package com.opensocket.aievent.gateway.netty.cluster.sync;

/**
 * Describes whether the local node can synchronize cluster state from a peer.
 */
public enum ClusterPeerSyncStatus {
    NOT_STARTED,
    SYNCED,
    FAILED,
    STALE,
    DISABLED
}
