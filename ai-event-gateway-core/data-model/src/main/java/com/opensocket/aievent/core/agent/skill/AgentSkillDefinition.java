package com.opensocket.aievent.core.agent.skill;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AgentSkillDefinition {
    private String skillCode;
    private String displayName;
    private String domain;
    private String description;
    private String taxonomyVersion = "opensocket-capability-taxonomy/v1";
    /**
     * P2-M2 formal task contract link. Advanced Dispatch Policy Definitions must
     * resolve to a Dispatch Task Definition instead of relying only on domain/taskTypes metadata.
     */
    private String taskDefinitionId;
    private String sourceSystem;
    private String taskType;
    private List<String> providers = new ArrayList<>();
    private List<String> taskTypes = new ArrayList<>();
    private List<String> operations = new ArrayList<>();
    private List<String> toolPolicies = new ArrayList<>();
    private List<String> resourceScopes = new ArrayList<>();
    private List<String> dataClasses = new ArrayList<>();
    private String riskLevel = "LOW";
    private boolean requiresHumanApproval;
    private boolean maskingRequired;
    private boolean enabled = true;
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public String getSkillCode() { return skillCode; }
    public void setSkillCode(String skillCode) { this.skillCode = skillCode; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getTaxonomyVersion() { return taxonomyVersion; }
    public void setTaxonomyVersion(String taxonomyVersion) { this.taxonomyVersion = taxonomyVersion; }
    public String getTaskDefinitionId() { return taskDefinitionId; }
    public void setTaskDefinitionId(String taskDefinitionId) { this.taskDefinitionId = taskDefinitionId; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public List<String> getProviders() { return providers; }
    public void setProviders(List<String> providers) { this.providers = safeList(providers); }
    public List<String> getTaskTypes() { return taskTypes; }
    public void setTaskTypes(List<String> taskTypes) { this.taskTypes = safeList(taskTypes); }
    public List<String> getOperations() { return operations; }
    public void setOperations(List<String> operations) { this.operations = safeList(operations); }
    public List<String> getToolPolicies() { return toolPolicies; }
    public void setToolPolicies(List<String> toolPolicies) { this.toolPolicies = safeList(toolPolicies); }
    public List<String> getResourceScopes() { return resourceScopes; }
    public void setResourceScopes(List<String> resourceScopes) { this.resourceScopes = safeList(resourceScopes); }
    public List<String> getDataClasses() { return dataClasses; }
    public void setDataClasses(List<String> dataClasses) { this.dataClasses = safeList(dataClasses); }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public boolean isRequiresHumanApproval() { return requiresHumanApproval; }
    public void setRequiresHumanApproval(boolean requiresHumanApproval) { this.requiresHumanApproval = requiresHumanApproval; }
    public boolean isMaskingRequired() { return maskingRequired; }
    public void setMaskingRequired(boolean maskingRequired) { this.maskingRequired = maskingRequired; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata); }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    private List<String> safeList(List<String> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }
}
