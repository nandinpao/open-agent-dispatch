package com.opensocket.aievent.core.agent.remediation;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentRemediationWorkflowActionExecutionRecord {
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
