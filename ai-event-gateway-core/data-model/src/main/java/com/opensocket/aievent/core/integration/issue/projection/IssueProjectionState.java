package com.opensocket.aievent.core.integration.issue.projection;

import com.opensocket.aievent.core.issuetracking.contract.*;
import java.time.OffsetDateTime;

/** Durable Projection aggregate state. Lifecycle, failure and recovery are intentionally separate. */
public record IssueProjectionState(
        String tenantId,
        String projectionId,
        String projectionAggregateKey,
        ProjectionPurpose projectionPurpose,
        String sourceEventId,
        String sourceEventType,
        String sourceAggregateType,
        String sourceAggregateId,
        String taskId,
        String a2aRequestId,
        String handoffSnapshotId,
        String taskIssueLinkId,
        String connectionId,
        String projectMappingId,
        int projectMappingVersion,
        String projectMappingSchemaHash,
        int canonicalDocumentSchemaVersion,
        String canonicalDocumentHash,
        IssueProjectionDesiredState desiredState,
        IssueProjectionObservedState observedState,
        ProjectionLifecycleStatus lifecycleStatus,
        ProjectionFailureClassification failureClassification,
        ProjectionRecoveryStrategy recoveryStrategy,
        IssueProjectionConflictPolicy conflictPolicy,
        String desiredPayloadHash,
        String observedPayloadHash,
        String lastAppliedDomainEventId,
        long operationSequence,
        long projectionVersion,
        String supersededBy,
        int retryCount,
        int maxAttempts,
        OffsetDateTime nextRetryAt,
        String lastErrorCode,
        String lastErrorMessage,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime completedAt,
        String correlationId) {

    public IssueProjectionState {
        if (projectionPurpose == null) projectionPurpose = ProjectionPurpose.PRIMARY_ISSUE;
        if (lifecycleStatus == null) lifecycleStatus = ProjectionLifecycleStatus.REQUESTED;
        if (failureClassification == null) failureClassification = ProjectionFailureClassification.NONE;
        if (recoveryStrategy == null) recoveryStrategy = ProjectionRecoveryStrategy.NONE;
        if (canonicalDocumentSchemaVersion < 1) canonicalDocumentSchemaVersion = 2;
        if (operationSequence < 1) operationSequence = 1;
        if (projectionVersion < 1) projectionVersion = 1;
    }

    /** Compatibility accessor retained for older UI/API code. */
    public ProjectionLifecycleStatus lifecycleState() { return lifecycleStatus; }

    /** Compatibility constructor for the Phase 3C state shape. */
    public IssueProjectionState(String tenantId,String projectionId,String sourceEventId,String sourceEventType,
            String sourceAggregateType,String sourceAggregateId,String taskId,String a2aRequestId,
            String handoffSnapshotId,String taskIssueLinkId,String connectionId,String projectMappingId,
            int projectMappingVersion,String projectMappingSchemaHash,IssueProjectionDesiredState desiredState,
            IssueProjectionObservedState observedState,IssueProjectionLifecycleState lifecycleState,
            IssueProjectionConflictPolicy conflictPolicy,String desiredPayloadHash,String observedPayloadHash,
            int retryCount,int maxAttempts,OffsetDateTime nextRetryAt,String lastErrorCode,String lastErrorMessage,
            long version,OffsetDateTime createdAt,OffsetDateTime updatedAt,OffsetDateTime completedAt,String correlationId) {
        this(tenantId,projectionId,legacyAggregateKey(tenantId,taskId,connectionId,projectMappingId),
                ProjectionPurpose.PRIMARY_ISSUE,sourceEventId,sourceEventType,sourceAggregateType,sourceAggregateId,
                taskId,a2aRequestId,handoffSnapshotId,taskIssueLinkId,connectionId,projectMappingId,
                projectMappingVersion,projectMappingSchemaHash,2,desiredPayloadHash,desiredState,observedState,
                lifecycleState==null?ProjectionLifecycleStatus.REQUESTED:lifecycleState.canonical(),
                ProjectionFailureClassification.NONE,ProjectionRecoveryStrategy.NONE,conflictPolicy,
                desiredPayloadHash,observedPayloadHash,sourceEventId,1,Math.max(1,version),null,retryCount,maxAttempts,
                nextRetryAt,lastErrorCode,lastErrorMessage,version,createdAt,updatedAt,completedAt,correlationId);
    }

    public IssueProjectionState(String tenantId,String projectionId,String sourceEventId,String sourceEventType,
            String sourceAggregateType,String sourceAggregateId,String taskId,String a2aRequestId,
            String handoffSnapshotId,String taskIssueLinkId,String connectionId,String projectMappingId,
            IssueProjectionDesiredState desiredState,IssueProjectionObservedState observedState,
            IssueProjectionLifecycleState lifecycleState,IssueProjectionConflictPolicy conflictPolicy,
            String desiredPayloadHash,String observedPayloadHash,int retryCount,int maxAttempts,
            OffsetDateTime nextRetryAt,String lastErrorCode,String lastErrorMessage,long version,
            OffsetDateTime createdAt,OffsetDateTime updatedAt,OffsetDateTime completedAt,String correlationId) {
        this(tenantId,projectionId,sourceEventId,sourceEventType,sourceAggregateType,sourceAggregateId,taskId,
                a2aRequestId,handoffSnapshotId,taskIssueLinkId,connectionId,projectMappingId,1,null,desiredState,
                observedState,lifecycleState,conflictPolicy,desiredPayloadHash,observedPayloadHash,retryCount,
                maxAttempts,nextRetryAt,lastErrorCode,lastErrorMessage,version,createdAt,updatedAt,completedAt,correlationId);
    }

    private static String legacyAggregateKey(String tenant,String task,String connection,String mapping) {
        if (tenant==null||task==null||connection==null||mapping==null) return "legacy:"+String.valueOf(tenant)+":"+String.valueOf(task);
        return new ProjectionAggregateKey(tenant,task,connection,mapping,ProjectionPurpose.PRIMARY_ISSUE).canonicalValue();
    }
}
