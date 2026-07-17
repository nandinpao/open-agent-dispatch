package com.opensocket.aievent.gateway.netty.tcp;

import com.opensocket.aievent.gateway.netty.agent.AgentLifecycleService;
import com.opensocket.aievent.gateway.netty.config.ConnectionProtectionProperties;
import com.opensocket.aievent.gateway.netty.protection.ConnectionRateLimiter;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.SocketAddress;

/**
 * TCP gateway component for Tcp Server Handler. It accepts newline-delimited JSON messages, binds
 * connections to Agents, and processes protocol envelopes at the transport layer.
 */
@Component
@ChannelHandler.Sharable
public class TcpServerHandler extends SimpleChannelInboundHandler<String> {

    private static final Logger log = LoggerFactory.getLogger(TcpServerHandler.class);

    private final TcpConnectionRegistry connectionRegistry;
    private final TcpMessageProcessor messageProcessor;
    private final AgentLifecycleService agentLifecycleService;
    private final ConnectionProtectionProperties protectionProperties;
    private final ConnectionRateLimiter rateLimiter;

    public TcpServerHandler(
            TcpConnectionRegistry connectionRegistry,
            TcpMessageProcessor messageProcessor,
            AgentLifecycleService agentLifecycleService,
            ConnectionProtectionProperties protectionProperties,
            ConnectionRateLimiter rateLimiter
    ) {
        this.connectionRegistry = connectionRegistry;
        this.messageProcessor = messageProcessor;
        this.agentLifecycleService = agentLifecycleService;
        this.protectionProperties = protectionProperties;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        var remoteAddress = remoteAddress(ctx.channel().remoteAddress());
        if (!connectionQuotaAvailable(remoteAddress)) {
            log.warn("TCP connection rejected by quota. remoteAddress={}, active={}, remoteActive={}",
                    remoteAddress,
                    connectionRegistry.countActive(),
                    connectionRegistry.countActiveByRemoteAddress(remoteAddress));
            ctx.close();
            return;
        }
        var snapshot = connectionRegistry.register(ctx.channel());
        log.info("TCP client connected. connectionId={}, remoteAddress={}", snapshot.connectionId(), snapshot.remoteAddress());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        var connectionId = TcpConnectionRegistry.connectionId(ctx.channel());
        var remoteAddress = connectionRegistry.getRemoteAddress(connectionId);
        if (!messageQuotaAvailable(remoteAddress)) {
            log.warn("TCP message rejected by rate limit. connectionId={}, remoteAddress={}", connectionId, remoteAddress);
            if (protectionProperties.closeOnRateLimit()) {
                ctx.close();
            }
            return;
        }
        var response = messageProcessor.processLine(connectionId, msg);
        if (response != null) {
            ctx.writeAndFlush(response + System.lineSeparator());
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        var connectionId = TcpConnectionRegistry.connectionId(ctx.channel());
        connectionRegistry.close(connectionId);
        agentLifecycleService.markOfflineByTcpConnection(connectionId);
        log.info("TCP client disconnected. connectionId={}", connectionId);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        var connectionId = TcpConnectionRegistry.connectionId(ctx.channel());
        log.warn("TCP channel exception. connectionId={}, reason={}", connectionId, cause.getMessage());
        ctx.close();
    }

    private boolean connectionQuotaAvailable(String remoteAddress) {
        if (!protectionProperties.enabled()) {
            return true;
        }
        if (connectionRegistry.countActive() >= protectionProperties.maxTcpConnections()) {
            return false;
        }
        return connectionRegistry.countActiveByRemoteAddress(remoteAddress)
                < protectionProperties.maxTcpConnectionsPerRemoteAddress();
    }

    private boolean messageQuotaAvailable(String remoteAddress) {
        if (!protectionProperties.enabled()) {
            return true;
        }
        return rateLimiter.tryAcquire(
                "tcp:" + (remoteAddress == null ? "unknown" : remoteAddress),
                protectionProperties.maxTcpMessagesPerMinutePerRemoteAddress()
        );
    }

    private String remoteAddress(SocketAddress address) {
        return address == null ? "unknown" : address.toString();
    }
}
