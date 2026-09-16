-- Phase 0-2 Admin UI / IAM / RBAC rebuild.
-- Registers object-centric Tenant, Department and Group membership entry points.

select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','phase0-2-admin-rebuild',true);

insert into permission_entry_point_inventory(
 entry_point_id,entry_point_type,application_id,owner_module,display_name,route_pattern,http_method,authority_state,
 target_permission_code,legacy_authority_type,legacy_authorities,resource_type,resource_resolver_id,exemption_reason,
 migration_deadline,manifest_revision,source_ref,source_hash,last_verified_at,created_by,updated_by)
values
('REST:GET:/api/admin/access/tenants/{tenantId}/members','REST','control-plane-app','iam-api','UnifiedAccessManagementController.tenantMembers','/api/admin/access/tenants/{tenantId}/members','GET','TARGET_ONLY','identity.tenant_membership.read',null,'[]'::jsonb,'UNIFIED_ACCESS_MANAGEMENT','R3_PATH_RESOURCE_RESOLVER',null,null,'phase0-2-rebuild-2026-08-05','ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/UnifiedAccessManagementController.java#tenantMembers','8fc69723813013d0a22781f57a1692024ee0b579aa55a97060226b8fa9fd2136',now(),'phase0-2-admin-rebuild','phase0-2-admin-rebuild'),
('REST:POST:/api/admin/access/tenants/{tenantId}/members','REST','control-plane-app','iam-api','UnifiedAccessManagementController.addTenantMember','/api/admin/access/tenants/{tenantId}/members','POST','TARGET_ONLY','identity.tenant_membership.manage',null,'[]'::jsonb,'UNIFIED_ACCESS_MANAGEMENT','R3_PATH_RESOURCE_RESOLVER',null,null,'phase0-2-rebuild-2026-08-05','ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/UnifiedAccessManagementController.java#addTenantMember','8fc69723813013d0a22781f57a1692024ee0b579aa55a97060226b8fa9fd2136',now(),'phase0-2-admin-rebuild','phase0-2-admin-rebuild'),
('REST:GET:/api/admin/access/tenants/{tenantId}/departments/{departmentId}/members','REST','control-plane-app','iam-api','UnifiedAccessManagementController.departmentMembers','/api/admin/access/tenants/{tenantId}/departments/{departmentId}/members','GET','TARGET_ONLY','identity.department.read',null,'[]'::jsonb,'UNIFIED_ACCESS_MANAGEMENT','R3_PATH_RESOURCE_RESOLVER',null,null,'phase0-2-rebuild-2026-08-05','ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/UnifiedAccessManagementController.java#departmentMembers','8fc69723813013d0a22781f57a1692024ee0b579aa55a97060226b8fa9fd2136',now(),'phase0-2-admin-rebuild','phase0-2-admin-rebuild'),
('REST:POST:/api/admin/access/tenants/{tenantId}/departments/{departmentId}/members','REST','control-plane-app','iam-api','UnifiedAccessManagementController.addDepartmentMember','/api/admin/access/tenants/{tenantId}/departments/{departmentId}/members','POST','TARGET_ONLY','identity.membership.manage',null,'[]'::jsonb,'UNIFIED_ACCESS_MANAGEMENT','R3_PATH_RESOURCE_RESOLVER',null,null,'phase0-2-rebuild-2026-08-05','ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/UnifiedAccessManagementController.java#addDepartmentMember','8fc69723813013d0a22781f57a1692024ee0b579aa55a97060226b8fa9fd2136',now(),'phase0-2-admin-rebuild','phase0-2-admin-rebuild'),
('REST:GET:/api/admin/access/tenants/{tenantId}/departments/{departmentId}/eligible-managers','REST','control-plane-app','iam-api','UnifiedAccessManagementController.eligibleDepartmentManagers','/api/admin/access/tenants/{tenantId}/departments/{departmentId}/eligible-managers','GET','TARGET_ONLY','identity.department.read',null,'[]'::jsonb,'UNIFIED_ACCESS_MANAGEMENT','R3_PATH_RESOURCE_RESOLVER',null,null,'phase0-2-rebuild-2026-08-05','ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/UnifiedAccessManagementController.java#eligibleDepartmentManagers','8fc69723813013d0a22781f57a1692024ee0b579aa55a97060226b8fa9fd2136',now(),'phase0-2-admin-rebuild','phase0-2-admin-rebuild'),
('REST:GET:/api/admin/access/tenants/{tenantId}/groups/{groupId}/members','REST','control-plane-app','iam-api','UnifiedAccessManagementController.groupMembers','/api/admin/access/tenants/{tenantId}/groups/{groupId}/members','GET','TARGET_ONLY','identity.group.read',null,'[]'::jsonb,'UNIFIED_ACCESS_MANAGEMENT','R3_PATH_RESOURCE_RESOLVER',null,null,'phase0-2-rebuild-2026-08-05','ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/UnifiedAccessManagementController.java#groupMembers','8fc69723813013d0a22781f57a1692024ee0b579aa55a97060226b8fa9fd2136',now(),'phase0-2-admin-rebuild','phase0-2-admin-rebuild'),
('REST:POST:/api/admin/access/tenants/{tenantId}/groups/{groupId}/members','REST','control-plane-app','iam-api','UnifiedAccessManagementController.addGroupMember','/api/admin/access/tenants/{tenantId}/groups/{groupId}/members','POST','TARGET_ONLY','identity.membership.manage',null,'[]'::jsonb,'UNIFIED_ACCESS_MANAGEMENT','R3_PATH_RESOURCE_RESOLVER',null,null,'phase0-2-rebuild-2026-08-05','ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/UnifiedAccessManagementController.java#addGroupMember','8fc69723813013d0a22781f57a1692024ee0b579aa55a97060226b8fa9fd2136',now(),'phase0-2-admin-rebuild','phase0-2-admin-rebuild')
on conflict (entry_point_id) do update set
 display_name=excluded.display_name,
 route_pattern=excluded.route_pattern,
 http_method=excluded.http_method,
 authority_state=excluded.authority_state,
 target_permission_code=excluded.target_permission_code,
 legacy_authority_type=null,
 legacy_authorities='[]'::jsonb,
 resource_type=excluded.resource_type,
 resource_resolver_id=excluded.resource_resolver_id,
 exemption_reason=null,
 migration_deadline=null,
 manifest_revision=excluded.manifest_revision,
 source_ref=excluded.source_ref,
 source_hash=excluded.source_hash,
 last_verified_at=now(),
 updated_by='phase0-2-admin-rebuild',
 version=permission_entry_point_inventory.version+1;

do $$
declare
  registered_count integer;
begin
  select count(*) into registered_count
  from permission_entry_point_inventory
  where manifest_revision='phase0-2-rebuild-2026-08-05';
  if registered_count <> 7 then
    raise exception 'PHASE0_2_ENTRY_POINT_INVENTORY_INCOMPLETE expected=7 actual=%', registered_count;
  end if;
end $$;

-- The route registry is deployment-scoped. Force authorization caches to observe the new entry points.
update rbac_policy_versions
set policy_version=policy_version+1,
    updated_at=now(),
    updated_by='phase0-2-admin-rebuild';
