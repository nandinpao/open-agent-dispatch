package com.opensocket.aievent.core.dispatch;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import com.opensocket.aievent.core.a2a.A2ACancellationRecord;
import com.opensocket.aievent.core.assignment.TaskAssignment;

@Component
@ConditionalOnProperty(prefix = "dispatch.client", name = "enabled", havingValue = "true")
public class HttpGatewayCancellationClient implements GatewayCancellationClient {
    private final DispatchProperties properties;
    private final ObjectMapper mapper;
    private final DispatchRuntimeConfigurationView runtimeConfiguration;
    private volatile HttpClient client;
    private volatile Duration clientConnectTimeout;

    @Autowired
    public HttpGatewayCancellationClient(DispatchProperties properties, ObjectMapper mapper, DispatchRuntimeConfigurationView runtimeConfiguration) {
        this.properties = properties;
        this.mapper = mapper;
        this.runtimeConfiguration = runtimeConfiguration;
    }

    /** Compatibility constructor for focused tests. */
    public HttpGatewayCancellationClient(DispatchProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.runtimeConfiguration = null;
    }

    private HttpClient client() {
        Duration desired = runtimeConnectTimeout();
        HttpClient current = client;
        if (current != null && desired.equals(clientConnectTimeout)) return current;
        synchronized (this) {
            if (client == null || !desired.equals(clientConnectTimeout)) {
                client = HttpClient.newBuilder().connectTimeout(desired).build();
                clientConnectTimeout = desired;
            }
            return client;
        }
    }

    private Duration runtimeConnectTimeout() {
        return runtimeConfiguration == null ? properties.getClient().getConnectTimeout() : runtimeConfiguration.connectTimeout();
    }

    private Duration runtimeRequestTimeout() {
        return runtimeConfiguration == null ? properties.getClient().getRequestTimeout() : runtimeConfiguration.requestTimeout();
    }

    private String runtimeGatewayBaseUrl(String gatewayNodeId) {
        String base;
        if (runtimeConfiguration != null) {
            base = runtimeConfiguration.gatewayBaseUrl(gatewayNodeId);
        } else {
            base = properties.getClient().getGatewayBaseUrls().get(gatewayNodeId);
            if (base == null || base.isBlank()) base = properties.getClient().getDefaultGatewayBaseUrl();
            if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private String runtimeSourceNodeId() {
        return runtimeConfiguration == null ? properties.getSourceNodeId() : runtimeConfiguration.sourceNodeId();
    }

    @Override
    public GatewayCancellationResult cancel(A2ACancellationRecord cancellation,
            DispatchRequest dispatch, TaskAssignment assignment) {
        if (cancellation == null || dispatch == null || assignment == null) {
            return GatewayCancellationResult.failed(0, "INVALID_CANCEL_REQUEST",
                    "INVALID_CANCEL_REQUEST",
                    "Cancellation, Dispatch Request, or Assignment is missing");
        }
        String callbackFencingToken = dispatch.getCommand() != null
                && dispatch.getCommand().getFencingToken() != null
                && !dispatch.getCommand().getFencingToken().isBlank()
                ? dispatch.getCommand().getFencingToken() : assignment.getFencingToken();
        if (callbackFencingToken == null || callbackFencingToken.isBlank()) {
            return GatewayCancellationResult.failed(0, "MISSING_CANCELLATION_FENCING_TOKEN",
                    "MISSING_CANCELLATION_FENCING_TOKEN",
                    "Neither the original Dispatch fence nor the rotated Assignment fence is available");
        }
        try {
            String base = runtimeGatewayBaseUrl(cancellation.getOwnerGatewayNodeId());
            String path = "/internal/delivery/agents/"
                    + URLEncoder.encode(cancellation.getAgentId(), StandardCharsets.UTF_8)
                    + "/commands";

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("cancellationId", cancellation.getCancellationId());
            payload.put("a2aRequestId", cancellation.getRequestId());
            payload.put("taskId", cancellation.getChildTaskId());
            payload.put("dispatchRequestId", cancellation.getDispatchRequestId());
            payload.put("assignmentId", cancellation.getAssignmentId());
            payload.put("executionAttemptId", cancellation.getExecutionAttemptId());
            payload.put("agentId", cancellation.getAgentId());
            payload.put("agentSessionId", cancellation.getAgentSessionId());
            payload.put("dispatchToken", dispatch.getDispatchToken());
            // The Agent already owns the original Dispatch fence. It may echo that fence only
            // for task.cancelled; every non-cancellation callback remains blocked by the rotated fence.
            payload.put("fencingToken", callbackFencingToken);
            payload.put("cancellationFenceToken", assignment.getFencingToken());
            payload.put("activeFencingTokenHash", cancellation.getActiveFencingTokenHash());
            payload.put("revokedFencingTokenHash", cancellation.getRevokedFencingTokenHash());
            payload.put("reason", cancellation.getReason());

            Map<String, Object> envelope = Map.of(
                    "commandId", cancellation.getCancellationId(),
                    "messageType", "TASK_CANCEL",
                    "payload", payload,
                    "traceId", cancellation.getRequestId(),
                    "issuedBy", runtimeSourceNodeId(),
                    "timeoutMs", Math.max(100L,
                            runtimeRequestTimeout().toMillis()));
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path))
                    .timeout(runtimeRequestTimeout())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(envelope)));
            String token = properties.getClient().getInternalToken();
            if (token != null && !token.isBlank()) {
                builder.header(properties.getClient().getInternalTokenHeader(), token);
            }
            HttpResponse<String> response = client().send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());
            boolean accepted = response.statusCode() >= 200 && response.statusCode() < 300;
            return accepted
                    ? GatewayCancellationResult.accepted(response.statusCode(), "DELIVERED", response.body())
                    : GatewayCancellationResult.failed(response.statusCode(), "REJECTED",
                            "GATEWAY_CANCEL_REJECTED", response.body());
        } catch (Exception ex) {
            return GatewayCancellationResult.failed(0, "EXCEPTION", "GATEWAY_CANCEL_EXCEPTION",
                    ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }
}
