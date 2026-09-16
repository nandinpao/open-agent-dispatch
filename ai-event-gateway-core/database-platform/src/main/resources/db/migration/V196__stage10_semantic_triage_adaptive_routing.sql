-- Stage 10 — Semantic Triage / Adaptive Routing rollout control.
-- Known work remains deterministic Flow Rule authority. Semantic Triage is eligible only after confirmed NO_MATCH.
-- Triage output is WHAT proposal only; it cannot select Provider/Binding/Agent/Peer/Pool/Protocol or authorize execution.

create table if not exists semantic_triage_runtime_policies (
  tenant_id varchar(64) not null,
  policy_id varchar(160) not null,
  rollout_mode varchar(24) not null default 'REPLAY',
  sample_rate numeric(6,5) not null default 1.0,
  controlled_live_approved boolean not null default false,
  model_profile_ref varchar(255),
  prompt_profile_ref varchar(255),
  status varchar(24) not null default 'DRAFT',
  version int not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key(tenant_id,policy_id),
  constraint stage10_triage_rollout_mode_check check(rollout_mode in ('REPLAY','SHADOW','ADVISORY','CONTROLLED_LIVE')),
  constraint stage10_triage_sample_rate_check check(sample_rate between 0 and 1),
  constraint stage10_triage_runtime_status_check check(status in ('DRAFT','ACTIVE','DISABLED','RETIRED')),
  constraint stage10_controlled_live_explicit_approval check(rollout_mode<>'CONTROLLED_LIVE' or controlled_live_approved=true),
  constraint stage10_triage_runtime_version_check check(version>=1)
);
create unique index if not exists uq_stage10_active_triage_runtime_policy on semantic_triage_runtime_policies(tenant_id) where status='ACTIVE';

create table if not exists semantic_triage_runtime_policy_versions (
  tenant_id varchar(64) not null,
  policy_id varchar(160) not null,
  version int not null,
  snapshot_json jsonb not null,
  change_reason varchar(512) not null,
  actor_ref varchar(255) not null,
  created_at timestamptz not null default now(),
  primary key(tenant_id,policy_id,version),
  constraint fk_stage10_triage_runtime_policy foreign key(tenant_id,policy_id) references semantic_triage_runtime_policies(tenant_id,policy_id) on delete cascade,
  constraint stage10_triage_runtime_snapshot_object check(jsonb_typeof(snapshot_json)='object')
);

create table if not exists semantic_triage_runtime_work_items (
  tenant_id varchar(64) not null,
  task_id varchar(255) not null,
  status varchar(24) not null default 'PENDING',
  attempt_count int not null default 0,
  next_attempt_at timestamptz not null default now(),
  claimed_by varchar(160),
  claim_until timestamptz,
  last_error text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key(tenant_id,task_id),
  constraint stage10_triage_work_status_check check(status in ('PENDING','CLAIMED','COMPLETED','RETRY','SKIPPED')),
  constraint stage10_triage_work_attempt_check check(attempt_count>=0)
);
create index if not exists idx_stage10_triage_due on semantic_triage_runtime_work_items(tenant_id,status,next_attempt_at,claim_until);

create table if not exists semantic_triage_runtime_decisions (
  tenant_id varchar(64) not null,
  runtime_decision_id varchar(180) not null,
  task_id varchar(255) not null,
  trigger_type varchar(16) not null,
  flow_match_result varchar(24) not null,
  rollout_mode varchar(24) not null,
  runtime_policy_id varchar(160),
  runtime_policy_version int,
  triage_request_id varchar(160),
  semantic_decision_id varchar(160),
  semantic_result varchar(64),
  action varchar(48) not null,
  classification_code varchar(160),
  accepted_requirements_json jsonb not null default '[]'::jsonb,
  planning_request_id varchar(160),
  model_profile_ref varchar(255),
  prompt_profile_ref varchar(255),
  execution_side_effect_allowed boolean not null default false,
  reason_codes_json jsonb not null default '[]'::jsonb,
  evidence_json jsonb not null default '{}'::jsonb,
  decided_at timestamptz not null default now(),
  primary key(tenant_id,runtime_decision_id),
  constraint stage10_triage_trigger_check check(trigger_type in ('REPLAY','RUNTIME')),
  constraint stage10_triage_flow_result_check check(flow_match_result in ('NO_MATCH','MATCHED','RESOLUTION_ERROR')),
  constraint stage10_triage_decision_rollout_check check(rollout_mode in ('REPLAY','SHADOW','ADVISORY','CONTROLLED_LIVE')),
  constraint stage10_triage_action_check check(action in ('NO_ACTION','SHADOW_ONLY','ADVISORY_AVAILABLE','PLANNING_REQUEST_CREATED','HUMAN_REVIEW_REQUIRED','TRIAGE_UNAVAILABLE','FLOW_MATCHED_SKIPPED','FLOW_RESOLUTION_ERROR_SKIPPED','SAMPLE_SKIPPED')),
  constraint stage10_triage_requirements_array check(jsonb_typeof(accepted_requirements_json)='array'),
  constraint stage10_triage_reason_codes_array check(jsonb_typeof(reason_codes_json)='array'),
  constraint stage10_triage_evidence_object check(jsonb_typeof(evidence_json)='object'),
  constraint stage10_no_execution_side_effect check(execution_side_effect_allowed=false)
);
create index if not exists idx_stage10_triage_runtime_decisions on semantic_triage_runtime_decisions(tenant_id,task_id,decided_at desc);

alter table semantic_triage_runtime_policies enable row level security;
drop policy if exists tenant_isolation on semantic_triage_runtime_policies;
create policy tenant_isolation on semantic_triage_runtime_policies using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
alter table semantic_triage_runtime_policy_versions enable row level security;
drop policy if exists tenant_isolation on semantic_triage_runtime_policy_versions;
create policy tenant_isolation on semantic_triage_runtime_policy_versions using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
alter table semantic_triage_runtime_work_items enable row level security;
drop policy if exists tenant_isolation on semantic_triage_runtime_work_items;
create policy tenant_isolation on semantic_triage_runtime_work_items using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
alter table semantic_triage_runtime_decisions enable row level security;
drop policy if exists tenant_isolation on semantic_triage_runtime_decisions;
create policy tenant_isolation on semantic_triage_runtime_decisions using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());

create or replace function prevent_stage10_triage_runtime_policy_version_mutation() returns trigger language plpgsql as $$ begin raise exception 'SEMANTIC_TRIAGE_RUNTIME_POLICY_VERSION_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_stage10_triage_runtime_policy_version_immutable on semantic_triage_runtime_policy_versions;
create trigger trg_stage10_triage_runtime_policy_version_immutable before update or delete on semantic_triage_runtime_policy_versions for each row execute function prevent_stage10_triage_runtime_policy_version_mutation();
create or replace function prevent_stage10_triage_runtime_decision_mutation() returns trigger language plpgsql as $$ begin raise exception 'SEMANTIC_TRIAGE_RUNTIME_DECISION_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_stage10_triage_runtime_decision_immutable on semantic_triage_runtime_decisions;
create trigger trg_stage10_triage_runtime_decision_immutable before update or delete on semantic_triage_runtime_decisions for each row execute function prevent_stage10_triage_runtime_decision_mutation();

insert into schema_contract_authority(contract_id,contract_family,authority_note,created_at,schema_version)
values('stage10-semantic-triage-adaptive-routing-v1','STAGE10_SEMANTIC_TRIAGE_ADAPTIVE_ROUTING','KNOWN_WORK_REMAINS_DETERMINISTIC_FLOW_RULE; ONLY_CONFIRMED_NO_MATCH_MAY_ENTER_SEMANTIC_TRIAGE; TRIAGE_OUTPUT_IS_WHAT_PROPOSAL_NOT_PROVIDER_SELECTION_OR_EXECUTION_AUTHORITY; ROLLOUT_REPLAY_SHADOW_ADVISORY_CONTROLLED_LIVE; CONTROLLED_LIVE_MAY_CREATE_PLANNING_REQUEST_ONLY; FAST_PATH_AND_REMOTE_WRITE_REMAIN_OUT_OF_SCOPE',now(),'V196')
on conflict(contract_id) do nothing;
