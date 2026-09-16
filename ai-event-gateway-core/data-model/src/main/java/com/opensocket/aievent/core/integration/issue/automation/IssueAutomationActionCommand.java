package com.opensocket.aievent.core.integration.issue.automation;

import java.util.Map;

/**
 * Canonical Route-B command emitted after a Task Issue policy has been evaluated
 * and a governed Project Mapping has been resolved.
 *
 * <p>Connection/mapping fields are durable evidence and an execution fence. They
 * are never trusted as provider credentials or client supplied authorization.
 * The Issue adapter reloads the Task and resolves its execution context
 * server-side before contacting the provider.</p>
 */
public record IssueAutomationActionCommand(
        String tenantId,
        String taskId,
        String incidentId,
        IssueAutomationOperation operation,
        String connectionId,
        String projectMappingId,
        int projectMappingVersion,
        String projectMappingSchemaHash,
        String policyId,
        int policyVersion,
        long terminalGeneration,
        String idempotencyKey,
        String title,
        String description,
        String priorityId,
        String taskIssueLinkId,
        String targetExternalIssueId,
        String targetExternalIssueUrl,
        String correlationId,
        String causationId,
        String traceId,
        String actorType,
        String actorId,
        Map<String, Object> approvedContext,
        Map<String, String> sourceEvidence) {

    public IssueAutomationActionCommand {
        tenantId = required(tenantId, "tenantId");
        taskId = required(taskId, "taskId");
        operation = java.util.Objects.requireNonNull(operation, "operation");
        connectionId = required(connectionId, "connectionId");
        projectMappingId = required(projectMappingId, "projectMappingId");
        if (projectMappingVersion < 1) throw new IllegalArgumentException("projectMappingVersion must be >= 1");
        projectMappingSchemaHash = required(projectMappingSchemaHash, "projectMappingSchemaHash");
        policyId = required(policyId, "policyId");
        if (policyVersion < 1) throw new IllegalArgumentException("policyVersion must be >= 1");
        if (terminalGeneration < 1) throw new IllegalArgumentException("terminalGeneration must be >= 1");
        idempotencyKey = required(idempotencyKey, "idempotencyKey");
        taskIssueLinkId = normalized(taskIssueLinkId);
        targetExternalIssueId = normalized(targetExternalIssueId);
        targetExternalIssueUrl = normalized(targetExternalIssueUrl);
        if (operation != IssueAutomationOperation.CREATE_ISSUE && targetExternalIssueId == null) {
            throw new IllegalArgumentException("targetExternalIssueId is required for existing Issue operations");
        }
        approvedContext = approvedContext == null ? Map.of() : Map.copyOf(approvedContext);
        sourceEvidence = sourceEvidence == null ? Map.of() : Map.copyOf(sourceEvidence);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }

    private static String normalized(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
