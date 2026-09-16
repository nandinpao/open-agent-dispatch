package com.opensocket.aievent.core.uicapability.contract;

/** Numeric wire limits shared with JavaScript clients. */
public final class UiWireNumbers {
    public static final long MAX_JSON_SAFE_INTEGER = 9_007_199_254_740_991L;

    private UiWireNumbers() {
    }

    public static long requireSafeNonNegative(long value, String field) {
        if (value < 0 || value > MAX_JSON_SAFE_INTEGER) {
            throw new IllegalArgumentException(field + " must be between 0 and " + MAX_JSON_SAFE_INTEGER);
        }
        return value;
    }

    public static Long requireSafeNonNegative(Long value, String field) {
        return value == null ? null : requireSafeNonNegative(value.longValue(), field);
    }
}
