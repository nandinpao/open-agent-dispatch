package com.opensocket.aievent.gateway.netty.websocket.dto;

import com.opensocket.aievent.gateway.netty.websocket.WebSocketSessionSnapshot;

import java.util.List;

/**
 * WebSocket gateway component for Web Socket Session Response. It supports Agent and Admin UI
 * real-time channels, including message processing, session tracking, and event broadcasting.
 */
public record WebSocketSessionResponse(
        long activeCount,
        long agentSessionCount,
        long adminSessionCount,
        List<WebSocketSessionSnapshot> sessions
) {
}
