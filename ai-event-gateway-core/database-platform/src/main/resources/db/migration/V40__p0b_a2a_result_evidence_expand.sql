-- P0B: evidence-bearing canonical A2A Result acceptance.
-- Raw dispatch/fencing secrets MUST NOT be copied into A2A tables.

alter table a2a_results add column if not exists assignment_id varchar(128);
alter table a2a_results add column if not exists execution_attempt_id varchar(128);
alter table a2a_results add column if not exists attempt_no int;
alter table a2a_results add column if not exists dispatch_request_id varchar(128);
alter table a2a_results add column if not exists dispatch_token_hash varchar(96);
alter table a2a_results add column if not exists fencing_token_hash varchar(96);
alter table a2a_results add column if not exists agent_session_id varchar(128);
alter table a2a_results add column if not exists callback_inbox_id varchar(128);
alter table a2a_results add column if not exists payload_hash varchar(96);
alter table a2a_results add column if not exists acceptance_attempt_id varchar(128);
alter table a2a_results add column if not exists received_at timestamptz;
alter table a2a_results add column if not exists accepted_at timestamptz;

create table if not exists a2a_result_attempts (
  tenant_id varchar(64) not null,
  attempt_id varchar(128) not null,
  a2a_request_id varchar(128) not null,
  child_task_id varchar(128) not null,
  a2a_result_id varchar(128),
  assignment_id varchar(128),
  execution_attempt_id varchar(128),
  attempt_no int,
  dispatch_request_id varchar(128),
  callback_inbox_id varchar(128),
  agent_id varchar(128),
  agent_session_id varchar(128),
  result_status varchar(32),
  decision varchar(32) not null,
  reason_code varchar(128) not null,
  reason text,
  payload_hash varchar(96),
  idempotency_key varchar(255) not null,
  correlation_id varchar(128),
  occurred_at timestamptz,
  received_at timestamptz not null default now(),
  primary key (tenant_id, attempt_id),
  unique (tenant_id, idempotency_key)
);

create table if not exists a2a_result_evidence (
  tenant_id varchar(64) not null,
  evidence_id varchar(128) not null,
  attempt_id varchar(128) not null,
  a2a_request_id varchar(128) not null,
  evidence_type varchar(64) not null,
  evidence_reference varchar(255),
  evidence_hash varchar(96),
  verification_decision varchar(32) not null,
  reason_code varchar(128),
  verified_at timestamptz not null default now(),
  primary key (tenant_id, evidence_id)
);

create table if not exists a2a_result_quarantine (
  tenant_id varchar(64) not null,
  quarantine_id varchar(128) not null,
  attempt_id varchar(128) not null,
  a2a_request_id varchar(128) not null,
  child_task_id varchar(128) not null,
  classification varchar(32) not null,
  reason_code varchar(128) not null,
  reason text,
  payload_hash varchar(96),
  callback_inbox_id varchar(128),
  assignment_id varchar(128),
  execution_attempt_id varchar(128),
  status varchar(32) not null default 'OPEN',
  resolved_by varchar(128),
  resolution_reason text,
  quarantined_at timestamptz not null default now(),
  resolved_at timestamptz,
  primary key (tenant_id, quarantine_id),
  unique (tenant_id, attempt_id)
);

create table if not exists a2a_parent_aggregations (
  tenant_id varchar(64) not null,
  parent_task_id varchar(128) not null,
  aggregation_policy varchar(64) not null,
  aggregate_status varchar(32) not null,
  total_count int not null default 0,
  pending_count int not null default 0,
  succeeded_count int not null default 0,
  partial_count int not null default 0,
  failed_count int not null default 0,
  cancelled_count int not null default 0,
  summary text,
  computation_hash varchar(96) not null,
  last_result_id varchar(128),
  computed_at timestamptz not null default now(),
  version bigint not null default 1,
  primary key (tenant_id, parent_task_id)
);

create index if not exists idx_a2a_result_attempts_request on a2a_result_attempts(tenant_id,a2a_request_id,received_at desc);
create index if not exists idx_a2a_result_attempts_callback on a2a_result_attempts(tenant_id,callback_inbox_id) where callback_inbox_id is not null;
create index if not exists idx_a2a_result_evidence_attempt on a2a_result_evidence(tenant_id,attempt_id,verified_at);
create index if not exists idx_a2a_result_quarantine_open on a2a_result_quarantine(tenant_id,status,quarantined_at desc);
create index if not exists idx_a2a_results_execution on a2a_results(tenant_id,assignment_id,execution_attempt_id);
