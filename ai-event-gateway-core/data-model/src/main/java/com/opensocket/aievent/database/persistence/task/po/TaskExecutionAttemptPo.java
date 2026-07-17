package com.opensocket.aievent.database.persistence.task.po;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TaskExecutionAttemptPo {
    private String executionAttemptId;
    private String taskId;
    private String assignmentId;
    private String dispatchAttemptId;
    private String agentId;
    private String agentSessionId;
    private String leaseId;
    private String fencingToken;
    private int attemptNo;
    private String status;
    private String resultCode;
    private String errorCode;
    private String errorMessage;
    private String callbackId;
    private OffsetDateTime createdAt;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private OffsetDateTime updatedAt;
}
