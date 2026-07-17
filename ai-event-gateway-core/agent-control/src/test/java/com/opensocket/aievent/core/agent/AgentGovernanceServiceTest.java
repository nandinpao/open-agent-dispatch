package com.opensocket.aievent.core.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.agent.governance.AgentAuthorizationDecision;
import com.opensocket.aievent.core.agent.governance.AgentAuthorizationDenyReason;
import com.opensocket.aievent.core.agent.governance.AgentAuthorizationScope;
import com.opensocket.aievent.core.agent.governance.AgentApprovalStatus;
import com.opensocket.aievent.core.agent.governance.AgentConnectionAuthorizationRequest;
import com.opensocket.aievent.core.agent.governance.AgentCredentialIssueCommand;
import com.opensocket.aievent.core.agent.governance.AgentDuplicateRuntimeSecurityCommand;
import com.opensocket.aievent.core.agent.governance.AgentEnrollmentApprovalCommand;
import com.opensocket.aievent.core.agent.governance.AgentEnrollmentRequest;
import com.opensocket.aievent.core.agent.governance.AgentEnrollmentRejectCommand;
import com.opensocket.aievent.core.agent.governance.AgentGovernanceService;
import com.opensocket.aievent.core.agent.governance.AgentProfileApprovalCommand;
import com.opensocket.aievent.core.agent.governance.AgentProfileUpdateCommand;
import com.opensocket.aievent.core.agent.governance.AgentRiskStatus;
import com.opensocket.aievent.core.agent.governance.InMemoryAgentGovernanceRepository;

class AgentGovernanceServiceTest {
    @Test
    void shouldDenyUnknownAgentAndAllowApprovedCredentialedAgent() {
        AgentGovernanceService service = new AgentGovernanceService(new InMemoryAgentGovernanceRepository());

        AgentConnectionAuthorizationRequest unknown = new AgentConnectionAuthorizationRequest();
        unknown.setClaimedAgentId("agent-001");
        unknown.setCredentialToken("token-001");
        assertThat(service.authorizeConnection(unknown).getDecision()).isEqualTo(AgentAuthorizationDecision.DENY);

        AgentEnrollmentRequest enrollment = new AgentEnrollmentRequest();
        enrollment.setClaimedAgentId("agent-001");
        enrollment.setTenantId("default");
        enrollment.setAgentName("OpenClaw Issue Agent");
        enrollment.setAgentType("OPENCLAW");
        enrollment = service.submitEnrollment(enrollment);

        AgentAuthorizationScope scope = new AgentAuthorizationScope();
        scope.setTenantId("default");
        scope.setSystemCode("GITHUB");
        scope.setTaskType("ISSUE_ANALYSIS");

        AgentEnrollmentApprovalCommand approve = new AgentEnrollmentApprovalCommand();
        approve.setApprovedBy("admin");
        approve.setCredentialToken("token-001");
        approve.setCapabilities(List.of("issue-analysis"));
        approve.setScopes(List.of(scope));
        service.approveEnrollment(enrollment.getEnrollmentId(), approve);

        AgentConnectionAuthorizationRequest approved = new AgentConnectionAuthorizationRequest();
        approved.setGatewayNodeId("gateway-node-001");
        approved.setClaimedAgentId("agent-001");
        approved.setCredentialToken("token-001");

        var result = service.authorizeConnection(approved);
        assertThat(result.getDecision()).isEqualTo(AgentAuthorizationDecision.ALLOW);
        assertThat(result.getCapabilities()).containsExactly("issue-analysis");
        assertThat(result.getAllowedSystemCodes()).containsExactly("GITHUB");
    }

    @Test
    void shouldRejectApprovalWithoutCredentialMaterial() {
        AgentGovernanceService service = new AgentGovernanceService(new InMemoryAgentGovernanceRepository());
        AgentEnrollmentRequest enrollment = new AgentEnrollmentRequest();
        enrollment.setClaimedAgentId("agent-no-credential");
        enrollment = service.submitEnrollment(enrollment);

        AgentEnrollmentApprovalCommand approve = new AgentEnrollmentApprovalCommand();
        approve.setApprovedBy("admin");

        String enrollmentId = enrollment.getEnrollmentId();
        assertThatThrownBy(() -> service.approveEnrollment(enrollmentId, approve))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("credentialToken");

        AgentConnectionAuthorizationRequest request = new AgentConnectionAuthorizationRequest();
        request.setClaimedAgentId("agent-no-credential");
        request.setCredentialToken("any-token");
        assertThat(service.authorizeConnection(request).getDecision()).isEqualTo(AgentAuthorizationDecision.DENY);
    }

    @Test
    void shouldDenyDisabledAgentEvenWithValidCredential() {
        AgentGovernanceService service = new AgentGovernanceService(new InMemoryAgentGovernanceRepository());
        AgentEnrollmentRequest enrollment = new AgentEnrollmentRequest();
        enrollment.setClaimedAgentId("agent-002");
        enrollment = service.submitEnrollment(enrollment);

        AgentEnrollmentApprovalCommand approve = new AgentEnrollmentApprovalCommand();
        approve.setCredentialToken("token-002");
        service.approveEnrollment(enrollment.getEnrollmentId(), approve);
        service.disableAgent("agent-002", "admin", "maintenance");

        AgentConnectionAuthorizationRequest request = new AgentConnectionAuthorizationRequest();
        request.setClaimedAgentId("agent-002");
        request.setCredentialToken("token-002");

        assertThat(service.authorizeConnection(request).getDecision()).isEqualTo(AgentAuthorizationDecision.DENY);
    }
    @Test
    void shouldAllowRejectedEnrollmentToBeApprovedAgainWhenHumanReviewWasWrong() {
        AgentGovernanceService service = new AgentGovernanceService(new InMemoryAgentGovernanceRepository());
        AgentEnrollmentRequest enrollment = new AgentEnrollmentRequest();
        enrollment.setClaimedAgentId("agent-reapprove");
        enrollment = service.submitEnrollment(enrollment);

        service.rejectEnrollment(enrollment.getEnrollmentId(), null);

        AgentEnrollmentApprovalCommand approve = new AgentEnrollmentApprovalCommand();
        approve.setCredentialToken("token-reapprove");
        var profile = service.approveEnrollment(enrollment.getEnrollmentId(), approve);

        assertThat(profile.getApprovalStatus().name()).isEqualTo("APPROVED");
        assertThat(profile.isEnabled()).isTrue();
        assertThat(profile.getCredential()).isNotNull();
        assertThat(profile.getCredential().getCredentialStatus().name()).isEqualTo("ACTIVE");
    }

    @Test
    void shouldExposeCredentialSummaryWithoutTokenHashInEnrichedProfile() {
        AgentGovernanceService service = new AgentGovernanceService(new InMemoryAgentGovernanceRepository());
        AgentEnrollmentRequest enrollment = new AgentEnrollmentRequest();
        enrollment.setClaimedAgentId("agent-credential-summary");
        enrollment = service.submitEnrollment(enrollment);

        AgentEnrollmentApprovalCommand approve = new AgentEnrollmentApprovalCommand();
        approve.setCredentialToken("secret-token");
        service.approveEnrollment(enrollment.getEnrollmentId(), approve);

        var profile = service.getProfile("agent-credential-summary");
        assertThat(profile.getCredential()).isNotNull();
        assertThat(profile.getCredential().getCredentialId()).startsWith("cred-");
        assertThat(profile.getCredential().getCredentialStatus().name()).isEqualTo("ACTIVE");
    }

    @Test
    void shouldIssueCredentialForExistingApprovedProfileMissingCredential() {
        InMemoryAgentGovernanceRepository repository = new InMemoryAgentGovernanceRepository();
        AgentGovernanceService service = new AgentGovernanceService(repository);
        AgentEnrollmentRequest enrollment = new AgentEnrollmentRequest();
        enrollment.setClaimedAgentId("agent-late-credential");
        enrollment = service.submitEnrollment(enrollment);

        AgentEnrollmentApprovalCommand approve = new AgentEnrollmentApprovalCommand();
        approve.setCredentialToken("initial-token");
        service.approveEnrollment(enrollment.getEnrollmentId(), approve);
        repository.revokeCredentials("agent-late-credential", "simulate missing active credential", java.time.OffsetDateTime.now());

        AgentCredentialIssueCommand issue = new AgentCredentialIssueCommand();
        issue.setCredentialToken("new-token");
        issue.setOperatorId("admin");
        issue.setReason("manual credential repair");
        var profile = service.issueCredential("agent-late-credential", issue);

        assertThat(profile.getCredential()).isNotNull();
        assertThat(profile.getCredential().getCredentialStatus().name()).isEqualTo("ACTIVE");

        AgentConnectionAuthorizationRequest request = new AgentConnectionAuthorizationRequest();
        request.setClaimedAgentId("agent-late-credential");
        request.setCredentialToken("new-token");
        assertThat(service.authorizeConnection(request).getDecision()).isEqualTo(AgentAuthorizationDecision.ALLOW);
    }

    @Test
    void shouldRevokeApprovedAgentAndRestoreWithNewCredential() {
        AgentGovernanceService service = new AgentGovernanceService(new InMemoryAgentGovernanceRepository());
        AgentEnrollmentRequest enrollment = new AgentEnrollmentRequest();
        enrollment.setClaimedAgentId("agent-revoked-restore");
        enrollment = service.submitEnrollment(enrollment);

        AgentEnrollmentApprovalCommand approve = new AgentEnrollmentApprovalCommand();
        approve.setCredentialToken("token-before-revoke");
        service.approveEnrollment(enrollment.getEnrollmentId(), approve);

        var revoked = service.revokeAgent("agent-revoked-restore", "admin", "manual review mistake test");
        assertThat(revoked.getApprovalStatus().name()).isEqualTo("REVOKED");
        assertThat(revoked.isEnabled()).isFalse();

        AgentConnectionAuthorizationRequest denied = new AgentConnectionAuthorizationRequest();
        denied.setClaimedAgentId("agent-revoked-restore");
        denied.setCredentialToken("token-before-revoke");
        assertThat(service.authorizeConnection(denied).getDecision()).isEqualTo(AgentAuthorizationDecision.DENY);

        AgentProfileApprovalCommand restore = new AgentProfileApprovalCommand();
        restore.setOperatorId("admin");
        restore.setReason("restore after mistaken revoke");
        restore.setCredentialToken("token-after-restore");
        var restored = service.approveAgent("agent-revoked-restore", restore);

        assertThat(restored.getApprovalStatus().name()).isEqualTo("APPROVED");
        assertThat(restored.isEnabled()).isTrue();
        assertThat(restored.getRiskStatus().name()).isEqualTo("NORMAL");
        assertThat(restored.getCredential()).isNotNull();
        assertThat(restored.getCredential().getCredentialStatus().name()).isEqualTo("ACTIVE");

        AgentConnectionAuthorizationRequest allowed = new AgentConnectionAuthorizationRequest();
        allowed.setClaimedAgentId("agent-revoked-restore");
        allowed.setCredentialToken("token-after-restore");
        assertThat(service.authorizeConnection(allowed).getDecision()).isEqualTo(AgentAuthorizationDecision.ALLOW);
    }

    @Test
    void shouldRejectRestoringRevokedAgentWithoutActiveOrNewCredential() {
        AgentGovernanceService service = new AgentGovernanceService(new InMemoryAgentGovernanceRepository());
        AgentEnrollmentRequest enrollment = new AgentEnrollmentRequest();
        enrollment.setClaimedAgentId("agent-restore-no-credential");
        enrollment = service.submitEnrollment(enrollment);

        AgentEnrollmentApprovalCommand approve = new AgentEnrollmentApprovalCommand();
        approve.setCredentialToken("token-before-revoke");
        service.approveEnrollment(enrollment.getEnrollmentId(), approve);
        service.revokeAgent("agent-restore-no-credential", "admin", "revoked for test");

        AgentProfileApprovalCommand restore = new AgentProfileApprovalCommand();
        restore.setOperatorId("admin");

        assertThatThrownBy(() -> service.approveAgent("agent-restore-no-credential", restore))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("credentialToken");
    }


    @Test
    void shouldDisableExistingProfileAndRevokeCredentialWhenEnrollmentIsRejected() {
        AgentGovernanceService service = new AgentGovernanceService(new InMemoryAgentGovernanceRepository());
        AgentEnrollmentRequest enrollment = new AgentEnrollmentRequest();
        enrollment.setClaimedAgentId("agent-reject-disable");
        enrollment = service.submitEnrollment(enrollment);

        AgentEnrollmentApprovalCommand approve = new AgentEnrollmentApprovalCommand();
        approve.setCredentialToken("token-before-reject");
        service.approveEnrollment(enrollment.getEnrollmentId(), approve);

        AgentEnrollmentRejectCommand reject = new AgentEnrollmentRejectCommand("admin", "manual rejection after re-check");
        service.rejectEnrollment(enrollment.getEnrollmentId(), reject);

        var profile = service.getProfile("agent-reject-disable");
        assertThat(profile.getApprovalStatus().name()).isEqualTo("REJECTED");
        assertThat(profile.isEnabled()).isFalse();
        assertThat(profile.getCredential()).isNotNull();
        assertThat(profile.getCredential().getCredentialStatus().name()).isEqualTo("REVOKED");

        AgentConnectionAuthorizationRequest request = new AgentConnectionAuthorizationRequest();
        request.setClaimedAgentId("agent-reject-disable");
        request.setCredentialToken("token-before-reject");
        var authorization = service.authorizeConnection(request);
        assertThat(authorization.getDecision()).isEqualTo(AgentAuthorizationDecision.DENY);
        assertThat(authorization.getReason()).isEqualTo(AgentAuthorizationDenyReason.AGENT_REJECTED);
        assertThat(authorization.getApprovalStatus().name()).isEqualTo("REJECTED");
        assertThat(authorization.isEnabled()).isFalse();
    }


    @Test
    void shouldNotApproveEnrollmentWhenExistingProfileIsRevoked() {
        AgentGovernanceService service = new AgentGovernanceService(new InMemoryAgentGovernanceRepository());
        AgentEnrollmentRequest enrollment = new AgentEnrollmentRequest();
        enrollment.setClaimedAgentId("agent-revoked-enrollment-guard");
        enrollment = service.submitEnrollment(enrollment);

        AgentEnrollmentApprovalCommand initialApproval = new AgentEnrollmentApprovalCommand();
        initialApproval.setCredentialToken("token-before-revoke");
        service.approveEnrollment(enrollment.getEnrollmentId(), initialApproval);
        service.revokeAgent("agent-revoked-enrollment-guard", "admin", "security revoke");

        AgentEnrollmentApprovalCommand approveAgain = new AgentEnrollmentApprovalCommand();
        approveAgain.setCredentialToken("token-should-not-restore");
        String enrollmentId = enrollment.getEnrollmentId();

        assertThatThrownBy(() -> service.approveEnrollment(enrollmentId, approveAgain))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Enrollment approval cannot restore a blocked Agent profile");

        var profile = service.getProfile("agent-revoked-enrollment-guard");
        assertThat(profile.getApprovalStatus().name()).isEqualTo("REVOKED");
        assertThat(profile.getRiskStatus().name()).isEqualTo("REVOKED");
        assertThat(profile.isEnabled()).isFalse();
        assertThat(profile.getCredential()).isNotNull();
        assertThat(profile.getCredential().getCredentialStatus().name()).isEqualTo("REVOKED");

        AgentConnectionAuthorizationRequest reconnect = new AgentConnectionAuthorizationRequest();
        reconnect.setClaimedAgentId("agent-revoked-enrollment-guard");
        reconnect.setCredentialToken("token-should-not-restore");
        var authorization = service.authorizeConnection(reconnect);
        assertThat(authorization.getDecision()).isEqualTo(AgentAuthorizationDecision.DENY);
        assertThat(authorization.getReason()).isEqualTo(AgentAuthorizationDenyReason.AGENT_REVOKED);
    }

    @Test
    void shouldNotApproveEnrollmentWhenExistingProfileIsSuspended() {
        AgentGovernanceService service = new AgentGovernanceService(new InMemoryAgentGovernanceRepository());
        AgentEnrollmentRequest enrollment = new AgentEnrollmentRequest();
        enrollment.setClaimedAgentId("agent-suspended-enrollment-guard");
        enrollment = service.submitEnrollment(enrollment);

        AgentEnrollmentApprovalCommand initialApproval = new AgentEnrollmentApprovalCommand();
        initialApproval.setCredentialToken("token-before-suspend");
        service.approveEnrollment(enrollment.getEnrollmentId(), initialApproval);
        service.suspendAgent("agent-suspended-enrollment-guard", "admin", "security suspend");

        AgentEnrollmentApprovalCommand approveAgain = new AgentEnrollmentApprovalCommand();
        approveAgain.setCredentialToken("token-should-not-restore");
        String enrollmentId = enrollment.getEnrollmentId();

        assertThatThrownBy(() -> service.approveEnrollment(enrollmentId, approveAgain))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Enrollment approval cannot restore a blocked Agent profile");

        var profile = service.getProfile("agent-suspended-enrollment-guard");
        assertThat(profile.getApprovalStatus().name()).isEqualTo("SUSPENDED");
        assertThat(profile.getRiskStatus().name()).isEqualTo("SUSPENDED");
        assertThat(profile.isEnabled()).isFalse();
    }

    @Test
    void shouldNotApproveEnrollmentWhenExistingProfileIsQuarantined() {
        AgentGovernanceService service = new AgentGovernanceService(new InMemoryAgentGovernanceRepository());
        AgentEnrollmentRequest enrollment = new AgentEnrollmentRequest();
        enrollment.setClaimedAgentId("agent-quarantined-enrollment-guard");
        enrollment = service.submitEnrollment(enrollment);

        AgentEnrollmentApprovalCommand initialApproval = new AgentEnrollmentApprovalCommand();
        initialApproval.setCredentialToken("token-before-quarantine");
        service.approveEnrollment(enrollment.getEnrollmentId(), initialApproval);

        AgentDuplicateRuntimeSecurityCommand quarantine = new AgentDuplicateRuntimeSecurityCommand();
        quarantine.setReason("duplicate runtime quarantine");
        quarantine.setRevokeCredentials(true);
        service.enforceDuplicateRuntimeSecurity("agent-quarantined-enrollment-guard", quarantine);

        AgentEnrollmentApprovalCommand approveAgain = new AgentEnrollmentApprovalCommand();
        approveAgain.setCredentialToken("token-should-not-restore");
        String enrollmentId = enrollment.getEnrollmentId();

        assertThatThrownBy(() -> service.approveEnrollment(enrollmentId, approveAgain))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Enrollment approval cannot restore a blocked Agent profile");

        var profile = service.getProfile("agent-quarantined-enrollment-guard");
        assertThat(profile.getApprovalStatus().name()).isEqualTo("APPROVED");
        assertThat(profile.getRiskStatus().name()).isEqualTo("QUARANTINED");
        assertThat(profile.isEnabled()).isFalse();
        assertThat(profile.getCredential()).isNotNull();
        assertThat(profile.getCredential().getCredentialStatus().name()).isEqualTo("REVOKED");
    }

    @Test
    void shouldRequireNewCredentialWhenRestoringBlockedProfile() {
        AgentGovernanceService service = new AgentGovernanceService(new InMemoryAgentGovernanceRepository());
        AgentEnrollmentRequest enrollment = new AgentEnrollmentRequest();
        enrollment.setClaimedAgentId("agent-blocked-restore-credential");
        enrollment = service.submitEnrollment(enrollment);

        AgentEnrollmentApprovalCommand initialApproval = new AgentEnrollmentApprovalCommand();
        initialApproval.setCredentialToken("token-before-suspend");
        service.approveEnrollment(enrollment.getEnrollmentId(), initialApproval);
        service.suspendAgent("agent-blocked-restore-credential", "admin", "security suspend");

        AgentProfileApprovalCommand restoreWithoutCredential = new AgentProfileApprovalCommand();
        restoreWithoutCredential.setOperatorId("admin");
        assertThatThrownBy(() -> service.approveAgent("agent-blocked-restore-credential", restoreWithoutCredential))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required to restore a blocked agent profile");

        AgentProfileApprovalCommand restoreWithCredential = new AgentProfileApprovalCommand();
        restoreWithCredential.setOperatorId("admin");
        restoreWithCredential.setCredentialToken("token-after-suspend-restore");
        var restored = service.approveAgent("agent-blocked-restore-credential", restoreWithCredential);

        assertThat(restored.getApprovalStatus().name()).isEqualTo("APPROVED");
        assertThat(restored.getRiskStatus().name()).isEqualTo("NORMAL");
        assertThat(restored.isEnabled()).isTrue();
    }



    @Test
    void shouldNotIssueCredentialForRejectedProfile() {
        AgentGovernanceService service = new AgentGovernanceService(new InMemoryAgentGovernanceRepository());
        AgentEnrollmentRequest enrollment = new AgentEnrollmentRequest();
        enrollment.setClaimedAgentId("agent-rejected-no-credential-issue");
        enrollment = service.submitEnrollment(enrollment);

        AgentEnrollmentApprovalCommand approve = new AgentEnrollmentApprovalCommand();
        approve.setCredentialToken("token-before-reject");
        service.approveEnrollment(enrollment.getEnrollmentId(), approve);

        service.rejectEnrollment(enrollment.getEnrollmentId(), new AgentEnrollmentRejectCommand("admin", "not allowed"));
        assertThat(service.getProfile("agent-rejected-no-credential-issue").getApprovalStatus())
                .isEqualTo(AgentApprovalStatus.REJECTED);

        AgentCredentialIssueCommand issue = new AgentCredentialIssueCommand();
        issue.setCredentialToken("token-should-not-issue");

        assertThatThrownBy(() -> service.issueCredential("agent-rejected-no-credential-issue", issue))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Credential issuance is allowed only for APPROVED Agent profiles with NORMAL risk");

        AgentConnectionAuthorizationRequest request = new AgentConnectionAuthorizationRequest();
        request.setClaimedAgentId("agent-rejected-no-credential-issue");
        request.setCredentialToken("token-should-not-issue");
        assertThat(service.authorizeConnection(request).getDecision()).isEqualTo(AgentAuthorizationDecision.DENY);
    }

    @Test
    void shouldNotIssueCredentialForQuarantinedProfile() {
        AgentGovernanceService service = new AgentGovernanceService(new InMemoryAgentGovernanceRepository());
        AgentEnrollmentRequest enrollment = new AgentEnrollmentRequest();
        enrollment.setClaimedAgentId("agent-quarantined-no-credential-issue");
        enrollment = service.submitEnrollment(enrollment);

        AgentEnrollmentApprovalCommand approve = new AgentEnrollmentApprovalCommand();
        approve.setCredentialToken("token-before-quarantine");
        service.approveEnrollment(enrollment.getEnrollmentId(), approve);

        AgentDuplicateRuntimeSecurityCommand quarantine = new AgentDuplicateRuntimeSecurityCommand();
        quarantine.setRevokeCredentials(true);
        quarantine.setReason("duplicate runtime");
        service.enforceDuplicateRuntimeSecurity("agent-quarantined-no-credential-issue", quarantine);

        AgentCredentialIssueCommand issue = new AgentCredentialIssueCommand();
        issue.setCredentialToken("token-should-not-issue");

        assertThatThrownBy(() -> service.issueCredential("agent-quarantined-no-credential-issue", issue))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Credential issuance is allowed only for APPROVED Agent profiles with NORMAL risk");
    }

    @Test
    void shouldNotEnableBlockedProfileThroughGenericProfileUpdate() {
        AgentGovernanceService service = new AgentGovernanceService(new InMemoryAgentGovernanceRepository());
        AgentEnrollmentRequest enrollment = new AgentEnrollmentRequest();
        enrollment.setClaimedAgentId("agent-update-enable-guard");
        enrollment = service.submitEnrollment(enrollment);

        AgentEnrollmentApprovalCommand approve = new AgentEnrollmentApprovalCommand();
        approve.setCredentialToken("token-before-quarantine");
        service.approveEnrollment(enrollment.getEnrollmentId(), approve);

        AgentProfileUpdateCommand quarantineAndEnable = new AgentProfileUpdateCommand();
        quarantineAndEnable.setRiskStatus(AgentRiskStatus.QUARANTINED);
        quarantineAndEnable.setEnabled(true);

        assertThatThrownBy(() -> service.updateProfile("agent-update-enable-guard", quarantineAndEnable))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Blocked or non-approved Agent profiles cannot be enabled through profile update");

        var afterRejectedUpdate = service.getProfile("agent-update-enable-guard");
        assertThat(afterRejectedUpdate.getRiskStatus()).isEqualTo(AgentRiskStatus.NORMAL);
        assertThat(afterRejectedUpdate.isEnabled()).isTrue();

        AgentProfileUpdateCommand quarantineOnly = new AgentProfileUpdateCommand();
        quarantineOnly.setRiskStatus(AgentRiskStatus.QUARANTINED);
        var updated = service.updateProfile("agent-update-enable-guard", quarantineOnly);
        assertThat(updated.getRiskStatus()).isEqualTo(AgentRiskStatus.QUARANTINED);
        assertThat(updated.isEnabled()).isFalse();
    }

}
