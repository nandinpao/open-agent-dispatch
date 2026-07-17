package com.opensocket.aievent.gateway.netty.cluster.sync;

import com.opensocket.aievent.gateway.netty.cluster.ClusterNodeRegistry;
import com.opensocket.aievent.gateway.netty.cluster.ClusterNodeStatus;
import com.opensocket.aievent.gateway.netty.cluster.dto.ClusterHelloPayload;
import com.opensocket.aievent.gateway.netty.config.ClusterSyncProperties;
import com.opensocket.aievent.gateway.netty.config.ClusterRuntimeProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cluster state synchronization component for Cluster State Sync Scheduler. It exposes or
 * consumes lightweight node state snapshots so Admin UI can show a cluster-wide view without
 * moving business ownership across nodes.
 */
@Component
public class ClusterStateSyncScheduler {

    private final ClusterRuntimeProperties clusterRuntimeProperties;
    private final ClusterSyncProperties clusterSyncProperties;
    private final ClusterNodeRegistry clusterNodeRegistry;
    private final ClusterStatePullClient clusterStatePullClient;
    private final ClusterRemoteStateRegistry clusterRemoteStateRegistry;
    private final ClusterPeerRelationRegistry clusterPeerRelationRegistry;
    private final DuplicateRuntimeDetector duplicateRuntimeDetector;

    @Autowired
    public ClusterStateSyncScheduler(
            ClusterRuntimeProperties clusterRuntimeProperties,
            ClusterSyncProperties clusterSyncProperties,
            ClusterNodeRegistry clusterNodeRegistry,
            ClusterStatePullClient clusterStatePullClient,
            ClusterRemoteStateRegistry clusterRemoteStateRegistry,
            ClusterPeerRelationRegistry clusterPeerRelationRegistry,
            DuplicateRuntimeDetector duplicateRuntimeDetector
    ) {
        this.clusterRuntimeProperties = clusterRuntimeProperties;
        this.clusterSyncProperties = clusterSyncProperties;
        this.clusterNodeRegistry = clusterNodeRegistry;
        this.clusterStatePullClient = clusterStatePullClient;
        this.clusterRemoteStateRegistry = clusterRemoteStateRegistry;
        this.clusterPeerRelationRegistry = clusterPeerRelationRegistry;
        this.duplicateRuntimeDetector = duplicateRuntimeDetector;
    }

    /** Backward-compatible constructor retained for focused unit tests. */
    public ClusterStateSyncScheduler(
            ClusterRuntimeProperties clusterRuntimeProperties,
            ClusterSyncProperties clusterSyncProperties,
            ClusterNodeRegistry clusterNodeRegistry,
            ClusterStatePullClient clusterStatePullClient,
            ClusterRemoteStateRegistry clusterRemoteStateRegistry,
            ClusterPeerRelationRegistry clusterPeerRelationRegistry
    ) {
        this(clusterRuntimeProperties, clusterSyncProperties, clusterNodeRegistry, clusterStatePullClient, clusterRemoteStateRegistry, clusterPeerRelationRegistry, null);
    }

    @Scheduled(fixedDelayString = "${cluster.sync.interval-ms:5000}", initialDelayString = "${cluster.sync.interval-ms:5000}")
    public void pullRemoteStates() {
        if (!clusterRuntimeProperties.enabled() || !clusterSyncProperties.enabled()) {
            return;
        }

        clusterPeerRelationRegistry.refreshConfiguredPeers();
        for (var node : clusterNodeRegistry.list()) {
            if (node.self() || node.status() == ClusterNodeStatus.OFFLINE) {
                continue;
            }
            clusterPeerRelationRegistry.registerDiscoveredPeer(node.nodeId());
            var startedAtNanos = System.nanoTime();
            try {
                var state = clusterStatePullClient.pull(node);
                var latencyMs = Math.max(0, (System.nanoTime() - startedAtNanos) / 1_000_000);
                markNodeOnlineFromRemoteState(state, node.remoteAddress());
                clusterRemoteStateRegistry.upsertSuccess(state);
                if (duplicateRuntimeDetector != null) {
                    duplicateRuntimeDetector.detectAndPublishClusterDuplicates();
                }
                clusterPeerRelationRegistry.markSyncSuccess(node.nodeId(), latencyMs);
            } catch (Exception e) {
                clusterRemoteStateRegistry.upsertFailure(node.nodeId(), e.getMessage());
                clusterPeerRelationRegistry.markSyncFailure(node.nodeId(), e.getMessage());
            }
        }
    }
    private void markNodeOnlineFromRemoteState(ClusterStateSnapshotResponse state, String remoteAddress) {
        if (state == null || state.node() == null) {
            return;
        }
        var node = state.node();
        clusterNodeRegistry.applyHello(new ClusterHelloPayload(
                node.nodeId(),
                node.host(),
                node.tcpPort(),
                node.websocketPort(),
                node.adminPort(),
                node.clusterUdpPort(),
                node.startedAt(),
                clusterRuntimeProperties.internalToken(),
                node.siteId(),
                node.siteName(),
                node.region(),
                node.zone()
        ), remoteAddress == null || remoteAddress.isBlank() ? "cluster-state-sync" : remoteAddress);
    }

}
