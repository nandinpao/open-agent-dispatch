-- OpenDispatch MRS v5.2.1 A0 Release / Production Foundation Cutover Gate
-- V208 is a release-governance boundary. It adds no new execution provider or network transport.
-- R7 NEW_AUTHORITATIVE without a release candidate remains controlled-live evidence only.
-- Production-bound cutover requires an ACTIVE release candidate backed by A0-R8 + Stage10 E2E evidence.

-- 1. Stage10 integrated end-to-end proof. These runs are externally supplied and append-only.
create table if not exists a0_release_cutover_scenario_catalog_v208 (
  scenario_code varchar(96) primary key,
  category varchar(48) not null,
  required boolean not null default true,
  evidence_requirement varchar(700) not null,
  live_only boolean not null default true
);

insert into a0_release_cutover_scenario_catalog_v208(scenario_code,category,required,evidence_requirement,live_only) values
('A_EXISTING_DETERMINISTIC_GOLDEN_PATH','E2E',true,'Existing deterministic Source -> Intake -> Flow -> Capability -> Admission -> Routing -> Assignment -> Managed Agent -> ACK -> RESULT -> Finalization succeeds without legacy Dispatch regression.',true),
('B_FLOW_NO_MATCH_TRIAGE','E2E',true,'A real no-match request produces canonical NO_MATCH / WAITING triage behavior and never defaults to an arbitrary Agent.',true),
('C_IDEMPOTENCY_COLLISION_QUARANTINE','E2E',true,'Same idempotency key with semantically different payload is rejected/quarantined with security evidence and no duplicate Task.',true),
('D_LEASE_TAKEOVER_FENCING','E2E',true,'Node/worker failure causes lease takeover with a newer fencing token; stale owner cannot commit or send.',true),
('E_REMOTE_UNFENCED_WRITE_DENY','E2E',true,'REMOTE_UNFENCED non-idempotent WRITE is denied without explicit human approval and creates no remote side effect.',true)
on conflict(scenario_code) do update set category=excluded.category,required=excluded.required,evidence_requirement=excluded.evidence_requirement,live_only=excluded.live_only;

create table if not exists a0_release_cutover_runs_v208 (
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
  constraint fk_a0release_cutover_scenario foreign key(scenario_code) references a0_release_cutover_scenario_catalog_v208(scenario_code),
  constraint a0release_cutover_result_check check(result in ('PASS','FAIL','BLOCKED','NOT_RUN')),
  constraint a0release_cutover_details_object check(jsonb_typeof(details_json)='object')
);
create index if not exists idx_a0release_cutover_run on a0_release_cutover_runs_v208(tenant_id,scenario_code,executed_at desc);

create or replace view a0_release_latest_cutover_result_v208 as
select distinct on(tenant_id,scenario_code)
       tenant_id,scenario_code,result,environment_ref,evidence_ref,details_json,executed_by,executed_at
  from a0_release_cutover_runs_v208
 order by tenant_id,scenario_code,executed_at desc,run_id desc;

create or replace view a0_release_cutover_gate_v208 as
select t.tenant_id,
       count(*) filter(where c.required) as required_count,
       count(*) filter(where c.required and r.result='PASS') as passed_count,
       count(*) filter(where c.required and coalesce(r.result,'NOT_RUN')<>'PASS') as blocking_count,
       case when count(*) filter(where c.required and coalesce(r.result,'NOT_RUN')<>'PASS')=0 then 'PASS' else 'NOT_CERTIFIED' end::varchar(24) as gate_status
  from (select distinct tenant_id from a0_release_cutover_runs_v208) t
 cross join a0_release_cutover_scenario_catalog_v208 c
 left join a0_release_latest_cutover_result_v208 r on r.tenant_id=t.tenant_id and r.scenario_code=c.scenario_code
 group by t.tenant_id;

-- 2. Release Candidate lifecycle. A candidate references externally generated build/source evidence;
--    DB rows never manufacture their own source/build PASS evidence.
create table if not exists a0_production_release_candidates_v208 (
  tenant_id varchar(64) not null,
  release_candidate_id varchar(180) not null,
  artifact_name varchar(512) not null,
  artifact_sha256 varchar(64) not null,
  product_snapshot varchar(64) not null,
  migration_version varchar(32) not null,
  source_gate_status varchar(24) not null,
  source_gate_ref varchar(1024) not null,
  build_status varchar(24) not null,
  build_ref varchar(1024) not null,
  environment_ref varchar(255) not null,
  certification_evidence_ref varchar(1024),
  status varchar(24) not null default 'DRAFT',
  r8_gate_status_snapshot varchar(24),
  r8_required_count int,
  r8_passed_count int,
  cutover_gate_status_snapshot varchar(24),
  cutover_required_count int,
  cutover_passed_count int,
  certification_reason text,
  created_by varchar(255) not null,
  created_at timestamptz not null default now(),
  certified_by varchar(255),
  certified_at timestamptz,
  activated_by varchar(255),
  activated_at timestamptz,
  revoked_by varchar(255),
  revoked_at timestamptz,
  revoke_reason text,
  primary key(tenant_id,release_candidate_id),
  constraint a0release_candidate_sha256 check(artifact_sha256 ~ '^[0-9a-f]{64}$'),
  constraint a0release_candidate_source_gate_check check(source_gate_status in ('PASS','FAIL','NOT_RUN')),
  constraint a0release_candidate_build_check check(build_status in ('PASS','FAIL','NOT_RUN')),
  constraint a0release_candidate_status_check check(status in ('DRAFT','CERTIFIED','ACTIVE','REVOKED')),
  constraint a0release_candidate_migration_check check(migration_version='V208')
);
create unique index if not exists ux_a0release_one_active_candidate on a0_production_release_candidates_v208(tenant_id) where status='ACTIVE';
create index if not exists idx_a0release_candidate_status on a0_production_release_candidates_v208(tenant_id,status,created_at desc);

create table if not exists a0_production_release_candidate_events_v208 (
  tenant_id varchar(64) not null,
  event_id varchar(180) not null,
  release_candidate_id varchar(180) not null,
  from_status varchar(24),
  to_status varchar(24) not null,
  reason_code varchar(160) not null,
  actor_ref varchar(255) not null,
  evidence_json jsonb not null default '{}'::jsonb,
  occurred_at timestamptz not null default now(),
  primary key(tenant_id,event_id),
  constraint fk_a0release_candidate_event foreign key(tenant_id,release_candidate_id) references a0_production_release_candidates_v208(tenant_id,release_candidate_id),
  constraint a0release_candidate_event_json check(jsonb_typeof(evidence_json)='object')
);
create index if not exists idx_a0release_candidate_events on a0_production_release_candidate_events_v208(tenant_id,release_candidate_id,occurred_at);

-- 3. Bind only production-promoted Flow states to a release candidate. Null means R7 controlled-live,
--    never Production Foundation authority.
alter table flow_routing_migration_state add column if not exists production_release_candidate_id varchar(180);
alter table flow_routing_migration_events add column if not exists production_release_candidate_id varchar(180);
create index if not exists idx_flow_routing_release_candidate on flow_routing_migration_state(tenant_id,production_release_candidate_id) where production_release_candidate_id is not null;

-- 4. Gate projections.
create or replace view a0_production_foundation_gate_v208 as
with tenants as (
  select tenant_id from a0_r8_acceptance_runs_v207
  union select tenant_id from a0_release_cutover_runs_v208
  union select tenant_id from a0_production_release_candidates_v208
  union select tenant_id from flow_routing_migration_state
), flow_health as (
  select tenant_id,
         count(*) filter(where migration_state='NEW_AUTHORITATIVE' and production_release_candidate_id is null) as controlled_live_unbound_flows,
         count(*) filter(where migration_state='NEW_AUTHORITATIVE' and production_release_candidate_id is not null) as production_bound_flows,
         count(*) filter(where migration_state='NEW_AUTHORITATIVE' and production_release_candidate_id is not null and not exists(
           select 1 from a0_production_release_candidates_v208 c where c.tenant_id=flow_routing_migration_state.tenant_id and c.release_candidate_id=flow_routing_migration_state.production_release_candidate_id and c.status='ACTIVE'
         )) as invalid_production_bound_flows
    from flow_routing_migration_state group by tenant_id
), active_candidate as (
  select tenant_id,release_candidate_id,artifact_sha256,environment_ref,activated_at
    from a0_production_release_candidates_v208 where status='ACTIVE'
)
select t.tenant_id,
       coalesce(r.gate_status,'NOT_CERTIFIED') as r8_gate_status,
       coalesce(r.required_count,20) as r8_required_count,
       coalesce(r.passed_count,0) as r8_passed_count,
       coalesce(r.blocking_count,20) as r8_blocking_count,
       coalesce(c.gate_status,'NOT_CERTIFIED') as cutover_gate_status,
       coalesce(c.required_count,5) as cutover_required_count,
       coalesce(c.passed_count,0) as cutover_passed_count,
       coalesce(c.blocking_count,5) as cutover_blocking_count,
       a.release_candidate_id as active_release_candidate_id,
       a.artifact_sha256 as active_artifact_sha256,
       a.environment_ref as active_environment_ref,
       a.activated_at as active_candidate_activated_at,
       coalesce(f.controlled_live_unbound_flows,0) as controlled_live_unbound_flows,
       coalesce(f.production_bound_flows,0) as production_bound_flows,
       coalesce(f.invalid_production_bound_flows,0) as invalid_production_bound_flows,
       case
         when coalesce(r.gate_status,'NOT_CERTIFIED')='PASS'
          and coalesce(c.gate_status,'NOT_CERTIFIED')='PASS'
          and a.release_candidate_id is not null
          and coalesce(f.invalid_production_bound_flows,0)=0
         then 'PASS' else 'NOT_CERTIFIED' end::varchar(24) as gate_status,
       case
         when coalesce(r.gate_status,'NOT_CERTIFIED')='PASS'
          and coalesce(c.gate_status,'NOT_CERTIFIED')='PASS'
          and a.release_candidate_id is not null
          and coalesce(f.invalid_production_bound_flows,0)=0
         then true else false end as production_foundation_ready
  from tenants t
  left join a0_r8_release_gate_v207 r on r.tenant_id=t.tenant_id
  left join a0_release_cutover_gate_v208 c on c.tenant_id=t.tenant_id
  left join active_candidate a on a.tenant_id=t.tenant_id
  left join flow_health f on f.tenant_id=t.tenant_id;

-- 5. DB lifecycle guards. Certification/activation cannot bypass R8 or Stage10 live evidence.
create or replace function a0release_guard_candidate_transition() returns trigger language plpgsql as $$
declare r8_status text; cutover_status text;
begin
  if TG_OP='INSERT' then
    if new.status<>'DRAFT' then raise exception 'A0_RELEASE_CANDIDATE_MUST_START_DRAFT'; end if;
    return new;
  end if;
  if new.tenant_id<>old.tenant_id or new.release_candidate_id<>old.release_candidate_id or new.created_at<>old.created_at then
    raise exception 'A0_RELEASE_CANDIDATE_IDENTITY_IMMUTABLE';
  end if;
  if old.status='REVOKED' and new.status<>'REVOKED' then raise exception 'A0_RELEASE_REVOKED_IS_TERMINAL'; end if;
  if old.status='DRAFT' and new.status not in ('DRAFT','CERTIFIED','REVOKED') then raise exception 'A0_RELEASE_INVALID_CANDIDATE_TRANSITION'; end if;
  if old.status='CERTIFIED' and new.status not in ('CERTIFIED','ACTIVE','REVOKED') then raise exception 'A0_RELEASE_INVALID_CANDIDATE_TRANSITION'; end if;
  if old.status='ACTIVE' and new.status not in ('ACTIVE','REVOKED') then raise exception 'A0_RELEASE_INVALID_CANDIDATE_TRANSITION'; end if;
  if new.status in ('CERTIFIED','ACTIVE') and old.status is distinct from new.status then
    select gate_status into r8_status from a0_r8_release_gate_v207 where tenant_id=new.tenant_id;
    select gate_status into cutover_status from a0_release_cutover_gate_v208 where tenant_id=new.tenant_id;
    if coalesce(r8_status,'NOT_CERTIFIED')<>'PASS' then raise exception 'A0_RELEASE_R8_GATE_NOT_PASS'; end if;
    if coalesce(cutover_status,'NOT_CERTIFIED')<>'PASS' then raise exception 'A0_RELEASE_E2E_CUTOVER_GATE_NOT_PASS'; end if;
    if new.source_gate_status<>'PASS' then raise exception 'A0_RELEASE_SOURCE_GATE_NOT_PASS'; end if;
    if new.build_status<>'PASS' then raise exception 'A0_RELEASE_BUILD_NOT_PASS'; end if;
  end if;
  return new;
end $$;
drop trigger if exists trg_a0release_candidate_transition on a0_production_release_candidates_v208;
create trigger trg_a0release_candidate_transition before insert or update on a0_production_release_candidates_v208
for each row execute function a0release_guard_candidate_transition();

create or replace function prevent_a0release_append_only_mutation() returns trigger language plpgsql as $$
begin raise exception 'A0_RELEASE_EVIDENCE_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_a0release_cutover_run_immutable on a0_release_cutover_runs_v208;
create trigger trg_a0release_cutover_run_immutable before update or delete on a0_release_cutover_runs_v208
for each row execute function prevent_a0release_append_only_mutation();
drop trigger if exists trg_a0release_candidate_event_immutable on a0_production_release_candidate_events_v208;
create trigger trg_a0release_candidate_event_immutable before update or delete on a0_production_release_candidate_events_v208
for each row execute function prevent_a0release_append_only_mutation();

create or replace function a0release_require_active_candidate_for_production_flow() returns trigger language plpgsql as $$
declare candidate_status text; gate_status text;
begin
  if new.migration_state='NEW_AUTHORITATIVE' and new.production_release_candidate_id is not null and
     (TG_OP='INSERT' or old.migration_state is distinct from 'NEW_AUTHORITATIVE' or old.production_release_candidate_id is distinct from new.production_release_candidate_id) then
    select status into candidate_status from a0_production_release_candidates_v208
      where tenant_id=new.tenant_id and release_candidate_id=new.production_release_candidate_id;
    if coalesce(candidate_status,'MISSING')<>'ACTIVE' then raise exception 'A0_RELEASE_ACTIVE_CANDIDATE_REQUIRED'; end if;
    select g.gate_status into gate_status from a0_production_foundation_gate_v208 g where g.tenant_id=new.tenant_id;
    if coalesce(gate_status,'NOT_CERTIFIED')<>'PASS' then raise exception 'A0_RELEASE_PRODUCTION_FOUNDATION_GATE_NOT_PASS'; end if;
  end if;
  if new.migration_state<>'NEW_AUTHORITATIVE' then new.production_release_candidate_id=null; end if;
  return new;
end $$;
drop trigger if exists trg_a0release_production_flow_guard on flow_routing_migration_state;
create trigger trg_a0release_production_flow_guard before insert or update on flow_routing_migration_state
for each row execute function a0release_require_active_candidate_for_production_flow();

-- 6. Tenant isolation. Scenario catalog is global; every run/candidate/event is tenant-scoped.
do $$ declare tbl text; begin
  foreach tbl in array array['a0_release_cutover_runs_v208','a0_production_release_candidates_v208','a0_production_release_candidate_events_v208'] loop
    execute format('alter table %I enable row level security',tbl);
    execute format('drop policy if exists tenant_isolation on %I',tbl);
    execute format('create policy tenant_isolation on %I using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id())',tbl);
  end loop;
end $$;

-- 7. Schema authority contract.
insert into schema_contract_authority(contract_id,contract_family,authority_note,created_at,schema_version)
values('mrs-a0-release-production-foundation-cutover-gate','MRS_A0_RELEASE_PRODUCTION_FOUNDATION',
'A0_RELEASE_GATE_ONLY: A0_R8_20_SCENARIOS_PLUS_STAGE10_5_E2E_SCENARIOS_PLUS_EXTERNAL_SOURCE_BUILD_EVIDENCE; RELEASE_CANDIDATE_LIFECYCLE_DRAFT_CERTIFIED_ACTIVE_REVOKED; PRODUCTION_FLOW_CUTOVER_REMAINS_PER_FLOW; R7_CONTROLLED_LIVE_WITHOUT_CANDIDATE_IS_NOT_PRODUCTION; NO_GLOBAL_FLOW_CUTOVER; ROLLBACK_ALWAYS_ALLOWED; NO_NEW_PROVIDER_OR_NETWORK_AUTHORITY',now(),'V208')
on conflict(contract_id) do nothing;

-- 8. Human Admin route inventory is INSTANCE-scoped FORCE-RLS governance data.
select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','a0-release-v208-migration',true);
insert into permission_entry_point_inventory(
 entry_point_id,entry_point_type,application_id,owner_module,display_name,route_pattern,http_method,authority_state,
 target_permission_code,legacy_authority_type,legacy_authorities,resource_type,resource_resolver_id,exemption_reason,
 migration_deadline,manifest_revision,source_ref,source_hash,last_verified_at,created_by,updated_by)
values
('REST:GET:/admin/production-foundation/summary','REST','control-plane-app','control-plane-app','ProductionFoundationReleaseController.summary','/admin/production-foundation/summary','GET','TARGET_ONLY','api.governance.contract.evidence',null,'[]'::jsonb,'GOVERNANCE_CONTRACT','R3_TENANT_RESOURCE_RESOLVER',null,null,'a0-release-v208-2026-08-25','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/ProductionFoundationReleaseController.java#summary','8f7c00be7542c90b304014b798e081fab5e5eab3c4fbe3ea8c803c335549ec69',now(),'a0-release-v208','a0-release-v208'),
('REST:POST:/admin/production-foundation/cutover-runs','REST','control-plane-app','control-plane-app','ProductionFoundationReleaseController.recordRun','/admin/production-foundation/cutover-runs','POST','TARGET_ONLY','admin.agent.assignment.upsert.capability',null,'[]'::jsonb,'GOVERNANCE_CONTRACT','R3_TENANT_RESOURCE_RESOLVER',null,null,'a0-release-v208-2026-08-25','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/ProductionFoundationReleaseController.java#recordRun','8f7c00be7542c90b304014b798e081fab5e5eab3c4fbe3ea8c803c335549ec69',now(),'a0-release-v208','a0-release-v208'),
('REST:POST:/admin/production-foundation/candidates','REST','control-plane-app','control-plane-app','ProductionFoundationReleaseController.createCandidate','/admin/production-foundation/candidates','POST','TARGET_ONLY','admin.agent.assignment.upsert.capability',null,'[]'::jsonb,'GOVERNANCE_CONTRACT','R3_TENANT_RESOURCE_RESOLVER',null,null,'a0-release-v208-2026-08-25','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/ProductionFoundationReleaseController.java#createCandidate','8f7c00be7542c90b304014b798e081fab5e5eab3c4fbe3ea8c803c335549ec69',now(),'a0-release-v208','a0-release-v208'),
('REST:POST:/admin/production-foundation/candidates/{candidateId}/certify','REST','control-plane-app','control-plane-app','ProductionFoundationReleaseController.certifyCandidate','/admin/production-foundation/candidates/{candidateId}/certify','POST','TARGET_ONLY','admin.agent.assignment.upsert.capability',null,'[]'::jsonb,'GOVERNANCE_CONTRACT','R3_TENANT_RESOURCE_RESOLVER',null,null,'a0-release-v208-2026-08-25','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/ProductionFoundationReleaseController.java#certifyCandidate','8f7c00be7542c90b304014b798e081fab5e5eab3c4fbe3ea8c803c335549ec69',now(),'a0-release-v208','a0-release-v208'),
('REST:POST:/admin/production-foundation/candidates/{candidateId}/activate','REST','control-plane-app','control-plane-app','ProductionFoundationReleaseController.activateCandidate','/admin/production-foundation/candidates/{candidateId}/activate','POST','TARGET_ONLY','admin.agent.assignment.upsert.capability',null,'[]'::jsonb,'GOVERNANCE_CONTRACT','R3_TENANT_RESOURCE_RESOLVER',null,null,'a0-release-v208-2026-08-25','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/ProductionFoundationReleaseController.java#activateCandidate','8f7c00be7542c90b304014b798e081fab5e5eab3c4fbe3ea8c803c335549ec69',now(),'a0-release-v208','a0-release-v208'),
('REST:POST:/admin/production-foundation/candidates/{candidateId}/revoke','REST','control-plane-app','control-plane-app','ProductionFoundationReleaseController.revokeCandidate','/admin/production-foundation/candidates/{candidateId}/revoke','POST','TARGET_ONLY','admin.agent.assignment.upsert.capability',null,'[]'::jsonb,'GOVERNANCE_CONTRACT','R3_TENANT_RESOURCE_RESOLVER',null,null,'a0-release-v208-2026-08-25','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/ProductionFoundationReleaseController.java#revokeCandidate','8f7c00be7542c90b304014b798e081fab5e5eab3c4fbe3ea8c803c335549ec69',now(),'a0-release-v208','a0-release-v208'),
('REST:PUT:/admin/production-foundation/flows/{flowId}/authority','REST','control-plane-app','control-plane-app','ProductionFoundationReleaseController.setFlowAuthority','/admin/production-foundation/flows/{flowId}/authority','PUT','TARGET_ONLY','admin.agent.assignment.capabilities',null,'[]'::jsonb,'AGENT_ASSIGNMENT','R3_TENANT_RESOURCE_RESOLVER',null,null,'a0-release-v208-2026-08-25','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/ProductionFoundationReleaseController.java#setFlowAuthority','8f7c00be7542c90b304014b798e081fab5e5eab3c4fbe3ea8c803c335549ec69',now(),'a0-release-v208','a0-release-v208')
on conflict(entry_point_id) do update set display_name=excluded.display_name,route_pattern=excluded.route_pattern,http_method=excluded.http_method,
 authority_state=excluded.authority_state,target_permission_code=excluded.target_permission_code,legacy_authority_type=null,legacy_authorities='[]'::jsonb,
 resource_type=excluded.resource_type,resource_resolver_id=excluded.resource_resolver_id,manifest_revision=excluded.manifest_revision,source_ref=excluded.source_ref,
 source_hash=excluded.source_hash,last_verified_at=now(),updated_at=now(),updated_by='a0-release-v208',version=permission_entry_point_inventory.version+1;
