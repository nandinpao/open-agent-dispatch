-- Phase 2G: cancellation aggregate, fencing evidence, retryable reconciliation and late-result cutoff.
alter table a2a_cancellations add column if not exists attempt_no integer;
alter table a2a_cancellations add column if not exists cancellation_fingerprint varchar(96);
alter table a2a_cancellations add column if not exists idempotency_key varchar(255);
alter table a2a_cancellations add column if not exists outcome varchar(48) not null default 'PENDING';
alter table a2a_cancellations add column if not exists processing_status varchar(48) not null default 'REQUESTED';
alter table a2a_cancellations add column if not exists reconciliation_classification varchar(64) not null default 'NONE';
alter table a2a_cancellations add column if not exists retry_count integer not null default 0;
alter table a2a_cancellations add column if not exists reconciliation_count integer not null default 0;
alter table a2a_cancellations add column if not exists result_cutoff_at timestamptz;
alter table a2a_cancellations add column if not exists next_reconcile_at timestamptz;
alter table a2a_cancellations add column if not exists last_reconciled_at timestamptz;
alter table a2a_cancellations add column if not exists completed_at timestamptz;

update a2a_cancellations
set result_cutoff_at=coalesce(result_cutoff_at,requested_at),
    processing_status=case status
      when 'ACKNOWLEDGED' then 'CONFIRMED'
      when 'DELIVERY_PENDING' then 'RUNTIME_DELIVERY_PENDING'
      when 'DELIVERED' then 'RUNTIME_ACK_PENDING'
      when 'TIMED_OUT' then 'WAIT_HUMAN'
      when 'WAIT_HUMAN' then 'WAIT_HUMAN'
      when 'FAILED' then 'FAILED_RETRYABLE'
      else 'REQUESTED' end,
    outcome=case status
      when 'ACKNOWLEDGED' then 'CANCELLED_CONFIRMED'
      when 'TIMED_OUT' then 'CANCELLATION_TIMEOUT'
      when 'WAIT_HUMAN' then 'CANCELLED_UNCONFIRMED'
      else 'PENDING' end,
    reconciliation_classification=case status
      when 'DELIVERED' then 'RUNTIME_ACK_MISSING'
      when 'TIMED_OUT' then 'CANCELLATION_TIMEOUT'
      when 'FAILED' then 'DELIVERY_FAILED'
      else 'NONE' end,
    next_reconcile_at=case when status in('DELIVERY_PENDING','DELIVERED','FAILED')
      then coalesce(next_reconcile_at,deadline_at) else null end,
    completed_at=case when status in('ACKNOWLEDGED','TIMED_OUT','WAIT_HUMAN')
      then coalesce(completed_at,acknowledged_at,updated_at) else completed_at end;

alter table a2a_cancellation_evidence add column if not exists event_key varchar(255);
alter table a2a_cancellation_evidence add column if not exists attempt_no integer;
alter table a2a_cancellation_evidence add column if not exists assignment_id varchar(128);
alter table a2a_cancellation_evidence add column if not exists execution_attempt_id varchar(128);
update a2a_cancellation_evidence
set event_key=coalesce(event_key,'legacy:'||evidence_id)
where event_key is null;
alter table a2a_cancellation_evidence alter column event_key set not null;

create unique index if not exists uk_a2a_cancellation_idempotency_p2g
 on a2a_cancellations(tenant_id,idempotency_key) where idempotency_key is not null;
create unique index if not exists uk_a2a_cancel_evidence_event_p2g
 on a2a_cancellation_evidence(tenant_id,event_key);
create index if not exists idx_a2a_cancellation_reconcile_p2g
 on a2a_cancellations(processing_status,next_reconcile_at)
 where processing_status in('RUNTIME_DELIVERY_PENDING','RUNTIME_ACK_PENDING','FAILED_RETRYABLE');
create index if not exists idx_a2a_cancellation_binding_p2g
 on a2a_cancellations(tenant_id,assignment_id,execution_attempt_id,attempt_no);
create index if not exists idx_a2a_late_result_request_p2g
 on a2a_result_quarantine(tenant_id,a2a_request_id,classification,quarantined_at);

do $$ begin
 if exists(select 1 from pg_constraint where conname='ck_a2a_cancellation_status') then
  alter table a2a_cancellations drop constraint ck_a2a_cancellation_status;
 end if;
 if not exists(select 1 from pg_constraint where conname='ck_a2a_cancellation_outcome_p2g') then
  alter table a2a_cancellations add constraint ck_a2a_cancellation_outcome_p2g check(outcome in('PENDING','CANCELLED_CONFIRMED','CANCELLED_UNCONFIRMED','CANCELLATION_TIMEOUT','AGENT_ALREADY_COMPLETED','STALE_CANCELLATION'));
 end if;
 if not exists(select 1 from pg_constraint where conname='ck_a2a_cancellation_processing_p2g') then
  alter table a2a_cancellations add constraint ck_a2a_cancellation_processing_p2g check(processing_status in('REQUESTED','FENCING_ROTATED','RUNTIME_DELIVERY_PENDING','RUNTIME_ACK_PENDING','CONFIRMED','FAILED_RETRYABLE','WAIT_HUMAN'));
 end if;
 if not exists(select 1 from pg_constraint where conname='ck_a2a_cancellation_reconciliation_p2g') then
  alter table a2a_cancellations add constraint ck_a2a_cancellation_reconciliation_p2g check(reconciliation_classification in('NONE','RUNTIME_ACK_MISSING','DELIVERY_FAILED','CANCELLATION_TIMEOUT','AGENT_ALREADY_COMPLETED','STALE_CANCELLATION','FENCING_CONFLICT','PROCESS_CRASH','RETRY_EXHAUSTED'));
 end if;
 if not exists(select 1 from pg_constraint where conname='ck_a2a_cancellation_final_p2g') then
  alter table a2a_cancellations add constraint ck_a2a_cancellation_final_p2g check(
    (processing_status not in('CONFIRMED','WAIT_HUMAN')) or completed_at is not null);
 end if;
 if not exists(select 1 from pg_constraint where conname='ck_a2a_cancellation_fencing_p2g') then
  alter table a2a_cancellations add constraint ck_a2a_cancellation_fencing_p2g check(
    assignment_id is null or (revoked_fencing_token_hash is not null and active_fencing_token_hash is not null));
 end if;
end $$;

-- Existing append-only trigger remains authoritative and now protects event-key/binding evidence too.
comment on column a2a_cancellations.result_cutoff_at is
 'Results from the revoked Assignment accepted after this cutoff must be quarantined as LATE_RESULT.';
comment on column a2a_cancellations.cancellation_fingerprint is
 'SHA-256 identity fingerprint; raw fencing tokens are forbidden.';
