package com.opensocket.aievent.gateway.netty.admin;

import java.util.Map;

/**
 * Port for publishing runtime events from the reusable gateway core to adapter layers such as
 * Admin WebSocket, REST event stores, or external event buses.
 */
public interface AdminEventPublisher {

    void broadcast(String eventType, String message, Map<String, Object> data);

    static AdminEventPublisher noop() {
        return (eventType, message, data) -> {
            // no-op adapter for unit tests or embedded runtimes without Admin UI push.
        };
    }
}
