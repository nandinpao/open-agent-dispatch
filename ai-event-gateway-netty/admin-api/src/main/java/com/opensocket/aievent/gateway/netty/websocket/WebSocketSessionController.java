package com.opensocket.aievent.gateway.netty.websocket;

import com.opensocket.aievent.gateway.netty.websocket.dto.WebSocketSessionResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * WebSocket gateway component for Web Socket Session Controller. It supports Agent and Admin UI
 * real-time channels, including message processing, session tracking, and event broadcasting.
 */
@RestController
@RequestMapping("/api/websocket")
public class WebSocketSessionController {

    private final WebSocketSessionRegistry sessionRegistry;

    public WebSocketSessionController(WebSocketSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @GetMapping("/sessions")
    public WebSocketSessionResponse listSessions() {
        return new WebSocketSessionResponse(
                sessionRegistry.countActive(),
                sessionRegistry.countActiveByType(WebSocketClientType.AGENT),
                sessionRegistry.countActiveByType(WebSocketClientType.ADMIN),
                sessionRegistry.list()
        );
    }
}
