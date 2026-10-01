package com.opensocket.aievent.gateway.netty.tcp;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.gateway.netty.configuration.GatewayDynamicFixedDelayTask;
import com.opensocket.aievent.gateway.netty.runtime.NettyOperationalRuntimeConfigurationView;

/** Runtime-configurable closed TCP connection history cleanup. */
@Component
public class TcpConnectionRegistryCleanupScheduler implements DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(TcpConnectionRegistryCleanupScheduler.class);

    private final TcpConnectionRegistry connectionRegistry;
    private final NettyOperationalRuntimeConfigurationView runtimeConfiguration;
    private final GatewayDynamicFixedDelayTask dynamicTask;

    public TcpConnectionRegistryCleanupScheduler(
            TcpConnectionRegistry connectionRegistry,
            NettyOperationalRuntimeConfigurationView runtimeConfiguration,
            TaskScheduler taskScheduler) {
        this.connectionRegistry = connectionRegistry;
        this.runtimeConfiguration = runtimeConfiguration;
        this.dynamicTask = new GatewayDynamicFixedDelayTask(
                taskScheduler,
                "tcp-closed-connection-cleanup",
                this::cleanup,
                runtimeConfiguration::tcpCleanupInitialDelay,
                runtimeConfiguration::tcpCleanupInterval);
    }

    public void cleanup() {
        if (!runtimeConfiguration.tcpCleanupEnabled()) return;
        var result = connectionRegistry.cleanupClosedConnections(
                Duration.ofMillis(runtimeConfiguration.tcpClosedHistoryTtlMs()),
                runtimeConfiguration.tcpMaxClosedHistory());
        if (result.totalRemoved() > 0) {
            log.debug("TCP closed connection history pruned. before={}, removedByTtl={}, removedByLimit={}, after={}",
                    result.closedHistoryBefore(), result.removedByTtl(), result.removedByLimit(), result.closedHistoryAfter());
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startAfterApplicationReady() {
        dynamicTask.start();
    }

    @Override
    public void destroy() {
        dynamicTask.stop();
    }
}
