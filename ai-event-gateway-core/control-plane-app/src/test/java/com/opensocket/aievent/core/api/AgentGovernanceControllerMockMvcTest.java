package com.opensocket.aievent.core.api;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.opensocket.aievent.core.agent.AgentDirectoryService;
import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.agent.governance.AgentApprovalStatus;
import com.opensocket.aievent.core.agent.governance.AgentGovernanceService;
import com.opensocket.aievent.core.agent.governance.AgentProfile;
import com.opensocket.aievent.core.agent.governance.AgentRiskStatus;
import com.opensocket.aievent.core.runtime.CoreRuntimeDisconnectClient;
import com.opensocket.aievent.core.runtime.RuntimeDisconnectResult;

/**
 * Focused MVC contract tests for Core Agent Governance APIs.
 *
 * <p>These tests intentionally use standalone MockMvc and mocked service ports so
 * that endpoint routing, request binding, API envelope wrapping, and runtime
 * disconnect delegation are covered without starting the full control-plane
 * application context.</p>
 */
class AgentGovernanceControllerMockMvcTest {
    private AgentGovernanceService governanceService;
    private AgentDirectoryService directoryService;
    private CoreRuntimeDisconnectClient runtimeDisconnectClient;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        governanceService = mock(AgentGovernanceService.class);
        directoryService = mock(AgentDirectoryService.class);
        runtimeDisconnectClient = mock(CoreRuntimeDisconnectClient.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AgentGovernanceController(governanceService, directoryService, runtimeDisconnectClient))
                .setControllerAdvice(new ApiExceptionHandler(), new StandardApiResponseAdvice())
                .build();
    }

    @Test
    void shouldWrapAgentSearchInStandardEnvelope() throws Exception {
        when(governanceService.searchProfiles(AgentApprovalStatus.APPROVED, 10))
                .thenReturn(List.of(agentProfile("agent-001", AgentApprovalStatus.APPROVED, true)));

        mockMvc.perform(get("/admin/agents")
                        .param("approvalStatus", "APPROVED")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("OK")))
                .andExpect(jsonPath("$.message", is("Success")))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.data[0].agentId", is("agent-001")))
                .andExpect(jsonPath("$.data[0].approvalStatus", is("APPROVED")))
                .andExpect(jsonPath("$.data[0].enabled", is(true)));
    }

    @Test
    void shouldDelegateRuntimeDisconnectWhenAgentIsDisabled() throws Exception {
        AgentProfile disabled = agentProfile("agent-001", AgentApprovalStatus.APPROVED, false);
        when(governanceService.disableAgent("agent-001", "qa-operator", "security quarantine"))
                .thenReturn(disabled);
        when(directoryService.findById("agent-001"))
                .thenReturn(Optional.of(agentSnapshot("agent-001", "gateway-node-001")));
        when(runtimeDisconnectClient.disconnectAgent("agent-001", "gateway-node-001", "security quarantine", "qa-operator"))
                .thenReturn(disconnected("agent-001", "gateway-node-001"));

        mockMvc.perform(post("/admin/agents/agent-001/disable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operatorId\":\"qa-operator\",\"reason\":\"security quarantine\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("OK")))
                .andExpect(jsonPath("$.data.agentId", is("agent-001")))
                .andExpect(jsonPath("$.data.approvalStatus", is("APPROVED")))
                .andExpect(jsonPath("$.data.enabled", is(false)));

        verify(runtimeDisconnectClient).disconnectAgent(
                eq("agent-001"), eq("gateway-node-001"), eq("security quarantine"), eq("qa-operator"));
    }

    @Test
    void shouldReturnManualRuntimeDisconnectResultWithFallbackOwnerGateway() throws Exception {
        when(directoryService.findById("agent-002"))
                .thenReturn(Optional.of(agentSnapshot("agent-002", "gateway-node-002")));
        when(runtimeDisconnectClient.disconnectAgent("agent-002", "gateway-node-002", "manual operator request", "ops"))
                .thenReturn(disconnected("agent-002", "gateway-node-002"));

        mockMvc.perform(post("/admin/agents/agent-002/disconnect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operatorId\":\"ops\",\"reason\":\"manual operator request\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("OK")))
                .andExpect(jsonPath("$.data.agentId", is("agent-002")))
                .andExpect(jsonPath("$.data.gatewayNodeId", is("gateway-node-002")))
                .andExpect(jsonPath("$.data.status", is("DISCONNECTED")))
                .andExpect(jsonPath("$.data.requested", is(true)))
                .andExpect(jsonPath("$.data.closed", is(true)));

        verify(runtimeDisconnectClient).disconnectAgent(
                eq("agent-002"), eq("gateway-node-002"), eq("manual operator request"), eq("ops"));
    }

    private AgentProfile agentProfile(String agentId, AgentApprovalStatus approvalStatus, boolean enabled) {
        AgentProfile profile = new AgentProfile();
        profile.setAgentId(agentId);
        profile.setTenantId("tenant-test");
        profile.setAgentName(agentId);
        profile.setAgentType("OPENCLAW");
        profile.setApprovalStatus(approvalStatus);
        profile.setEnabled(enabled);
        profile.setRiskStatus(AgentRiskStatus.NORMAL);
        profile.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return profile;
    }

    private AgentSnapshot agentSnapshot(String agentId, String ownerGatewayNodeId) {
        AgentSnapshot snapshot = new AgentSnapshot();
        snapshot.setAgentId(agentId);
        snapshot.setOwnerGatewayNodeId(ownerGatewayNodeId);
        snapshot.setAgentSessionId("session-" + agentId);
        return snapshot;
    }

    private RuntimeDisconnectResult disconnected(String agentId, String gatewayNodeId) {
        return new RuntimeDisconnectResult(
                agentId,
                gatewayNodeId,
                "DISCONNECTED",
                true,
                true,
                200,
                "Runtime session closed",
                Map.of("endpoint", "http://netty.example.test/api/admin/agents/" + agentId + "/disconnect"),
                OffsetDateTime.now(ZoneOffset.UTC));
    }
}
