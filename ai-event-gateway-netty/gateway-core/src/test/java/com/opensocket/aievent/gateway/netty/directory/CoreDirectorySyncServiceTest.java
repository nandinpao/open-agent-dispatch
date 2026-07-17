package com.opensocket.aievent.gateway.netty.directory;

import com.opensocket.aievent.gateway.netty.agent.AgentRegistry;
import com.opensocket.aievent.gateway.netty.agent.AgentSnapshot;
import com.opensocket.aievent.gateway.netty.agent.AgentStatus;
import com.opensocket.aievent.gateway.netty.agent.AgentType;
import com.opensocket.aievent.gateway.netty.agent.ConnectionType;
import com.opensocket.aievent.gateway.netty.agent.dto.AgentRegisterPayload;
import com.opensocket.aievent.gateway.netty.config.CoreDirectorySyncProperties;
import com.opensocket.aievent.gateway.netty.config.CoreOutboundProperties;
import com.opensocket.aievent.gateway.netty.config.GatewayProperties;
import com.opensocket.aievent.gateway.netty.config.NettyServerProperties;
import com.opensocket.aievent.gateway.netty.outbound.CoreOutboundDispatcher;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class CoreDirectorySyncServiceTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldPublishGatewayRegistrationHeartbeatAndAgentLifecycleToCoreDirectoryApi() throws Exception {
        var received = new CopyOnWriteArrayList<ReceivedRequest>();
        var latch = new CountDownLatch(4);
        startServer(received, latch);

        var properties = new CoreDirectorySyncProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setAuthToken("cluster-token");
        properties.setTimeoutMs(2000);

        var gatewayProperties = new GatewayProperties(
                "gateway-node-test",
                "test",
                "1.2.5-test",
                "test gateway",
                "TPE",
                "Taipei Site",
                "tw-north",
                "tpe-a"
        );
        var registry = new AgentRegistry(gatewayProperties);
        AgentSnapshot agent = registry.register(
                new AgentRegisterPayload(
                        "openclaw-agent-001",
                        AgentType.OPENCLAW,
                        ConnectionType.TCP,
                        List.of("log-analysis"),
                        Map.of(
                                "supportedCapabilities", List.of("MES_ALARM_TRIAGE"),
                                "runtimeCapabilities", List.of("MES_ALARM_TRIAGE"),
                                "supportedTaskTypes", List.of("MES_ALARM_TRIAGE")
                        ),
                        Map.of("maxConcurrentTasks", "3"),
                        null
                ),
                ConnectionType.TCP,
                "tcp-connection-001",
                null,
                "127.0.0.1"
        );

        var service = new CoreDirectorySyncService(
                properties,
                gatewayProperties,
                new NettyServerProperties(null, null, null),
                registry,
                JsonMapper.builder().build(),
                new MockEnvironment().withProperty("server.port", "18080"),
                new CoreOutboundDispatcher(new CoreOutboundProperties())
        );

        service.publishGatewayRegistration();
        service.publishGatewayHeartbeat();
        service.publishAgentConnected(agent);
        service.publishGatewaySnapshot(List.of(agent));

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(received).extracting(ReceivedRequest::path).contains(
                "/internal/gateway-nodes/register",
                "/internal/gateway-nodes/gateway-node-test/heartbeat",
                "/internal/gateway-nodes/gateway-node-test/agents/openclaw-agent-001/connected",
                "/internal/gateway-nodes/gateway-node-test/agents/snapshot"
        );
        assertThat(received).allSatisfy(request -> assertThat(request.clusterToken()).isEqualTo("cluster-token"));

        String connectedBody = received.stream()
                .filter(request -> request.path().endsWith("/connected"))
                .findFirst()
                .orElseThrow()
                .body();
        assertThat(connectedBody).contains("\"ownerGatewayNodeId\":\"gateway-node-test\"");
        assertThat(connectedBody).contains("\"agentSessionId\":\"tcp-connection-001\"");
        assertThat(connectedBody).contains("\"maxConcurrentTasks\":3");
        assertThat(connectedBody).contains("\"status\":\"IDLE\"");
        assertThat(connectedBody).contains("\"capabilityProfile\"");
        assertThat(connectedBody).contains("MES_ALARM_TRIAGE");
    }

    @Test
    void shouldMapBusyAgentHeartbeatToCoreBusyStatus() throws Exception {
        var received = new CopyOnWriteArrayList<ReceivedRequest>();
        var latch = new CountDownLatch(1);
        startServer(received, latch);

        var properties = new CoreDirectorySyncProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());

        var gatewayProperties = new GatewayProperties("gateway-node-test", "test", "1.2.5-test", "test gateway");
        var registry = new AgentRegistry(gatewayProperties);
        var agent = registry.register(
                new AgentRegisterPayload("agent-001", AgentType.CUSTOM, ConnectionType.WEBSOCKET, List.of(), Map.of()),
                ConnectionType.WEBSOCKET,
                null,
                "ws-session-001",
                "127.0.0.1"
        );
        agent = new AgentSnapshot(
                agent.agentId(), agent.agentType(), agent.connectionType(), agent.gatewayNodeId(), AgentStatus.BUSY,
                agent.capabilities(), "task-001", agent.registeredAt(), agent.lastHeartbeatAt(), agent.statusUpdatedAt(),
                agent.remoteAddress(), agent.connectionId(), agent.sessionId(), agent.metadata()
        );

        var service = new CoreDirectorySyncService(
                properties,
                gatewayProperties,
                new NettyServerProperties(null, null, null),
                registry,
                JsonMapper.builder().build(),
                new MockEnvironment(),
                new CoreOutboundDispatcher(new CoreOutboundProperties())
        );

        service.publishAgentHeartbeat(agent);

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(received.getFirst().path()).endsWith("/agents/agent-001/heartbeat");
        assertThat(received.getFirst().body()).contains("\"status\":\"BUSY\"");
        assertThat(received.getFirst().body()).contains("\"currentTaskCount\":1");
        assertThat(received.getFirst().body()).contains("\"agentSessionId\":\"ws-session-001\"");
    }

    private void startServer(List<ReceivedRequest> received, CountDownLatch latch) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            received.add(new ReceivedRequest(
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("X-Cluster-Token"),
                    body
            ));
            exchange.sendResponseHeaders(200, 2);
            exchange.getResponseBody().write("{}".getBytes(StandardCharsets.UTF_8));
            exchange.close();
            latch.countDown();
        });
        server.start();
    }

    private record ReceivedRequest(String path, String clusterToken, String body) {}
}
