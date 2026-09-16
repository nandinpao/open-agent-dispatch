-- Phase 5D enforcement: lifecycle constraints, append-only evidence and final Platform Admin protection.

alter table org_tenant_memberships alter column updated_at set not null;
alter table org_tenant_memberships alter column updated_by set not null;
alter table org_tenant_memberships alter column status_reason set not null;
alter table org_tenant_memberships alter column default_tenant set not null;
alter table org_tenant_memberships alter column membership_source set not null;

alter table org_tenant_memberships drop constraint if exists ck_org_tenant_membership_source;
alter table org_tenant_memberships add constraint ck_org_tenant_membership_source
  check(membership_source in('ADMIN_CREATED','INVITATION','LEGACY_IMPORT','PLATFORM_PROVISIONING'));
alter table org_tenant_memberships drop constraint if exists ck_org_tenant_membership_default_active;
alter table org_tenant_memberships add constraint ck_org_tenant_membership_default_active
  check(not default_tenant or status='ACTIVE');
alter table org_tenant_memberships drop constraint if exists ck_org_tenant_membership_status_dates;
alter table org_tenant_memberships add constraint ck_org_tenant_membership_status_dates check(
  (status<>'INVITED' or invited_at is not null)
  and (status<>'ACTIVE' or activated_at is not null)
  and (status<>'SUSPENDED' or suspended_at is not null)
  and (status<>'EXPIRED' or expired_at is not null)
  and (status<>'REMOVED' or removed_at is not null)
);

create or replace function phase5d_validate_tenant_membership_transition()
returns trigger language plpgsql as $$
begin
  if tg_op='UPDATE' then
    if old.status='REMOVED' and new.status<>'REMOVED' then
      raise exception 'IDENTITY_TENANT_MEMBERSHIP_TRANSITION_INVALID' using errcode='23514';
    end if;
    if old.status<>new.status and not (
      (old.status='INVITED' and new.status in('ACTIVE','SUSPENDED','EXPIRED','REMOVED')) or
      (old.status='ACTIVE' and new.status in('SUSPENDED','EXPIRED','REMOVED')) or
      (old.status='SUSPENDED' and new.status in('ACTIVE','EXPIRED','REMOVED')) or
      (old.status='EXPIRED' and new.status in('ACTIVE','REMOVED'))
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
  if new.status='ACTIVE' then new.activated_at=coalesce(new.activated_at,now()); end if;
  if new.status='SUSPENDED' then new.suspended_at=coalesce(new.suspended_at,now()); new.default_tenant=false; end if;
  if new.status='EXPIRED' then new.expired_at=coalesce(new.expired_at,now()); new.default_tenant=false; end if;
  if new.status='REMOVED' then new.removed_at=coalesce(new.removed_at,now()); new.default_tenant=false; end if;
  return new;
end $$;

drop trigger if exists trg_phase5d_tenant_membership_transition on org_tenant_memberships;
create trigger trg_phase5d_tenant_membership_transition
before insert or update on org_tenant_memberships
for each row execute function phase5d_validate_tenant_membership_transition();

-- Refresh the non-sensitive cross-Tenant membership directory after every lifecycle change.
-- Preserve the existing Tenant initialization side effects from Phase 1A7; this migration only expands
-- the membership projection and must not replace the MFA/password/session/token policy bootstrap.
create or replace function phase1a7_refresh_user_tenant_directory()
returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
begin
  if tg_table_name='org_tenant_memberships' then
    if tg_op='DELETE' then
      delete from iam_user_tenant_directory where user_id=old.user_id and tenant_id=old.tenant_id;
      return old;
    end if;
    insert into iam_user_tenant_directory(
      user_id,tenant_id,tenant_code,tenant_name,membership_status,tenant_status,expires_at,updated_at,
      default_tenant,membership_source,membership_version)
    select new.user_id,new.tenant_id,t.tenant_code,t.display_name,new.status,t.status,new.expires_at,now(),
           new.default_tenant,new.membership_source,new.version
      from tenants t where t.tenant_id=new.tenant_id
    on conflict(user_id,tenant_id) do update set
      membership_status=excluded.membership_status,tenant_code=excluded.tenant_code,
      tenant_name=excluded.tenant_name,tenant_status=excluded.tenant_status,
      expires_at=excluded.expires_at,updated_at=excluded.updated_at,
      default_tenant=excluded.default_tenant,membership_source=excluded.membership_source,
      membership_version=excluded.membership_version;
  elsif tg_table_name='tenants' then
    perform set_config('app.current_tenant_id',new.tenant_id,true);
    perform set_config('app.current_actor_id',coalesce(nullif(new.updated_by,''),'phase5d-system'),true);
    insert into auth_tenant_mfa_policies(tenant_id,updated_at,updated_by)
      values(new.tenant_id,now(),coalesce(nullif(new.updated_by,''),'phase5d-system'))
      on conflict(tenant_id) do nothing;
    insert into auth_tenant_password_policies(
      tenant_id,minimum_length,maximum_length,require_uppercase,require_lowercase,require_number,require_symbol,
      password_history_count,maximum_age_seconds,minimum_age_seconds,failed_attempt_threshold,lockout_duration_seconds,
      breached_password_mode,updated_at,updated_by,version)
    select new.tenant_id,minimum_length,maximum_length,require_uppercase,require_lowercase,require_number,require_symbol,
      password_history_count,maximum_age_seconds,minimum_age_seconds,failed_attempt_threshold,lockout_duration_seconds,
      breached_password_mode,now(),coalesce(nullif(new.updated_by,''),'phase5d-system'),1
      from auth_instance_password_policy where policy_id='INSTANCE_MINIMUM'
      on conflict(tenant_id) do nothing;
    insert into auth_tenant_session_policies(
      tenant_id,idle_timeout_seconds,absolute_timeout_seconds,max_concurrent_sessions,revoke_on_password_change,
      revoke_on_mfa_reset,reauthentication_window_seconds,updated_at,updated_by,version)
    select new.tenant_id,idle_timeout_seconds,absolute_timeout_seconds,max_concurrent_sessions,revoke_on_password_change,
      revoke_on_mfa_reset,reauthentication_window_seconds,now(),coalesce(nullif(new.updated_by,''),'phase5d-system'),1
      from auth_instance_session_policy where policy_id='INSTANCE_MINIMUM'
      on conflict(tenant_id) do nothing;
    insert into token_tenant_policies(tenant_id,updated_by)
      values(new.tenant_id,coalesce(nullif(new.updated_by,''),'phase5d-system'))
      on conflict(tenant_id) do nothing;
    update iam_user_tenant_directory
       set tenant_code=new.tenant_code,tenant_name=new.display_name,tenant_status=new.status,updated_at=now()
     where tenant_id=new.tenant_id;
  end if;
  return new;
end $$;

revoke all on function phase1a7_refresh_user_tenant_directory() from public;

-- Tighten the Platform/Tenant role boundary without creating a second authorization model.
create or replace function phase1a4_validate_principal_binding()
returns trigger language plpgsql as $$
declare role_tenant varchar(64); role_kind varchar(40); role_status varchar(32); role_code varchar(128);
begin
  select tenant_id,role_type,status,rbac_roles.role_code
    into role_tenant,role_kind,role_status,role_code
    from rbac_roles where role_id=new.role_id for key share;
  if not found then raise exception 'ROLE_NOT_FOUND' using errcode='23503'; end if;
  if role_status<>'ACTIVE' then raise exception 'ROLE_DISABLED' using errcode='23514'; end if;
  if role_tenant is not null and role_tenant is distinct from new.tenant_id then
    raise exception 'ROLE_TENANT_MISMATCH' using errcode='23514';
  end if;
  if role_kind='SYSTEM_ROLE' and (
       new.scope_type<>'INSTANCE' or new.scope_id<>'INSTANCE' or new.tenant_id is not null) then
    raise exception 'ROLE_BINDING_SCOPE_INVALID' using errcode='23514';
  end if;
  if role_kind<>'SYSTEM_ROLE' and new.scope_type='INSTANCE' then
    raise exception 'ROLE_BINDING_SCOPE_INVALID' using errcode='23514';
  end if;
  if role_code in('SYSTEM_ADMIN','TENANT_ADMIN') and new.principal_type<>'USER' then
    raise exception 'ROLE_BINDING_PRINCIPAL_FORBIDDEN' using errcode='23514';
  end if;
  return new;
end $$;

-- Every membership mutation invalidates cached/session authorization through the existing principal epoch.
create or replace function phase5d_increment_membership_security_epoch()
returns trigger language plpgsql as $$
declare affected_tenant varchar(64); affected_user varchar(128); actor varchar(128);
begin
  affected_tenant=case when tg_op='DELETE' then old.tenant_id else new.tenant_id end;
  affected_user=case when tg_op='DELETE' then old.user_id else new.user_id end;
  actor=coalesce(nullif(current_setting('app.current_actor_id',true),''),
                 case when tg_op='DELETE' then old.updated_by else new.updated_by end,
                 'phase5d-system');
  perform phase1a3_increment_principal_security_epoch(affected_tenant,affected_user,actor);
  if tg_op='DELETE' then return old; end if;
  return new;
end $$;

drop trigger if exists trg_phase5d_membership_security_epoch on org_tenant_memberships;
create trigger trg_phase5d_membership_security_epoch
after insert or update or delete on org_tenant_memberships
for each row execute function phase5d_increment_membership_security_epoch();

-- Membership events are immutable evidence and remain Tenant isolated.
alter table iam_tenant_membership_events enable row level security;
alter table iam_tenant_membership_events force row level security;
drop policy if exists tenant_isolation on iam_tenant_membership_events;
create policy tenant_isolation on iam_tenant_membership_events
  using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());

create or replace function phase5d_reject_membership_event_mutation()
returns trigger language plpgsql as $$
begin
  raise exception 'TENANT_MEMBERSHIP_EVENT_APPEND_ONLY' using errcode='55000';
end $$;
drop trigger if exists trg_phase5d_membership_event_immutable on iam_tenant_membership_events;
create trigger trg_phase5d_membership_event_immutable
before update or delete on iam_tenant_membership_events
for each row execute function phase5d_reject_membership_event_mutation();

-- Protect the final active named Platform Administrator binding.
create or replace function phase5d_protect_last_platform_admin_binding()
returns trigger language plpgsql as $$
declare system_admin_role varchar(128); remaining integer; new_remains boolean;
begin
  select role_id into system_admin_role from rbac_roles
   where tenant_id is null and role_code='SYSTEM_ADMIN' and status='ACTIVE';
  if old.role_id<>system_admin_role or old.scope_type<>'INSTANCE' or old.scope_id<>'INSTANCE'
     or old.principal_type<>'USER' or old.status<>'ACTIVE' then
    if tg_op='DELETE' then return old; else return new; end if;
  end if;
  new_remains := tg_op='UPDATE' and new.role_id=system_admin_role and new.scope_type='INSTANCE'
    and new.scope_id='INSTANCE' and new.principal_type='USER' and new.status='ACTIVE'
    and new.effective_at<=now() and (new.expires_at is null or new.expires_at>now());
  if new_remains then return new; end if;
  perform pg_advisory_xact_lock(hashtextextended('rbac-last-platform-admin',0));
  select count(*) into remaining
    from rbac_principal_role_bindings b join iam_users u on u.user_id=b.principal_id
   where b.binding_id<>old.binding_id and b.role_id=system_admin_role and b.scope_type='INSTANCE'
     and b.scope_id='INSTANCE' and b.principal_type='USER' and b.status='ACTIVE'
     and b.effective_at<=now() and (b.expires_at is null or b.expires_at>now()) and u.status='ACTIVE';
  if remaining=0 then
    raise exception 'IDENTITY_LAST_PLATFORM_ADMIN_PROTECTED' using errcode='23514';
  end if;
  if tg_op='DELETE' then return old; end if;
  return new;
end $$;

drop trigger if exists trg_phase5d_last_platform_admin_binding on rbac_principal_role_bindings;
create trigger trg_phase5d_last_platform_admin_binding
before update or delete on rbac_principal_role_bindings
for each row execute function phase5d_protect_last_platform_admin_binding();

-- Disabling, locking, suspending or deleting the final active Platform Administrator is also blocked.
create or replace function phase5d_protect_last_platform_admin_user()
returns trigger language plpgsql as $$
declare system_admin_role varchar(128); is_admin boolean; remaining integer;
begin
  if old.status<>'ACTIVE' then
    if tg_op='DELETE' then return old; end if;
    return new;
  end if;
  if tg_op='UPDATE' and new.status='ACTIVE' then return new; end if;
  select role_id into system_admin_role from rbac_roles
   where tenant_id is null and role_code='SYSTEM_ADMIN' and status='ACTIVE';
  select exists(select 1 from rbac_principal_role_bindings b
    where b.role_id=system_admin_role and b.principal_type='USER' and b.principal_id=old.user_id
      and b.scope_type='INSTANCE' and b.scope_id='INSTANCE' and b.status='ACTIVE'
      and b.effective_at<=now() and (b.expires_at is null or b.expires_at>now())) into is_admin;
  if not is_admin then
    if tg_op='DELETE' then return old; end if;
    return new;
  end if;
  perform pg_advisory_xact_lock(hashtextextended('rbac-last-platform-admin',0));
  select count(distinct b.principal_id) into remaining
    from rbac_principal_role_bindings b join iam_users u on u.user_id=b.principal_id
   where b.role_id=system_admin_role and b.principal_type='USER' and b.principal_id<>old.user_id
     and b.scope_type='INSTANCE' and b.scope_id='INSTANCE' and b.status='ACTIVE'
     and b.effective_at<=now() and (b.expires_at is null or b.expires_at>now()) and u.status='ACTIVE';
  if remaining=0 then
    raise exception 'IDENTITY_LAST_PLATFORM_ADMIN_PROTECTED' using errcode='23514';
  end if;
  if tg_op='DELETE' then return old; end if;
  return new;
end $$;

drop trigger if exists trg_phase5d_last_platform_admin_user on iam_users;
create trigger trg_phase5d_last_platform_admin_user
before update of status on iam_users
for each row execute function phase5d_protect_last_platform_admin_user();

drop trigger if exists trg_phase5d_last_platform_admin_user_delete on iam_users;
create trigger trg_phase5d_last_platform_admin_user_delete
before delete on iam_users
for each row execute function phase5d_protect_last_platform_admin_user();
