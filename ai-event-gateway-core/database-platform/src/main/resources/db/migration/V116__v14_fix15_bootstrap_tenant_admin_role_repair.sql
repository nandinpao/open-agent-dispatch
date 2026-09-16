-- OpenDispatch v14 Fix15: Bootstrap Tenant Administrator Role repair.
--
-- Runtime evidence showed that the application could resolve the canonical Tenant Administrator
-- role before the insert, while the database binding trigger returned ROLE_NOT_FOUND. This can
-- occur when an upgraded database has drifted RBAC RLS policies or a non-canonical role identifier.
-- The migration repairs the global role template, replaces the RBAC catalog policies atomically,
-- and redefines the binding validator against the exact global-or-tenant role scope.

select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','v14-fix15-bootstrap-role-repair',true);

-- Flyway applies this migration before the application accepts traffic. Temporarily remove FORCE
-- RLS so migration-owned normalization can inspect every catalog row, including drifted rows that
-- a restrictive policy may otherwise hide. RLS is re-enabled and forced before commit.
alter table rbac_roles no force row level security;
alter table rbac_roles disable row level security;
alter table rbac_role_permissions no force row level security;
alter table rbac_role_permissions disable row level security;

-- The Role code is the canonical business identity. Preserve an existing global role_id when one
-- exists; create the stable role_id only when the template is absent.
do $$
declare canonical_role_id varchar(128);
begin
  select role_id into canonical_role_id
    from rbac_roles
   where tenant_id is null and role_code='TENANT_ADMIN'
   order by role_id
   limit 1;

  if canonical_role_id is null then
    select role_id into canonical_role_id
      from rbac_roles
     where role_id='role-tenant-admin'
     limit 1;
  end if;

  if canonical_role_id is null then
    canonical_role_id := 'role-tenant-admin';
    insert into rbac_roles(
      role_id,tenant_id,role_code,role_name,description,role_type,status,system_managed,
      created_at,updated_at,created_by,updated_by,version,risk_level,review_required,
      next_review_at,last_reviewed_at)
    values(
      canonical_role_id,null,'TENANT_ADMIN','Tenant Administrator',
      'Tenant identity, organization, role binding, and basic settings administration.',
      'TENANT_ROLE','ACTIVE',true,now(),now(),'v14-fix15-bootstrap-role-repair',
      'v14-fix15-bootstrap-role-repair',1,'CRITICAL',true,now()+interval '30 days',null);
  else
    update rbac_roles
       set tenant_id=null,role_code='TENANT_ADMIN',role_name='Tenant Administrator',
           description='Tenant identity, organization, role binding, and basic settings administration.',
           role_type='TENANT_ROLE',status='ACTIVE',system_managed=true,risk_level='CRITICAL',
           review_required=true,next_review_at=coalesce(next_review_at,now()+interval '30 days'),
           updated_at=now(),updated_by='v14-fix15-bootstrap-role-repair',version=version+1
     where role_id=canonical_role_id;
  end if;
end $$;

-- Remove every legacy/drifted policy on the two RBAC catalog tables, then install the single
-- canonical policy set. SELECT intentionally exposes global role templates to Tenant requests;
-- mutations of global templates remain INSTANCE-only.
do $$
declare policy_row record;
begin
  for policy_row in
    select schemaname,tablename,policyname
      from pg_policies
     where schemaname=current_schema()
       and tablename in('rbac_roles','rbac_role_permissions')
  loop
    execute format('drop policy if exists %I on %I.%I',
                   policy_row.policyname,policy_row.schemaname,policy_row.tablename);
  end loop;
end $$;

create policy rbac_select_scope on rbac_roles
  for select using(tenant_id is null or tenant_id=iam_current_tenant_id());
create policy rbac_insert_scope on rbac_roles
  for insert with check((tenant_id is null and iam_current_tenant_id()='INSTANCE')
                     or tenant_id=iam_current_tenant_id());
create policy rbac_update_scope on rbac_roles
  for update using((tenant_id is null and iam_current_tenant_id()='INSTANCE')
                or tenant_id=iam_current_tenant_id())
  with check((tenant_id is null and iam_current_tenant_id()='INSTANCE')
          or tenant_id=iam_current_tenant_id());
create policy rbac_delete_scope on rbac_roles
  for delete using((tenant_id is null and iam_current_tenant_id()='INSTANCE')
                or tenant_id=iam_current_tenant_id());

create policy rbac_select_scope on rbac_role_permissions
  for select using(tenant_id is null or tenant_id=iam_current_tenant_id());
create policy rbac_insert_scope on rbac_role_permissions
  for insert with check((tenant_id is null and iam_current_tenant_id()='INSTANCE')
                     or tenant_id=iam_current_tenant_id());
create policy rbac_update_scope on rbac_role_permissions
  for update using((tenant_id is null and iam_current_tenant_id()='INSTANCE')
                or tenant_id=iam_current_tenant_id())
  with check((tenant_id is null and iam_current_tenant_id()='INSTANCE')
          or tenant_id=iam_current_tenant_id());
create policy rbac_delete_scope on rbac_role_permissions
  for delete using((tenant_id is null and iam_current_tenant_id()='INSTANCE')
                or tenant_id=iam_current_tenant_id());

alter table rbac_roles enable row level security;
alter table rbac_roles force row level security;
alter table rbac_role_permissions enable row level security;
alter table rbac_role_permissions force row level security;

-- Recreate the authoritative binding trigger. The lookup is constrained to either the global
-- template or the binding Tenant, preventing a same-ID role from another Tenant from satisfying
-- validation even if future schema changes relax the current primary-key model.
create or replace function phase1a4_validate_principal_binding()
returns trigger language plpgsql as $$
declare
  role_tenant varchar(64);
  role_kind varchar(40);
  role_status varchar(32);
  role_code varchar(128);
  role_risk varchar(16);
begin
  select tenant_id,role_type,status,rbac_roles.role_code,risk_level
    into role_tenant,role_kind,role_status,role_code,role_risk
    from rbac_roles
   where role_id=new.role_id
     and (tenant_id is null or tenant_id is not distinct from new.tenant_id)
   for key share;

  if not found then raise exception 'ROLE_NOT_FOUND' using errcode='23503'; end if;
  if role_status<>'ACTIVE' then raise exception 'ROLE_DISABLED' using errcode='23514'; end if;
  if role_tenant is not null and role_tenant is distinct from new.tenant_id then
    raise exception 'ROLE_TENANT_MISMATCH' using errcode='23514';
  end if;
  if role_kind in('SYSTEM_ROLE','CUSTOM_PLATFORM_ROLE')
     and (new.scope_type<>'INSTANCE' or new.scope_id<>'INSTANCE' or new.tenant_id is not null) then
    raise exception 'ROLE_BINDING_SCOPE_INVALID' using errcode='23514';
  end if;
  if role_kind not in('SYSTEM_ROLE','CUSTOM_PLATFORM_ROLE') and new.scope_type='INSTANCE' then
    raise exception 'ROLE_BINDING_SCOPE_INVALID' using errcode='23514';
  end if;
  if role_code in('SYSTEM_ADMIN','TENANT_ADMIN') and new.principal_type<>'USER' then
    raise exception 'ROLE_BINDING_PRINCIPAL_FORBIDDEN' using errcode='23514';
  end if;
  if new.principal_type='SERVICE_ACCOUNT' or role_risk='CRITICAL' then
    new.review_required=true;
    new.next_review_at=coalesce(
      new.next_review_at,
      now()+case when role_risk='CRITICAL' then interval '30 days' else interval '90 days' end);
  end if;
  return new;
end $$;

-- Migration-level acceptance assertions fail the deployment before application startup instead of
-- letting Bootstrap reach Step 3 with an unusable RBAC catalog.
do $$
declare
  repaired_select_policy_count integer;
begin
  if not exists(
    select 1 from rbac_roles
     where tenant_id is null and role_code='TENANT_ADMIN'
       and role_type='TENANT_ROLE' and status='ACTIVE' and system_managed=true
  ) then
    raise exception 'FIX15_TENANT_ADMIN_ROLE_REPAIR_FAILED' using errcode='23514';
  end if;

  -- pg_policies renders SQL keywords using PostgreSQL's canonical uppercase form
  -- (for example, "tenant_id IS NULL"). Compare normalized lowercase text rather than
  -- a case-sensitive representation, and validate both RBAC catalog tables.
  select count(*) into repaired_select_policy_count
    from pg_policies
   where schemaname=current_schema()
     and tablename in('rbac_roles','rbac_role_permissions')
     and policyname='rbac_select_scope'
     and cmd='SELECT'
     and position('tenant_id is null' in lower(coalesce(qual,'')))>0
     and position('iam_current_tenant_id' in lower(coalesce(qual,'')))>0;

  if repaired_select_policy_count<>2 then
    raise exception 'FIX15_RBAC_GLOBAL_ROLE_POLICY_REPAIR_FAILED' using errcode='23514';
  end if;
end $$;
