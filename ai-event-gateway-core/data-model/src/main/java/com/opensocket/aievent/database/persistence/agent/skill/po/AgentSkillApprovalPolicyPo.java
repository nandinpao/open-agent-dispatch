package com.opensocket.aievent.database.persistence.agent.skill.po;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentSkillApprovalPolicyPo {
    private String skillCode;
    private boolean enabled;
    private String submitRolesJson;
    private String approveRolesJson;
    private String publishRolesJson;
    private String rollbackRolesJson;
    private boolean separationOfDuties;
    private String updatedBy;
    private OffsetDateTime updatedAt;
    private String metadataJson;
}
