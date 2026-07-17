package com.opensocket.aievent.core.agent.governance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.agent.setup.AgentSetupTroubleshootingStep;

class AgentGovernanceServiceLatestAuthFailureTest {
    @Test
    void shouldReturnNoFailureWhenNoDeniedRuntimeAuthorizationExists() {
        AgentGovernanceService service = new AgentGovernanceService(new InMemoryAgentGovernanceRepository());

        AgentLatestAuthFailureResponse response = service.latestAuthFailure("redmine-agent-001");

        assertThat(response.isHasFailure()).isFalse();
        assertThat(response.getAgentId()).isEqualTo("redmine-agent-001");
        assertThat(response.getSummary()).contains("No runtime authorization failure");
        assertThat(response.getTroubleshooting()).extracting(AgentSetupTroubleshootingStep::getCode)
                .contains("NO_AUTH_FAILURE_RECORDED");
    }

    @Test
    void shouldExposeLatestInvalidCredentialAuthorizationFailureWithTroubleshooting() {
        AgentGovernanceService service = new AgentGovernanceService(new InMemoryAgentGovernanceRepository());
        approveAgent(service, "redmine-agent-001", "valid-token");

        AgentConnectionAuthorizationRequest request = new AgentConnectionAuthorizationRequest();
        request.setAgentId("redmine-agent-001");
        request.setClaimedAgentId("redmine-agent-001");
        request.setGatewayNodeId("gateway-node-stage13");
        request.setCredentialToken("wrong-token");
        request.setRemoteAddress("127.0.0.1");

        AgentConnectionAuthorizationResult result = service.authorizeConnection(request);
        AgentLatestAuthFailureResponse response = service.latestAuthFailure("redmine-agent-001");

        assertThat(result.getDecision()).isEqualTo(AgentAuthorizationDecision.DENY);
        assertThat(result.getReason()).isEqualTo(AgentAuthorizationDenyReason.CREDENTIAL_INVALID);
        assertThat(response.isHasFailure()).isTrue();
        assertThat(response.getAgentId()).isEqualTo("redmine-agent-001");
        assertThat(response.getDenyReason()).isEqualTo("CREDENTIAL_INVALID");
        assertThat(response.getEventType()).isEqualTo("INVALID_CREDENTIAL");
        assertThat(response.getSecurityEventId()).startsWith("asec-");
        assertThat(response.getGatewayNodeId()).isEqualTo("gateway-node-stage13");
        assertThat(response.getSecurityEventLink()).contains("/security-events?agentId=redmine-agent-001");
        assertThat(response.getSummary()).contains("credential");
        assertThat(response.getTroubleshooting()).extracting(AgentSetupTroubleshootingStep::getCode)
                .contains("CREDENTIAL_INVALID", "VERIFY_AUTHORIZATION");
        assertThat(response.getRepairActions()).extracting(AgentConnectionRepairAction::getActionCode)
                .contains("ROTATE_CREDENTIAL");
        assertThat(response.getMetadata()).containsEntry("denyReason", "CREDENTIAL_INVALID");
    }

    @Test
    void shouldIgnoreSuccessfulAuthorizationWhenLookingForLatestFailure() {
        AgentGovernanceService service = new AgentGovernanceService(new InMemoryAgentGovernanceRepository());
        approveAgent(service, "redmine-agent-001", "valid-token");

        AgentConnectionAuthorizationRequest bad = new AgentConnectionAuthorizationRequest();
        bad.setAgentId("redmine-agent-001");
        bad.setClaimedAgentId("redmine-agent-001");
        bad.setCredentialToken("wrong-token");
        service.authorizeConnection(bad);

        AgentConnectionAuthorizationRequest good = new AgentConnectionAuthorizationRequest();
        good.setAgentId("redmine-agent-001");
        good.setClaimedAgentId("redmine-agent-001");
        good.setCredentialToken("valid-token");
        AgentConnectionAuthorizationResult authorized = service.authorizeConnection(good);

        AgentLatestAuthFailureResponse response = service.latestAuthFailure("redmine-agent-001");

        assertThat(authorized.getDecision()).isEqualTo(AgentAuthorizationDecision.ALLOW);
        assertThat(response.isHasFailure()).isTrue();
        assertThat(response.getDenyReason()).isEqualTo("CREDENTIAL_INVALID");
    }

    @Test
    void shouldRotateCredentialThroughConnectionRepairAction() {
        AgentGovernanceService service = new AgentGovernanceService(new InMemoryAgentGovernanceRepository());
        approveAgent(service, "redmine-agent-001", "valid-token");

        AgentConnectionAuthorizationRequest bad = new AgentConnectionAuthorizationRequest();
        bad.setAgentId("redmine-agent-001");
        bad.setClaimedAgentId("redmine-agent-001");
        bad.setCredentialToken("wrong-token");
        service.authorizeConnection(bad);

        AgentConnectionRepairActionsResponse actions = service.connectionRepairActions("redmine-agent-001");
        assertThat(actions.getActions()).extracting(AgentConnectionRepairAction::getActionCode).contains("ROTATE_CREDENTIAL");

        AgentConnectionRepairActionCommand command = new AgentConnectionRepairActionCommand();
        command.setCredentialToken("rotated-token");
        command.setReason("test repair");
        AgentConnectionRepairActionResult result = service.executeConnectionRepairAction("redmine-agent-001", "ROTATE_CREDENTIAL", command);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getActionCode()).isEqualTo("ROTATE_CREDENTIAL");

        AgentConnectionAuthorizationRequest good = new AgentConnectionAuthorizationRequest();
        good.setAgentId("redmine-agent-001");
        good.setClaimedAgentId("redmine-agent-001");
        good.setCredentialToken("rotated-token");
        AgentConnectionAuthorizationResult authorized = service.authorizeConnection(good);

        assertThat(authorized.getDecision()).isEqualTo(AgentAuthorizationDecision.ALLOW);
    }

    private void approveAgent(AgentGovernanceService service, String agentId, String token) {
        AgentEnrollmentRequest enrollment = new AgentEnrollmentRequest();
        enrollment.setClaimedAgentId(agentId);
        enrollment.setTenantId("tenant-a");
        enrollment.setAgentName("Redmine Issue Agent");
        enrollment.setAgentType("ISSUE_TRACKING");
        AgentEnrollmentRequest saved = service.submitEnrollment(enrollment);

        AgentEnrollmentApprovalCommand approval = new AgentEnrollmentApprovalCommand();
        approval.setAgentId(agentId);
        approval.setTenantId("tenant-a");
        approval.setAgentName("Redmine Issue Agent");
        approval.setAgentType("ISSUE_TRACKING");
        approval.setCredentialToken(token);
        approval.setApprovedBy("test");
        service.approveEnrollment(saved.getEnrollmentId(), approval);
    }
}
