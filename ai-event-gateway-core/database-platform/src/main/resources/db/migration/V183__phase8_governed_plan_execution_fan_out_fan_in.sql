-- Phase 8 — Governed Plan Execution / Fan-out / Fan-in.
-- Executes a frozen Phase 7 Plan revision. READY is dependency readiness only; every Step must still
-- carry current WHO MAY -> WHO SHOULD -> HOW evidence before submission.

create table if not exists plan_execution_policies (
  tenant_id varchar(64) not null,
  policy_id varchar(160) not null,
  display_name varchar(255) not null,
  max_attempts_per_step int not null default 3,
  default_step_timeout_seconds bigint not null default 300,
  max_active_steps int not null default 16,
  require_artifact_on_success boolean not null default true,
  status varchar(24) not null default 'DRAFT',
  version int not null default 1,
  created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
  primary key(tenant_id,policy_id),
  constraint plan_execution_policy_status_check check(status in ('DRAFT','ACTIVE','DISABLED','RETIRED')),
  constraint plan_execution_policy_attempts_check check(max_attempts_per_step between 1 and 20),
  constraint plan_execution_policy_timeout_check check(default_step_timeout_seconds between 1 and 86400),
  constraint plan_execution_policy_active_steps_check check(max_active_steps between 1 and 500),
  constraint plan_execution_policy_version_check check(version>=1)
);
create unique index if not exists uq_plan_execution_active_policy on plan_execution_policies(tenant_id) where status='ACTIVE';

create table if not exists plan_execution_policy_versions (
  tenant_id varchar(64) not null, policy_id varchar(160) not null, version int not null,
  snapshot_json jsonb not null, change_reason varchar(512) not null, actor_ref varchar(255) not null,
  created_at timestamptz not null default now(), primary key(tenant_id,policy_id,version),
  constraint fk_plan_execution_policy_version foreign key(tenant_id,policy_id) references plan_execution_policies(tenant_id,policy_id) on delete cascade,
  constraint plan_execution_policy_snapshot_object check(jsonb_typeof(snapshot_json)='object')
);

create table if not exists plan_execution_runs (
  tenant_id varchar(64) not null, run_id varchar(160) not null, plan_id varchar(160) not null, plan_revision int not null,
  plan_decision_id varchar(160) not null, execution_mode varchar(16) not null, status varchar(32) not null,
  policy_id varchar(160) not null, policy_version int not null, idempotency_key varchar(255) not null,
  fencing_token bigint not null default 1, started_at timestamptz not null default now(), updated_at timestamptz not null default now(), completed_at timestamptz,
  primary key(tenant_id,run_id),
  constraint fk_plan_execution_run_plan foreign key(tenant_id,plan_id) references execution_plans(tenant_id,plan_id),
  constraint plan_execution_mode_check check(execution_mode in ('SIMULATION','RUNTIME')),
  constraint plan_execution_run_status_check check(status in ('RUNNING','CANCELLING','SUCCEEDED','PARTIAL_SUCCEEDED','FAILED','CANCELLED','TIMED_OUT')),
  constraint plan_execution_run_revision_check check(plan_revision>=1),
  constraint plan_execution_run_fence_check check(fencing_token>=1)
);
create unique index if not exists uq_plan_execution_run_idempotency on plan_execution_runs(tenant_id,plan_id,plan_revision,idempotency_key);
create index if not exists idx_plan_execution_runs on plan_execution_runs(tenant_id,status,updated_at desc,run_id);

create table if not exists plan_execution_steps (
  tenant_id varchar(64) not null, run_id varchar(160) not null, step_id varchar(160) not null,
  capability_requirement_json jsonb not null, depends_on_json jsonb not null default '[]'::jsonb, required boolean not null default true,
  state varchar(32) not null, attempt_count int not null default 0,
  authorization_decision_id varchar(160), routing_decision_id varchar(160), adapter_resolution_id varchar(160), child_task_ref varchar(255),
  deadline_at timestamptz, updated_at timestamptz not null default now(),
  primary key(tenant_id,run_id,step_id),
  constraint fk_plan_execution_step_run foreign key(tenant_id,run_id) references plan_execution_runs(tenant_id,run_id) on delete cascade,
  constraint plan_execution_step_requirement_object check(jsonb_typeof(capability_requirement_json)='object'),
  constraint plan_execution_step_dependencies_array check(jsonb_typeof(depends_on_json)='array'),
  constraint plan_execution_step_attempt_check check(attempt_count>=0),
  constraint plan_execution_step_state_check check(state in ('BLOCKED','READY','DISPATCH_READY','RUNNING','RETRY_WAIT','SUCCEEDED','FAILED','TIMED_OUT','CANCEL_REQUESTED','CANCELLED','SKIPPED'))
);
create index if not exists idx_plan_execution_steps on plan_execution_steps(tenant_id,run_id,state,step_id);

create table if not exists plan_execution_attempts (
  tenant_id varchar(64) not null, attempt_id varchar(160) not null, run_id varchar(160) not null, step_id varchar(160) not null,
  attempt_no int not null, idempotency_key varchar(255) not null, state varchar(32) not null, adapter_resolution_id varchar(160) not null,
  child_task_ref varchar(255), external_execution_ref varchar(512), reason_codes_json jsonb not null default '[]'::jsonb,
  submitted_at timestamptz not null default now(), deadline_at timestamptz, completed_at timestamptz,
  primary key(tenant_id,attempt_id),
  constraint fk_plan_execution_attempt_step foreign key(tenant_id,run_id,step_id) references plan_execution_steps(tenant_id,run_id,step_id),
  constraint plan_execution_attempt_no_check check(attempt_no>=1),
  constraint plan_execution_attempt_state_check check(state in ('SIMULATED','SUBMITTED','RUNNING','SUCCEEDED','FAILED','TIMED_OUT','CANCEL_REQUESTED','CANCELLED')),
  constraint plan_execution_attempt_reasons_array check(jsonb_typeof(reason_codes_json)='array')
);
create unique index if not exists uq_plan_execution_attempt_idempotency on plan_execution_attempts(tenant_id,run_id,step_id,idempotency_key);
create unique index if not exists uq_plan_execution_attempt_no on plan_execution_attempts(tenant_id,run_id,step_id,attempt_no);

create table if not exists plan_execution_artifacts (
  tenant_id varchar(64) not null, artifact_id varchar(160) not null, run_id varchar(160) not null, step_id varchar(160) not null, attempt_id varchar(160) not null,
  capability_code varchar(160) not null, finding text, confidence numeric(8,6), evidence_refs_json jsonb not null default '[]'::jsonb,
  output_json jsonb not null default '{}'::jsonb, data_classification varchar(80), created_at timestamptz not null default now(),
  primary key(tenant_id,artifact_id),
  constraint fk_plan_execution_artifact_attempt foreign key(tenant_id,attempt_id) references plan_execution_attempts(tenant_id,attempt_id),
  constraint plan_execution_artifact_evidence_array check(jsonb_typeof(evidence_refs_json)='array'),
  constraint plan_execution_artifact_output_object check(jsonb_typeof(output_json)='object'),
  constraint plan_execution_artifact_confidence_check check(confidence is null or (confidence>=0 and confidence<=1))
);
create index if not exists idx_plan_execution_artifacts on plan_execution_artifacts(tenant_id,run_id,step_id,created_at);

create table if not exists plan_execution_events (
  tenant_id varchar(64) not null, event_id varchar(160) not null, run_id varchar(160) not null, step_id varchar(160),
  event_type varchar(80) not null, from_state varchar(32), to_state varchar(32), reason varchar(512), actor_ref varchar(255) not null,
  evidence_json jsonb not null default '{}'::jsonb, occurred_at timestamptz not null default now(), primary key(tenant_id,event_id),
  constraint fk_plan_execution_event_run foreign key(tenant_id,run_id) references plan_execution_runs(tenant_id,run_id) on delete cascade,
  constraint plan_execution_event_evidence_object check(jsonb_typeof(evidence_json)='object')
);
create index if not exists idx_plan_execution_events on plan_execution_events(tenant_id,run_id,occurred_at,event_id);

alter table plan_execution_policies enable row level security;
drop policy if exists tenant_isolation on plan_execution_policies; create policy tenant_isolation on plan_execution_policies using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
alter table plan_execution_policy_versions enable row level security;
drop policy if exists tenant_isolation on plan_execution_policy_versions; create policy tenant_isolation on plan_execution_policy_versions using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
alter table plan_execution_runs enable row level security;
drop policy if exists tenant_isolation on plan_execution_runs; create policy tenant_isolation on plan_execution_runs using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
alter table plan_execution_steps enable row level security;
drop policy if exists tenant_isolation on plan_execution_steps; create policy tenant_isolation on plan_execution_steps using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
alter table plan_execution_attempts enable row level security;
drop policy if exists tenant_isolation on plan_execution_attempts; create policy tenant_isolation on plan_execution_attempts using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
alter table plan_execution_artifacts enable row level security;
drop policy if exists tenant_isolation on plan_execution_artifacts; create policy tenant_isolation on plan_execution_artifacts using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
alter table plan_execution_events enable row level security;
drop policy if exists tenant_isolation on plan_execution_events; create policy tenant_isolation on plan_execution_events using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());

create or replace function prevent_plan_execution_policy_version_mutation() returns trigger language plpgsql as $$ begin raise exception 'PLAN_EXECUTION_POLICY_VERSION_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_plan_execution_policy_version_immutable on plan_execution_policy_versions;
create trigger trg_plan_execution_policy_version_immutable before update or delete on plan_execution_policy_versions for each row execute function prevent_plan_execution_policy_version_mutation();
create or replace function prevent_plan_execution_artifact_mutation() returns trigger language plpgsql as $$ begin raise exception 'PLAN_EXECUTION_ARTIFACT_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_plan_execution_artifact_immutable on plan_execution_artifacts;
create trigger trg_plan_execution_artifact_immutable before update or delete on plan_execution_artifacts for each row execute function prevent_plan_execution_artifact_mutation();
create or replace function prevent_plan_execution_event_mutation() returns trigger language plpgsql as $$ begin raise exception 'PLAN_EXECUTION_EVENT_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_plan_execution_event_immutable on plan_execution_events;
create trigger trg_plan_execution_event_immutable before update or delete on plan_execution_events for each row execute function prevent_plan_execution_event_mutation();

insert into schema_contract_authority(contract_id,contract_family,authority_note,created_at,schema_version)
values('phase8-governed-plan-execution-authority','PHASE8_GOVERNED_PLAN_EXECUTION_FAN_OUT_FAN_IN','EXECUTE_FROZEN_PLAN_REVISION_DEPENDENCY_RELEASE_ONLY_EVERY_READY_STEP_REQUIRES_CURRENT_WHO_MAY_WHO_SHOULD_HOW_EVIDENCE_RETRY_CLEARS_OLD_AUTHORITY_FAN_IN_USES_NORMALIZED_ARTIFACTS',now(),'V183')
on conflict(contract_id) do update set contract_family=excluded.contract_family,authority_note=excluded.authority_note,created_at=excluded.created_at,schema_version=excluded.schema_version;
