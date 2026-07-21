package com.opensocket.aievent.core.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.opensocket.aievent.core.dispatch.flow.DispatchSimulationRequest;
import com.opensocket.aievent.core.dispatch.flow.DispatchSimulationResponse;
import com.opensocket.aievent.core.routing.RoutingSimulationService;

/** Current Dispatch no-side-effect simulation API. */
@RestController
@RequestMapping("/admin/dispatch")
public class DispatchSimulationController {
    private final RoutingSimulationService routingSimulationService;

    public DispatchSimulationController(RoutingSimulationService routingSimulationService) {
        this.routingSimulationService = routingSimulationService;
    }

    /**
     * Simulates Source Flow -> Rule/default Pool -> Runtime Eligibility -> Selection Strategy
     * without creating Task, Assignment, Delivery, ACK, or Result records.
     */
    @PostMapping("/simulate")
    public DispatchSimulationResponse simulate(@RequestBody(required = false) DispatchSimulationRequest request,
                                               @RequestParam String tenantId) {
        DispatchSimulationRequest simulationRequest = request == null ? new DispatchSimulationRequest() : request;
        simulationRequest.setTenantId(tenantId);
        return routingSimulationService.simulate(simulationRequest);
    }
}
