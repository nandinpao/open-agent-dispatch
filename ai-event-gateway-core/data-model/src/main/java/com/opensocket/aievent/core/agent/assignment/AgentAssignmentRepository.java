package com.opensocket.aievent.core.agent.assignment;

import java.util.List;
import java.util.Optional;

public interface AgentAssignmentRepository {
    DispatchTaskDefinition saveTaskDefinition(DispatchTaskDefinition definition);
    Optional<DispatchTaskDefinition> findTaskDefinitionById(String tenantId, String definitionId);
    Optional<DispatchTaskDefinition> findTaskDefinitionBySourceAndTask(String tenantId, String sourceSystem, String taskType);
    List<DispatchTaskDefinition> searchTaskDefinitions(String tenantId, String status, int limit);

    DispatchEventTaskMapping saveEventTaskMapping(DispatchEventTaskMapping mapping);
    Optional<DispatchEventTaskMapping> findEventTaskMappingById(String tenantId, String mappingId);
    Optional<DispatchEventTaskMapping> findBestEventTaskMapping(String tenantId, String sourceSystem, String objectType, String eventType, String errorCode, String message);
    List<DispatchEventTaskMapping> searchEventTaskMappings(String tenantId, String sourceSystem, Boolean active, int limit);

    AgentAssignmentProfile saveProfile(AgentAssignmentProfile profile);
    Optional<AgentAssignmentProfile> findProfileByCode(String tenantId, String profileCode);
    List<AgentAssignmentProfile> searchProfiles(String tenantId, String agentType, Boolean active, int limit);
    boolean deleteProfile(String tenantId, String profileCode);


    AgentCapabilityCatalog saveCapabilityCatalog(AgentCapabilityCatalog capability);
    Optional<AgentCapabilityCatalog> findCapabilityByCode(String tenantId, String capabilityCode);
    List<AgentCapabilityCatalog> searchCapabilities(String tenantId, String status, String taskDefinitionId, int limit);

    AssignmentProfileCapabilityBinding saveCapabilityBinding(AssignmentProfileCapabilityBinding binding);
    Optional<AssignmentProfileCapabilityBinding> findCapabilityBinding(String tenantId, String profileCode, String capabilityCode);
    List<AssignmentProfileCapabilityBinding> findCapabilityBindings(String tenantId, String profileCode, Boolean active);
    boolean deleteCapabilityBinding(String tenantId, String profileCode, String capabilityCode);





    SupplyProfile saveSupplyProfile(SupplyProfile profile);
    Optional<SupplyProfile> findSupplyProfileById(String tenantId, String supplyProfileId);
    Optional<SupplyProfile> findSupplyProfileByCode(String tenantId, String profileCode);
    List<SupplyProfile> searchSupplyProfiles(String tenantId, String agentId, String runtimeBindingId, String status, int limit);

    RuntimeResource saveRuntimeResource(RuntimeResource resource);
    Optional<RuntimeResource> findRuntimeResourceById(String tenantId, String runtimeId);
    Optional<RuntimeResource> findRuntimeResourceByCode(String tenantId, String runtimeCode);
    List<RuntimeResource> searchRuntimeResources(String tenantId, String status, String trustStatus, int limit);

    AgentRuntimeBinding saveRuntimeBinding(AgentRuntimeBinding binding);
    int updateActiveRuntimeBindingByTenantAndAgent(AgentRuntimeBinding binding);
    Optional<AgentRuntimeBinding> findRuntimeBinding(String bindingId);
    Optional<AgentRuntimeBinding> findActiveRuntimeBindingByTenantAndAgent(String tenantId, String agentId);
    Optional<AgentRuntimeBinding> findActiveRuntimeBindingByAgent(String agentId);
    List<AgentRuntimeBinding> findRuntimeBindingsByAgent(String agentId, String status);
    List<AgentRuntimeBinding> searchRuntimeBindings(String tenantId, String runtimeId, String status, int limit);

    RuntimeFeatureCatalog saveRuntimeFeatureCatalog(RuntimeFeatureCatalog feature);
    Optional<RuntimeFeatureCatalog> findRuntimeFeatureByCode(String tenantId, String featureCode);
    List<RuntimeFeatureCatalog> searchRuntimeFeatures(String tenantId, String status, int limit);

    AgentRuntimeFeatureObservation saveRuntimeFeatureObservation(AgentRuntimeFeatureObservation observation);
    List<AgentRuntimeFeatureObservation> findRuntimeFeatureObservationsByAgent(String agentId);

    AgentRuntimeFeatureTrust saveRuntimeFeatureTrust(AgentRuntimeFeatureTrust trust);
    Optional<AgentRuntimeFeatureTrust> findRuntimeFeatureTrust(String trustId);
    Optional<AgentRuntimeFeatureTrust> findRuntimeFeatureTrustByAgentAndFeature(String agentId, String featureCode);
    List<AgentRuntimeFeatureTrust> findRuntimeFeatureTrustsByAgent(String agentId);

    AgentCapabilityAssignment saveAgentCapabilityAssignment(AgentCapabilityAssignment assignment);
    Optional<AgentCapabilityAssignment> findAgentCapabilityAssignment(String assignmentId);
    Optional<AgentCapabilityAssignment> findAgentCapabilityAssignmentByAgentAndCapability(String agentId, String capabilityCode);
    List<AgentCapabilityAssignment> findAgentCapabilityAssignmentsByAgent(String agentId);
    boolean deleteAgentCapabilityAssignment(String agentId, String assignmentId);


    DispatchPolicy saveDispatchPolicy(DispatchPolicy policy);
    Optional<DispatchPolicy> findDispatchPolicyByCode(String tenantId, String policyCode);
    List<DispatchPolicy> searchDispatchPolicies(String tenantId, String status, int limit);
    DispatchPolicyScope saveDispatchPolicyScope(DispatchPolicyScope scope);
    List<DispatchPolicyScope> findDispatchPolicyScopes(String tenantId, String policyCode, Boolean active);
    DispatchPolicyRequiredCapability saveDispatchPolicyRequiredCapability(DispatchPolicyRequiredCapability rule);
    List<DispatchPolicyRequiredCapability> findDispatchPolicyRequiredCapabilities(String tenantId, String policyCode, Boolean blocking);
    DispatchPolicyRequiredRuntimeFeature saveDispatchPolicyRequiredRuntimeFeature(DispatchPolicyRequiredRuntimeFeature rule);
    List<DispatchPolicyRequiredRuntimeFeature> findDispatchPolicyRequiredRuntimeFeatures(String tenantId, String policyCode, Boolean blocking);
    DispatchPolicyQualityRule saveDispatchPolicyQualityRule(DispatchPolicyQualityRule rule);
    List<DispatchPolicyQualityRule> findDispatchPolicyQualityRules(String tenantId, String policyCode, Boolean blocking);
    DispatchPolicyScoringRule saveDispatchPolicyScoringRule(DispatchPolicyScoringRule rule);
    List<DispatchPolicyScoringRule> findDispatchPolicyScoringRules(String tenantId, String policyCode);


AgentQualityMetricsDaily saveAgentQualityMetricsDaily(AgentQualityMetricsDaily metrics);
List<AgentQualityMetricsDaily> findAgentQualityMetricsDaily(String tenantId, String agentId, int limit);
AgentQualityMetricsWindow saveAgentQualityMetricsWindow(AgentQualityMetricsWindow metrics);
List<AgentQualityMetricsWindow> findAgentQualityMetricsWindow(String tenantId, String agentId, String metricWindow, int limit);
RuntimeQualityMetricsDaily saveRuntimeQualityMetricsDaily(RuntimeQualityMetricsDaily metrics);
List<RuntimeQualityMetricsDaily> findRuntimeQualityMetricsDaily(String tenantId, String runtimeId, int limit);
SupplyProfileQualitySnapshot saveSupplyProfileQualitySnapshot(SupplyProfileQualitySnapshot snapshot);
Optional<SupplyProfileQualitySnapshot> findSupplyProfileQualitySnapshot(String tenantId, String profileCode, String metricWindow);
List<SupplyProfileQualitySnapshot> searchSupplyProfileQualitySnapshots(String tenantId, String agentId, String runtimeId, String metricWindow, int limit);

    AgentAssignmentProfilePolicyBinding savePolicyBinding(AgentAssignmentProfilePolicyBinding binding);
    Optional<AgentAssignmentProfilePolicyBinding> findPolicyBinding(String tenantId, String profileCode, String policyCode);
    List<AgentAssignmentProfilePolicyBinding> findPolicyBindings(String tenantId, String profileCode, Boolean active);
    boolean deletePolicyBinding(String tenantId, String profileCode, String policyCode);

    AgentQualification saveQualification(AgentQualification qualification);
    Optional<AgentQualification> findQualification(String qualificationId);
    boolean deleteQualification(String agentId, String qualificationId);
    Optional<AgentQualification> findQualificationByAgentAndProfile(String agentId, String profileCode);
    List<AgentQualification> findQualificationsByAgent(String agentId);
    List<AgentQualification> findQualificationsByProfile(String tenantId, String profileCode);

    String mode();
}
