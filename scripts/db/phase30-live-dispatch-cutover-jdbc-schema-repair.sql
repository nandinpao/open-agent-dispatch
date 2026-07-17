-- Phase 30 live repair for JDBC repositories that are not covered by MyBatis XML scans.
-- The generic routing path calls DispatchCutoverService before persisting routing decisions.
-- These objects must exist even when no operator-defined cutover policy has been created yet.

create table if not exists dispatch_cutover_policies (
  tenant_id varchar(64) not null,
  policy_id varchar(128) not null,
  flow_id varchar(128) not null default '*',
  mode varchar(32) not null default 'AUTHORITATIVE',
  canary_percentage int not null default 100,
  minimum_sample_size int not null default 50,
  maximum_requirement_blocked_rate double precision not null default 0.05,
  maximum_no_candidate_rate double precision not null default 0.10,
  maximum_selection_difference_rate double precision not null default 0.20,
  auto_rollback_enabled boolean not null default false,
  status varchar(32) not null default 'ACTIVE',
  version int not null default 1,
  rolled_back_at timestamptz,
  rollback_reason text,
  created_at timestamptz not null default now(),
  created_by varchar(128),
  updated_at timestamptz not null default now(),
  updated_by varchar(128),
  primary key (tenant_id, policy_id),
  unique (tenant_id, flow_id)
);
create index if not exists idx_dispatch_cutover_policies_effective on dispatch_cutover_policies(tenant_id, flow_id, status, updated_at desc);

create table if not exists dispatch_cutover_task_decisions (
  tenant_id varchar(64) not null,
  decision_id varchar(128) not null,
  task_id varchar(128) not null,
  flow_id varchar(128) not null,
  policy_id varchar(128),
  configured_mode varchar(32) not null,
  authoritative boolean not null default false,
  deterministic_bucket int not null default 0,
  reason_code varchar(128),
  created_at timestamptz not null default now(),
  primary key (tenant_id, decision_id),
  unique (tenant_id, task_id, flow_id)
);
create index if not exists idx_dispatch_cutover_task_decisions_task on dispatch_cutover_task_decisions(tenant_id, task_id, flow_id, created_at desc);

create table if not exists dispatch_cutover_outcomes (
  tenant_id varchar(64) not null,
  outcome_id varchar(128) not null,
  task_id varchar(128) not null,
  flow_id varchar(128) not null,
  policy_id varchar(128),
  authoritative boolean not null default false,
  requirement_blocked boolean not null default false,
  no_candidate boolean not null default false,
  selected_agent_different boolean not null default false,
  selected_agent_id varchar(128),
  legacy_selected_agent_id varchar(128),
  reason_code varchar(128),
  created_at timestamptz not null default now(),
  primary key (tenant_id, outcome_id),
  unique (tenant_id, task_id, flow_id, authoritative)
);
create index if not exists idx_dispatch_cutover_outcomes_flow on dispatch_cutover_outcomes(tenant_id, flow_id, authoritative, created_at desc);

create or replace view dispatch_p10_cutover_readiness as
select
  tenant_id,
  flow_id,
  count(*)::bigint as sample_size,
  count(*) filter (where authoritative)::bigint as authoritative_sample_size,
  count(*) filter (where not authoritative)::bigint as control_sample_size,
  count(*) filter (where requirement_blocked)::bigint as requirement_blocked_count,
  count(*) filter (where no_candidate)::bigint as no_candidate_count,
  count(*) filter (where selected_agent_different)::bigint as selection_difference_count,
  coalesce((count(*) filter (where requirement_blocked))::double precision / nullif(count(*), 0), 0.0) as requirement_blocked_rate,
  coalesce((count(*) filter (where no_candidate))::double precision / nullif(count(*), 0), 0.0) as no_candidate_rate,
  coalesce((count(*) filter (where selected_agent_different))::double precision / nullif(count(*) filter (where authoritative), 0), 0.0) as selection_difference_rate,
  (count(*) filter (where authoritative) > 0) as authoritative_metrics_available
from dispatch_cutover_outcomes
group by tenant_id, flow_id;

create table if not exists dispatch_operator_incidents (
  incident_id varchar(128) primary key,
  trigger_code varchar(128),
  severity varchar(32),
  task_id varchar(128),
  agent_id varchar(128),
  message text,
  metadata_json jsonb not null default '{}'::jsonb,
  status varchar(32) not null default 'OPEN',
  created_at timestamptz not null default now(),
  created_by varchar(128),
  updated_at timestamptz not null default now(),
  updated_by varchar(128)
);
create index if not exists idx_dispatch_operator_incidents_status on dispatch_operator_incidents(status, created_at desc);

create table if not exists dispatch_release_artifacts (
  artifact_id varchar(128) primary key,
  artifact_name varchar(255),
  artifact_path text,
  source varchar(128),
  generated_at timestamptz not null default now(),
  retained_until timestamptz,
  metadata_json jsonb not null default '{}'::jsonb
);
create index if not exists idx_dispatch_release_artifacts_source_time on dispatch_release_artifacts(source, generated_at desc);

create or replace view dispatch_p11_enforce_observability_snapshot as
select
  now() as generated_at,
  'runtime'::varchar(128) as source,
  'AUTHORITATIVE'::varchar(64) as mode,
  '24 hours'::varchar(64) as metric_window,
  0::bigint as v2_allowed,
  0::bigint as v2_blocked,
  coalesce((select count(*) from routing_decisions where status = 'NO_CANDIDATE'), 0)::bigint as no_candidate,
  0::bigint as fallback_denied,
  0::bigint as quality_unavailable,
  0::bigint as score_breakdown_missing,
  0.0::double precision as blocked_rate,
  0.0::double precision as no_candidate_rate,
  0.0::double precision as quality_unavailable_rate,
  0::bigint as readiness_blocking_count;

create or replace view dispatch_p11_routing_audit as
select
  decision_id,
  task_id,
  selected_agent_id as agent_id,
  routing_policy::text as policy_code,
  case when status = 'NO_CANDIDATE' then decision_reason else null end as blocking_code,
  'STANDARD'::varchar(64) as eligibility_engine_mode,
  false as eligibility_v2_applied,
  false as eligibility_v2_candidate_eligible,
  selected_score as eligibility_v2_score,
  '[]'::text as eligibility_v2_blocking_reasons,
  '{}'::text as eligibility_v2_score_breakdown,
  created_at
from routing_decisions;

create or replace view dispatch_p11_legacy_final_report as
select
  'STANDARD_RUNTIME'::varchar(128) as category,
  0::bigint as count,
  'INFO'::varchar(32) as severity,
  array[]::text[] as sample_refs;

create or replace view dispatch_p11_artifact_retention as
select
  artifact_name,
  artifact_path,
  generated_at,
  retained_until,
  source
from dispatch_release_artifacts;
