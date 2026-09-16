-- Phase 4 — Provider Routing / WHO SHOULD
-- Ranking begins only after Phase 3 PASS authorization. This schema contains no A->B topology
-- and no execution adapter / endpoint / credential / Agent Pool authority.

create table if not exists routing_profiles (
  tenant_id varchar(64) not null,
  profile_id varchar(160) not null,
  display_name varchar(255) not null,
  description text,
  profile_type varchar(32) not null default 'BALANCED',
  status varchar(24) not null default 'DRAFT',
  weights_json jsonb not null default '{"QUALITY":35,"RELIABILITY":25,"LATENCY":20,"COST":10,"LOAD":10,"LOCALITY":0}'::jsonb,
  min_quality_score numeric(8,4),
  min_reliability_score numeric(8,4),
  max_p95_latency_ms bigint,
  max_estimated_cost numeric(18,6),
  max_load_percent numeric(8,4),
  max_observation_age_seconds int not null default 300,
  version int not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id,profile_id),
  constraint routing_profile_type_check check (profile_type in ('FAST','LOW_COST','BALANCED','HIGH_ACCURACY','CRITICAL','CUSTOM')),
  constraint routing_profile_status_check check (status in ('DRAFT','ACTIVE','SUSPENDED','RETIRED')),
  constraint routing_profile_weights_object check (jsonb_typeof(weights_json)='object'),
  constraint routing_profile_quality_check check (min_quality_score is null or (min_quality_score between 0 and 100)),
  constraint routing_profile_reliability_check check (min_reliability_score is null or (min_reliability_score between 0 and 100)),
  constraint routing_profile_latency_check check (max_p95_latency_ms is null or max_p95_latency_ms >= 0),
  constraint routing_profile_cost_check check (max_estimated_cost is null or max_estimated_cost >= 0),
  constraint routing_profile_load_check check (max_load_percent is null or (max_load_percent between 0 and 100)),
  constraint routing_profile_age_check check (max_observation_age_seconds between 1 and 86400),
  constraint routing_profile_version_check check (version >= 1)
);

comment on table routing_profiles is
  'Phase 4 WHO SHOULD ranking profiles. They rank already-authorized eligible providers and cannot grant WHO MAY or select HOW.';
comment on column routing_profiles.weights_json is
  'Explainable weighted ranking components: QUALITY, RELIABILITY, LATENCY, COST, LOAD, LOCALITY. Authorization is never a score component.';

create table if not exists routing_profile_versions (
  tenant_id varchar(64) not null,
  profile_id varchar(160) not null,
  version int not null,
  snapshot_json jsonb not null,
  change_reason varchar(512) not null,
  actor_ref varchar(255) not null,
  created_at timestamptz not null default now(),
  primary key (tenant_id,profile_id,version),
  constraint routing_profile_version_snapshot_object check (jsonb_typeof(snapshot_json)='object'),
  constraint fk_routing_profile_version foreign key (tenant_id,profile_id)
    references routing_profiles(tenant_id,profile_id) on delete cascade
);

create table if not exists provider_eligibility_observations (
  tenant_id varchar(64) not null,
  observation_id varchar(160) not null,
  binding_id varchar(160) not null,
  provider_id varchar(160) not null,
  observation_source varchar(40) not null,
  available boolean not null,
  healthy boolean not null,
  capacity_available boolean not null,
  quality_score numeric(8,4),
  reliability_score numeric(8,4),
  p95_latency_ms bigint,
  estimated_cost numeric(18,6),
  load_percent numeric(8,4),
  locality_score numeric(8,4),
  observed_at timestamptz not null,
  expires_at timestamptz,
  created_at timestamptz not null default now(),
  primary key (tenant_id,observation_id),
  constraint provider_eligibility_source_check check (observation_source in ('RUNTIME_OBSERVED','HISTORICAL_METRICS','ADMIN_VERIFIED','SYNTHETIC_PREVIEW')),
  constraint provider_eligibility_quality_check check (quality_score is null or (quality_score between 0 and 100)),
  constraint provider_eligibility_reliability_check check (reliability_score is null or (reliability_score between 0 and 100)),
  constraint provider_eligibility_latency_check check (p95_latency_ms is null or p95_latency_ms >= 0),
  constraint provider_eligibility_cost_check check (estimated_cost is null or estimated_cost >= 0),
  constraint provider_eligibility_load_check check (load_percent is null or (load_percent between 0 and 100)),
  constraint provider_eligibility_locality_check check (locality_score is null or (locality_score between 0 and 100)),
  constraint fk_provider_eligibility_binding foreign key (tenant_id,binding_id)
    references capability_bindings(tenant_id,binding_id),
  constraint fk_provider_eligibility_provider foreign key (tenant_id,provider_id)
    references capability_providers(tenant_id,provider_id)
);

create unique index if not exists uq_provider_eligibility_observation_identity
  on provider_eligibility_observations(tenant_id,observation_id);
create index if not exists idx_provider_eligibility_latest
  on provider_eligibility_observations(tenant_id,binding_id,observed_at desc);

comment on table provider_eligibility_observations is
  'Immutable protocol-neutral Phase 4 availability/quality observations. Self-advertised Agent Card/MCP metadata is not an accepted observation source.';

create table if not exists provider_routing_decisions (
  tenant_id varchar(64) not null,
  decision_id varchar(160) not null,
  decision_mode varchar(16) not null default 'PREVIEW',
  result varchar(32) not null,
  capability_code varchar(160) not null,
  operation varchar(80) not null,
  routing_profile_id varchar(160) not null,
  routing_profile_version int not null,
  selected_binding_id varchar(160),
  selected_provider_id varchar(160),
  reason_codes_json jsonb not null default '[]'::jsonb,
  candidates_json jsonb not null default '[]'::jsonb,
  evaluated_at timestamptz not null default now(),
  primary key (tenant_id,decision_id),
  constraint provider_routing_mode_check check (decision_mode in ('PREVIEW','RUNTIME')),
  constraint provider_routing_result_check check (result in ('SELECTED','NO_ELIGIBLE_PROVIDER','ROUTING_AMBIGUOUS')),
  constraint provider_routing_reason_array check (jsonb_typeof(reason_codes_json)='array'),
  constraint provider_routing_candidates_array check (jsonb_typeof(candidates_json)='array'),
  constraint fk_provider_routing_profile_version foreign key (tenant_id,routing_profile_id,routing_profile_version)
    references routing_profile_versions(tenant_id,profile_id,version)
);

create index if not exists idx_provider_routing_decisions
  on provider_routing_decisions(tenant_id,capability_code,evaluated_at desc);

comment on table provider_routing_decisions is
  'Append-only Phase 4 WHO SHOULD evidence. selected_binding_id/provider_id are routing results, never request inputs; no execution transport is represented.';

alter table routing_profiles enable row level security;
drop policy if exists tenant_isolation on routing_profiles;
create policy tenant_isolation on routing_profiles using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id());

alter table routing_profile_versions enable row level security;
drop policy if exists tenant_isolation on routing_profile_versions;
create policy tenant_isolation on routing_profile_versions using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id());

alter table provider_eligibility_observations enable row level security;
drop policy if exists tenant_isolation on provider_eligibility_observations;
create policy tenant_isolation on provider_eligibility_observations using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id());

alter table provider_routing_decisions enable row level security;
drop policy if exists tenant_isolation on provider_routing_decisions;
create policy tenant_isolation on provider_routing_decisions using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id());

create or replace function prevent_routing_profile_version_mutation() returns trigger language plpgsql as $$
begin raise exception 'ROUTING_PROFILE_VERSION_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_routing_profile_version_immutable on routing_profile_versions;
create trigger trg_routing_profile_version_immutable before update or delete on routing_profile_versions
for each row execute function prevent_routing_profile_version_mutation();

create or replace function prevent_provider_eligibility_observation_mutation() returns trigger language plpgsql as $$
begin raise exception 'PROVIDER_ELIGIBILITY_OBSERVATION_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_provider_eligibility_observation_immutable on provider_eligibility_observations;
create trigger trg_provider_eligibility_observation_immutable before update or delete on provider_eligibility_observations
for each row execute function prevent_provider_eligibility_observation_mutation();

create or replace function prevent_provider_routing_decision_mutation() returns trigger language plpgsql as $$
begin raise exception 'PROVIDER_ROUTING_DECISION_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_provider_routing_decision_immutable on provider_routing_decisions;
create trigger trg_provider_routing_decision_immutable before update or delete on provider_routing_decisions
for each row execute function prevent_provider_routing_decision_mutation();

insert into capability_architecture_evidence(evidence_id,architecture_version,principle,effective_at,created_by)
values(
  'phase4-provider-routing-authority',
  'PHASE4_PROVIDER_ROUTING_WHO_SHOULD',
  'RANK_ONLY_PHASE3_PASS_CANDIDATES_APPLY_ELIGIBILITY_HARD_GATES_PERSIST_EXPLAINABLE_SCORES_AND_NEVER_SELECT_EXECUTION_TRANSPORT',
  now(),
  'V179')
on conflict(evidence_id) do nothing;
