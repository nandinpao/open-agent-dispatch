package com.opensocket.aievent.gateway.netty.websocket;

import com.opensocket.aievent.gateway.netty.delivery.TransportWriteResult;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.springframework.stereotype.Component;

import java.net.SocketAddress;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket gateway component for Web Socket Session Registry. It supports Agent and Admin UI
 * real-time channels, including message processing, session tracking, and event broadcasting.
 */
@Component
public class WebSocketSessionRegistry {

    private final Map<String, WebSocketSessionRecord> sessions = new ConcurrentHashMap<>();

    public WebSocketSessionSnapshot register(Channel channel, WebSocketClientType clientType, String path) {
        return register(channel, clientType, path, false);
    }

    public WebSocketSessionSnapshot register(Channel channel, WebSocketClientType clientType, String path, boolean agentOnboardingAuthenticated) {
        var now = OffsetDateTime.now();
        var sessionId = sessionId(channel);
        var record = new WebSocketSessionRecord(
                sessionId,
                clientType,
                path,
                remoteAddress(channel.remoteAddress()),
                channel,
                null,
                WebSocketSessionState.CONNECTED,
                now,
                now,
                agentOnboardingAuthenticated
        );
        sessions.put(sessionId, record);
        return record.toSnapshot();
    }

    public void touch(String sessionId) {
        var record = sessions.get(sessionId);
        if (record != null) {
            record.lastActiveAt = OffsetDateTime.now();
        }
    }

    public boolean isAgentOnboardingAuthenticated(String sessionId) {
        var record = sessions.get(sessionId);
        return record != null && record.agentOnboardingAuthenticated;
    }

    public void markAgentOnboardingAuthenticated(String sessionId) {
        var record = sessions.get(sessionId);
        if (record != null) {
            record.agentOnboardingAuthenticated = true;
            record.lastActiveAt = OffsetDateTime.now();
        }
    }

    public void markAgentRegistered(String sessionId, String agentId) {
        var now = OffsetDateTime.now();
        var record = sessions.get(sessionId);
        if (record == null) {
            record = new WebSocketSessionRecord(
                    sessionId,
                    WebSocketClientType.AGENT,
                    "/ws/agent",
                    "unknown",
                    null,
                    agentId,
                    WebSocketSessionState.REGISTERED,
                    now,
                    now,
                    false
            );
            sessions.put(sessionId, record);
            return;
        }
        record.agentId = agentId;
        record.state = WebSocketSessionState.REGISTERED;
        record.lastActiveAt = now;
    }

    public void close(String sessionId) {
        var record = sessions.get(sessionId);
        if (record != null) {
            record.state = WebSocketSessionState.CLOSED;
            record.lastActiveAt = OffsetDateTime.now();
        }
    }

    public WebSocketSessionSnapshot getSnapshot(String sessionId) {
        var record = sessions.get(sessionId);
        return record == null ? null : record.toSnapshot();
    }

    public String getAgentId(String sessionId) {
        var record = sessions.get(sessionId);
        return record == null ? null : record.agentId;
    }

    public String getRemoteAddress(String sessionId) {
        var record = sessions.get(sessionId);
        return record == null ? null : record.remoteAddress;
    }

    public boolean sendText(String sessionId, String message) {
        return sendTextWithTimeout(sessionId, message, Duration.ofSeconds(3)).status() == com.opensocket.aievent.gateway.netty.delivery.TransportWriteStatus.SENT;
    }

    public boolean isWritable(String sessionId) {
        var record = sessions.get(sessionId);
        return record != null
                && record.channel != null
                && record.state != WebSocketSessionState.CLOSED
                && record.channel.isActive()
                && record.channel.isWritable();
    }

    public TransportWriteResult sendTextWithTimeout(String sessionId, String message, Duration timeout) {
        var record = sessions.get(sessionId);
        if (record == null || record.channel == null || record.state == WebSocketSessionState.CLOSED) {
            return TransportWriteResult.notWritable("WebSocket session was not found or already closed");
        }
        if (!record.channel.isActive() || !record.channel.isWritable()) {
            return TransportWriteResult.notWritable("WebSocket channel is not active or not writable");
        }
        try {
            var future = record.channel.writeAndFlush(new TextWebSocketFrame(message));
            var timeoutMillis = timeout == null ? 3000L : Math.max(100L, timeout.toMillis());
            if (!future.await(timeoutMillis)) {
                return TransportWriteResult.timeout("WebSocket channel write did not complete within " + timeoutMillis + " ms");
            }
            if (!future.isSuccess()) {
                var cause = future.cause();
                return TransportWriteResult.failed(cause == null ? "WebSocket channel write failed" : cause.getMessage());
            }
            record.lastActiveAt = OffsetDateTime.now();
            return TransportWriteResult.sent();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return TransportWriteResult.failed("WebSocket channel write was interrupted");
        } catch (Exception ex) {
            return TransportWriteResult.failed(ex.getMessage());
        }
    }


    /**
     * Closes an active WebSocket channel by session id. This is used by Admin UI disconnect actions.
     */
    public boolean closeChannel(String sessionId) {
        var record = sessions.get(sessionId);
        if (record == null || record.channel == null) {
            return false;
        }
        record.channel.close();
        record.state = WebSocketSessionState.CLOSED;
        record.lastActiveAt = OffsetDateTime.now();
        return true;
    }

    public List<WebSocketSessionSnapshot> list() {
        return sessions.values().stream()
                .map(WebSocketSessionRecord::toSnapshot)
                .sorted(Comparator.comparing(WebSocketSessionSnapshot::connectedAt))
                .toList();
    }

    public long countActive() {
        return sessions.values().stream()
                .filter(session -> session.state != WebSocketSessionState.CLOSED)
                .count();
    }

    public long countActiveByRemoteAddress(String remoteAddress) {
        return sessions.values().stream()
                .filter(session -> session.state != WebSocketSessionState.CLOSED)
                .filter(session -> remoteAddress != null && remoteAddress.equals(session.remoteAddress))
                .count();
    }

    public long countActiveByType(WebSocketClientType clientType) {
        return sessions.values().stream()
                .filter(session -> session.state != WebSocketSessionState.CLOSED)
                .filter(session -> session.clientType == clientType)
                .count();
    }

    public static String sessionId(Channel channel) {
        return channel.id().asLongText();
    }

    private static String remoteAddress(SocketAddress address) {
        return address == null ? "unknown" : address.toString();
    }

    private static final class WebSocketSessionRecord {
        private final String sessionId;
        private final WebSocketClientType clientType;
        private final String path;
        private final String remoteAddress;
        private final Channel channel;
        private String agentId;
        private WebSocketSessionState state;
        private final OffsetDateTime connectedAt;
        private OffsetDateTime lastActiveAt;
        private boolean agentOnboardingAuthenticated;

        private WebSocketSessionRecord(
                String sessionId,
                WebSocketClientType clientType,
                String path,
                String remoteAddress,
                Channel channel,
                String agentId,
                WebSocketSessionState state,
                OffsetDateTime connectedAt,
                OffsetDateTime lastActiveAt,
                boolean agentOnboardingAuthenticated
        ) {
            this.sessionId = sessionId;
            this.clientType = clientType;
            this.path = path;
            this.remoteAddress = remoteAddress;
            this.channel = channel;
            this.agentId = agentId;
            this.state = state;
            this.connectedAt = connectedAt;
            this.lastActiveAt = lastActiveAt;
            this.agentOnboardingAuthenticated = agentOnboardingAuthenticated;
        }

        private WebSocketSessionSnapshot toSnapshot() {
            return new WebSocketSessionSnapshot(
                    sessionId,
                    clientType,
                    path,
                    remoteAddress,
                    agentId,
                    state,
                    connectedAt,
                    lastActiveAt
            );
        }
    }
}
