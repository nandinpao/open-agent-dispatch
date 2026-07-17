package com.opensocket.aievent.core.routing.governance.eligibility;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.agent.assignment.AgentCapabilityAssignment;
import com.opensocket.aievent.core.agent.governance.AgentProfile;
import com.opensocket.aievent.core.routing.governance.TaskRequirementEvidence;
import com.opensocket.aievent.core.task.TaskRecord;

/** Fully hydrated input for the Stage 8 direct-dispatch Agent eligibility checks. */
public class DispatchEligibilityShadowContext {
    private TaskRecord task;
    private TaskRequirementEvidence requirement;
    private AgentSnapshot runtime;
    private AgentProfile agentProfile;
    private List<AgentCapabilityAssignment> capabilityAssignments = new ArrayList<>();
    private boolean legacyCandidate;
    private boolean legacyEligible;
    private Integer legacyScore;
    private OffsetDateTime evaluatedAt;

    public String agentId() {
        if (runtime != null && runtime.getAgentId() != null) return runtime.getAgentId();
        if (agentProfile != null) return agentProfile.getAgentId();
        return null;
    }

    public String taskType() {
        return task == null ? null : task.getEffectiveTaskTypeCode();
    }

    public TaskRecord getTask() { return task; }
    public void setTask(TaskRecord task) { this.task = task; }
    public TaskRequirementEvidence getRequirement() { return requirement; }
    public void setRequirement(TaskRequirementEvidence requirement) { this.requirement = requirement; }
    public AgentSnapshot getRuntime() { return runtime; }
    public void setRuntime(AgentSnapshot runtime) { this.runtime = runtime; }
    public AgentProfile getAgentProfile() { return agentProfile; }
    public void setAgentProfile(AgentProfile agentProfile) { this.agentProfile = agentProfile; }
    public List<AgentCapabilityAssignment> getCapabilityAssignments() { return new ArrayList<>(capabilityAssignments); }
    public void setCapabilityAssignments(List<AgentCapabilityAssignment> capabilityAssignments) { this.capabilityAssignments = capabilityAssignments == null ? new ArrayList<>() : new ArrayList<>(capabilityAssignments); }
    public boolean isLegacyCandidate() { return legacyCandidate; }
    public void setLegacyCandidate(boolean legacyCandidate) { this.legacyCandidate = legacyCandidate; }
    public boolean isLegacyEligible() { return legacyEligible; }
    public void setLegacyEligible(boolean legacyEligible) { this.legacyEligible = legacyEligible; }
    public Integer getLegacyScore() { return legacyScore; }
    public void setLegacyScore(Integer legacyScore) { this.legacyScore = legacyScore; }
    public OffsetDateTime getEvaluatedAt() { return evaluatedAt; }
    public void setEvaluatedAt(OffsetDateTime evaluatedAt) { this.evaluatedAt = evaluatedAt; }
}
