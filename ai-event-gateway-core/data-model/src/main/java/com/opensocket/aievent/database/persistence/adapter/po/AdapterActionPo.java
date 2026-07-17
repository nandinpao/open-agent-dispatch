package com.opensocket.aievent.database.persistence.adapter.po;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
public class AdapterActionPo {
    private String actionId;
    private String idempotencyKey;
    private String incidentId;
    private String taskId;
    private String dispatchRequestId;
    private String assignmentId;
    private String agentId;
    private String adapterName;
    private String adapterType;
    private String actionType;
    private String status;
    private String reason;
    private String requestHash;
    private String responseRef;
    private String payloadJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime executingAt;
    private OffsetDateTime completedAt;
    private OffsetDateTime failedAt;
    private OffsetDateTime nextAttemptAt;
    private OffsetDateTime retryWaitingAt;
    private OffsetDateTime executorUnavailableAt;
    private String claimedBy;
    private OffsetDateTime claimedAt;
    private OffsetDateTime leaseExpiresAt;
    private OffsetDateTime workerHeartbeatAt;
    private int attemptCount;
    private int maxAttempts;
    private String executorName;
    private String lastError;
}
