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
        "netty.tcp.port=0",
        "netty.websocket.enabled=false",
        "netty.websocket.host=0.0.0.0",
        "netty.websocket.port=19091",
        "netty.cluster.enabled=false",
        "netty.cluster.udp-host=0.0.0.0",
        "netty.cluster.udp-port=19100",
        "agent.heartbeat-timeout-seconds=30",
        "agent.timeout-scan-interval-ms=5000",
        "admin.recent-event-limit=200",
        "admin.cors-allowed-origins=http://localhost:3000"
})
@AutoConfigureMockMvc
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnAdminDashboard() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("OK")))
                .andExpect(jsonPath("$.message", is("Success")))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.data.gateway.nodeId", is("gateway-node-test")))
                .andExpect(jsonPath("$.data.agentSummary.total", is(0)));
    }

    @Test
    void shouldReturnAdminMetricsSnapshot() throws Exception {
        mockMvc.perform(get("/api/admin/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("OK")))
                .andExpect(jsonPath("$.message", is("Success")))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.data.nodeId", is("gateway-node-test")))
                .andExpect(jsonPath("$.data.transportQueueSize", is(0)))
                .andExpect(jsonPath("$.data.activeDeliveries", is(0)));
    }

    @Test
    void shouldReturnAdminWebSocketStatus() throws Exception {
        mockMvc.perform(get("/api/admin/websocket/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("OK")))
                .andExpect(jsonPath("$.message", is("Success")))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.data.activeAdminChannels", is(0)))
                .andExpect(jsonPath("$.data.retainedEventLimit", is(200)));
    }

    @Test
    void shouldReturnRecentEvents() throws Exception {
        mockMvc.perform(get("/api/admin/events?limit=10"))
                .andExpect(status().isOk());
    }
}
