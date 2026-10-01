package com.opensocket.aievent.core.action.executor;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import com.opensocket.aievent.core.configuration.runtime.DynamicFixedDelayTask;

@Component
public class ScheduledAdapterActionExecutor implements InitializingBean, DisposableBean {
    private final AdapterActionExecutionService service;
    private final AdapterExecutorRuntimeConfigurationView runtimeConfiguration;
    private final DynamicFixedDelayTask dynamicTask;

    public ScheduledAdapterActionExecutor(AdapterActionExecutionService service,
            AdapterExecutorRuntimeConfigurationView runtimeConfiguration,
            @Qualifier("dispatchOperationalScheduler") TaskScheduler scheduler) {
        this.service = service;
        this.runtimeConfiguration = runtimeConfiguration;
        this.dynamicTask = new DynamicFixedDelayTask(scheduler, "adapter-action-auto-executor", this::executePending, runtimeConfiguration::autoExecuteInterval);
    }

    @Override public void afterPropertiesSet() { dynamicTask.start(); }
    @Override public void destroy() { dynamicTask.stop(); }

    public void executePending() {
        if (!service.hasAutoExecutableAuthority()) return;
        service.executeAutoPending(runtimeConfiguration.batchSize());
    }
}
