package com.opensocket.aievent.core.agent.remediation;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentRemediationWorkflowRecord {
    private String workflowId;
    private String proposalId;
    private String agentId;
    private String status;
    private String severity;
    private Boolean approvalRequired;
    private String createdBy;
    private String lastOperatorId;
    private String rollbackSuggestionsJson;
    private String actionsJson;
    private String metadataJson;
    private String executionLeaseOwner;
    private OffsetDateTime executionLeaseAcquiredAt;
    private OffsetDateTime executionLeaseExpiresAt;
    private Long executionLeaseVersion;
    private Long version;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
