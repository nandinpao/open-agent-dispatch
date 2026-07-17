package com.opensocket.aievent.database.persistence.agent.assignment.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.opensocket.aievent.database.persistence.agent.assignment.po.DispatchTaskDefinitionPo;
import com.opensocket.aievent.database.persistence.agent.assignment.po.DispatchEventTaskMappingPo;
import com.opensocket.aievent.database.persistence.agent.assignment.po.AgentAssignmentProfilePo;
import com.opensocket.aievent.database.persistence.agent.assignment.po.AgentCapabilityCatalogPo;
import com.opensocket.aievent.database.persistence.agent.assignment.po.AgentCapabilityAssignmentPo;
import com.opensocket.aievent.database.persistence.agent.assignment.po.AssignmentProfileCapabilityBindingPo;
import com.opensocket.aievent.database.persistence.agent.assignment.po.RuntimeFeatureCatalogPo;
import com.opensocket.aievent.database.persistence.agent.assignment.po.RuntimeResourcePo;
import com.opensocket.aievent.database.persistence.agent.assignment.po.AgentRuntimeBindingPo;
import com.opensocket.aievent.database.persistence.agent.assignment.po.SupplyProfilePo;
import com.opensocket.aievent.database.persistence.agent.assignment.po.AgentRuntimeFeatureObservationPo;
import com.opensocket.aievent.database.persistence.agent.assignment.po.AgentRuntimeFeatureTrustPo;
import com.opensocket.aievent.database.persistence.agent.assignment.po.DispatchPolicyPo;
import com.opensocket.aievent.database.persistence.agent.assignment.po.DispatchPolicyScopePo;
import com.opensocket.aievent.database.persistence.agent.assignment.po.DispatchPolicyRequiredCapabilityPo;
import com.opensocket.aievent.database.persistence.agent.assignment.po.DispatchPolicyRequiredRuntimeFeaturePo;
import com.opensocket.aievent.database.persistence.agent.assignment.po.DispatchPolicyQualityRulePo;
import com.opensocket.aievent.database.persistence.agent.assignment.po.DispatchPolicyScoringRulePo;
import com.opensocket.aievent.database.persistence.agent.assignment.po.AgentQualityMetricsDailyPo;
import com.opensocket.aievent.database.persistence.agent.assignment.po.AgentQualityMetricsWindowPo;
import com.opensocket.aievent.database.persistence.agent.assignment.po.RuntimeQualityMetricsDailyPo;
import com.opensocket.aievent.database.persistence.agent.assignment.po.SupplyProfileQualitySnapshotPo;
import com.opensocket.aievent.database.persistence.agent.assignment.po.AgentAssignmentProfilePolicyBindingPo;
import com.opensocket.aievent.database.persistence.agent.assignment.po.AgentQualificationPo;

@Mapper
public interface AgentAssignmentDao {
    int upsertTaskDefinition(@Param("definition") DispatchTaskDefinitionPo definition);
    DispatchTaskDefinitionPo findTaskDefinitionById(@Param("tenantId") String tenantId, @Param("definitionId") String definitionId);
    DispatchTaskDefinitionPo findTaskDefinitionBySourceAndTask(@Param("tenantId") String tenantId, @Param("sourceSystem") String sourceSystem, @Param("taskType") String taskType);
    List<DispatchTaskDefinitionPo> searchTaskDefinitions(@Param("tenantId") String tenantId, @Param("status") String status, @Param("limit") int limit);

    int upsertEventTaskMapping(@Param("mapping") DispatchEventTaskMappingPo mapping);
    DispatchEventTaskMappingPo findEventTaskMappingById(@Param("tenantId") String tenantId, @Param("mappingId") String mappingId);
    DispatchEventTaskMappingPo findBestEventTaskMapping(@Param("tenantId") String tenantId,
                                                        @Param("sourceSystem") String sourceSystem,
                                                        @Param("objectType") String objectType,
                                                        @Param("eventType") String eventType,
                                                        @Param("errorCode") String errorCode,
                                                        @Param("message") String message);
    List<DispatchEventTaskMappingPo> searchEventTaskMappings(@Param("tenantId") String tenantId,
                                                             @Param("sourceSystem") String sourceSystem,
                                                             @Param("active") Boolean active,
                                                             @Param("limit") int limit);

    int upsertProfile(@Param("profile") AgentAssignmentProfilePo profile);
    AgentAssignmentProfilePo findProfileByCode(@Param("tenantId") String tenantId, @Param("profileCode") String profileCode);
    List<AgentAssignmentProfilePo> searchProfiles(@Param("tenantId") String tenantId,
                                                  @Param("agentType") String agentType,
                                                  @Param("active") Boolean active,
                                                  @Param("limit") int limit);
    int deleteProfile(@Param("tenantId") String tenantId, @Param("profileCode") String profileCode);


    int upsertCapabilityCatalog(@Param("capability") AgentCapabilityCatalogPo capability);
    AgentCapabilityCatalogPo findCapabilityByCode(@Param("tenantId") String tenantId, @Param("capabilityCode") String capabilityCode);
    List<AgentCapabilityCatalogPo> searchCapabilities(@Param("tenantId") String tenantId, @Param("status") String status, @Param("taskDefinitionId") String taskDefinitionId, @Param("limit") int limit);

    int upsertCapabilityBinding(@Param("binding") AssignmentProfileCapabilityBindingPo binding);
    AssignmentProfileCapabilityBindingPo findCapabilityBinding(@Param("tenantId") String tenantId, @Param("profileCode") String profileCode, @Param("capabilityCode") String capabilityCode);
    List<AssignmentProfileCapabilityBindingPo> findCapabilityBindings(@Param("tenantId") String tenantId, @Param("profileCode") String profileCode, @Param("active") Boolean active);
    int deleteCapabilityBinding(@Param("tenantId") String tenantId, @Param("profileCode") String profileCode, @Param("capabilityCode") String capabilityCode);



    int upsertSupplyProfile(@Param("profile") SupplyProfilePo profile);
    SupplyProfilePo findSupplyProfileById(@Param("tenantId") String tenantId, @Param("supplyProfileId") String supplyProfileId);
    SupplyProfilePo findSupplyProfileByCode(@Param("tenantId") String tenantId, @Param("profileCode") String profileCode);
    List<SupplyProfilePo> searchSupplyProfiles(@Param("tenantId") String tenantId, @Param("agentId") String agentId, @Param("runtimeBindingId") String runtimeBindingId, @Param("status") String status, @Param("limit") int limit);

    int upsertRuntimeResource(@Param("resource") RuntimeResourcePo resource);
    RuntimeResourcePo findRuntimeResourceById(@Param("tenantId") String tenantId, @Param("runtimeId") String runtimeId);
    RuntimeResourcePo findRuntimeResourceByCode(@Param("tenantId") String tenantId, @Param("runtimeCode") String runtimeCode);
    List<RuntimeResourcePo> searchRuntimeResources(@Param("tenantId") String tenantId, @Param("status") String status, @Param("trustStatus") String trustStatus, @Param("limit") int limit);

    int upsertRuntimeBinding(@Param("binding") AgentRuntimeBindingPo binding);
    int updateActiveRuntimeBindingByTenantAndAgent(@Param("binding") AgentRuntimeBindingPo binding);
    AgentRuntimeBindingPo findRuntimeBinding(@Param("bindingId") String bindingId);
    AgentRuntimeBindingPo findActiveRuntimeBindingByTenantAndAgent(@Param("tenantId") String tenantId, @Param("agentId") String agentId);
    AgentRuntimeBindingPo findActiveRuntimeBindingByAgent(@Param("agentId") String agentId);
    List<AgentRuntimeBindingPo> findRuntimeBindingsByAgent(@Param("agentId") String agentId, @Param("status") String status);
    List<AgentRuntimeBindingPo> searchRuntimeBindings(@Param("tenantId") String tenantId, @Param("runtimeId") String runtimeId, @Param("status") String status, @Param("limit") int limit);

    int upsertRuntimeFeatureCatalog(@Param("feature") RuntimeFeatureCatalogPo feature);
    RuntimeFeatureCatalogPo findRuntimeFeatureByCode(@Param("tenantId") String tenantId, @Param("featureCode") String featureCode);
    List<RuntimeFeatureCatalogPo> searchRuntimeFeatures(@Param("tenantId") String tenantId, @Param("status") String status, @Param("limit") int limit);

    int insertRuntimeFeatureObservation(@Param("observation") AgentRuntimeFeatureObservationPo observation);
    List<AgentRuntimeFeatureObservationPo> findRuntimeFeatureObservationsByAgent(@Param("agentId") String agentId);

    int upsertRuntimeFeatureTrust(@Param("trust") AgentRuntimeFeatureTrustPo trust);
    AgentRuntimeFeatureTrustPo findRuntimeFeatureTrust(@Param("trustId") String trustId);
    AgentRuntimeFeatureTrustPo findRuntimeFeatureTrustByAgentAndFeature(@Param("agentId") String agentId, @Param("featureCode") String featureCode);
    List<AgentRuntimeFeatureTrustPo> findRuntimeFeatureTrustsByAgent(@Param("agentId") String agentId);

    int upsertCapabilityAssignment(@Param("assignment") AgentCapabilityAssignmentPo assignment);
    AgentCapabilityAssignmentPo findCapabilityAssignment(@Param("assignmentId") String assignmentId);
    AgentCapabilityAssignmentPo findCapabilityAssignmentByAgentAndCapability(@Param("agentId") String agentId, @Param("capabilityCode") String capabilityCode);
    List<AgentCapabilityAssignmentPo> findCapabilityAssignmentsByAgent(@Param("agentId") String agentId);
    int deleteCapabilityAssignment(@Param("agentId") String agentId, @Param("assignmentId") String assignmentId);


    int upsertDispatchPolicy(@Param("policy") DispatchPolicyPo policy);
    DispatchPolicyPo findDispatchPolicyByCode(@Param("tenantId") String tenantId, @Param("policyCode") String policyCode);
    List<DispatchPolicyPo> searchDispatchPolicies(@Param("tenantId") String tenantId, @Param("status") String status, @Param("limit") int limit);
    int upsertDispatchPolicyScope(@Param("scope") DispatchPolicyScopePo scope);
    List<DispatchPolicyScopePo> findDispatchPolicyScopes(@Param("tenantId") String tenantId, @Param("policyCode") String policyCode, @Param("active") Boolean active);
    int upsertDispatchPolicyRequiredCapability(@Param("rule") DispatchPolicyRequiredCapabilityPo rule);
    List<DispatchPolicyRequiredCapabilityPo> findDispatchPolicyRequiredCapabilities(@Param("tenantId") String tenantId, @Param("policyCode") String policyCode, @Param("blocking") Boolean blocking);
    int upsertDispatchPolicyRequiredRuntimeFeature(@Param("rule") DispatchPolicyRequiredRuntimeFeaturePo rule);
    List<DispatchPolicyRequiredRuntimeFeaturePo> findDispatchPolicyRequiredRuntimeFeatures(@Param("tenantId") String tenantId, @Param("policyCode") String policyCode, @Param("blocking") Boolean blocking);
    int upsertDispatchPolicyQualityRule(@Param("rule") DispatchPolicyQualityRulePo rule);
    List<DispatchPolicyQualityRulePo> findDispatchPolicyQualityRules(@Param("tenantId") String tenantId, @Param("policyCode") String policyCode, @Param("blocking") Boolean blocking);
    int upsertDispatchPolicyScoringRule(@Param("rule") DispatchPolicyScoringRulePo rule);
    List<DispatchPolicyScoringRulePo> findDispatchPolicyScoringRules(@Param("tenantId") String tenantId, @Param("policyCode") String policyCode);

    int upsertPolicyBinding(@Param("binding") AgentAssignmentProfilePolicyBindingPo binding);

    int upsertAgentQualityMetricsDaily(@Param("metrics") AgentQualityMetricsDailyPo metrics);
    List<AgentQualityMetricsDailyPo> findAgentQualityMetricsDaily(@Param("tenantId") String tenantId, @Param("agentId") String agentId, @Param("limit") int limit);
    int upsertAgentQualityMetricsWindow(@Param("metrics") AgentQualityMetricsWindowPo metrics);
    List<AgentQualityMetricsWindowPo> findAgentQualityMetricsWindow(@Param("tenantId") String tenantId, @Param("agentId") String agentId, @Param("metricWindow") String metricWindow, @Param("limit") int limit);
    int upsertRuntimeQualityMetricsDaily(@Param("metrics") RuntimeQualityMetricsDailyPo metrics);
    List<RuntimeQualityMetricsDailyPo> findRuntimeQualityMetricsDaily(@Param("tenantId") String tenantId, @Param("runtimeId") String runtimeId, @Param("limit") int limit);
    int upsertSupplyProfileQualitySnapshot(@Param("snapshot") SupplyProfileQualitySnapshotPo snapshot);
    SupplyProfileQualitySnapshotPo findSupplyProfileQualitySnapshot(@Param("tenantId") String tenantId, @Param("profileCode") String profileCode, @Param("metricWindow") String metricWindow);
    List<SupplyProfileQualitySnapshotPo> searchSupplyProfileQualitySnapshots(@Param("tenantId") String tenantId, @Param("agentId") String agentId, @Param("runtimeId") String runtimeId, @Param("metricWindow") String metricWindow, @Param("limit") int limit);

    AgentAssignmentProfilePolicyBindingPo findPolicyBinding(@Param("tenantId") String tenantId, @Param("profileCode") String profileCode, @Param("policyCode") String policyCode);
    List<AgentAssignmentProfilePolicyBindingPo> findPolicyBindings(@Param("tenantId") String tenantId, @Param("profileCode") String profileCode, @Param("active") Boolean active);
    int deletePolicyBinding(@Param("tenantId") String tenantId, @Param("profileCode") String profileCode, @Param("policyCode") String policyCode);

    int upsertQualification(@Param("qualification") AgentQualificationPo qualification);
    AgentQualificationPo findQualification(@Param("qualificationId") String qualificationId);
    int deleteQualification(@Param("agentId") String agentId, @Param("qualificationId") String qualificationId);
    AgentQualificationPo findQualificationByAgentAndProfile(@Param("agentId") String agentId, @Param("profileCode") String profileCode);
    List<AgentQualificationPo> findQualificationsByAgent(@Param("agentId") String agentId);
    List<AgentQualificationPo> findQualificationsByProfile(@Param("tenantId") String tenantId, @Param("profileCode") String profileCode);
}
