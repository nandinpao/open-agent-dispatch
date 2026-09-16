-- Phase 6C-1 Wave 0 Read-only Pilot.
-- Adds low-risk read-pilot observation, error-budget gates and independent Target read views.
-- Production execution remains disabled by application configuration until Phase 6C-0 full certification passes.

select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','phase6c1-migration',true);

-- Publish Phase 6C-1 permissions through the governed Catalog pipeline.
insert into permission_catalog_revisions(revision_id,revision_code,revision_number,status,content_hash,description,supersedes_revision_id,created_at,created_by,published_at,published_by,version)
select '00000000-0000-0000-0000-000000000010'::uuid,'PHASE6C1-0.8.2',coalesce(max(revision_number),0)+1,'DRAFT','DRAFT:UNPUBLISHED',
 'Phase 6C-1 Wave 0 read-pilot observation, error-budget and operational pause permissions.',
 (select revision_id from permission_catalog_active_revision where singleton_id='ACTIVE'),now(),'phase6c1-migration',null,null,1
from permission_catalog_revisions on conflict(revision_id) do nothing;

insert into permission_catalog_revision_entries(revision_id,permission_code,owner_module,resource_type,action_code,description,risk_level,risk_lane,lifecycle,allowed_scope_types,system_managed,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by,version)
select '00000000-0000-0000-0000-000000000010'::uuid,e.permission_code,e.owner_module,e.resource_type,e.action_code,e.description,e.risk_level,e.risk_lane,e.lifecycle,e.allowed_scope_types,e.system_managed,e.replacement_permission_code,e.introduced_at,e.deprecated_at,e.retired_at,now(),'phase6c1-migration',1
from permission_catalog_revision_entries e join permission_catalog_active_revision a on a.singleton_id='ACTIVE' and a.revision_id=e.revision_id
on conflict(revision_id,permission_code) do nothing;

insert into permission_catalog_revision_aliases(revision_id,alias_code,canonical_permission_code,alias_type,valid_from,valid_until,reason,created_by,created_at,version)
select '00000000-0000-0000-0000-000000000010'::uuid,a.alias_code,a.canonical_permission_code,a.alias_type,a.valid_from,a.valid_until,a.reason,'phase6c1-migration',now(),1
from permission_catalog_revision_aliases a join permission_catalog_active_revision active on active.singleton_id='ACTIVE' and active.revision_id=a.revision_id
on conflict(revision_id,alias_code) do nothing;

insert into permission_catalog_revision_entries(revision_id,permission_code,owner_module,resource_type,action_code,description,risk_level,risk_lane,lifecycle,allowed_scope_types,system_managed,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by,version)
values
 ('00000000-0000-0000-0000-000000000010','permission.enforcement_wave0.read','enforcement-activation','ENFORCEMENT_WAVE0_READ_PILOT','READ','Read Wave 0 entry-point policies, gates, observations, error-budget metrics and pilot responses.','HIGH','READ','ACTIVE',array['INSTANCE'],true,null,now(),null,null,now(),'phase6c1-migration',1),
 ('00000000-0000-0000-0000-000000000010','permission.enforcement_wave0.operate','enforcement-activation','ENFORCEMENT_WAVE0_READ_GATE','OPERATE','Pause, resume and evaluate a Wave 0 read-only pilot gate with immutable audit evidence.','CRITICAL','CRITICAL','ACTIVE',array['INSTANCE'],true,null,now(),null,null,now(),'phase6c1-migration',1)
on conflict(revision_id,permission_code) do update set owner_module=excluded.owner_module,resource_type=excluded.resource_type,action_code=excluded.action_code,description=excluded.description,risk_level=excluded.risk_level,risk_lane=excluded.risk_lane,lifecycle='ACTIVE',allowed_scope_types=excluded.allowed_scope_types,updated_at=excluded.updated_at,updated_by=excluded.updated_by,version=permission_catalog_revision_entries.version+1;

select set_config('app.permission_catalog_publish_revision_id','00000000-0000-0000-0000-000000000010',true);
insert into permission_definitions(permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,active,version,owner_module,risk_lane,lifecycle,catalog_revision_id,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by)
select permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,lifecycle<>'RETIRED',1,owner_module,risk_lane,lifecycle,revision_id,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by
from permission_catalog_revision_entries where revision_id='00000000-0000-0000-0000-000000000010'::uuid
on conflict(permission_code) do update set resource_type=excluded.resource_type,action_code=excluded.action_code,description=excluded.description,risk_level=excluded.risk_level,allowed_scope_types=excluded.allowed_scope_types,system_managed=excluded.system_managed,active=excluded.active,owner_module=excluded.owner_module,risk_lane=excluded.risk_lane,lifecycle=excluded.lifecycle,catalog_revision_id=excluded.catalog_revision_id,replacement_permission_code=excluded.replacement_permission_code,deprecated_at=excluded.deprecated_at,retired_at=excluded.retired_at,updated_at=excluded.updated_at,updated_by=excluded.updated_by,version=permission_definitions.version+1;
update permission_catalog_revisions set status='SUPERSEDED',version=version+1 where revision_id=(select revision_id from permission_catalog_active_revision where singleton_id='ACTIVE') and revision_id<>'00000000-0000-0000-0000-000000000010'::uuid and status='PUBLISHED';
update permission_catalog_revisions set status='PUBLISHED',content_hash=(with catalog_lines as (
 select 'P|'||permission_code||'|'||owner_module||'|'||resource_type||'|'||action_code||'|'||description||'|'||risk_level||'|'||risk_lane||'|'||lifecycle||'|'||coalesce(array_to_string(allowed_scope_types,','),'')||'|'||system_managed::text||'|'||coalesce(replacement_permission_code,'') line from permission_catalog_revision_entries where revision_id='00000000-0000-0000-0000-000000000010'::uuid
 union all select 'A|'||alias_code||'|'||canonical_permission_code||'|'||alias_type||'|'||coalesce(to_char(valid_from at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'')||'|'||coalesce(to_char(valid_until at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'')||'|'||reason from permission_catalog_revision_aliases where revision_id='00000000-0000-0000-0000-000000000010'::uuid)
 select 'sha256:'||encode(sha256(convert_to(coalesce(string_agg(line,E'\n' order by line),''),'UTF8')),'hex') from catalog_lines),published_at=now(),published_by='phase6c1-migration',version=version+1
where revision_id='00000000-0000-0000-0000-000000000010'::uuid and status='DRAFT';
update permission_catalog_active_revision set revision_id='00000000-0000-0000-0000-000000000010'::uuid,activated_at=now(),activated_by='phase6c1-migration',version=version+1 where singleton_id='ACTIVE';
insert into permission_catalog_publication_events(publication_id,revision_id,previous_revision_id,content_hash,entry_count,alias_count,actor_id,audit_reason,correlation_id,published_at)
select '00000000-0000-0000-0000-000000010010'::uuid,r.revision_id,r.supersedes_revision_id,r.content_hash,(select count(*)::integer from permission_catalog_revision_entries e where e.revision_id=r.revision_id),(select count(*)::integer from permission_catalog_revision_aliases a where a.revision_id=r.revision_id),'phase6c1-migration','Phase 6C-1 migration-owned Catalog publication','phase6c1-migration',coalesce(r.published_at,now()) from permission_catalog_revisions r where r.revision_id='00000000-0000-0000-0000-000000000010'::uuid on conflict(publication_id) do nothing;

insert into rbac_role_permissions(grant_id,tenant_id,role_id,permission_point,created_at,created_by,version) values
 ('grant-system-admin-enforcement-wave0-read',null,'role-system-admin','permission.enforcement_wave0.read',now(),'phase6c1-migration',1),
 ('grant-system-admin-enforcement-wave0-operate',null,'role-system-admin','permission.enforcement_wave0.operate',now(),'phase6c1-migration',1),
 ('grant-security-admin-enforcement-wave0-read',null,'role-security-admin','permission.enforcement_wave0.read',now(),'phase6c1-migration',1),
 ('grant-security-admin-enforcement-wave0-operate',null,'role-security-admin','permission.enforcement_wave0.operate',now(),'phase6c1-migration',1),
 ('grant-auditor-enforcement-wave0-read',null,'role-auditor','permission.enforcement_wave0.read',now(),'phase6c1-migration',1)
on conflict(grant_id) do nothing;

create table if not exists enforcement_wave0_read_policies(
 entry_point_id varchar(64) primary key check(entry_point_id in('ENFORCEMENT_RUNTIME_STATUS','READINESS_EVIDENCE','PERMISSION_CATALOG','NON_SENSITIVE_ADMIN','TASK_LIST_SEARCH')),
 enabled boolean not null,
 compare_legacy_canary boolean not null default true,
 minimum_samples bigint not null check(minimum_samples>0),
 maximum_mismatch_basis_points integer not null check(maximum_mismatch_basis_points between 0 and 10000),
 maximum_target_error_basis_points integer not null check(maximum_target_error_basis_points between 0 and 10000),
 maximum_target_p95_ms bigint not null check(maximum_target_p95_ms>0),
 observation_window_seconds bigint not null check(observation_window_seconds between 60 and 86400),
 auto_pause boolean not null default true,
 updated_at timestamptz not null default now(),
 updated_by varchar(128) not null,
 version bigint not null default 1 check(version>0)
);

insert into enforcement_wave0_read_policies(entry_point_id,enabled,compare_legacy_canary,minimum_samples,maximum_mismatch_basis_points,maximum_target_error_basis_points,maximum_target_p95_ms,observation_window_seconds,auto_pause,updated_by)
values
 ('ENFORCEMENT_RUNTIME_STATUS',true,true,25,0,100,250,900,true,'phase6c1-migration'),
 ('READINESS_EVIDENCE',true,true,50,0,100,300,900,true,'phase6c1-migration'),
 ('PERMISSION_CATALOG',true,true,50,0,100,300,900,true,'phase6c1-migration'),
 ('NON_SENSITIVE_ADMIN',true,true,50,0,100,350,900,true,'phase6c1-migration'),
 ('TASK_LIST_SEARCH',false,false,500,0,50,500,1800,true,'phase6c1-migration')
on conflict(entry_point_id) do nothing;

create table if not exists enforcement_wave0_read_gate_state(
 entry_point_id varchar(64) primary key references enforcement_wave0_read_policies(entry_point_id),
 state varchar(24) not null check(state in('DISABLED','OBSERVING','OPEN','PAUSED','BLOCKED')),
 reason_code text not null,
 changed_by varchar(128) not null,
 changed_at timestamptz not null,
 version bigint not null default 1 check(version>0)
);
insert into enforcement_wave0_read_gate_state(entry_point_id,state,reason_code,changed_by,changed_at)
select entry_point_id,'DISABLED','PHASE6C0_FULL_CERTIFICATION_REQUIRED','phase6c1-migration',now()
from enforcement_wave0_read_policies on conflict(entry_point_id) do nothing;

create table if not exists enforcement_wave0_read_observations(
 observation_id uuid primary key,
 entry_point_id varchar(64) not null references enforcement_wave0_read_policies(entry_point_id),
 tenant_id varchar(64) not null,
 authority_revision bigint not null check(authority_revision>=0),
 authority_mode varchar(32) not null check(authority_mode in('LEGACY_ONLY','SHADOW','TARGET_CANARY','TARGET_PRIMARY','TARGET_ONLY','PAUSED')),
 selected_plane varchar(16) not null check(selected_plane in('NONE','LEGACY','TARGET')),
 served_by varchar(16) not null check(served_by in('LEGACY','TARGET')),
 shadow_compared boolean not null,
 fallback_used boolean not null,
 mismatch_category varchar(32) not null check(mismatch_category in('MATCH','PAYLOAD_MISMATCH','LEGACY_ERROR','TARGET_ERROR','BOTH_ERROR','UNSUPPORTED_MODE','NOT_COMPARED')),
 legacy_fingerprint varchar(71) not null default '',
 target_fingerprint varchar(71) not null default '',
 legacy_duration_micros bigint not null check(legacy_duration_micros>=0),
 target_duration_micros bigint not null check(target_duration_micros>=0),
 legacy_error_code varchar(128) not null default '',
 target_error_code varchar(128) not null default '',
 correlation_id varchar(128) not null,
 observed_at timestamptz not null
);
create index if not exists idx_wave0_read_observation_entry_time on enforcement_wave0_read_observations(entry_point_id,observed_at desc);
create index if not exists idx_wave0_read_observation_mismatch on enforcement_wave0_read_observations(entry_point_id,mismatch_category,observed_at desc);

create table if not exists enforcement_wave0_read_metrics(
 bucket_start timestamptz not null,
 entry_point_id varchar(64) not null references enforcement_wave0_read_policies(entry_point_id),
 sample_count bigint not null default 0 check(sample_count>=0),
 compared_count bigint not null default 0 check(compared_count>=0),
 mismatch_count bigint not null default 0 check(mismatch_count>=0),
 target_error_count bigint not null default 0 check(target_error_count>=0),
 legacy_duration_micros_total bigint not null default 0 check(legacy_duration_micros_total>=0),
 target_duration_micros_total bigint not null default 0 check(target_duration_micros_total>=0),
 target_duration_micros_max bigint not null default 0 check(target_duration_micros_max>=0),
 updated_at timestamptz not null,
 primary key(bucket_start,entry_point_id)
);

create table if not exists enforcement_wave0_read_gate_events(
 event_id uuid primary key default gen_random_uuid(),
 entry_point_id varchar(64) not null references enforcement_wave0_read_policies(entry_point_id),
 event_type varchar(64) not null,
 from_state varchar(24) not null check(from_state in('DISABLED','OBSERVING','OPEN','PAUSED','BLOCKED')),
 to_state varchar(24) not null check(to_state in('DISABLED','OBSERVING','OPEN','PAUSED','BLOCKED')),
 reason_code text not null,
 actor_id varchar(128) not null,
 correlation_id varchar(128) not null,
 occurred_at timestamptz not null default now()
);
create index if not exists idx_wave0_read_gate_event_entry_time on enforcement_wave0_read_gate_events(entry_point_id,occurred_at desc);

create or replace function phase6c1_validate_wave0_gate_transition() returns trigger language plpgsql as $$
begin
 if new.entry_point_id='TASK_LIST_SEARCH' and new.state<>'DISABLED' then
   raise exception 'PHASE6C1_TASK_LIST_SEARCH_NOT_CERTIFIED' using errcode='23514';
 end if;
 if TG_OP='UPDATE' and new.version<>old.version+1 then
   raise exception 'PHASE6C1_GATE_VERSION_INVALID' using errcode='23514';
 end if;
 if TG_OP='UPDATE' and old.state<>new.state then
   if not (
     (old.state='DISABLED' and new.state='OBSERVING') or
     (old.state='OBSERVING' and new.state in('OPEN','PAUSED','BLOCKED')) or
     (old.state='OPEN' and new.state in('OBSERVING','PAUSED','BLOCKED')) or
     (old.state='PAUSED' and new.state='OBSERVING') or
     (old.state='BLOCKED' and new.state in('OBSERVING','PAUSED'))
   ) then
     raise exception 'PHASE6C1_GATE_TRANSITION_INVALID:%->%',old.state,new.state using errcode='23514';
   end if;
 end if;
 return new;
end $$;
create trigger trg_phase6c1_wave0_gate_transition
before insert or update on enforcement_wave0_read_gate_state
for each row execute function phase6c1_validate_wave0_gate_transition();

create or replace function phase6c1_reject_wave0_immutable_mutation() returns trigger language plpgsql as $$
begin
 raise exception 'PHASE6C1_WAVE0_EVIDENCE_APPEND_ONLY' using errcode='55000';
end $$;
create trigger trg_phase6c1_wave0_observation_immutable before update or delete on enforcement_wave0_read_observations for each row execute function phase6c1_reject_wave0_immutable_mutation();
create trigger trg_phase6c1_wave0_gate_event_immutable before update or delete on enforcement_wave0_read_gate_events for each row execute function phase6c1_reject_wave0_immutable_mutation();

-- Independent Target read paths. security_invoker keeps FORCE-RLS and current transaction context authoritative.
create or replace view enforcement_wave0_runtime_target_read_v1 with (security_invoker=true) as
select target_revision,target_checksum,generation from enforcement_authority_activation_target where singleton_id='ACTIVE';
create or replace view enforcement_wave0_runtime_node_read_v1 with (security_invoker=true) as
select node_id,state,active_revision,last_known_good_revision,active_checksum,updated_at from enforcement_snapshot_node_status;

create or replace view enforcement_wave0_readiness_evidence_read_v1 with (security_invoker=true) as
select evidence_id,'PHASE6_ELIGIBILITY'::varchar evidence_type,source_tenant_id tenant_id,''::varchar domain_code,status,evaluated_at,evaluated_at+interval '24 hours' expires_at,catalog_revision_id::text source_revision,
 jsonb_build_object('requiredDomains',required_domains,'domainEvidenceRefs',domain_evidence_refs,'catalogRevisionId',catalog_revision_id,'manifestId',manifest_id,'blockers',blockers)::text payload
from permission_phase6_eligibility_evidence
union all
select evidence_id,'DOMAIN_READINESS'::varchar,source_tenant_id,domain_code,status,evaluated_at,evaluated_at+interval '24 hours',catalog_revision_id::text,
 jsonb_build_object('windowStartedAt',window_started_at,'windowEndedAt',window_ended_at,'sampleCount',sample_count,'catalogRevisionId',catalog_revision_id,'manifestId',manifest_id,'blockers',blockers)::text
from permission_domain_readiness_evidence
union all
select evidence_id,'RUNTIME_CERTIFICATION'::varchar,'INSTANCE','',status,generated_at,generated_at+interval '72 hours',source_version,
 jsonb_build_object('sourceVersion',source_version,'catalogRevisionId',catalog_revision_id,'manifestId',manifest_id,'evidence',evidence,'statuses',jsonb_build_array(postgresql_clean_status,postgresql_upgrade_status,application_context_status,admin_ui_build_status,playwright_status,load_test_status,pipeline_status))::text
from permission_phase5_runtime_certification_evidence;

create or replace view enforcement_wave0_permission_catalog_read_v1 with (security_invoker=true) as
select r.revision_id,r.revision_code,r.revision_number,r.status,r.content_hash,
 (select count(*) from permission_catalog_revision_entries e where e.revision_id=r.revision_id)::integer entry_count,
 (select count(*) from permission_catalog_revision_aliases a where a.revision_id=r.revision_id)::integer alias_count,
 r.published_at
from permission_catalog_active_revision active join permission_catalog_revisions r on r.revision_id=active.revision_id
where active.singleton_id='ACTIVE';

create or replace view enforcement_wave0_admin_summary_read_v1 with (security_invoker=true) as
select
 (select count(*) from enforcement_authority_revisions where status='PUBLISHED') published_authority_revisions,
 (select count(*) from enforcement_cutover_plans) cutover_plans,
 (select count(*) from permission_definitions where active=true) active_permissions,
 (select count(*) from enforcement_snapshot_node_status) runtime_nodes,
 (select count(*) from enforcement_snapshot_node_status where state='FAILED') failed_runtime_nodes;

alter table enforcement_wave0_read_policies enable row level security;
alter table enforcement_wave0_read_policies force row level security;
create policy phase6c1_wave0_policy_instance_scope on enforcement_wave0_read_policies using(iam_current_tenant_id()='INSTANCE') with check(iam_current_tenant_id()='INSTANCE');
alter table enforcement_wave0_read_gate_state enable row level security;
alter table enforcement_wave0_read_gate_state force row level security;
create policy phase6c1_wave0_gate_instance_scope on enforcement_wave0_read_gate_state using(iam_current_tenant_id()='INSTANCE') with check(iam_current_tenant_id()='INSTANCE');
alter table enforcement_wave0_read_observations enable row level security;
alter table enforcement_wave0_read_observations force row level security;
create policy phase6c1_wave0_observation_instance_scope on enforcement_wave0_read_observations using(iam_current_tenant_id()='INSTANCE') with check(iam_current_tenant_id()='INSTANCE');
alter table enforcement_wave0_read_metrics enable row level security;
alter table enforcement_wave0_read_metrics force row level security;
create policy phase6c1_wave0_metric_instance_scope on enforcement_wave0_read_metrics using(iam_current_tenant_id()='INSTANCE') with check(iam_current_tenant_id()='INSTANCE');
alter table enforcement_wave0_read_gate_events enable row level security;
alter table enforcement_wave0_read_gate_events force row level security;
create policy phase6c1_wave0_gate_event_instance_scope on enforcement_wave0_read_gate_events using(iam_current_tenant_id()='INSTANCE') with check(iam_current_tenant_id()='INSTANCE');

comment on table enforcement_wave0_read_policies is 'Phase 6C-1 per-entry-point read-pilot error-budget policy. TASK_LIST_SEARCH remains disabled until the later pilot wave.';
comment on table enforcement_wave0_read_gate_state is 'Operational Wave 0 gate. This gate can fail safe to Legacy without mutating an immutable Authority Revision.';
comment on table enforcement_wave0_read_observations is 'Append-only Authority Decision, Legacy/Target fingerprint, latency and fallback evidence for low-risk reads.';
comment on table enforcement_wave0_read_metrics is 'Minute-bucket operational counters; authoritative gate evaluation still reads the immutable observation window.';
comment on table enforcement_wave0_read_gate_events is 'Append-only manual and automatic Wave 0 pause/resume/error-budget transition evidence.';
