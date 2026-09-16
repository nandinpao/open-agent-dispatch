-- P4RA-J: immutable scale certification evidence. This schema does not claim a certification run has passed.
create table if not exists resource_scale_certification_runs (
  tenant_id varchar(64) not null,
  run_id varchar(128) not null,
  run_status varchar(24) not null,
  target_cache_misses integer not null,
  target_task_rows bigint not null,
  started_at timestamptz not null,
  completed_at timestamptz,
  actor_id varchar(128) not null,
  correlation_id varchar(128) not null,
  blocking_reasons varchar(160)[] not null default '{}',
  created_at timestamptz not null default now(),
  primary key(tenant_id,run_id),
  foreign key(tenant_id) references tenants(tenant_id),
  check(run_status in ('RUNNING','PASS','FAIL','BLOCKED')),
  check(target_cache_misses>=10000),
  check(target_task_rows>=1000000),
  check((run_status='RUNNING' and completed_at is null) or (run_status<>'RUNNING' and completed_at is not null)),
  check((run_status='PASS' and cardinality(blocking_reasons)=0) or run_status<>'PASS')
);
create table if not exists resource_scale_certification_evidence (
  tenant_id varchar(64) not null,
  evidence_id varchar(128) not null,
  run_id varchar(128) not null,
  lane_type varchar(64) not null,
  lane_status varchar(24) not null,
  sample_count bigint not null,
  duration_millis bigint not null,
  p50_micros bigint not null,
  p95_micros bigint not null,
  p99_micros bigint not null,
  error_count bigint not null,
  artifact_ref text not null default '',
  artifact_sha256 varchar(64) not null default '',
  summary text not null default '',
  recorded_at timestamptz not null,
  primary key(tenant_id,evidence_id),
  foreign key(tenant_id,run_id) references resource_scale_certification_runs(tenant_id,run_id),
  check(lane_type in ('RUNTIME_LEASE_FENCING','LATE_RESULT_QUARANTINE','SCOPE_SNAPSHOT_CUTOVER','CACHE_10000_CONCURRENT_MISS','TASK_1000000_SCOPE_QUERY')),
  check(lane_status in ('PASS','FAIL','BLOCKED','NOT_RUN')),
  check(sample_count>=0 and duration_millis>=0 and p50_micros>=0 and p95_micros>=0 and p99_micros>=0 and error_count>=0),
  check(p50_micros<=p95_micros and p95_micros<=p99_micros),
  check(artifact_sha256='' or artifact_sha256~'^[0-9a-f]{64}$')
);
create index if not exists idx_p4ra_j_scale_run_status on resource_scale_certification_runs(tenant_id,run_status,started_at desc);

alter table resource_scale_certification_runs enable row level security;
alter table resource_scale_certification_runs force row level security;
alter table resource_scale_certification_evidence enable row level security;
alter table resource_scale_certification_evidence force row level security;
drop policy if exists tenant_isolation on resource_scale_certification_runs;
create policy tenant_isolation on resource_scale_certification_runs using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
drop policy if exists tenant_isolation on resource_scale_certification_evidence;
create policy tenant_isolation on resource_scale_certification_evidence using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());

drop trigger if exists trg_p4ra_j_scale_run_immutable on resource_scale_certification_runs;
create trigger trg_p4ra_j_scale_run_immutable before update or delete on resource_scale_certification_runs
 for each row execute function p4ra_reject_append_only_mutation();
drop trigger if exists trg_p4ra_j_scale_evidence_immutable on resource_scale_certification_evidence;
create trigger trg_p4ra_j_scale_evidence_immutable before update or delete on resource_scale_certification_evidence
 for each row execute function p4ra_reject_append_only_mutation();

comment on table resource_scale_certification_runs is 'Immutable P4RA-J scale certification run; PASS requires all external live lanes to pass.';
