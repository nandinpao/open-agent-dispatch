package com.opensocket.aievent.database.persistence.agent.assignment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;

import com.opensocket.aievent.core.agent.assignment.DispatchTaskDefinition;
import com.opensocket.aievent.core.agent.assignment.DispatchEventTaskMapping;
import com.opensocket.aievent.core.agent.assignment.AgentAssignmentProfile;
import com.opensocket.aievent.core.agent.assignment.AgentCapabilityCatalog;
import com.opensocket.aievent.core.agent.assignment.AgentCapabilityAssignment;
import com.opensocket.aievent.core.agent.assignment.AssignmentProfileCapabilityBinding;
import com.opensocket.aievent.core.agent.assignment.RuntimeFeatureCatalog;
import com.opensocket.aievent.core.agent.assignment.RuntimeResource;
import com.opensocket.aievent.core.agent.assignment.AgentRuntimeBinding;
import com.opensocket.aievent.core.agent.assignment.SupplyProfile;
import com.opensocket.aievent.core.agent.assignment.AgentRuntimeFeatureObservation;
import com.opensocket.aievent.core.agent.assignment.AgentRuntimeFeatureTrust;
import com.opensocket.aievent.core.agent.assignment.DispatchPolicy;
import com.opensocket.aievent.core.agent.assignment.DispatchPolicyScope;
import com.opensocket.aievent.core.agent.assignment.DispatchPolicyRequiredCapability;
import com.opensocket.aievent.core.agent.assignment.DispatchPolicyRequiredRuntimeFeature;
import com.opensocket.aievent.core.agent.assignment.DispatchPolicyQualityRule;
import com.opensocket.aievent.core.agent.assignment.DispatchPolicyScoringRule;
import com.opensocket.aievent.core.agent.assignment.AgentQualityMetricsDaily;
import com.opensocket.aievent.core.agent.assignment.AgentQualityMetricsWindow;
import com.opensocket.aievent.core.agent.assignment.RuntimeQualityMetricsDaily;
import com.opensocket.aievent.core.agent.assignment.SupplyProfileQualitySnapshot;
import com.opensocket.aievent.core.agent.assignment.AgentAssignmentProfilePolicyBinding;
import com.opensocket.aievent.core.agent.assignment.AgentAssignmentRepository;
import com.opensocket.aievent.core.agent.assignment.AgentQualification;
import com.opensocket.aievent.database.persistence.agent.assignment.converter.AgentAssignmentPersistenceConverter;
import com.opensocket.aievent.database.persistence.agent.assignment.dao.AgentAssignmentDao;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;

@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix = "agent-assignment", name = "store", havingValue = "MYBATIS")
public class MybatisAgentAssignmentRepository implements AgentAssignmentRepository {
    private final AgentAssignmentDao dao;
    private final AgentAssignmentPersistenceConverter converter;

    public MybatisAgentAssignmentRepository(AgentAssignmentDao dao, AgentAssignmentPersistenceConverter converter) {
        this.dao = dao;
        this.converter = converter;
    }


    @Override
    public DispatchTaskDefinition saveTaskDefinition(DispatchTaskDefinition definition) {
        dao.upsertTaskDefinition(converter.toTaskDefinitionPo(definition));
        return findTaskDefinitionById(definition.getTenantId(), definition.getDefinitionId()).orElse(definition);
    }

    @Override
    public Optional<DispatchTaskDefinition> findTaskDefinitionById(String tenantId, String definitionId) {
        return Optional.ofNullable(dao.findTaskDefinitionById(defaultTenant(tenantId), definitionId)).map(converter::toTaskDefinition);
    }

    @Override
    public Optional<DispatchTaskDefinition> findTaskDefinitionBySourceAndTask(String tenantId, String sourceSystem, String taskType) {
        return Optional.ofNullable(dao.findTaskDefinitionBySourceAndTask(defaultTenant(tenantId), sourceSystem, taskType)).map(converter::toTaskDefinition);
    }

    @Override
    public List<DispatchTaskDefinition> searchTaskDefinitions(String tenantId, String status, int limit) {
        return dao.searchTaskDefinitions(defaultTenant(tenantId), status, Math.max(1, limit)).stream().map(converter::toTaskDefinition).toList();
    }


    @Override
    public DispatchEventTaskMapping saveEventTaskMapping(DispatchEventTaskMapping mapping) {
        dao.upsertEventTaskMapping(converter.toEventTaskMappingPo(mapping));
        return findEventTaskMappingById(mapping.getTenantId(), mapping.getMappingId()).orElse(mapping);
    }

    @Override
    public Optional<DispatchEventTaskMapping> findEventTaskMappingById(String tenantId, String mappingId) {
        return Optional.ofNullable(dao.findEventTaskMappingById(defaultTenant(tenantId), mappingId)).map(converter::toEventTaskMapping);
    }

    @Override
    public Optional<DispatchEventTaskMapping> findBestEventTaskMapping(String tenantId, String sourceSystem, String objectType, String eventType, String errorCode, String message) {
        return Optional.ofNullable(dao.findBestEventTaskMapping(defaultTenant(tenantId), sourceSystem, objectType, eventType, errorCode, message)).map(converter::toEventTaskMapping);
    }

    @Override
    public List<DispatchEventTaskMapping> searchEventTaskMappings(String tenantId, String sourceSystem, Boolean active, int limit) {
        return dao.searchEventTaskMappings(defaultTenant(tenantId), sourceSystem, active, Math.max(1, limit)).stream().map(converter::toEventTaskMapping).toList();
    }

    @Override
    public AgentAssignmentProfile saveProfile(AgentAssignmentProfile profile) {
        dao.upsertProfile(converter.toProfilePo(profile));
        return findProfileByCode(profile.getTenantId(), profile.getProfileCode()).orElse(profile);
    }

    @Override
    public Optional<AgentAssignmentProfile> findProfileByCode(String tenantId, String profileCode) {
        return Optional.ofNullable(dao.findProfileByCode(defaultTenant(tenantId), profileCode))
                .map(converter::toProfile)
                .map(this::attachPolicyBindings);
    }

    @Override
    public List<AgentAssignmentProfile> searchProfiles(String tenantId, String agentType, Boolean active, int limit) {
        return dao.searchProfiles(defaultTenant(tenantId), agentType, active, Math.max(1, limit)).stream()
                .map(converter::toProfile)
                .map(this::attachPolicyBindings)
                .toList();
    }

    @Override
    public boolean deleteProfile(String tenantId, String profileCode) {
        return dao.deleteProfile(defaultTenant(tenantId), profileCode) > 0;
    }



    @Override
    public AgentCapabilityCatalog saveCapabilityCatalog(AgentCapabilityCatalog capability) {
        dao.upsertCapabilityCatalog(converter.toCapabilityCatalogPo(capability));
        return findCapabilityByCode(capability.getTenantId(), capability.getCapabilityCode()).orElse(capability);
    }

    @Override
    public Optional<AgentCapabilityCatalog> findCapabilityByCode(String tenantId, String capabilityCode) {
        return Optional.ofNullable(dao.findCapabilityByCode(defaultTenant(tenantId), capabilityCode)).map(converter::toCapabilityCatalog);
    }

    @Override
    public List<AgentCapabilityCatalog> searchCapabilities(String tenantId, String status, String taskDefinitionId, int limit) {
        return dao.searchCapabilities(defaultTenant(tenantId), status, taskDefinitionId, Math.max(1, limit)).stream().map(converter::toCapabilityCatalog).toList();
    }

    @Override
    public AssignmentProfileCapabilityBinding saveCapabilityBinding(AssignmentProfileCapabilityBinding binding) {
        dao.upsertCapabilityBinding(converter.toCapabilityBindingPo(binding));
        return findCapabilityBinding(binding.getTenantId(), binding.getProfileCode(), binding.getCapabilityCode()).orElse(binding);
    }

    @Override
    public Optional<AssignmentProfileCapabilityBinding> findCapabilityBinding(String tenantId, String profileCode, String capabilityCode) {
        return Optional.ofNullable(dao.findCapabilityBinding(defaultTenant(tenantId), profileCode, capabilityCode)).map(converter::toCapabilityBinding);
    }

    @Override
    public List<AssignmentProfileCapabilityBinding> findCapabilityBindings(String tenantId, String profileCode, Boolean active) {
        return dao.findCapabilityBindings(defaultTenant(tenantId), profileCode, active).stream().map(converter::toCapabilityBinding).toList();
    }

    @Override
    public boolean deleteCapabilityBinding(String tenantId, String profileCode, String capabilityCode) {
        return dao.deleteCapabilityBinding(defaultTenant(tenantId), profileCode, capabilityCode) > 0;
    }





    @Override
    public SupplyProfile saveSupplyProfile(SupplyProfile profile) {
        dao.upsertSupplyProfile(converter.toSupplyProfilePo(profile));
        return findSupplyProfileById(profile.getTenantId(), profile.getSupplyProfileId()).orElse(profile);
    }

    @Override
    public Optional<SupplyProfile> findSupplyProfileById(String tenantId, String supplyProfileId) {
        return Optional.ofNullable(dao.findSupplyProfileById(defaultTenant(tenantId), supplyProfileId)).map(converter::toSupplyProfile);
    }

    @Override
    public Optional<SupplyProfile> findSupplyProfileByCode(String tenantId, String profileCode) {
        return Optional.ofNullable(dao.findSupplyProfileByCode(defaultTenant(tenantId), profileCode)).map(converter::toSupplyProfile);
    }

    @Override
    public List<SupplyProfile> searchSupplyProfiles(String tenantId, String agentId, String runtimeBindingId, String status, int limit) {
        return dao.searchSupplyProfiles(defaultTenant(tenantId), agentId, runtimeBindingId, status, Math.max(1, limit)).stream()
                .map(converter::toSupplyProfile)
                .toList();
    }


    @Override
    public RuntimeResource saveRuntimeResource(RuntimeResource resource) {
        dao.upsertRuntimeResource(converter.toRuntimeResourcePo(resource));
        return findRuntimeResourceById(resource.getTenantId(), resource.getRuntimeId()).orElse(resource);
    }

    @Override
    public Optional<RuntimeResource> findRuntimeResourceById(String tenantId, String runtimeId) {
        return Optional.ofNullable(dao.findRuntimeResourceById(defaultTenant(tenantId), runtimeId)).map(converter::toRuntimeResource);
    }

    @Override
    public Optional<RuntimeResource> findRuntimeResourceByCode(String tenantId, String runtimeCode) {
        return Optional.ofNullable(dao.findRuntimeResourceByCode(defaultTenant(tenantId), runtimeCode)).map(converter::toRuntimeResource);
    }

    @Override
    public List<RuntimeResource> searchRuntimeResources(String tenantId, String status, String trustStatus, int limit) {
        return dao.searchRuntimeResources(defaultTenant(tenantId), status, trustStatus, Math.max(1, limit)).stream().map(converter::toRuntimeResource).toList();
    }

    @Override
    public AgentRuntimeBinding saveRuntimeBinding(AgentRuntimeBinding binding) {
        var po = converter.toRuntimeBindingPo(binding);
        boolean activeTenantAgentBinding = "ACTIVE".equalsIgnoreCase(binding.getBindingStatus())
                && binding.getTenantId() != null && !binding.getTenantId().isBlank()
                && binding.getAgentId() != null && !binding.getAgentId().isBlank();
        boolean bindingIdAlreadyExists = binding.getBindingId() != null
                && !binding.getBindingId().isBlank()
                && dao.findRuntimeBinding(binding.getBindingId()) != null;
        if (activeTenantAgentBinding && !bindingIdAlreadyExists) {
            int updated = dao.updateActiveRuntimeBindingByTenantAndAgent(po);
            if (updated == 0) {
                try {
                    dao.upsertRuntimeBinding(po);
                } catch (DuplicateKeyException conflict) {
                    updated = dao.updateActiveRuntimeBindingByTenantAndAgent(po);
                    if (updated == 0) {
                        throw conflict;
                    }
                }
            }
            return findActiveRuntimeBindingByTenantAndAgent(binding.getTenantId(), binding.getAgentId())
                    .orElseGet(() -> findRuntimeBinding(binding.getBindingId()).orElse(binding));
        }
        dao.upsertRuntimeBinding(po);
        return findRuntimeBinding(binding.getBindingId()).orElse(binding);
    }

    @Override
    public int updateActiveRuntimeBindingByTenantAndAgent(AgentRuntimeBinding binding) {
        return dao.updateActiveRuntimeBindingByTenantAndAgent(converter.toRuntimeBindingPo(binding));
    }

    @Override
    public Optional<AgentRuntimeBinding> findRuntimeBinding(String bindingId) {
        return Optional.ofNullable(dao.findRuntimeBinding(bindingId)).map(converter::toRuntimeBinding);
    }

    @Override
    public Optional<AgentRuntimeBinding> findActiveRuntimeBindingByTenantAndAgent(String tenantId, String agentId) {
        return Optional.ofNullable(dao.findActiveRuntimeBindingByTenantAndAgent(defaultTenant(tenantId), agentId))
                .map(converter::toRuntimeBinding);
    }

    @Override
    public Optional<AgentRuntimeBinding> findActiveRuntimeBindingByAgent(String agentId) {
        return Optional.ofNullable(dao.findActiveRuntimeBindingByAgent(agentId)).map(converter::toRuntimeBinding);
    }

    @Override
    public List<AgentRuntimeBinding> findRuntimeBindingsByAgent(String agentId, String status) {
        return dao.findRuntimeBindingsByAgent(agentId, status).stream().map(converter::toRuntimeBinding).toList();
    }

    @Override
    public List<AgentRuntimeBinding> searchRuntimeBindings(String tenantId, String runtimeId, String status, int limit) {
        return dao.searchRuntimeBindings(defaultTenant(tenantId), runtimeId, status, Math.max(1, limit)).stream().map(converter::toRuntimeBinding).toList();
    }

    @Override
    public RuntimeFeatureCatalog saveRuntimeFeatureCatalog(RuntimeFeatureCatalog feature) {
        dao.upsertRuntimeFeatureCatalog(converter.toRuntimeFeatureCatalogPo(feature));
        return findRuntimeFeatureByCode(feature.getTenantId(), feature.getFeatureCode()).orElse(feature);
    }

    @Override
    public Optional<RuntimeFeatureCatalog> findRuntimeFeatureByCode(String tenantId, String featureCode) {
        return Optional.ofNullable(dao.findRuntimeFeatureByCode(defaultTenant(tenantId), featureCode)).map(converter::toRuntimeFeatureCatalog);
    }

    @Override
    public List<RuntimeFeatureCatalog> searchRuntimeFeatures(String tenantId, String status, int limit) {
        return dao.searchRuntimeFeatures(defaultTenant(tenantId), status, Math.max(1, limit)).stream().map(converter::toRuntimeFeatureCatalog).toList();
    }

    @Override
    public AgentRuntimeFeatureObservation saveRuntimeFeatureObservation(AgentRuntimeFeatureObservation observation) {
        dao.insertRuntimeFeatureObservation(converter.toRuntimeFeatureObservationPo(observation));
        return observation;
    }

    @Override
    public List<AgentRuntimeFeatureObservation> findRuntimeFeatureObservationsByAgent(String agentId) {
        return dao.findRuntimeFeatureObservationsByAgent(agentId).stream().map(converter::toRuntimeFeatureObservation).toList();
    }

    @Override
    public AgentRuntimeFeatureTrust saveRuntimeFeatureTrust(AgentRuntimeFeatureTrust trust) {
        dao.upsertRuntimeFeatureTrust(converter.toRuntimeFeatureTrustPo(trust));
        return findRuntimeFeatureTrust(trust.getTrustId()).orElse(trust);
    }

    @Override
    public Optional<AgentRuntimeFeatureTrust> findRuntimeFeatureTrust(String trustId) {
        return Optional.ofNullable(dao.findRuntimeFeatureTrust(trustId)).map(converter::toRuntimeFeatureTrust);
    }

    @Override
    public Optional<AgentRuntimeFeatureTrust> findRuntimeFeatureTrustByAgentAndFeature(String agentId, String featureCode) {
        return Optional.ofNullable(dao.findRuntimeFeatureTrustByAgentAndFeature(agentId, featureCode)).map(converter::toRuntimeFeatureTrust);
    }

    @Override
    public List<AgentRuntimeFeatureTrust> findRuntimeFeatureTrustsByAgent(String agentId) {
        return dao.findRuntimeFeatureTrustsByAgent(agentId).stream().map(converter::toRuntimeFeatureTrust).toList();
    }

    @Override
    public AgentCapabilityAssignment saveAgentCapabilityAssignment(AgentCapabilityAssignment assignment) {
        dao.upsertCapabilityAssignment(converter.toCapabilityAssignmentPo(assignment));
        return findAgentCapabilityAssignment(assignment.getAssignmentId()).orElse(assignment);
    }

    @Override
    public Optional<AgentCapabilityAssignment> findAgentCapabilityAssignment(String assignmentId) {
        return Optional.ofNullable(dao.findCapabilityAssignment(assignmentId)).map(converter::toCapabilityAssignment);
    }

    @Override
    public Optional<AgentCapabilityAssignment> findAgentCapabilityAssignmentByAgentAndCapability(String agentId, String capabilityCode) {
        return Optional.ofNullable(dao.findCapabilityAssignmentByAgentAndCapability(agentId, capabilityCode)).map(converter::toCapabilityAssignment);
    }

    @Override
    public List<AgentCapabilityAssignment> findAgentCapabilityAssignmentsByAgent(String agentId) {
        return dao.findCapabilityAssignmentsByAgent(agentId).stream().map(converter::toCapabilityAssignment).toList();
    }

    @Override
    public boolean deleteAgentCapabilityAssignment(String agentId, String assignmentId) {
        return dao.deleteCapabilityAssignment(agentId, assignmentId) > 0;
    }



    @Override
    public DispatchPolicy saveDispatchPolicy(DispatchPolicy policy) {
        dao.upsertDispatchPolicy(converter.toDispatchPolicyPo(policy));
        return findDispatchPolicyByCode(policy.getTenantId(), policy.getPolicyCode()).orElse(policy);
    }

    @Override
    public Optional<DispatchPolicy> findDispatchPolicyByCode(String tenantId, String policyCode) {
        return Optional.ofNullable(dao.findDispatchPolicyByCode(defaultTenant(tenantId), policyCode))
                .map(converter::toDispatchPolicy)
                .map(this::attachDispatchPolicyRules);
    }

    @Override
    public List<DispatchPolicy> searchDispatchPolicies(String tenantId, String status, int limit) {
        return dao.searchDispatchPolicies(defaultTenant(tenantId), status, Math.max(1, limit)).stream()
                .map(converter::toDispatchPolicy)
                .map(this::attachDispatchPolicyRules)
                .toList();
    }

    @Override
    public DispatchPolicyScope saveDispatchPolicyScope(DispatchPolicyScope scope) {
        dao.upsertDispatchPolicyScope(converter.toDispatchPolicyScopePo(scope));
        return scope;
    }

    @Override
    public List<DispatchPolicyScope> findDispatchPolicyScopes(String tenantId, String policyCode, Boolean active) {
        return dao.findDispatchPolicyScopes(defaultTenant(tenantId), policyCode, active).stream().map(converter::toDispatchPolicyScope).toList();
    }

    @Override
    public DispatchPolicyRequiredCapability saveDispatchPolicyRequiredCapability(DispatchPolicyRequiredCapability rule) {
        dao.upsertDispatchPolicyRequiredCapability(converter.toDispatchPolicyRequiredCapabilityPo(rule));
        return rule;
    }

    @Override
    public List<DispatchPolicyRequiredCapability> findDispatchPolicyRequiredCapabilities(String tenantId, String policyCode, Boolean blocking) {
        return dao.findDispatchPolicyRequiredCapabilities(defaultTenant(tenantId), policyCode, blocking).stream().map(converter::toDispatchPolicyRequiredCapability).toList();
    }

    @Override
    public DispatchPolicyRequiredRuntimeFeature saveDispatchPolicyRequiredRuntimeFeature(DispatchPolicyRequiredRuntimeFeature rule) {
        dao.upsertDispatchPolicyRequiredRuntimeFeature(converter.toDispatchPolicyRequiredRuntimeFeaturePo(rule));
        return rule;
    }

    @Override
    public List<DispatchPolicyRequiredRuntimeFeature> findDispatchPolicyRequiredRuntimeFeatures(String tenantId, String policyCode, Boolean blocking) {
        return dao.findDispatchPolicyRequiredRuntimeFeatures(defaultTenant(tenantId), policyCode, blocking).stream().map(converter::toDispatchPolicyRequiredRuntimeFeature).toList();
    }

    @Override
    public DispatchPolicyQualityRule saveDispatchPolicyQualityRule(DispatchPolicyQualityRule rule) {
        dao.upsertDispatchPolicyQualityRule(converter.toDispatchPolicyQualityRulePo(rule));
        return rule;
    }

    @Override
    public List<DispatchPolicyQualityRule> findDispatchPolicyQualityRules(String tenantId, String policyCode, Boolean blocking) {
        return dao.findDispatchPolicyQualityRules(defaultTenant(tenantId), policyCode, blocking).stream().map(converter::toDispatchPolicyQualityRule).toList();
    }

    @Override
    public DispatchPolicyScoringRule saveDispatchPolicyScoringRule(DispatchPolicyScoringRule rule) {
        dao.upsertDispatchPolicyScoringRule(converter.toDispatchPolicyScoringRulePo(rule));
        return rule;
    }

    @Override
    public List<DispatchPolicyScoringRule> findDispatchPolicyScoringRules(String tenantId, String policyCode) {
        return dao.findDispatchPolicyScoringRules(defaultTenant(tenantId), policyCode).stream().map(converter::toDispatchPolicyScoringRule).toList();
    }

    private DispatchPolicy attachDispatchPolicyRules(DispatchPolicy policy) {
        policy.setScopes(findDispatchPolicyScopes(policy.getTenantId(), policy.getPolicyCode(), true));
        policy.setRequiredCapabilities(findDispatchPolicyRequiredCapabilities(policy.getTenantId(), policy.getPolicyCode(), null));
        policy.setRequiredRuntimeFeatures(findDispatchPolicyRequiredRuntimeFeatures(policy.getTenantId(), policy.getPolicyCode(), null));
        policy.setQualityRules(findDispatchPolicyQualityRules(policy.getTenantId(), policy.getPolicyCode(), null));
        policy.setScoringRules(findDispatchPolicyScoringRules(policy.getTenantId(), policy.getPolicyCode()));
        return policy;
    }



    @Override
    public AgentQualityMetricsDaily saveAgentQualityMetricsDaily(AgentQualityMetricsDaily metrics) {
        dao.upsertAgentQualityMetricsDaily(converter.toAgentQualityMetricsDailyPo(metrics));
        return metrics;
    }

    @Override
    public List<AgentQualityMetricsDaily> findAgentQualityMetricsDaily(String tenantId, String agentId, int limit) {
        return dao.findAgentQualityMetricsDaily(defaultTenant(tenantId), agentId, Math.max(1, limit)).stream()
                .map(converter::toAgentQualityMetricsDaily)
                .toList();
    }

    @Override
    public AgentQualityMetricsWindow saveAgentQualityMetricsWindow(AgentQualityMetricsWindow metrics) {
        dao.upsertAgentQualityMetricsWindow(converter.toAgentQualityMetricsWindowPo(metrics));
        return metrics;
    }

    @Override
    public List<AgentQualityMetricsWindow> findAgentQualityMetricsWindow(String tenantId, String agentId, String metricWindow, int limit) {
        return dao.findAgentQualityMetricsWindow(defaultTenant(tenantId), agentId, metricWindow, Math.max(1, limit)).stream()
                .map(converter::toAgentQualityMetricsWindow)
                .toList();
    }

    @Override
    public RuntimeQualityMetricsDaily saveRuntimeQualityMetricsDaily(RuntimeQualityMetricsDaily metrics) {
        dao.upsertRuntimeQualityMetricsDaily(converter.toRuntimeQualityMetricsDailyPo(metrics));
        return metrics;
    }

    @Override
    public List<RuntimeQualityMetricsDaily> findRuntimeQualityMetricsDaily(String tenantId, String runtimeId, int limit) {
        return dao.findRuntimeQualityMetricsDaily(defaultTenant(tenantId), runtimeId, Math.max(1, limit)).stream()
                .map(converter::toRuntimeQualityMetricsDaily)
                .toList();
    }

    @Override
    public SupplyProfileQualitySnapshot saveSupplyProfileQualitySnapshot(SupplyProfileQualitySnapshot snapshot) {
        dao.upsertSupplyProfileQualitySnapshot(converter.toSupplyProfileQualitySnapshotPo(snapshot));
        return findSupplyProfileQualitySnapshot(snapshot.getTenantId(), snapshot.getProfileCode(), snapshot.getMetricWindow()).orElse(snapshot);
    }

    @Override
    public Optional<SupplyProfileQualitySnapshot> findSupplyProfileQualitySnapshot(String tenantId, String profileCode, String metricWindow) {
        return Optional.ofNullable(dao.findSupplyProfileQualitySnapshot(defaultTenant(tenantId), profileCode, metricWindow))
                .map(converter::toSupplyProfileQualitySnapshot);
    }

    @Override
    public List<SupplyProfileQualitySnapshot> searchSupplyProfileQualitySnapshots(String tenantId, String agentId, String runtimeId, String metricWindow, int limit) {
        return dao.searchSupplyProfileQualitySnapshots(defaultTenant(tenantId), agentId, runtimeId, metricWindow, Math.max(1, limit)).stream()
                .map(converter::toSupplyProfileQualitySnapshot)
                .toList();
    }

    @Override
    public AgentAssignmentProfilePolicyBinding savePolicyBinding(AgentAssignmentProfilePolicyBinding binding) {
        dao.upsertPolicyBinding(converter.toPolicyBindingPo(binding));
        return findPolicyBinding(binding.getTenantId(), binding.getProfileCode(), binding.getPolicyCode()).orElse(binding);
    }

    @Override
    public Optional<AgentAssignmentProfilePolicyBinding> findPolicyBinding(String tenantId, String profileCode, String policyCode) {
        return Optional.ofNullable(dao.findPolicyBinding(defaultTenant(tenantId), profileCode, policyCode)).map(converter::toPolicyBinding);
    }

    @Override
    public List<AgentAssignmentProfilePolicyBinding> findPolicyBindings(String tenantId, String profileCode, Boolean active) {
        return dao.findPolicyBindings(defaultTenant(tenantId), profileCode, active).stream().map(converter::toPolicyBinding).toList();
    }

    @Override
    public boolean deletePolicyBinding(String tenantId, String profileCode, String policyCode) {
        return dao.deletePolicyBinding(defaultTenant(tenantId), profileCode, policyCode) > 0;
    }

    @Override
    public AgentQualification saveQualification(AgentQualification qualification) {
        dao.upsertQualification(converter.toQualificationPo(qualification));
        return findQualification(qualification.getQualificationId()).orElse(qualification);
    }

    @Override
    public Optional<AgentQualification> findQualification(String qualificationId) {
        return Optional.ofNullable(dao.findQualification(qualificationId)).map(converter::toQualification);
    }

    @Override
    public boolean deleteQualification(String agentId, String qualificationId) {
        return dao.deleteQualification(agentId, qualificationId) > 0;
    }

    @Override
    public Optional<AgentQualification> findQualificationByAgentAndProfile(String agentId, String profileCode) {
        return Optional.ofNullable(dao.findQualificationByAgentAndProfile(agentId, profileCode)).map(converter::toQualification);
    }

    @Override
    public List<AgentQualification> findQualificationsByAgent(String agentId) {
        return dao.findQualificationsByAgent(agentId).stream().map(converter::toQualification).toList();
    }

    @Override
    public List<AgentQualification> findQualificationsByProfile(String tenantId, String profileCode) {
        return dao.findQualificationsByProfile(defaultTenant(tenantId), profileCode).stream().map(converter::toQualification).toList();
    }

    private AgentAssignmentProfile attachPolicyBindings(AgentAssignmentProfile profile) {
        profile.setPolicyBindings(findPolicyBindings(profile.getTenantId(), profile.getProfileCode(), true));
        profile.setCapabilityBindings(findCapabilityBindings(profile.getTenantId(), profile.getProfileCode(), true));
        return profile;
    }

    @Override
    public String mode() {
        return "MYBATIS";
    }

    private String defaultTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        return tenantId.trim();
    }
}
