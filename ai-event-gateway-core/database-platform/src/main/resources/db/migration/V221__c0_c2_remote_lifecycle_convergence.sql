-- C0-C2 — Remote Lifecycle Convergence.
-- Preserves C0-C1 RemoteTrackingLease authority and closes multi-replica lease renewal,
-- durable PUSH handoff, crash/takeover, terminal/cancel races, and retry/reconciliation convergence.
-- C0-B8 Credential Binding / Outbound Destination Policy snapshots remain unchanged.

-- -----------------------------------------------------------------------------
-- 1. Lifecycle convergence remains on the existing Stage 8/C0-C1 tracking row.
--    No second remote authority table is introduced.
-- -----------------------------------------------------------------------------
alter table a2a_remote_tracking_leases
  add column if not exists lifecycle_version bigint not null default 0,
  add column if not exists lease_renewed_at timestamptz,
  add column if not exists lease_renewal_count bigint not null default 0,
  add column if not exists cancellation_requested_at timestamptz,
  add column if not exists terminal_outcome varchar(24),
  add column if not exists terminal_source varchar(32),
  add column if not exists terminal_remote_state varchar(64),
  add column if not exists terminal_authority_epoch bigint,
  add column if not exists terminal_stream_id varchar(320),
  add column if not exists terminal_journal_event_id varchar(180),
  add column if not exists terminalized_at timestamptz;

alter table a2a_remote_tracking_leases drop constraint if exists ck_a2a_remote_lifecycle_version_c0c2;
alter table a2a_remote_tracking_leases add constraint ck_a2a_remote_lifecycle_version_c0c2
  check (lifecycle_version >= 0 and lease_renewal_count >= 0);

alter table a2a_remote_tracking_leases drop constraint if exists ck_a2a_remote_terminal_outcome_c0c2;
alter table a2a_remote_tracking_leases add constraint ck_a2a_remote_terminal_outcome_c0c2
  check (terminal_outcome is null or terminal_outcome in ('SUCCEEDED','FAILED','BLOCKED','CANCELED'));

alter table a2a_remote_tracking_leases drop constraint if exists ck_a2a_remote_terminal_consistency_c0c2;
alter table a2a_remote_tracking_leases add constraint ck_a2a_remote_terminal_consistency_c0c2
  check (
    (terminal_outcome is null and terminalized_at is null)
    or
    (terminal_outcome is not null and terminalized_at is not null and terminal_authority_epoch is not null)
  );

create index if not exists idx_a2a_remote_tracking_lifecycle_c0c2
  on a2a_remote_tracking_leases(tenant_id,status,lifecycle_version,terminal_outcome,lease_until);

comment on column a2a_remote_tracking_leases.lifecycle_version is
'C0-C2 monotonic canonical remote lifecycle version. Cancellation requests and terminal CAS decisions increment this value.';
comment on column a2a_remote_tracking_leases.lease_renewed_at is
'C0-C2 last successful fenced renewal timestamp. Renewal cannot revive an expired/stale lease.';
comment on column a2a_remote_tracking_leases.lease_renewal_count is
'C0-C2 count of successful owner+token+epoch+stream fenced renewals.';
comment on column a2a_remote_tracking_leases.terminal_outcome is
'C0-C2 exactly-once canonical remote terminal outcome. A non-null value fences all later terminal/cancel mutations.';
comment on column a2a_remote_tracking_leases.terminal_authority_epoch is
'C0-C2 authority epoch that won terminal CAS. Stale epochs remain evidence only.';
comment on column a2a_remote_tracking_leases.terminal_stream_id is
'C0-C2 authoritative stream that won terminal CAS; transport arrival order alone never chooses the outcome.';

-- -----------------------------------------------------------------------------
-- 2. Durable owner-independent PUSH handoff.
--    Ingress stores first; the current lease owner later claims/observes the event.
-- -----------------------------------------------------------------------------
alter table a2a_push_inbox
  add column if not exists target_owner_instance_id varchar(160),
  add column if not exists target_authority_epoch bigint,
  add column if not exists handoff_available_at timestamptz,
  add column if not exists handoff_attempt_count int not null default 0,
  add column if not exists claimed_by varchar(160),
  add column if not exists claim_token varchar(180),
  add column if not exists claim_until timestamptz,
  add column if not exists last_handoff_error text;

alter table a2a_push_inbox drop constraint if exists a2a_push_inbox_status_check;
alter table a2a_push_inbox add constraint a2a_push_inbox_status_check
  check (processing_status in ('RECEIVED','HANDOFF_PENDING','CLAIMED','PROCESSED','REJECTED','CONFLICT'));

alter table a2a_push_inbox drop constraint if exists ck_a2a_push_handoff_attempt_c0c2;
alter table a2a_push_inbox add constraint ck_a2a_push_handoff_attempt_c0c2
  check (handoff_attempt_count >= 0);

create index if not exists idx_a2a_push_handoff_due_c0c2
  on a2a_push_inbox(tenant_id,processing_status,handoff_available_at,claim_until,received_at)
  where processing_status in ('RECEIVED','HANDOFF_PENDING','CLAIMED');

comment on column a2a_push_inbox.target_owner_instance_id is
'C0-C2 best-known lease owner at ingress time. It is routing evidence only; a later valid takeover owner may claim the durable inbox row.';
comment on column a2a_push_inbox.target_authority_epoch is
'C0-C2 best-known authority epoch at ingress. It does not make the callback authoritative and may become stale before processing.';
comment on column a2a_push_inbox.claim_token is
'C0-C2 opaque inbox-work claim token. It is independent of the RemoteTrackingLease token and cannot grant lifecycle authority.';

-- -----------------------------------------------------------------------------
-- 3. Execution projection exposes the winning remote lifecycle decision.
-- -----------------------------------------------------------------------------
alter table a2a_remote_read_executions
  add column if not exists remote_lifecycle_version bigint not null default 0,
  add column if not exists remote_terminal_outcome varchar(24),
  add column if not exists remote_terminal_authority_epoch bigint,
  add column if not exists remote_terminal_stream_id varchar(320);

alter table a2a_remote_read_executions drop constraint if exists ck_a2a_execution_remote_terminal_outcome_c0c2;
alter table a2a_remote_read_executions add constraint ck_a2a_execution_remote_terminal_outcome_c0c2
  check (remote_terminal_outcome is null or remote_terminal_outcome in ('SUCCEEDED','FAILED','BLOCKED','CANCELED'));

-- -----------------------------------------------------------------------------
-- 4. Current contract authority.
-- -----------------------------------------------------------------------------
insert into schema_contract_authority(contract_id,contract_family,authority_note,created_at,schema_version)
values(
  'c0-c2-remote-lifecycle-convergence',
  'C0_C2_REMOTE_LIFECYCLE_CONVERGENCE',
  'C0-C1 LEASE OWNER TOKEN EXPIRY EPOCH STREAM REMAINS AUTHORITY; LEASE RENEWAL CANNOT REVIVE EXPIRED AUTHORITY; PUSH INGRESS IS DURABLE HANDOFF TO CURRENT OWNER; CANCEL AND TERMINAL RACE THROUGH ONE TRACKING ROW LIFECYCLE VERSION AND EXACTLY-ONCE TERMINAL CAS; CRASH TAKEOVER ADVANCES EPOCH; STALE RETRY RECONCILIATION OR TERMINAL WORK CANNOT MUTATE CANONICAL LIFECYCLE',
  now(),
  'V221'
)
on conflict(contract_id) do nothing;
