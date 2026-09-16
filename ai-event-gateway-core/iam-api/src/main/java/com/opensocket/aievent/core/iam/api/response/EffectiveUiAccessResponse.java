package com.opensocket.aievent.core.iam.api.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Effective UI projection plus the canonical grant sources that caused each visible page/action. */
public record EffectiveUiAccessResponse(
        String tenantId,
        String userId,
        Instant evaluatedAt,
        UiEntitlementResponse uiAccess,
        Map<String, List<UiAccessGrantReasonResponse>> pageReasons,
        Map<String, List<UiAccessGrantReasonResponse>> actionReasons) {
    public EffectiveUiAccessResponse {
        pageReasons = copy(pageReasons);
        actionReasons = copy(actionReasons);
    }

    private static Map<String, List<UiAccessGrantReasonResponse>> copy(
            Map<String, List<UiAccessGrantReasonResponse>> source) {
        if (source == null) return Map.of();
        return source.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    }
}
