-- Phase 0E-1: Project Isolation and Credential Federation expand migration.
-- External Project access is governed by scoped principals, immutable probes and auditable overrides.

create table if not exists integration_principal_scopes (
  tenant_id varchar(64) not null,
  principal_id varchar(128) not null,
  isolation_mode varchar(32) not null default 'PER_PROJECT',
  scope_reference varchar(255),
  allowed_project_ids_json jsonb not null default '[]'::jsonb,
  production_allowed boolean not null default false,
  version bigint not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, principal_id)
);

create table if not exists integration_security_overrides (
  tenant_id varchar(64) not null,
  override_id varchar(128) not null,
  principal_id varchar(128) not null,
  mapping_id varchar(128),
  override_type varchar(64) not null,
  reason text not null,
  approved_by varchar(128) not null,
  approved_at timestamptz not null default now(),
  expires_at timestamptz not null,
  status varchar(32) not null default 'APPROVED',
  revoked_by varchar(128),
  revoked_at timestamptz,
  correlation_id varchar(128),
  version bigint not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, override_id)
);

create table if not exists integration_permission_change_events (
  tenant_id varchar(64) not null,
  change_event_id varchar(128) not null,
  principal_id varchar(128) not null,
  mapping_id varchar(128),
  previous_probe_id varchar(128),
  current_probe_id varchar(128) not null,
  previous_fingerprint varchar(128),
  current_fingerprint varchar(128) not null,
  change_summary text not null,
  detected_at timestamptz not null default now(),
  correlation_id varchar(128),
  primary key (tenant_id, change_event_id)
);

create table if not exists integration_provider_authorization_failures (
  tenant_id varchar(64) not null,
  failure_id varchar(128) not null,
  connection_id varchar(128) not null,
  principal_id varchar(128) not null,
  credential_id varchar(128),
  mapping_id varchar(128),
  provider_status integer not null,
  operation_code varchar(32) not null,
  reason_code varchar(128) not null,
  reprobe_status varchar(32) not null default 'PENDING',
  correlation_id varchar(128),
  occurred_at timestamptz not null default now(),
  reprobed_at timestamptz,
  primary key (tenant_id, failure_id)
);

alter table integration_credential_rotation_events add column if not exists grace_expires_at timestamptz;
alter table integration_credential_rotation_events add column if not exists activated_at timestamptz;
alter table integration_project_mappings add column if not exists reprobe_required boolean not null default true;
alter table integration_project_mappings add column if not exists last_permission_fingerprint varchar(128);
alter table integration_project_mappings add column if not exists last_permission_changed_at timestamptz;
alter table integration_project_mappings add column if not exists last_authorization_failure_at timestamptz;

create index if not exists idx_integration_security_overrides_active
  on integration_security_overrides(tenant_id, principal_id, mapping_id, expires_at)
  where status='APPROVED';
create index if not exists idx_integration_permission_changes_principal
  on integration_permission_change_events(tenant_id, principal_id, detected_at desc);
create index if not exists idx_integration_auth_failures_mapping
  on integration_provider_authorization_failures(tenant_id, mapping_id, occurred_at desc);
