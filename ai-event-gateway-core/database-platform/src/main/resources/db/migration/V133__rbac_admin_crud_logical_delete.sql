-- RBAC Convergence R6 Build Fix 9: complete beginner-facing CRUD with governed logical delete.
-- Governed IAM objects are tombstoned as DELETED instead of physically removed so audit evidence,
-- historical memberships and Role Binding provenance remain explainable.
select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','rbac-convergence-r6-build-fix9',true);

alter table departments drop constraint if exists ck_departments_phase1_status;
alter table departments add constraint ck_departments_phase1_status
  check (status in ('ACTIVE','DISABLED','DELETED')) not valid;
alter table departments validate constraint ck_departments_phase1_status;

alter table organization_groups drop constraint if exists ck_groups_phase1_status;
alter table organization_groups add constraint ck_groups_phase1_status
  check (status in ('ACTIVE','DISABLED','DELETED')) not valid;
alter table organization_groups validate constraint ck_groups_phase1_status;

alter table rbac_roles drop constraint if exists ck_rbac_role_status;
alter table rbac_roles add constraint ck_rbac_role_status
  check (status in ('ACTIVE','DISABLED','DELETED')) not valid;
alter table rbac_roles validate constraint ck_rbac_role_status;

alter table org_department_revisions drop constraint if exists ck_org_department_revision_change_type;
alter table org_department_revisions add constraint ck_org_department_revision_change_type
  check (change_type in ('CREATED','UPDATED','RENAMED','CODE_CHANGED','MOVED','MERGED','SPLIT','DISABLED','REACTIVATED','DELETED')) not valid;
alter table org_department_revisions validate constraint ck_org_department_revision_change_type;

create or replace function r6_guard_department_logical_delete() returns trigger language plpgsql as $$
begin
  if new.status='DELETED' and old.status<>'DELETED' then
    if exists (
      select 1 from departments d
      where d.tenant_id=new.tenant_id and d.parent_department_id=new.department_id and d.status<>'DELETED'
    ) then raise exception 'DEPARTMENT_DELETE_BLOCKED: move or delete child Departments first' using errcode='23514'; end if;
    if exists (
      select 1 from org_department_memberships m
      where m.tenant_id=new.tenant_id and m.department_id=new.department_id
        and m.status in ('INVITED','ACTIVE','SUSPENDED')
    ) then raise exception 'DEPARTMENT_DELETE_BLOCKED: move or remove Department members first' using errcode='23514'; end if;
    if exists (
      select 1 from organization_groups g
      where g.tenant_id=new.tenant_id and g.owner_department_id=new.department_id and g.status<>'DELETED'
    ) then raise exception 'DEPARTMENT_DELETE_BLOCKED: move or delete owned Groups first' using errcode='23514'; end if;
    if exists (
      select 1 from rbac_principal_role_bindings b
      where b.tenant_id=new.tenant_id and b.status='ACTIVE'
        and ((b.principal_type='DEPARTMENT' and b.principal_id=new.department_id)
          or (b.scope_type in ('DEPARTMENT','DEPARTMENT_SUBTREE') and b.scope_id=new.department_id))
    ) then raise exception 'DEPARTMENT_DELETE_BLOCKED: revoke active access assignments first' using errcode='23514'; end if;
  end if;
  if old.status='DELETED' and new.status<>'DELETED' then
    raise exception 'DEPARTMENT_DELETED: deleted Departments cannot be reactivated' using errcode='23514';
  end if;
  return new;
end $$;

drop trigger if exists trg_r6_guard_department_logical_delete on departments;
create trigger trg_r6_guard_department_logical_delete
before update of status on departments
for each row execute function r6_guard_department_logical_delete();

create or replace function r6_guard_group_logical_delete() returns trigger language plpgsql as $$
begin
  if new.status='DELETED' and old.status<>'DELETED' then
    if exists (
      select 1 from organization_groups g
      where g.tenant_id=new.tenant_id and g.parent_group_id=new.group_id and g.status<>'DELETED'
    ) then raise exception 'GROUP_DELETE_BLOCKED: move or delete child Groups first' using errcode='23514'; end if;
    if exists (
      select 1 from org_group_memberships m
      where m.tenant_id=new.tenant_id and m.group_id=new.group_id
        and m.status in ('INVITED','ACTIVE','SUSPENDED')
    ) then raise exception 'GROUP_DELETE_BLOCKED: remove Group members first' using errcode='23514'; end if;
    if exists (
      select 1 from rbac_principal_role_bindings b
      where b.tenant_id=new.tenant_id and b.status='ACTIVE'
        and ((b.principal_type='GROUP' and b.principal_id=new.group_id)
          or (b.scope_type='GROUP' and b.scope_id=new.group_id))
    ) then raise exception 'GROUP_DELETE_BLOCKED: revoke active access assignments first' using errcode='23514'; end if;
  end if;
  if old.status='DELETED' and new.status<>'DELETED' then
    raise exception 'GROUP_DELETED: deleted Groups cannot be reactivated' using errcode='23514';
  end if;
  return new;
end $$;

drop trigger if exists trg_r6_guard_group_logical_delete on organization_groups;
create trigger trg_r6_guard_group_logical_delete
before update of status on organization_groups
for each row execute function r6_guard_group_logical_delete();

create or replace function r6_guard_role_logical_delete() returns trigger language plpgsql as $$
begin
  if new.status='DELETED' and old.status<>'DELETED' then
    if new.system_managed then
      raise exception 'ROLE_DELETE_BLOCKED: system-managed Responsibilities cannot be deleted' using errcode='23514';
    end if;
    if exists (
      select 1 from rbac_principal_role_bindings b
      where b.role_id=new.role_id and b.status='ACTIVE'
    ) then raise exception 'ROLE_DELETE_BLOCKED: revoke active assignments first' using errcode='23514'; end if;
  end if;
  if old.status='DELETED' and new.status<>'DELETED' then
    raise exception 'ROLE_DELETED: deleted Responsibilities cannot be reactivated' using errcode='23514';
  end if;
  return new;
end $$;

drop trigger if exists trg_r6_guard_role_logical_delete on rbac_roles;
create trigger trg_r6_guard_role_logical_delete
before update of status on rbac_roles
for each row execute function r6_guard_role_logical_delete();
