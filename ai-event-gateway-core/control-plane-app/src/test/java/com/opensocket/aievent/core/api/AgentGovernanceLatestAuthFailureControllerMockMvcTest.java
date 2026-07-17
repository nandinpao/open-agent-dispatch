package com.opensocket.aievent.core.api;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.opensocket.aievent.core.agent.AgentDirectoryService;
import com.opensocket.aievent.core.agent.governance.AgentConnectionRepairAction;
import com.opensocket.aievent.core.agent.governance.AgentConnectionRepairActionResult;
import com.opensocket.aievent.core.agent.governance.AgentConnectionRepairActionsResponse;
import com.opensocket.aievent.core.agent.governance.AgentGovernanceService;
import com.opensocket.aievent.core.agent.governance.AgentLatestAuthFailureResponse;
import com.opensocket.aievent.core.agent.setup.AgentSetupTroubleshootingStep;
import com.opensocket.aievent.core.runtime.CoreRuntimeDisconnectClient;

class AgentGovernanceLatestAuthFailureControllerMockMvcTest {
    private AgentGovernanceService agentGovernanceService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        agentGovernanceService = mock(AgentGovernanceService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AgentGovernanceController(
                        agentGovernanceService,
                        mock(AgentDirectoryService.class),
                        mock(CoreRuntimeDisconnectClient.class)))
                .setControllerAdvice(new ApiExceptionHandler(), new StandardApiResponseAdvice())
                .build();
    }

    @Test
    void shouldWrapLatestAuthFailureInStandardEnvelope() throws Exception {
        AgentLatestAuthFailureResponse response = new AgentLatestAuthFailureResponse();
        response.setAgentId("redmine-agent-001");
        response.setHasFailure(true);
        response.setSecurityEventId("asec-001");
        response.setEventType("INVALID_CREDENTIAL");
        response.setDenyReason("CREDENTIAL_INVALID");
        response.setReason("CREDENTIAL_INVALID");
        response.setGatewayNodeId("gateway-node-stage13");
        response.setSecurityEventLink("/security-events?agentId=redmine-agent-001&eventId=asec-001");
        response.setSummary("The latest runtime authorization failed because the credential did not match an active Core credential.");
        response.setTroubleshooting(List.of(AgentSetupTroubleshootingStep.error(
                "CREDENTIAL_INVALID",
                "Credential mismatch",
                "The runtime token does not match an active Core credential.",
                "Rotate and redeploy the Agent credential.")));
        response.setGeneratedAt(OffsetDateTime.now(ZoneOffset.UTC));
        when(agentGovernanceService.latestAuthFailure("redmine-agent-001")).thenReturn(response);

        mockMvc.perform(get("/admin/agents/redmine-agent-001/latest-auth-failure"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("OK")))
                .andExpect(jsonPath("$.data.agentId", is("redmine-agent-001")))
                .andExpect(jsonPath("$.data.hasFailure", is(true)))
                .andExpect(jsonPath("$.data.securityEventId", is("asec-001")))
                .andExpect(jsonPath("$.data.denyReason", is("CREDENTIAL_INVALID")))
                .andExpect(jsonPath("$.data.securityEventLink", is("/security-events?agentId=redmine-agent-001&eventId=asec-001")))
                .andExpect(jsonPath("$.data.troubleshooting[0].code", is("CREDENTIAL_INVALID")));
    }

    @Test
    void shouldWrapConnectionRepairActionsInStandardEnvelope() throws Exception {
        AgentConnectionRepairActionsResponse response = new AgentConnectionRepairActionsResponse();
        response.setAgentId("redmine-agent-001");
        response.setHasFailure(true);
        response.setDenyReason("CREDENTIAL_INVALID");
        response.setActions(List.of(AgentConnectionRepairAction.execute(
                "ROTATE_CREDENTIAL",
                "Rotate credential",
                "Issue a replacement credential.",
                "/admin/agents/redmine-agent-001/connection-repair-actions/ROTATE_CREDENTIAL")));
        when(agentGovernanceService.connectionRepairActions("redmine-agent-001")).thenReturn(response);

        mockMvc.perform(get("/admin/agents/redmine-agent-001/connection-repair-actions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("OK")))
                .andExpect(jsonPath("$.data.actions[0].actionCode", is("ROTATE_CREDENTIAL")));
    }

    @Test
    void shouldExecuteConnectionRepairActionInStandardEnvelope() throws Exception {
        AgentConnectionRepairActionResult result = new AgentConnectionRepairActionResult();
        result.setAgentId("redmine-agent-001");
        result.setActionCode("ROTATE_CREDENTIAL");
        result.setStatus("COMPLETED");
        result.setMessage("Credential rotated");
        when(agentGovernanceService.executeConnectionRepairAction(org.mockito.ArgumentMatchers.eq("redmine-agent-001"),
                org.mockito.ArgumentMatchers.eq("ROTATE_CREDENTIAL"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(result);

        mockMvc.perform(post("/admin/agents/redmine-agent-001/connection-repair-actions/ROTATE_CREDENTIAL")
                        .contentType("application/json")
                        .content("{\"credentialToken\":\"new-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("OK")))
                .andExpect(jsonPath("$.data.status", is("COMPLETED")))
                .andExpect(jsonPath("$.data.actionCode", is("ROTATE_CREDENTIAL")));
    }
}
