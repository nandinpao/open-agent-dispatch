-- Phase 18 dev/live schema repair for existing databases that already applied
-- V1 before the incident lifecycle mapper contract was completed.
-- This script is intentionally outside Flyway migration to preserve the clean
-- single-baseline contract. Prefer a fresh DB reset; use this only for a running
-- local/dev database you cannot reset immediately.

alter table incidents add column if not exists fingerprint varchar(255);
alter table incidents add column if not exists tenant_id varchar(64);
alter table incidents add column if not exists source_system varchar(128);
alter table incidents add column if not exists site_id varchar(128);
alter table incidents add column if not exists plant_id varchar(128);
alter table incidents add column if not exists object_type varchar(128);
alter table incidents add column if not exists object_id varchar(128);
alter table incidents add column if not exists event_type varchar(128);
alter table incidents add column if not exists error_code varchar(128);
alter table incidents add column if not exists severity varchar(32);
alter table incidents add column if not exists status varchar(32);
update incidents set status = 'ACTIVE' where status is null;
alter table incidents alter column status set default 'ACTIVE';
alter table incidents add column if not exists first_seen_at timestamptz;
alter table incidents add column if not exists last_seen_at timestamptz;
update incidents set first_seen_at = coalesce(first_seen_at, occurred_at, created_at, now());
update incidents set last_seen_at = coalesce(last_seen_at, occurred_at, updated_at, created_at, now());
alter table incidents alter column first_seen_at set default now();
alter table incidents alter column last_seen_at set default now();
alter table incidents add column if not exists occurrence_count int;
update incidents set occurrence_count = 1 where occurrence_count is null;
alter table incidents alter column occurrence_count set default 1;
alter table incidents add column if not exists last_message text;
alter table incidents add column if not exists linked_task_id varchar(128);
alter table incidents add column if not exists linked_issue_id varchar(128);
alter table incidents add column if not exists resolved_at timestamptz;
alter table incidents add column if not exists reopened_at timestamptz;
alter table incidents add column if not exists reopen_count int;
update incidents set reopen_count = 0 where reopen_count is null;
alter table incidents alter column reopen_count set default 0;
alter table incidents add column if not exists lifecycle_reason varchar(128);

create index if not exists idx_incidents_active_last_seen on incidents(status, last_seen_at);
create index if not exists idx_incidents_fingerprint_last_seen on incidents(fingerprint, last_seen_at desc);
create index if not exists idx_incidents_tenant_status on incidents(tenant_id, status, last_seen_at desc);
