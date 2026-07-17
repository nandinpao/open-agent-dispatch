package com.opensocket.aievent.gateway.netty.cluster;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.opensocket.aievent.gateway.netty.cluster.dto.ClusterHelloPayload;
import com.opensocket.aievent.gateway.netty.cluster.dto.ClusterHeartbeatPayload;
import com.opensocket.aievent.gateway.netty.protocol.AiEventEnvelope;
import com.opensocket.aievent.gateway.netty.protocol.MessageType;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Cluster discovery component for Cluster Udp Datagram Handler. It manages Gateway node
 * visibility, UDP/static-peer discovery, health transitions, and Admin cluster monitoring events.
 */
@Component
public class ClusterUdpDatagramHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private static final Logger log = LoggerFactory.getLogger(ClusterUdpDatagramHandler.class);

    private final ObjectMapper objectMapper;
    private final ClusterDiscoveryService clusterDiscoveryService;

    public ClusterUdpDatagramHandler(ObjectMapper objectMapper, ClusterDiscoveryService clusterDiscoveryService) {
        this.objectMapper = objectMapper;
        this.clusterDiscoveryService = clusterDiscoveryService;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, DatagramPacket packet) {
        var message = packet.content().toString(StandardCharsets.UTF_8);
        var remoteAddress = packet.sender() == null ? "unknown" : packet.sender().toString();

        try {
            var envelope = objectMapper.readValue(
                    message,
                    new TypeReference<AiEventEnvelope<JsonNode>>() {
                    }
            );

            if (envelope.messageType() == MessageType.CLUSTER_HELLO && envelope.payload() != null) {
                var payload = objectMapper.convertValue(envelope.payload(), ClusterHelloPayload.class);
                if (clusterDiscoveryService.isTrustedToken(payload.internalToken(), remoteAddress)) {
                    clusterDiscoveryService.handleHello(payload, remoteAddress);
                }
                return;
            }

            if (envelope.messageType() == MessageType.CLUSTER_HEARTBEAT && envelope.payload() != null) {
                var payload = objectMapper.convertValue(envelope.payload(), ClusterHeartbeatPayload.class);
                if (clusterDiscoveryService.isTrustedToken(payload.internalToken(), remoteAddress)) {
                    clusterDiscoveryService.handleHeartbeat(payload, remoteAddress);
                }
            }
        } catch (Exception ex) {
            log.warn("Ignored invalid UDP cluster datagram from {}. reason={}", remoteAddress, ex.getMessage());
        }
    }
}
