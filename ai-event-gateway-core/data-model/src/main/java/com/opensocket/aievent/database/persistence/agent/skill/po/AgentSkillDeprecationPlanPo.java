package com.opensocket.aievent.database.persistence.agent.skill.po;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentSkillDeprecationPlanPo {
    private String skillCode;
    private String status;
    private String replacementSkillCodesJson;
    private OffsetDateTime migrationDeadline;
    private String createdBy;
    private OffsetDateTime createdAt;
    private String updatedBy;
    private OffsetDateTime updatedAt;
    private String metadataJson;
}
