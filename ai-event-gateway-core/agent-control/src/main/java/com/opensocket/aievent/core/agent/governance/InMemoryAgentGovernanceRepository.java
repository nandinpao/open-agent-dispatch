package com.opensocket.aievent.core.agent.governance;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!prod")
@ConditionalOnProperty(prefix = "agent-governance", name = "store", havingValue = "MEMORY")
public class InMemoryAgentGovernanceRepository implements AgentGovernanceRepository {
    private final ConcurrentHashMap<String, AgentEnrollmentRequest> enrollments = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AgentProfile> profiles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AgentCredential> credentials = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<AgentCapability>> capabilities = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<AgentAuthorizationScope>> scopes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AgentApprovalAuditEntry> audits = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AgentSecurityEvent> securityEvents = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AgentSecurityEnforcementPolicy> securityPolicies = new ConcurrentHashMap<>();

    @Override
    public AgentEnrollmentRequest saveEnrollment(AgentEnrollmentRequest enrollment) {
        enrollments.put(enrollment.getEnrollmentId(), enrollment);
        return enrollment;
    }

    @Override
    public Optional<AgentEnrollmentRequest> findEnrollment(String enrollmentId) {
        return Optional.ofNullable(enrollments.get(enrollmentId));
    }

    @Override
    public Optional<AgentEnrollmentRequest> findLatestEnrollmentByAgent(String claimedAgentId) {
        if (claimedAgentId == null || claimedAgentId.isBlank()) return Optional.empty();
        return enrollments.values().stream()
                .filter(enrollment -> claimedAgentId.equals(enrollment.getClaimedAgentId()))
                .sorted(Comparator.comparing(AgentEnrollmentRequest::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .findFirst();
    }

    @Override
    public List<AgentEnrollmentRequest> searchEnrollments(AgentEnrollmentStatus status, int limit) {
        return enrollments.values().stream()
                .filter(enrollment -> status == null || enrollment.getStatus() == status)
                .sorted(Comparator.comparing(AgentEnrollmentRequest::getSubmittedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public AgentProfile saveProfile(AgentProfile profile) {
        profiles.put(profile.getAgentId(), profile);
        return profile;
    }

    @Override
    public Optional<AgentProfile> findProfile(String agentId) {
        return Optional.ofNullable(profiles.get(agentId));
    }

    @Override
    public List<AgentProfile> searchProfiles(AgentApprovalStatus approvalStatus, int limit) {
        return profiles.values().stream()
                .filter(profile -> approvalStatus == null || profile.getApprovalStatus() == approvalStatus)
                .sorted(Comparator.comparing(AgentProfile::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public AgentCredential saveCredential(AgentCredential credential) {
        credentials.put(credential.getCredentialId(), credential);
        return credential;
    }

    @Override
    public Optional<AgentCredential> findActiveCredentialByTokenHash(String agentId, String tokenHash, OffsetDateTime now) {
        return credentials.values().stream()
                .filter(credential -> agentId.equals(credential.getAgentId()))
                .filter(credential -> tokenHash != null && tokenHash.equals(credential.getTokenHash()))
                .filter(credential -> credential.activeAt(now))
                .findFirst();
    }

    @Override
    public Optional<AgentCredential> findActiveCredentialByFingerprint(String agentId, String publicKeyFingerprint, OffsetDateTime now) {
        return credentials.values().stream()
                .filter(credential -> agentId.equals(credential.getAgentId()))
                .filter(credential -> publicKeyFingerprint != null && publicKeyFingerprint.equals(credential.getPublicKeyFingerprint()))
                .filter(credential -> credential.activeAt(now))
                .findFirst();
    }

    @Override
    public Optional<AgentCredential> findLatestCredential(String agentId) {
        return credentials.values().stream()
                .filter(credential -> agentId != null && agentId.equals(credential.getAgentId()))
                .sorted(Comparator.comparing(AgentCredential::getCredentialVersion).reversed())
                .findFirst();
    }

    @Override
    public int revokeCredentials(String agentId, String reason, OffsetDateTime revokedAt) {
        int[] count = {0};
        credentials.values().forEach(credential -> {
            if (agentId.equals(credential.getAgentId()) && credential.getRevokedAt() == null) {
                credential.setRevokedAt(revokedAt);
                credential.setRevokedReason(reason);
                credential.setUpdatedAt(revokedAt);
                count[0]++;
            }
        });
        return count[0];
    }

    @Override
    public List<AgentCapability> replaceCapabilities(String agentId, List<AgentCapability> values) {
        List<AgentCapability> copy = values == null ? List.of() : List.copyOf(values);
        capabilities.put(agentId, copy);
        return copy;
    }

    @Override
    public List<AgentCapability> findEnabledCapabilities(String agentId) {
        return capabilities.getOrDefault(agentId, List.of()).stream().filter(AgentCapability::isEnabled).toList();
    }

    @Override
    public List<AgentAuthorizationScope> replaceScopes(String agentId, List<AgentAuthorizationScope> values) {
        List<AgentAuthorizationScope> copy = values == null ? List.of() : List.copyOf(values);
        scopes.put(agentId, copy);
        return copy;
    }

    @Override
    public List<AgentAuthorizationScope> findEnabledScopes(String agentId) {
        return scopes.getOrDefault(agentId, List.of()).stream().filter(AgentAuthorizationScope::isEnabled).toList();
    }

    @Override
    public AgentApprovalAuditEntry appendAudit(AgentApprovalAuditEntry audit) {
        audits.put(audit.getAuditId(), audit);
        return audit;
    }

    @Override
    public AgentSecurityEvent saveSecurityEvent(AgentSecurityEvent event) {
        securityEvents.put(event.getSecurityEventId(), event);
        return event;
    }

    @Override
    public List<AgentSecurityEvent> searchSecurityEvents(String agentId, int limit) {
        return securityEvents.values().stream()
                .filter(event -> agentId == null || agentId.isBlank() || agentId.equals(event.getAgentId()) || agentId.equals(event.getClaimedAgentId()))
                .sorted(Comparator.comparing(AgentSecurityEvent::getOccurredAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(limit)
                .toList();
    }


    @Override
    public AgentSecurityEnforcementPolicy saveSecurityEnforcementPolicy(AgentSecurityEnforcementPolicy policy) {
        securityPolicies.put(policy.getAgentId(), policy);
        return policy;
    }

    @Override
    public Optional<AgentSecurityEnforcementPolicy> findSecurityEnforcementPolicy(String agentId) {
        return Optional.ofNullable(securityPolicies.get(agentId));
    }

    @Override
    public List<AgentSecurityEnforcementPolicy> searchSecurityEnforcementPolicies(int limit) {
        return securityPolicies.values().stream()
                .sorted(Comparator.comparing(AgentSecurityEnforcementPolicy::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public List<AgentGovernanceTableDiagnostic> tableDiagnostics() {
        return List.of(
                new AgentGovernanceTableDiagnostic("agent_profiles", profiles.size(), "Core AgentGovernanceService", "No governed Agent profile has been approved or created."),
                new AgentGovernanceTableDiagnostic("agent_enrollment_requests", enrollments.size(), "Core authorization/enrollment API", "No Agent has attempted enrollment or runtime authorization yet."),
                new AgentGovernanceTableDiagnostic("agent_credentials", credentials.size(), "Core approve/re-approve flow", "Agents are not connectable until credential material is issued."),
                new AgentGovernanceTableDiagnostic("agent_capabilities", capabilities.values().stream().mapToLong(List::size).sum(), "Core approve/update profile flow", "Dispatch governance cannot match Agent capabilities until populated."),
                new AgentGovernanceTableDiagnostic("agent_authorization_scopes", scopes.values().stream().mapToLong(List::size).sum(), "Core approve/update profile flow", "Dispatch governance has no allowed tenant/system/task scopes."),
                new AgentGovernanceTableDiagnostic("agent_approval_audit", audits.size(), "Core governance state transitions", "No governance decision or profile edit has occurred."),
                new AgentGovernanceTableDiagnostic("agent_security_events", securityEvents.size(), "Core authorize-connection/security-event API", "No connection authorization attempt has reached Core yet."),
                new AgentGovernanceTableDiagnostic("agent_security_enforcement_policies", securityPolicies.size(), "Core security policy API", "No per-Agent security enforcement policy has been configured."),
                new AgentGovernanceTableDiagnostic("agents", 0, "Netty core-directory-sync -> Core AgentDirectoryService", "Runtime directory is not available in MEMORY governance repository diagnostics.")
        );
    }

    @Override
    public String mode() {
        return "MEMORY";
    }
}
