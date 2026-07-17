package com.opensocket.aievent.database.persistence.agent.remediation.po;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentRemediationWorkflowHistoryPo {
    private String historyId;
    private String workflowId;
    private String agentId;
    private String eventType;
    private String operatorId;
    private String reason;
    private String metadataJson;
    private OffsetDateTime occurredAt;
}
