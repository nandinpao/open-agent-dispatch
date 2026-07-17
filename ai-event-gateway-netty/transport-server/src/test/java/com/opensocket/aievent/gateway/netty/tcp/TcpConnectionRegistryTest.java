package com.opensocket.aievent.gateway.netty.tcp;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class TcpConnectionRegistryTest {

    @Test
    void shouldMoveClosedConnectionOutOfActiveRegistry() {
        var registry = new TcpConnectionRegistry();

        registry.markAgentRegistered("connection-001", "agent-001");
        assertThat(registry.countActive()).isEqualTo(1);

        registry.close("connection-001");

        assertThat(registry.countActive()).isZero();
        assertThat(registry.countClosedHistory()).isEqualTo(1);
        assertThat(registry.getSnapshot("connection-001").state()).isEqualTo(TcpConnectionState.CLOSED);
        assertThat(registry.listActive()).isEmpty();
        assertThat(registry.listClosedHistory()).hasSize(1);
    }

    @Test
    void shouldCleanupClosedConnectionHistoryByTtl() {
        var registry = new TcpConnectionRegistry();

        registry.markAgentRegistered("connection-001", "agent-001");
        registry.close("connection-001");

        var result = registry.cleanupClosedConnections(Duration.ZERO, 100);

        assertThat(result.closedHistoryBefore()).isEqualTo(1);
        assertThat(result.removedByTtl()).isEqualTo(1);
        assertThat(result.closedHistoryAfter()).isZero();
        assertThat(registry.countClosedHistory()).isZero();
        assertThat(registry.getSnapshot("connection-001")).isNull();
    }

    @Test
    void shouldCleanupClosedConnectionHistoryByMaxHistory() {
        var registry = new TcpConnectionRegistry();

        registry.markAgentRegistered("connection-001", "agent-001");
        registry.close("connection-001");
        registry.markAgentRegistered("connection-002", "agent-002");
        registry.close("connection-002");
        registry.markAgentRegistered("connection-003", "agent-003");
        registry.close("connection-003");

        var result = registry.cleanupClosedConnections(Duration.ofDays(1), 1);

        assertThat(result.closedHistoryBefore()).isEqualTo(3);
        assertThat(result.removedByLimit()).isEqualTo(2);
        assertThat(result.closedHistoryAfter()).isEqualTo(1);
        assertThat(registry.countClosedHistory()).isEqualTo(1);
    }
}
