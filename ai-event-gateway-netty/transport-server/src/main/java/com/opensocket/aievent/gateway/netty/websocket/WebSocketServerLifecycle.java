package com.opensocket.aievent.gateway.netty.websocket;

import com.opensocket.aievent.gateway.netty.config.NettyServerProperties;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WebSocket gateway component for Web Socket Server Lifecycle. It supports Agent and Admin UI
 * real-time channels, including message processing, session tracking, and event broadcasting.
 */
@Component
public class WebSocketServerLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(WebSocketServerLifecycle.class);

    private final NettyServerProperties nettyServerProperties;
    private final WebSocketServerHandler webSocketServerHandler;
    private final Environment environment;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public WebSocketServerLifecycle(
            NettyServerProperties nettyServerProperties,
            WebSocketServerHandler webSocketServerHandler,
            Environment environment
    ) {
        this.nettyServerProperties = nettyServerProperties;
        this.webSocketServerHandler = webSocketServerHandler;
        this.environment = environment;
    }

    @Override
    public void start() {
        var websocket = nettyServerProperties.websocket();
        if (!websocketEnabled()) {
            log.info("Netty WebSocket server is disabled.");
            return;
        }

        if (!running.compareAndSet(false, true)) {
            return;
        }

        bossGroup = new NioEventLoopGroup(websocket.safeBossThreads());
        workerGroup = newWorkerGroup(websocket.safeWorkerThreads());

        try {
            var bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, websocket.safeSoBacklog())
                    .childOption(ChannelOption.SO_KEEPALIVE, websocket.soKeepalive())
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            channel.pipeline().addLast(new HttpServerCodec());
                            channel.pipeline().addLast(new HttpObjectAggregator(websocket.safeMaxContentLengthBytes()));
                            channel.pipeline().addLast(webSocketServerHandler);
                        }
                    });

            serverChannel = bootstrap.bind(websocket.safeHost(), websocket.safePort()).syncUninterruptibly().channel();
            log.info("Netty WebSocket server started. host={}, configuredPort={}, actualPort={}, bossThreads={}, workerThreads={}, maxContentLengthBytes={}, endpoints=[/ws/agent,/ws/admin,/api/admin/ws/status]",
                    websocket.safeHost(), websocket.safePort(), actualPort(), websocket.safeBossThreads(), websocket.safeWorkerThreads(), websocket.safeMaxContentLengthBytes());
        } catch (RuntimeException ex) {
            running.set(false);
            shutdownEventLoopGroups();
            throw ex;
        }
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
            serverChannel = null;
        }
        shutdownEventLoopGroups();
        log.info("Netty WebSocket server stopped.");
    }

    /**
     * Returns the actual bound port. This is especially useful when tests or local tools bind with
     * port 0 and let the operating system allocate an available ephemeral port.
     */
    public int actualPort() {
        if (serverChannel == null || serverChannel.localAddress() == null) {
            return -1;
        }
        if (serverChannel.localAddress() instanceof InetSocketAddress address) {
            return address.getPort();
        }
        return -1;
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return transportLifecycleAutoStartEnabled() && websocketEnabled();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 1;
    }


    private boolean transportLifecycleAutoStartEnabled() {
        String raw = environment.getProperty("netty.transport.auto-start-enabled");
        if (raw != null && !raw.isBlank()) {
            return Boolean.parseBoolean(raw.trim());
        }
        raw = environment.getProperty("netty.server.auto-start-enabled");
        if (raw != null && !raw.isBlank()) {
            return Boolean.parseBoolean(raw.trim());
        }
        return true;
    }

    private boolean websocketEnabled() {
        String raw = environment.getProperty("netty.websocket.enabled");
        if (raw != null && !raw.isBlank()) {
            return Boolean.parseBoolean(raw.trim());
        }
        return nettyServerProperties.websocket().enabled();
    }

    private EventLoopGroup newWorkerGroup(int configuredThreads) {
        return configuredThreads <= 0 ? new NioEventLoopGroup() : new NioEventLoopGroup(configuredThreads);
    }

    private void shutdownEventLoopGroups() {
        var websocket = nettyServerProperties.websocket();
        if (workerGroup != null) {
            workerGroup.shutdownGracefully(
                    websocket.safeShutdownQuietPeriodMs(),
                    websocket.safeShutdownTimeoutMs(),
                    TimeUnit.MILLISECONDS
            ).syncUninterruptibly();
            workerGroup = null;
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully(
                    websocket.safeShutdownQuietPeriodMs(),
                    websocket.safeShutdownTimeoutMs(),
                    TimeUnit.MILLISECONDS
            ).syncUninterruptibly();
            bossGroup = null;
        }
    }
}
