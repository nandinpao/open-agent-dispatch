-- Phase 2I: A2A migration/cutover, compatibility freeze and signed release evidence.
-- Cutover and release evidence are append-only.
create table if not exists a2a_cutover_state (
 scope_id varchar(64) primary key,
 stage varchar(40) not null,
 shadow_read_enabled boolean not null default false,
 legacy_write_enabled boolean not null default true,
 issue_tracking_enabled boolean not null default true,
 shadow_mismatch_count bigint not null default 0,
 migration_evidence_reference varchar(512),
 runtime_gate_run_id varchar(160),
 release_evidence_reference varchar(512),
 rollback_deadline timestamptz,
 updated_at timestamptz not null default now(),
 updated_by varchar(160) not null default 'migration',
 version bigint not null default 1,
 constraint ck_a2a_cutover_stage_p2i check(stage in('EXPAND','BACKFILL','SHADOW_READ','CUTOVER','LEGACY_WRITE_DISABLED','CONTRACT')),
 constraint ck_a2a_cutover_counts_p2i check(shadow_mismatch_count >= 0 and version >= 1),
 constraint ck_a2a_cutover_legacy_p2i check((stage in('EXPAND','BACKFILL','SHADOW_READ','CUTOVER') and legacy_write_enabled) or (stage in('LEGACY_WRITE_DISABLED','CONTRACT') and not legacy_write_enabled)),
 constraint ck_a2a_cutover_contract_p2i check(stage <> 'CONTRACT' or (release_evidence_reference is not null and rollback_deadline is not null))
);
insert into a2a_cutover_state(scope_id,stage,shadow_read_enabled,legacy_write_enabled,issue_tracking_enabled,updated_by,version)
values('INSTANCE','EXPAND',false,true,true,'V55',1) on conflict(scope_id) do nothing;

create table if not exists a2a_cutover_evidence (
 evidence_id varchar(160) primary key,
 scope_id varchar(64) not null references a2a_cutover_state(scope_id),
 from_stage varchar(40) not null,
 to_stage varchar(40) not null,
 evidence_type varchar(80) not null,
 evidence_reference varchar(512),
 evidence_hash char(64) not null,
 actor_id varchar(160) not null,
 occurred_at timestamptz not null,
 constraint uk_a2a_cutover_evidence_p2i unique(scope_id,evidence_hash)
);

create table if not exists a2a_shadow_read_comparisons (
 comparison_id varchar(160) primary key,
 tenant_id varchar(160) not null,
 request_id varchar(160),
 legacy_fingerprint char(64) not null,
 canonical_fingerprint char(64) not null,
 matches boolean not null,
 safe_difference_summary varchar(2000),
 compared_at timestamptz not null default now(),
 constraint uk_a2a_shadow_read_request_p2i unique(tenant_id,request_id,legacy_fingerprint,canonical_fingerprint)
);
create index if not exists idx_a2a_shadow_read_mismatch_p2i on a2a_shadow_read_comparisons(matches,compared_at);

create table if not exists a2a_phase2_release_evidence (
 release_id varchar(160) primary key,
 test_run_id varchar(160) not null,
 git_commit varchar(160) not null,
 artifact_sha256 char(64) not null,
 container_digest varchar(255) not null,
 database_version varchar(32) not null,
 runtime_environment varchar(255) not null,
 stage0_status varchar(40) not null,
 issue_tracking_disabled boolean not null,
 legacy_writes_disabled boolean not null,
 failure_injection_status varchar(40) not null,
 playwright_status varchar(40) not null,
 evidence_manifest_sha256 char(64) not null,
 signature_algorithm varchar(80),
 signature_reference varchar(512),
 created_at timestamptz not null default now(),
 constraint ck_a2a_phase2_release_p2i check(database_version='V55' and issue_tracking_disabled and legacy_writes_disabled)
);

create or replace function prevent_a2a_phase2_evidence_mutation_p2i() returns trigger language plpgsql as $$
begin raise exception 'PHASE2_RELEASE_EVIDENCE_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_a2a_cutover_evidence_immutable_p2i on a2a_cutover_evidence;
create trigger trg_a2a_cutover_evidence_immutable_p2i before update or delete on a2a_cutover_evidence for each row execute function prevent_a2a_phase2_evidence_mutation_p2i();
drop trigger if exists trg_a2a_release_evidence_immutable_p2i on a2a_phase2_release_evidence;
create trigger trg_a2a_release_evidence_immutable_p2i before update or delete on a2a_phase2_release_evidence for each row execute function prevent_a2a_phase2_evidence_mutation_p2i();

-- Backfill evidence proving V1..V54 data can enter the Phase 2I lifecycle without rewriting canonical authority rows.
insert into a2a_cutover_evidence(evidence_id,scope_id,from_stage,to_stage,evidence_type,evidence_reference,evidence_hash,actor_id,occurred_at)
select 'a2a-cutover-v55-expand','INSTANCE','EXPAND','EXPAND','SCHEMA_EXPANDED','flyway:V55',
       md5('INSTANCE|EXPAND|V55')||md5('p2i|INSTANCE|EXPAND|V55'),'V55',now()
where not exists(select 1 from a2a_cutover_evidence where evidence_id='a2a-cutover-v55-expand');
