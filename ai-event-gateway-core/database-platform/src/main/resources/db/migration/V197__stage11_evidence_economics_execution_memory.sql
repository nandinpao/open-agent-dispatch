-- Stage 11 — Evidence / Economics / Execution Memory.
-- Observation only: no Dispatch, Routing, Assignment, Planning, Learning or Fast-Path authority is added here.
-- Existing immutable evidence remains source-of-truth; Decision Trace is a projection to avoid write amplification.

create table if not exists execution_cost_ledger (
  tenant_id varchar(64) not null,
  cost_entry_id varchar(180) not null,
  context_type varchar(24) not null,
  context_id varchar(180) not null,
  task_id varchar(128),
  run_id varchar(160),
  step_id varchar(160),
  attempt_id varchar(160),
  assignment_id varchar(160),
  capability_code varchar(160),
  operation varchar(80),
  provider_type varchar(40),
  provider_id varchar(160),
  binding_id varchar(160),
  entry_type varchar(32) not null,
  quantity numeric(24,8),
  unit varchar(48),
  token_usage bigint,
  latency_ms bigint,
  currency varchar(12),
  booked_actual_cost numeric(24,8),
  normalized_comparison_cost numeric(24,8),
  price_profile_ref varchar(255),
  source_ref varchar(512) not null,
  evidence_json jsonb not null default '{}'::jsonb,
  recorded_at timestamptz not null default now(),
  primary key(tenant_id,cost_entry_id),
  constraint stage11_cost_context_check check(context_type in ('TASK','DELEGATION','PLAN_STEP','PROVIDER_EXECUTION','ADJUSTMENT')),
  constraint stage11_cost_entry_type_check check(entry_type in ('ACTUAL','ADJUSTMENT','COMPARISON_ESTIMATE','USAGE_ONLY')),
  constraint stage11_cost_quantity_check check(quantity is null or quantity>=0),
  constraint stage11_cost_token_check check(token_usage is null or token_usage>=0),
  constraint stage11_cost_latency_check check(latency_ms is null or latency_ms>=0),
  constraint stage11_cost_actual_check check(entry_type='ADJUSTMENT' or booked_actual_cost is null or booked_actual_cost>=0),
  constraint stage11_cost_normalized_check check(normalized_comparison_cost is null or normalized_comparison_cost>=0),
  constraint stage11_cost_evidence_object check(jsonb_typeof(evidence_json)='object'),
  constraint stage11_cost_actual_semantics check(entry_type<>'ACTUAL' or booked_actual_cost is not null),
  constraint stage11_cost_adjustment_semantics check(entry_type<>'ADJUSTMENT' or booked_actual_cost is not null)
);
create index if not exists idx_stage11_cost_task on execution_cost_ledger(tenant_id,task_id,recorded_at desc) where task_id is not null;
create index if not exists idx_stage11_cost_context on execution_cost_ledger(tenant_id,context_type,context_id,recorded_at desc);
create index if not exists idx_stage11_cost_provider on execution_cost_ledger(tenant_id,provider_type,provider_id,recorded_at desc);
alter table execution_cost_ledger enable row level security;
drop policy if exists tenant_isolation on execution_cost_ledger;
create policy tenant_isolation on execution_cost_ledger using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
create or replace function prevent_stage11_cost_ledger_mutation() returns trigger language plpgsql as $$ begin raise exception 'EXECUTION_COST_LEDGER_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_stage11_cost_ledger_immutable on execution_cost_ledger;
create trigger trg_stage11_cost_ledger_immutable before update or delete on execution_cost_ledger for each row execute function prevent_stage11_cost_ledger_mutation();

create table if not exists evidence_retention_policies (
  tenant_id varchar(64) not null,
  policy_id varchar(160) not null,
  hot_days int not null default 30,
  warm_days int not null default 180,
  cold_days int not null default 2555,
  archive_required_before_prune boolean not null default true,
  status varchar(24) not null default 'ACTIVE',
  version int not null default 1,
  updated_by varchar(255) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key(tenant_id,policy_id),
  constraint stage11_retention_days_check check(hot_days>=1 and warm_days>hot_days and cold_days>=warm_days),
  constraint stage11_retention_status_check check(status in ('ACTIVE','DISABLED','RETIRED')),
  constraint stage11_retention_version_check check(version>=1)
);
create unique index if not exists uq_stage11_active_retention_policy on evidence_retention_policies(tenant_id) where status='ACTIVE';
alter table evidence_retention_policies enable row level security;
drop policy if exists tenant_isolation on evidence_retention_policies;
create policy tenant_isolation on evidence_retention_policies using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());

create table if not exists evidence_archive_segments (
  tenant_id varchar(64) not null,
  archive_segment_id varchar(180) not null,
  source_family varchar(80) not null,
  range_start timestamptz not null,
  range_end timestamptz not null,
  object_ref varchar(1024),
  content_digest varchar(160),
  record_count bigint,
  status varchar(24) not null default 'PLANNED',
  verified_at timestamptz,
  created_at timestamptz not null default now(),
  primary key(tenant_id,archive_segment_id),
  constraint stage11_archive_range_check check(range_end>range_start),
  constraint stage11_archive_status_check check(status in ('PLANNED','EXPORTED','VERIFIED','FAILED')),
  constraint stage11_archive_verified_check check(status<>'VERIFIED' or (object_ref is not null and content_digest is not null and verified_at is not null))
);
alter table evidence_archive_segments enable row level security;
drop policy if exists tenant_isolation on evidence_archive_segments;
create policy tenant_isolation on evidence_archive_segments using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());

-- Provider-neutral execution observations. This view is rebuildable and never becomes routing authority.
create or replace view execution_observation_projection_v53 as
select d.tenant_id,
       'DELEGATION'::varchar(24) as context_type,
       d.delegation_id::varchar(180) as context_id,
       coalesce(d.child_task_id,d.parent_task_id)::varchar(128) as task_id,
       null::varchar(160) as run_id,
       null::varchar(160) as step_id,
       null::varchar(160) as attempt_id,
       d.assignment_id::varchar(160) as assignment_id,
       d.capability_code,
       d.operation,
       d.selected_provider_type as provider_type,
       d.selected_provider_id as provider_id,
       d.selected_binding_id as binding_id,
       d.status as outcome,
       d.created_at as started_at,
       coalesce(d.result_received_at,d.updated_at) as completed_at,
       case when d.result_received_at is null then null::bigint else greatest(0,(extract(epoch from (d.result_received_at-d.created_at))*1000)::bigint) end as latency_ms
  from capability_delegation_requests d
union all
select pa.tenant_id,
       'PLAN_STEP'::varchar(24),
       pa.attempt_id::varchar(180),
       pa.child_task_ref::varchar(128),
       pa.run_id,
       pa.step_id,
       pa.attempt_id,
       ta.assignment_id,
       coalesce(ps.capability_requirement_json->>'capabilityCode',ps.capability_requirement_json->>'code'),
       coalesce(ps.capability_requirement_json->>'operation','READ'),
       ta.provider_type,
       ta.provider_id,
       ta.binding_id,
       pa.state,
       pa.submitted_at,
       pa.completed_at,
       case when pa.completed_at is null then null::bigint else greatest(0,(extract(epoch from (pa.completed_at-pa.submitted_at))*1000)::bigint) end
  from plan_execution_attempts pa
  join plan_execution_steps ps on ps.tenant_id=pa.tenant_id and ps.run_id=pa.run_id and ps.step_id=pa.step_id
  left join lateral (
    select a.assignment_id,a.provider_type,a.provider_id,a.binding_id
      from task_assignments a
     where a.tenant_id=pa.tenant_id and a.task_id=pa.child_task_ref
     order by a.created_at desc limit 1
  ) ta on true;

create or replace view decision_trace_event_projection_v53 as
select f.tenant_id,
       ('flow:'||f.decision_id)::varchar(256) as trace_event_id,
       'TASK'::varchar(24) as context_type,f.task_id::varchar(180) as context_id,f.task_id,
       null::varchar(160) as run_id,null::varchar(160) as delegation_id,
       'FLOW_MATCH'::varchar(64) as stage,'FLOW_MATCH_DECISION'::varchar(96) as event_type,
       f.match_result::varchar(96) as result,
       null::varchar(160) as capability_code,null::varchar(40) as provider_type,null::varchar(160) as binding_id,
       f.decision_id::varchar(180) as decision_id,
       jsonb_build_object('source',f.decision_source,'flowId',f.flow_id,'ruleId',f.matched_rule_id,'serviceCode',f.output_service_code,'requirements',f.output_capability_requirements_json,'evaluatorVersion',f.evaluator_version) as details_json,
       f.decided_at as occurred_at,'flow_match_decisions'::varchar(96) as source_table
  from flow_match_decisions f
union all
select s.tenant_id,'triage:'||s.runtime_decision_id,'TASK',s.task_id,s.task_id,null,null,'SEMANTIC_TRIAGE','SEMANTIC_TRIAGE_DECISION',s.action,s.classification_code,null,null,s.runtime_decision_id,
       jsonb_build_object('flowResult',s.flow_match_result,'rolloutMode',s.rollout_mode,'semanticResult',s.semantic_result,'planningRequestId',s.planning_request_id,'reasonCodes',s.reason_codes_json,'evidence',s.evidence_json,'modelProfileRef',s.model_profile_ref,'promptProfileRef',s.prompt_profile_ref),s.decided_at,'semantic_triage_runtime_decisions'
  from semantic_triage_runtime_decisions s
union all
select d.tenant_id,'delegation:'||e.event_id,'DELEGATION',d.delegation_id,d.parent_task_id,null,d.delegation_id,'DELEGATION',e.event_type,coalesce(e.to_status,e.from_status),d.capability_code,d.selected_provider_type,d.selected_binding_id,d.delegation_id,
       jsonb_build_object('reasonCodes',e.reason_codes_json,'evidence',e.evidence_json,'providerId',d.selected_provider_id,'childTaskId',d.child_task_id,'assignmentId',d.assignment_id),e.occurred_at,'capability_delegation_events'
  from capability_delegation_events e join capability_delegation_requests d on d.tenant_id=e.tenant_id and d.delegation_id=e.delegation_id
union all
select d.tenant_id,'who-may:'||a.decision_id,'DELEGATION',d.delegation_id,d.parent_task_id,null,d.delegation_id,'WHO_MAY','AUTHORIZATION_DECISION',a.result,a.capability_code,a.provider_type,a.binding_id,a.decision_id,
       jsonb_build_object('operation',a.operation,'providerId',a.provider_id,'policyId',a.selected_policy_id,'policyVersion',a.selected_policy_version,'approvalMode',a.approval_mode,'reasonCodes',a.reason_codes_json),a.evaluated_at,'delegation_authorization_decisions'
  from capability_delegation_requests d join delegation_authorization_decisions a on a.tenant_id=d.tenant_id and a.decision_id=d.authorization_decision_id
union all
select d.tenant_id,'who-should:'||r.decision_id,'DELEGATION',d.delegation_id,d.parent_task_id,null,d.delegation_id,'WHO_SHOULD','ROUTING_DECISION',r.result,r.capability_code,null,r.selected_binding_id,r.decision_id,
       jsonb_build_object('operation',r.operation,'providerId',r.selected_provider_id,'routingProfileId',r.routing_profile_id,'routingProfileVersion',r.routing_profile_version,'reasonCodes',r.reason_codes_json,'candidateCount',jsonb_array_length(r.candidates_json),'candidatesDigest',md5(r.candidates_json::text)),r.evaluated_at,'provider_routing_decisions'
  from capability_delegation_requests d join provider_routing_decisions r on r.tenant_id=d.tenant_id and r.decision_id=d.routing_decision_id
union all
select a.tenant_id,'assignment:'||a.assignment_id,
       case when d.delegation_id is not null then 'DELEGATION' when pa.attempt_id is not null then 'PLAN_STEP' else 'TASK' end,
       coalesce(d.delegation_id,pa.attempt_id,a.task_id),a.task_id,pa.run_id,d.delegation_id,'ASSIGNMENT','EXECUTION_ASSIGNMENT',a.status,null,a.provider_type,a.binding_id,a.routing_decision_id,
       jsonb_build_object('assignmentId',a.assignment_id,'providerId',a.provider_id,'agentId',a.agent_id,'executionTargetType',a.execution_target_type,'safetyMode',a.execution_safety_mode,'leaseId',a.lease_id,'fencingToken',a.fencing_token,'leaseExpiresAt',a.lease_expires_at),a.created_at,'task_assignments'
  from task_assignments a
  left join capability_delegation_requests d on d.tenant_id=a.tenant_id and d.assignment_id=a.assignment_id
  left join lateral (select p0.attempt_id,p0.run_id from plan_execution_attempts p0 where p0.tenant_id=a.tenant_id and p0.child_task_ref=a.task_id order by p0.submitted_at desc limit 1) pa on true
union all
select s.tenant_id,'safety:'||s.event_id,'DELEGATION',s.delegation_id,d.parent_task_id,null,s.delegation_id,'RUNTIME_SAFETY',s.event_type,s.reason_code,d.capability_code,d.selected_provider_type,d.selected_binding_id,d.authorization_decision_id,
       jsonb_build_object('assignmentId',s.assignment_id,'authorizationEpoch',s.authorization_epoch,'revocationVersion',s.revocation_version,'evidence',s.evidence_json),s.occurred_at,'capability_runtime_safety_events'
  from capability_runtime_safety_events s join capability_delegation_requests d on d.tenant_id=s.tenant_id and d.delegation_id=s.delegation_id
union all
select m.tenant_id,'mcp:'||m.execution_id,m.execution_context_type,m.execution_id,m.task_id,m.plan_run_id,m.delegation_id,'PROVIDER_RUNTIME','MCP_READ_EXECUTION',m.status,null,'MCP_TOOL',null,null,
       jsonb_build_object('providerId',m.provider_id,'serverId',m.mcp_server_id,'toolId',m.mcp_tool_id,'attempts',m.attempts,'httpStatus',m.http_status,'errorCode',m.error_code),coalesce(m.completed_at,m.updated_at),'mcp_read_executions'
  from mcp_read_executions m
union all
select x.tenant_id,'a2a:'||x.execution_id,x.execution_context_type,x.execution_id,x.task_id,x.plan_run_id,x.delegation_id,'PROVIDER_RUNTIME','A2A_REMOTE_EXECUTION',x.status,null,'REMOTE_A2A_AGENT',null,null,
       jsonb_build_object('providerId',x.provider_id,'peerId',x.peer_id,'interfaceId',x.interface_id,'remoteTaskId',x.remote_task_id,'remoteState',x.remote_state,'attempts',x.attempts,'httpStatus',x.http_status,'errorCode',x.error_code),coalesce(x.completed_at,x.updated_at),'a2a_remote_read_executions'
  from a2a_remote_read_executions x
union all
select x.tenant_id,'a2a-event:'||j.journal_event_id,x.execution_context_type,x.execution_id,x.task_id,x.plan_run_id,x.delegation_id,'REMOTE_EVENT',j.event_type,coalesce(j.remote_state,j.source),null,'REMOTE_A2A_AGENT',null,null,
       jsonb_build_object('trackingId',j.tracking_id,'remoteTaskId',j.remote_task_id,'source',j.source,'dedupKey',j.dedup_key,'payloadHash',j.payload_hash),j.observed_at,'a2a_remote_event_journal'
  from a2a_remote_event_journal j join a2a_remote_read_executions x on x.tenant_id=j.tenant_id and x.execution_id=j.execution_id
union all
select e.tenant_id,'plan-event:'||e.event_id,'PLAN_RUN',e.run_id,ps.child_task_ref,e.run_id,null,'PLAN_EXECUTION',e.event_type,coalesce(e.to_state,e.from_state),null,ta.provider_type,ta.binding_id,null,
       jsonb_build_object('stepId',e.step_id,'reason',e.reason,'actorRef',e.actor_ref,'evidence',e.evidence_json),e.occurred_at,'plan_execution_events'
  from plan_execution_events e
  left join plan_execution_steps ps on ps.tenant_id=e.tenant_id and ps.run_id=e.run_id and ps.step_id=e.step_id
  left join lateral (select a.provider_type,a.binding_id from task_assignments a where a.tenant_id=e.tenant_id and a.task_id=ps.child_task_ref order by a.created_at desc limit 1) ta on true
union all
select c.tenant_id,'convergence:'||c.convergence_id,'PLAN_RUN',c.run_id,null,c.run_id,null,'CONVERGENCE','PLAN_CONVERGENCE_DECISION',c.outcome,null,null,null,c.convergence_id,
       jsonb_build_object('completionPolicy',c.completion_policy,'quorumRequired',c.quorum_required,'requiredSteps',c.required_step_count,'successfulRequired',c.successful_required_count,'failedRequired',c.failed_required_count,'artifactCount',c.artifact_count,'conflictCount',c.conflict_count,'reasonCodes',c.reason_codes_json,'evidence',c.evidence_json),c.decided_at,'plan_execution_convergence_decisions'
  from plan_execution_convergence_decisions c;

create or replace view decision_trace_retention_projection_v53 as
select d.*,
       case
         when d.occurred_at >= now() - make_interval(days=>coalesce(p.hot_days,30)) then 'HOT'
         when d.occurred_at >= now() - make_interval(days=>coalesce(p.warm_days,180)) then 'WARM'
         else 'COLD'
       end::varchar(16) as retention_tier,
       coalesce(p.archive_required_before_prune,true) as archive_required_before_prune
  from decision_trace_event_projection_v53 d
  left join lateral (
    select hot_days,warm_days,archive_required_before_prune
      from evidence_retention_policies p0
     where p0.tenant_id=d.tenant_id and p0.status='ACTIVE'
     order by p0.version desc limit 1
  ) p on true;

create or replace view execution_economics_summary_v53 as
select tenant_id,capability_code,operation,provider_type,provider_id,binding_id,currency,
       count(*) as entry_count,
       count(*) filter(where booked_actual_cost is not null) as actual_cost_entry_count,
       sum(booked_actual_cost) as booked_actual_cost,
       sum(normalized_comparison_cost) as normalized_comparison_cost,
       sum(token_usage) as token_usage,
       sum(latency_ms) as latency_ms,
       min(recorded_at) as first_recorded_at,max(recorded_at) as last_recorded_at
  from execution_cost_ledger
 group by tenant_id,capability_code,operation,provider_type,provider_id,binding_id,currency;

create or replace view execution_cost_completeness_v53 as
select o.tenant_id,o.context_type,o.context_id,o.task_id,o.run_id,o.step_id,o.attempt_id,o.assignment_id,
       o.capability_code,o.operation,o.provider_type,o.provider_id,o.binding_id,o.outcome,o.started_at,o.completed_at,o.latency_ms,
       coalesce(sum(l.booked_actual_cost),0) as booked_actual_cost,
       coalesce(sum(l.normalized_comparison_cost),0) as normalized_comparison_cost,
       case when count(l.cost_entry_id) filter(where l.booked_actual_cost is not null)>0 then 'ACTUAL_BOOKED'
            when count(l.cost_entry_id) filter(where l.normalized_comparison_cost is not null)>0 then 'ESTIMATE_ONLY'
            else 'UNBOOKED' end::varchar(24) as cost_completeness
  from execution_observation_projection_v53 o
  left join execution_cost_ledger l on l.tenant_id=o.tenant_id and l.context_type=o.context_type and l.context_id=o.context_id
 group by o.tenant_id,o.context_type,o.context_id,o.task_id,o.run_id,o.step_id,o.attempt_id,o.assignment_id,o.capability_code,o.operation,o.provider_type,o.provider_id,o.binding_id,o.outcome,o.started_at,o.completed_at,o.latency_ms;

create table if not exists execution_memory_daily_buckets (
  tenant_id varchar(64) not null,
  bucket_date date not null,
  capability_code varchar(160) not null default '',
  operation varchar(80) not null default '',
  provider_type varchar(40) not null default '',
  provider_id varchar(160) not null default '',
  binding_id varchar(160) not null default '',
  sample_count bigint not null,
  success_count bigint not null,
  failure_count bigint not null,
  pending_count bigint not null,
  latency_sample_count bigint not null,
  latency_sum_ms numeric(24,2) not null,
  max_latency_ms bigint,
  booked_actual_cost numeric(24,8) not null default 0,
  normalized_comparison_cost numeric(24,8) not null default 0,
  unbooked_count bigint not null default 0,
  refreshed_at timestamptz not null default now(),
  primary key(tenant_id,bucket_date,capability_code,operation,provider_type,provider_id,binding_id),
  constraint stage11_memory_counts_check check(sample_count>=0 and success_count>=0 and failure_count>=0 and pending_count>=0 and latency_sample_count>=0),
  constraint stage11_memory_cost_check check(booked_actual_cost>=0 and normalized_comparison_cost>=0)
);
create index if not exists idx_stage11_memory_capability on execution_memory_daily_buckets(tenant_id,capability_code,bucket_date desc);
alter table execution_memory_daily_buckets enable row level security;
drop policy if exists tenant_isolation on execution_memory_daily_buckets;
create policy tenant_isolation on execution_memory_daily_buckets using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());

create table if not exists execution_memory_refresh_runs (
  tenant_id varchar(64) not null,
  refresh_id varchar(180) not null,
  window_start date not null,
  window_end date not null,
  bucket_count bigint not null,
  observation_count bigint not null,
  status varchar(24) not null,
  evidence_json jsonb not null default '{}'::jsonb,
  refreshed_at timestamptz not null default now(),
  primary key(tenant_id,refresh_id),
  constraint stage11_memory_refresh_status_check check(status in ('COMPLETED','FAILED')),
  constraint stage11_memory_refresh_evidence_object check(jsonb_typeof(evidence_json)='object')
);
alter table execution_memory_refresh_runs enable row level security;
drop policy if exists tenant_isolation on execution_memory_refresh_runs;
create policy tenant_isolation on execution_memory_refresh_runs using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
create or replace function prevent_stage11_memory_refresh_mutation() returns trigger language plpgsql as $$ begin raise exception 'EXECUTION_MEMORY_REFRESH_RUN_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_stage11_memory_refresh_immutable on execution_memory_refresh_runs;
create trigger trg_stage11_memory_refresh_immutable before update or delete on execution_memory_refresh_runs for each row execute function prevent_stage11_memory_refresh_mutation();

create or replace view execution_memory_rollup_v53 as
select tenant_id,capability_code,operation,provider_type,provider_id,binding_id,
       sum(sample_count) as sample_count,
       sum(success_count) as success_count,
       sum(failure_count) as failure_count,
       sum(pending_count) as pending_count,
       case when sum(success_count)+sum(failure_count)=0 then null else round(sum(success_count)::numeric/(sum(success_count)+sum(failure_count)),6) end as observed_success_rate,
       case when sum(latency_sample_count)=0 then null else round(sum(latency_sum_ms)/sum(latency_sample_count),2) end as average_latency_ms,
       max(max_latency_ms) as max_latency_ms,
       sum(booked_actual_cost) as booked_actual_cost,
       sum(normalized_comparison_cost) as normalized_comparison_cost,
       sum(unbooked_count) as unbooked_count,
       min(bucket_date) as first_bucket_date,max(bucket_date) as last_bucket_date,max(refreshed_at) as refreshed_at
  from execution_memory_daily_buckets
 group by tenant_id,capability_code,operation,provider_type,provider_id,binding_id;

insert into schema_contract_authority(contract_id,contract_family,authority_note,created_at,schema_version)
values('stage11-evidence-economics-execution-memory-v1','STAGE11_EVIDENCE_ECONOMICS_EXECUTION_MEMORY',
'OBSERVATION_ONLY_NO_EXECUTION_AUTHORITY; DECISION_TRACE_IS_PROJECTION_OF_EXISTING_EVIDENCE; COST_LEDGER_APPEND_ONLY; EXECUTION_MEMORY_IS_EXECUTOR_LEVEL_AGGREGATE_NOT_ROUTING_PATTERN; NO_LEARNING_OR_FAST_PATH_PROMOTION; HOT_WARM_COLD_RETENTION_IS_NON_DESTRUCTIVE_UNTIL_ARCHIVE_VERIFIED',now(),'V197')
on conflict(contract_id) do nothing;
