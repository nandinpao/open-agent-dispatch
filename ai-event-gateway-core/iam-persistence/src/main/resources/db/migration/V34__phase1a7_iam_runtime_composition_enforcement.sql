-- Phase 1A-7 enforcement: bounded durable responses, tenant RLS and one-time
-- login challenge consumption.

alter table iam_api_instance_idempotency_records
  add constraint ck_iam_api_instance_idem_status check(status in('IN_PROGRESS','COMPLETED')),
  add constraint ck_iam_api_instance_idem_expiry check(expires_at > created_at),
  add constraint ck_iam_api_instance_idem_response check(
    (status='IN_PROGRESS' and response_http_status is null and response_body is null)
    or
    (status='COMPLETED' and response_http_status between 100 and 599 and response_body is not null and completed_at is not null)
  ),
  add constraint ck_iam_api_instance_idem_response_size check(response_body is null or octet_length(response_body) <= 1048576);

alter table iam_api_tenant_idempotency_records
  add constraint ck_iam_api_tenant_idem_status check(status in('IN_PROGRESS','COMPLETED')),
  add constraint ck_iam_api_tenant_idem_expiry check(expires_at > created_at),
  add constraint ck_iam_api_tenant_idem_response check(
    (status='IN_PROGRESS' and response_http_status is null and response_body is null)
    or
    (status='COMPLETED' and response_http_status between 100 and 599 and response_body is not null and completed_at is not null)
  ),
  add constraint ck_iam_api_tenant_idem_response_size check(response_body is null or octet_length(response_body) <= 1048576);

alter table auth_login_challenges
  add constraint ck_auth_login_challenge_subject check(subject_type in('INSTANCE_ROOT','HUMAN_USER')),
  add constraint ck_auth_login_challenge_status check(status in('ACTIVE','CONSUMED','EXPIRED','REVOKED')),
  add constraint ck_auth_login_challenge_hash check(length(challenge_hash)>=32 and challenge_hash not like 'odp\_%'),
  add constraint ck_auth_login_challenge_expiry check(expires_at > authenticated_at and expires_at <= authenticated_at + interval '10 minutes'),
  add constraint ck_auth_login_challenge_version check(version > 0),
  add constraint ck_auth_login_challenge_consumed check((status='CONSUMED')=(consumed_at is not null));

alter table auth_tenant_mfa_policies enable row level security;
alter table auth_tenant_mfa_policies force row level security;
drop policy if exists tenant_isolation on auth_tenant_mfa_policies;
create policy tenant_isolation on auth_tenant_mfa_policies using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());

alter table iam_api_tenant_idempotency_records enable row level security;
alter table iam_api_tenant_idempotency_records force row level security;
drop policy if exists tenant_isolation on iam_api_tenant_idempotency_records;
create policy tenant_isolation on iam_api_tenant_idempotency_records
  using(tenant_id=iam_current_tenant_id())
  with check(tenant_id=iam_current_tenant_id());

create or replace function phase1a7_consume_login_challenge(
  p_challenge_id varchar,
  p_challenge_hash varchar,
  p_consumed_at timestamptz)
returns table(subject_type varchar,subject_id varchar,requested_tenant_id varchar,authenticated_at timestamptz,version bigint)
language plpgsql security definer set search_path=public,pg_temp as $$
begin
  return query
  update auth_login_challenges c
     set status='CONSUMED', consumed_at=p_consumed_at, version=c.version+1
   where c.challenge_id=p_challenge_id
     and c.challenge_hash=p_challenge_hash
     and c.status='ACTIVE'
     and c.expires_at>p_consumed_at
  returning c.subject_type,c.subject_id,c.requested_tenant_id,c.authenticated_at,c.version;
end $$;

revoke all on function phase1a7_consume_login_challenge(varchar,varchar,timestamptz) from public;
grant execute on function phase1a7_consume_login_challenge(varchar,varchar,timestamptz) to opendispatch_runtime;
revoke update, delete on auth_login_challenges from opendispatch_runtime;
grant select, insert on auth_login_challenges to opendispatch_runtime;
revoke insert, update, delete on iam_user_tenant_directory, iam_one_time_token_directory from opendispatch_runtime;
grant select on iam_user_tenant_directory, iam_one_time_token_directory to opendispatch_runtime;

create or replace function phase1a7_guard_idempotency_identity()
returns trigger language plpgsql as $$
begin
  if new.scope_id is distinct from old.scope_id
     or new.actor_id is distinct from old.actor_id
     or new.operation is distinct from old.operation
     or new.idempotency_key is distinct from old.idempotency_key
     or new.request_hash is distinct from old.request_hash
     or new.created_at is distinct from old.created_at
     or new.expires_at is distinct from old.expires_at then
    raise exception 'IAM_IDEMPOTENCY_IDENTITY_IMMUTABLE' using errcode='55000';
  end if;
  if old.status='COMPLETED' and new is distinct from old then
    raise exception 'IAM_IDEMPOTENCY_RESPONSE_IMMUTABLE' using errcode='55000';
  end if;
  return new;
end $$;

drop trigger if exists trg_iam_api_instance_idempotency_guard on iam_api_instance_idempotency_records;
create trigger trg_iam_api_instance_idempotency_guard before update on iam_api_instance_idempotency_records
for each row execute function phase1a7_guard_idempotency_identity();

create or replace function phase1a7_guard_tenant_idempotency_identity()
returns trigger language plpgsql as $$
begin
  if new.tenant_id is distinct from old.tenant_id
     or new.actor_id is distinct from old.actor_id
     or new.operation is distinct from old.operation
     or new.idempotency_key is distinct from old.idempotency_key
     or new.request_hash is distinct from old.request_hash
     or new.created_at is distinct from old.created_at
     or new.expires_at is distinct from old.expires_at then
    raise exception 'IAM_IDEMPOTENCY_IDENTITY_IMMUTABLE' using errcode='55000';
  end if;
  if old.status='COMPLETED' and new is distinct from old then
    raise exception 'IAM_IDEMPOTENCY_RESPONSE_IMMUTABLE' using errcode='55000';
  end if;
  return new;
end $$;

drop trigger if exists trg_iam_api_tenant_idempotency_guard on iam_api_tenant_idempotency_records;
create trigger trg_iam_api_tenant_idempotency_guard before update on iam_api_tenant_idempotency_records
for each row execute function phase1a7_guard_tenant_idempotency_identity();


-- Cross-Tenant login selection reads a deliberately narrow, instance-scope projection.
-- It contains no permissions or credential data and is maintained transactionally.
create or replace function phase1a7_refresh_user_tenant_directory()
returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
begin
  if tg_table_name='org_tenant_memberships' then
    if tg_op='DELETE' then
      delete from iam_user_tenant_directory
       where user_id=old.user_id and tenant_id=old.tenant_id;
      return old;
    end if;
    insert into iam_user_tenant_directory(user_id,tenant_id,tenant_code,tenant_name,membership_status,tenant_status,expires_at,updated_at)
    select new.user_id,new.tenant_id,t.tenant_code,t.display_name,new.status,t.status,new.expires_at,now() from tenants t where t.tenant_id=new.tenant_id
    on conflict(user_id,tenant_id) do update set membership_status=excluded.membership_status,tenant_code=excluded.tenant_code,tenant_name=excluded.tenant_name,tenant_status=excluded.tenant_status,expires_at=excluded.expires_at,updated_at=excluded.updated_at;
  elsif tg_table_name='tenants' then
    perform set_config('app.current_tenant_id',new.tenant_id,true);
    perform set_config('app.current_actor_id',coalesce(nullif(new.updated_by,''),'phase1a7-system'),true);
    insert into auth_tenant_mfa_policies(tenant_id,updated_at,updated_by)
      values(new.tenant_id,now(),coalesce(nullif(new.updated_by,''),'phase1a7-system'))
      on conflict(tenant_id) do nothing;
    insert into auth_tenant_password_policies(
      tenant_id,minimum_length,maximum_length,require_uppercase,require_lowercase,require_number,require_symbol,
      password_history_count,maximum_age_seconds,minimum_age_seconds,failed_attempt_threshold,lockout_duration_seconds,
      breached_password_mode,updated_at,updated_by,version)
    select new.tenant_id,minimum_length,maximum_length,require_uppercase,require_lowercase,require_number,require_symbol,
      password_history_count,maximum_age_seconds,minimum_age_seconds,failed_attempt_threshold,lockout_duration_seconds,
      breached_password_mode,now(),coalesce(nullif(new.updated_by,''),'phase1a7-system'),1
      from auth_instance_password_policy where policy_id='INSTANCE_MINIMUM'
      on conflict(tenant_id) do nothing;
    insert into auth_tenant_session_policies(
      tenant_id,idle_timeout_seconds,absolute_timeout_seconds,max_concurrent_sessions,revoke_on_password_change,
      revoke_on_mfa_reset,reauthentication_window_seconds,updated_at,updated_by,version)
    select new.tenant_id,idle_timeout_seconds,absolute_timeout_seconds,max_concurrent_sessions,revoke_on_password_change,
      revoke_on_mfa_reset,reauthentication_window_seconds,now(),coalesce(nullif(new.updated_by,''),'phase1a7-system'),1
      from auth_instance_session_policy where policy_id='INSTANCE_MINIMUM'
      on conflict(tenant_id) do nothing;
    insert into token_tenant_policies(tenant_id,updated_by)
      values(new.tenant_id,coalesce(nullif(new.updated_by,''),'phase1a7-system'))
      on conflict(tenant_id) do nothing;
    update iam_user_tenant_directory set tenant_code=new.tenant_code,tenant_name=new.display_name,tenant_status=new.status,updated_at=now() where tenant_id=new.tenant_id;
  end if;
  return new;
end $$;

drop trigger if exists trg_phase1a7_membership_directory on org_tenant_memberships;
create trigger trg_phase1a7_membership_directory after insert or update or delete on org_tenant_memberships
for each row execute function phase1a7_refresh_user_tenant_directory();

drop trigger if exists trg_phase1a7_tenant_directory on tenants;
create trigger trg_phase1a7_tenant_directory after insert or update of tenant_code,display_name,status on tenants
for each row execute function phase1a7_refresh_user_tenant_directory();

revoke all on function phase1a7_refresh_user_tenant_directory() from public;


create or replace function phase1a7_refresh_one_time_token_directory()
returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
begin
  if new.token_type in('INVITATION_TOKEN','PASSWORD_RESET_TOKEN','EMAIL_VERIFICATION_TOKEN') then
    insert into iam_one_time_token_directory(token_prefix,tenant_id,token_type,expires_at,status,updated_at)
    values(new.token_prefix,new.tenant_id,new.token_type,new.expires_at,new.status,now())
    on conflict(token_prefix) do update set tenant_id=excluded.tenant_id,token_type=excluded.token_type,expires_at=excluded.expires_at,status=excluded.status,updated_at=excluded.updated_at;
  end if;
  return new;
end $$;
drop trigger if exists trg_phase1a7_one_time_token_directory on token_access_tokens;
create trigger trg_phase1a7_one_time_token_directory after insert or update of status,expires_at on token_access_tokens
for each row execute function phase1a7_refresh_one_time_token_directory();

revoke all on function phase1a7_refresh_one_time_token_directory() from public;
