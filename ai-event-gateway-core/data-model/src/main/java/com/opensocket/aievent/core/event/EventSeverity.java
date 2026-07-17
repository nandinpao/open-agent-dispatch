package com.opensocket.aievent.core.event;

public enum EventSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public static EventSeverity parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return MEDIUM;
        }
        try {
            return EventSeverity.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return MEDIUM;
        }
    }

    public boolean higherThan(EventSeverity other) {
        return this.ordinal() > other.ordinal();
    }
}
