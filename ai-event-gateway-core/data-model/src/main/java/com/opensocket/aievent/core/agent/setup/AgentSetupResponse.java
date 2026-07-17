package com.opensocket.aievent.core.agent.setup;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.opensocket.aievent.core.agent.assignment.AgentCapabilityAssignment;
import com.opensocket.aievent.core.agent.assignment.AgentCapabilityCatalog;
import com.opensocket.aievent.core.agent.assignment.AgentRuntimeBinding;
import com.opensocket.aievent.core.agent.assignment.DispatchPolicy;
import com.opensocket.aievent.core.agent.assignment.RuntimeResource;
import com.opensocket.aievent.core.agent.assignment.SupplyProfile;
import com.opensocket.aievent.core.agent.governance.AgentEnrollmentRequest;
import com.opensocket.aievent.core.agent.governance.AgentProfile;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentSetupResponse {
    private String tenantId;
    private String agentId;
    private String setupStatus;
    private String setupMode = "FIRST_AGENT_SETUP";
    private AgentEnrollmentRequest enrollment;
    private AgentProfile agentProfile;
    private RuntimeResource runtimeResource;
    private AgentRuntimeBinding runtimeBinding;
    private SupplyProfile supplyProfile;
    private DispatchPolicy dispatchPolicy;
    private List<AgentCapabilityCatalog> capabilityCatalog = new ArrayList<>();
    private List<AgentCapabilityAssignment> capabilityAssignments = new ArrayList<>();
    private List<AgentSetupReadinessCheck> readinessChecks = new ArrayList<>();
    private AgentSetupStartCommand startCommand;
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private OffsetDateTime createdAt;

    public void setCapabilityCatalog(List<AgentCapabilityCatalog> capabilityCatalog) {
        this.capabilityCatalog = capabilityCatalog == null ? new ArrayList<>() : new ArrayList<>(capabilityCatalog);
    }

    public void setCapabilityAssignments(List<AgentCapabilityAssignment> capabilityAssignments) {
        this.capabilityAssignments = capabilityAssignments == null ? new ArrayList<>() : new ArrayList<>(capabilityAssignments);
    }

    public void setReadinessChecks(List<AgentSetupReadinessCheck> readinessChecks) {
        this.readinessChecks = readinessChecks == null ? new ArrayList<>() : new ArrayList<>(readinessChecks);
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }
}
