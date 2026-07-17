package com.opensocket.aievent.database.persistence.execution.po;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
public class TaskCallbackPo {
    private String callbackId;
    private String callbackType;
    private String taskId;
    private String dispatchRequestId;
    private String assignmentId;
    private String agentId;
    private String ownerGatewayNodeId;
    private String agentSessionId;
    private Integer attemptNo;
    private String fencingToken;
    private boolean accepted;
    private String ignoredReason;
    private String message;
    private Integer progressPercent;
    private String errorCode;
    private String errorMessage;
    private String payloadJson;
    private OffsetDateTime occurredAt;
    private OffsetDateTime processedAt;
    private boolean duplicate;
    private String idempotencyKey;
    private String callbackFingerprint;
    private boolean replayDetected;
    private String previousTaskStatus;
    private String newTaskStatus;
    private String previousDispatchStatus;
    private String newDispatchStatus;
}
