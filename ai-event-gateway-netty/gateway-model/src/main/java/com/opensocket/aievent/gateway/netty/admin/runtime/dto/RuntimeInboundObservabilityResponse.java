package com.opensocket.aievent.gateway.netty.admin.runtime.dto;

import com.opensocket.aievent.gateway.netty.inbound.InboundEventHistoryResponse;
import com.opensocket.aievent.gateway.netty.inbound.InboundEventMetrics;

import java.time.OffsetDateTime;

/** Inbound event receiver / optional forwarder observability response. */
public record RuntimeInboundObservabilityResponse(
        InboundEventMetrics metrics,
        InboundEventHistoryResponse history,
        OffsetDateTime generatedAt
) {
}
