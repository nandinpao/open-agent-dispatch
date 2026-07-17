package com.opensocket.aievent.core.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

class HttpGatewayDispatchClientContractTest {

    @Test
    void shouldCallNettyDeliveryEndpointAndTranslateDeliveredResponse() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicReference<String> observedPath = new AtomicReference<>();
        AtomicReference<String> observedToken = new AtomicReference<>();
        AtomicReference<Map<String, Object>> observedBody = new AtomicReference<>();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            observedPath.set(exchange.getRequestURI().getPath());
            observedToken.set(exchange.getRequestHeaders().getFirst("X-Cluster-Token"));
            observedBody.set(readJson(exchange, objectMapper));
            writeJson(exchange, 200, """
                    {
                      "attemptId":"attempt-1",
                      "commandId":"dispatch-1",
                      "traceId":"task-1",
                      "agentId":"agent-001",
                      "gatewayNodeId":"gateway-node-001",
                      "deliveryStatus":"DELIVERED",
                      "connectionType":"TCP",
                      "message":"delivered to local agent"
                    }
                    """);
        });
        server.start();
        try {
            DispatchProperties properties = new DispatchProperties();
            properties.getClient().setDefaultGatewayBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            properties.getClient().setInternalToken("internal-token");
            properties.getClient().setInternalTokenHeader("X-Cluster-Token");
            HttpGatewayDispatchClient client = new HttpGatewayDispatchClient(properties, objectMapper);

            DispatchRequest request = request();
            GatewayDispatchResult result = client.dispatch(request);

            assertThat(result.success()).isTrue();
            assertThat(result.gatewayStatus()).isEqualTo("DELIVERED");
            assertThat(result.response()).isNotNull();
            assertThat(result.response().isAccepted()).isTrue();
            assertThat(result.response().getTargetAgentId()).isEqualTo("agent-001");
            assertThat(result.response().getOwnerGatewayNodeId()).isEqualTo("gateway-node-001");

            assertThat(observedPath.get()).isEqualTo("/internal/delivery/agents/agent-001/commands");
            assertThat(observedToken.get()).isEqualTo("internal-token");
            assertThat(observedBody.get()).containsEntry("messageType", "TASK_DISPATCH");
            assertThat(observedBody.get()).containsEntry("commandId", "dispatch-1");
            assertThat(observedBody.get()).containsEntry("traceId", "task-1");
            assertThat(observedBody.get()).containsEntry("issuedBy", "ai-event-gateway-core");
            assertThat(observedBody.get().get("payload")).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) observedBody.get().get("payload");
            assertThat(payload).containsEntry("taskId", "task-1");
            assertThat(payload).containsEntry("dispatchRequestId", "dispatch-1");
            assertThat(payload).containsEntry("assignmentId", "assignment-1");
            assertThat(payload).containsEntry("agentId", "agent-001");
            assertThat(payload).containsEntry("targetAgentId", "agent-001");
            assertThat(payload).containsEntry("ownerGatewayNodeId", "gateway-node-001");
            assertThat(payload).containsEntry("dispatchToken", "dispatch-token-1");
            assertThat(payload).containsEntry("fencingToken", "fence-1");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatNonDeliveredNettyResponseAsDispatchFailure() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> writeJson(exchange, 202, """
                {
                  "attemptId":"attempt-1",
                  "commandId":"dispatch-1",
                  "agentId":"agent-001",
                  "gatewayNodeId":"gateway-node-001",
                  "deliveryStatus":"AGENT_NOT_CONNECTED",
                  "message":"agent is not locally connected"
                }
                """));
        server.start();
        try {
            DispatchProperties properties = new DispatchProperties();
            properties.getClient().setDefaultGatewayBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            HttpGatewayDispatchClient client = new HttpGatewayDispatchClient(properties, objectMapper);

            GatewayDispatchResult result = client.dispatch(request());

            assertThat(result.success()).isFalse();
            assertThat(result.httpStatus()).isEqualTo(202);
            assertThat(result.gatewayStatus()).isEqualTo("AGENT_NOT_CONNECTED");
            assertThat(result.message()).isEqualTo("agent is not locally connected");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldUnwrapStandardApiEnvelopeForNettyDeliveryResponse() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> writeJson(exchange, 200, """
                {
                  "code":"OK",
                  "message":"Success",
                  "data":{
                    "attemptId":"attempt-1",
                    "commandId":"dispatch-1",
                    "agentId":"agent-001",
                    "gatewayNodeId":"gateway-node-001",
                    "deliveryStatus":"AGENT_NOT_CONNECTED",
                    "message":"Agent is not connected to this gateway"
                  },
                  "timestamp":"2026-06-28T00:00:00Z"
                }
                """));
        server.start();
        try {
            DispatchProperties properties = new DispatchProperties();
            properties.getClient().setDefaultGatewayBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            HttpGatewayDispatchClient client = new HttpGatewayDispatchClient(properties, objectMapper);

            GatewayDispatchResult result = client.dispatch(request());

            assertThat(result.success()).isFalse();
            assertThat(result.httpStatus()).isEqualTo(200);
            assertThat(result.gatewayStatus()).isEqualTo("AGENT_NOT_CONNECTED");
            assertThat(result.message()).isEqualTo("Agent is not connected to this gateway");
        } finally {
            server.stop(0);
        }
    }

    private DispatchRequest request() {
        NettyDispatchCommand command = new NettyDispatchCommand();
        command.setTaskId("task-1");
        command.setDispatchRequestId("dispatch-1");
        command.setAssignmentId("assignment-1");
        command.setTargetAgentId("agent-001");
        command.setOwnerGatewayNodeId("gateway-node-001");
        command.setAgentSessionId("session-001");
        command.setSourceNodeId("ai-event-gateway-core");
        command.setAttemptNo(1);
        command.setTaskType("INCIDENT_RESPONSE");
        command.setFencingToken("fence-1");
        command.setInput(Map.of("incidentId", "incident-1"));

        DispatchRequest request = new DispatchRequest();
        request.setTaskId("task-1");
        request.setDispatchRequestId("dispatch-1");
        request.setAssignmentId("assignment-1");
        request.setAgentId("agent-001");
        request.setOwnerGatewayNodeId("gateway-node-001");
        request.setAgentSessionId("session-001");
        request.setDispatchToken("dispatch-token-1");
        request.setCommand(command);
        return request;
    }

    private Map<String, Object> readJson(HttpExchange exchange, ObjectMapper objectMapper) throws IOException {
        try (InputStream input = exchange.getRequestBody()) {
            String body = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
        }
    }

    private void writeJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
