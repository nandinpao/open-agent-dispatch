package com.opensocket.aievent.core.integration.issue.policy;

import java.time.OffsetDateTime;

/**
 * Durable explanation of why a terminal Task does or does not require an external Issue.
 * It is policy authority only; Projection, Outbox and TaskIssueLink keep independent state machines.
 */
public record IssuePolicyDecision(
        String tenantId,
        String decisionId,
        String taskId,
        String projectionPurpose,
        String policyId,
        int policyVersion,
        String taskIssueSyncPolicy,
        IssuePolicyDecisionOutcome decision,
        String reasonCode,
        String sourceEventId,
        String sourceEventType,
        String taskStatus,
        IssuePolicyBindingStatus bindingStatus,
        String connectionId,
        String projectMappingId,
        Integer projectMappingVersion,
        String projectMappingSchemaHash,
        String projectionId,
        String taskIssueLinkId,
        String outboxId,
        String issueOperation,
        String adapterActionId,
        String actionIdempotencyKey,
        Long terminalGeneration,
        IssuePolicyAutomationStatus automationStatus,
        String lastErrorCode,
        String lastErrorMessage,
        String correlationId,
        String causationId,
        String traceId,
        String actorType,
        String actorId,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public IssuePolicyDecision {
        projectionPurpose = projectionPurpose == null || projectionPurpose.isBlank() ? "PRIMARY_ISSUE" : projectionPurpose.trim();
        policyVersion = Math.max(1, policyVersion);
        version = Math.max(1L, version);
        decision = decision == null ? IssuePolicyDecisionOutcome.MANUAL_DECISION : decision;
        bindingStatus = bindingStatus == null ? IssuePolicyBindingStatus.NOT_EVALUATED : bindingStatus;
        automationStatus = automationStatus == null ? IssuePolicyAutomationStatus.WAITING_MANUAL_DECISION : automationStatus;
    }
}
