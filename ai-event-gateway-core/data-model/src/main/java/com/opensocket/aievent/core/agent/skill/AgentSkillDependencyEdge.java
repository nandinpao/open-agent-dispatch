package com.opensocket.aievent.core.agent.skill;

import java.time.OffsetDateTime;
import java.util.Map;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentSkillDependencyEdge {
    private String edgeId;
    private String sourceSkillCode;
    private String targetSkillCode;
    private String relationType;
    private boolean required;
    private boolean enabled = true;
    private double confidence = 1.0d;
    private String description;
    private String createdBy;
    private OffsetDateTime createdAt;
    private String updatedBy;
    private OffsetDateTime updatedAt;
    private Map<String, Object> metadata = Map.of();
}
