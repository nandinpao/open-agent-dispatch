-- RBAC Convergence R3: Role / Responsibility / Scope foundation.
-- Adds Department as a first-class role recipient, Department Subtree as a governed RBAC scope,
-- and makes nested Group / Department membership part of effective principal inheritance.

select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','rbac-convergence-r3',true);

-- ---------------------------------------------------------------------------
-- Role Binding contract: explicit Department principal + Department Subtree.
-- ---------------------------------------------------------------------------
alter table rbac_principal_role_bindings drop constraint if exists ck_rbac_binding_principal;
alter table rbac_principal_role_bindings
  add constraint ck_rbac_binding_principal
  check(principal_type in('USER','DEPARTMENT','GROUP','SERVICE_ACCOUNT')) not valid;
alter table rbac_principal_role_bindings validate constraint ck_rbac_binding_principal;

alter table rbac_principal_role_bindings drop constraint if exists ck_rbac_binding_scope;
alter table rbac_principal_role_bindings
  add constraint ck_rbac_binding_scope
  check(scope_type in('INSTANCE','TENANT','DEPARTMENT','DEPARTMENT_SUBTREE','GROUP')) not valid;
alter table rbac_principal_role_bindings validate constraint ck_rbac_binding_scope;

alter table rbac_principal_role_bindings drop constraint if exists ck_rbac_binding_scope_target;
alter table rbac_principal_role_bindings drop constraint if exists ck_rbac_binding_scope_tenant;
alter table rbac_principal_role_bindings
  add constraint ck_rbac_binding_scope_tenant check(
    (scope_type='INSTANCE' and tenant_id is null and scope_id='INSTANCE')
    or (scope_type='TENANT' and tenant_id is not null and scope_id=tenant_id)
    or (scope_type in('DEPARTMENT','DEPARTMENT_SUBTREE','GROUP') and tenant_id is not null and btrim(scope_id)<>'')
  ) not valid;
alter table rbac_principal_role_bindings validate constraint ck_rbac_binding_scope_tenant;

-- ---------------------------------------------------------------------------
-- Polymorphic Role Binding referential defense. UI selectors improve usability, but
-- direct API callers must not be able to bind a Role to an unknown/cross-Tenant
-- principal or scope target. Historical inactive bindings remain readable/revocable.
-- ---------------------------------------------------------------------------
create or replace function r3_validate_role_binding_targets() returns trigger language plpgsql as $$
begin
  if new.status<>'ACTIVE' then return new; end if;

  -- INSTANCE bindings retain the pre-R3 platform authority model. Tenant bindings
  -- below are validated against canonical Tenant-owned identity/organization data.
  if new.scope_type='INSTANCE' then return new; end if;
  if new.tenant_id is null then
    raise exception using errcode='23514',message='ROLE_BINDING_TENANT_REQUIRED';
  end if;

  if new.principal_type='USER' and not exists(
    select 1 from iam_users u
    join org_tenant_memberships tm on tm.user_id=u.user_id and tm.tenant_id=new.tenant_id
    where u.user_id=new.principal_id and u.status<>'DELETED' and tm.status<>'REMOVED') then
    raise exception using errcode='23514',message='ROLE_BINDING_PRINCIPAL_NOT_FOUND';
  elsif new.principal_type='DEPARTMENT' and not exists(
    select 1 from departments d where d.tenant_id=new.tenant_id and d.department_id=new.principal_id) then
    raise exception using errcode='23514',message='ROLE_BINDING_PRINCIPAL_NOT_FOUND';
  elsif new.principal_type='GROUP' and not exists(
    select 1 from organization_groups g where g.tenant_id=new.tenant_id and g.group_id=new.principal_id) then
    raise exception using errcode='23514',message='ROLE_BINDING_PRINCIPAL_NOT_FOUND';
  elsif new.principal_type='SERVICE_ACCOUNT' and not exists(
    select 1 from token_service_accounts sa where sa.tenant_id=new.tenant_id and sa.service_account_id=new.principal_id) then
    raise exception using errcode='23514',message='ROLE_BINDING_PRINCIPAL_NOT_FOUND';
  end if;

  if new.scope_type='TENANT' and not exists(
    select 1 from tenants t where t.tenant_id=new.tenant_id and new.scope_id=new.tenant_id) then
    raise exception using errcode='23514',message='ROLE_BINDING_SCOPE_TARGET_NOT_FOUND';
  elsif new.scope_type in('DEPARTMENT','DEPARTMENT_SUBTREE') and not exists(
    select 1 from departments d where d.tenant_id=new.tenant_id and d.department_id=new.scope_id) then
    raise exception using errcode='23514',message='ROLE_BINDING_SCOPE_TARGET_NOT_FOUND';
  elsif new.scope_type='GROUP' and not exists(
    select 1 from organization_groups g where g.tenant_id=new.tenant_id and g.group_id=new.scope_id) then
    raise exception using errcode='23514',message='ROLE_BINDING_SCOPE_TARGET_NOT_FOUND';
  end if;
  return new;
end $$;

drop trigger if exists trg_r3_role_binding_targets on rbac_principal_role_bindings;
create trigger trg_r3_role_binding_targets
before insert or update of tenant_id,principal_type,principal_id,scope_type,scope_id,status on rbac_principal_role_bindings
for each row execute function r3_validate_role_binding_targets();

-- ---------------------------------------------------------------------------
-- Permission Catalog revision 017.
-- Department-subtree is the hierarchical form of every already Department-capable permission.
-- This does not auto-grant anything: an explicit Role Binding must still choose this scope.
-- ---------------------------------------------------------------------------
insert into permission_catalog_revisions(
  revision_id,revision_code,revision_number,status,content_hash,description,supersedes_revision_id,
  created_at,created_by,published_at,published_by,version)
select '00000000-0000-0000-0000-000000000017'::uuid,'RBAC-R3-0.8.2',coalesce(max(revision_number),0)+1,
  'DRAFT','DRAFT:UNPUBLISHED',
  'RBAC Convergence R3 Department principal, Department Subtree scope and hierarchy inheritance.',
  (select revision_id from permission_catalog_active_revision where singleton_id='ACTIVE'),
  now(),'rbac-convergence-r3',null,null,1
from permission_catalog_revisions
on conflict(revision_id) do nothing;

insert into permission_catalog_revision_entries(
  revision_id,permission_code,owner_module,resource_type,action_code,description,risk_level,risk_lane,
  lifecycle,allowed_scope_types,system_managed,replacement_permission_code,introduced_at,deprecated_at,
  retired_at,updated_at,updated_by,version)
select '00000000-0000-0000-0000-000000000017'::uuid,e.permission_code,e.owner_module,e.resource_type,
  e.action_code,e.description,e.risk_level,e.risk_lane,e.lifecycle,e.allowed_scope_types,e.system_managed,
  e.replacement_permission_code,e.introduced_at,e.deprecated_at,e.retired_at,now(),'rbac-convergence-r3',1
from permission_catalog_revision_entries e
join permission_catalog_active_revision a on a.singleton_id='ACTIVE' and a.revision_id=e.revision_id
on conflict(revision_id,permission_code) do nothing;

insert into permission_catalog_revision_aliases(
  revision_id,alias_code,canonical_permission_code,alias_type,valid_from,valid_until,reason,created_by,created_at,version)
select '00000000-0000-0000-0000-000000000017'::uuid,a.alias_code,a.canonical_permission_code,
  a.alias_type,a.valid_from,a.valid_until,a.reason,'rbac-convergence-r3',now(),1
from permission_catalog_revision_aliases a
join permission_catalog_active_revision active on active.singleton_id='ACTIVE' and active.revision_id=a.revision_id
on conflict(revision_id,alias_code) do nothing;

update permission_catalog_revision_entries
set allowed_scope_types=(
      select array_agg(distinct scope_name order by scope_name)
      from unnest(allowed_scope_types || array['DEPARTMENT_SUBTREE']::varchar[]) scope_name),
    updated_at=now(),updated_by='rbac-convergence-r3',version=version+1
where revision_id='00000000-0000-0000-0000-000000000017'::uuid
  and 'DEPARTMENT'=any(allowed_scope_types)
  and not('DEPARTMENT_SUBTREE'=any(allowed_scope_types));

-- V30 established the runtime scope-domain check on permission_point_catalog. V80
-- renamed that table to permission_definitions, so the constraint survived the rename.
-- R3 introduces DEPARTMENT_SUBTREE and must evolve the database invariant before the
-- revision-017 projection writes the newly legal scope into permission_definitions.
alter table permission_definitions drop constraint if exists ck_permission_point_scope_values;
alter table permission_definitions
  add constraint ck_permission_point_scope_values
  check(allowed_scope_types <@ array['INSTANCE','TENANT','DEPARTMENT','DEPARTMENT_SUBTREE','GROUP']::varchar[]) not valid;
alter table permission_definitions validate constraint ck_permission_point_scope_values;

-- V80 retired permission_point_catalog in favor of permission_definitions.
-- The updated revision is projected into permission_definitions below.

select set_config('app.permission_catalog_publish_revision_id','00000000-0000-0000-0000-000000000017',true);

insert into permission_definitions(
  permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,
  active,version,owner_module,risk_lane,lifecycle,catalog_revision_id,replacement_permission_code,
  introduced_at,deprecated_at,retired_at,updated_at,updated_by)
select permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,
  lifecycle<>'RETIRED',1,owner_module,risk_lane,lifecycle,revision_id,replacement_permission_code,
  introduced_at,deprecated_at,retired_at,updated_at,updated_by
from permission_catalog_revision_entries
where revision_id='00000000-0000-0000-0000-000000000017'::uuid
on conflict(permission_code) do update set
  resource_type=excluded.resource_type,action_code=excluded.action_code,description=excluded.description,
  risk_level=excluded.risk_level,allowed_scope_types=excluded.allowed_scope_types,
  system_managed=excluded.system_managed,active=excluded.active,owner_module=excluded.owner_module,
  risk_lane=excluded.risk_lane,lifecycle=excluded.lifecycle,catalog_revision_id=excluded.catalog_revision_id,
  replacement_permission_code=excluded.replacement_permission_code,deprecated_at=excluded.deprecated_at,
  retired_at=excluded.retired_at,updated_at=excluded.updated_at,updated_by=excluded.updated_by,
  version=permission_definitions.version+1;

update permission_catalog_revisions set status='SUPERSEDED',version=version+1
where revision_id=(select revision_id from permission_catalog_active_revision where singleton_id='ACTIVE')
  and revision_id<>'00000000-0000-0000-0000-000000000017'::uuid and status='PUBLISHED';

update permission_catalog_revisions set status='PUBLISHED',content_hash=(
  with catalog_lines as (
    select 'P|'||permission_code||'|'||owner_module||'|'||resource_type||'|'||action_code||'|'||description||'|'||
      risk_level||'|'||risk_lane||'|'||lifecycle||'|'||coalesce(array_to_string(allowed_scope_types,','),'')||'|'||
      system_managed::text||'|'||coalesce(replacement_permission_code,'') as line
    from permission_catalog_revision_entries where revision_id='00000000-0000-0000-0000-000000000017'::uuid
    union all
    select 'A|'||alias_code||'|'||canonical_permission_code||'|'||alias_type||'|'||
      coalesce(to_char(valid_from at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'')||'|'||
      coalesce(to_char(valid_until at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'')||'|'||reason
    from permission_catalog_revision_aliases where revision_id='00000000-0000-0000-0000-000000000017'::uuid)
  select 'sha256:'||encode(sha256(convert_to(coalesce(string_agg(line,E'\n' order by line),''),'UTF8')),'hex') from catalog_lines),
  published_at=now(),published_by='rbac-convergence-r3',version=version+1
where revision_id='00000000-0000-0000-0000-000000000017'::uuid and status='DRAFT';

update permission_catalog_active_revision
set revision_id='00000000-0000-0000-0000-000000000017'::uuid,
    activated_at=now(),activated_by='rbac-convergence-r3',version=version+1
where singleton_id='ACTIVE';

insert into permission_catalog_publication_events(
  publication_id,revision_id,previous_revision_id,content_hash,entry_count,alias_count,
  actor_id,audit_reason,correlation_id,published_at)
select '00000000-0000-0000-0000-000000005017'::uuid,r.revision_id,r.supersedes_revision_id,r.content_hash,
  (select count(*)::integer from permission_catalog_revision_entries e where e.revision_id=r.revision_id),
  (select count(*)::integer from permission_catalog_revision_aliases a where a.revision_id=r.revision_id),
  'rbac-convergence-r3','R3 Role Responsibility Scope publication','rbac-convergence-r3',coalesce(r.published_at,now())
from permission_catalog_revisions r
where r.revision_id='00000000-0000-0000-0000-000000000017'::uuid
on conflict(publication_id) do nothing;

-- ---------------------------------------------------------------------------
-- Canonical scope containment used by hardening and authorization.
-- ---------------------------------------------------------------------------
create or replace function r7_rbac_scopes_overlap(
  p_tenant_id varchar,p_left_type varchar,p_left_id varchar,p_right_type varchar,p_right_id varchar)
returns boolean language sql stable as $$
  select case
    when p_left_type='INSTANCE' or p_right_type='INSTANCE' then p_left_type=p_right_type
    when p_left_type='TENANT' or p_right_type='TENANT' then true
    when p_left_type='GROUP' or p_right_type='GROUP' then
      p_left_type='GROUP' and p_right_type='GROUP' and p_left_id=p_right_id
    when p_left_type in('DEPARTMENT','DEPARTMENT_SUBTREE') and p_right_type in('DEPARTMENT','DEPARTMENT_SUBTREE') then
      case
        when p_left_type='DEPARTMENT' and p_right_type='DEPARTMENT' then p_left_id=p_right_id
        when p_left_type='DEPARTMENT_SUBTREE' and p_right_type='DEPARTMENT' then exists(
          select 1 from org_department_closure c where c.tenant_id=p_tenant_id
            and c.ancestor_department_id=p_left_id and c.descendant_department_id=p_right_id)
        when p_left_type='DEPARTMENT' and p_right_type='DEPARTMENT_SUBTREE' then exists(
          select 1 from org_department_closure c where c.tenant_id=p_tenant_id
            and c.ancestor_department_id=p_right_id and c.descendant_department_id=p_left_id)
        else exists(
          select 1 from org_department_closure c where c.tenant_id=p_tenant_id and
            ((c.ancestor_department_id=p_left_id and c.descendant_department_id=p_right_id)
              or (c.ancestor_department_id=p_right_id and c.descendant_department_id=p_left_id)))
      end
    else false end
$$;

create or replace function r7_rbac_scope_contains(
  p_tenant_id varchar,p_container_type varchar,p_container_id varchar,p_candidate_type varchar,p_candidate_id varchar)
returns boolean language sql stable as $$
  select case
    when p_container_type='INSTANCE' then true
    when p_container_type='TENANT' then p_candidate_type in('TENANT','DEPARTMENT','DEPARTMENT_SUBTREE','GROUP')
    when p_container_type='GROUP' then p_candidate_type='GROUP' and p_container_id=p_candidate_id
    when p_container_type='DEPARTMENT' then p_candidate_type='DEPARTMENT' and p_container_id=p_candidate_id
    when p_container_type='DEPARTMENT_SUBTREE' then p_candidate_type in('DEPARTMENT','DEPARTMENT_SUBTREE') and exists(
      select 1 from org_department_closure c where c.tenant_id=p_tenant_id
        and c.ancestor_department_id=p_container_id and c.descendant_department_id=p_candidate_id)
    else false end
$$;

-- ---------------------------------------------------------------------------
-- Effective-principal helper: users affected by a Role recipient.
-- Parent Group bindings affect members of descendant groups. Department bindings
-- affect active users in the Department subtree.
-- ---------------------------------------------------------------------------
create or replace function r3_users_for_rbac_principal(
  p_tenant_id varchar,p_principal_type varchar,p_principal_id varchar,p_at timestamptz)
returns table(user_id varchar) language sql volatile as $$
  select p_principal_id where p_principal_type='USER'
  union
  select dm.user_id
  from org_department_memberships dm
  join departments root_department on root_department.tenant_id=dm.tenant_id
    and root_department.department_id=p_principal_id and root_department.status='ACTIVE'
  join departments member_department on member_department.tenant_id=dm.tenant_id
    and member_department.department_id=dm.department_id and member_department.status='ACTIVE'
  join org_department_closure dc on dc.tenant_id=dm.tenant_id and dc.descendant_department_id=dm.department_id
  where p_principal_type='DEPARTMENT' and dm.tenant_id=p_tenant_id
    and dc.ancestor_department_id=p_principal_id and dm.status='ACTIVE'
    and dm.effective_at<=p_at and (dm.expires_at is null or dm.expires_at>p_at)
  union
  select gm.user_id
  from org_group_memberships gm
  where p_principal_type='GROUP' and gm.tenant_id=p_tenant_id and gm.status='ACTIVE'
    and gm.effective_at<=p_at and (gm.expires_at is null or gm.expires_at>p_at)
    and gm.group_id in(
      with recursive group_subtree(group_id) as (
        select root_group.group_id from organization_groups root_group
        where root_group.tenant_id=p_tenant_id and root_group.group_id=p_principal_id and root_group.status='ACTIVE'
        union
        select child.group_id from organization_groups child
        join group_subtree parent on child.parent_group_id=parent.group_id
        where child.tenant_id=p_tenant_id and child.status='ACTIVE')
      select group_id from group_subtree)
$$;

create or replace function r3_user_has_rbac_principal(
  p_tenant_id varchar,p_user_id varchar,p_principal_type varchar,p_principal_id varchar,p_at timestamptz)
returns boolean language sql volatile as $$
  select exists(select 1 from r3_users_for_rbac_principal(p_tenant_id,p_principal_type,p_principal_id,p_at) x where x.user_id=p_user_id)
$$;

-- Principals contributed by one organization membership. Used for self-binding defense
-- before an organization relationship can become a privilege-escalation side door.
create or replace function r3_principals_for_membership(
  p_tenant_id varchar,p_membership_type varchar,p_resource_id varchar)
returns table(principal_type varchar,principal_id varchar) language sql volatile as $$
  with recursive group_principals(group_id) as (
    select root_group.group_id from organization_groups root_group
    where p_membership_type='GROUP' and root_group.tenant_id=p_tenant_id
      and root_group.group_id=p_resource_id and root_group.status='ACTIVE'
    union
    select parent_group.group_id
    from group_principals child
    join organization_groups child_group on child_group.tenant_id=p_tenant_id
      and child_group.group_id=child.group_id and child_group.status='ACTIVE'
    join organization_groups parent_group on parent_group.tenant_id=child_group.tenant_id
      and parent_group.group_id=child_group.parent_group_id and parent_group.status='ACTIVE'
  ), department_principals(department_id) as (
    select closure.ancestor_department_id
    from org_department_closure closure
    join departments direct_department on direct_department.tenant_id=closure.tenant_id
      and direct_department.department_id=p_resource_id and direct_department.status='ACTIVE'
    join departments ancestor_department on ancestor_department.tenant_id=closure.tenant_id
      and ancestor_department.department_id=closure.ancestor_department_id and ancestor_department.status='ACTIVE'
    where p_membership_type='DEPARTMENT' and closure.tenant_id=p_tenant_id
      and closure.descendant_department_id=p_resource_id
  )
  select 'DEPARTMENT'::varchar,department_id from department_principals
  union all
  select 'GROUP'::varchar,group_id from group_principals
$$;

-- Validate the post-mutation effective access of one Human User. This is deliberately
-- evaluated after organization changes so moved memberships / hierarchy changes cannot
-- evade Separation-of-Duties by relying on the pre-update organization snapshot.
create or replace function r3_assert_user_effective_sod(
  p_tenant_id varchar,p_user_id varchar,p_at timestamptz)
returns void language plpgsql volatile as $$
declare left_binding record; right_binding record; sod record;
begin
  for left_binding in
    select b.* from rbac_principal_role_bindings b
    where b.tenant_id=p_tenant_id and b.status='ACTIVE' and b.effective_at<=p_at
      and (b.expires_at is null or b.expires_at>p_at)
      and r3_user_has_rbac_principal(b.tenant_id,p_user_id,b.principal_type,b.principal_id,p_at)
  loop
    for right_binding in
      select b.* from rbac_principal_role_bindings b
      where b.tenant_id=p_tenant_id and b.binding_id>left_binding.binding_id
        and b.status='ACTIVE' and b.effective_at<=p_at and (b.expires_at is null or b.expires_at>p_at)
        and r3_user_has_rbac_principal(b.tenant_id,p_user_id,b.principal_type,b.principal_id,p_at)
    loop
      for sod in select * from rbac_separation_of_duties_rules r
        where r.status='ACTIVE' and (r.tenant_id is null or r.tenant_id=p_tenant_id)
          and ((r.left_role_id=left_binding.role_id and r.right_role_id=right_binding.role_id)
            or (r.right_role_id=left_binding.role_id and r.left_role_id=right_binding.role_id))
      loop
        if not sod.scope_overlap_required or r7_rbac_scopes_overlap(
            p_tenant_id,left_binding.scope_type,left_binding.scope_id,right_binding.scope_type,right_binding.scope_id) then
          raise exception using errcode='23514',message='ROLE_SEPARATION_OF_DUTIES_CONFLICT';
        end if;
      end loop;
    end loop;
  end loop;
end $$;

-- Replace R7 SoD validation with hierarchy-aware principal expansion.
create or replace function r7_validate_effective_separation_of_duties() returns trigger language plpgsql as $$
declare affected_user varchar(128);
begin
  if new.status<>'ACTIVE' or new.effective_at>now() or (new.expires_at is not null and new.expires_at<=now()) then return new; end if;
  for affected_user in select user_id from r3_users_for_rbac_principal(new.tenant_id,new.principal_type,new.principal_id,now())
  loop
    perform r3_assert_user_effective_sod(new.tenant_id,affected_user,now());
  end loop;
  return new;
end $$;

drop trigger if exists trg_r7_effective_sod on rbac_principal_role_bindings;
create trigger trg_r7_effective_sod after insert or update of principal_type,principal_id,role_id,scope_type,scope_id,status,effective_at,expires_at
on rbac_principal_role_bindings for each row execute function r7_validate_effective_separation_of_duties();

-- Database final defense against direct or inherited self-binding. The application performs
-- the same check through Principal Expansion; this protects direct SQL and alternate adapters.
create or replace function r7_reject_direct_self_binding() returns trigger language plpgsql as $$
declare actor varchar(128):=nullif(current_setting('app.current_actor_id',true),'');
begin
  if new.status<>'ACTIVE' then return new; end if;
  if actor is not null and (
      (new.principal_type='USER' and new.principal_id=actor)
      or (new.principal_type in('DEPARTMENT','GROUP') and r3_user_has_rbac_principal(
        new.tenant_id,actor,new.principal_type,new.principal_id,coalesce(new.effective_at,now())))) then
    raise exception using errcode='23514',message='RBAC_SELF_BINDING_FORBIDDEN';
  end if;
  return new;
end $$;
drop trigger if exists trg_r7_direct_self_binding on rbac_principal_role_bindings;
create trigger trg_r7_direct_self_binding before insert or update of principal_type,principal_id,role_id,scope_type,scope_id,status,effective_at,expires_at on rbac_principal_role_bindings
for each row execute function r7_reject_direct_self_binding();

-- Membership changes are authorization mutations. Run after the row mutation so the canonical
-- Principal Expansion sees the new organization state; any exception rolls the statement back.
create or replace function r3_validate_membership_effective_access() returns trigger language plpgsql as $$
declare
  actor varchar(128):=nullif(current_setting('app.current_actor_id',true),'');
  membership_type varchar(32):=case when tg_table_name='org_group_memberships' then 'GROUP' else 'DEPARTMENT' end;
  resource_id varchar(128):=case when tg_table_name='org_group_memberships' then new.group_id else new.department_id end;
begin
  if new.status<>'ACTIVE' or new.effective_at>now() or (new.expires_at is not null and new.expires_at<=now()) then return new; end if;
  if actor is not null and actor=new.user_id and exists(
    select 1 from rbac_principal_role_bindings b
    join r3_principals_for_membership(new.tenant_id,membership_type,resource_id) p
      on p.principal_type=b.principal_type and p.principal_id=b.principal_id
    where b.tenant_id=new.tenant_id and b.status='ACTIVE' and b.effective_at<=now()
      and (b.expires_at is null or b.expires_at>now())) then
    raise exception using errcode='23514',message='RBAC_SELF_BINDING_FORBIDDEN';
  end if;
  perform r3_assert_user_effective_sod(new.tenant_id,new.user_id,now());
  return new;
end $$;

drop trigger if exists trg_r7_group_membership_effective_access on org_group_memberships;
create trigger trg_r7_group_membership_effective_access
after insert or update of user_id,group_id,status,effective_at,expires_at on org_group_memberships
for each row execute function r3_validate_membership_effective_access();

drop trigger if exists trg_r3_department_membership_effective_access on org_department_memberships;
create trigger trg_r3_department_membership_effective_access
after insert or update of user_id,department_id,status,effective_at,expires_at on org_department_memberships
for each row execute function r3_validate_membership_effective_access();

-- Moving a Group or Department under a privileged parent changes inherited Principals without
-- touching membership rows. Validate affected users after the hierarchy mutation. Department
-- closure is rebuilt by trg_phase1a2_department_closure_rebuild before this R3 trigger runs.
create or replace function r3_validate_group_hierarchy_effective_access() returns trigger language plpgsql as $$
declare affected_user varchar(128); actor varchar(128):=nullif(current_setting('app.current_actor_id',true),'');
begin
  if new.status<>'ACTIVE' or (old.parent_group_id is not distinct from new.parent_group_id and old.status is not distinct from new.status) then return new; end if;
  for affected_user in select user_id from r3_users_for_rbac_principal(new.tenant_id,'GROUP',new.group_id,now())
  loop
    if actor is not null and actor=affected_user and exists(
      with recursive new_chain(group_id) as (
        select new.group_id
        union
        select parent_group.group_id from new_chain child
        join organization_groups child_group on child_group.tenant_id=new.tenant_id and child_group.group_id=child.group_id
        join organization_groups parent_group on parent_group.tenant_id=child_group.tenant_id and parent_group.group_id=child_group.parent_group_id and parent_group.status='ACTIVE'
      ), old_parents(group_id) as (
        select parent.group_id from organization_groups parent
        where old.status='ACTIVE' and old.parent_group_id is not null and parent.tenant_id=new.tenant_id and parent.group_id=old.parent_group_id and parent.status='ACTIVE'
        union
        select parent_group.group_id from old_parents child
        join organization_groups child_group on child_group.tenant_id=new.tenant_id and child_group.group_id=child.group_id
        join organization_groups parent_group on parent_group.tenant_id=child_group.tenant_id and parent_group.group_id=child_group.parent_group_id and parent_group.status='ACTIVE'
      ), old_chain(group_id) as (
        select old.group_id where old.status='ACTIVE'
        union select group_id from old_parents
      ), introduced(group_id) as (
        select group_id from new_chain except select group_id from old_chain
      )
      select 1 from rbac_principal_role_bindings b
      where b.tenant_id=new.tenant_id and b.principal_type='GROUP' and b.status='ACTIVE'
        and b.effective_at<=now() and (b.expires_at is null or b.expires_at>now())
        and b.principal_id in(select group_id from introduced)) then
      raise exception using errcode='23514',message='RBAC_SELF_BINDING_FORBIDDEN';
    end if;
    perform r3_assert_user_effective_sod(new.tenant_id,affected_user,now());
  end loop;
  return new;
end $$;

drop trigger if exists trg_r3_group_hierarchy_effective_access on organization_groups;
create trigger trg_r3_group_hierarchy_effective_access
after update of parent_group_id,status on organization_groups
for each row execute function r3_validate_group_hierarchy_effective_access();

create or replace function r3_validate_department_hierarchy_effective_access() returns trigger language plpgsql as $$
declare affected_user varchar(128); actor varchar(128):=nullif(current_setting('app.current_actor_id',true),'');
begin
  if new.status<>'ACTIVE' or (old.parent_department_id is not distinct from new.parent_department_id and old.status is not distinct from new.status) then return new; end if;
  for affected_user in select user_id from r3_users_for_rbac_principal(new.tenant_id,'DEPARTMENT',new.department_id,now())
  loop
    if actor is not null and actor=affected_user and exists(
      with new_chain(department_id) as (
        select c.ancestor_department_id from org_department_closure c
        join departments d on d.tenant_id=c.tenant_id and d.department_id=c.ancestor_department_id and d.status='ACTIVE'
        where c.tenant_id=new.tenant_id and c.descendant_department_id=new.department_id
      ), old_chain(department_id) as (
        select old.department_id where old.status='ACTIVE'
        union
        select c.ancestor_department_id from org_department_closure c
        join departments d on d.tenant_id=c.tenant_id and d.department_id=c.ancestor_department_id and d.status='ACTIVE'
        where old.status='ACTIVE' and old.parent_department_id is not null
          and c.tenant_id=new.tenant_id and c.descendant_department_id=old.parent_department_id
      ), introduced(department_id) as (
        select department_id from new_chain except select department_id from old_chain
      )
      select 1 from rbac_principal_role_bindings b
      where b.tenant_id=new.tenant_id and b.principal_type='DEPARTMENT' and b.status='ACTIVE'
        and b.effective_at<=now() and (b.expires_at is null or b.expires_at>now())
        and b.principal_id in(select department_id from introduced)) then
      raise exception using errcode='23514',message='RBAC_SELF_BINDING_FORBIDDEN';
    end if;
    perform r3_assert_user_effective_sod(new.tenant_id,affected_user,now());
  end loop;
  return new;
end $$;

drop trigger if exists trg_r3_department_hierarchy_effective_access on departments;
create trigger trg_r3_department_hierarchy_effective_access
after update of parent_department_id,status on departments
for each row execute function r3_validate_department_hierarchy_effective_access();

create or replace function r3_invalidate_organization_authorization() returns trigger language plpgsql as $$
declare
  affected_tenant varchar(64):=case when tg_op='DELETE' then old.tenant_id else new.tenant_id end;
  actor varchar(128):=coalesce(nullif(current_setting('app.current_actor_id',true),''),'rbac-convergence-r3');
begin
  perform phase1a4_increment_policy_version(affected_tenant,actor);
  if tg_op='DELETE' then return old; end if;
  return new;
end $$;

drop trigger if exists trg_r3_department_membership_authorization_version on org_department_memberships;
create trigger trg_r3_department_membership_authorization_version
after insert or update or delete on org_department_memberships
for each row execute function r3_invalidate_organization_authorization();

-- Parent Group changes alter every descendant member's Principal Expansion.
drop trigger if exists trg_r3_group_hierarchy_authorization_version on organization_groups;
create trigger trg_r3_group_hierarchy_authorization_version
after update of parent_group_id,status on organization_groups
for each row when(old.parent_group_id is distinct from new.parent_group_id or old.status is distinct from new.status)
execute function r3_invalidate_organization_authorization();

-- Department hierarchy/status changes also alter inherited Department Principals.
drop trigger if exists trg_r3_department_hierarchy_authorization_version on departments;
create trigger trg_r3_department_hierarchy_authorization_version
after update of parent_department_id,status on departments
for each row when(old.parent_department_id is distinct from new.parent_department_id or old.status is distinct from new.status)
execute function r3_invalidate_organization_authorization();

-- Scope/principal semantics change authorization outcomes for the whole Tenant cache.
update rbac_policy_versions set policy_version=policy_version+1,updated_at=now(),updated_by='rbac-convergence-r3';
