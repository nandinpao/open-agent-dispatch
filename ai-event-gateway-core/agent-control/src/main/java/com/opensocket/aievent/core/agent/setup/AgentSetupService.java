package com.opensocket.aievent.core.agent.setup;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opensocket.aievent.core.agent.AgentDirectoryService;
import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.agent.AgentRuntimeCapabilityItem;
import com.opensocket.aievent.core.agent.AgentStatus;
import com.opensocket.aievent.core.agent.assignment.AgentAssignmentService;
import com.opensocket.aievent.core.agent.assignment.AgentCapabilityAssignment;
import com.opensocket.aievent.core.agent.assignment.AgentCapabilityAssignmentStatus;
import com.opensocket.aievent.core.agent.assignment.AgentRuntimeBinding;
import com.opensocket.aievent.core.agent.assignment.RuntimeResource;
import com.opensocket.aievent.core.agent.governance.AgentAuthorizationScope;
import com.opensocket.aievent.core.agent.governance.AgentEnrollmentApprovalCommand;
import com.opensocket.aievent.core.agent.governance.AgentEnrollmentRequest;
import com.opensocket.aievent.core.agent.governance.AgentGovernanceService;
import com.opensocket.aievent.core.agent.governance.AgentProfile;

@Service
public class AgentSetupService {
    private final AgentGovernanceService governanceService;
    private final AgentAssignmentService assignmentService;
    private final AgentDirectoryService agentDirectoryService;

    public AgentSetupService(AgentGovernanceService governanceService,
                             AgentAssignmentService assignmentService) {
        this(governanceService, assignmentService, null);
    }

    @Autowired
    public AgentSetupService(AgentGovernanceService governanceService,
                             AgentAssignmentService assignmentService,
                             AgentDirectoryService agentDirectoryService) {
        this.governanceService = governanceService;
        this.assignmentService = assignmentService;
        this.agentDirectoryService = agentDirectoryService;
    }


    public AgentSetupReadinessResponse getSetupReadiness(String agentId) {
        if (blank(agentId)) {
            throw new IllegalArgumentException("agentId is required");
        }
        String normalizedAgentId = normalizeAgentId(agentId);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        AgentProfile profile = findProfile(normalizedAgentId);
        String tenantId = profile == null ? null : normalizeRequiredTenant(profile.getTenantId());
        List<AgentCapabilityAssignment> capabilityAssignments = safeList(() -> assignmentService.findAgentCapabilities(normalizedAgentId));
        List<AgentRuntimeBinding> runtimeBindings = safeList(() -> assignmentService.findRuntimeBindingsByAgent(normalizedAgentId, "ACTIVE"));
        AgentSnapshot runtimeSnapshot = findRuntimeSnapshot(normalizedAgentId);
        List<String> profileCapabilityCodes = approvedProfileCapabilities(profile, capabilityAssignments);
        List<String> runtimeReportedCapabilities = runtimeReportedCapabilities(normalizedAgentId, runtimeSnapshot);
        // Capability ownership is admin-managed in Core. Runtime heartbeats may include optional
        // observations, but absence from the heartbeat must not block dispatch readiness.
        List<String> missingRuntimeCapabilities = List.of();
        List<String> extraRuntimeCapabilities = runtimeReportedCapabilities.stream()
                .filter(capability -> !profileCapabilityCodes.contains(capability))
                .toList();

        List<AgentSetupReadinessCheck> checks = new ArrayList<>();
        checks.add(profile != null
                ? AgentSetupReadinessCheck.ready("AGENT_PROFILE_EXISTS", "Agent profile exists", "Core has a managed Agent profile for this Agent.")
                : AgentSetupReadinessCheck.pending("AGENT_PROFILE_EXISTS", "Agent profile exists", "Create or approve this Agent profile before it can receive tasks.", "Create Agent"));
        checks.add(profile != null && profile.allowsConnection()
                ? AgentSetupReadinessCheck.ready("AGENT_APPROVED", "Agent approved and enabled", "The Agent profile is approved, enabled, and not under security restriction.")
                : AgentSetupReadinessCheck.pending("AGENT_APPROVED", "Agent approved and enabled", "Approve and enable the Agent profile before runtime connection and dispatch.", "Approve Agent"));
        checks.add(profile != null && profile.getCredential() != null && "ACTIVE".equalsIgnoreCase(String.valueOf(profile.getCredential().getCredentialStatus()))
                ? AgentSetupReadinessCheck.ready("CREDENTIAL_ACTIVE", "Credential is active", "The Agent has active credential material for runtime authentication.")
                : AgentSetupReadinessCheck.pending("CREDENTIAL_ACTIVE", "Credential is active", "Issue or rotate a runtime credential before starting the Agent.", "Issue Credential"));

        checks.add(AgentSetupReadinessCheck.ready(
                "OPTIONAL_CAPABILITIES_READY",
                "Capabilities optional",
                "Capabilities are optional and are checked only when a Dispatch Flow rule explicitly requires them."));

        boolean runtimeBindingReady = runtimeBindings.stream().anyMatch(binding -> "ACTIVE".equalsIgnoreCase(binding.getBindingStatus()));
        checks.add(runtimeBindingReady
                ? AgentSetupReadinessCheck.ready("RUNTIME_BINDING_ACTIVE", "Runtime binding active", "The Agent has an active runtime binding in Core.")
                : AgentSetupReadinessCheck.pending("RUNTIME_BINDING_ACTIVE", "Runtime binding active", "Create or activate a runtime binding before this Agent can be considered dispatch-ready.", "Configure Connection"));


        boolean runtimeConnected = runtimeSnapshot != null && runtimeConnected(runtimeSnapshot);
        AgentSetupReadinessCheck runtimeCheck = runtimeConnected
                ? AgentSetupReadinessCheck.ready("RUNTIME_CONNECTED", "Runtime connected", "The Agent runtime is connected and sending heartbeat data.")
                : AgentSetupReadinessCheck.pending("RUNTIME_CONNECTED", "Runtime connected", "Start the Agent runtime and wait for its first heartbeat.", "Start Agent Runtime");
        Map<String, Object> runtimeMetadata = new LinkedHashMap<>();
        if (runtimeSnapshot != null) {
            runtimeMetadata.put("runtimeStatus", runtimeSnapshot.getStatus() == null ? null : runtimeSnapshot.getStatus().name());
            runtimeMetadata.put("lastHeartbeatAt", runtimeSnapshot.getLastHeartbeatAt());
            runtimeMetadata.put("connectedAt", runtimeSnapshot.getConnectedAt());
            runtimeMetadata.put("availableSlots", runtimeSnapshot.getAvailableSlots());
            runtimeMetadata.put("assignable", runtimeSnapshot.isAssignable());
        } else {
            runtimeMetadata.put("runtimeStatus", "MISSING");
        }
        runtimeCheck.setMetadata(runtimeMetadata);
        checks.add(runtimeCheck);

        if (!profileCapabilityCodes.isEmpty()) {
            AgentSetupReadinessCheck adminCapabilityCheck = AgentSetupReadinessCheck.ready(
                    "ADMIN_MANAGED_CAPABILITIES_ACTIVE",
                    "Admin-managed capabilities active",
                    "Core-approved capabilities are the dispatch source of truth. Runtime capability self-reporting is optional diagnostic evidence only.");
            Map<String, Object> capabilityMetadata = new LinkedHashMap<>();
            capabilityMetadata.put("profileCapabilities", profileCapabilityCodes);
            capabilityMetadata.put("runtimeReportedCapabilities", runtimeReportedCapabilities);
            capabilityMetadata.put("missingRuntimeCapabilities", missingRuntimeCapabilities);
            capabilityMetadata.put("extraRuntimeCapabilities", extraRuntimeCapabilities);
            capabilityMetadata.put("runtimeCapabilityObservationMode", "OPTIONAL_DIAGNOSTIC_ONLY");
            adminCapabilityCheck.setMetadata(capabilityMetadata);
            checks.add(adminCapabilityCheck);
        }

        List<String> blockingReasons = checks.stream()
                .filter(check -> !check.isReady())
                .map(AgentSetupReadinessCheck::getCode)
                .toList();
        boolean ready = blockingReasons.isEmpty();

        AgentSetupReadinessResponse response = new AgentSetupReadinessResponse();
        response.setTenantId(tenantId);
        response.setAgentId(normalizedAgentId);
        response.setReady(ready);
        response.setStatus(ready ? "READY" : profile == null ? "MISSING_PROFILE" : "INCOMPLETE");
        response.setSummary(ready
                ? "Agent is ready to receive tasks."
                : "Agent is not ready to receive tasks. Resolve the blocking checks first.");
        response.setBlockingReasons(blockingReasons);
        response.setChecks(checks);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("contract", "GET /admin/agents/{agentId}/setup-readiness");
        metadata.put("sourceOfTruth", "CORE_BACKEND_READINESS");
        metadata.put("approvedCapabilityCount", approvedCapabilityCount(profile, capabilityAssignments));
        metadata.put("profileCapabilities", profileCapabilityCodes);
        metadata.put("runtimeReportedCapabilities", runtimeReportedCapabilities);
        metadata.put("missingRuntimeCapabilities", missingRuntimeCapabilities);
        metadata.put("extraRuntimeCapabilities", extraRuntimeCapabilities);
        metadata.put("activeRuntimeBindingCount", runtimeBindings.size());
        metadata.put("activeFlowUsageCount", 0);
        metadata.put("activeDispatchRuleCount", 0);
        metadata.put("runtimeSnapshotPresent", runtimeSnapshot != null);
        RuntimeResource runtimeResource = runtimeBindings.stream()
                .findFirst()
                .map(binding -> findRuntimeResource(tenantId, binding))
                .orElse(null);
        AgentSetupStartCommand startCommand = buildStartCommandForReadiness(profile, normalizedAgentId, runtimeResource, profileCapabilityCodes);
        response.setProfileCapabilities(profileCapabilityCodes);
        response.setRuntimeReportedCapabilities(runtimeReportedCapabilities);
        response.setMissingRuntimeCapabilities(missingRuntimeCapabilities);
        response.setExtraRuntimeCapabilities(extraRuntimeCapabilities);
        response.setStartCommand(startCommand);
        response.setTroubleshooting(troubleshooting(profile, runtimeSnapshot, startCommand, blockingReasons));
        response.setMetadata(metadata);
        response.setGeneratedAt(now);
        return response;
    }


    @Transactional
    public AgentSetupResponse setupAgent(AgentSetupRequest request) {
        AgentSetupRequest body = request == null ? new AgentSetupRequest() : request;
        validate(body);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String tenantId = requireTenant(body.getTenantId());
        String agentId = normalizeAgentId(body.getAgentId());
        String purpose = normalizeCode(body.getPurpose());
        List<String> capabilityCodes = normalizedList(body.getDefaultCapabilities());
        List<String> taskTypes = List.of();

        AgentSetupResponse response = new AgentSetupResponse();
        response.setTenantId(tenantId);
        response.setAgentId(agentId);
        response.setCreatedAt(now);

        AgentEnrollmentRequest enrollment = submitEnrollment(body, tenantId, agentId, purpose, capabilityCodes, taskTypes, now);
        response.setEnrollment(enrollment);

        if (!body.isAutoApprove()) {
            response.setSetupStatus("PENDING_REVIEW");
            response.setReadinessChecks(List.of(
                    AgentSetupReadinessCheck.ready("ENROLLMENT_CREATED", "Enrollment draft created", "The enrollment request was created and is waiting for review."),
                    AgentSetupReadinessCheck.pending("AGENT_APPROVAL", "Agent approval", "Approve this enrollment before runtime connection and dispatch setup can be completed.", "Open Enrollment Review")
            ));
            response.setStartCommand(buildStartCommand(body, agentId));
            response.setMetadata(metadata(body, purpose, capabilityCodes, taskTypes, "Enrollment draft created; default runtime and dispatch records were not created because autoApprove=false."));
            return response;
        }

        AgentProfile profile = approveEnrollment(enrollment, body, tenantId, agentId, purpose, capabilityCodes, taskTypes);
        response.setAgentProfile(profile);

        List<AgentCapabilityAssignment> assignments = List.of();
        response.setCapabilityCatalog(List.of());
        response.setCapabilityAssignments(assignments);

        RuntimeResource runtimeResource = null;
        AgentRuntimeBinding runtimeBinding = null;

        if (body.isCreateRuntimeBinding()) {
            runtimeResource = upsertRuntimeResource(body, tenantId, agentId, purpose);
            response.setRuntimeResource(runtimeResource);
            runtimeBinding = upsertRuntimeBinding(body, tenantId, agentId, runtimeResource);
            response.setRuntimeBinding(runtimeBinding);
        }

        response.setDispatchPolicy(null);

        response.setStartCommand(buildStartCommand(body, agentId));
        response.setReadinessChecks(readinessChecks(profile, capabilityCodes, assignments, runtimeBinding));
        response.setSetupStatus(response.getReadinessChecks().stream().allMatch(AgentSetupReadinessCheck::isReady) ? "READY" : "INCOMPLETE");
        response.setMetadata(metadata(body, purpose, capabilityCodes, taskTypes, "Agent setup completed through backend setup contract."));
        return response;
    }

    private AgentEnrollmentRequest submitEnrollment(AgentSetupRequest request,
                                                    String tenantId,
                                                    String agentId,
                                                    String purpose,
                                                    List<String> capabilityCodes,
                                                    List<String> taskTypes,
                                                    OffsetDateTime now) {
        AgentEnrollmentRequest enrollment = new AgentEnrollmentRequest();
        enrollment.setClaimedAgentId(agentId);
        enrollment.setTenantId(tenantId);
        enrollment.setAgentName(request.getAgentName().trim());
        enrollment.setAgentType(purpose);
        enrollment.setSubmittedAt(now);
        Map<String, Object> submitted = new LinkedHashMap<>();
        submitted.put("source", "ADMIN_UI_AGENT_SETUP_CONTRACT");
        submitted.put("runtimeType", firstNonBlank(request.getRuntimeType(), "Docker"));
        submitted.put("gatewayUrl", firstNonBlank(request.getGatewayUrl(), "http://localhost:18081"));
        submitted.put("defaultCapabilities", capabilityCodes);
        submitted.put("defaultTaskTypes", taskTypes);
        submitted.put("setupMode", "BACKEND_CONTRACT");
        submitted.putAll(request.getMetadata());
        enrollment.setSubmittedMetadata(submitted);
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("createdFrom", "POST /admin/agents/setup");
        evidence.put("purpose", purpose);
        evidence.put("autoApprove", request.isAutoApprove());
        enrollment.setEvidence(evidence);
        return governanceService.submitEnrollment(enrollment);
    }

    private AgentProfile approveEnrollment(AgentEnrollmentRequest enrollment,
                                           AgentSetupRequest request,
                                           String tenantId,
                                           String agentId,
                                           String purpose,
                                           List<String> capabilityCodes,
                                           List<String> taskTypes) {
        AgentEnrollmentApprovalCommand approval = new AgentEnrollmentApprovalCommand();
        approval.setAgentId(agentId);
        approval.setApprovedBy(firstNonBlank(request.getOperatorId(), "admin-ui"));
        approval.setTenantId(tenantId);
        approval.setAgentName(request.getAgentName().trim());
        approval.setAgentType(purpose);
        approval.setOwnerTeam(request.getOwnerTeam());
        approval.setDescription(firstNonBlank(request.getDescription(), "Agent created from Admin UI setup contract."));
        approval.setComment("Approved through POST /admin/agents/setup.");
        approval.setCapabilities(capabilityCodes);
        approval.setScopes(taskTypes.stream().map(taskType -> {
            AgentAuthorizationScope scope = new AgentAuthorizationScope();
            scope.setTenantId(tenantId);
            scope.setSystemCode("*");
            scope.setTaskType(taskType);
            scope.setEnabled(true);
            return scope;
        }).toList());
        approval.setCredentialToken(request.getCredentialToken());
        return governanceService.approveEnrollment(enrollment.getEnrollmentId(), approval);
    }

    private RuntimeResource upsertRuntimeResource(AgentSetupRequest request, String tenantId, String agentId, String purpose) {
        RuntimeResource resource = new RuntimeResource();
        String runtimeCode = normalizeRuntimeCode(agentId + "-runtime");
        resource.setTenantId(tenantId);
        resource.setRuntimeId("runtime-" + runtimeCode);
        resource.setRuntimeCode(runtimeCode);
        resource.setRuntimeName(firstNonBlank(request.getAgentName(), agentId) + " Runtime");
        resource.setRuntimeType(firstNonBlank(request.getRuntimeType(), "Docker"));
        resource.setConnectorType(purpose);
        resource.setExecutionHost(firstNonBlank(request.getGatewayUrl(), "http://localhost:18081"));
        resource.setEnvironment("default");
        resource.setTrustStatus("TRUSTED");
        resource.setStatus("ACTIVE");
        resource.setCapacityLimit(Math.max(1, request.getCapacityLimit()));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("gatewayUrl", firstNonBlank(request.getGatewayUrl(), "http://localhost:18081"));
        metadata.put("createdBy", "POST /admin/agents/setup");
        metadata.put("purpose", purpose);
        resource.setMetadata(metadata);
        return assignmentService.upsertRuntimeResource(resource);
    }

    private AgentRuntimeBinding upsertRuntimeBinding(AgentSetupRequest request, String tenantId, String agentId, RuntimeResource resource) {
        AgentRuntimeBinding binding = new AgentRuntimeBinding();
        binding.setTenantId(tenantId);
        binding.setAgentId(agentId);
        binding.setRuntimeId(resource.getRuntimeId());
        binding.setRuntimeCode(resource.getRuntimeCode());
        binding.setBindingStatus("ACTIVE");
        binding.setVerifiedBy(firstNonBlank(request.getOperatorId(), "admin-ui"));
        binding.setVerifiedAt(OffsetDateTime.now(ZoneOffset.UTC));
        binding.setApprovedBy(firstNonBlank(request.getOperatorId(), "admin-ui"));
        binding.setApprovedAt(OffsetDateTime.now(ZoneOffset.UTC));
        binding.setCapacityLimit(Math.max(1, request.getCapacityLimit()));
        binding.setDataScope("STANDARD");
        binding.setRiskLimit("MIDDLE");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("createdBy", "POST /admin/agents/setup");
        metadata.put("gatewayUrl", firstNonBlank(request.getGatewayUrl(), "http://localhost:18081"));
        binding.setMetadata(metadata);
        return assignmentService.upsertRuntimeBinding(agentId, binding);
    }

    private List<AgentSetupReadinessCheck> readinessChecks(AgentProfile profile,
                                                           List<String> capabilityCodes,
                                                           List<AgentCapabilityAssignment> capabilityAssignments,
                                                           AgentRuntimeBinding runtimeBinding) {
        List<AgentSetupReadinessCheck> checks = new ArrayList<>();
        checks.add(profile != null && profile.allowsConnection()
                ? AgentSetupReadinessCheck.ready("AGENT_APPROVED", "Agent approved and enabled", "The Core profile can connect to the gateway.")
                : AgentSetupReadinessCheck.pending("AGENT_APPROVAL", "Agent approval", "Approve and enable the Agent profile.", "Open Agent Detail"));
        checks.add(AgentSetupReadinessCheck.ready(
                "OPTIONAL_CAPABILITIES_READY",
                "Capabilities optional",
                "Capabilities are optional and are checked only when a Dispatch Flow rule explicitly requires them."));
        checks.add(runtimeBinding != null && "ACTIVE".equalsIgnoreCase(runtimeBinding.getBindingStatus())
                ? AgentSetupReadinessCheck.ready("RUNTIME_BINDING_ACTIVE", "Runtime binding active", "The Agent has an active runtime binding.")
                : AgentSetupReadinessCheck.pending("RUNTIME_BINDING_ACTIVE", "Runtime binding active", "Create or activate a runtime binding.", "Configure Connection"));
        checks.add(AgentSetupReadinessCheck.pending("RUNTIME_CONNECTED", "Runtime connected", "Start the Agent runtime and wait for its first heartbeat.", "Copy Start Command"));
        return checks;
    }

    private AgentSetupStartCommand buildStartCommand(AgentSetupRequest request, String agentId) {
        String runtimeType = firstNonBlank(request.getRuntimeType(), "Docker");
        String gatewayUrl = firstNonBlank(request.getGatewayUrl(), "http://localhost:18081");
        String token = firstNonBlank(request.getCredentialToken(), "<issued-token>");
        String purpose = normalizeCode(request.getPurpose());
        List<String> capabilityCodes = normalizedList(request.getDefaultCapabilities());
        return buildStartCommand(agentId, runtimeType, gatewayUrl, token, purpose, true, capabilityCodes);
    }

    private AgentSetupStartCommand buildStartCommandForReadiness(AgentProfile profile, String agentId, RuntimeResource runtimeResource, List<String> capabilityCodes) {
        String runtimeType = runtimeResource == null ? "Docker" : firstNonBlank(runtimeResource.getRuntimeType(), "Docker");
        String gatewayUrl = runtimeResource == null ? "http://localhost:18081" : firstNonBlank(runtimeResource.getExecutionHost(), metadataString(runtimeResource.getMetadata(), "gatewayUrl"), "http://localhost:18081");
        String purpose = profile == null ? "GENERAL" : normalizeCode(firstNonBlank(profile.getAgentType(), "GENERAL"));
        return buildStartCommand(agentId, runtimeType, gatewayUrl, "<issued-token>", purpose, false, capabilityCodes);
    }

    private AgentSetupStartCommand buildStartCommand(String agentId,
                                                     String runtimeType,
                                                     String gatewayUrl,
                                                     String token,
                                                     String purpose,
                                                     boolean includeSensitiveToken,
                                                     List<String> capabilityCodes) {
        String normalizedRuntimeType = firstNonBlank(runtimeType, "Docker");
        String normalizedGatewayUrl = firstNonBlank(gatewayUrl, "http://localhost:18081");
        String runtimeToken = firstNonBlank(token, "<issued-token>");
        String image = "opendispatch/agent-runtime:local";
        String healthCheckUrl = normalizedGatewayUrl.replaceAll("/+$", "") + "/actuator/health";
        String authUrl = "${CORE_URL:-http://127.0.0.1:18080}/internal/agents/authorize-connection";
        List<String> expectedCapabilities = normalizedList(capabilityCodes);

        String dockerCommand = String.join(" \\n",
                "docker run --rm --name opendispatch-" + agentId,
                "  -e OPENSOCKET_AGENT_ID=" + shell(agentId),
                "  -e OPENSOCKET_AGENT_TOKEN=" + shell(runtimeToken),
                "  -e OPENSOCKET_GATEWAY_URL=" + shell(normalizedGatewayUrl),
                "  -e AGENT_ID=" + shell(agentId),
                "  -e AGENT_TOKEN=" + shell(runtimeToken),
                "  -e GATEWAY_URL=" + shell(normalizedGatewayUrl),
                "  " + image);
        String localCommand = String.join("\n",
                "export OPENSOCKET_AGENT_ID=" + shell(agentId),
                "export OPENSOCKET_AGENT_TOKEN=" + shell(runtimeToken),
                "export OPENSOCKET_GATEWAY_URL=" + shell(normalizedGatewayUrl),
                "export AGENT_ID=$OPENSOCKET_AGENT_ID",
                "export AGENT_TOKEN=$OPENSOCKET_AGENT_TOKEN",
                "export GATEWAY_URL=$OPENSOCKET_GATEWAY_URL",
                "./bin/start-agent.sh");
        String remoteCommand = String.join("\n",
                "# Run these commands on the remote host that will execute the Agent runtime.",
                localCommand);

        AgentSetupStartCommand command = new AgentSetupStartCommand();
        command.setRuntimeType(normalizedRuntimeType);
        command.setGatewayUrl(normalizedGatewayUrl);
        command.setDockerCommand(dockerCommand);
        command.setLocalCommand(localCommand);
        command.setRemoteCommand(remoteCommand);
        command.setCommand(selectStartCommand(normalizedRuntimeType, dockerCommand, localCommand, remoteCommand));
        command.setHealthCheckCommand("curl -fsS " + shell(healthCheckUrl));
        command.setLogsCommand("docker logs -f opendispatch-" + agentId);
        command.setExpectedCapabilities(expectedCapabilities);
        command.setCapabilityEnvironmentVariable("ADMIN_UI_MANAGED_CAPABILITIES");
        command.setVerifyConnectionCommand("curl -fsS -X POST " + shell(authUrl)
                + " -H 'Content-Type: application/json'"
                + " -d '{\"agentId\":\"" + agentId + "\",\"credentialToken\":\"" + (includeSensitiveToken ? runtimeToken : "<issued-token>") + "\"}'");
        command.setStartupSteps(List.of(
                "Verify the Gateway URL is reachable from the runtime host.",
                "Start the Agent with the command that matches the runtime type.",
                "Wait for the first heartbeat and refresh Agent Detail.",
                "Confirm RUNTIME_CONNECTED changed to READY.",
                "Confirm Admin-managed capabilities remain approved in Core. Runtime capability self-reporting is not required for dispatch."));
        command.setTroubleshooting(defaultTroubleshooting(agentId, normalizedGatewayUrl));
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("OPENSOCKET_AGENT_ID", agentId);
        env.put("OPENSOCKET_AGENT_TOKEN", runtimeToken);
        env.put("OPENSOCKET_GATEWAY_URL", normalizedGatewayUrl);
        env.put("AGENT_ID", agentId);
        env.put("AGENT_TOKEN", runtimeToken);
        env.put("GATEWAY_URL", normalizedGatewayUrl);
        command.setEnvironment(env);
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("expectedAgentId", agentId);
        diagnostics.put("gatewayUrl", normalizedGatewayUrl);
        diagnostics.put("healthCheckUrl", healthCheckUrl);
        diagnostics.put("image", image);
        diagnostics.put("adminManagedCapabilities", expectedCapabilities);
        diagnostics.put("capabilitySourceOfTruth", "CORE_ADMIN_UI");
        diagnostics.put("tokenIncluded", includeSensitiveToken);
        diagnostics.put("tokenValue", includeSensitiveToken ? "included-in-initial-setup-response" : "redacted-after-setup");
        command.setDiagnostics(diagnostics);
        return command;
    }

    private String selectStartCommand(String runtimeType, String dockerCommand, String localCommand, String remoteCommand) {
        String normalized = normalizeCode(firstNonBlank(runtimeType, "Docker"));
        if (normalized.contains("LOCAL")) return localCommand;
        if (normalized.contains("REMOTE")) return remoteCommand;
        return dockerCommand;
    }

    private List<AgentSetupTroubleshootingStep> defaultTroubleshooting(String agentId, String gatewayUrl) {
        return List.of(
                AgentSetupTroubleshootingStep.warn("TOKEN_MISMATCH", "Token mismatch", "If authorization is denied with CREDENTIAL_INVALID, copy a fresh credential from Agent Detail and restart only this Agent runtime.", "Issue or rotate credential"),
                AgentSetupTroubleshootingStep.warn("GATEWAY_UNREACHABLE", "Gateway URL unreachable", "If the runtime never sends heartbeat, verify the Gateway URL is reachable from the runtime host, not only from the browser.", "Run gateway health check"),
                AgentSetupTroubleshootingStep.warn("AGENT_ID_MISMATCH", "Agent ID mismatch", "If another runtime connects with a different Agent ID, this Agent will remain Not Ready. Confirm the runtime uses exactly " + agentId + ".", "Check environment variables"),
                AgentSetupTroubleshootingStep.command("GATEWAY_HEALTH_CHECK", "Gateway health check", "Run this from the runtime host before starting the Agent.", "curl -fsS " + shell(gatewayUrl.replaceAll("/+$", "") + "/actuator/health"))
        );
    }

    private List<AgentSetupTroubleshootingStep> troubleshooting(AgentProfile profile,
                                                                AgentSnapshot runtimeSnapshot,
                                                                AgentSetupStartCommand startCommand,
                                                                List<String> blockingReasons) {
        List<AgentSetupTroubleshootingStep> steps = new ArrayList<>();
        if (blockingReasons.contains("CREDENTIAL_ACTIVE")) {
            steps.add(AgentSetupTroubleshootingStep.error("CREDENTIAL_MISSING", "Credential missing or inactive", "Issue or rotate an Agent credential before starting the runtime.", "Issue Credential"));
        }
        if (blockingReasons.contains("RUNTIME_CONNECTED")) {
            steps.add(AgentSetupTroubleshootingStep.warn("RUNTIME_NOT_CONNECTED", "Runtime not connected", "Start the Agent runtime, then refresh this page to re-read Core backend readiness.", "Copy Start Command"));
            steps.addAll(startCommand == null ? List.of() : startCommand.getTroubleshooting());
        }
        // Admin-managed capabilities are not a runtime startup blocker. Runtime capability
        // observations remain diagnostics only and must not produce troubleshooting actions.
        if (runtimeSnapshot != null && runtimeSnapshot.getAgentId() != null && profile != null && !runtimeSnapshot.getAgentId().equals(profile.getAgentId())) {
            steps.add(AgentSetupTroubleshootingStep.error("AGENT_ID_MISMATCH", "Runtime Agent ID mismatch", "The observed runtime Agent ID does not match the Core Agent profile.", "Restart runtime with the correct Agent ID"));
        }
        if (steps.isEmpty()) {
            steps.add(AgentSetupTroubleshootingStep.info("READY_NO_ACTION", "No startup issues detected", "Core backend readiness has no blocking startup checks for this Agent.", "Monitor Recent Tasks"));
        }
        return steps;
    }

    private RuntimeResource findRuntimeResource(String tenantId, AgentRuntimeBinding binding) {
        if (binding == null) return null;
        try {
            if (!blank(binding.getRuntimeId())) {
                return assignmentService.getRuntimeResource(tenantId, binding.getRuntimeId());
            }
        } catch (RuntimeException ignored) {
            // Fall through to runtimeCode lookup.
        }
        try {
            if (!blank(binding.getRuntimeCode())) {
                return assignmentService.searchRuntimeResources(tenantId, null, null, 500).stream()
                        .filter(resource -> binding.getRuntimeCode().equalsIgnoreCase(resource.getRuntimeCode()))
                        .findFirst()
                        .orElse(null);
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private String metadataString(Map<String, Object> metadata, String key) {
        Object value = metadata == null ? null : metadata.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String shell(String value) {
        String safe = firstNonBlank(value, "");
        if (safe.matches("[A-Za-z0-9_./:@%+=,\\-]+")) return safe;
        return "'" + safe.replace("'", "'\\''") + "'";
    }

    private Map<String, Object> metadata(AgentSetupRequest request,
                                         String purpose,
                                         List<String> capabilityCodes,
                                         List<String> taskTypes,
                                         String message) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("contract", "POST /admin/agents/setup");
        metadata.put("message", message);
        metadata.put("purpose", purpose);
        metadata.put("defaultCapabilities", capabilityCodes);
        metadata.put("defaultTaskTypes", taskTypes);
        metadata.put("runtimeType", firstNonBlank(request.getRuntimeType(), "Docker"));
        metadata.put("gatewayUrl", firstNonBlank(request.getGatewayUrl(), "http://localhost:18081"));
        return metadata;
    }


    private AgentProfile findProfile(String agentId) {
        try {
            return governanceService.getProfile(agentId);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private AgentSnapshot findRuntimeSnapshot(String agentId) {
        if (agentDirectoryService == null) return null;
        return agentDirectoryService.findAgent(agentId).orElse(null);
    }

    private boolean runtimeConnected(AgentSnapshot snapshot) {
        if (snapshot == null || snapshot.getStatus() == null) return false;
        AgentStatus status = snapshot.getStatus();
        return status != AgentStatus.OFFLINE
                && status != AgentStatus.EXPIRED
                && status != AgentStatus.ERROR
                && status != AgentStatus.DRAINING;
    }


    private List<String> approvedProfileCapabilities(AgentProfile profile, List<AgentCapabilityAssignment> assignments) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (profile != null && profile.getCapabilities() != null) {
            profile.getCapabilities().stream()
                    .filter(capability -> capability != null && capability.isEnabled())
                    .map(capability -> normalizeCode(capability.getCapabilityCode()))
                    .filter(this::operatorRuntimeCapability)
                    .forEach(values::add);
        }
        if (assignments != null) {
            assignments.stream()
                    .filter(assignment -> assignment != null && assignment.getStatus() == AgentCapabilityAssignmentStatus.APPROVED)
                    .map(AgentCapabilityAssignment::getCapabilityCode)
                    .map(this::normalizeCode)
                    .filter(this::operatorRuntimeCapability)
                    .forEach(values::add);
        }
        return new ArrayList<>(values);
    }

    private boolean operatorRuntimeCapability(String capabilityCode) {
        return !blank(normalizeCode(capabilityCode));
    }

    private List<String> runtimeReportedCapabilities(String agentId, AgentSnapshot runtimeSnapshot) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (agentDirectoryService != null && !blank(agentId)) {
            try {
                List<AgentRuntimeCapabilityItem> items = agentDirectoryService.findRuntimeCapabilityItems(agentId);
                if (items != null) {
                    items.stream()
                            .filter(item -> item != null)
                            .map(AgentRuntimeCapabilityItem::capabilityValue)
                            .map(this::normalizeCode)
                            .filter(this::operatorRuntimeCapability)
                            .forEach(values::add);
                }
            } catch (RuntimeException ignored) {
                // Fall back to snapshot flat capabilities below.
            }
        }
        if (values.isEmpty() && runtimeSnapshot != null && runtimeSnapshot.getCapabilities() != null) {
            runtimeSnapshot.getCapabilities().stream()
                    .map(this::normalizeCode)
                    .filter(this::operatorRuntimeCapability)
                    .forEach(values::add);
        }
        return new ArrayList<>(values);
    }

    private int approvedCapabilityCount(AgentProfile profile, List<AgentCapabilityAssignment> assignments) {
        long governed = assignments.stream()
                .filter(assignment -> assignment.getStatus() == AgentCapabilityAssignmentStatus.APPROVED)
                .map(AgentCapabilityAssignment::getCapabilityCode)
                .map(this::normalizeCode)
                .filter(this::operatorRuntimeCapability)
                .count();
        int profileCapabilities = profile == null ? 0 : profile.getCapabilities().stream()
                .filter(capability -> capability.isEnabled())
                .map(capability -> normalizeCode(capability.getCapabilityCode()))
                .filter(this::operatorRuntimeCapability)
                .toList()
                .size();
        return Math.max((int) governed, profileCapabilities);
    }

    private <T> List<T> safeList(SafeListSupplier<T> supplier) {
        try {
            List<T> values = supplier.get();
            return values == null ? List.of() : values;
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    @FunctionalInterface
    private interface SafeListSupplier<T> {
        List<T> get();
    }


    private void validate(AgentSetupRequest request) {
        if (blank(request.getTenantId())) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (blank(request.getPurpose())) {
            throw new IllegalArgumentException("purpose is required");
        }
        if (request.isCreateDefaultCapabilities()) {
            throw new IllegalArgumentException("Automatic Capability Catalog creation was removed; create and approve capabilities through dispatch governance");
        }
        if (request.isCreateDefaultDispatchRule()) {
            throw new IllegalArgumentException("Automatic Dispatch Rule creation was removed; configure Dispatch Flows through the back office");
        }
        if (blank(request.getAgentId())) {
            throw new IllegalArgumentException("agentId is required");
        }
        if (blank(request.getAgentName())) {
            throw new IllegalArgumentException("agentName is required");
        }
        if (request.isAutoApprove() && blank(request.getCredentialToken())) {
            throw new IllegalArgumentException("credentialToken is required when autoApprove=true");
        }
    }

    private List<String> normalizedList(List<String> values) {
        return values == null ? List.of() : values.stream()
                .filter(value -> !blank(value))
                .map(this::normalizeCode)
                .distinct()
                .toList();
    }

    private String normalizeCode(String value) {
        return blank(value) ? null : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private String normalizeRuntimeCode(String value) {
        return blank(value) ? null : value.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace('.', '-').replace(' ', '-');
    }

    private String normalizeAgentId(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-").replaceAll("^-+|-+$", "");
    }

    private String toTitle(String value) {
        if (blank(value)) return value;
        String normalized = value.replace('_', ' ').replace('-', ' ').toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder();
        for (String part : normalized.split(" ")) {
            if (part.isBlank()) continue;
            if (builder.length() > 0) builder.append(' ');
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private String requireTenant(String tenantId) {
        if (blank(tenantId)) {
            throw new IllegalArgumentException("tenantId is required");
        }
        return tenantId.trim();
    }

    private String normalizeRequiredTenant(String tenantId) {
        return blank(tenantId) ? null : tenantId.trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (!blank(value)) return value;
        }
        return null;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
