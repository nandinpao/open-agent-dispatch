package com.opensocket.aievent.gateway.netty.admin.runtime;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Runtime observability controller smoke contracts.
 *
 * <p>The Netty admin-api module already has a lightweight test Spring Boot
 * application, so this test uses the same SpringBootTest + MockMvc pattern as
 * the existing AdminController/GatewayStatusController tests and verifies that
 * high-risk runtime endpoints stay wrapped in the standard gateway envelope.</p>
 */
@SpringBootTest(properties = {
        "gateway.node-id=gateway-node-test",
        "gateway.environment=test",
        "gateway.version=1.2.5-p24-runtime-controller-test",
        "netty.transport.auto-start-enabled=false",
        "netty.tcp.enabled=false",
        "netty.tcp.host=0.0.0.0",
        "netty.tcp.port=19090",
        "netty.websocket.enabled=false",
        "netty.websocket.host=0.0.0.0",
        "netty.websocket.port=19091",
        "netty.cluster.enabled=false",
        "netty.cluster.udp-host=0.0.0.0",
        "netty.cluster.udp-port=19100",
        "agent.heartbeat-timeout-seconds=30",
        "agent.timeout-scan-interval-ms=5000",
        "admin.recent-event-limit=200"
})
@AutoConfigureMockMvc
class AdminRuntimeControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnRuntimeSnapshotEnvelope() throws Exception {
        mockMvc.perform(get("/api/admin/runtime/snapshot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("OK")))
                .andExpect(jsonPath("$.message", is("Success")))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.data.gateway.nodeId", is("gateway-node-test")))
                .andExpect(jsonPath("$.data.connections.tcpActiveConnections", is(0)))
                .andExpect(jsonPath("$.data.connections.websocketActiveSessions", is(0)))
                .andExpect(jsonPath("$.data.connections.totalTransportConnections", is(0)))
                .andExpect(jsonPath("$.data.agents.totalAgents", is(0)))
                .andExpect(jsonPath("$.data.generatedAt").exists());
    }

    @Test
    void shouldReturnRuntimeConnectionsEnvelope() throws Exception {
        mockMvc.perform(get("/api/admin/runtime/connections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("OK")))
                .andExpect(jsonPath("$.data.tcpActiveConnections", is(0)))
                .andExpect(jsonPath("$.data.tcpRegisteredAgentConnections", is(0)))
                .andExpect(jsonPath("$.data.websocketActiveSessions", is(0)))
                .andExpect(jsonPath("$.data.totalTransportConnections", is(0)))
                .andExpect(jsonPath("$.data.generatedAt").exists());
    }

    @Test
    void shouldReturnLocalRuntimeAgentsEnvelope() throws Exception {
        mockMvc.perform(get("/api/admin/runtime/agents").param("scope", "local"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("OK")))
                .andExpect(jsonPath("$.data.totalAgents", is(0)))
                .andExpect(jsonPath("$.data.connectedAgents", is(0)))
                .andExpect(jsonPath("$.data.agents.length()", is(0)))
                .andExpect(jsonPath("$.data.generatedAt").exists());
    }

    @Test
    void shouldReturnRejectedConnectionsEnvelope() throws Exception {
        mockMvc.perform(get("/api/admin/runtime/rejected-connections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("OK")))
                .andExpect(jsonPath("$.data.length()", is(0)))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
