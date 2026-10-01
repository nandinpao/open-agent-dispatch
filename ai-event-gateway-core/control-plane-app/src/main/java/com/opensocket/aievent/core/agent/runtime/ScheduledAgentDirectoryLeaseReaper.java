package com.opensocket.aievent.core.agent.runtime;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.agent.AgentDirectoryService;
import com.opensocket.aievent.core.configuration.runtime.DynamicFixedDelayTask;

/** Runtime-configurable Agent Directory lease reaper. */
@Component
public final class ScheduledAgentDirectoryLeaseReaper implements InitializingBean, DisposableBean {
    private final AgentDirectoryService directoryService;
    private final DynamicFixedDelayTask dynamicTask;

    public ScheduledAgentDirectoryLeaseReaper(
            AgentDirectoryService directoryService,
            AgentDirectoryRuntimeConfigurationView runtimeConfiguration,
            @Qualifier("maintenanceOperationalScheduler") TaskScheduler taskScheduler) {
        this.directoryService = directoryService;
        this.dynamicTask = new DynamicFixedDelayTask(
                taskScheduler,
                "agent-directory-lease-reaper",
                directoryService::expireLeases,
                runtimeConfiguration::leaseReaperFixedDelay);
    }

    @Override
    public void afterPropertiesSet() {
        dynamicTask.start();
    }

    @Override
    public void destroy() {
        dynamicTask.stop();
    }
}
