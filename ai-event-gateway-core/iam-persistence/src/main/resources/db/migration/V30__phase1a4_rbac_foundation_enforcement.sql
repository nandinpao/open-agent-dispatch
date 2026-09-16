-- Phase 1A-4 Enforcement: invariants, RLS, append-only decision evidence,
-- monotonic policy/security versions and cache invalidation safety.

alter table permission_point_catalog
  add constraint ck_permission_point_scope_nonempty check(cardinality(allowed_scope_types)>0),
  add constraint ck_permission_point_scope_values check(allowed_scope_types <@ array['INSTANCE','TENANT','DEPARTMENT','GROUP']::varchar[]);

alter table rbac_roles
  add constraint ck_rbac_role_type check(role_type in('SYSTEM_ROLE','TENANT_ROLE','CUSTOM_TENANT_ROLE','LEGACY_COMPATIBILITY_ROLE')),
  add constraint ck_rbac_role_status check(status in('ACTIVE','DISABLED')),
  add constraint ck_rbac_role_version check(version>0),
  add constraint ck_rbac_role_ownership check(
    (role_type='CUSTOM_TENANT_ROLE' and tenant_id is not null and system_managed=false)
    or (role_type<>'CUSTOM_TENANT_ROLE' and tenant_id is null and system_managed=true));

alter table rbac_role_permissions
  add constraint ck_rbac_role_permission_version check(version>0);

alter table rbac_principal_role_bindings
  add constraint ck_rbac_binding_principal check(principal_type in('USER','GROUP','SERVICE_ACCOUNT')),
  add constraint ck_rbac_binding_scope check(scope_type in('INSTANCE','TENANT','DEPARTMENT','GROUP')),
  add constraint ck_rbac_binding_status check(status in('ACTIVE','REVOKED')),
  add constraint ck_rbac_binding_expiry check(expires_at is null or expires_at>effective_at),
  add constraint ck_rbac_binding_version check(version>0),
  add constraint ck_rbac_binding_scope_tenant check(
    (scope_type='INSTANCE' and tenant_id is null and scope_id='INSTANCE')
    or (scope_type='TENANT' and tenant_id is not null and scope_id=tenant_id)
    or (scope_type in('DEPARTMENT','GROUP') and tenant_id is not null and btrim(scope_id)<>''));

alter table rbac_policy_versions
  add constraint ck_rbac_policy_scope check(
    (scope_type='INSTANCE' and scope_id='INSTANCE' and tenant_id is null)
    or (scope_type='TENANT' and tenant_id is not null and scope_id=tenant_id)),
  add constraint ck_rbac_policy_version_nonnegative check(policy_version>=0);

alter table rbac_decision_audits
  add constraint ck_rbac_decision_value check(decision in('ALLOW','DENY','SHADOW_ALLOW','SHADOW_DENY')),
  add constraint ck_rbac_decision_epoch check(global_security_epoch>=0 and tenant_security_epoch>=0 and principal_security_epoch>=0),
  add constraint ck_rbac_decision_policy check(policy_version>=0);
alter table rbac_shadow_decisions
  add constraint ck_rbac_shadow_legacy check(legacy_decision in('ALLOW','DENY','ERROR')),
  add constraint ck_rbac_shadow_new check(new_decision in('ALLOW','DENY','SHADOW_ALLOW','SHADOW_DENY')),
  add constraint ck_rbac_shadow_lane check(lane in('CRITICAL','SAMPLE','AGGREGATE'));
alter table rbac_shadow_aggregates
  add constraint ck_rbac_shadow_aggregate_count check(decision_count>=0);

-- Global role templates are readable to Tenant requests, but bindings and evidence
-- are never exposed across Tenant or Instance scope through the Tenant runtime path.
do $$
declare table_name text;
begin
  foreach table_name in array array['rbac_roles','rbac_role_permissions'] loop
    execute format('alter table %I enable row level security',table_name);
    execute format('alter table %I force row level security',table_name);
    execute format('drop policy if exists rbac_select_scope on %I',table_name);
    execute format('drop policy if exists rbac_insert_tenant on %I',table_name);
    execute format('drop policy if exists rbac_update_tenant on %I',table_name);
    execute format('drop policy if exists rbac_delete_tenant on %I',table_name);
    execute format('create policy rbac_select_scope on %I for select using (tenant_id is null or tenant_id=iam_current_tenant_id())',table_name);
    execute format('create policy rbac_insert_tenant on %I for insert with check (tenant_id=iam_current_tenant_id())',table_name);
    execute format('create policy rbac_update_tenant on %I for update using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id())',table_name);
    execute format('create policy rbac_delete_tenant on %I for delete using (tenant_id=iam_current_tenant_id())',table_name);
  end loop;

  foreach table_name in array array[
    'rbac_principal_role_bindings','rbac_policy_versions','rbac_decision_audits','rbac_shadow_decisions'
  ] loop
    execute format('alter table %I enable row level security',table_name);
    execute format('alter table %I force row level security',table_name);
    execute format('drop policy if exists rbac_tenant_only on %I',table_name);
    execute format('create policy rbac_tenant_only on %I using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id())',table_name);
  end loop;
end $$;

alter table rbac_shadow_aggregates enable row level security;
alter table rbac_shadow_aggregates force row level security;
drop policy if exists rbac_shadow_aggregate_tenant on rbac_shadow_aggregates;
create policy rbac_shadow_aggregate_tenant on rbac_shadow_aggregates
using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());

create or replace function phase1a4_validate_role_permission()
returns trigger language plpgsql as $$
declare role_tenant varchar(64); role_kind varchar(40); allowed varchar(32)[];
begin
  select tenant_id,role_type into role_tenant,role_kind from rbac_roles where role_id=new.role_id for key share;
  if not found then raise exception 'ROLE_NOT_FOUND' using errcode='23503'; end if;
  if role_tenant is distinct from new.tenant_id then raise exception 'ROLE_TENANT_MISMATCH' using errcode='23514'; end if;
  select allowed_scope_types into allowed from permission_point_catalog where permission_point=new.permission_point and active=true;
  if not found then raise exception 'AUTH_PERMISSION_UNKNOWN' using errcode='23503'; end if;
  if role_kind='CUSTOM_TENANT_ROLE' and 'INSTANCE'=any(allowed) then
    raise exception 'ROLE_INSTANCE_PERMISSION_FORBIDDEN' using errcode='23514';
  end if;
  return new;
end $$;
drop trigger if exists trg_phase1a4_role_permission_validate on rbac_role_permissions;
create trigger trg_phase1a4_role_permission_validate before insert or update on rbac_role_permissions
for each row execute function phase1a4_validate_role_permission();

create or replace function phase1a4_validate_principal_binding()
returns trigger language plpgsql as $$
declare role_tenant varchar(64); role_kind varchar(40); role_status varchar(32);
begin
  select tenant_id,role_type,status into role_tenant,role_kind,role_status from rbac_roles where role_id=new.role_id for key share;
  if not found then raise exception 'ROLE_NOT_FOUND' using errcode='23503'; end if;
  if role_status<>'ACTIVE' then raise exception 'ROLE_DISABLED' using errcode='23514'; end if;
  if role_tenant is not null and role_tenant is distinct from new.tenant_id then raise exception 'ROLE_TENANT_MISMATCH' using errcode='23514'; end if;
  if role_kind='SYSTEM_ROLE' and new.scope_type<>'INSTANCE' then raise exception 'ROLE_BINDING_SCOPE_INVALID' using errcode='23514'; end if;
  if role_kind<>'SYSTEM_ROLE' and new.scope_type='INSTANCE' then raise exception 'ROLE_BINDING_SCOPE_INVALID' using errcode='23514'; end if;
  if exists(select 1 from rbac_roles where role_id=new.role_id and role_code='TENANT_ADMIN')
     and new.principal_type<>'USER' then
    raise exception 'ROLE_BINDING_PRINCIPAL_FORBIDDEN' using errcode='23514';
  end if;
  return new;
end $$;
drop trigger if exists trg_phase1a4_binding_validate on rbac_principal_role_bindings;
create trigger trg_phase1a4_binding_validate before insert or update on rbac_principal_role_bindings
for each row execute function phase1a4_validate_principal_binding();


create or replace function phase1a4_protect_last_tenant_admin()
returns trigger language plpgsql as $$
declare remaining_admins integer; tenant_role_id varchar(128); new_remains_admin boolean;
begin
  if old.status<>'ACTIVE' then
    if tg_op='DELETE' then return old; else return new; end if;
  end if;
  select role_id into tenant_role_id from rbac_roles where role_code='TENANT_ADMIN' and tenant_id is null;
  if old.role_id<>tenant_role_id or old.scope_type<>'TENANT' or old.principal_type<>'USER' then
    if tg_op='DELETE' then return old; else return new; end if;
  end if;

  new_remains_admin := tg_op='UPDATE'
    and new.status='ACTIVE'
    and new.role_id=tenant_role_id
    and new.scope_type='TENANT'
    and new.tenant_id=old.tenant_id
    and new.principal_type='USER'
    and new.effective_at<=now()
    and (new.expires_at is null or new.expires_at>now());
  if new_remains_admin then return new; end if;

  perform pg_advisory_xact_lock(hashtextextended('rbac-last-tenant-admin:'||old.tenant_id,0));
  select count(*) into remaining_admins from rbac_principal_role_bindings
   where tenant_id=old.tenant_id and role_id=tenant_role_id and scope_type='TENANT'
     and principal_type='USER' and status='ACTIVE' and binding_id<>old.binding_id
     and effective_at<=now() and (expires_at is null or expires_at>now());
  if remaining_admins=0 then
    raise exception 'IDENTITY_LAST_TENANT_ADMIN_PROTECTED' using errcode='23514';
  end if;
  if tg_op='DELETE' then return old; end if;
  return new;
end $$;
drop trigger if exists trg_phase1a4_last_tenant_admin on rbac_principal_role_bindings;
create trigger trg_phase1a4_last_tenant_admin before update or delete on rbac_principal_role_bindings
for each row execute function phase1a4_protect_last_tenant_admin();

create or replace function phase1a4_increment_policy_version(p_tenant_id varchar,p_actor_id varchar)
returns bigint language plpgsql security invoker as $$
declare next_version bigint;
begin
  if p_tenant_id is null or btrim(p_tenant_id)='' then
    update rbac_policy_versions set policy_version=policy_version+1,updated_at=now(),updated_by=p_actor_id
      where scope_type='INSTANCE' and scope_id='INSTANCE' returning policy_version into next_version;
    update iam_global_security_epoch set security_epoch=security_epoch+1,updated_at=now(),updated_by=p_actor_id where singleton_id='GLOBAL';
    return next_version;
  end if;
  if p_tenant_id<>iam_current_tenant_id() then raise exception 'TENANT_CONTEXT_MISMATCH' using errcode='42501'; end if;
  insert into rbac_policy_versions(scope_type,scope_id,tenant_id,policy_version,updated_at,updated_by)
    values('TENANT',p_tenant_id,p_tenant_id,1,now(),p_actor_id)
  on conflict(scope_type,scope_id) do update set policy_version=rbac_policy_versions.policy_version+1,
    updated_at=excluded.updated_at,updated_by=excluded.updated_by
  returning policy_version into next_version;
  update iam_tenant_security_epochs set security_epoch=security_epoch+1,updated_at=now(),updated_by=p_actor_id where tenant_id=p_tenant_id;
  return next_version;
end $$;

create or replace function phase1a4_policy_change_trigger()
returns trigger language plpgsql as $$
declare affected_tenant varchar(64); affected_principal varchar(128); actor varchar(128);
begin
  actor=coalesce(nullif(current_setting('app.current_actor_id',true),''),'phase1a4-system');
  if tg_table_name='rbac_role_permissions' then
    select tenant_id into affected_tenant from rbac_roles where role_id=case when tg_op='DELETE' then old.role_id else new.role_id end;
  elsif tg_op='DELETE' then
    affected_tenant=old.tenant_id;
  else
    affected_tenant=new.tenant_id;
  end if;
  perform phase1a4_increment_policy_version(affected_tenant,actor);
  if tg_table_name='rbac_principal_role_bindings' then
    affected_principal=case when tg_op='DELETE' then old.principal_id else new.principal_id end;
    perform phase1a3_increment_principal_security_epoch(affected_tenant,affected_principal,actor);
  end if;
  if tg_op='DELETE' then return old; end if;
  return new;
end $$;

drop trigger if exists trg_phase1a4_roles_version on rbac_roles;
create trigger trg_phase1a4_roles_version after insert or update or delete on rbac_roles
for each row execute function phase1a4_policy_change_trigger();
drop trigger if exists trg_phase1a4_role_permissions_version on rbac_role_permissions;
create trigger trg_phase1a4_role_permissions_version after insert or update or delete on rbac_role_permissions
for each row execute function phase1a4_policy_change_trigger();
drop trigger if exists trg_phase1a4_bindings_version on rbac_principal_role_bindings;
create trigger trg_phase1a4_bindings_version after insert or update or delete on rbac_principal_role_bindings
for each row execute function phase1a4_policy_change_trigger();

create or replace function phase1a4_reject_decision_mutation()
returns trigger language plpgsql as $$ begin raise exception 'RBAC_DECISION_IMMUTABLE' using errcode='55000'; end $$;
drop trigger if exists trg_phase1a4_decision_immutable on rbac_decision_audits;
create trigger trg_phase1a4_decision_immutable before update or delete on rbac_decision_audits
for each row execute function phase1a4_reject_decision_mutation();
drop trigger if exists trg_phase1a4_shadow_immutable on rbac_shadow_decisions;
create trigger trg_phase1a4_shadow_immutable before update or delete on rbac_shadow_decisions
for each row execute function phase1a4_reject_decision_mutation();

create or replace function phase1a4_initialize_tenant_policy_version()
returns trigger language plpgsql as $$
begin
  perform set_config('app.current_tenant_id',new.tenant_id,true);
  perform set_config('app.current_actor_id',coalesce(nullif(new.updated_by,''),'system'),true);
  insert into rbac_policy_versions(scope_type,scope_id,tenant_id,policy_version,updated_at,updated_by)
  values('TENANT',new.tenant_id,new.tenant_id,0,now(),coalesce(nullif(new.updated_by,''),'system')) on conflict do nothing;
  return new;
end $$;
drop trigger if exists trg_phase1a4_tenant_policy_version on tenants;
create trigger trg_phase1a4_tenant_policy_version after insert on tenants
for each row execute function phase1a4_initialize_tenant_policy_version();
