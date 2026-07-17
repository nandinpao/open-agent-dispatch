package com.opensocket.aievent.gateway.netty.inbound;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin and internal diagnostics for the inbound event receiver / optional forwarder. */
@RestController
@RequestMapping
public class InboundEventController {

    private final InboundEventForwarder inboundEventForwarder;

    public InboundEventController(InboundEventForwarder inboundEventForwarder) {
        this.inboundEventForwarder = inboundEventForwarder;
    }

    @GetMapping({"/api/admin/inbound/metrics", "/internal/inbound/metrics"})
    public InboundEventMetrics metrics() {
        return inboundEventForwarder.metrics();
    }

    @GetMapping({"/api/admin/inbound/history", "/internal/inbound/history"})
    public InboundEventHistoryResponse history(@RequestParam(defaultValue = "100") int limit) {
        return inboundEventForwarder.history(limit);
    }
}
