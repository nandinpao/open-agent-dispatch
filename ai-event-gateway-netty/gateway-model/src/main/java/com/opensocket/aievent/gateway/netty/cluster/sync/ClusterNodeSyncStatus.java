package com.opensocket.aievent.gateway.netty.cluster.sync;

/**
 * Cluster state synchronization component for Cluster Node Sync Status. It exposes or consumes
 * lightweight node state snapshots so Admin UI can show a cluster-wide view without moving task
 * ownership across nodes.
 */
public enum ClusterNodeSyncStatus {
    NEVER_SYNCED,
    SYNCED,
    FAILED,
    SKIPPED,
    STALE
}
