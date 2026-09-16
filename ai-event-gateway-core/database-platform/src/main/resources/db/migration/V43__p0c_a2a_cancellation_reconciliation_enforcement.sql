-- P0C enforcement.
do $$ begin
 if not exists(select 1 from pg_constraint where conname='ck_a2a_cancellation_status') then alter table a2a_cancellations add constraint ck_a2a_cancellation_status check(status in('REQUESTED','DELIVERY_PENDING','DELIVERED','ACKNOWLEDGED','TIMED_OUT','DISCONNECTED','FAILED','WAIT_HUMAN')); end if;
 if not exists(select 1 from pg_constraint where conname='ck_a2a_reconciliation_status') then alter table a2a_reconciliation_cases add constraint ck_a2a_reconciliation_status check(status in('OPEN','RESOLVED','IGNORED')); end if;
 if not exists(select 1 from pg_constraint where conname='fk_a2a_cancel_request') then alter table a2a_cancellations add constraint fk_a2a_cancel_request foreign key(tenant_id,a2a_request_id) references a2a_requests(tenant_id,a2a_request_id) not valid; end if;
 if not exists(select 1 from pg_constraint where conname='fk_a2a_cancel_evidence') then alter table a2a_cancellation_evidence add constraint fk_a2a_cancel_evidence foreign key(tenant_id,cancellation_id) references a2a_cancellations(tenant_id,cancellation_id) not valid; end if;
 if not exists(select 1 from pg_constraint where conname='fk_a2a_reconcile_cancel') then alter table a2a_reconciliation_cases add constraint fk_a2a_reconcile_cancel foreign key(tenant_id,cancellation_id) references a2a_cancellations(tenant_id,cancellation_id) not valid; end if;
end $$;
create or replace function reject_a2a_cancellation_evidence_mutation() returns trigger language plpgsql as $$ begin raise exception 'A2A_CANCELLATION_EVIDENCE_APPEND_ONLY'; end $$;
drop trigger if exists trg_a2a_cancellation_evidence_append_only on a2a_cancellation_evidence;
create trigger trg_a2a_cancellation_evidence_append_only before update or delete on a2a_cancellation_evidence for each row execute function reject_a2a_cancellation_evidence_mutation();
