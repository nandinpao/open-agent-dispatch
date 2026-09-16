create table if not exists integration_phase3_certification_runs (
 tenant_id varchar(64) not null,
 run_id varchar(128) not null,
 status varchar(32) not null,
 requested_by varchar(128) not null,
 started_at timestamptz,
 completed_at timestamptz,
 release_version varchar(64),
 source_commit varchar(128),
 safe_summary varchar(1024),
 row_version bigint not null default 1,
 created_at timestamptz not null,
 updated_at timestamptz not null,
 primary key(tenant_id,run_id),
 check(status in('REQUESTED','RUNNING','PARTIAL','NOT_CERTIFIED','READY_FOR_SIGNING','PRODUCTION_READY','FAILED')),
 check(row_version>0)
);
create table if not exists integration_phase3_certification_evidence (
 tenant_id varchar(64) not null,
 evidence_id varchar(128) not null,
 run_id varchar(128) not null,
 gate_id varchar(64) not null,
 status varchar(32) not null,
 artifact_path varchar(512) not null,
 artifact_sha256 varchar(64) not null,
 toolchain_json jsonb not null default '{}'::jsonb,
 metadata_json jsonb not null default '{}'::jsonb,
 previous_evidence_hash varchar(64),
 evidence_hash varchar(64),
 created_at timestamptz not null,
 primary key(tenant_id,evidence_id),
 foreign key(tenant_id,run_id) references integration_phase3_certification_runs(tenant_id,run_id),
 check(status in('NOT_RUN','PASSED','FAILED','SKIPPED','BLOCKED','NOT_CERTIFIED')),
 check(length(artifact_sha256)=64)
);
create index if not exists idx_phase3_certification_runs_status on integration_phase3_certification_runs(tenant_id,status,updated_at desc);
create index if not exists idx_phase3_certification_evidence_gate on integration_phase3_certification_evidence(tenant_id,run_id,gate_id,created_at);
create or replace function phase3j_certification_run_guard() returns trigger language plpgsql as $$
begin
 if old.tenant_id<>new.tenant_id or old.run_id<>new.run_id then raise exception 'PHASE3_CERTIFICATION_RUN_IDENTITY_IMMUTABLE'; end if;
 if new.row_version<>old.row_version+1 then raise exception 'PHASE3_CERTIFICATION_RUN_VERSION_CONFLICT'; end if;
 return new;
end $$;
drop trigger if exists trg_phase3_certification_run_guard on integration_phase3_certification_runs;
create trigger trg_phase3_certification_run_guard before update on integration_phase3_certification_runs for each row execute function phase3j_certification_run_guard();
create or replace function phase3j_certification_evidence_guard() returns trigger language plpgsql as $$ begin raise exception 'PHASE3_CERTIFICATION_EVIDENCE_APPEND_ONLY'; end $$;
drop trigger if exists trg_phase3_certification_evidence_guard on integration_phase3_certification_evidence;
create trigger trg_phase3_certification_evidence_guard before update or delete on integration_phase3_certification_evidence for each row execute function phase3j_certification_evidence_guard();
comment on table integration_phase3_certification_runs is 'Phase 3 certification execution status. Source completion cannot imply runtime or production readiness.';
comment on table integration_phase3_certification_evidence is 'Append-only evidence index for runtime, PostgreSQL, Admin UI, Jira, Redmine and signed release gates.';
insert into permission_point_catalog(permission_point,resource_type,action_code,description,risk_level) values
 ('integration.sync_operations.read','INTEGRATION_SYNC_OPERATIONS','READ','Read projection queue, Webhook, conflict, candidate, relay, reconciliation and Dead Letter operations.','MEDIUM'),
 ('integration.phase3_release.read','INTEGRATION_RELEASE','READ','Read Phase 3 certification and signed release evidence status.','MEDIUM'),
 ('integration.phase3_release.manage','INTEGRATION_RELEASE','MANAGE','Record and sign governed Phase 3 release evidence.','CRITICAL')
on conflict(permission_point) do nothing;
insert into reason_code_catalog(reason_code,http_status,category,retryable,message_template) values
 ('PHASE3_RUNTIME_NOT_CERTIFIED',409,'INTEGRATION',false,'Phase 3 runtime certification is incomplete.'),
 ('PHASE3_LIVE_PROVIDER_NOT_CERTIFIED',409,'INTEGRATION',false,'Jira or Redmine live-provider certification is incomplete.'),
 ('PHASE3_RELEASE_EVIDENCE_NOT_SIGNED',409,'INTEGRATION',false,'Phase 3 release evidence has not been signed.')
on conflict(reason_code) do nothing;
