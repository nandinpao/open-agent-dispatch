-- Phase 7 Build Fix 13: canonical Tenant-addressed user-onboarding route.
-- Root uses one INSTANCE Session and receives a request-scoped Tenant projection only from
-- an explicit {tenantId} path variable. Header-only Tenant selection must not authorize a
-- Tenant mutation because it cannot provide the R4 route projection boundary.
select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','phase7-build-fix13',true);
select set_config('app.current_audit_reason','Replace header-only Tenant onboarding with a canonical Tenant-addressed route',true);

update permission_entry_point_inventory
set authority_state='EXEMPT',
    target_permission_code=null,
    legacy_authority_type=null,
    legacy_authorities='[]'::jsonb,
    resource_resolver_id='NONE',
    exemption_reason='Superseded by /api/admin/access/tenants/{tenantId}/user-onboarding',
    migration_deadline=null,
    last_verified_at=now(),
    updated_at=now(),
    updated_by='phase7-build-fix13',
    version=version+1
where entry_point_id='REST:POST:/api/admin/access/user-onboarding/'
   or route_pattern='/api/admin/access/user-onboarding/';

insert into permission_entry_point_inventory(
 entry_point_id,entry_point_type,application_id,owner_module,display_name,route_pattern,http_method,authority_state,
 target_permission_code,legacy_authority_type,legacy_authorities,resource_type,resource_resolver_id,exemption_reason,
 migration_deadline,manifest_revision,source_ref,source_hash,last_verified_at,created_by,updated_by)
values (
 'REST:POST:/api/admin/access/tenants/{tenantId}/user-onboarding',
 'REST','control-plane-app','iam-api','IamUserLifecycleController.onboard',
 '/api/admin/access/tenants/{tenantId}/user-onboarding','POST','TARGET_ONLY',
 'identity.user.create',null,'[]'::jsonb,'IAM_USER_LIFECYCLE','R3_PATH_RESOURCE_RESOLVER',null,
 null,'phase7-build-fix13-tenant-onboarding-route-2026-08-06',
 'ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/IamUserLifecycleController.java#onboard',
 'b215ea055cfa99636569c14ac42f404bf5e2775d55512f89856cfef07a2dba73',
 now(),'phase7-build-fix13','phase7-build-fix13')
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
    updated_by='phase7-build-fix13';
