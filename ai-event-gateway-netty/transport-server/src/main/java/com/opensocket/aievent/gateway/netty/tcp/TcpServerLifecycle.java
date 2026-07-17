package com.opensocket.aievent.gateway.netty.tcp;

import com.opensocket.aievent.gateway.netty.config.NettyServerProperties;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TCP gateway component for Tcp Server Lifecycle. It accepts newline-delimited JSON messages,
 * binds connections to Agents, and forwards protocol envelopes through transport-only processors.
 */
@Component
public class TcpServerLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TcpServerLifecycle.class);

    private final NettyServerProperties nettyServerProperties;
    private final TcpServerHandler tcpServerHandler;
    private final Environment environment;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public TcpServerLifecycle(
            NettyServerProperties nettyServerProperties,
            TcpServerHandler tcpServerHandler,
            Environment environment
    ) {
        this.nettyServerProperties = nettyServerProperties;
        this.tcpServerHandler = tcpServerHandler;
        this.environment = environment;
    }

    @Override
    public void start() {
        var tcp = nettyServerProperties.tcp();
        if (!tcpEnabled()) {
            log.info("Netty TCP server is disabled.");
            return;
        }

        if (!running.compareAndSet(false, true)) {
            return;
        }

        bossGroup = new NioEventLoopGroup(tcp.safeBossThreads());
        workerGroup = newWorkerGroup(tcp.safeWorkerThreads());

        try {
            var bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, tcp.safeSoBacklog())
                    .childOption(ChannelOption.SO_KEEPALIVE, tcp.soKeepalive())
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            channel.pipeline().addLast(new DelimiterBasedFrameDecoder(tcp.safeMaxFrameLengthBytes(), Delimiters.lineDelimiter()));
                            channel.pipeline().addLast(new StringDecoder(StandardCharsets.UTF_8));
                            channel.pipeline().addLast(new StringEncoder(StandardCharsets.UTF_8));
                            channel.pipeline().addLast(tcpServerHandler);
                        }
                    });

            serverChannel = bootstrap.bind(tcp.safeHost(), tcp.safePort()).syncUninterruptibly().channel();
            log.info("Netty TCP server started. host={}, configuredPort={}, actualPort={}, bossThreads={}, workerThreads={}, maxFrameLengthBytes={}",
                    tcp.safeHost(), tcp.safePort(), actualPort(), tcp.safeBossThreads(), tcp.safeWorkerThreads(), tcp.safeMaxFrameLengthBytes());
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
        log.info("Netty TCP server stopped.");
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
        return transportLifecycleAutoStartEnabled() && tcpEnabled();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
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

    private boolean tcpEnabled() {
        String raw = environment.getProperty("netty.tcp.enabled");
        if (raw != null && !raw.isBlank()) {
            return Boolean.parseBoolean(raw.trim());
        }
        return nettyServerProperties.tcp().enabled();
    }

    private EventLoopGroup newWorkerGroup(int configuredThreads) {
        return configuredThreads <= 0 ? new NioEventLoopGroup() : new NioEventLoopGroup(configuredThreads);
    }

    private void shutdownEventLoopGroups() {
        var tcp = nettyServerProperties.tcp();
        if (workerGroup != null) {
            workerGroup.shutdownGracefully(
                    tcp.safeShutdownQuietPeriodMs(),
                    tcp.safeShutdownTimeoutMs(),
                    TimeUnit.MILLISECONDS
            ).syncUninterruptibly();
            workerGroup = null;
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully(
                    tcp.safeShutdownQuietPeriodMs(),
                    tcp.safeShutdownTimeoutMs(),
                    TimeUnit.MILLISECONDS
            ).syncUninterruptibly();
            bossGroup = null;
        }
    }
}
