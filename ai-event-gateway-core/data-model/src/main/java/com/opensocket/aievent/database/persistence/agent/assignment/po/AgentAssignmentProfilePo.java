package com.opensocket.aievent.database.persistence.agent.assignment.po;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentAssignmentProfilePo {
    private String tenantId;
    private String profileId;
    private String profileCode;
    private String profileName;
    private String agentType;
    private String taskDefinitionId;
    private String sourceSystem;
    private String taskType;
    private String description;
    private String allowedTaskTypesJson;
    private String allowedIssueProvidersJson;
    private String requiredRuntimeFeaturesJson;
    private String toolPolicy;
    private String riskLevelLimit;
    private boolean requiresCertification;
    private boolean requiresHumanApproval;
    private boolean active;
    private int policyVersion;
    private OffsetDateTime effectiveAt;
    private OffsetDateTime expiresAt;
    private int renewalRequiredBeforeDays;
    private String metadataJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
