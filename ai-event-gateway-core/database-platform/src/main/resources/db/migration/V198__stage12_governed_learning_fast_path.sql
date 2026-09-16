-- Stage 12 — Governed Learning / Semantic Fast Path.
-- Memory may recommend but never authorize execution.
-- A certified Fast Path is a semantic hint only; current WHO CAN / WHO MAY / WHO SHOULD / HOW,
-- current authorization epoch, assignment lease and fencing remain mandatory at execution time.

-- Retire the legacy Phase11 direct runtime activation path at the database boundary.
update fast_path_runtime_policies set active_fast_path_enabled=false where active_fast_path_enabled=true;
alter table fast_path_runtime_policies drop constraint if exists stage12_legacy_fast_path_runtime_disabled;
alter table fast_path_runtime_policies add constraint stage12_legacy_fast_path_runtime_disabled check(active_fast_path_enabled=false);

create table if not exists learning_governance_policies_v53 (
  tenant_id varchar(64) not null,
  policy_id varchar(160) not null,
  display_name varchar(255) not null,
  min_recommendation_samples int not null default 20,
  min_replay_samples int not null default 20,
  min_replay_match_rate numeric(8,6) not null default 0.950000,
  min_shadow_samples int not null default 20,
  min_shadow_match_rate numeric(8,6) not null default 0.980000,
  min_shadow_capability_coverage numeric(8,6) not null default 1.000000,
  require_human_certification boolean not null default true,
  status varchar(24) not null default 'DRAFT',
  version int not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key(tenant_id,policy_id),
  constraint stage12_learning_policy_status check(status in ('DRAFT','ACTIVE','DISABLED','RETIRED')),
  constraint stage12_learning_policy_samples check(min_recommendation_samples>=1 and min_replay_samples>=1 and min_shadow_samples>=1),
  constraint stage12_learning_policy_rates check(min_replay_match_rate between 0 and 1 and min_shadow_match_rate between 0 and 1 and min_shadow_capability_coverage between 0 and 1),
  constraint stage12_learning_policy_version check(version>=1),
  constraint stage12_human_certification_mandatory check(require_human_certification=true)
);
create unique index if not exists uq_stage12_active_learning_policy on learning_governance_policies_v53(tenant_id) where status='ACTIVE';

create table if not exists learning_governance_policy_versions_v53 (
  tenant_id varchar(64) not null,
  policy_id varchar(160) not null,
  version int not null,
  snapshot_json jsonb not null,
  change_reason varchar(512) not null,
  actor_ref varchar(255) not null,
  created_at timestamptz not null default now(),
  primary key(tenant_id,policy_id,version),
  foreign key(tenant_id,policy_id) references learning_governance_policies_v53(tenant_id,policy_id) on delete cascade,
  constraint stage12_policy_snapshot_object check(jsonb_typeof(snapshot_json)='object')
);

create table if not exists learning_recommendations_v53 (
  tenant_id varchar(64) not null,
  recommendation_id varchar(180) not null,
  target_type varchar(64) not null,
  target_key varchar(512),
  title varchar(255) not null,
  summary text not null,
  capability_code varchar(160),
  operation varchar(80),
  provider_type varchar(40),
  provider_id varchar(160),
  binding_id varchar(160),
  source_window_start date,
  source_window_end date,
  sample_count bigint not null default 0,
  observed_success_rate numeric(8,6),
  average_latency_ms numeric(24,4),
  booked_actual_cost numeric(24,8),
  normalized_comparison_cost numeric(24,8),
  unbooked_count bigint not null default 0,
  payload_json jsonb not null default '{}'::jsonb,
  evidence_digest varchar(96) not null,
  status varchar(24) not null default 'PROPOSED',
  reviewed_by varchar(255),
  review_reason varchar(1024),
  reviewed_at timestamptz,
  created_at timestamptz not null default now(),
  primary key(tenant_id,recommendation_id),
  constraint stage12_recommendation_target_type check(target_type in ('FLOW_RULE_CANDIDATE','ROUTING_PROFILE_CANDIDATE','CAPABILITY_MAPPING_CANDIDATE','PROVIDER_RELIABILITY_RECOMMENDATION','GOVERNANCE_POLICY_RECOMMENDATION','OPERATIONAL_RECOMMENDATION')),
  constraint stage12_recommendation_status check(status in ('PROPOSED','APPROVED','REJECTED','SUPERSEDED')),
  constraint stage12_recommendation_sample_count check(sample_count>=0),
  constraint stage12_recommendation_success_rate check(observed_success_rate is null or observed_success_rate between 0 and 1),
  constraint stage12_recommendation_payload_object check(jsonb_typeof(payload_json)='object')
);
create index if not exists idx_stage12_recommendation_status on learning_recommendations_v53(tenant_id,status,created_at desc);
create unique index if not exists uq_stage12_recommendation_digest on learning_recommendations_v53(tenant_id,evidence_digest) where status<>'SUPERSEDED';

create table if not exists learning_recommendation_events_v53 (
  tenant_id varchar(64) not null,
  event_id varchar(180) not null,
  recommendation_id varchar(180) not null,
  event_type varchar(48) not null,
  from_status varchar(24),
  to_status varchar(24),
  actor_ref varchar(255) not null,
  reason varchar(1024),
  evidence_json jsonb not null default '{}'::jsonb,
  occurred_at timestamptz not null default now(),
  primary key(tenant_id,event_id),
  foreign key(tenant_id,recommendation_id) references learning_recommendations_v53(tenant_id,recommendation_id) on delete cascade,
  constraint stage12_recommendation_event_evidence check(jsonb_typeof(evidence_json)='object')
);

create table if not exists fast_path_semantic_candidates_v53 (
  tenant_id varchar(64) not null,
  candidate_id varchar(180) not null,
  source_recommendation_id varchar(180),
  problem_signature varchar(160) not null,
  classification varchar(160),
  source_plan_id varchar(160),
  source_plan_revision int,
  semantic_plan_template_json jsonb not null,
  capability_keys_json jsonb not null,
  template_digest varchar(96) not null,
  status varchar(32) not null default 'REPLAY_REQUIRED',
  version int not null default 1,
  created_by varchar(255) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key(tenant_id,candidate_id),
  foreign key(tenant_id,source_recommendation_id) references learning_recommendations_v53(tenant_id,recommendation_id) on delete set null,
  constraint stage12_fast_path_candidate_status check(status in ('REPLAY_REQUIRED','SHADOW_REQUIRED','CERTIFICATION_READY','CERTIFIED','SUSPENDED','REVOKED')),
  constraint stage12_fast_path_candidate_version check(version>=1),
  constraint stage12_fast_path_template_object check(jsonb_typeof(semantic_plan_template_json)='object'),
  constraint stage12_fast_path_capability_keys_array check(jsonb_typeof(capability_keys_json)='array')
);
create index if not exists idx_stage12_candidate_status on fast_path_semantic_candidates_v53(tenant_id,status,updated_at desc);
create unique index if not exists uq_stage12_candidate_signature_live on fast_path_semantic_candidates_v53(tenant_id,problem_signature) where status not in ('REVOKED');

create table if not exists fast_path_replay_evidence_v53 (
  tenant_id varchar(64) not null,
  evidence_id varchar(180) not null,
  candidate_id varchar(180) not null,
  candidate_version int not null,
  sample_count int not null,
  semantic_match_rate numeric(8,6) not null,
  mismatch_count int not null default 0,
  execution_affected boolean not null default false,
  evidence_json jsonb not null default '{}'::jsonb,
  observed_at timestamptz not null default now(),
  primary key(tenant_id,evidence_id),
  foreign key(tenant_id,candidate_id) references fast_path_semantic_candidates_v53(tenant_id,candidate_id) on delete cascade,
  constraint stage12_replay_sample_check check(sample_count>=1 and mismatch_count>=0),
  constraint stage12_replay_match_rate check(semantic_match_rate between 0 and 1),
  constraint stage12_replay_no_execution check(execution_affected=false),
  constraint stage12_replay_evidence_object check(jsonb_typeof(evidence_json)='object')
);

create table if not exists fast_path_shadow_evidence_v53 (
  tenant_id varchar(64) not null,
  evidence_id varchar(180) not null,
  candidate_id varchar(180) not null,
  candidate_version int not null,
  sample_count int not null,
  semantic_match_rate numeric(8,6) not null,
  capability_coverage numeric(8,6) not null,
  mismatch_count int not null default 0,
  execution_affected boolean not null default false,
  evidence_json jsonb not null default '{}'::jsonb,
  observed_at timestamptz not null default now(),
  primary key(tenant_id,evidence_id),
  foreign key(tenant_id,candidate_id) references fast_path_semantic_candidates_v53(tenant_id,candidate_id) on delete cascade,
  constraint stage12_shadow_sample_check check(sample_count>=1 and mismatch_count>=0),
  constraint stage12_shadow_rates check(semantic_match_rate between 0 and 1 and capability_coverage between 0 and 1),
  constraint stage12_shadow_no_execution check(execution_affected=false),
  constraint stage12_shadow_evidence_object check(jsonb_typeof(evidence_json)='object')
);

create table if not exists fast_path_certifications_v53 (
  tenant_id varchar(64) not null,
  certification_id varchar(180) not null,
  candidate_id varchar(180) not null,
  candidate_version int not null,
  template_digest varchar(96) not null,
  capability_schema_digest varchar(96) not null,
  policy_id varchar(160) not null,
  policy_version int not null,
  replay_sample_count int not null,
  replay_match_rate numeric(8,6) not null,
  shadow_sample_count int not null,
  shadow_match_rate numeric(8,6) not null,
  shadow_capability_coverage numeric(8,6) not null,
  status varchar(24) not null default 'CERTIFIED',
  certified_by varchar(255) not null,
  certification_reason varchar(1024) not null,
  suspended_reason varchar(1024),
  certified_at timestamptz not null default now(),
  suspended_at timestamptz,
  primary key(tenant_id,certification_id),
  foreign key(tenant_id,candidate_id) references fast_path_semantic_candidates_v53(tenant_id,candidate_id) on delete cascade,
  constraint stage12_certification_status check(status in ('CERTIFIED','SUSPENDED','REVOKED')),
  constraint stage12_certification_rates check(replay_match_rate between 0 and 1 and shadow_match_rate between 0 and 1 and shadow_capability_coverage between 0 and 1)
);
create index if not exists idx_stage12_certification_candidate on fast_path_certifications_v53(tenant_id,candidate_id,certified_at desc);

create table if not exists fast_path_runtime_hints_v53 (
  tenant_id varchar(64) not null,
  hint_id varchar(180) not null,
  task_ref varchar(255) not null,
  idempotency_key varchar(255) not null,
  problem_signature varchar(160) not null,
  result varchar(48) not null,
  candidate_id varchar(180),
  candidate_version int,
  certification_id varchar(180),
  template_digest varchar(96),
  semantic_plan_template_json jsonb,
  capability_keys_json jsonb,
  execution_authorized boolean not null default false,
  reason_codes_json jsonb not null default '[]'::jsonb,
  created_at timestamptz not null default now(),
  primary key(tenant_id,hint_id),
  constraint stage12_runtime_hint_result check(result in ('HINT_AVAILABLE','FALLBACK_ADAPTIVE_PATH','CANDIDATE_TEMPLATE_DRIFT','CAPABILITY_SCHEMA_DRIFT','NOT_CERTIFIED','NO_CANDIDATE','MULTIPLE_CANDIDATES_FAIL_CLOSED')),
  constraint stage12_runtime_hint_never_authorizes check(execution_authorized=false),
  constraint stage12_runtime_hint_reasons_array check(jsonb_typeof(reason_codes_json)='array'),
  constraint stage12_runtime_hint_template_object check(semantic_plan_template_json is null or jsonb_typeof(semantic_plan_template_json)='object'),
  constraint stage12_runtime_hint_caps_array check(capability_keys_json is null or jsonb_typeof(capability_keys_json)='array')
);
create unique index if not exists uq_stage12_runtime_hint_idempotency on fast_path_runtime_hints_v53(tenant_id,task_ref,idempotency_key);

-- Tenant isolation for every Stage12 governance/evidence table.
alter table learning_governance_policies_v53 enable row level security;
alter table learning_governance_policy_versions_v53 enable row level security;
alter table learning_recommendations_v53 enable row level security;
alter table learning_recommendation_events_v53 enable row level security;
alter table fast_path_semantic_candidates_v53 enable row level security;
alter table fast_path_replay_evidence_v53 enable row level security;
alter table fast_path_shadow_evidence_v53 enable row level security;
alter table fast_path_certifications_v53 enable row level security;
alter table fast_path_runtime_hints_v53 enable row level security;

do $$ declare r text; begin
  foreach r in array array['learning_governance_policies_v53','learning_governance_policy_versions_v53','learning_recommendations_v53','learning_recommendation_events_v53','fast_path_semantic_candidates_v53','fast_path_replay_evidence_v53','fast_path_shadow_evidence_v53','fast_path_certifications_v53','fast_path_runtime_hints_v53'] loop
    execute format('drop policy if exists tenant_isolation on %I',r);
    execute format('create policy tenant_isolation on %I using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id())',r);
  end loop;
end $$;

-- Immutable recommendation evidence: only review lifecycle may change.
create or replace function stage12_protect_recommendation_content() returns trigger language plpgsql as $$
begin
  if old.target_type is distinct from new.target_type or old.target_key is distinct from new.target_key or old.title is distinct from new.title or old.summary is distinct from new.summary
     or old.capability_code is distinct from new.capability_code or old.operation is distinct from new.operation or old.provider_type is distinct from new.provider_type
     or old.provider_id is distinct from new.provider_id or old.binding_id is distinct from new.binding_id or old.source_window_start is distinct from new.source_window_start
     or old.source_window_end is distinct from new.source_window_end or old.sample_count is distinct from new.sample_count or old.observed_success_rate is distinct from new.observed_success_rate
     or old.average_latency_ms is distinct from new.average_latency_ms or old.booked_actual_cost is distinct from new.booked_actual_cost
     or old.normalized_comparison_cost is distinct from new.normalized_comparison_cost or old.unbooked_count is distinct from new.unbooked_count
     or old.payload_json is distinct from new.payload_json or old.evidence_digest is distinct from new.evidence_digest or old.created_at is distinct from new.created_at then
    raise exception 'STAGE12_RECOMMENDATION_CONTENT_IS_IMMUTABLE';
  end if;
  return new;
end $$;
drop trigger if exists trg_stage12_recommendation_content_immutable on learning_recommendations_v53;
create trigger trg_stage12_recommendation_content_immutable before update on learning_recommendations_v53 for each row execute function stage12_protect_recommendation_content();

-- Candidate semantic content is immutable. State transitions require new evidence; content changes require a new candidate/version.
create or replace function stage12_protect_fast_path_candidate_content() returns trigger language plpgsql as $$
begin
  if old.source_recommendation_id is distinct from new.source_recommendation_id or old.problem_signature is distinct from new.problem_signature
     or old.classification is distinct from new.classification or old.source_plan_id is distinct from new.source_plan_id or old.source_plan_revision is distinct from new.source_plan_revision
     or old.semantic_plan_template_json is distinct from new.semantic_plan_template_json or old.capability_keys_json is distinct from new.capability_keys_json
     or old.template_digest is distinct from new.template_digest or old.version is distinct from new.version or old.created_by is distinct from new.created_by or old.created_at is distinct from new.created_at then
    raise exception 'STAGE12_FAST_PATH_CANDIDATE_CONTENT_IS_IMMUTABLE';
  end if;
  return new;
end $$;
drop trigger if exists trg_stage12_fast_path_candidate_content_immutable on fast_path_semantic_candidates_v53;
create trigger trg_stage12_fast_path_candidate_content_immutable before update on fast_path_semantic_candidates_v53 for each row execute function stage12_protect_fast_path_candidate_content();

create or replace function prevent_stage12_append_only_mutation() returns trigger language plpgsql as $$ begin raise exception 'STAGE12_EVIDENCE_IS_APPEND_ONLY'; end $$;
do $$ declare r text; begin
  foreach r in array array['learning_governance_policy_versions_v53','learning_recommendation_events_v53','fast_path_replay_evidence_v53','fast_path_shadow_evidence_v53','fast_path_runtime_hints_v53'] loop
    execute format('drop trigger if exists trg_stage12_append_only on %I',r);
    execute format('create trigger trg_stage12_append_only before update or delete on %I for each row execute function prevent_stage12_append_only_mutation()',r);
  end loop;
end $$;

insert into schema_contract_authority(contract_id,contract_family,authority_note,created_at,schema_version)
values('stage12-governed-learning-fast-path-v1','STAGE12_GOVERNED_LEARNING_FAST_PATH',
'MEMORY_MAY_RECOMMEND_BUT_NEVER_AUTHORIZE; LEGACY_PHASE11_DIRECT_RUNTIME_RETIRED; FAST_PATH_REQUIRES_REPLAY_THEN_SHADOW_THEN_HUMAN_CERTIFICATION; RUNTIME_HINT_EXECUTION_AUTHORIZED_FALSE; CURRENT_WHO_CAN_WHO_MAY_WHO_SHOULD_HOW_AND_AUTHORIZATION_EPOCH_LEASE_FENCING_REMAIN_MANDATORY',now(),'V198')
on conflict(contract_id) do nothing;
