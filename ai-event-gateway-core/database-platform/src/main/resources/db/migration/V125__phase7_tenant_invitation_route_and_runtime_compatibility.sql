-- Phase 7 Build Fix 14: canonical Tenant-addressed invitation routes and stale Admin UI compatibility.
-- Root uses one INSTANCE Session and receives a request-scoped Tenant projection only from an
-- explicit {tenantId} route variable. The Next.js proxy may rewrite only the explicitly retired
-- onboarding/invitation paths into these canonical routes before the request reaches Core.
select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','phase7-build-fix14',true);
select set_config('app.current_audit_reason','Replace header-only invitation routes with canonical Tenant-addressed routes',true);

update permission_entry_point_inventory
set authority_state='EXEMPT',
    target_permission_code=null,
    legacy_authority_type=null,
    legacy_authorities='[]'::jsonb,
    resource_resolver_id='NONE',
    exemption_reason='Superseded by Tenant-addressed invitation route',
    migration_deadline=null,
    last_verified_at=now(),
    updated_at=now(),
    updated_by='phase7-build-fix14',
    version=version+1
where entry_point_id in (
    'REST:GET:/api/admin/access/users/{userId}/invitation/',
    'REST:POST:/api/admin/access/users/{userId}/invitation/resend',
    'REST:POST:/api/admin/access/users/{userId}/invitation/revoke'
) or route_pattern in (
    '/api/admin/access/users/{userId}/invitation/',
    '/api/admin/access/users/{userId}/invitation/resend',
    '/api/admin/access/users/{userId}/invitation/revoke'
);

insert into permission_entry_point_inventory(
 entry_point_id,entry_point_type,application_id,owner_module,display_name,route_pattern,http_method,authority_state,
 target_permission_code,legacy_authority_type,legacy_authorities,resource_type,resource_resolver_id,exemption_reason,
 migration_deadline,manifest_revision,source_ref,source_hash,last_verified_at,created_by,updated_by)
values
(
 'REST:GET:/api/admin/access/tenants/{tenantId}/users/{userId}/invitation',
 'REST','control-plane-app','iam-api','IamUserInvitationController.status',
 '/api/admin/access/tenants/{tenantId}/users/{userId}/invitation','GET','TARGET_ONLY',
 'identity.user.read',null,'[]'::jsonb,'IAM_USER_INVITATION','R3_PATH_RESOURCE_RESOLVER',null,
 null,'phase7-build-fix14-tenant-invitation-routes-2026-08-06',
 'ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/IamUserInvitationController.java#status',
 'b5d33d99923d640d45a0e9972a031acefa958c5f6a1566fcb754fdae3d24023c',now(),'phase7-build-fix14','phase7-build-fix14'
),
(
 'REST:POST:/api/admin/access/tenants/{tenantId}/users/{userId}/invitation/resend',
 'REST','control-plane-app','iam-api','IamUserInvitationController.resend',
 '/api/admin/access/tenants/{tenantId}/users/{userId}/invitation/resend','POST','TARGET_ONLY',
 'identity.user.update',null,'[]'::jsonb,'IAM_USER_INVITATION','R3_PATH_RESOURCE_RESOLVER',null,
 null,'phase7-build-fix14-tenant-invitation-routes-2026-08-06',
 'ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/IamUserInvitationController.java#resend',
 'b5d33d99923d640d45a0e9972a031acefa958c5f6a1566fcb754fdae3d24023c',now(),'phase7-build-fix14','phase7-build-fix14'
),
(
 'REST:POST:/api/admin/access/tenants/{tenantId}/users/{userId}/invitation/revoke',
 'REST','control-plane-app','iam-api','IamUserInvitationController.revoke',
 '/api/admin/access/tenants/{tenantId}/users/{userId}/invitation/revoke','POST','TARGET_ONLY',
 'identity.user.update',null,'[]'::jsonb,'IAM_USER_INVITATION','R3_PATH_RESOURCE_RESOLVER',null,
 null,'phase7-build-fix14-tenant-invitation-routes-2026-08-06',
 'ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/IamUserInvitationController.java#revoke',
 'b5d33d99923d640d45a0e9972a031acefa958c5f6a1566fcb754fdae3d24023c',now(),'phase7-build-fix14','phase7-build-fix14'
)
on conflict(entry_point_id) do update set
 display_name=excluded.display_name,
 route_pattern=excluded.route_pattern,
 http_method=excluded.http_method,
 authority_state=excluded.authority_state,
 target_permission_code=excluded.target_permission_code,
 legacy_authority_type=excluded.legacy_authority_type,
 legacy_authorities=excluded.legacy_authorities,
 resource_type=excluded.resource_type,
 resource_resolver_id=excluded.resource_resolver_id,
 exemption_reason=excluded.exemption_reason,
 migration_deadline=excluded.migration_deadline,
 manifest_revision=excluded.manifest_revision,
 source_ref=excluded.source_ref,
 source_hash=excluded.source_hash,
 last_verified_at=excluded.last_verified_at,
 updated_at=now(),
 updated_by=excluded.updated_by,
 version=permission_entry_point_inventory.version+1;

update rbac_policy_versions
set policy_version=policy_version+1,
    updated_at=now(),
    updated_by='phase7-build-fix14';
