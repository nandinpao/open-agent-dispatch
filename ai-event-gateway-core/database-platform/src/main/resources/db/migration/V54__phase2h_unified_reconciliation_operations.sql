-- Phase 2H: unified reconciliation case, lease-safe claim, append-only evidence and operations pagination.
alter table a2a_reconciliation_cases add column if not exists child_task_id varchar(128);
alter table a2a_reconciliation_cases add column if not exists dispatch_request_id varchar(128);
alter table a2a_reconciliation_cases add column if not exists a2a_result_id varchar(128);
alter table a2a_reconciliation_cases add column if not exists handoff_snapshot_id varchar(128);
alter table a2a_reconciliation_cases add column if not exists authority_owner varchar(64);
alter table a2a_reconciliation_cases add column if not exists diagnosis_code varchar(128);
alter table a2a_reconciliation_cases add column if not exists evidence_snapshot_hash varchar(96);
alter table a2a_reconciliation_cases add column if not exists repair_action varchar(64);
alter table a2a_reconciliation_cases add column if not exists required_permission varchar(128);
alter table a2a_reconciliation_cases add column if not exists impact_summary text;
alter table a2a_reconciliation_cases add column if not exists plan_hash varchar(96);
alter table a2a_reconciliation_cases add column if not exists expected_resource_version bigint not null default 0;
alter table a2a_reconciliation_cases add column if not exists repair_attempt_no integer not null default 0;
alter table a2a_reconciliation_cases add column if not exists idempotency_key varchar(255);
alter table a2a_reconciliation_cases add column if not exists claimed_by varchar(128);
alter table a2a_reconciliation_cases add column if not exists claim_token_hash varchar(96);
alter table a2a_reconciliation_cases add column if not exists claim_until timestamptz;
alter table a2a_reconciliation_cases add column if not exists heartbeat_at timestamptz;
alter table a2a_reconciliation_cases add column if not exists last_error_code varchar(128);
alter table a2a_reconciliation_cases add column if not exists last_error text;
alter table a2a_reconciliation_cases add column if not exists repair_evidence_id varchar(128);
update a2a_reconciliation_cases set status='READY' where status='OPEN';

create table if not exists a2a_reconciliation_evidence(
 tenant_id varchar(64) not null,evidence_id varchar(128) not null,case_id varchar(128) not null,repair_attempt_no integer not null default 0,
 event_key varchar(255) not null,evidence_type varchar(64) not null,evidence_reference varchar(255),evidence_hash varchar(96),decision varchar(48) not null,
 reason_code varchar(128),safe_summary text,actor_id varchar(128),occurred_at timestamptz not null default now(),primary key(tenant_id,evidence_id));
create unique index if not exists uk_a2a_reconciliation_evidence_event_p2h on a2a_reconciliation_evidence(tenant_id,event_key);
create index if not exists idx_a2a_reconciliation_evidence_case_p2h on a2a_reconciliation_evidence(tenant_id,case_id,occurred_at desc);
create index if not exists idx_a2a_reconciliation_claim_p2h on a2a_reconciliation_cases(status,next_attempt_at,claim_until) where status in('READY','CLAIMED','EXECUTING','RETRY_WAITING');
create index if not exists idx_a2a_reconciliation_search_p2h on a2a_reconciliation_cases(tenant_id,status,case_type,authority_owner,updated_at desc);
create unique index if not exists uk_a2a_reconciliation_idempotency_p2h on a2a_reconciliation_cases(tenant_id,idempotency_key) where idempotency_key is not null;

do $$ begin
 if exists(select 1 from pg_constraint where conname='ck_a2a_reconciliation_status') then alter table a2a_reconciliation_cases drop constraint ck_a2a_reconciliation_status; end if;
 if not exists(select 1 from pg_constraint where conname='ck_a2a_reconciliation_status_p2h') then alter table a2a_reconciliation_cases add constraint ck_a2a_reconciliation_status_p2h check(status in('OPEN','READY','CLAIMED','EXECUTING','RETRY_WAITING','WAIT_HUMAN','RESOLVED','IGNORED')); end if;
 if not exists(select 1 from pg_constraint where conname='ck_a2a_reconciliation_claim_p2h') then alter table a2a_reconciliation_cases add constraint ck_a2a_reconciliation_claim_p2h check(status not in('CLAIMED','EXECUTING') or (claimed_by is not null and claim_token_hash is not null and claim_until is not null)); end if;
 if not exists(select 1 from pg_constraint where conname='ck_a2a_reconciliation_plan_p2h') then alter table a2a_reconciliation_cases add constraint ck_a2a_reconciliation_plan_p2h check(status not in('READY','EXECUTING','RETRY_WAITING') or (diagnosis_code is not null and repair_action is not null and required_permission is not null and plan_hash is not null)); end if;
end $$;

create or replace function prevent_a2a_reconciliation_evidence_mutation() returns trigger language plpgsql as $$ begin raise exception 'a2a_reconciliation_evidence is append-only'; end $$;
drop trigger if exists trg_a2a_reconciliation_evidence_immutable on a2a_reconciliation_evidence;
create trigger trg_a2a_reconciliation_evidence_immutable before update or delete on a2a_reconciliation_evidence for each row execute function prevent_a2a_reconciliation_evidence_mutation();

create table if not exists a2a_operations_saved_views(
 tenant_id varchar(64) not null,view_id varchar(128) not null,owner_id varchar(128) not null,name varchar(160) not null,filter_json jsonb not null default '{}'::jsonb,
 sort_json jsonb not null default '{}'::jsonb,is_default boolean not null default false,created_at timestamptz not null default now(),updated_at timestamptz not null default now(),version bigint not null default 1,
 primary key(tenant_id,view_id),unique(tenant_id,owner_id,name));

comment on column a2a_reconciliation_cases.claim_token_hash is 'Lease proof hash only; raw claim tokens are forbidden.';
comment on column a2a_reconciliation_cases.evidence_snapshot_hash is 'Hash of data-minimized evidence snapshot used to derive the deterministic repair plan.';
