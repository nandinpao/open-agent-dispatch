package com.opensocket.aievent.core.runtime;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.core.type.TypeReference;

import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * Core-owned runtime enforcement client.
 *
 * <p>This moves disconnect responsibility out of the Admin UI. Core updates governance first,
 * then calls Netty Admin API to close any currently connected runtime session.</p>
 */
@Component
public class CoreRuntimeDisconnectClient {
    private final RuntimeDisconnectProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public CoreRuntimeDisconnectClient(RuntimeDisconnectProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(properties.getRequestTimeout()).build();
    }

    public RuntimeDisconnectResult disconnectAgent(String agentId, String gatewayNodeId, String reason, String operatorId) {
        if (!properties.isEnabled()) {
            return RuntimeDisconnectResult.disabled(agentId, "Core runtime disconnect enforcement is disabled.");
        }
        List<String> candidates = candidateBaseUrlsFor(gatewayNodeId);
        if (candidates.isEmpty()) {
            RuntimeDisconnectResult result = RuntimeDisconnectResult.failed(agentId, gatewayNodeId, 0, "No Netty Admin API base URL configured for runtime disconnect.");
            throw new RuntimeDisconnectException(result);
        }

        RuntimeDisconnectResult lastFailure = null;
        Map<String, Object> attempts = new LinkedHashMap<>();
        for (String baseUrl : candidates) {
            String endpoint = baseUrl + "/api/admin/agents/" + URLEncoder.encode(agentId, StandardCharsets.UTF_8) + "/disconnect";
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("requestedBy", operatorId == null || operatorId.isBlank() ? "core-governance" : operatorId);
                payload.put("reason", reason == null || reason.isBlank() ? "Core governance transition requires runtime disconnect" : reason);
                payload.put("gatewayNodeId", gatewayNodeId == null ? "" : gatewayNodeId);
                String body = objectMapper.writeValueAsString(payload);
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint))
                        .timeout(properties.getRequestTimeout())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body));
                addTokenHeader(builder);
                HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                Map<String, Object> data = parseData(response.body());
                data.putIfAbsent("endpoint", endpoint);
                data.putIfAbsent("requestedGatewayNodeId", gatewayNodeId == null ? "" : gatewayNodeId);
                boolean closed = booleanValue(data.get("closed"));
                String status = response.statusCode() >= 200 && response.statusCode() < 300
                        ? (closed ? "DISCONNECTED" : "REQUESTED_NOT_CLOSED")
                        : response.statusCode() == 404 ? "NOT_FOUND_ON_NODE" : "FAILED";
                RuntimeDisconnectResult result = new RuntimeDisconnectResult(
                        agentId,
                        gatewayNodeId,
                        status,
                        true,
                        closed,
                        response.statusCode(),
                        messageFrom(data, response.body()),
                        data,
                        OffsetDateTime.now()
                );
                attempts.put(endpoint, Map.of(
                        "httpStatus", response.statusCode(),
                        "status", status,
                        "closed", closed,
                        "message", result.message() == null ? "" : result.message()
                ));
                if (response.statusCode() >= 200 && response.statusCode() < 300 && closed) {
                    return result;
                }
                lastFailure = result;
                // 404 is common when Core calls a non-owner Netty node. Continue trying the
                // remaining configured node URLs before returning a failure result.
            } catch (IOException e) {
                lastFailure = RuntimeDisconnectResult.failed(agentId, gatewayNodeId, 0, "Netty runtime disconnect request failed at " + endpoint + ": " + e.getMessage());
                attempts.put(endpoint, Map.of("httpStatus", 0, "status", "FAILED", "closed", false, "message", e.getMessage() == null ? "" : e.getMessage()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                RuntimeDisconnectResult result = RuntimeDisconnectResult.failed(agentId, gatewayNodeId, 0, "Netty runtime disconnect request was interrupted.");
                throw new RuntimeDisconnectException(result);
            }
        }

        if (lastFailure == null) {
            lastFailure = RuntimeDisconnectResult.failed(agentId, gatewayNodeId, 0, "Netty runtime disconnect failed without a detailed response.");
        }
        Map<String, Object> details = new LinkedHashMap<>(lastFailure.details() == null ? Map.of() : lastFailure.details());
        details.put("attemptedEndpoints", attempts);
        RuntimeDisconnectResult finalResult = new RuntimeDisconnectResult(
                lastFailure.agentId(),
                lastFailure.gatewayNodeId(),
                lastFailure.status(),
                lastFailure.requested(),
                lastFailure.closed(),
                lastFailure.httpStatus(),
                "Runtime disconnect was not completed after trying configured Netty Admin API endpoints. Last result: " + lastFailure.message(),
                details,
                OffsetDateTime.now()
        );
        throw new RuntimeDisconnectException(finalResult);
    }


    private List<String> candidateBaseUrlsFor(String gatewayNodeId) {
        List<String> candidates = new ArrayList<>();
        if (properties.isGatewayRegistryEnabled()) {
            addCandidates(candidates, discoverGatewayRegistryBaseUrls(gatewayNodeId));
        }
        addCandidates(candidates, properties.candidateBaseUrlsFor(gatewayNodeId));
        return List.copyOf(candidates);
    }

    @SuppressWarnings("unchecked")
    private List<String> discoverGatewayRegistryBaseUrls(String gatewayNodeId) {
        String registryUrl = properties.getGatewayRegistryUrl();
        if (registryUrl == null || registryUrl.isBlank()) {
            return List.of();
        }
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(registryUrl.trim()))
                    .timeout(properties.getRequestTimeout())
                    .GET();
            addTokenHeader(builder);
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body() == null || response.body().isBlank()) {
                return List.of();
            }
            Map<String, Object> root = objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
            List<String> candidates = new ArrayList<>();
            Object baseUrls = root.get("baseUrls");
            if (baseUrls instanceof Map<?, ?> map) {
                if (gatewayNodeId != null && !gatewayNodeId.isBlank()) {
                    Object owner = map.get(gatewayNodeId);
                    if (owner != null) addCandidate(candidates, String.valueOf(owner));
                }
                for (Object value : map.values()) {
                    if (value != null) addCandidate(candidates, String.valueOf(value));
                }
            }
            Object nodes = root.get("nodes");
            if (nodes instanceof Iterable<?> iterable) {
                for (Object item : iterable) {
                    if (!(item instanceof Map<?, ?> node)) continue;
                    Object nodeId = node.get("nodeId");
                    Object adminBaseUrl = node.get("adminBaseUrl");
                    if (gatewayNodeId != null && !gatewayNodeId.isBlank() && gatewayNodeId.equals(String.valueOf(nodeId))) {
                        if (adminBaseUrl != null) addCandidate(candidates, String.valueOf(adminBaseUrl));
                    }
                }
                for (Object item : iterable) {
                    if (!(item instanceof Map<?, ?> node)) continue;
                    Object adminBaseUrl = node.get("adminBaseUrl");
                    if (adminBaseUrl != null) addCandidate(candidates, String.valueOf(adminBaseUrl));
                }
            }
            return List.copyOf(candidates);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private void addCandidates(List<String> target, List<String> values) {
        if (values == null) return;
        for (String value : values) addCandidate(target, value);
    }

    private void addCandidate(List<String> target, String value) {
        if (target == null || value == null || value.isBlank()) return;
        String normalized = value.trim();
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        if (!normalized.isBlank() && !target.contains(normalized)) target.add(normalized);
    }

    private void addTokenHeader(HttpRequest.Builder builder) {
        String token = properties.getAdminToken();
        if (token == null || token.isBlank()) return;
        String header = properties.getAdminTokenHeader() == null || properties.getAdminTokenHeader().isBlank()
                ? "Authorization"
                : properties.getAdminTokenHeader();
        if ("Authorization".equalsIgnoreCase(header)) {
            String scheme = properties.getAuthorizationScheme() == null || properties.getAuthorizationScheme().isBlank()
                    ? "Bearer"
                    : properties.getAuthorizationScheme().trim();
            builder.header(header, scheme + " " + token);
        } else {
            builder.header(header, token);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseData(String body) {
        if (body == null || body.isBlank()) return Map.of();
        try {
            Map<String, Object> root = objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
            Object data = root.get("data");
            if (data instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) result.put(String.valueOf(entry.getKey()), entry.getValue());
                Object message = root.get("message");
                if (message != null) result.putIfAbsent("message", message);
                Object status = root.get("status");
                if (status != null) result.putIfAbsent("status", status);
                return result;
            }
            return root;
        } catch (Exception ignored) {
            return Map.of("rawBody", body);
        }
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof String text) return Boolean.parseBoolean(text);
        return false;
    }

    private String messageFrom(Map<String, Object> data, String fallback) {
        Object message = data.get("message");
        if (message != null && !String.valueOf(message).isBlank()) return String.valueOf(message);
        return fallback == null || fallback.isBlank() ? "Netty runtime disconnect completed without a message." : fallback;
    }
}
