package com.opensocket.aievent.gateway.netty.admin;

import com.opensocket.aievent.gateway.netty.admin.dto.GatewayStatusResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin API component for Gateway Status Controller. It aggregates gateway, agent connection, cluster,
 * and event-store data for operational dashboards.
 */
@RestController
@RequestMapping("/api/gateway")
public class GatewayStatusController {

    private final GatewayStatusService gatewayStatusService;

    public GatewayStatusController(GatewayStatusService gatewayStatusService) {
        this.gatewayStatusService = gatewayStatusService;
    }

    @GetMapping("/status")
    public GatewayStatusResponse status() {
        return gatewayStatusService.getStatus();
    }
}
