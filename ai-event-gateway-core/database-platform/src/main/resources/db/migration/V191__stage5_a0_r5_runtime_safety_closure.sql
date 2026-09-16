-- Stage 5 / A0-R5: Runtime Safety Closure for capability-governed managed-agent execution.
-- This migration is additive. Existing Dispatch / Callback authority remains canonical.

create table if not exists capability_runtime_authorization_envelopes (
  tenant_id varchar(64) not null,
  envelope_id varchar(180) not null,
  delegation_id varchar(160) not null,
  assignment_id varchar(160) not null,
  binding_id varchar(160) not null,
  authorization_decision_id varchar(160) not null,
  routing_decision_id varchar(160) not null,
  authorization_epoch bigint not null default 1,
  revocation_version bigint not null default 0,
  status varchar(24) not null default 'ACTIVE',
  valid_until timestamptz,
  revoked_at timestamptz,
  revocation_reason text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id,envelope_id),
  unique (tenant_id,delegation_id),
  unique (tenant_id,assignment_id),
  constraint capability_runtime_authorization_envelope_status_check
    check (status in ('ACTIVE','REVOKED','EXPIRED')),
  constraint capability_runtime_authorization_envelope_epoch_check
    check (authorization_epoch >= 1 and revocation_version >= 0),
  constraint fk_capability_runtime_authorization_envelope_delegation
    foreign key (tenant_id,delegation_id)
    references capability_delegation_requests(tenant_id,delegation_id) on delete cascade
);
create index if not exists idx_cap_runtime_auth_envelope_status
  on capability_runtime_authorization_envelopes(tenant_id,status,valid_until);
alter table capability_runtime_authorization_envelopes enable row level security;
drop policy if exists tenant_isolation on capability_runtime_authorization_envelopes;
create policy tenant_isolation on capability_runtime_authorization_envelopes
  using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id());

create table if not exists capability_runtime_safety_events (
  tenant_id varchar(64) not null,
  event_id varchar(180) not null,
  delegation_id varchar(160) not null,
  assignment_id varchar(160),
  event_type varchar(96) not null,
  reason_code varchar(128) not null,
  authorization_epoch bigint,
  revocation_version bigint,
  evidence_json jsonb not null default '{}'::jsonb,
  occurred_at timestamptz not null default now(),
  primary key (tenant_id,event_id),
  constraint capability_runtime_safety_event_evidence_object check (jsonb_typeof(evidence_json)='object'),
  constraint fk_capability_runtime_safety_event_delegation
    foreign key (tenant_id,delegation_id)
    references capability_delegation_requests(tenant_id,delegation_id) on delete cascade
);
create index if not exists idx_cap_runtime_safety_events_delegation
  on capability_runtime_safety_events(tenant_id,delegation_id,occurred_at,event_id);
alter table capability_runtime_safety_events enable row level security;
drop policy if exists tenant_isolation on capability_runtime_safety_events;
create policy tenant_isolation on capability_runtime_safety_events
  using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id());

create or replace function prevent_capability_runtime_safety_event_mutation()
returns trigger language plpgsql as $$
begin raise exception 'CAPABILITY_RUNTIME_SAFETY_EVENT_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_capability_runtime_safety_event_immutable on capability_runtime_safety_events;
create trigger trg_capability_runtime_safety_event_immutable
before update or delete on capability_runtime_safety_events
for each row execute function prevent_capability_runtime_safety_event_mutation();

alter table capability_delegation_requests
  add column if not exists result_payload_json jsonb,
  add column if not exists result_notification_next_retry_at timestamptz,
  add column if not exists result_notification_max_attempts int not null default 8,
  add column if not exists result_notification_claimed_by varchar(160),
  add column if not exists result_notification_claim_until timestamptz,
  add column if not exists finalization_status varchar(32) not null default 'NOT_STARTED',
  add column if not exists terminalization_reason varchar(96),
  add column if not exists finalized_at timestamptz,
  add column if not exists finalization_error text;

alter table capability_delegation_requests
  drop constraint if exists capability_delegation_result_notification_max_attempts_check;
alter table capability_delegation_requests
  add constraint capability_delegation_result_notification_max_attempts_check
  check (result_notification_max_attempts between 1 and 100);
alter table capability_delegation_requests
  drop constraint if exists capability_delegation_finalization_status_check;
alter table capability_delegation_requests
  add constraint capability_delegation_finalization_status_check
  check (finalization_status in ('NOT_STARTED','PENDING_NOTIFICATION','COMPLETED','FAILED'));
create index if not exists idx_capability_delegation_notification_retry
  on capability_delegation_requests(tenant_id,result_notification_status,result_notification_next_retry_at)
  where result_notification_status in ('PENDING','FAILED');

insert into schema_contract_authority(contract_id,contract_family,authority_note,created_at,schema_version)
values(
  'stage5-a0-r5-runtime-safety-closure-v1',
  'A0_R5_RUNTIME_SAFETY_CLOSURE',
  'CAPABILITY_GOVERNED_DISPATCH_RECHECKS_LOCAL_FENCING_LEASE_AND_AUTHORIZATION_ENVELOPE_BEFORE_NETWORK_SEND; RESULT_NOTIFICATION_RETRY_IS_DURABLE; REVOCATION_USES_EPOCH_VERSION; FINALIZATION_IS_EXPLICIT; LEGACY_DISPATCH_AUTHORITY_REMAINS_CANONICAL',
  now(),
  'V191')
on conflict(contract_id) do nothing;
