package com.opensocket.aievent.core.agent.governance.p3;

import java.time.OffsetDateTime;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentEnterpriseGovernanceSummary {
    private String agentId;
    private String status = "UNKNOWN";
    private int policyDriftCount;
    private int renewalDueCount;
    private int expiredQualificationCount;
    private int blockingCount;
    private List<AgentPolicyDriftFinding> driftFindings = List.of();
    private List<AgentGovernanceRemediationAction> remediationActions = List.of();
    private OffsetDateTime generatedAt;
}
