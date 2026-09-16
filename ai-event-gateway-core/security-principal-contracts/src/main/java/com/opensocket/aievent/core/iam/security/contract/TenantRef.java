package com.opensocket.aievent.core.iam.security.contract;

import java.util.Objects;

/** Explicit Instance or Tenant scope; null tenant identifiers are not used as scope markers. */
public record TenantRef(Scope scope, String tenantId) {
    public TenantRef {
        Objects.requireNonNull(scope, "scope");
        tenantId = tenantId == null ? "" : tenantId.trim();
        if (scope == Scope.TENANT && tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required for TENANT scope");
        if (scope == Scope.INSTANCE && !tenantId.isBlank()) throw new IllegalArgumentException("INSTANCE scope must not carry tenantId");
    }

    public static TenantRef instance() { return new TenantRef(Scope.INSTANCE, ""); }
    public static TenantRef tenant(String tenantId) { return new TenantRef(Scope.TENANT, tenantId); }

    public enum Scope { INSTANCE, TENANT }
}
