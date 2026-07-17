package com.opensocket.aievent.database.persistence.agent.assignment.converter;

import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.opensocket.aievent.core.agent.assignment.DispatchTaskDefinition;
import com.opensocket.aievent.core.agent.assignment.DispatchEventTaskMapping;
import com.opensocket.aievent.core.agent.assignment.AgentAssignmentProfile;
import com.opensocket.aievent.core.agent.assignment.AgentCapabilityCatalog;
import com.opensocket.aievent.core.agent.assignment.AgentCapabilityAssignment;
import com.opensocket.aievent.core.agent.assignment.AgentCapabilityAssignmentStatus;
import com.opensocket.aievent.core.agent.assignment.RuntimeFeatureCatalog;
import com.opensocket.aievent.core.agent.assignment.RuntimeResource;
import com.opensocket.aievent.core.agent.assignment.AgentRuntimeBinding;
import com.opensocket.aievent.core.agent.assignment.SupplyProfile;
import com.opensocket.aievent.core.agent.assignment.AgentRuntimeFeatureObservation;
import com.opensocket.aievent.core.agent.assignment.AgentRuntimeFeatureTrust;
import com.opensocket.aievent.core.agent.assignment.AgentRuntimeFeatureTrustStatus;
import com.opensocket.aievent.core.agent.assignment.AssignmentProfileCapabilityBinding;
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
import com.opensocket.aievent.core.agent.assignment.AgentQualification;
import com.opensocket.aievent.core.agent.assignment.AgentQualificationStatus;
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
import com.opensocket.aievent.database.persistence.spi.DatabasePersistenceConverter;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@DatabasePersistenceConverter
@ConditionalOnProperty(prefix = "agent-assignment", name = "store", havingValue = "MYBATIS")
public class AgentAssignmentPersistenceConverter {
    private final ObjectMapper objectMapper;

    public AgentAssignmentPersistenceConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }


    public DispatchTaskDefinitionPo toTaskDefinitionPo(DispatchTaskDefinition definition) {
        DispatchTaskDefinitionPo po = new DispatchTaskDefinitionPo();
        po.setTenantId(definition.getTenantId());
        po.setDefinitionId(definition.getDefinitionId());
        po.setSourceSystem(definition.getSourceSystem());
        po.setTaskType(definition.getTaskType());
        po.setDisplayName(definition.getDisplayName());
        po.setDescription(definition.getDescription());
        po.setDomain(definition.getDomain());
        po.setRiskLevel(definition.getRiskLevel());
        po.setDefaultSeverity(definition.getDefaultSeverity());
        po.setOwnerTeam(definition.getOwnerTeam());
        po.setStatus(definition.getStatus());
        po.setVersion(definition.getVersion());
        po.setEffectiveFrom(definition.getEffectiveFrom());
        po.setRetiredAt(definition.getRetiredAt());
        po.setMetadataJson(toObjectJson(definition.getMetadata()));
        po.setCreatedAt(definition.getCreatedAt());
        po.setUpdatedAt(definition.getUpdatedAt());
        return po;
    }

    public DispatchTaskDefinition toTaskDefinition(DispatchTaskDefinitionPo po) {
        DispatchTaskDefinition definition = new DispatchTaskDefinition();
        definition.setTenantId(po.getTenantId());
        definition.setDefinitionId(po.getDefinitionId());
        definition.setSourceSystem(po.getSourceSystem());
        definition.setTaskType(po.getTaskType());
        definition.setDisplayName(po.getDisplayName());
        definition.setDescription(po.getDescription());
        definition.setDomain(po.getDomain());
        definition.setRiskLevel(po.getRiskLevel());
        definition.setDefaultSeverity(po.getDefaultSeverity());
        definition.setOwnerTeam(po.getOwnerTeam());
        definition.setStatus(po.getStatus());
        definition.setVersion(po.getVersion() <= 0 ? 1 : po.getVersion());
        definition.setEffectiveFrom(po.getEffectiveFrom());
        definition.setRetiredAt(po.getRetiredAt());
        definition.setMetadata(fromObjectJson(po.getMetadataJson()));
        definition.setCreatedAt(po.getCreatedAt());
        definition.setUpdatedAt(po.getUpdatedAt());
        return definition;
    }


    public DispatchEventTaskMappingPo toEventTaskMappingPo(DispatchEventTaskMapping mapping) {
        DispatchEventTaskMappingPo po = new DispatchEventTaskMappingPo();
        po.setTenantId(mapping.getTenantId());
        po.setMappingId(mapping.getMappingId());
        po.setSourceSystem(mapping.getSourceSystem());
        po.setObjectType(mapping.getObjectType());
        po.setEventType(mapping.getEventType());
        po.setErrorCode(mapping.getErrorCode());
        po.setMessagePattern(mapping.getMessagePattern());
        po.setTaskType(mapping.getTaskType());
        po.setCapabilityCode(mapping.getCapabilityCode());
        po.setPriority(mapping.getPriority());
        po.setActive(mapping.isActive());
        po.setMetadataJson(toObjectJson(mapping.getMetadata()));
        po.setCreatedAt(mapping.getCreatedAt());
        po.setUpdatedAt(mapping.getUpdatedAt());
        return po;
    }

    public DispatchEventTaskMapping toEventTaskMapping(DispatchEventTaskMappingPo po) {
        DispatchEventTaskMapping mapping = new DispatchEventTaskMapping();
        mapping.setTenantId(po.getTenantId());
        mapping.setMappingId(po.getMappingId());
        mapping.setSourceSystem(po.getSourceSystem());
        mapping.setObjectType(po.getObjectType());
        mapping.setEventType(po.getEventType());
        mapping.setErrorCode(po.getErrorCode());
        mapping.setMessagePattern(po.getMessagePattern());
        mapping.setTaskType(po.getTaskType());
        mapping.setCapabilityCode(po.getCapabilityCode());
        mapping.setPriority(po.getPriority());
        mapping.setActive(po.isActive());
        mapping.setMetadata(fromObjectJson(po.getMetadataJson()));
        mapping.setCreatedAt(po.getCreatedAt());
        mapping.setUpdatedAt(po.getUpdatedAt());
        return mapping;
    }

    public AgentAssignmentProfilePo toProfilePo(AgentAssignmentProfile profile) {
        AgentAssignmentProfilePo po = new AgentAssignmentProfilePo();
        po.setTenantId(profile.getTenantId());
        po.setProfileId(profile.getProfileId());
        po.setProfileCode(profile.getProfileCode());
        po.setProfileName(profile.getProfileName());
        po.setAgentType(profile.getAgentType());
        po.setTaskDefinitionId(profile.getTaskDefinitionId());
        po.setSourceSystem(profile.getSourceSystem());
        po.setTaskType(profile.getTaskType());
        po.setDescription(profile.getDescription());
        po.setAllowedTaskTypesJson(toStringListJson(profile.getAllowedTaskTypes()));
        po.setAllowedIssueProvidersJson(toStringListJson(profile.getAllowedIssueProviders()));
        po.setRequiredRuntimeFeaturesJson(toStringListJson(profile.getRequiredRuntimeFeatures()));
        po.setToolPolicy(profile.getToolPolicy());
        po.setRiskLevelLimit(profile.getRiskLevelLimit());
        po.setRequiresCertification(profile.isRequiresCertification());
        po.setRequiresHumanApproval(profile.isRequiresHumanApproval());
        po.setActive(profile.isActive());
        po.setPolicyVersion(profile.getPolicyVersion());
        po.setEffectiveAt(profile.getEffectiveAt());
        po.setExpiresAt(profile.getExpiresAt());
        po.setRenewalRequiredBeforeDays(profile.getRenewalRequiredBeforeDays());
        po.setMetadataJson(toObjectJson(profile.getMetadata()));
        po.setCreatedAt(profile.getCreatedAt());
        po.setUpdatedAt(profile.getUpdatedAt());
        return po;
    }

    public AgentAssignmentProfile toProfile(AgentAssignmentProfilePo po) {
        AgentAssignmentProfile profile = new AgentAssignmentProfile();
        profile.setTenantId(po.getTenantId());
        profile.setProfileId(po.getProfileId());
        profile.setProfileCode(po.getProfileCode());
        profile.setProfileName(po.getProfileName());
        profile.setAgentType(po.getAgentType());
        profile.setTaskDefinitionId(po.getTaskDefinitionId());
        profile.setSourceSystem(po.getSourceSystem());
        profile.setTaskType(po.getTaskType());
        profile.setDescription(po.getDescription());
        profile.setAllowedTaskTypes(fromStringListJson(po.getAllowedTaskTypesJson()));
        profile.setAllowedIssueProviders(fromStringListJson(po.getAllowedIssueProvidersJson()));
        profile.setRequiredRuntimeFeatures(fromStringListJson(po.getRequiredRuntimeFeaturesJson()));
        profile.setToolPolicy(po.getToolPolicy());
        profile.setRiskLevelLimit(po.getRiskLevelLimit());
        profile.setRequiresCertification(po.isRequiresCertification());
        profile.setRequiresHumanApproval(po.isRequiresHumanApproval());
        profile.setActive(po.isActive());
        profile.setPolicyVersion(po.getPolicyVersion() <= 0 ? 1 : po.getPolicyVersion());
        profile.setEffectiveAt(po.getEffectiveAt());
        profile.setExpiresAt(po.getExpiresAt());
        profile.setRenewalRequiredBeforeDays(po.getRenewalRequiredBeforeDays() <= 0 ? 14 : po.getRenewalRequiredBeforeDays());
        profile.setMetadata(fromObjectJson(po.getMetadataJson()));
        profile.setCreatedAt(po.getCreatedAt());
        profile.setUpdatedAt(po.getUpdatedAt());
        return profile;
    }




    public DispatchPolicyPo toDispatchPolicyPo(DispatchPolicy policy) {
        DispatchPolicyPo po = new DispatchPolicyPo();
        po.setTenantId(policy.getTenantId());
        po.setPolicyId(policy.getPolicyId());
        po.setPolicyCode(policy.getPolicyCode());
        po.setPolicyName(policy.getPolicyName());
        po.setDescription(policy.getDescription());
        po.setOwnerTeam(policy.getOwnerTeam());
        po.setRiskLevel(policy.getRiskLevel());
        po.setStatus(policy.getStatus());
        po.setVersion(policy.getVersion());
        po.setEffectiveFrom(policy.getEffectiveFrom());
        po.setRetiredAt(policy.getRetiredAt());
        po.setMetadataJson(toObjectJson(policy.getMetadata()));
        po.setCreatedAt(policy.getCreatedAt());
        po.setUpdatedAt(policy.getUpdatedAt());
        return po;
    }

    public DispatchPolicy toDispatchPolicy(DispatchPolicyPo po) {
        DispatchPolicy policy = new DispatchPolicy();
        policy.setTenantId(po.getTenantId());
        policy.setPolicyId(po.getPolicyId());
        policy.setPolicyCode(po.getPolicyCode());
        policy.setPolicyName(po.getPolicyName());
        policy.setDescription(po.getDescription());
        policy.setOwnerTeam(po.getOwnerTeam());
        policy.setRiskLevel(po.getRiskLevel());
        policy.setStatus(po.getStatus());
        policy.setVersion(po.getVersion() <= 0 ? 1 : po.getVersion());
        policy.setEffectiveFrom(po.getEffectiveFrom());
        policy.setRetiredAt(po.getRetiredAt());
        policy.setMetadata(fromObjectJson(po.getMetadataJson()));
        policy.setCreatedAt(po.getCreatedAt());
        policy.setUpdatedAt(po.getUpdatedAt());
        return policy;
    }

    public DispatchPolicyScopePo toDispatchPolicyScopePo(DispatchPolicyScope scope) {
        DispatchPolicyScopePo po = new DispatchPolicyScopePo();
        po.setTenantId(scope.getTenantId());
        po.setScopeId(scope.getScopeId());
        po.setPolicyCode(scope.getPolicyCode());
        po.setSourceSystem(scope.getSourceSystem());
        po.setTaskType(scope.getTaskType());
        po.setTaskDefinitionId(scope.getTaskDefinitionId());
        po.setRiskLevel(scope.getRiskLevel());
        po.setPriority(scope.getPriority());
        po.setConditionExpr(scope.getConditionExpr());
        po.setActive(scope.isActive());
        po.setPriorityOrder(scope.getPriorityOrder());
        po.setMetadataJson(toObjectJson(scope.getMetadata()));
        po.setCreatedAt(scope.getCreatedAt());
        po.setUpdatedAt(scope.getUpdatedAt());
        return po;
    }

    public DispatchPolicyScope toDispatchPolicyScope(DispatchPolicyScopePo po) {
        DispatchPolicyScope scope = new DispatchPolicyScope();
        scope.setTenantId(po.getTenantId());
        scope.setScopeId(po.getScopeId());
        scope.setPolicyCode(po.getPolicyCode());
        scope.setSourceSystem(po.getSourceSystem());
        scope.setTaskType(po.getTaskType());
        scope.setTaskDefinitionId(po.getTaskDefinitionId());
        scope.setRiskLevel(po.getRiskLevel());
        scope.setPriority(po.getPriority());
        scope.setConditionExpr(po.getConditionExpr());
        scope.setActive(po.isActive());
        scope.setPriorityOrder(po.getPriorityOrder());
        scope.setMetadata(fromObjectJson(po.getMetadataJson()));
        scope.setCreatedAt(po.getCreatedAt());
        scope.setUpdatedAt(po.getUpdatedAt());
        return scope;
    }

    public DispatchPolicyRequiredCapabilityPo toDispatchPolicyRequiredCapabilityPo(DispatchPolicyRequiredCapability rule) {
        DispatchPolicyRequiredCapabilityPo po = new DispatchPolicyRequiredCapabilityPo();
        po.setTenantId(rule.getTenantId());
        po.setRuleId(rule.getRuleId());
        po.setPolicyCode(rule.getPolicyCode());
        po.setCapabilityCode(rule.getCapabilityCode());
        po.setCapabilityName(rule.getCapabilityName());
        po.setRequiredMode(rule.getRequiredMode());
        po.setMinVersion(rule.getMinVersion());
        po.setConditionExpr(rule.getConditionExpr());
        po.setBlocking(rule.isBlocking());
        po.setPriority(rule.getPriority());
        po.setMetadataJson(toObjectJson(rule.getMetadata()));
        po.setCreatedAt(rule.getCreatedAt());
        po.setUpdatedAt(rule.getUpdatedAt());
        return po;
    }

    public DispatchPolicyRequiredCapability toDispatchPolicyRequiredCapability(DispatchPolicyRequiredCapabilityPo po) {
        DispatchPolicyRequiredCapability rule = new DispatchPolicyRequiredCapability();
        rule.setTenantId(po.getTenantId());
        rule.setRuleId(po.getRuleId());
        rule.setPolicyCode(po.getPolicyCode());
        rule.setCapabilityCode(po.getCapabilityCode());
        rule.setCapabilityName(po.getCapabilityName());
        rule.setRequiredMode(po.getRequiredMode());
        rule.setMinVersion(po.getMinVersion());
        rule.setConditionExpr(po.getConditionExpr());
        rule.setBlocking(po.isBlocking());
        rule.setPriority(po.getPriority());
        rule.setMetadata(fromObjectJson(po.getMetadataJson()));
        rule.setCreatedAt(po.getCreatedAt());
        rule.setUpdatedAt(po.getUpdatedAt());
        return rule;
    }

    public DispatchPolicyRequiredRuntimeFeaturePo toDispatchPolicyRequiredRuntimeFeaturePo(DispatchPolicyRequiredRuntimeFeature rule) {
        DispatchPolicyRequiredRuntimeFeaturePo po = new DispatchPolicyRequiredRuntimeFeaturePo();
        po.setTenantId(rule.getTenantId());
        po.setRuleId(rule.getRuleId());
        po.setPolicyCode(rule.getPolicyCode());
        po.setFeatureCode(rule.getFeatureCode());
        po.setFeatureName(rule.getFeatureName());
        po.setTrustStatus(rule.getTrustStatus());
        po.setConditionExpr(rule.getConditionExpr());
        po.setBlocking(rule.isBlocking());
        po.setPriority(rule.getPriority());
        po.setMetadataJson(toObjectJson(rule.getMetadata()));
        po.setCreatedAt(rule.getCreatedAt());
        po.setUpdatedAt(rule.getUpdatedAt());
        return po;
    }

    public DispatchPolicyRequiredRuntimeFeature toDispatchPolicyRequiredRuntimeFeature(DispatchPolicyRequiredRuntimeFeaturePo po) {
        DispatchPolicyRequiredRuntimeFeature rule = new DispatchPolicyRequiredRuntimeFeature();
        rule.setTenantId(po.getTenantId());
        rule.setRuleId(po.getRuleId());
        rule.setPolicyCode(po.getPolicyCode());
        rule.setFeatureCode(po.getFeatureCode());
        rule.setFeatureName(po.getFeatureName());
        rule.setTrustStatus(po.getTrustStatus());
        rule.setConditionExpr(po.getConditionExpr());
        rule.setBlocking(po.isBlocking());
        rule.setPriority(po.getPriority());
        rule.setMetadata(fromObjectJson(po.getMetadataJson()));
        rule.setCreatedAt(po.getCreatedAt());
        rule.setUpdatedAt(po.getUpdatedAt());
        return rule;
    }

    public DispatchPolicyQualityRulePo toDispatchPolicyQualityRulePo(DispatchPolicyQualityRule rule) {
        DispatchPolicyQualityRulePo po = new DispatchPolicyQualityRulePo();
        po.setTenantId(rule.getTenantId());
        po.setRuleId(rule.getRuleId());
        po.setPolicyCode(rule.getPolicyCode());
        po.setMetricName(rule.getMetricName());
        po.setOperator(rule.getOperator());
        po.setThresholdValue(rule.getThresholdValue());
        po.setMetricWindow(rule.getMetricWindow());
        po.setBlocking(rule.isBlocking());
        po.setScoreWeight(rule.getScoreWeight());
        po.setConditionExpr(rule.getConditionExpr());
        po.setMetadataJson(toObjectJson(rule.getMetadata()));
        po.setCreatedAt(rule.getCreatedAt());
        po.setUpdatedAt(rule.getUpdatedAt());
        return po;
    }

    public DispatchPolicyQualityRule toDispatchPolicyQualityRule(DispatchPolicyQualityRulePo po) {
        DispatchPolicyQualityRule rule = new DispatchPolicyQualityRule();
        rule.setTenantId(po.getTenantId());
        rule.setRuleId(po.getRuleId());
        rule.setPolicyCode(po.getPolicyCode());
        rule.setMetricName(po.getMetricName());
        rule.setOperator(po.getOperator());
        rule.setThresholdValue(po.getThresholdValue());
        rule.setMetricWindow(po.getMetricWindow());
        rule.setBlocking(po.isBlocking());
        rule.setScoreWeight(po.getScoreWeight());
        rule.setConditionExpr(po.getConditionExpr());
        rule.setMetadata(fromObjectJson(po.getMetadataJson()));
        rule.setCreatedAt(po.getCreatedAt());
        rule.setUpdatedAt(po.getUpdatedAt());
        return rule;
    }

    public DispatchPolicyScoringRulePo toDispatchPolicyScoringRulePo(DispatchPolicyScoringRule rule) {
        DispatchPolicyScoringRulePo po = new DispatchPolicyScoringRulePo();
        po.setTenantId(rule.getTenantId());
        po.setRuleId(rule.getRuleId());
        po.setPolicyCode(rule.getPolicyCode());
        po.setFactorName(rule.getFactorName());
        po.setWeight(rule.getWeight());
        po.setDirection(rule.getDirection());
        po.setConditionExpr(rule.getConditionExpr());
        po.setMetadataJson(toObjectJson(rule.getMetadata()));
        po.setCreatedAt(rule.getCreatedAt());
        po.setUpdatedAt(rule.getUpdatedAt());
        return po;
    }

    public DispatchPolicyScoringRule toDispatchPolicyScoringRule(DispatchPolicyScoringRulePo po) {
        DispatchPolicyScoringRule rule = new DispatchPolicyScoringRule();
        rule.setTenantId(po.getTenantId());
        rule.setRuleId(po.getRuleId());
        rule.setPolicyCode(po.getPolicyCode());
        rule.setFactorName(po.getFactorName());
        rule.setWeight(po.getWeight());
        rule.setDirection(po.getDirection());
        rule.setConditionExpr(po.getConditionExpr());
        rule.setMetadata(fromObjectJson(po.getMetadataJson()));
        rule.setCreatedAt(po.getCreatedAt());
        rule.setUpdatedAt(po.getUpdatedAt());
        return rule;
    }


    public AgentAssignmentProfilePolicyBindingPo toPolicyBindingPo(AgentAssignmentProfilePolicyBinding binding) {
        AgentAssignmentProfilePolicyBindingPo po = new AgentAssignmentProfilePolicyBindingPo();
        po.setTenantId(binding.getTenantId());
        po.setBindingId(binding.getBindingId());
        po.setProfileCode(binding.getProfileCode());
        po.setPolicyCode(binding.getPolicyCode());
        po.setPolicyName(binding.getPolicyName());
        po.setBindingMode(binding.getBindingMode());
        po.setRequired(binding.isRequired());
        po.setActive(binding.isActive());
        po.setPriority(binding.getPriority());
        po.setConditionExpr(binding.getConditionExpr());
        po.setMetadataJson(toObjectJson(binding.getMetadata()));
        po.setCreatedAt(binding.getCreatedAt());
        po.setUpdatedAt(binding.getUpdatedAt());
        return po;
    }

    public AgentAssignmentProfilePolicyBinding toPolicyBinding(AgentAssignmentProfilePolicyBindingPo po) {
        AgentAssignmentProfilePolicyBinding binding = new AgentAssignmentProfilePolicyBinding();
        binding.setTenantId(po.getTenantId());
        binding.setBindingId(po.getBindingId());
        binding.setProfileCode(po.getProfileCode());
        binding.setPolicyCode(po.getPolicyCode());
        binding.setPolicyName(po.getPolicyName());
        binding.setBindingMode(po.getBindingMode());
        binding.setRequired(po.isRequired());
        binding.setActive(po.isActive());
        binding.setPriority(po.getPriority());
        binding.setConditionExpr(po.getConditionExpr());
        binding.setMetadata(fromObjectJson(po.getMetadataJson()));
        binding.setCreatedAt(po.getCreatedAt());
        binding.setUpdatedAt(po.getUpdatedAt());
        return binding;
    }

    public AgentQualificationPo toQualificationPo(AgentQualification qualification) {
        AgentQualificationPo po = new AgentQualificationPo();
        po.setTenantId(qualification.getTenantId());
        po.setQualificationId(qualification.getQualificationId());
        po.setAgentId(qualification.getAgentId());
        po.setProfileCode(qualification.getProfileCode());
        po.setQualificationStatus(qualification.getQualificationStatus() == null ? null : qualification.getQualificationStatus().name());
        po.setEvidenceType(qualification.getEvidenceType());
        po.setEvidenceRef(qualification.getEvidenceRef());
        po.setApprovedBy(qualification.getApprovedBy());
        po.setApprovedAt(qualification.getApprovedAt());
        po.setExpiresAt(qualification.getExpiresAt());
        po.setGrantedPolicyVersion(qualification.getGrantedPolicyVersion());
        po.setLastRenewedAt(qualification.getLastRenewedAt());
        po.setRenewalDueAt(qualification.getRenewalDueAt());
        po.setRenewalStatus(qualification.getRenewalStatus());
        po.setReason(qualification.getReason());
        po.setMetadataJson(toObjectJson(qualification.getMetadata()));
        po.setCreatedAt(qualification.getCreatedAt());
        po.setUpdatedAt(qualification.getUpdatedAt());
        return po;
    }

    public AgentQualification toQualification(AgentQualificationPo po) {
        AgentQualification qualification = new AgentQualification();
        qualification.setTenantId(po.getTenantId());
        qualification.setQualificationId(po.getQualificationId());
        qualification.setAgentId(po.getAgentId());
        qualification.setProfileCode(po.getProfileCode());
        qualification.setQualificationStatus(po.getQualificationStatus() == null ? null : AgentQualificationStatus.valueOf(po.getQualificationStatus()));
        qualification.setEvidenceType(po.getEvidenceType());
        qualification.setEvidenceRef(po.getEvidenceRef());
        qualification.setApprovedBy(po.getApprovedBy());
        qualification.setApprovedAt(po.getApprovedAt());
        qualification.setExpiresAt(po.getExpiresAt());
        qualification.setGrantedPolicyVersion(po.getGrantedPolicyVersion() <= 0 ? 1 : po.getGrantedPolicyVersion());
        qualification.setLastRenewedAt(po.getLastRenewedAt());
        qualification.setRenewalDueAt(po.getRenewalDueAt());
        qualification.setRenewalStatus(po.getRenewalStatus() == null ? "CURRENT" : po.getRenewalStatus());
        qualification.setReason(po.getReason());
        qualification.setMetadata(fromObjectJson(po.getMetadataJson()));
        qualification.setCreatedAt(po.getCreatedAt());
        qualification.setUpdatedAt(po.getUpdatedAt());
        return qualification;
    }



    public AgentCapabilityCatalogPo toCapabilityCatalogPo(AgentCapabilityCatalog capability) {
        AgentCapabilityCatalogPo po = new AgentCapabilityCatalogPo();
        po.setTenantId(capability.getTenantId());
        po.setCapabilityId(capability.getCapabilityId());
        po.setCapabilityCode(capability.getCapabilityCode());
        po.setCapabilityName(capability.getCapabilityName());
        po.setCategory(capability.getCategory());
        po.setCapabilityType(capability.getCapabilityType());
        po.setDomain(capability.getDomain());
        po.setResourceType(capability.getResourceType());
        po.setOperation(capability.getOperation());
        po.setDataClass(capability.getDataClass());
        po.setServiceLevel(capability.getServiceLevel());
        po.setLegacyTaskCoupling(capability.isLegacyTaskCoupling());
        po.setMigrationStatus(capability.getMigrationStatus());
        po.setDescription(capability.getDescription());
        po.setTaskDefinitionId(capability.getTaskDefinitionId());
        po.setSourceSystem(capability.getSourceSystem());
        po.setTaskType(capability.getTaskType());
        po.setRiskLevel(capability.getRiskLevel());
        po.setStatus(capability.getStatus());
        po.setVersion(capability.getVersion());
        po.setOwnerTeam(capability.getOwnerTeam());
        po.setRequiresApproval(capability.isRequiresApproval());
        po.setRequiresCertification(capability.isRequiresCertification());
        po.setRequiresRuntimeProbe(capability.isRequiresRuntimeProbe());
        po.setDispatchEligible(capability.isDispatchEligible());
        po.setEffectiveFrom(capability.getEffectiveFrom());
        po.setRetiredAt(capability.getRetiredAt());
        po.setMetadataJson(toObjectJson(capability.getMetadata()));
        po.setCreatedAt(capability.getCreatedAt());
        po.setUpdatedAt(capability.getUpdatedAt());
        return po;
    }

    public AgentCapabilityCatalog toCapabilityCatalog(AgentCapabilityCatalogPo po) {
        AgentCapabilityCatalog capability = new AgentCapabilityCatalog();
        capability.setTenantId(po.getTenantId());
        capability.setCapabilityId(po.getCapabilityId());
        capability.setCapabilityCode(po.getCapabilityCode());
        capability.setCapabilityName(po.getCapabilityName());
        capability.setCategory(po.getCategory());
        capability.setCapabilityType(po.getCapabilityType() == null ? "SERVICE" : po.getCapabilityType());
        capability.setDomain(po.getDomain());
        capability.setResourceType(po.getResourceType());
        capability.setOperation(po.getOperation());
        capability.setDataClass(po.getDataClass());
        capability.setServiceLevel(po.getServiceLevel());
        capability.setLegacyTaskCoupling(po.isLegacyTaskCoupling());
        capability.setMigrationStatus(po.getMigrationStatus() == null ? "CURRENT" : po.getMigrationStatus());
        capability.setDescription(po.getDescription());
        capability.setTaskDefinitionId(po.getTaskDefinitionId());
        capability.setSourceSystem(po.getSourceSystem());
        capability.setTaskType(po.getTaskType());
        capability.setRiskLevel(po.getRiskLevel());
        capability.setStatus(po.getStatus());
        capability.setVersion(po.getVersion() <= 0 ? 1 : po.getVersion());
        capability.setOwnerTeam(po.getOwnerTeam());
        capability.setRequiresApproval(po.isRequiresApproval());
        capability.setRequiresCertification(po.isRequiresCertification());
        capability.setRequiresRuntimeProbe(po.isRequiresRuntimeProbe());
        capability.setDispatchEligible(po.isDispatchEligible());
        capability.setEffectiveFrom(po.getEffectiveFrom());
        capability.setRetiredAt(po.getRetiredAt());
        capability.setMetadata(fromObjectJson(po.getMetadataJson()));
        capability.setCreatedAt(po.getCreatedAt());
        capability.setUpdatedAt(po.getUpdatedAt());
        return capability;
    }

    public AssignmentProfileCapabilityBindingPo toCapabilityBindingPo(AssignmentProfileCapabilityBinding binding) {
        AssignmentProfileCapabilityBindingPo po = new AssignmentProfileCapabilityBindingPo();
        po.setTenantId(binding.getTenantId());
        po.setBindingId(binding.getBindingId());
        po.setProfileCode(binding.getProfileCode());
        po.setCapabilityCode(binding.getCapabilityCode());
        po.setCapabilityName(binding.getCapabilityName());
        po.setBindingMode(binding.getBindingMode());
        po.setRequired(binding.isRequired());
        po.setActive(binding.isActive());
        po.setPriority(binding.getPriority());
        po.setApprovalStatus(binding.getApprovalStatus());
        po.setConditionExpr(binding.getConditionExpr());
        po.setMetadataJson(toObjectJson(binding.getMetadata()));
        po.setCreatedAt(binding.getCreatedAt());
        po.setUpdatedAt(binding.getUpdatedAt());
        return po;
    }

    public AssignmentProfileCapabilityBinding toCapabilityBinding(AssignmentProfileCapabilityBindingPo po) {
        AssignmentProfileCapabilityBinding binding = new AssignmentProfileCapabilityBinding();
        binding.setTenantId(po.getTenantId());
        binding.setBindingId(po.getBindingId());
        binding.setProfileCode(po.getProfileCode());
        binding.setCapabilityCode(po.getCapabilityCode());
        binding.setCapabilityName(po.getCapabilityName());
        binding.setBindingMode(po.getBindingMode());
        binding.setRequired(po.isRequired());
        binding.setActive(po.isActive());
        binding.setPriority(po.getPriority());
        binding.setApprovalStatus(po.getApprovalStatus() == null ? "ACTIVE" : po.getApprovalStatus());
        binding.setConditionExpr(po.getConditionExpr());
        binding.setMetadata(fromObjectJson(po.getMetadataJson()));
        binding.setCreatedAt(po.getCreatedAt());
        binding.setUpdatedAt(po.getUpdatedAt());
        return binding;
    }

    public AgentCapabilityAssignmentPo toCapabilityAssignmentPo(AgentCapabilityAssignment assignment) {
        AgentCapabilityAssignmentPo po = new AgentCapabilityAssignmentPo();
        po.setTenantId(assignment.getTenantId());
        po.setAssignmentId(assignment.getAssignmentId());
        po.setAgentId(assignment.getAgentId());
        po.setCapabilityCode(assignment.getCapabilityCode());
        po.setCapabilityName(assignment.getCapabilityName());
        po.setStatus(assignment.getStatus() == null ? null : assignment.getStatus().name());
        po.setSource(assignment.getSource());
        po.setRequestedBy(assignment.getRequestedBy());
        po.setRequestedAt(assignment.getRequestedAt());
        po.setApprovedBy(assignment.getApprovedBy());
        po.setApprovedAt(assignment.getApprovedAt());
        po.setRevokedBy(assignment.getRevokedBy());
        po.setRevokedAt(assignment.getRevokedAt());
        po.setExpiresAt(assignment.getExpiresAt());
        po.setEvidenceRef(assignment.getEvidenceRef());
        po.setReason(assignment.getReason());
        po.setMetadataJson(toObjectJson(assignment.getMetadata()));
        po.setCreatedAt(assignment.getCreatedAt());
        po.setUpdatedAt(assignment.getUpdatedAt());
        return po;
    }

    public AgentCapabilityAssignment toCapabilityAssignment(AgentCapabilityAssignmentPo po) {
        AgentCapabilityAssignment assignment = new AgentCapabilityAssignment();
        assignment.setTenantId(po.getTenantId());
        assignment.setAssignmentId(po.getAssignmentId());
        assignment.setAgentId(po.getAgentId());
        assignment.setCapabilityCode(po.getCapabilityCode());
        assignment.setCapabilityName(po.getCapabilityName());
        assignment.setStatus(po.getStatus() == null ? null : AgentCapabilityAssignmentStatus.valueOf(po.getStatus()));
        assignment.setSource(po.getSource());
        assignment.setRequestedBy(po.getRequestedBy());
        assignment.setRequestedAt(po.getRequestedAt());
        assignment.setApprovedBy(po.getApprovedBy());
        assignment.setApprovedAt(po.getApprovedAt());
        assignment.setRevokedBy(po.getRevokedBy());
        assignment.setRevokedAt(po.getRevokedAt());
        assignment.setExpiresAt(po.getExpiresAt());
        assignment.setEvidenceRef(po.getEvidenceRef());
        assignment.setReason(po.getReason());
        assignment.setMetadata(fromObjectJson(po.getMetadataJson()));
        assignment.setCreatedAt(po.getCreatedAt());
        assignment.setUpdatedAt(po.getUpdatedAt());
        return assignment;
    }





    public SupplyProfilePo toSupplyProfilePo(SupplyProfile profile) {
        SupplyProfilePo po = new SupplyProfilePo();
        po.setTenantId(profile.getTenantId());
        po.setSupplyProfileId(profile.getSupplyProfileId());
        po.setProfileCode(profile.getProfileCode());
        po.setProfileName(profile.getProfileName());
        po.setAgentId(profile.getAgentId());
        po.setRuntimeBindingId(profile.getRuntimeBindingId());
        po.setRuntimeId(profile.getRuntimeId());
        po.setServiceRole(profile.getServiceRole());
        po.setServiceLevel(profile.getServiceLevel());
        po.setQualityGrade(profile.getQualityGrade());
        po.setRiskLimit(profile.getRiskLimit());
        po.setDataScope(profile.getDataScope());
        po.setCapacityPolicy(profile.getCapacityPolicy());
        po.setStatus(profile.getStatus());
        po.setEffectiveFrom(profile.getEffectiveFrom());
        po.setExpiresAt(profile.getExpiresAt());
        po.setCapabilitySnapshotJson(toStringListJson(profile.getCapabilitySnapshot()));
        po.setRuntimeFeatureSnapshotJson(toStringListJson(profile.getRuntimeFeatureSnapshot()));
        po.setQualitySnapshotJson(toObjectJson(profile.getQualitySnapshot()));
        po.setMetadataJson(toObjectJson(profile.getMetadata()));
        po.setCreatedAt(profile.getCreatedAt());
        po.setUpdatedAt(profile.getUpdatedAt());
        return po;
    }

    public SupplyProfile toSupplyProfile(SupplyProfilePo po) {
        SupplyProfile profile = new SupplyProfile();
        profile.setTenantId(po.getTenantId());
        profile.setSupplyProfileId(po.getSupplyProfileId());
        profile.setProfileCode(po.getProfileCode());
        profile.setProfileName(po.getProfileName());
        profile.setAgentId(po.getAgentId());
        profile.setRuntimeBindingId(po.getRuntimeBindingId());
        profile.setRuntimeId(po.getRuntimeId());
        profile.setServiceRole(po.getServiceRole());
        profile.setServiceLevel(po.getServiceLevel());
        profile.setQualityGrade(po.getQualityGrade());
        profile.setRiskLimit(po.getRiskLimit());
        profile.setDataScope(po.getDataScope());
        profile.setCapacityPolicy(po.getCapacityPolicy());
        profile.setStatus(po.getStatus());
        profile.setEffectiveFrom(po.getEffectiveFrom());
        profile.setExpiresAt(po.getExpiresAt());
        profile.setCapabilitySnapshot(fromStringListJson(po.getCapabilitySnapshotJson()));
        profile.setRuntimeFeatureSnapshot(fromStringListJson(po.getRuntimeFeatureSnapshotJson()));
        profile.setQualitySnapshot(fromObjectJson(po.getQualitySnapshotJson()));
        profile.setMetadata(fromObjectJson(po.getMetadataJson()));
        profile.setCreatedAt(po.getCreatedAt());
        profile.setUpdatedAt(po.getUpdatedAt());
        return profile;
    }


    public RuntimeResourcePo toRuntimeResourcePo(RuntimeResource resource) {
        RuntimeResourcePo po = new RuntimeResourcePo();
        po.setTenantId(resource.getTenantId());
        po.setRuntimeId(resource.getRuntimeId());
        po.setRuntimeCode(resource.getRuntimeCode());
        po.setRuntimeName(resource.getRuntimeName());
        po.setRuntimeType(resource.getRuntimeType());
        po.setConnectorType(resource.getConnectorType());
        po.setExecutionHost(resource.getExecutionHost());
        po.setEnvironment(resource.getEnvironment());
        po.setRegion(resource.getRegion());
        po.setZone(resource.getZone());
        po.setTrustStatus(resource.getTrustStatus());
        po.setStatus(resource.getStatus());
        po.setCapacityLimit(resource.getCapacityLimit());
        po.setMetadataJson(toObjectJson(resource.getMetadata()));
        po.setCreatedAt(resource.getCreatedAt());
        po.setUpdatedAt(resource.getUpdatedAt());
        return po;
    }

    public RuntimeResource toRuntimeResource(RuntimeResourcePo po) {
        RuntimeResource resource = new RuntimeResource();
        resource.setTenantId(po.getTenantId());
        resource.setRuntimeId(po.getRuntimeId());
        resource.setRuntimeCode(po.getRuntimeCode());
        resource.setRuntimeName(po.getRuntimeName());
        resource.setRuntimeType(po.getRuntimeType());
        resource.setConnectorType(po.getConnectorType());
        resource.setExecutionHost(po.getExecutionHost());
        resource.setEnvironment(po.getEnvironment());
        resource.setRegion(po.getRegion());
        resource.setZone(po.getZone());
        resource.setTrustStatus(po.getTrustStatus());
        resource.setStatus(po.getStatus());
        resource.setCapacityLimit(po.getCapacityLimit() <= 0 ? 1 : po.getCapacityLimit());
        resource.setMetadata(fromObjectJson(po.getMetadataJson()));
        resource.setCreatedAt(po.getCreatedAt());
        resource.setUpdatedAt(po.getUpdatedAt());
        return resource;
    }

    public AgentRuntimeBindingPo toRuntimeBindingPo(AgentRuntimeBinding binding) {
        AgentRuntimeBindingPo po = new AgentRuntimeBindingPo();
        po.setTenantId(binding.getTenantId());
        po.setBindingId(binding.getBindingId());
        po.setAgentId(binding.getAgentId());
        po.setRuntimeId(binding.getRuntimeId());
        po.setRuntimeCode(binding.getRuntimeCode());
        po.setBindingStatus(binding.getBindingStatus());
        po.setVerifiedBy(binding.getVerifiedBy());
        po.setVerifiedAt(binding.getVerifiedAt());
        po.setApprovedBy(binding.getApprovedBy());
        po.setApprovedAt(binding.getApprovedAt());
        po.setEffectiveFrom(binding.getEffectiveFrom());
        po.setExpiresAt(binding.getExpiresAt());
        po.setCapacityLimit(binding.getCapacityLimit());
        po.setRegion(binding.getRegion());
        po.setZone(binding.getZone());
        po.setDataScope(binding.getDataScope());
        po.setRiskLimit(binding.getRiskLimit());
        po.setMetadataJson(toObjectJson(binding.getMetadata()));
        po.setCreatedAt(binding.getCreatedAt());
        po.setUpdatedAt(binding.getUpdatedAt());
        return po;
    }

    public AgentRuntimeBinding toRuntimeBinding(AgentRuntimeBindingPo po) {
        AgentRuntimeBinding binding = new AgentRuntimeBinding();
        binding.setTenantId(po.getTenantId());
        binding.setBindingId(po.getBindingId());
        binding.setAgentId(po.getAgentId());
        binding.setRuntimeId(po.getRuntimeId());
        binding.setRuntimeCode(po.getRuntimeCode());
        binding.setBindingStatus(po.getBindingStatus());
        binding.setVerifiedBy(po.getVerifiedBy());
        binding.setVerifiedAt(po.getVerifiedAt());
        binding.setApprovedBy(po.getApprovedBy());
        binding.setApprovedAt(po.getApprovedAt());
        binding.setEffectiveFrom(po.getEffectiveFrom());
        binding.setExpiresAt(po.getExpiresAt());
        binding.setCapacityLimit(po.getCapacityLimit() <= 0 ? 1 : po.getCapacityLimit());
        binding.setRegion(po.getRegion());
        binding.setZone(po.getZone());
        binding.setDataScope(po.getDataScope());
        binding.setRiskLimit(po.getRiskLimit());
        binding.setMetadata(fromObjectJson(po.getMetadataJson()));
        binding.setCreatedAt(po.getCreatedAt());
        binding.setUpdatedAt(po.getUpdatedAt());
        return binding;
    }

    public RuntimeFeatureCatalogPo toRuntimeFeatureCatalogPo(RuntimeFeatureCatalog feature) {
        RuntimeFeatureCatalogPo po = new RuntimeFeatureCatalogPo();
        po.setTenantId(feature.getTenantId());
        po.setFeatureId(feature.getFeatureId());
        po.setFeatureCode(feature.getFeatureCode());
        po.setFeatureName(feature.getFeatureName());
        po.setCategory(feature.getCategory());
        po.setDescription(feature.getDescription());
        po.setStatus(feature.getStatus());
        po.setVersion(feature.getVersion());
        po.setRequiresProbe(feature.isRequiresProbe());
        po.setRequiresTrustApproval(feature.isRequiresTrustApproval());
        po.setDispatchEligible(feature.isDispatchEligible());
        po.setOwnerTeam(feature.getOwnerTeam());
        po.setEffectiveFrom(feature.getEffectiveFrom());
        po.setRetiredAt(feature.getRetiredAt());
        po.setMetadataJson(toObjectJson(feature.getMetadata()));
        po.setCreatedAt(feature.getCreatedAt());
        po.setUpdatedAt(feature.getUpdatedAt());
        return po;
    }

    public RuntimeFeatureCatalog toRuntimeFeatureCatalog(RuntimeFeatureCatalogPo po) {
        RuntimeFeatureCatalog feature = new RuntimeFeatureCatalog();
        feature.setTenantId(po.getTenantId());
        feature.setFeatureId(po.getFeatureId());
        feature.setFeatureCode(po.getFeatureCode());
        feature.setFeatureName(po.getFeatureName());
        feature.setCategory(po.getCategory());
        feature.setDescription(po.getDescription());
        feature.setStatus(po.getStatus());
        feature.setVersion(po.getVersion() <= 0 ? 1 : po.getVersion());
        feature.setRequiresProbe(po.isRequiresProbe());
        feature.setRequiresTrustApproval(po.isRequiresTrustApproval());
        feature.setDispatchEligible(po.isDispatchEligible());
        feature.setOwnerTeam(po.getOwnerTeam());
        feature.setEffectiveFrom(po.getEffectiveFrom());
        feature.setRetiredAt(po.getRetiredAt());
        feature.setMetadata(fromObjectJson(po.getMetadataJson()));
        feature.setCreatedAt(po.getCreatedAt());
        feature.setUpdatedAt(po.getUpdatedAt());
        return feature;
    }

    public AgentRuntimeFeatureObservationPo toRuntimeFeatureObservationPo(AgentRuntimeFeatureObservation observation) {
        AgentRuntimeFeatureObservationPo po = new AgentRuntimeFeatureObservationPo();
        po.setTenantId(observation.getTenantId());
        po.setObservationId(observation.getObservationId());
        po.setAgentId(observation.getAgentId());
        po.setFeatureCode(observation.getFeatureCode());
        po.setFeatureName(observation.getFeatureName());
        po.setObservedValue(observation.getObservedValue());
        po.setSource(observation.getSource());
        po.setProbeResult(observation.getProbeResult());
        po.setObservedAt(observation.getObservedAt());
        po.setMetadataJson(toObjectJson(observation.getMetadata()));
        po.setCreatedAt(observation.getCreatedAt());
        po.setUpdatedAt(observation.getUpdatedAt());
        return po;
    }

    public AgentRuntimeFeatureObservation toRuntimeFeatureObservation(AgentRuntimeFeatureObservationPo po) {
        AgentRuntimeFeatureObservation observation = new AgentRuntimeFeatureObservation();
        observation.setTenantId(po.getTenantId());
        observation.setObservationId(po.getObservationId());
        observation.setAgentId(po.getAgentId());
        observation.setFeatureCode(po.getFeatureCode());
        observation.setFeatureName(po.getFeatureName());
        observation.setObservedValue(po.getObservedValue());
        observation.setSource(po.getSource());
        observation.setProbeResult(po.getProbeResult());
        observation.setObservedAt(po.getObservedAt());
        observation.setMetadata(fromObjectJson(po.getMetadataJson()));
        observation.setCreatedAt(po.getCreatedAt());
        observation.setUpdatedAt(po.getUpdatedAt());
        return observation;
    }

    public AgentRuntimeFeatureTrustPo toRuntimeFeatureTrustPo(AgentRuntimeFeatureTrust trust) {
        AgentRuntimeFeatureTrustPo po = new AgentRuntimeFeatureTrustPo();
        po.setTenantId(trust.getTenantId());
        po.setTrustId(trust.getTrustId());
        po.setAgentId(trust.getAgentId());
        po.setRuntimeId(trust.getRuntimeId());
        po.setBindingId(trust.getBindingId());
        po.setFeatureCode(trust.getFeatureCode());
        po.setFeatureName(trust.getFeatureName());
        po.setTrustStatus(trust.getTrustStatus() == null ? null : trust.getTrustStatus().name());
        po.setSource(trust.getSource());
        po.setObservedAt(trust.getObservedAt());
        po.setVerifiedBy(trust.getVerifiedBy());
        po.setVerifiedAt(trust.getVerifiedAt());
        po.setTrustedBy(trust.getTrustedBy());
        po.setTrustedAt(trust.getTrustedAt());
        po.setRevokedBy(trust.getRevokedBy());
        po.setRevokedAt(trust.getRevokedAt());
        po.setExpiresAt(trust.getExpiresAt());
        po.setEvidenceRef(trust.getEvidenceRef());
        po.setReason(trust.getReason());
        po.setMetadataJson(toObjectJson(trust.getMetadata()));
        po.setCreatedAt(trust.getCreatedAt());
        po.setUpdatedAt(trust.getUpdatedAt());
        return po;
    }

    public AgentRuntimeFeatureTrust toRuntimeFeatureTrust(AgentRuntimeFeatureTrustPo po) {
        AgentRuntimeFeatureTrust trust = new AgentRuntimeFeatureTrust();
        trust.setTenantId(po.getTenantId());
        trust.setTrustId(po.getTrustId());
        trust.setAgentId(po.getAgentId());
        trust.setRuntimeId(po.getRuntimeId());
        trust.setBindingId(po.getBindingId());
        trust.setFeatureCode(po.getFeatureCode());
        trust.setFeatureName(po.getFeatureName());
        trust.setTrustStatus(po.getTrustStatus() == null ? null : AgentRuntimeFeatureTrustStatus.valueOf(po.getTrustStatus()));
        trust.setSource(po.getSource());
        trust.setObservedAt(po.getObservedAt());
        trust.setVerifiedBy(po.getVerifiedBy());
        trust.setVerifiedAt(po.getVerifiedAt());
        trust.setTrustedBy(po.getTrustedBy());
        trust.setTrustedAt(po.getTrustedAt());
        trust.setRevokedBy(po.getRevokedBy());
        trust.setRevokedAt(po.getRevokedAt());
        trust.setExpiresAt(po.getExpiresAt());
        trust.setEvidenceRef(po.getEvidenceRef());
        trust.setReason(po.getReason());
        trust.setMetadata(fromObjectJson(po.getMetadataJson()));
        trust.setCreatedAt(po.getCreatedAt());
        trust.setUpdatedAt(po.getUpdatedAt());
        return trust;
    }


    public AgentQualityMetricsDailyPo toAgentQualityMetricsDailyPo(AgentQualityMetricsDaily metrics) {
        AgentQualityMetricsDailyPo po = new AgentQualityMetricsDailyPo();
        copyQualityToPo(metrics, po);
        po.setMetricDate(metrics.getMetricDate());
        return po;
    }

    public AgentQualityMetricsDaily toAgentQualityMetricsDaily(AgentQualityMetricsDailyPo po) {
        AgentQualityMetricsDaily metrics = new AgentQualityMetricsDaily();
        copyQualityFromPo(po, metrics);
        metrics.setMetricDate(po.getMetricDate());
        return metrics;
    }

    public AgentQualityMetricsWindowPo toAgentQualityMetricsWindowPo(AgentQualityMetricsWindow metrics) {
        AgentQualityMetricsWindowPo po = new AgentQualityMetricsWindowPo();
        copyQualityToPo(metrics, po);
        return po;
    }

    public AgentQualityMetricsWindow toAgentQualityMetricsWindow(AgentQualityMetricsWindowPo po) {
        AgentQualityMetricsWindow metrics = new AgentQualityMetricsWindow();
        copyQualityFromPo(po, metrics);
        return metrics;
    }

    public RuntimeQualityMetricsDailyPo toRuntimeQualityMetricsDailyPo(RuntimeQualityMetricsDaily metrics) {
        RuntimeQualityMetricsDailyPo po = new RuntimeQualityMetricsDailyPo();
        copyQualityToPo(metrics, po);
        po.setMetricDate(metrics.getMetricDate());
        return po;
    }

    public RuntimeQualityMetricsDaily toRuntimeQualityMetricsDaily(RuntimeQualityMetricsDailyPo po) {
        RuntimeQualityMetricsDaily metrics = new RuntimeQualityMetricsDaily();
        copyQualityFromPo(po, metrics);
        metrics.setMetricDate(po.getMetricDate());
        return metrics;
    }

    public SupplyProfileQualitySnapshotPo toSupplyProfileQualitySnapshotPo(SupplyProfileQualitySnapshot snapshot) {
        SupplyProfileQualitySnapshotPo po = new SupplyProfileQualitySnapshotPo();
        copyQualityToPo(snapshot, po);
        po.setSnapshotId(snapshot.getSnapshotId());
        return po;
    }

    public SupplyProfileQualitySnapshot toSupplyProfileQualitySnapshot(SupplyProfileQualitySnapshotPo po) {
        SupplyProfileQualitySnapshot snapshot = new SupplyProfileQualitySnapshot();
        copyQualityFromPo(po, snapshot);
        snapshot.setSnapshotId(po.getSnapshotId());
        return snapshot;
    }

    private void copyQualityToPo(AgentQualityMetricsWindow metrics, AgentQualityMetricsWindowPo po) {
        po.setTenantId(metrics.getTenantId());
        po.setMetricId(metrics.getMetricId());
        po.setAgentId(metrics.getAgentId());
        po.setRuntimeId(metrics.getRuntimeId());
        po.setBindingId(metrics.getBindingId());
        po.setSupplyProfileId(metrics.getSupplyProfileId());
        po.setProfileCode(metrics.getProfileCode());
        po.setMetricWindow(metrics.getMetricWindow());
        po.setWindowStart(metrics.getWindowStart());
        po.setWindowEnd(metrics.getWindowEnd());
        po.setSuccessRate(metrics.getSuccessRate());
        po.setFailureRate(metrics.getFailureRate());
        po.setTimeoutRate(metrics.getTimeoutRate());
        po.setSlaBreachRate(metrics.getSlaBreachRate());
        po.setAvgAckLatencyMs(metrics.getAvgAckLatencyMs());
        po.setAvgCompletionLatencyMs(metrics.getAvgCompletionLatencyMs());
        po.setRecentFailureCount(metrics.getRecentFailureCount());
        po.setManualRating(metrics.getManualRating());
        po.setQualityGrade(metrics.getQualityGrade());
        po.setRiskPenalty(metrics.getRiskPenalty());
        po.setScore(metrics.getScore());
        po.setSampleSize(metrics.getSampleSize());
        po.setCalculatedAt(metrics.getCalculatedAt());
        po.setSource(metrics.getSource());
        po.setMetadataJson(toObjectJson(metrics.getMetadata()));
        po.setCreatedAt(metrics.getCreatedAt());
        po.setUpdatedAt(metrics.getUpdatedAt());
    }

    private void copyQualityFromPo(AgentQualityMetricsWindowPo po, AgentQualityMetricsWindow metrics) {
        metrics.setTenantId(po.getTenantId());
        metrics.setMetricId(po.getMetricId());
        metrics.setAgentId(po.getAgentId());
        metrics.setRuntimeId(po.getRuntimeId());
        metrics.setBindingId(po.getBindingId());
        metrics.setSupplyProfileId(po.getSupplyProfileId());
        metrics.setProfileCode(po.getProfileCode());
        metrics.setMetricWindow(po.getMetricWindow());
        metrics.setWindowStart(po.getWindowStart());
        metrics.setWindowEnd(po.getWindowEnd());
        metrics.setSuccessRate(po.getSuccessRate());
        metrics.setFailureRate(po.getFailureRate());
        metrics.setTimeoutRate(po.getTimeoutRate());
        metrics.setSlaBreachRate(po.getSlaBreachRate());
        metrics.setAvgAckLatencyMs(po.getAvgAckLatencyMs());
        metrics.setAvgCompletionLatencyMs(po.getAvgCompletionLatencyMs());
        metrics.setRecentFailureCount(po.getRecentFailureCount());
        metrics.setManualRating(po.getManualRating());
        metrics.setQualityGrade(po.getQualityGrade());
        metrics.setRiskPenalty(po.getRiskPenalty());
        metrics.setScore(po.getScore());
        metrics.setSampleSize(po.getSampleSize());
        metrics.setCalculatedAt(po.getCalculatedAt());
        metrics.setSource(po.getSource());
        metrics.setMetadata(fromObjectJson(po.getMetadataJson()));
        metrics.setCreatedAt(po.getCreatedAt());
        metrics.setUpdatedAt(po.getUpdatedAt());
    }

    private void copyQualityToPo(AgentQualityMetricsDaily metrics, AgentQualityMetricsDailyPo po) {
        AgentQualityMetricsWindow window = new AgentQualityMetricsWindow();
        copyQualityDailyToWindow(metrics, window);
        copyQualityToPo(window, po);
    }

    private void copyQualityFromPo(AgentQualityMetricsDailyPo po, AgentQualityMetricsDaily metrics) {
        AgentQualityMetricsWindow window = new AgentQualityMetricsWindow();
        copyQualityFromPo((AgentQualityMetricsWindowPo) po, window);
        copyQualityWindowToDaily(window, metrics);
    }

    private void copyQualityToPo(RuntimeQualityMetricsDaily metrics, RuntimeQualityMetricsDailyPo po) {
        AgentQualityMetricsWindow window = new AgentQualityMetricsWindow();
        copyQualityRuntimeToWindow(metrics, window);
        copyQualityToPo(window, po);
    }

    private void copyQualityFromPo(RuntimeQualityMetricsDailyPo po, RuntimeQualityMetricsDaily metrics) {
        AgentQualityMetricsWindow window = new AgentQualityMetricsWindow();
        copyQualityFromPo((AgentQualityMetricsWindowPo) po, window);
        copyQualityWindowToRuntime(window, metrics);
    }

    private void copyQualityToPo(SupplyProfileQualitySnapshot snapshot, SupplyProfileQualitySnapshotPo po) {
        AgentQualityMetricsWindow window = new AgentQualityMetricsWindow();
        copyQualitySnapshotToWindow(snapshot, window);
        copyQualityToPo(window, po);
    }

    private void copyQualityFromPo(SupplyProfileQualitySnapshotPo po, SupplyProfileQualitySnapshot snapshot) {
        AgentQualityMetricsWindow window = new AgentQualityMetricsWindow();
        copyQualityFromPo((AgentQualityMetricsWindowPo) po, window);
        copyQualityWindowToSnapshot(window, snapshot);
    }

    private void copyQualityDailyToWindow(AgentQualityMetricsDaily daily, AgentQualityMetricsWindow window) {
        window.setTenantId(daily.getTenantId()); window.setMetricId(daily.getMetricId()); window.setAgentId(daily.getAgentId());
        window.setRuntimeId(daily.getRuntimeId()); window.setBindingId(daily.getBindingId()); window.setSupplyProfileId(daily.getSupplyProfileId());
        window.setProfileCode(daily.getProfileCode()); window.setMetricWindow(daily.getMetricWindow()); window.setWindowStart(daily.getWindowStart()); window.setWindowEnd(daily.getWindowEnd());
        window.setSuccessRate(daily.getSuccessRate()); window.setFailureRate(daily.getFailureRate()); window.setTimeoutRate(daily.getTimeoutRate()); window.setSlaBreachRate(daily.getSlaBreachRate());
        window.setAvgAckLatencyMs(daily.getAvgAckLatencyMs()); window.setAvgCompletionLatencyMs(daily.getAvgCompletionLatencyMs()); window.setRecentFailureCount(daily.getRecentFailureCount());
        window.setManualRating(daily.getManualRating()); window.setQualityGrade(daily.getQualityGrade()); window.setRiskPenalty(daily.getRiskPenalty()); window.setScore(daily.getScore()); window.setSampleSize(daily.getSampleSize());
        window.setCalculatedAt(daily.getCalculatedAt()); window.setSource(daily.getSource()); window.setMetadata(daily.getMetadata()); window.setCreatedAt(daily.getCreatedAt()); window.setUpdatedAt(daily.getUpdatedAt());
    }

    private void copyQualityWindowToDaily(AgentQualityMetricsWindow window, AgentQualityMetricsDaily daily) {
        daily.setTenantId(window.getTenantId()); daily.setMetricId(window.getMetricId()); daily.setAgentId(window.getAgentId()); daily.setRuntimeId(window.getRuntimeId()); daily.setBindingId(window.getBindingId()); daily.setSupplyProfileId(window.getSupplyProfileId()); daily.setProfileCode(window.getProfileCode()); daily.setMetricWindow(window.getMetricWindow()); daily.setWindowStart(window.getWindowStart()); daily.setWindowEnd(window.getWindowEnd());
        daily.setSuccessRate(window.getSuccessRate()); daily.setFailureRate(window.getFailureRate()); daily.setTimeoutRate(window.getTimeoutRate()); daily.setSlaBreachRate(window.getSlaBreachRate()); daily.setAvgAckLatencyMs(window.getAvgAckLatencyMs()); daily.setAvgCompletionLatencyMs(window.getAvgCompletionLatencyMs()); daily.setRecentFailureCount(window.getRecentFailureCount()); daily.setManualRating(window.getManualRating()); daily.setQualityGrade(window.getQualityGrade()); daily.setRiskPenalty(window.getRiskPenalty()); daily.setScore(window.getScore()); daily.setSampleSize(window.getSampleSize());
        daily.setCalculatedAt(window.getCalculatedAt()); daily.setSource(window.getSource()); daily.setMetadata(window.getMetadata()); daily.setCreatedAt(window.getCreatedAt()); daily.setUpdatedAt(window.getUpdatedAt());
    }
    private void copyQualityRuntimeToWindow(RuntimeQualityMetricsDaily runtime, AgentQualityMetricsWindow window) { window.setTenantId(runtime.getTenantId()); window.setMetricId(runtime.getMetricId()); window.setRuntimeId(runtime.getRuntimeId()); window.setMetricWindow(runtime.getMetricWindow()); window.setWindowStart(runtime.getWindowStart()); window.setWindowEnd(runtime.getWindowEnd()); window.setSuccessRate(runtime.getSuccessRate()); window.setFailureRate(runtime.getFailureRate()); window.setTimeoutRate(runtime.getTimeoutRate()); window.setSlaBreachRate(runtime.getSlaBreachRate()); window.setAvgAckLatencyMs(runtime.getAvgAckLatencyMs()); window.setAvgCompletionLatencyMs(runtime.getAvgCompletionLatencyMs()); window.setRecentFailureCount(runtime.getRecentFailureCount()); window.setQualityGrade(runtime.getQualityGrade()); window.setRiskPenalty(runtime.getRiskPenalty()); window.setScore(runtime.getScore()); window.setSampleSize(runtime.getSampleSize()); window.setCalculatedAt(runtime.getCalculatedAt()); window.setSource(runtime.getSource()); window.setMetadata(runtime.getMetadata()); window.setCreatedAt(runtime.getCreatedAt()); window.setUpdatedAt(runtime.getUpdatedAt()); }
    private void copyQualityWindowToRuntime(AgentQualityMetricsWindow window, RuntimeQualityMetricsDaily runtime) { runtime.setTenantId(window.getTenantId()); runtime.setMetricId(window.getMetricId()); runtime.setRuntimeId(window.getRuntimeId()); runtime.setMetricWindow(window.getMetricWindow()); runtime.setWindowStart(window.getWindowStart()); runtime.setWindowEnd(window.getWindowEnd()); runtime.setSuccessRate(window.getSuccessRate()); runtime.setFailureRate(window.getFailureRate()); runtime.setTimeoutRate(window.getTimeoutRate()); runtime.setSlaBreachRate(window.getSlaBreachRate()); runtime.setAvgAckLatencyMs(window.getAvgAckLatencyMs()); runtime.setAvgCompletionLatencyMs(window.getAvgCompletionLatencyMs()); runtime.setRecentFailureCount(window.getRecentFailureCount()); runtime.setQualityGrade(window.getQualityGrade()); runtime.setRiskPenalty(window.getRiskPenalty()); runtime.setScore(window.getScore()); runtime.setSampleSize(window.getSampleSize()); runtime.setCalculatedAt(window.getCalculatedAt()); runtime.setSource(window.getSource()); runtime.setMetadata(window.getMetadata()); runtime.setCreatedAt(window.getCreatedAt()); runtime.setUpdatedAt(window.getUpdatedAt()); }
    private void copyQualitySnapshotToWindow(SupplyProfileQualitySnapshot snapshot, AgentQualityMetricsWindow window) { window.setTenantId(snapshot.getTenantId()); window.setMetricId(snapshot.getMetricId()); window.setAgentId(snapshot.getAgentId()); window.setRuntimeId(snapshot.getRuntimeId()); window.setBindingId(snapshot.getBindingId()); window.setSupplyProfileId(snapshot.getSupplyProfileId()); window.setProfileCode(snapshot.getProfileCode()); window.setMetricWindow(snapshot.getMetricWindow()); window.setWindowStart(snapshot.getWindowStart()); window.setWindowEnd(snapshot.getWindowEnd()); window.setSuccessRate(snapshot.getSuccessRate()); window.setFailureRate(snapshot.getFailureRate()); window.setTimeoutRate(snapshot.getTimeoutRate()); window.setSlaBreachRate(snapshot.getSlaBreachRate()); window.setAvgAckLatencyMs(snapshot.getAvgAckLatencyMs()); window.setAvgCompletionLatencyMs(snapshot.getAvgCompletionLatencyMs()); window.setRecentFailureCount(snapshot.getRecentFailureCount()); window.setManualRating(snapshot.getManualRating()); window.setQualityGrade(snapshot.getQualityGrade()); window.setRiskPenalty(snapshot.getRiskPenalty()); window.setScore(snapshot.getScore()); window.setSampleSize(snapshot.getSampleSize()); window.setCalculatedAt(snapshot.getCalculatedAt()); window.setSource(snapshot.getSource()); window.setMetadata(snapshot.getMetadata()); window.setCreatedAt(snapshot.getCreatedAt()); window.setUpdatedAt(snapshot.getUpdatedAt()); }
    private void copyQualityWindowToSnapshot(AgentQualityMetricsWindow window, SupplyProfileQualitySnapshot snapshot) { snapshot.setTenantId(window.getTenantId()); snapshot.setMetricId(window.getMetricId()); snapshot.setAgentId(window.getAgentId()); snapshot.setRuntimeId(window.getRuntimeId()); snapshot.setBindingId(window.getBindingId()); snapshot.setSupplyProfileId(window.getSupplyProfileId()); snapshot.setProfileCode(window.getProfileCode()); snapshot.setMetricWindow(window.getMetricWindow()); snapshot.setWindowStart(window.getWindowStart()); snapshot.setWindowEnd(window.getWindowEnd()); snapshot.setSuccessRate(window.getSuccessRate()); snapshot.setFailureRate(window.getFailureRate()); snapshot.setTimeoutRate(window.getTimeoutRate()); snapshot.setSlaBreachRate(window.getSlaBreachRate()); snapshot.setAvgAckLatencyMs(window.getAvgAckLatencyMs()); snapshot.setAvgCompletionLatencyMs(window.getAvgCompletionLatencyMs()); snapshot.setRecentFailureCount(window.getRecentFailureCount()); snapshot.setManualRating(window.getManualRating()); snapshot.setQualityGrade(window.getQualityGrade()); snapshot.setRiskPenalty(window.getRiskPenalty()); snapshot.setScore(window.getScore()); snapshot.setSampleSize(window.getSampleSize()); snapshot.setCalculatedAt(window.getCalculatedAt()); snapshot.setSource(window.getSource()); snapshot.setMetadata(window.getMetadata()); snapshot.setCreatedAt(window.getCreatedAt()); snapshot.setUpdatedAt(window.getUpdatedAt()); }

    private String toStringListJson(List<String> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private List<String> fromStringListJson(String json) {
        try {
            if (json == null || json.isBlank()) return List.of();
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String toObjectJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private Map<String, Object> fromObjectJson(String json) {
        try {
            if (json == null || json.isBlank()) return Map.of();
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }
}
