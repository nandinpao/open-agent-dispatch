-- P2 Data Integrity & Runtime Acceptance overlay.
-- This migration preserves V1-V108 and adds database-level safeguards for organization
-- authority, primary Department consistency, hierarchy cycles, active ownership, and
-- effective-access query entry points.
select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','p2-data-integrity-migration',true);

-- MANAGER is no longer a Department Membership type. Official manager is the
-- departments.manager_user_id authority; membership only records placement.
create temporary table p2_manager_primary_merge on commit drop as
select manager_row.tenant_id,manager_row.user_id,manager_row.department_id
from org_department_memberships manager_row
join org_department_memberships member_row
  on manager_row.tenant_id=member_row.tenant_id and manager_row.user_id=member_row.user_id
 and manager_row.department_id=member_row.department_id and member_row.membership_type='MEMBER'
where manager_row.membership_type='MANAGER' and manager_row.is_primary;

update org_department_memberships member_row
set effective_at = least(member_row.effective_at,manager_row.effective_at),
    expires_at = case when member_row.expires_at is null or manager_row.expires_at is null then null else greatest(member_row.expires_at,manager_row.expires_at) end,
    status = case when member_row.status='ACTIVE' or manager_row.status='ACTIVE' then 'ACTIVE' else member_row.status end,
    version = member_row.version+1
from org_department_memberships manager_row
where manager_row.tenant_id=member_row.tenant_id and manager_row.user_id=member_row.user_id
  and manager_row.department_id=member_row.department_id and manager_row.membership_type='MANAGER'
  and member_row.membership_type='MEMBER';
delete from org_department_memberships manager_row
using org_department_memberships member_row
where manager_row.tenant_id=member_row.tenant_id and manager_row.user_id=member_row.user_id
  and manager_row.department_id=member_row.department_id and manager_row.membership_type='MANAGER'
  and member_row.membership_type='MEMBER';
update org_department_memberships member_row
set is_primary=true,version=member_row.version+1
from p2_manager_primary_merge merge_row
where member_row.tenant_id=merge_row.tenant_id and member_row.user_id=merge_row.user_id
  and member_row.department_id=merge_row.department_id and member_row.membership_type='MEMBER'
  and not member_row.is_primary;
update org_department_memberships dm set membership_type='MEMBER',version=dm.version+1
where dm.membership_type='MANAGER';
alter table org_department_memberships drop constraint if exists ck_org_department_membership_type;
alter table org_department_memberships add constraint ck_org_department_membership_type
 check (membership_type in ('MEMBER','DELEGATE'));

-- Invalid manager assignments cannot survive the new deferred authority checks.
update departments d set manager_user_id=null,updated_at=now(),updated_by='p2-data-integrity-migration',version=d.version+1
where d.manager_user_id is not null and not exists (
  select 1 from iam_users u join org_tenant_memberships tm
    on tm.tenant_id=d.tenant_id and tm.user_id=u.user_id
  where u.user_id=d.manager_user_id and u.status='ACTIVE' and tm.status='ACTIVE'
    and tm.joined_at<=now() and (tm.expires_at is null or tm.expires_at>now())
);

-- Reactivate or create organization placement for every retained Official Manager.
update org_department_memberships dm
set membership_type='MEMBER',status='ACTIVE',expires_at=null,version=dm.version+1
from departments d
where d.tenant_id=dm.tenant_id and d.department_id=dm.department_id
  and d.manager_user_id=dm.user_id and d.manager_user_id is not null
  and (dm.status<>'ACTIVE' or dm.membership_type<>'MEMBER' or dm.expires_at is not null);
insert into org_department_memberships(tenant_id,membership_id,user_id,department_id,membership_type,is_primary,effective_at,expires_at,status,version)
select d.tenant_id,md5('official-manager:'||d.tenant_id||':'||d.department_id||':'||d.manager_user_id),d.manager_user_id,d.department_id,
       'MEMBER',false,now(),null,'ACTIVE',1
from departments d
where d.manager_user_id is not null and not exists(
  select 1 from org_department_memberships dm
  where dm.tenant_id=d.tenant_id and dm.user_id=d.manager_user_id and dm.department_id=d.department_id
)
on conflict do nothing;

-- Every user with active Department placement has exactly one Primary Department.
with candidates as (
  select tenant_id,user_id,membership_id,
         row_number() over(partition by tenant_id,user_id order by effective_at,membership_id) rn
  from org_department_memberships
  where status='ACTIVE' and effective_at<=now() and (expires_at is null or expires_at>now())
), missing as (
  select c.tenant_id,c.user_id,min(c.membership_id) filter(where c.rn=1) membership_id
  from candidates c
  where not exists(select 1 from org_department_memberships p
    where p.tenant_id=c.tenant_id and p.user_id=c.user_id and p.status='ACTIVE' and p.is_primary
      and p.effective_at<=now() and (p.expires_at is null or p.expires_at>now()))
  group by c.tenant_id,c.user_id
)
update org_department_memberships dm set is_primary=true,version=dm.version+1
from missing m where dm.tenant_id=m.tenant_id and dm.membership_id=m.membership_id;

-- Active Groups cannot retain a disabled owner Department.
update organization_groups g set owner_department_id=null,updated_at=now(),updated_by='p2-data-integrity-migration',version=g.version+1
where g.status='ACTIVE' and g.owner_department_id is not null and not exists(
  select 1 from departments d where d.tenant_id=g.tenant_id and d.department_id=g.owner_department_id and d.status='ACTIVE'
);

create index if not exists idx_departments_active_manager on departments(tenant_id,manager_user_id,status) where manager_user_id is not null;
create index if not exists idx_departments_active_parent on departments(tenant_id,parent_department_id,status) where parent_department_id is not null;
create index if not exists idx_groups_active_parent on organization_groups(tenant_id,parent_group_id,status) where parent_group_id is not null;
create index if not exists idx_groups_active_owner on organization_groups(tenant_id,owner_department_id,status) where owner_department_id is not null;
create index if not exists idx_department_membership_active_resource on org_department_memberships(tenant_id,department_id,status,effective_at,expires_at);
create index if not exists idx_group_membership_active_resource on org_group_memberships(tenant_id,group_id,status,effective_at,expires_at);

create or replace function p2_validate_department_integrity()
returns trigger language plpgsql security invoker as $$
declare cursor_id varchar(128); next_id varchar(128); depth integer:=0; n bigint;
begin
  if new.parent_department_id is not null then
    cursor_id:=new.parent_department_id;
    while cursor_id is not null loop
      if cursor_id=new.department_id then raise exception 'DEPARTMENT_CYCLE_DETECTED' using errcode='23514'; end if;
      select parent_department_id into next_id from departments where tenant_id=new.tenant_id and department_id=cursor_id;
      if not found then raise exception 'DEPARTMENT_PARENT_NOT_FOUND' using errcode='23503'; end if;
      cursor_id:=next_id; depth:=depth+1;
      if depth>32 then raise exception 'DEPARTMENT_HIERARCHY_DEPTH_EXCEEDED' using errcode='23514'; end if;
    end loop;
  end if;
  if new.manager_user_id is not null then
    if not exists(select 1 from iam_users u join org_tenant_memberships tm on tm.user_id=u.user_id and tm.tenant_id=new.tenant_id
      where u.user_id=new.manager_user_id and u.status='ACTIVE' and tm.status='ACTIVE' and tm.joined_at<=now() and (tm.expires_at is null or tm.expires_at>now()))
    then raise exception 'OFFICIAL_MANAGER_TENANT_MEMBERSHIP_REQUIRED' using errcode='23514'; end if;
    if not exists(select 1 from org_department_memberships dm where dm.tenant_id=new.tenant_id and dm.department_id=new.department_id
      and dm.user_id=new.manager_user_id and dm.status='ACTIVE' and dm.membership_type in('MEMBER','DELEGATE')
      and dm.effective_at<=now() and (dm.expires_at is null or dm.expires_at>now()))
    then raise exception 'OFFICIAL_MANAGER_DEPARTMENT_MEMBERSHIP_REQUIRED' using errcode='23514'; end if;
  end if;
  if new.status='DISABLED' then
    select count(*) into n from departments where tenant_id=new.tenant_id and parent_department_id=new.department_id and status='ACTIVE';
    if n>0 then raise exception 'DEPARTMENT_DISABLE_BLOCKED_BY_CHILDREN' using errcode='23514'; end if;
    select count(*) into n from org_department_memberships where tenant_id=new.tenant_id and department_id=new.department_id and status='ACTIVE' and effective_at<=now() and (expires_at is null or expires_at>now());
    if n>0 then raise exception 'DEPARTMENT_DISABLE_BLOCKED_BY_MEMBERS' using errcode='23514'; end if;
    select count(*) into n from organization_groups where tenant_id=new.tenant_id and owner_department_id=new.department_id and status='ACTIVE';
    if n>0 then raise exception 'DEPARTMENT_DISABLE_BLOCKED_BY_GROUPS' using errcode='23514'; end if;
  end if;
  return new;
end $$;

drop trigger if exists trg_p2_department_integrity on departments;
create constraint trigger trg_p2_department_integrity after insert or update on departments
deferrable initially deferred for each row execute function p2_validate_department_integrity();

create or replace function p2_validate_group_integrity()
returns trigger language plpgsql security invoker as $$
declare cursor_id varchar(128); next_id varchar(128); depth integer:=0; n bigint;
begin
  if new.parent_group_id is not null then
    cursor_id:=new.parent_group_id;
    while cursor_id is not null loop
      if cursor_id=new.group_id then raise exception 'GROUP_CYCLE_DETECTED' using errcode='23514'; end if;
      select parent_group_id into next_id from organization_groups where tenant_id=new.tenant_id and group_id=cursor_id;
      if not found then raise exception 'GROUP_PARENT_NOT_FOUND' using errcode='23503'; end if;
      cursor_id:=next_id; depth:=depth+1;
      if depth>32 then raise exception 'GROUP_HIERARCHY_DEPTH_EXCEEDED' using errcode='23514'; end if;
    end loop;
  end if;
  if new.status='ACTIVE' and new.owner_department_id is not null and not exists(
    select 1 from departments d where d.tenant_id=new.tenant_id and d.department_id=new.owner_department_id and d.status='ACTIVE')
  then raise exception 'GROUP_OWNER_DEPARTMENT_DISABLED' using errcode='23514'; end if;
  if new.status='DISABLED' then
    select count(*) into n from organization_groups where tenant_id=new.tenant_id and parent_group_id=new.group_id and status='ACTIVE';
    if n>0 then raise exception 'GROUP_DISABLE_BLOCKED_BY_CHILDREN' using errcode='23514'; end if;
    select count(*) into n from org_group_memberships where tenant_id=new.tenant_id and group_id=new.group_id and status='ACTIVE' and effective_at<=now() and (expires_at is null or expires_at>now());
    if n>0 then raise exception 'GROUP_DISABLE_BLOCKED_BY_MEMBERS' using errcode='23514'; end if;
  end if;
  return new;
end $$;

drop trigger if exists trg_p2_group_integrity on organization_groups;
create constraint trigger trg_p2_group_integrity after insert or update on organization_groups
deferrable initially deferred for each row execute function p2_validate_group_integrity();

create or replace function p2_validate_department_membership_integrity()
returns trigger language plpgsql security invoker as $$
declare t varchar(64); u varchar(128); active_count bigint; primary_count bigint;
begin
  if tg_op='DELETE' then t:=old.tenant_id; u:=old.user_id; else t:=new.tenant_id; u:=new.user_id; end if;
  select count(*),count(*) filter(where is_primary) into active_count,primary_count
  from org_department_memberships where tenant_id=t and user_id=u and status='ACTIVE'
    and effective_at<=now() and (expires_at is null or expires_at>now());
  if active_count>0 and primary_count<>1 then raise exception 'PRIMARY_DEPARTMENT_REQUIRED' using errcode='23514'; end if;
  if exists(select 1 from departments d where d.tenant_id=t and d.manager_user_id=u and d.status='ACTIVE'
    and not exists(select 1 from org_department_memberships dm where dm.tenant_id=d.tenant_id and dm.department_id=d.department_id
      and dm.user_id=u and dm.status='ACTIVE' and dm.effective_at<=now() and (dm.expires_at is null or dm.expires_at>now())))
  then raise exception 'OFFICIAL_MANAGER_DEPARTMENT_MEMBERSHIP_REQUIRED' using errcode='23514'; end if;
  if tg_op='DELETE' then return old; else return new; end if;
end $$;

drop trigger if exists trg_p2_department_membership_integrity on org_department_memberships;
create constraint trigger trg_p2_department_membership_integrity after insert or update or delete on org_department_memberships
deferrable initially deferred for each row execute function p2_validate_department_membership_integrity();

create or replace function p2_validate_manager_account_integrity()
returns trigger language plpgsql security invoker as $$
begin
  if new.status<>'ACTIVE' and exists(select 1 from departments d where d.manager_user_id=new.user_id and d.status='ACTIVE')
  then raise exception 'OFFICIAL_MANAGER_ASSIGNMENT_BLOCKS_USER_STATUS' using errcode='23514'; end if;
  return new;
end $$;
drop trigger if exists trg_p2_manager_account_integrity on iam_users;
create constraint trigger trg_p2_manager_account_integrity after update on iam_users
deferrable initially deferred for each row execute function p2_validate_manager_account_integrity();

create or replace function p2_validate_manager_tenant_membership_integrity()
returns trigger language plpgsql security invoker as $$
begin
  if (new.status<>'ACTIVE' or (new.expires_at is not null and new.expires_at<=now()))
    and exists(select 1 from departments d where d.tenant_id=new.tenant_id and d.manager_user_id=new.user_id and d.status='ACTIVE')
  then raise exception 'OFFICIAL_MANAGER_ASSIGNMENT_BLOCKS_TENANT_STATUS' using errcode='23514'; end if;
  return new;
end $$;
drop trigger if exists trg_p2_manager_tenant_membership_integrity on org_tenant_memberships;
create constraint trigger trg_p2_manager_tenant_membership_integrity after update on org_tenant_memberships
deferrable initially deferred for each row execute function p2_validate_manager_tenant_membership_integrity();

update permission_entry_point_inventory pei
set source_hash='9141fa3ce70bd282e215454f5f59e21fa9082584b3b48f81a5476e0833a5d8c8',manifest_revision='p2-data-integrity-source-inventory-2026-08-03',last_verified_at=now(),updated_at=now(),updated_by='p2-data-integrity-migration',version=pei.version+1
where pei.source_ref like 'ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/IamOrganizationController.java#%';
update permission_entry_point_inventory pei
set source_hash='81cd7b2eed18903b12fbc0b034d75ea2654cfc17ed5c57827f58ea808865b84b',manifest_revision='p2-data-integrity-source-inventory-2026-08-03',last_verified_at=now(),updated_at=now(),updated_by='p2-data-integrity-migration',version=pei.version+1
where pei.source_ref like 'ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/IamRbacController.java#%';

insert into permission_entry_point_inventory(entry_point_id,entry_point_type,application_id,owner_module,display_name,route_pattern,http_method,authority_state,target_permission_code,legacy_authority_type,legacy_authorities,resource_type,resource_resolver_id,exemption_reason,migration_deadline,manifest_revision,source_ref,source_hash,last_verified_at,created_by,updated_by)
values
('REST:GET:/api/identity/users/{userId}/effective-access','REST','control-plane-app','iam-api','IamRbacController.effectiveAccess','/api/identity/users/{userId}/effective-access','GET','TARGET_ONLY','identity.role_binding.read',null,'[]'::jsonb,'IDENTITY','NONE',null,null,'p2-data-integrity-source-inventory-2026-08-03','ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/IamRbacController.java#effectiveAccess','81cd7b2eed18903b12fbc0b034d75ea2654cfc17ed5c57827f58ea808865b84b',now(),'p2-data-integrity-migration','p2-data-integrity-migration'),
('REST:GET:/api/identity/role-bindings/{bindingId}/revocation-preview','REST','control-plane-app','iam-api','IamRbacController.previewRevocation','/api/identity/role-bindings/{bindingId}/revocation-preview','GET','TARGET_ONLY','identity.role_binding.read',null,'[]'::jsonb,'IDENTITY','NONE',null,null,'p2-data-integrity-source-inventory-2026-08-03','ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/IamRbacController.java#previewRevocation','81cd7b2eed18903b12fbc0b034d75ea2654cfc17ed5c57827f58ea808865b84b',now(),'p2-data-integrity-migration','p2-data-integrity-migration')
on conflict(entry_point_id) do update set display_name=excluded.display_name,route_pattern=excluded.route_pattern,http_method=excluded.http_method,authority_state=excluded.authority_state,target_permission_code=excluded.target_permission_code,manifest_revision=excluded.manifest_revision,source_ref=excluded.source_ref,source_hash=excluded.source_hash,last_verified_at=excluded.last_verified_at,updated_at=now(),updated_by=excluded.updated_by,version=permission_entry_point_inventory.version+1;

insert into permission_application_manifests(
 manifest_id,application_id,environment,build_version,manifest_revision,schema_version,manifest_hash,
 catalog_revision_id,catalog_revision_code,catalog_content_hash,source_inventory_revision,entry_count,
 protected_entry_count,covered_entry_count,coverage_percent,target_permission_count,legacy_authority_count,
 exempt_count,delegated_count,uncovered_count,status,registered_at,registered_by,activated_at,activated_by,version)
select
 '00000000-0000-0000-0000-000000010901'::uuid,'control-plane-app','default',
 '0.8.2-SNAPSHOT-p2-data-integrity-runtime-acceptance','p2-data-integrity-permission-manifest-2026-08-03',1,
 'sha256:'||repeat('0',64),r.revision_id,r.revision_code,r.content_hash,
 'p2-data-integrity-source-inventory-2026-08-03',
 count(*)::integer,
 count(*) filter(where i.entry_point_type not in('COMMAND','QUERY'))::integer,
 count(*)::integer,100.0000,
 count(*) filter(where i.target_permission_code is not null and i.entry_point_type not in('COMMAND','QUERY'))::integer,
 count(*) filter(where i.entry_point_type not in('COMMAND','QUERY') and i.authority_state<>'EXEMPT' and i.target_permission_code is null)::integer,
 count(*) filter(where i.authority_state='EXEMPT')::integer,
 count(*) filter(where i.entry_point_type in('COMMAND','QUERY'))::integer,
 0,'REGISTERING',now(),'p2-data-integrity-migration',null,null,1
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
select '00000000-0000-0000-0000-000000010901'::uuid,i.entry_point_id,i.entry_point_type,i.owner_module,i.display_name,
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
             where m.manifest_id='00000000-0000-0000-0000-000000010901'::uuid and m.status='REGISTERING')
on conflict(manifest_id,entry_point_id) do nothing;

update permission_application_manifests m
set manifest_hash=(
      select 'sha256:'||encode(sha256(convert_to(
        coalesce(string_agg(e.entry_point_id||'|'||e.descriptor_hash,E'\n' order by e.entry_point_id),''),'UTF8')),'hex')
      from permission_application_manifest_entries e where e.manifest_id=m.manifest_id),
    version=m.version+1
where m.manifest_id='00000000-0000-0000-0000-000000010901'::uuid
  and m.status='REGISTERING';

select phase5h_activate_manifest(
 '00000000-0000-0000-0000-000000010901'::uuid,
 'p2-data-integrity-migration',
 'p2-data-integrity-migration',
 'Activate P2 data integrity and effective access manifest');
