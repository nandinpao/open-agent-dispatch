-- Phase 2D: durable dispatch outbox lifecycle, lease evidence and deterministic reconciliation.
alter table dispatch_requests add column if not exists outbox_status varchar(32) not null default 'PENDING';
alter table dispatch_requests add column if not exists claim_token varchar(128);
alter table dispatch_requests add column if not exists claim_heartbeat_at timestamptz;
alter table dispatch_requests add column if not exists dispatch_token_hash varchar(128);
alter table dispatch_requests add column if not exists fencing_token_hash varchar(128);
alter table dispatch_requests add column if not exists runtime_session_id varchar(128);
alter table dispatch_requests add column if not exists ack_evidence_id varchar(128);
alter table dispatch_requests add column if not exists acked_at timestamptz;
alter table dispatch_requests add column if not exists recovery_classification varchar(64) not null default 'NONE';
alter table dispatch_requests add column if not exists uncertain_since timestamptz;
alter table dispatch_requests add column if not exists last_reconciled_at timestamptz;
alter table dispatch_requests add column if not exists reconciliation_count integer not null default 0;
alter table dispatch_requests add column if not exists row_version bigint not null default 0;

update dispatch_requests set outbox_status = case
  when status in ('PENDING_REVIEW','APPROVED') then 'PENDING'
  when status = 'DISPATCHING' and claimed_by is not null then 'DISPATCHING'
  when status in ('DISPATCHED','ACKED','RUNNING','COMPLETED') then 'ACKNOWLEDGED'
  when status in ('RETRY_WAITING','FAILED','TIMED_OUT') then 'FAILED_RETRYABLE'
  when status in ('DEAD_LETTER','CANCELLED','REJECTED','SUPPRESSED') then 'DEAD_LETTER'
  else outbox_status end;

alter table dispatch_requests drop constraint if exists ck_dispatch_outbox_status_p2d;
alter table dispatch_requests add constraint ck_dispatch_outbox_status_p2d
  check(outbox_status in ('PENDING','CLAIMED','DISPATCHING','ACKNOWLEDGED','FAILED_RETRYABLE','DEAD_LETTER'));
alter table dispatch_requests drop constraint if exists ck_dispatch_recovery_classification_p2d;
alter table dispatch_requests add constraint ck_dispatch_recovery_classification_p2d
  check(recovery_classification in ('NONE','RESPONSE_LOST','ASSIGNMENT_STATE_UNCERTAIN','ACK_PERSISTENCE_UNCERTAIN','LEASE_EXPIRED','RETRY_EXHAUSTED','POLICY_OR_PERMISSION_BLOCKED'));
alter table dispatch_requests drop constraint if exists ck_dispatch_claim_evidence_p2d;
alter table dispatch_requests add constraint ck_dispatch_claim_evidence_p2d check(
  outbox_status not in ('CLAIMED','DISPATCHING') or
  (claimed_by is not null and claim_token is not null and claim_until is not null)
);

create index if not exists idx_dispatch_requests_p2d_claim
  on dispatch_requests(outbox_status, claim_until, updated_at)
  where outbox_status in ('CLAIMED','DISPATCHING');
create index if not exists idx_dispatch_requests_p2d_recovery
  on dispatch_requests(recovery_classification, uncertain_since, updated_at)
  where recovery_classification <> 'NONE';

create table if not exists dispatch_assignment_evidence (
  evidence_id varchar(128) primary key,
  tenant_id varchar(64) not null,
  dispatch_request_id varchar(128) not null,
  assignment_id varchar(128),
  task_id varchar(128) not null,
  agent_id varchar(128),
  attempt_no integer not null,
  event_type varchar(64) not null,
  outbox_status varchar(32),
  worker_id varchar(128),
  claim_token_hash varchar(128),
  claim_until timestamptz,
  dispatch_token_hash varchar(128),
  fencing_token_hash varchar(128),
  runtime_session_id varchar(128),
  ack_evidence_id varchar(128),
  gateway_status varchar(128),
  recovery_classification varchar(64),
  evidence_json jsonb not null default '{}'::jsonb,
  occurred_at timestamptz not null,
  created_at timestamptz not null default now(),
  constraint fk_dispatch_assignment_evidence_request foreign key(dispatch_request_id) references dispatch_requests(dispatch_request_id)
);
create unique index if not exists uq_dispatch_assignment_evidence_event
  on dispatch_assignment_evidence(dispatch_request_id, attempt_no, event_type);
create index if not exists idx_dispatch_assignment_evidence_task
  on dispatch_assignment_evidence(tenant_id, task_id, occurred_at desc);

create or replace function prevent_dispatch_assignment_evidence_mutation() returns trigger language plpgsql as $$
begin
  raise exception 'dispatch_assignment_evidence is append-only';
end $$;
drop trigger if exists trg_dispatch_assignment_evidence_append_only on dispatch_assignment_evidence;
create trigger trg_dispatch_assignment_evidence_append_only
before update or delete on dispatch_assignment_evidence
for each row execute function prevent_dispatch_assignment_evidence_mutation();
