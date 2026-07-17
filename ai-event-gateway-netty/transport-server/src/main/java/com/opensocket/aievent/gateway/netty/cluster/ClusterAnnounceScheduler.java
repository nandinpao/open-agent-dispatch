package com.opensocket.aievent.gateway.netty.cluster;

import com.opensocket.aievent.gateway.netty.config.ClusterRuntimeProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cluster discovery component for Cluster Announce Scheduler. It manages Gateway node visibility,
 * UDP/static-peer discovery, health transitions, and Admin cluster monitoring events.
 */
@Component
public class ClusterAnnounceScheduler {

    private final ClusterRuntimeProperties clusterRuntimeProperties;
    private final ClusterDiscoveryService clusterDiscoveryService;
    private final ClusterUdpServerLifecycle clusterUdpServerLifecycle;
    private final AtomicBoolean helloSent = new AtomicBoolean(false);

    public ClusterAnnounceScheduler(
            ClusterRuntimeProperties clusterRuntimeProperties,
            ClusterDiscoveryService clusterDiscoveryService,
            ClusterUdpServerLifecycle clusterUdpServerLifecycle
    ) {
        this.clusterRuntimeProperties = clusterRuntimeProperties;
        this.clusterDiscoveryService = clusterDiscoveryService;
        this.clusterUdpServerLifecycle = clusterUdpServerLifecycle;
    }

    @Scheduled(fixedDelayString = "${netty.cluster.heartbeat-interval-ms:3000}")
    public void announce() {
        if (!clusterRuntimeProperties.enabled() || !clusterRuntimeProperties.udpBroadcastEnabled()) {
            return;
        }

        if (!helloSent.get()) {
            if (clusterUdpServerLifecycle.send(clusterDiscoveryService.buildHelloEnvelope())) {
                helloSent.set(true);
            }
            return;
        }

        clusterUdpServerLifecycle.send(clusterDiscoveryService.buildHeartbeatEnvelope());
    }
}
