package com.opensocket.aievent.gateway.netty.websocket;

/**
 * WebSocket gateway component for Web Socket Session State. It supports Agent and Admin UI
 * real-time channels, including message processing, session tracking, and event broadcasting.
 */
public enum WebSocketSessionState {
    CONNECTED,
    REGISTERED,
    CLOSED
}
