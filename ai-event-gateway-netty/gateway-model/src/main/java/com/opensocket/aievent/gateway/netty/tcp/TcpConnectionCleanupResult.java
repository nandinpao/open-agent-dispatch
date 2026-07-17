package com.opensocket.aievent.gateway.netty.tcp;

/** Result of one TCP closed-connection history pruning run. */
public record TcpConnectionCleanupResult(
        int closedHistoryBefore,
        int removedByTtl,
        int removedByLimit,
        int closedHistoryAfter
) {
    public int totalRemoved() {
        return removedByTtl + removedByLimit;
    }
}
