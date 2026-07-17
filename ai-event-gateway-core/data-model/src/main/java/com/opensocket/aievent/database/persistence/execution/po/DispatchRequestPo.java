package com.opensocket.aievent.database.persistence.execution.po;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
public class DispatchRequestPo {
    private String dispatchRequestId;
    private String assignmentId;
    private String taskId;
    private String incidentId;
    private String agentId;
    private String ownerGatewayNodeId;
    private String agentSessionId;
    private String siteId;
    private String status;
    private String reviewMode;
    private String eligibilityStatus;
    private String dispatchMethod;
    private String gatewayDispatchPath;
    private String dispatchToken;
    private String reason;
    private String commandJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime approvedAt;
    private OffsetDateTime dispatchedAt;
    private OffsetDateTime failedAt;
    private int attemptCount;
    private String lastError;
    private String lastCallbackId;
    private OffsetDateTime completedAt;
    private OffsetDateTime timedOutAt;
    private OffsetDateTime retryWaitingAt;
    private OffsetDateTime nextRetryAt;
    private OffsetDateTime deadLetterAt;
    private String claimedBy;
    private OffsetDateTime claimStartedAt;
    private OffsetDateTime claimUntil;
}

