package com.opensocket.aievent.core.dispatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.DynamicFixedDelayTask;

/**
 * V41-C3R2B runtime-reschedulable automatic Dispatch executor.
 *
 * <p>{@code dispatch.client.enabled} remains a TEST_RELEASE_ONLY startup boundary. Runtime
 * execution policy, batch size, and cadence are resolved from the authenticated local snapshot
 * on every cycle / next-cycle schedule.</p>
 */
@Component
@ConditionalOnProperty(prefix = "dispatch.client", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ScheduledDispatchExecutor implements InitializingBean, DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(ScheduledDispatchExecutor.class);

    private final DispatchExecutionService executionService;
    private final DispatchRuntimeConfigurationView runtimeConfiguration;
    private final DynamicFixedDelayTask dynamicTask;

    public ScheduledDispatchExecutor(
            DispatchExecutionService executionService,
            DispatchRuntimeConfigurationView runtimeConfiguration,
            @Qualifier("dispatchOperationalScheduler") TaskScheduler scheduler) {
        this.executionService = executionService;
        this.runtimeConfiguration = runtimeConfiguration;
        this.dynamicTask = new DynamicFixedDelayTask(
                scheduler,
                "dispatch-auto-executor",
                this::executeApprovedBatch,
                runtimeConfiguration::autoExecuteInterval);
    }

    @Override
    public void afterPropertiesSet() {
        dynamicTask.start();
    }

    @Override
    public void destroy() {
        dynamicTask.stop();
    }

    public void executeApprovedBatch() {
        DispatchExecutionPolicy policy = runtimeConfiguration.executionPolicy();
        int maxBatchSize = runtimeConfiguration.maxBatchSize();
        if (!policy.autoExecutes()) {
            log.info("dispatch_executor_skipped reason=EXECUTION_POLICY_NOT_AUTO executionPolicy={} maxBatchSize={}", policy, maxBatchSize);
            return;
        }
        var results = executionService.executeApproved(maxBatchSize);
        if (results.isEmpty()) {
            log.debug("dispatch_executor_no_claimable executionPolicy={} maxBatchSize={}", policy, maxBatchSize);
        } else {
            log.info("dispatch_executor_batch_executed count={} executionPolicy={} maxBatchSize={}", results.size(), policy, maxBatchSize);
        }
    }
}
