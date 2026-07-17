package com.opensocket.aievent.gateway.netty.cluster.sync;

/**
 * Describes whether the local node can still reach a peer.
 */
public enum ClusterPeerHeartbeatStatus {
    OK,
    WARNING,
    LOST,
    UNKNOWN,
    DISABLED
}
