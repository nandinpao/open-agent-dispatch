package com.opensocket.aievent.database.persistence.agent.remediation.po;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentRemediationWorkflowActionExecutionPo {
    private String actionExecutionId;
    private String workflowId;
    private String agentId;
    private String actionId;
    private String actionType;
    private String idempotencyKey;
    private String status;
    private Integer attemptCount;
    private String lastOperatorId;
    private String lastReason;
    private String lastResultJson;
    private String lastError;
    private OffsetDateTime firstAttemptAt;
    private OffsetDateTime lastAttemptAt;
    private OffsetDateTime completedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
