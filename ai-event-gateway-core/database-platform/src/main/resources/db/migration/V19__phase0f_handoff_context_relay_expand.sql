-- Phase 0F: Handoff Context and Cross-Project Relay expand migration.
-- Handoff snapshots are the only supported cross-department payload delivered to Agents.

create table if not exists handoff_context_policies (
  tenant_id varchar(64) not null,
  policy_id varchar(128) not null,
  policy_name varchar(255) not null,
  policy_type varchar(48) not null default 'SUMMARY_ONLY',
  context_requirement varchar(48) not null default 'OPTIONAL',
  default_field_decision varchar(32) not null default 'OMIT',
  attachment_policy varchar(48) not null default 'ATTACHMENT_METADATA_ONLY',
  approval_mode varchar(32) not null default 'NONE',
  allowed_field_paths_json jsonb not null default '[]'::jsonb,
  allowed_comment_types_json jsonb not null default '[]'::jsonb,
  masking_rules_json jsonb not null default '{}'::jsonb,
  result_sharing_policy varchar(48) not null default 'SUMMARY_ONLY',
  enabled boolean not null default true,
  version bigint not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, policy_id)
);


alter table a2a_policies add column if not exists handoff_context_policy_id varchar(128) not null default 'DEFAULT_HANDOFF';
alter table a2a_policies add column if not exists handoff_context_requirement varchar(48) not null default 'OPTIONAL';

create table if not exists handoff_context_snapshots (
  tenant_id varchar(64) not null,
  snapshot_id varchar(128) not null,
  root_task_id varchar(128) not null,
  source_task_id varchar(128) not null,
  target_task_id varchar(128) not null,
  source_issue_link_id varchar(128),
  target_issue_link_id varchar(128),
  context_policy_id varchar(128) not null,
  snapshot_version integer not null,
  summary text,
  structured_context_json jsonb not null default '{}'::jsonb,
  allowed_comment_refs_json jsonb not null default '[]'::jsonb,
  attachment_metadata_json jsonb not null default '[]'::jsonb,
  redacted_field_paths_json jsonb not null default '[]'::jsonb,
  omitted_content_reasons_json jsonb not null default '[]'::jsonb,
  sensitivity_level varchar(32) not null default 'INTERNAL',
  content_hash varchar(128) not null,
  source_observed_at timestamptz,
  created_at timestamptz not null default now(),
  created_by_type varchar(32) not null,
  created_by_id varchar(128) not null,
  expires_at timestamptz,
  status varchar(32) not null default 'DRAFT',
  approved_by varchar(128),
  approved_at timestamptz,
  supersedes_snapshot_id varchar(128),
  correlation_id varchar(128),
  primary key (tenant_id, snapshot_id),
  unique (tenant_id, source_task_id, target_task_id, context_policy_id, snapshot_version),
  unique (tenant_id, source_task_id, target_task_id, context_policy_id, content_hash)
);

create table if not exists handoff_context_field_decisions (
  tenant_id varchar(64) not null,
  snapshot_id varchar(128) not null,
  field_path varchar(512) not null,
  classification varchar(64),
  sensitivity_level varchar(32) not null default 'INTERNAL',
  share_decision varchar(32) not null,
  masking_method varchar(64),
  source_reference varchar(512),
  projected_value_json jsonb,
  original_value_hash varchar(128),
  primary key (tenant_id, snapshot_id, field_path)
);

create table if not exists handoff_context_approvals (
  tenant_id varchar(64) not null,
  approval_id varchar(128) not null,
  snapshot_id varchar(128) not null,
  decision varchar(32) not null,
  actor_type varchar(32) not null,
  actor_id varchar(128) not null,
  reason text,
  idempotency_key varchar(128) not null,
  decided_at timestamptz not null default now(),
  correlation_id varchar(128),
  primary key (tenant_id, approval_id),
  unique (tenant_id, snapshot_id, idempotency_key)
);

create table if not exists result_context_snapshots (
  tenant_id varchar(64) not null,
  result_snapshot_id varchar(128) not null,
  root_task_id varchar(128) not null,
  source_task_id varchar(128) not null,
  target_task_id varchar(128) not null,
  policy_id varchar(128) not null,
  snapshot_version integer not null,
  result_summary text,
  shared_evidence_refs_json jsonb not null default '[]'::jsonb,
  masked_output_json jsonb not null default '{}'::jsonb,
  omitted_output_reasons_json jsonb not null default '[]'::jsonb,
  result_content_hash varchar(128) not null,
  created_by_agent_id varchar(128),
  created_at timestamptz not null default now(),
  status varchar(32) not null default 'CREATED',
  correlation_id varchar(128),
  primary key (tenant_id, result_snapshot_id),
  unique (tenant_id, source_task_id, target_task_id, policy_id, snapshot_version),
  unique (tenant_id, target_task_id, result_content_hash)
);

create table if not exists cross_project_issue_relays (
  tenant_id varchar(64) not null,
  relay_id varchar(128) not null,
  a2a_request_id varchar(128),
  root_task_id varchar(128) not null,
  source_task_id varchar(128) not null,
  target_task_id varchar(128) not null,
  source_mapping_id varchar(128) not null,
  target_mapping_id varchar(128) not null,
  source_snapshot_id varchar(128),
  result_snapshot_id varchar(128),
  source_issue_link_id varchar(128),
  target_issue_link_id varchar(128),
  strategy varchar(48) not null default 'DUAL_BACKLINK_COMMENT',
  relay_state varchar(48) not null default 'PENDING_SOURCE_CONTEXT',
  native_relation_supported boolean,
  source_backlink_status varchar(32) not null default 'NOT_REQUIRED',
  target_backlink_status varchar(32) not null default 'NOT_REQUIRED',
  retry_count integer not null default 0,
  next_retry_at timestamptz,
  last_error_code varchar(128),
  last_error_message text,
  idempotency_key varchar(128) not null,
  correlation_id varchar(128) not null,
  version bigint not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, relay_id),
  unique (tenant_id, idempotency_key)
);

create table if not exists issue_relay_attempts (
  tenant_id varchar(64) not null,
  attempt_id varchar(128) not null,
  relay_id varchar(128) not null,
  operation_code varchar(64) not null,
  operation_side varchar(16) not null,
  provider_type varchar(32),
  project_mapping_id varchar(128),
  principal_id varchar(128),
  attempt_no integer not null,
  status varchar(32) not null,
  provider_status integer,
  external_issue_id varchar(255),
  external_issue_url text,
  response_summary text,
  error_code varchar(128),
  retryable boolean not null default false,
  started_at timestamptz not null default now(),
  completed_at timestamptz,
  correlation_id varchar(128),
  primary key (tenant_id, attempt_id),
  unique (tenant_id, relay_id, operation_code, operation_side, attempt_no)
);

create table if not exists agent_context_access_events (
  tenant_id varchar(64) not null,
  access_event_id varchar(128) not null,
  task_id varchar(128) not null,
  assignment_id varchar(128),
  dispatch_request_id varchar(128),
  agent_id varchar(128) not null,
  agent_session_id varchar(128),
  snapshot_id varchar(128),
  snapshot_version integer not null default 0,
  access_decision varchar(32) not null,
  reason_code varchar(128) not null,
  dispatch_token_hash varchar(128),
  client_address varchar(128),
  correlation_id varchar(128),
  accessed_at timestamptz not null default now(),
  primary key (tenant_id, access_event_id)
);

create index if not exists idx_handoff_snapshot_task on handoff_context_snapshots(tenant_id,target_task_id,snapshot_version desc);
create index if not exists idx_handoff_snapshot_status on handoff_context_snapshots(tenant_id,status,created_at desc);
create index if not exists idx_result_snapshot_task on result_context_snapshots(tenant_id,target_task_id,snapshot_version desc);
create index if not exists idx_issue_relay_state on cross_project_issue_relays(tenant_id,relay_state,next_retry_at);
create index if not exists idx_issue_relay_task on cross_project_issue_relays(tenant_id,root_task_id,created_at desc);
create index if not exists idx_agent_context_access_task on agent_context_access_events(tenant_id,task_id,accessed_at desc);
-- Conservative tenant defaults: summary and attachment metadata only.
insert into handoff_context_policies(tenant_id,policy_id,policy_name,policy_type,context_requirement,default_field_decision,attachment_policy,approval_mode,result_sharing_policy,enabled)
select tenant_id,'DEFAULT_HANDOFF','Default Handoff Context','SUMMARY_ONLY','OPTIONAL','OMIT','ATTACHMENT_METADATA_ONLY','NONE','SUMMARY_ONLY',true from tenants
on conflict (tenant_id,policy_id) do nothing;

