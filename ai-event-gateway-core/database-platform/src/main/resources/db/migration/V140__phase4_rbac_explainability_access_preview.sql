-- Phase 4: RBAC Explainability / Access Preview.
-- Registers read-only preview/explanation endpoints in the canonical authorization inventory.
select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','phase4-rbac-explainability',true);

insert into permission_entry_point_inventory(
 entry_point_id,entry_point_type,application_id,owner_module,display_name,route_pattern,http_method,authority_state,
 target_permission_code,legacy_authority_type,legacy_authorities,resource_type,resource_resolver_id,exemption_reason,
 migration_deadline,manifest_revision,source_ref,source_hash,last_verified_at,created_by,updated_by)
values
('REST:GET:/api/admin/access/tenants/{tenantId}/roles/{roleId}/ui-access-preview','REST','control-plane-app','iam-api','UnifiedAccessManagementController.responsibilityUiAccessPreview','/api/admin/access/tenants/{tenantId}/roles/{roleId}/ui-access-preview','GET','TARGET_ONLY','identity.tenant_role.read',null,'[]'::jsonb,'ROLE','R3_PATH_RESOURCE_RESOLVER',null,null,'phase4-rbac-explainability-2026-08-11','ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/UnifiedAccessManagementController#responsibilityUiAccessPreview','51ae93e65393bef2e4543637993b449e6a78a69ad2f2de764fcf64dd969923d9',now(),'phase4-rbac-explainability','phase4-rbac-explainability'),
('REST:POST:/api/admin/access/tenants/{tenantId}/roles/{roleId}/ui-access-preview','REST','control-plane-app','iam-api','UnifiedAccessManagementController.previewResponsibilityUiAccessDraft','/api/admin/access/tenants/{tenantId}/roles/{roleId}/ui-access-preview','POST','TARGET_ONLY','identity.tenant_role.read',null,'[]'::jsonb,'ROLE','R3_PATH_RESOURCE_RESOLVER',null,null,'phase4-rbac-explainability-2026-08-11','ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/UnifiedAccessManagementController#previewResponsibilityUiAccessDraft','51ae93e65393bef2e4543637993b449e6a78a69ad2f2de764fcf64dd969923d9',now(),'phase4-rbac-explainability','phase4-rbac-explainability'),
('REST:GET:/api/admin/access/tenants/{tenantId}/users/{userId}/ui-access','REST','control-plane-app','iam-api','UnifiedAccessManagementController.effectiveUiAccess','/api/admin/access/tenants/{tenantId}/users/{userId}/ui-access','GET','TARGET_ONLY','identity.role_binding.read',null,'[]'::jsonb,'USER','R3_PATH_RESOURCE_RESOLVER',null,null,'phase4-rbac-explainability-2026-08-11','ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/UnifiedAccessManagementController#effectiveUiAccess','51ae93e65393bef2e4543637993b449e6a78a69ad2f2de764fcf64dd969923d9',now(),'phase4-rbac-explainability','phase4-rbac-explainability')
on conflict (entry_point_id) do update set
 display_name=excluded.display_name,route_pattern=excluded.route_pattern,http_method=excluded.http_method,
 authority_state=excluded.authority_state,target_permission_code=excluded.target_permission_code,
 legacy_authority_type=null,legacy_authorities='[]'::jsonb,resource_type=excluded.resource_type,
 resource_resolver_id=excluded.resource_resolver_id,exemption_reason=null,migration_deadline=null,
 manifest_revision=excluded.manifest_revision,source_ref=excluded.source_ref,source_hash=excluded.source_hash,
 last_verified_at=now(),updated_by='phase4-rbac-explainability',version=permission_entry_point_inventory.version+1;

update rbac_policy_versions
set policy_version=policy_version+1,updated_at=now(),updated_by='phase4-rbac-explainability';
