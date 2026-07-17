package com.opensocket.aievent.core.agent.remediation;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentRemediationWorkflowHistoryRecord {
    private String historyId;
    private String workflowId;
    private String agentId;
    private String eventType;
    private String operatorId;
    private String reason;
    private String metadataJson;
    private OffsetDateTime occurredAt;
}
