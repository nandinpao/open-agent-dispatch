package com.opensocket.aievent.gateway.netty.inbound;

import com.opensocket.aievent.gateway.netty.agent.ConnectionType;
import com.opensocket.aievent.gateway.netty.config.InboundEventCategory;
import com.opensocket.aievent.gateway.netty.protocol.MessageType;
import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;

/** Envelope sent by Netty to the optional Core forward endpoint. */
public record InboundEventEnvelope(
        String inboundId,
        String messageId,
        MessageType messageType,
        String eventType,
        InboundEventCategory category,
        String source,
        String target,
        String gatewayNodeId,
        String siteId,
        String region,
        String zone,
        ConnectionType connectionType,
        String connectionId,
        String agentId,
        OffsetDateTime sourceTimestamp,
        OffsetDateTime receivedAt,
        JsonNode payload
) {
}
