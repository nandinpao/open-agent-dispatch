package com.opensocket.aievent.core.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.opensocket.aievent.core.agent.setup.AgentSetupReadinessCheck;
import com.opensocket.aievent.core.agent.setup.AgentSetupReadinessResponse;
import com.opensocket.aievent.core.agent.setup.AgentSetupRequest;
import com.opensocket.aievent.core.agent.setup.AgentSetupResponse;
import com.opensocket.aievent.core.agent.setup.AgentSetupService;
import com.opensocket.aievent.core.agent.setup.AgentSetupStartCommand;
import com.opensocket.aievent.core.agent.setup.AgentSetupTroubleshootingStep;

/**
 * Focused MVC contract tests for the first-Agent setup backend contract.
 *
 * <p>The Admin UI now relies on a single POST /admin/agents/setup endpoint for
 * the empty-database onboarding path. These tests keep request binding, response
 * envelope wrapping, and user-facing failure envelopes stable without starting
 * the full Core application context.</p>
 */
class AgentSetupControllerMockMvcTest {
    private AgentSetupService agentSetupService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        agentSetupService = mock(AgentSetupService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AgentSetupController(agentSetupService))
                .setControllerAdvice(new ApiExceptionHandler(), new StandardApiResponseAdvice())
                .build();
    }

    @Test
    void shouldWrapBackendOwnedReadinessResponseInStandardEnvelope() throws Exception {
        when(agentSetupService.getSetupReadiness("redmine-agent-001"))
                .thenReturn(readinessResponse("redmine-agent-001"));

        mockMvc.perform(get("/admin/agents/redmine-agent-001/setup-readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("OK")))
                .andExpect(jsonPath("$.data.agentId", is("redmine-agent-001")))
                .andExpect(jsonPath("$.data.ready", is(false)))
                .andExpect(jsonPath("$.data.status", is("INCOMPLETE")))
                .andExpect(jsonPath("$.data.blockingReasons[0]", is("RUNTIME_CONNECTED")))
                .andExpect(jsonPath("$.data.checks[0].code", is("AGENT_APPROVED")))
                .andExpect(jsonPath("$.data.startCommand.verifyConnectionCommand", containsString("authorize-connection")))
                .andExpect(jsonPath("$.data.startCommand.capabilityEnvironmentVariable", is("ADMIN_UI_MANAGED_CAPABILITIES")))
                .andExpect(jsonPath("$.data.missingRuntimeCapabilities", hasSize(0)))
                .andExpect(jsonPath("$.data.troubleshooting[0].code", is("RUNTIME_NOT_CONNECTED")));
    }


    @Test
    void shouldExposeReadyTransitionAfterRuntimeHeartbeat() throws Exception {
        AgentSetupReadinessResponse ready = readinessResponse("redmine-agent-001");
        ready.setReady(true);
        ready.setStatus("READY");
        ready.setBlockingReasons(List.of());
        ready.setSummary("Agent is ready to receive tasks.");
        ready.setChecks(List.of(
                AgentSetupReadinessCheck.ready("AGENT_APPROVED", "Agent approved and enabled", "The Agent profile is approved."),
                AgentSetupReadinessCheck.ready("RUNTIME_CONNECTED", "Runtime connected", "The Agent runtime is connected and sending heartbeat data."),
                AgentSetupReadinessCheck.ready("ADMIN_MANAGED_CAPABILITIES_ACTIVE", "Admin-managed capabilities active", "Core-approved capabilities are active.")
        ));
        when(agentSetupService.getSetupReadiness("redmine-agent-001"))
                .thenReturn(ready);

        mockMvc.perform(get("/admin/agents/redmine-agent-001/setup-readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("OK")))
                .andExpect(jsonPath("$.data.ready", is(true)))
                .andExpect(jsonPath("$.data.status", is("READY")))
                .andExpect(jsonPath("$.data.blockingReasons.length()", is(0)))
                .andExpect(jsonPath("$.data.checks[1].code", is("RUNTIME_CONNECTED")))
                .andExpect(jsonPath("$.data.checks[1].ready", is(true)));
    }

    @Test
    void shouldReturnBadRequestEnvelopeForInvalidReadinessAgentId() throws Exception {
        when(agentSetupService.getSetupReadiness("bad-agent"))
                .thenThrow(new IllegalArgumentException("Agent profile not found: bad-agent"));

        mockMvc.perform(get("/admin/agents/bad-agent/setup-readiness"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("BAD_REQUEST")))
                .andExpect(jsonPath("$.message", is("Agent profile not found: bad-agent")));
    }

    @Test
    void shouldWrapFirstAgentSetupResponseInStandardEnvelope() throws Exception {
        when(agentSetupService.setupAgent(any(AgentSetupRequest.class)))
                .thenReturn(setupResponse("redmine-agent-001"));

        mockMvc.perform(post("/admin/agents/setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "tenant-a",
                                  "agentId": "redmine-agent-001",
                                  "agentName": "Redmine Issue Agent",
                                  "purpose": "ISSUE_TRACKING",
                                  "runtimeType": "Docker",
                                  "gatewayUrl": "http://127.0.0.1:18081",
                                  "credentialToken": "local-token",
                                  "autoApprove": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("OK")))
                .andExpect(jsonPath("$.message", is("Success")))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.data.agentId", is("redmine-agent-001")))
                .andExpect(jsonPath("$.data.setupStatus", is("INCOMPLETE")))
                .andExpect(jsonPath("$.data.readinessChecks[0].code", is("AGENT_APPROVED")))
                .andExpect(jsonPath("$.data.startCommand.command", containsString("docker run")))
                .andExpect(jsonPath("$.data.startCommand.dockerCommand", containsString("OPENSOCKET_AGENT_ID")))
                .andExpect(jsonPath("$.data.startCommand.healthCheckCommand", containsString("actuator/health")))
                .andExpect(jsonPath("$.data.startCommand.troubleshooting[0].code", is("TOKEN_MISMATCH")));

        ArgumentCaptor<AgentSetupRequest> requestCaptor = ArgumentCaptor.forClass(AgentSetupRequest.class);
        verify(agentSetupService).setupAgent(requestCaptor.capture());
        AgentSetupRequest request = requestCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(request.getAgentId()).isEqualTo("redmine-agent-001");
        org.assertj.core.api.Assertions.assertThat(request.getCredentialToken()).isEqualTo("local-token");
        org.assertj.core.api.Assertions.assertThat(request.isAutoApprove()).isTrue();
    }

    @Test
    void shouldReturnBadRequestEnvelopeForInvalidSetupRequest() throws Exception {
        when(agentSetupService.setupAgent(any(AgentSetupRequest.class)))
                .thenThrow(new IllegalArgumentException("credentialToken is required when autoApprove=true"));

        mockMvc.perform(post("/admin/agents/setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "missing-token-agent",
                                  "agentName": "Missing Token Agent",
                                  "autoApprove": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("BAD_REQUEST")))
                .andExpect(jsonPath("$.message", is("credentialToken is required when autoApprove=true")))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldReturnBadRequestEnvelopeForEmptyRequestBody() throws Exception {
        when(agentSetupService.setupAgent(any(AgentSetupRequest.class)))
                .thenThrow(new IllegalArgumentException("agentId is required"));

        mockMvc.perform(post("/admin/agents/setup")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("BAD_REQUEST")))
                .andExpect(jsonPath("$.message", is("agentId is required")));
    }

    private AgentSetupReadinessResponse readinessResponse(String agentId) {
        AgentSetupReadinessResponse response = new AgentSetupReadinessResponse();
        response.setTenantId("tenant-a");
        response.setAgentId(agentId);
        response.setReady(false);
        response.setStatus("INCOMPLETE");
        response.setBlockingReasons(List.of("RUNTIME_CONNECTED"));
        response.setProfileCapabilities(List.of("ISSUE_CREATE"));
        response.setRuntimeReportedCapabilities(List.of());
        response.setMissingRuntimeCapabilities(List.of());
        AgentSetupStartCommand command = new AgentSetupStartCommand();
        command.setRuntimeType("Docker");
        command.setGatewayUrl("http://127.0.0.1:18081");
        command.setCommand("docker run --rm -e OPENSOCKET_AGENT_ID=" + agentId + " opendispatch/issue-agent:local");
        command.setVerifyConnectionCommand("curl -fsS -X POST http://127.0.0.1:18080/internal/agents/authorize-connection");
        command.setExpectedCapabilities(List.of("ISSUE_CREATE"));
        command.setCapabilityEnvironmentVariable("ADMIN_UI_MANAGED_CAPABILITIES");
        command.setTroubleshooting(List.of(AgentSetupTroubleshootingStep.warn("TOKEN_MISMATCH", "Token mismatch", "Use the issued token.", "Issue Credential")));
        response.setStartCommand(command);
        response.setTroubleshooting(List.of(AgentSetupTroubleshootingStep.warn("RUNTIME_NOT_CONNECTED", "Runtime not connected", "Start the runtime.", "Copy Start Command")));
        response.setChecks(List.of(
                AgentSetupReadinessCheck.ready("AGENT_APPROVED", "Agent approved and enabled", "The Agent profile is approved."),
                AgentSetupReadinessCheck.pending("RUNTIME_CONNECTED", "Runtime connected", "Start the Agent runtime and wait for heartbeat.", "Start Agent Runtime"),
                AgentSetupReadinessCheck.ready("ADMIN_MANAGED_CAPABILITIES_ACTIVE", "Admin-managed capabilities active", "Core-approved capabilities are active.")
        ));
        return response;
    }

    private AgentSetupResponse setupResponse(String agentId) {
        AgentSetupStartCommand command = new AgentSetupStartCommand();
        command.setRuntimeType("Docker");
        command.setGatewayUrl("http://127.0.0.1:18081");
        command.setCommand("docker run --rm -e AGENT_ID=" + agentId + " opendispatch/issue-agent:local");
        command.setDockerCommand("docker run --rm -e OPENSOCKET_AGENT_ID=" + agentId + " opendispatch/issue-agent:local");
        command.setHealthCheckCommand("curl -fsS http://127.0.0.1:18081/actuator/health");
        command.setTroubleshooting(List.of(AgentSetupTroubleshootingStep.warn("TOKEN_MISMATCH", "Token mismatch", "Use the issued token.", "Issue Credential")));

        AgentSetupResponse response = new AgentSetupResponse();
        response.setTenantId("tenant-a");
        response.setAgentId(agentId);
        response.setSetupStatus("INCOMPLETE");
        response.setStartCommand(command);
        response.setReadinessChecks(List.of(
                AgentSetupReadinessCheck.ready("AGENT_APPROVED", "Agent approved and enabled", "The Core profile can connect to the gateway."),
                AgentSetupReadinessCheck.pending("RUNTIME_CONNECTED", "Runtime connected", "Start the Agent runtime and wait for its first heartbeat.", "Copy Start Command")
        ));
        return response;
    }
}
