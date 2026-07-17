-- Phase 24 live hotfix: ensure AgentGovernanceDao support tables exist.
-- Safe for local/dev databases that were created before the clean V1 baseline
-- included Agent governance audit/security support tables. Schema only; no
-- business rows are inserted or mutated.

create table if not exists agent_authorization_scopes (
  scope_id varchar(128) primary key,
  agent_id varchar(128) not null,
  tenant_id varchar(64) not null,
  system_code varchar(128),
  site_code varchar(128),
  event_type varchar(128),
  task_type varchar(128),
  data_classification_limit varchar(128),
  enabled boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
alter table agent_authorization_scopes add column if not exists scope_id varchar(128);
alter table agent_authorization_scopes add column if not exists agent_id varchar(128);
alter table agent_authorization_scopes add column if not exists tenant_id varchar(64);
alter table agent_authorization_scopes add column if not exists system_code varchar(128);
alter table agent_authorization_scopes add column if not exists site_code varchar(128);
alter table agent_authorization_scopes add column if not exists event_type varchar(128);
alter table agent_authorization_scopes add column if not exists task_type varchar(128);
alter table agent_authorization_scopes add column if not exists data_classification_limit varchar(128);
alter table agent_authorization_scopes add column if not exists enabled boolean not null default true;
alter table agent_authorization_scopes add column if not exists created_at timestamptz not null default now();
alter table agent_authorization_scopes add column if not exists updated_at timestamptz not null default now();
create index if not exists idx_agent_authorization_scopes_agent_enabled on agent_authorization_scopes(agent_id, enabled);
create index if not exists idx_agent_authorization_scopes_tenant_system on agent_authorization_scopes(tenant_id, system_code, task_type);

create table if not exists agent_approval_audit (
  audit_id varchar(128) primary key,
  agent_id varchar(128),
  enrollment_id varchar(128),
  action varchar(64) not null,
  old_status varchar(64),
  new_status varchar(64),
  operator_id varchar(128),
  reason text,
  created_at timestamptz not null default now()
);
alter table agent_approval_audit add column if not exists audit_id varchar(128);
alter table agent_approval_audit add column if not exists agent_id varchar(128);
alter table agent_approval_audit add column if not exists enrollment_id varchar(128);
alter table agent_approval_audit add column if not exists action varchar(64);
alter table agent_approval_audit add column if not exists old_status varchar(64);
alter table agent_approval_audit add column if not exists new_status varchar(64);
alter table agent_approval_audit add column if not exists operator_id varchar(128);
alter table agent_approval_audit add column if not exists reason text;
alter table agent_approval_audit add column if not exists created_at timestamptz not null default now();
create index if not exists idx_agent_approval_audit_agent_created on agent_approval_audit(agent_id, created_at desc);
create index if not exists idx_agent_approval_audit_enrollment_created on agent_approval_audit(enrollment_id, created_at desc);

create table if not exists agent_security_events (
  security_event_id varchar(128) primary key,
  gateway_node_id varchar(128),
  claimed_agent_id varchar(128),
  agent_id varchar(128),
  event_type varchar(128) not null,
  reason text,
  fingerprint varchar(255),
  remote_address varchar(255),
  metadata_json jsonb not null default '{}'::jsonb,
  occurred_at timestamptz,
  created_at timestamptz not null default now()
);
alter table agent_security_events add column if not exists security_event_id varchar(128);
alter table agent_security_events add column if not exists gateway_node_id varchar(128);
alter table agent_security_events add column if not exists claimed_agent_id varchar(128);
alter table agent_security_events add column if not exists agent_id varchar(128);
alter table agent_security_events add column if not exists event_type varchar(128);
alter table agent_security_events add column if not exists reason text;
alter table agent_security_events add column if not exists fingerprint varchar(255);
alter table agent_security_events add column if not exists remote_address varchar(255);
alter table agent_security_events add column if not exists metadata_json jsonb not null default '{}'::jsonb;
alter table agent_security_events add column if not exists occurred_at timestamptz;
alter table agent_security_events add column if not exists created_at timestamptz not null default now();
create index if not exists idx_agent_security_events_agent_created on agent_security_events(agent_id, created_at desc);
create index if not exists idx_agent_security_events_claimed_created on agent_security_events(claimed_agent_id, created_at desc);
create index if not exists idx_agent_security_events_gateway_created on agent_security_events(gateway_node_id, created_at desc);

create table if not exists agent_security_enforcement_policies (
  policy_id varchar(128) primary key,
  agent_id varchar(128) not null,
  enabled boolean not null default true,
  duplicate_runtime_mode varchar(64) not null default 'WARN',
  require_credential_rotation boolean not null default false,
  notify_email boolean not null default false,
  notify_slack boolean not null default false,
  notify_siem boolean not null default false,
  email_recipients_json jsonb not null default '[]'::jsonb,
  slack_channels_json jsonb not null default '[]'::jsonb,
  siem_topics_json jsonb not null default '[]'::jsonb,
  metadata_json jsonb not null default '{}'::jsonb,
  updated_by varchar(128),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(agent_id)
);
alter table agent_security_enforcement_policies add column if not exists policy_id varchar(128);
alter table agent_security_enforcement_policies add column if not exists agent_id varchar(128);
alter table agent_security_enforcement_policies add column if not exists enabled boolean not null default true;
alter table agent_security_enforcement_policies add column if not exists duplicate_runtime_mode varchar(64) not null default 'WARN';
alter table agent_security_enforcement_policies add column if not exists require_credential_rotation boolean not null default false;
alter table agent_security_enforcement_policies add column if not exists notify_email boolean not null default false;
alter table agent_security_enforcement_policies add column if not exists notify_slack boolean not null default false;
alter table agent_security_enforcement_policies add column if not exists notify_siem boolean not null default false;
alter table agent_security_enforcement_policies add column if not exists email_recipients_json jsonb not null default '[]'::jsonb;
alter table agent_security_enforcement_policies add column if not exists slack_channels_json jsonb not null default '[]'::jsonb;
alter table agent_security_enforcement_policies add column if not exists siem_topics_json jsonb not null default '[]'::jsonb;
alter table agent_security_enforcement_policies add column if not exists metadata_json jsonb not null default '{}'::jsonb;
alter table agent_security_enforcement_policies add column if not exists updated_by varchar(128);
alter table agent_security_enforcement_policies add column if not exists created_at timestamptz not null default now();
alter table agent_security_enforcement_policies add column if not exists updated_at timestamptz not null default now();
create unique index if not exists ux_agent_security_enforcement_policies_agent on agent_security_enforcement_policies(agent_id);
create index if not exists idx_agent_security_enforcement_policies_enabled on agent_security_enforcement_policies(enabled, updated_at desc);
