-- Phase 0E: Integration Identity and Project Mapping expand migration.
-- Connection, Principal, Credential and Project Mapping are separate aggregates.

create table if not exists integration_connections (
  tenant_id varchar(64) not null,
  connection_id varchar(128) not null,
  provider_type varchar(32) not null,
  connection_name varchar(255) not null,
  base_url text not null,
  deployment_type varchar(32) not null default 'SELF_HOSTED',
  provider_version varchar(64),
  status varchar(32) not null default 'DRAFT',
  timeout_ms integer not null default 10000,
  retry_policy_id varchar(128),
  rate_limit_policy_id varchar(128),
  tls_policy_id varchar(128),
  enabled boolean not null default false,
  version bigint not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, connection_id),
  unique (tenant_id, connection_name)
);

create table if not exists integration_principals (
  tenant_id varchar(64) not null,
  principal_id varchar(128) not null,
  connection_id varchar(128) not null,
  principal_name varchar(255) not null,
  principal_type varchar(32) not null,
  owner_department_id varchar(128),
  owner_group_id varchar(128),
  trust_zone_id varchar(128),
  external_principal_identifier varchar(255),
  status varchar(32) not null default 'DRAFT',
  risk_level varchar(16) not null default 'UNKNOWN',
  permission_summary_json jsonb not null default '{}'::jsonb,
  over_privileged_reasons_json jsonb not null default '[]'::jsonb,
  last_permission_probe_at timestamptz,
  version bigint not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, principal_id),
  unique (tenant_id, connection_id, principal_name)
);

create table if not exists integration_credentials (
  tenant_id varchar(64) not null,
  credential_id varchar(128) not null,
  principal_id varchar(128) not null,
  auth_type varchar(40) not null,
  secret_ref text not null,
  secret_version varchar(64) not null,
  secret_last4 varchar(4),
  valid_from timestamptz,
  expires_at timestamptz,
  rotated_at timestamptz,
  last_used_at timestamptz,
  status varchar(32) not null default 'PENDING_VALIDATION',
  version bigint not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, credential_id),
  unique (tenant_id, principal_id, secret_version)
);

create table if not exists integration_permission_profiles (
  tenant_id varchar(64) not null,
  permission_profile_id varchar(128) not null,
  profile_code varchar(128) not null,
  profile_name varchar(255) not null,
  required_capabilities_json jsonb not null default '[]'::jsonb,
  optional_capabilities_json jsonb not null default '[]'::jsonb,
  description text,
  version bigint not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, permission_profile_id),
  unique (tenant_id, profile_code)
);

create table if not exists integration_project_mappings (
  tenant_id varchar(64) not null,
  mapping_id varchar(128) not null,
  connection_id varchar(128) not null,
  department_id varchar(128),
  group_id varchar(128),
  service_domain_id varchar(128),
  source_system_id varchar(128),
  task_type varchar(128),
  external_project_id varchar(255) not null,
  external_project_key varchar(255),
  external_issue_type varchar(128),
  external_tracker_id varchar(128),
  read_principal_id varchar(128),
  create_principal_id varchar(128),
  comment_principal_id varchar(128),
  update_principal_id varchar(128),
  relation_principal_id varchar(128),
  webhook_principal_id varchar(128),
  permission_profile_id varchar(128),
  context_policy_id varchar(128),
  result_sharing_policy_id varchar(128),
  mapping_status varchar(32) not null default 'DRAFT',
  resolution_priority integer not null default 1000,
  is_default boolean not null default false,
  enabled boolean not null default false,
  version bigint not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, mapping_id),
  unique (tenant_id, connection_id, external_project_id, mapping_id)
);

create table if not exists integration_permission_probe_runs (
  tenant_id varchar(64) not null,
  probe_id varchar(128) not null,
  connection_id varchar(128) not null,
  principal_id varchar(128) not null,
  mapping_id varchar(128),
  overall_status varchar(32) not null default 'IN_PROGRESS',
  provider_response_summary text,
  elevated_permissions_json jsonb not null default '[]'::jsonb,
  started_at timestamptz not null default now(),
  completed_at timestamptz,
  correlation_id varchar(128),
  actor_type varchar(32),
  actor_id varchar(128),
  version bigint not null default 1,
  primary key (tenant_id, probe_id)
);

create table if not exists integration_permission_probe_results (
  tenant_id varchar(64) not null,
  probe_result_id varchar(128) not null,
  probe_id varchar(128) not null,
  capability_code varchar(128) not null,
  result_status varchar(32) not null,
  required_for_mapping boolean not null default false,
  detail text,
  observed_at timestamptz not null default now(),
  primary key (tenant_id, probe_result_id),
  unique (tenant_id, probe_id, capability_code)
);

create table if not exists integration_credential_rotation_events (
  tenant_id varchar(64) not null,
  rotation_event_id varchar(128) not null,
  principal_id varchar(128) not null,
  old_credential_id varchar(128),
  new_credential_id varchar(128) not null,
  status varchar(32) not null default 'PENDING_PROBE',
  reason text,
  correlation_id varchar(128),
  actor_type varchar(32),
  actor_id varchar(128),
  created_at timestamptz not null default now(),
  completed_at timestamptz,
  primary key (tenant_id, rotation_event_id)
);

create table if not exists integration_legacy_configuration_inventory (
  tenant_id varchar(64) not null,
  inventory_id varchar(128) not null,
  legacy_key varchar(255) not null,
  target_aggregate varchar(64) not null,
  target_field varchar(128) not null,
  migration_status varchar(32) not null default 'NOT_STARTED',
  notes text,
  observed_at timestamptz not null default now(),
  primary key (tenant_id, inventory_id),
  unique (tenant_id, legacy_key)
);

create index if not exists idx_integration_principals_connection on integration_principals(tenant_id, connection_id, status);
create index if not exists idx_integration_credentials_principal on integration_credentials(tenant_id, principal_id, status);
create index if not exists idx_integration_mappings_resolution on integration_project_mappings(tenant_id, enabled, department_id, group_id, service_domain_id, source_system_id, task_type, resolution_priority);
create index if not exists idx_integration_probe_principal on integration_permission_probe_runs(tenant_id, principal_id, started_at desc);
create index if not exists idx_integration_probe_mapping on integration_permission_probe_runs(tenant_id, mapping_id, started_at desc);

-- Seed reusable permission profiles for every existing Tenant.
insert into integration_permission_profiles(
  tenant_id, permission_profile_id, profile_code, profile_name,
  required_capabilities_json, optional_capabilities_json, description)
select t.tenant_id, 'profile-issue-create', 'ISSUE_CREATE', 'Create external issues',
       '["AUTHENTICATE","PROJECT_VISIBLE","CREATE_ISSUE"]'::jsonb,
       '["ADD_COMMENT","CREATE_RELATION","OBSERVE_STATUS"]'::jsonb,
       'Minimum permission contract for creating an external issue.'
from tenants t on conflict do nothing;

insert into integration_permission_profiles(
  tenant_id, permission_profile_id, profile_code, profile_name,
  required_capabilities_json, optional_capabilities_json, description)
select t.tenant_id, 'profile-issue-relay', 'ISSUE_RELAY', 'Cross-project issue relay',
       '["AUTHENTICATE","PROJECT_VISIBLE","CREATE_ISSUE","ADD_COMMENT"]'::jsonb,
       '["READ_ISSUE","CREATE_RELATION","READ_ATTACHMENT_METADATA"]'::jsonb,
       'Minimum permission contract for dual-sided issue relay.'
from tenants t on conflict do nothing;
