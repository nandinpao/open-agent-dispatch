package com.opensocket.aievent.core.api;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * P11 operator-facing stale execution lease queue.
 */
@RestController
@RequestMapping("/admin/remediation/workflow-leases")
public class AgentRemediationWorkflowLeaseRecoveryController {
    private final AgentRemediationWorkflowStaleLeaseRecoveryService recoveryService;

    public AgentRemediationWorkflowLeaseRecoveryController(AgentRemediationWorkflowStaleLeaseRecoveryService recoveryService) {
        this.recoveryService = recoveryService;
    }

    @GetMapping("/stale")
    public StaleLeaseQueueResponse listStaleWorkflowExecutionLeases(@RequestParam(defaultValue = "50") int limit) {
        return new StaleLeaseQueueResponse(
                OffsetDateTime.now(ZoneOffset.UTC),
                recoveryService.listStaleLeases(limit));
    }

    @GetMapping("/recovered")
    public RecoveredLeaseQueueResponse listRecoveredWorkflowExecutionLeases(@RequestParam(defaultValue = "50") int limit) {
        return new RecoveredLeaseQueueResponse(
                OffsetDateTime.now(ZoneOffset.UTC),
                recoveryService.listRecentRecoveredLeases(limit));
    }

    @PostMapping("/recover-stale")
    public AgentRemediationWorkflowStaleLeaseRecoveryService.StaleLeaseRecoveryRun recoverStaleWorkflowExecutionLeases(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "admin-ui") String operatorId,
            @RequestParam(defaultValue = "Manual P11 stale workflow execution lease recovery.") String reason) {
        return recoveryService.recoverExpiredLeases(limit, operatorId, reason);
    }

    public record StaleLeaseQueueResponse(
            OffsetDateTime generatedAt,
            List<AgentRemediationWorkflowStaleLeaseRecoveryService.StaleWorkflowExecutionLeaseView> staleLeases) {
    }

    public record RecoveredLeaseQueueResponse(
            OffsetDateTime generatedAt,
            List<AgentRemediationWorkflowStaleLeaseRecoveryService.RecoveredWorkflowExecutionLeaseView> recoveredLeases) {
    }
}
