package com.opensocket.aievent.core.iam.rbac.application.port.in;

import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.core.iam.security.contract.SecurityEpoch;
import com.opensocket.aievent.core.iam.security.contract.TenantRef;
import java.util.Objects;

/** Request to enumerate all effective IAM Role Binding scope ceilings for one Permission. */
public record EffectivePermissionScopeQuery(
        PrincipalRef principal,
        TenantRef activeTenant,
        String permissionCode,
        SecurityEpoch presentedEpoch) {
    public EffectivePermissionScopeQuery {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(activeTenant, "activeTenant");
        if (activeTenant.scope() != TenantRef.Scope.TENANT) {
            throw new IllegalArgumentException("RS1 effective permission scope query requires an active Tenant");
        }
        if (permissionCode == null || permissionCode.isBlank()) throw new IllegalArgumentException("permissionCode is required");
        permissionCode = permissionCode.trim();
        presentedEpoch = presentedEpoch == null ? SecurityEpoch.ZERO : presentedEpoch;
    }
}
