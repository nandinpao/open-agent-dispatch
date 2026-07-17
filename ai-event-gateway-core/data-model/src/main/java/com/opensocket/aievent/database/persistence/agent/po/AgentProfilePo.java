package com.opensocket.aievent.database.persistence.agent.po;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentProfilePo {
    private String agentId;
    private String tenantId;
    private String agentName;
    private String agentType;
    private String ownerTeam;
    private String description;
    private String approvalStatus;
    private boolean enabled;
    private String riskStatus;
    private int policyVersion;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
