package com.opensocket.aievent.gateway.netty.cluster.sync;

import com.opensocket.aievent.gateway.netty.config.ClusterSyncProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cluster state synchronization component for Cluster Remote State Registry. It exposes or
 * consumes lightweight node state snapshots so Admin UI can show a cluster-wide view without
 * moving business ownership across nodes.
 */
@Component
public class ClusterRemoteStateRegistry {

    private final ClusterSyncProperties clusterSyncProperties;
    private final Map<String, RemoteClusterStateSnapshot> remoteStates = new ConcurrentHashMap<>();

    public ClusterRemoteStateRegistry(ClusterSyncProperties clusterSyncProperties) {
        this.clusterSyncProperties = clusterSyncProperties;
    }

    public RemoteClusterStateSnapshot upsertSuccess(ClusterStateSnapshotResponse state) {
        var snapshot = RemoteClusterStateSnapshot.success(state, OffsetDateTime.now());
        remoteStates.put(state.nodeId(), snapshot);
        return snapshot;
    }

    public RemoteClusterStateSnapshot upsertFailure(String nodeId, String message) {
        var now = OffsetDateTime.now();
        var existing = remoteStates.get(nodeId);
        var failed = existing == null
                ? new RemoteClusterStateSnapshot(
                        nodeId,
                        ClusterNodeSyncStatus.FAILED,
                        message,
                        null,
                        null,
                        ClusterNodeRuntimeMetricsResponse.empty(nodeId),
                        List.of(),
                        List.of(),
                        null,
                        null,
                        now
                )
                : existing.withFailure(nodeId, message, now);
        remoteStates.put(nodeId, failed);
        return failed;
    }

    public List<RemoteClusterStateSnapshot> list() {
        markStaleStates();
        return remoteStates.values().stream()
                .sorted(Comparator.comparing(RemoteClusterStateSnapshot::nodeId))
                .toList();
    }

    public Optional<RemoteClusterStateSnapshot> findByNodeId(String nodeId) {
        markStaleStates();
        return Optional.ofNullable(remoteStates.get(nodeId));
    }

    public long count() {
        return remoteStates.size();
    }

    public long countByStatus(ClusterNodeSyncStatus status) {
        markStaleStates();
        return remoteStates.values().stream()
                .filter(state -> state.syncStatus() == status)
                .count();
    }

    private void markStaleStates() {
        var now = OffsetDateTime.now();
        for (var entry : remoteStates.entrySet()) {
            var state = entry.getValue();
            if (state.lastSyncAt() == null || state.syncStatus() != ClusterNodeSyncStatus.SYNCED) {
                continue;
            }
            var age = Duration.between(state.lastSyncAt(), now).toMillis();
            if (age > clusterSyncProperties.safeRemoteStateTtlMs()) {
                remoteStates.put(entry.getKey(), state.markStale(now));
            }
        }
    }
}
