package com.opensocket.aievent.gateway.netty.delivery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal transport API used by a future Core / Control Plane to ask this Netty gateway to deliver
 * a command to a locally connected Agent. This endpoint performs no task creation, routing decision,
 * or business state mutation.
 */
@RestController
@RequestMapping("/internal/delivery")
public class CommandDeliveryController {
    private static final Logger log = LoggerFactory.getLogger(CommandDeliveryController.class);

    private final CommandDeliveryService commandDeliveryService;

    public CommandDeliveryController(CommandDeliveryService commandDeliveryService) {
        this.commandDeliveryService = commandDeliveryService;
    }

    @PostMapping("/agents/{agentId}/commands")
    public CommandDeliveryResponse deliver(
            @PathVariable String agentId,
            @RequestBody CommandDeliveryRequest request
    ) {
        log.info("netty_delivery_request_received agentId={} commandId={} messageType={} traceId={}",
                safe(agentId), request == null ? "-" : safe(request.commandId()), request == null ? "-" : request.messageType(),
                request == null ? "-" : safe(request.traceId()));
        CommandDeliveryResponse response = commandDeliveryService.deliverToAgent(agentId, request);
        log.info("netty_delivery_request_completed agentId={} commandId={} deliveryStatus={} message={}",
                safe(agentId), response == null ? "-" : safe(response.commandId()), response == null ? "-" : response.deliveryStatus(),
                response == null ? "-" : safe(response.message()));
        return response;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    @GetMapping("/metrics")
    public CommandDeliveryMetrics metrics() {
        return commandDeliveryService.metrics();
    }

    @GetMapping("/history")
    public CommandDeliveryHistoryResponse history(@RequestParam(defaultValue = "100") int limit) {
        return commandDeliveryService.history(limit);
    }
}
