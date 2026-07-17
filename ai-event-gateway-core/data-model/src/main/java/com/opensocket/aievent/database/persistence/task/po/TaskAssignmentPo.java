package com.opensocket.aievent.database.persistence.task.po;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
public class TaskAssignmentPo {
    private String assignmentId; private String taskId; private String incidentId; private String agentId; private String agentType; private String ownerGatewayNodeId;
    private String agentSessionId; private String siteId; private String eventStage; private String originSourceSystem; private String targetSystem; private String requestedSkill; private String correlationId; private String parentTaskId; private String handoffMode; private String matchedFlowId; private String matchedRuleId; private String assignedPoolId; private String targetPoolId; private String routingPath; private String status; private String routingPolicy; private String routingDecisionId; private String dispatchAttemptId; private String leaseId; private String fencingToken; private OffsetDateTime leaseExpiresAt; private int score; private String reason;
    private boolean capacityReserved; private OffsetDateTime capacityReservedAt; private OffsetDateTime capacityReleasedAt; private OffsetDateTime createdAt; private OffsetDateTime updatedAt;
}
