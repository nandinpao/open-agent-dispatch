package com.opensocket.aievent.core.agent.assignment;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignmentProfileRelationshipMap {
    private String tenantId;
    private String profileCode;
    private String profileName;
    private boolean active;
    private int policyVersion;
    private List<String> sourceSystems = List.of();
    private List<String> taskTypes = List.of();
    private List<AgentAssignmentProfilePolicyBinding> policyBindings = List.of();
    private List<AssignmentProfileCapabilityBinding> capabilityBindings = List.of();
    private List<AgentQualification> qualifications = List.of();
    private Map<String, Integer> qualificationStatusCounts = new LinkedHashMap<>();
    private int assignedAgentCount;
    private int approvedAgentCount;
    private int pendingAgentCount;
    private int suspendedAgentCount;
    private int revokedAgentCount;
    private int expiredAgentCount;
    private List<String> relationshipSteps = List.of();
    private List<String> warnings = List.of();
    private OffsetDateTime generatedAt;

    public void setSourceSystems(List<String> sourceSystems) {
        this.sourceSystems = sourceSystems == null ? List.of() : List.copyOf(sourceSystems);
    }

    public void setTaskTypes(List<String> taskTypes) {
        this.taskTypes = taskTypes == null ? List.of() : List.copyOf(taskTypes);
    }

    public void setPolicyBindings(List<AgentAssignmentProfilePolicyBinding> policyBindings) {
        this.policyBindings = policyBindings == null ? List.of() : List.copyOf(policyBindings);
    }

    public void setCapabilityBindings(List<AssignmentProfileCapabilityBinding> capabilityBindings) {
        this.capabilityBindings = capabilityBindings == null ? List.of() : List.copyOf(capabilityBindings);
    }

    public void setQualifications(List<AgentQualification> qualifications) {
        this.qualifications = qualifications == null ? List.of() : List.copyOf(qualifications);
    }

    public void setQualificationStatusCounts(Map<String, Integer> qualificationStatusCounts) {
        this.qualificationStatusCounts = qualificationStatusCounts == null ? new LinkedHashMap<>() : new LinkedHashMap<>(qualificationStatusCounts);
    }

    public void setRelationshipSteps(List<String> relationshipSteps) {
        this.relationshipSteps = relationshipSteps == null ? List.of() : List.copyOf(relationshipSteps);
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
