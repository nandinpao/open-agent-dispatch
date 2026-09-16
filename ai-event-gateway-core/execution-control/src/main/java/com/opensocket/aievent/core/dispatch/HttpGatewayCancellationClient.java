package com.opensocket.aievent.core.dispatch;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
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
    private final HttpClient client;

    public HttpGatewayCancellationClient(DispatchProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.client = HttpClient.newBuilder()
                .connectTimeout(properties.getClient().getConnectTimeout())
                .build();
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
            String base = properties.getClient().getGatewayBaseUrls()
                    .get(cancellation.getOwnerGatewayNodeId());
            if (base == null || base.isBlank()) {
                base = properties.getClient().getDefaultGatewayBaseUrl();
            }
            if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
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
                    "issuedBy", properties.getSourceNodeId(),
                    "timeoutMs", Math.max(100L,
                            properties.getClient().getRequestTimeout().toMillis()));
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path))
                    .timeout(properties.getClient().getRequestTimeout())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(envelope)));
            String token = properties.getClient().getInternalToken();
            if (token != null && !token.isBlank()) {
                builder.header(properties.getClient().getInternalTokenHeader(), token);
            }
            HttpResponse<String> response = client.send(builder.build(),
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
