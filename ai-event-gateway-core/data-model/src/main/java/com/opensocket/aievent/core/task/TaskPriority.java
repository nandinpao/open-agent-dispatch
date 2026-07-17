package com.opensocket.aievent.core.task;

import com.opensocket.aievent.core.event.EventSeverity;

public enum TaskPriority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public static TaskPriority fromSeverity(EventSeverity severity) {
        if (severity == null) {
            return MEDIUM;
        }
        return switch (severity) {
            case LOW -> LOW;
            case MEDIUM -> MEDIUM;
            case HIGH -> HIGH;
            case CRITICAL -> CRITICAL;
        };
    }
}
