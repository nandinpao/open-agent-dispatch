-- Phase 1A-7 expand: durable API idempotency, one-time login challenges and
-- runtime projection support. No IAM route is activated by this migration.

create table if not exists iam_api_instance_idempotency_records (
  scope_id varchar(128) not null,
  actor_id varchar(128) not null,
  operation varchar(160) not null,
  idempotency_key varchar(200) not null,
  request_hash char(64) not null,
  status varchar(24) not null,
  response_http_status integer,
  response_content_type varchar(160),
  response_body text,
  created_at timestamptz not null,
  completed_at timestamptz,
  expires_at timestamptz not null,
  primary key(scope_id,actor_id,operation,idempotency_key)
);
create index if not exists idx_iam_api_instance_idempotency_expiry
  on iam_api_instance_idempotency_records(expires_at);

create table if not exists iam_api_tenant_idempotency_records (
  tenant_id varchar(64) not null,
  actor_id varchar(128) not null,
  operation varchar(160) not null,
  idempotency_key varchar(200) not null,
  request_hash char(64) not null,
  status varchar(24) not null,
  response_http_status integer,
  response_content_type varchar(160),
  response_body text,
  created_at timestamptz not null,
  completed_at timestamptz,
  expires_at timestamptz not null,
  primary key(tenant_id,actor_id,operation,idempotency_key),
  foreign key(tenant_id) references tenants(tenant_id)
);
create index if not exists idx_iam_api_tenant_idempotency_expiry
  on iam_api_tenant_idempotency_records(tenant_id,expires_at);

create table if not exists auth_tenant_mfa_policies (
  tenant_id varchar(64) primary key references tenants(tenant_id),
  required_for_administrators boolean not null default true,
  allow_recovery_codes boolean not null default true,
  allow_totp boolean not null default true,
  updated_at timestamptz not null,
  updated_by varchar(128) not null,
  version bigint not null default 1
);
insert into auth_tenant_mfa_policies(tenant_id,updated_at,updated_by)
select tenant_id,now(),'phase1a7-migration' from tenants on conflict(tenant_id) do nothing;

create table if not exists iam_user_tenant_directory (
  user_id varchar(128) not null references iam_users(user_id),
  tenant_id varchar(64) not null references tenants(tenant_id),
  tenant_code varchar(64) not null,
  tenant_name varchar(200) not null,
  membership_status varchar(32) not null,
  tenant_status varchar(32) not null,
  expires_at timestamptz,
  updated_at timestamptz not null,
  primary key(user_id,tenant_id)
);
create index if not exists idx_iam_user_tenant_directory_user
  on iam_user_tenant_directory(user_id,membership_status,tenant_status,tenant_name,tenant_id);

-- org_tenant_memberships has FORCE RLS from V26. Backfill one Tenant at a time
-- with transaction-local Tenant context instead of weakening RLS or granting BYPASSRLS.
do $$
declare
  v_tenant_id varchar(64);
  v_previous_tenant text := current_setting('app.current_tenant_id', true);
begin
  for v_tenant_id in select tenant_id from tenants order by tenant_id loop
    perform set_config('app.current_tenant_id', v_tenant_id, true);

    insert into iam_user_tenant_directory(
      user_id,tenant_id,tenant_code,tenant_name,membership_status,tenant_status,expires_at,updated_at)
    select m.user_id,m.tenant_id,t.tenant_code,t.display_name,m.status,t.status,m.expires_at,now()
      from org_tenant_memberships m
      join tenants t on t.tenant_id=m.tenant_id
     where m.tenant_id=v_tenant_id
    on conflict(user_id,tenant_id) do update set
      tenant_code=excluded.tenant_code,
      tenant_name=excluded.tenant_name,
      membership_status=excluded.membership_status,
      tenant_status=excluded.tenant_status,
      expires_at=excluded.expires_at,
      updated_at=excluded.updated_at;
  end loop;

  perform set_config('app.current_tenant_id', coalesce(v_previous_tenant,''), true);
end $$;

create table if not exists iam_one_time_token_directory (
  token_prefix varchar(96) primary key,
  tenant_id varchar(64) not null references tenants(tenant_id),
  token_type varchar(48) not null,
  expires_at timestamptz not null,
  status varchar(32) not null,
  updated_at timestamptz not null
);
create index if not exists idx_iam_one_time_token_directory_expiry
  on iam_one_time_token_directory(token_type,status,expires_at);

-- token_access_tokens has FORCE RLS from V32. Use the same Tenant-scoped
-- migration pattern so clean and upgrade migrations preserve fail-closed isolation.
do $$
declare
  v_tenant_id varchar(64);
  v_previous_tenant text := current_setting('app.current_tenant_id', true);
begin
  for v_tenant_id in select tenant_id from tenants order by tenant_id loop
    perform set_config('app.current_tenant_id', v_tenant_id, true);

    insert into iam_one_time_token_directory(
      token_prefix,tenant_id,token_type,expires_at,status,updated_at)
    select token_prefix,tenant_id,token_type,expires_at,status,now()
      from token_access_tokens
     where tenant_id=v_tenant_id
       and token_type in('INVITATION_TOKEN','PASSWORD_RESET_TOKEN','EMAIL_VERIFICATION_TOKEN')
    on conflict(token_prefix) do update set
      tenant_id=excluded.tenant_id,
      token_type=excluded.token_type,
      expires_at=excluded.expires_at,
      status=excluded.status,
      updated_at=excluded.updated_at;
  end loop;

  perform set_config('app.current_tenant_id', coalesce(v_previous_tenant,''), true);
end $$;

create table if not exists auth_login_challenges (
  challenge_id varchar(128) primary key,
  challenge_hash varchar(512) not null,
  subject_type varchar(32) not null,
  subject_id varchar(128) not null,
  requested_tenant_id varchar(64),
  authenticated_at timestamptz not null,
  expires_at timestamptz not null,
  consumed_at timestamptz,
  status varchar(24) not null,
  correlation_id varchar(128) not null,
  ip_address varchar(128),
  user_agent varchar(1000),
  version bigint not null default 1
);
create index if not exists idx_auth_login_challenges_subject
  on auth_login_challenges(subject_type,subject_id,status,expires_at);

insert into reason_code_catalog(reason_code,http_status,category,retryable,message_template)
values
 ('IAM_API_COMPOSITION_INCOMPLETE',503,'IAM',false,'IAM API runtime composition is incomplete.'),
 ('IAM_IDEMPOTENCY_KEY_CONFLICT',409,'IAM',false,'Idempotency key was reused with a different request.'),
 ('AUTH_LOGIN_CHALLENGE_INVALID',401,'AUTHENTICATION',false,'Login challenge is invalid, expired, or already consumed.'),
 ('AUTH_SESSION_COOKIE_INVALID',401,'AUTHENTICATION',false,'Browser session cookie is malformed or has an invalid signature.')
on conflict(reason_code) do nothing;
