package com.opensocket.aievent.gateway.netty.tcp.dto;

import com.opensocket.aievent.gateway.netty.tcp.TcpConnectionSnapshot;

import java.util.List;

/**
 * TCP gateway component for Tcp Connection Response. It accepts newline-delimited JSON messages,
 * binds connections to Agents, and exposes local transport connection diagnostics.
 */
public record TcpConnectionResponse(
        long activeCount,
        List<TcpConnectionSnapshot> connections
) {
}
