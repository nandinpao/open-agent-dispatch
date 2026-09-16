package com.opensocket.aievent.core.resourceaccess.contract;
import java.util.Set;
/** Comparable authorization evidence. Legacy adapters may implement LegacyAuthorizationPortV2 to provide complete scope and visibility. */
public record ShadowDecisionEvidenceV2(
        String effect,
        Set<String> effectiveScopes,
        VisibilityLevel visibility,
        String reasonCode,
        boolean contextComplete,
        String errorCode,
        String decisionId) {
    public ShadowDecisionEvidenceV2 {
        effect = required(effect,"effect");
        effectiveScopes = effectiveScopes == null ? Set.of() : Set.copyOf(effectiveScopes);
        visibility = visibility == null ? VisibilityLevel.NONE : visibility;
        reasonCode = reasonCode == null ? "" : reasonCode.trim();
        errorCode = errorCode == null ? "" : errorCode.trim();
        decisionId = decisionId == null ? "" : decisionId.trim();
    }
    private static String required(String value,String field){if(value==null||value.isBlank())throw new IllegalArgumentException(field+" is required");return value.trim();}
}
