-- Phase 1A-3 enforcement: constraints, tenant RLS and session/recovery safety.

alter table auth_instance_password_policy
  add constraint ck_auth_instance_password_length check(minimum_length between 12 and 256 and maximum_length between minimum_length and 1024),
  add constraint ck_auth_instance_password_history check(password_history_count between 0 and 50),
  add constraint ck_auth_instance_password_attempts check(failed_attempt_threshold between 3 and 50),
  add constraint ck_auth_instance_password_breach_mode check(breached_password_mode in('DISABLED','AUDIT','BLOCK'));

alter table auth_tenant_password_policies
  add constraint ck_auth_tenant_password_length check(minimum_length between 12 and 256 and maximum_length between minimum_length and 1024),
  add constraint ck_auth_tenant_password_history check(password_history_count between 0 and 50),
  add constraint ck_auth_tenant_password_attempts check(failed_attempt_threshold between 3 and 50),
  add constraint ck_auth_tenant_password_breach_mode check(breached_password_mode in('DISABLED','AUDIT','BLOCK'));

alter table auth_instance_session_policy
  add constraint ck_auth_instance_session_timeout check(idle_timeout_seconds > 0 and absolute_timeout_seconds > idle_timeout_seconds),
  add constraint ck_auth_instance_session_concurrency check(max_concurrent_sessions between 1 and 100);
alter table auth_tenant_session_policies
  add constraint ck_auth_tenant_session_timeout check(idle_timeout_seconds > 0 and absolute_timeout_seconds > idle_timeout_seconds),
  add constraint ck_auth_tenant_session_concurrency check(max_concurrent_sessions between 1 and 100);

alter table auth_password_credentials
  add constraint ck_auth_password_subject_type check(subject_type in('INSTANCE_ROOT','HUMAN_USER')),
  add constraint ck_auth_password_expiry check(expires_at > changed_at),
  add constraint ck_auth_password_version check(version > 0);
alter table auth_password_history
  add constraint ck_auth_password_history_subject_type check(subject_type in('INSTANCE_ROOT','HUMAN_USER'));
alter table auth_mfa_methods
  add constraint ck_auth_mfa_subject_type check(subject_type in('INSTANCE_ROOT','HUMAN_USER')),
  add constraint ck_auth_mfa_type check(method_type='TOTP'),
  add constraint ck_auth_mfa_status check(status in('PENDING','ACTIVE','DISABLED')),
  add constraint ck_auth_mfa_digits check(digits between 6 and 8),
  add constraint ck_auth_mfa_period check(period_seconds between 15 and 120),
  add constraint ck_auth_mfa_version check(version > 0);
alter table auth_recovery_codes
  add constraint ck_auth_recovery_subject_type check(subject_type in('INSTANCE_ROOT','HUMAN_USER'));
alter table auth_subject_security_states
  add constraint ck_auth_state_subject_type check(subject_type in('INSTANCE_ROOT','HUMAN_USER')),
  add constraint ck_auth_state_failures check(failed_login_count >= 0),
  add constraint ck_auth_state_version check(version > 0);

alter table auth_tenant_sessions
  add constraint ck_auth_tenant_session_subject check(subject_type='HUMAN_USER'),
  add constraint ck_auth_tenant_session_status check(status in('ACTIVE','REVOKED')),
  add constraint ck_auth_tenant_session_expiry check(absolute_expires_at > created_at and idle_expires_at <= absolute_expires_at),
  add constraint ck_auth_tenant_session_epoch check(global_security_epoch >= 0 and tenant_security_epoch >= 0 and principal_security_epoch >= 0),
  add constraint ck_auth_tenant_session_version check(version > 0);
alter table auth_root_sessions
  add constraint ck_auth_root_session_status check(status in('ACTIVE','REVOKED')),
  add constraint ck_auth_root_session_expiry check(absolute_expires_at > created_at and idle_expires_at <= absolute_expires_at),
  add constraint ck_auth_root_session_absolute_ttl check(absolute_expires_at <= created_at + interval '30 minutes'),
  add constraint ck_auth_root_session_idle_ttl check(idle_expires_at <= last_seen_at + interval '5 minutes'),
  add constraint ck_auth_root_session_epoch check(global_security_epoch >= 0 and principal_security_epoch >= 0),
  add constraint ck_auth_root_session_version check(version > 0);
alter table auth_root_bootstrap_state
  add constraint ck_auth_root_bootstrap_status check(status in('REQUIRED','IN_PROGRESS','COMPLETED')),
  add constraint ck_auth_root_bootstrap_complete check(status <> 'COMPLETED' or (password_configured and mfa_configured and tenant_created and tenant_admin_created and completed_at is not null));
alter table auth_root_recovery_grants
  add constraint ck_auth_root_recovery_dual_control check(initiator_id <> approver_id),
  add constraint ck_auth_root_recovery_expiry check(expires_at > issued_at and expires_at <= issued_at + interval '30 minutes'),
  add constraint ck_auth_root_recovery_status check(status in('ACTIVE','CONSUMED','REVOKED'));

-- Tenant-owned authentication tables use the Phase 1A-2 transaction-local Tenant GUC.
do $$
declare table_name text;
begin
  foreach table_name in array array[
    'auth_tenant_password_policies','auth_tenant_session_policies','iam_principal_security_epochs',
    'auth_tenant_sessions','auth_tenant_reauthentication_grants'
  ] loop
    execute format('alter table %I enable row level security',table_name);
    execute format('alter table %I force row level security',table_name);
    execute format('drop policy if exists tenant_isolation on %I',table_name);
    execute format('create policy tenant_isolation on %I using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id())',table_name);
  end loop;
end $$;

-- Principal epoch rows are initialized lazily and increment monotonically.
create or replace function phase1a3_increment_principal_security_epoch(
  p_tenant_id varchar,p_principal_id varchar,p_actor_id varchar)
returns bigint language plpgsql security invoker as $$
declare next_epoch bigint;
begin
  if p_tenant_id is null or btrim(p_tenant_id)='' then
    insert into iam_global_principal_security_epochs(principal_id,security_epoch,updated_at,updated_by)
    values(p_principal_id,1,now(),p_actor_id)
    on conflict(principal_id) do update set security_epoch=iam_global_principal_security_epochs.security_epoch+1,
      updated_at=excluded.updated_at,updated_by=excluded.updated_by
    returning security_epoch into next_epoch;
    return next_epoch;
  end if;
  if p_tenant_id <> iam_current_tenant_id() then
    raise exception 'TENANT_CONTEXT_MISMATCH' using errcode='42501';
  end if;
  insert into iam_principal_security_epochs(tenant_id,principal_id,security_epoch,updated_at,updated_by)
  values(p_tenant_id,p_principal_id,1,now(),p_actor_id)
  on conflict(tenant_id,principal_id) do update set security_epoch=iam_principal_security_epochs.security_epoch+1,
    updated_at=excluded.updated_at,updated_by=excluded.updated_by
  returning security_epoch into next_epoch;
  return next_epoch;
end $$;

-- Recovery codes are one-way: only the first concurrent consumer may mark a code used.
create or replace function phase1a3_mark_recovery_code_used(p_code_id varchar,p_used_at timestamptz)
returns boolean language plpgsql as $$
begin
  update auth_recovery_codes set used_at=p_used_at where recovery_code_id=p_code_id and used_at is null;
  return found;
end $$;

-- Root bootstrap completion cannot be reverted by runtime SQL.
create or replace function phase1a3_guard_root_bootstrap_transition()
returns trigger language plpgsql as $$
begin
  if old.status='COMPLETED' and new is distinct from old then
    raise exception 'ROOT_BOOTSTRAP_ALREADY_COMPLETED' using errcode='55000';
  end if;
  if new.version <> old.version + 1 then
    raise exception 'IDENTITY_VERSION_CONFLICT' using errcode='40001';
  end if;
  return new;
end $$;
drop trigger if exists trg_auth_root_bootstrap_guard on auth_root_bootstrap_state;
create trigger trg_auth_root_bootstrap_guard before update on auth_root_bootstrap_state
for each row execute function phase1a3_guard_root_bootstrap_transition();
