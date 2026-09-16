-- Phase 5: Enterprise Organization & Bulk Operations.
-- Registers the server-owned People bulk command and refreshes Unified Access controller source evidence.
select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','phase5-enterprise-bulk',true);

insert into permission_entry_point_inventory(
 entry_point_id,entry_point_type,application_id,owner_module,display_name,route_pattern,http_method,authority_state,
 target_permission_code,legacy_authority_type,legacy_authorities,resource_type,resource_resolver_id,exemption_reason,
 migration_deadline,manifest_revision,source_ref,source_hash,last_verified_at,created_by,updated_by)
values
('REST:POST:/api/admin/access/tenants/{tenantId}/people/bulk-actions','REST','control-plane-app','iam-api','UnifiedAccessManagementController.peopleBulkAction','/api/admin/access/tenants/{tenantId}/people/bulk-actions','POST','TARGET_ONLY','identity.user.read',null,'[]'::jsonb,'UNIFIED_ACCESS_MANAGEMENT','R3_PATH_RESOURCE_RESOLVER',null,null,'phase5-enterprise-bulk-2026-08-11','ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/UnifiedAccessManagementController.java#peopleBulkAction','8d20e0770192f6f212584cc8c80b1f9169601067b88fb243982dcdd8ac7eed45',now(),'phase5-enterprise-bulk','phase5-enterprise-bulk')
on conflict (entry_point_id) do update set
 display_name=excluded.display_name,route_pattern=excluded.route_pattern,http_method=excluded.http_method,
 authority_state=excluded.authority_state,target_permission_code=excluded.target_permission_code,
 legacy_authority_type=null,legacy_authorities='[]'::jsonb,resource_type=excluded.resource_type,
 resource_resolver_id=excluded.resource_resolver_id,exemption_reason=null,migration_deadline=null,
 manifest_revision=excluded.manifest_revision,source_ref=excluded.source_ref,source_hash=excluded.source_hash,
 last_verified_at=now(),updated_by='phase5-enterprise-bulk',version=permission_entry_point_inventory.version+1;

-- The inventory hashes whole controller source files. Keep prior canonical Unified Access entries aligned
-- with the controller that now also owns the server-side bulk command.
update permission_entry_point_inventory
set source_hash='8d20e0770192f6f212584cc8c80b1f9169601067b88fb243982dcdd8ac7eed45',
    manifest_revision='phase5-enterprise-bulk-2026-08-11',last_verified_at=now(),
    updated_by='phase5-enterprise-bulk',version=version+1
where source_ref like 'ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/UnifiedAccessManagementController%';

update rbac_policy_versions
set policy_version=policy_version+1,updated_at=now(),updated_by='phase5-enterprise-bulk';
