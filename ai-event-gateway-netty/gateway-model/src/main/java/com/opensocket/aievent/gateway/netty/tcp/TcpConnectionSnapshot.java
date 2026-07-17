package com.opensocket.aievent.gateway.netty.tcp;

import java.time.OffsetDateTime;

/**
 * TCP gateway component for Tcp Connection Snapshot. It accepts newline-delimited JSON messages,
 * binds connections to Agents, and exposes local transport state.
 */
public record TcpConnectionSnapshot(
        String connectionId,
        String remoteAddress,
        String agentId,
        TcpConnectionState state,
        OffsetDateTime connectedAt,
        OffsetDateTime lastActiveAt
) {
}
