package com.opensocket.aievent.gateway.netty.websocket;

import com.opensocket.aievent.gateway.netty.agent.AgentLifecycleService;
import com.opensocket.aievent.gateway.netty.agent.AgentOnboardingTokenValidator;
import com.opensocket.aievent.gateway.netty.admin.AdminDashboardSnapshotProvider;
import com.opensocket.aievent.gateway.netty.admin.MachineAdminTokenAuthFilter;
import com.opensocket.aievent.gateway.netty.config.AdminProperties;
import com.opensocket.aievent.gateway.netty.config.ConnectionProtectionProperties;
import com.opensocket.aievent.gateway.netty.config.NettyServerProperties;
import com.opensocket.aievent.gateway.netty.protection.ConnectionRateLimiter;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.SocketAddress;
import java.util.Map;

/**
 * WebSocket gateway component for Web Socket Server Handler. It supports Agent and Admin UI
 * real-time channels, including message processing, session tracking, and event broadcasting.
 */
@Component
@ChannelHandler.Sharable
public class WebSocketServerHandler extends SimpleChannelInboundHandler<Object> {

    private static final Logger log = LoggerFactory.getLogger(WebSocketServerHandler.class);
    private static final AttributeKey<WebSocketServerHandshaker> HANDSHAKER = AttributeKey.valueOf("ws.handshaker");
    private static final AttributeKey<WebSocketClientType> CLIENT_TYPE = AttributeKey.valueOf("ws.clientType");

    private final NettyServerProperties nettyServerProperties;
    private final WebSocketSessionRegistry sessionRegistry;
    private final WebSocketMessageProcessor messageProcessor;
    private final WebSocketAdminBroadcaster adminBroadcaster;
    private final AgentLifecycleService agentLifecycleService;
    private final AdminDashboardSnapshotProvider adminDashboardSnapshotProvider;
    private final AdminProperties adminProperties;
    private final MachineAdminTokenAuthFilter machineAdminTokenAuthFilter;
    private final AgentOnboardingTokenValidator agentOnboardingTokenValidator;
    private final ConnectionProtectionProperties protectionProperties;
    private final ConnectionRateLimiter rateLimiter;

    public WebSocketServerHandler(
            NettyServerProperties nettyServerProperties,
            WebSocketSessionRegistry sessionRegistry,
            WebSocketMessageProcessor messageProcessor,
            WebSocketAdminBroadcaster adminBroadcaster,
            AgentLifecycleService agentLifecycleService,
            AdminDashboardSnapshotProvider adminDashboardSnapshotProvider,
            AdminProperties adminProperties,
            MachineAdminTokenAuthFilter machineAdminTokenAuthFilter,
            AgentOnboardingTokenValidator agentOnboardingTokenValidator,
            ConnectionProtectionProperties protectionProperties,
            ConnectionRateLimiter rateLimiter
    ) {
        this.nettyServerProperties = nettyServerProperties;
        this.sessionRegistry = sessionRegistry;
        this.messageProcessor = messageProcessor;
        this.adminBroadcaster = adminBroadcaster;
        this.agentLifecycleService = agentLifecycleService;
        this.adminDashboardSnapshotProvider = adminDashboardSnapshotProvider;
        this.adminProperties = adminProperties;
        this.machineAdminTokenAuthFilter = machineAdminTokenAuthFilter;
        this.agentOnboardingTokenValidator = agentOnboardingTokenValidator;
        this.protectionProperties = protectionProperties;
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof FullHttpRequest request) {
            handleHttpRequest(ctx, request);
            return;
        }

        if (msg instanceof WebSocketFrame frame) {
            handleWebSocketFrame(ctx, frame);
            return;
        }

        log.warn("Unsupported WebSocket server message type. type={}", msg.getClass().getName());
    }

    private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest request) {
        if (!request.decoderResult().isSuccess()) {
            sendHttpError(ctx, HttpResponseStatus.BAD_REQUEST);
            return;
        }

        var uri = new QueryStringDecoder(request.uri()).path();
        var clientType = resolveClientType(uri);
        if (clientType == WebSocketClientType.UNKNOWN) {
            sendHttpError(ctx, HttpResponseStatus.NOT_FOUND);
            return;
        }

        var remoteAddress = remoteAddress(ctx.channel().remoteAddress());
        if (!connectionQuotaAvailable(remoteAddress)) {
            log.warn("WebSocket handshake rejected by quota. remoteAddress={}, activeSessions={}, remoteActiveSessions={}",
                    remoteAddress,
                    sessionRegistry.countActive(),
                    sessionRegistry.countActiveByRemoteAddress(remoteAddress));
            sendHttpError(ctx, HttpResponseStatus.TOO_MANY_REQUESTS);
            return;
        }

        var agentHandshakeAuthenticated = isAgentHandshakeAuthenticated(request, clientType);
        if (!isHandshakeAuthorized(request, clientType, agentHandshakeAuthenticated)) {
            sendHttpError(ctx, HttpResponseStatus.UNAUTHORIZED);
            return;
        }

        var factory = new WebSocketServerHandshakerFactory(
                webSocketLocation(request),
                null,
                nettyServerProperties.websocket().safeAllowExtensions()
        );
        var handshaker = factory.newHandshaker(request);
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
            return;
        }

        ctx.channel().attr(HANDSHAKER).set(handshaker);
        ctx.channel().attr(CLIENT_TYPE).set(clientType);

        handshaker.handshake(ctx.channel(), request).addListener(future -> {
            if (!future.isSuccess()) {
                log.warn("WebSocket handshake failed. clientType={}, path={}, reason={}",
                        clientType,
                        uri,
                        future.cause() == null ? "unknown" : future.cause().getMessage());
                ctx.channel().attr(HANDSHAKER).set(null);
                ctx.channel().attr(CLIENT_TYPE).set(WebSocketClientType.UNKNOWN);
                ctx.close();
                return;
            }

            var snapshot = sessionRegistry.register(ctx.channel(), clientType, uri, agentHandshakeAuthenticated);
            log.info("WebSocket client connected. sessionId={}, clientType={}, path={}", snapshot.sessionId(), clientType, uri);

            if (clientType == WebSocketClientType.ADMIN) {
                adminBroadcaster.addAdminChannel(ctx.channel());
                adminBroadcaster.sendDirect(
                        ctx.channel(),
                        "ADMIN_SNAPSHOT",
                        "Initial dashboard snapshot for Admin UI",
                        Map.of("dashboard", adminDashboardSnapshotProvider.dashboardSnapshot())
                );
            }

            if (clientType == WebSocketClientType.AGENT) {
                adminBroadcaster.broadcast(
                        "AGENT_WS_CONNECTED",
                        "Agent WebSocket channel connected",
                        Map.of("sessionId", snapshot.sessionId(), "path", uri)
                );
            }
        });
    }

    private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
        var handshaker = ctx.channel().attr(HANDSHAKER).get();
        if (frame instanceof CloseWebSocketFrame closeFrame) {
            if (handshaker != null) {
                handshaker.close(ctx.channel(), closeFrame.retain());
            } else {
                ctx.close();
            }
            return;
        }

        if (frame instanceof PingWebSocketFrame pingFrame) {
            ctx.writeAndFlush(new PongWebSocketFrame(pingFrame.content().retain()));
            return;
        }

        if (!(frame instanceof TextWebSocketFrame textFrame)) {
            ctx.writeAndFlush(new CloseWebSocketFrame(1003, "Only text WebSocket frames are supported"));
            return;
        }

        var sessionId = WebSocketSessionRegistry.sessionId(ctx.channel());
        var remoteAddress = sessionRegistry.getRemoteAddress(sessionId);
        if (!messageQuotaAvailable(remoteAddress)) {
            log.warn("WebSocket message rejected by rate limit. sessionId={}, remoteAddress={}", sessionId, remoteAddress);
            if (protectionProperties.closeOnRateLimit()) {
                ctx.writeAndFlush(new CloseWebSocketFrame(1013, "Rate limit exceeded"));
                ctx.close();
            }
            return;
        }

        var clientType = ctx.channel().attr(CLIENT_TYPE).get();
        if (clientType == null) {
            clientType = WebSocketClientType.UNKNOWN;
        }

        var response = messageProcessor.processText(sessionId, clientType, textFrame.text());
        if (response != null) {
            ctx.writeAndFlush(new TextWebSocketFrame(response));
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        var sessionId = WebSocketSessionRegistry.sessionId(ctx.channel());
        sessionRegistry.close(sessionId);
        agentLifecycleService.markOfflineByWebSocketSession(sessionId);
        adminBroadcaster.remove(ctx.channel());
        log.info("WebSocket client disconnected. sessionId={}", sessionId);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        var sessionId = WebSocketSessionRegistry.sessionId(ctx.channel());
        log.warn("WebSocket channel exception. sessionId={}, reason={}", sessionId, cause.getMessage());
        ctx.close();
    }

    private boolean isHandshakeAuthorized(FullHttpRequest request, WebSocketClientType clientType, boolean agentHandshakeAuthenticated) {
        if (clientType == WebSocketClientType.ADMIN) {
            if (!adminProperties.machineAuthEnabled() || !adminProperties.machineWebSocketHandshakeAuthEnabled()) {
                return true;
            }
            return machineAdminTokenAuthFilter.isAuthorizedMachineToken(resolveAdminHandshakeToken(request));
        }
        if (clientType == WebSocketClientType.AGENT) {
            if (!agentOnboardingTokenValidator.authEnabled() || !agentOnboardingTokenValidator.webSocketHandshakeAuthEnabled()) {
                return true;
            }
            return agentHandshakeAuthenticated;
        }
        return true;
    }

    private boolean isAgentHandshakeAuthenticated(FullHttpRequest request, WebSocketClientType clientType) {
        if (clientType != WebSocketClientType.AGENT) {
            return false;
        }
        if (!agentOnboardingTokenValidator.authEnabled() || !agentOnboardingTokenValidator.webSocketHandshakeAuthEnabled()) {
            return false;
        }
        return agentOnboardingTokenValidator.isAuthorizedToken(resolveAgentHandshakeToken(request));
    }

    private boolean connectionQuotaAvailable(String remoteAddress) {
        if (!protectionProperties.enabled()) {
            return true;
        }
        if (sessionRegistry.countActive() >= protectionProperties.maxWebSocketSessions()) {
            return false;
        }
        return sessionRegistry.countActiveByRemoteAddress(remoteAddress)
                < protectionProperties.maxWebSocketSessionsPerRemoteAddress();
    }

    private boolean messageQuotaAvailable(String remoteAddress) {
        if (!protectionProperties.enabled()) {
            return true;
        }
        return rateLimiter.tryAcquire(
                "ws:" + (remoteAddress == null ? "unknown" : remoteAddress),
                protectionProperties.maxWebSocketMessagesPerMinutePerRemoteAddress()
        );
    }

    private String resolveAdminHandshakeToken(FullHttpRequest request) {
        var authorization = request.headers().get(HttpHeaderNames.AUTHORIZATION);
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorization.substring(7).trim();
        }
        var machineToken = request.headers().get("X-Machine-Token");
        return machineToken == null ? "" : machineToken.trim();
    }

    private String resolveAgentHandshakeToken(FullHttpRequest request) {
        var authorization = request.headers().get(HttpHeaderNames.AUTHORIZATION);
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorization.substring(7).trim();
        }
        var agentToken = request.headers().get("X-Agent-Token");
        if (agentToken != null && !agentToken.isBlank()) {
            return agentToken.trim();
        }
        var decoder = new QueryStringDecoder(request.uri());
        for (String parameter : new String[]{"agent_token", "onboarding_token", "token"}) {
            var values = decoder.parameters().get(parameter);
            if (values != null && !values.isEmpty() && values.getFirst() != null && !values.getFirst().isBlank()) {
                return values.getFirst().trim();
            }
        }
        return "";
    }

    private WebSocketClientType resolveClientType(String path) {
        return switch (path) {
            case "/ws/agent" -> WebSocketClientType.AGENT;
            case "/ws/admin", "/api/admin/ws/status", "/api/admin/runtime/stream" -> WebSocketClientType.ADMIN;
            default -> WebSocketClientType.UNKNOWN;
        };
    }

    private String webSocketLocation(FullHttpRequest request) {
        var host = firstNonBlank(
                request.headers().get("X-Forwarded-Host"),
                request.headers().get(HttpHeaderNames.HOST)
        );
        if (host == null || host.isBlank()) {
            var websocket = nettyServerProperties.websocket();
            host = websocket.safeHost() + ":" + websocket.safePort();
        }

        var forwardedProto = request.headers().get("X-Forwarded-Proto");
        var scheme = "https".equalsIgnoreCase(forwardedProto) || "wss".equalsIgnoreCase(forwardedProto) ? "wss" : "ws";
        return scheme + "://" + host + new QueryStringDecoder(request.uri()).path();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return "";
    }

    private void sendHttpError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        var response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        ctx.writeAndFlush(response).addListener(future -> ctx.close());
    }

    private String remoteAddress(SocketAddress address) {
        return address == null ? "unknown" : address.toString();
    }
}
