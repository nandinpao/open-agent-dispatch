package com.opensocket.aievent.gateway.netty.admin.runtime.dto;

import com.opensocket.aievent.gateway.netty.tcp.TcpConnectionSnapshot;
import com.opensocket.aievent.gateway.netty.tcp.TcpConnectionState;
import com.opensocket.aievent.gateway.netty.websocket.WebSocketClientType;
import com.opensocket.aievent.gateway.netty.websocket.WebSocketSessionSnapshot;
import com.opensocket.aievent.gateway.netty.websocket.WebSocketSessionState;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Transport connection snapshot for Admin Runtime Observability. */
public record RuntimeConnectionSummaryResponse(
        long tcpActiveConnections,
        long tcpRegisteredAgentConnections,
        long websocketActiveSessions,
        long websocketAgentSessions,
        long websocketAdminSessions,
        long totalTransportConnections,
        Map<String, Long> tcpByState,
        Map<String, Long> websocketByState,
        List<TcpConnectionSnapshot> tcpConnections,
        List<WebSocketSessionSnapshot> websocketSessions,
        OffsetDateTime generatedAt
) {
    public static RuntimeConnectionSummaryResponse from(
            List<TcpConnectionSnapshot> tcpConnections,
            List<WebSocketSessionSnapshot> websocketSessions
    ) {
        var safeTcp = tcpConnections == null ? List.<TcpConnectionSnapshot>of() : List.copyOf(tcpConnections);
        var safeWs = websocketSessions == null ? List.<WebSocketSessionSnapshot>of() : List.copyOf(websocketSessions);
        long tcpActive = safeTcp.stream().filter(connection -> connection.state() != TcpConnectionState.CLOSED).count();
        long tcpRegistered = safeTcp.stream()
                .filter(connection -> connection.state() != TcpConnectionState.CLOSED)
                .filter(connection -> connection.agentId() != null && !connection.agentId().isBlank())
                .count();
        long wsActive = safeWs.stream().filter(session -> session.state() != WebSocketSessionState.CLOSED).count();
        long wsAgent = safeWs.stream()
                .filter(session -> session.state() != WebSocketSessionState.CLOSED)
                .filter(session -> session.clientType() == WebSocketClientType.AGENT)
                .count();
        long wsAdmin = safeWs.stream()
                .filter(session -> session.state() != WebSocketSessionState.CLOSED)
                .filter(session -> session.clientType() == WebSocketClientType.ADMIN)
                .count();
        return new RuntimeConnectionSummaryResponse(
                tcpActive,
                tcpRegistered,
                wsActive,
                wsAgent,
                wsAdmin,
                tcpActive + wsActive,
                groupTcpByState(safeTcp),
                groupWebSocketByState(safeWs),
                safeTcp,
                safeWs,
                OffsetDateTime.now()
        );
    }

    private static Map<String, Long> groupTcpByState(List<TcpConnectionSnapshot> connections) {
        return connections.stream()
                .collect(Collectors.groupingBy(
                        connection -> connection.state() == null ? "UNKNOWN" : connection.state().name(),
                        java.util.LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

    private static Map<String, Long> groupWebSocketByState(List<WebSocketSessionSnapshot> sessions) {
        return sessions.stream()
                .collect(Collectors.groupingBy(
                        session -> session.state() == null ? "UNKNOWN" : session.state().name(),
                        java.util.LinkedHashMap::new,
                        Collectors.counting()
                ));
    }
}
