-- Phase 1A-5 Expand: Access Token and Service Account Foundation.
-- All tables are tenant-owned. Full token secrets are never persisted.

create table if not exists token_tenant_policies (
  tenant_id varchar(64) primary key references tenants(tenant_id),
  personal_default_ttl_seconds bigint not null default 2592000,
  personal_max_ttl_seconds bigint not null default 7776000,
  service_default_ttl_seconds bigint not null default 2592000,
  service_max_ttl_seconds bigint not null default 7776000,
  max_active_personal_tokens integer not null default 10,
  max_active_service_tokens integer not null default 2,
  require_service_cidr boolean not null default true,
  updated_at timestamptz not null default now(),
  updated_by varchar(128) not null default 'phase1a5-migration',
  version bigint not null default 1
);
insert into token_tenant_policies(tenant_id)
select tenant_id from tenants on conflict(tenant_id) do nothing;

create table if not exists token_service_accounts (
  tenant_id varchar(64) not null,
  service_account_id varchar(128) not null,
  account_name varchar(160) not null,
  description text not null default '',
  owner_user_id varchar(128) not null references iam_users(user_id),
  owner_department_id varchar(128) not null,
  permission_scopes varchar(160)[] not null default '{}',
  allowed_audiences varchar(160)[] not null default '{}',
  allowed_api_prefixes varchar(256)[] not null default '{}',
  allowed_cidrs varchar(80)[] not null,
  token_max_ttl_seconds bigint not null default 2592000,
  max_active_tokens integer not null default 2,
  rate_limit_per_minute integer not null default 60,
  last_reviewed_at timestamptz,
  next_review_at timestamptz not null,
  risk_level varchar(32) not null default 'LOW',
  status varchar(32) not null default 'ACTIVE',
  status_reason varchar(500) not null default '',
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by varchar(128) not null,
  updated_by varchar(128) not null,
  version bigint not null default 1,
  primary key(tenant_id, service_account_id),
  foreign key(tenant_id) references tenants(tenant_id),
  foreign key(tenant_id, owner_department_id) references departments(tenant_id, department_id)
);
create unique index if not exists uq_token_service_account_name on token_service_accounts(tenant_id, lower(account_name));
create index if not exists idx_token_service_account_owner on token_service_accounts(tenant_id, owner_user_id, status);
create index if not exists idx_token_service_account_review on token_service_accounts(tenant_id, status, next_review_at);

create table if not exists token_access_tokens (
  tenant_id varchar(64) not null,
  token_id varchar(128) not null,
  token_type varchar(48) not null,
  principal_type varchar(40) not null,
  principal_id varchar(128) not null,
  token_name varchar(160) not null,
  token_prefix varchar(96) not null,
  last4 varchar(4) not null,
  hash_algorithm varchar(32) not null,
  token_hash varchar(512) not null,
  permission_scopes varchar(160)[] not null default '{}',
  audiences varchar(160)[] not null default '{}',
  api_prefixes varchar(256)[] not null default '{}',
  allowed_cidrs varchar(80)[] not null default '{}',
  issued_at timestamptz not null,
  expires_at timestamptz not null,
  last_used_at timestamptz,
  rotated_from_token_id varchar(128),
  rotation_grace_expires_at timestamptz,
  revoked_at timestamptz,
  revoked_by varchar(128),
  revocation_reason varchar(500) not null default '',
  consumed_at timestamptz,
  status varchar(32) not null default 'ACTIVE',
  global_security_epoch bigint not null default 0,
  tenant_security_epoch bigint not null default 0,
  principal_security_epoch bigint not null default 0,
  use_count bigint not null default 0,
  version bigint not null default 1,
  primary key(tenant_id, token_id),
  unique(token_prefix),
  foreign key(tenant_id) references tenants(tenant_id),
  foreign key(tenant_id, rotated_from_token_id) references token_access_tokens(tenant_id, token_id)
);
create index if not exists idx_token_active_principal on token_access_tokens(tenant_id, principal_type, principal_id, token_type, status, expires_at);
create index if not exists idx_token_expiration on token_access_tokens(tenant_id, status, expires_at);
create index if not exists idx_token_last_used on token_access_tokens(tenant_id, last_used_at desc);

create table if not exists token_usage_events (
  event_id varchar(128) primary key,
  tenant_id varchar(64) not null,
  token_id varchar(128) not null,
  token_type varchar(48) not null,
  principal_type varchar(40) not null,
  principal_id varchar(128) not null,
  outcome varchar(16) not null,
  reason_code varchar(128) not null default '',
  audience varchar(160),
  api_path varchar(512),
  source_ip varchar(80),
  occurred_at timestamptz not null,
  correlation_id varchar(128),
  foreign key(tenant_id, token_id) references token_access_tokens(tenant_id, token_id)
);
create index if not exists idx_token_usage_tenant_time on token_usage_events(tenant_id, occurred_at desc);
create index if not exists idx_token_usage_token_time on token_usage_events(tenant_id, token_id, occurred_at desc);

create table if not exists token_rate_limit_windows (
  tenant_id varchar(64) not null,
  rate_limit_key varchar(256) not null,
  window_start timestamptz not null,
  request_count integer not null default 0,
  primary key(tenant_id, rate_limit_key, window_start),
  foreign key(tenant_id) references tenants(tenant_id)
);
create index if not exists idx_token_rate_limit_expiration on token_rate_limit_windows(tenant_id, window_start);

insert into reason_code_catalog(reason_code,http_status,category,retryable,message_template)
values
 ('AUTH_TOKEN_NOT_FOUND',404,'VALIDATION',false,'The access token was not found.'),
 ('AUTH_TOKEN_INVALID',401,'AUTHENTICATION',false,'The access token is invalid.'),
 ('AUTH_TOKEN_EXPIRED',401,'AUTHENTICATION',false,'The access token has expired.'),
 ('AUTH_TOKEN_REVOKED',401,'AUTHENTICATION',false,'The access token was revoked.'),
 ('AUTH_TOKEN_ALREADY_CONSUMED',401,'AUTHENTICATION',false,'The one-time token was already consumed.'),
 ('AUTH_TOKEN_SCOPE_INSUFFICIENT',403,'AUTHORIZATION',false,'The token scope does not permit this action.'),
 ('AUTH_TOKEN_TENANT_MISMATCH',403,'AUTHORIZATION',false,'The token is bound to another Tenant.'),
 ('AUTH_TOKEN_AUDIENCE_DENIED',403,'AUTHORIZATION',false,'The requested audience is not permitted by the token.'),
 ('AUTH_TOKEN_API_PREFIX_DENIED',403,'AUTHORIZATION',false,'The requested API prefix is not permitted by the token.'),
 ('AUTH_TOKEN_CIDR_DENIED',403,'AUTHORIZATION',false,'The source address is not permitted by the token.'),
 ('AUTH_TOKEN_SECURITY_EPOCH_STALE',401,'AUTHENTICATION',true,'The token security epoch is stale.'),
 ('AUTH_TOKEN_TTL_EXCEEDED',400,'VALIDATION',false,'The requested token lifetime exceeds policy.'),
 ('AUTH_TOKEN_ACTIVE_LIMIT_EXCEEDED',409,'SECURITY',false,'The active token limit was reached.'),
 ('AUTH_TOKEN_RATE_LIMITED',429,'SECURITY',true,'The token rate limit was exceeded.'),
 ('AUTH_TOKEN_ROTATION_CONFLICT',409,'SECURITY',false,'The token cannot be rotated in its current state.'),
 ('AUTH_TOKEN_TYPE_MISMATCH',400,'VALIDATION',false,'The token type is not valid for this operation.'),
 ('SERVICE_ACCOUNT_NOT_FOUND',404,'VALIDATION',false,'The service account was not found.'),
 ('SERVICE_ACCOUNT_OWNER_REQUIRED',400,'VALIDATION',false,'An active owner user is required.'),
 ('SERVICE_ACCOUNT_DEPARTMENT_REQUIRED',400,'VALIDATION',false,'An active owner Department is required.'),
 ('SERVICE_ACCOUNT_OWNER_INACTIVE',409,'SECURITY',false,'The owner user or owner Department is inactive.'),
 ('SERVICE_ACCOUNT_CIDR_REQUIRED',400,'VALIDATION',false,'At least one allowed CIDR is required.'),
 ('SERVICE_ACCOUNT_INSTANCE_PERMISSION_FORBIDDEN',400,'AUTHORIZATION',false,'Service accounts cannot receive Instance permissions.'),
 ('SERVICE_ACCOUNT_RISK_SUSPENDED',403,'SECURITY',false,'The service account is suspended due to risk.'),
 ('SERVICE_ACCOUNT_OWNERSHIP_REVIEW_REQUIRED',409,'SECURITY',false,'The service account requires ownership review.'),
 ('SERVICE_ACCOUNT_INVALID_STATUS',409,'SECURITY',false,'The service account status does not permit this operation.')
on conflict(reason_code) do update set http_status=excluded.http_status,category=excluded.category,retryable=excluded.retryable,message_template=excluded.message_template,active=true;

insert into domain_event_catalog(event_type,payload_version,aggregate_type,description,exportable)
values
 ('SERVICE_ACCOUNT_CREATED','1','SERVICE_ACCOUNT','A service account was created.',false),
 ('SERVICE_ACCOUNT_OWNERSHIP_REVIEWED','1','SERVICE_ACCOUNT','Service account ownership was reviewed.',false),
 ('SERVICE_ACCOUNT_OWNERSHIP_REVIEW_REQUIRED','1','SERVICE_ACCOUNT','A service account requires ownership review.',false),
 ('SERVICE_ACCOUNT_RISK_SUSPENDED','1','SERVICE_ACCOUNT','A service account was risk suspended.',false),
 ('TOKEN_CREATED','1','ACCESS_TOKEN','An access token was issued.',false),
 ('TOKEN_ROTATED','1','ACCESS_TOKEN','An access token was rotated.',false),
 ('TOKEN_REVOKED','1','ACCESS_TOKEN','An access token was revoked.',false),
 ('TOKEN_CONSUMED','1','ACCESS_TOKEN','A one-time token was consumed.',false),
 ('TOKEN_USED','1','ACCESS_TOKEN','An access token was used.',false)
on conflict(event_type,payload_version) do nothing;
