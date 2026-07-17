package com.opensocket.aievent.core.dispatch.flow;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class DispatchFlowAgentView {
    private String tenantId;
    private String id;
    private String flowId;
    private String agentId;
    private String agentName;
    private String eventStage;
    private String agentRole;
    private String assignmentStatus = "DRAFT";
    private String runtimeStatus = "UNKNOWN";
    private String approvalStatus = "PENDING";
    private Integer skillCoverageTotal = 0;
    private Integer skillCoverageMatched = 0;
    private List<String> missingSkills = new ArrayList<>();
    private List<String> missingAuthorities = new ArrayList<>();
    private String readinessStatus = "NOT_READY";
    private String legacyStatus = "FLOW_OWNED_AGENT_ASSIGNMENT";
    private OffsetDateTime updatedAt;

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFlowId() { return flowId; }
    public void setFlowId(String flowId) { this.flowId = flowId; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public String getEventStage() { return eventStage; }
    public void setEventStage(String eventStage) { this.eventStage = eventStage; }
    public String getAgentRole() { return agentRole; }
    public void setAgentRole(String agentRole) { this.agentRole = agentRole; }
    public String getAssignmentStatus() { return assignmentStatus; }
    public void setAssignmentStatus(String assignmentStatus) { this.assignmentStatus = assignmentStatus; }
    public String getRuntimeStatus() { return runtimeStatus; }
    public void setRuntimeStatus(String runtimeStatus) { this.runtimeStatus = runtimeStatus; }
    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }
    public Integer getSkillCoverageTotal() { return skillCoverageTotal; }
    public void setSkillCoverageTotal(Integer skillCoverageTotal) { this.skillCoverageTotal = skillCoverageTotal; }
    public Integer getCapabilityCoverageTotal() { return skillCoverageTotal; }
    public void setCapabilityCoverageTotal(Integer capabilityCoverageTotal) { this.skillCoverageTotal = capabilityCoverageTotal; }
    public Integer getSkillCoverageMatched() { return skillCoverageMatched; }
    public void setSkillCoverageMatched(Integer skillCoverageMatched) { this.skillCoverageMatched = skillCoverageMatched; }
    public Integer getCapabilityCoverageMatched() { return skillCoverageMatched; }
    public void setCapabilityCoverageMatched(Integer capabilityCoverageMatched) { this.skillCoverageMatched = capabilityCoverageMatched; }
    public List<String> getMissingSkills() { return missingSkills; }
    public void setMissingSkills(List<String> missingSkills) { this.missingSkills = missingSkills == null ? new ArrayList<>() : new ArrayList<>(missingSkills); }
    public List<String> getMissingCapabilities() { return missingSkills; }
    public void setMissingCapabilities(List<String> missingCapabilities) { this.missingSkills = missingCapabilities == null ? new ArrayList<>() : new ArrayList<>(missingCapabilities); }
    public List<String> getMissingAuthorities() { return missingAuthorities; }
    public void setMissingAuthorities(List<String> missingAuthorities) { this.missingAuthorities = missingAuthorities == null ? new ArrayList<>() : new ArrayList<>(missingAuthorities); }
    public String getReadinessStatus() { return readinessStatus; }
    public void setReadinessStatus(String readinessStatus) { this.readinessStatus = readinessStatus; }
    public String getLegacyStatus() { return legacyStatus; }
    public void setLegacyStatus(String legacyStatus) { this.legacyStatus = legacyStatus; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
