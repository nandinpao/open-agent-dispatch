package com.opensocket.aievent.core.dispatch.flow;

import java.util.ArrayList;
import java.util.List;

/** Candidate Agent row returned by P2 Flow Rule dry-run. */
public class DispatchFlowCandidateAgentView {
    private String agentId;
    private String agentName;
    private String eventStage;
    private String agentRole;
    private String assignmentStatus;
    private String approvalStatus;
    private String readinessStatus;
    private String runtimeStatus;
    private String skillGrantStatus;
    private Boolean assignmentActive = false;
    private Boolean approvalReady = false;
    private Boolean readinessReady = false;
    private Boolean requestedSkillGranted = false;
    private Boolean dispatchable = false;
    private List<String> blockingReasons = new ArrayList<>();

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
    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }
    public String getReadinessStatus() { return readinessStatus; }
    public void setReadinessStatus(String readinessStatus) { this.readinessStatus = readinessStatus; }
    public String getRuntimeStatus() { return runtimeStatus; }
    public void setRuntimeStatus(String runtimeStatus) { this.runtimeStatus = runtimeStatus; }
    public String getSkillGrantStatus() { return skillGrantStatus; }
    public void setSkillGrantStatus(String skillGrantStatus) { this.skillGrantStatus = skillGrantStatus; }
    public Boolean getAssignmentActive() { return assignmentActive; }
    public void setAssignmentActive(Boolean assignmentActive) { this.assignmentActive = assignmentActive; }
    public Boolean getApprovalReady() { return approvalReady; }
    public void setApprovalReady(Boolean approvalReady) { this.approvalReady = approvalReady; }
    public Boolean getReadinessReady() { return readinessReady; }
    public void setReadinessReady(Boolean readinessReady) { this.readinessReady = readinessReady; }
    public Boolean getRequestedSkillGranted() { return requestedSkillGranted; }
    public void setRequestedSkillGranted(Boolean requestedSkillGranted) { this.requestedSkillGranted = requestedSkillGranted; }
    public Boolean getDispatchable() { return dispatchable; }
    public void setDispatchable(Boolean dispatchable) { this.dispatchable = dispatchable; }
    public List<String> getBlockingReasons() { return blockingReasons; }
    public void setBlockingReasons(List<String> blockingReasons) { this.blockingReasons = blockingReasons == null ? new ArrayList<>() : new ArrayList<>(blockingReasons); }
}
