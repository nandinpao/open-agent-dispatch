package com.opensocket.aievent.gateway.netty.cluster;

import com.opensocket.aievent.gateway.netty.cluster.dto.ClusterHelloPayload;
import com.opensocket.aievent.gateway.netty.cluster.dto.ClusterHeartbeatPayload;
import com.opensocket.aievent.gateway.netty.config.GatewayProperties;
import com.opensocket.aievent.gateway.netty.config.ClusterRuntimeProperties;
import com.opensocket.aievent.gateway.netty.protocol.AiEventEnvelope;
import com.opensocket.aievent.gateway.netty.protocol.MessageType;
import com.opensocket.aievent.gateway.netty.websocket.WebSocketAdminBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

/**
 * Cluster discovery component for Cluster Discovery Service. It manages Gateway node visibility,
 * UDP/static-peer discovery, health transitions, and Admin cluster monitoring events.
 */
@Service
public class ClusterDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(ClusterDiscoveryService.class);

    private final GatewayProperties gatewayProperties;
    private final ClusterRuntimeProperties clusterRuntimeProperties;
    private final ClusterNodeRegistry clusterNodeRegistry;
    private final WebSocketAdminBroadcaster adminBroadcaster;

    public ClusterDiscoveryService(
            GatewayProperties gatewayProperties,
            ClusterRuntimeProperties clusterRuntimeProperties,
            ClusterNodeRegistry clusterNodeRegistry,
            WebSocketAdminBroadcaster adminBroadcaster
    ) {
        this.gatewayProperties = gatewayProperties;
        this.clusterRuntimeProperties = clusterRuntimeProperties;
        this.clusterNodeRegistry = clusterNodeRegistry;
        this.adminBroadcaster = adminBroadcaster;
    }

    public AiEventEnvelope<ClusterHelloPayload> buildHelloEnvelope() {
        return AiEventEnvelope.of(
                MessageType.CLUSTER_HELLO,
                gatewayProperties.nodeId(),
                "cluster-broadcast",
                clusterNodeRegistry.selfHelloPayload()
        );
    }

    public AiEventEnvelope<ClusterHeartbeatPayload> buildHeartbeatEnvelope() {
        return AiEventEnvelope.of(
                MessageType.CLUSTER_HEARTBEAT,
                gatewayProperties.nodeId(),
                "cluster-broadcast",
                clusterNodeRegistry.selfHeartbeatPayload()
        );
    }

    public boolean isTrustedToken(String receivedToken, String remoteAddress) {
        var expectedToken = clusterRuntimeProperties.internalToken();
        if (expectedToken.isBlank()) {
            log.warn("UDP cluster discovery is running without CLUSTER_INTERNAL_TOKEN. remoteAddress={}", remoteAddress);
            return true;
        }
        if (receivedToken == null || receivedToken.isBlank()) {
            log.warn("Rejected UDP cluster discovery message without internal token. remoteAddress={}", remoteAddress);
            return false;
        }
        var trusted = MessageDigest.isEqual(
                expectedToken.getBytes(StandardCharsets.UTF_8),
                receivedToken.trim().getBytes(StandardCharsets.UTF_8)
        );
        if (!trusted) {
            log.warn("Rejected UDP cluster discovery message with invalid internal token. remoteAddress={}", remoteAddress);
        }
        return trusted;
    }

    public void handleHello(ClusterHelloPayload payload, String remoteAddress) {
        clusterNodeRegistry.applyHello(payload, remoteAddress).ifPresent(change -> {
            log.info("Cluster node hello received. nodeId={}, status={}", change.nodeId(), change.currentStatus());
            broadcastChange("CLUSTER_NODE_ONLINE", "Cluster node is online", change);
        });
    }

    public void handleHeartbeat(ClusterHeartbeatPayload payload, String remoteAddress) {
        clusterNodeRegistry.applyHeartbeat(payload, remoteAddress).ifPresent(change -> {
            if (change.statusChanged()) {
                log.info("Cluster node heartbeat changed status. nodeId={}, {} -> {}",
                        change.nodeId(), change.previousStatus(), change.currentStatus());
                broadcastChange("CLUSTER_NODE_ONLINE", "Cluster node heartbeat restored online status", change);
            }
        });
    }

    public List<ClusterNodeChange> scanStaleNodes() {
        var changes = clusterNodeRegistry.markStaleNodes(
                clusterRuntimeProperties.suspectTimeoutMs(),
                clusterRuntimeProperties.offlineTimeoutMs()
        );
        for (ClusterNodeChange change : changes) {
            var eventType = change.currentStatus() == ClusterNodeStatus.OFFLINE
                    ? "CLUSTER_NODE_OFFLINE"
                    : "CLUSTER_NODE_SUSPECT";
            var message = change.currentStatus() == ClusterNodeStatus.OFFLINE
                    ? "Cluster node heartbeat timed out"
                    : "Cluster node heartbeat is delayed";
            broadcastChange(eventType, message, change);
        }
        return changes;
    }

    private void broadcastChange(String eventType, String message, ClusterNodeChange change) {
        adminBroadcaster.broadcast(eventType, message, Map.of(
                "nodeId", change.nodeId(),
                "previousStatus", change.previousStatus().name(),
                "currentStatus", change.currentStatus().name(),
                "host", change.node().host(),
                "adminPort", change.node().adminPort(),
                "tcpPort", change.node().tcpPort(),
                "websocketPort", change.node().websocketPort(),
                "clusterUdpPort", change.node().clusterUdpPort(),
                "lastSeenAt", String.valueOf(change.node().lastSeenAt())
        ));
    }
}
