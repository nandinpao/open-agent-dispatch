package com.opensocket.aievent.gateway.netty.cluster;

import com.opensocket.aievent.gateway.netty.cluster.dto.ClusterNodeResponse;
import com.opensocket.aievent.gateway.netty.cluster.sync.ClusterAgentsResponse;
import com.opensocket.aievent.gateway.netty.cluster.sync.ClusterEventsResponse;
import com.opensocket.aievent.gateway.netty.cluster.sync.ClusterOverviewResponse;
import com.opensocket.aievent.gateway.netty.cluster.sync.ClusterOverviewService;
import com.opensocket.aievent.gateway.netty.cluster.sync.ClusterPeerRelationService;
import com.opensocket.aievent.gateway.netty.cluster.sync.ClusterPeersResponse;
import com.opensocket.aievent.gateway.netty.cluster.dto.ClusterSummaryResponse;
import com.opensocket.aievent.gateway.netty.config.ClusterRuntimeProperties;
import com.opensocket.aievent.gateway.netty.api.GatewayApiErrorCode;
import com.opensocket.aievent.gateway.netty.api.GatewayApiException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Cluster REST API for Netty transport topology and runtime observability. */
@RestController
@RequestMapping("/api/cluster")
public class ClusterController {

    private final ClusterRuntimeProperties clusterRuntimeProperties;
    private final ClusterNodeRegistry clusterNodeRegistry;
    private final ClusterOverviewService clusterOverviewService;
    private final ClusterPeerRelationService clusterPeerRelationService;

    public ClusterController(
            ClusterRuntimeProperties clusterRuntimeProperties,
            ClusterNodeRegistry clusterNodeRegistry,
            ClusterOverviewService clusterOverviewService,
            ClusterPeerRelationService clusterPeerRelationService
    ) {
        this.clusterRuntimeProperties = clusterRuntimeProperties;
        this.clusterNodeRegistry = clusterNodeRegistry;
        this.clusterOverviewService = clusterOverviewService;
        this.clusterPeerRelationService = clusterPeerRelationService;
    }

    @GetMapping("/overview")
    public ClusterOverviewResponse overview() {
        return clusterOverviewService.overview();
    }

    @GetMapping("/sites")
    public List<com.opensocket.aievent.gateway.netty.cluster.sync.ClusterSiteOverviewResponse> sites() {
        return clusterOverviewService.siteOverviews();
    }

    @GetMapping("/nodes")
    public List<ClusterNodeResponse> nodes() {
        return clusterOverviewService.gatewayNodes();
    }

    @GetMapping("/gateway-registry")
    public GatewayRegistryResponse gatewayRegistry() {
        List<GatewayRegistryNode> nodes = clusterOverviewService.gatewayNodes().stream()
                .map(node -> new GatewayRegistryNode(
                        node.nodeId(),
                        node.status() == null ? null : node.status().name(),
                        node.self(),
                        node.host(),
                        node.adminPort(),
                        adminBaseUrl(node),
                        node.siteId(),
                        node.region(),
                        node.zone(),
                        node.lastSeenAt()))
                .toList();
        Map<String, String> baseUrls = nodes.stream()
                .filter(node -> node.adminBaseUrl() != null && !node.adminBaseUrl().isBlank())
                .collect(Collectors.toMap(GatewayRegistryNode::nodeId, GatewayRegistryNode::adminBaseUrl, (left, right) -> left, java.util.LinkedHashMap::new));
        return new GatewayRegistryResponse(clusterOverviewService.overview().localState().nodeId(), nodes, baseUrls, OffsetDateTime.now());
    }

    @GetMapping("/peers")
    public ClusterPeersResponse peers() {
        return clusterPeerRelationService.peers();
    }

    @GetMapping("/agents")
    public ClusterAgentsResponse agents() {
        return clusterOverviewService.agents();
    }

    @GetMapping("/agents/by-node")
    public ClusterAgentsResponse agentsByNode() {
        return clusterOverviewService.agents();
    }

    @GetMapping("/events")
    public ClusterEventsResponse events() {
        return clusterOverviewService.events();
    }

    @GetMapping("/events/by-node")
    public ClusterEventsResponse eventsByNode() {
        return clusterOverviewService.events();
    }

    @GetMapping("/runtime-config")
    public Map<String, Object> runtimeConfig() {
        return Map.of(
                "clusterEnabled", clusterRuntimeProperties.enabled(),
                "currentApiNode", clusterOverviewService.overview().localState().node(),
                "discoveryMode", clusterRuntimeProperties.discoveryMode(),
                "announceHost", clusterRuntimeProperties.announceHost(),
                "udpHost", clusterRuntimeProperties.udpHost(),
                "udpPort", clusterRuntimeProperties.udpPort(),
                "staticPeersRaw", clusterRuntimeProperties.staticPeersRaw(),
                "staticPeerCount", clusterRuntimeProperties.parsedStaticPeers().size(),
                "syncTargetNodeCount", Math.max(0, clusterNodeRegistry.count() - 1)
        );
    }

    @GetMapping("/nodes/{nodeId}")
    public ClusterNodeResponse node(@PathVariable String nodeId) {
        return clusterOverviewService.gatewayNodes().stream()
                .filter(node -> java.util.Objects.equals(node.nodeId(), nodeId))
                .findFirst()
                .orElseThrow(() -> new GatewayApiException(GatewayApiErrorCode.GATEWAY_CLUSTER_NODE_NOT_FOUND, "Cluster node not found: " + nodeId));
    }

    @GetMapping("/summary")
    public ClusterSummaryResponse summary() {
        Map<String, Long> byStatus = clusterNodeRegistry.countGroupByStatus().entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().name(), Map.Entry::getValue));

        return new ClusterSummaryResponse(
                clusterNodeRegistry.count(),
                clusterNodeRegistry.countByStatus(ClusterNodeStatus.ONLINE) + clusterNodeRegistry.countByStatus(ClusterNodeStatus.SELF),
                clusterNodeRegistry.countByStatus(ClusterNodeStatus.SUSPECT),
                clusterNodeRegistry.countByStatus(ClusterNodeStatus.OFFLINE),
                clusterNodeRegistry.countByStatus(ClusterNodeStatus.DISCOVERED),
                clusterRuntimeProperties.enabled(),
                clusterRuntimeProperties.udpHost(),
                clusterRuntimeProperties.udpPort(),
                clusterRuntimeProperties.broadcastHost(),
                clusterRuntimeProperties.broadcastPort(),
                clusterRuntimeProperties.heartbeatIntervalMs(),
                clusterRuntimeProperties.suspectTimeoutMs(),
                clusterRuntimeProperties.offlineTimeoutMs(),
                byStatus
        );
    }
    private String adminBaseUrl(ClusterNodeResponse node) {
        if (node == null || node.host() == null || node.host().isBlank() || node.adminPort() <= 0 || "unknown".equalsIgnoreCase(node.host())) {
            return null;
        }
        return "http://" + node.host().trim() + ":" + node.adminPort();
    }

    public record GatewayRegistryNode(
            String nodeId,
            String status,
            boolean self,
            String host,
            int adminPort,
            String adminBaseUrl,
            String siteId,
            String region,
            String zone,
            OffsetDateTime lastSeenAt
    ) {}

    public record GatewayRegistryResponse(
            String localNodeId,
            List<GatewayRegistryNode> nodes,
            Map<String, String> baseUrls,
            OffsetDateTime generatedAt
    ) {}

}
