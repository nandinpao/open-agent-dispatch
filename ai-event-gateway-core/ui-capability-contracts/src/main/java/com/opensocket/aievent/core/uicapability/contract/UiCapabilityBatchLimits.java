package com.opensocket.aievent.core.uicapability.contract;

/** Separate limits remove the former generic-20/list-50 ambiguity. */
public final class UiCapabilityBatchLimits {
    public static final int GENERIC_CONTEXTS = 20;
    public static final int GENERIC_MAX_PAYLOAD_BYTES = 128 * 1024;
    public static final int GENERIC_ACTIONS_PER_CONTEXT = 20;
    public static final int LIST_ROW_CONTEXTS = 50;
    public static final int LIST_ROW_MAX_PAYLOAD_BYTES = 256 * 1024;
    public static final int LIST_ROW_ACTIONS_PER_CONTEXT = 8;

    private UiCapabilityBatchLimits() {
    }
}
