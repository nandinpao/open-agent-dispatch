-- Phase 5J expand: physical three-tier observation storage, durable async ingress,
-- daily partitions, retention/archive metadata and runtime certification evidence.

-- Flyway runs each migration in its own transaction. Set an explicit INSTANCE
-- authority context before accessing FORCE-RLS protected global RBAC rows.
select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','phase5j-migration',true);

insert into permission_catalog_revisions(revision_id,revision_code,revision_number,status,content_hash,description,supersedes_revision_id,created_at,created_by,published_at,published_by,version)
select '00000000-0000-0000-0000-000000000008'::uuid,'PHASE5J-0.8.2',coalesce(max(revision_number),0)+1,'DRAFT','DRAFT:UNPUBLISHED',
 'Phase 5J observation pipeline, partition storage, retention and runtime certification permissions.',
 (select revision_id from permission_catalog_active_revision where singleton_id='ACTIVE'),now(),'phase5j-migration',null,null,1
from permission_catalog_revisions on conflict(revision_id) do nothing;

insert into permission_catalog_revision_entries(revision_id,permission_code,owner_module,resource_type,action_code,description,risk_level,risk_lane,lifecycle,allowed_scope_types,system_managed,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by,version)
select '00000000-0000-0000-0000-000000000008'::uuid,e.permission_code,e.owner_module,e.resource_type,e.action_code,e.description,e.risk_level,e.risk_lane,e.lifecycle,e.allowed_scope_types,e.system_managed,e.replacement_permission_code,e.introduced_at,e.deprecated_at,e.retired_at,now(),'phase5j-migration',1
from permission_catalog_revision_entries e join permission_catalog_active_revision a on a.singleton_id='ACTIVE' and a.revision_id=e.revision_id
on conflict(revision_id,permission_code) do nothing;
insert into permission_catalog_revision_aliases(revision_id,alias_code,canonical_permission_code,alias_type,valid_from,valid_until,reason,created_by,created_at,version)
select '00000000-0000-0000-0000-000000000008'::uuid,a.alias_code,a.canonical_permission_code,a.alias_type,a.valid_from,a.valid_until,a.reason,'phase5j-migration',now(),1
from permission_catalog_revision_aliases a join permission_catalog_active_revision active on active.singleton_id='ACTIVE' and active.revision_id=a.revision_id
on conflict(revision_id,alias_code) do nothing;

insert into permission_catalog_revision_entries(revision_id,permission_code,owner_module,resource_type,action_code,description,risk_level,risk_lane,lifecycle,allowed_scope_types,system_managed,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by,version)
values
 ('00000000-0000-0000-0000-000000000008','permission.pipeline.read','permission-readiness','SHADOW_PIPELINE','READ','Read Shadow observation queue, backpressure and dead-letter metrics.','HIGH','READ','ACTIVE',array['INSTANCE'],true,null,now(),null,null,now(),'phase5j-migration',1),
 ('00000000-0000-0000-0000-000000000008','permission.pipeline.manage','permission-readiness','SHADOW_PIPELINE','MANAGE','Run or recover the asynchronous Shadow observation pipeline.','CRITICAL','ADMIN','ACTIVE',array['INSTANCE'],true,null,now(),null,null,now(),'phase5j-migration',1),
 ('00000000-0000-0000-0000-000000000008','permission.storage.read','permission-readiness','OBSERVATION_STORAGE','READ','Read partition, retention and archive readiness.','HIGH','READ','ACTIVE',array['INSTANCE'],true,null,now(),null,null,now(),'phase5j-migration',1),
 ('00000000-0000-0000-0000-000000000008','permission.storage.manage','permission-readiness','OBSERVATION_STORAGE','MANAGE','Execute partition precreation, retention and archive operations.','CRITICAL','ADMIN','ACTIVE',array['INSTANCE'],true,null,now(),null,null,now(),'phase5j-migration',1),
 ('00000000-0000-0000-0000-000000000008','permission.runtime_evidence.read','permission-readiness','RUNTIME_CERTIFICATION','READ','Read Phase 5 runtime certification evidence.','HIGH','READ','ACTIVE',array['INSTANCE'],true,null,now(),null,null,now(),'phase5j-migration',1),
 ('00000000-0000-0000-0000-000000000008','permission.runtime_evidence.generate','permission-readiness','RUNTIME_CERTIFICATION','GENERATE','Generate immutable Phase 5 runtime certification evidence.','CRITICAL','CRITICAL','ACTIVE',array['INSTANCE'],true,null,now(),null,null,now(),'phase5j-migration',1)
on conflict(revision_id,permission_code) do update set owner_module=excluded.owner_module,resource_type=excluded.resource_type,action_code=excluded.action_code,description=excluded.description,risk_level=excluded.risk_level,risk_lane=excluded.risk_lane,lifecycle='ACTIVE',allowed_scope_types=excluded.allowed_scope_types,updated_at=excluded.updated_at,updated_by=excluded.updated_by,version=permission_catalog_revision_entries.version+1;

select set_config('app.permission_catalog_publish_revision_id','00000000-0000-0000-0000-000000000008',true);
insert into permission_definitions(permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,active,version,owner_module,risk_lane,lifecycle,catalog_revision_id,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by)
select permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,lifecycle<>'RETIRED',1,owner_module,risk_lane,lifecycle,revision_id,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by
from permission_catalog_revision_entries where revision_id='00000000-0000-0000-0000-000000000008'::uuid
on conflict(permission_code) do update set resource_type=excluded.resource_type,action_code=excluded.action_code,description=excluded.description,risk_level=excluded.risk_level,allowed_scope_types=excluded.allowed_scope_types,system_managed=excluded.system_managed,active=excluded.active,owner_module=excluded.owner_module,risk_lane=excluded.risk_lane,lifecycle=excluded.lifecycle,catalog_revision_id=excluded.catalog_revision_id,replacement_permission_code=excluded.replacement_permission_code,deprecated_at=excluded.deprecated_at,retired_at=excluded.retired_at,updated_at=excluded.updated_at,updated_by=excluded.updated_by,version=permission_definitions.version+1;
update permission_catalog_revisions set status='SUPERSEDED',version=version+1 where revision_id=(select revision_id from permission_catalog_active_revision where singleton_id='ACTIVE') and revision_id<>'00000000-0000-0000-0000-000000000008'::uuid and status='PUBLISHED';
update permission_catalog_revisions set status='PUBLISHED',content_hash=(with catalog_lines as (
 select 'P|'||permission_code||'|'||owner_module||'|'||resource_type||'|'||action_code||'|'||description||'|'||risk_level||'|'||risk_lane||'|'||lifecycle||'|'||coalesce(array_to_string(allowed_scope_types,','),'')||'|'||system_managed::text||'|'||coalesce(replacement_permission_code,'') line from permission_catalog_revision_entries where revision_id='00000000-0000-0000-0000-000000000008'::uuid
 union all select 'A|'||alias_code||'|'||canonical_permission_code||'|'||alias_type||'|'||coalesce(to_char(valid_from at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'')||'|'||coalesce(to_char(valid_until at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'')||'|'||reason from permission_catalog_revision_aliases where revision_id='00000000-0000-0000-0000-000000000008'::uuid)
 select 'sha256:'||encode(sha256(convert_to(coalesce(string_agg(line,E'\n' order by line),''),'UTF8')),'hex') from catalog_lines),published_at=now(),published_by='phase5j-migration',version=version+1
where revision_id='00000000-0000-0000-0000-000000000008'::uuid and status='DRAFT';
update permission_catalog_active_revision set revision_id='00000000-0000-0000-0000-000000000008'::uuid,activated_at=now(),activated_by='phase5j-migration',version=version+1 where singleton_id='ACTIVE';
insert into permission_catalog_publication_events(publication_id,revision_id,previous_revision_id,content_hash,entry_count,alias_count,actor_id,audit_reason,correlation_id,published_at)
select '00000000-0000-0000-0000-000000008008'::uuid,r.revision_id,r.supersedes_revision_id,r.content_hash,(select count(*)::integer from permission_catalog_revision_entries e where e.revision_id=r.revision_id),(select count(*)::integer from permission_catalog_revision_aliases a where a.revision_id=r.revision_id),'phase5j-migration','Phase 5J migration-owned Catalog publication','phase5j-migration',coalesce(r.published_at,now()) from permission_catalog_revisions r where r.revision_id='00000000-0000-0000-0000-000000000008'::uuid on conflict(publication_id) do nothing;

insert into rbac_role_permissions(grant_id,tenant_id,role_id,permission_point,created_at,created_by,version) values
 ('grant-system-admin-pipeline-read',null,'role-system-admin','permission.pipeline.read',now(),'phase5j-migration',1),
 ('grant-system-admin-pipeline-manage',null,'role-system-admin','permission.pipeline.manage',now(),'phase5j-migration',1),
 ('grant-system-admin-storage-read',null,'role-system-admin','permission.storage.read',now(),'phase5j-migration',1),
 ('grant-system-admin-storage-manage',null,'role-system-admin','permission.storage.manage',now(),'phase5j-migration',1),
 ('grant-system-admin-runtime-read',null,'role-system-admin','permission.runtime_evidence.read',now(),'phase5j-migration',1),
 ('grant-system-admin-runtime-generate',null,'role-system-admin','permission.runtime_evidence.generate',now(),'phase5j-migration',1),
 ('grant-auditor-pipeline-read',null,'role-auditor','permission.pipeline.read',now(),'phase5j-migration',1),
 ('grant-auditor-storage-read',null,'role-auditor','permission.storage.read',now(),'phase5j-migration',1),
 ('grant-auditor-runtime-read',null,'role-auditor','permission.runtime_evidence.read',now(),'phase5j-migration',1)
on conflict(role_id,permission_point) do nothing;

-- Phase 5J archive storage is self-contained inside the already-authorized public
-- schema. Detached partitions are renamed to a compact p5j_arc_* namespace and
-- have all PUBLIC/runtime privileges revoked by the retention function in V97.
-- No database-level CREATE privilege or administrator-created schema is required.

create table if not exists permission_shadow_pipeline_settings(
 singleton_id varchar(32) primary key,
 enabled boolean not null default true,
 queue_capacity integer not null default 100000,
 batch_size integer not null default 500,
 maximum_attempts integer not null default 8,
 lease_seconds integer not null default 60,
 retry_base_seconds integer not null default 2,
 partition_days_ahead integer not null default 7,
 dead_letter_retention_days integer not null default 90,
 metrics_retention_days integer not null default 400,
 receipt_retention_days integer not null default 400,
 archive_purge_days integer not null default 30,
 updated_at timestamptz not null default now(),updated_by varchar(128) not null,version bigint not null default 1);
insert into permission_shadow_pipeline_settings(singleton_id,updated_by) values('ACTIVE','phase5j-migration') on conflict(singleton_id) do nothing;

create table if not exists permission_shadow_observation_ingress(
 queue_id uuid primary key,tenant_id varchar(64) not null,comparison_id varchar(128) not null,domain_code varchar(32) not null,entry_point_id varchar(220),resource_type varchar(64) not null,resource_id varchar(128) not null,permission_code varchar(160) not null,
 legacy_effect varchar(32) not null,legacy_scope text not null default '',legacy_visibility varchar(32) not null,legacy_reason_code varchar(128) not null,legacy_context_complete boolean not null,legacy_error_code varchar(128),legacy_decision_id varchar(128),
 target_effect varchar(32) not null,target_scope text not null default '',target_visibility varchar(32) not null,target_reason_code varchar(128) not null,target_context_complete boolean not null,target_error_code varchar(128),target_decision_id varchar(128) not null,
 mismatch_category varchar(64) not null,severity varchar(16) not null,risk_lane varchar(16) not null,protection_mode varchar(32) not null,correlation_id varchar(128) not null,compared_at timestamptz not null,
 match_retention_days integer not null,mismatch_retention_days integer not null,payload_hash varchar(71) not null,payload_bytes integer not null,
 status varchar(16) not null default 'PENDING',priority integer not null,attempts integer not null default 0,available_at timestamptz not null default now(),lease_owner varchar(128),lease_until timestamptz,last_error text,enqueued_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 unique(tenant_id,comparison_id));
create index if not exists idx_shadow_ingress_claim on permission_shadow_observation_ingress(status,priority desc,available_at,enqueued_at);
create index if not exists idx_shadow_ingress_lease on permission_shadow_observation_ingress(lease_until) where status='PROCESSING';

create table if not exists permission_shadow_observation_receipts(
 tenant_id varchar(64) not null,comparison_id varchar(128) not null,storage_tier varchar(24) not null,observed_at timestamptz not null,ingested_at timestamptz not null default now(),payload_hash varchar(71) not null,queue_id uuid,primary key(tenant_id,comparison_id));

create table if not exists permission_shadow_observation_metrics(
 bucket_start timestamptz not null,tenant_id varchar(64) not null,domain_code varchar(32) not null,entry_point_id varchar(220) not null default '',permission_code varchar(160) not null,mismatch_category varchar(64) not null,severity varchar(16) not null,risk_lane varchar(16) not null,protection_mode varchar(32) not null,
 observation_count bigint not null default 0,dropped_count bigint not null default 0,retry_count bigint not null default 0,dead_letter_count bigint not null default 0,queue_delay_ms_total bigint not null default 0,queue_delay_ms_max bigint not null default 0,processing_ms_total bigint not null default 0,processing_ms_max bigint not null default 0,updated_at timestamptz not null default now(),
 primary key(bucket_start,tenant_id,domain_code,entry_point_id,permission_code,mismatch_category,severity,risk_lane,protection_mode));
create index if not exists idx_shadow_metrics_domain on permission_shadow_observation_metrics(tenant_id,domain_code,bucket_start desc);

create table if not exists permission_shadow_durable_mismatches(
 tenant_id varchar(64) not null,comparison_id varchar(128) not null,domain_code varchar(32) not null,entry_point_id varchar(220),resource_type varchar(64) not null,resource_id varchar(128) not null,permission_code varchar(160) not null,
 legacy_effect varchar(32) not null,legacy_scope text not null default '',legacy_visibility varchar(32) not null,legacy_reason_code varchar(128) not null,legacy_context_complete boolean not null,legacy_error_code varchar(128),legacy_decision_id varchar(128),
 target_effect varchar(32) not null,target_scope text not null default '',target_visibility varchar(32) not null,target_reason_code varchar(128) not null,target_context_complete boolean not null,target_error_code varchar(128),target_decision_id varchar(128) not null,
 mismatch_category varchar(64) not null,severity varchar(16) not null,risk_lane varchar(16) not null,protection_mode varchar(32) not null,correlation_id varchar(128) not null,compared_at timestamptz not null,retention_until timestamptz not null,ingested_at timestamptz not null default now(),payload_hash varchar(71) not null,
 primary key(tenant_id,comparison_id,compared_at)) partition by range(compared_at);

create table if not exists permission_shadow_sampled_matches(
 tenant_id varchar(64) not null,comparison_id varchar(128) not null,domain_code varchar(32) not null,entry_point_id varchar(220),resource_type varchar(64) not null,resource_id varchar(128) not null,permission_code varchar(160) not null,
 legacy_effect varchar(32) not null,legacy_scope text not null default '',legacy_visibility varchar(32) not null,legacy_reason_code varchar(128) not null,legacy_context_complete boolean not null,legacy_error_code varchar(128),legacy_decision_id varchar(128),
 target_effect varchar(32) not null,target_scope text not null default '',target_visibility varchar(32) not null,target_reason_code varchar(128) not null,target_context_complete boolean not null,target_error_code varchar(128),target_decision_id varchar(128) not null,
 mismatch_category varchar(64) not null,severity varchar(16) not null,risk_lane varchar(16) not null,protection_mode varchar(32) not null,correlation_id varchar(128) not null,compared_at timestamptz not null,retention_until timestamptz not null,ingested_at timestamptz not null default now(),payload_hash varchar(71) not null,
 primary key(tenant_id,comparison_id,compared_at)) partition by range(compared_at);

create table if not exists permission_shadow_observation_dead_letters(
 failed_at timestamptz not null,dead_letter_id uuid not null,queue_id uuid not null,tenant_id varchar(64) not null,comparison_id varchar(128) not null,mismatch_category varchar(64) not null,severity varchar(16) not null,attempts integer not null,error_code varchar(128) not null,error_message text not null,payload jsonb not null,created_at timestamptz not null default now(),primary key(dead_letter_id,failed_at)) partition by range(failed_at);

create table if not exists permission_shadow_pipeline_events(
 event_id uuid primary key,event_type varchar(64) not null,tenant_id varchar(64),comparison_id varchar(128),queue_id uuid,details jsonb not null default '{}'::jsonb,occurred_at timestamptz not null default now());
create table if not exists permission_shadow_partition_archives(
 archive_id uuid primary key,storage_tier varchar(24) not null,parent_table varchar(128) not null,partition_name varchar(128) not null,partition_day date not null,row_count bigint not null,maximum_retention_until timestamptz,archive_schema varchar(64) not null,archived_at timestamptz not null,archived_by varchar(128) not null,audit_reason text not null,purge_after timestamptz not null,status varchar(24) not null default 'ARCHIVED',purged_at timestamptz,unique(parent_table,partition_name));
create table if not exists permission_shadow_retention_runs(
 run_id uuid primary key,status varchar(24) not null,metrics_deleted bigint not null default 0,receipts_deleted bigint not null default 0,dead_letters_deleted bigint not null default 0,partitions_archived integer not null default 0,partitions_purged integer not null default 0,details jsonb not null default '{}'::jsonb,started_at timestamptz not null,completed_at timestamptz,actor_id varchar(128) not null,audit_reason text not null,correlation_id varchar(128));
create table if not exists permission_phase5_runtime_certification_evidence(
 evidence_id uuid primary key,status varchar(24) not null,source_version varchar(64) not null,catalog_revision_id uuid not null,manifest_id uuid,postgresql_clean_status varchar(24) not null,postgresql_upgrade_status varchar(24) not null,application_context_status varchar(24) not null,admin_ui_build_status varchar(24) not null,playwright_status varchar(24) not null,load_test_status varchar(24) not null,pipeline_status varchar(24) not null,evidence jsonb not null,actor_id varchar(128) not null,audit_reason text not null,correlation_id varchar(128),generated_at timestamptz not null default now());

create or replace function phase5j_partition_parent(p_tier varchar) returns varchar language plpgsql immutable as $$
begin
 return case upper(p_tier) when 'DURABLE_MISMATCH' then 'permission_shadow_durable_mismatches' when 'SAMPLED_MATCH' then 'permission_shadow_sampled_matches' when 'DEAD_LETTER' then 'permission_shadow_observation_dead_letters' else null end;
end $$;

create or replace function phase5j_ensure_shadow_partition(p_tier varchar,p_day date) returns varchar language plpgsql security definer set search_path=public,pg_temp as $$
declare v_parent varchar;v_name varchar;v_from timestamptz;v_to timestamptz;
begin
 v_parent:=phase5j_partition_parent(p_tier);if v_parent is null then raise exception 'PHASE5J_STORAGE_TIER_INVALID';end if;
 v_name:=case upper(p_tier) when 'DURABLE_MISMATCH' then 'permission_shadow_durable_mismatch_p'||to_char(p_day,'YYYYMMDD') when 'SAMPLED_MATCH' then 'permission_shadow_sampled_match_p'||to_char(p_day,'YYYYMMDD') else 'permission_shadow_dead_letter_p'||to_char(p_day,'YYYYMMDD') end;
 v_from:=p_day::timestamptz;v_to:=(p_day+1)::timestamptz;
 perform pg_advisory_xact_lock(hashtextextended('phase5j-partition-'||v_name,0));
 if to_regclass('public.'||v_name) is null then
  execute format('create table %I partition of %I for values from (%L) to (%L)',v_name,v_parent,v_from,v_to);
  execute format('alter table %I enable row level security',v_name);
  execute format('alter table %I force row level security',v_name);
  execute format('create policy phase5j_partition_tenant_scope on %I using(iam_current_tenant_id()=''INSTANCE'' or tenant_id=iam_current_tenant_id()) with check(iam_current_tenant_id()=''INSTANCE'' or tenant_id=iam_current_tenant_id())',v_name);
 end if;
 return v_name;
end $$;

create or replace function phase5j_ensure_shadow_partitions(p_from date,p_to date) returns integer language plpgsql security definer set search_path=public,pg_temp as $$
declare d date;v_count integer:=0;
begin
 if p_to<p_from or p_to-p_from>62 then raise exception 'PHASE5J_PARTITION_RANGE_INVALID';end if;
 d:=p_from;while d<=p_to loop perform phase5j_ensure_shadow_partition('DURABLE_MISMATCH',d);perform phase5j_ensure_shadow_partition('SAMPLED_MATCH',d);perform phase5j_ensure_shadow_partition('DEAD_LETTER',d);v_count:=v_count+3;d:=d+1;end loop;return v_count;
end $$;
select phase5j_ensure_shadow_partitions(current_date-1,current_date+7);

-- Seed partitions needed by historical Phase 5I evidence before backfill.
do $$ declare d date;begin for d in select distinct compared_at::date from resource_shadow_decision_comparisons_v2 loop perform phase5j_ensure_shadow_partition('DURABLE_MISMATCH',d);perform phase5j_ensure_shadow_partition('SAMPLED_MATCH',d);end loop;end $$;

insert into permission_shadow_durable_mismatches
select o.tenant_id,o.comparison_id,o.domain_code,o.entry_point_id,o.resource_type,o.resource_id,o.permission_code,o.legacy_effect,o.legacy_scope,o.legacy_visibility,o.legacy_reason_code,o.legacy_context_complete,o.legacy_error_code,o.legacy_decision_id,o.target_effect,o.target_scope,o.target_visibility,o.target_reason_code,o.target_context_complete,o.target_error_code,o.target_decision_id,o.mismatch_category,o.severity,o.risk_lane,o.protection_mode,o.correlation_id,o.compared_at,o.compared_at+make_interval(days=>coalesce(p.mismatch_retention_days,180)),o.created_at,'sha256:'||encode(sha256(convert_to(o.tenant_id||'|'||o.comparison_id||'|'||o.mismatch_category,'UTF8')),'hex')
from resource_shadow_decision_comparisons_v2 o left join permission_shadow_sampling_policies p on p.risk_lane=o.risk_lane and p.protection_mode=o.protection_mode and p.active=true where o.mismatch_category<>'MATCH' on conflict do nothing;
insert into permission_shadow_sampled_matches
select o.tenant_id,o.comparison_id,o.domain_code,o.entry_point_id,o.resource_type,o.resource_id,o.permission_code,o.legacy_effect,o.legacy_scope,o.legacy_visibility,o.legacy_reason_code,o.legacy_context_complete,o.legacy_error_code,o.legacy_decision_id,o.target_effect,o.target_scope,o.target_visibility,o.target_reason_code,o.target_context_complete,o.target_error_code,o.target_decision_id,o.mismatch_category,o.severity,o.risk_lane,o.protection_mode,o.correlation_id,o.compared_at,o.compared_at+make_interval(days=>coalesce(p.match_retention_days,14)),o.created_at,'sha256:'||encode(sha256(convert_to(o.tenant_id||'|'||o.comparison_id||'|'||o.mismatch_category,'UTF8')),'hex')
from resource_shadow_decision_comparisons_v2 o left join permission_shadow_sampling_policies p on p.risk_lane=o.risk_lane and p.protection_mode=o.protection_mode and p.active=true where o.mismatch_category='MATCH' on conflict do nothing;
insert into permission_shadow_observation_receipts(tenant_id,comparison_id,storage_tier,observed_at,ingested_at,payload_hash)
select tenant_id,comparison_id,case when mismatch_category='MATCH' then 'SAMPLED_MATCH' else 'DURABLE_MISMATCH' end,compared_at,created_at,'sha256:'||encode(sha256(convert_to(tenant_id||'|'||comparison_id||'|'||mismatch_category,'UTF8')),'hex') from resource_shadow_decision_comparisons_v2 on conflict do nothing;
insert into permission_shadow_observation_metrics(bucket_start,tenant_id,domain_code,entry_point_id,permission_code,mismatch_category,severity,risk_lane,protection_mode,observation_count,updated_at)
select date_trunc('minute',compared_at),tenant_id,domain_code,coalesce(entry_point_id,''),permission_code,mismatch_category,severity,risk_lane,protection_mode,count(*),now() from resource_shadow_decision_comparisons_v2 group by 1,2,3,4,5,6,7,8,9 on conflict do nothing;

drop view if exists permission_shadow_observation_readiness;
drop table resource_shadow_decision_comparisons_v2;
create or replace view resource_shadow_decision_comparisons_v2 with (security_invoker=true) as
 select tenant_id,comparison_id,domain_code,entry_point_id,resource_type,resource_id,permission_code,legacy_effect,legacy_scope,legacy_visibility,legacy_reason_code,legacy_context_complete,legacy_error_code,legacy_decision_id,target_effect,target_scope,target_visibility,target_reason_code,target_context_complete,target_error_code,target_decision_id,mismatch_category,severity,risk_lane,protection_mode,correlation_id,compared_at,ingested_at created_at from permission_shadow_durable_mismatches
 union all
 select tenant_id,comparison_id,domain_code,entry_point_id,resource_type,resource_id,permission_code,legacy_effect,legacy_scope,legacy_visibility,legacy_reason_code,legacy_context_complete,legacy_error_code,legacy_decision_id,target_effect,target_scope,target_visibility,target_reason_code,target_context_complete,target_error_code,target_decision_id,mismatch_category,severity,risk_lane,protection_mode,correlation_id,compared_at,ingested_at created_at from permission_shadow_sampled_matches;
create or replace view permission_shadow_observation_readiness with (security_invoker=true) as
select o.*,c.case_id::text,c.status case_status,c.owner_id,c.sla_due_at,
 exists(select 1 from permission_shadow_mismatch_waivers w where w.case_id=c.case_id and w.status='ACTIVE' and w.expires_at>now()) active_waiver,
 case when o.mismatch_category in('UNEXPECTED_ALLOW','SCOPE_WIDENED','VISIBILITY_WIDENED','TARGET_ERROR') then true
      when o.mismatch_category='CONTEXT_INCOMPLETE' and not exists(select 1 from permission_shadow_mismatch_waivers w where w.case_id=c.case_id and w.status='ACTIVE' and w.expires_at>now()) then true
      else false end cutover_blocker
from resource_shadow_decision_comparisons_v2 o left join permission_shadow_mismatch_cases c on c.source_tenant_id=o.tenant_id and c.comparison_id=o.comparison_id;

create or replace view permission_shadow_pipeline_readiness with (security_invoker=true) as
select
 (select count(*) from permission_shadow_observation_ingress where status='PENDING') pending_count,
 (select count(*) from permission_shadow_observation_ingress where status='PROCESSING') processing_count,
 (select count(*) from permission_shadow_observation_ingress where status='PENDING' and available_at<=now()) ready_count,
 coalesce((select extract(epoch from(now()-min(enqueued_at)))::bigint from permission_shadow_observation_ingress where status='PENDING'),0) oldest_pending_seconds,
 (select count(*) from permission_shadow_observation_dead_letters where failed_at>=now()-interval '24 hours') dead_letters_24h,
 coalesce((select sum(dropped_count) from permission_shadow_observation_metrics where bucket_start>=now()-interval '24 hours'),0) dropped_24h,
 coalesce((select sum(retry_count) from permission_shadow_observation_metrics where bucket_start>=now()-interval '24 hours'),0) retries_24h,
 coalesce((select sum(observation_count) from permission_shadow_observation_metrics where bucket_start>=now()-interval '24 hours'),0) processed_24h,
 (select queue_capacity from permission_shadow_pipeline_settings where singleton_id='ACTIVE') queue_capacity,
 (select enabled from permission_shadow_pipeline_settings where singleton_id='ACTIVE') enabled;

create or replace view permission_shadow_storage_readiness with (security_invoker=true) as
select
 (select count(*) from permission_shadow_durable_mismatches) durable_mismatch_rows,
 (select count(*) from permission_shadow_sampled_matches) sampled_match_rows,
 (select count(*) from permission_shadow_observation_metrics) metric_rows,
 (select count(*) from permission_shadow_observation_receipts) receipt_rows,
 (select count(*) from permission_shadow_partition_archives where status='ARCHIVED') archived_partitions,
 (select count(*) from permission_shadow_partition_archives where status='ARCHIVED' and purge_after<now()) overdue_archive_purges,
 (select count(*) from pg_inherits i join pg_class p on p.oid=i.inhparent where p.relname='permission_shadow_durable_mismatches') durable_partitions,
 (select count(*) from pg_inherits i join pg_class p on p.oid=i.inhparent where p.relname='permission_shadow_sampled_matches') sampled_partitions,
 (select count(*) from pg_inherits i join pg_class p on p.oid=i.inhparent where p.relname='permission_shadow_observation_dead_letters') dead_letter_partitions,
 (select max(completed_at) from permission_shadow_retention_runs where status='COMPLETED') last_retention_completed_at;
