package com.opensocket.aievent.gateway.netty.cluster;

import tools.jackson.databind.ObjectMapper;
import com.opensocket.aievent.gateway.netty.config.ClusterRuntimeProperties;
import com.opensocket.aievent.gateway.netty.protocol.AiEventEnvelope;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Cluster discovery component for Cluster Udp Server Lifecycle. It manages Gateway node
 * visibility, UDP/static-peer discovery, health transitions, and Admin cluster monitoring events.
 */
@Component
public class ClusterUdpServerLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ClusterUdpServerLifecycle.class);

    private final ClusterRuntimeProperties clusterRuntimeProperties;
    private final ClusterUdpDatagramHandler datagramHandler;
    private final ObjectMapper objectMapper;
    private EventLoopGroup group;
    private Channel channel;

    public ClusterUdpServerLifecycle(
            ClusterRuntimeProperties clusterRuntimeProperties,
            ClusterUdpDatagramHandler datagramHandler,
            ObjectMapper objectMapper
    ) {
        this.clusterRuntimeProperties = clusterRuntimeProperties;
        this.datagramHandler = datagramHandler;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() throws InterruptedException {
        if (!clusterRuntimeProperties.enabled() || !clusterRuntimeProperties.udpBroadcastEnabled()) {
            log.info("UDP cluster discovery is disabled. mode={}", clusterRuntimeProperties.discoveryMode());
            return;
        }

        group = new NioEventLoopGroup(1);
        var bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.SO_BROADCAST, true)
                .option(ChannelOption.SO_REUSEADDR, true)
                .handler(new ChannelInitializer<NioDatagramChannel>() {
                    @Override
                    protected void initChannel(NioDatagramChannel channel) {
                        channel.pipeline().addLast(datagramHandler);
                    }
                });

        channel = bootstrap.bind(clusterRuntimeProperties.udpHost(), clusterRuntimeProperties.udpPort()).sync().channel();
        log.info("UDP cluster discovery started on {}:{} and broadcasting to {}:{}",
                clusterRuntimeProperties.udpHost(), clusterRuntimeProperties.udpPort(),
                clusterRuntimeProperties.broadcastHost(), clusterRuntimeProperties.broadcastPort());
    }

    public boolean send(AiEventEnvelope<?> envelope) {
        if (!clusterRuntimeProperties.enabled() || !clusterRuntimeProperties.udpBroadcastEnabled()) {
            return false;
        }
        if (channel == null || !channel.isActive()) {
            return false;
        }

        try {
            var json = objectMapper.writeValueAsString(envelope);
            var recipient = new InetSocketAddress(clusterRuntimeProperties.broadcastHost(), clusterRuntimeProperties.broadcastPort());
            var content = Unpooled.copiedBuffer(json, StandardCharsets.UTF_8);
            channel.writeAndFlush(new DatagramPacket(content, recipient));
            return true;
        } catch (Exception ex) {
            log.warn("Failed to send UDP cluster discovery message. messageType={}, reason={}", envelope.messageType(), ex.getMessage());
            return false;
        }
    }

    @PreDestroy
    public void stop() {
        if (channel != null) {
            channel.close();
        }
        if (group != null) {
            group.shutdownGracefully();
        }
    }
}
