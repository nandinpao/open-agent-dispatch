package com.opensocket.aievent.gateway.netty.agent;

import com.opensocket.aievent.gateway.netty.agent.dto.AgentHeartbeatPayload;
import com.opensocket.aievent.gateway.netty.agent.dto.AgentRegisterPayload;
import com.opensocket.aievent.gateway.netty.config.GatewayProperties;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRegistryTest {

    private final AgentRegistry registry = new AgentRegistry(
            new GatewayProperties("gateway-node-test", "test", "1.2.5-p9-netty-server-test-port-fix-test", "test gateway")
    );

    @Test
    void shouldRegisterAndUpdateHeartbeat() {
        registry.register(
                new AgentRegisterPayload(
                        "openclaw-agent-001",
                        AgentType.OPENCLAW,
                        ConnectionType.WEBSOCKET,
                        List.of("log-analysis"),
                        Map.of("host", "local")
                ),
                ConnectionType.WEBSOCKET,
                null,
                "session-test-001",
                "127.0.0.1"
        );

        var heartbeat = registry.heartbeat(new AgentHeartbeatPayload(
                "openclaw-agent-001",
                AgentStatus.BUSY,
                "task-001",
                OffsetDateTime.now()
        ));

        assertThat(heartbeat).isPresent();
        assertThat(heartbeat.orElseThrow().status()).isEqualTo(AgentStatus.BUSY);
        assertThat(heartbeat.orElseThrow().currentTaskId()).isEqualTo("task-001");
    }

    @Test
    void shouldPreserveRegisterCapabilityProfileAsRuntimeMetadata() {
        var snapshot = registry.register(
                new AgentRegisterPayload(
                        "agent-mes-001",
                        AgentType.OPENCLAW,
                        ConnectionType.TCP,
                        List.of("MES_ALARM_TRIAGE"),
                        Map.of(
                                "supportedCapabilities", List.of("MES_ALARM_TRIAGE", "MES_LOT_TRACE"),
                                "runtimeCapabilities", List.of("MES_ALARM_TRIAGE", "MES_LOT_TRACE"),
                                "supportedTaskTypes", List.of("MES_ALARM_TRIAGE")
                        ),
                        Map.of("host", "local"),
                        "token"
                ),
                ConnectionType.TCP,
                "tcp-connection-001",
                null,
                "127.0.0.1"
        );

        assertThat(snapshot.capabilities()).containsExactly("MES_ALARM_TRIAGE");
        assertThat(snapshot.metadata()).containsKey("capabilityProfile");
        assertThat(snapshot.metadata().get("capabilityProfile").toString()).contains("MES_LOT_TRACE");
    }

}
