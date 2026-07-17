package com.opensocket.aievent.core.routing.governance;

public enum SideEffectLevel {
    NONE(false),
    REVERSIBLE_WRITE(true),
    IRREVERSIBLE_WRITE(true);

    private final boolean effectful;

    SideEffectLevel(boolean effectful) {
        this.effectful = effectful;
    }

    public boolean isEffectful() {
        return effectful;
    }
}
