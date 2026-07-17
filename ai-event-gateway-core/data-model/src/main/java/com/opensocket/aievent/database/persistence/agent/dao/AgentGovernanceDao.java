package com.opensocket.aievent.database.persistence.agent.dao;

import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.opensocket.aievent.database.persistence.agent.po.AgentApprovalAuditPo;
import com.opensocket.aievent.database.persistence.agent.po.AgentAuthorizationScopePo;
import com.opensocket.aievent.database.persistence.agent.po.AgentCapabilityPo;
import com.opensocket.aievent.database.persistence.agent.po.AgentCredentialPo;
import com.opensocket.aievent.database.persistence.agent.po.AgentEnrollmentRequestPo;
import com.opensocket.aievent.database.persistence.agent.po.AgentProfilePo;
import com.opensocket.aievent.database.persistence.agent.po.AgentSecurityEventPo;
import com.opensocket.aievent.database.persistence.agent.po.AgentSecurityEnforcementPolicyPo;
import com.opensocket.aievent.core.agent.governance.AgentGovernanceTableDiagnostic;

@Mapper
public interface AgentGovernanceDao {
    int upsertEnrollment(@Param("enrollment") AgentEnrollmentRequestPo enrollment);
    AgentEnrollmentRequestPo findEnrollment(@Param("enrollmentId") String enrollmentId);
    AgentEnrollmentRequestPo findLatestEnrollmentByAgent(@Param("claimedAgentId") String claimedAgentId);
    List<AgentEnrollmentRequestPo> searchEnrollments(@Param("status") String status, @Param("limit") int limit);

    int upsertProfile(@Param("profile") AgentProfilePo profile);
    AgentProfilePo findProfile(@Param("agentId") String agentId);
    List<AgentProfilePo> searchProfiles(@Param("approvalStatus") String approvalStatus, @Param("limit") int limit);

    int insertCredential(@Param("credential") AgentCredentialPo credential);
    AgentCredentialPo findActiveCredentialByTokenHash(@Param("agentId") String agentId,
                                                      @Param("tokenHash") String tokenHash,
                                                      @Param("now") OffsetDateTime now);
    AgentCredentialPo findActiveCredentialByFingerprint(@Param("agentId") String agentId,
                                                        @Param("publicKeyFingerprint") String publicKeyFingerprint,
                                                        @Param("now") OffsetDateTime now);
    AgentCredentialPo findLatestCredential(@Param("agentId") String agentId);
    int revokeCredentials(@Param("agentId") String agentId,
                          @Param("reason") String reason,
                          @Param("revokedAt") OffsetDateTime revokedAt);

    int deleteCapabilities(@Param("agentId") String agentId);
    int insertCapability(@Param("capability") AgentCapabilityPo capability);
    List<AgentCapabilityPo> findEnabledCapabilities(@Param("agentId") String agentId);

    int deleteScopes(@Param("agentId") String agentId);
    int insertScope(@Param("scope") AgentAuthorizationScopePo scope);
    List<AgentAuthorizationScopePo> findEnabledScopes(@Param("agentId") String agentId);

    int insertAudit(@Param("audit") AgentApprovalAuditPo audit);
    int insertSecurityEvent(@Param("event") AgentSecurityEventPo event);
    List<AgentSecurityEventPo> searchSecurityEvents(@Param("agentId") String agentId, @Param("limit") int limit);

    int upsertSecurityEnforcementPolicy(@Param("policy") AgentSecurityEnforcementPolicyPo policy);
    AgentSecurityEnforcementPolicyPo findSecurityEnforcementPolicy(@Param("agentId") String agentId);
    List<AgentSecurityEnforcementPolicyPo> searchSecurityEnforcementPolicies(@Param("limit") int limit);
    List<AgentGovernanceTableDiagnostic> tableDiagnostics();
}
