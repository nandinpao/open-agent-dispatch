package com.opensocket.aievent.gateway.netty.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.gateway.netty.agent.dto.AgentHeartbeatPayload;
import com.opensocket.aievent.gateway.netty.agent.dto.AgentRegisterPayload;
import com.opensocket.aievent.gateway.netty.config.GatewayProperties;

class AgentRegistryP2ConcurrencyTest {

    @Test
    void concurrentAgentRegistrationsShouldKeepOneRuntimeRecordPerAgent() throws Exception {
        AgentRegistry registry = new AgentRegistry(gatewayProperties());
        int agentCount = 500;
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(agentCount, 64));
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int index = 0; index < agentCount; index++) {
                final int agentIndex = index;
                futures.add(executor.submit(() -> {
                    String agentId = "agent-p2-" + agentIndex;
                    registry.register(
                            registerPayload(agentId),
                            ConnectionType.WEBSOCKET,
                            null,
                            "session-" + agentId,
                            "127.0.0.1");
                    registry.heartbeat(new AgentHeartbeatPayload(agentId, AgentStatus.IDLE, null));
                }));
            }

            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }

            assertThat(registry.count()).isEqualTo(agentCount);
            assertThat(registry.countByStatus(AgentStatus.IDLE)).isEqualTo(agentCount);
            assertThat(registry.list()).extracting(AgentSnapshot::agentId).doesNotHaveDuplicates();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void staleDisconnectFromOldSessionShouldNotOfflineReconnectedAgent() {
        AgentRegistry registry = new AgentRegistry(gatewayProperties());
        String agentId = "agent-reconnect-p2";

        registry.register(registerPayload(agentId), ConnectionType.WEBSOCKET, null, "session-old", "127.0.0.1");
        registry.register(registerPayload(agentId), ConnectionType.WEBSOCKET, null, "session-new", "127.0.0.1");

        assertThat(registry.markOfflineByConnection(ConnectionType.WEBSOCKET, "session-old")).isEmpty();
        AgentSnapshot afterStaleDisconnect = registry.findById(agentId).orElseThrow();
        assertThat(afterStaleDisconnect.status()).isEqualTo(AgentStatus.IDLE);
        assertThat(afterStaleDisconnect.sessionId()).isEqualTo("session-new");

        assertThat(registry.markOfflineByConnection(ConnectionType.WEBSOCKET, "session-new")).isPresent();
        assertThat(registry.findById(agentId).orElseThrow().status()).isEqualTo(AgentStatus.OFFLINE);
    }

    private AgentRegisterPayload registerPayload(String agentId) {
        return new AgentRegisterPayload(
                agentId,
                AgentType.OPENCLAW,
                ConnectionType.WEBSOCKET,
                List.of("ERP_PURCHASE_ORDER_REVIEW"),
                Map.of("protocolFamily", "OPENSOCKET_AGENT_PROTOCOL"));
    }

    private GatewayProperties gatewayProperties() {
        return new GatewayProperties("gateway-node-p2", "p2", "p2-test", "P2 test gateway");
    }
}
