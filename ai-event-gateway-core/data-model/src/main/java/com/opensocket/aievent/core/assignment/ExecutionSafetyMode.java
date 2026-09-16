package com.opensocket.aievent.core.assignment;

/**
 * Stage 2 schema type reserved for the Stage 5 execution-safety gate.
 * A NULL value on a legacy assignment is intentional and must not be treated as
 * LOCAL_FENCED.
 */
public enum ExecutionSafetyMode {
    LOCAL_FENCED,
    REMOTE_NATIVE_IDEMPOTENT,
    REMOTE_UNFENCED
}
