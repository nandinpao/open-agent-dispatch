-- Phase 9 — Execution Artifact Aggregation / Case Convergence.
-- Aggregation is capability-driven and consumes normalized Phase 8 Artifacts only.
-- Case is the enterprise problem authority; external issues are 0..N projections, never one issue per Child Task.

create table if not exists aggregation_definitions (
  tenant_id varchar(64) not null,
  aggregation_id varchar(160) not null,
  display_name varchar(255) not null,
  aggregation_capability_code varchar(160) not null,
  operation varchar(120) not null,
  required_input_capabilities_json jsonb not null default '[]'::jsonb,
  min_input_confidence numeric(8,6),
  allow_partial_evidence boolean not null default false,
  require_human_review_on_partial boolean not null default true,
  status varchar(24) not null default 'DRAFT',
  version int not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key(tenant_id,aggregation_id),
  constraint aggregation_definition_status_check check(status in ('DRAFT','ACTIVE','DISABLED','RETIRED')),
  constraint aggregation_definition_inputs_array check(jsonb_typeof(required_input_capabilities_json)='array'),
  constraint aggregation_definition_confidence_check check(min_input_confidence is null or (min_input_confidence>=0 and min_input_confidence<=1)),
  constraint aggregation_definition_version_check check(version>=1)
);
create index if not exists idx_aggregation_definitions_capability on aggregation_definitions(tenant_id,aggregation_capability_code,status,aggregation_id);

create table if not exists aggregation_definition_versions (
  tenant_id varchar(64) not null,
  aggregation_id varchar(160) not null,
  version int not null,
  snapshot_json jsonb not null,
  change_reason varchar(512) not null,
  actor_ref varchar(255) not null,
  created_at timestamptz not null default now(),
  primary key(tenant_id,aggregation_id,version),
  constraint fk_aggregation_definition_version foreign key(tenant_id,aggregation_id) references aggregation_definitions(tenant_id,aggregation_id) on delete cascade,
  constraint aggregation_definition_snapshot_object check(jsonb_typeof(snapshot_json)='object')
);

create table if not exists aggregation_preparation_decisions (
  tenant_id varchar(64) not null,
  decision_id varchar(160) not null,
  aggregation_id varchar(160) not null,
  aggregation_version int not null,
  run_id varchar(160) not null,
  result varchar(40) not null,
  aggregation_requirement_json jsonb,
  input_artifact_ids_json jsonb not null default '[]'::jsonb,
  reason_codes_json jsonb not null default '[]'::jsonb,
  requires_human_review boolean not null default false,
  created_at timestamptz not null default now(),
  primary key(tenant_id,decision_id),
  constraint fk_aggregation_preparation_run foreign key(tenant_id,run_id) references plan_execution_runs(tenant_id,run_id),
  constraint aggregation_preparation_result_check check(result in ('READY_FOR_CAPABILITY_EXECUTION','INCOMPLETE_EVIDENCE','CAPABILITY_GAP','RUN_NOT_TERMINAL')),
  constraint aggregation_preparation_requirement_object check(aggregation_requirement_json is null or jsonb_typeof(aggregation_requirement_json)='object'),
  constraint aggregation_preparation_artifacts_array check(jsonb_typeof(input_artifact_ids_json)='array'),
  constraint aggregation_preparation_reasons_array check(jsonb_typeof(reason_codes_json)='array')
);
create index if not exists idx_aggregation_preparation_run on aggregation_preparation_decisions(tenant_id,run_id,created_at desc,decision_id);

create table if not exists enterprise_cases (
  tenant_id varchar(64) not null,
  case_id varchar(160) not null,
  source_task_ref varchar(255),
  plan_id varchar(160) not null,
  run_id varchar(160) not null,
  aggregation_id varchar(160) not null,
  aggregation_version int not null,
  aggregation_artifact_id varchar(160) not null,
  classification varchar(160),
  affected_resources_json jsonb not null default '[]'::jsonb,
  root_cause text,
  confidence numeric(8,6),
  recommended_actions_json jsonb not null default '[]'::jsonb,
  human_decision text,
  accountable_ref varchar(255),
  status varchar(32) not null default 'OPEN',
  version int not null default 1,
  correlation_id varchar(160) not null,
  trace_id varchar(160) not null,
  idempotency_key varchar(255) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key(tenant_id,case_id),
  constraint fk_enterprise_case_run foreign key(tenant_id,run_id) references plan_execution_runs(tenant_id,run_id),
  constraint fk_enterprise_case_aggregation_artifact foreign key(tenant_id,aggregation_artifact_id) references plan_execution_artifacts(tenant_id,artifact_id),
  constraint enterprise_case_affected_resources_array check(jsonb_typeof(affected_resources_json)='array'),
  constraint enterprise_case_recommended_actions_array check(jsonb_typeof(recommended_actions_json)='array'),
  constraint enterprise_case_status_check check(status in ('OPEN','UNDER_REVIEW','RESOLVED','CLOSED')),
  constraint enterprise_case_confidence_check check(confidence is null or (confidence>=0 and confidence<=1)),
  constraint enterprise_case_version_check check(version>=1)
);
create unique index if not exists uq_enterprise_case_idempotency on enterprise_cases(tenant_id,run_id,idempotency_key);
create index if not exists idx_enterprise_cases_status on enterprise_cases(tenant_id,status,updated_at desc,case_id);

create table if not exists enterprise_case_versions (
  tenant_id varchar(64) not null,
  case_id varchar(160) not null,
  version int not null,
  snapshot_json jsonb not null,
  change_reason varchar(512) not null,
  actor_ref varchar(255) not null,
  created_at timestamptz not null default now(),
  primary key(tenant_id,case_id,version),
  constraint fk_enterprise_case_version foreign key(tenant_id,case_id) references enterprise_cases(tenant_id,case_id) on delete cascade,
  constraint enterprise_case_snapshot_object check(jsonb_typeof(snapshot_json)='object')
);

create table if not exists enterprise_case_artifacts (
  tenant_id varchar(64) not null,
  case_id varchar(160) not null,
  artifact_id varchar(160) not null,
  role varchar(40) not null,
  step_id varchar(160) not null,
  capability_code varchar(160) not null,
  linked_at timestamptz not null default now(),
  primary key(tenant_id,case_id,artifact_id),
  constraint fk_enterprise_case_artifact_case foreign key(tenant_id,case_id) references enterprise_cases(tenant_id,case_id) on delete cascade,
  constraint fk_enterprise_case_artifact_artifact foreign key(tenant_id,artifact_id) references plan_execution_artifacts(tenant_id,artifact_id),
  constraint enterprise_case_artifact_role_check check(role in ('EVIDENCE','AGGREGATION','SUPPORTING'))
);

create table if not exists enterprise_case_issue_projections (
  tenant_id varchar(64) not null,
  projection_id varchar(160) not null,
  case_id varchar(160) not null,
  connector_type varchar(80) not null,
  target_ref varchar(255) not null,
  primary_projection boolean not null default false,
  status varchar(32) not null default 'INTENT_RECORDED',
  existing_projection_ref varchar(255),
  external_issue_ref varchar(512),
  idempotency_key varchar(255) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key(tenant_id,projection_id),
  constraint fk_enterprise_case_projection_case foreign key(tenant_id,case_id) references enterprise_cases(tenant_id,case_id) on delete cascade,
  constraint enterprise_case_projection_status_check check(status in ('INTENT_RECORDED','LINKED','MATERIALIZED','FAILED','SUPERSEDED'))
);
create unique index if not exists uq_enterprise_case_projection_idempotency on enterprise_case_issue_projections(tenant_id,case_id,idempotency_key);
create unique index if not exists uq_enterprise_case_primary_projection on enterprise_case_issue_projections(tenant_id,case_id) where primary_projection=true and status<>'SUPERSEDED';

create table if not exists enterprise_case_events (
  tenant_id varchar(64) not null,
  event_id varchar(160) not null,
  case_id varchar(160) not null,
  event_type varchar(80) not null,
  actor_ref varchar(255) not null,
  reason varchar(512),
  evidence_json jsonb not null default '{}'::jsonb,
  occurred_at timestamptz not null default now(),
  primary key(tenant_id,event_id),
  constraint fk_enterprise_case_event_case foreign key(tenant_id,case_id) references enterprise_cases(tenant_id,case_id) on delete cascade,
  constraint enterprise_case_event_evidence_object check(jsonb_typeof(evidence_json)='object')
);
create index if not exists idx_enterprise_case_events on enterprise_case_events(tenant_id,case_id,occurred_at,event_id);

alter table aggregation_definitions enable row level security;
drop policy if exists tenant_isolation on aggregation_definitions; create policy tenant_isolation on aggregation_definitions using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
alter table aggregation_definition_versions enable row level security;
drop policy if exists tenant_isolation on aggregation_definition_versions; create policy tenant_isolation on aggregation_definition_versions using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
alter table aggregation_preparation_decisions enable row level security;
drop policy if exists tenant_isolation on aggregation_preparation_decisions; create policy tenant_isolation on aggregation_preparation_decisions using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
alter table enterprise_cases enable row level security;
drop policy if exists tenant_isolation on enterprise_cases; create policy tenant_isolation on enterprise_cases using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
alter table enterprise_case_versions enable row level security;
drop policy if exists tenant_isolation on enterprise_case_versions; create policy tenant_isolation on enterprise_case_versions using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
alter table enterprise_case_artifacts enable row level security;
drop policy if exists tenant_isolation on enterprise_case_artifacts; create policy tenant_isolation on enterprise_case_artifacts using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
alter table enterprise_case_issue_projections enable row level security;
drop policy if exists tenant_isolation on enterprise_case_issue_projections; create policy tenant_isolation on enterprise_case_issue_projections using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
alter table enterprise_case_events enable row level security;
drop policy if exists tenant_isolation on enterprise_case_events; create policy tenant_isolation on enterprise_case_events using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());

create or replace function prevent_phase9_append_only_mutation() returns trigger language plpgsql as $$ begin raise exception 'PHASE9_EVIDENCE_IS_APPEND_ONLY'; end $$;
do $$ begin
  if not exists(select 1 from pg_trigger where tgname='trg_aggregation_definition_versions_append_only') then create trigger trg_aggregation_definition_versions_append_only before update or delete on aggregation_definition_versions for each row execute function prevent_phase9_append_only_mutation(); end if;
  if not exists(select 1 from pg_trigger where tgname='trg_aggregation_preparation_decisions_append_only') then create trigger trg_aggregation_preparation_decisions_append_only before update or delete on aggregation_preparation_decisions for each row execute function prevent_phase9_append_only_mutation(); end if;
  if not exists(select 1 from pg_trigger where tgname='trg_enterprise_case_versions_append_only') then create trigger trg_enterprise_case_versions_append_only before update or delete on enterprise_case_versions for each row execute function prevent_phase9_append_only_mutation(); end if;
  if not exists(select 1 from pg_trigger where tgname='trg_enterprise_case_artifacts_append_only') then create trigger trg_enterprise_case_artifacts_append_only before update or delete on enterprise_case_artifacts for each row execute function prevent_phase9_append_only_mutation(); end if;
  if not exists(select 1 from pg_trigger where tgname='trg_enterprise_case_events_append_only') then create trigger trg_enterprise_case_events_append_only before update or delete on enterprise_case_events for each row execute function prevent_phase9_append_only_mutation(); end if;
end $$;

comment on table aggregation_definitions is 'phase9-artifact-aggregation-capability-authority; no provider/agent/pool/transport selection';
comment on table enterprise_cases is 'phase9-canonical-enterprise-case-authority; external issues are projections';
comment on table enterprise_case_issue_projections is 'phase9-0-to-N external issue projection intents; never one issue per child task';
