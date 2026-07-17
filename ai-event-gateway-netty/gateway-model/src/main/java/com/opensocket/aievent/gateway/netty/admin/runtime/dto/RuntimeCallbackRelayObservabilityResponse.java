package com.opensocket.aievent.gateway.netty.admin.runtime.dto;

import com.opensocket.aievent.gateway.netty.callback.TaskCallbackRelayMetricsHistory;
import com.opensocket.aievent.gateway.netty.callback.TaskCallbackRelayMetricsSnapshot;

import java.time.OffsetDateTime;

/** Callback relay observability response. This is Netty relay submission state, not Core task truth. */
public record RuntimeCallbackRelayObservabilityResponse(
        TaskCallbackRelayMetricsSnapshot metrics,
        TaskCallbackRelayMetricsHistory history,
        OffsetDateTime generatedAt
) {
}
