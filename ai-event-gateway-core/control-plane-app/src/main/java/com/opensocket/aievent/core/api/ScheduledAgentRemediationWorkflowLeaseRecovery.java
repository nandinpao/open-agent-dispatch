package com.opensocket.aievent.core.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * P11 scheduled stale workflow execution-lease recovery.
 */
@Component
public class ScheduledAgentRemediationWorkflowLeaseRecovery {
    private static final Logger log = LoggerFactory.getLogger(ScheduledAgentRemediationWorkflowLeaseRecovery.class);

    private final AgentRemediationWorkflowStaleLeaseRecoveryService recoveryService;
    private final boolean enabled;
    private final int limit;

    public ScheduledAgentRemediationWorkflowLeaseRecovery(
            AgentRemediationWorkflowStaleLeaseRecoveryService recoveryService,
            @Value("${agent-remediation.workflow.stale-lease-reaper.enabled:true}") boolean enabled,
            @Value("${agent-remediation.workflow.stale-lease-reaper.limit:100}") int limit) {
        this.recoveryService = recoveryService;
        this.enabled = enabled;
        this.limit = limit;
    }

    @Scheduled(
            fixedDelayString = "${agent-remediation.workflow.stale-lease-reaper.fixed-delay-ms:60000}",
            initialDelayString = "${agent-remediation.workflow.stale-lease-reaper.initial-delay-ms:30000}")
    public void recoverExpiredWorkflowExecutionLeases() {
        if (!enabled) {
            return;
        }
        AgentRemediationWorkflowStaleLeaseRecoveryService.StaleLeaseRecoveryRun run = recoveryService.recoverExpiredLeases(
                limit,
                "p11-stale-lease-reaper",
                "Scheduled P11 stale workflow execution lease recovery.");
        if (run.recoveredCount() > 0 || run.raceLostCount() > 0) {
            log.info("P11 stale remediation workflow lease recovery completed: scanned={}, recovered={}, raceLost={}",
                    run.scannedCount(), run.recoveredCount(), run.raceLostCount());
        }
    }
}
