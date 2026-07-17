package com.opensocket.aievent.database.persistence.agent.skill.converter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.opensocket.aievent.core.agent.skill.AgentSkillDefinition;
import com.opensocket.aievent.core.agent.skill.AgentSkillAuditEntry;
import com.opensocket.aievent.core.agent.skill.AgentSkillLifecycleStatus;
import com.opensocket.aievent.core.agent.skill.AgentSkillVersion;
import com.opensocket.aievent.core.agent.skill.AgentSkillApprovalPolicy;
import com.opensocket.aievent.core.agent.skill.AgentApprovedSkill;
import com.opensocket.aievent.core.agent.skill.AgentSkillDeprecationPlan;
import com.opensocket.aievent.core.agent.skill.AgentSkillDependencyEdge;
import com.opensocket.aievent.database.persistence.agent.skill.po.AgentSkillDefinitionPo;
import com.opensocket.aievent.database.persistence.agent.skill.po.AgentSkillAuditEntryPo;
import com.opensocket.aievent.database.persistence.agent.skill.po.AgentSkillVersionPo;
import com.opensocket.aievent.database.persistence.agent.skill.po.AgentSkillApprovalPolicyPo;
import com.opensocket.aievent.database.persistence.agent.skill.po.AgentApprovedSkillPo;
import com.opensocket.aievent.database.persistence.agent.skill.po.AgentSkillDeprecationPlanPo;
import com.opensocket.aievent.database.persistence.agent.skill.po.AgentSkillDependencyEdgePo;
import com.opensocket.aievent.database.persistence.spi.DatabasePersistenceConverter;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@DatabasePersistenceConverter
@ConditionalOnProperty(prefix = "agent-directory", name = "store", havingValue = "MYBATIS")
public class AgentSkillRegistryPersistenceConverter {
    private final ObjectMapper objectMapper;

    public AgentSkillRegistryPersistenceConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AgentSkillDefinitionPo toPo(AgentSkillDefinition skill) {
        AgentSkillDefinitionPo po = new AgentSkillDefinitionPo();
        po.setSkillCode(skill.getSkillCode());
        po.setDisplayName(skill.getDisplayName());
        po.setDomain(skill.getDomain());
        po.setDescription(skill.getDescription());
        po.setTaxonomyVersion(skill.getTaxonomyVersion() == null ? "opensocket-capability-taxonomy/v1" : skill.getTaxonomyVersion());
        po.setTaskDefinitionId(skill.getTaskDefinitionId());
        po.setSourceSystem(skill.getSourceSystem());
        po.setTaskType(skill.getTaskType());
        po.setProvidersJson(toStringListJson(skill.getProviders()));
        po.setTaskTypesJson(toStringListJson(skill.getTaskTypes()));
        po.setOperationsJson(toStringListJson(skill.getOperations()));
        po.setToolPoliciesJson(toStringListJson(skill.getToolPolicies()));
        po.setResourceScopesJson(toStringListJson(skill.getResourceScopes()));
        po.setDataClassesJson(toStringListJson(skill.getDataClasses()));
        po.setRiskLevel(skill.getRiskLevel() == null || skill.getRiskLevel().isBlank() ? "LOW" : skill.getRiskLevel());
        po.setRequiresHumanApproval(skill.isRequiresHumanApproval());
        po.setMaskingRequired(skill.isMaskingRequired());
        po.setEnabled(skill.isEnabled());
        po.setMetadataJson(toObjectJson(skill.getMetadata()));
        po.setCreatedAt(skill.getCreatedAt());
        po.setUpdatedAt(skill.getUpdatedAt());
        return po;
    }

    public AgentSkillDefinition toDomain(AgentSkillDefinitionPo po) {
        AgentSkillDefinition skill = new AgentSkillDefinition();
        skill.setSkillCode(po.getSkillCode());
        skill.setDisplayName(po.getDisplayName());
        skill.setDomain(po.getDomain());
        skill.setDescription(po.getDescription());
        skill.setTaxonomyVersion(po.getTaxonomyVersion());
        skill.setTaskDefinitionId(po.getTaskDefinitionId());
        skill.setSourceSystem(po.getSourceSystem());
        skill.setTaskType(po.getTaskType());
        skill.setProviders(fromStringListJson(po.getProvidersJson()));
        skill.setTaskTypes(fromStringListJson(po.getTaskTypesJson()));
        skill.setOperations(fromStringListJson(po.getOperationsJson()));
        skill.setToolPolicies(fromStringListJson(po.getToolPoliciesJson()));
        skill.setResourceScopes(fromStringListJson(po.getResourceScopesJson()));
        skill.setDataClasses(fromStringListJson(po.getDataClassesJson()));
        skill.setRiskLevel(po.getRiskLevel());
        skill.setRequiresHumanApproval(po.isRequiresHumanApproval());
        skill.setMaskingRequired(po.isMaskingRequired());
        skill.setEnabled(po.isEnabled());
        skill.setMetadata(fromObjectJson(po.getMetadataJson()));
        skill.setCreatedAt(po.getCreatedAt());
        skill.setUpdatedAt(po.getUpdatedAt());
        return skill;
    }


    public AgentApprovedSkillPo toPo(AgentApprovedSkill skill) {
        AgentApprovedSkillPo po = new AgentApprovedSkillPo();
        po.setAgentId(skill.getAgentId());
        po.setSkillCode(skill.getSkillCode());
        po.setPolicyVersion(skill.getPolicyVersion());
        po.setEnabled(skill.isEnabled());
        po.setApprovedBy(skill.getApprovedBy());
        po.setApprovedAt(skill.getApprovedAt());
        po.setMetadataJson(toObjectJson(skill.getMetadata()));
        po.setCreatedAt(skill.getCreatedAt());
        po.setUpdatedAt(skill.getUpdatedAt());
        return po;
    }

    public AgentApprovedSkill toDomain(AgentApprovedSkillPo po) {
        AgentApprovedSkill skill = new AgentApprovedSkill();
        skill.setAgentId(po.getAgentId());
        skill.setSkillCode(po.getSkillCode());
        skill.setPolicyVersion(po.getPolicyVersion());
        skill.setEnabled(po.isEnabled());
        skill.setApprovedBy(po.getApprovedBy());
        skill.setApprovedAt(po.getApprovedAt());
        skill.setMetadata(fromObjectJson(po.getMetadataJson()));
        skill.setCreatedAt(po.getCreatedAt());
        skill.setUpdatedAt(po.getUpdatedAt());
        return skill;
    }



    public AgentSkillVersionPo toPo(AgentSkillVersion version) {
        AgentSkillVersionPo po = new AgentSkillVersionPo();
        po.setSkillCode(version.getSkillCode());
        po.setVersion(version.getVersion());
        po.setStatus(version.getStatus() == null ? "DRAFT" : version.getStatus().name());
        po.setDefinitionJson(toDefinitionJson(version.getDefinition()));
        po.setSubmittedBy(version.getSubmittedBy());
        po.setSubmittedAt(version.getSubmittedAt());
        po.setReviewedBy(version.getReviewedBy());
        po.setReviewedAt(version.getReviewedAt());
        po.setReviewComment(version.getReviewComment());
        po.setPublishedBy(version.getPublishedBy());
        po.setPublishedAt(version.getPublishedAt());
        po.setSupersedesVersion(version.getSupersedesVersion());
        po.setRollbackOfVersion(version.getRollbackOfVersion());
        po.setMetadataJson(toObjectJson(version.getMetadata()));
        po.setCreatedAt(version.getCreatedAt());
        po.setUpdatedAt(version.getUpdatedAt());
        return po;
    }

    public AgentSkillVersion toDomain(AgentSkillVersionPo po) {
        AgentSkillVersion version = new AgentSkillVersion();
        version.setSkillCode(po.getSkillCode());
        version.setVersion(po.getVersion());
        version.setStatus(parseStatus(po.getStatus()));
        version.setDefinition(fromDefinitionJson(po.getDefinitionJson()));
        version.setSubmittedBy(po.getSubmittedBy());
        version.setSubmittedAt(po.getSubmittedAt());
        version.setReviewedBy(po.getReviewedBy());
        version.setReviewedAt(po.getReviewedAt());
        version.setReviewComment(po.getReviewComment());
        version.setPublishedBy(po.getPublishedBy());
        version.setPublishedAt(po.getPublishedAt());
        version.setSupersedesVersion(po.getSupersedesVersion());
        version.setRollbackOfVersion(po.getRollbackOfVersion());
        version.setMetadata(fromObjectJson(po.getMetadataJson()));
        version.setCreatedAt(po.getCreatedAt());
        version.setUpdatedAt(po.getUpdatedAt());
        return version;
    }

    public AgentSkillAuditEntryPo toPo(AgentSkillAuditEntry entry) {
        AgentSkillAuditEntryPo po = new AgentSkillAuditEntryPo();
        po.setAuditId(entry.getAuditId());
        po.setSkillCode(entry.getSkillCode());
        po.setVersion(entry.getVersion());
        po.setAction(entry.getAction());
        po.setOperatorId(entry.getOperatorId());
        po.setReason(entry.getReason());
        po.setFromStatus(entry.getFromStatus() == null ? null : entry.getFromStatus().name());
        po.setToStatus(entry.getToStatus() == null ? null : entry.getToStatus().name());
        po.setMetadataJson(toObjectJson(entry.getMetadata()));
        po.setCreatedAt(entry.getCreatedAt());
        return po;
    }

    public AgentSkillAuditEntry toDomain(AgentSkillAuditEntryPo po) {
        AgentSkillAuditEntry entry = new AgentSkillAuditEntry();
        entry.setAuditId(po.getAuditId());
        entry.setSkillCode(po.getSkillCode());
        entry.setVersion(po.getVersion());
        entry.setAction(po.getAction());
        entry.setOperatorId(po.getOperatorId());
        entry.setReason(po.getReason());
        entry.setFromStatus(parseStatus(po.getFromStatus()));
        entry.setToStatus(parseStatus(po.getToStatus()));
        entry.setMetadata(fromObjectJson(po.getMetadataJson()));
        entry.setCreatedAt(po.getCreatedAt());
        return entry;
    }

    public AgentSkillApprovalPolicyPo toPo(AgentSkillApprovalPolicy policy) {
        AgentSkillApprovalPolicyPo po = new AgentSkillApprovalPolicyPo();
        po.setSkillCode(policy.getSkillCode());
        po.setEnabled(policy.isEnabled());
        po.setSubmitRolesJson(toStringListJson(policy.getSubmitRoles()));
        po.setApproveRolesJson(toStringListJson(policy.getApproveRoles()));
        po.setPublishRolesJson(toStringListJson(policy.getPublishRoles()));
        po.setRollbackRolesJson(toStringListJson(policy.getRollbackRoles()));
        po.setSeparationOfDuties(policy.isSeparationOfDuties());
        po.setUpdatedBy(policy.getUpdatedBy());
        po.setUpdatedAt(policy.getUpdatedAt());
        po.setMetadataJson(toObjectJson(policy.getMetadata()));
        return po;
    }

    public AgentSkillApprovalPolicy toDomain(AgentSkillApprovalPolicyPo po) {
        AgentSkillApprovalPolicy policy = new AgentSkillApprovalPolicy();
        policy.setSkillCode(po.getSkillCode());
        policy.setEnabled(po.isEnabled());
        policy.setSubmitRoles(fromStringListJson(po.getSubmitRolesJson()));
        policy.setApproveRoles(fromStringListJson(po.getApproveRolesJson()));
        policy.setPublishRoles(fromStringListJson(po.getPublishRolesJson()));
        policy.setRollbackRoles(fromStringListJson(po.getRollbackRolesJson()));
        policy.setSeparationOfDuties(po.isSeparationOfDuties());
        policy.setUpdatedBy(po.getUpdatedBy());
        policy.setUpdatedAt(po.getUpdatedAt());
        policy.setMetadata(fromObjectJson(po.getMetadataJson()));
        return policy;
    }


    public AgentSkillDeprecationPlanPo toPo(AgentSkillDeprecationPlan plan) {
        AgentSkillDeprecationPlanPo po = new AgentSkillDeprecationPlanPo();
        po.setSkillCode(plan.getSkillCode());
        po.setStatus(plan.getStatus() == null || plan.getStatus().isBlank() ? "PLANNED" : plan.getStatus());
        po.setReplacementSkillCodesJson(toStringListJson(plan.getReplacementSkillCodes()));
        po.setMigrationDeadline(plan.getMigrationDeadline());
        po.setCreatedBy(plan.getCreatedBy());
        po.setCreatedAt(plan.getCreatedAt());
        po.setUpdatedBy(plan.getUpdatedBy());
        po.setUpdatedAt(plan.getUpdatedAt());
        po.setMetadataJson(toObjectJson(plan.getMetadata()));
        return po;
    }

    public AgentSkillDeprecationPlan toDomain(AgentSkillDeprecationPlanPo po) {
        AgentSkillDeprecationPlan plan = new AgentSkillDeprecationPlan();
        plan.setSkillCode(po.getSkillCode());
        plan.setStatus(po.getStatus());
        plan.setReplacementSkillCodes(fromStringListJson(po.getReplacementSkillCodesJson()));
        plan.setMigrationDeadline(po.getMigrationDeadline());
        plan.setCreatedBy(po.getCreatedBy());
        plan.setCreatedAt(po.getCreatedAt());
        plan.setUpdatedBy(po.getUpdatedBy());
        plan.setUpdatedAt(po.getUpdatedAt());
        plan.setMetadata(fromObjectJson(po.getMetadataJson()));
        return plan;
    }



    public AgentSkillDependencyEdgePo toPo(AgentSkillDependencyEdge edge) {
        AgentSkillDependencyEdgePo po = new AgentSkillDependencyEdgePo();
        po.setEdgeId(edge.getEdgeId());
        po.setSourceSkillCode(edge.getSourceSkillCode());
        po.setTargetSkillCode(edge.getTargetSkillCode());
        po.setRelationType(edge.getRelationType());
        po.setRequired(edge.isRequired());
        po.setEnabled(edge.isEnabled());
        po.setConfidence(edge.getConfidence());
        po.setDescription(edge.getDescription());
        po.setCreatedBy(edge.getCreatedBy());
        po.setCreatedAt(edge.getCreatedAt());
        po.setUpdatedBy(edge.getUpdatedBy());
        po.setUpdatedAt(edge.getUpdatedAt());
        po.setMetadataJson(toObjectJson(edge.getMetadata()));
        return po;
    }

    public AgentSkillDependencyEdge toDomain(AgentSkillDependencyEdgePo po) {
        AgentSkillDependencyEdge edge = new AgentSkillDependencyEdge();
        edge.setEdgeId(po.getEdgeId());
        edge.setSourceSkillCode(po.getSourceSkillCode());
        edge.setTargetSkillCode(po.getTargetSkillCode());
        edge.setRelationType(po.getRelationType());
        edge.setRequired(po.isRequired());
        edge.setEnabled(po.isEnabled());
        edge.setConfidence(po.getConfidence());
        edge.setDescription(po.getDescription());
        edge.setCreatedBy(po.getCreatedBy());
        edge.setCreatedAt(po.getCreatedAt());
        edge.setUpdatedBy(po.getUpdatedBy());
        edge.setUpdatedAt(po.getUpdatedAt());
        edge.setMetadata(fromObjectJson(po.getMetadataJson()));
        return edge;
    }

    private String toStringListJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
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


    private String toDefinitionJson(AgentSkillDefinition value) {
        try {
            return objectMapper.writeValueAsString(value == null ? new AgentSkillDefinition() : value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private AgentSkillDefinition fromDefinitionJson(String json) {
        try {
            if (json == null || json.isBlank()) return new AgentSkillDefinition();
            return objectMapper.readValue(json, AgentSkillDefinition.class);
        } catch (Exception ex) {
            return new AgentSkillDefinition();
        }
    }

    private AgentSkillLifecycleStatus parseStatus(String value) {
        try {
            return value == null || value.isBlank() ? AgentSkillLifecycleStatus.DRAFT : AgentSkillLifecycleStatus.valueOf(value);
        } catch (Exception ex) {
            return AgentSkillLifecycleStatus.DRAFT;
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
            if (json == null || json.isBlank()) return new LinkedHashMap<>();
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }
}
