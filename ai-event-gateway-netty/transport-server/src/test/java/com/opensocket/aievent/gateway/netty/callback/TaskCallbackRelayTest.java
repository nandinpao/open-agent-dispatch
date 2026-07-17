package com.opensocket.aievent.gateway.netty.callback;

import com.opensocket.aievent.gateway.netty.agent.ConnectionType;
import com.opensocket.aievent.gateway.netty.config.CoreTaskCallbackRelayProperties;
import com.opensocket.aievent.gateway.netty.config.CoreOutboundProperties;
import com.opensocket.aievent.gateway.netty.config.GatewayProperties;
import com.opensocket.aievent.gateway.netty.outbound.CoreOutboundDispatcher;
import com.opensocket.aievent.gateway.netty.protocol.AiEventEnvelope;
import com.opensocket.aievent.gateway.netty.protocol.MessageType;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TaskCallbackRelayTest {
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void shouldRelayTaskAckToCoreCallbackEndpointWithGatewayAndSessionIdentity() throws Exception {
        ArrayBlockingQueue<CapturedRequest> captured = new ArrayBlockingQueue<>(1);
        HttpServer server = startServer(captured, "{\"accepted\":true}");
        try {
            var properties = new CoreTaskCallbackRelayProperties();
            properties.setEnabled(true);
            properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            properties.setAuthToken("local-cluster-token");
            properties.setTimeoutMs(3000);

            var relay = new TaskCallbackRelay(
                    objectMapper,
                    new GatewayProperties("gateway-node-test", "test", "1.2.5-test", "test gateway"),
                    properties,
                    new CoreOutboundDispatcher(new CoreOutboundProperties()),
                    new TaskCallbackRelayMetrics()
            );

            var payload = objectMapper.readTree("""
                    {
                      "taskId":"task-001",
                      "agentId":"agent-001",
                      "dispatchRequestId":"dispatch-001",
                      "assignmentId":"assignment-001",
                      "attemptNo":2,
                      "dispatchToken":"dispatch-token-001",
                      "fencingToken":"fence-001",
                      "accepted":true,
                      "message":"accepted"
                    }
                    """);
            var envelope = new AiEventEnvelope<>(
                    "msg-callback-001",
                    MessageType.TASK_ACK,
                    null,
                    "agent-001",
                    "gateway-node-test",
                    null,
                    payload
            );

            TaskCallbackRelayResult result = relay.accept(envelope, ConnectionType.TCP, "connection-001", "agent-001");

            assertThat(result.submitted()).isTrue();
            CapturedRequest request = captured.poll(3, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.path()).isEqualTo("/internal/control-plane/tasks/task-001/ack");
            assertThat(request.header("X-Cluster-Token")).isEqualTo("local-cluster-token");
            Map<String, Object> body = objectMapper.readValue(request.body(), new TypeReference<Map<String, Object>>() {});
            assertThat(body).containsEntry("taskId", "task-001");
            assertThat(body).containsEntry("dispatchRequestId", "dispatch-001");
            assertThat(body).containsEntry("assignmentId", "assignment-001");
            assertThat(body).containsEntry("agentId", "agent-001");
            assertThat(body).containsEntry("ownerGatewayNodeId", "gateway-node-test");
            assertThat(body).containsEntry("agentSessionId", "connection-001");
            assertThat(body).containsEntry("dispatchToken", "dispatch-token-001");
            assertThat(body).containsEntry("fencingToken", "fence-001");
            assertThat(body).containsEntry("attemptNo", 2);
        } finally {
            server.stop(0);
        }
    }


    @Test
    void shouldRejectCallbackWhenPayloadAgentDoesNotMatchRegisteredSessionAgent() throws Exception {
        var properties = new CoreTaskCallbackRelayProperties();
        properties.setEnabled(true);
        properties.setRequireDispatchContext(true);
        properties.setRequireAssignmentId(true);
        var relay = new TaskCallbackRelay(
                objectMapper,
                new GatewayProperties("gateway-node-test", "test", "1.2.5-test", "test gateway"),
                properties,
                    new CoreOutboundDispatcher(new CoreOutboundProperties()),
                    new TaskCallbackRelayMetrics()
        );
        var envelope = new AiEventEnvelope<>(
                "msg-callback-agent-mismatch",
                MessageType.TASK_ACK,
                null,
                "agent-002",
                "gateway-node-test",
                null,
                objectMapper.readTree("""
                        {
                          "taskId":"task-001",
                          "agentId":"agent-002",
                          "dispatchRequestId":"dispatch-001",
                          "assignmentId":"assignment-001",
                          "attemptNo":1,
                          "dispatchToken":"dispatch-token-001"
                        }
                        """)
        );

        TaskCallbackRelayResult result = relay.accept(envelope, ConnectionType.TCP, "connection-001", "agent-001");

        assertThat(result.submitted()).isFalse();
        assertThat(result.message()).contains("agentId must match");
    }

    @Test
    void shouldRejectCallbackWithNonPositiveAttemptWhenDispatchContextIsRequired() throws Exception {
        var properties = new CoreTaskCallbackRelayProperties();
        properties.setEnabled(true);
        properties.setRequireDispatchContext(true);
        properties.setRequireAssignmentId(true);
        var relay = new TaskCallbackRelay(
                objectMapper,
                new GatewayProperties("gateway-node-test", "test", "1.2.5-test", "test gateway"),
                properties,
                    new CoreOutboundDispatcher(new CoreOutboundProperties()),
                    new TaskCallbackRelayMetrics()
        );
        var envelope = new AiEventEnvelope<>(
                "msg-callback-invalid-attempt",
                MessageType.TASK_ACK,
                null,
                "agent-001",
                "gateway-node-test",
                null,
                objectMapper.readTree("""
                        {
                          "taskId":"task-001",
                          "agentId":"agent-001",
                          "dispatchRequestId":"dispatch-001",
                          "assignmentId":"assignment-001",
                          "attemptNo":0,
                          "dispatchToken":"dispatch-token-001"
                        }
                        """)
        );

        TaskCallbackRelayResult result = relay.accept(envelope, ConnectionType.TCP, "connection-001", "agent-001");

        assertThat(result.submitted()).isFalse();
        assertThat(result.message()).contains("positive attemptNo");
    }

    @Test
    void shouldNotSubmitWhenRelayDisabled() throws Exception {
        var properties = new CoreTaskCallbackRelayProperties();
        properties.setEnabled(false);
        var relay = new TaskCallbackRelay(
                objectMapper,
                new GatewayProperties("gateway-node-test", "test", "1.2.5-test", "test gateway"),
                properties,
                    new CoreOutboundDispatcher(new CoreOutboundProperties()),
                    new TaskCallbackRelayMetrics()
        );
        var envelope = new AiEventEnvelope<>(
                "msg-callback-disabled",
                MessageType.TASK_RESULT,
                null,
                "agent-001",
                "gateway-node-test",
                null,
                objectMapper.readTree("{\"taskId\":\"task-001\",\"agentId\":\"agent-001\",\"status\":\"COMPLETED\"}")
        );

        TaskCallbackRelayResult result = relay.accept(envelope, ConnectionType.WEBSOCKET, "session-001", "agent-001");

        assertThat(result.submitted()).isFalse();
        assertThat(result.status()).isEqualTo("RELAY_DISABLED");
    }

    private HttpServer startServer(ArrayBlockingQueue<CapturedRequest> captured, String responseBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            captured.offer(new CapturedRequest(exchange.getRequestURI().getPath(), exchange.getRequestHeaders().getFirst("X-Cluster-Token"), body));
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        return server;
    }

    private record CapturedRequest(String path, String clusterToken, String body) {
        String header(String name) {
            return "X-Cluster-Token".equalsIgnoreCase(name) ? clusterToken : null;
        }
    }
}
