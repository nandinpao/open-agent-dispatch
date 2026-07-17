package com.opensocket.aievent.gateway.netty.tcp;

/**
 * TCP gateway component for Tcp Connection State. It accepts newline-delimited JSON messages,
 * binds connections to Agents, and exposes local transport state.
 */
public enum TcpConnectionState {
    CONNECTED,
    REGISTERED,
    CLOSED
}
