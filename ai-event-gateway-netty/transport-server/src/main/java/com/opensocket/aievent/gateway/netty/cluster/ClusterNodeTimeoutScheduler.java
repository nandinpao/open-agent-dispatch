package com.opensocket.aievent.gateway.netty.cluster;

import com.opensocket.aievent.gateway.netty.config.ClusterRuntimeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cluster discovery component for Cluster Node Timeout Scheduler. It manages Gateway node
 * visibility, UDP/static-peer discovery, health transitions, and Admin cluster monitoring events.
 */
@Component
public class ClusterNodeTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(ClusterNodeTimeoutScheduler.class);

    private final ClusterRuntimeProperties clusterRuntimeProperties;
    private final ClusterDiscoveryService clusterDiscoveryService;

    public ClusterNodeTimeoutScheduler(
            ClusterRuntimeProperties clusterRuntimeProperties,
            ClusterDiscoveryService clusterDiscoveryService
    ) {
        this.clusterRuntimeProperties = clusterRuntimeProperties;
        this.clusterDiscoveryService = clusterDiscoveryService;
    }

    @Scheduled(fixedDelayString = "${netty.cluster.heartbeat-interval-ms:3000}")
    public void scanStaleNodes() {
        if (!clusterRuntimeProperties.enabled()) {
            return;
        }

        var changes = clusterDiscoveryService.scanStaleNodes();
        if (!changes.isEmpty()) {
            log.warn("Cluster stale scan changed {} node(s)", changes.size());
        }
    }
}
