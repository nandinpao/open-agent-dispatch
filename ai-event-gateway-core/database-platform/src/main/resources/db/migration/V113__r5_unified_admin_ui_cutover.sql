-- R5 Unified Admin UI cutover.
-- Adds the global identity search required by the no-database-ID Tenant Membership workflow
-- and certifies the canonical /api/admin/access surface consumed by the Admin UI.

select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','r5-unified-admin-ui',true);

insert into permission_entry_point_inventory(
 entry_point_id,entry_point_type,application_id,owner_module,display_name,route_pattern,http_method,authority_state,
 target_permission_code,legacy_authority_type,legacy_authorities,resource_type,resource_resolver_id,exemption_reason,
 migration_deadline,manifest_revision,source_ref,source_hash,last_verified_at,created_by,updated_by)
values(
 'REST:GET:/api/admin/access/tenants/{tenantId}/available-users','REST','control-plane-app','iam-api',
 'UnifiedAccessManagementController.availableTenantUsers','/api/admin/access/tenants/{tenantId}/available-users','GET','TARGET_ONLY',
 'identity.tenant_membership.manage',null,'[]'::jsonb,'IAM_USER','R5_PATH_RESOURCE_RESOLVER',null,null,
 'r5-unified-admin-ui-2026-08-03',
 'ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/UnifiedAccessManagementController.java',
 'e3cb5509865a7b72b92f0fe966892851589dee721445034d2838ed46210fa731',now(),'r5-unified-admin-ui','r5-unified-admin-ui')
on conflict(entry_point_id) do update set
 entry_point_type=excluded.entry_point_type,application_id=excluded.application_id,owner_module=excluded.owner_module,
 display_name=excluded.display_name,route_pattern=excluded.route_pattern,http_method=excluded.http_method,
 authority_state='TARGET_ONLY',target_permission_code=excluded.target_permission_code,legacy_authority_type=null,
 legacy_authorities='[]'::jsonb,resource_type=excluded.resource_type,resource_resolver_id=excluded.resource_resolver_id,
 exemption_reason=null,migration_deadline=null,manifest_revision=excluded.manifest_revision,source_ref=excluded.source_ref,
 source_hash=excluded.source_hash,last_verified_at=now(),updated_at=now(),updated_by='r5-unified-admin-ui',
 version=permission_entry_point_inventory.version+1;

do $$
declare route_count integer;
begin
 -- R5 owns the 58 routes published by R4 plus the available-users route added above.
 -- Do not count the entire live /api/admin/access inventory: V111 republishes the
 -- current TARGET_ONLY manifest and later phases legitimately add more canonical routes.
 select count(*) into route_count from permission_entry_point_inventory
  where manifest_revision in (
      'r4-unified-access-management-2026-08-03',
      'r5-unified-admin-ui-2026-08-03'
    )
    and route_pattern like '/api/admin/access/%'
    and authority_state='TARGET_ONLY';
 if route_count <> 59 then
   raise exception 'R5 phase-owned Access Management inventory incomplete: expected 59 TARGET_ONLY routes, found %',route_count;
 end if;
 if exists(select 1 from permission_entry_point_inventory
   where manifest_revision in (
       'r4-unified-access-management-2026-08-03',
       'r5-unified-admin-ui-2026-08-03'
     )
     and route_pattern like '/api/admin/access/%'
     and (target_permission_code is null or resource_resolver_id is null or authority_state<>'TARGET_ONLY')) then
   raise exception 'R5 phase-owned Access Management route has incomplete canonical authorization metadata';
 end if;
 if not exists(select 1 from permission_entry_point_inventory
   where manifest_revision='r5-unified-admin-ui-2026-08-03'
     and entry_point_id='REST:GET:/api/admin/access/tenants/{tenantId}/available-users'
     and authority_state='TARGET_ONLY'
     and target_permission_code='identity.tenant_membership.manage'
     and resource_resolver_id='R5_PATH_RESOURCE_RESOLVER') then
   raise exception 'R5 available-users canonical route metadata is missing';
 end if;
end $$;

update rbac_policy_versions
 set policy_version=policy_version+1,updated_at=now(),updated_by='r5-unified-admin-ui';
insert into rbac_policy_versions(scope_type,scope_id,tenant_id,policy_version,updated_at,updated_by)
select 'TENANT',tenant_id,tenant_id,1,now(),'r5-unified-admin-ui' from tenants
on conflict(scope_type,scope_id) do update
 set policy_version=rbac_policy_versions.policy_version+1,updated_at=now(),updated_by='r5-unified-admin-ui';
