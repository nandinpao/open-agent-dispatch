package com.opensocket.aievent.gateway.netty.admin.runtime.dto;

import com.opensocket.aievent.gateway.netty.admin.dto.AdminMetricsResponse;
import com.opensocket.aievent.gateway.netty.admin.dto.GatewayStatusResponse;
import com.opensocket.aievent.gateway.netty.delivery.CommandDeliveryMetrics;
import com.opensocket.aievent.gateway.netty.inbound.InboundEventMetrics;
import com.opensocket.aievent.gateway.netty.callback.TaskCallbackRelayMetricsSnapshot;

import java.time.OffsetDateTime;

/** Single Admin Runtime Observability snapshot for connection / delivery / inbound views. */
public record RuntimeSummaryResponse(
        GatewayStatusResponse gateway,
        AdminMetricsResponse runtimeMetrics,
        RuntimeConnectionSummaryResponse connections,
        RuntimeAgentConnectionsResponse agents,
        CommandDeliveryMetrics delivery,
        InboundEventMetrics inbound,
        TaskCallbackRelayMetricsSnapshot callbackRelay,
        OffsetDateTime generatedAt
) {
}
