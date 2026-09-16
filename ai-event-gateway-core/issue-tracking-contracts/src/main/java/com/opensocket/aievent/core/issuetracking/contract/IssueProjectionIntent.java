package com.opensocket.aievent.core.issuetracking.contract;

import java.time.OffsetDateTime;
import java.util.Objects;

/** Durable, provider-neutral intent accepted by a scoped provider adapter. */
public record IssueProjectionIntent(
        String tenantId,
        String projectionId,
        ProjectionAggregateKey aggregateKey,
        ProjectionPurpose projectionPurpose,
        String taskId,
        String connectionId,
        String projectMappingId,
        long mappingVersion,
        String mappingSchemaHash,
        IssueProjectionOperation operation,
        ExternalIssueDocument document,
        String domainEventId,
        long operationSequence,
        long expectedProjectionVersion,
        String idempotencyKey,
        OffsetDateTime createdAt) {

    public IssueProjectionIntent {
        tenantId = requireText(tenantId, "tenantId");
        projectionId = requireText(projectionId, "projectionId");
        aggregateKey = Objects.requireNonNull(aggregateKey, "aggregateKey is required");
        projectionPurpose = Objects.requireNonNull(projectionPurpose, "projectionPurpose is required");
        taskId = requireText(taskId, "taskId");
        connectionId = requireText(connectionId, "connectionId");
        projectMappingId = requireText(projectMappingId, "projectMappingId");
        if (mappingVersion < 1) throw new IllegalArgumentException("mappingVersion must be positive");
        mappingSchemaHash = requireText(mappingSchemaHash, "mappingSchemaHash");
        operation = Objects.requireNonNull(operation, "operation is required");
        document = Objects.requireNonNull(document, "document is required");
        domainEventId = requireText(domainEventId, "domainEventId");
        if (operationSequence < 1) throw new IllegalArgumentException("operationSequence must be positive");
        if (expectedProjectionVersion < 0) throw new IllegalArgumentException("expectedProjectionVersion cannot be negative");
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
    }

    /** Compatibility constructor for Phase 3A/3B callers. */
    public IssueProjectionIntent(String tenantId, String projectionId, String taskId,
            String connectionId, String projectMappingId, long mappingVersion,
            IssueProjectionOperation operation, ExternalIssueDocument document,
            String idempotencyKey, OffsetDateTime createdAt) {
        this(tenantId, projectionId,
                new ProjectionAggregateKey(tenantId, taskId, connectionId, projectMappingId,
                        ProjectionPurpose.PRIMARY_ISSUE),
                ProjectionPurpose.PRIMARY_ISSUE, taskId, connectionId, projectMappingId,
                mappingVersion, document.mappingSchemaHash(), operation, document,
                idempotencyKey, 1, 0, idempotencyKey, createdAt);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " is required");
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " is required");
        return normalized;
    }
}
