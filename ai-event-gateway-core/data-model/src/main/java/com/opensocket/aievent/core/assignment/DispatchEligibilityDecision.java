package com.opensocket.aievent.core.assignment;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record DispatchEligibilityDecision(
        DispatchEligibilityStatus status,
        boolean eligible,
        String agentId,
        String gatewayNodeId,
        String sessionId,
        String siteId,
        String reason,
        Map<String, Object> facts,
        OffsetDateTime evaluatedAt) {

    public static DispatchEligibilityDecision eligible(String agentId,
                                                       String gatewayNodeId,
                                                       String sessionId,
                                                       String siteId,
                                                       String reason,
                                                       Map<String, Object> facts,
                                                       OffsetDateTime evaluatedAt) {
        return new DispatchEligibilityDecision(DispatchEligibilityStatus.ELIGIBLE, true, agentId, gatewayNodeId,
                sessionId, siteId, reason, safeFacts(facts), evaluatedAt);
    }

    public static DispatchEligibilityDecision rejected(DispatchEligibilityStatus status,
                                                       String agentId,
                                                       String gatewayNodeId,
                                                       String sessionId,
                                                       String siteId,
                                                       String reason,
                                                       Map<String, Object> facts,
                                                       OffsetDateTime evaluatedAt) {
        if (status == DispatchEligibilityStatus.ELIGIBLE) {
            throw new IllegalArgumentException("rejected eligibility cannot use ELIGIBLE status");
        }
        return new DispatchEligibilityDecision(status, false, agentId, gatewayNodeId, sessionId, siteId,
                reason, safeFacts(facts), evaluatedAt);
    }

    private static Map<String, Object> safeFacts(Map<String, Object> facts) {
        return facts == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(facts));
    }
}
