-- P2 Fix11: register the bounded DUAL-mode Legacy ADMIN -> IAM account migration bridge
-- and activate an exact control-plane permission manifest. The bridge remains a cutover
-- blocker because every entry is explicitly LEGACY_ONLY and must be removed before IAM_ONLY.
select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','p2-fix11-account-bridge-migration',true);

insert into permission_entry_point_inventory(
 entry_point_id,entry_point_type,application_id,owner_module,display_name,route_pattern,http_method,
 authority_state,target_permission_code,legacy_authority_type,legacy_authorities,resource_type,resource_resolver_id,
 exemption_reason,migration_deadline,manifest_revision,source_ref,source_hash,last_verified_at,created_by,updated_by)
values
('REST:GET:/api/legacy-auth/iam-accounts','REST','control-plane-app','control-plane-app',
 'LegacyIamAccountBridgeController.search','/api/legacy-auth/iam-accounts','GET','LEGACY_ONLY',
 null,'SPRING_ROLE','["ROLE_ADMIN"]'::jsonb,'IDENTITY','ACTIVE_TENANT',null,'2026-12-31'::date,
 'p2-fix11-account-bridge-source-inventory-2026-08-02',
 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/identity/api/LegacyIamAccountBridgeController.java#search',
 'b13ade43fa6d0f9818b070b3b67558e2a9bd2d364a030532e821bc15e56c6d52',now(),
 'p2-fix11-account-bridge-migration','p2-fix11-account-bridge-migration'),
('REST:POST:/api/legacy-auth/iam-accounts','REST','control-plane-app','control-plane-app',
 'LegacyIamAccountBridgeController.create','/api/legacy-auth/iam-accounts','POST','LEGACY_ONLY',
 null,'SPRING_ROLE','["ROLE_ADMIN"]'::jsonb,'IDENTITY','ACTIVE_TENANT',null,'2026-12-31'::date,
 'p2-fix11-account-bridge-source-inventory-2026-08-02',
 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/identity/api/LegacyIamAccountBridgeController.java#create',
 'b13ade43fa6d0f9818b070b3b67558e2a9bd2d364a030532e821bc15e56c6d52',now(),
 'p2-fix11-account-bridge-migration','p2-fix11-account-bridge-migration'),
('REST:PUT:/api/legacy-auth/iam-accounts/{userId}','REST','control-plane-app','control-plane-app',
 'LegacyIamAccountBridgeController.update','/api/legacy-auth/iam-accounts/{userId}','PUT','LEGACY_ONLY',
 null,'SPRING_ROLE','["ROLE_ADMIN"]'::jsonb,'IDENTITY','PATH:userId',null,'2026-12-31'::date,
 'p2-fix11-account-bridge-source-inventory-2026-08-02',
 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/identity/api/LegacyIamAccountBridgeController.java#update',
 'b13ade43fa6d0f9818b070b3b67558e2a9bd2d364a030532e821bc15e56c6d52',now(),
 'p2-fix11-account-bridge-migration','p2-fix11-account-bridge-migration'),
('REST:POST:/api/legacy-auth/iam-accounts/{userId}/status','REST','control-plane-app','control-plane-app',
 'LegacyIamAccountBridgeController.changeStatus','/api/legacy-auth/iam-accounts/{userId}/status','POST','LEGACY_ONLY',
 null,'SPRING_ROLE','["ROLE_ADMIN"]'::jsonb,'IDENTITY','PATH:userId',null,'2026-12-31'::date,
 'p2-fix11-account-bridge-source-inventory-2026-08-02',
 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/identity/api/LegacyIamAccountBridgeController.java#changeStatus',
 'b13ade43fa6d0f9818b070b3b67558e2a9bd2d364a030532e821bc15e56c6d52',now(),
 'p2-fix11-account-bridge-migration','p2-fix11-account-bridge-migration'),
('REST:POST:/api/legacy-auth/iam-accounts/{userId}/require-password-change','REST','control-plane-app','control-plane-app',
 'LegacyIamAccountBridgeController.requirePasswordChange','/api/legacy-auth/iam-accounts/{userId}/require-password-change','POST','LEGACY_ONLY',
 null,'SPRING_ROLE','["ROLE_ADMIN"]'::jsonb,'SECURITY','PATH:userId',null,'2026-12-31'::date,
 'p2-fix11-account-bridge-source-inventory-2026-08-02',
 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/identity/api/LegacyIamAccountBridgeController.java#requirePasswordChange',
 'b13ade43fa6d0f9818b070b3b67558e2a9bd2d364a030532e821bc15e56c6d52',now(),
 'p2-fix11-account-bridge-migration','p2-fix11-account-bridge-migration'),
('REST:POST:/api/legacy-auth/iam-accounts/{userId}/revoke-sessions','REST','control-plane-app','control-plane-app',
 'LegacyIamAccountBridgeController.revokeSessions','/api/legacy-auth/iam-accounts/{userId}/revoke-sessions','POST','LEGACY_ONLY',
 null,'SPRING_ROLE','["ROLE_ADMIN"]'::jsonb,'SECURITY','PATH:userId',null,'2026-12-31'::date,
 'p2-fix11-account-bridge-source-inventory-2026-08-02',
 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/identity/api/LegacyIamAccountBridgeController.java#revokeSessions',
 'b13ade43fa6d0f9818b070b3b67558e2a9bd2d364a030532e821bc15e56c6d52',now(),
 'p2-fix11-account-bridge-migration','p2-fix11-account-bridge-migration')
on conflict(entry_point_id) do update set
 entry_point_type=excluded.entry_point_type,application_id=excluded.application_id,owner_module=excluded.owner_module,
 display_name=excluded.display_name,route_pattern=excluded.route_pattern,http_method=excluded.http_method,
 authority_state=excluded.authority_state,target_permission_code=excluded.target_permission_code,
 legacy_authority_type=excluded.legacy_authority_type,legacy_authorities=excluded.legacy_authorities,
 resource_type=excluded.resource_type,resource_resolver_id=excluded.resource_resolver_id,
 exemption_reason=excluded.exemption_reason,migration_deadline=excluded.migration_deadline,
 manifest_revision=excluded.manifest_revision,source_ref=excluded.source_ref,source_hash=excluded.source_hash,
 last_verified_at=excluded.last_verified_at,updated_at=now(),updated_by=excluded.updated_by,
 version=permission_entry_point_inventory.version+1;

insert into permission_application_manifests(
 manifest_id,application_id,environment,build_version,manifest_revision,schema_version,manifest_hash,
 catalog_revision_id,catalog_revision_code,catalog_content_hash,source_inventory_revision,entry_count,
 protected_entry_count,covered_entry_count,coverage_percent,target_permission_count,legacy_authority_count,
 exempt_count,delegated_count,uncovered_count,status,registered_at,registered_by,activated_at,activated_by,version)
select
 '00000000-0000-0000-0000-000000010601'::uuid,'control-plane-app','default',
 '0.8.2-SNAPSHOT-p2-fix11-account-management','p2-fix11-control-plane-permission-manifest-2026-08-02',1,
 'sha256:'||repeat('0',64),r.revision_id,r.revision_code,r.content_hash,
 'p2-fix11-account-bridge-source-inventory-2026-08-02',
 count(*)::integer,
 count(*) filter(where i.entry_point_type not in('COMMAND','QUERY'))::integer,
 count(*)::integer,100.0000,
 count(*) filter(where i.target_permission_code is not null and i.entry_point_type not in('COMMAND','QUERY'))::integer,
 count(*) filter(where i.entry_point_type not in('COMMAND','QUERY') and i.authority_state<>'EXEMPT' and i.target_permission_code is null)::integer,
 count(*) filter(where i.authority_state='EXEMPT')::integer,
 count(*) filter(where i.entry_point_type in('COMMAND','QUERY'))::integer,
 0,'REGISTERING',now(),'p2-fix11-account-bridge-migration',null,null,1
from permission_entry_point_inventory i
cross join permission_catalog_active_revision a
join permission_catalog_revisions r on r.revision_id=a.revision_id and r.status='PUBLISHED'
where a.singleton_id='ACTIVE' and i.application_id='control-plane-app'
group by r.revision_id,r.revision_code,r.content_hash
on conflict(application_id,environment,manifest_revision) do nothing;

insert into permission_application_manifest_entries(
 manifest_id,entry_point_id,entry_point_type,owner_module,display_name,route_pattern,http_method,authority_state,
 protection_mode,coverage_status,permission_code,legacy_authorities,resource_type,resource_resolver_id,scope_required,
 exemption_reason,source_ref,source_hash,descriptor_hash)
select '00000000-0000-0000-0000-000000010601'::uuid,i.entry_point_id,i.entry_point_type,i.owner_module,i.display_name,
 i.route_pattern,i.http_method,i.authority_state,
 case when i.entry_point_type in('COMMAND','QUERY') then 'INTERNAL_DELEGATED'
      when i.authority_state='EXEMPT' then 'EXEMPT'
      when i.target_permission_code is not null and i.authority_state='DUAL_SHADOW' then 'DUAL_SHADOW'
      when i.target_permission_code is not null then 'TARGET_PERMISSION'
      else 'LEGACY_AUTHORITY' end,
 case when i.entry_point_type in('COMMAND','QUERY') then 'DELEGATED'
      when i.authority_state='EXEMPT' then 'EXEMPT' else 'COVERED' end,
 i.target_permission_code,i.legacy_authorities,i.resource_type,i.resource_resolver_id,
 (i.target_permission_code is not null and i.resource_type not in('IDENTITY','SECURITY','AUDIT','INSTANCE','PERMISSION','GLOBAL','INTERNAL_MESSAGE')),
 i.exemption_reason,i.source_ref,i.source_hash,
 encode(sha256(convert_to(
   coalesce(i.entry_point_id,'')||'|'||coalesce(i.entry_point_type,'')||'|'||coalesce(i.application_id,'')||'|'||
   coalesce(i.owner_module,'')||'|'||coalesce(i.display_name,'')||'|'||coalesce(i.route_pattern,'')||'|'||
   coalesce(i.http_method,'')||'|'||coalesce(i.authority_state,'')||'|'||coalesce(i.target_permission_code,'')||'|'||
   coalesce(i.legacy_authority_type,'')||'|'||coalesce(i.resource_type,'')||'|'||coalesce(i.resource_resolver_id,'')||'|'||
   coalesce(i.exemption_reason,'')||'|'||coalesce(i.source_ref,'')||'|'||coalesce(i.source_hash,'')||'|'||
   (case when i.entry_point_type in('COMMAND','QUERY') then 'INTERNAL_DELEGATED'
         when i.authority_state='EXEMPT' then 'EXEMPT'
         when i.target_permission_code is not null and i.authority_state='DUAL_SHADOW' then 'DUAL_SHADOW'
         when i.target_permission_code is not null then 'TARGET_PERMISSION' else 'LEGACY_AUTHORITY' end)||'|'||
   (case when i.entry_point_type in('COMMAND','QUERY') then 'DELEGATED'
         when i.authority_state='EXEMPT' then 'EXEMPT' else 'COVERED' end)||'|'||
   (case when i.target_permission_code is not null and i.resource_type not in('IDENTITY','SECURITY','AUDIT','INSTANCE','PERMISSION','GLOBAL','INTERNAL_MESSAGE') then 'true' else 'false' end),
   'UTF8')),'hex')
from permission_entry_point_inventory i
where i.application_id='control-plane-app'
  and exists(select 1 from permission_application_manifests m
             where m.manifest_id='00000000-0000-0000-0000-000000010601'::uuid and m.status='REGISTERING')
on conflict(manifest_id,entry_point_id) do nothing;

update permission_application_manifests m
set manifest_hash=(
      select 'sha256:'||encode(sha256(convert_to(
        coalesce(string_agg(e.entry_point_id||'|'||e.descriptor_hash,E'\n' order by e.entry_point_id),''),'UTF8')),'hex')
      from permission_application_manifest_entries e where e.manifest_id=m.manifest_id),
    version=m.version+1
where m.manifest_id='00000000-0000-0000-0000-000000010601'::uuid
  and m.status='REGISTERING';

select phase5h_activate_manifest(
 '00000000-0000-0000-0000-000000010601'::uuid,
 'p2-fix11-account-bridge-migration',
 'p2-fix11-account-bridge-migration',
 'Activate P2 Fix11 bounded Legacy ADMIN to IAM account bridge manifest');
