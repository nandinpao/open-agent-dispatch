-- SHADOW comparison evidence is append-only observation evidence and never runtime authority.
-- Phase 11: Fast Path Runtime Integration / Pattern Shadow Execution & Certification.
-- Runtime Fast Path may skip repeated semantic Triage/Planner only. Every materialized step still traverses WHO MAY -> WHO SHOULD -> HOW.

create table if not exists fast_path_runtime_policies (
  tenant_id varchar(64) not null,
  policy_id varchar(160) not null,
  display_name varchar(255) not null,
  active_fast_path_enabled boolean not null default false,
  shadow_evaluation_enabled boolean not null default true,
  emergency_kill_switch boolean not null default false,
  min_shadow_comparisons int not null default 3,
  min_shadow_plan_match_rate numeric(8,6) not null default 0.900000,
  min_shadow_capability_coverage numeric(8,6) not null default 1.000000,
  status varchar(24) not null default 'DRAFT',
  version int not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key(tenant_id,policy_id),
  constraint fast_path_runtime_policy_status_check check(status in ('DRAFT','ACTIVE','DISABLED','RETIRED')),
  constraint fast_path_runtime_policy_threshold_check check(min_shadow_comparisons>=1 and min_shadow_plan_match_rate between 0 and 1 and min_shadow_capability_coverage between 0 and 1),
  constraint fast_path_runtime_policy_version_check check(version>=1)
);
create unique index if not exists uq_fast_path_runtime_policy_active on fast_path_runtime_policies(tenant_id) where status='ACTIVE';

create table if not exists fast_path_runtime_policy_versions (
  tenant_id varchar(64) not null,
  policy_id varchar(160) not null,
  version int not null,
  snapshot_json jsonb not null,
  change_reason varchar(512) not null,
  actor_ref varchar(255) not null,
  created_at timestamptz not null default now(),
  primary key(tenant_id,policy_id,version),
  constraint fk_fast_path_runtime_policy_version foreign key(tenant_id,policy_id) references fast_path_runtime_policies(tenant_id,policy_id) on delete cascade,
  constraint fast_path_runtime_policy_snapshot_object check(jsonb_typeof(snapshot_json)='object')
);

create table if not exists fast_path_shadow_comparisons (
  tenant_id varchar(64) not null,
  comparison_id varchar(160) not null,
  pattern_id varchar(160) not null,
  pattern_version int not null,
  actual_plan_id varchar(160) not null,
  actual_plan_revision int not null,
  result varchar(32) not null,
  plan_match boolean not null,
  dependency_match boolean not null,
  capability_coverage numeric(8,6) not null,
  pattern_template_hash varchar(64) not null,
  actual_template_hash varchar(64) not null,
  reason_codes_json jsonb not null default '[]'::jsonb,
  observed_at timestamptz not null default now(),
  primary key(tenant_id,comparison_id),
  constraint fk_fast_path_shadow_pattern foreign key(tenant_id,pattern_id) references routing_patterns(tenant_id,pattern_id) on delete cascade,
  constraint fk_fast_path_shadow_plan foreign key(tenant_id,actual_plan_id) references execution_plans(tenant_id,plan_id) on delete cascade,
  constraint fast_path_shadow_result_check check(result in ('MATCHED','DIVERGED','PATTERN_STALE','NOT_SHADOW_ELIGIBLE')),
  constraint fast_path_shadow_coverage_check check(capability_coverage between 0 and 1),
  constraint fast_path_shadow_reasons_array check(jsonb_typeof(reason_codes_json)='array')
);
create unique index if not exists uq_fast_path_shadow_plan_pattern on fast_path_shadow_comparisons(tenant_id,pattern_id,pattern_version,actual_plan_id,actual_plan_revision);
create index if not exists idx_fast_path_shadow_pattern_latest on fast_path_shadow_comparisons(tenant_id,pattern_id,observed_at desc,comparison_id);

create table if not exists fast_path_runtime_certifications (
  tenant_id varchar(64) not null,
  certification_id varchar(160) not null,
  pattern_id varchar(160) not null,
  pattern_version int not null,
  runtime_policy_id varchar(160) not null,
  runtime_policy_version int not null,
  status varchar(24) not null,
  shadow_comparison_count int not null,
  shadow_plan_match_rate numeric(8,6) not null,
  shadow_capability_coverage numeric(8,6) not null,
  reason varchar(512) not null,
  actor_ref varchar(255) not null,
  certified_at timestamptz not null default now(),
  primary key(tenant_id,certification_id),
  constraint fk_fast_path_runtime_cert_pattern foreign key(tenant_id,pattern_id) references routing_patterns(tenant_id,pattern_id) on delete cascade,
  constraint fast_path_runtime_cert_status_check check(status in ('CERTIFIED','SUSPENDED','REVOKED')),
  constraint fast_path_runtime_cert_rates check(shadow_plan_match_rate between 0 and 1 and shadow_capability_coverage between 0 and 1 and shadow_comparison_count>=0)
);
create index if not exists idx_fast_path_runtime_cert_pattern on fast_path_runtime_certifications(tenant_id,pattern_id,certified_at desc,certification_id);

create table if not exists fast_path_runtime_decisions (
  tenant_id varchar(64) not null,
  decision_id varchar(160) not null,
  task_ref varchar(255) not null,
  idempotency_key varchar(255) not null,
  problem_signature varchar(128) not null,
  result varchar(48) not null,
  pattern_id varchar(160),
  pattern_version int,
  certification_id varchar(160),
  plan_id varchar(160),
  plan_revision int,
  plan_decision_id varchar(160),
  run_id varchar(160),
  reason_codes_json jsonb not null default '[]'::jsonb,
  decided_at timestamptz not null default now(),
  primary key(tenant_id,decision_id),
  constraint fast_path_runtime_decision_result_check check(result in ('FAST_PATH_RUN_STARTED','FALLBACK_ADAPTIVE_PATH','KILL_SWITCH_FALLBACK','PATTERN_NOT_CERTIFIED','PATTERN_STALE')),
  constraint fast_path_runtime_decision_reasons_array check(jsonb_typeof(reason_codes_json)='array')
);
create unique index if not exists uq_fast_path_runtime_decision_idempotency on fast_path_runtime_decisions(tenant_id,task_ref,idempotency_key);
create index if not exists idx_fast_path_runtime_decision_task on fast_path_runtime_decisions(tenant_id,task_ref,decided_at desc,decision_id);

alter table fast_path_runtime_policies enable row level security; drop policy if exists tenant_isolation on fast_path_runtime_policies; create policy tenant_isolation on fast_path_runtime_policies using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
alter table fast_path_runtime_policy_versions enable row level security; drop policy if exists tenant_isolation on fast_path_runtime_policy_versions; create policy tenant_isolation on fast_path_runtime_policy_versions using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
alter table fast_path_shadow_comparisons enable row level security; drop policy if exists tenant_isolation on fast_path_shadow_comparisons; create policy tenant_isolation on fast_path_shadow_comparisons using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
alter table fast_path_runtime_certifications enable row level security; drop policy if exists tenant_isolation on fast_path_runtime_certifications; create policy tenant_isolation on fast_path_runtime_certifications using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
alter table fast_path_runtime_decisions enable row level security; drop policy if exists tenant_isolation on fast_path_runtime_decisions; create policy tenant_isolation on fast_path_runtime_decisions using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());

create or replace function prevent_phase11_append_only_mutation() returns trigger language plpgsql as $$ begin raise exception 'PHASE11_EVIDENCE_IS_APPEND_ONLY'; end $$;
do $$ begin
  if not exists(select 1 from pg_trigger where tgname='trg_fast_path_runtime_policy_versions_append_only') then create trigger trg_fast_path_runtime_policy_versions_append_only before update or delete on fast_path_runtime_policy_versions for each row execute function prevent_phase11_append_only_mutation(); end if;
  if not exists(select 1 from pg_trigger where tgname='trg_fast_path_shadow_comparisons_append_only') then create trigger trg_fast_path_shadow_comparisons_append_only before update or delete on fast_path_shadow_comparisons for each row execute function prevent_phase11_append_only_mutation(); end if;
  if not exists(select 1 from pg_trigger where tgname='trg_fast_path_runtime_certifications_append_only') then create trigger trg_fast_path_runtime_certifications_append_only before update or delete on fast_path_runtime_certifications for each row execute function prevent_phase11_append_only_mutation(); end if;
  if not exists(select 1 from pg_trigger where tgname='trg_fast_path_runtime_decisions_append_only') then create trigger trg_fast_path_runtime_decisions_append_only before update or delete on fast_path_runtime_decisions for each row execute function prevent_phase11_append_only_mutation(); end if;
end $$;

comment on table fast_path_shadow_comparisons is 'Phase11 non-authoritative shadow evidence. It compares learned semantic plan topology with the actual adaptive plan and never dispatches or changes task outcome.';
comment on table fast_path_runtime_certifications is 'Human runtime certification of an exact Routing Pattern version after shadow evidence. Phase10 ACTIVE alone is not runtime authority.';
comment on table fast_path_runtime_decisions is 'Phase11 runtime bootstrap evidence. Fast path materializes a fresh plan/run only; every Step still requires current WHO MAY, WHO SHOULD and HOW before dispatch.';

insert into schema_contract_authority(contract_id,contract_family,authority_note,created_at,schema_version)
values('phase11-fast-path-runtime-authority','PHASE11_FAST_PATH_RUNTIME_INTEGRATION_SHADOW_CERTIFICATION','ACTIVE_PATTERN_REQUIRES_CURRENT_HUMAN_RUNTIME_CERTIFICATION_AND_KILL_SWITCH_CLEAR_SHADOW_NEVER_AFFECTS_REAL_EXECUTION_FAST_PATH_MATERIALIZES_FRESH_PLAN_AND_EVERY_STEP_REQUIRES_WHO_MAY_WHO_SHOULD_HOW',now(),'V186')
on conflict(contract_id) do update set contract_family=excluded.contract_family,authority_note=excluded.authority_note,created_at=excluded.created_at,schema_version=excluded.schema_version;
