package com.opensocket.aievent.core.agent.eligibility;

import java.time.OffsetDateTime;
import java.util.List;

import com.opensocket.aievent.core.agent.AgentRuntimeDescriptor;
import com.opensocket.aievent.core.agent.assignment.AgentQualification;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentDispatchEligibility {
    private String agentId;
    private String taskId;
    private String taskType;
    private boolean eligible;
    private String dispatchStatus = "BLOCKED";
    private String connectionStatus = "UNKNOWN";
    private List<String> approvedProfiles = List.of();
    private List<String> requiredProfiles = List.of();
    private List<AgentQualification> qualifications = List.of();
    private AgentRuntimeDescriptor runtimeDescriptor;
    private List<DispatchEligibilityCheck> checks = List.of();
    private List<DispatchNextAction> nextActions = List.of();
    private OffsetDateTime generatedAt;

    public void setApprovedProfiles(List<String> approvedProfiles) {
        this.approvedProfiles = approvedProfiles == null ? List.of() : List.copyOf(approvedProfiles);
    }

    public void setRequiredProfiles(List<String> requiredProfiles) {
        this.requiredProfiles = requiredProfiles == null ? List.of() : List.copyOf(requiredProfiles);
    }

    public void setQualifications(List<AgentQualification> qualifications) {
        this.qualifications = qualifications == null ? List.of() : List.copyOf(qualifications);
    }

    public void setChecks(List<DispatchEligibilityCheck> checks) {
        this.checks = checks == null ? List.of() : List.copyOf(checks);
    }

    public void setNextActions(List<DispatchNextAction> nextActions) {
        this.nextActions = nextActions == null ? List.of() : List.copyOf(nextActions);
    }
}
