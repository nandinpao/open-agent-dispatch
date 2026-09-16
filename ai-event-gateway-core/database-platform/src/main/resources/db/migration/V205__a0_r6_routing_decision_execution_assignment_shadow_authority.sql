-- MRS A0-R6 — RoutingDecision + ExecutionAssignment authority cutover foundation.
-- R6 is SHADOW ONLY. It turns an A0-R5 admitted Step-scoped envelope into explainable
-- EligibilityDecision -> RoutingFeatureSnapshot -> RoutingDecision -> ExecutionAssignment evidence.
-- It does NOT write canonical task_assignments, acquire ExecutionLease/fencing, persist DispatchIntent,
-- or send Netty/A2A/MCP/network traffic. Those authorities remain outside R6.

-- -----------------------------------------------------------------------------
-- 1. EligibilityDecision: WHO CAN NOW inside the R5 maximum authorization envelope.
-- -----------------------------------------------------------------------------
create table if not exists routing_eligibility_decisions (
  tenant_id varchar(64) not null,
  decision_id varchar(180) not null,
  plan_id varchar(160) not null,
  plan_revision int not null,
  step_id varchar(160) not null,
  task_id varchar(128),
  envelope_id varchar(180) not null,
  capability_code varchar(160) not null,
  capability_version int not null,
  operation varchar(80) not null,
  routing_profile_id varchar(160) not null,
  routing_profile_version int not null,
  candidate_count int not null,
  eligible_count int not null,
  excluded_count int not null,
  result varchar(40) not null,
  exclusion_summary_json jsonb not null default '{}'::jsonb,
  candidate_evaluation_set_digest varchar(128) not null,
  policy_snapshot_ref varchar(255),
  evaluated_at timestamptz not null default now(),
  primary key(tenant_id,decision_id),
  constraint a0r6_eligibility_result_check check(result in ('ELIGIBLE_SET','NO_ELIGIBLE_CANDIDATE')),
  constraint a0r6_eligibility_count_check check(candidate_count>=0 and eligible_count>=0 and excluded_count>=0 and candidate_count=eligible_count+excluded_count),
  constraint a0r6_eligibility_summary_object check(jsonb_typeof(exclusion_summary_json)='object'),
  constraint fk_a0r6_eligibility_envelope foreign key(tenant_id,envelope_id) references binding_authorization_envelopes(tenant_id,envelope_id),
  constraint fk_a0r6_eligibility_profile foreign key(tenant_id,routing_profile_id,routing_profile_version) references routing_profile_versions(tenant_id,profile_id,version)
);
create index if not exists idx_a0r6_eligibility_step on routing_eligibility_decisions(tenant_id,plan_id,plan_revision,step_id,evaluated_at desc);

create table if not exists routing_eligibility_candidate_evaluations (
  tenant_id varchar(64) not null,
  evaluation_id varchar(180) not null,
  decision_id varchar(180) not null,
  binding_id varchar(160) not null,
  provider_id varchar(160) not null,
  provider_type varchar(40) not null,
  binding_class varchar(40) not null,
  agent_pool_id varchar(128),
  observation_id varchar(160),
  result varchar(24) not null,
  reason_codes_json jsonb not null default '[]'::jsonb,
  score_dimensions_json jsonb not null default '{}'::jsonb,
  normalized_weighted_score numeric(18,6),
  evaluated_at timestamptz not null default now(),
  primary key(tenant_id,evaluation_id),
  constraint fk_a0r6_candidate_decision foreign key(tenant_id,decision_id) references routing_eligibility_decisions(tenant_id,decision_id) on delete cascade,
  constraint fk_a0r6_candidate_binding foreign key(tenant_id,binding_id) references capability_bindings(tenant_id,binding_id),
  constraint a0r6_candidate_result_check check(result in ('ELIGIBLE','EXCLUDED')),
  constraint a0r6_candidate_reasons_array check(jsonb_typeof(reason_codes_json)='array'),
  constraint a0r6_candidate_scores_object check(jsonb_typeof(score_dimensions_json)='object')
);
create index if not exists idx_a0r6_candidate_decision on routing_eligibility_candidate_evaluations(tenant_id,decision_id,result,binding_id);

-- -----------------------------------------------------------------------------
-- 2. RoutingFeatureSnapshot: immutable inputs needed to replay a RoutingDecision.
-- -----------------------------------------------------------------------------
create table if not exists routing_feature_snapshots (
  tenant_id varchar(64) not null,
  snapshot_id varchar(180) not null,
  eligibility_decision_id varchar(180) not null,
  candidate_catalog_version varchar(160) not null,
  metric_as_of timestamptz not null,
  quality_model_version varchar(160) not null,
  pool_health_revision varchar(160) not null,
  availability_snapshot_json jsonb not null default '{}'::jsonb,
  load_snapshot_json jsonb not null default '{}'::jsonb,
  pricing_version varchar(160) not null,
  routing_profile_id varchar(160) not null,
  routing_profile_version int not null,
  scoring_config_version varchar(160) not null,
  captured_at timestamptz not null default now(),
  primary key(tenant_id,snapshot_id),
  constraint fk_a0r6_feature_eligibility foreign key(tenant_id,eligibility_decision_id) references routing_eligibility_decisions(tenant_id,decision_id),
  constraint a0r6_feature_availability_object check(jsonb_typeof(availability_snapshot_json)='object'),
  constraint a0r6_feature_load_object check(jsonb_typeof(load_snapshot_json)='object')
);
create index if not exists idx_a0r6_feature_eligibility on routing_feature_snapshots(tenant_id,eligibility_decision_id,captured_at desc);

-- Extend the existing Phase 4 RoutingDecision family without rewriting historical rows.
alter table provider_routing_decisions add column if not exists plan_id varchar(160);
alter table provider_routing_decisions add column if not exists plan_revision int;
alter table provider_routing_decisions add column if not exists step_id varchar(160);
alter table provider_routing_decisions add column if not exists binding_authorization_envelope_id varchar(180);
alter table provider_routing_decisions add column if not exists eligibility_decision_id varchar(180);
alter table provider_routing_decisions add column if not exists routing_feature_snapshot_ref varchar(180);
alter table provider_routing_decisions add column if not exists selected_agent_pool_id varchar(128);
alter table provider_routing_decisions add column if not exists authority_mode varchar(32);
alter table provider_routing_decisions drop constraint if exists a0r6_routing_authority_mode_check;
alter table provider_routing_decisions add constraint a0r6_routing_authority_mode_check
  check(authority_mode is null or authority_mode in ('LEGACY_PREVIEW','SHADOW'));
comment on column provider_routing_decisions.authority_mode is 'A0-R6 forbids NEW_AUTHORITATIVE. SHADOW decisions are evidence only and cannot dispatch.';

-- -----------------------------------------------------------------------------
-- 3. RebindingAdmission: explicit classification of fallback authority.
-- -----------------------------------------------------------------------------
create table if not exists rebinding_admission_decisions (
  tenant_id varchar(64) not null,
  decision_id varchar(180) not null,
  plan_id varchar(160) not null,
  plan_revision int not null,
  step_id varchar(160) not null,
  envelope_id varchar(180) not null,
  requested_binding_id varchar(160) not null,
  requested_binding_class varchar(40) not null,
  side_effect varchar(16) not null,
  write_semantics varchar(24),
  result varchar(48) not null,
  reason_codes_json jsonb not null default '[]'::jsonb,
  evaluated_at timestamptz not null default now(),
  primary key(tenant_id,decision_id),
  constraint fk_a0r6_rebinding_envelope foreign key(tenant_id,envelope_id) references binding_authorization_envelopes(tenant_id,envelope_id),
  constraint a0r6_rebinding_result_check check(result in ('FAST_FALLBACK_ALLOWED','REBINDING_ADMISSION_REQUIRED','FULL_PLAN_ADMISSION_REQUIRED','DENIED')),
  constraint a0r6_rebinding_side_effect_check check(side_effect in ('NONE','READ','WRITE')),
  constraint a0r6_rebinding_reasons_array check(jsonb_typeof(reason_codes_json)='array')
);
create index if not exists idx_a0r6_rebinding_step on rebinding_admission_decisions(tenant_id,plan_id,plan_revision,step_id,evaluated_at desc);

-- -----------------------------------------------------------------------------
-- 4. ExecutionAssignment SHADOW evidence. No Lease/Fencing/DispatchIntent in R6.
-- -----------------------------------------------------------------------------
create table if not exists execution_assignment_shadows (
  tenant_id varchar(64) not null,
  assignment_id varchar(180) not null,
  task_id varchar(128),
  plan_id varchar(160) not null,
  plan_revision int not null,
  step_id varchar(160) not null,
  routing_decision_id varchar(160) not null,
  binding_id varchar(160) not null,
  provider_id varchar(160) not null,
  agent_pool_id varchar(128),
  selected_agent_id varchar(128),
  selected_session_id varchar(160),
  selected_peer_interface_id varchar(160),
  selected_mcp_server_id varchar(160),
  selected_adapter_id varchar(160),
  assignment_reason varchar(512) not null,
  runtime_load_snapshot_json jsonb not null default '{}'::jsonb,
  attempt_number int not null default 1,
  previous_assignment_id varchar(180),
  authority_mode varchar(32) not null default 'SHADOW_ONLY',
  side_effect_allowed boolean not null default false,
  assigned_at timestamptz not null default now(),
  released_at timestamptz,
  outcome varchar(40) not null default 'SHADOW_EVALUATED',
  primary key(tenant_id,assignment_id),
  constraint fk_a0r6_shadow_routing foreign key(tenant_id,routing_decision_id) references provider_routing_decisions(tenant_id,decision_id),
  constraint fk_a0r6_shadow_binding foreign key(tenant_id,binding_id) references capability_bindings(tenant_id,binding_id),
  constraint a0r6_shadow_authority_check check(authority_mode='SHADOW_ONLY'),
  constraint a0r6_shadow_side_effect_check check(side_effect_allowed=false),
  constraint a0r6_shadow_attempt_check check(attempt_number>=1),
  constraint a0r6_shadow_load_object check(jsonb_typeof(runtime_load_snapshot_json)='object')
);
create index if not exists idx_a0r6_shadow_assignment_step on execution_assignment_shadows(tenant_id,plan_id,plan_revision,step_id,assigned_at desc);

-- -----------------------------------------------------------------------------
-- 5. Per-Flow migration state. NEW_AUTHORITATIVE is intentionally impossible in R6.
-- -----------------------------------------------------------------------------
create table if not exists flow_routing_migration_state (
  tenant_id varchar(64) not null,
  flow_id varchar(128) not null,
  migration_state varchar(40) not null default 'LEGACY_AUTHORITATIVE',
  version int not null default 1,
  change_reason varchar(512) not null default 'R6_BASELINE',
  changed_by varchar(255) not null default 'V205',
  changed_at timestamptz not null default now(),
  primary key(tenant_id,flow_id),
  constraint a0r6_flow_migration_state_check check(migration_state in ('LEGACY_AUTHORITATIVE','SHADOW')),
  constraint a0r6_flow_migration_version_check check(version>=1)
);
create table if not exists flow_routing_migration_events (
  tenant_id varchar(64) not null,
  event_id varchar(180) not null,
  flow_id varchar(128) not null,
  from_state varchar(40),
  to_state varchar(40) not null,
  version int not null,
  reason varchar(512) not null,
  actor_ref varchar(255) not null,
  occurred_at timestamptz not null default now(),
  primary key(tenant_id,event_id),
  constraint a0r6_flow_event_state_check check(to_state in ('LEGACY_AUTHORITATIVE','SHADOW'))
);
create index if not exists idx_a0r6_flow_migration_events on flow_routing_migration_events(tenant_id,flow_id,occurred_at desc);

-- -----------------------------------------------------------------------------
-- 6. Tenant isolation + immutable evidence.
-- -----------------------------------------------------------------------------
DO $$
DECLARE tbl text;
BEGIN
  FOREACH tbl IN ARRAY ARRAY[
    'routing_eligibility_decisions','routing_eligibility_candidate_evaluations','routing_feature_snapshots',
    'rebinding_admission_decisions','execution_assignment_shadows','flow_routing_migration_state','flow_routing_migration_events'
  ] LOOP
    EXECUTE format('alter table %I enable row level security',tbl);
    EXECUTE format('drop policy if exists tenant_isolation on %I',tbl);
    EXECUTE format('create policy tenant_isolation on %I using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id())',tbl);
  END LOOP;
END $$;

create or replace function prevent_a0r6_immutable_evidence_mutation() returns trigger language plpgsql as $$
begin raise exception 'A0_R6_EVIDENCE_IS_APPEND_ONLY'; end $$;
DO $$
DECLARE tbl text;
BEGIN
  FOREACH tbl IN ARRAY ARRAY[
    'routing_eligibility_decisions','routing_eligibility_candidate_evaluations','routing_feature_snapshots',
    'rebinding_admission_decisions','execution_assignment_shadows','flow_routing_migration_events'
  ] LOOP
    EXECUTE format('drop trigger if exists trg_a0r6_immutable on %I',tbl);
    EXECUTE format('create trigger trg_a0r6_immutable before update or delete on %I for each row execute function prevent_a0r6_immutable_evidence_mutation()',tbl);
  END LOOP;
END $$;

create or replace view routing_authority_shadow_trace_v205 as
select e.tenant_id,e.plan_id,e.plan_revision,e.step_id,e.task_id,e.envelope_id,
       e.decision_id as eligibility_decision_id,e.result as eligibility_result,e.candidate_count,e.eligible_count,e.excluded_count,
       r.decision_id as routing_decision_id,r.result as routing_result,r.selected_binding_id,r.selected_provider_id,r.selected_agent_pool_id,
       r.routing_feature_snapshot_ref,r.authority_mode,
       a.assignment_id as shadow_assignment_id,a.selected_agent_id,a.selected_session_id,a.selected_peer_interface_id,
       a.selected_mcp_server_id,a.selected_adapter_id,a.authority_mode as assignment_authority_mode,a.side_effect_allowed,
       a.assigned_at
  from routing_eligibility_decisions e
  left join provider_routing_decisions r on r.tenant_id=e.tenant_id and r.eligibility_decision_id=e.decision_id
  left join execution_assignment_shadows a on a.tenant_id=r.tenant_id and a.routing_decision_id=r.decision_id;
comment on view routing_authority_shadow_trace_v205 is 'A0-R6 explainability projection. It is read-only shadow evidence and has no network/lease/side-effect authority.';

insert into schema_contract_authority(contract_id,contract_family,authority_note,created_at,schema_version)
values('mrs-a0-r6-routing-assignment-shadow-authority','MRS_A0_R6_ROUTING_DECISION_EXECUTION_ASSIGNMENT',
'R5_ADMITTED_ENVELOPE_TO_ELIGIBILITY_TO_FEATURE_SNAPSHOT_TO_ROUTING_BINDING_POOL_TO_SHADOW_EXECUTION_ASSIGNMENT_ONLY_LEGACY_REMAINS_AUTHORITATIVE_NO_LEASE_NO_FENCING_NO_DISPATCH_INTENT_NO_NETWORK_SEND',now(),'V205')
on conflict(contract_id) do nothing;
