package com.opensocket.aievent.gateway.netty.cluster.sync;

import tools.jackson.databind.ObjectMapper;
import com.opensocket.aievent.gateway.netty.cluster.ClusterNodeSnapshot;
import com.opensocket.aievent.gateway.netty.cluster.ClusterNodeStatus;
import com.opensocket.aievent.gateway.netty.config.AdminProperties;
import com.opensocket.aievent.gateway.netty.config.ClusterSyncProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Cluster state synchronization component for Cluster State Pull Client. It exposes or consumes
 * lightweight node state snapshots so Admin UI can show a cluster-wide view without moving business
 * ownership across nodes.
 */
@Component
public class ClusterStatePullClient {

    private final ClusterSyncProperties clusterSyncProperties;
    private final AdminProperties adminProperties;
    private final ObjectMapper objectMapper;

    public ClusterStatePullClient(
            ClusterSyncProperties clusterSyncProperties,
            AdminProperties adminProperties,
            ObjectMapper objectMapper
    ) {
        this.clusterSyncProperties = clusterSyncProperties;
        this.adminProperties = adminProperties;
        this.objectMapper = objectMapper;
    }

    public ClusterStateSnapshotResponse pull(ClusterNodeSnapshot node) {
        if (node == null || node.self()) {
            throw new IllegalArgumentException("Cannot pull state from self node");
        }
        if (node.status() == ClusterNodeStatus.OFFLINE) {
            throw new IllegalStateException("Cannot pull state from offline node: " + node.nodeId());
        }
        if (node.host() == null || node.host().isBlank() || node.adminPort() <= 0) {
            throw new IllegalStateException("Cluster node has no reachable admin endpoint: " + node.nodeId());
        }

        var timeout = Duration.ofMillis(clusterSyncProperties.safeRequestTimeoutMs());
        var client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
        var requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create("http://" + node.host() + ":" + node.adminPort() + "/internal/cluster/state"))
                .timeout(timeout)
                .GET()
                .header("Accept", "application/json");
        if (adminProperties.machineAuthEnabled() && adminProperties.internalToken() != null && !adminProperties.internalToken().isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + adminProperties.internalToken());
            requestBuilder.header("X-Cluster-Token", adminProperties.internalToken());
        }
        var request = requestBuilder.build();

        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Remote node returned HTTP " + response.statusCode());
            }
            return objectMapper.readValue(response.body(), ClusterStateSnapshotResponse.class);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while pulling cluster state from " + node.nodeId(), e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to pull cluster state from " + node.nodeId() + ": " + e.getMessage(), e);
        }
    }
}
