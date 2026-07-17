package com.opensocket.aievent.core.agent.assignment;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignmentProfileImpactPreview {
    private String tenantId;
    private String profileCode;
    private String action;
    private boolean allowed;
    private String severity = "INFO";
    private String summary;
    private List<String> blockingReasons = List.of();
    private List<String> warnings = List.of();
    private Map<String, Integer> affectedCounts = new LinkedHashMap<>();
    private List<AgentAssignmentProfilePolicyBinding> affectedPolicyBindings = List.of();
    private List<AssignmentProfileCapabilityBinding> affectedCapabilityBindings = List.of();
    private List<AgentQualification> affectedQualifications = List.of();
    private OffsetDateTime generatedAt;

    public void setBlockingReasons(List<String> blockingReasons) {
        this.blockingReasons = blockingReasons == null ? List.of() : List.copyOf(blockingReasons);
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public void setAffectedCounts(Map<String, Integer> affectedCounts) {
        this.affectedCounts = affectedCounts == null ? new LinkedHashMap<>() : new LinkedHashMap<>(affectedCounts);
    }

    public void setAffectedPolicyBindings(List<AgentAssignmentProfilePolicyBinding> affectedPolicyBindings) {
        this.affectedPolicyBindings = affectedPolicyBindings == null ? List.of() : List.copyOf(affectedPolicyBindings);
    }

    public void setAffectedCapabilityBindings(List<AssignmentProfileCapabilityBinding> affectedCapabilityBindings) {
        this.affectedCapabilityBindings = affectedCapabilityBindings == null ? List.of() : List.copyOf(affectedCapabilityBindings);
    }

    public void setAffectedQualifications(List<AgentQualification> affectedQualifications) {
        this.affectedQualifications = affectedQualifications == null ? List.of() : List.copyOf(affectedQualifications);
    }
}
