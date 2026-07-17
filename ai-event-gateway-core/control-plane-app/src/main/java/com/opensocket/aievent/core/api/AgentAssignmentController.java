package com.opensocket.aievent.core.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.opensocket.aievent.core.agent.assignment.AgentAssignmentService;
import com.opensocket.aievent.core.agent.assignment.AgentCapabilityAssignment;
import com.opensocket.aievent.core.agent.assignment.AgentCapabilityCatalog;
import com.opensocket.aievent.core.agent.assignment.AgentCapabilityCommand;
import com.opensocket.aievent.core.agent.assignment.AgentRuntimeBinding;
import com.opensocket.aievent.core.agent.assignment.AgentRuntimeFeatureCommand;
import com.opensocket.aievent.core.agent.assignment.AgentRuntimeFeatureObservation;
import com.opensocket.aievent.core.agent.assignment.AgentRuntimeFeatureTrust;
import com.opensocket.aievent.core.agent.assignment.DispatchPolicy;
import com.opensocket.aievent.core.agent.assignment.DispatchPolicyQualityRule;
import com.opensocket.aievent.core.agent.assignment.DispatchPolicyRequiredCapability;
import com.opensocket.aievent.core.agent.assignment.DispatchPolicyRequiredRuntimeFeature;
import com.opensocket.aievent.core.agent.assignment.DispatchPolicyScope;
import com.opensocket.aievent.core.agent.assignment.DispatchPolicyScoringRule;
import com.opensocket.aievent.core.agent.assignment.RuntimeFeatureCatalog;
import com.opensocket.aievent.core.agent.assignment.RuntimeResource;
import com.opensocket.aievent.core.agent.contract.DispatchContractBootstrapRequest;
import com.opensocket.aievent.core.agent.contract.DispatchContractBootstrapResponse;
import com.opensocket.aievent.core.agent.contract.DispatchContractChainInspectionRequest;
import com.opensocket.aievent.core.agent.contract.DispatchContractChainInspectionResponse;
import com.opensocket.aievent.core.agent.contract.DispatchContractReadinessRequest;
import com.opensocket.aievent.core.agent.contract.DispatchContractReadinessResponse;
import com.opensocket.aievent.core.agent.contract.DispatchSourceSystemOption;

@RestController
@RequestMapping("/admin")
public class AgentAssignmentController {
    private final AgentAssignmentService service;

    public AgentAssignmentController(AgentAssignmentService service) {
        this.service = service;
    }

    @GetMapping("/dispatch-policies")
    public List<DispatchPolicy> dispatchPolicies(@RequestParam(required = false) String tenantId,
                                                 @RequestParam(required = false) String status,
                                                 @RequestParam(defaultValue = "500") int limit) {
        return service.searchDispatchPolicies(tenantId, status, limit);
    }

    @GetMapping("/dispatch-policies/{policyCode}")
    public DispatchPolicy dispatchPolicy(@PathVariable String policyCode,
                                         @RequestParam(required = false) String tenantId) {
        try {
            return service.getDispatchPolicy(tenantId, policyCode);
        } catch (IllegalArgumentException ex) {
            throw new StandardApiException(StandardApiErrorCode.NOT_FOUND, ex.getMessage());
        }
    }

    @PutMapping("/dispatch-policies/{policyCode}")
    public DispatchPolicy upsertDispatchPolicy(@PathVariable String policyCode,
                                               @RequestBody(required = false) DispatchPolicy request,
                                               @RequestParam(required = false) String tenantId) {
        try {
            DispatchPolicy body = request == null ? new DispatchPolicy() : request;
            body.setTenantId(tenantId == null || tenantId.isBlank() ? body.getTenantId() : tenantId);
            body.setPolicyCode(policyCode);
            return service.upsertDispatchPolicy(body);
        } catch (IllegalArgumentException ex) {
            throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST, ex.getMessage());
        }
    }

    @PostMapping("/dispatch-policies/{policyCode}/scopes")
    public DispatchPolicyScope upsertDispatchPolicyScope(@PathVariable String policyCode,
                                                         @RequestBody(required = false) DispatchPolicyScope request) {
        try {
            return service.upsertDispatchPolicyScope(policyCode, request == null ? new DispatchPolicyScope() : request);
        } catch (IllegalArgumentException ex) {
            throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST, ex.getMessage());
        }
    }

    @PostMapping("/dispatch-policies/{policyCode}/required-capabilities")
    public DispatchPolicyRequiredCapability upsertDispatchPolicyRequiredCapability(@PathVariable String policyCode,
                                                                                  @RequestBody(required = false) DispatchPolicyRequiredCapability request) {
        try {
            return service.upsertDispatchPolicyRequiredCapability(policyCode, request == null ? new DispatchPolicyRequiredCapability() : request);
        } catch (IllegalArgumentException ex) {
            throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST, ex.getMessage());
        }
    }

    @PostMapping("/dispatch-policies/{policyCode}/required-runtime-features")
    public DispatchPolicyRequiredRuntimeFeature upsertDispatchPolicyRequiredRuntimeFeature(@PathVariable String policyCode,
                                                                                          @RequestBody(required = false) DispatchPolicyRequiredRuntimeFeature request) {
        try {
            return service.upsertDispatchPolicyRequiredRuntimeFeature(policyCode, request == null ? new DispatchPolicyRequiredRuntimeFeature() : request);
        } catch (IllegalArgumentException ex) {
            throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST, ex.getMessage());
        }
    }

    @PostMapping("/dispatch-policies/{policyCode}/quality-rules")
    public DispatchPolicyQualityRule upsertDispatchPolicyQualityRule(@PathVariable String policyCode,
                                                                     @RequestBody(required = false) DispatchPolicyQualityRule request) {
        try {
            return service.upsertDispatchPolicyQualityRule(policyCode, request == null ? new DispatchPolicyQualityRule() : request);
        } catch (IllegalArgumentException ex) {
            throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST, ex.getMessage());
        }
    }

    @PostMapping("/dispatch-policies/{policyCode}/scoring-rules")
    public DispatchPolicyScoringRule upsertDispatchPolicyScoringRule(@PathVariable String policyCode,
                                                                     @RequestBody(required = false) DispatchPolicyScoringRule request) {
        try {
            return service.upsertDispatchPolicyScoringRule(policyCode, request == null ? new DispatchPolicyScoringRule() : request);
        } catch (IllegalArgumentException ex) {
            throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST, ex.getMessage());
        }
    }

    @GetMapping("/capabilities")
    public List<AgentCapabilityCatalog> capabilities(@RequestParam(required = false) String tenantId,
                                                     @RequestParam(required = false) String status,
                                                     @RequestParam(required = false) String taskDefinitionId,
                                                     @RequestParam(defaultValue = "500") int limit) {
        return service.searchCapabilities(tenantId, status, taskDefinitionId, limit);
    }

    @PutMapping("/capabilities/{capabilityCode}")
    public AgentCapabilityCatalog upsertCapability(@PathVariable String capabilityCode,
                                                   @RequestBody(required = false) AgentCapabilityCatalog request,
                                                   @RequestParam(required = false) String tenantId) {
        try {
            AgentCapabilityCatalog body = request == null ? new AgentCapabilityCatalog() : request;
            body.setTenantId(tenantId == null || tenantId.isBlank() ? body.getTenantId() : tenantId);
            body.setCapabilityCode(capabilityCode);
            return service.upsertCapability(body);
        } catch (IllegalArgumentException ex) {
            throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST, ex.getMessage());
        }
    }

    @GetMapping("/agents/{agentId}/capabilities")
    public List<AgentCapabilityAssignment> agentCapabilities(@PathVariable String agentId) {
        return service.findAgentCapabilities(agentId);
    }

    @PostMapping("/agents/{agentId}/capabilities")
    public AgentCapabilityAssignment requestAgentCapability(@PathVariable String agentId,
                                                            @RequestBody(required = false) AgentCapabilityCommand request) {
        return service.requestAgentCapability(agentId, request == null ? new AgentCapabilityCommand() : request);
    }

    @PostMapping("/agents/{agentId}/capabilities/{assignmentId}/approve")
    public AgentCapabilityAssignment approveAgentCapability(@PathVariable String agentId,
                                                            @PathVariable String assignmentId,
                                                            @RequestBody(required = false) AgentCapabilityCommand request) {
        return service.approveAgentCapability(agentId, assignmentId, request == null ? new AgentCapabilityCommand() : request);
    }

    @PostMapping("/agents/{agentId}/capabilities/{assignmentId}/suspend")
    public AgentCapabilityAssignment suspendAgentCapability(@PathVariable String agentId,
                                                            @PathVariable String assignmentId,
                                                            @RequestBody(required = false) AgentCapabilityCommand request) {
        return service.suspendAgentCapability(agentId, assignmentId, request == null ? new AgentCapabilityCommand() : request);
    }

    @PostMapping("/agents/{agentId}/capabilities/{assignmentId}/resume")
    public AgentCapabilityAssignment resumeAgentCapability(@PathVariable String agentId,
                                                           @PathVariable String assignmentId,
                                                           @RequestBody(required = false) AgentCapabilityCommand request) {
        return service.resumeAgentCapability(agentId, assignmentId, request == null ? new AgentCapabilityCommand() : request);
    }

    @PostMapping("/agents/{agentId}/capabilities/{assignmentId}/revoke")
    public AgentCapabilityAssignment revokeAgentCapability(@PathVariable String agentId,
                                                           @PathVariable String assignmentId,
                                                           @RequestBody(required = false) AgentCapabilityCommand request) {
        return service.revokeAgentCapability(agentId, assignmentId, request == null ? new AgentCapabilityCommand() : request);
    }

    @PostMapping("/agents/{agentId}/capabilities/{assignmentId}/remove")
    public AgentCapabilityAssignment removeAgentCapability(@PathVariable String agentId,
                                                           @PathVariable String assignmentId,
                                                           @RequestBody(required = false) AgentCapabilityCommand request) {
        return service.removeAgentCapability(agentId, assignmentId, request == null ? new AgentCapabilityCommand() : request);
    }

    @GetMapping("/runtime-resources")
    public List<RuntimeResource> runtimeResources(@RequestParam(required = false) String tenantId,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(required = false) String trustStatus,
                                                  @RequestParam(defaultValue = "500") int limit) {
        return service.searchRuntimeResources(tenantId, status, trustStatus, limit);
    }

    @GetMapping("/runtime-resources/{runtimeId}")
    public RuntimeResource runtimeResource(@PathVariable String runtimeId,
                                           @RequestParam(required = false) String tenantId) {
        try {
            return service.getRuntimeResource(tenantId, runtimeId);
        } catch (IllegalArgumentException ex) {
            throw new StandardApiException(StandardApiErrorCode.NOT_FOUND, ex.getMessage());
        }
    }

    @PutMapping("/runtime-resources/{runtimeId}")
    public RuntimeResource upsertRuntimeResource(@PathVariable String runtimeId,
                                                 @RequestBody(required = false) RuntimeResource request,
                                                 @RequestParam(required = false) String tenantId) {
        RuntimeResource body = request == null ? new RuntimeResource() : request;
        body.setTenantId(tenantId == null || tenantId.isBlank() ? body.getTenantId() : tenantId);
        body.setRuntimeId(runtimeId);
        return service.upsertRuntimeResource(body);
    }

    @GetMapping("/agents/{agentId}/runtime-bindings")
    public List<AgentRuntimeBinding> runtimeBindings(@PathVariable String agentId,
                                                     @RequestParam(required = false) String status) {
        return service.findRuntimeBindingsByAgent(agentId, status);
    }

    @PostMapping("/agents/{agentId}/runtime-bindings")
    public AgentRuntimeBinding createRuntimeBinding(@PathVariable String agentId,
                                                    @RequestBody(required = false) AgentRuntimeBinding request) {
        return service.upsertRuntimeBinding(agentId, request == null ? new AgentRuntimeBinding() : request);
    }

    @PutMapping("/agents/{agentId}/runtime-bindings/{bindingId}")
    public AgentRuntimeBinding upsertRuntimeBinding(@PathVariable String agentId,
                                                    @PathVariable String bindingId,
                                                    @RequestBody(required = false) AgentRuntimeBinding request) {
        AgentRuntimeBinding body = request == null ? new AgentRuntimeBinding() : request;
        body.setBindingId(bindingId);
        return service.upsertRuntimeBinding(agentId, body);
    }

    @PostMapping("/agents/{agentId}/runtime-bindings/{bindingId}/{targetStatus}")
    public AgentRuntimeBinding transitionRuntimeBinding(@PathVariable String agentId,
                                                        @PathVariable String bindingId,
                                                        @PathVariable String targetStatus,
                                                        @RequestBody(required = false) AgentRuntimeBinding request) {
        return service.transitionRuntimeBinding(agentId, bindingId, targetStatus, request == null ? new AgentRuntimeBinding() : request);
    }

    @GetMapping("/runtime-features")
    public List<RuntimeFeatureCatalog> runtimeFeatures(@RequestParam(required = false) String tenantId,
                                                       @RequestParam(required = false) String status,
                                                       @RequestParam(defaultValue = "500") int limit) {
        return service.searchRuntimeFeatures(tenantId, status, limit);
    }

    @PutMapping("/runtime-features/{featureCode}")
    public RuntimeFeatureCatalog upsertRuntimeFeature(@PathVariable String featureCode,
                                                      @RequestBody(required = false) RuntimeFeatureCatalog request,
                                                      @RequestParam(required = false) String tenantId) {
        RuntimeFeatureCatalog body = request == null ? new RuntimeFeatureCatalog() : request;
        body.setTenantId(tenantId == null || tenantId.isBlank() ? body.getTenantId() : tenantId);
        body.setFeatureCode(featureCode);
        return service.upsertRuntimeFeature(body);
    }

    @GetMapping("/agents/{agentId}/runtime-features/observations")
    public List<AgentRuntimeFeatureObservation> runtimeFeatureObservations(@PathVariable String agentId) {
        return service.findRuntimeFeatureObservations(agentId);
    }

    @GetMapping("/agents/{agentId}/runtime-features/trust")
    public List<AgentRuntimeFeatureTrust> runtimeFeatureTrusts(@PathVariable String agentId) {
        return service.findRuntimeFeatureTrusts(agentId);
    }

    @PostMapping("/agents/{agentId}/runtime-features/trust")
    public AgentRuntimeFeatureTrust observeRuntimeFeature(@PathVariable String agentId,
                                                          @RequestBody(required = false) AgentRuntimeFeatureCommand request) {
        return service.observeRuntimeFeature(agentId, request == null ? new AgentRuntimeFeatureCommand() : request);
    }

    @PostMapping("/agents/{agentId}/runtime-features/trust/{trustId}/verify")
    public AgentRuntimeFeatureTrust verifyRuntimeFeature(@PathVariable String agentId,
                                                         @PathVariable String trustId,
                                                         @RequestBody(required = false) AgentRuntimeFeatureCommand request) {
        return service.verifyRuntimeFeature(agentId, trustId, request == null ? new AgentRuntimeFeatureCommand() : request);
    }

    @PostMapping("/agents/{agentId}/runtime-features/trust/{trustId}/trust")
    public AgentRuntimeFeatureTrust trustRuntimeFeature(@PathVariable String agentId,
                                                        @PathVariable String trustId,
                                                        @RequestBody(required = false) AgentRuntimeFeatureCommand request) {
        return service.trustRuntimeFeature(agentId, trustId, request == null ? new AgentRuntimeFeatureCommand() : request);
    }

    @PostMapping("/agents/{agentId}/runtime-features/trust/{trustId}/suspend")
    public AgentRuntimeFeatureTrust suspendRuntimeFeatureTrust(@PathVariable String agentId,
                                                               @PathVariable String trustId,
                                                               @RequestBody(required = false) AgentRuntimeFeatureCommand request) {
        return service.suspendRuntimeFeatureTrust(agentId, trustId, request == null ? new AgentRuntimeFeatureCommand() : request);
    }

    @PostMapping("/agents/{agentId}/runtime-features/trust/{trustId}/resume")
    public AgentRuntimeFeatureTrust resumeRuntimeFeatureTrust(@PathVariable String agentId,
                                                              @PathVariable String trustId,
                                                              @RequestBody(required = false) AgentRuntimeFeatureCommand request) {
        return service.resumeRuntimeFeatureTrust(agentId, trustId, request == null ? new AgentRuntimeFeatureCommand() : request);
    }

    @PostMapping("/agents/{agentId}/runtime-features/trust/{trustId}/revoke")
    public AgentRuntimeFeatureTrust revokeRuntimeFeatureTrust(@PathVariable String agentId,
                                                              @PathVariable String trustId,
                                                              @RequestBody(required = false) AgentRuntimeFeatureCommand request) {
        return service.revokeRuntimeFeatureTrust(agentId, trustId, request == null ? new AgentRuntimeFeatureCommand() : request);
    }

    @PostMapping("/dispatch-contract/bootstrap")
    public DispatchContractBootstrapResponse bootstrapDispatchContract(@RequestBody(required = false) DispatchContractBootstrapRequest request) {
        return service.bootstrapDispatchContract(request == null ? new DispatchContractBootstrapRequest() : request);
    }

    @PostMapping("/dispatch-contract/chain-inspection")
    public DispatchContractChainInspectionResponse inspectDispatchContractChain(@RequestBody(required = false) DispatchContractChainInspectionRequest request) {
        return service.inspectDispatchContractChain(request == null ? new DispatchContractChainInspectionRequest() : request);
    }

    @PostMapping("/dispatch-contract/readiness")
    public DispatchContractReadinessResponse dispatchContractReadiness(@RequestBody(required = false) DispatchContractReadinessRequest request) {
        return service.dispatchContractReadiness(request == null ? new DispatchContractReadinessRequest() : request);
    }

    @GetMapping("/dispatch-contract/source-systems")
    public List<DispatchSourceSystemOption> sourceSystemsFromContracts(@RequestParam(required = false) String tenantId) {
        return service.sourceSystemsFromContracts(tenantId);
    }
}
