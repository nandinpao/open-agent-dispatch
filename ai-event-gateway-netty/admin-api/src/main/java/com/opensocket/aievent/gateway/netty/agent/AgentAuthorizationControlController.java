package com.opensocket.aievent.gateway.netty.agent;

import com.opensocket.aievent.gateway.netty.authorization.AgentAuthorizationRuntimeRegistry;
import com.opensocket.aievent.gateway.netty.tcp.TcpConnectionRegistry;
import com.opensocket.aievent.gateway.netty.websocket.WebSocketSessionRegistry;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Internal Core -> Netty control API for immediate Agent authorization revocation.
 *
 * <p>Core remains the authority for Agent approval, suspension, disablement, and credential
 * revocation. This controller lets Core force the Netty runtime to close an already-authorized
 * session when that authority changes.</p>
 */
@RestController
@RequestMapping("/internal/agent-authorization")
public class AgentAuthorizationControlController {

    private final AgentAuthorizationRuntimeRegistry authorizationRuntimeRegistry;
    private final AgentLifecycleService agentLifecycleService;
    private final TcpConnectionRegistry tcpConnectionRegistry;
    private final WebSocketSessionRegistry webSocketSessionRegistry;

    public AgentAuthorizationControlController(
            AgentAuthorizationRuntimeRegistry authorizationRuntimeRegistry,
            AgentLifecycleService agentLifecycleService,
            TcpConnectionRegistry tcpConnectionRegistry,
            WebSocketSessionRegistry webSocketSessionRegistry
    ) {
        this.authorizationRuntimeRegistry = authorizationRuntimeRegistry;
        this.agentLifecycleService = agentLifecycleService;
        this.tcpConnectionRegistry = tcpConnectionRegistry;
        this.webSocketSessionRegistry = webSocketSessionRegistry;
    }

    @PostMapping("/agents/{agentId}/revoke")
    public Map<String, Object> revoke(@PathVariable String agentId) {
        var context = authorizationRuntimeRegistry.findByAgentId(agentId);
        if (context.isEmpty()) {
            return Map.of(
                    "agentId", agentId,
                    "revoked", false,
                    "message", "Agent has no authorized local session on this gateway"
            );
        }

        var authorization = context.get();
        boolean closed = false;
        if (authorization.connectionType() == ConnectionType.TCP) {
            closed = tcpConnectionRegistry.closeChannel(authorization.connectionId());
            agentLifecycleService.markOfflineByTcpConnection(authorization.connectionId());
        } else if (authorization.connectionType() == ConnectionType.WEBSOCKET) {
            closed = webSocketSessionRegistry.closeChannel(authorization.sessionId());
            agentLifecycleService.markOfflineByWebSocketSession(authorization.sessionId());
        }
        authorizationRuntimeRegistry.removeByEndpoint(
                authorization.connectionType(),
                authorization.connectionType() == ConnectionType.TCP ? authorization.connectionId() : authorization.sessionId()
        );
        return Map.of(
                "agentId", agentId,
                "revoked", true,
                "transportClosed", closed,
                "message", "Authorized Agent session was revoked on this gateway"
        );
    }
}
