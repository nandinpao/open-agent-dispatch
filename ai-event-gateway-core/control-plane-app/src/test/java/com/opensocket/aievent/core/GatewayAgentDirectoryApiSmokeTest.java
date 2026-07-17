package com.opensocket.aievent.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.gateway.GatewayNode;

@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.flyway.enabled=false",
        "gateway-nodes.store=MEMORY",
        "agent-directory.store=MEMORY",
        "agent-governance.store=MEMORY"
})
class GatewayAgentDirectoryApiSmokeTest {
    @Autowired
    private TestRestTemplate rest;

    @Test
    void gatewayShouldRegisterAgentSessionAndExposeOwnerGateway() {
        GatewayNode node = new GatewayNode();
        node.setGatewayNodeId("gateway-node-001");
        node.setNodeName("Gateway 001");
        node.setAdvertiseHost("127.0.0.1");
        node.setHttpPort(18081);
        node.setWsPort(18082);
        node.setSiteId("TNN");

        ResponseEntity<Map> registered = rest.postForEntity("/internal/gateway-nodes/register", node, Map.class);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> registeredData = data(registered);
        assertThat(registered.getBody()).containsEntry("code", "OK");
        assertThat(registeredData).containsEntry("gatewayNodeId", "gateway-node-001");

        AgentSnapshot agent = new AgentSnapshot();
        agent.setAgentId("openclaw-agent-001");
        agent.setAgentType("OPENCLAW");
        agent.setAgentSessionId("ws-session-001");
        agent.setSiteId("TNN");
        agent.setCapabilities(List.of("incident-analysis", "issue-tracking"));
        agent.setMaxConcurrentTasks(2);

        ResponseEntity<Map> connected = rest.postForEntity(
                "/internal/gateway-nodes/gateway-node-001/agents/openclaw-agent-001/connected",
                agent,
                Map.class);
        assertThat(connected.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> connectedData = data(connected);
        assertThat(connectedData).containsEntry("ownerGatewayNodeId", "gateway-node-001");
        assertThat(connectedData).containsEntry("agentSessionId", "ws-session-001");

        ResponseEntity<Map> agents = rest.getForEntity("/api/gateway-nodes/gateway-node-001/agents", Map.class);
        assertThat(agents.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> agentData = listData(agents);
        assertThat(agentData).hasSize(1);
        assertThat(agentData.get(0)).containsEntry("agentId", "openclaw-agent-001");
        assertThat(agentData.get(0)).containsEntry("agentSessionId", "ws-session-001");

        ResponseEntity<Map> status = rest.getForEntity("/api/core/status", Map.class);
        assertThat(data(status)).containsEntry("gatewayNodeStore", "MEMORY");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResponseEntity<Map> response) {
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKeys("code", "message", "data", "timestamp");
        return (Map<String, Object>) response.getBody().get("data");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listData(ResponseEntity<Map> response) {
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKeys("code", "message", "data", "timestamp");
        return (List<Map<String, Object>>) response.getBody().get("data");
    }
}
