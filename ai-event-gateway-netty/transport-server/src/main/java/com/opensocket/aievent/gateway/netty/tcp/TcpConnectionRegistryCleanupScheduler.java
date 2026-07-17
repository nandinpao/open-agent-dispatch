package com.opensocket.aievent.gateway.netty.tcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Periodically prunes TCP closed-connection history so long-running Agent reconnect or load tests
 * do not accumulate unbounded CLOSED records in memory.
 */
@Component
public class TcpConnectionRegistryCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(TcpConnectionRegistryCleanupScheduler.class);

    private final TcpConnectionRegistry connectionRegistry;
    private final boolean enabled;
    private final long closedHistoryTtlMs;
    private final int maxClosedHistory;

    public TcpConnectionRegistryCleanupScheduler(
            TcpConnectionRegistry connectionRegistry,
            @Value("${netty.tcp.closed-connection-cleanup-enabled:true}") boolean enabled,
            @Value("${netty.tcp.closed-connection-history-ttl-ms:600000}") long closedHistoryTtlMs,
            @Value("${netty.tcp.max-closed-connection-history:2000}") int maxClosedHistory
    ) {
        this.connectionRegistry = connectionRegistry;
        this.enabled = enabled;
        this.closedHistoryTtlMs = Math.max(0L, closedHistoryTtlMs);
        this.maxClosedHistory = Math.max(0, maxClosedHistory);
    }

    @Scheduled(
            fixedDelayString = "${netty.tcp.closed-connection-cleanup-interval-ms:60000}",
            initialDelayString = "${netty.tcp.closed-connection-cleanup-initial-delay-ms:60000}"
    )
    public void cleanup() {
        if (!enabled) {
            return;
        }
        var result = connectionRegistry.cleanupClosedConnections(
                Duration.ofMillis(closedHistoryTtlMs),
                maxClosedHistory
        );
        if (result.totalRemoved() > 0) {
            log.debug(
                    "TCP closed connection history pruned. before={}, removedByTtl={}, removedByLimit={}, after={}",
                    result.closedHistoryBefore(),
                    result.removedByTtl(),
                    result.removedByLimit(),
                    result.closedHistoryAfter()
            );
        }
    }
}
