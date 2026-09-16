package com.opensocket.aievent.gateway.netty.agent;

import com.opensocket.aievent.gateway.netty.admin.AdminEventPublisher;
import com.opensocket.aievent.gateway.netty.agent.dto.AgentHeartbeatPayload;
import com.opensocket.aievent.gateway.netty.agent.dto.AgentRegisterPayload;
import com.opensocket.aievent.gateway.netty.authorization.AgentAuthorizationDecision;
import com.opensocket.aievent.gateway.netty.authorization.AgentAuthorizationRuntimeRegistry;
import com.opensocket.aievent.gateway.netty.authorization.AgentConnectionAuthorizationResponse;
import com.opensocket.aievent.gateway.netty.authorization.AgentSecurityEventPublisher;
import com.opensocket.aievent.gateway.netty.authorization.CoreAgentAuthorizationProperties;
import com.opensocket.aievent.gateway.netty.config.AgentProperties;
import com.opensocket.aievent.gateway.netty.config.GatewayProperties;
import com.opensocket.aievent.gateway.netty.directory.CoreDirectorySyncPublisher;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AgentLifecycleGovernanceReconciliationTest {

    @Test
    void pendingAgentHeartbeatPromotesOnlyAfterGovernedCoreAllow() {
        var gateway = new GatewayProperties("gateway-node-test", "test", "test", "test");
        var registry = new AgentRegistry(gateway);
        var authorizationRegistry = new AgentAuthorizationRuntimeRegistry(new CoreAgentAuthorizationProperties());
        var authorizationCalls = new AtomicInteger();
        var sync = new RecordingDirectorySync();
        var events = new ArrayList<String>();
        AdminEventPublisher adminEvents = (eventType, message, data) -> events.add(eventType);

        var service = new AgentLifecycleService(
                registry,
                new AgentProperties(),
                adminEvents,
                sync,
                request -> {
                    int call = authorizationCalls.incrementAndGet();
                    if (call == 1) {
                        return AgentConnectionAuthorizationResponse.deny(request.agentId(), "AGENT_NOT_APPROVED");
                    }
                    return new AgentConnectionAuthorizationResponse(
                            AgentAuthorizationDecision.ALLOW, "ALLOW", request.agentId(), "tenant-a", "APPROVED", true,
                            "NORMAL", List.of("MES_WORK_ORDER_TRACE"), List.of(), List.of(), 1, 2);
                },
                authorizationRegistry,
                AgentSecurityEventPublisher.noop()
        );

        var registered = service.registerAgent(
                new AgentRegisterPayload(
                        "agent-001", AgentType.CUSTOM, ConnectionType.TCP, List.of("MES_WORK_ORDER_TRACE"),
                        Map.of("credentialToken", "agent-credential")),
                ConnectionType.TCP, "tcp-001", null, "127.0.0.1"
        );

        assertThat(registered.agentId()).isEqualTo("agent-001");
        assertThat(authorizationRegistry.findByAgentId("agent-001")).isEmpty();
        assertThat(sync.connected).isZero();

        service.heartbeat(new AgentHeartbeatPayload("agent-001", AgentStatus.IDLE, null));

        assertThat(authorizationCalls).hasValue(2);
        var authorized = authorizationRegistry.findByAgentId("agent-001").orElseThrow();
        assertThat(authorized.tenantId()).isEqualTo("tenant-a");
        assertThat(sync.connected).isEqualTo(1);
        assertThat(sync.heartbeats).isEqualTo(1);
        assertThat(events).contains("AGENT_AUTHORIZATION_RECONCILED");
    }

    @Test
    void pendingAgentHeartbeatNeverCreatesDirectoryWhenCoreStillDenies() {
        var gateway = new GatewayProperties("gateway-node-test", "test", "test", "test");
        var registry = new AgentRegistry(gateway);
        var authorizationRegistry = new AgentAuthorizationRuntimeRegistry(new CoreAgentAuthorizationProperties());
        var sync = new RecordingDirectorySync();
        var service = new AgentLifecycleService(
                registry,
                new AgentProperties(),
                AdminEventPublisher.noop(),
                sync,
                request -> AgentConnectionAuthorizationResponse.deny(request.agentId(), "AGENT_NOT_APPROVED"),
                authorizationRegistry,
                AgentSecurityEventPublisher.noop()
        );

        service.registerAgent(
                new AgentRegisterPayload("agent-001", AgentType.CUSTOM, ConnectionType.TCP, List.of(), Map.of()),
                ConnectionType.TCP, "tcp-001", null, "127.0.0.1");
        service.heartbeat(new AgentHeartbeatPayload("agent-001", AgentStatus.IDLE, null));

        assertThat(authorizationRegistry.findByAgentId("agent-001")).isEmpty();
        assertThat(sync.connected).isZero();
        assertThat(sync.heartbeats).isZero();
    }

    private static final class RecordingDirectorySync implements CoreDirectorySyncPublisher {
        private int connected;
        private int heartbeats;

        @Override public void publishGatewayRegistration() { }
        @Override public void publishGatewayHeartbeat() { }
        @Override public void publishAgentConnected(AgentSnapshot agent) { connected++; }
        @Override public void publishAgentHeartbeat(AgentSnapshot agent) { heartbeats++; }
        @Override public void publishAgentDisconnected(AgentSnapshot agent, String reason) { }
        @Override public void publishGatewaySnapshot(List<AgentSnapshot> agents) { }
    }
}
