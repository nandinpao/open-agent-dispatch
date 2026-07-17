package com.opensocket.aievent.core.dispatch.flow;

public class DispatchFlowRequiredSkillView {
    private String tenantId;
    private String id;
    private String flowId;
    private String ruleId;
    private String eventStage;
    private String agentRole;
    private String skillCode;
    private String skillName;
    private String skillKind;
    private String authorityCode;
    private Boolean required = Boolean.TRUE;
    private Boolean openClawSkill = Boolean.FALSE;
    private String description;
    private String legacyStatus;

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFlowId() { return flowId; }
    public void setFlowId(String flowId) { this.flowId = flowId; }
    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public String getEventStage() { return eventStage; }
    public void setEventStage(String eventStage) { this.eventStage = eventStage; }
    public String getAgentRole() { return agentRole; }
    public void setAgentRole(String agentRole) { this.agentRole = agentRole; }
    public String getSkillCode() { return skillCode; }
    public void setSkillCode(String skillCode) { this.skillCode = skillCode; }
    public String getCapabilityCode() { return skillCode; }
    public void setCapabilityCode(String capabilityCode) { this.skillCode = capabilityCode; }
    public String getSkillName() { return skillName; }
    public void setSkillName(String skillName) { this.skillName = skillName; }
    public String getCapabilityName() { return skillName; }
    public void setCapabilityName(String capabilityName) { this.skillName = capabilityName; }
    public String getSkillKind() { return skillKind; }
    public void setSkillKind(String skillKind) { this.skillKind = skillKind; }
    public String getCapabilityKind() { return skillKind; }
    public void setCapabilityKind(String capabilityKind) { this.skillKind = capabilityKind; }
    public String getAuthorityCode() { return authorityCode; }
    public void setAuthorityCode(String authorityCode) { this.authorityCode = authorityCode; }
    public Boolean getRequired() { return required; }
    public void setRequired(Boolean required) { this.required = required; }
    public Boolean getOpenClawSkill() { return openClawSkill; }
    public void setOpenClawSkill(Boolean openClawSkill) { this.openClawSkill = openClawSkill; }
    public Boolean getOpenClawCapability() { return openClawSkill; }
    public void setOpenClawCapability(Boolean openClawCapability) { this.openClawSkill = openClawCapability; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getLegacyStatus() { return legacyStatus; }
    public void setLegacyStatus(String legacyStatus) { this.legacyStatus = legacyStatus; }
}
