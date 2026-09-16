-- Phase 7 — Execution Plan / Multi-Capability Planning.
-- Planner output is proposal-only. Plans describe Canonical Capability requirements and dependencies only.
-- No Provider/Agent/Pool/Domain/A2A/MCP/Netty selection is represented or authorized by this schema.

-- Cross-phase schema contract registry. Phase 7 is the first migration that writes this
-- registry, so the registry must be created here before the first INSERT. Keeping this
-- bootstrap in the migration makes a clean V1 -> current Flyway install self-contained;
-- no manually pre-created relation or out-of-band database bootstrap is required.
create table if not exists schema_contract_authority (
  contract_id varchar(160) primary key,
  contract_family varchar(160) not null,
  authority_note text not null,
  created_at timestamptz not null default now(),
  schema_version varchar(32) not null
);
comment on table schema_contract_authority is
  'Current cross-phase schema architecture contract registry. This registry records schema-level authority metadata only and is not Tenant routing or runtime authorization state.';
create index if not exists idx_schema_contract_authority_version
  on schema_contract_authority(schema_version,contract_family);

create table if not exists execution_plan_policies (
  tenant_id varchar(64) not null,
  policy_id varchar(160) not null,
  display_name varchar(255) not null,
  max_steps int not null default 20,
  max_plan_depth int not null default 8,
  max_concurrent_branches int not null default 8,
  max_capability_invocations int not null default 20,
  max_token_budget bigint,
  max_estimated_cost numeric(18,6),
  max_execution_time_seconds bigint,
  require_human_review_on_plan_change boolean not null default false,
  status varchar(24) not null default 'DRAFT',
  version int not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id,policy_id),
  constraint execution_plan_policy_status_check check (status in ('DRAFT','ACTIVE','DISABLED','RETIRED')),
  constraint execution_plan_policy_steps_check check (max_steps between 1 and 500),
  constraint execution_plan_policy_depth_check check (max_plan_depth between 1 and 100),
  constraint execution_plan_policy_branches_check check (max_concurrent_branches between 1 and 500),
  constraint execution_plan_policy_invocations_check check (max_capability_invocations between 1 and 1000),
  constraint execution_plan_policy_token_budget_check check (max_token_budget is null or max_token_budget > 0),
  constraint execution_plan_policy_cost_check check (max_estimated_cost is null or max_estimated_cost >= 0),
  constraint execution_plan_policy_execution_time_check check (max_execution_time_seconds is null or max_execution_time_seconds > 0),
  constraint execution_plan_policy_version_check check (version >= 1)
);
create unique index if not exists uq_execution_plan_active_policy_per_tenant on execution_plan_policies(tenant_id) where status='ACTIVE';
comment on table execution_plan_policies is 'Phase 7 structural/budget guardrails for semantic plans. They do not authorize Provider execution.';

create table if not exists execution_plan_policy_versions (
  tenant_id varchar(64) not null,
  policy_id varchar(160) not null,
  version int not null,
  snapshot_json jsonb not null,
  change_reason varchar(512) not null,
  actor_ref varchar(255) not null,
  created_at timestamptz not null default now(),
  primary key (tenant_id,policy_id,version),
  constraint fk_execution_plan_policy_version foreign key (tenant_id,policy_id) references execution_plan_policies(tenant_id,policy_id) on delete cascade,
  constraint execution_plan_policy_snapshot_object check (jsonb_typeof(snapshot_json)='object')
);

create table if not exists execution_plan_requests (
  tenant_id varchar(64) not null,
  request_id varchar(160) not null,
  task_ref varchar(255),
  source_triage_decision_id varchar(160),
  classification_code varchar(160),
  initial_requirements_json jsonb not null default '[]'::jsonb,
  context_refs_json jsonb not null default '[]'::jsonb,
  status varchar(40) not null default 'PLANNING_REQUIRED',
  created_at timestamptz not null default now(),
  primary key (tenant_id,request_id),
  constraint execution_plan_request_requirements_array check (jsonb_typeof(initial_requirements_json)='array'),
  constraint execution_plan_request_context_refs_array check (jsonb_typeof(context_refs_json)='array'),
  constraint execution_plan_request_status_check check (status in ('PLANNING_REQUIRED','PLAN_VALIDATED','HUMAN_REVIEW_REQUIRED','CLOSED'))
);
create index if not exists idx_execution_plan_requests on execution_plan_requests(tenant_id,status,created_at desc);
comment on table execution_plan_requests is 'Phase 7 planning input. No provider/binding/transport destination is accepted.';

create table if not exists execution_plan_proposals (
  tenant_id varchar(64) not null,
  proposal_id varchar(160) not null,
  request_id varchar(160) not null,
  proposer_type varchar(40) not null,
  proposer_ref varchar(255),
  steps_json jsonb not null,
  rationale text,
  proposed_at timestamptz not null default now(),
  primary key (tenant_id,proposal_id),
  constraint fk_execution_plan_proposal_request foreign key (tenant_id,request_id) references execution_plan_requests(tenant_id,request_id),
  constraint execution_plan_proposer_type_check check (proposer_type in ('PLANNER_AGENT','HUMAN_ANALYST','SYSTEM_IMPORT')),
  constraint execution_plan_proposal_steps_array check (jsonb_typeof(steps_json)='array')
);
create index if not exists idx_execution_plan_proposals on execution_plan_proposals(tenant_id,request_id,proposed_at desc);
comment on table execution_plan_proposals is 'Append-only Planner/Human proposal evidence. Proposals never authorize or select a Provider.';

create table if not exists execution_plans (
  tenant_id varchar(64) not null,
  plan_id varchar(160) not null,
  request_id varchar(160) not null,
  task_ref varchar(255),
  source_triage_decision_id varchar(160),
  classification_code varchar(160),
  policy_id varchar(160) not null,
  policy_version int not null,
  status varchar(40) not null,
  current_revision int not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id,plan_id),
  constraint fk_execution_plan_request foreign key (tenant_id,request_id) references execution_plan_requests(tenant_id,request_id),
  constraint execution_plan_status_check check (status in ('SEMANTICALLY_VALIDATED','HUMAN_REVIEW_REQUIRED','SUPERSEDED','CANCELLED','CLOSED')),
  constraint execution_plan_revision_check check (current_revision >= 1)
);
create index if not exists idx_execution_plans on execution_plans(tenant_id,status,updated_at desc,plan_id);
comment on table execution_plans is 'Current pointer for a versioned semantic plan. SEMANTICALLY_VALIDATED does not mean WHO MAY or executable authorization.';

create table if not exists execution_plan_revisions (
  tenant_id varchar(64) not null,
  plan_id varchar(160) not null,
  revision int not null,
  proposal_id varchar(160),
  snapshot_json jsonb not null,
  change_reason varchar(512) not null,
  actor_ref varchar(255) not null,
  created_at timestamptz not null default now(),
  primary key (tenant_id,plan_id,revision),
  constraint fk_execution_plan_revision_plan foreign key (tenant_id,plan_id) references execution_plans(tenant_id,plan_id) on delete cascade,
  constraint execution_plan_revision_snapshot_object check (jsonb_typeof(snapshot_json)='object')
);

create table if not exists execution_plan_revision_steps (
  tenant_id varchar(64) not null,
  plan_id varchar(160) not null,
  revision int not null,
  step_id varchar(160) not null,
  capability_requirement_json jsonb not null,
  depends_on_json jsonb not null default '[]'::jsonb,
  purpose text,
  required boolean not null default true,
  sequence_hint int,
  created_at timestamptz not null default now(),
  primary key (tenant_id,plan_id,revision,step_id),
  constraint fk_execution_plan_revision_step foreign key (tenant_id,plan_id,revision) references execution_plan_revisions(tenant_id,plan_id,revision) on delete cascade,
  constraint execution_plan_step_requirement_object check (jsonb_typeof(capability_requirement_json)='object'),
  constraint execution_plan_step_dependencies_array check (jsonb_typeof(depends_on_json)='array')
);
create index if not exists idx_execution_plan_revision_steps on execution_plan_revision_steps(tenant_id,plan_id,revision,sequence_hint,step_id);
comment on table execution_plan_revision_steps is 'Immutable Phase 7 step graph. Each step contains WHAT + dependency only; WHO CAN/MAY/SHOULD/HOW remain separate.';

create table if not exists execution_plan_decisions (
  tenant_id varchar(64) not null,
  decision_id varchar(160) not null,
  request_id varchar(160) not null,
  proposal_id varchar(160),
  decision_mode varchar(16) not null default 'PREVIEW',
  result varchar(48) not null,
  plan_id varchar(160),
  plan_revision int,
  policy_id varchar(160),
  policy_version int,
  max_depth_observed int,
  max_concurrent_branches_observed int,
  reason_codes_json jsonb not null default '[]'::jsonb,
  requires_human_review boolean not null default false,
  decided_at timestamptz not null default now(),
  primary key (tenant_id,decision_id),
  constraint fk_execution_plan_decision_request foreign key (tenant_id,request_id) references execution_plan_requests(tenant_id,request_id),
  constraint execution_plan_decision_mode_check check (decision_mode in ('PREVIEW','RUNTIME')),
  constraint execution_plan_decision_result_check check (result in ('PLANNING_REQUIRED','PLAN_POLICY_NOT_CONFIGURED','PLAN_INVALID','CAPABILITY_GAP','HUMAN_REVIEW_REQUIRED','PLAN_VALIDATED')),
  constraint execution_plan_decision_reasons_array check (jsonb_typeof(reason_codes_json)='array')
);
create index if not exists idx_execution_plan_decisions on execution_plan_decisions(tenant_id,request_id,decided_at desc);
comment on table execution_plan_decisions is 'Append-only Phase 7 semantic plan validation evidence. PLAN_VALIDATED is not Provider authorization or execution approval.';

alter table execution_plan_policies enable row level security;
drop policy if exists tenant_isolation on execution_plan_policies;
create policy tenant_isolation on execution_plan_policies using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id());
alter table execution_plan_policy_versions enable row level security;
drop policy if exists tenant_isolation on execution_plan_policy_versions;
create policy tenant_isolation on execution_plan_policy_versions using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id());
alter table execution_plan_requests enable row level security;
drop policy if exists tenant_isolation on execution_plan_requests;
create policy tenant_isolation on execution_plan_requests using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id());
alter table execution_plan_proposals enable row level security;
drop policy if exists tenant_isolation on execution_plan_proposals;
create policy tenant_isolation on execution_plan_proposals using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id());
alter table execution_plans enable row level security;
drop policy if exists tenant_isolation on execution_plans;
create policy tenant_isolation on execution_plans using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id());
alter table execution_plan_revisions enable row level security;
drop policy if exists tenant_isolation on execution_plan_revisions;
create policy tenant_isolation on execution_plan_revisions using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id());
alter table execution_plan_revision_steps enable row level security;
drop policy if exists tenant_isolation on execution_plan_revision_steps;
create policy tenant_isolation on execution_plan_revision_steps using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id());
alter table execution_plan_decisions enable row level security;
drop policy if exists tenant_isolation on execution_plan_decisions;
create policy tenant_isolation on execution_plan_decisions using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id());

create or replace function prevent_execution_plan_policy_version_mutation() returns trigger language plpgsql as $$ begin raise exception 'EXECUTION_PLAN_POLICY_VERSION_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_execution_plan_policy_version_immutable on execution_plan_policy_versions;
create trigger trg_execution_plan_policy_version_immutable before update or delete on execution_plan_policy_versions for each row execute function prevent_execution_plan_policy_version_mutation();
create or replace function prevent_execution_plan_proposal_mutation() returns trigger language plpgsql as $$ begin raise exception 'EXECUTION_PLAN_PROPOSAL_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_execution_plan_proposal_immutable on execution_plan_proposals;
create trigger trg_execution_plan_proposal_immutable before update or delete on execution_plan_proposals for each row execute function prevent_execution_plan_proposal_mutation();
create or replace function prevent_execution_plan_revision_mutation() returns trigger language plpgsql as $$ begin raise exception 'EXECUTION_PLAN_REVISION_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_execution_plan_revision_immutable on execution_plan_revisions;
create trigger trg_execution_plan_revision_immutable before update or delete on execution_plan_revisions for each row execute function prevent_execution_plan_revision_mutation();
create or replace function prevent_execution_plan_revision_step_mutation() returns trigger language plpgsql as $$ begin raise exception 'EXECUTION_PLAN_REVISION_STEP_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_execution_plan_revision_step_immutable on execution_plan_revision_steps;
create trigger trg_execution_plan_revision_step_immutable before update or delete on execution_plan_revision_steps for each row execute function prevent_execution_plan_revision_step_mutation();
create or replace function prevent_execution_plan_decision_mutation() returns trigger language plpgsql as $$ begin raise exception 'EXECUTION_PLAN_DECISION_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_execution_plan_decision_immutable on execution_plan_decisions;
create trigger trg_execution_plan_decision_immutable before update or delete on execution_plan_decisions for each row execute function prevent_execution_plan_decision_mutation();

insert into schema_contract_authority(contract_id,contract_family,authority_note,created_at,schema_version)
values('phase7-execution-plan-authority','PHASE7_EXECUTION_PLAN_MULTI_CAPABILITY_PLANNING','PLANNER_PROPOSES_CAPABILITY_REQUIREMENTS_AND_DEPENDENCIES_ONLY_OPENDISPATCH_VALIDATES_DAG_CAPABILITIES_AND_STRUCTURAL_BUDGETS_EACH_STEP_MUST_LATER_PASS_WHO_CAN_WHO_MAY_WHO_SHOULD_HOW',now(),'V182')
on conflict (contract_id) do update set contract_family=excluded.contract_family,authority_note=excluded.authority_note,created_at=excluded.created_at,schema_version=excluded.schema_version;
