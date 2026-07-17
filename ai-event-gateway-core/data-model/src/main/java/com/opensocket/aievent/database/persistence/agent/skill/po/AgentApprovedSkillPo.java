package com.opensocket.aievent.database.persistence.agent.skill.po;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentApprovedSkillPo {
    private String agentId;
    private String skillCode;
    private int policyVersion;
    private boolean enabled;
    private String approvedBy;
    private OffsetDateTime approvedAt;
    private String metadataJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

}
