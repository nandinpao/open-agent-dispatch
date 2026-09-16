-- Phase 10 — Outcome Evidence / Learning Fast Path / Drift Governance
-- Learning records accepted enterprise outcomes and proposes reusable semantic plan templates.
-- It MUST NOT learn or pin Provider, Agent, Pool, target Domain/System, endpoint, credential or transport.

create table if not exists learning_policies (
  tenant_id varchar(64) not null,
  policy_id varchar(160) not null,
  display_name varchar(255) not null,
  min_candidate_samples int not null default 3,
  min_candidate_success_rate numeric(8,6) not null default 0.90,
  min_candidate_human_acceptance_rate numeric(8,6) not null default 0.90,
  min_shadow_samples int not null default 3,
  min_active_success_rate numeric(8,6) not null default 0.90,
  min_active_human_acceptance_rate numeric(8,6) not null default 0.90,
  max_p95_latency_ms bigint,
  drift_window_size int not null default 20,
  require_human_approval_for_promotion boolean not null default true,
  status varchar(32) not null default 'DRAFT',
  version int not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key(tenant_id,policy_id),
  constraint learning_policy_status_check check(status in ('DRAFT','ACTIVE','DISABLED','RETIRED')),
  constraint learning_policy_counts_check check(min_candidate_samples>=1 and min_shadow_samples>=1 and drift_window_size>=1),
  constraint learning_policy_rates_check check(min_candidate_success_rate between 0 and 1 and min_candidate_human_acceptance_rate between 0 and 1 and min_active_success_rate between 0 and 1 and min_active_human_acceptance_rate between 0 and 1),
  constraint learning_policy_latency_check check(max_p95_latency_ms is null or max_p95_latency_ms>=0),
  constraint learning_policy_human_promotion_required check(require_human_approval_for_promotion=true),
  constraint learning_policy_version_check check(version>=1)
);
create unique index if not exists uq_learning_policy_active on learning_policies(tenant_id) where status='ACTIVE';

create table if not exists learning_policy_versions (
  tenant_id varchar(64) not null,
  policy_id varchar(160) not null,
  version int not null,
  snapshot_json jsonb not null,
  change_reason varchar(512) not null,
  actor_ref varchar(255) not null,
  created_at timestamptz not null default now(),
  primary key(tenant_id,policy_id,version),
  constraint fk_learning_policy_version foreign key(tenant_id,policy_id) references learning_policies(tenant_id,policy_id) on delete cascade,
  constraint learning_policy_snapshot_object check(jsonb_typeof(snapshot_json)='object')
);

create table if not exists execution_outcome_evidence (
  tenant_id varchar(64) not null,
  outcome_id varchar(160) not null,
  case_id varchar(160) not null,
  run_id varchar(160) not null,
  plan_id varchar(160) not null,
  plan_revision int not null,
  problem_signature varchar(128) not null,
  signature_version varchar(64) not null,
  classification varchar(160),
  capability_keys_json jsonb not null,
  execution_succeeded boolean not null,
  human_accepted boolean not null,
  latency_ms bigint,
  token_usage bigint,
  estimated_cost numeric(20,8),
  human_decision text not null,
  accountable_ref varchar(255) not null,
  recorded_at timestamptz not null default now(),
  primary key(tenant_id,outcome_id),
  constraint fk_learning_outcome_case foreign key(tenant_id,case_id) references enterprise_cases(tenant_id,case_id),
  constraint fk_learning_outcome_run foreign key(tenant_id,run_id) references plan_execution_runs(tenant_id,run_id),
  constraint learning_outcome_capabilities_array check(jsonb_typeof(capability_keys_json)='array'),
  constraint learning_outcome_latency_check check(latency_ms is null or latency_ms>=0),
  constraint learning_outcome_token_check check(token_usage is null or token_usage>=0),
  constraint learning_outcome_cost_check check(estimated_cost is null or estimated_cost>=0)
);
create unique index if not exists uq_execution_outcome_case on execution_outcome_evidence(tenant_id,case_id);
create index if not exists idx_execution_outcome_signature on execution_outcome_evidence(tenant_id,problem_signature,recorded_at desc,outcome_id);

create table if not exists routing_pattern_candidates (
  tenant_id varchar(64) not null,
  candidate_id varchar(160) not null,
  problem_signature varchar(128) not null,
  signature_version varchar(64) not null,
  classification varchar(160),
  capability_keys_json jsonb not null,
  plan_template_json jsonb not null,
  sample_count int not null,
  success_rate numeric(8,6) not null,
  human_acceptance_rate numeric(8,6) not null,
  p95_latency_ms bigint,
  status varchar(40) not null,
  evidence_case_ids_json jsonb not null,
  first_observed_at timestamptz not null,
  last_observed_at timestamptz not null,
  primary key(tenant_id,candidate_id),
  constraint routing_pattern_candidate_signature_unique unique(tenant_id,problem_signature),
  constraint routing_pattern_candidate_status_check check(status in ('OBSERVING','CANDIDATE_READY','AMBIGUOUS_PLAN','PROMOTED','REJECTED')),
  constraint routing_pattern_candidate_cap_array check(jsonb_typeof(capability_keys_json)='array'),
  constraint routing_pattern_candidate_plan_object check(jsonb_typeof(plan_template_json)='object'),
  constraint routing_pattern_candidate_cases_array check(jsonb_typeof(evidence_case_ids_json)='array'),
  constraint routing_pattern_candidate_rates check(success_rate between 0 and 1 and human_acceptance_rate between 0 and 1),
  constraint routing_pattern_candidate_samples check(sample_count>=1)
);

create table if not exists routing_patterns (
  tenant_id varchar(64) not null,
  pattern_id varchar(160) not null,
  candidate_id varchar(160) not null,
  problem_signature varchar(128) not null,
  signature_version varchar(64) not null,
  classification varchar(160),
  capability_keys_json jsonb not null,
  plan_template_json jsonb not null,
  status varchar(32) not null,
  version int not null default 1,
  learning_policy_id varchar(160) not null,
  learning_policy_version int not null,
  activated_at timestamptz,
  degraded_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key(tenant_id,pattern_id),
  constraint fk_routing_pattern_candidate foreign key(tenant_id,candidate_id) references routing_pattern_candidates(tenant_id,candidate_id),
  constraint fk_routing_pattern_policy foreign key(tenant_id,learning_policy_id) references learning_policies(tenant_id,policy_id),
  constraint routing_pattern_status_check check(status in ('SHADOW','ACTIVE','DEGRADED','RETIRED')),
  constraint routing_pattern_cap_array check(jsonb_typeof(capability_keys_json)='array'),
  constraint routing_pattern_plan_object check(jsonb_typeof(plan_template_json)='object'),
  constraint routing_pattern_version_check check(version>=1)
);
create unique index if not exists uq_routing_pattern_signature_live on routing_patterns(tenant_id,problem_signature) where status in ('SHADOW','ACTIVE','DEGRADED');
create index if not exists idx_routing_patterns_status on routing_patterns(tenant_id,status,updated_at desc,pattern_id);

create table if not exists routing_pattern_versions (
  tenant_id varchar(64) not null,
  pattern_id varchar(160) not null,
  version int not null,
  snapshot_json jsonb not null,
  change_reason varchar(512) not null,
  actor_ref varchar(255) not null,
  created_at timestamptz not null default now(),
  primary key(tenant_id,pattern_id,version),
  constraint fk_routing_pattern_version foreign key(tenant_id,pattern_id) references routing_patterns(tenant_id,pattern_id) on delete cascade,
  constraint routing_pattern_version_snapshot_object check(jsonb_typeof(snapshot_json)='object')
);

create table if not exists pattern_promotion_decisions (
  tenant_id varchar(64) not null,
  decision_id varchar(160) not null,
  candidate_id varchar(160),
  pattern_id varchar(160),
  from_status varchar(40),
  requested_status varchar(40) not null,
  result varchar(40) not null,
  reason_codes_json jsonb not null,
  actor_ref varchar(255) not null,
  decided_at timestamptz not null default now(),
  primary key(tenant_id,decision_id),
  constraint pattern_promotion_reasons_array check(jsonb_typeof(reason_codes_json)='array'),
  constraint pattern_promotion_result_check check(result in ('APPROVED','REJECTED','HUMAN_REVIEW_REQUIRED'))
);

create table if not exists pattern_quality_snapshots (
  tenant_id varchar(64) not null,
  snapshot_id varchar(160) not null,
  pattern_id varchar(160) not null,
  window_size int not null,
  sample_count int not null,
  success_rate numeric(8,6) not null,
  human_acceptance_rate numeric(8,6) not null,
  p95_latency_ms bigint,
  outcome_ids_json jsonb not null,
  observed_at timestamptz not null default now(),
  primary key(tenant_id,snapshot_id),
  constraint fk_pattern_quality_pattern foreign key(tenant_id,pattern_id) references routing_patterns(tenant_id,pattern_id) on delete cascade,
  constraint pattern_quality_outcomes_array check(jsonb_typeof(outcome_ids_json)='array'),
  constraint pattern_quality_rates check(success_rate between 0 and 1 and human_acceptance_rate between 0 and 1),
  constraint pattern_quality_samples check(sample_count>=0 and window_size>=1)
);
create index if not exists idx_pattern_quality_latest on pattern_quality_snapshots(tenant_id,pattern_id,observed_at desc,snapshot_id);

create table if not exists pattern_drift_observations (
  tenant_id varchar(64) not null,
  drift_id varchar(160) not null,
  pattern_id varchar(160) not null,
  result varchar(40) not null,
  reason_codes_json jsonb not null,
  quality_snapshot_id varchar(160),
  pattern_degraded boolean not null default false,
  observed_at timestamptz not null default now(),
  primary key(tenant_id,drift_id),
  constraint fk_pattern_drift_pattern foreign key(tenant_id,pattern_id) references routing_patterns(tenant_id,pattern_id) on delete cascade,
  constraint pattern_drift_result_check check(result in ('NO_DRIFT','DRIFT_DETECTED','INSUFFICIENT_EVIDENCE')),
  constraint pattern_drift_reasons_array check(jsonb_typeof(reason_codes_json)='array')
);

create table if not exists fast_path_resolution_decisions (
  tenant_id varchar(64) not null,
  decision_id varchar(160) not null,
  problem_signature varchar(128) not null,
  result varchar(40) not null,
  pattern_id varchar(160),
  pattern_version int,
  plan_template_json jsonb,
  reason_codes_json jsonb not null,
  decided_at timestamptz not null default now(),
  primary key(tenant_id,decision_id),
  constraint fast_path_result_check check(result in ('ACTIVE_PATTERN_MATCHED','NO_ACTIVE_PATTERN','PATTERN_STALE','CAPABILITY_GAP')),
  constraint fast_path_plan_object check(plan_template_json is null or jsonb_typeof(plan_template_json)='object'),
  constraint fast_path_reasons_array check(jsonb_typeof(reason_codes_json)='array')
);
create index if not exists idx_fast_path_signature on fast_path_resolution_decisions(tenant_id,problem_signature,decided_at desc,decision_id);

alter table learning_policies enable row level security; drop policy if exists tenant_isolation on learning_policies; create policy tenant_isolation on learning_policies using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
alter table learning_policy_versions enable row level security; drop policy if exists tenant_isolation on learning_policy_versions; create policy tenant_isolation on learning_policy_versions using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
alter table execution_outcome_evidence enable row level security; drop policy if exists tenant_isolation on execution_outcome_evidence; create policy tenant_isolation on execution_outcome_evidence using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
alter table routing_pattern_candidates enable row level security; drop policy if exists tenant_isolation on routing_pattern_candidates; create policy tenant_isolation on routing_pattern_candidates using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
alter table routing_patterns enable row level security; drop policy if exists tenant_isolation on routing_patterns; create policy tenant_isolation on routing_patterns using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
alter table routing_pattern_versions enable row level security; drop policy if exists tenant_isolation on routing_pattern_versions; create policy tenant_isolation on routing_pattern_versions using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
alter table pattern_promotion_decisions enable row level security; drop policy if exists tenant_isolation on pattern_promotion_decisions; create policy tenant_isolation on pattern_promotion_decisions using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
alter table pattern_quality_snapshots enable row level security; drop policy if exists tenant_isolation on pattern_quality_snapshots; create policy tenant_isolation on pattern_quality_snapshots using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
alter table pattern_drift_observations enable row level security; drop policy if exists tenant_isolation on pattern_drift_observations; create policy tenant_isolation on pattern_drift_observations using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
alter table fast_path_resolution_decisions enable row level security; drop policy if exists tenant_isolation on fast_path_resolution_decisions; create policy tenant_isolation on fast_path_resolution_decisions using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());

create or replace function prevent_phase10_append_only_mutation() returns trigger language plpgsql as $$ begin raise exception 'PHASE10_EVIDENCE_IS_APPEND_ONLY'; end $$;
do $$ begin
  if not exists(select 1 from pg_trigger where tgname='trg_learning_policy_versions_append_only') then create trigger trg_learning_policy_versions_append_only before update or delete on learning_policy_versions for each row execute function prevent_phase10_append_only_mutation(); end if;
  if not exists(select 1 from pg_trigger where tgname='trg_execution_outcome_evidence_append_only') then create trigger trg_execution_outcome_evidence_append_only before update or delete on execution_outcome_evidence for each row execute function prevent_phase10_append_only_mutation(); end if;
  if not exists(select 1 from pg_trigger where tgname='trg_routing_pattern_versions_append_only') then create trigger trg_routing_pattern_versions_append_only before update or delete on routing_pattern_versions for each row execute function prevent_phase10_append_only_mutation(); end if;
  if not exists(select 1 from pg_trigger where tgname='trg_pattern_promotion_decisions_append_only') then create trigger trg_pattern_promotion_decisions_append_only before update or delete on pattern_promotion_decisions for each row execute function prevent_phase10_append_only_mutation(); end if;
  if not exists(select 1 from pg_trigger where tgname='trg_pattern_quality_snapshots_append_only') then create trigger trg_pattern_quality_snapshots_append_only before update or delete on pattern_quality_snapshots for each row execute function prevent_phase10_append_only_mutation(); end if;
  if not exists(select 1 from pg_trigger where tgname='trg_pattern_drift_observations_append_only') then create trigger trg_pattern_drift_observations_append_only before update or delete on pattern_drift_observations for each row execute function prevent_phase10_append_only_mutation(); end if;
  if not exists(select 1 from pg_trigger where tgname='trg_fast_path_resolution_decisions_append_only') then create trigger trg_fast_path_resolution_decisions_append_only before update or delete on fast_path_resolution_decisions for each row execute function prevent_phase10_append_only_mutation(); end if;
end $$;

comment on table execution_outcome_evidence is 'phase10 accepted enterprise outcome evidence; only human-accountable Case outcomes enter learning';
comment on table routing_patterns is 'phase10 governed semantic fast-path templates; never pin provider/agent/pool/protocol and never bypass WHO MAY/SHOULD/HOW';
comment on table fast_path_resolution_decisions is 'phase10 exact semantic signature lookup; selects plan template only, never execution authority';
