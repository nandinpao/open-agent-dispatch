package com.opensocket.aievent.core.agent.setup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.opensocket.aievent.core.agent.AgentDirectoryService;
import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.agent.AgentStatus;
import com.opensocket.aievent.core.agent.assignment.AgentAssignmentService;
import com.opensocket.aievent.core.agent.assignment.AgentCapabilityAssignment;
import com.opensocket.aievent.core.agent.assignment.AgentCapabilityAssignmentStatus;
import com.opensocket.aievent.core.agent.assignment.AgentCapabilityCatalog;
import com.opensocket.aievent.core.agent.assignment.AgentCapabilityCommand;
import com.opensocket.aievent.core.agent.assignment.AgentRuntimeBinding;
import com.opensocket.aievent.core.agent.assignment.DispatchPolicy;
import com.opensocket.aievent.core.agent.assignment.DispatchPolicyRequiredCapability;
import com.opensocket.aievent.core.agent.assignment.DispatchPolicyScope;
import com.opensocket.aievent.core.agent.assignment.RuntimeResource;
import com.opensocket.aievent.core.agent.governance.AgentApprovalStatus;
import com.opensocket.aievent.core.agent.governance.AgentCapability;
import com.opensocket.aievent.core.agent.governance.AgentCredentialStatus;
import com.opensocket.aievent.core.agent.governance.AgentCredentialSummary;
import com.opensocket.aievent.core.agent.governance.AgentEnrollmentApprovalCommand;
import com.opensocket.aievent.core.agent.governance.AgentEnrollmentRequest;
import com.opensocket.aievent.core.agent.governance.AgentEnrollmentStatus;
import com.opensocket.aievent.core.agent.governance.AgentGovernanceService;
import com.opensocket.aievent.core.agent.governance.AgentProfile;
import com.opensocket.aievent.core.agent.governance.AgentRiskStatus;

/**
 * Unit-level contract tests for the backend first-Agent setup orchestration.
 *
 * <p>The Admin UI deliberately calls a single setup endpoint. These tests make
 * sure the service does the full backend bundle instead of letting the browser
 * coordinate low-level enrollment, capability, runtime binding, and
 * Dispatch Flow APIs.</p>
 */
class AgentSetupServiceTest {
    private AgentGovernanceService governanceService;
    private AgentAssignmentService assignmentService;
    private AgentDirectoryService agentDirectoryService;
    private AgentSetupService service;

    @BeforeEach
    void setUp() {
        governanceService = mock(AgentGovernanceService.class);
        assignmentService = mock(AgentAssignmentService.class);
        agentDirectoryService = mock(AgentDirectoryService.class);
        service = new AgentSetupService(governanceService, assignmentService, agentDirectoryService);

        when(governanceService.submitEnrollment(any(AgentEnrollmentRequest.class)))
                .thenAnswer(invocation -> submittedEnrollment(invocation.getArgument(0)));
        when(governanceService.approveEnrollment(any(String.class), any(AgentEnrollmentApprovalCommand.class)))
                .thenAnswer(invocation -> approvedProfile(invocation.getArgument(1)));

        when(assignmentService.upsertCapability(any(AgentCapabilityCatalog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(assignmentService.requestAgentCapability(any(String.class), any(AgentCapabilityCommand.class)))
                .thenAnswer(invocation -> capabilityAssignment(invocation.getArgument(0), invocation.getArgument(1), AgentCapabilityAssignmentStatus.PENDING_APPROVAL));
        when(assignmentService.approveAgentCapability(any(String.class), any(String.class), any(AgentCapabilityCommand.class)))
                .thenAnswer(invocation -> capabilityAssignment(invocation.getArgument(0), invocation.getArgument(2), AgentCapabilityAssignmentStatus.APPROVED));
        when(assignmentService.upsertRuntimeResource(any(RuntimeResource.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(assignmentService.upsertRuntimeBinding(any(String.class), any(AgentRuntimeBinding.class)))
                .thenAnswer(invocation -> {
                    AgentRuntimeBinding binding = invocation.getArgument(1);
                    if (binding.getBindingId() == null) binding.setBindingId("binding-" + binding.getAgentId());
                    return binding;
                });
        when(assignmentService.upsertDispatchPolicy(any(DispatchPolicy.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(assignmentService.upsertDispatchPolicyScope(any(String.class), any(DispatchPolicyScope.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(assignmentService.upsertDispatchPolicyRequiredCapability(any(String.class), any(DispatchPolicyRequiredCapability.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));
    }

    @Test
    void shouldCreateApprovedAgentWithoutImplicitCapabilityOrDispatchData() {
        AgentSetupResponse response = service.setupAgent(defaultAutoApproveRequest());

        assertThat(response.getAgentId()).isEqualTo("redmine-agent-001");
        assertThat(response.getTenantId()).isEqualTo("tenant-a");
        assertThat(response.getSetupMode()).isEqualTo("FIRST_AGENT_SETUP");
        assertThat(response.getSetupStatus()).isEqualTo("INCOMPLETE");
        assertThat(response.getCapabilityAssignments()).isEmpty();
        assertThat(response.getCapabilityCatalog()).isEmpty();
        assertThat(response.getRuntimeBinding()).isNotNull();
        assertThat(response.getRuntimeBinding().getBindingStatus()).isEqualTo("ACTIVE");
        assertThat(response.getSupplyProfile()).isNull();
        assertThat(response.getDispatchPolicy()).isNull();
        assertThat(response.getStartCommand().getCommand()).contains("docker run", "AGENT_ID=redmine-agent-001", "AGENT_TOKEN=local-token");
        assertThat(response.getStartCommand().getDockerCommand()).contains("OPENSOCKET_AGENT_ID=redmine-agent-001");
        assertThat(response.getStartCommand().getLocalCommand()).contains("OPENSOCKET_GATEWAY_URL=http://127.0.0.1:18081");
        assertThat(response.getStartCommand().getHealthCheckCommand()).contains("/actuator/health");
        assertThat(response.getStartCommand().getVerifyConnectionCommand()).contains("/internal/agents/authorize-connection");
        assertThat(response.getStartCommand().getTroubleshooting()).extracting(AgentSetupTroubleshootingStep::getCode)
                .contains("TOKEN_MISMATCH", "GATEWAY_UNREACHABLE", "AGENT_ID_MISMATCH");
        assertThat(response.getReadinessChecks()).extracting(AgentSetupReadinessCheck::getCode)
                .contains("AGENT_APPROVED", "OPTIONAL_CAPABILITIES_READY", "RUNTIME_BINDING_ACTIVE", "RUNTIME_CONNECTED")
                .doesNotContain("CAPABILITIES_ASSIGNED", "SERVICE_SCOPE_ACTIVE", "DISPATCH_RULE_ACTIVE");
        assertThat(response.getReadinessChecks()).filteredOn(check -> "RUNTIME_CONNECTED".equals(check.getCode()))
                .singleElement()
                .satisfies(check -> assertThat(check.isReady()).isFalse());

        ArgumentCaptor<AgentEnrollmentRequest> enrollmentCaptor = ArgumentCaptor.forClass(AgentEnrollmentRequest.class);
        verify(governanceService).submitEnrollment(enrollmentCaptor.capture());
        assertThat(enrollmentCaptor.getValue().getSubmittedMetadata())
                .containsEntry("setupMode", "BACKEND_CONTRACT")
                .containsEntry("gatewayUrl", "http://127.0.0.1:18081");

        verify(assignmentService).upsertRuntimeBinding(eq("redmine-agent-001"), any(AgentRuntimeBinding.class));
        verify(assignmentService, never()).upsertCapability(any(AgentCapabilityCatalog.class));
        verify(assignmentService, never()).requestAgentCapability(any(String.class), any(AgentCapabilityCommand.class));
        verify(assignmentService, never()).upsertDispatchPolicy(any(DispatchPolicy.class));
    }

    @Test
    void shouldCreateEnrollmentDraftOnlyWhenAutoApproveIsDisabled() {
        AgentSetupRequest request = defaultAutoApproveRequest();
        request.setAutoApprove(false);
        request.setCredentialToken(null);

        AgentSetupResponse response = service.setupAgent(request);

        assertThat(response.getSetupStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(response.getEnrollment()).isNotNull();
        assertThat(response.getAgentProfile()).isNull();
        assertThat(response.getCapabilityAssignments()).isEmpty();
        assertThat(response.getRuntimeBinding()).isNull();
        assertThat(response.getSupplyProfile()).isNull();
        assertThat(response.getDispatchPolicy()).isNull();
        assertThat(response.getReadinessChecks()).extracting(AgentSetupReadinessCheck::getCode)
                .containsExactly("ENROLLMENT_CREATED", "AGENT_APPROVAL");

        verify(governanceService, never()).approveEnrollment(any(String.class), any(AgentEnrollmentApprovalCommand.class));
        verify(assignmentService, never()).upsertRuntimeBinding(any(String.class), any(AgentRuntimeBinding.class));
        verify(assignmentService, never()).upsertDispatchPolicy(any(DispatchPolicy.class));
    }

    @Test
    void shouldRejectAutoApproveWithoutCredentialToken() {
        AgentSetupRequest request = defaultAutoApproveRequest();
        request.setCredentialToken(" ");

        assertThatThrownBy(() -> service.setupAgent(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("credentialToken is required when autoApprove=true");
    }

    @Test
    void shouldRejectImplicitCapabilityCatalogCreation() {
        AgentSetupRequest request = defaultAutoApproveRequest();
        request.setCreateDefaultCapabilities(true);

        assertThatThrownBy(() -> service.setupAgent(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Automatic Capability Catalog creation was removed");
    }

    @Test
    void shouldRejectImplicitDispatchRuleCreation() {
        AgentSetupRequest request = defaultAutoApproveRequest();
        request.setCreateDefaultDispatchRule(true);

        assertThatThrownBy(() -> service.setupAgent(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Automatic Dispatch Rule creation was removed");
    }

    @Test
    void shouldPreserveExplicitCapabilityAndTaskMetadataWithoutAutoCreatingGovernance() {
        AgentSetupRequest request = defaultAutoApproveRequest();
        request.setAgentId("Data Sync Agent 001");
        request.setPurpose("data-sync");
        request.setDefaultCapabilities(List.of("data-read", "data read", "DATA_SYNC"));
        request.setDefaultTaskTypes(List.of("sync-customers", "SYNC CUSTOMERS"));

        AgentSetupResponse response = service.setupAgent(request);

        assertThat(response.getAgentId()).isEqualTo("data-sync-agent-001");
        assertThat(response.getCapabilityAssignments()).isEmpty();
        assertThat(response.getStartCommand().getExpectedCapabilities()).containsExactly("DATA_READ", "DATA_SYNC");
        assertThat(response.getDispatchPolicy()).isNull();
    }


    @Test
    void shouldReturnRemoteHostStartCommandForRemoteRuntimeType() {
        AgentSetupRequest request = defaultAutoApproveRequest();
        request.setRuntimeType("Remote Host");

        AgentSetupResponse response = service.setupAgent(request);

        assertThat(response.getStartCommand().getCommand()).contains("Run these commands on the remote host");
        assertThat(response.getStartCommand().getRemoteCommand()).contains("OPENSOCKET_AGENT_ID=redmine-agent-001");
        assertThat(response.getStartCommand().getDockerCommand()).contains("docker run");
        assertThat(response.getStartCommand().getDiagnostics()).containsEntry("tokenIncluded", true);
    }

    @Test
    void shouldPropagateDuplicateOrRuntimeBindingFailuresForOperatorVisibility() {
        when(assignmentService.upsertRuntimeBinding(any(String.class), any(AgentRuntimeBinding.class)))
                .thenThrow(new IllegalStateException("runtime binding conflict for redmine-agent-001"));

        assertThatThrownBy(() -> service.setupAgent(defaultAutoApproveRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("runtime binding conflict");
    }


    @Test
    void shouldReportRuntimeConnectedBlockingReasonBeforeHeartbeat() {
        stubReadinessDependencies(Optional.empty());

        AgentSetupReadinessResponse response = service.getSetupReadiness("redmine-agent-001");

        assertThat(response.isReady()).isFalse();
        assertThat(response.getStatus()).isEqualTo("INCOMPLETE");
        assertThat(response.getBlockingReasons()).contains("RUNTIME_CONNECTED");
        assertThat(response.getBlockingReasons()).doesNotContain("ADMIN_MANAGED_CAPABILITIES_ACTIVE");
        assertThat(response.getChecks()).filteredOn(check -> "RUNTIME_CONNECTED".equals(check.getCode()))
                .singleElement()
                .satisfies(check -> {
                    assertThat(check.isReady()).isFalse();
                    assertThat(check.getStatus()).isEqualTo("PENDING");
                    assertThat(check.getMetadata()).containsEntry("runtimeStatus", "MISSING");
                });
        assertThat(response.getMetadata()).containsEntry("sourceOfTruth", "CORE_BACKEND_READINESS")
                .containsEntry("runtimeSnapshotPresent", false);
        assertThat(response.getStartCommand().getCommand()).contains("<issued-token>");
        assertThat(response.getStartCommand().getVerifyConnectionCommand()).contains("authorize-connection");
        assertThat(response.getTroubleshooting()).extracting(AgentSetupTroubleshootingStep::getCode)
                .contains("RUNTIME_NOT_CONNECTED", "TOKEN_MISMATCH", "GATEWAY_UNREACHABLE", "AGENT_ID_MISMATCH")
                .doesNotContain("RUNTIME_CAPABILITIES_NOT_REPORTED");
    }

    @Test
    void shouldTransitionReadinessToReadyAfterRuntimeHeartbeatSnapshot() {
        AgentSnapshot heartbeatSnapshot = runtimeSnapshot(AgentStatus.IDLE);
        heartbeatSnapshot.setAvailableSlots(1);
        heartbeatSnapshot.setCurrentTaskCount(0);
        heartbeatSnapshot.setHealthScore(100);
        stubReadinessDependencies(Optional.of(heartbeatSnapshot));

        AgentSetupReadinessResponse response = service.getSetupReadiness("redmine-agent-001");

        assertThat(response.isReady()).isTrue();
        assertThat(response.getStatus()).isEqualTo("READY");
        assertThat(response.getBlockingReasons()).isEmpty();
        assertThat(response.getProfileCapabilities()).contains("ISSUE_CREATE");
        assertThat(response.getRuntimeReportedCapabilities()).contains("ISSUE_CREATE");
        assertThat(response.getMissingRuntimeCapabilities()).isEmpty();
        assertThat(response.getChecks()).filteredOn(check -> "ADMIN_MANAGED_CAPABILITIES_ACTIVE".equals(check.getCode()))
                .singleElement()
                .satisfies(check -> assertThat(check.isReady()).isTrue());
        assertThat(response.getChecks()).filteredOn(check -> "RUNTIME_CONNECTED".equals(check.getCode()))
                .singleElement()
                .satisfies(check -> {
                    assertThat(check.isReady()).isTrue();
                    assertThat(check.getStatus()).isEqualTo("READY");
                    assertThat(check.getMetadata()).containsEntry("runtimeStatus", "IDLE")
                            .containsEntry("assignable", true);
                });
    }

    @Test
    void shouldReturnIncompleteWhenRuntimeDisconnectsAfterBeingReady() {
        AgentSnapshot disconnectedSnapshot = runtimeSnapshot(AgentStatus.OFFLINE);
        stubReadinessDependencies(Optional.of(disconnectedSnapshot));

        AgentSetupReadinessResponse response = service.getSetupReadiness("redmine-agent-001");

        assertThat(response.isReady()).isFalse();
        assertThat(response.getStatus()).isEqualTo("INCOMPLETE");
        assertThat(response.getBlockingReasons()).contains("RUNTIME_CONNECTED");
        assertThat(response.getBlockingReasons()).doesNotContain("ADMIN_MANAGED_CAPABILITIES_ACTIVE");
        assertThat(response.getChecks()).filteredOn(check -> "RUNTIME_CONNECTED".equals(check.getCode()))
                .singleElement()
                .satisfies(check -> {
                    assertThat(check.isReady()).isFalse();
                    assertThat(check.getMetadata()).containsEntry("runtimeStatus", "OFFLINE")
                            .containsEntry("assignable", false);
                });
    }

    private AgentSetupRequest defaultAutoApproveRequest() {
        AgentSetupRequest request = new AgentSetupRequest();
        request.setTenantId("tenant-a");
        request.setAgentId("Redmine Agent 001");
        request.setAgentName("Redmine Issue Agent");
        request.setOwnerTeam("ops");
        request.setPurpose("ISSUE_TRACKING");
        request.setRuntimeType("Docker");
        request.setGatewayUrl("http://127.0.0.1:18081");
        request.setCredentialToken("local-token");
        request.setAutoApprove(true);
        request.setCreateDefaultCapabilities(false);
        request.setCreateRuntimeBinding(true);
        request.setCreateSupplyProfile(true);
        request.setCreateDefaultDispatchRule(false);
        request.setDefaultCapabilities(List.of("ISSUE_CREATE", "ISSUE_UPDATE", "CALLBACK_HANDLE"));
        request.setDefaultTaskTypes(List.of("ISSUE_CREATE", "ISSUE_UPDATE"));
        request.setCapacityLimit(2);
        request.setOperatorId("qa-operator");
        request.setMetadata(Map.of("testCase", "stage9"));
        return request;
    }

    private AgentEnrollmentRequest submittedEnrollment(AgentEnrollmentRequest request) {
        request.setEnrollmentId("enroll-" + request.getClaimedAgentId());
        request.setStatus(AgentEnrollmentStatus.PENDING_REVIEW);
        return request;
    }

    private AgentProfile approvedProfile(AgentEnrollmentApprovalCommand command) {
        AgentProfile profile = new AgentProfile();
        profile.setAgentId(command.getAgentId());
        profile.setTenantId(command.getTenantId());
        profile.setAgentName(command.getAgentName());
        profile.setAgentType(command.getAgentType());
        profile.setOwnerTeam(command.getOwnerTeam());
        profile.setApprovalStatus(AgentApprovalStatus.APPROVED);
        profile.setEnabled(true);
        profile.setRiskStatus(AgentRiskStatus.NORMAL);
        return profile;
    }


    private void stubReadinessDependencies(Optional<AgentSnapshot> runtimeSnapshot) {
        AgentProfile profile = approvedProfile(approvalCommandFor("redmine-agent-001"));
        profile.setCredential(activeCredential());
        profile.setCapabilities(List.of(new AgentCapability("redmine-agent-001", "ISSUE_CREATE")));
        when(governanceService.getProfile("redmine-agent-001")).thenReturn(profile);
        when(assignmentService.findAgentCapabilities("redmine-agent-001"))
                .thenReturn(List.of(capabilityAssignment("redmine-agent-001", capabilityCommand("tenant-a", "ISSUE_CREATE"), AgentCapabilityAssignmentStatus.APPROVED)));

        AgentRuntimeBinding binding = new AgentRuntimeBinding();
        binding.setTenantId("tenant-a");
        binding.setAgentId("redmine-agent-001");
        binding.setBindingId("binding-redmine-agent-001");
        binding.setRuntimeId("runtime-redmine-agent-001");
        binding.setRuntimeCode("redmine-agent-runtime");
        binding.setBindingStatus("ACTIVE");
        when(assignmentService.findRuntimeBindingsByAgent("redmine-agent-001", "ACTIVE"))
                .thenReturn(List.of(binding));

        DispatchPolicy policy = new DispatchPolicy();
        policy.setTenantId("tenant-a");
        policy.setPolicyCode("DEFAULT_ISSUE_TRACKING_DISPATCH_RULE");
        policy.setStatus("ACTIVE");
        when(assignmentService.searchDispatchPolicies("tenant-a", "ACTIVE", 500))
                .thenReturn(List.of(policy));
        when(agentDirectoryService.findAgent("redmine-agent-001")).thenReturn(runtimeSnapshot);
    }

    private AgentEnrollmentApprovalCommand approvalCommandFor(String agentId) {
        AgentEnrollmentApprovalCommand command = new AgentEnrollmentApprovalCommand();
        command.setAgentId(agentId);
        command.setTenantId("tenant-a");
        command.setAgentName("Redmine Issue Agent");
        command.setAgentType("ISSUE_TRACKING");
        command.setOwnerTeam("ops");
        return command;
    }

    private AgentCredentialSummary activeCredential() {
        AgentCredentialSummary credential = new AgentCredentialSummary();
        credential.setCredentialId("cred-redmine-agent-001");
        credential.setCredentialStatus(AgentCredentialStatus.ACTIVE);
        credential.setCredentialVersion(1);
        return credential;
    }

    private AgentCapabilityCommand capabilityCommand(String tenantId, String capabilityCode) {
        AgentCapabilityCommand command = new AgentCapabilityCommand();
        command.setTenantId(tenantId);
        command.setCapabilityCode(capabilityCode);
        command.setSource("TEST");
        command.setReason("test readiness transition");
        return command;
    }

    private AgentSnapshot runtimeSnapshot(AgentStatus status) {
        AgentSnapshot snapshot = new AgentSnapshot();
        snapshot.setAgentId("redmine-agent-001");
        snapshot.setOwnerGatewayNodeId("gateway-node-001");
        snapshot.setAgentSessionId("session-redmine-agent-001");
        snapshot.setStatus(status);
        snapshot.setMaxConcurrentTasks(1);
        snapshot.setAvailableSlots(status == AgentStatus.IDLE ? 1 : 0);
        snapshot.setCapabilities(List.of("ISSUE_CREATE", "ISSUE_UPDATE"));
        snapshot.setPluginName("stage11-runtime");
        return snapshot;
    }

    private AgentCapabilityAssignment capabilityAssignment(String agentId,
                                                           AgentCapabilityCommand command,
                                                           AgentCapabilityAssignmentStatus status) {
        AgentCapabilityAssignment assignment = new AgentCapabilityAssignment();
        assignment.setTenantId(command.getTenantId());
        assignment.setAssignmentId("assignment-" + command.getCapabilityCode().toLowerCase());
        assignment.setAgentId(agentId);
        assignment.setCapabilityCode(command.getCapabilityCode());
        assignment.setStatus(status);
        assignment.setSource(command.getSource());
        assignment.setReason(command.getReason());
        return assignment;
    }
}
