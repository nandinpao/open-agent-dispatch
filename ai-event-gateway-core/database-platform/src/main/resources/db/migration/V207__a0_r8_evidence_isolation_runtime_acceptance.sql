-- MRS A0-R8 — Evidence / Isolation / Runtime Acceptance.
-- This stage adds no Routing/Assignment/Dispatch authority. It establishes a canonical evidence index,
-- separable payload storage, tenant-scoped digest-key metadata, runtime acceptance evidence, and a hard
-- release gate that remains NOT_CERTIFIED until every required live scenario has PASS evidence.

-- -----------------------------------------------------------------------------
-- 1. Tenant-scoped digest key metadata. Secret material is NEVER stored here.
-- -----------------------------------------------------------------------------
create table if not exists evidence_digest_key_registry_v207 (
  tenant_id varchar(64) not null,
  key_ref varchar(255) not null,
  key_version varchar(96) not null,
  digest_mode varchar(40) not null default 'HMAC_SHA256_TENANT_KEY',
  secret_backend_ref varchar(255) not null,
  status varchar(24) not null default 'ACTIVE',
  activated_at timestamptz not null default now(),
  revoked_at timestamptz,
  rotated_from_version varchar(96),
  created_by varchar(255) not null,
  created_at timestamptz not null default now(),
  primary key(tenant_id,key_ref,key_version),
  constraint a0r8_digest_mode_check check(digest_mode in ('HMAC_SHA256_TENANT_KEY')),
  constraint a0r8_digest_key_status_check check(status in ('ACTIVE','RETIRED','REVOKED')),
  constraint a0r8_digest_key_no_secret_material check(secret_backend_ref not like 'literal:%' and length(secret_backend_ref)>3)
);
create unique index if not exists uq_a0r8_active_digest_key on evidence_digest_key_registry_v207(tenant_id,key_ref) where status='ACTIVE';

-- -----------------------------------------------------------------------------
-- 2. Separable payload store. payload_handle is intentionally opaque and contains no tenant/business path.
--    Payload bytes may be disposed while the immutable ledger survives.
-- -----------------------------------------------------------------------------
create table if not exists evidence_payload_store_v207 (
  tenant_id varchar(64) not null,
  payload_handle varchar(64) not null,
  content_type varchar(160) not null,
  content_encoding varchar(48) not null default 'UTF-8',
  encryption_mode varchar(32) not null default 'NONE',
  encryption_key_ref varchar(255),
  encryption_key_version varchar(96),
  payload_bytes bytea,
  payload_size_bytes bigint not null,
  classification varchar(32) not null,
  storage_tier varchar(16) not null default 'HOT',
  disposition_state varchar(32) not null default 'ACTIVE',
  retention_policy_ref varchar(255),
  legal_hold_ref varchar(255),
  created_at timestamptz not null default now(),
  disposed_at timestamptz,
  disposition_reason varchar(512),
  primary key(tenant_id,payload_handle),
  constraint a0r8_payload_handle_opaque check(payload_handle ~ '^ep-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'),
  constraint a0r8_payload_size_check check(payload_size_bytes>=0),
  constraint a0r8_payload_encryption_mode_check check(encryption_mode in ('NONE','EXTERNAL_ENVELOPE')),
  constraint a0r8_payload_encryption_ref_check check(encryption_mode<>'EXTERNAL_ENVELOPE' or (encryption_key_ref is not null and encryption_key_version is not null)),
  constraint a0r8_sensitive_payload_encrypted_check check(classification not in ('PERSONAL','SENSITIVE','RESTRICTED','SECRET') or encryption_mode='EXTERNAL_ENVELOPE'),
  constraint a0r8_payload_classification_check check(classification in ('PUBLIC','INTERNAL','CONFIDENTIAL','PERSONAL','SENSITIVE','RESTRICTED','SECRET')),
  constraint a0r8_payload_tier_check check(storage_tier in ('HOT','WARM','COLD')),
  constraint a0r8_payload_disposition_check check(disposition_state in ('ACTIVE','ARCHIVED','DELETED','CRYPTO_SHREDDED','LEGAL_HOLD')),
  constraint a0r8_payload_disposed_content_check check(disposition_state in ('ACTIVE','ARCHIVED','LEGAL_HOLD') or payload_bytes is null)
);
create index if not exists idx_a0r8_payload_retention on evidence_payload_store_v207(tenant_id,disposition_state,storage_tier,created_at);

-- -----------------------------------------------------------------------------
-- 3. Canonical append-only execution evidence ledger.
-- -----------------------------------------------------------------------------
create table if not exists execution_evidence_ledger_v207 (
  tenant_id varchar(64) not null,
  evidence_id varchar(180) not null,
  evidence_type varchar(80) not null,
  source_family varchar(96) not null,
  source_ref varchar(512) not null,
  task_id varchar(128),
  plan_id varchar(160),
  plan_revision int,
  step_id varchar(160),
  assignment_id varchar(180),
  dispatch_intent_id varchar(180),
  decision_id varchar(180),
  policy_snapshot_ref varchar(255),
  actor_type varchar(48),
  actor_id varchar(255),
  classification varchar(32) not null default 'INTERNAL',
  digest_algorithm varchar(24),
  digest_mode varchar(40),
  canonicalization_version varchar(64),
  digest_key_ref varchar(255),
  digest_key_version varchar(96),
  payload_digest varchar(160),
  opaque_payload_handle varchar(64),
  payload_disposition_state varchar(32) not null default 'NONE',
  retention_policy_ref varchar(255),
  details_json jsonb not null default '{}'::jsonb,
  occurred_at timestamptz not null,
  recorded_at timestamptz not null default now(),
  schema_version varchar(32) not null default 'V207',
  primary key(tenant_id,evidence_id),
  unique(tenant_id,source_family,source_ref),
  constraint a0r8_evidence_classification_check check(classification in ('PUBLIC','INTERNAL','CONFIDENTIAL','PERSONAL','SENSITIVE','RESTRICTED','SECRET')),
  constraint a0r8_evidence_digest_algorithm_check check(digest_algorithm is null or digest_algorithm in ('SHA256','SHA384')),
  constraint a0r8_evidence_digest_mode_check check(digest_mode is null or digest_mode in ('SHA256_CANONICAL','HMAC_SHA256_TENANT_KEY')),
  constraint a0r8_evidence_payload_state_check check(payload_disposition_state in ('NONE','ACTIVE','ARCHIVED','DELETED','CRYPTO_SHREDDED','LEGAL_HOLD')),
  constraint a0r8_evidence_details_object check(jsonb_typeof(details_json)='object'),
  constraint a0r8_sensitive_payload_requires_hmac check(
    opaque_payload_handle is null or classification not in ('PERSONAL','SENSITIVE','RESTRICTED','SECRET') or
    (digest_mode='HMAC_SHA256_TENANT_KEY' and digest_algorithm='SHA256' and digest_key_ref is not null and digest_key_version is not null and payload_digest is not null)
  ),
  constraint a0r8_payload_digest_contract check(
    opaque_payload_handle is null or (payload_digest is not null and digest_mode is not null and digest_algorithm is not null and canonicalization_version is not null)
  ),
  constraint fk_a0r8_payload_handle foreign key(tenant_id,opaque_payload_handle) references evidence_payload_store_v207(tenant_id,payload_handle)
);
create index if not exists idx_a0r8_evidence_task on execution_evidence_ledger_v207(tenant_id,task_id,occurred_at,evidence_id) where task_id is not null;
create index if not exists idx_a0r8_evidence_assignment on execution_evidence_ledger_v207(tenant_id,assignment_id,occurred_at) where assignment_id is not null;
create index if not exists idx_a0r8_evidence_type on execution_evidence_ledger_v207(tenant_id,evidence_type,occurred_at desc);

-- -----------------------------------------------------------------------------
-- 4. Runtime acceptance evidence is append-only. It cannot self-certify.
-- -----------------------------------------------------------------------------
create table if not exists a0_r8_acceptance_scenario_catalog_v207 (
  scenario_code varchar(96) primary key,
  category varchar(48) not null,
  required boolean not null default true,
  evidence_requirement varchar(512) not null,
  source_only_allowed boolean not null default false
);

insert into a0_r8_acceptance_scenario_catalog_v207(scenario_code,category,required,evidence_requirement,source_only_allowed) values
('CROSS_TENANT_DB_RLS','ISOLATION',true,'Two non-bypass tenant contexts prove zero cross-tenant visibility and write-through.',false),
('CROSS_TENANT_CACHE_NAMESPACE','ISOLATION',true,'Tenant A cache keys/results are unreachable from Tenant B.',false),
('CROSS_TENANT_SEMANTIC_INDEX','ISOLATION',true,'Tenant A semantic index records cannot be recalled by Tenant B.',false),
('CROSS_TENANT_EVIDENCE','ISOLATION',true,'Ledger and payload handles are tenant-isolated.',false),
('CROSS_TENANT_CANDIDATE_SET','ISOLATION',true,'Eligibility/candidate evidence cannot cross tenants.',false),
('IDEMPOTENCY_KEYSPACE_ISOLATION','ISOLATION',true,'Same key in two tenants does not collide.',false),
('DIGEST_KEY_TENANT_ISOLATION','CRYPTO',true,'Tenant digest keys resolve only inside the same tenant namespace.',false),
('SENSITIVE_PAYLOAD_HMAC','CRYPTO',true,'PERSONAL+ payload evidence uses per-tenant HMAC-SHA256 with key version.',false),
('PAYLOAD_DELETE_LEDGER_SURVIVES','EVIDENCE',true,'Payload can be deleted while immutable digest/disposition evidence remains.',false),
('EVIDENCE_LEDGER_IMMUTABLE','EVIDENCE',true,'Update/delete of canonical ledger is rejected.',false),
('CRASH_BEFORE_ASSIGNMENT','RECOVERY',true,'Crash before durable assignment creates no orphan network operation.',false),
('CRASH_AFTER_INTENT_BEFORE_SEND','RECOVERY',true,'Committed intent survives restart and is safely reclaimable.',false),
('SEND_STARTED_RESPONSE_LOST','RECOVERY',true,'Unknown transport outcome becomes DELIVERY_UNKNOWN and is not blindly retried.',false),
('STALE_FENCE_AFTER_NODE_TAKEOVER','RECOVERY',true,'Old node fencing token cannot commit/send after takeover.',false),
('EVIDENCE_STORE_FINALIZATION_RETRY','FAILURE',true,'Evidence outage leaves Task FINALIZING and closes only after durable recovery.',false),
('CLEAN_INSTALL_V207','MIGRATION',true,'Clean database migrates through V207 successfully.',false),
('UPGRADE_V206_TO_V207','MIGRATION',true,'Existing V206 data upgrades to V207 without authority regression.',false),
('FLOW_RULE_INDEXED_SCALE','PERFORMANCE',true,'10K+ Flow Rule evaluation avoids full-table scan.',false),
('ROUTING_INDEXED_SCALE','PERFORMANCE',true,'100K Binding routing path avoids full-table scan.',false),
('HISTORICAL_TASK_SCALE','PERFORMANCE',true,'10M historical Task query path remains bounded/indexed.',false)
on conflict(scenario_code) do update set category=excluded.category,required=excluded.required,evidence_requirement=excluded.evidence_requirement,source_only_allowed=excluded.source_only_allowed;

create table if not exists a0_r8_acceptance_runs_v207 (
  tenant_id varchar(64) not null,
  run_id varchar(180) not null,
  scenario_code varchar(96) not null,
  result varchar(24) not null,
  environment_ref varchar(255) not null,
  evidence_ref varchar(1024) not null,
  details_json jsonb not null default '{}'::jsonb,
  executed_by varchar(255) not null,
  executed_at timestamptz not null default now(),
  primary key(tenant_id,run_id),
  constraint fk_a0r8_acceptance_scenario foreign key(scenario_code) references a0_r8_acceptance_scenario_catalog_v207(scenario_code),
  constraint a0r8_acceptance_result_check check(result in ('PASS','FAIL','BLOCKED','NOT_RUN')),
  constraint a0r8_acceptance_details_object check(jsonb_typeof(details_json)='object')
);
create index if not exists idx_a0r8_acceptance_scenario on a0_r8_acceptance_runs_v207(tenant_id,scenario_code,executed_at desc);

create or replace view a0_r8_latest_acceptance_result_v207 as
select distinct on(tenant_id,scenario_code)
       tenant_id,scenario_code,result,environment_ref,evidence_ref,details_json,executed_by,executed_at
  from a0_r8_acceptance_runs_v207
 order by tenant_id,scenario_code,executed_at desc,run_id desc;

create or replace view a0_r8_release_gate_v207 as
select t.tenant_id,
       count(*) filter(where c.required) as required_count,
       count(*) filter(where c.required and r.result='PASS') as passed_count,
       count(*) filter(where c.required and coalesce(r.result,'NOT_RUN')<>'PASS') as blocking_count,
       case when count(*) filter(where c.required and coalesce(r.result,'NOT_RUN')<>'PASS')=0 then 'PASS' else 'NOT_CERTIFIED' end::varchar(24) as gate_status
  from (select distinct tenant_id from a0_r8_acceptance_runs_v207) t
 cross join a0_r8_acceptance_scenario_catalog_v207 c
 left join a0_r8_latest_acceptance_result_v207 r on r.tenant_id=t.tenant_id and r.scenario_code=c.scenario_code
 group by t.tenant_id;

-- Dedicated tenant-isolation probe used only by the live acceptance script.
create table if not exists a0_r8_tenant_isolation_probe_v207 (
  tenant_id varchar(64) not null,
  probe_id varchar(180) not null,
  marker varchar(255) not null,
  created_at timestamptz not null default now(),
  primary key(tenant_id,probe_id)
);

-- -----------------------------------------------------------------------------
-- 5. Row-level tenant isolation on every A0-R8 tenant-scoped object.
-- -----------------------------------------------------------------------------
do $$ declare t text; begin
  foreach t in array array[
    'evidence_digest_key_registry_v207','evidence_payload_store_v207','execution_evidence_ledger_v207',
    'a0_r8_acceptance_runs_v207','a0_r8_tenant_isolation_probe_v207'
  ] loop
    execute format('alter table %I enable row level security',t);
    execute format('drop policy if exists tenant_isolation on %I',t);
    execute format('create policy tenant_isolation on %I using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id())',t);
  end loop;
end $$;

-- -----------------------------------------------------------------------------
-- 6. Append-only / disposition transition guards.
-- -----------------------------------------------------------------------------
create or replace function prevent_a0r8_evidence_ledger_mutation() returns trigger language plpgsql as $$
begin raise exception 'A0_R8_EXECUTION_EVIDENCE_LEDGER_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_a0r8_evidence_ledger_immutable on execution_evidence_ledger_v207;
create trigger trg_a0r8_evidence_ledger_immutable before update or delete on execution_evidence_ledger_v207
for each row execute function prevent_a0r8_evidence_ledger_mutation();

create or replace function prevent_a0r8_acceptance_run_mutation() returns trigger language plpgsql as $$
begin raise exception 'A0_R8_ACCEPTANCE_RUN_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_a0r8_acceptance_run_immutable on a0_r8_acceptance_runs_v207;
create trigger trg_a0r8_acceptance_run_immutable before update or delete on a0_r8_acceptance_runs_v207
for each row execute function prevent_a0r8_acceptance_run_mutation();

create or replace function a0r8_guard_payload_transition() returns trigger language plpgsql as $$
begin
  if new.tenant_id<>old.tenant_id or new.payload_handle<>old.payload_handle or new.created_at<>old.created_at then
    raise exception 'A0_R8_PAYLOAD_IDENTITY_IS_IMMUTABLE';
  end if;
  if old.disposition_state in ('DELETED','CRYPTO_SHREDDED') then
    raise exception 'A0_R8_DISPOSED_PAYLOAD_IS_TERMINAL';
  end if;
  if old.payload_bytes is null and new.payload_bytes is not null then
    raise exception 'A0_R8_PAYLOAD_CONTENT_CANNOT_BE_RESTORED';
  end if;
  if old.payload_bytes is not null and new.payload_bytes is not null and old.payload_bytes<>new.payload_bytes then
    raise exception 'A0_R8_PAYLOAD_CONTENT_IS_IMMUTABLE';
  end if;
  return new;
end $$;
drop trigger if exists trg_a0r8_payload_transition_guard on evidence_payload_store_v207;
create trigger trg_a0r8_payload_transition_guard before update on evidence_payload_store_v207
for each row execute function a0r8_guard_payload_transition();

-- -----------------------------------------------------------------------------
-- 7. Canonical read surface: new ledger plus authoritative structural evidence from R3-R7.
--    Structural rows do not duplicate raw payloads.
-- -----------------------------------------------------------------------------
create or replace view canonical_execution_evidence_projection_v207 as
select l.tenant_id,l.evidence_id,l.evidence_type,l.source_family,l.source_ref,l.task_id,l.plan_id,l.plan_revision,l.step_id,
       l.assignment_id,l.dispatch_intent_id,l.decision_id,l.policy_snapshot_ref,l.classification,l.payload_digest,
       l.opaque_payload_handle,l.payload_disposition_state,l.details_json,l.occurred_at,l.recorded_at,l.schema_version
  from execution_evidence_ledger_v207 l
union all
select f.tenant_id,'struct-flow-'||f.decision_id,'FLOW_MATCH_DECISION','FLOW_MATCH',f.decision_id,f.task_id,null,null,null,
       null,null,f.decision_id,null,'INTERNAL',null,null,'NONE',
       jsonb_build_object('matchResult',f.match_result,'flowId',f.flow_id,'ruleId',f.matched_rule_id,'serviceCode',f.output_service_code),f.decided_at,f.decided_at,'V207_PROJECTION'
  from flow_match_decisions f
union all
select d.tenant_id,'struct-admission-'||d.decision_id,'PLAN_ADMISSION_DECISION','PLAN_ADMISSION',d.decision_id,null,d.plan_id,d.plan_revision,null,
       null,null,d.decision_id,d.policy_snapshot_ref,'INTERNAL',null,null,'NONE',
       jsonb_build_object('result',d.result,'stepCount',d.step_count,'admittedSteps',d.admitted_step_count,'deniedSteps',d.denied_step_count),d.decided_at,d.decided_at,'V207_PROJECTION'
  from plan_admission_decisions d
union all
select r.tenant_id,'struct-routing-'||r.decision_id,'ROUTING_DECISION','ROUTING',r.decision_id,null,r.plan_id,r.plan_revision,r.step_id,
       null,null,r.decision_id,null,'INTERNAL',null,null,'NONE',
       jsonb_build_object('result',r.result,'selectedBindingId',r.selected_binding_id,'selectedAgentPoolId',r.selected_agent_pool_id,'authorityMode',r.authority_mode,'featureSnapshotRef',r.routing_feature_snapshot_ref),r.evaluated_at,r.evaluated_at,'V207_PROJECTION'
  from provider_routing_decisions r
 where r.plan_id is not null
union all
select a.tenant_id,'struct-assignment-'||a.assignment_id,'EXECUTION_ASSIGNMENT','EXECUTION_ASSIGNMENT',a.assignment_id,a.task_id,a.plan_id,a.plan_revision,a.step_id,
       a.assignment_id,null,a.routing_decision_id,null,'INTERNAL',null,null,'NONE',
       jsonb_build_object('bindingId',a.binding_id,'providerType',a.provider_type,'providerId',a.provider_id,'safetyMode',a.execution_safety_mode,'fencingToken',a.fencing_token,'status',a.status),a.created_at,a.created_at,'V207_PROJECTION'
  from execution_assignments_v206 a
union all
select e.tenant_id,'struct-intent-event-'||e.event_id,'DISPATCH_INTENT_EVENT','DISPATCH_INTENT',e.event_id,null,null,null,null,
       e.assignment_id,e.intent_id,null,null,'INTERNAL',null,null,'NONE',
       jsonb_build_object('fromStatus',e.from_status,'toStatus',e.to_status,'reasonCode',e.reason_code,'actorRef',e.actor_ref,'evidence',e.evidence_json),e.occurred_at,e.occurred_at,'V207_PROJECTION'
  from execution_dispatch_intent_events_v206 e;

-- -----------------------------------------------------------------------------
-- 8. Schema authority: A0-R8 is evidence/isolation/certification only.
-- -----------------------------------------------------------------------------
insert into schema_contract_authority(contract_id,contract_family,authority_note,created_at,schema_version)
values('mrs-a0-r8-evidence-isolation-runtime-acceptance','MRS_A0_R8_EVIDENCE_ISOLATION_RUNTIME_ACCEPTANCE',
'CANONICAL_EVIDENCE_INDEX_PLUS_SEPARABLE_PAYLOAD_STORE; SENSITIVE_PAYLOAD_REQUIRES_TENANT_HMAC_METADATA; ALL_A0_R8_TABLES_RLS; ACCEPTANCE_RESULTS_APPEND_ONLY; RELEASE_GATE_DEFAULTS_NOT_CERTIFIED_UNTIL_EVERY_REQUIRED_LIVE_SCENARIO_PASSES; NO_NEW_ROUTING_ASSIGNMENT_DISPATCH_OR_NETWORK_AUTHORITY',now(),'V207')
on conflict(contract_id) do nothing;
