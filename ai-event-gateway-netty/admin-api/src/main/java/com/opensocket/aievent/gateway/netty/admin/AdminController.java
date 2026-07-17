package com.opensocket.aievent.gateway.netty.admin;

import com.opensocket.aievent.gateway.netty.admin.dto.AdminDashboardResponse;
import com.opensocket.aievent.gateway.netty.admin.dto.AdminEventPayload;
import com.opensocket.aievent.gateway.netty.admin.dto.AdminWebSocketStatusResponse;
import com.opensocket.aievent.gateway.netty.websocket.WebSocketAdminBroadcaster;
import com.opensocket.aievent.gateway.netty.websocket.WebSocketClientType;
import com.opensocket.aievent.gateway.netty.websocket.WebSocketSessionRegistry;
import com.opensocket.aievent.gateway.netty.api.GatewayApiErrorCode;
import com.opensocket.aievent.gateway.netty.api.GatewayApiException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin API component for Admin Controller. It aggregates gateway, agent connection, cluster, and
 * event-store data for operational dashboards.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminDashboardService adminDashboardService;
    private final AdminEventStore adminEventStore;
    private final WebSocketAdminBroadcaster adminBroadcaster;
    private final WebSocketSessionRegistry webSocketSessionRegistry;

    public AdminController(
            AdminDashboardService adminDashboardService,
            AdminEventStore adminEventStore,
            WebSocketAdminBroadcaster adminBroadcaster,
            WebSocketSessionRegistry webSocketSessionRegistry
    ) {
        this.adminDashboardService = adminDashboardService;
        this.adminEventStore = adminEventStore;
        this.adminBroadcaster = adminBroadcaster;
        this.webSocketSessionRegistry = webSocketSessionRegistry;
    }

    @GetMapping("/dashboard")
    public AdminDashboardResponse dashboard() {
        return adminDashboardService.dashboard();
    }

    @GetMapping("/events")
    public List<AdminEventPayload> recentEvents(@RequestParam(defaultValue = "50") int limit) {
        return adminEventStore.recent(limit);
    }

    @GetMapping("/events/{eventId}")
    public AdminEventPayload getEvent(@PathVariable String eventId) {
        return adminEventStore.findById(eventId)
                .orElseThrow(() -> new GatewayApiException(GatewayApiErrorCode.NOT_FOUND, "Admin event not found: " + eventId));
    }

    @GetMapping("/websocket/status")
    public AdminWebSocketStatusResponse websocketStatus() {
        return new AdminWebSocketStatusResponse(
                adminBroadcaster.adminChannelCount(),
                webSocketSessionRegistry.countActiveByType(WebSocketClientType.ADMIN),
                adminEventStore.count(),
                adminEventStore.limit()
        );
    }
}
