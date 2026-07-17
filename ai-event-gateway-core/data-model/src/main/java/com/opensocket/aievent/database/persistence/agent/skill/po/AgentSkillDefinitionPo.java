package com.opensocket.aievent.database.persistence.agent.skill.po;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentSkillDefinitionPo {
    private String skillCode;
    private String displayName;
    private String domain;
    private String description;
    private String taxonomyVersion;
    private String taskDefinitionId;
    private String sourceSystem;
    private String taskType;
    private String providersJson;
    private String taskTypesJson;
    private String operationsJson;
    private String toolPoliciesJson;
    private String resourceScopesJson;
    private String dataClassesJson;
    private String riskLevel;
    private boolean requiresHumanApproval;
    private boolean maskingRequired;
    private boolean enabled;
    private String metadataJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
