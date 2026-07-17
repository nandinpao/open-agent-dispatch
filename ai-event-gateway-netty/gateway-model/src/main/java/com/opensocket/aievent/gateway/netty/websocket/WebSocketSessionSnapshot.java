package com.opensocket.aievent.gateway.netty.websocket;

import java.time.OffsetDateTime;

/**
 * WebSocket gateway component for Web Socket Session Snapshot. It supports Agent and Admin UI
 * real-time channels, including message processing, session tracking, and event broadcasting.
 */
public record WebSocketSessionSnapshot(
        String sessionId,
        WebSocketClientType clientType,
        String path,
        String remoteAddress,
        String agentId,
        WebSocketSessionState state,
        OffsetDateTime connectedAt,
        OffsetDateTime lastActiveAt
) {
}
