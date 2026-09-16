-- Phase 2F: immutable Result aggregate, precise classifications, processing reconciliation and parent aggregation evidence.
alter table a2a_policies add column if not exists aggregation_quorum int not null default 1;
alter table a2a_results add column if not exists result_schema_version int not null default 1;
alter table a2a_results add column if not exists result_evidence_hash varchar(96);
alter table a2a_results add column if not exists result_fingerprint varchar(96);
alter table a2a_results add column if not exists policy_version bigint not null default 0;
alter table a2a_results add column if not exists policy_snapshot_hash varchar(96);
alter table a2a_result_attempts add column if not exists classification varchar(64);
alter table a2a_result_attempts add column if not exists result_schema_version int not null default 1;
alter table a2a_result_attempts add column if not exists result_evidence_hash varchar(96);
alter table a2a_result_attempts add column if not exists result_fingerprint varchar(96);
alter table a2a_result_attempts add column if not exists policy_version bigint not null default 0;
alter table a2a_result_attempts add column if not exists policy_snapshot_hash varchar(96);
alter table a2a_result_evidence add column if not exists classification varchar(64);
alter table a2a_result_evidence add column if not exists result_fingerprint varchar(96);
alter table a2a_parent_aggregations add column if not exists policy_version bigint not null default 0;
alter table a2a_parent_aggregations add column if not exists policy_snapshot_hash varchar(96);
alter table a2a_parent_aggregations add column if not exists quorum_count int not null default 1;
alter table a2a_parent_aggregations add column if not exists decision_reason text;
alter table a2a_parent_aggregations add column if not exists manual_decision_required boolean not null default false;
alter table a2a_parent_aggregations add column if not exists result_count int not null default 0;

create table if not exists a2a_result_processing (
 tenant_id varchar(64) not null, a2a_result_id varchar(128) not null, a2a_request_id varchar(128) not null,
 parent_task_id varchar(128) not null, child_task_id varchar(128) not null,
 processing_status varchar(64) not null default 'CHILD_COMPLETION_PENDING',
 reconciliation_classification varchar(64) not null default 'NONE', last_error_code varchar(128), last_error text,
 attempt_count int not null default 0, next_reconcile_at timestamptz, last_reconciled_at timestamptz, completed_at timestamptz,
 created_at timestamptz not null default now(), updated_at timestamptz not null default now(), row_version bigint not null default 1,
 primary key(tenant_id,a2a_result_id), unique(tenant_id,a2a_request_id),
 foreign key(tenant_id,a2a_result_id) references a2a_results(tenant_id,a2a_result_id)
);
-- Existing immutable canonical results are reconciled deterministically after upgrade.
insert into a2a_result_processing(
 tenant_id,a2a_result_id,a2a_request_id,parent_task_id,child_task_id,processing_status,
 reconciliation_classification,attempt_count,next_reconcile_at,created_at,updated_at,row_version
)
select r.tenant_id,r.a2a_result_id,r.a2a_request_id,r.parent_task_id,r.child_task_id,
       'CHILD_COMPLETION_PENDING','NONE',0,now(),coalesce(r.created_at,now()),now(),1
from a2a_results r
where not exists (
 select 1 from a2a_result_processing p
 where p.tenant_id=r.tenant_id and p.a2a_result_id=r.a2a_result_id
);

create index if not exists idx_a2a_result_processing_due on a2a_result_processing(processing_status,next_reconcile_at) where processing_status <> 'COMPLETED';
create index if not exists idx_a2a_result_processing_task on a2a_result_processing(tenant_id,parent_task_id,child_task_id);

create table if not exists a2a_aggregation_evidence (
 tenant_id varchar(64) not null, evidence_id varchar(128) not null, parent_task_id varchar(128) not null, a2a_result_id varchar(128),
 evidence_type varchar(64) not null, computation_hash varchar(96) not null, aggregate_status varchar(32), decision_reason text,
 expected_version bigint not null default 0, resulting_version bigint not null default 0, attempt_no int not null default 1,
 occurred_at timestamptz not null default now(), primary key(tenant_id,evidence_id),
 unique(tenant_id,parent_task_id,computation_hash,evidence_type)
);
create index if not exists idx_a2a_aggregation_evidence_parent on a2a_aggregation_evidence(tenant_id,parent_task_id,occurred_at desc);

-- Normalize legacy quarantine values before the precise Phase 2F check constraint is installed.
update a2a_result_quarantine
set classification = case classification
    when 'DUPLICATE' then 'IDENTICAL_DUPLICATE'
    when 'CONFLICT' then 'CONFLICTING_DUPLICATE'
    when 'STALE' then 'STALE_ATTEMPT'
    when 'LATE' then 'LATE_RESULT'
    when 'REVOKED' then 'STALE_ATTEMPT'
    when 'MISSING_EVIDENCE' then 'MISSING_EVIDENCE'
    else 'BINDING_CONFLICT'
end
where classification not in (
    'IDENTICAL_DUPLICATE','CONFLICTING_DUPLICATE','STALE_ATTEMPT','LATE_RESULT',
    'UNKNOWN_ASSIGNMENT','TOKEN_MISMATCH','TERMINAL_REQUEST','MISSING_EVIDENCE','BINDING_CONFLICT'
);

do $$ begin
 if exists(select 1 from pg_constraint where conname='ck_a2a_result_quarantine_class') then alter table a2a_result_quarantine drop constraint ck_a2a_result_quarantine_class; end if;
 if not exists(select 1 from pg_constraint where conname='ck_a2a_result_quarantine_class_p2f') then alter table a2a_result_quarantine add constraint ck_a2a_result_quarantine_class_p2f check(classification in ('IDENTICAL_DUPLICATE','CONFLICTING_DUPLICATE','STALE_ATTEMPT','LATE_RESULT','UNKNOWN_ASSIGNMENT','TOKEN_MISMATCH','TERMINAL_REQUEST','MISSING_EVIDENCE','BINDING_CONFLICT')); end if;
 if not exists(select 1 from pg_constraint where conname='ck_a2a_result_processing_status_p2f') then alter table a2a_result_processing add constraint ck_a2a_result_processing_status_p2f check(processing_status in ('CHILD_COMPLETION_PENDING','CHILD_COMPLETED','PARENT_AGGREGATION_PENDING','COMPLETED','FAILED_RETRYABLE','WAIT_HUMAN')); end if;
 if not exists(select 1 from pg_constraint where conname='ck_a2a_result_reconcile_class_p2f') then alter table a2a_result_processing add constraint ck_a2a_result_reconcile_class_p2f check(reconciliation_classification in ('NONE','CHILD_COMPLETION_MISSING','PARENT_AGGREGATION_MISSING','AGGREGATION_CAS_CONFLICT','POLICY_BINDING_INVALID','PROCESSING_ERROR','RETRY_EXHAUSTED')); end if;
 if not exists(select 1 from pg_constraint where conname='ck_a2a_result_schema_p2f') then alter table a2a_results add constraint ck_a2a_result_schema_p2f check(result_schema_version > 0); end if;
 if not exists(select 1 from pg_constraint where conname='ck_a2a_aggregation_quorum_p2f') then alter table a2a_policies add constraint ck_a2a_aggregation_quorum_p2f check(aggregation_quorum > 0); end if;
end $$;

create unique index if not exists uq_a2a_result_fingerprint_p2f on a2a_results(tenant_id,result_fingerprint) where result_fingerprint is not null;
create unique index if not exists uq_a2a_result_evidence_type_hash_p2f on a2a_result_evidence(tenant_id,attempt_id,evidence_type,evidence_hash);

create or replace function reject_a2a_aggregation_evidence_mutation() returns trigger language plpgsql as $$ begin raise exception 'A2A_AGGREGATION_EVIDENCE_APPEND_ONLY'; end $$;
drop trigger if exists trg_a2a_aggregation_evidence_append_only on a2a_aggregation_evidence;
create trigger trg_a2a_aggregation_evidence_append_only before update or delete on a2a_aggregation_evidence for each row execute function reject_a2a_aggregation_evidence_mutation();
