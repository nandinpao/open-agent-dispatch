package com.opensocket.aievent.gateway.netty.config;

import java.util.Locale;

/**
 * Defines the production task-assignment boundary for ai-event-gateway-netty.
 *
 * <p>Netty is a transport/data-plane gateway. It is never the production assignment authority;
 * ai-event-gateway-core owns task creation, agent selection, dispatch context, retry, recovery,
 * and callback validation. This enum intentionally does not expose a standalone/demo task
 * assignment mode in runtime code.</p>
 */
public enum TaskAssignmentMode {
    CORE_ONLY,
    DISABLED;

    public static TaskAssignmentMode from(String value) {
        if (value == null || value.isBlank()) {
            return CORE_ONLY;
        }
        return switch (value.trim().replace('-', '_').toUpperCase(Locale.ROOT)) {
            case "CORE_ONLY" -> CORE_ONLY;
            case "DISABLED" -> DISABLED;
            default -> throw new IllegalArgumentException("Unsupported production gateway.task-assignment.mode: " + value);
        };
    }

    public String wireName() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
