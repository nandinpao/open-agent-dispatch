package com.opensocket.aievent.gateway.netty.cluster;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.gateway.netty.config.ClusterRuntimeProperties;
import com.opensocket.aievent.gateway.netty.configuration.GatewayDynamicFixedDelayTask;
import com.opensocket.aievent.gateway.netty.runtime.NettyOperationalRuntimeConfigurationView;

/** Runtime-configurable cluster announce/heartbeat scheduler. */
@Component
public class ClusterAnnounceScheduler implements DisposableBean {
    private final ClusterRuntimeProperties clusterRuntimeProperties;
    private final ClusterDiscoveryService clusterDiscoveryService;
    private final ClusterUdpServerLifecycle clusterUdpServerLifecycle;
    private final AtomicBoolean helloSent = new AtomicBoolean(false);
    private final GatewayDynamicFixedDelayTask dynamicTask;

    public ClusterAnnounceScheduler(
            ClusterRuntimeProperties clusterRuntimeProperties,
            ClusterDiscoveryService clusterDiscoveryService,
            ClusterUdpServerLifecycle clusterUdpServerLifecycle,
            NettyOperationalRuntimeConfigurationView runtimeConfiguration,
            TaskScheduler taskScheduler) {
        this.clusterRuntimeProperties = clusterRuntimeProperties;
        this.clusterDiscoveryService = clusterDiscoveryService;
        this.clusterUdpServerLifecycle = clusterUdpServerLifecycle;
        this.dynamicTask = new GatewayDynamicFixedDelayTask(
                taskScheduler,
                "cluster-announce",
                this::announce,
                runtimeConfiguration::clusterHeartbeatDelay);
    }

    public void announce() {
        if (!clusterRuntimeProperties.enabled() || !clusterRuntimeProperties.udpBroadcastEnabled()) return;
        if (!helloSent.get()) {
            if (clusterUdpServerLifecycle.send(clusterDiscoveryService.buildHelloEnvelope())) helloSent.set(true);
            return;
        }
        clusterUdpServerLifecycle.send(clusterDiscoveryService.buildHeartbeatEnvelope());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startAfterApplicationReady() { dynamicTask.start(); }
    @Override public void destroy() { dynamicTask.stop(); }
}
