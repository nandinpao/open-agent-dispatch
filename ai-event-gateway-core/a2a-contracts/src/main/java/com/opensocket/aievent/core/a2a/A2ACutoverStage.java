package com.opensocket.aievent.core.a2a;

/** Ordered Phase 2 migration stages. Stages may only advance one step at a time. */
public enum A2ACutoverStage {
    EXPAND,
    BACKFILL,
    SHADOW_READ,
    CUTOVER,
    LEGACY_WRITE_DISABLED,
    CONTRACT;

    public boolean legacyWritesAllowed() {
        return ordinal() < LEGACY_WRITE_DISABLED.ordinal();
    }

    public A2ACutoverStage next() {
        if (this == CONTRACT) return CONTRACT;
        return values()[ordinal() + 1];
    }
}
