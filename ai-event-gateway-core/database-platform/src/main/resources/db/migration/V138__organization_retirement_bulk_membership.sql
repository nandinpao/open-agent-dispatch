-- RC0 Fix5: enterprise organization retirement and bulk membership convergence.
-- People identities survive Department / Group retirement. Organizational relationships and
-- inherited RBAC authority end atomically; governed business-resource ownership remains a hard blocker.

select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','rc0-fix5-organization-retirement',true);

-- A Person may be temporarily Unassigned. There may be zero or one active Primary Department,
-- never more than one. This supports organization restructuring without deleting the Person.
create or replace function p2_validate_department_membership_integrity()
returns trigger language plpgsql security invoker as $$
declare t varchar(64); u varchar(128); primary_count bigint;
begin
  if tg_op='DELETE' then t:=old.tenant_id; u:=old.user_id; else t:=new.tenant_id; u:=new.user_id; end if;
  select count(*) filter(where is_primary) into primary_count
  from org_department_memberships where tenant_id=t and user_id=u and status='ACTIVE'
    and effective_at<=now() and (expires_at is null or expires_at>now());
  if primary_count>1 then raise exception 'PRIMARY_DEPARTMENT_CONFLICT' using errcode='23514'; end if;
  if exists(select 1 from departments d where d.tenant_id=t and d.manager_user_id=u and d.status='ACTIVE'
    and not exists(select 1 from org_department_memberships dm where dm.tenant_id=d.tenant_id and dm.department_id=d.department_id
      and dm.user_id=u and dm.status='ACTIVE' and dm.effective_at<=now() and (dm.expires_at is null or dm.expires_at>now())))
  then raise exception 'OFFICIAL_MANAGER_DEPARTMENT_MEMBERSHIP_REQUIRED' using errcode='23514'; end if;
  if tg_op='DELETE' then return old; else return new; end if;
end $$;

-- Disabled organization objects may retain people/hierarchy relationships. Their Department/Group
-- principals are already excluded from effective principal expansion while status != ACTIVE.
-- Business-resource ownership remains protected by the P2.3B resource-scope guards.
create or replace function p2_validate_department_integrity()
returns trigger language plpgsql security invoker as $$
declare cursor_id varchar(128); next_id varchar(128); depth integer:=0;
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
  if new.manager_user_id is not null and new.status='ACTIVE' then
    if not exists(select 1 from iam_users u join org_tenant_memberships tm on tm.user_id=u.user_id and tm.tenant_id=new.tenant_id
      where u.user_id=new.manager_user_id and u.status='ACTIVE' and tm.status='ACTIVE' and tm.joined_at<=now() and (tm.expires_at is null or tm.expires_at>now()))
    then raise exception 'OFFICIAL_MANAGER_TENANT_MEMBERSHIP_REQUIRED' using errcode='23514'; end if;
    if not exists(select 1 from org_department_memberships dm where dm.tenant_id=new.tenant_id and dm.department_id=new.department_id
      and dm.user_id=new.manager_user_id and dm.status='ACTIVE' and dm.membership_type in('MEMBER','DELEGATE')
      and dm.effective_at<=now() and (dm.expires_at is null or dm.expires_at>now()))
    then raise exception 'OFFICIAL_MANAGER_DEPARTMENT_MEMBERSHIP_REQUIRED' using errcode='23514'; end if;
  end if;
  return new;
end $$;

create or replace function p2_validate_group_integrity()
returns trigger language plpgsql security invoker as $$
declare cursor_id varchar(128); next_id varchar(128); depth integer:=0;
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
  return new;
end $$;

-- Logical Department delete becomes a governed retirement operation:
--  * child Departments are re-parented to the retiring Department's parent;
--  * Groups owned by the Department inherit that parent owner (or become unowned at top level);
--  * Department memberships are ended, leaving People Unassigned when no other Primary exists;
--  * all Department-principal and Department-scoped Role Bindings are revoked.
-- P2.3B resource/A2A triggers execute independently and still block unresolved governed resources.
create or replace function r6_guard_department_logical_delete() returns trigger language plpgsql as $$
declare actor varchar(256);
begin
  if new.status='DELETED' and old.status<>'DELETED' then
    actor:=coalesce(nullif(current_setting('app.current_actor_id',true),''),new.updated_by,'organization-retirement');

    update departments child
       set parent_department_id=old.parent_department_id,
           updated_at=now(),updated_by=actor,version=child.version+1
     where child.tenant_id=new.tenant_id and child.parent_department_id=new.department_id
       and child.status<>'DELETED';

    update organization_groups g
       set owner_department_id=(
             select case when p.status='ACTIVE' then p.department_id else null end
               from departments p
              where p.tenant_id=new.tenant_id and p.department_id=old.parent_department_id
           ),
           updated_at=now(),updated_by=actor,version=g.version+1
     where g.tenant_id=new.tenant_id and g.owner_department_id=new.department_id
       and g.status<>'DELETED';

    update org_department_memberships m
       set status='REMOVED',is_primary=false,version=m.version+1
     where m.tenant_id=new.tenant_id and m.department_id=new.department_id
       and m.status<>'REMOVED';

    update rbac_principal_role_bindings b
       set status='REVOKED',revoked_at=coalesce(b.revoked_at,now()),revoked_by=actor,
           revoke_reason=coalesce(nullif(b.revoke_reason,''),'Organization scope retired'),version=b.version+1
     where b.tenant_id=new.tenant_id and b.status='ACTIVE'
       and ((b.principal_type='DEPARTMENT' and b.principal_id=new.department_id)
         or (b.scope_type in('DEPARTMENT','DEPARTMENT_SUBTREE') and b.scope_id=new.department_id));

    update departments retired
       set manager_user_id=null,updated_at=now(),updated_by=actor,version=retired.version+1
     where retired.tenant_id=new.tenant_id and retired.department_id=new.department_id
       and retired.manager_user_id is not null;
  end if;
  if old.status='DELETED' and new.status<>'DELETED' then
    raise exception 'DEPARTMENT_DELETED: deleted Departments cannot be reactivated' using errcode='23514';
  end if;
  return new;
end $$;

-- Logical Group delete follows the same identity-preserving model.
create or replace function r6_guard_group_logical_delete() returns trigger language plpgsql as $$
declare actor varchar(256);
begin
  if new.status='DELETED' and old.status<>'DELETED' then
    actor:=coalesce(nullif(current_setting('app.current_actor_id',true),''),new.updated_by,'organization-retirement');

    update organization_groups child
       set parent_group_id=old.parent_group_id,
           updated_at=now(),updated_by=actor,version=child.version+1
     where child.tenant_id=new.tenant_id and child.parent_group_id=new.group_id
       and child.status<>'DELETED';

    update org_group_memberships m
       set status='REMOVED',version=m.version+1
     where m.tenant_id=new.tenant_id and m.group_id=new.group_id
       and m.status<>'REMOVED';

    update rbac_principal_role_bindings b
       set status='REVOKED',revoked_at=coalesce(b.revoked_at,now()),revoked_by=actor,
           revoke_reason=coalesce(nullif(b.revoke_reason,''),'Organization scope retired'),version=b.version+1
     where b.tenant_id=new.tenant_id and b.status='ACTIVE'
       and ((b.principal_type='GROUP' and b.principal_id=new.group_id)
         or (b.scope_type='GROUP' and b.scope_id=new.group_id));
  end if;
  if old.status='DELETED' and new.status<>'DELETED' then
    raise exception 'GROUP_DELETED: deleted Groups cannot be reactivated' using errcode='23514';
  end if;
  return new;
end $$;

comment on function r6_guard_department_logical_delete() is
'RC0 Fix5 Department retirement: preserve People identity, end memberships/access, re-parent hierarchy; P2.3B governed resources remain hard blockers.';
comment on function r6_guard_group_logical_delete() is
'RC0 Fix5 Group retirement: preserve People identity, end memberships/access and re-parent child Groups; P2.3B governed resources remain hard blockers.';


-- V133 created these lifecycle triggers as BEFORE triggers. Retirement cleanup must occur only
-- after the status transition has passed the P2.3B governed-resource guards and the Department /
-- Group row is no longer ACTIVE. This ordering also lets existing membership integrity checks see
-- the retired organization state while memberships are ended.
drop trigger if exists trg_r6_guard_department_logical_delete on departments;
create trigger trg_r6_guard_department_logical_delete
after update of status on departments
for each row execute function r6_guard_department_logical_delete();

drop trigger if exists trg_r6_guard_group_logical_delete on organization_groups;
create trigger trg_r6_guard_group_logical_delete
after update of status on organization_groups
for each row execute function r6_guard_group_logical_delete();
