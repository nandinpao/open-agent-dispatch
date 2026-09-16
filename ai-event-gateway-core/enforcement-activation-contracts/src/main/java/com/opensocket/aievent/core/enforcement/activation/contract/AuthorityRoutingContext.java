package com.opensocket.aievent.core.enforcement.activation.contract;

public record AuthorityRoutingContext(
        String tenantId,
        String domain,
        String unit,
        String riskLane,
        String entryPoint,
        String cohortKey,
        String correlationId) {

    public AuthorityRoutingContext {
        AuthorityRouteKey normalized = new AuthorityRouteKey(tenantId, domain, unit, riskLane, entryPoint);
        tenantId = normalized.tenantId();
        domain = normalized.domain();
        unit = normalized.unit();
        riskLane = normalized.riskLane();
        entryPoint = normalized.entryPoint();
        if (cohortKey == null || cohortKey.isBlank()) throw new IllegalArgumentException("cohortKey must not be blank");
        cohortKey = cohortKey.trim();
        correlationId = correlationId == null ? "" : correlationId.trim();
    }

    public AuthorityRouteKey routeKey() {
        return new AuthorityRouteKey(tenantId, domain, unit, riskLane, entryPoint);
    }
}
