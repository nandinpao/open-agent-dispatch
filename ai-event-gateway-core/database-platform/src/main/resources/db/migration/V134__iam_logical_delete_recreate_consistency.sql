-- OpenDispatch v17: logical-delete recreation, Tenant readmission and offboarding consistency.
-- Keep immutable audit/evidence rows while ensuring deleted business objects do not poison
-- future creation and removed Tenant users cannot silently regain stale organization/access state.
select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','v17-lifecycle-consistency',true);

-- Department / Group logical delete keeps the internal ID tombstone, but a deleted
-- business code must not permanently block creation of a replacement object.
alter table departments
  drop constraint if exists departments_tenant_id_department_code_key;
create unique index if not exists uq_departments_current_code
  on departments(tenant_id,department_code)
  where status <> 'DELETED';

alter table organization_groups
  drop constraint if exists organization_groups_tenant_id_group_code_key;
create unique index if not exists uq_organization_groups_current_code
  on organization_groups(tenant_id,group_code)
  where status <> 'DELETED';

-- Department / Group membership history may contain a REMOVED relationship followed by a
-- later re-admission. Replace the original all-history uniqueness with current-row uniqueness.
do $$
declare constraint_name text;
begin
  select c.conname into constraint_name
  from pg_constraint c
  where c.conrelid='org_department_memberships'::regclass and c.contype='u'
    and (select array_agg(a.attname::text order by u.ord)
         from unnest(c.conkey) with ordinality u(attnum,ord)
         join pg_attribute a on a.attrelid=c.conrelid and a.attnum=u.attnum)
        = array['tenant_id','user_id','department_id','membership_type']::text[]
  limit 1;
  if constraint_name is not null then
    execute format('alter table org_department_memberships drop constraint %I',constraint_name);
  end if;

  select c.conname into constraint_name
  from pg_constraint c
  where c.conrelid='org_group_memberships'::regclass and c.contype='u'
    and (select array_agg(a.attname::text order by u.ord)
         from unnest(c.conkey) with ordinality u(attnum,ord)
         join pg_attribute a on a.attrelid=c.conrelid and a.attnum=u.attnum)
        = array['tenant_id','user_id','group_id']::text[]
  limit 1;
  if constraint_name is not null then
    execute format('alter table org_group_memberships drop constraint %I',constraint_name);
  end if;
end $$;

create unique index if not exists uq_org_department_memberships_current_relation
  on org_department_memberships(tenant_id,user_id,department_id,membership_type)
  where status <> 'REMOVED';
create unique index if not exists uq_org_group_memberships_current_relation
  on org_group_memberships(tenant_id,user_id,group_id)
  where status <> 'REMOVED';

-- REMOVED Tenant membership remains the same aggregate row because Department / Group tables
-- reference (tenant_id,user_id). Re-admission is therefore a governed REMOVED -> ACTIVE lifecycle
-- transition, not a second Tenant Membership row. The application exposes this only through
-- existing-person onboarding; ordinary status mutation still treats REMOVED as terminal.
create or replace function phase5d_validate_tenant_membership_transition()
returns trigger language plpgsql as $$
begin
  if tg_op='UPDATE' then
    if old.status<>new.status and not (
      (old.status='INVITED' and new.status in('ACTIVE','SUSPENDED','EXPIRED','REMOVED')) or
      (old.status='ACTIVE' and new.status in('SUSPENDED','EXPIRED','REMOVED')) or
      (old.status='SUSPENDED' and new.status in('ACTIVE','EXPIRED','REMOVED')) or
      (old.status='EXPIRED' and new.status in('ACTIVE','REMOVED')) or
      (old.status='REMOVED' and new.status='ACTIVE')
    ) then
      raise exception 'IDENTITY_TENANT_MEMBERSHIP_TRANSITION_INVALID' using errcode='23514';
    end if;
  end if;
  if new.status='ACTIVE' and new.expires_at is not null and new.expires_at<=now() then
    raise exception 'IDENTITY_TENANT_MEMBERSHIP_EXPIRED' using errcode='23514';
  end if;
  if new.default_tenant and new.status<>'ACTIVE' then
    raise exception 'IDENTITY_DEFAULT_TENANT_REQUIRES_ACTIVE_MEMBERSHIP' using errcode='23514';
  end if;
  if new.status='INVITED' then new.invited_at=coalesce(new.invited_at,now()); end if;
  if new.status='ACTIVE' then
    if tg_op='UPDATE' and old.status='REMOVED' then
      new.activated_at=now();
      new.removed_at=null;
    else
      new.activated_at=coalesce(new.activated_at,now());
    end if;
  end if;
  if new.status='SUSPENDED' then new.suspended_at=coalesce(new.suspended_at,now()); new.default_tenant=false; end if;
  if new.status='EXPIRED' then new.expired_at=coalesce(new.expired_at,now()); new.default_tenant=false; end if;
  if new.status='REMOVED' then new.removed_at=coalesce(new.removed_at,now()); new.default_tenant=false; end if;
  return new;
end $$;

-- Removing a person from a workspace is an authorization boundary, not merely a directory filter.
-- Organization relationships become historical and direct User Role Bindings are revoked in the
-- same transaction, preventing a later re-admission from silently restoring stale access.
create or replace function v17_cascade_tenant_membership_removal()
returns trigger language plpgsql as $$
begin
  if new.status='REMOVED' and old.status<>'REMOVED' then
    update org_department_memberships
       set status='REMOVED',is_primary=false,version=version+1
     where tenant_id=new.tenant_id and user_id=new.user_id and status<>'REMOVED';

    update org_group_memberships
       set status='REMOVED',version=version+1
     where tenant_id=new.tenant_id and user_id=new.user_id and status<>'REMOVED';

    update rbac_principal_role_bindings
       set status='REVOKED',revoked_at=coalesce(revoked_at,now()),
           revoked_by=coalesce(nullif(current_setting('app.current_actor_id',true),''),new.updated_by),
           revoke_reason=coalesce(nullif(new.status_reason,''),'Tenant membership removed'),
           version=version+1
     where tenant_id=new.tenant_id and principal_type='USER' and principal_id=new.user_id
       and status='ACTIVE';
  end if;
  return new;
end $$;

drop trigger if exists trg_v17_cascade_tenant_membership_removal on org_tenant_memberships;
create trigger trg_v17_cascade_tenant_membership_removal
after update of status on org_tenant_memberships
for each row execute function v17_cascade_tenant_membership_removal();

-- Repair rows created before this invariant existed. FORCE RLS is respected by applying each
-- correction inside its Tenant context instead of disabling isolation for the migration.
do $$
declare tenant_value varchar(64);
begin
  for tenant_value in select tenant_id from tenants order by tenant_id loop
    perform set_config('app.current_tenant_id',tenant_value,true);
    perform set_config('app.current_actor_id','v17-lifecycle-consistency',true);

    update org_department_memberships dm
       set status='REMOVED',is_primary=false,version=dm.version+1
     where dm.tenant_id=tenant_value and dm.status<>'REMOVED'
       and exists(select 1 from org_tenant_memberships tm
                  where tm.tenant_id=dm.tenant_id and tm.user_id=dm.user_id and tm.status='REMOVED');

    update org_group_memberships gm
       set status='REMOVED',version=gm.version+1
     where gm.tenant_id=tenant_value and gm.status<>'REMOVED'
       and exists(select 1 from org_tenant_memberships tm
                  where tm.tenant_id=gm.tenant_id and tm.user_id=gm.user_id and tm.status='REMOVED');

    update rbac_principal_role_bindings b
       set status='REVOKED',revoked_at=coalesce(b.revoked_at,now()),
           revoked_by=coalesce(b.revoked_by,'v17-lifecycle-consistency'),
           revoke_reason=coalesce(nullif(b.revoke_reason,''),'Tenant membership was already removed before v17 lifecycle consistency repair'),
           version=b.version+1
     where b.tenant_id=tenant_value and b.principal_type='USER' and b.status='ACTIVE'
       and exists(select 1 from org_tenant_memberships tm
                  where tm.tenant_id=b.tenant_id and tm.user_id=b.principal_id and tm.status='REMOVED');
  end loop;
  perform set_config('app.current_tenant_id','INSTANCE',true);
  perform set_config('app.current_actor_id','v17-lifecycle-consistency',true);
end $$;

comment on index uq_departments_current_code is
'Department codes are unique among current records. DELETED tombstones retain history without permanently reserving the business code.';
comment on index uq_organization_groups_current_code is
'Group codes are unique among current records. DELETED tombstones retain history without permanently reserving the business code.';
comment on index uq_org_department_memberships_current_relation is
'Historical REMOVED Department relationships do not block a later governed re-admission.';
comment on index uq_org_group_memberships_current_relation is
'Historical REMOVED Group relationships do not block a later governed re-admission.';
