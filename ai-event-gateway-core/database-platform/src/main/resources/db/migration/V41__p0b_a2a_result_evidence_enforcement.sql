-- P0B enforcement: canonical result uniqueness already exists on
-- (tenant_id,a2a_request_id). Add evidence invariants and append-only guards.

do $$ begin
  if not exists (select 1 from pg_constraint where conname='ck_a2a_result_attempt_decision') then
    alter table a2a_result_attempts add constraint ck_a2a_result_attempt_decision
      check (decision in ('ACCEPTED','DUPLICATE','STALE','REVOKED','CONFLICT','LATE','MISSING_EVIDENCE'));
  end if;
  if not exists (select 1 from pg_constraint where conname='ck_a2a_result_quarantine_class') then
    alter table a2a_result_quarantine add constraint ck_a2a_result_quarantine_class
      check (classification in ('STALE','REVOKED','CONFLICT','LATE','MISSING_EVIDENCE'));
  end if;
  if not exists (select 1 from pg_constraint where conname='ck_a2a_result_quarantine_status') then
    alter table a2a_result_quarantine add constraint ck_a2a_result_quarantine_status
      check (status in ('OPEN','RESOLVED_ACCEPTED','RESOLVED_REJECTED'));
  end if;
  if not exists (select 1 from pg_constraint where conname='ck_a2a_result_attempt_no') then
    alter table a2a_result_attempts add constraint ck_a2a_result_attempt_no check (attempt_no is null or attempt_no > 0);
  end if;
  if not exists (select 1 from pg_constraint where conname='fk_a2a_result_attempt_request') then
    alter table a2a_result_attempts add constraint fk_a2a_result_attempt_request
      foreign key (tenant_id,a2a_request_id) references a2a_requests(tenant_id,a2a_request_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname='fk_a2a_result_evidence_attempt') then
    alter table a2a_result_evidence add constraint fk_a2a_result_evidence_attempt
      foreign key (tenant_id,attempt_id) references a2a_result_attempts(tenant_id,attempt_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname='fk_a2a_result_quarantine_attempt') then
    alter table a2a_result_quarantine add constraint fk_a2a_result_quarantine_attempt
      foreign key (tenant_id,attempt_id) references a2a_result_attempts(tenant_id,attempt_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname='fk_a2a_result_acceptance_attempt') then
    alter table a2a_results add constraint fk_a2a_result_acceptance_attempt
      foreign key (tenant_id,acceptance_attempt_id) references a2a_result_attempts(tenant_id,attempt_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname='ck_a2a_canonical_evidence_complete') then
    alter table a2a_results add constraint ck_a2a_canonical_evidence_complete check (
      accepted_at is null or (
        assignment_id is not null and execution_attempt_id is not null and attempt_no > 0
        and dispatch_request_id is not null and dispatch_token_hash is not null
        and fencing_token_hash is not null and agent_session_id is not null
        and callback_inbox_id is not null and payload_hash is not null
        and acceptance_attempt_id is not null and received_at is not null
      )
    ) not valid;
  end if;
end $$;

create unique index if not exists uq_a2a_results_callback_inbox
  on a2a_results(tenant_id,callback_inbox_id) where callback_inbox_id is not null;
create unique index if not exists uq_a2a_results_execution_attempt
  on a2a_results(tenant_id,execution_attempt_id) where execution_attempt_id is not null;
create unique index if not exists uq_a2a_results_acceptance_attempt
  on a2a_results(tenant_id,acceptance_attempt_id) where acceptance_attempt_id is not null;

create or replace function reject_a2a_result_evidence_mutation() returns trigger language plpgsql as $$
begin
  raise exception 'A2A_RESULT_EVIDENCE_APPEND_ONLY';
end $$;

drop trigger if exists trg_a2a_result_attempts_append_only on a2a_result_attempts;
create trigger trg_a2a_result_attempts_append_only before update or delete on a2a_result_attempts
for each row execute function reject_a2a_result_evidence_mutation();

drop trigger if exists trg_a2a_result_evidence_append_only on a2a_result_evidence;
create trigger trg_a2a_result_evidence_append_only before update or delete on a2a_result_evidence
for each row execute function reject_a2a_result_evidence_mutation();

-- Canonical Result is append-only. Corrections are new attempts/evidence or
-- governed quarantine resolutions; the accepted record is never rewritten.
create or replace function protect_a2a_canonical_result() returns trigger language plpgsql as $$
begin
  raise exception 'A2A_CANONICAL_RESULT_IMMUTABLE';
end $$;

drop trigger if exists trg_a2a_canonical_result_immutable on a2a_results;
create trigger trg_a2a_canonical_result_immutable before update or delete on a2a_results
for each row execute function protect_a2a_canonical_result();
