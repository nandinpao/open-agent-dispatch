package com.opensocket.aievent.gateway.netty.cluster.sync;

import com.opensocket.aievent.gateway.netty.agent.dto.AgentResponse;
import com.opensocket.aievent.gateway.netty.agent.dto.AgentSummaryResponse;
import com.opensocket.aievent.gateway.netty.cluster.dto.ClusterNodeResponse;
import com.opensocket.aievent.gateway.netty.admin.dto.AdminEventPayload;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Local or remote Netty transport-state snapshot for Admin UI cluster aggregation.
 *
 * <p>P6.1.1 removes task summary and task detail fields; Netty cluster sync carries transport
 * runtime, Agent connection, runtime metrics, and recent Admin events only.</p>
 */
public record ClusterStateSnapshotResponse(
        String nodeId,
        String environment,
        String version,
        ClusterNodeResponse node,
        AgentSummaryResponse agentSummary,
        ClusterNodeRuntimeMetricsResponse runtimeMetrics,
        List<AgentResponse> agents,
        List<AdminEventPayload> recentEvents,
        OffsetDateTime capturedAt
) {
}
