package com.opensocket.aievent.database.persistence.agent.governance.converter;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import com.opensocket.aievent.core.agent.governance.AgentApprovalAuditEntry;
import com.opensocket.aievent.core.agent.governance.AgentApprovalStatus;
import com.opensocket.aievent.core.agent.governance.AgentAuthorizationScope;
import com.opensocket.aievent.core.agent.governance.AgentCapability;
import com.opensocket.aievent.core.agent.governance.AgentCredential;
import com.opensocket.aievent.core.agent.governance.AgentCredentialType;
import com.opensocket.aievent.core.agent.governance.AgentEnrollmentRequest;
import com.opensocket.aievent.core.agent.governance.AgentEnrollmentStatus;
import com.opensocket.aievent.core.agent.governance.AgentProfile;
import com.opensocket.aievent.core.agent.governance.AgentRiskStatus;
import com.opensocket.aievent.core.agent.governance.AgentSecurityEvent;
import com.opensocket.aievent.core.agent.governance.AgentSecurityEventType;
import com.opensocket.aievent.core.agent.governance.AgentSecurityEnforcementMode;
import com.opensocket.aievent.core.agent.governance.AgentSecurityEnforcementPolicy;
import com.opensocket.aievent.database.persistence.agent.po.AgentApprovalAuditPo;
import com.opensocket.aievent.database.persistence.agent.po.AgentAuthorizationScopePo;
import com.opensocket.aievent.database.persistence.agent.po.AgentCapabilityPo;
import com.opensocket.aievent.database.persistence.agent.po.AgentCredentialPo;
import com.opensocket.aievent.database.persistence.agent.po.AgentEnrollmentRequestPo;
import com.opensocket.aievent.database.persistence.agent.po.AgentProfilePo;
import com.opensocket.aievent.database.persistence.agent.po.AgentSecurityEventPo;
import com.opensocket.aievent.database.persistence.agent.po.AgentSecurityEnforcementPolicyPo;
import com.opensocket.aievent.database.persistence.spi.DatabasePersistenceConverter;

@DatabasePersistenceConverter
@ConditionalOnProperty(prefix = "agent-governance", name = "store", havingValue = "MYBATIS")
public class AgentGovernancePersistenceConverter {
    private final ObjectMapper objectMapper;

    public AgentGovernancePersistenceConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AgentEnrollmentRequestPo toPo(AgentEnrollmentRequest item) {
        AgentEnrollmentRequestPo po = new AgentEnrollmentRequestPo();
        po.setEnrollmentId(item.getEnrollmentId());
        po.setClaimedAgentId(item.getClaimedAgentId());
        po.setTenantId(item.getTenantId());
        po.setAgentName(item.getAgentName());
        po.setAgentType(item.getAgentType());
        po.setSubmittedMetadataJson(toJson(item.getSubmittedMetadata()));
        po.setEvidenceJson(toJson(item.getEvidence()));
        po.setFingerprint(item.getFingerprint());
        po.setRemoteAddress(item.getRemoteAddress());
        po.setStatus(item.getStatus() == null ? null : item.getStatus().name());
        po.setSubmittedAt(item.getSubmittedAt());
        po.setReviewedBy(item.getReviewedBy());
        po.setReviewedAt(item.getReviewedAt());
        po.setReviewComment(item.getReviewComment());
        po.setCreatedAt(item.getCreatedAt());
        po.setUpdatedAt(item.getUpdatedAt());
        return po;
    }

    public AgentEnrollmentRequest toEnrollment(AgentEnrollmentRequestPo po) {
        AgentEnrollmentRequest item = new AgentEnrollmentRequest();
        item.setEnrollmentId(po.getEnrollmentId());
        item.setClaimedAgentId(po.getClaimedAgentId());
        item.setTenantId(po.getTenantId());
        item.setAgentName(po.getAgentName());
        item.setAgentType(po.getAgentType());
        item.setSubmittedMetadata(fromJsonMap(po.getSubmittedMetadataJson()));
        item.setEvidence(fromJsonMap(po.getEvidenceJson()));
        item.setFingerprint(po.getFingerprint());
        item.setRemoteAddress(po.getRemoteAddress());
        item.setStatus(po.getStatus() == null ? null : AgentEnrollmentStatus.valueOf(po.getStatus()));
        item.setSubmittedAt(po.getSubmittedAt());
        item.setReviewedBy(po.getReviewedBy());
        item.setReviewedAt(po.getReviewedAt());
        item.setReviewComment(po.getReviewComment());
        item.setCreatedAt(po.getCreatedAt());
        item.setUpdatedAt(po.getUpdatedAt());
        return item;
    }

    public AgentProfilePo toPo(AgentProfile item) {
        AgentProfilePo po = new AgentProfilePo();
        po.setAgentId(item.getAgentId());
        po.setTenantId(item.getTenantId());
        po.setAgentName(item.getAgentName());
        po.setAgentType(item.getAgentType());
        po.setOwnerTeam(item.getOwnerTeam());
        po.setDescription(item.getDescription());
        po.setApprovalStatus(item.getApprovalStatus() == null ? null : item.getApprovalStatus().name());
        po.setEnabled(item.isEnabled());
        po.setRiskStatus(item.getRiskStatus() == null ? null : item.getRiskStatus().name());
        po.setPolicyVersion(item.getPolicyVersion());
        po.setCreatedAt(item.getCreatedAt());
        po.setUpdatedAt(item.getUpdatedAt());
        return po;
    }

    public AgentProfile toProfile(AgentProfilePo po) {
        AgentProfile item = new AgentProfile();
        item.setAgentId(po.getAgentId());
        item.setTenantId(po.getTenantId());
        item.setAgentName(po.getAgentName());
        item.setAgentType(po.getAgentType());
        item.setOwnerTeam(po.getOwnerTeam());
        item.setDescription(po.getDescription());
        item.setApprovalStatus(po.getApprovalStatus() == null ? null : AgentApprovalStatus.valueOf(po.getApprovalStatus()));
        item.setEnabled(po.isEnabled());
        item.setRiskStatus(po.getRiskStatus() == null ? null : AgentRiskStatus.valueOf(po.getRiskStatus()));
        item.setPolicyVersion(po.getPolicyVersion());
        item.setCreatedAt(po.getCreatedAt());
        item.setUpdatedAt(po.getUpdatedAt());
        return item;
    }

    public AgentCredentialPo toPo(AgentCredential item) {
        AgentCredentialPo po = new AgentCredentialPo();
        po.setCredentialId(item.getCredentialId());
        po.setAgentId(item.getAgentId());
        po.setCredentialType(item.getCredentialType() == null ? null : item.getCredentialType().name());
        po.setPublicKeyFingerprint(item.getPublicKeyFingerprint());
        po.setTokenHash(item.getTokenHash());
        po.setCredentialVersion(item.getCredentialVersion());
        po.setIssuedAt(item.getIssuedAt());
        po.setExpiresAt(item.getExpiresAt());
        po.setRevokedAt(item.getRevokedAt());
        po.setRevokedReason(item.getRevokedReason());
        po.setCreatedAt(item.getCreatedAt());
        po.setUpdatedAt(item.getUpdatedAt());
        return po;
    }

    public AgentCredential toCredential(AgentCredentialPo po) {
        AgentCredential item = new AgentCredential();
        item.setCredentialId(po.getCredentialId());
        item.setAgentId(po.getAgentId());
        item.setCredentialType(po.getCredentialType() == null ? null : AgentCredentialType.valueOf(po.getCredentialType()));
        item.setPublicKeyFingerprint(po.getPublicKeyFingerprint());
        item.setTokenHash(po.getTokenHash());
        item.setCredentialVersion(po.getCredentialVersion());
        item.setIssuedAt(po.getIssuedAt());
        item.setExpiresAt(po.getExpiresAt());
        item.setRevokedAt(po.getRevokedAt());
        item.setRevokedReason(po.getRevokedReason());
        item.setCreatedAt(po.getCreatedAt());
        item.setUpdatedAt(po.getUpdatedAt());
        return item;
    }

    public AgentCapabilityPo toPo(AgentCapability item) {
        AgentCapabilityPo po = new AgentCapabilityPo();
        po.setAgentId(item.getAgentId());
        po.setCapabilityCode(item.getCapabilityCode());
        po.setCapabilityVersion(item.getCapabilityVersion());
        po.setEnabled(item.isEnabled());
        po.setApprovedBy(item.getApprovedBy());
        po.setApprovedAt(item.getApprovedAt());
        return po;
    }

    public AgentCapability toCapability(AgentCapabilityPo po) {
        AgentCapability item = new AgentCapability();
        item.setAgentId(po.getAgentId());
        item.setCapabilityCode(po.getCapabilityCode());
        item.setCapabilityVersion(po.getCapabilityVersion());
        item.setEnabled(po.isEnabled());
        item.setApprovedBy(po.getApprovedBy());
        item.setApprovedAt(po.getApprovedAt());
        return item;
    }

    public AgentAuthorizationScopePo toPo(AgentAuthorizationScope item) {
        AgentAuthorizationScopePo po = new AgentAuthorizationScopePo();
        po.setScopeId(item.getScopeId());
        po.setAgentId(item.getAgentId());
        po.setTenantId(item.getTenantId());
        po.setSystemCode(item.getSystemCode());
        po.setSiteCode(item.getSiteCode());
        po.setEventType(item.getEventType());
        po.setTaskType(item.getTaskType());
        po.setDataClassificationLimit(item.getDataClassificationLimit());
        po.setEnabled(item.isEnabled());
        po.setCreatedAt(item.getCreatedAt());
        po.setUpdatedAt(item.getUpdatedAt());
        return po;
    }

    public AgentAuthorizationScope toScope(AgentAuthorizationScopePo po) {
        AgentAuthorizationScope item = new AgentAuthorizationScope();
        item.setScopeId(po.getScopeId());
        item.setAgentId(po.getAgentId());
        item.setTenantId(po.getTenantId());
        item.setSystemCode(po.getSystemCode());
        item.setSiteCode(po.getSiteCode());
        item.setEventType(po.getEventType());
        item.setTaskType(po.getTaskType());
        item.setDataClassificationLimit(po.getDataClassificationLimit());
        item.setEnabled(po.isEnabled());
        item.setCreatedAt(po.getCreatedAt());
        item.setUpdatedAt(po.getUpdatedAt());
        return item;
    }

    public AgentApprovalAuditPo toPo(AgentApprovalAuditEntry item) {
        AgentApprovalAuditPo po = new AgentApprovalAuditPo();
        po.setAuditId(item.getAuditId());
        po.setAgentId(item.getAgentId());
        po.setEnrollmentId(item.getEnrollmentId());
        po.setAction(item.getAction());
        po.setOldStatus(item.getOldStatus());
        po.setNewStatus(item.getNewStatus());
        po.setOperatorId(item.getOperatorId());
        po.setReason(item.getReason());
        po.setCreatedAt(item.getCreatedAt());
        return po;
    }

    public AgentSecurityEventPo toPo(AgentSecurityEvent item) {
        AgentSecurityEventPo po = new AgentSecurityEventPo();
        po.setSecurityEventId(item.getSecurityEventId());
        po.setGatewayNodeId(item.getGatewayNodeId());
        po.setClaimedAgentId(item.getClaimedAgentId());
        po.setAgentId(item.getAgentId());
        po.setEventType(item.getEventType() == null ? null : item.getEventType().name());
        po.setReason(item.getReason());
        po.setFingerprint(item.getFingerprint());
        po.setRemoteAddress(item.getRemoteAddress());
        po.setMetadataJson(toJson(item.getMetadata()));
        po.setOccurredAt(item.getOccurredAt());
        po.setCreatedAt(item.getCreatedAt());
        return po;
    }

    public AgentSecurityEvent toSecurityEvent(AgentSecurityEventPo po) {
        AgentSecurityEvent item = new AgentSecurityEvent();
        item.setSecurityEventId(po.getSecurityEventId());
        item.setGatewayNodeId(po.getGatewayNodeId());
        item.setClaimedAgentId(po.getClaimedAgentId());
        item.setAgentId(po.getAgentId());
        item.setEventType(po.getEventType() == null ? null : AgentSecurityEventType.valueOf(po.getEventType()));
        item.setReason(po.getReason());
        item.setFingerprint(po.getFingerprint());
        item.setRemoteAddress(po.getRemoteAddress());
        item.setMetadata(fromJsonMap(po.getMetadataJson()));
        item.setOccurredAt(po.getOccurredAt());
        item.setCreatedAt(po.getCreatedAt());
        return item;
    }


    public AgentSecurityEnforcementPolicyPo toPo(AgentSecurityEnforcementPolicy item) {
        AgentSecurityEnforcementPolicyPo po = new AgentSecurityEnforcementPolicyPo();
        po.setPolicyId(item.getPolicyId());
        po.setAgentId(item.getAgentId());
        po.setEnabled(item.isEnabled());
        po.setDuplicateRuntimeMode(item.getDuplicateRuntimeMode() == null ? null : item.getDuplicateRuntimeMode().name());
        po.setRequireCredentialRotation(item.isRequireCredentialRotation());
        po.setNotifyEmail(item.isNotifyEmail());
        po.setNotifySlack(item.isNotifySlack());
        po.setNotifySiem(item.isNotifySiem());
        po.setEmailRecipientsJson(toJson(item.getEmailRecipients()));
        po.setSlackChannelsJson(toJson(item.getSlackChannels()));
        po.setSiemTopicsJson(toJson(item.getSiemTopics()));
        po.setMetadataJson(toJson(item.getMetadata()));
        po.setUpdatedBy(item.getUpdatedBy());
        po.setCreatedAt(item.getCreatedAt());
        po.setUpdatedAt(item.getUpdatedAt());
        return po;
    }

    public AgentSecurityEnforcementPolicy toSecurityEnforcementPolicy(AgentSecurityEnforcementPolicyPo po) {
        AgentSecurityEnforcementPolicy item = new AgentSecurityEnforcementPolicy();
        item.setPolicyId(po.getPolicyId());
        item.setAgentId(po.getAgentId());
        item.setEnabled(po.isEnabled());
        item.setDuplicateRuntimeMode(po.getDuplicateRuntimeMode() == null ? null : AgentSecurityEnforcementMode.valueOf(po.getDuplicateRuntimeMode()));
        item.setRequireCredentialRotation(po.isRequireCredentialRotation());
        item.setNotifyEmail(po.isNotifyEmail());
        item.setNotifySlack(po.isNotifySlack());
        item.setNotifySiem(po.isNotifySiem());
        item.setEmailRecipients(fromJsonList(po.getEmailRecipientsJson()));
        item.setSlackChannels(fromJsonList(po.getSlackChannelsJson()));
        item.setSiemTopics(fromJsonList(po.getSiemTopicsJson()));
        item.setMetadata(fromJsonMap(po.getMetadataJson()));
        item.setUpdatedBy(po.getUpdatedBy());
        item.setCreatedAt(po.getCreatedAt());
        item.setUpdatedAt(po.getUpdatedAt());
        return item;
    }

    public String toJson(Object value) {
        try { return objectMapper.writeValueAsString(value == null ? Map.of() : value); }
        catch (Exception ex) { return "{}"; }
    }

    public java.util.List<String> fromJsonList(String json) {
        try {
            if (json == null || json.isBlank()) return java.util.List.of();
            return objectMapper.readValue(json, new TypeReference<java.util.List<String>>() {});
        } catch (Exception ex) {
            return java.util.List.of();
        }
    }

    public Map<String, Object> fromJsonMap(String json) {
        try {
            if (json == null || json.isBlank()) return new LinkedHashMap<>();
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }
}
