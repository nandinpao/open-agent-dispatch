package com.opensocket.aievent.core.iam.persistence.tenant;

import java.util.Objects;

/** Server-resolved tenant/actor context used only by tenant-owned persistence adapters. */
public record IamTenantExecutionContext(String tenantId, String actorId) {
    public IamTenantExecutionContext {
        tenantId = required(tenantId, "tenantId");
        actorId = required(actorId, "actorId");
    }
    private static String required(String value, String name) {
        Objects.requireNonNull(value, name);
        String checked = value.trim();
        if (checked.isEmpty()) throw new IllegalArgumentException(name + " is required");
        return checked;
    }
}
