package com.opensocket.aievent.gateway.netty.cluster.dto;

import com.opensocket.aievent.gateway.netty.cluster.ClusterNodeStatus;
import java.time.OffsetDateTime;

public record ClusterHeartbeatPayload(
        String nodeId,
        ClusterNodeStatus status,
        OffsetDateTime heartbeatAt,
        String internalToken
) {
}
