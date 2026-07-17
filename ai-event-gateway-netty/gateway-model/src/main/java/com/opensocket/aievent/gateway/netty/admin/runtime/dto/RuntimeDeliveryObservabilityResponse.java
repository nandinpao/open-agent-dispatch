package com.opensocket.aievent.gateway.netty.admin.runtime.dto;

import com.opensocket.aievent.gateway.netty.delivery.CommandDeliveryHistoryResponse;
import com.opensocket.aievent.gateway.netty.delivery.CommandDeliveryMetrics;

import java.time.OffsetDateTime;

/** Command delivery observability response. This is transport delivery only, not task state. */
public record RuntimeDeliveryObservabilityResponse(
        CommandDeliveryMetrics metrics,
        CommandDeliveryHistoryResponse history,
        OffsetDateTime generatedAt
) {
}
