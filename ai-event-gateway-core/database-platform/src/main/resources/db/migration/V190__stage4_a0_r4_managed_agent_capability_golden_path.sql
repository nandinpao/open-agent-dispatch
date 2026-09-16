-- Stage 4 / A0-R4: Managed Agent Capability Golden Path
--
-- Authority contract:
--   Agent runtime proposes WHAT only (CapabilityRequirement).
--   Core resolves WHO CAN -> WHO MAY -> WHO SHOULD -> HOW server-side.
--   The first executable provider type is MANAGED_AGENT through MANAGED_AGENT_NETTY.
--   Caller-supplied target Agent/Pool/Domain/System/Provider/Binding/transport is forbidden.
--   Child execution converges on canonical task_assignments -> dispatch_requests -> Netty.

create table if not exists capability_delegation_requests (
  tenant_id varchar(64) not null,
  delegation_id varchar(160) not null,
  parent_task_id varchar(128) not null,
  requesting_agent_id varchar(128) not null,
  requesting_agent_session_id varchar(160),
  gateway_node_id varchar(160),
  idempotency_key varchar(200) not null,
  request_digest varchar(128) not null,
  capability_code varchar(160) not null,
  operation varchar(80) not null,
  requirement_json jsonb not null,
  reason text not null,
  input_payload_ref varchar(512),
  sensitivity_level varchar(64),
  status varchar(40) not null default 'RECEIVED',
  authorization_decision_id varchar(160),
  routing_decision_id varchar(160),
  adapter_resolution_id varchar(160),
  selected_binding_id varchar(160),
  selected_provider_id varchar(160),
  child_task_id varchar(128),
  assignment_id varchar(160),
  dispatch_request_id varchar(160),
  result_callback_id varchar(128),
  result_callback_fingerprint varchar(255),
  result_payload_hash varchar(255),
  result_status varchar(64),
  result_message text,
  result_error_code varchar(128),
  result_error_message text,
  completed_by_agent_id varchar(128),
  result_assignment_id varchar(160),
  result_dispatch_request_id varchar(160),
  result_received_at timestamptz,
  result_notification_id varchar(220),
  result_notification_status varchar(32) not null default 'NOT_REQUIRED',
  result_notification_attempts int not null default 0,
  result_notification_error text,
  result_notified_parent_assignment_id varchar(160),
  result_notified_agent_id varchar(128),
  result_notified_agent_session_id varchar(160),
  result_notified_gateway_node_id varchar(160),
  result_notified_at timestamptz,
  correlation_id varchar(160) not null,
  reason_codes_json jsonb not null default '[]'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id,delegation_id),
  unique (tenant_id,parent_task_id,requesting_agent_id,idempotency_key),
  constraint fk_capability_delegation_parent_task
    foreign key (tenant_id,parent_task_id) references tasks(tenant_id,task_id) on delete cascade,
  constraint capability_delegation_status_check check (status in (
    'RECEIVED','WAITING_APPROVAL','NO_CANDIDATE','ROUTING_UNAVAILABLE','HOW_UNAVAILABLE',
    'AUTHORIZED','CHILD_CREATED','DISPATCH_QUEUED','RESULT_SUCCEEDED','RESULT_FAILED','FAILED')),
  constraint capability_delegation_result_notification_status_check check (result_notification_status in (
    'NOT_REQUIRED','PENDING','DELIVERED','FAILED')),
  constraint capability_delegation_result_notification_attempts_check check (result_notification_attempts >= 0),
  constraint capability_delegation_reason_codes_array check (jsonb_typeof(reason_codes_json)='array')
);
create index if not exists idx_capability_delegation_parent
  on capability_delegation_requests(tenant_id,parent_task_id,created_at desc);
create index if not exists idx_capability_delegation_status
  on capability_delegation_requests(tenant_id,status,updated_at desc);
create index if not exists idx_capability_delegation_child
  on capability_delegation_requests(tenant_id,child_task_id) where child_task_id is not null;
create unique index if not exists uq_capability_delegation_result_callback
  on capability_delegation_requests(tenant_id,result_callback_id) where result_callback_id is not null;
create index if not exists idx_capability_delegation_result_notification
  on capability_delegation_requests(tenant_id,result_notification_status,updated_at desc)
  where result_notification_status in ('PENDING','FAILED');

alter table capability_delegation_requests enable row level security;
drop policy if exists tenant_isolation on capability_delegation_requests;
create policy tenant_isolation on capability_delegation_requests
  using (tenant_id=iam_current_tenant_id())
  with check (tenant_id=iam_current_tenant_id());

comment on table capability_delegation_requests is
  'Stage 4 Agent-originated capability-first runtime authority record. No target topology fields are accepted; selected provider/binding are server-side decision evidence.';

create table if not exists capability_delegation_events (
  tenant_id varchar(64) not null,
  event_id varchar(160) not null,
  delegation_id varchar(160) not null,
  event_type varchar(80) not null,
  from_status varchar(40),
  to_status varchar(40),
  reason_codes_json jsonb not null default '[]'::jsonb,
  evidence_json jsonb not null default '{}'::jsonb,
  occurred_at timestamptz not null default now(),
  primary key (tenant_id,event_id),
  constraint fk_capability_delegation_event_request
    foreign key (tenant_id,delegation_id)
    references capability_delegation_requests(tenant_id,delegation_id) on delete cascade,
  constraint capability_delegation_event_reasons_array check (jsonb_typeof(reason_codes_json)='array'),
  constraint capability_delegation_event_evidence_object check (jsonb_typeof(evidence_json)='object')
);
create index if not exists idx_capability_delegation_events
  on capability_delegation_events(tenant_id,delegation_id,occurred_at,event_id);

alter table capability_delegation_events enable row level security;
drop policy if exists tenant_isolation on capability_delegation_events;
create policy tenant_isolation on capability_delegation_events
  using (tenant_id=iam_current_tenant_id())
  with check (tenant_id=iam_current_tenant_id());

create or replace function prevent_capability_delegation_event_mutation()
returns trigger language plpgsql as $$
begin raise exception 'CAPABILITY_DELEGATION_EVENT_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_capability_delegation_event_immutable on capability_delegation_events;
create trigger trg_capability_delegation_event_immutable
before update or delete on capability_delegation_events
for each row execute function prevent_capability_delegation_event_mutation();

insert into schema_contract_authority(contract_id,contract_family,authority_note,created_at,schema_version)
values(
  'stage4-a0-r4-managed-agent-capability-golden-path-v1',
  'A0_R4_MANAGED_AGENT_CAPABILITY_GOLDEN_PATH',
  'AGENT_PROPOSES_CAPABILITY_ONLY; CORE_RESOLVES_PROVIDER; MANAGED_AGENT_NETTY_CONVERGES_ON_CANONICAL_TASK_ASSIGNMENT_DISPATCH_NETTY_ACK_RESULT; TERMINAL_RESULT_IS_PERSISTED_BEFORE_CAPABILITY_DELEGATION_RESULT_NOTIFICATION_TO_CURRENT_PARENT_AGENT; NO_TARGET_TOPOLOGY_INPUT',
  now(),
  'V190')
on conflict(contract_id) do nothing;
