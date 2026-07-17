package com.opensocket.aievent.gateway.netty.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "gateway.node-id=gateway-node-test",
        "gateway.environment=test",
        "gateway.version=1.2.5-p9-netty-server-test-port-fix-test",
        "netty.transport.auto-start-enabled=false",
        "netty.tcp.enabled=false",
        "netty.tcp.host=0.0.0.0",
        "netty.tcp.port=19090",
        "netty.websocket.enabled=true",
        "netty.websocket.host=0.0.0.0",
        "netty.websocket.port=19091",
        "netty.cluster.enabled=false",
        "netty.cluster.udp-host=0.0.0.0",
        "netty.cluster.udp-port=19100",
        "agent.heartbeat-timeout-seconds=30",
        "agent.timeout-scan-interval-ms=5000"
})
@AutoConfigureMockMvc
class GatewayStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnGatewayStatus() throws Exception {
        mockMvc.perform(get("/api/gateway/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("OK")))
                .andExpect(jsonPath("$.message", is("Success")))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.data.nodeId", is("gateway-node-test")))
                .andExpect(jsonPath("$.data.status", is("UP")))
                .andExpect(jsonPath("$.data.tcpPort", is(19090)))
                .andExpect(jsonPath("$.data.tcpActiveConnections", is(0)))
                .andExpect(jsonPath("$.data.websocketEnabled", is(true)))
                .andExpect(jsonPath("$.data.websocketPort", is(19091)))
                .andExpect(jsonPath("$.data.websocketActiveSessions", is(0)))
                .andExpect(jsonPath("$.data.agentTotal", is(0)))
                .andExpect(jsonPath("$.data.agentHeartbeatTimeoutSeconds", is(30)))
                .andExpect(jsonPath("$.data.clusterEnabled", is(false)));
    }
}
