-- A0-R2 — Task State / Terminalization / Finalization Authority
-- Contract: MRS v5.2.1 A0-C frozen contract §§11, 11A, 12, 12A.
--
-- A0-R2 flips the authority direction established as a projection in V188:
--   * tasks.status remains a compatibility/runtime signal for existing workers.
--   * task_lifecycle / task_phase / task_outcome become the canonical Task state authority.
--   * a legacy terminal status requests terminalization; it MUST NOT directly close a Task.
--   * only the A0-R2 FinalizationCoordinator may transition FINALIZING -> CLOSED.
--
-- Routing, candidate selection, assignment selection and provider execution authority are NOT changed here.

-- -----------------------------------------------------------------------------
-- 1. Canonical task authority columns.
-- -----------------------------------------------------------------------------
alter table tasks add column if not exists task_state_authority_version varchar(64);
alter table tasks add column if not exists terminalization_reason varchar(48);
alter table tasks add column if not exists terminalization_requested_at timestamptz;
alter table tasks add column if not exists terminalization_actor_ref varchar(255);
alter table tasks add column if not exists outcome_resolver_version varchar(80);
alter table tasks add column if not exists cancellation_state varchar(24);
alter table tasks add column if not exists finalization_state varchar(40);
alter table tasks add column if not exists finalization_checkpoint varchar(64);
alter table tasks add column if not exists finalization_attempt_count int;
alter table tasks add column if not exists finalization_next_attempt_at timestamptz;
alter table tasks add column if not exists finalization_claimed_by varchar(160);
alter table tasks add column if not exists finalization_claim_until timestamptz;
alter table tasks add column if not exists finalization_last_error_code varchar(160);
alter table tasks add column if not exists finalization_last_error_message text;
alter table tasks add column if not exists finalization_completed_at timestamptz;
alter table tasks add column if not exists evidence_availability_requirement varchar(48);

alter table tasks drop constraint if exists chk_a0r2_terminalization_reason;
alter table tasks add constraint chk_a0r2_terminalization_reason check (
  terminalization_reason is null or terminalization_reason in (
    'PLAN_COMPLETED','PLAN_FAILED','USER_CANCELLED','KILL_SWITCHED','POLICY_REVOKED',
    'BUDGET_EXHAUSTED','CAPACITY_TIMEOUT','APPROVAL_EXPIRED','RESIDENCY_DENIED',
    'FLOW_CONFIGURATION_ERROR','REMOTE_UNRESOLVED','LOCAL_UNRESOLVED','RUNTIME_FATAL','MANUAL_ABORT'
  )
);
alter table tasks drop constraint if exists chk_a0r2_cancellation_state;
alter table tasks add constraint chk_a0r2_cancellation_state check (
  cancellation_state is null or cancellation_state in ('NONE','REQUESTED','CONFIRMED','REJECTED','UNCONFIRMED')
);
alter table tasks drop constraint if exists chk_a0r2_finalization_state;
alter table tasks add constraint chk_a0r2_finalization_state check (
  finalization_state is null or finalization_state in ('NONE','PENDING','RUNNING','RETRY_PENDING','MANUAL_RECOVERY_REQUIRED','COMPLETED')
);
alter table tasks drop constraint if exists chk_a0r2_finalization_attempt_count;
alter table tasks add constraint chk_a0r2_finalization_attempt_count check (finalization_attempt_count is null or finalization_attempt_count >= 0);
alter table tasks drop constraint if exists chk_a0r2_evidence_requirement;
alter table tasks add constraint chk_a0r2_evidence_requirement check (
  evidence_availability_requirement is null or evidence_availability_requirement in (
    'EVIDENCE_REQUIRED_BEFORE_EXECUTION','EVIDENCE_REQUIRED_BEFORE_COMMIT','BUFFER_ALLOWED','BEST_EFFORT'
  )
);

-- Existing rows that were already terminal before A0-R2 are historical CLOSED rows; do not
-- retroactively reopen millions of historical tasks for a finalization pipeline that did not exist.
update tasks set
  task_state_authority_version = 'A0R2_CANONICAL_V1',
  cancellation_state = case when upper(coalesce(status,'')) in ('CANCELLED','SUPPRESSED') then 'CONFIRMED' else 'NONE' end,
  finalization_state = case when task_lifecycle='CLOSED' then 'COMPLETED' else 'NONE' end,
  finalization_checkpoint = case when task_lifecycle='CLOSED' then 'COMPLETE' else 'NOT_STARTED' end,
  finalization_attempt_count = coalesce(finalization_attempt_count,0),
  finalization_completed_at = case when task_lifecycle='CLOSED' then coalesce(finalization_completed_at,terminal_at,updated_at,created_at) else finalization_completed_at end,
  terminalization_requested_at = case when task_lifecycle='CLOSED' then coalesce(terminalization_requested_at,terminal_at,updated_at,created_at) else terminalization_requested_at end,
  terminalization_reason = case
    when task_lifecycle<>'CLOSED' then terminalization_reason
    when terminalization_reason is not null then terminalization_reason
    when upper(coalesce(status,'')) in ('SUCCEEDED','COMPLETED') then 'PLAN_COMPLETED'
    when upper(coalesce(status,'')) in ('CANCELLED','SUPPRESSED') then 'USER_CANCELLED'
    when upper(coalesce(status,''))='EXPIRED' then 'APPROVAL_EXPIRED'
    else 'RUNTIME_FATAL' end,
  outcome_resolver_version = case when task_lifecycle='CLOSED' then coalesce(outcome_resolver_version,'A0R2_LEGACY_BACKFILL_V1') else outcome_resolver_version end,
  evidence_availability_requirement = coalesce(evidence_availability_requirement,'EVIDENCE_REQUIRED_BEFORE_COMMIT')
where task_state_authority_version is distinct from 'A0R2_CANONICAL_V1'
   or cancellation_state is null or finalization_state is null or finalization_checkpoint is null
   or finalization_attempt_count is null or evidence_availability_requirement is null;

alter table tasks alter column task_lifecycle set not null;
alter table tasks alter column task_phase set not null;
alter table tasks alter column task_outcome set not null;
alter table tasks alter column task_state_authority_version set default 'A0R2_CANONICAL_V1';
alter table tasks alter column task_state_authority_version set not null;
alter table tasks alter column cancellation_state set default 'NONE';
alter table tasks alter column cancellation_state set not null;
alter table tasks alter column finalization_state set default 'NONE';
alter table tasks alter column finalization_state set not null;
alter table tasks alter column finalization_checkpoint set default 'NOT_STARTED';
alter table tasks alter column finalization_checkpoint set not null;
alter table tasks alter column finalization_attempt_count set default 0;
alter table tasks alter column finalization_attempt_count set not null;
alter table tasks alter column evidence_availability_requirement set default 'EVIDENCE_REQUIRED_BEFORE_COMMIT';
alter table tasks alter column evidence_availability_requirement set not null;

alter table tasks drop constraint if exists chk_a0r2_closed_requires_finalization;
alter table tasks add constraint chk_a0r2_closed_requires_finalization check (
  task_lifecycle <> 'CLOSED' or (
    finalization_state='COMPLETED' and task_outcome<>'UNRESOLVED' and terminalization_reason is not null
    and finalization_completed_at is not null
  )
);
alter table tasks drop constraint if exists chk_a0r2_finalizing_requires_terminalization;
alter table tasks add constraint chk_a0r2_finalizing_requires_terminalization check (
  task_lifecycle <> 'FINALIZING' or (
    terminalization_reason is not null and terminalization_requested_at is not null
    and finalization_state in ('PENDING','RUNNING','RETRY_PENDING','MANUAL_RECOVERY_REQUIRED')
  )
);

create index if not exists idx_tasks_a0r2_finalization_due
  on tasks(tenant_id,finalization_state,finalization_next_attempt_at,updated_at,task_id)
  where task_lifecycle='FINALIZING' and finalization_state in ('PENDING','RETRY_PENDING','RUNNING');
create index if not exists idx_tasks_a0r2_condition_state
  on tasks(tenant_id,task_lifecycle,task_phase,updated_at desc,task_id);

-- Canonical open-task uniqueness. The existing status-based index is removed because a legacy
-- terminal status now means FINALIZING, not CLOSED.
drop index if exists uq_tasks_open_incident_type;
create unique index if not exists uq_tasks_open_incident_type
  on tasks(tenant_id,incident_id,task_type)
  where task_lifecycle <> 'CLOSED';

comment on column tasks.status is 'A0-R2 compatibility/runtime signal. It no longer determines whether the canonical Task is CLOSED.';
comment on column tasks.task_lifecycle is 'A0-R2 canonical Task lifecycle authority: CREATED/ACTIVE/WAITING/FINALIZING/CLOSED.';
comment on column tasks.task_phase is 'A0-R2 canonical Task execution phase authority.';
comment on column tasks.task_outcome is 'A0-R2 canonical business execution outcome. It is resolved deterministically during FINALIZING.';
comment on column tasks.terminalization_reason is 'A0-R2 reason that authorizes entry to FINALIZING; a condition does not directly decide TaskOutcome.';
comment on column tasks.finalization_state is 'A0-R2 durable finalization runtime state. Only COMPLETED permits canonical CLOSED.';

-- -----------------------------------------------------------------------------
-- 2. Legacy status becomes an input signal to canonical state, never closure authority.
-- -----------------------------------------------------------------------------
create or replace function a0_r2_terminal_reason_from_legacy_status(p_status varchar, p_reason text, p_failure_code varchar)
returns varchar language plpgsql immutable as $$
begin
  if upper(coalesce(p_failure_code,'')) like '%RESIDENCY%' then return 'RESIDENCY_DENIED'; end if;
  if upper(coalesce(p_failure_code,'')) like '%POLICY%REVOK%' then return 'POLICY_REVOKED'; end if;
  if upper(coalesce(p_failure_code,'')) like '%BUDGET%' then return 'BUDGET_EXHAUSTED'; end if;
  if upper(coalesce(p_reason,'')) like '%FLOW%CONFIG%' then return 'FLOW_CONFIGURATION_ERROR'; end if;
  if upper(coalesce(p_reason,'')) like '%REMOTE%UNKNOWN%' then return 'REMOTE_UNRESOLVED'; end if;
  if upper(coalesce(p_reason,'')) like '%LOCAL%UNKNOWN%' then return 'LOCAL_UNRESOLVED'; end if;
  case upper(coalesce(p_status,''))
    when 'SUCCEEDED' then return 'PLAN_COMPLETED';
    when 'COMPLETED' then return 'PLAN_COMPLETED';
    when 'CANCELLED' then return 'USER_CANCELLED';
    when 'SUPPRESSED' then return 'USER_CANCELLED';
    when 'EXPIRED' then return 'APPROVAL_EXPIRED';
    when 'TIMED_OUT' then return 'CAPACITY_TIMEOUT';
    when 'FAILED' then return 'RUNTIME_FATAL';
    when 'ESCALATED' then return 'RUNTIME_FATAL';
    when 'DEAD_LETTER' then return 'RUNTIME_FATAL';
    else return 'RUNTIME_FATAL';
  end case;
end $$;

create or replace function a0_r2_apply_legacy_task_status_signal()
returns trigger language plpgsql as $$
declare
  s varchar := upper(coalesce(new.status,''));
  is_terminal boolean := s in ('SUCCEEDED','COMPLETED','CANCELLED','SUPPRESSED','FAILED','ESCALATED','DEAD_LETTER','EXPIRED','TIMED_OUT');
begin
  new.task_state_authority_version := 'A0R2_CANONICAL_V1';
  new.evidence_availability_requirement := coalesce(new.evidence_availability_requirement,'EVIDENCE_REQUIRED_BEFORE_COMMIT');
  new.cancellation_state := coalesce(new.cancellation_state,'NONE');
  new.finalization_state := coalesce(new.finalization_state,'NONE');
  new.finalization_checkpoint := coalesce(new.finalization_checkpoint,'NOT_STARTED');
  new.finalization_attempt_count := coalesce(new.finalization_attempt_count,0);

  -- The canonical finalizer may synchronize the legacy compatibility status while
  -- atomically closing the canonical state. That status write must not be
  -- reinterpreted as a fresh terminalization request.
  if tg_op='UPDATE'
     and coalesce(current_setting('app.a0_r2_finalizer',true),'')='true'
     and new.task_lifecycle='CLOSED' then
    return new;
  end if;

  if tg_op='UPDATE' and old.task_lifecycle='CLOSED' then
    -- CLOSED is terminal. A stale legacy writer must never reopen canonical execution.
    if new.status is distinct from old.status and not is_terminal then
      raise exception 'TASK_CANONICAL_CLOSED_REGRESSION_DENIED taskId=% oldStatus=% newStatus=%', old.task_id, old.status, new.status;
    end if;
    new.task_lifecycle := old.task_lifecycle;
    new.task_phase := old.task_phase;
    new.task_outcome := old.task_outcome;
    new.terminalization_reason := old.terminalization_reason;
    new.terminalization_requested_at := old.terminalization_requested_at;
    new.outcome_resolver_version := old.outcome_resolver_version;
    new.finalization_state := old.finalization_state;
    new.finalization_checkpoint := old.finalization_checkpoint;
    new.finalization_completed_at := old.finalization_completed_at;
    return new;
  end if;

  if is_terminal then
    new.task_lifecycle := 'FINALIZING';
    new.task_phase := 'CLOSURE';
    new.task_outcome := 'UNRESOLVED';
    new.terminalization_reason := coalesce(new.terminalization_reason,a0_r2_terminal_reason_from_legacy_status(new.status,new.lifecycle_reason,new.failure_code));
    new.terminalization_requested_at := coalesce(new.terminalization_requested_at,new.terminal_at,new.updated_at,now());
    new.terminalization_actor_ref := coalesce(new.terminalization_actor_ref,nullif(new.actor_principal_id,''),nullif(new.created_by_id,''),'LEGACY_STATUS_SIGNAL');
    if new.finalization_state not in ('RUNNING','RETRY_PENDING','MANUAL_RECOVERY_REQUIRED') then new.finalization_state := 'PENDING'; end if;
    if new.finalization_checkpoint='COMPLETE' then new.finalization_checkpoint := 'NOT_STARTED'; end if;
    new.finalization_next_attempt_at := coalesce(new.finalization_next_attempt_at,now());
    new.finalization_completed_at := null;
    new.outcome_resolver_version := null;
    if s in ('CANCELLED','SUPPRESSED') then new.cancellation_state := 'CONFIRMED'; end if;
    return new;
  end if;

  -- Non-terminal compatibility signals map to canonical non-terminal lifecycle/phase only.
  new.task_outcome := 'UNRESOLVED';
  new.terminalization_reason := null;
  new.terminalization_requested_at := null;
  new.terminalization_actor_ref := null;
  new.outcome_resolver_version := null;
  new.finalization_state := 'NONE';
  new.finalization_checkpoint := 'NOT_STARTED';
  new.finalization_attempt_count := 0;
  new.finalization_next_attempt_at := null;
  new.finalization_claimed_by := null;
  new.finalization_claim_until := null;
  new.finalization_last_error_code := null;
  new.finalization_last_error_message := null;
  new.finalization_completed_at := null;

  case s
    when 'DRAFT' then new.task_lifecycle:='CREATED'; new.task_phase:='INTAKE';
    when 'WAITING_APPROVAL' then new.task_lifecycle:='WAITING'; new.task_phase:='ADMISSION';
    when 'WAITING_CONTEXT' then new.task_lifecycle:='WAITING'; new.task_phase:='ADMISSION';
    when 'WAITING_DEPENDENCY' then new.task_lifecycle:='WAITING'; new.task_phase:='EXECUTION';
    when 'WAITING_HUMAN' then new.task_lifecycle:='WAITING'; new.task_phase:='REMEDIATION';
    when 'BLOCKED' then new.task_lifecycle:='WAITING'; new.task_phase:='REMEDIATION';
    when 'CANCEL_REQUESTED' then new.task_lifecycle:='WAITING'; new.task_phase:='REMEDIATION'; new.cancellation_state:='REQUESTED';
    when 'PARTIALLY_COMPLETED' then new.task_lifecycle:='ACTIVE'; new.task_phase:='AGGREGATION';
    when 'ORPHANED' then new.task_lifecycle:='ACTIVE'; new.task_phase:='REMEDIATION';
    when 'RECONCILING' then new.task_lifecycle:='ACTIVE'; new.task_phase:='REMEDIATION';
    else new.task_lifecycle:='ACTIVE'; new.task_phase:='EXECUTION';
  end case;
  return new;
end $$;

drop trigger if exists trg_stage2_task_state_projection on tasks;
drop trigger if exists trg_a0_r2_legacy_status_signal on tasks;
create trigger trg_a0_r2_legacy_status_signal
before insert or update of status on tasks
for each row execute function a0_r2_apply_legacy_task_status_signal();

-- -----------------------------------------------------------------------------
-- 3. Task Condition authority and resolution policy.
-- -----------------------------------------------------------------------------
create table if not exists task_condition_resolution_policies (
  scope_type varchar(16) not null,
  scope_id varchar(128) not null,
  condition_type varchar(64) not null,
  blocking boolean not null,
  resolution varchar(24) not null,
  timeout_seconds bigint,
  timeout_action varchar(32) not null,
  timeout_terminalization_reason varchar(48),
  escalation_policy_ref varchar(255),
  status varchar(24) not null default 'ACTIVE',
  version int not null default 1,
  updated_at timestamptz not null default now(),
  primary key(scope_type,scope_id,condition_type),
  constraint chk_a0r2_condition_policy_scope check(scope_type in ('SYSTEM','TENANT')),
  constraint chk_a0r2_condition_policy_resolution check(resolution in ('WAIT','FAIL','REQUIRE_HUMAN')),
  constraint chk_a0r2_condition_policy_timeout_action check(timeout_action in ('NONE','FAIL','ESCALATE','MANUAL_RECONCILIATION')),
  constraint chk_a0r2_condition_policy_status check(status in ('ACTIVE','DISABLED','RETIRED')),
  constraint chk_a0r2_condition_policy_timeout check(timeout_seconds is null or timeout_seconds>=0),
  constraint chk_a0r2_condition_policy_terminal_reason check(timeout_terminalization_reason is null or timeout_terminalization_reason in (
    'PLAN_COMPLETED','PLAN_FAILED','USER_CANCELLED','KILL_SWITCHED','POLICY_REVOKED','BUDGET_EXHAUSTED','CAPACITY_TIMEOUT',
    'APPROVAL_EXPIRED','RESIDENCY_DENIED','FLOW_CONFIGURATION_ERROR','REMOTE_UNRESOLVED','LOCAL_UNRESOLVED','RUNTIME_FATAL','MANUAL_ABORT'))
);

insert into task_condition_resolution_policies(scope_type,scope_id,condition_type,blocking,resolution,timeout_seconds,timeout_action,timeout_terminalization_reason,status,version) values
('SYSTEM','*','CAPACITY_WAIT',true,'WAIT',1800,'FAIL','CAPACITY_TIMEOUT','ACTIVE',1),
('SYSTEM','*','WAITING_APPROVAL',true,'WAIT',86400,'FAIL','APPROVAL_EXPIRED','ACTIVE',1),
('SYSTEM','*','WAITING_EXTERNAL_INPUT',true,'REQUIRE_HUMAN',86400,'FAIL','RUNTIME_FATAL','ACTIVE',1),
('SYSTEM','*','AUTH_REQUIRED',true,'REQUIRE_HUMAN',3600,'FAIL','POLICY_REVOKED','ACTIVE',1),
('SYSTEM','*','RESIDENCY_BLOCKED',true,'FAIL',0,'FAIL','RESIDENCY_DENIED','ACTIVE',1),
('SYSTEM','*','BUDGET_REVIEW_REQUIRED',true,'REQUIRE_HUMAN',86400,'FAIL','BUDGET_EXHAUSTED','ACTIVE',1),
('SYSTEM','*','FLOW_CONFIGURATION_AMBIGUOUS',true,'REQUIRE_HUMAN',3600,'FAIL','FLOW_CONFIGURATION_ERROR','ACTIVE',1),
('SYSTEM','*','REMOTE_OUTCOME_UNKNOWN',true,'WAIT',86400,'MANUAL_RECONCILIATION','REMOTE_UNRESOLVED','ACTIVE',1),
('SYSTEM','*','LOCAL_OUTCOME_UNKNOWN',true,'WAIT',86400,'MANUAL_RECONCILIATION','LOCAL_UNRESOLVED','ACTIVE',1),
('SYSTEM','*','EVIDENCE_CONFLICT',false,'REQUIRE_HUMAN',null,'NONE',null,'ACTIVE',1),
('SYSTEM','*','INSUFFICIENT_EVIDENCE',false,'REQUIRE_HUMAN',null,'NONE',null,'ACTIVE',1),
('SYSTEM','*','COMPENSATING',true,'WAIT',86400,'FAIL','PLAN_FAILED','ACTIVE',1),
('SYSTEM','*','COMPENSATION_FAILED',false,'REQUIRE_HUMAN',null,'NONE',null,'ACTIVE',1),
('SYSTEM','*','FINALIZATION_RETRY_PENDING',false,'WAIT',null,'NONE',null,'ACTIVE',1),
('SYSTEM','*','EVIDENCE_PERSISTENCE_BLOCKED',false,'WAIT',null,'NONE',null,'ACTIVE',1),
('SYSTEM','*','CASE_PERSISTENCE_BLOCKED',false,'WAIT',null,'NONE',null,'ACTIVE',1),
('SYSTEM','*','MANUAL_RECOVERY_REQUIRED',false,'REQUIRE_HUMAN',null,'NONE',null,'ACTIVE',1)
on conflict(scope_type,scope_id,condition_type) do nothing;

create table if not exists task_conditions (
  tenant_id varchar(64) not null,
  task_id varchar(128) not null,
  condition_type varchar(64) not null,
  condition_state varchar(24) not null default 'ACTIVE',
  blocking boolean not null,
  resolution varchar(24) not null,
  timeout_at timestamptz,
  timeout_action varchar(32) not null,
  timeout_terminalization_reason varchar(48),
  reason_code varchar(160),
  reason text,
  source_ref varchar(255),
  raised_by varchar(255) not null,
  raised_at timestamptz not null default now(),
  resolved_by varchar(255),
  resolved_at timestamptz,
  resolution_note text,
  version int not null default 1,
  primary key(tenant_id,task_id,condition_type),
  constraint fk_a0r2_task_condition_task foreign key(tenant_id,task_id) references tasks(tenant_id,task_id) on delete cascade,
  constraint chk_a0r2_condition_state check(condition_state in ('ACTIVE','RESOLVED','EXPIRED')),
  constraint chk_a0r2_condition_resolution check(resolution in ('WAIT','FAIL','REQUIRE_HUMAN')),
  constraint chk_a0r2_condition_timeout_action check(timeout_action in ('NONE','FAIL','ESCALATE','MANUAL_RECONCILIATION')),
  constraint chk_a0r2_condition_resolved_fields check(condition_state='ACTIVE' or resolved_at is not null)
);
create index if not exists idx_a0r2_task_conditions_due on task_conditions(tenant_id,condition_state,timeout_at,task_id) where condition_state='ACTIVE' and timeout_at is not null;
create index if not exists idx_a0r2_task_conditions_task on task_conditions(tenant_id,task_id,condition_state,condition_type);
alter table task_conditions enable row level security;
drop policy if exists tenant_isolation on task_conditions;
create policy tenant_isolation on task_conditions using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());

-- -----------------------------------------------------------------------------
-- 4. Terminalization / Finalization evidence and checkpoints.
-- -----------------------------------------------------------------------------
create table if not exists task_terminalization_events (
  tenant_id varchar(64) not null,
  event_id varchar(180) not null,
  task_id varchar(128) not null,
  terminalization_reason varchar(48) not null,
  source_status varchar(32),
  actor_ref varchar(255) not null,
  reason text,
  correlation_id varchar(160),
  occurred_at timestamptz not null default now(),
  primary key(tenant_id,event_id),
  constraint fk_a0r2_terminal_event_task foreign key(tenant_id,task_id) references tasks(tenant_id,task_id) on delete cascade
);
create index if not exists idx_a0r2_terminal_events_task on task_terminalization_events(tenant_id,task_id,occurred_at,event_id);
alter table task_terminalization_events enable row level security;
drop policy if exists tenant_isolation on task_terminalization_events;
create policy tenant_isolation on task_terminalization_events using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());

create table if not exists task_finalization_steps (
  tenant_id varchar(64) not null,
  task_id varchar(128) not null,
  step_name varchar(64) not null,
  step_order int not null,
  step_status varchar(24) not null default 'PENDING',
  attempt_count int not null default 0,
  evidence_json jsonb not null default '{}'::jsonb,
  last_error_code varchar(160),
  last_error_message text,
  completed_at timestamptz,
  updated_at timestamptz not null default now(),
  primary key(tenant_id,task_id,step_name),
  constraint fk_a0r2_finalization_step_task foreign key(tenant_id,task_id) references tasks(tenant_id,task_id) on delete cascade,
  constraint chk_a0r2_finalization_step_name check(step_name in ('OUTCOME_RESOLUTION','AGGREGATION_PERSISTENCE','EVIDENCE_PERSISTENCE','CASE_PERSISTENCE','ISSUE_PROJECTION_OUTBOX','BUDGET_SETTLEMENT','EXECUTION_MEMORY','LEASE_RELEASE')),
  constraint chk_a0r2_finalization_step_status check(step_status in ('PENDING','RUNNING','COMPLETED','NOT_APPLICABLE','FAILED')),
  constraint chk_a0r2_finalization_step_evidence check(jsonb_typeof(evidence_json)='object'),
  constraint chk_a0r2_finalization_step_attempt check(attempt_count>=0)
);
create index if not exists idx_a0r2_finalization_steps on task_finalization_steps(tenant_id,task_id,step_order);
alter table task_finalization_steps enable row level security;
drop policy if exists tenant_isolation on task_finalization_steps;
create policy tenant_isolation on task_finalization_steps using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());

create table if not exists task_finalization_events (
  tenant_id varchar(64) not null,
  event_id varchar(180) not null,
  task_id varchar(128) not null,
  event_type varchar(64) not null,
  finalization_state varchar(40) not null,
  step_name varchar(64),
  outcome varchar(32),
  terminalization_reason varchar(48),
  actor_ref varchar(255) not null,
  evidence_json jsonb not null default '{}'::jsonb,
  occurred_at timestamptz not null default now(),
  primary key(tenant_id,event_id),
  constraint fk_a0r2_finalization_event_task foreign key(tenant_id,task_id) references tasks(tenant_id,task_id) on delete cascade,
  constraint chk_a0r2_finalization_event_evidence check(jsonb_typeof(evidence_json)='object')
);
create index if not exists idx_a0r2_finalization_events_task on task_finalization_events(tenant_id,task_id,occurred_at,event_id);
alter table task_finalization_events enable row level security;
drop policy if exists tenant_isolation on task_finalization_events;
create policy tenant_isolation on task_finalization_events using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());

create table if not exists task_finalization_evidence (
  tenant_id varchar(64) not null,
  evidence_id varchar(180) not null,
  task_id varchar(128) not null,
  terminalization_reason varchar(48) not null,
  resolved_outcome varchar(32) not null,
  outcome_resolver_version varchar(80) not null,
  legacy_status varchar(32),
  finalization_attempt int not null,
  evidence_json jsonb not null,
  recorded_at timestamptz not null default now(),
  primary key(tenant_id,evidence_id),
  unique(tenant_id,task_id),
  constraint fk_a0r2_finalization_evidence_task foreign key(tenant_id,task_id) references tasks(tenant_id,task_id) on delete cascade,
  constraint chk_a0r2_finalization_evidence_outcome check(resolved_outcome in ('SUCCEEDED','PARTIAL_SUCCEEDED','FAILED','CANCELLED')),
  constraint chk_a0r2_finalization_evidence_json check(jsonb_typeof(evidence_json)='object')
);
alter table task_finalization_evidence enable row level security;
drop policy if exists tenant_isolation on task_finalization_evidence;
create policy tenant_isolation on task_finalization_evidence using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());

create table if not exists task_finalization_projection_outbox (
  tenant_id varchar(64) not null,
  outbox_id varchar(180) not null,
  task_id varchar(128) not null,
  event_type varchar(64) not null default 'TASK_FINALIZED',
  idempotency_key varchar(255) not null,
  payload_json jsonb not null,
  status varchar(24) not null default 'PENDING',
  attempt_count int not null default 0,
  next_attempt_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key(tenant_id,outbox_id),
  unique(tenant_id,idempotency_key),
  constraint fk_a0r2_finalization_outbox_task foreign key(tenant_id,task_id) references tasks(tenant_id,task_id) on delete cascade,
  constraint chk_a0r2_finalization_outbox_status check(status in ('WAITING_FINALIZATION','PENDING','DISPATCHED','ACKNOWLEDGED','FAILED','SUPERSEDED')),
  constraint chk_a0r2_finalization_outbox_payload check(jsonb_typeof(payload_json)='object')
);
create index if not exists idx_a0r2_finalization_outbox_due on task_finalization_projection_outbox(tenant_id,status,next_attempt_at,outbox_id) where status in ('PENDING','FAILED');
alter table task_finalization_projection_outbox enable row level security;
drop policy if exists tenant_isolation on task_finalization_projection_outbox;
create policy tenant_isolation on task_finalization_projection_outbox using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());

create or replace function prevent_a0r2_append_only_mutation() returns trigger language plpgsql as $$ begin raise exception 'A0_R2_EVIDENCE_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_a0r2_terminalization_events_immutable on task_terminalization_events;
create trigger trg_a0r2_terminalization_events_immutable before update or delete on task_terminalization_events for each row execute function prevent_a0r2_append_only_mutation();
drop trigger if exists trg_a0r2_finalization_events_immutable on task_finalization_events;
create trigger trg_a0r2_finalization_events_immutable before update or delete on task_finalization_events for each row execute function prevent_a0r2_append_only_mutation();
drop trigger if exists trg_a0r2_finalization_evidence_immutable on task_finalization_evidence;
create trigger trg_a0r2_finalization_evidence_immutable before update or delete on task_finalization_evidence for each row execute function prevent_a0r2_append_only_mutation();

-- Record terminalization entry after the compatibility status signal has moved the task to FINALIZING.
create or replace function a0_r2_record_terminalization_event()
returns trigger language plpgsql as $$
begin
  if new.task_lifecycle='FINALIZING' and (old.task_lifecycle is distinct from 'FINALIZING' or old.terminalization_reason is distinct from new.terminalization_reason) then
    insert into task_terminalization_events(tenant_id,event_id,task_id,terminalization_reason,source_status,actor_ref,reason,correlation_id,occurred_at)
    values(new.tenant_id,'term-'||substr(md5(new.tenant_id||':'||new.task_id||':'||clock_timestamp()::text||':'||random()::text),1,40),new.task_id,new.terminalization_reason,new.status,
           coalesce(new.terminalization_actor_ref,'LEGACY_STATUS_SIGNAL'),new.lifecycle_reason,new.correlation_id,coalesce(new.terminalization_requested_at,now()));
  end if;
  return new;
end $$;
drop trigger if exists trg_a0_r2_terminalization_event on tasks;
create trigger trg_a0_r2_terminalization_event
after update of status on tasks
for each row execute function a0_r2_record_terminalization_event();

-- Finalizer-only canonical closure guard. Setting CLOSED outside an explicit A0-R2 finalizer
-- transaction is rejected even if a caller happens to know the column names.
create or replace function a0_r2_guard_canonical_close()
returns trigger language plpgsql as $$
begin
  if new.task_lifecycle='CLOSED' and old.task_lifecycle is distinct from 'CLOSED' then
    if coalesce(current_setting('app.a0_r2_finalizer',true),'') <> 'true' then
      raise exception 'TASK_CANONICAL_CLOSE_REQUIRES_FINALIZATION_COORDINATOR taskId=%', new.task_id;
    end if;
    if new.finalization_state<>'COMPLETED' or new.task_outcome='UNRESOLVED' or new.terminalization_reason is null or new.finalization_completed_at is null then
      raise exception 'TASK_CANONICAL_CLOSE_INCOMPLETE taskId=%', new.task_id;
    end if;
  end if;
  return new;
end $$;
drop trigger if exists trg_a0_r2_guard_canonical_close on tasks;
create trigger trg_a0_r2_guard_canonical_close
before update of task_lifecycle on tasks
for each row execute function a0_r2_guard_canonical_close();

-- Contract metadata.
insert into schema_contract_authority(contract_id,contract_family,authority_note,created_at,schema_version)
values('a0-r2-task-state-terminalization-finalization-authority-v1','A0_R2_TASK_STATE_AUTHORITY',
'TASK_LIFECYCLE_PHASE_OUTCOME_ARE_CANONICAL; LEGACY_STATUS_IS_COMPATIBILITY_SIGNAL; TERMINAL_STATUS_ENTERS_FINALIZING_NOT_CLOSED; ONLY_FINALIZATION_COORDINATOR_MAY_CLOSE; CONDITIONS_HAVE_RESOLUTION_POLICY; FINALIZATION_IS_DURABLE_IDEMPOTENT_AND_RECOVERABLE; ROUTING_AND_ASSIGNMENT_AUTHORITY_UNCHANGED',now(),'V200')
on conflict(contract_id) do nothing;


-- Flyway INSTANCE governance context for FORCE-RLS IAM/catalog DML below.
-- set_config(..., true) is transaction-local; earlier tenant-data backfills remain context-neutral.
select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','a0-r2-task-finalization-migration',true);

-- A0-R2 Human Admin finalization read/recovery entry points.
insert into permission_entry_point_inventory(
 entry_point_id,entry_point_type,application_id,owner_module,display_name,route_pattern,http_method,authority_state,
 target_permission_code,legacy_authority_type,legacy_authorities,resource_type,resource_resolver_id,exemption_reason,
 migration_deadline,manifest_revision,source_ref,source_hash,last_verified_at,created_by,updated_by)
values
('REST:GET:/admin/tasks/finalization-recovery','REST','control-plane-app','control-plane-app','TaskFinalizationAuthorityController.recovery','/admin/tasks/finalization-recovery','GET','TARGET_ONLY','task.read',null,'[]'::jsonb,'TASK','R3_PATH_RESOURCE_RESOLVER',null,null,'a0-r2-task-finalization-authority-2026-08-24','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/TaskFinalizationAuthorityController.java#recovery','3f69b5429756a070dec7401519aa2123d4137a6106b175b38b630cba5ef72716',now(),'a0-r2-task-finalization-authority','a0-r2-task-finalization-authority'),
('REST:GET:/admin/tasks/{taskId}/finalization-authority','REST','control-plane-app','control-plane-app','TaskFinalizationAuthorityController.view','/admin/tasks/{taskId}/finalization-authority','GET','TARGET_ONLY','task.read',null,'[]'::jsonb,'TASK','R3_PATH_RESOURCE_RESOLVER',null,null,'a0-r2-task-finalization-authority-2026-08-24','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/TaskFinalizationAuthorityController.java#view','3f69b5429756a070dec7401519aa2123d4137a6106b175b38b630cba5ef72716',now(),'a0-r2-task-finalization-authority','a0-r2-task-finalization-authority'),
('REST:POST:/admin/tasks/{taskId}/finalization-authority/retry','REST','control-plane-app','control-plane-app','TaskFinalizationAuthorityController.retry','/admin/tasks/{taskId}/finalization-authority/retry','POST','TARGET_ONLY','admin.core.admin.task.facade.run.task.remediation.command',null,'[]'::jsonb,'TASK','R3_PATH_RESOURCE_RESOLVER',null,null,'a0-r2-task-finalization-authority-2026-08-24','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/TaskFinalizationAuthorityController.java#retry','3f69b5429756a070dec7401519aa2123d4137a6106b175b38b630cba5ef72716',now(),'a0-r2-task-finalization-authority','a0-r2-task-finalization-authority')
on conflict(entry_point_id) do update set
 display_name=excluded.display_name,route_pattern=excluded.route_pattern,http_method=excluded.http_method,
 authority_state=excluded.authority_state,target_permission_code=excluded.target_permission_code,legacy_authority_type=null,
 legacy_authorities='[]'::jsonb,resource_type=excluded.resource_type,resource_resolver_id=excluded.resource_resolver_id,
 exemption_reason=null,migration_deadline=null,manifest_revision=excluded.manifest_revision,source_ref=excluded.source_ref,
 source_hash=excluded.source_hash,last_verified_at=now(),updated_at=now(),updated_by='a0-r2-task-finalization-authority',
 version=permission_entry_point_inventory.version+1;

update rbac_policy_versions set policy_version=policy_version+1,updated_at=now(),updated_by='a0-r2-task-finalization-authority';
