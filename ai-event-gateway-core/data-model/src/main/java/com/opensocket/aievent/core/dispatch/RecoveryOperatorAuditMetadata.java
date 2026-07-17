package com.opensocket.aievent.core.dispatch;

/**
 * P10.6 operator metadata captured for high-risk recovery governance actions.
 *
 * <p>This object is persisted inside dispatch_attempt_history.payload_json rather than
 * becoming metric tags, because operator/user/action dimensions are high-cardinality
 * and belong to audit timeline queries instead of Prometheus counters.</p>
 */
public record RecoveryOperatorAuditMetadata(
        String operatorId,
        String principal,
        String role,
        String action,
        String riskLevel,
        String requestId,
        String clientAddress,
        String userAgent,
        String confirmationPolicy
) {
    public static RecoveryOperatorAuditMetadata basic(String operatorId, String action) {
        return new RecoveryOperatorAuditMetadata(operatorId, operatorId, "UNKNOWN", action, "MODERATE", null, null, null, null);
    }
}
