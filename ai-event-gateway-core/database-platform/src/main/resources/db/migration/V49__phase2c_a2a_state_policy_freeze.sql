-- Phase 2C: freeze A2A lifecycle status, operational stage, blocker code and policy binding.

alter table a2a_requests add column if not exists operational_stage varchar(48);
alter table a2a_requests add column if not exists blocker_code varchar(64) not null default 'NONE';
alter table a2a_requests add column if not exists blocker_reason text;
alter table a2a_requests add column if not exists policy_version bigint not null default 0;
alter table a2a_requests add column if not exists policy_snapshot_json jsonb;
alter table a2a_requests add column if not exists policy_snapshot_hash varchar(128);

update a2a_requests
set operational_stage = case request_status
  when 'REQUESTED' then 'REQUESTED'
  when 'VALIDATING' then 'VALIDATING'
  when 'WAITING_APPROVAL' then 'WAITING_APPROVAL'
  when 'APPROVED' then 'APPROVED'
  when 'CHILD_TASK_CREATED' then 'CHILD_TASK_CREATED'
  when 'DISPATCHING' then 'DISPATCHING'
  when 'RUNNING' then 'WAITING_RESULT'
  when 'CANCEL_REQUESTED' then 'CANCEL_REQUESTED'
  when 'CANCELLED_UNCONFIRMED' then 'BLOCKED'
  when 'WAIT_HUMAN' then 'WAIT_HUMAN'
  else 'TERMINAL'
end
where operational_stage is null;

update a2a_requests
set blocker_code = case
  when request_status = 'WAITING_APPROVAL' then 'APPROVAL_REQUIRED'
  when request_status = 'CANCELLED_UNCONFIRMED' then 'CANCELLATION_UNCONFIRMED'
  when request_status = 'WAIT_HUMAN' then 'MANUAL_REVIEW_REQUIRED'
  else coalesce(nullif(blocker_code, ''), 'NONE')
end;

update a2a_requests r
set policy_version = p.version,
    policy_snapshot_json = jsonb_build_object(
      'policyId', p.policy_id,
      'policyVersion', p.version,
      'sourceDomainId', r.source_domain_id,
      'targetDomainId', r.target_domain_id,
      'taskType', r.requested_task_type,
      'serviceCode', r.requested_service_code,
      'agentCapabilityCodes', r.requested_capability_codes_json,
      'targetAgentPoolId', r.target_agent_pool_id,
      'approvalMode', p.approval_mode,
      'hopLimit', p.max_hop_count,
      'timeoutSeconds', p.timeout_seconds,
      'aggregationPolicy', p.result_aggregation_policy,
      'cancellationPolicy', p.cancellation_policy,
      'failurePropagationPolicy', p.failure_propagation_policy,
      'handoffPolicyId', p.handoff_context_policy_id,
      'handoffRequirement', p.handoff_context_requirement,
      'issueProjectionPolicy', p.issue_projection_policy,
      'snapshotHash', null
    )
from a2a_policies p
where p.tenant_id = r.tenant_id
  and p.policy_id = r.policy_id
  and (r.policy_version = 0 or r.policy_snapshot_json is null);

alter table a2a_requests alter column operational_stage set not null;

alter table a2a_state_history add column if not exists from_operational_stage varchar(48);
alter table a2a_state_history add column if not exists to_operational_stage varchar(48);
alter table a2a_state_history add column if not exists blocker_code varchar(64) not null default 'NONE';
alter table a2a_state_history add column if not exists transition_command varchar(64);
alter table a2a_state_history add column if not exists required_permission varchar(64);
alter table a2a_state_history add column if not exists evidence_type varchar(64);
alter table a2a_state_history add column if not exists evidence_reference text;
alter table a2a_state_history add column if not exists domain_event_code varchar(128);
alter table a2a_state_history add column if not exists failure_handling varchar(64);
alter table a2a_state_history add column if not exists timeout_policy varchar(64);
alter table a2a_state_history add column if not exists expected_version bigint not null default 0;
alter table a2a_state_history add column if not exists resulting_version bigint not null default 0;
alter table a2a_state_history add column if not exists recovery boolean not null default false;

update a2a_state_history
set from_operational_stage = case from_status
      when 'REQUESTED' then 'REQUESTED'
      when 'VALIDATING' then 'VALIDATING'
      when 'WAITING_APPROVAL' then 'WAITING_APPROVAL'
      when 'APPROVED' then 'APPROVED'
      when 'CHILD_TASK_CREATED' then 'CHILD_TASK_CREATED'
      when 'DISPATCHING' then 'DISPATCHING'
      when 'RUNNING' then 'WAITING_RESULT'
      when 'CANCEL_REQUESTED' then 'CANCEL_REQUESTED'
      when 'CANCELLED_UNCONFIRMED' then 'BLOCKED'
      when 'WAIT_HUMAN' then 'WAIT_HUMAN'
      when null then null
      else 'TERMINAL'
    end,
    to_operational_stage = case to_status
      when 'REQUESTED' then 'REQUESTED'
      when 'VALIDATING' then 'VALIDATING'
      when 'WAITING_APPROVAL' then 'WAITING_APPROVAL'
      when 'APPROVED' then 'APPROVED'
      when 'CHILD_TASK_CREATED' then 'CHILD_TASK_CREATED'
      when 'DISPATCHING' then 'DISPATCHING'
      when 'RUNNING' then 'WAITING_RESULT'
      when 'CANCEL_REQUESTED' then 'CANCEL_REQUESTED'
      when 'CANCELLED_UNCONFIRMED' then 'BLOCKED'
      when 'WAIT_HUMAN' then 'WAIT_HUMAN'
      else 'TERMINAL'
    end,
    blocker_code = case
      when to_status = 'WAITING_APPROVAL' then 'APPROVAL_REQUIRED'
      when to_status = 'CANCELLED_UNCONFIRMED' then 'CANCELLATION_UNCONFIRMED'
      when to_status = 'WAIT_HUMAN' then 'MANUAL_REVIEW_REQUIRED'
      else 'NONE'
    end,
    expected_version = greatest(request_version - 1, 0),
    resulting_version = request_version
where to_operational_stage is null;

do $$
begin
  if not exists (select 1 from pg_constraint where conname = 'ck_a2a_operational_stage_p2c') then
    alter table a2a_requests add constraint ck_a2a_operational_stage_p2c check (operational_stage in (
      'REQUESTED','VALIDATING','WAITING_APPROVAL','APPROVED','CHILD_TASK_CREATING',
      'CHILD_TASK_CREATED','DISPATCH_REQUESTED','DISPATCHING','BLOCKED','RUNNING',
      'WAITING_RESULT','CANCEL_REQUESTED','WAIT_HUMAN','TERMINAL'
    )) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'ck_a2a_blocker_code_p2c') then
    alter table a2a_requests add constraint ck_a2a_blocker_code_p2c check (blocker_code in (
      'NONE','APPROVAL_REQUIRED','CHILD_TASK_CREATION_PENDING','CHILD_TASK_CREATION_FAILED',
      'HANDOFF_REQUIRED','DISPATCH_REQUEST_PENDING','DISPATCH_RETRY_WAITING','DISPATCH_FAILED',
      'DISPATCH_TIMED_OUT','DISPATCH_DEAD_LETTER','NO_ELIGIBLE_AGENT','AGENT_UNAVAILABLE',
      'ASSIGNMENT_EVIDENCE_MISSING','RESULT_EVIDENCE_MISSING','RESULT_QUARANTINED',
      'CANCELLATION_UNCONFIRMED','POLICY_BINDING_INVALID','RECONCILIATION_REQUIRED',
      'MANUAL_REVIEW_REQUIRED'
    )) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'ck_a2a_blocked_requires_code_p2c') then
    alter table a2a_requests add constraint ck_a2a_blocked_requires_code_p2c check (
      operational_stage <> 'BLOCKED' or blocker_code <> 'NONE'
    ) not valid;
  end if;
end $$;

alter table a2a_requests validate constraint ck_a2a_operational_stage_p2c;
alter table a2a_requests validate constraint ck_a2a_blocker_code_p2c;
alter table a2a_requests validate constraint ck_a2a_blocked_requires_code_p2c;

create index if not exists ix_a2a_requests_operational_stage
  on a2a_requests(tenant_id, operational_stage, updated_at desc);
create index if not exists ix_a2a_requests_blocker
  on a2a_requests(tenant_id, blocker_code, updated_at desc)
  where blocker_code <> 'NONE';
