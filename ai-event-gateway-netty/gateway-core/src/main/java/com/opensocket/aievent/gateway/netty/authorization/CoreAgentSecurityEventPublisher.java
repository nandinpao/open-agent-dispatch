package com.opensocket.aievent.gateway.netty.authorization;

import com.opensocket.aievent.gateway.netty.config.GatewayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class CoreAgentSecurityEventPublisher implements AgentSecurityEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(CoreAgentSecurityEventPublisher.class);
    private final CoreAgentAuthorizationProperties properties;
    private final GatewayProperties gatewayProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public CoreAgentSecurityEventPublisher(
            CoreAgentAuthorizationProperties properties,
            GatewayProperties gatewayProperties,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.gatewayProperties = gatewayProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishRejectedConnection(RejectedAgentConnectionSnapshot rejectedConnection) {
        if (!properties.enabled() || rejectedConnection == null) {
            return;
        }
        Map<String, Object> metadata = new LinkedHashMap<>(rejectedConnection.metadata() == null ? Map.of() : rejectedConnection.metadata());
        metadata.put("connectionType", rejectedConnection.connectionType() == null ? null : rejectedConnection.connectionType().name());
        metadata.put("connectionId", rejectedConnection.connectionId());
        metadata.put("sessionId", rejectedConnection.sessionId());
        publishEvent(
                "CONNECTION_DENIED",
                rejectedConnection.claimedAgentId(),
                rejectedConnection.claimedAgentId(),
                rejectedConnection.remoteAddress(),
                rejectedConnection.reason(),
                rejectedConnection.rejectedAt() == null ? null : rejectedConnection.rejectedAt().toString(),
                metadata
        );
    }

    @Override
    public void publishDuplicateRuntime(DuplicateRuntimeSecurityEvent duplicateRuntime) {
        if (!properties.enabled() || !properties.duplicateRuntimeDetectionEnabled() || duplicateRuntime == null) {
            return;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("gatewayNodeIds", duplicateRuntime.gatewayNodeIds());
        metadata.put("connectedCount", duplicateRuntime.connectedCount());
        metadata.put("sessions", duplicateRuntime.sessions());
        metadata.put("detectedBy", duplicateRuntime.detectedBy());
        metadata.put("localDuplicate", duplicateRuntime.localDuplicate());
        metadata.put("clusterDuplicate", duplicateRuntime.clusterDuplicate());
        metadata.put("policySource", "CORE_PER_AGENT_POLICY");
        metadata.put("corePolicyManaged", true);
        metadata.put("legacyAutoEnforceHint", properties.duplicateRuntimeAutoEnforce());
        metadata.put("legacyDisconnectAllHint", properties.duplicateRuntimeAutoDisconnectAll());
        metadata.put("legacyRevokeCredentialsHint", properties.duplicateRuntimeRevokeCredentials());
        metadata.put("requireCredentialRotation", true);
        metadata.put("operatorId", "netty-duplicate-runtime-detector");
        publishEvent(
                "DUPLICATE_RUNTIME_DETECTED",
                duplicateRuntime.agentId(),
                duplicateRuntime.agentId(),
                null,
                duplicateRuntime.reason(),
                duplicateRuntime.detectedAt() == null ? null : duplicateRuntime.detectedAt().toString(),
                metadata
        );
    }

    private void publishEvent(String eventType, String agentId, String claimedAgentId, String remoteAddress, String reason, String occurredAt, Map<String, Object> metadata) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("eventType", eventType);
            body.put("gatewayNodeId", gatewayProperties.nodeId());
            body.put("agentId", agentId);
            body.put("claimedAgentId", claimedAgentId);
            body.put("remoteAddress", remoteAddress);
            body.put("reason", reason);
            body.put("occurredAt", occurredAt);
            body.put("metadata", metadata == null ? Map.of() : metadata);
            var builder = HttpRequest.newBuilder(URI.create(properties.securityEventUrl()))
                    .timeout(Duration.ofMillis(properties.timeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("X-Gateway-Node-Id", gatewayProperties.nodeId())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            if (properties.hasAuthToken()) {
                builder.header(properties.authHeaderName(), properties.authToken());
            }
            httpClient.sendAsync(builder.build(), HttpResponse.BodyHandlers.discarding())
                    .thenAccept(response -> {
                        if (response.statusCode() < 200 || response.statusCode() >= 300) {
                            log.warn("Core security-event endpoint returned non-2xx. eventType={}, httpStatus={}, agentId={}", eventType, response.statusCode(), agentId);
                        }
                    })
                    .exceptionally(ex -> {
                        log.warn("Unable to publish Agent security event. eventType={}, agentId={}, reason={}", eventType, agentId, ex.getMessage());
                        return null;
                    });
        } catch (Exception ex) {
            log.warn("Unable to serialize Agent security event. eventType={}, agentId={}, reason={}", eventType, agentId, ex.getMessage());
        }
    }
}
