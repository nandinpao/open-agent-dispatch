package com.opensocket.aievent.gateway.netty.directory;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.gateway.netty.configuration.GatewayDynamicFixedDelayTask;
import com.opensocket.aievent.gateway.netty.runtime.GatewayOperationalRuntimeConfigurationView;

/** C3R2I dynamic schedulers for Core directory heartbeat and snapshot publication. */
@Component
public final class CoreDirectorySyncDynamicSchedulers implements DisposableBean {
    private final GatewayDynamicFixedDelayTask heartbeat;
    private final GatewayDynamicFixedDelayTask snapshot;

    public CoreDirectorySyncDynamicSchedulers(
            CoreDirectorySyncService service,
            GatewayOperationalRuntimeConfigurationView runtimeConfiguration,
            TaskScheduler taskScheduler) {
        this.heartbeat = new GatewayDynamicFixedDelayTask(
                taskScheduler, "core-directory-heartbeat", service::scheduledGatewayHeartbeat,
                runtimeConfiguration::directoryHeartbeatDelay);
        this.snapshot = new GatewayDynamicFixedDelayTask(
                taskScheduler, "core-directory-snapshot", service::scheduledGatewaySnapshot,
                runtimeConfiguration::directorySnapshotDelay);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startAfterApplicationReady() { heartbeat.start(); snapshot.start(); }
    @Override public void destroy() { heartbeat.stop(); snapshot.stop(); }
}
