package com.opensocket.aievent.gateway.netty.admin.runtime.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Runtime Agent connection response for Admin UI transport observability. */
public record RuntimeAgentConnectionsResponse(
        long totalAgents,
        long connectedAgents,
        long disconnectedAgents,
        long timeoutAgents,
        long staleAgents,
        Map<String, Long> byTransportStatus,
        Map<String, Long> byReportedStatus,
        Map<String, Long> byFreshnessStatus,
        List<RuntimeAgentConnectionResponse> agents,
        OffsetDateTime generatedAt
) {
    public static RuntimeAgentConnectionsResponse from(List<RuntimeAgentConnectionResponse> agents) {
        var safeAgents = agents == null ? List.<RuntimeAgentConnectionResponse>of() : List.copyOf(agents);
        var byTransport = safeAgents.stream()
                .collect(Collectors.groupingBy(
                        agent -> agent.transportStatus() == null ? "UNKNOWN" : agent.transportStatus(),
                        java.util.LinkedHashMap::new,
                        Collectors.counting()
                ));
        var byReported = safeAgents.stream()
                .collect(Collectors.groupingBy(
                        agent -> agent.reportedStatus() == null ? "UNKNOWN" : agent.reportedStatus(),
                        java.util.LinkedHashMap::new,
                        Collectors.counting()
                ));
        var byFreshness = safeAgents.stream()
                .collect(Collectors.groupingBy(
                        agent -> agent.freshnessStatus() == null ? "UNKNOWN" : agent.freshnessStatus(),
                        java.util.LinkedHashMap::new,
                        Collectors.counting()
                ));
        return new RuntimeAgentConnectionsResponse(
                safeAgents.size(),
                byTransport.getOrDefault("CONNECTED", 0L),
                byTransport.getOrDefault("DISCONNECTED", 0L),
                byTransport.getOrDefault("TIMEOUT", 0L),
                byFreshness.getOrDefault("STALE", 0L),
                Map.copyOf(byTransport),
                Map.copyOf(byReported),
                Map.copyOf(byFreshness),
                safeAgents,
                OffsetDateTime.now()
        );
    }
}
