package com.opensocket.aievent.gateway.netty.delivery.routing;

import com.opensocket.aievent.gateway.netty.delivery.CommandDeliveryRequest;
import com.opensocket.aievent.gateway.netty.delivery.CommandDeliveryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Cluster-facing command delivery router API for Admin UI and control-plane callers. */
@RestController
@RequestMapping({"/api/cluster/delivery", "/internal/cluster/delivery"})
public class ClusterDeliveryRouterController {

    private final ClusterDeliveryRouterService clusterDeliveryRouterService;

    public ClusterDeliveryRouterController(ClusterDeliveryRouterService clusterDeliveryRouterService) {
        this.clusterDeliveryRouterService = clusterDeliveryRouterService;
    }

    @PostMapping("/agents/{agentId}/commands")
    public CommandDeliveryResponse deliver(
            @PathVariable String agentId,
            @RequestBody CommandDeliveryRequest request
    ) {
        return clusterDeliveryRouterService.deliver(agentId, request);
    }

    @GetMapping("/agents/{agentId}/route")
    public DeliveryRouteDecision route(@PathVariable String agentId) {
        return clusterDeliveryRouterService.routeDecision(agentId);
    }
}
