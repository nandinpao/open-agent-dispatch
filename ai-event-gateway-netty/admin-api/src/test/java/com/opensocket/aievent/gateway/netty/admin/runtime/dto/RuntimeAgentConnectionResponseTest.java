package com.opensocket.aievent.gateway.netty.admin.runtime.dto;

import com.opensocket.aievent.gateway.netty.agent.AgentStatus;
import com.opensocket.aievent.gateway.netty.agent.AgentType;
import com.opensocket.aievent.gateway.netty.agent.ConnectionType;
import com.opensocket.aievent.gateway.netty.agent.dto.AgentResponse;
import com.opensocket.aievent.gateway.netty.authorization.AgentAuthorizationContext;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeAgentConnectionResponseTest {

    @Test
    void shouldExposeFreshnessAndCoreAuthorizationContext() {
        var now = OffsetDateTime.now();
        var agent = new AgentResponse(
                "agent-001",
                AgentType.OPENCLAW,
                ConnectionType.WEBSOCKET,
                "gateway-node-001",
                AgentStatus.IDLE,
                List.of("erp.sync"),
                null,
                now.minusSeconds(20),
                now.minusSeconds(10),
                now.minusSeconds(10),
                "127.0.0.1",
                null,
                "ws-001",
                Map.of("pluginName", "openclaw")
        );
        var authorization = new AgentAuthorizationContext(
                "agent-001",
                "tenant-001",
                "APPROVED",
                true,
                "NORMAL",
                List.of("erp.sync"),
                List.of("ERP_SYNC"),
                List.of("ERP"),
                3,
                7,
                ConnectionType.WEBSOCKET,
                null,
                "ws-001",
                "127.0.0.1",
                now.minusSeconds(20)
        );

        var response = RuntimeAgentConnectionResponse.from(agent, 30, authorization);

        assertThat(response.transportStatus()).isEqualTo("CONNECTED");
        assertThat(response.freshnessStatus()).isEqualTo("FRESH");
        assertThat(response.heartbeatStale()).isFalse();
        assertThat(response.heartbeatTimeoutMs()).isEqualTo(30_000L);
        assertThat(response.heartbeatAgeMs()).isBetween(0L, 30_000L);
        assertThat(response.coreAuthorized()).isTrue();
        assertThat(response.coreApprovalStatus()).isEqualTo("APPROVED");
        assertThat(response.coreRiskStatus()).isEqualTo("NORMAL");
        assertThat(response.credentialVersion()).isEqualTo(3);
        assertThat(response.policyVersion()).isEqualTo(7);
    }

    @Test
    void shouldClassifyStaleHeartbeatSeparatelyFromTransportDisconnection() {
        var now = OffsetDateTime.now();
        var agent = new AgentResponse(
                "agent-002",
                AgentType.CUSTOM,
                ConnectionType.TCP,
                "gateway-node-001",
                AgentStatus.IDLE,
                List.of(),
                null,
                now.minusMinutes(3),
                now.minusMinutes(2),
                now.minusMinutes(2),
                "127.0.0.1",
                "tcp-001",
                null,
                Map.of()
        );

        var response = RuntimeAgentConnectionResponse.from(agent, 30);

        assertThat(response.transportStatus()).isEqualTo("CONNECTED");
        assertThat(response.freshnessStatus()).isEqualTo("STALE");
        assertThat(response.heartbeatStale()).isTrue();
    }

    @Test
    void summaryShouldGroupFreshnessStatus() {
        var now = OffsetDateTime.now();
        var fresh = RuntimeAgentConnectionResponse.from(new AgentResponse(
                "agent-003",
                AgentType.CUSTOM,
                ConnectionType.TCP,
                "gateway-node-001",
                AgentStatus.IDLE,
                List.of(),
                null,
                now,
                now,
                now,
                "127.0.0.1",
                "tcp-003",
                null,
                Map.of()
        ), 30);
        var stale = RuntimeAgentConnectionResponse.from(new AgentResponse(
                "agent-004",
                AgentType.CUSTOM,
                ConnectionType.TCP,
                "gateway-node-001",
                AgentStatus.IDLE,
                List.of(),
                null,
                now.minusMinutes(3),
                now.minusMinutes(3),
                now.minusMinutes(3),
                "127.0.0.1",
                "tcp-004",
                null,
                Map.of()
        ), 30);

        var summary = RuntimeAgentConnectionsResponse.from(List.of(fresh, stale));

        assertThat(summary.byFreshnessStatus()).containsEntry("FRESH", 1L);
        assertThat(summary.byFreshnessStatus()).containsEntry("STALE", 1L);
        assertThat(summary.staleAgents()).isEqualTo(1L);
    }
}
