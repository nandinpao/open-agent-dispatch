package com.opensocket.aievent.core.callback;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.DynamicFixedDelayTask;

/**
 * Runtime-reschedulable dispatch timeout recovery loop.
 *
 * <p>The bean is always present. Runtime configuration decides whether a cycle performs work,
 * which avoids the previous startup-only {@code @ConditionalOnProperty} authority leak.</p>
 */
@Component
public class ScheduledDispatchRecovery implements InitializingBean, DisposableBean {
    private final DispatchRecoveryService recoveryService;
    private final TaskCallbackRuntimeConfigurationView runtimeConfiguration;
    private final DynamicFixedDelayTask dynamicTask;

    public ScheduledDispatchRecovery(
            DispatchRecoveryService recoveryService,
            TaskCallbackRuntimeConfigurationView runtimeConfiguration,
            @Qualifier("dispatchOperationalScheduler") TaskScheduler scheduler) {
        this.recoveryService = recoveryService;
        this.runtimeConfiguration = runtimeConfiguration;
        this.dynamicTask = new DynamicFixedDelayTask(
                scheduler,
                "task-callback-dispatch-recovery",
                this::recoverTimedOutDispatches,
                runtimeConfiguration::recoveryScanInterval);
    }

    @Override
    public void afterPropertiesSet() {
        dynamicTask.start();
    }

    @Override
    public void destroy() {
        dynamicTask.stop();
    }

    public void recoverTimedOutDispatches() {
        if (!runtimeConfiguration.recoveryTimeoutEnabled()) {
            return;
        }
        recoveryService.scanAndRecoverTimedOut(runtimeConfiguration.recoveryMaxBatchSize());
    }
}
