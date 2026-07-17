package com.opensocket.aievent.core.routing.governance;

/** Platform-level operation semantics. Values are source-system agnostic. */
public enum DispatchOperation {
    READ(false),
    ANALYZE(false),
    PROPOSE(false),
    EXECUTE(true),
    REMEDIATE(true),
    APPROVE(true);

    private final boolean effectful;

    DispatchOperation(boolean effectful) {
        this.effectful = effectful;
    }

    public boolean isEffectful() {
        return effectful;
    }
}
