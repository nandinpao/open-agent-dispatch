package com.opensocket.aievent.core.dispatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "dispatch.client", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ScheduledDispatchExecutor {
    private static final Logger log = LoggerFactory.getLogger(ScheduledDispatchExecutor.class);

    private final DispatchExecutionService executionService;
    private final DispatchProperties properties;

    public ScheduledDispatchExecutor(DispatchExecutionService executionService, DispatchProperties properties) {
        this.executionService = executionService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${dispatch.client.auto-execute-interval-ms:5000}")
    public void executeApprovedBatch() {
        if (!properties.getClient().isEnabled()) {
            log.debug("dispatch_executor_skipped reason=CLIENT_DISABLED executionPolicy={} maxBatchSize={}", properties.getExecutionPolicy(), properties.getClient().getMaxBatchSize());
            return;
        }
        if (!properties.getExecutionPolicy().autoExecutes()) {
            log.info("dispatch_executor_skipped reason=EXECUTION_POLICY_NOT_AUTO executionPolicy={} maxBatchSize={}", properties.getExecutionPolicy(), properties.getClient().getMaxBatchSize());
            return;
        }
        var results = executionService.executeApproved(properties.getClient().getMaxBatchSize());
        if (results.isEmpty()) {
            log.debug("dispatch_executor_no_claimable executionPolicy={} maxBatchSize={}", properties.getExecutionPolicy(), properties.getClient().getMaxBatchSize());
        } else {
            log.info("dispatch_executor_batch_executed count={} executionPolicy={} maxBatchSize={}", results.size(), properties.getExecutionPolicy(), properties.getClient().getMaxBatchSize());
        }
    }
}
