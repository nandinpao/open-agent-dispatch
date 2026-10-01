package com.opensocket.aievent.gateway.netty.cluster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.gateway.netty.config.ClusterRuntimeProperties;
import com.opensocket.aievent.gateway.netty.configuration.GatewayDynamicFixedDelayTask;
import com.opensocket.aievent.gateway.netty.runtime.NettyOperationalRuntimeConfigurationView;

/** Runtime-configurable cluster stale-node scan scheduler. */
@Component
public class ClusterNodeTimeoutScheduler implements DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(ClusterNodeTimeoutScheduler.class);
    private final ClusterRuntimeProperties clusterRuntimeProperties;
    private final ClusterDiscoveryService clusterDiscoveryService;
    private final GatewayDynamicFixedDelayTask dynamicTask;

    public ClusterNodeTimeoutScheduler(
            ClusterRuntimeProperties clusterRuntimeProperties,
            ClusterDiscoveryService clusterDiscoveryService,
            NettyOperationalRuntimeConfigurationView runtimeConfiguration,
            TaskScheduler taskScheduler) {
        this.clusterRuntimeProperties = clusterRuntimeProperties;
        this.clusterDiscoveryService = clusterDiscoveryService;
        this.dynamicTask = new GatewayDynamicFixedDelayTask(
                taskScheduler,
                "cluster-node-timeout-scan",
                this::scanStaleNodes,
                runtimeConfiguration::clusterHeartbeatDelay);
    }

    public void scanStaleNodes() {
        if (!clusterRuntimeProperties.enabled()) return;
        var changes = clusterDiscoveryService.scanStaleNodes();
        if (!changes.isEmpty()) log.warn("Cluster stale scan changed {} node(s)", changes.size());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startAfterApplicationReady() { dynamicTask.start(); }
    @Override public void destroy() { dynamicTask.stop(); }
}
