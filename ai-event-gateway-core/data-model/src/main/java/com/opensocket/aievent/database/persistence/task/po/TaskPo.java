package com.opensocket.aievent.database.persistence.task.po;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
public class TaskPo {
    private String taskId;
    private String incidentId;
    private String sourceEventId;
    private String sourceSystem;
    private String eventStage;
    private String originSourceSystem;
    private String targetSystem;
    private String taskType;
    private String taskTypeCode;
    private String status;
    private String priority;
    private String tenantId;
    private String siteId;
    private String plantId;
    private String objectType;
    private String objectId;
    private String eventType;
    private String errorCode;
    private String requestedSkill;
    private String handoffMode;
    private String correlationId;
    private String parentTaskId;
    private String matchedFlowId;
    private String matchedRuleId;
    private String assignedPoolId;
    private String targetPoolId;
    private String classificationStatus;
    private String classificationResultJson;
    private String routingPath;
    private String routingPolicy;
    private String requiredCapabilitiesJson;
    private String createdReason;
    private long occurrenceCountAtCreation;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime timeoutAt;
    private OffsetDateTime terminalAt;
    private int reassignmentCount;
    private OffsetDateTime nextDispatchAttemptAt;
    private int dispatchAttemptCount;
    private String dispatchRetryReason;
    private String dispatchRecoveryClaimedBy;
    private OffsetDateTime dispatchRecoveryClaimUntil;
    private String lifecycleReason;
    private String externalExecutionKey;
    public void setReassignmentCount(int reassignmentCount) { this.reassignmentCount = Math.max(0, reassignmentCount); }
    public void setDispatchAttemptCount(int dispatchAttemptCount) { this.dispatchAttemptCount = Math.max(0, dispatchAttemptCount); }
}

