package com.opensocket.aievent.gateway.netty.cluster;

import com.opensocket.aievent.gateway.netty.cluster.dto.ClusterHelloPayload;
import com.opensocket.aievent.gateway.netty.cluster.dto.ClusterHeartbeatPayload;
import com.opensocket.aievent.gateway.netty.config.GatewayProperties;
import com.opensocket.aievent.gateway.netty.config.ClusterRuntimeProperties;
import com.opensocket.aievent.gateway.netty.config.NettyServerProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cluster discovery component for Cluster Node Registry. It manages Gateway node visibility,
 * UDP/static-peer discovery, health transitions, and Admin cluster monitoring events.
 */
@Component
public class ClusterNodeRegistry {

    private final GatewayProperties gatewayProperties;
    private final NettyServerProperties nettyServerProperties;
    private final ClusterRuntimeProperties clusterRuntimeProperties;
    private final int adminPort;
    private final Map<String, ClusterNodeRecord> nodes = new ConcurrentHashMap<>();
    private final OffsetDateTime selfStartedAt = OffsetDateTime.now();

    @Autowired
    public ClusterNodeRegistry(
            GatewayProperties gatewayProperties,
            NettyServerProperties nettyServerProperties,
            ClusterRuntimeProperties clusterRuntimeProperties,
            @Value("${server.port:18080}") int adminPort
    ) {
        this.gatewayProperties = gatewayProperties;
        this.nettyServerProperties = nettyServerProperties;
        this.clusterRuntimeProperties = clusterRuntimeProperties;
        this.adminPort = adminPort;
        refreshSelf();
    }

    /** Backward-compatible constructor for unit tests that instantiate the registry directly. */
    public ClusterNodeRegistry(
            GatewayProperties gatewayProperties,
            NettyServerProperties nettyServerProperties,
            int adminPort
    ) {
        this(gatewayProperties, nettyServerProperties, new ClusterRuntimeProperties(nettyServerProperties), adminPort);
    }

    public ClusterHelloPayload selfHelloPayload() {
        return new ClusterHelloPayload(
                gatewayProperties.nodeId(),
                clusterRuntimeProperties.announceHost(),
                clusterRuntimeProperties.tcpPort(),
                clusterRuntimeProperties.websocketPort(),
                adminPort,
                clusterRuntimeProperties.udpPort(),
                selfStartedAt,
                clusterRuntimeProperties.internalToken(),
                gatewayProperties.siteId(),
                gatewayProperties.siteName(),
                gatewayProperties.region(),
                gatewayProperties.zone()
        );
    }

    public ClusterHeartbeatPayload selfHeartbeatPayload() {
        return new ClusterHeartbeatPayload(
                gatewayProperties.nodeId(),
                ClusterNodeStatus.SELF,
                OffsetDateTime.now(),
                clusterRuntimeProperties.internalToken()
        );
    }

    public ClusterNodeSnapshot refreshSelf() {
        var payload = selfHelloPayload();
        var now = OffsetDateTime.now();
        var record = new ClusterNodeRecord(
                payload.nodeId(),
                payload.host(),
                payload.tcpPort(),
                payload.websocketPort(),
                payload.adminPort(),
                payload.clusterUdpPort(),
                ClusterNodeStatus.SELF,
                true,
                payload.siteId(),
                payload.siteName(),
                payload.region(),
                payload.zone(),
                payload.startedAt(),
                now,
                now,
                "SELF",
                "local"
        );
        nodes.put(gatewayProperties.nodeId(), record);
        return record.toSnapshot();
    }


    public Optional<ClusterNodeChange> applyStaticPeer(NettyServerProperties.StaticPeer peer) {
        if (peer == null || isSelf(peer.nodeId())) {
            return Optional.empty();
        }

        var now = OffsetDateTime.now();
        var existing = nodes.get(peer.nodeId());
        var previousStatus = existing == null ? ClusterNodeStatus.DISCOVERED : existing.status;
        var record = new ClusterNodeRecord(
                peer.nodeId(),
                peer.host(),
                peer.tcpPort(),
                peer.websocketPort(),
                peer.adminPort(),
                peer.clusterUdpPort(),
                ClusterNodeStatus.DISCOVERED,
                false,
                siteIdFromNodeId(peer.nodeId()),
                siteNameFromSiteId(siteIdFromNodeId(peer.nodeId())),
                "unknown",
                "unknown-zone",
                null,
                existing == null ? now : existing.firstSeenAt,
                now,
                "STATIC_PEER",
                "static"
        );
        nodes.put(peer.nodeId(), record);
        return Optional.of(new ClusterNodeChange(peer.nodeId(), previousStatus, record.status, record.toSnapshot()));
    }

    public Optional<ClusterNodeChange> applyHello(ClusterHelloPayload payload, String remoteAddress) {
        if (payload == null || isSelf(payload.nodeId())) {
            return Optional.empty();
        }

        var now = OffsetDateTime.now();
        var existing = nodes.get(payload.nodeId());
        var previousStatus = existing == null ? ClusterNodeStatus.DISCOVERED : existing.status;
        var record = new ClusterNodeRecord(
                payload.nodeId(),
                payload.host(),
                payload.tcpPort(),
                payload.websocketPort(),
                payload.adminPort(),
                payload.clusterUdpPort(),
                ClusterNodeStatus.ONLINE,
                false,
                payload.siteId(),
                payload.siteName(),
                payload.region(),
                payload.zone(),
                payload.startedAt(),
                existing == null ? now : existing.firstSeenAt,
                now,
                "CLUSTER_HELLO",
                remoteAddress
        );
        nodes.put(payload.nodeId(), record);
        return Optional.of(new ClusterNodeChange(payload.nodeId(), previousStatus, record.status, record.toSnapshot()));
    }

    public Optional<ClusterNodeChange> applyHeartbeat(ClusterHeartbeatPayload payload, String remoteAddress) {
        if (payload == null || isSelf(payload.nodeId())) {
            return Optional.empty();
        }

        var now = OffsetDateTime.now();
        var existing = nodes.get(payload.nodeId());
        var previousStatus = existing == null ? ClusterNodeStatus.DISCOVERED : existing.status;
        ClusterNodeRecord record;
        if (existing == null) {
            record = new ClusterNodeRecord(
                    payload.nodeId(),
                    "unknown",
                    0,
                    0,
                    0,
                    0,
                    ClusterNodeStatus.ONLINE,
                    false,
                    siteIdFromNodeId(payload.nodeId()),
                    siteNameFromSiteId(siteIdFromNodeId(payload.nodeId())),
                    "unknown",
                    "unknown-zone",
                    null,
                    now,
                    now,
                    "CLUSTER_HEARTBEAT",
                    remoteAddress
            );
        } else {
            record = existing.copyWithStatus(ClusterNodeStatus.ONLINE, now, "CLUSTER_HEARTBEAT", remoteAddress);
        }
        nodes.put(payload.nodeId(), record);
        return Optional.of(new ClusterNodeChange(payload.nodeId(), previousStatus, record.status, record.toSnapshot()));
    }

    public List<ClusterNodeChange> markStaleNodes(long suspectTimeoutMs, long offlineTimeoutMs) {
        var now = OffsetDateTime.now();
        var changes = new ArrayList<ClusterNodeChange>();

        for (ClusterNodeRecord record : nodes.values()) {
            if (record.self) {
                continue;
            }
            if ("STATIC_PEER".equals(record.lastMessageType) && clusterRuntimeProperties.staticPeersEnabled()) {
                continue;
            }
            var ageMs = Duration.between(record.lastSeenAt, now).toMillis();
            var targetStatus = record.status;
            if (ageMs >= offlineTimeoutMs) {
                targetStatus = ClusterNodeStatus.OFFLINE;
            } else if (ageMs >= suspectTimeoutMs) {
                targetStatus = ClusterNodeStatus.SUSPECT;
            }

            if (targetStatus != record.status) {
                var updated = record.copyWithStatus(targetStatus, record.lastSeenAt, "STALE_SCAN", record.remoteAddress);
                nodes.put(record.nodeId, updated);
                changes.add(new ClusterNodeChange(record.nodeId, record.status, updated.status, updated.toSnapshot()));
            }
        }

        return List.copyOf(changes);
    }

    public List<ClusterNodeSnapshot> list() {
        refreshSelf();
        return nodes.values().stream()
                .map(ClusterNodeRecord::toSnapshot)
                .sorted(Comparator.comparing(ClusterNodeSnapshot::self).reversed()
                        .thenComparing(ClusterNodeSnapshot::nodeId))
                .toList();
    }

    public Optional<ClusterNodeSnapshot> findByNodeId(String nodeId) {
        if (isSelf(nodeId)) {
            refreshSelf();
        }
        var record = nodes.get(nodeId);
        return record == null ? Optional.empty() : Optional.of(record.toSnapshot());
    }

    public long count() {
        return nodes.size();
    }

    public long countByStatus(ClusterNodeStatus status) {
        return nodes.values().stream().filter(node -> node.status == status).count();
    }

    public Map<ClusterNodeStatus, Long> countGroupByStatus() {
        var result = new EnumMap<ClusterNodeStatus, Long>(ClusterNodeStatus.class);
        for (ClusterNodeStatus status : ClusterNodeStatus.values()) {
            result.put(status, countByStatus(status));
        }
        return Map.copyOf(result);
    }

    private boolean isSelf(String nodeId) {
        return nodeId != null && nodeId.equals(gatewayProperties.nodeId());
    }

    private static String normalizeSiteId(String siteId) {
        return blank(siteId) ? "UNKNOWN" : siteId.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static String normalizeSiteName(String siteName, String siteId) {
        return blank(siteName) ? siteId : siteName.trim();
    }

    private static String siteIdFromNodeId(String nodeId) {
        if (blank(nodeId)) {
            return "UNKNOWN";
        }
        var lower = nodeId.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("tpe") || lower.contains("taipei")) {
            return "TPE";
        }
        if (lower.contains("tyn") || lower.contains("taoyuan")) {
            return "TYN";
        }
        if (lower.contains("tnn") || lower.contains("tainan")) {
            return "TNN";
        }
        return "UNKNOWN";
    }

    private static String siteNameFromSiteId(String siteId) {
        return switch (normalizeSiteId(siteId)) {
            case "TPE" -> "台北機房";
            case "TYN" -> "桃園機房";
            case "TNN" -> "台南機房";
            case "LOCAL" -> "Local Site";
            default -> "Unknown Site";
        };
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static final class ClusterNodeRecord {
        private final String nodeId;
        private final String host;
        private final int tcpPort;
        private final int websocketPort;
        private final int adminPort;
        private final int clusterUdpPort;
        private final ClusterNodeStatus status;
        private final boolean self;
        private final String siteId;
        private final String siteName;
        private final String region;
        private final String zone;
        private final OffsetDateTime startedAt;
        private final OffsetDateTime firstSeenAt;
        private final OffsetDateTime lastSeenAt;
        private final String lastMessageType;
        private final String remoteAddress;

        private ClusterNodeRecord(
                String nodeId,
                String host,
                int tcpPort,
                int websocketPort,
                int adminPort,
                int clusterUdpPort,
                ClusterNodeStatus status,
                boolean self,
                String siteId,
                String siteName,
                String region,
                String zone,
                OffsetDateTime startedAt,
                OffsetDateTime firstSeenAt,
                OffsetDateTime lastSeenAt,
                String lastMessageType,
                String remoteAddress
        ) {
            this.nodeId = nodeId;
            this.host = host;
            this.tcpPort = tcpPort;
            this.websocketPort = websocketPort;
            this.adminPort = adminPort;
            this.clusterUdpPort = clusterUdpPort;
            this.status = status;
            this.self = self;
            this.siteId = normalizeSiteId(siteId);
            this.siteName = normalizeSiteName(siteName, this.siteId);
            this.region = blank(region) ? "unknown" : region.trim();
            this.zone = blank(zone) ? "unknown-zone" : zone.trim();
            this.startedAt = startedAt;
            this.firstSeenAt = firstSeenAt;
            this.lastSeenAt = lastSeenAt;
            this.lastMessageType = lastMessageType;
            this.remoteAddress = remoteAddress;
        }

        private ClusterNodeRecord copyWithStatus(
                ClusterNodeStatus status,
                OffsetDateTime lastSeenAt,
                String lastMessageType,
                String remoteAddress
        ) {
            return new ClusterNodeRecord(
                    nodeId,
                    host,
                    tcpPort,
                    websocketPort,
                    adminPort,
                    clusterUdpPort,
                    status,
                    self,
                    siteId,
                    siteName,
                    region,
                    zone,
                    startedAt,
                    firstSeenAt,
                    lastSeenAt,
                    lastMessageType,
                    remoteAddress
            );
        }

        private ClusterNodeSnapshot toSnapshot() {
            return new ClusterNodeSnapshot(
                    nodeId,
                    host,
                    tcpPort,
                    websocketPort,
                    adminPort,
                    clusterUdpPort,
                    status,
                    self,
                    siteId,
                    siteName,
                    region,
                    zone,
                    self ? "CURRENT_API_NODE" : "PEER_NODE",
                    startedAt,
                    firstSeenAt,
                    lastSeenAt,
                    lastMessageType,
                    remoteAddress
            );
        }
    }
}
