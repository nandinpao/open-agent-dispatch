package com.opensocket.aievent.gateway.netty.cluster.sync;

import com.opensocket.aievent.gateway.netty.config.ClusterRuntimeProperties;
import com.opensocket.aievent.gateway.netty.config.ClusterSyncProperties;
import com.opensocket.aievent.gateway.netty.config.GatewayProperties;
import com.opensocket.aievent.gateway.netty.config.NettyServerProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks peer relation, sync and heartbeat state from the perspective of the local node.
 *
 * <p>Cluster nodes answer "which nodes exist". Peer relations answer "can this local node reach
 * and synchronize each peer". Admin UI should use this model for relation/heartbeat panels instead
 * of deriving health from SELF/ONLINE alone.</p>
 */
@Component
public class ClusterPeerRelationRegistry {

    private static final int LOST_MISSED_HEARTBEAT_THRESHOLD = 3;

    private final GatewayProperties gatewayProperties;
    private final ClusterRuntimeProperties clusterRuntimeProperties;
    private final ClusterSyncProperties clusterSyncProperties;
    private final Map<String, ClusterPeerRelationRecord> peers = new ConcurrentHashMap<>();

    public ClusterPeerRelationRegistry(
            GatewayProperties gatewayProperties,
            ClusterRuntimeProperties clusterRuntimeProperties,
            ClusterSyncProperties clusterSyncProperties
    ) {
        this.gatewayProperties = gatewayProperties;
        this.clusterRuntimeProperties = clusterRuntimeProperties;
        this.clusterSyncProperties = clusterSyncProperties;
    }

    public void refreshConfiguredPeers() {
        if (!clusterRuntimeProperties.enabled()) {
            return;
        }
        if (clusterRuntimeProperties.staticPeersEnabled()) {
            for (var peer : clusterRuntimeProperties.parsedStaticPeers()) {
                registerStaticPeer(peer);
            }
        }
    }

    public void registerStaticPeer(NettyServerProperties.StaticPeer peer) {
        if (peer == null || isSelf(peer.nodeId())) {
            return;
        }
        peers.compute(peer.nodeId(), (nodeId, existing) -> {
            var record = existing == null ? ClusterPeerRelationRecord.initial(nodeId) : existing;
            return record.withRelation(ClusterPeerRelation.STATIC_SEED);
        });
    }

    public void registerDiscoveredPeer(String nodeId) {
        if (nodeId == null || nodeId.isBlank() || isSelf(nodeId)) {
            return;
        }
        peers.compute(nodeId, (id, existing) -> {
            if (existing == null) {
                return ClusterPeerRelationRecord.initial(id).withRelation(ClusterPeerRelation.DISCOVERED);
            }
            if (existing.relation() == ClusterPeerRelation.UNKNOWN) {
                return existing.withRelation(ClusterPeerRelation.DISCOVERED);
            }
            return existing;
        });
    }

    public void markSyncSuccess(String nodeId, long heartbeatLatencyMs) {
        if (nodeId == null || nodeId.isBlank() || isSelf(nodeId)) {
            return;
        }
        var now = OffsetDateTime.now();
        peers.compute(nodeId, (id, existing) -> ensureRecord(id, existing).markSyncSuccess(now, heartbeatLatencyMs));
    }

    public void markSyncFailure(String nodeId, String errorMessage) {
        if (nodeId == null || nodeId.isBlank() || isSelf(nodeId)) {
            return;
        }
        var now = OffsetDateTime.now();
        peers.compute(nodeId, (id, existing) -> ensureRecord(id, existing).markSyncFailure(now, normalizeError(errorMessage)));
    }

    public List<ClusterPeerRelationDto> snapshot() {
        refreshConfiguredPeers();
        markStaleStates();
        return peers.values().stream()
                .sorted(Comparator.comparing(ClusterPeerRelationRecord::nodeId))
                .map(this::toDto)
                .toList();
    }

    public ClusterPeersResponse response() {
        return new ClusterPeersResponse(
                gatewayProperties.nodeId(),
                OffsetDateTime.now(),
                snapshot()
        );
    }

    private ClusterPeerRelationRecord ensureRecord(String nodeId, ClusterPeerRelationRecord existing) {
        return existing == null ? ClusterPeerRelationRecord.initial(nodeId) : existing;
    }

    private ClusterPeerRelationDto toDto(ClusterPeerRelationRecord record) {
        var syncStatus = record.syncStatus();
        var heartbeatStatus = record.heartbeatStatus();
        if (!clusterRuntimeProperties.enabled()) {
            syncStatus = ClusterPeerSyncStatus.DISABLED;
            heartbeatStatus = ClusterPeerHeartbeatStatus.DISABLED;
        } else if (!clusterSyncProperties.enabled()) {
            syncStatus = ClusterPeerSyncStatus.DISABLED;
            heartbeatStatus = ClusterPeerHeartbeatStatus.DISABLED;
        }

        return new ClusterPeerRelationDto(
                record.nodeId(),
                record.relation().name(),
                syncStatus.name(),
                heartbeatStatus.name(),
                record.lastSyncAt(),
                record.lastHeartbeatAt(),
                record.heartbeatLatencyMs(),
                record.missedHeartbeatCount(),
                record.lastError()
        );
    }

    private void markStaleStates() {
        if (!clusterRuntimeProperties.enabled() || !clusterSyncProperties.enabled()) {
            return;
        }
        var now = OffsetDateTime.now();
        var staleAfterMs = clusterSyncProperties.safeRemoteStateTtlMs();
        for (var entry : peers.entrySet()) {
            var record = entry.getValue();
            if (record.lastHeartbeatAt() == null || record.syncStatus() != ClusterPeerSyncStatus.SYNCED) {
                continue;
            }
            var ageMs = Duration.between(record.lastHeartbeatAt(), now).toMillis();
            if (ageMs > staleAfterMs) {
                peers.put(entry.getKey(), record.markStale(now));
            }
        }
    }

    private boolean isSelf(String nodeId) {
        return nodeId != null && nodeId.equals(gatewayProperties.nodeId());
    }

    private String normalizeError(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return "Unknown cluster peer sync failure";
        }
        return errorMessage.length() > 500 ? errorMessage.substring(0, 500) : errorMessage;
    }

    private record ClusterPeerRelationRecord(
            String nodeId,
            ClusterPeerRelation relation,
            ClusterPeerSyncStatus syncStatus,
            ClusterPeerHeartbeatStatus heartbeatStatus,
            OffsetDateTime lastSyncAt,
            OffsetDateTime lastHeartbeatAt,
            Long heartbeatLatencyMs,
            int missedHeartbeatCount,
            String lastError,
            OffsetDateTime lastFailureAt
    ) {
        static ClusterPeerRelationRecord initial(String nodeId) {
            return new ClusterPeerRelationRecord(
                    nodeId,
                    ClusterPeerRelation.UNKNOWN,
                    ClusterPeerSyncStatus.NOT_STARTED,
                    ClusterPeerHeartbeatStatus.UNKNOWN,
                    null,
                    null,
                    null,
                    0,
                    null,
                    null
            );
        }

        ClusterPeerRelationRecord withRelation(ClusterPeerRelation relation) {
            return new ClusterPeerRelationRecord(
                    nodeId,
                    relation == null ? ClusterPeerRelation.UNKNOWN : relation,
                    syncStatus,
                    heartbeatStatus,
                    lastSyncAt,
                    lastHeartbeatAt,
                    heartbeatLatencyMs,
                    missedHeartbeatCount,
                    lastError,
                    lastFailureAt
            );
        }

        ClusterPeerRelationRecord markSyncSuccess(OffsetDateTime now, long latencyMs) {
            return new ClusterPeerRelationRecord(
                    nodeId,
                    relation,
                    ClusterPeerSyncStatus.SYNCED,
                    ClusterPeerHeartbeatStatus.OK,
                    now,
                    now,
                    Math.max(0, latencyMs),
                    0,
                    null,
                    null
            );
        }

        ClusterPeerRelationRecord markSyncFailure(OffsetDateTime now, String error) {
            var missed = missedHeartbeatCount + 1;
            var heartbeat = missed >= LOST_MISSED_HEARTBEAT_THRESHOLD
                    ? ClusterPeerHeartbeatStatus.LOST
                    : ClusterPeerHeartbeatStatus.WARNING;
            return new ClusterPeerRelationRecord(
                    nodeId,
                    relation,
                    ClusterPeerSyncStatus.FAILED,
                    heartbeat,
                    lastSyncAt,
                    lastHeartbeatAt,
                    heartbeatLatencyMs,
                    missed,
                    error,
                    now
            );
        }

        ClusterPeerRelationRecord markStale(OffsetDateTime now) {
            var missed = Math.max(1, missedHeartbeatCount);
            var heartbeat = missed >= LOST_MISSED_HEARTBEAT_THRESHOLD
                    ? ClusterPeerHeartbeatStatus.LOST
                    : ClusterPeerHeartbeatStatus.WARNING;
            return new ClusterPeerRelationRecord(
                    nodeId,
                    relation,
                    ClusterPeerSyncStatus.STALE,
                    heartbeat,
                    lastSyncAt,
                    lastHeartbeatAt,
                    heartbeatLatencyMs,
                    missed,
                    "Remote cluster state is stale",
                    now
            );
        }
    }
}
