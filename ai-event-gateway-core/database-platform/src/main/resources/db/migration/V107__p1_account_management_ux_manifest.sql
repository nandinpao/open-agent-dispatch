-- P1 Account Management UX: preserve the immutable V106 history while registering
-- server-generated identity IDs, searchable existing identities, and governed invitation
-- lifecycle endpoints. The DUAL-mode Legacy ADMIN bridge remains LEGACY_ONLY and is still
-- an explicit IAM_ONLY cutover blocker.
select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','p1-account-management-ux-migration',true);

-- Refresh all descriptors for controllers changed by the P1 UX/API convergence.
update permission_entry_point_inventory
set source_hash='9dae896e9ae7d7202aa0507899c13c357912c8956cd73cb9975a919a3e06db0b',
    manifest_revision='p1-account-management-ux-source-inventory-2026-08-03',
    last_verified_at=now(),updated_at=now(),updated_by='p1-account-management-ux-migration',version=version+1
where source_ref like 'ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/IamUserController.java#%';

update permission_entry_point_inventory
set source_hash='822c22b862d201e9ab5be46b736dbff04c643c3d8c6a883af8748899c6cf3088',
    manifest_revision='p1-account-management-ux-source-inventory-2026-08-03',
    last_verified_at=now(),updated_at=now(),updated_by='p1-account-management-ux-migration',version=version+1
where source_ref like 'ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/IamPlatformUserController.java#%';

update permission_entry_point_inventory
set source_hash='c3e53e8096634d4be5b56be7ba3d5e9f648f65842b795e835549c356ab40d662',
    manifest_revision='p1-account-management-ux-source-inventory-2026-08-03',
    last_verified_at=now(),updated_at=now(),updated_by='p1-account-management-ux-migration',version=version+1
where source_ref like 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/identity/api/LegacyIamAccountBridgeController.java#%';

insert into permission_entry_point_inventory(
 entry_point_id,entry_point_type,application_id,owner_module,display_name,route_pattern,http_method,
 authority_state,target_permission_code,legacy_authority_type,legacy_authorities,resource_type,resource_resolver_id,
 exemption_reason,migration_deadline,manifest_revision,source_ref,source_hash,last_verified_at,created_by,updated_by)
values
('REST:GET:/api/identity/users/available-identities','REST','control-plane-app','iam-api',
 'IamUserController.availableIdentities','/api/identity/users/available-identities','GET','TARGET_ONLY',
 'identity.tenant_membership.manage',null,'[]'::jsonb,'IDENTITY','ACTIVE_TENANT',null,null,
 'p1-account-management-ux-source-inventory-2026-08-03',
 'ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/IamUserController.java#availableIdentities',
 '9dae896e9ae7d7202aa0507899c13c357912c8956cd73cb9975a919a3e06db0b',now(),
 'p1-account-management-ux-migration','p1-account-management-ux-migration'),
('REST:GET:/api/identity/users/{userId}/invitation','REST','control-plane-app','iam-api',
 'IamUserInvitationController.status','/api/identity/users/{userId}/invitation','GET','TARGET_ONLY',
 'identity.user.read',null,'[]'::jsonb,'IDENTITY','PATH:userId',null,null,
 'p1-account-management-ux-source-inventory-2026-08-03',
 'ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/IamUserInvitationController.java#status',
 '3dcbfe81e42a6ced437595679fa8143af4c2d013eee0b11d7abc25a5f02ca9e2',now(),
 'p1-account-management-ux-migration','p1-account-management-ux-migration'),
('REST:POST:/api/identity/users/{userId}/invitation/resend','REST','control-plane-app','iam-api',
 'IamUserInvitationController.resend','/api/identity/users/{userId}/invitation/resend','POST','TARGET_ONLY',
 'identity.user.update',null,'[]'::jsonb,'IDENTITY','PATH:userId',null,null,
 'p1-account-management-ux-source-inventory-2026-08-03',
 'ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/IamUserInvitationController.java#resend',
 '3dcbfe81e42a6ced437595679fa8143af4c2d013eee0b11d7abc25a5f02ca9e2',now(),
 'p1-account-management-ux-migration','p1-account-management-ux-migration'),
('REST:POST:/api/identity/users/{userId}/invitation/revoke','REST','control-plane-app','iam-api',
 'IamUserInvitationController.revoke','/api/identity/users/{userId}/invitation/revoke','POST','TARGET_ONLY',
 'identity.user.update',null,'[]'::jsonb,'IDENTITY','PATH:userId',null,null,
 'p1-account-management-ux-source-inventory-2026-08-03',
 'ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/IamUserInvitationController.java#revoke',
 '3dcbfe81e42a6ced437595679fa8143af4c2d013eee0b11d7abc25a5f02ca9e2',now(),
 'p1-account-management-ux-migration','p1-account-management-ux-migration'),
('REST:GET:/api/legacy-auth/iam-accounts/{userId}/invitation','REST','control-plane-app','control-plane-app',
 'LegacyIamAccountBridgeController.invitationStatus','/api/legacy-auth/iam-accounts/{userId}/invitation','GET','LEGACY_ONLY',
 null,'SPRING_ROLE','["ROLE_ADMIN"]'::jsonb,'IDENTITY','PATH:userId',null,'2026-12-31'::date,
 'p1-account-management-ux-source-inventory-2026-08-03',
 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/identity/api/LegacyIamAccountBridgeController.java#invitationStatus',
 'c3e53e8096634d4be5b56be7ba3d5e9f648f65842b795e835549c356ab40d662',now(),
 'p1-account-management-ux-migration','p1-account-management-ux-migration'),
('REST:POST:/api/legacy-auth/iam-accounts/{userId}/invitation/resend','REST','control-plane-app','control-plane-app',
 'LegacyIamAccountBridgeController.resendInvitation','/api/legacy-auth/iam-accounts/{userId}/invitation/resend','POST','LEGACY_ONLY',
 null,'SPRING_ROLE','["ROLE_ADMIN"]'::jsonb,'IDENTITY','PATH:userId',null,'2026-12-31'::date,
 'p1-account-management-ux-source-inventory-2026-08-03',
 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/identity/api/LegacyIamAccountBridgeController.java#resendInvitation',
 'c3e53e8096634d4be5b56be7ba3d5e9f648f65842b795e835549c356ab40d662',now(),
 'p1-account-management-ux-migration','p1-account-management-ux-migration'),
('REST:POST:/api/legacy-auth/iam-accounts/{userId}/invitation/revoke','REST','control-plane-app','control-plane-app',
 'LegacyIamAccountBridgeController.revokeInvitation','/api/legacy-auth/iam-accounts/{userId}/invitation/revoke','POST','LEGACY_ONLY',
 null,'SPRING_ROLE','["ROLE_ADMIN"]'::jsonb,'IDENTITY','PATH:userId',null,'2026-12-31'::date,
 'p1-account-management-ux-source-inventory-2026-08-03',
 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/identity/api/LegacyIamAccountBridgeController.java#revokeInvitation',
 'c3e53e8096634d4be5b56be7ba3d5e9f648f65842b795e835549c356ab40d662',now(),
 'p1-account-management-ux-migration','p1-account-management-ux-migration')
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
 '00000000-0000-0000-0000-000000010701'::uuid,'control-plane-app','default',
 '0.8.2-SNAPSHOT-p1-account-management-ux','p1-account-management-ux-permission-manifest-2026-08-03',1,
 'sha256:'||repeat('0',64),r.revision_id,r.revision_code,r.content_hash,
 'p1-account-management-ux-source-inventory-2026-08-03',
 count(*)::integer,
 count(*) filter(where i.entry_point_type not in('COMMAND','QUERY'))::integer,
 count(*)::integer,100.0000,
 count(*) filter(where i.target_permission_code is not null and i.entry_point_type not in('COMMAND','QUERY'))::integer,
 count(*) filter(where i.entry_point_type not in('COMMAND','QUERY') and i.authority_state<>'EXEMPT' and i.target_permission_code is null)::integer,
 count(*) filter(where i.authority_state='EXEMPT')::integer,
 count(*) filter(where i.entry_point_type in('COMMAND','QUERY'))::integer,
 0,'REGISTERING',now(),'p1-account-management-ux-migration',null,null,1
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
select '00000000-0000-0000-0000-000000010701'::uuid,i.entry_point_id,i.entry_point_type,i.owner_module,i.display_name,
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
             where m.manifest_id='00000000-0000-0000-0000-000000010701'::uuid and m.status='REGISTERING')
on conflict(manifest_id,entry_point_id) do nothing;

update permission_application_manifests m
set manifest_hash=(
      select 'sha256:'||encode(sha256(convert_to(
        coalesce(string_agg(e.entry_point_id||'|'||e.descriptor_hash,E'\n' order by e.entry_point_id),''),'UTF8')),'hex')
      from permission_application_manifest_entries e where e.manifest_id=m.manifest_id),
    version=m.version+1
where m.manifest_id='00000000-0000-0000-0000-000000010701'::uuid
  and m.status='REGISTERING';

select phase5h_activate_manifest(
 '00000000-0000-0000-0000-000000010701'::uuid,
 'p1-account-management-ux-migration',
 'p1-account-management-ux-migration',
 'Activate P1 account-management UX and invitation lifecycle manifest');
