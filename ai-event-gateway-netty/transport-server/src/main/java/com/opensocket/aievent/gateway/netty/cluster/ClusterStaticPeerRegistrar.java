package com.opensocket.aievent.gateway.netty.cluster;

import com.opensocket.aievent.gateway.netty.config.ClusterRuntimeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Cluster discovery component for Cluster Static Peer Registrar. It manages Gateway node
 * visibility, UDP/static-peer discovery, health transitions, and Admin cluster monitoring events.
 */
@Component
public class ClusterStaticPeerRegistrar {

    private static final Logger log = LoggerFactory.getLogger(ClusterStaticPeerRegistrar.class);

    private final ClusterRuntimeProperties clusterRuntimeProperties;
    private final ClusterNodeRegistry clusterNodeRegistry;

    public ClusterStaticPeerRegistrar(ClusterRuntimeProperties clusterRuntimeProperties, ClusterNodeRegistry clusterNodeRegistry) {
        this.clusterRuntimeProperties = clusterRuntimeProperties;
        this.clusterNodeRegistry = clusterNodeRegistry;
    }

    @EventListener(ApplicationReadyEvent.class)
    /**
     * Registers configured static peers after the Spring application is ready. Static peers provide
     * deterministic discovery for routed networks where UDP broadcast is blocked.
     */
    public void registerStaticPeers() {
        if (!clusterRuntimeProperties.enabled() || !clusterRuntimeProperties.staticPeersEnabled()) {
            return;
        }
        for (var peer : clusterRuntimeProperties.parsedStaticPeers()) {
            clusterNodeRegistry.applyStaticPeer(peer).ifPresent(change -> log.info(
                    "Static cluster peer registered. nodeId={}, host={}, adminPort={}",
                    change.nodeId(), change.node().host(), change.node().adminPort()
            ));
        }
    }
}
