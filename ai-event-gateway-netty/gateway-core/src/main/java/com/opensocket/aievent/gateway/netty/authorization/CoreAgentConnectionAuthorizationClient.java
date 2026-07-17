package com.opensocket.aievent.gateway.netty.authorization;

import com.opensocket.aievent.gateway.netty.config.GatewayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class CoreAgentConnectionAuthorizationClient implements AgentConnectionAuthorizationClient {
    private static final Logger log = LoggerFactory.getLogger(CoreAgentConnectionAuthorizationClient.class);

    private final CoreAgentAuthorizationProperties properties;
    private final GatewayProperties gatewayProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public CoreAgentConnectionAuthorizationClient(
            CoreAgentAuthorizationProperties properties,
            GatewayProperties gatewayProperties,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.gatewayProperties = gatewayProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentConnectionAuthorizationResponse authorize(AgentConnectionAuthorizationRequest request) {
        if (!properties.enabled()) {
            if (properties.allowWhenDisabled()) {
                log.warn("Core Agent authorization is disabled and allow-when-disabled=true. Allowing Agent without Core governance. agentId={}", request.agentId());
                return AgentConnectionAuthorizationResponse.allow(request.agentId());
            }
            log.error("Core Agent authorization is disabled. Denying Agent connection to avoid bypassing Core Governance. agentId={}", request.agentId());
            return AgentConnectionAuthorizationResponse.deny(request.agentId(), "AGENT_AUTHORIZATION_DISABLED");
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("agentId", request.agentId());
            body.put("agentType", request.agentType() == null ? null : request.agentType().name());
            body.put("connectionType", request.connectionType() == null ? null : request.connectionType().name());
            body.put("gatewayNodeId", gatewayProperties.nodeId());
            body.put("agentSessionId", firstNonBlank(request.sessionId(), request.connectionId()));
            body.put("connectionId", request.connectionId());
            body.put("sessionId", request.sessionId());
            body.put("remoteAddress", request.remoteAddress());
            body.put("claimedCapabilities", request.claimedCapabilities());
            body.put("metadata", request.metadata());
            body.put("credentialToken", request.credentialToken());
            body.put("publicKeyFingerprint", request.publicKeyFingerprint());
            var json = objectMapper.writeValueAsString(body);
            var builder = HttpRequest.newBuilder(URI.create(properties.authorizeUrl()))
                    .timeout(Duration.ofMillis(properties.timeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("X-Gateway-Node-Id", gatewayProperties.nodeId())
                    .header("X-Gateway-Site-Id", gatewayProperties.siteId())
                    .POST(HttpRequest.BodyPublishers.ofString(json));
            if (properties.hasAuthToken()) {
                builder.header(properties.authHeaderName(), properties.authToken());
            }
            var response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return readAuthorizationResponse(request.agentId(), response.body());
            }
            log.warn("Core denied or failed Agent authorization. agentId={}, httpStatus={}, response={}", request.agentId(), response.statusCode(), truncate(response.body()));
            return AgentConnectionAuthorizationResponse.deny(request.agentId(), "CORE_AUTHORIZATION_HTTP_" + response.statusCode());
        } catch (Exception ex) {
            log.warn("Core Agent authorization request failed. agentId={}, reason={}", request.agentId(), ex.getMessage());
            if (properties.failClosed()) {
                return AgentConnectionAuthorizationResponse.deny(request.agentId(), "CORE_AUTHORIZATION_UNAVAILABLE");
            }
            return AgentConnectionAuthorizationResponse.allow(request.agentId());
        }
    }

    private AgentConnectionAuthorizationResponse readAuthorizationResponse(String agentId, String body) throws Exception {
        Map<String, Object> root = objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
        if (isStandardApiEnvelope(root)) {
            String code = String.valueOf(root.getOrDefault("code", "INTERNAL_ERROR"));
            if (!"OK".equals(code)) {
                String message = String.valueOf(root.getOrDefault("message", code));
                log.warn("Core denied Agent authorization with standard envelope. agentId={}, code={}, message={}", agentId, code, message);
                return AgentConnectionAuthorizationResponse.deny(agentId, code);
            }
            Object data = root.get("data");
            if (data == null) {
                return AgentConnectionAuthorizationResponse.deny(agentId, "CORE_AUTHORIZATION_EMPTY_DATA");
            }
            return objectMapper.convertValue(data, AgentConnectionAuthorizationResponse.class);
        }
        return objectMapper.convertValue(root, AgentConnectionAuthorizationResponse.class);
    }

    private boolean isStandardApiEnvelope(Map<String, Object> root) {
        return root != null
                && root.containsKey("code")
                && root.containsKey("message")
                && root.containsKey("data")
                && root.containsKey("timestamp");
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private String truncate(String value) {
        if (value == null) return "";
        return value.length() <= 500 ? value : value.substring(0, 500) + "...";
    }
}
