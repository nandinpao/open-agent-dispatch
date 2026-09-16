-- Stage 8 — A2A Async Lifecycle / Reconciliation.
-- Adds remote task tracking, polling/stream/push/cancel/reconciliation and append-only remote event evidence.
-- Remote WRITE remains forbidden.

alter table a2a_remote_read_executions
  add column if not exists tracking_mode varchar(24),
  add column if not exists tracking_status varchar(32),
  add column if not exists tracking_id varchar(180),
  add column if not exists last_remote_event_at timestamptz,
  add column if not exists last_reconciled_at timestamptz,
  add column if not exists cancel_requested_at timestamptz,
  add column if not exists cancel_completed_at timestamptz,
  add column if not exists terminal_at timestamptz;

alter table a2a_remote_read_executions drop constraint if exists a2a_remote_tracking_mode_check;
alter table a2a_remote_read_executions add constraint a2a_remote_tracking_mode_check
  check (tracking_mode is null or tracking_mode in ('POLL','STREAM','PUSH','HYBRID'));
alter table a2a_remote_read_executions drop constraint if exists a2a_remote_tracking_status_check;
alter table a2a_remote_read_executions add constraint a2a_remote_tracking_status_check
  check (tracking_status is null or tracking_status in ('PENDING','ACTIVE','RECONCILING','CANCELING','TERMINAL','FAILED'));

create table if not exists a2a_remote_tracking_leases (
  tenant_id varchar(64) not null,
  tracking_id varchar(180) not null,
  execution_id varchar(180) not null,
  delegation_id varchar(160) not null,
  remote_task_id varchar(255) not null,
  peer_id varchar(160) not null,
  interface_id varchar(180) not null,
  tracking_mode varchar(24) not null,
  status varchar(32) not null default 'PENDING',
  owner_instance_id varchar(160),
  lease_token varchar(180),
  lease_until timestamptz,
  next_poll_at timestamptz,
  poll_interval_seconds int not null default 10,
  push_config_id varchar(255),
  push_token_hash varchar(128),
  stream_last_event_id varchar(255),
  failure_count int not null default 0,
  last_error text,
  last_observed_state varchar(64),
  last_observed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id,tracking_id),
  unique (tenant_id,execution_id),
  constraint fk_a2a_tracking_execution foreign key (tenant_id,execution_id) references a2a_remote_read_executions(tenant_id,execution_id) on delete cascade,
  constraint a2a_tracking_mode_check check (tracking_mode in ('POLL','STREAM','PUSH','HYBRID')),
  constraint a2a_tracking_status_check check (status in ('PENDING','ACTIVE','RECONCILING','CANCELING','TERMINAL','FAILED')),
  constraint a2a_tracking_poll_interval_check check (poll_interval_seconds between 1 and 3600),
  constraint a2a_tracking_failure_count_check check (failure_count>=0)
);
create index if not exists idx_a2a_tracking_due
  on a2a_remote_tracking_leases(tenant_id,status,next_poll_at,lease_until)
  where status in ('PENDING','ACTIVE','RECONCILING','CANCELING');

create table if not exists a2a_remote_event_journal (
  tenant_id varchar(64) not null,
  journal_event_id varchar(180) not null,
  tracking_id varchar(180) not null,
  execution_id varchar(180) not null,
  remote_task_id varchar(255) not null,
  source varchar(24) not null,
  event_type varchar(64) not null,
  remote_state varchar(64),
  remote_event_id varchar(255),
  dedup_key varchar(255) not null,
  payload_hash varchar(128) not null,
  payload_json jsonb not null,
  observed_at timestamptz not null default now(),
  primary key (tenant_id,journal_event_id),
  unique (tenant_id,tracking_id,dedup_key),
  constraint fk_a2a_journal_tracking foreign key (tenant_id,tracking_id) references a2a_remote_tracking_leases(tenant_id,tracking_id) on delete cascade,
  constraint a2a_journal_source_check check (source in ('POLL','STREAM','PUSH','CANCEL','RECONCILIATION')),
  constraint a2a_journal_payload_object check (jsonb_typeof(payload_json)='object')
);
create index if not exists idx_a2a_remote_event_journal_tracking on a2a_remote_event_journal(tenant_id,tracking_id,observed_at,journal_event_id);

create or replace function prevent_a2a_remote_event_journal_mutation()
returns trigger language plpgsql as $$ begin raise exception 'A2A_REMOTE_EVENT_JOURNAL_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_a2a_remote_event_journal_immutable on a2a_remote_event_journal;
create trigger trg_a2a_remote_event_journal_immutable before update or delete on a2a_remote_event_journal
for each row execute function prevent_a2a_remote_event_journal_mutation();

create table if not exists a2a_push_inbox (
  tenant_id varchar(64) not null,
  inbox_id varchar(180) not null,
  tracking_id varchar(180) not null,
  remote_task_id varchar(255) not null,
  authorization_fingerprint varchar(128),
  payload_hash varchar(128) not null,
  payload_json jsonb not null,
  processing_status varchar(24) not null default 'RECEIVED',
  received_at timestamptz not null default now(),
  processed_at timestamptz,
  error_message text,
  primary key (tenant_id,inbox_id),
  unique (tenant_id,tracking_id,payload_hash),
  constraint fk_a2a_push_tracking foreign key (tenant_id,tracking_id) references a2a_remote_tracking_leases(tenant_id,tracking_id) on delete cascade,
  constraint a2a_push_inbox_status_check check (processing_status in ('RECEIVED','PROCESSED','REJECTED','CONFLICT')),
  constraint a2a_push_inbox_payload_object check (jsonb_typeof(payload_json)='object')
);

alter table a2a_remote_tracking_leases enable row level security;
alter table a2a_remote_event_journal enable row level security;
alter table a2a_push_inbox enable row level security;
do $$ declare t text; begin foreach t in array array['a2a_remote_tracking_leases','a2a_remote_event_journal','a2a_push_inbox'] loop execute format('drop policy if exists tenant_isolation on %I',t); execute format('create policy tenant_isolation on %I using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id())',t); end loop; end $$;

insert into schema_contract_authority(contract_id,contract_family,authority_note,created_at,schema_version)
values('stage8-a2a-async-lifecycle-reconciliation-v1','STAGE8_A2A_ASYNC_LIFECYCLE_RECONCILIATION','REMOTE_READ_TASKS_MAY_BE_TRACKED_BY_POLL_STREAM_PUSH_OR_HYBRID; TRACKING_LEASE_IS_SINGLE_OWNER; REMOTE_EVENT_JOURNAL_APPEND_ONLY; CANCEL_AND_RECONCILIATION_ARE_DURABLE; REMOTE_WRITE_FORBIDDEN','now'::timestamptz,'V194')
on conflict(contract_id) do nothing;
