package com.opensocket.aievent.core.iam.rbac.domain;

import java.util.Objects;

public record ScopeRef(ScopeType type, String scopeId, String tenantId) {
    public ScopeRef {
        Objects.requireNonNull(type, "type");
        scopeId = normalize(scopeId);
        tenantId = normalize(tenantId);
        if (type == ScopeType.INSTANCE) {
            if (!tenantId.isEmpty()) throw new IllegalArgumentException("INSTANCE scope must not carry tenantId");
            if (scopeId.isEmpty()) scopeId = "INSTANCE";
            if (!"INSTANCE".equals(scopeId)) throw new IllegalArgumentException("INSTANCE scopeId must be INSTANCE");
        } else {
            if (tenantId.isEmpty()) throw new IllegalArgumentException("tenantId is required for tenant-owned scopes");
            if (scopeId.isEmpty()) throw new IllegalArgumentException("scopeId is required");
            if (type == ScopeType.TENANT && !scopeId.equals(tenantId)) throw new IllegalArgumentException("TENANT scopeId must equal tenantId");
        }
    }
    public static ScopeRef instance() { return new ScopeRef(ScopeType.INSTANCE, "INSTANCE", ""); }
    public static ScopeRef tenant(String tenantId) { return new ScopeRef(ScopeType.TENANT, tenantId, tenantId); }
    public static ScopeRef department(String tenantId, String departmentId) { return new ScopeRef(ScopeType.DEPARTMENT, departmentId, tenantId); }
    public static ScopeRef departmentSubtree(String tenantId, String departmentId) { return new ScopeRef(ScopeType.DEPARTMENT_SUBTREE, departmentId, tenantId); }
    public static ScopeRef group(String tenantId, String groupId) { return new ScopeRef(ScopeType.GROUP, groupId, tenantId); }
    private static String normalize(String value) { return value == null ? "" : value.trim(); }
}
