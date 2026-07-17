package com.opensocket.aievent.gateway.netty.websocket;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.opensocket.aievent.gateway.netty.admin.AdminEventStore;
import com.opensocket.aievent.gateway.netty.admin.audit.NoopAuditEventPersistencePort;
import com.opensocket.aievent.gateway.netty.admin.dto.AdminEventPayload;
import com.opensocket.aievent.gateway.netty.config.AdminProperties;
import com.opensocket.aievent.gateway.netty.config.AuditLogProperties;
import com.opensocket.aievent.gateway.netty.config.GatewayProperties;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WebSocketAdminBroadcasterTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void persistedAdminEventsShouldSerializeAsFlatRealtimeShape() throws Exception {
        var broadcaster = newBroadcaster();
        var payload = new AdminEventPayload(
                "event-1",
                "gateway-node-test",
                "AGENT_REGISTERED",
                "Agent registered",
                Map.of("agentId", "agent-1"),
                OffsetDateTime.now()
        );

        var json = objectMapper.writeValueAsString(broadcaster.toRealtimeEvent(payload));
        Map<String, Object> event = objectMapper.readValue(json, new TypeReference<>() {
        });

        assertThat(event).containsKeys("eventType", "timestamp", "nodeId", "payload");
        assertThat(event).doesNotContainKeys("messageType", "messageId", "source", "target");
        assertThat(event.get("eventType")).isEqualTo("AGENT_REGISTERED");
        assertThat(event.get("nodeId")).isEqualTo("gateway-node-test");

        @SuppressWarnings("unchecked")
        var serializedPayload = (Map<String, Object>) event.get("payload");
        assertThat(serializedPayload.get("eventType")).isEqualTo("AGENT_REGISTERED");
        assertThat(serializedPayload.get("message")).isEqualTo("Agent registered");
    }

    @Test
    void transientMetricEventsShouldUseSameFlatRealtimeShape() throws Exception {
        var broadcaster = newBroadcaster();

        var json = objectMapper.writeValueAsString(broadcaster.newRealtimeEvent(
                "GATEWAY_METRICS_UPDATED",
                OffsetDateTime.now(),
                Map.of("active", 1)
        ));
        Map<String, Object> event = objectMapper.readValue(json, new TypeReference<>() {
        });

        assertThat(event).containsKeys("eventType", "timestamp", "nodeId", "payload");
        assertThat(event.get("eventType")).isEqualTo("GATEWAY_METRICS_UPDATED");
        assertThat(event.get("nodeId")).isEqualTo("gateway-node-test");
    }

    private WebSocketAdminBroadcaster newBroadcaster() {
        var gatewayProperties = new GatewayProperties(
                "gateway-node-test",
                "test",
                "1.2.5-p9-netty-server-test-port-fix-test",
                "test gateway"
        );
        var adminProperties = new AdminProperties(200, List.of("http://localhost:3000"));
        var adminEventStore = new AdminEventStore(
                gatewayProperties,
                adminProperties,
                new AuditLogProperties(),
                new NoopAuditEventPersistencePort()
        );
        return new WebSocketAdminBroadcaster(objectMapper, gatewayProperties, adminEventStore);
    }
}
