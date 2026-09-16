-- C0-C1 — Authoritative Remote Stream.
-- Closes the Stage 8 ambiguity where POLL / STREAM / PUSH / RECONCILIATION evidence could all be
-- persisted as authoritative without proving current lease ownership, authority epoch, or stream identity.
--
-- C0-C1 is additive to C0-B8. Credential Binding / Outbound Destination Policy authority is unchanged.

-- -----------------------------------------------------------------------------
-- 1. Extend the existing durable remote tracking lease; do not create a second authority table.
-- -----------------------------------------------------------------------------
alter table a2a_remote_tracking_leases
  add column if not exists authority_epoch bigint not null default 0,
  add column if not exists authoritative_stream_id varchar(320),
  add column if not exists authority_changed_at timestamptz;

alter table a2a_remote_tracking_leases drop constraint if exists ck_a2a_remote_tracking_authority_epoch_c0c1;
alter table a2a_remote_tracking_leases add constraint ck_a2a_remote_tracking_authority_epoch_c0c1
  check (authority_epoch >= 0);

create index if not exists idx_a2a_remote_tracking_current_authority_c0c1
  on a2a_remote_tracking_leases(tenant_id,tracking_id,authority_epoch,authoritative_stream_id,lease_until);

comment on column a2a_remote_tracking_leases.owner_instance_id is
'C0-C1 current runtime owner. An event cannot mutate canonical remote lifecycle unless this owner matches the event authority context.';
comment on column a2a_remote_tracking_leases.lease_token is
'C0-C1 opaque fencing token for the current owner. A stale owner/token cannot mutate canonical remote lifecycle.';
comment on column a2a_remote_tracking_leases.lease_until is
'C0-C1 lease expiry. Expired ownership is never authoritative even when owner/token/epoch values otherwise match.';
comment on column a2a_remote_tracking_leases.authority_epoch is
'C0-C1 monotonically increasing authority generation. Every new ownership claim or explicit transport authority switch advances the epoch.';
comment on column a2a_remote_tracking_leases.authoritative_stream_id is
'C0-C1 single authoritative transport stream for the current authority epoch. Other transports remain observation/evidence only.';
comment on column a2a_remote_tracking_leases.tracking_mode is
'C0-C1 configured tracking capability. tracking_mode does not itself grant authority; current lease owner + token + epoch + authoritative_stream_id do.';

-- -----------------------------------------------------------------------------
-- 2. Make append-only journal authority classification explicit and auditable.
--    Historical rows retain their historical flag but are distinguishable as PRE_C0_C1.
-- -----------------------------------------------------------------------------
alter table a2a_remote_event_journal
  add column if not exists authority_epoch bigint,
  add column if not exists lease_owner_instance_id varchar(160),
  add column if not exists lease_token_fingerprint varchar(128),
  add column if not exists authority_decision_reason varchar(80),
  add column if not exists authority_contract_version varchar(32) not null default 'PRE_C0_C1';

create index if not exists idx_a2a_remote_event_authority_c0c1
  on a2a_remote_event_journal(tenant_id,tracking_id,authority_contract_version,is_authoritative,authority_epoch,local_remote_sequence);

comment on column a2a_remote_event_journal.stream_id is
'C0-C1 event stream identity. For current authority the value must equal the lease authoritative_stream_id at append time.';
comment on column a2a_remote_event_journal.is_authoritative is
'C0-C1 canonical mutation eligibility captured at append time. Current rows are true only when owner, lease token, lease expiry, authority epoch, and authoritative stream all match.';
comment on column a2a_remote_event_journal.authority_epoch is
'C0-C1 authority generation presented by the event producer. Stale epochs are evidence only.';
comment on column a2a_remote_event_journal.lease_owner_instance_id is
'C0-C1 event producer instance identity used for current-owner fencing.';
comment on column a2a_remote_event_journal.lease_token_fingerprint is
'C0-C1 SHA-256 fingerprint of the presented lease token; raw lease tokens are never copied into append-only event evidence.';
comment on column a2a_remote_event_journal.authority_decision_reason is
'C0-C1 immutable classification reason such as CURRENT_AUTHORITY, OWNER_MISMATCH, LEASE_EXPIRED, AUTHORITY_EPOCH_STALE, or NON_AUTHORITATIVE_STREAM.';
comment on column a2a_remote_event_journal.authority_contract_version is
'PRE_C0_C1 identifies legacy unfenced authority classification. C0_C1_V1 identifies events classified by current owner/token/expiry/epoch/stream fencing.';
comment on column a2a_remote_event_journal.local_remote_sequence is
'OpenDispatch-local append order only. It is not peer sequence authority and does not make an observation authoritative.';

-- -----------------------------------------------------------------------------
-- 3. Push inbox records how a callback was classified without making ingress itself authority.
-- -----------------------------------------------------------------------------
alter table a2a_push_inbox
  add column if not exists authority_epoch bigint,
  add column if not exists stream_id varchar(320),
  add column if not exists authority_classification varchar(24),
  add column if not exists authority_reason varchar(80);

alter table a2a_push_inbox drop constraint if exists ck_a2a_push_authority_classification_c0c1;
alter table a2a_push_inbox add constraint ck_a2a_push_authority_classification_c0c1
  check (authority_classification is null or authority_classification in ('AUTHORITATIVE','OBSERVATION'));

comment on column a2a_push_inbox.authority_classification is
'C0-C1 PUSH delivery classification. Authentication proves callback admission only; it does not prove current remote lifecycle authority.';

-- -----------------------------------------------------------------------------
-- 4. Current contract authority.
-- -----------------------------------------------------------------------------
insert into schema_contract_authority(contract_id,contract_family,authority_note,created_at,schema_version)
values(
  'c0-c1-authoritative-remote-stream',
  'C0_C1_AUTHORITATIVE_REMOTE_STREAM',
  'ONE CURRENT REMOTE TRACKING LEASE OWNER; LEASE TOKEN + LEASE EXPIRY + AUTHORITY EPOCH + AUTHORITATIVE STREAM FENCE CANONICAL LIFECYCLE MUTATION; POLL STREAM PUSH CANCEL RECONCILIATION THAT FAIL THE FENCE ARE APPEND-ONLY OBSERVATION/EVIDENCE; EXPLICIT TRANSPORT FALLBACK MUST ADVANCE AUTHORITY EPOCH',
  now(),
  'V220'
)
on conflict(contract_id) do nothing;
