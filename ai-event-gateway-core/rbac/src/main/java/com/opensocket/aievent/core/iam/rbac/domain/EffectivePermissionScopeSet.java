package com.opensocket.aievent.core.iam.rbac.domain;

import com.opensocket.aievent.core.iam.security.contract.SecurityEpoch;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * RS1 canonical IAM authority projection for one Permission.
 *
 * <p>This is an enumeration of effective Role Binding scope ceilings, not organization membership.
 * Resource Access may narrow these ceilings and add explicit RESOURCE exceptions, but must never
 * broaden them from membership alone.</p>
 */
public record EffectivePermissionScopeSet(
        String tenantId,
        String permissionCode,
        boolean granted,
        String reasonCode,
        List<EffectivePermissionScopeGrant> grants,
        long policyVersion,
        SecurityEpoch securityEpoch,
        Instant evaluatedAt) {
    public EffectivePermissionScopeSet {
        tenantId = tenantId == null ? "" : tenantId.trim();
        permissionCode = required(permissionCode, "permissionCode");
        reasonCode = required(reasonCode, "reasonCode");
        grants = grants == null ? List.of() : List.copyOf(grants);
        if (policyVersion < 0) throw new IllegalArgumentException("policyVersion must be non-negative");
        securityEpoch = securityEpoch == null ? SecurityEpoch.ZERO : securityEpoch;
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        if (!granted && !grants.isEmpty()) throw new IllegalArgumentException("denied scope set must not contain grants");
        if (granted && grants.isEmpty()) throw new IllegalArgumentException("granted scope set requires at least one grant");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
