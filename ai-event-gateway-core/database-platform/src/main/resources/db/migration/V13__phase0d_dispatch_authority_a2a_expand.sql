-- Phase 0D expand: canonical Dispatch authority and directional A2A governance.
-- Existing migrations are immutable. This migration only expands and deterministically backfills.

alter table dispatch_flows add column if not exists candidate_source_mode varchar(32) not null default 'AGENT_POOL_ONLY';
alter table dispatch_flows add column if not exists capability_policy_mode varchar(32) not null default 'METADATA_ONLY';
alter table dispatch_policies add column if not exists candidate_source_mode varchar(32) not null default 'AGENT_POOL_ONLY';
alter table dispatch_policies add column if not exists capability_policy_mode varchar(32) not null default 'METADATA_ONLY';

update dispatch_flows set candidate_source_mode='AGENT_POOL_ONLY' where candidate_source_mode is null or candidate_source_mode='';
update dispatch_flows set capability_policy_mode='METADATA_ONLY' where capability_policy_mode is null or capability_policy_mode='';
update dispatch_policies set candidate_source_mode='AGENT_POOL_ONLY' where candidate_source_mode is null or candidate_source_mode='';
update dispatch_policies set capability_policy_mode='METADATA_ONLY' where capability_policy_mode is null or capability_policy_mode='';

create table if not exists pool_capability_policies (
  tenant_id varchar(64) not null,
  policy_id varchar(128) not null,
  pool_id varchar(128) not null,
  policy_code varchar(128) not null,
  capability_codes_json jsonb not null default '[]'::jsonb,
  match_mode varchar(32) not null default 'ALL',
  enforcement_mode varchar(32) not null default 'ADVISORY',
  enabled boolean not null default true,
  effective_at timestamptz,
  expires_at timestamptz,
  version bigint not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, policy_id),
  unique (tenant_id, pool_id, policy_code)
);

alter table tasks add column if not exists requesting_agent_id varchar(128);
alter table tasks add column if not exists a2a_policy_id varchar(128);
alter table tasks add column if not exists hop_count int not null default 0;
alter table tasks add column if not exists result_aggregation_policy varchar(32) not null default 'MANUAL_REVIEW';
alter table tasks add column if not exists child_cancellation_policy varchar(32) not null default 'MANUAL_DECISION';
alter table tasks add column if not exists child_failure_policy varchar(32) not null default 'WAIT_HUMAN';

with recursive task_depth as (
  select tenant_id, task_id, parent_task_id, 0 as depth
  from tasks where parent_task_id is null
  union all
  select child.tenant_id, child.task_id, child.parent_task_id, parent.depth + 1
  from tasks child join task_depth parent
    on parent.tenant_id=child.tenant_id and parent.task_id=child.parent_task_id
  where parent.depth < 100
)
update tasks t set hop_count=d.depth
from task_depth d where d.tenant_id=t.tenant_id and d.task_id=t.task_id;

create table if not exists a2a_policies (
  tenant_id varchar(64) not null,
  policy_id varchar(128) not null,
  policy_code varchar(128) not null,
  policy_name varchar(255) not null,
  source_domain_id varchar(128) not null,
  source_department_id varchar(128),
  source_group_id varchar(128),
  source_agent_pool_id varchar(128),
  source_agent_id varchar(128),
  target_domain_id varchar(128) not null,
  target_department_id varchar(128),
  target_group_id varchar(128),
  target_agent_pool_id varchar(128) not null,
  allowed_task_types_json jsonb not null default '[]'::jsonb,
  allowed_service_codes_json jsonb not null default '[]'::jsonb,
  allowed_capability_codes_json jsonb not null default '[]'::jsonb,
  max_sensitivity_level varchar(32) not null default 'INTERNAL',
  approval_mode varchar(32) not null default 'NONE',
  max_hop_count int not null default 3,
  rate_limit_per_minute int not null default 60,
  timeout_seconds int not null default 300,
  issue_projection_policy varchar(64) not null default 'NONE',
  result_aggregation_policy varchar(32) not null default 'MANUAL_REVIEW',
  cancellation_policy varchar(32) not null default 'MANUAL_DECISION',
  failure_propagation_policy varchar(32) not null default 'WAIT_HUMAN',
  allow_return_to_existing_domain boolean not null default false,
  enabled boolean not null default true,
  effective_at timestamptz,
  expires_at timestamptz,
  version bigint not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, policy_id),
  unique (tenant_id, policy_code)
);

create table if not exists a2a_requests (
  tenant_id varchar(64) not null,
  a2a_request_id varchar(128) not null,
  root_task_id varchar(128) not null,
  source_task_id varchar(128) not null,
  requesting_task_id varchar(128) not null,
  requesting_agent_id varchar(128),
  requester_type varchar(32) not null,
  source_department_id varchar(128),
  source_group_id varchar(128),
  source_domain_id varchar(128) not null,
  target_department_id varchar(128),
  target_group_id varchar(128),
  target_domain_id varchar(128) not null,
  requested_task_type varchar(128) not null,
  requested_service_code varchar(128),
  requested_capability_codes_json jsonb not null default '[]'::jsonb,
  target_agent_pool_id varchar(128) not null,
  policy_id varchar(128) not null,
  approval_status varchar(32) not null default 'NOT_REQUIRED',
  approval_count int not null default 0,
  required_approval_count int not null default 0,
  approval_actor_ids_json jsonb not null default '[]'::jsonb,
  request_status varchar(32) not null default 'REQUESTED',
  reason text,
  input_payload_ref varchar(255),
  sensitivity_level varchar(32) not null default 'INTERNAL',
  hop_count int not null,
  idempotency_key varchar(255) not null,
  correlation_id varchar(128) not null,
  child_task_id varchar(128),
  requested_by_type varchar(32) not null,
  requested_by_id varchar(128) not null,
  rejection_reason_code varchar(128),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  expires_at timestamptz,
  version bigint not null default 1,
  primary key (tenant_id, a2a_request_id),
  unique (tenant_id, idempotency_key)
);

create table if not exists a2a_results (
  tenant_id varchar(64) not null,
  a2a_result_id varchar(128) not null,
  a2a_request_id varchar(128) not null,
  root_task_id varchar(128) not null,
  parent_task_id varchar(128) not null,
  child_task_id varchar(128) not null,
  result_status varchar(32) not null,
  result_summary text,
  result_payload_ref varchar(255),
  evidence_refs_json jsonb not null default '[]'::jsonb,
  completed_by_agent_id varchar(128),
  completed_at timestamptz not null,
  idempotency_key varchar(255) not null,
  created_at timestamptz not null default now(),
  version bigint not null default 1,
  primary key (tenant_id, a2a_result_id),
  unique (tenant_id, idempotency_key),
  unique (tenant_id, a2a_request_id)
);

create table if not exists a2a_state_history (
  tenant_id varchar(64) not null,
  history_id varchar(128) not null,
  a2a_request_id varchar(128) not null,
  from_status varchar(32),
  to_status varchar(32) not null,
  reason_code varchar(128) not null,
  reason text,
  actor_type varchar(32) not null,
  actor_id varchar(128) not null,
  correlation_id varchar(128),
  transition_at timestamptz not null default now(),
  request_version bigint not null,
  idempotency_key varchar(255),
  primary key (tenant_id, history_id)
);
create unique index if not exists uq_a2a_state_history_idempotency
  on a2a_state_history(tenant_id,idempotency_key) where idempotency_key is not null;

create table if not exists a2a_idempotency_records (
  tenant_id varchar(64) not null,
  idempotency_key varchar(255) not null,
  operation_type varchar(64) not null,
  request_hash varchar(128) not null,
  resource_type varchar(64),
  resource_id varchar(128),
  result_status varchar(32) not null default 'IN_PROGRESS',
  created_at timestamptz not null default now(),
  completed_at timestamptz,
  expires_at timestamptz,
  primary key (tenant_id, idempotency_key, operation_type)
);

create table if not exists a2a_rate_limit_windows (
  tenant_id varchar(64) not null,
  policy_id varchar(128) not null,
  window_started_at timestamptz not null,
  request_count int not null default 0,
  updated_at timestamptz not null default now(),
  primary key (tenant_id, policy_id, window_started_at)
);

create index if not exists idx_a2a_policies_direction on a2a_policies(tenant_id,source_domain_id,target_domain_id,enabled);
create index if not exists idx_a2a_requests_source on a2a_requests(tenant_id,source_task_id,created_at desc);
create index if not exists idx_a2a_requests_root on a2a_requests(tenant_id,root_task_id,created_at asc);
create index if not exists idx_a2a_requests_status on a2a_requests(tenant_id,request_status,updated_at);
create index if not exists idx_a2a_results_parent on a2a_results(tenant_id,parent_task_id,completed_at desc);
