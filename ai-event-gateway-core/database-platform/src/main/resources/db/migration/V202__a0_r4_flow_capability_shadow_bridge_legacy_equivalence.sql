-- MRS A0-R4 — Flow -> Capability Shadow Bridge / Legacy Equivalence
--
-- Contract:
--   * LEGACY_FLOW_DIRECT remains the only runtime execution authority.
--   * A0-R4 proves that existing flow_agent_assignments can be represented by
--     Canonical Capability + DIRECT_AGENT compatibility bindings without changing
--     which executor actually receives production work.
--   * No A0-R4 table, trigger, API or worker may create TaskAssignment,
--     DispatchRequest, invoke an execution adapter, or enable side effects.
--   * A0-R3 FlowMatchDecision is the classification/capability input authority.

-- -----------------------------------------------------------------------------
-- 1. Extend the per-Flow migration state with an explicit compatibility-readiness
--    state. Runtime authority remains hard-constrained to LEGACY_FLOW_DIRECT.
-- -----------------------------------------------------------------------------
alter table flow_capability_migration_states
  drop constraint if exists flow_capability_migration_state_check;
alter table flow_capability_migration_states
  add constraint flow_capability_migration_state_check
  check (migration_state in (
    'LEGACY_DIRECT','SHADOW_EVALUATION','CAPABILITY_READY',
    'READY_FOR_COMPAT_CUTOVER','ROLLED_BACK'));

alter table flow_capability_migration_states
  drop constraint if exists flow_capability_shadow_state_check;
alter table flow_capability_migration_states
  add constraint flow_capability_shadow_state_check
  check ((migration_state='SHADOW_EVALUATION' and shadow_enabled=true)
     or (migration_state='READY_FOR_COMPAT_CUTOVER' and shadow_enabled=true)
     or migration_state='CAPABILITY_READY'
     or (migration_state in ('LEGACY_DIRECT','ROLLED_BACK') and shadow_enabled=false));

alter table flow_capability_migration_states
  add column if not exists compatibility_bridge_revision bigint not null default 0;
alter table flow_capability_migration_states
  add column if not exists compatibility_bridge_source_revision timestamptz;
alter table flow_capability_migration_states
  add column if not exists minimum_equivalence_samples int not null default 10;
alter table flow_capability_migration_states
  add column if not exists required_selected_equivalence_rate numeric(7,6) not null default 1.000000;

alter table flow_capability_migration_states
  drop constraint if exists chk_a0r4_minimum_equivalence_samples;
alter table flow_capability_migration_states
  add constraint chk_a0r4_minimum_equivalence_samples
  check (minimum_equivalence_samples between 1 and 100000);
alter table flow_capability_migration_states
  drop constraint if exists chk_a0r4_required_selected_equivalence_rate;
alter table flow_capability_migration_states
  add constraint chk_a0r4_required_selected_equivalence_rate
  check (required_selected_equivalence_rate >= 0 and required_selected_equivalence_rate <= 1);

comment on column flow_capability_migration_states.compatibility_bridge_revision is
  'A0-R4 monotonic revision of the current DIRECT_AGENT compatibility bridge snapshot. It never changes runtime authority.';

-- -----------------------------------------------------------------------------
-- 2. Flow-scoped DIRECT_AGENT compatibility bindings.
--    capability_bindings remains the canonical WHO-CAN registry. This table is the
--    transitional Flow-scoped wrapper required by MRS §136.2 so an existing
--    flow_agent_assignment can be represented without broadening candidates.
-- -----------------------------------------------------------------------------
create table if not exists flow_direct_agent_compatibility_bindings (
  tenant_id varchar(64) not null,
  bridge_id varchar(180) not null,
  flow_id varchar(128) not null,
  flow_agent_assignment_id varchar(128) not null,
  event_stage varchar(32) not null default 'EXTERNAL',
  agent_id varchar(128) not null,
  legacy_skill_code varchar(160) not null,
  capability_code varchar(160),
  provider_id varchar(160),
  capability_binding_id varchar(160),
  binding_type varchar(32) not null default 'DIRECT_AGENT',
  bridge_status varchar(48) not null,
  authority_mode varchar(32) not null default 'SHADOW_ONLY',
  side_effect_allowed boolean not null default false,
  source_assignment_updated_at timestamptz,
  source_binding_updated_at timestamptz,
  bridge_revision bigint not null,
  reason_codes_json jsonb not null default '[]'::jsonb,
  refreshed_by varchar(160) not null,
  refreshed_at timestamptz not null default now(),
  primary key (tenant_id,bridge_id),
  unique (tenant_id,flow_agent_assignment_id,legacy_skill_code),
  constraint fk_a0r4_bridge_flow
    foreign key (tenant_id,flow_id) references dispatch_flows(tenant_id,flow_id)
    on update cascade on delete cascade,
  constraint fk_a0r4_bridge_flow_assignment
    foreign key (tenant_id,flow_agent_assignment_id) references flow_agent_assignments(tenant_id,id)
    on update cascade on delete cascade,
  constraint chk_a0r4_bridge_type check (binding_type='DIRECT_AGENT'),
  constraint chk_a0r4_bridge_authority check (authority_mode='SHADOW_ONLY'),
  constraint chk_a0r4_bridge_side_effect check (side_effect_allowed=false),
  constraint chk_a0r4_bridge_status check (bridge_status in (
    'READY','UNMAPPED_CAPABILITY','NO_MANAGED_PROVIDER_LINK',
    'NO_APPROVED_CAPABILITY_BINDING','LEGACY_ASSIGNMENT_INACTIVE')),
  constraint chk_a0r4_bridge_reasons_array check (jsonb_typeof(reason_codes_json)='array')
);
create index if not exists idx_a0r4_direct_bridge_flow
  on flow_direct_agent_compatibility_bindings(tenant_id,flow_id,bridge_status,agent_id,capability_code);
create index if not exists idx_a0r4_direct_bridge_agent
  on flow_direct_agent_compatibility_bindings(tenant_id,agent_id,bridge_status,capability_code);

alter table flow_direct_agent_compatibility_bindings enable row level security;
drop policy if exists tenant_isolation on flow_direct_agent_compatibility_bindings;
create policy tenant_isolation on flow_direct_agent_compatibility_bindings
  using (tenant_id=iam_current_tenant_id())
  with check (tenant_id=iam_current_tenant_id());

comment on table flow_direct_agent_compatibility_bindings is
  'A0-R4 current Flow-scoped DIRECT_AGENT compatibility bridge. It narrows to existing legacy Flow candidates only and is SHADOW_ONLY; it cannot authorize or dispatch execution.';

-- Append-only bridge refresh evidence. The mutable bridge table is only the current
-- operational snapshot; every refresh is independently auditable here.
create table if not exists flow_direct_agent_bridge_events (
  tenant_id varchar(64) not null,
  event_id varchar(180) not null,
  flow_id varchar(128) not null,
  bridge_revision bigint not null,
  bridge_snapshot_json jsonb not null,
  ready_count int not null default 0,
  blocked_count int not null default 0,
  refreshed_by varchar(160) not null,
  refreshed_at timestamptz not null default now(),
  primary key (tenant_id,event_id),
  constraint fk_a0r4_bridge_event_flow
    foreign key (tenant_id,flow_id) references dispatch_flows(tenant_id,flow_id)
    on update cascade on delete cascade,
  constraint chk_a0r4_bridge_snapshot_object check (jsonb_typeof(bridge_snapshot_json)='object')
);
create index if not exists idx_a0r4_bridge_events_flow
  on flow_direct_agent_bridge_events(tenant_id,flow_id,bridge_revision desc,refreshed_at desc);
alter table flow_direct_agent_bridge_events enable row level security;
drop policy if exists tenant_isolation on flow_direct_agent_bridge_events;
create policy tenant_isolation on flow_direct_agent_bridge_events
  using (tenant_id=iam_current_tenant_id())
  with check (tenant_id=iam_current_tenant_id());

create or replace function prevent_a0r4_bridge_event_mutation() returns trigger language plpgsql as $$
begin raise exception 'A0_R4_DIRECT_AGENT_BRIDGE_EVENT_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_a0r4_bridge_event_immutable on flow_direct_agent_bridge_events;
create trigger trg_a0r4_bridge_event_immutable
before update or delete on flow_direct_agent_bridge_events
for each row execute function prevent_a0r4_bridge_event_mutation();

-- -----------------------------------------------------------------------------
-- 3. Immutable per-Task legacy-equivalence evidence. This uses A0-R3 canonical
--    FlowMatchDecision/required capabilities and compares them to the existing
--    legacy Flow Agent candidates and actual selected executor.
-- -----------------------------------------------------------------------------
create table if not exists flow_capability_legacy_equivalence_evidence (
  tenant_id varchar(64) not null,
  evidence_id varchar(180) not null,
  task_id varchar(128) not null,
  task_version bigint not null,
  assignment_id varchar(128),
  flow_id varchar(128) not null,
  rule_id varchar(128),
  flow_match_decision_id varchar(160),
  legacy_selected_agent_id varchar(128),
  legacy_candidate_agents_json jsonb not null default '[]'::jsonb,
  required_capabilities_json jsonb not null default '[]'::jsonb,
  direct_bridge_agent_ids_json jsonb not null default '[]'::jsonb,
  direct_bridge_binding_ids_json jsonb not null default '[]'::jsonb,
  fully_representable_agent_ids_json jsonb not null default '[]'::jsonb,
  selected_executor_equivalent boolean not null default false,
  candidate_set_equivalent boolean not null default false,
  comparison_result varchar(64) not null,
  reason_codes_json jsonb not null default '[]'::jsonb,
  authority_mode varchar(40) not null default 'LEGACY_FLOW_DIRECT',
  shadow_only boolean not null default true,
  side_effect_allowed boolean not null default false,
  evaluator_version varchar(80) not null default 'MRS_A0_R4_V1',
  evaluated_by varchar(160) not null,
  evaluated_at timestamptz not null default now(),
  primary key (tenant_id,evidence_id),
  unique (tenant_id,task_id,task_version,evaluator_version),
  constraint fk_a0r4_equivalence_task
    foreign key (tenant_id,task_id) references tasks(tenant_id,task_id) on delete cascade,
  constraint fk_a0r4_equivalence_flow
    foreign key (tenant_id,flow_id) references dispatch_flows(tenant_id,flow_id) on delete cascade,
  constraint chk_a0r4_equivalence_result check (comparison_result in (
    'EXACT_LEGACY_EQUIVALENCE','SELECTED_EXECUTOR_EQUIVALENT_CANDIDATE_GAP',
    'LEGACY_SELECTED_NOT_REPRESENTABLE','LEGACY_CANDIDATE_SET_NOT_REPRESENTABLE',
    'NO_CANONICAL_FLOW_MATCH','NO_REQUIRED_CAPABILITY','NO_LEGACY_CANDIDATE',
    'NO_LEGACY_SELECTED_EXECUTOR','BRIDGE_INCOMPLETE')),
  constraint chk_a0r4_equivalence_authority check (authority_mode='LEGACY_FLOW_DIRECT'),
  constraint chk_a0r4_equivalence_shadow check (shadow_only=true),
  constraint chk_a0r4_equivalence_side_effect check (side_effect_allowed=false),
  constraint chk_a0r4_equivalence_legacy_array check (jsonb_typeof(legacy_candidate_agents_json)='array'),
  constraint chk_a0r4_equivalence_caps_array check (jsonb_typeof(required_capabilities_json)='array'),
  constraint chk_a0r4_equivalence_bridge_agents_array check (jsonb_typeof(direct_bridge_agent_ids_json)='array'),
  constraint chk_a0r4_equivalence_bridge_bindings_array check (jsonb_typeof(direct_bridge_binding_ids_json)='array'),
  constraint chk_a0r4_equivalence_repr_array check (jsonb_typeof(fully_representable_agent_ids_json)='array'),
  constraint chk_a0r4_equivalence_reasons_array check (jsonb_typeof(reason_codes_json)='array')
);
create index if not exists idx_a0r4_equivalence_flow
  on flow_capability_legacy_equivalence_evidence(tenant_id,flow_id,evaluated_at desc);
create index if not exists idx_a0r4_equivalence_result
  on flow_capability_legacy_equivalence_evidence(tenant_id,flow_id,comparison_result,evaluated_at desc);

alter table flow_capability_legacy_equivalence_evidence enable row level security;
drop policy if exists tenant_isolation on flow_capability_legacy_equivalence_evidence;
create policy tenant_isolation on flow_capability_legacy_equivalence_evidence
  using (tenant_id=iam_current_tenant_id())
  with check (tenant_id=iam_current_tenant_id());

create or replace function prevent_a0r4_equivalence_evidence_mutation() returns trigger language plpgsql as $$
begin raise exception 'A0_R4_LEGACY_EQUIVALENCE_EVIDENCE_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_a0r4_equivalence_evidence_immutable on flow_capability_legacy_equivalence_evidence;
create trigger trg_a0r4_equivalence_evidence_immutable
before update or delete on flow_capability_legacy_equivalence_evidence
for each row execute function prevent_a0r4_equivalence_evidence_mutation();

-- -----------------------------------------------------------------------------
-- 4. Durable automatic shadow-work queue. It is populated only after the legacy
--    TaskAssignment exists, so the evaluator can compare the actual selected executor.
-- -----------------------------------------------------------------------------
create table if not exists flow_capability_shadow_work_items (
  tenant_id varchar(64) not null,
  work_item_id varchar(180) not null,
  task_id varchar(128) not null,
  assignment_id varchar(128) not null,
  flow_id varchar(128) not null,
  status varchar(32) not null default 'PENDING',
  attempt_count int not null default 0,
  next_attempt_at timestamptz not null default now(),
  claimed_by varchar(160),
  claim_until timestamptz,
  last_error text,
  created_at timestamptz not null default now(),
  completed_at timestamptz,
  primary key (tenant_id,work_item_id),
  unique (tenant_id,assignment_id),
  constraint fk_a0r4_shadow_work_task
    foreign key (tenant_id,task_id) references tasks(tenant_id,task_id) on delete cascade,
  constraint fk_a0r4_shadow_work_flow
    foreign key (tenant_id,flow_id) references dispatch_flows(tenant_id,flow_id) on delete cascade,
  constraint chk_a0r4_shadow_work_status check (status in ('PENDING','RUNNING','RETRY_PENDING','COMPLETED','FAILED')),
  constraint chk_a0r4_shadow_work_attempt check (attempt_count >= 0)
);
create index if not exists idx_a0r4_shadow_work_due
  on flow_capability_shadow_work_items(tenant_id,status,next_attempt_at,created_at)
  where status in ('PENDING','RETRY_PENDING','RUNNING');

alter table flow_capability_shadow_work_items enable row level security;
drop policy if exists tenant_isolation on flow_capability_shadow_work_items;
create policy tenant_isolation on flow_capability_shadow_work_items
  using (tenant_id=iam_current_tenant_id())
  with check (tenant_id=iam_current_tenant_id());

create or replace function enqueue_a0r4_flow_capability_shadow_work() returns trigger language plpgsql as $$
declare v_tenant varchar(64); v_flow varchar(128); v_state varchar(32); v_authority varchar(40);
begin
  select t.tenant_id,coalesce(new.matched_flow_id,t.matched_flow_id)
    into v_tenant,v_flow
    from tasks t where t.task_id=new.task_id;
  if v_tenant is null or v_flow is null then return new; end if;
  select migration_state,authoritative_mode into v_state,v_authority
    from flow_capability_migration_states
   where tenant_id=v_tenant and flow_id=v_flow;
  if v_authority='LEGACY_FLOW_DIRECT' and v_state in ('SHADOW_EVALUATION','READY_FOR_COMPAT_CUTOVER','CAPABILITY_READY') then
    insert into flow_capability_shadow_work_items(
      tenant_id,work_item_id,task_id,assignment_id,flow_id,status,next_attempt_at)
    values(v_tenant,'a0r4-shadow-'||md5(v_tenant||':'||new.assignment_id),new.task_id,new.assignment_id,v_flow,'PENDING',now())
    on conflict(tenant_id,assignment_id) do nothing;
  end if;
  return new;
end $$;
drop trigger if exists trg_a0r4_enqueue_shadow_work on task_assignments;
create trigger trg_a0r4_enqueue_shadow_work
after insert on task_assignments
for each row execute function enqueue_a0r4_flow_capability_shadow_work();

-- -----------------------------------------------------------------------------
-- 5. Strict readiness read model. READY_FOR_COMPAT_CUTOVER means only that the
--    legacy execution contract can be represented canonically; it grants no routing
--    or execution authority.
-- -----------------------------------------------------------------------------
create or replace view flow_capability_legacy_equivalence_readiness_v202 as
with latest as (
  select distinct on (tenant_id,flow_id,task_id)
         tenant_id,flow_id,task_id,selected_executor_equivalent,candidate_set_equivalent,
         comparison_result,evaluated_at
    from flow_capability_legacy_equivalence_evidence
   order by tenant_id,flow_id,task_id,evaluated_at desc,evidence_id desc
), bridge as (
  select tenant_id,flow_id,
         count(*)::bigint as bridge_rows,
         count(*) filter (where bridge_status='READY')::bigint as ready_bridge_rows,
         count(*) filter (where bridge_status<>'READY')::bigint as blocked_bridge_rows
    from flow_direct_agent_compatibility_bindings
   group by tenant_id,flow_id
)
select s.tenant_id,s.flow_id,s.migration_state,s.authoritative_mode,s.shadow_enabled,
       s.compatibility_bridge_revision,s.minimum_equivalence_samples,s.required_selected_equivalence_rate,
       count(l.task_id)::bigint as sample_size,
       count(*) filter (where l.selected_executor_equivalent)::bigint as selected_executor_equivalent_count,
       count(*) filter (where l.candidate_set_equivalent)::bigint as candidate_set_equivalent_count,
       count(*) filter (where l.comparison_result='EXACT_LEGACY_EQUIVALENCE')::bigint as exact_equivalence_count,
       count(*) filter (where not l.selected_executor_equivalent)::bigint as selected_executor_gap_count,
       coalesce(b.bridge_rows,0)::bigint as bridge_rows,
       coalesce(b.ready_bridge_rows,0)::bigint as ready_bridge_rows,
       coalesce(b.blocked_bridge_rows,0)::bigint as blocked_bridge_rows,
       case when count(l.task_id)=0 then 0::numeric
            else (count(*) filter (where l.selected_executor_equivalent))::numeric / count(l.task_id)::numeric end as selected_equivalence_rate,
       max(l.evaluated_at) as last_evaluated_at
  from flow_capability_migration_states s
  left join latest l on l.tenant_id=s.tenant_id and l.flow_id=s.flow_id
  left join bridge b on b.tenant_id=s.tenant_id and b.flow_id=s.flow_id
 group by s.tenant_id,s.flow_id,s.migration_state,s.authoritative_mode,s.shadow_enabled,
          s.compatibility_bridge_revision,s.minimum_equivalence_samples,s.required_selected_equivalence_rate,
          b.bridge_rows,b.ready_bridge_rows,b.blocked_bridge_rows;

comment on view flow_capability_legacy_equivalence_readiness_v202 is
  'A0-R4 strict legacy-equivalence readiness. READY means representability only; LEGACY_FLOW_DIRECT remains runtime authority.';

-- Architecture evidence.
insert into schema_contract_authority(contract_id,contract_family,authority_note,created_at,schema_version)
values(
  'mrs-a0-r4-flow-capability-shadow-bridge-v1',
  'A0_R4_FLOW_CAPABILITY_SHADOW_BRIDGE',
  'LEGACY_FLOW_DIRECT_REMAINS_ONLY_EXECUTION_AUTHORITY; DIRECT_AGENT_COMPATIBILITY_BINDINGS_AND_EQUIVALENCE_EVIDENCE_ARE_SHADOW_ONLY_AND_CANNOT_ASSIGN_OR_DISPATCH',
  now(),
  'V202')
on conflict(contract_id) do nothing;

-- -----------------------------------------------------------------------------
-- Flyway INSTANCE governance context for FORCE-RLS IAM/catalog DML below.
-- set_config(..., true) is transaction-local; earlier tenant-data backfills remain context-neutral.
select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','a0-r4-flow-capability-shadow-migration',true);

-- 6. Human Admin fail-closed route inventory for A0-R4 migration operations.
-- -----------------------------------------------------------------------------
insert into permission_entry_point_inventory(
 entry_point_id,entry_point_type,application_id,owner_module,display_name,route_pattern,http_method,authority_state,
 target_permission_code,legacy_authority_type,legacy_authorities,resource_type,resource_resolver_id,exemption_reason,
 migration_deadline,manifest_revision,source_ref,source_hash,last_verified_at,created_by,updated_by)
values
('REST:GET:/admin/dispatch-flows/{flowId}/capability-migration/compatibility-bridge','REST','control-plane-app','control-plane-app','DispatchFlowCapabilityEquivalenceController.bridge','/admin/dispatch-flows/{flowId}/capability-migration/compatibility-bridge','GET','TARGET_ONLY','admin.dispatch.flow.readiness',null,'[]'::jsonb,'DISPATCH_FLOW','R3_PATH_RESOURCE_RESOLVER',null,null,'a0-r4-flow-capability-shadow-bridge-2026-08-24','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchFlowCapabilityEquivalenceController.java#bridge','d59d000e2c85c402a4f659a9aa618e823c79c40a641c7c164af5b584346c8bdc',now(),'a0-r4-flow-capability-shadow-bridge','a0-r4-flow-capability-shadow-bridge'),
('REST:POST:/admin/dispatch-flows/{flowId}/capability-migration/compatibility-bridge/refresh','REST','control-plane-app','control-plane-app','DispatchFlowCapabilityEquivalenceController.refreshBridge','/admin/dispatch-flows/{flowId}/capability-migration/compatibility-bridge/refresh','POST','TARGET_ONLY','admin.dispatch.flow.update',null,'[]'::jsonb,'DISPATCH_FLOW','R3_PATH_RESOURCE_RESOLVER',null,null,'a0-r4-flow-capability-shadow-bridge-2026-08-24','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchFlowCapabilityEquivalenceController.java#refreshBridge','d59d000e2c85c402a4f659a9aa618e823c79c40a641c7c164af5b584346c8bdc',now(),'a0-r4-flow-capability-shadow-bridge','a0-r4-flow-capability-shadow-bridge'),
('REST:GET:/admin/dispatch-flows/{flowId}/capability-migration/legacy-equivalence','REST','control-plane-app','control-plane-app','DispatchFlowCapabilityEquivalenceController.evidence','/admin/dispatch-flows/{flowId}/capability-migration/legacy-equivalence','GET','TARGET_ONLY','admin.dispatch.flow.readiness',null,'[]'::jsonb,'DISPATCH_FLOW','R3_PATH_RESOURCE_RESOLVER',null,null,'a0-r4-flow-capability-shadow-bridge-2026-08-24','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchFlowCapabilityEquivalenceController.java#evidence','d59d000e2c85c402a4f659a9aa618e823c79c40a641c7c164af5b584346c8bdc',now(),'a0-r4-flow-capability-shadow-bridge','a0-r4-flow-capability-shadow-bridge'),
('REST:POST:/admin/dispatch-flows/{flowId}/capability-migration/legacy-equivalence/evaluate','REST','control-plane-app','control-plane-app','DispatchFlowCapabilityEquivalenceController.evaluate','/admin/dispatch-flows/{flowId}/capability-migration/legacy-equivalence/evaluate','POST','TARGET_ONLY','admin.dispatch.flow.update',null,'[]'::jsonb,'DISPATCH_FLOW','R3_PATH_RESOURCE_RESOLVER',null,null,'a0-r4-flow-capability-shadow-bridge-2026-08-24','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchFlowCapabilityEquivalenceController.java#evaluate','d59d000e2c85c402a4f659a9aa618e823c79c40a641c7c164af5b584346c8bdc',now(),'a0-r4-flow-capability-shadow-bridge','a0-r4-flow-capability-shadow-bridge'),
('REST:GET:/admin/dispatch-flows/{flowId}/capability-migration/legacy-equivalence/readiness','REST','control-plane-app','control-plane-app','DispatchFlowCapabilityEquivalenceController.readiness','/admin/dispatch-flows/{flowId}/capability-migration/legacy-equivalence/readiness','GET','TARGET_ONLY','admin.dispatch.flow.readiness',null,'[]'::jsonb,'DISPATCH_FLOW','R3_PATH_RESOURCE_RESOLVER',null,null,'a0-r4-flow-capability-shadow-bridge-2026-08-24','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchFlowCapabilityEquivalenceController.java#readiness','d59d000e2c85c402a4f659a9aa618e823c79c40a641c7c164af5b584346c8bdc',now(),'a0-r4-flow-capability-shadow-bridge','a0-r4-flow-capability-shadow-bridge'),
('REST:POST:/admin/dispatch-flows/{flowId}/capability-migration/legacy-equivalence/backfill','REST','control-plane-app','control-plane-app','DispatchFlowCapabilityEquivalenceController.backfill','/admin/dispatch-flows/{flowId}/capability-migration/legacy-equivalence/backfill','POST','TARGET_ONLY','admin.dispatch.flow.update',null,'[]'::jsonb,'DISPATCH_FLOW','R3_PATH_RESOURCE_RESOLVER',null,null,'a0-r4-flow-capability-shadow-bridge-2026-08-24','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchFlowCapabilityEquivalenceController.java#backfill','d59d000e2c85c402a4f659a9aa618e823c79c40a641c7c164af5b584346c8bdc',now(),'a0-r4-flow-capability-shadow-bridge','a0-r4-flow-capability-shadow-bridge')
on conflict(entry_point_id) do update set
 display_name=excluded.display_name,route_pattern=excluded.route_pattern,http_method=excluded.http_method,
 authority_state=excluded.authority_state,target_permission_code=excluded.target_permission_code,legacy_authority_type=null,
 legacy_authorities='[]'::jsonb,resource_type=excluded.resource_type,resource_resolver_id=excluded.resource_resolver_id,
 exemption_reason=null,migration_deadline=null,manifest_revision=excluded.manifest_revision,source_ref=excluded.source_ref,
 source_hash=excluded.source_hash,last_verified_at=now(),updated_at=now(),updated_by='a0-r4-flow-capability-shadow-bridge',
 version=permission_entry_point_inventory.version+1;

update rbac_policy_versions set policy_version=policy_version+1,updated_at=now(),updated_by='a0-r4-flow-capability-shadow-bridge';
