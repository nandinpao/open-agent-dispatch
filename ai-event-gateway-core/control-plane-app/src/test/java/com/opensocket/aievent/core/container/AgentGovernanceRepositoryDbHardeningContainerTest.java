package com.opensocket.aievent.core.container;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.agent.governance.AgentApprovalStatus;
import com.opensocket.aievent.core.agent.governance.AgentAuthorizationScope;
import com.opensocket.aievent.core.agent.governance.AgentCapability;
import com.opensocket.aievent.core.agent.governance.AgentCredential;
import com.opensocket.aievent.core.agent.governance.AgentCredentialType;
import com.opensocket.aievent.core.agent.governance.AgentEnrollmentRequest;
import com.opensocket.aievent.core.agent.governance.AgentEnrollmentStatus;
import com.opensocket.aievent.core.agent.governance.AgentGovernanceRepository;
import com.opensocket.aievent.core.agent.governance.AgentProfile;
import com.opensocket.aievent.core.agent.governance.AgentRiskStatus;
import com.opensocket.aievent.core.agent.governance.AgentSecurityEnforcementMode;
import com.opensocket.aievent.core.agent.governance.AgentSecurityEnforcementPolicy;
import com.opensocket.aievent.core.agent.governance.AgentSecurityEvent;
import com.opensocket.aievent.core.agent.governance.AgentSecurityEventType;

class AgentGovernanceRepositoryDbHardeningContainerTest extends P25RepositoryDbContainerSupport {

    @Test
    void profileCredentialCapabilityScopeAndPolicyMustRoundTripWithDbConstraints() {
        AgentGovernanceRepository repository = agentGovernanceRepository();
        var now = now();

        repository.saveEnrollment(enrollment("enroll-db-1", "agent-db-1"));
        AgentEnrollmentRequest reviewed = repository.findLatestEnrollmentByAgent("agent-db-1").orElseThrow();
        reviewed.setStatus(AgentEnrollmentStatus.APPROVED);
        reviewed.setReviewedBy("qa");
        reviewed.setReviewedAt(now);
        reviewed.setReviewComment("approved by repository DB hardening test");
        repository.saveEnrollment(reviewed);

        repository.saveProfile(profile("agent-db-1", AgentApprovalStatus.APPROVED, true, AgentRiskStatus.NORMAL));
        repository.saveCredential(credential("cred-db-1", "agent-db-1", "token-hash-1", "fp-key-1"));

        assertThat(repository.findProfile("agent-db-1")).hasValueSatisfying(profile -> {
            assertThat(profile.allowsConnection()).isTrue();
            assertThat(profile.getPolicyVersion()).isEqualTo(1);
        });
        assertThat(repository.findActiveCredentialByTokenHash("agent-db-1", "token-hash-1", now)).isPresent();
        assertThat(repository.findActiveCredentialByFingerprint("agent-db-1", "fp-key-1", now)).isPresent();

        repository.replaceCapabilities("agent-db-1", List.of(
                capability("agent-db-1", "ERP_PO_REVIEW", true),
                capability("agent-db-1", "MES_ALARM_TRIAGE", false)));
        assertThat(repository.findEnabledCapabilities("agent-db-1"))
                .extracting(AgentCapability::getCapabilityCode)
                .containsExactly("ERP_PO_REVIEW");

        repository.replaceScopes("agent-db-1", List.of(
                scope("scope-db-1", "agent-db-1", "ERP", true),
                scope("scope-db-2", "agent-db-1", "HR", false)));
        assertThat(repository.findEnabledScopes("agent-db-1"))
                .extracting(AgentAuthorizationScope::getSystemCode)
                .containsExactly("ERP");

        repository.saveSecurityEvent(securityEvent("sec-db-1", "agent-db-1"));
        assertThat(repository.searchSecurityEvents("agent-db-1", 10))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getEventType()).isEqualTo(AgentSecurityEventType.CONNECTION_AUTHORIZED);
                    assertThat(event.getMetadata()).containsEntry("source", "P25");
                });

        repository.saveSecurityEnforcementPolicy(policy("policy-db-1", "agent-db-1"));
        assertThat(repository.findSecurityEnforcementPolicy("agent-db-1"))
                .hasValueSatisfying(policy -> {
                    assertThat(policy.shouldQuarantine()).isTrue();
                    assertThat(policy.shouldDisconnectAll()).isTrue();
                    assertThat(policy.shouldRevokeCredentials()).isFalse();
                    assertThat(policy.getEmailRecipients()).containsExactly("security@example.com");
                });

        assertThat(repository.revokeCredentials("agent-db-1", "rotation", now.plusMinutes(1))).isEqualTo(1);
        assertThat(repository.findActiveCredentialByTokenHash("agent-db-1", "token-hash-1", now.plusMinutes(2))).isEmpty();
        assertThat(repository.findLatestCredential("agent-db-1")).hasValueSatisfying(credential ->
                assertThat(credential.getRevokedReason()).isEqualTo("rotation"));
    }

    private AgentEnrollmentRequest enrollment(String enrollmentId, String agentId) {
        AgentEnrollmentRequest request = new AgentEnrollmentRequest();
        request.setEnrollmentId(enrollmentId);
        request.setClaimedAgentId(agentId);
        request.setTenantId("tenant-a");
        request.setAgentName("DB Agent");
        request.setAgentType("ERP");
        request.setSubmittedMetadata(Map.of("stage", "P25"));
        request.setEvidence(Map.of("kind", "repository-db"));
        request.setFingerprint("fp-" + agentId);
        request.setRemoteAddress("127.0.0.1");
        request.setStatus(AgentEnrollmentStatus.PENDING_REVIEW);
        request.setSubmittedAt(now());
        request.setCreatedAt(now());
        request.setUpdatedAt(now());
        return request;
    }

    private AgentProfile profile(String agentId, AgentApprovalStatus approvalStatus, boolean enabled, AgentRiskStatus riskStatus) {
        AgentProfile profile = new AgentProfile();
        profile.setAgentId(agentId);
        profile.setTenantId("tenant-a");
        profile.setAgentName("DB Agent");
        profile.setAgentType("ERP");
        profile.setOwnerTeam("qa");
        profile.setDescription("repository DB hardening fixture");
        profile.setApprovalStatus(approvalStatus);
        profile.setEnabled(enabled);
        profile.setRiskStatus(riskStatus);
        profile.setCreatedAt(now());
        profile.setUpdatedAt(now());
        return profile;
    }

    private AgentCredential credential(String credentialId, String agentId, String tokenHash, String fingerprint) {
        AgentCredential credential = new AgentCredential();
        credential.setCredentialId(credentialId);
        credential.setAgentId(agentId);
        credential.setCredentialType(AgentCredentialType.TOKEN);
        credential.setTokenHash(tokenHash);
        credential.setPublicKeyFingerprint(fingerprint);
        credential.setCredentialVersion(1);
        credential.setIssuedAt(now());
        credential.setExpiresAt(now().plusDays(7));
        credential.setCreatedAt(now());
        credential.setUpdatedAt(now());
        return credential;
    }

    private AgentCapability capability(String agentId, String code, boolean enabled) {
        AgentCapability capability = new AgentCapability(agentId, code);
        capability.setCapabilityVersion("1.0.0");
        capability.setEnabled(enabled);
        capability.setApprovedBy("qa");
        capability.setApprovedAt(now());
        return capability;
    }

    private AgentAuthorizationScope scope(String scopeId, String agentId, String systemCode, boolean enabled) {
        AgentAuthorizationScope scope = new AgentAuthorizationScope();
        scope.setScopeId(scopeId);
        scope.setAgentId(agentId);
        scope.setTenantId("tenant-a");
        scope.setSystemCode(systemCode);
        scope.setSiteCode("TNN");
        scope.setTaskType("INCIDENT_RESPONSE");
        scope.setDataClassificationLimit("INTERNAL");
        scope.setEnabled(enabled);
        scope.setCreatedAt(now());
        scope.setUpdatedAt(now());
        return scope;
    }

    private AgentSecurityEvent securityEvent(String eventId, String agentId) {
        AgentSecurityEvent event = new AgentSecurityEvent();
        event.setSecurityEventId(eventId);
        event.setGatewayNodeId("gateway-db-1");
        event.setClaimedAgentId(agentId);
        event.setAgentId(agentId);
        event.setEventType(AgentSecurityEventType.CONNECTION_AUTHORIZED);
        event.setReason("repository DB hardening");
        event.setFingerprint("fp-" + agentId);
        event.setRemoteAddress("127.0.0.1");
        event.setMetadata(Map.of("source", "P25"));
        event.setOccurredAt(now());
        event.setCreatedAt(now());
        return event;
    }

    private AgentSecurityEnforcementPolicy policy(String policyId, String agentId) {
        AgentSecurityEnforcementPolicy policy = new AgentSecurityEnforcementPolicy();
        policy.setPolicyId(policyId);
        policy.setAgentId(agentId);
        policy.setEnabled(true);
        policy.setDuplicateRuntimeMode(AgentSecurityEnforcementMode.QUARANTINE_AND_DISCONNECT);
        policy.setRequireCredentialRotation(true);
        policy.setNotifyEmail(true);
        policy.setEmailRecipients(List.of("security@example.com"));
        policy.setMetadata(Map.of("stage", "P25"));
        policy.setUpdatedBy("qa");
        policy.setCreatedAt(now());
        policy.setUpdatedAt(now());
        return policy;
    }
}
