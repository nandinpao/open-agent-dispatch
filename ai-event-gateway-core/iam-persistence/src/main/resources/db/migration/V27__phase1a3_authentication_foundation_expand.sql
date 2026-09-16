-- Phase 1A-3 Expand: password, MFA, login security, browser session,
-- root bootstrap/recovery and re-authentication persistence.

create table if not exists auth_instance_password_policy (
  policy_id varchar(64) primary key,
  minimum_length integer not null,
  maximum_length integer not null,
  require_uppercase boolean not null,
  require_lowercase boolean not null,
  require_number boolean not null,
  require_symbol boolean not null,
  password_history_count integer not null,
  maximum_age_seconds bigint not null,
  minimum_age_seconds bigint not null,
  failed_attempt_threshold integer not null,
  lockout_duration_seconds bigint not null,
  breached_password_mode varchar(16) not null,
  updated_at timestamptz not null,
  updated_by varchar(128) not null,
  version bigint not null default 1
);
insert into auth_instance_password_policy(
  policy_id,minimum_length,maximum_length,require_uppercase,require_lowercase,
  require_number,require_symbol,password_history_count,maximum_age_seconds,
  minimum_age_seconds,failed_attempt_threshold,lockout_duration_seconds,
  breached_password_mode,updated_at,updated_by,version)
values('INSTANCE_MINIMUM',14,256,true,true,true,true,10,7776000,3600,5,900,'AUDIT',now(),'phase1a3-migration',1)
on conflict(policy_id) do nothing;

create table if not exists auth_tenant_password_policies (
  tenant_id varchar(64) primary key,
  minimum_length integer not null,
  maximum_length integer not null,
  require_uppercase boolean not null,
  require_lowercase boolean not null,
  require_number boolean not null,
  require_symbol boolean not null,
  password_history_count integer not null,
  maximum_age_seconds bigint not null,
  minimum_age_seconds bigint not null,
  failed_attempt_threshold integer not null,
  lockout_duration_seconds bigint not null,
  breached_password_mode varchar(16) not null,
  updated_at timestamptz not null,
  updated_by varchar(128) not null,
  version bigint not null default 1,
  foreign key(tenant_id) references tenants(tenant_id)
);

create table if not exists auth_instance_session_policy (
  policy_id varchar(64) primary key,
  idle_timeout_seconds bigint not null,
  absolute_timeout_seconds bigint not null,
  max_concurrent_sessions integer not null,
  revoke_on_password_change boolean not null,
  revoke_on_mfa_reset boolean not null,
  reauthentication_window_seconds bigint not null,
  updated_at timestamptz not null,
  updated_by varchar(128) not null,
  version bigint not null default 1
);
insert into auth_instance_session_policy values(
  'INSTANCE_MINIMUM',1800,43200,5,true,true,300,now(),'phase1a3-migration',1)
on conflict(policy_id) do nothing;

create table if not exists auth_tenant_session_policies (
  tenant_id varchar(64) primary key,
  idle_timeout_seconds bigint not null,
  absolute_timeout_seconds bigint not null,
  max_concurrent_sessions integer not null,
  revoke_on_password_change boolean not null,
  revoke_on_mfa_reset boolean not null,
  reauthentication_window_seconds bigint not null,
  updated_at timestamptz not null,
  updated_by varchar(128) not null,
  version bigint not null default 1,
  foreign key(tenant_id) references tenants(tenant_id)
);

create table if not exists auth_password_credentials (
  subject_type varchar(32) not null,
  subject_id varchar(128) not null,
  hash_algorithm varchar(64) not null,
  password_hash varchar(1024) not null,
  changed_at timestamptz not null,
  expires_at timestamptz not null,
  must_change boolean not null default false,
  version bigint not null default 1,
  primary key(subject_type,subject_id)
);

create table if not exists auth_password_history (
  history_id varchar(128) primary key,
  subject_type varchar(32) not null,
  subject_id varchar(128) not null,
  hash_algorithm varchar(64) not null,
  password_hash varchar(1024) not null,
  changed_at timestamptz not null
);
create index if not exists idx_auth_password_history_subject
  on auth_password_history(subject_type,subject_id,changed_at desc,history_id);

create table if not exists auth_mfa_methods (
  method_id varchar(128) primary key,
  subject_type varchar(32) not null,
  subject_id varchar(128) not null,
  method_type varchar(32) not null,
  status varchar(32) not null,
  protected_secret text not null,
  key_id varchar(128) not null,
  digits integer not null,
  period_seconds integer not null,
  algorithm varchar(32) not null,
  enrolled_at timestamptz not null,
  verified_at timestamptz,
  version bigint not null default 1
);
create unique index if not exists uq_auth_mfa_active_subject
  on auth_mfa_methods(subject_type,subject_id) where status='ACTIVE';
create index if not exists idx_auth_mfa_subject on auth_mfa_methods(subject_type,subject_id,status);

create table if not exists auth_recovery_codes (
  recovery_code_id varchar(128) primary key,
  subject_type varchar(32) not null,
  subject_id varchar(128) not null,
  code_hash varchar(512) not null,
  created_at timestamptz not null,
  used_at timestamptz
);
create index if not exists idx_auth_recovery_unused
  on auth_recovery_codes(subject_type,subject_id,created_at) where used_at is null;

create table if not exists auth_subject_security_states (
  subject_type varchar(32) not null,
  subject_id varchar(128) not null,
  failed_login_count integer not null default 0,
  locked_until timestamptz,
  last_success_at timestamptz,
  version bigint not null default 1,
  primary key(subject_type,subject_id)
);

create table if not exists auth_login_attempts (
  attempt_id varchar(128) primary key,
  normalized_username varchar(128) not null,
  subject_id varchar(128),
  tenant_id varchar(64),
  successful boolean not null,
  reason_code varchar(128) not null,
  ip_address varchar(128),
  user_agent varchar(1000),
  correlation_id varchar(128) not null,
  attempted_at timestamptz not null
);
create index if not exists idx_auth_login_attempts_username_time
  on auth_login_attempts(normalized_username,attempted_at desc);
create index if not exists idx_auth_login_attempts_subject_time
  on auth_login_attempts(subject_id,attempted_at desc) where subject_id is not null;
create index if not exists idx_auth_login_attempts_ip_time
  on auth_login_attempts(ip_address,attempted_at desc) where ip_address is not null;

create table if not exists iam_global_security_epoch (
  singleton_id varchar(32) primary key,
  security_epoch bigint not null default 0,
  updated_at timestamptz not null,
  updated_by varchar(128) not null
);
insert into iam_global_security_epoch values('GLOBAL',0,now(),'phase1a3-migration')
on conflict(singleton_id) do nothing;

create table if not exists iam_global_principal_security_epochs (
  principal_id varchar(128) primary key,
  security_epoch bigint not null default 0,
  updated_at timestamptz not null,
  updated_by varchar(128) not null
);

create table if not exists iam_principal_security_epochs (
  tenant_id varchar(64) not null,
  principal_id varchar(128) not null,
  security_epoch bigint not null default 0,
  updated_at timestamptz not null,
  updated_by varchar(128) not null,
  primary key(tenant_id,principal_id),
  foreign key(tenant_id) references tenants(tenant_id)
);

create table if not exists auth_tenant_sessions (
  tenant_id varchar(64) not null,
  session_id varchar(256) not null,
  subject_type varchar(32) not null,
  subject_id varchar(128) not null,
  authentication_methods varchar(32)[] not null default '{}',
  mfa_verified_at timestamptz,
  created_at timestamptz not null,
  last_seen_at timestamptz not null,
  idle_expires_at timestamptz not null,
  absolute_expires_at timestamptz not null,
  ip_address varchar(128),
  user_agent varchar(1000),
  status varchar(32) not null,
  revoked_at timestamptz,
  revoked_by varchar(128),
  revoke_reason varchar(500),
  global_security_epoch bigint not null,
  tenant_security_epoch bigint not null,
  principal_security_epoch bigint not null,
  version bigint not null default 1,
  primary key(tenant_id,session_id),
  foreign key(tenant_id) references tenants(tenant_id),
  foreign key(subject_id) references iam_users(user_id)
);
create index if not exists idx_auth_tenant_sessions_subject
  on auth_tenant_sessions(tenant_id,subject_type,subject_id,status,last_seen_at desc);

create table if not exists auth_root_sessions (
  session_id varchar(256) primary key,
  subject_id varchar(128) not null,
  authentication_methods varchar(32)[] not null default '{}',
  mfa_verified_at timestamptz,
  created_at timestamptz not null,
  last_seen_at timestamptz not null,
  idle_expires_at timestamptz not null,
  absolute_expires_at timestamptz not null,
  ip_address varchar(128),
  user_agent varchar(1000),
  status varchar(32) not null,
  revoked_at timestamptz,
  revoked_by varchar(128),
  revoke_reason varchar(500),
  global_security_epoch bigint not null,
  principal_security_epoch bigint not null,
  version bigint not null default 1,
  foreign key(subject_id) references iam_root_identities(root_identity_id)
);
create index if not exists idx_auth_root_sessions_status on auth_root_sessions(status,created_at desc);

create table if not exists auth_root_bootstrap_state (
  singleton_id varchar(32) primary key,
  status varchar(32) not null,
  password_configured boolean not null,
  mfa_configured boolean not null,
  tenant_created boolean not null,
  tenant_admin_created boolean not null,
  completed_at timestamptz,
  version bigint not null default 1
);
insert into auth_root_bootstrap_state values('ROOT', 'REQUIRED', false,false,false,false,null,1)
on conflict(singleton_id) do nothing;

create table if not exists auth_root_recovery_grants (
  grant_id varchar(128) primary key,
  initiator_id varchar(128) not null,
  approver_id varchar(128) not null,
  grant_hash varchar(512) not null,
  issued_at timestamptz not null,
  expires_at timestamptz not null,
  consumed_at timestamptz,
  status varchar(32) not null,
  version bigint not null default 1
);

create table if not exists auth_tenant_reauthentication_grants (
  tenant_id varchar(64) not null,
  grant_id varchar(128) not null,
  session_id varchar(256) not null,
  subject_id varchar(128) not null,
  purposes varchar(128)[] not null,
  issued_at timestamptz not null,
  expires_at timestamptz not null,
  consumed boolean not null default false,
  version bigint not null default 1,
  primary key(tenant_id,grant_id),
  foreign key(tenant_id,session_id) references auth_tenant_sessions(tenant_id,session_id)
);

create table if not exists auth_root_reauthentication_grants (
  grant_id varchar(128) primary key,
  session_id varchar(256) not null references auth_root_sessions(session_id),
  subject_id varchar(128) not null,
  purposes varchar(128)[] not null,
  issued_at timestamptz not null,
  expires_at timestamptz not null,
  consumed boolean not null default false,
  version bigint not null default 1
);
