package com.opensocket.aievent.core.agent.contract;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.opensocket.aievent.core.agent.assignment.AgentAssignmentProfile;
import com.opensocket.aievent.core.agent.assignment.AgentAssignmentProfilePolicyBinding;
import com.opensocket.aievent.core.agent.assignment.AgentCapabilityAssignment;
import com.opensocket.aievent.core.agent.assignment.AgentCapabilityCatalog;
import com.opensocket.aievent.core.agent.assignment.AgentQualification;
import com.opensocket.aievent.core.agent.assignment.AssignmentProfileCapabilityBinding;
import com.opensocket.aievent.core.agent.assignment.DispatchTaskDefinition;
import com.opensocket.aievent.core.agent.assignment.DispatchPolicy;
import com.opensocket.aievent.core.agent.skill.AgentSkillDefinition;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DispatchContractBootstrapResponse {
    private String tenantId;
    private String sourceSystem;
    private String taskType;
    private DispatchTaskDefinition taskDefinition;
    private AgentCapabilityCatalog capability;
    private AgentAssignmentProfile profile;
    private AgentSkillDefinition policyDefinition;
    private DispatchPolicy dispatchPolicy;
    private AgentAssignmentProfilePolicyBinding policyBinding;
    private AssignmentProfileCapabilityBinding capabilityBinding;
    private AgentQualification qualification;
    private AgentCapabilityAssignment capabilityAssignment;
    private DispatchContractReadinessResponse readiness;
    private DispatchContractChainInspectionResponse chainInspection;
    private List<String> createdOrUpdated = new ArrayList<>();
    private Map<String, Object> diagnostics = new LinkedHashMap<>();
    private OffsetDateTime generatedAt;

    public void setCreatedOrUpdated(List<String> createdOrUpdated) {
        this.createdOrUpdated = createdOrUpdated == null ? new ArrayList<>() : new ArrayList<>(createdOrUpdated);
    }

    public void setDiagnostics(Map<String, Object> diagnostics) {
        this.diagnostics = diagnostics == null ? new LinkedHashMap<>() : new LinkedHashMap<>(diagnostics);
    }
}
