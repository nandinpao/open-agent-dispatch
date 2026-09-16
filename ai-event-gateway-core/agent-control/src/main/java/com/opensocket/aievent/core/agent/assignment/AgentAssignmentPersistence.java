package com.opensocket.aievent.core.agent.assignment;

import java.util.List;
import java.util.Optional;

/**
 * Persistence boundary used by the Agent assignment application service.
 *
 * <p>The repository contract remains unchanged. This package-private adapter
 * prevents command orchestration from depending directly on the persistence
 * port and gives later R3 tranches one place for transactional write semantics.</p>
 */
final class AgentAssignmentPersistence {
    private final AgentAssignmentRepository repository;

    AgentAssignmentPersistence(AgentAssignmentRepository repository) {
        this.repository = repository;
    }

    List<DispatchPolicy> searchDispatchPolicies(String tenantId, String status, int limit) { return repository.searchDispatchPolicies(tenantId, status, limit); }
    List<DispatchPolicyScope> findDispatchPolicyScopes(String tenantId, String policyCode, Boolean active) { return repository.findDispatchPolicyScopes(tenantId, policyCode, active); }
    List<DispatchPolicyRequiredCapability> findDispatchPolicyRequiredCapabilities(String tenantId, String policyCode, Boolean blocking) { return repository.findDispatchPolicyRequiredCapabilities(tenantId, policyCode, blocking); }
    Optional<DispatchPolicy> findDispatchPolicyByCode(String tenantId, String policyCode) { return repository.findDispatchPolicyByCode(tenantId, policyCode); }
    DispatchPolicy saveDispatchPolicy(DispatchPolicy value) { return repository.saveDispatchPolicy(value); }
    DispatchPolicyScope saveDispatchPolicyScope(DispatchPolicyScope value) { return repository.saveDispatchPolicyScope(value); }
    DispatchPolicyRequiredCapability saveDispatchPolicyRequiredCapability(DispatchPolicyRequiredCapability value) { return repository.saveDispatchPolicyRequiredCapability(value); }
    DispatchPolicyRequiredRuntimeFeature saveDispatchPolicyRequiredRuntimeFeature(DispatchPolicyRequiredRuntimeFeature value) { return repository.saveDispatchPolicyRequiredRuntimeFeature(value); }
    DispatchPolicyQualityRule saveDispatchPolicyQualityRule(DispatchPolicyQualityRule value) { return repository.saveDispatchPolicyQualityRule(value); }
    DispatchPolicyScoringRule saveDispatchPolicyScoringRule(DispatchPolicyScoringRule value) { return repository.saveDispatchPolicyScoringRule(value); }

    List<AgentQualityMetricsDaily> findAgentQualityMetricsDaily(String tenantId, String agentId, int limit) { return repository.findAgentQualityMetricsDaily(tenantId, agentId, limit); }
    List<AgentQualityMetricsWindow> findAgentQualityMetricsWindow(String tenantId, String agentId, String window, int limit) { return repository.findAgentQualityMetricsWindow(tenantId, agentId, window, limit); }
    AgentQualityMetricsWindow saveAgentQualityMetricsWindow(AgentQualityMetricsWindow value) { return repository.saveAgentQualityMetricsWindow(value); }
    List<RuntimeQualityMetricsDaily> findRuntimeQualityMetricsDaily(String tenantId, String runtimeId, int limit) { return repository.findRuntimeQualityMetricsDaily(tenantId, runtimeId, limit); }
    List<SupplyProfileQualitySnapshot> searchSupplyProfileQualitySnapshots(String tenantId, String agentId, String runtimeId, String window, int limit) { return repository.searchSupplyProfileQualitySnapshots(tenantId, agentId, runtimeId, window, limit); }
    Optional<SupplyProfileQualitySnapshot> findSupplyProfileQualitySnapshot(String tenantId, String profileCode, String window) { return repository.findSupplyProfileQualitySnapshot(tenantId, profileCode, window); }

    List<AgentCapabilityCatalog> searchCapabilities(String tenantId, String status, String taskDefinitionId, int limit) { return repository.searchCapabilities(tenantId, status, taskDefinitionId, limit); }
    AgentCapabilityCatalog saveCapabilityCatalog(AgentCapabilityCatalog value) { return repository.saveCapabilityCatalog(value); }
    Optional<AgentCapabilityCatalog> findCapabilityByCode(String tenantId, String capabilityCode) { return repository.findCapabilityByCode(tenantId, capabilityCode); }
    Optional<AgentCapabilityCatalog> findCanonicalCapabilityByCode(String tenantId, String capabilityCode) { return repository.findCanonicalCapabilityByCode(tenantId, capabilityCode); }
    List<AgentCapabilityCatalog> searchCanonicalCapabilities(String tenantId, String status, int limit) { return repository.searchCanonicalCapabilities(tenantId, status, limit); }
    List<AgentCapabilityAssignment> findAgentCapabilityAssignmentsByAgent(String agentId) { return repository.findAgentCapabilityAssignmentsByAgent(agentId); }
    Optional<AgentCapabilityAssignment> findAgentCapabilityAssignmentByAgentAndCapability(String agentId, String capabilityCode) { return repository.findAgentCapabilityAssignmentByAgentAndCapability(agentId, capabilityCode); }
    AgentCapabilityAssignment saveAgentCapabilityAssignment(AgentCapabilityAssignment value) { return repository.saveAgentCapabilityAssignment(value); }
    Optional<AgentCapabilityAssignment> findAgentCapabilityAssignment(String assignmentId) { return repository.findAgentCapabilityAssignment(assignmentId); }
    boolean deleteAgentCapabilityAssignment(String agentId, String assignmentId) { return repository.deleteAgentCapabilityAssignment(agentId, assignmentId); }

    List<RuntimeResource> searchRuntimeResources(String tenantId, String status, String trustStatus, int limit) { return repository.searchRuntimeResources(tenantId, status, trustStatus, limit); }
    Optional<RuntimeResource> findRuntimeResourceById(String tenantId, String runtimeId) { return repository.findRuntimeResourceById(tenantId, runtimeId); }
    Optional<RuntimeResource> findRuntimeResourceByCode(String tenantId, String runtimeCode) { return repository.findRuntimeResourceByCode(tenantId, runtimeCode); }
    RuntimeResource saveRuntimeResource(RuntimeResource value) { return repository.saveRuntimeResource(value); }
    List<AgentRuntimeBinding> findRuntimeBindingsByAgent(String agentId, String status) { return repository.findRuntimeBindingsByAgent(agentId, status); }
    Optional<AgentRuntimeBinding> findActiveRuntimeBindingByAgent(String agentId) { return repository.findActiveRuntimeBindingByAgent(agentId); }
    Optional<AgentRuntimeBinding> findActiveRuntimeBindingByTenantAndAgent(String tenantId, String agentId) { return repository.findActiveRuntimeBindingByTenantAndAgent(tenantId, agentId); }
    AgentRuntimeBinding saveRuntimeBinding(AgentRuntimeBinding value) { return repository.saveRuntimeBinding(value); }
    Optional<AgentRuntimeBinding> findRuntimeBinding(String bindingId) { return repository.findRuntimeBinding(bindingId); }

    List<RuntimeFeatureCatalog> searchRuntimeFeatures(String tenantId, String status, int limit) { return repository.searchRuntimeFeatures(tenantId, status, limit); }
    RuntimeFeatureCatalog saveRuntimeFeatureCatalog(RuntimeFeatureCatalog value) { return repository.saveRuntimeFeatureCatalog(value); }
    Optional<RuntimeFeatureCatalog> findRuntimeFeatureByCode(String tenantId, String featureCode) { return repository.findRuntimeFeatureByCode(tenantId, featureCode); }
    List<AgentRuntimeFeatureObservation> findRuntimeFeatureObservationsByAgent(String agentId) { return repository.findRuntimeFeatureObservationsByAgent(agentId); }
    List<AgentRuntimeFeatureTrust> findRuntimeFeatureTrustsByAgent(String agentId) { return repository.findRuntimeFeatureTrustsByAgent(agentId); }
    Optional<AgentRuntimeFeatureTrust> findRuntimeFeatureTrustByAgentAndFeature(String agentId, String featureCode) { return repository.findRuntimeFeatureTrustByAgentAndFeature(agentId, featureCode); }
    AgentRuntimeFeatureTrust saveRuntimeFeatureTrust(AgentRuntimeFeatureTrust value) { return repository.saveRuntimeFeatureTrust(value); }
    Optional<AgentRuntimeFeatureTrust> findRuntimeFeatureTrust(String trustId) { return repository.findRuntimeFeatureTrust(trustId); }
}
