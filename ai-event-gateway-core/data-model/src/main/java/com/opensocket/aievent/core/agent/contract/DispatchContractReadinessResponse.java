package com.opensocket.aievent.core.agent.contract;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.opensocket.aievent.core.agent.assignment.AgentAssignmentProfile;
import com.opensocket.aievent.core.agent.assignment.DispatchTaskDefinition;
import com.opensocket.aievent.core.agent.assignment.DispatchPolicy;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DispatchContractReadinessResponse {
    private String tenantId;
    private String sourceSystem;
    private String taskType;
    private String agentId;
    private boolean ready;
    private String status;
    private String summary;
    private String firstBlockingCode;
    private String firstBlockingReason;
    private DispatchTaskDefinition taskDefinition;
    private List<AgentAssignmentProfile> profiles = new ArrayList<>();
    private List<String> requiredProfiles = new ArrayList<>();
    private List<String> requiredCapabilities = new ArrayList<>();
    private List<String> requiredPolicyCodes = new ArrayList<>();
    private List<String> dispatchRulePolicyCodes = new ArrayList<>();
    private List<DispatchPolicy> dispatchPolicies = new ArrayList<>();
    private List<DispatchContractReadinessCheck> checks = new ArrayList<>();
    private Map<String, Object> diagnostics = new LinkedHashMap<>();
    private OffsetDateTime generatedAt;

    public void setProfiles(List<AgentAssignmentProfile> profiles) {
        this.profiles = profiles == null ? new ArrayList<>() : new ArrayList<>(profiles);
    }

    public void setRequiredProfiles(List<String> requiredProfiles) {
        this.requiredProfiles = requiredProfiles == null ? new ArrayList<>() : new ArrayList<>(requiredProfiles);
    }

    public void setRequiredCapabilities(List<String> requiredCapabilities) {
        this.requiredCapabilities = requiredCapabilities == null ? new ArrayList<>() : new ArrayList<>(requiredCapabilities);
    }

    public void setRequiredPolicyCodes(List<String> requiredPolicyCodes) {
        this.requiredPolicyCodes = requiredPolicyCodes == null ? new ArrayList<>() : new ArrayList<>(requiredPolicyCodes);
    }

    public void setDispatchRulePolicyCodes(List<String> dispatchRulePolicyCodes) {
        this.dispatchRulePolicyCodes = dispatchRulePolicyCodes == null ? new ArrayList<>() : new ArrayList<>(dispatchRulePolicyCodes);
    }

    public void setDispatchPolicies(List<DispatchPolicy> dispatchPolicies) {
        this.dispatchPolicies = dispatchPolicies == null ? new ArrayList<>() : new ArrayList<>(dispatchPolicies);
    }

    public void setChecks(List<DispatchContractReadinessCheck> checks) {
        this.checks = checks == null ? new ArrayList<>() : new ArrayList<>(checks);
    }

    public void setDiagnostics(Map<String, Object> diagnostics) {
        this.diagnostics = diagnostics == null ? new LinkedHashMap<>() : new LinkedHashMap<>(diagnostics);
    }
}
