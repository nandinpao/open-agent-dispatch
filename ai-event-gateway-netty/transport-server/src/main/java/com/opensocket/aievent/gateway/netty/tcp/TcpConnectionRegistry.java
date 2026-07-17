package com.opensocket.aievent.gateway.netty.tcp;

import com.opensocket.aievent.gateway.netty.delivery.TransportWriteResult;
import io.netty.channel.Channel;
import org.springframework.stereotype.Component;

import java.net.SocketAddress;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * TCP gateway component for Tcp Connection Registry. It accepts newline-delimited JSON messages,
 * binds connections to Agents, and exposes local transport state.
 *
 * <p>Active TCP connections and recently closed connection history are intentionally stored in
 * separate maps. Active transport operations only consult the active map. Closed records are kept
 * as a bounded diagnostic history for Admin UI and are pruned by {@link TcpConnectionRegistryCleanupScheduler}.</p>
 */
@Component
public class TcpConnectionRegistry {

    private final Map<String, TcpConnectionRecord> activeConnections = new ConcurrentHashMap<>();
    private final Map<String, TcpConnectionRecord> closedConnectionHistory = new ConcurrentHashMap<>();

    public TcpConnectionSnapshot register(Channel channel) {
        var now = OffsetDateTime.now();
        var connectionId = connectionId(channel);
        var record = new TcpConnectionRecord(
                connectionId,
                remoteAddress(channel.remoteAddress()),
                channel,
                null,
                TcpConnectionState.CONNECTED,
                now,
                now
        );
        closedConnectionHistory.remove(connectionId);
        activeConnections.put(connectionId, record);
        return record.toSnapshot();
    }

    public void touch(String connectionId) {
        var record = activeConnections.get(connectionId);
        if (record != null) {
            record.lastActiveAt = OffsetDateTime.now();
        }
    }

    public void markAgentRegistered(String connectionId, String agentId) {
        var now = OffsetDateTime.now();
        var record = activeConnections.get(connectionId);
        if (record == null) {
            record = new TcpConnectionRecord(
                    connectionId,
                    "unknown",
                    null,
                    agentId,
                    TcpConnectionState.REGISTERED,
                    now,
                    now
            );
            closedConnectionHistory.remove(connectionId);
            activeConnections.put(connectionId, record);
            return;
        }
        record.agentId = agentId;
        record.state = TcpConnectionState.REGISTERED;
        record.lastActiveAt = now;
    }

    /**
     * Moves an active connection into bounded closed-history storage. The Netty Channel reference is
     * deliberately dropped so closed records do not retain transport resources after disconnect.
     */
    public void close(String connectionId) {
        var record = activeConnections.remove(connectionId);
        if (record != null) {
            var now = OffsetDateTime.now();
            closedConnectionHistory.put(connectionId, record.closedCopy(now));
            return;
        }
        var closedRecord = closedConnectionHistory.get(connectionId);
        if (closedRecord != null) {
            closedRecord.state = TcpConnectionState.CLOSED;
            closedRecord.lastActiveAt = OffsetDateTime.now();
        }
    }

    public TcpConnectionSnapshot getSnapshot(String connectionId) {
        var record = activeConnections.get(connectionId);
        if (record == null) {
            record = closedConnectionHistory.get(connectionId);
        }
        return record == null ? null : record.toSnapshot();
    }

    public String getAgentId(String connectionId) {
        var record = activeConnections.get(connectionId);
        if (record == null) {
            record = closedConnectionHistory.get(connectionId);
        }
        return record == null ? null : record.agentId;
    }

    public String getRemoteAddress(String connectionId) {
        var record = activeConnections.get(connectionId);
        if (record == null) {
            record = closedConnectionHistory.get(connectionId);
        }
        return record == null ? null : record.remoteAddress;
    }

    public boolean send(String connectionId, String message) {
        return sendWithTimeout(connectionId, message, Duration.ofSeconds(3)).status() == com.opensocket.aievent.gateway.netty.delivery.TransportWriteStatus.SENT;
    }

    public boolean isWritable(String connectionId) {
        var record = activeConnections.get(connectionId);
        return record != null
                && record.channel != null
                && record.state != TcpConnectionState.CLOSED
                && record.channel.isActive()
                && record.channel.isWritable();
    }

    public TransportWriteResult sendWithTimeout(String connectionId, String message, Duration timeout) {
        var record = activeConnections.get(connectionId);
        if (record == null || record.channel == null || record.state == TcpConnectionState.CLOSED) {
            return TransportWriteResult.notWritable("TCP connection was not found or already closed");
        }
        if (!record.channel.isActive() || !record.channel.isWritable()) {
            return TransportWriteResult.notWritable("TCP channel is not active or not writable");
        }
        try {
            var future = record.channel.writeAndFlush(message);
            var timeoutMillis = timeout == null ? 3000L : Math.max(100L, timeout.toMillis());
            if (!future.await(timeoutMillis)) {
                return TransportWriteResult.timeout("TCP channel write did not complete within " + timeoutMillis + " ms");
            }
            if (!future.isSuccess()) {
                var cause = future.cause();
                return TransportWriteResult.failed(cause == null ? "TCP channel write failed" : cause.getMessage());
            }
            record.lastActiveAt = OffsetDateTime.now();
            return TransportWriteResult.sent();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return TransportWriteResult.failed("TCP channel write was interrupted");
        } catch (Exception ex) {
            return TransportWriteResult.failed(ex.getMessage());
        }
    }


    /**
     * Closes an active TCP channel by connection id. This is used by Admin UI disconnect actions.
     */
    public boolean closeChannel(String connectionId) {
        var record = activeConnections.get(connectionId);
        if (record == null || record.channel == null) {
            return false;
        }
        record.channel.close();
        close(connectionId);
        return true;
    }

    public void remove(String connectionId) {
        activeConnections.remove(connectionId);
        closedConnectionHistory.remove(connectionId);
    }

    public List<TcpConnectionSnapshot> list() {
        return Stream.concat(activeConnections.values().stream(), closedConnectionHistory.values().stream())
                .map(TcpConnectionRecord::toSnapshot)
                .sorted(Comparator.comparing(TcpConnectionSnapshot::connectedAt))
                .toList();
    }

    public List<TcpConnectionSnapshot> listActive() {
        return activeConnections.values().stream()
                .map(TcpConnectionRecord::toSnapshot)
                .sorted(Comparator.comparing(TcpConnectionSnapshot::connectedAt))
                .toList();
    }

    public List<TcpConnectionSnapshot> listClosedHistory() {
        return closedConnectionHistory.values().stream()
                .map(TcpConnectionRecord::toSnapshot)
                .sorted(Comparator.comparing(TcpConnectionSnapshot::lastActiveAt).reversed())
                .toList();
    }

    public long countActive() {
        return activeConnections.values().stream()
                .filter(connection -> connection.state != TcpConnectionState.CLOSED)
                .count();
    }

    public long countClosedHistory() {
        return closedConnectionHistory.size();
    }

    public long countActiveByRemoteAddress(String remoteAddress) {
        return activeConnections.values().stream()
                .filter(connection -> connection.state != TcpConnectionState.CLOSED)
                .filter(connection -> remoteAddress != null && remoteAddress.equals(connection.remoteAddress))
                .count();
    }

    /**
     * Prunes recently closed TCP connection records. Active connections are never removed by this
     * method. TTL is evaluated against the close time stored in {@code lastActiveAt}; maxClosedHistory
     * then keeps only the newest closed records that remain after TTL pruning.
     */
    public TcpConnectionCleanupResult cleanupClosedConnections(Duration closedHistoryTtl, int maxClosedHistory) {
        var before = closedConnectionHistory.size();
        var ttl = closedHistoryTtl == null || closedHistoryTtl.isNegative() ? Duration.ZERO : closedHistoryTtl;
        var cutoff = OffsetDateTime.now().minus(ttl);
        var removedByTtl = 0;
        for (var entry : closedConnectionHistory.entrySet()) {
            var lastActiveAt = entry.getValue().lastActiveAt;
            if (lastActiveAt == null || !lastActiveAt.isAfter(cutoff)) {
                if (closedConnectionHistory.remove(entry.getKey(), entry.getValue())) {
                    removedByTtl++;
                }
            }
        }

        var maxHistory = Math.max(0, maxClosedHistory);
        var remainingClosed = closedConnectionHistory.values().stream()
                .sorted(Comparator.comparing(TcpConnectionRecord::lastActiveAtNullSafe))
                .toList();
        var overflow = Math.max(0, remainingClosed.size() - maxHistory);
        var removedByLimit = 0;
        for (var record : remainingClosed.subList(0, overflow)) {
            if (closedConnectionHistory.remove(record.connectionId, record)) {
                removedByLimit++;
            }
        }

        return new TcpConnectionCleanupResult(
                before,
                removedByTtl,
                removedByLimit,
                closedConnectionHistory.size()
        );
    }

    public static String connectionId(Channel channel) {
        return channel.id().asLongText();
    }

    private static String remoteAddress(SocketAddress address) {
        return address == null ? "unknown" : address.toString();
    }

    private static final class TcpConnectionRecord {
        private final String connectionId;
        private final String remoteAddress;
        private final Channel channel;
        private String agentId;
        private TcpConnectionState state;
        private final OffsetDateTime connectedAt;
        private OffsetDateTime lastActiveAt;

        private TcpConnectionRecord(
                String connectionId,
                String remoteAddress,
                Channel channel,
                String agentId,
                TcpConnectionState state,
                OffsetDateTime connectedAt,
                OffsetDateTime lastActiveAt
        ) {
            this.connectionId = connectionId;
            this.remoteAddress = remoteAddress;
            this.channel = channel;
            this.agentId = agentId;
            this.state = state;
            this.connectedAt = connectedAt;
            this.lastActiveAt = lastActiveAt;
        }

        private TcpConnectionRecord closedCopy(OffsetDateTime closedAt) {
            return new TcpConnectionRecord(
                    connectionId,
                    remoteAddress,
                    null,
                    agentId,
                    TcpConnectionState.CLOSED,
                    connectedAt,
                    closedAt
            );
        }

        private OffsetDateTime lastActiveAtNullSafe() {
            return lastActiveAt == null ? OffsetDateTime.MIN : lastActiveAt;
        }

        private TcpConnectionSnapshot toSnapshot() {
            return new TcpConnectionSnapshot(
                    connectionId,
                    remoteAddress,
                    agentId,
                    state,
                    connectedAt,
                    lastActiveAt
            );
        }
    }
}
