package com.opensocket.aievent.gateway.netty.admin.runtime.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Local Netty runtime SLO snapshot for transport observability.
 *
 * <p>This is gateway-local runtime health only: delivery backlog/failure and callback relay
 * submission health. Core remains the authoritative task/dispatch/callback state.</p>
 */
public record RuntimeSloSnapshotResponse(
        String status,
        Map<String, Object> deliveryBacklog,
        Map<String, Object> gatewayRelayBacklog,
        Map<String, Object> callbackRelay,
        List<Map<String, Object>> alerts,
        OffsetDateTime generatedAt
) {
}
