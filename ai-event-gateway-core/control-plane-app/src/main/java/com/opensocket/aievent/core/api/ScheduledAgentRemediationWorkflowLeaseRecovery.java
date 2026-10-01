package com.opensocket.aievent.core.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.DynamicFixedDelayTask;

/** V41-C3B1 dynamic stale workflow execution-lease recovery scheduler. */
@Component
public class ScheduledAgentRemediationWorkflowLeaseRecovery implements InitializingBean, DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(ScheduledAgentRemediationWorkflowLeaseRecovery.class);

    private final AgentRemediationWorkflowStaleLeaseRecoveryService recoveryService;
    private final AgentRemediationWorkflowRuntimeConfigurationView runtimeConfiguration;
    private final DynamicFixedDelayTask dynamicTask;

    public ScheduledAgentRemediationWorkflowLeaseRecovery(
            AgentRemediationWorkflowStaleLeaseRecoveryService recoveryService,
            AgentRemediationWorkflowRuntimeConfigurationView runtimeConfiguration,
            @Qualifier("maintenanceOperationalScheduler") TaskScheduler scheduler) {
        this.recoveryService = recoveryService;
        this.runtimeConfiguration = runtimeConfiguration;
        this.dynamicTask = new DynamicFixedDelayTask(
                scheduler,
                "agent-remediation-stale-lease-recovery",
                this::recoverExpiredWorkflowExecutionLeases,
                runtimeConfiguration::initialDelay,
                runtimeConfiguration::fixedDelay);
    }

    @Override public void afterPropertiesSet() { dynamicTask.start(); }
    @Override public void destroy() { dynamicTask.stop(); }

    public void recoverExpiredWorkflowExecutionLeases() {
        if (!runtimeConfiguration.enabled()) return;
        AgentRemediationWorkflowStaleLeaseRecoveryService.StaleLeaseRecoveryRun run = recoveryService.recoverExpiredLeases(
                runtimeConfiguration.limit(),
                "p11-stale-lease-reaper",
                "Scheduled P11 stale workflow execution lease recovery.");
        if (run.recoveredCount() > 0 || run.raceLostCount() > 0) {
            log.info("P11 stale remediation workflow lease recovery completed: scanned={}, recovered={}, raceLost={}",
                    run.scannedCount(), run.recoveredCount(), run.raceLostCount());
        }
    }
}
