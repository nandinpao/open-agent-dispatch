package com.opensocket.aievent.core.gateway.runtime;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.DynamicFixedDelayTask;
import com.opensocket.aievent.core.gateway.GatewayDirectoryService;

/** C3R2I dynamic Gateway-node lease reaper. */
@Component
public final class ScheduledGatewayNodeLeaseReaper implements InitializingBean, DisposableBean {
    private final DynamicFixedDelayTask dynamicTask;

    public ScheduledGatewayNodeLeaseReaper(
            GatewayDirectoryService directoryService,
            GatewayNodeRuntimeConfigurationView runtimeConfiguration,
            @Qualifier("maintenanceOperationalScheduler") TaskScheduler taskScheduler) {
        this.dynamicTask = new DynamicFixedDelayTask(
                taskScheduler,
                "gateway-node-lease-reaper",
                directoryService::expireLeases,
                runtimeConfiguration::leaseReaperDelay);
    }

    @Override public void afterPropertiesSet() { dynamicTask.start(); }
    @Override public void destroy() { dynamicTask.stop(); }
}
