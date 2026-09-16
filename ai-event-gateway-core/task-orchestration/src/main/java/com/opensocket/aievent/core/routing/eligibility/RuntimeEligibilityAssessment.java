package com.opensocket.aievent.core.routing.eligibility;

import java.util.LinkedHashMap;
import java.util.Map;

/** Canonical runtime eligibility result used by dispatch and diagnostic projections. */
public record RuntimeEligibilityAssessment(
        boolean eligible,
        String reasonCode,
        String message,
        Map<String, Object> details) {

    public RuntimeEligibilityAssessment {
        details = details == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(details));
    }

    public static RuntimeEligibilityAssessment pass(String reasonCode, String message, Map<String, Object> details) {
        return new RuntimeEligibilityAssessment(true, reasonCode, message, details);
    }

    public static RuntimeEligibilityAssessment block(String reasonCode, String message, Map<String, Object> details) {
        return new RuntimeEligibilityAssessment(false, reasonCode, message, details);
    }
}
