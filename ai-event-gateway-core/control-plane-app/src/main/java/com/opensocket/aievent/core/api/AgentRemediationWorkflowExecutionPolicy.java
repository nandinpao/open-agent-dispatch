package com.opensocket.aievent.core.api;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.opensocket.aievent.core.agent.remediation.AgentRemediationWorkflowRecord;

/**
 * Centralizes remediation workflow execution policy that used to live directly
 * in AgentRemediationController. Keeping the state/lease/idempotency predicates
 * here makes controller changes safer while preserving the public API contract.
 */
final class AgentRemediationWorkflowExecutionPolicy {
    static final Duration WORKFLOW_EXECUTION_LEASE_DURATION = Duration.ofMinutes(10);
    static final String EXECUTION_MODE_LEASE_BUSY = "P10_WORKFLOW_EXECUTION_LEASE";
    static final String EXECUTION_MODE_DRY_RUN = "P9_DRY_RUN";
    static final String EXECUTION_MODE_LEASED_ACTION_LEVEL = "P10_WORKFLOW_LEASED_ACTION_LEVEL_EXECUTION";

    private AgentRemediationWorkflowExecutionPolicy() {
    }

    static boolean isTerminalWorkflowStatus(String status) {
        return "EXECUTED".equals(status) || "REJECTED".equals(status) || "CANCELLED".equals(status);
    }

    static boolean isCompletedActionStatus(String status) {
        return "SUCCEEDED".equals(status) || "SKIPPED".equals(status);
    }

    static boolean executionLeaseActive(AgentRemediationWorkflowRecord record) {
        return record != null
                && record.getExecutionLeaseOwner() != null
                && record.getExecutionLeaseExpiresAt() != null
                && record.getExecutionLeaseExpiresAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC));
    }

    static Integer executionLeaseRemainingSeconds(AgentRemediationWorkflowRecord record) {
        if (!executionLeaseActive(record)) {
            return 0;
        }
        long seconds = Duration.between(OffsetDateTime.now(ZoneOffset.UTC), record.getExecutionLeaseExpiresAt()).toSeconds();
        return (int) Math.max(0, seconds);
    }
}
