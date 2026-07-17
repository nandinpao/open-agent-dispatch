package com.opensocket.aievent.core.http.context;

/**
 * Per-request diagnostic context. This object contains request metadata only; it is not
 * an authorization decision and must never be used as a replacement for Spring Security.
 */
public record OpenDispatchRequestContext(
        String requestId,
        String correlationId,
        String tenantId,
        String operatorId,
        String clientAddress,
        String userAgent,
        String requestKind) {

    public OpenDispatchRequestContext withBusinessContext(String businessTenantId, String businessCorrelationId) {
        return new OpenDispatchRequestContext(
                requestId,
                firstNonBlank(businessCorrelationId, correlationId),
                firstNonBlank(businessTenantId, tenantId),
                operatorId,
                clientAddress,
                userAgent,
                requestKind);
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }
}
