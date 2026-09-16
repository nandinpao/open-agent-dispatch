-- MRS A0-R7 — Execution Safety / Dispatch Authority.
-- Establishes the durable boundary:
-- RoutingDecision -> authoritative ExecutionAssignment -> ExecutionLease/fencing -> DispatchIntent COMMIT -> worker/network.
-- Per-Flow NEW_AUTHORITATIVE is permitted only after R6 shadow evidence exists. Global cutover is forbidden.

-- 1. Permit a controlled per-Flow cutover only in R7.
alter table flow_routing_migration_state drop constraint if exists a0r6_flow_migration_state_check;
alter table flow_routing_migration_state add constraint a0r7_flow_migration_state_check
  check(migration_state in ('LEGACY_AUTHORITATIVE','SHADOW','NEW_AUTHORITATIVE'));
alter table flow_routing_migration_events drop constraint if exists a0r6_flow_event_state_check;
alter table flow_routing_migration_events add constraint a0r7_flow_event_state_check
  check(to_state in ('LEGACY_AUTHORITATIVE','SHADOW','NEW_AUTHORITATIVE'));

alter table provider_routing_decisions drop constraint if exists a0r6_routing_authority_mode_check;
alter table provider_routing_decisions add constraint a0r7_routing_authority_mode_check
  check(authority_mode is null or authority_mode in ('LEGACY_PREVIEW','SHADOW','NEW_AUTHORITATIVE'));

-- 2. Monotonic fencing sequence per Task Step.
create table if not exists execution_fencing_counters (
  tenant_id varchar(64) not null,
  task_id varchar(128) not null,
  step_key varchar(200) not null,
  last_token bigint not null default 0,
  updated_at timestamptz not null default now(),
  primary key(tenant_id,task_id,step_key),
  constraint a0r7_fence_counter_nonnegative check(last_token>=0)
);

-- 3. ExecutionLease is ownership authority. It is not a remote-side-effect guarantee.
create table if not exists execution_leases_v206 (
  tenant_id varchar(64) not null,
  lease_id varchar(180) not null,
  task_id varchar(128) not null,
  plan_id varchar(160) not null,
  plan_revision int not null,
  step_id varchar(160) not null,
  assignment_id varchar(180) not null,
  owner_node_id varchar(160) not null,
  fencing_token bigint not null,
  lease_until timestamptz not null,
  status varchar(24) not null default 'ACTIVE',
  acquired_at timestamptz not null default now(),
  released_at timestamptz,
  release_reason varchar(512),
  version bigint not null default 1,
  primary key(tenant_id,lease_id),
  unique(tenant_id,assignment_id),
  constraint a0r7_lease_status_check check(status in ('ACTIVE','RELEASED','EXPIRED','REVOKED')),
  constraint a0r7_fence_positive check(fencing_token>=1),
  constraint a0r7_lease_time_check check(lease_until>acquired_at),
  constraint a0r7_lease_version_check check(version>=1)
);
create unique index if not exists uq_a0r7_one_active_step_lease
  on execution_leases_v206(tenant_id,task_id,step_id) where status='ACTIVE';
create index if not exists idx_a0r7_lease_expiry on execution_leases_v206(tenant_id,status,lease_until);

-- 4. Provider-neutral authoritative ExecutionAssignment.
create table if not exists execution_assignments_v206 (
  tenant_id varchar(64) not null,
  assignment_id varchar(180) not null,
  shadow_assignment_id varchar(180) not null,
  task_id varchar(128) not null,
  plan_id varchar(160) not null,
  plan_revision int not null,
  step_id varchar(160) not null,
  flow_id varchar(128) not null,
  routing_decision_id varchar(160) not null,
  envelope_id varchar(180) not null,
  binding_id varchar(160) not null,
  provider_id varchar(160) not null,
  provider_type varchar(40) not null,
  agent_pool_id varchar(128),
  selected_agent_id varchar(128),
  selected_session_id varchar(160),
  selected_peer_interface_id varchar(160),
  selected_mcp_server_id varchar(160),
  selected_adapter_id varchar(160) not null,
  adapter_type varchar(48) not null,
  execution_safety_mode varchar(40) not null,
  side_effect varchar(16) not null,
  write_semantics varchar(32),
  human_approval_ref varchar(255),
  lease_id varchar(180) not null,
  fencing_token bigint not null,
  lease_until timestamptz not null,
  attempt_number int not null default 1,
  previous_assignment_id varchar(180),
  authority_mode varchar(32) not null default 'NEW_AUTHORITATIVE',
  status varchar(32) not null default 'ASSIGNED',
  assignment_reason varchar(512) not null,
  created_at timestamptz not null default now(),
  released_at timestamptz,
  outcome varchar(40),
  primary key(tenant_id,assignment_id),
  unique(tenant_id,shadow_assignment_id),
  constraint fk_a0r7_assignment_shadow foreign key(tenant_id,shadow_assignment_id) references execution_assignment_shadows(tenant_id,assignment_id),
  constraint fk_a0r7_assignment_routing foreign key(tenant_id,routing_decision_id) references provider_routing_decisions(tenant_id,decision_id),
  constraint fk_a0r7_assignment_envelope foreign key(tenant_id,envelope_id) references binding_authorization_envelopes(tenant_id,envelope_id),
  constraint fk_a0r7_assignment_binding foreign key(tenant_id,binding_id) references capability_bindings(tenant_id,binding_id),
  constraint a0r7_assignment_authority_check check(authority_mode='NEW_AUTHORITATIVE'),
  constraint a0r7_assignment_safety_check check(execution_safety_mode in ('LOCAL_FENCED','REMOTE_NATIVE_IDEMPOTENT','REMOTE_UNFENCED')),
  constraint a0r7_assignment_side_effect_check check(side_effect in ('NONE','READ','WRITE')),
  constraint a0r7_assignment_attempt_check check(attempt_number>=1),
  constraint a0r7_assignment_fence_check check(fencing_token>=1),
  constraint a0r7_remote_unfenced_write_guard check(
    not (execution_safety_mode='REMOTE_UNFENCED' and side_effect='WRITE' and coalesce(write_semantics,'')<>'IDEMPOTENT' and human_approval_ref is null)
  )
);
create index if not exists idx_a0r7_assignment_step on execution_assignments_v206(tenant_id,task_id,step_id,created_at desc);
create index if not exists idx_a0r7_assignment_flow on execution_assignments_v206(tenant_id,flow_id,status,created_at desc);

alter table execution_leases_v206 drop constraint if exists fk_a0r7_lease_assignment;
alter table execution_leases_v206 add constraint fk_a0r7_lease_assignment
  foreign key(tenant_id,assignment_id) references execution_assignments_v206(tenant_id,assignment_id) deferrable initially deferred;

-- 5. Canonical durable DispatchIntent outbox. No network is allowed before this row commits.
create table if not exists execution_dispatch_intents_v206 (
  tenant_id varchar(64) not null,
  intent_id varchar(180) not null,
  assignment_id varchar(180) not null,
  lease_id varchar(180) not null,
  fencing_token bigint not null,
  task_id varchar(128) not null,
  plan_id varchar(160) not null,
  plan_revision int not null,
  step_id varchar(160) not null,
  flow_id varchar(128) not null,
  binding_id varchar(160) not null,
  provider_id varchar(160) not null,
  provider_type varchar(40) not null,
  selected_adapter_id varchar(160) not null,
  adapter_type varchar(48) not null,
  execution_safety_mode varchar(40) not null,
  side_effect varchar(16) not null,
  write_semantics varchar(32),
  payload_json jsonb not null default '{}'::jsonb,
  status varchar(40) not null default 'PENDING',
  attempt_count int not null default 0,
  claimed_by varchar(160),
  claim_token varchar(180),
  claim_until timestamptz,
  send_started_at timestamptz,
  handed_off_at timestamptz,
  external_execution_ref varchar(255),
  delivery_unknown_since timestamptz,
  last_error_code varchar(160),
  last_error_message text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key(tenant_id,intent_id),
  unique(tenant_id,assignment_id),
  constraint fk_a0r7_intent_assignment foreign key(tenant_id,assignment_id) references execution_assignments_v206(tenant_id,assignment_id),
  constraint fk_a0r7_intent_lease foreign key(tenant_id,lease_id) references execution_leases_v206(tenant_id,lease_id),
  constraint a0r7_intent_status_check check(status in ('PENDING','CLAIMED','SEND_STARTED','HANDED_OFF','SENT','ACKNOWLEDGED','DELIVERY_FAILED','DELIVERY_UNKNOWN','BLOCKED','CANCELLED')),
  constraint a0r7_intent_attempt_check check(attempt_count>=0),
  constraint a0r7_intent_fence_check check(fencing_token>=1),
  constraint a0r7_intent_payload_object check(jsonb_typeof(payload_json)='object')
);
create index if not exists idx_a0r7_intent_worker on execution_dispatch_intents_v206(tenant_id,status,updated_at,created_at);
create index if not exists idx_a0r7_intent_recovery on execution_dispatch_intents_v206(tenant_id,status,claim_until) where status in ('CLAIMED','SEND_STARTED','DELIVERY_UNKNOWN');

create table if not exists execution_dispatch_intent_events_v206 (
  tenant_id varchar(64) not null,
  event_id varchar(180) not null,
  intent_id varchar(180) not null,
  assignment_id varchar(180) not null,
  from_status varchar(40),
  to_status varchar(40) not null,
  reason_code varchar(160) not null,
  actor_ref varchar(255) not null,
  evidence_json jsonb not null default '{}'::jsonb,
  occurred_at timestamptz not null default now(),
  primary key(tenant_id,event_id),
  constraint fk_a0r7_intent_event foreign key(tenant_id,intent_id) references execution_dispatch_intents_v206(tenant_id,intent_id),
  constraint a0r7_intent_event_evidence_object check(jsonb_typeof(evidence_json)='object')
);
create index if not exists idx_a0r7_intent_events on execution_dispatch_intent_events_v206(tenant_id,intent_id,occurred_at);

-- 6. R7 compatibility mirror provenance on the existing Agent execution table.
alter table task_assignments add column if not exists execution_authority_version varchar(32);
alter table task_assignments add column if not exists canonical_execution_assignment_id varchar(180);
create index if not exists idx_task_assignments_a0r7_canonical on task_assignments(tenant_id,canonical_execution_assignment_id) where canonical_execution_assignment_id is not null;

-- 7. Database safety functions/triggers.
create or replace function a0r7_require_shadow_before_new_authority() returns trigger language plpgsql as $$
begin
  if new.migration_state='NEW_AUTHORITATIVE' and (TG_OP='INSERT' or old.migration_state is distinct from 'NEW_AUTHORITATIVE') then
    if not exists(select 1 from execution_assignment_shadows s join tasks t on t.tenant_id=s.tenant_id and t.task_id=s.task_id where s.tenant_id=new.tenant_id and t.matched_flow_id=new.flow_id) then
      raise exception 'A0_R7_NEW_AUTHORITY_REQUIRES_R6_SHADOW_EVIDENCE';
    end if;
  end if;
  return new;
end $$;
drop trigger if exists trg_a0r7_require_shadow on flow_routing_migration_state;
create trigger trg_a0r7_require_shadow before insert or update on flow_routing_migration_state
  for each row execute function a0r7_require_shadow_before_new_authority();

create or replace function a0r7_validate_dispatch_intent_fence() returns trigger language plpgsql as $$
declare a execution_assignments_v206%rowtype; l execution_leases_v206%rowtype;
begin
  select * into a from execution_assignments_v206 where tenant_id=new.tenant_id and assignment_id=new.assignment_id;
  select * into l from execution_leases_v206 where tenant_id=new.tenant_id and lease_id=new.lease_id;
  if a.assignment_id is null or l.lease_id is null then raise exception 'A0_R7_ASSIGNMENT_OR_LEASE_MISSING'; end if;
  if a.lease_id<>new.lease_id or a.fencing_token<>new.fencing_token or l.assignment_id<>new.assignment_id or l.fencing_token<>new.fencing_token then
    raise exception 'A0_R7_FENCING_MISMATCH';
  end if;
  if l.status<>'ACTIVE' or l.lease_until<=now() then raise exception 'A0_R7_LEASE_NOT_ACTIVE'; end if;
  return new;
end $$;
drop trigger if exists trg_a0r7_validate_intent_fence on execution_dispatch_intents_v206;
create trigger trg_a0r7_validate_intent_fence before insert or update of lease_id,fencing_token,assignment_id on execution_dispatch_intents_v206
  for each row execute function a0r7_validate_dispatch_intent_fence();

-- 8. Tenant isolation.
DO $$
DECLARE tbl text;
BEGIN
  FOREACH tbl IN ARRAY ARRAY['execution_fencing_counters','execution_leases_v206','execution_assignments_v206','execution_dispatch_intents_v206','execution_dispatch_intent_events_v206'] LOOP
    EXECUTE format('alter table %I enable row level security',tbl);
    EXECUTE format('drop policy if exists tenant_isolation on %I',tbl);
    EXECUTE format('create policy tenant_isolation on %I using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id())',tbl);
  END LOOP;
END $$;

create or replace function prevent_a0r7_event_mutation() returns trigger language plpgsql as $$
begin raise exception 'A0_R7_EVENT_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_a0r7_event_immutable on execution_dispatch_intent_events_v206;
create trigger trg_a0r7_event_immutable before update or delete on execution_dispatch_intent_events_v206 for each row execute function prevent_a0r7_event_mutation();

-- 9. Explainability/operations projection.
create or replace view execution_safety_trace_v206 as
select a.tenant_id,a.assignment_id,a.task_id,a.plan_id,a.plan_revision,a.step_id,a.flow_id,a.routing_decision_id,a.envelope_id,
       a.binding_id,a.provider_id,a.provider_type,a.adapter_type,a.execution_safety_mode,a.side_effect,a.write_semantics,
       a.lease_id,a.fencing_token,a.lease_until,a.status assignment_status,
       l.owner_node_id,l.status lease_status,l.lease_until as current_lease_until,
       i.intent_id,i.status intent_status,i.attempt_count,i.claimed_by,i.send_started_at,i.handed_off_at,i.external_execution_ref,i.delivery_unknown_since,
       s.assignment_id shadow_assignment_id,s.outcome shadow_outcome,a.created_at
  from execution_assignments_v206 a
  join execution_leases_v206 l on l.tenant_id=a.tenant_id and l.lease_id=a.lease_id
  join execution_dispatch_intents_v206 i on i.tenant_id=a.tenant_id and i.assignment_id=a.assignment_id
  join execution_assignment_shadows s on s.tenant_id=a.tenant_id and s.assignment_id=a.shadow_assignment_id;

insert into schema_contract_authority(contract_id,contract_family,authority_note,created_at,schema_version)
values('mrs-a0-r7-execution-safety-dispatch-authority','A0_R7_EXECUTION_SAFETY',
'PER_FLOW_CONTROLLED_LIVE_ONLY: EXECUTION_ASSIGNMENT_AND_LEASE_AND_DISPATCH_INTENT_MUST_COMMIT_BEFORE_NETWORK; MONOTONIC_FENCING; REMOTE_UNFENCED_NON_IDEMPOTENT_WRITE_DENIED_WITHOUT_HUMAN_APPROVAL; LEGACY_GLOBAL_DISPATCH_REMAINS_AVAILABLE_FOR_NON_CUTOVER_FLOWS',now(),'V206')
on conflict(contract_id) do nothing;
