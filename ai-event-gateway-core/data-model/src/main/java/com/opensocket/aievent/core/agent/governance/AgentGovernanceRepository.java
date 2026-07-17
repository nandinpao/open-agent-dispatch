package com.opensocket.aievent.core.agent.governance;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface AgentGovernanceRepository {
    AgentEnrollmentRequest saveEnrollment(AgentEnrollmentRequest enrollment);
    Optional<AgentEnrollmentRequest> findEnrollment(String enrollmentId);
    Optional<AgentEnrollmentRequest> findLatestEnrollmentByAgent(String claimedAgentId);
    List<AgentEnrollmentRequest> searchEnrollments(AgentEnrollmentStatus status, int limit);

    AgentProfile saveProfile(AgentProfile profile);
    Optional<AgentProfile> findProfile(String agentId);
    List<AgentProfile> searchProfiles(AgentApprovalStatus approvalStatus, int limit);

    AgentCredential saveCredential(AgentCredential credential);
    Optional<AgentCredential> findActiveCredentialByTokenHash(String agentId, String tokenHash, OffsetDateTime now);
    Optional<AgentCredential> findActiveCredentialByFingerprint(String agentId, String publicKeyFingerprint, OffsetDateTime now);
    Optional<AgentCredential> findLatestCredential(String agentId);
    int revokeCredentials(String agentId, String reason, OffsetDateTime revokedAt);

    List<AgentCapability> replaceCapabilities(String agentId, List<AgentCapability> capabilities);
    List<AgentCapability> findEnabledCapabilities(String agentId);

    List<AgentAuthorizationScope> replaceScopes(String agentId, List<AgentAuthorizationScope> scopes);
    List<AgentAuthorizationScope> findEnabledScopes(String agentId);

    AgentApprovalAuditEntry appendAudit(AgentApprovalAuditEntry audit);
    AgentSecurityEvent saveSecurityEvent(AgentSecurityEvent event);
    List<AgentSecurityEvent> searchSecurityEvents(String agentId, int limit);

    AgentSecurityEnforcementPolicy saveSecurityEnforcementPolicy(AgentSecurityEnforcementPolicy policy);
    Optional<AgentSecurityEnforcementPolicy> findSecurityEnforcementPolicy(String agentId);
    List<AgentSecurityEnforcementPolicy> searchSecurityEnforcementPolicies(int limit);
    List<AgentGovernanceTableDiagnostic> tableDiagnostics();

    String mode();
}
