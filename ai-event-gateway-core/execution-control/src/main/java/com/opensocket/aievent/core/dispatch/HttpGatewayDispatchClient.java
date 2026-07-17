package com.opensocket.aievent.core.dispatch;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(prefix = "dispatch.client", name = "enabled", havingValue = "true")
public class HttpGatewayDispatchClient implements GatewayDispatchClient {
    private static final Logger log = LoggerFactory.getLogger(HttpGatewayDispatchClient.class);

    private static final String DEFAULT_NETTY_DELIVERY_PATH = "/internal/delivery/agents/{agentId}/commands";
    private static final String LEGACY_CORE_DISPATCH_PATH = "/internal/gateway/tasks/dispatch";

    private final DispatchProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HttpGatewayDispatchClient(DispatchProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getClient().getConnectTimeout())
                .build();
    }

    @Override
    public GatewayDispatchResult dispatch(DispatchRequest request) {
        if (request == null || request.getCommand() == null) {
            return GatewayDispatchResult.failure(0, "INVALID_DISPATCH_REQUEST", "Dispatch request or command is missing");
        }
        NettyDispatchCommand command = request.getCommand();
        if (blank(targetAgentId(request))) {
            return GatewayDispatchResult.failure(0, "INVALID_DISPATCH_REQUEST", "Target agent id is required for Netty command delivery");
        }
        try {
            URI uri = URI.create(baseUrl(request.getOwnerGatewayNodeId()) + dispatchPath(request));
            String body = objectMapper.writeValueAsString(toGatewayRequest(request));
            log.info("gateway_dispatch_http_started dispatchRequestId={} taskId={} agentId={} gatewayNode={} uri={} timeoutMs={} tokenPresent={}",
                    safe(request.getDispatchRequestId()), safe(request.getTaskId()), safe(targetAgentId(request)), safe(request.getOwnerGatewayNodeId()),
                    uri, properties.getClient().getRequestTimeout().toMillis(), !blank(properties.getClient().getInternalToken()));
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                    .timeout(properties.getClient().getRequestTimeout())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (!blank(properties.getClient().getInternalToken())) {
                builder.header(properties.getClient().getInternalTokenHeader(), properties.getClient().getInternalToken());
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            log.info("gateway_dispatch_http_response dispatchRequestId={} taskId={} agentId={} httpStatus={} bodyPreview={}",
                    safe(request.getDispatchRequestId()), safe(request.getTaskId()), safe(targetAgentId(request)), response.statusCode(), preview(response.body()));
            GatewayDispatchResponse gatewayResponse = parse(response.body(), request);
            if (response.statusCode() >= 200 && response.statusCode() < 300 && gatewayResponse != null && gatewayResponse.isAccepted()) {
                return GatewayDispatchResult.success(response.statusCode(), gatewayResponse);
            }
            String status = gatewayResponse == null ? "GATEWAY_DISPATCH_REJECTED" : gatewayResponse.getStatus();
            String message = gatewayResponse == null ? response.body() : gatewayResponse.getMessage();
            return GatewayDispatchResult.failure(response.statusCode(), status, message);
        } catch (Exception ex) {
            log.warn("gateway_dispatch_http_exception dispatchRequestId={} taskId={} agentId={} exception={} message={}",
                    safe(request.getDispatchRequestId()), safe(request.getTaskId()), safe(targetAgentId(request)), ex.getClass().getSimpleName(), ex.getMessage());
            return GatewayDispatchResult.failure(0, "GATEWAY_DISPATCH_EXCEPTION", ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    private String baseUrl(String ownerGatewayNodeId) {
        String configured = properties.getClient().getGatewayBaseUrls().get(ownerGatewayNodeId);
        String base = configured == null || configured.isBlank() ? properties.getClient().getDefaultGatewayBaseUrl() : configured;
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private String dispatchPath(DispatchRequest request) {
        String path = firstNonBlank(request.getGatewayDispatchPath(), properties.getGatewayDispatchPath(), DEFAULT_NETTY_DELIVERY_PATH);
        if (LEGACY_CORE_DISPATCH_PATH.equals(path)) {
            path = firstNonBlank(properties.getGatewayDispatchPath(), DEFAULT_NETTY_DELIVERY_PATH);
        }
        if (LEGACY_CORE_DISPATCH_PATH.equals(path)) {
            path = DEFAULT_NETTY_DELIVERY_PATH;
        }
        NettyDispatchCommand command = request.getCommand();
        Map<String, String> variables = Map.of(
                "agentId", targetAgentId(request),
                "targetAgentId", targetAgentId(request),
                "ownerGatewayNodeId", firstNonBlank(request.getOwnerGatewayNodeId(), command.getOwnerGatewayNodeId(), ""),
                "gatewayNodeId", firstNonBlank(request.getOwnerGatewayNodeId(), command.getOwnerGatewayNodeId(), "")
        );
        String rendered = path;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            rendered = rendered.replace("{" + entry.getKey() + "}", urlEncode(entry.getValue()));
        }
        return rendered.startsWith("/") ? rendered : "/" + rendered;
    }

    private Map<String, Object> toGatewayRequest(DispatchRequest request) {
        NettyDispatchCommand command = request.getCommand();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", firstNonBlank(command.getTaskId(), request.getTaskId()));
        payload.put("dispatchRequestId", firstNonBlank(command.getDispatchRequestId(), request.getDispatchRequestId()));
        payload.put("assignmentId", firstNonBlank(command.getAssignmentId(), request.getAssignmentId()));
        payload.put("attemptNo", command.getAttemptNo());
        // Netty delivery validates payload.agentId against the /agents/{agentId}
        // path before writing the command to the transport. Keep targetAgentId for
        // backward-compatible Admin/UI consumers, but always include the canonical
        // Core -> Netty delivery identity as agentId.
        payload.put("agentId", targetAgentId(request));
        payload.put("targetAgentId", targetAgentId(request));
        payload.put("ownerGatewayNodeId", firstNonBlank(command.getOwnerGatewayNodeId(), request.getOwnerGatewayNodeId()));
        payload.put("agentSessionId", firstNonBlank(command.getAgentSessionId(), request.getAgentSessionId()));
        payload.put("sourceNodeId", firstNonBlank(command.getSourceNodeId(), properties.getSourceNodeId()));
        payload.put("dispatchToken", firstNonBlank(request.getDispatchToken(), command.getDispatchToken()));
        payload.put("fencingToken", command.getFencingToken());
        payload.put("incidentId", firstNonBlank(command.getIncidentId(), request.getIncidentId()));
        payload.put("taskType", command.getTaskType() == null ? "INCIDENT_RESPONSE" : command.getTaskType());
        payload.put("priority", command.getPriority());
        payload.put("routingPolicy", command.getRoutingPolicy());
        payload.put("requiredCapabilities", command.getRequiredCapabilities() == null ? java.util.List.of() : command.getRequiredCapabilities());
        payload.put("input", command.getInput() == null ? Map.of() : command.getInput());

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("commandId", firstNonBlank(command.getDispatchRequestId(), request.getDispatchRequestId(), command.getTaskId(), request.getTaskId()));
        envelope.put("messageType", "TASK_DISPATCH");
        envelope.put("payload", payload);
        envelope.put("traceId", firstNonBlank(command.getTaskId(), request.getTaskId(), command.getDispatchRequestId(), request.getDispatchRequestId()));
        envelope.put("issuedBy", firstNonBlank(command.getSourceNodeId(), properties.getSourceNodeId()));
        envelope.put("timeoutMs", Math.max(100L, properties.getClient().getRequestTimeout().toMillis()));
        return envelope;
    }

    private GatewayDispatchResponse parse(String body, DispatchRequest request) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
            map = unwrapStandardApiEnvelope(map);
            if (map.containsKey("deliveryStatus")) {
                return fromNettyDeliveryResponse(map, request, body);
            }
            if (map.containsKey("accepted") || map.containsKey("status")) {
                return fromLegacyGatewayResponse(map, request, body);
            }
        } catch (Exception ignored) {
            // Fall back to the legacy typed response below.
        }
        try {
            return objectMapper.readValue(body, GatewayDispatchResponse.class);
        } catch (Exception ignoredAgain) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapStandardApiEnvelope(Map<String, Object> map) {
        if (map == null) {
            return Map.of();
        }
        Object code = map.get("code");
        Object data = map.get("data");
        if (code instanceof String && data instanceof Map<?, ?> dataMap) {
            Map<String, Object> unwrapped = new LinkedHashMap<>();
            dataMap.forEach((key, value) -> {
                if (key != null) {
                    unwrapped.put(String.valueOf(key), value);
                }
            });
            return unwrapped;
        }
        return map;
    }

    private GatewayDispatchResponse fromNettyDeliveryResponse(Map<String, Object> map, DispatchRequest request, String rawBody) {
        String deliveryStatus = stringValue(map.get("deliveryStatus"), "UNKNOWN");
        boolean accepted = "DELIVERED".equalsIgnoreCase(deliveryStatus);
        GatewayDispatchResponse response = new GatewayDispatchResponse();
        response.setTaskId(request.getTaskId());
        response.setAssignmentId(request.getAssignmentId());
        response.setTargetAgentId(stringValue(map.get("agentId"), targetAgentId(request)));
        response.setOwnerGatewayNodeId(stringValue(map.get("gatewayNodeId"), request.getOwnerGatewayNodeId()));
        response.setAccepted(accepted);
        response.setStatus(deliveryStatus.toUpperCase(Locale.ROOT));
        response.setMessage(stringValue(map.get("message"), rawBody));
        return response;
    }

    private GatewayDispatchResponse fromLegacyGatewayResponse(Map<String, Object> map, DispatchRequest request, String rawBody) {
        GatewayDispatchResponse response = new GatewayDispatchResponse();
        response.setTaskId(stringValue(map.get("taskId"), request.getTaskId()));
        response.setAssignmentId(stringValue(map.get("assignmentId"), request.getAssignmentId()));
        response.setTargetAgentId(stringValue(map.get("targetAgentId"), targetAgentId(request)));
        response.setOwnerGatewayNodeId(stringValue(map.get("ownerGatewayNodeId"), request.getOwnerGatewayNodeId()));
        response.setAccepted(Boolean.TRUE.equals(map.get("accepted")));
        response.setStatus(stringValue(map.get("status"), "UNKNOWN"));
        response.setMessage(stringValue(map.get("message"), rawBody));
        return response;
    }

    private String preview(String body) {
        if (body == null) {
            return "-";
        }
        String normalized = body.replaceAll("\\s+", " ").trim();
        return normalized.length() > 500 ? normalized.substring(0, 500) + "..." : normalized;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String targetAgentId(DispatchRequest request) {
        NettyDispatchCommand command = request.getCommand();
        return firstNonBlank(command.getTargetAgentId(), request.getAgentId());
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String stringValue(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value);
        return text == null || text.isBlank() ? defaultValue : text;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
