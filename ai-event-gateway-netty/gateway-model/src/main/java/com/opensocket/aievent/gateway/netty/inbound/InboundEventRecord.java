package com.opensocket.aievent.gateway.netty.inbound;

import com.opensocket.aievent.gateway.netty.agent.ConnectionType;
import com.opensocket.aievent.gateway.netty.config.InboundEventCategory;
import com.opensocket.aievent.gateway.netty.protocol.MessageType;

import java.time.OffsetDateTime;

/**
 * Bounded, local inbound event diagnostic record.
 *
 * <p>Payload is intentionally not stored here. This is a transport diagnostic view, not an event
 * store, task store, audit database, or Core state store.</p>
 */
public record InboundEventRecord(
        String inboundId,
        String messageId,
        MessageType messageType,
        String eventType,
        InboundEventCategory category,
        String source,
        String target,
        String gatewayNodeId,
        String siteId,
        ConnectionType connectionType,
        String connectionId,
        String agentId,
        InboundForwardStatus status,
        OffsetDateTime receivedAt,
        OffsetDateTime completedAt,
        long durationMillis,
        String message
) {
}
