package com.opensocket.aievent.core.iam.rbac.application.port.in;

import com.opensocket.aievent.core.iam.rbac.domain.EffectivePermissionScopeSet;

/** Enumerates every effective Role Binding scope ceiling for one principal/Permission in one Tenant. */
public interface EffectivePermissionScopePort {
    EffectivePermissionScopeSet resolve(EffectivePermissionScopeQuery query);
}
