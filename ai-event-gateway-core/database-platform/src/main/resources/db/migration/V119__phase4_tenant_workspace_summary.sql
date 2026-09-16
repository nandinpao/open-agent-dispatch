-- Phase 4 Tenant Workspace production landing-page summary.
-- source_hash values below are SHA-256 digests of the owning Java source file in the phase source artifact.
select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','phase4-tenant-workspace',true);

insert into permission_entry_point_inventory(
 entry_point_id,entry_point_type,application_id,owner_module,display_name,route_pattern,http_method,authority_state,
 target_permission_code,legacy_authority_type,legacy_authorities,resource_type,resource_resolver_id,exemption_reason,
 migration_deadline,manifest_revision,source_ref,source_hash,last_verified_at,created_by,updated_by)
values
('REST:GET:/api/admin/access/tenants/{tenantId}/workspace-summary','REST','control-plane-app','iam-api',
 'UnifiedAccessManagementController.tenantWorkspaceSummary','/api/admin/access/tenants/{tenantId}/workspace-summary','GET','TARGET_ONLY',
 'identity.tenant_membership.read',null,'[]'::jsonb,'UNIFIED_ACCESS_MANAGEMENT','R3_PATH_RESOURCE_RESOLVER',null,null,
 'phase4-tenant-workspace-2026-08-05',
 'ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/UnifiedAccessManagementController.java#tenantWorkspaceSummary',
 'c0f7d385642f934647125e1c10246daa756d8b728c94ab3145c6d67abcf24ea5',now(),'phase4-tenant-workspace','phase4-tenant-workspace')
on conflict (entry_point_id) do update set
 display_name=excluded.display_name,route_pattern=excluded.route_pattern,http_method=excluded.http_method,authority_state=excluded.authority_state,
 target_permission_code=excluded.target_permission_code,legacy_authority_type=null,legacy_authorities='[]'::jsonb,
 resource_type=excluded.resource_type,resource_resolver_id=excluded.resource_resolver_id,exemption_reason=null,migration_deadline=null,
 manifest_revision=excluded.manifest_revision,source_ref=excluded.source_ref,source_hash=excluded.source_hash,last_verified_at=now(),
 updated_by='phase4-tenant-workspace',version=permission_entry_point_inventory.version+1;

update rbac_policy_versions set policy_version=policy_version+1,updated_at=now(),updated_by='phase4-tenant-workspace';
