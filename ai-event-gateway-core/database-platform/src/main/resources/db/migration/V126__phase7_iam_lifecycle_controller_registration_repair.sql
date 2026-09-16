-- Phase 7 Build Fix 15: make Tenant lifecycle routes register deterministically.
-- The canonical onboarding route is exact and has no trailing-slash dependency. The lifecycle
-- and invitation controllers are property-gated and constructor-wired rather than conditionally
-- omitted by @ConditionalOnBean processing order.
select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','phase7-build-fix15',true);
select set_config('app.current_audit_reason','Refresh IAM lifecycle entry-point evidence after deterministic controller registration repair',true);

update permission_entry_point_inventory
set source_hash='b1636d3d864cf0efc440221e99f5b529230085fd4911dd996d1a98af5b8abb4d',
    manifest_revision='phase7-build-fix15-lifecycle-controller-registration-2026-08-06',
    last_verified_at=now(),
    updated_at=now(),
    updated_by='phase7-build-fix15',
    version=version+1
where entry_point_id='REST:POST:/api/admin/access/tenants/{tenantId}/user-onboarding'
  and route_pattern='/api/admin/access/tenants/{tenantId}/user-onboarding'
  and http_method='POST';

update permission_entry_point_inventory
set source_hash='02526e6ed5b0fa3378ab4725e49233a12bfd4aef26895a62bd6af9865560b4de',
    manifest_revision='phase7-build-fix15-lifecycle-controller-registration-2026-08-06',
    last_verified_at=now(),
    updated_at=now(),
    updated_by='phase7-build-fix15',
    version=version+1
where entry_point_id in (
    'REST:GET:/api/admin/access/tenants/{tenantId}/users/{userId}/invitation',
    'REST:POST:/api/admin/access/tenants/{tenantId}/users/{userId}/invitation/resend',
    'REST:POST:/api/admin/access/tenants/{tenantId}/users/{userId}/invitation/revoke'
);

update rbac_policy_versions
set policy_version=policy_version+1,
    updated_at=now(),
    updated_by='phase7-build-fix15';
