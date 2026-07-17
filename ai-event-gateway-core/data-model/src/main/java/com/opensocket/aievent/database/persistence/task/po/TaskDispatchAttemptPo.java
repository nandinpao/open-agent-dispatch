package com.opensocket.aievent.database.persistence.task.po;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TaskDispatchAttemptPo {
    private String dispatchAttemptId;
    private String taskId;
    private String incidentId;
    private String routingDecisionId;
    private String selectedAgentId;
    private String selectedGatewayNodeId;
    private String selectedAgentSessionId;
    private String selectedSiteId;
    private int selectedScore;
    private String status;
    private String eligibilityStatus;
    private String decisionReason;
    private String scoreBreakdownJson;
    private String eligibilityFactsJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
