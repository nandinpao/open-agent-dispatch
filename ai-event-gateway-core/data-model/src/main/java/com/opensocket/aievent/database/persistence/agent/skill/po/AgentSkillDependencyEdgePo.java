package com.opensocket.aievent.database.persistence.agent.skill.po;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentSkillDependencyEdgePo {
    private String edgeId;
    private String sourceSkillCode;
    private String targetSkillCode;
    private String relationType;
    private boolean required;
    private boolean enabled;
    private double confidence;
    private String description;
    private String createdBy;
    private OffsetDateTime createdAt;
    private String updatedBy;
    private OffsetDateTime updatedAt;
    private String metadataJson;
}
