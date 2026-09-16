-- Stage 2 / A0-R2: Additive Runtime Foundation
--
-- Contract: add the v5.3 runtime projection model without changing the existing
-- Dispatch authority chain. Existing TaskStatus, task_assignments, Flow/Rule
-- routing and Netty delivery remain authoritative during A0-R2.

-- -----------------------------------------------------------------------------
-- 1. Task lifecycle / phase / outcome dual-write projection.
-- -----------------------------------------------------------------------------
alter table tasks add column if not exists task_lifecycle varchar(32);
alter table tasks add column if not exists task_phase varchar(32);
alter table tasks add column if not exists task_outcome varchar(32);
alter table tasks add column if not exists state_projection_version varchar(64);

alter table tasks drop constraint if exists chk_tasks_v53_lifecycle;
alter table tasks add constraint chk_tasks_v53_lifecycle
  check (task_lifecycle is null or task_lifecycle in ('CREATED','ACTIVE','WAITING','FINALIZING','CLOSED'));

alter table tasks drop constraint if exists chk_tasks_v53_phase;
alter table tasks add constraint chk_tasks_v53_phase
  check (task_phase is null or task_phase in ('INTAKE','FLOW_MATCH','TRIAGE','PLANNING','ADMISSION','EXECUTION','AGGREGATION','REMEDIATION','CLOSURE'));

alter table tasks drop constraint if exists chk_tasks_v53_outcome;
alter table tasks add constraint chk_tasks_v53_outcome
  check (task_outcome is null or task_outcome in ('UNRESOLVED','SUCCEEDED','PARTIAL_SUCCEEDED','FAILED','CANCELLED'));

create or replace function stage2_project_task_state_from_legacy_status()
returns trigger language plpgsql as $$
begin
  -- This projection is intentionally derived from the existing TaskStatus.
  -- It cannot grant execution authority and it must not change NEW.status.
  case upper(coalesce(new.status,''))
    when 'DRAFT' then
      new.task_lifecycle := 'CREATED'; new.task_phase := 'INTAKE'; new.task_outcome := 'UNRESOLVED';
    when 'WAITING_APPROVAL' then
      new.task_lifecycle := 'WAITING'; new.task_phase := 'ADMISSION'; new.task_outcome := 'UNRESOLVED';
    when 'WAITING_CONTEXT' then
      new.task_lifecycle := 'WAITING'; new.task_phase := 'ADMISSION'; new.task_outcome := 'UNRESOLVED';
    when 'WAITING_DEPENDENCY' then
      new.task_lifecycle := 'WAITING'; new.task_phase := 'EXECUTION'; new.task_outcome := 'UNRESOLVED';
    when 'WAITING_HUMAN' then
      new.task_lifecycle := 'WAITING'; new.task_phase := 'REMEDIATION'; new.task_outcome := 'UNRESOLVED';
    when 'BLOCKED' then
      new.task_lifecycle := 'WAITING'; new.task_phase := 'REMEDIATION'; new.task_outcome := 'UNRESOLVED';
    when 'CANCEL_REQUESTED' then
      new.task_lifecycle := 'WAITING'; new.task_phase := 'REMEDIATION'; new.task_outcome := 'UNRESOLVED';
    when 'PARTIALLY_COMPLETED' then
      new.task_lifecycle := 'ACTIVE'; new.task_phase := 'AGGREGATION'; new.task_outcome := 'UNRESOLVED';
    when 'ORPHANED' then
      new.task_lifecycle := 'ACTIVE'; new.task_phase := 'REMEDIATION'; new.task_outcome := 'UNRESOLVED';
    when 'RECONCILING' then
      new.task_lifecycle := 'ACTIVE'; new.task_phase := 'REMEDIATION'; new.task_outcome := 'UNRESOLVED';
    when 'SUCCEEDED' then
      new.task_lifecycle := 'CLOSED'; new.task_phase := 'CLOSURE'; new.task_outcome := 'SUCCEEDED';
    when 'COMPLETED' then
      new.task_lifecycle := 'CLOSED'; new.task_phase := 'CLOSURE'; new.task_outcome := 'SUCCEEDED';
    when 'CANCELLED' then
      new.task_lifecycle := 'CLOSED'; new.task_phase := 'CLOSURE'; new.task_outcome := 'CANCELLED';
    when 'SUPPRESSED' then
      new.task_lifecycle := 'CLOSED'; new.task_phase := 'CLOSURE'; new.task_outcome := 'CANCELLED';
    when 'FAILED' then
      new.task_lifecycle := 'CLOSED'; new.task_phase := 'CLOSURE'; new.task_outcome := 'FAILED';
    when 'ESCALATED' then
      new.task_lifecycle := 'CLOSED'; new.task_phase := 'CLOSURE'; new.task_outcome := 'FAILED';
    when 'DEAD_LETTER' then
      new.task_lifecycle := 'CLOSED'; new.task_phase := 'CLOSURE'; new.task_outcome := 'FAILED';
    when 'EXPIRED' then
      new.task_lifecycle := 'CLOSED'; new.task_phase := 'CLOSURE'; new.task_outcome := 'FAILED';
    when 'TIMED_OUT' then
      new.task_lifecycle := 'CLOSED'; new.task_phase := 'CLOSURE'; new.task_outcome := 'FAILED';
    else
      new.task_lifecycle := 'ACTIVE'; new.task_phase := 'EXECUTION'; new.task_outcome := 'UNRESOLVED';
  end case;
  new.state_projection_version := 'V188_LEGACY_TASK_STATUS_V1';
  return new;
end $$;

drop trigger if exists trg_stage2_task_state_projection on tasks;
create trigger trg_stage2_task_state_projection
before insert or update of status on tasks
for each row execute function stage2_project_task_state_from_legacy_status();

-- Backfill without forcing a status update, because status itself remains authority.
update tasks set
  task_lifecycle = case upper(coalesce(status,''))
    when 'DRAFT' then 'CREATED'
    when 'WAITING_APPROVAL' then 'WAITING'
    when 'WAITING_CONTEXT' then 'WAITING'
    when 'WAITING_DEPENDENCY' then 'WAITING'
    when 'WAITING_HUMAN' then 'WAITING'
    when 'BLOCKED' then 'WAITING'
    when 'CANCEL_REQUESTED' then 'WAITING'
    when 'SUCCEEDED' then 'CLOSED'
    when 'COMPLETED' then 'CLOSED'
    when 'CANCELLED' then 'CLOSED'
    when 'SUPPRESSED' then 'CLOSED'
    when 'FAILED' then 'CLOSED'
    when 'ESCALATED' then 'CLOSED'
    when 'DEAD_LETTER' then 'CLOSED'
    when 'EXPIRED' then 'CLOSED'
    when 'TIMED_OUT' then 'CLOSED'
    else 'ACTIVE' end,
  task_phase = case upper(coalesce(status,''))
    when 'DRAFT' then 'INTAKE'
    when 'WAITING_APPROVAL' then 'ADMISSION'
    when 'WAITING_CONTEXT' then 'ADMISSION'
    when 'PARTIALLY_COMPLETED' then 'AGGREGATION'
    when 'WAITING_HUMAN' then 'REMEDIATION'
    when 'BLOCKED' then 'REMEDIATION'
    when 'CANCEL_REQUESTED' then 'REMEDIATION'
    when 'ORPHANED' then 'REMEDIATION'
    when 'RECONCILING' then 'REMEDIATION'
    when 'SUCCEEDED' then 'CLOSURE'
    when 'COMPLETED' then 'CLOSURE'
    when 'CANCELLED' then 'CLOSURE'
    when 'SUPPRESSED' then 'CLOSURE'
    when 'FAILED' then 'CLOSURE'
    when 'ESCALATED' then 'CLOSURE'
    when 'DEAD_LETTER' then 'CLOSURE'
    when 'EXPIRED' then 'CLOSURE'
    when 'TIMED_OUT' then 'CLOSURE'
    else 'EXECUTION' end,
  task_outcome = case upper(coalesce(status,''))
    when 'SUCCEEDED' then 'SUCCEEDED'
    when 'COMPLETED' then 'SUCCEEDED'
    when 'CANCELLED' then 'CANCELLED'
    when 'SUPPRESSED' then 'CANCELLED'
    when 'FAILED' then 'FAILED'
    when 'ESCALATED' then 'FAILED'
    when 'DEAD_LETTER' then 'FAILED'
    when 'EXPIRED' then 'FAILED'
    when 'TIMED_OUT' then 'FAILED'
    else 'UNRESOLVED' end,
  state_projection_version = 'V188_LEGACY_TASK_STATUS_V1'
where task_lifecycle is null or task_phase is null or task_outcome is null
   or state_projection_version is distinct from 'V188_LEGACY_TASK_STATUS_V1';

create index if not exists idx_tasks_v53_state
  on tasks(tenant_id,task_lifecycle,task_phase,task_outcome,updated_at desc);

comment on column tasks.task_lifecycle is 'Stage 2 additive projection. Existing tasks.status remains runtime authority until a later cutover gate.';
comment on column tasks.task_phase is 'Stage 2 additive phase projection; it must not drive current Dispatch decisions.';
comment on column tasks.task_outcome is 'Stage 2 additive outcome projection; current TaskStatus remains authoritative during A0-R2.';

-- -----------------------------------------------------------------------------
-- 2. FlowMatchDecision projection evidence.
--    A0-R2 only projects legacy MATCHED evidence. NO_MATCH/AMBIGUOUS become
--    authoritative decision records in Stage 3 when the Flow shadow evaluator is
--    introduced. Absence of matched_flow_id must therefore not be interpreted as
--    NO_MATCH here.
-- -----------------------------------------------------------------------------
create table if not exists flow_match_decisions (
  tenant_id varchar(64) not null,
  decision_id varchar(160) not null,
  task_id varchar(128) not null,
  task_version bigint,
  decision_source varchar(48) not null default 'LEGACY_TASK_PROJECTION',
  flow_id varchar(128),
  flow_version varchar(64),
  evaluated_rule_count int not null default 0,
  matched_rule_id varchar(128),
  matched_rule_priority int,
  evaluated_rules_json jsonb not null default '[]'::jsonb,
  output_service_code varchar(160),
  output_capability_requirements_json jsonb not null default '[]'::jsonb,
  match_result varchar(24) not null,
  decided_at timestamptz not null default now(),
  evaluator_version varchar(80) not null default 'V188_LEGACY_TASK_PROJECTION',
  primary key (tenant_id,decision_id),
  unique (tenant_id,task_id,task_version,decision_source),
  constraint fk_flow_match_decision_task foreign key (tenant_id,task_id)
    references tasks(tenant_id,task_id) on delete cascade,
  constraint chk_flow_match_decision_result check (match_result in ('MATCHED','NO_MATCH','AMBIGUOUS')),
  constraint chk_flow_match_rules_array check (jsonb_typeof(evaluated_rules_json)='array'),
  constraint chk_flow_match_capabilities_array check (jsonb_typeof(output_capability_requirements_json)='array')
);

create index if not exists idx_flow_match_decisions_task
  on flow_match_decisions(tenant_id,task_id,decided_at desc);
create index if not exists idx_flow_match_decisions_flow
  on flow_match_decisions(tenant_id,flow_id,matched_rule_id,decided_at desc);

alter table flow_match_decisions enable row level security;
drop policy if exists tenant_isolation on flow_match_decisions;
create policy tenant_isolation on flow_match_decisions
  using (tenant_id=iam_current_tenant_id())
  with check (tenant_id=iam_current_tenant_id());

create or replace view legacy_flow_match_projection_v53 as
select t.tenant_id,
       'fmd-v188-' || substr(md5(t.tenant_id || ':' || t.task_id || ':' || coalesce(t.version,0)::text),1,40) as decision_id,
       t.task_id,coalesce(t.version,0) as task_version,'LEGACY_TASK_PROJECTION'::varchar(48) as decision_source,
       t.matched_flow_id as flow_id,t.matched_rule_id,
       t.task_type_code as output_service_code,
       coalesce(t.required_capabilities_json,'[]'::jsonb) as output_capability_requirements_json,
       'MATCHED'::varchar(24) as match_result,
       coalesce(t.updated_at,t.created_at,now()) as decided_at,
       'V188_LEGACY_TASK_PROJECTION'::varchar(80) as evaluator_version
  from tasks t
 where t.tenant_id is not null and (t.matched_flow_id is not null or t.matched_rule_id is not null);

comment on view legacy_flow_match_projection_v53 is 'Read-only Stage 2 projection of current Task matched Flow/Rule fields. It cannot block Task persistence and it does not infer NO_MATCH or AMBIGUOUS.';

insert into flow_match_decisions(
  tenant_id,decision_id,task_id,task_version,decision_source,flow_id,matched_rule_id,
  output_service_code,output_capability_requirements_json,match_result,decided_at,evaluator_version)
select t.tenant_id,
       'fmd-v188-' || substr(md5(t.tenant_id || ':' || t.task_id || ':' || coalesce(t.version,0)::text),1,40),
       t.task_id,coalesce(t.version,0),'LEGACY_TASK_PROJECTION',t.matched_flow_id,t.matched_rule_id,
       t.task_type_code,coalesce(t.required_capabilities_json,'[]'::jsonb),'MATCHED',coalesce(t.updated_at,t.created_at,now()),
       'V188_LEGACY_TASK_PROJECTION'
from tasks t
where t.tenant_id is not null and (t.matched_flow_id is not null or t.matched_rule_id is not null)
on conflict(tenant_id,task_id,task_version,decision_source) do nothing;

comment on table flow_match_decisions is 'Stage 2 backfill target for legacy Flow match provenance. Runtime Task writes do not write this table in A0-R2; Stage 3 introduces the authoritative Flow decision writer.';

-- -----------------------------------------------------------------------------
-- 3. TaskLineageEdge relational projection.
-- -----------------------------------------------------------------------------
create table if not exists task_lineage_edges (
  tenant_id varchar(64) not null,
  child_task_id varchar(128) not null,
  root_task_id varchar(128) not null,
  parent_task_id varchar(128) not null,
  depth int not null,
  creation_reason varchar(64),
  created_by_principal_ref varchar(320),
  created_at timestamptz not null,
  updated_at timestamptz not null default now(),
  primary key (tenant_id,child_task_id),
  constraint fk_task_lineage_child foreign key (tenant_id,child_task_id)
    references tasks(tenant_id,task_id) on delete cascade,
  constraint chk_task_lineage_depth check (depth >= 1)
);

create index if not exists idx_task_lineage_parent
  on task_lineage_edges(tenant_id,parent_task_id,created_at,child_task_id);
create index if not exists idx_task_lineage_root
  on task_lineage_edges(tenant_id,root_task_id,depth,created_at,child_task_id);

alter table task_lineage_edges enable row level security;
drop policy if exists tenant_isolation on task_lineage_edges;
create policy tenant_isolation on task_lineage_edges
  using (tenant_id=iam_current_tenant_id())
  with check (tenant_id=iam_current_tenant_id());

create or replace view task_lineage_projection_v53 as
with recursive lineage as (
  select t.tenant_id,t.task_id as child_task_id,t.task_id as root_task_id,
         cast(null as varchar(128)) as parent_task_id,0 as depth,
         array[t.task_id]::varchar[] as path
    from tasks t
   where t.tenant_id is not null and t.parent_task_id is null
  union all
  select c.tenant_id,c.task_id,l.root_task_id,c.parent_task_id,l.depth+1,l.path || c.task_id
    from tasks c
    join lineage l on l.tenant_id=c.tenant_id and l.child_task_id=c.parent_task_id
   where not c.task_id = any(l.path) and l.depth < 64
)
select t.tenant_id,t.task_id as child_task_id,coalesce(t.root_task_id,l.root_task_id,t.parent_task_id) as root_task_id,
       t.parent_task_id,greatest(coalesce(l.depth,case when t.hop_count>0 then t.hop_count else 1 end),1) as depth,
       t.created_reason as creation_reason,
       concat(coalesce(t.created_by_type,'SYSTEM'),':',coalesce(t.created_by_id,'CORE')) as created_by_principal_ref,
       coalesce(t.created_at,now()) as created_at,coalesce(t.updated_at,t.created_at,now()) as updated_at
  from tasks t
  left join lineage l on l.tenant_id=t.tenant_id and l.child_task_id=t.task_id
 where t.tenant_id is not null and t.parent_task_id is not null;

comment on view task_lineage_projection_v53 is 'Read-only Stage 2 projection from existing root_task_id/parent_task_id. It is diagnostic and cannot change Task persistence.';

with recursive lineage as (
  select t.tenant_id,t.task_id as child_task_id,t.task_id as root_task_id,
         cast(null as varchar(128)) as parent_task_id,0 as depth,
         array[t.task_id]::varchar[] as path
    from tasks t
   where t.tenant_id is not null and t.parent_task_id is null
  union all
  select c.tenant_id,c.task_id,l.root_task_id,c.parent_task_id,l.depth+1,l.path || c.task_id
    from tasks c
    join lineage l on l.tenant_id=c.tenant_id and l.child_task_id=c.parent_task_id
   where not c.task_id = any(l.path) and l.depth < 64
)
insert into task_lineage_edges(
  tenant_id,child_task_id,root_task_id,parent_task_id,depth,creation_reason,
  created_by_principal_ref,created_at,updated_at)
select t.tenant_id,t.task_id,coalesce(t.root_task_id,l.root_task_id,t.parent_task_id),t.parent_task_id,
       greatest(coalesce(l.depth,case when t.hop_count>0 then t.hop_count else 1 end),1),
       t.created_reason,concat(coalesce(t.created_by_type,'SYSTEM'),':',coalesce(t.created_by_id,'CORE')),
       coalesce(t.created_at,now()),coalesce(t.updated_at,t.created_at,now())
  from tasks t
  left join lineage l on l.tenant_id=t.tenant_id and l.child_task_id=t.task_id
 where t.tenant_id is not null and t.parent_task_id is not null
on conflict(tenant_id,child_task_id) do nothing;

comment on table task_lineage_edges is 'Stage 2 relational lineage projection. Existing Task root_task_id/parent_task_id remain authoritative during A0-R2.';

-- -----------------------------------------------------------------------------
-- 4. ExecutionAssignment additive fields on the existing task_assignments table.
--    No new assignment authority is introduced here.
-- -----------------------------------------------------------------------------
alter table task_assignments add column if not exists binding_id varchar(160);
alter table task_assignments add column if not exists selected_peer_interface_id varchar(160);
alter table task_assignments add column if not exists selected_mcp_server_id varchar(160);
alter table task_assignments add column if not exists execution_safety_mode varchar(40);
alter table task_assignments add column if not exists attempt_number int;
alter table task_assignments add column if not exists previous_assignment_id varchar(128);
alter table task_assignments add column if not exists released_at timestamptz;
alter table task_assignments add column if not exists outcome varchar(40);

alter table task_assignments drop constraint if exists chk_task_assignments_execution_safety_mode;
alter table task_assignments add constraint chk_task_assignments_execution_safety_mode
  check (execution_safety_mode is null or execution_safety_mode in ('LOCAL_FENCED','REMOTE_NATIVE_IDEMPOTENT','REMOTE_UNFENCED'));
alter table task_assignments drop constraint if exists chk_task_assignments_attempt_number;
alter table task_assignments add constraint chk_task_assignments_attempt_number
  check (attempt_number is null or attempt_number >= 1);

with ranked as (
  select tenant_id,assignment_id,
         row_number() over(partition by tenant_id,task_id order by created_at,assignment_id)::int as rn,
         lag(assignment_id) over(partition by tenant_id,task_id order by created_at,assignment_id) as prev
    from task_assignments
)
update task_assignments a set
  attempt_number=coalesce(a.attempt_number,r.rn),
  previous_assignment_id=coalesce(a.previous_assignment_id,r.prev)
from ranked r
where a.tenant_id=r.tenant_id and a.assignment_id=r.assignment_id
  and (a.attempt_number is null or a.previous_assignment_id is null);

create index if not exists idx_task_assignments_binding
  on task_assignments(tenant_id,binding_id,status,created_at desc) where binding_id is not null;
create index if not exists idx_task_assignments_attempt
  on task_assignments(tenant_id,task_id,attempt_number,created_at);

create or replace view execution_assignment_projection_v53 as
select a.*,
       a.assigned_pool_id as agent_pool_id,
       a.agent_id as selected_agent_id,
       a.agent_session_id as selected_session_id,
       coalesce(a.attempt_number,
         row_number() over(partition by a.tenant_id,a.task_id order by a.created_at,a.assignment_id)::int) as effective_attempt_number,
       coalesce(a.previous_assignment_id,
         lag(a.assignment_id) over(partition by a.tenant_id,a.task_id order by a.created_at,a.assignment_id)) as effective_previous_assignment_id
  from task_assignments a;

comment on column task_assignments.binding_id is 'Stage 2 optional Canonical Capability binding reference. NULL means legacy assignment provenance; it must not be inferred from Agent identity.';
comment on column task_assignments.execution_safety_mode is 'Reserved for Stage 5 execution safety closure; NULL in legacy assignments is intentional and must not be treated as LOCAL_FENCED.';
comment on view execution_assignment_projection_v53 is 'Read-only additive ExecutionAssignment projection over existing task_assignments; task_assignments remains the assignment authority.';

-- -----------------------------------------------------------------------------
-- 5. Schema authority evidence.
-- -----------------------------------------------------------------------------
insert into schema_contract_authority(contract_id,contract_family,authority_note,created_at,schema_version)
values(
  'stage2-a0-r2-additive-runtime-foundation-v1',
  'A0_R2_RUNTIME_PROJECTION',
  'ADDITIVE_ONLY: EXISTING_TASK_STATUS_TASK_ASSIGNMENTS_AND_DISPATCH_FLOW_REMAIN_AUTHORITATIVE; NEW_TASK_STATE_FLOW_MATCH_LINEAGE_AND_EXECUTION_ASSIGNMENT_FIELDS_ARE_PROJECTIONS_UNTIL_LATER_GATES',
  now(),
  'V188')
on conflict(contract_id) do nothing;
