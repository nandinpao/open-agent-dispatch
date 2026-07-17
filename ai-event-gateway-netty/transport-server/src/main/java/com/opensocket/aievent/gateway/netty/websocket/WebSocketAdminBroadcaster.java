package com.opensocket.aievent.gateway.netty.websocket;

import tools.jackson.databind.ObjectMapper;
import com.opensocket.aievent.gateway.netty.admin.AdminEventPublisher;
import com.opensocket.aievent.gateway.netty.admin.AdminEventStore;
import com.opensocket.aievent.gateway.netty.admin.dto.AdminEventPayload;
import com.opensocket.aievent.gateway.netty.admin.dto.AdminRealtimeEvent;
import com.opensocket.aievent.gateway.netty.config.GatewayProperties;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * WebSocket gateway component for Web Socket Admin Broadcaster. It supports Agent and Admin UI
 * real-time channels, including message processing, session tracking, and event broadcasting.
 */
@Component
public class WebSocketAdminBroadcaster implements AdminEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAdminBroadcaster.class);

    private final ChannelGroup adminChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    private final ObjectMapper objectMapper;
    private final GatewayProperties gatewayProperties;
    private final AdminEventStore adminEventStore;

    public WebSocketAdminBroadcaster(
            ObjectMapper objectMapper,
            GatewayProperties gatewayProperties,
            AdminEventStore adminEventStore
    ) {
        this.objectMapper = objectMapper;
        this.gatewayProperties = gatewayProperties;
        this.adminEventStore = adminEventStore;
    }

    public void addAdminChannel(Channel channel) {
        adminChannels.add(channel);
    }

    public void remove(Channel channel) {
        adminChannels.remove(channel);
    }

    public int adminChannelCount() {
        return adminChannels.size();
    }

    @Override
    public void broadcast(String eventType, String message, Map<String, Object> data) {
        var payload = adminEventStore.append(eventType, message, data);
        writePayloadToAll(payload);
    }

    public void sendDirect(Channel channel, String eventType, String message, Map<String, Object> data) {
        var payload = adminEventStore.append(eventType, message, data);
        writePayloadToChannel(channel, payload);
    }

    /**
     * Broadcasts a transient Admin UI event without persisting it in the AdminEventStore.
     *
     * <p>P2 standardizes all Admin WebSocket pushes on the same flat event shape:</p>
     *
     * <pre>{"eventType":"...","timestamp":"...","nodeId":"...","payload":{...}}</pre>
     */
    public void broadcastRealtime(String eventType, Object payload) {
        if (adminChannels.isEmpty()) {
            return;
        }
        writeRealtimeToAll(newRealtimeEvent(eventType, OffsetDateTime.now(), payload));
    }

    private void writePayloadToAll(AdminEventPayload payload) {
        writeRealtimeToAll(toRealtimeEvent(payload));
    }

    private void writePayloadToChannel(Channel channel, AdminEventPayload payload) {
        if (channel == null || !channel.isActive()) {
            return;
        }
        writeRealtimeToChannel(channel, toRealtimeEvent(payload));
    }

    AdminRealtimeEvent<AdminEventPayload> toRealtimeEvent(AdminEventPayload payload) {
        return newRealtimeEvent(
                payload.eventType(),
                payload.occurredAt() == null ? OffsetDateTime.now() : payload.occurredAt(),
                payload
        );
    }

    <T> AdminRealtimeEvent<T> newRealtimeEvent(String eventType, OffsetDateTime timestamp, T payload) {
        return new AdminRealtimeEvent<>(
                eventType,
                timestamp,
                gatewayProperties.nodeId(),
                payload
        );
    }

    private void writeRealtimeToAll(AdminRealtimeEvent<?> event) {
        try {
            var json = objectMapper.writeValueAsString(event);
            adminChannels.writeAndFlush(new TextWebSocketFrame(json));
        } catch (Exception ex) {
            log.warn("Failed to broadcast realtime admin event. eventType={}, reason={}", event.eventType(), ex.getMessage());
        }
    }

    private void writeRealtimeToChannel(Channel channel, AdminRealtimeEvent<?> event) {
        try {
            channel.writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(event)));
        } catch (Exception ex) {
            log.warn("Failed to send direct admin event. eventType={}, reason={}", event.eventType(), ex.getMessage());
        }
    }
}
