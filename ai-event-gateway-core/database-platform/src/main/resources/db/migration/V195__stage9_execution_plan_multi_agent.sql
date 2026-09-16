-- Stage 9 — Execution Plan / Multi-Agent runtime convergence.
-- Adds provider-neutral Plan Step execution context, immutable convergence evidence and
-- deterministic ALL_REQUIRED / QUORUM / BEST_EFFORT fan-in.
-- Remote WRITE remains forbidden.


alter table plan_execution_steps
  add column if not exists runtime_claimed_by varchar(160),
  add column if not exists runtime_claim_until timestamptz;
create index if not exists idx_stage9_plan_dispatch_ready on plan_execution_steps(tenant_id,state,runtime_claim_until,updated_at) where state='DISPATCH_READY';

alter table plan_execution_runs
  add column if not exists completion_policy varchar(24) not null default 'ALL_REQUIRED',
  add column if not exists quorum_required int,
  add column if not exists convergence_outcome varchar(32),
  add column if not exists convergence_reason varchar(160);

alter table plan_execution_runs drop constraint if exists stage9_plan_completion_policy_check;
alter table plan_execution_runs add constraint stage9_plan_completion_policy_check
  check (completion_policy in ('ALL_REQUIRED','QUORUM','BEST_EFFORT'));
alter table plan_execution_runs drop constraint if exists stage9_plan_quorum_check;
alter table plan_execution_runs add constraint stage9_plan_quorum_check check (quorum_required is null or quorum_required >= 1);
alter table plan_execution_runs drop constraint if exists stage9_plan_convergence_outcome_check;
alter table plan_execution_runs add constraint stage9_plan_convergence_outcome_check
  check (convergence_outcome is null or convergence_outcome in ('SUCCEEDED','FAILED','PARTIAL','PROVISIONAL','EVIDENCE_CONFLICT','CANCELLED','TIMED_OUT'));

create table if not exists plan_execution_convergence_decisions (
  tenant_id varchar(64) not null,
  convergence_id varchar(180) not null,
  run_id varchar(160) not null,
  completion_policy varchar(24) not null,
  quorum_required int,
  required_step_count int not null,
  successful_required_count int not null,
  failed_required_count int not null,
  successful_step_count int not null,
  non_success_step_count int not null,
  artifact_count int not null,
  conflict_count int not null,
  outcome varchar(32) not null,
  reason_codes_json jsonb not null default '[]'::jsonb,
  evidence_json jsonb not null default '{}'::jsonb,
  decided_at timestamptz not null default now(),
  primary key (tenant_id,convergence_id),
  unique (tenant_id,run_id),
  constraint fk_stage9_convergence_run foreign key(tenant_id,run_id) references plan_execution_runs(tenant_id,run_id) on delete cascade,
  constraint stage9_convergence_policy_check check (completion_policy in ('ALL_REQUIRED','QUORUM','BEST_EFFORT')),
  constraint stage9_convergence_outcome_check check (outcome in ('SUCCEEDED','FAILED','PARTIAL','PROVISIONAL','EVIDENCE_CONFLICT','CANCELLED','TIMED_OUT')),
  constraint stage9_convergence_reasons_array check (jsonb_typeof(reason_codes_json)='array'),
  constraint stage9_convergence_evidence_object check (jsonb_typeof(evidence_json)='object')
);
alter table plan_execution_convergence_decisions enable row level security;
drop policy if exists tenant_isolation on plan_execution_convergence_decisions;
create policy tenant_isolation on plan_execution_convergence_decisions
  using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id());

create or replace function prevent_stage9_convergence_mutation() returns trigger language plpgsql as $$ begin raise exception 'PLAN_EXECUTION_CONVERGENCE_DECISION_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_stage9_convergence_immutable on plan_execution_convergence_decisions;
create trigger trg_stage9_convergence_immutable before update or delete on plan_execution_convergence_decisions for each row execute function prevent_stage9_convergence_mutation();

alter table capability_runtime_authorization_envelopes
  alter column delegation_id drop not null,
  add column if not exists execution_context_type varchar(24) not null default 'DELEGATION',
  add column if not exists plan_run_id varchar(160),
  add column if not exists plan_step_id varchar(160),
  add column if not exists plan_attempt_id varchar(160);
alter table capability_runtime_authorization_envelopes drop constraint if exists capability_runtime_authorization_context_check;
alter table capability_runtime_authorization_envelopes add constraint capability_runtime_authorization_context_check check (
  (execution_context_type='DELEGATION' and delegation_id is not null and plan_run_id is null and plan_step_id is null and plan_attempt_id is null)
  or
  (execution_context_type='PLAN_STEP' and delegation_id is null and plan_run_id is not null and plan_step_id is not null and plan_attempt_id is not null)
);
create unique index if not exists uq_stage9_plan_attempt_auth_envelope on capability_runtime_authorization_envelopes(tenant_id,plan_attempt_id) where execution_context_type='PLAN_STEP';

alter table mcp_read_executions
  alter column delegation_id drop not null,
  add column if not exists execution_context_type varchar(24) not null default 'DELEGATION',
  add column if not exists plan_run_id varchar(160),
  add column if not exists plan_step_id varchar(160),
  add column if not exists plan_attempt_id varchar(160);
alter table mcp_read_executions drop constraint if exists stage9_mcp_execution_context_check;
alter table mcp_read_executions add constraint stage9_mcp_execution_context_check check (
  (execution_context_type='DELEGATION' and delegation_id is not null and plan_attempt_id is null)
  or
  (execution_context_type='PLAN_STEP' and delegation_id is null and plan_run_id is not null and plan_step_id is not null and plan_attempt_id is not null)
);
create unique index if not exists uq_stage9_mcp_plan_attempt on mcp_read_executions(tenant_id,plan_attempt_id) where execution_context_type='PLAN_STEP';

alter table a2a_remote_read_executions
  alter column delegation_id drop not null,
  add column if not exists execution_context_type varchar(24) not null default 'DELEGATION',
  add column if not exists plan_run_id varchar(160),
  add column if not exists plan_step_id varchar(160),
  add column if not exists plan_attempt_id varchar(160);
alter table a2a_remote_read_executions drop constraint if exists stage9_a2a_execution_context_check;
alter table a2a_remote_read_executions add constraint stage9_a2a_execution_context_check check (
  (execution_context_type='DELEGATION' and delegation_id is not null and plan_attempt_id is null)
  or
  (execution_context_type='PLAN_STEP' and delegation_id is null and plan_run_id is not null and plan_step_id is not null and plan_attempt_id is not null)
);
create unique index if not exists uq_stage9_a2a_plan_attempt on a2a_remote_read_executions(tenant_id,plan_attempt_id) where execution_context_type='PLAN_STEP';

alter table a2a_remote_tracking_leases alter column delegation_id drop not null;

insert into schema_contract_authority(contract_id,contract_family,authority_note,created_at,schema_version)
values('stage9-execution-plan-multi-agent-v1','STAGE9_EXECUTION_PLAN_MULTI_AGENT','PLAN_DESCRIBES_CAPABILITY_DEPENDENCIES_ONLY; EVERY_STEP_REQUIRES_SERVER_SIDE_WHO_CAN_WHO_MAY_WHO_SHOULD_HOW; MANAGED_AGENT_MCP_AND_REMOTE_A2A_SHARE_CANONICAL_CHILD_TASK_EXECUTION_ASSIGNMENT_AND_AUTHORIZATION_ENVELOPE; FAN_IN_IS_DETERMINISTIC; REMOTE_WRITE_REMAINS_FORBIDDEN',now(),'V195')
on conflict(contract_id) do nothing;
