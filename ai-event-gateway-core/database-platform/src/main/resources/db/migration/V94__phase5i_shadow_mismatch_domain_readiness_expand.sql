-- Phase 5I expand: semantic shadow comparator evidence, mismatch governance and Domain Readiness.
-- PostgreSQL remains the single Permission and readiness evidence authority.

-- Flyway runs each migration in its own transaction. Set an explicit INSTANCE
-- authority context before accessing FORCE-RLS protected global RBAC rows.
select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','phase5i-migration',true);

insert into permission_catalog_revisions(revision_id,revision_code,revision_number,status,content_hash,description,supersedes_revision_id,created_at,created_by,published_at,published_by,version)
select '00000000-0000-0000-0000-000000000007'::uuid,'PHASE5I-0.8.2',coalesce(max(revision_number),0)+1,'DRAFT','DRAFT:UNPUBLISHED',
 'Phase 5I Shadow Comparator v2, Mismatch Governance, Domain Readiness and Phase 6 eligibility permissions.',
 (select revision_id from permission_catalog_active_revision where singleton_id='ACTIVE'),now(),'phase5i-migration',null,null,1
from permission_catalog_revisions on conflict(revision_id) do nothing;

insert into permission_catalog_revision_entries(revision_id,permission_code,owner_module,resource_type,action_code,description,risk_level,risk_lane,lifecycle,allowed_scope_types,system_managed,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by,version)
select '00000000-0000-0000-0000-000000000007'::uuid,e.permission_code,e.owner_module,e.resource_type,e.action_code,e.description,e.risk_level,e.risk_lane,e.lifecycle,e.allowed_scope_types,e.system_managed,e.replacement_permission_code,e.introduced_at,e.deprecated_at,e.retired_at,now(),'phase5i-migration',1
from permission_catalog_revision_entries e join permission_catalog_active_revision a on a.singleton_id='ACTIVE' and a.revision_id=e.revision_id
on conflict(revision_id,permission_code) do nothing;
insert into permission_catalog_revision_aliases(revision_id,alias_code,canonical_permission_code,alias_type,valid_from,valid_until,reason,created_by,created_at,version)
select '00000000-0000-0000-0000-000000000007'::uuid,a.alias_code,a.canonical_permission_code,a.alias_type,a.valid_from,a.valid_until,a.reason,'phase5i-migration',now(),1
from permission_catalog_revision_aliases a join permission_catalog_active_revision active on active.singleton_id='ACTIVE' and active.revision_id=a.revision_id
on conflict(revision_id,alias_code) do nothing;

insert into permission_catalog_revision_entries(revision_id,permission_code,owner_module,resource_type,action_code,description,risk_level,risk_lane,lifecycle,allowed_scope_types,system_managed,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by,version)
values
 ('00000000-0000-0000-0000-000000000007','permission.shadow.read','permission-readiness','SHADOW_OBSERVATION','READ','Read semantic legacy versus target Shadow Decision observations.','HIGH','READ','ACTIVE',array['INSTANCE'],true,null,now(),null,null,now(),'phase5i-migration',1),
 ('00000000-0000-0000-0000-000000000007','permission.shadow.manage','permission-readiness','SHADOW_OBSERVATION','MANAGE','Manage PostgreSQL Shadow sampling policy and observation governance.','CRITICAL','ADMIN','ACTIVE',array['INSTANCE'],true,null,now(),null,null,now(),'phase5i-migration',1),
 ('00000000-0000-0000-0000-000000000007','permission.mismatch.read','permission-readiness','MISMATCH_CASE','READ','Read mismatch cases, waivers and regression evidence.','HIGH','READ','ACTIVE',array['INSTANCE'],true,null,now(),null,null,now(),'phase5i-migration',1),
 ('00000000-0000-0000-0000-000000000007','permission.mismatch.manage','permission-readiness','MISMATCH_CASE','MANAGE','Triage, assign and resolve mismatch cases.','CRITICAL','ADMIN','ACTIVE',array['INSTANCE'],true,null,now(),null,null,now(),'phase5i-migration',1),
 ('00000000-0000-0000-0000-000000000007','permission.mismatch.waive','permission-readiness','MISMATCH_WAIVER','APPROVE','Approve time-bounded non-critical mismatch waivers.','CRITICAL','CRITICAL','ACTIVE',array['INSTANCE'],true,null,now(),null,null,now(),'phase5i-migration',1),
 ('00000000-0000-0000-0000-000000000007','permission.domain_readiness.read','permission-readiness','DOMAIN_READINESS','READ','Read immutable Domain Readiness evidence.','HIGH','READ','ACTIVE',array['INSTANCE'],true,null,now(),null,null,now(),'phase5i-migration',1),
 ('00000000-0000-0000-0000-000000000007','permission.domain_readiness.evaluate','permission-readiness','DOMAIN_READINESS','EVALUATE','Evaluate Task, A2A, Agent, Issue and Integration readiness.','CRITICAL','CRITICAL','ACTIVE',array['INSTANCE'],true,null,now(),null,null,now(),'phase5i-migration',1),
 ('00000000-0000-0000-0000-000000000007','permission.phase6_eligibility.read','permission-readiness','PHASE6_ELIGIBILITY','READ','Read immutable Phase 6 eligibility evidence.','HIGH','READ','ACTIVE',array['INSTANCE'],true,null,now(),null,null,now(),'phase5i-migration',1),
 ('00000000-0000-0000-0000-000000000007','permission.phase6_eligibility.evaluate','permission-readiness','PHASE6_ELIGIBILITY','EVALUATE','Evaluate the fail-closed Phase 6 Canary eligibility gate.','CRITICAL','CRITICAL','ACTIVE',array['INSTANCE'],true,null,now(),null,null,now(),'phase5i-migration',1)
on conflict(revision_id,permission_code) do update set owner_module=excluded.owner_module,resource_type=excluded.resource_type,action_code=excluded.action_code,description=excluded.description,risk_level=excluded.risk_level,risk_lane=excluded.risk_lane,lifecycle='ACTIVE',allowed_scope_types=excluded.allowed_scope_types,updated_at=excluded.updated_at,updated_by=excluded.updated_by,version=permission_catalog_revision_entries.version+1;

select set_config('app.permission_catalog_publish_revision_id','00000000-0000-0000-0000-000000000007',true);
insert into permission_definitions(permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,active,version,owner_module,risk_lane,lifecycle,catalog_revision_id,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by)
select permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,lifecycle<>'RETIRED',1,owner_module,risk_lane,lifecycle,revision_id,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by
from permission_catalog_revision_entries where revision_id='00000000-0000-0000-0000-000000000007'::uuid
on conflict(permission_code) do update set resource_type=excluded.resource_type,action_code=excluded.action_code,description=excluded.description,risk_level=excluded.risk_level,allowed_scope_types=excluded.allowed_scope_types,system_managed=excluded.system_managed,active=excluded.active,owner_module=excluded.owner_module,risk_lane=excluded.risk_lane,lifecycle=excluded.lifecycle,catalog_revision_id=excluded.catalog_revision_id,replacement_permission_code=excluded.replacement_permission_code,deprecated_at=excluded.deprecated_at,retired_at=excluded.retired_at,updated_at=excluded.updated_at,updated_by=excluded.updated_by,version=permission_definitions.version+1;
update permission_catalog_revisions set status='SUPERSEDED',version=version+1 where revision_id=(select revision_id from permission_catalog_active_revision where singleton_id='ACTIVE') and revision_id<>'00000000-0000-0000-0000-000000000007'::uuid and status='PUBLISHED';
update permission_catalog_revisions set status='PUBLISHED',content_hash=(with catalog_lines as (
 select 'P|'||permission_code||'|'||owner_module||'|'||resource_type||'|'||action_code||'|'||description||'|'||risk_level||'|'||risk_lane||'|'||lifecycle||'|'||coalesce(array_to_string(allowed_scope_types,','),'')||'|'||system_managed::text||'|'||coalesce(replacement_permission_code,'') line from permission_catalog_revision_entries where revision_id='00000000-0000-0000-0000-000000000007'::uuid
 union all select 'A|'||alias_code||'|'||canonical_permission_code||'|'||alias_type||'|'||coalesce(to_char(valid_from at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'')||'|'||coalesce(to_char(valid_until at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'')||'|'||reason from permission_catalog_revision_aliases where revision_id='00000000-0000-0000-0000-000000000007'::uuid)
 select 'sha256:'||encode(sha256(convert_to(coalesce(string_agg(line,E'\n' order by line),''),'UTF8')),'hex') from catalog_lines),published_at=now(),published_by='phase5i-migration',version=version+1
where revision_id='00000000-0000-0000-0000-000000000007'::uuid and status='DRAFT';
update permission_catalog_active_revision set revision_id='00000000-0000-0000-0000-000000000007'::uuid,activated_at=now(),activated_by='phase5i-migration',version=version+1 where singleton_id='ACTIVE';
insert into permission_catalog_publication_events(publication_id,revision_id,previous_revision_id,content_hash,entry_count,alias_count,actor_id,audit_reason,correlation_id,published_at)
select '00000000-0000-0000-0000-000000007007'::uuid,r.revision_id,r.supersedes_revision_id,r.content_hash,(select count(*)::integer from permission_catalog_revision_entries e where e.revision_id=r.revision_id),(select count(*)::integer from permission_catalog_revision_aliases a where a.revision_id=r.revision_id),'phase5i-migration','Phase 5I migration-owned Catalog publication','phase5i-migration',coalesce(r.published_at,now()) from permission_catalog_revisions r where r.revision_id='00000000-0000-0000-0000-000000000007'::uuid on conflict(publication_id) do nothing;

insert into rbac_role_permissions(grant_id,tenant_id,role_id,permission_point,created_at,created_by,version) values
 ('grant-system-admin-shadow-read',null,'role-system-admin','permission.shadow.read',now(),'phase5i-migration',1),
 ('grant-system-admin-shadow-manage',null,'role-system-admin','permission.shadow.manage',now(),'phase5i-migration',1),
 ('grant-system-admin-mismatch-read',null,'role-system-admin','permission.mismatch.read',now(),'phase5i-migration',1),
 ('grant-system-admin-mismatch-manage',null,'role-system-admin','permission.mismatch.manage',now(),'phase5i-migration',1),
 ('grant-system-admin-mismatch-waive',null,'role-system-admin','permission.mismatch.waive',now(),'phase5i-migration',1),
 ('grant-system-admin-domain-read',null,'role-system-admin','permission.domain_readiness.read',now(),'phase5i-migration',1),
 ('grant-system-admin-domain-evaluate',null,'role-system-admin','permission.domain_readiness.evaluate',now(),'phase5i-migration',1),
 ('grant-system-admin-phase6-read',null,'role-system-admin','permission.phase6_eligibility.read',now(),'phase5i-migration',1),
 ('grant-system-admin-phase6-evaluate',null,'role-system-admin','permission.phase6_eligibility.evaluate',now(),'phase5i-migration',1),
 ('grant-auditor-shadow-read',null,'role-auditor','permission.shadow.read',now(),'phase5i-migration',1),
 ('grant-auditor-mismatch-read',null,'role-auditor','permission.mismatch.read',now(),'phase5i-migration',1),
 ('grant-auditor-domain-read',null,'role-auditor','permission.domain_readiness.read',now(),'phase5i-migration',1),
 ('grant-auditor-phase6-read',null,'role-auditor','permission.phase6_eligibility.read',now(),'phase5i-migration',1)
on conflict(role_id,permission_point) do nothing;

create table if not exists permission_shadow_sampling_policies(
 risk_lane varchar(16) not null,protection_mode varchar(32) not null,match_sample_bps integer not null,mismatch_durable boolean not null default true,
 match_retention_days integer not null,mismatch_retention_days integer not null,active boolean not null default true,updated_at timestamptz not null default now(),updated_by varchar(128) not null,version bigint not null default 1,
 primary key(risk_lane,protection_mode));
insert into permission_shadow_sampling_policies(risk_lane,protection_mode,match_sample_bps,mismatch_durable,match_retention_days,mismatch_retention_days,updated_by) values
 ('CRITICAL','TARGET_PERMISSION',10000,true,30,180,'phase5i-migration'),('CRITICAL','DUAL_SHADOW',10000,true,30,180,'phase5i-migration'),
 ('ADMIN','TARGET_PERMISSION',10000,true,30,180,'phase5i-migration'),('ADMIN','DUAL_SHADOW',10000,true,30,180,'phase5i-migration'),
 ('EXPORT','TARGET_PERMISSION',10000,true,30,180,'phase5i-migration'),('EXPORT','DUAL_SHADOW',10000,true,30,180,'phase5i-migration'),
 ('WRITE','TARGET_PERMISSION',10000,true,30,180,'phase5i-migration'),('WRITE','DUAL_SHADOW',10000,true,30,180,'phase5i-migration'),
 ('READ','TARGET_PERMISSION',2000,true,14,90,'phase5i-migration'),('READ','DUAL_SHADOW',2000,true,14,90,'phase5i-migration'),
 ('READ','LEGACY_AUTHORITY',500,true,7,90,'phase5i-migration'),('READ','INTERNAL_DELEGATED',100,true,7,90,'phase5i-migration'),('READ','EXEMPT',0,true,1,30,'phase5i-migration')
on conflict(risk_lane,protection_mode) do nothing;

create table if not exists resource_shadow_decision_comparisons_v2(
 tenant_id varchar(64) not null,comparison_id varchar(128) not null,domain_code varchar(32) not null,entry_point_id varchar(220),resource_type varchar(64) not null,resource_id varchar(128) not null,permission_code varchar(160) not null,
 legacy_effect varchar(32) not null,legacy_scope text not null default '',legacy_visibility varchar(32) not null,legacy_reason_code varchar(128) not null,legacy_context_complete boolean not null,legacy_error_code varchar(128),legacy_decision_id varchar(128),
 target_effect varchar(32) not null,target_scope text not null default '',target_visibility varchar(32) not null,target_reason_code varchar(128) not null,target_context_complete boolean not null,target_error_code varchar(128),target_decision_id varchar(128) not null,
 mismatch_category varchar(64) not null,severity varchar(16) not null,risk_lane varchar(16) not null,protection_mode varchar(32) not null,correlation_id varchar(128) not null,compared_at timestamptz not null,created_at timestamptz not null default now(),
 primary key(tenant_id,comparison_id));
create index if not exists idx_shadow_v2_domain_category on resource_shadow_decision_comparisons_v2(tenant_id,domain_code,mismatch_category,compared_at desc);
create index if not exists idx_shadow_v2_permission on resource_shadow_decision_comparisons_v2(permission_code,compared_at desc);

create table if not exists permission_shadow_mismatch_cases(
 case_id uuid primary key,source_tenant_id varchar(64) not null,comparison_id varchar(128) not null,domain_code varchar(32) not null,entry_point_id varchar(220),permission_code varchar(160) not null,
 category varchar(64) not null,severity varchar(16) not null,status varchar(32) not null,owner_id varchar(128) not null,sla_due_at timestamptz not null,title varchar(240) not null,resolution text,
 first_seen_at timestamptz not null,last_seen_at timestamptz not null,occurrence_count bigint not null default 1,created_at timestamptz not null,created_by varchar(128) not null,updated_at timestamptz not null,updated_by varchar(128) not null,version bigint not null default 1,
 unique(source_tenant_id,comparison_id));
create index if not exists idx_shadow_case_queue on permission_shadow_mismatch_cases(status,severity,sla_due_at,domain_code);

create table if not exists permission_shadow_mismatch_waivers(
 waiver_id uuid primary key,case_id uuid not null references permission_shadow_mismatch_cases(case_id),reason text not null,approved_by varchar(128) not null,approved_at timestamptz not null,expires_at timestamptz not null,status varchar(16) not null default 'ACTIVE',revoked_at timestamptz,revoked_by varchar(128),version bigint not null default 1);
create table if not exists permission_shadow_regression_evidence(
 evidence_id uuid primary key,case_id uuid not null references permission_shadow_mismatch_cases(case_id),test_reference varchar(320) not null,result varchar(16) not null,details jsonb not null default '{}'::jsonb,executed_at timestamptz not null,executed_by varchar(128) not null,created_at timestamptz not null default now());
create table if not exists permission_shadow_case_events(
 event_id uuid primary key,case_id uuid not null,event_type varchar(64) not null,previous_value jsonb,current_value jsonb,actor_id varchar(128) not null,audit_reason text not null,correlation_id varchar(128),occurred_at timestamptz not null default now());

create table if not exists permission_domain_readiness_thresholds(
 domain_code varchar(32) primary key,minimum_samples bigint not null,minimum_window_hours integer not null,maximum_unexpected_deny_bps integer not null,maximum_evidence_age_hours integer not null default 24,active boolean not null default true,updated_at timestamptz not null default now(),updated_by varchar(128) not null,version bigint not null default 1);
insert into permission_domain_readiness_thresholds(domain_code,minimum_samples,minimum_window_hours,maximum_unexpected_deny_bps,updated_by) values
 ('TASK',1000,24,50,'phase5i-migration'),('A2A',500,24,50,'phase5i-migration'),('AGENT',500,24,50,'phase5i-migration'),('ISSUE',500,24,50,'phase5i-migration'),('INTEGRATION',1000,24,50,'phase5i-migration')
on conflict(domain_code) do nothing;

create table if not exists permission_domain_readiness_evidence(
 evidence_id uuid primary key,source_tenant_id varchar(64) not null,domain_code varchar(32) not null,status varchar(24) not null,window_started_at timestamptz not null,window_ended_at timestamptz not null,
 sample_count bigint not null,match_count bigint not null,unexpected_allow_count bigint not null,scope_widened_count bigint not null,visibility_widened_count bigint not null,target_error_count bigint not null,context_incomplete_count bigint not null,unexpected_deny_count bigint not null,reason_different_count bigint not null,
 open_blocking_cases bigint not null,active_waivers bigint not null,failed_regressions bigint not null,manifest_coverage_percent numeric(7,4) not null,runtime_drift_blockers bigint not null,entry_point_blockers bigint not null,expired_bypasses bigint not null,blockers jsonb not null,
 catalog_revision_id uuid not null,manifest_id uuid,actor_id varchar(128) not null,audit_reason text not null,correlation_id varchar(128),evaluated_at timestamptz not null default now());
create index if not exists idx_domain_readiness_latest on permission_domain_readiness_evidence(source_tenant_id,domain_code,evaluated_at desc);

create table if not exists permission_phase6_eligibility_evidence(
 evidence_id uuid primary key,source_tenant_id varchar(64) not null,status varchar(24) not null,required_domains jsonb not null,domain_evidence_refs jsonb not null,eligible_domains bigint not null,blocked_domains bigint not null,observing_domains bigint not null,
 catalog_revision_id uuid not null,manifest_id uuid,manifest_coverage_percent numeric(7,4) not null,runtime_drift_blockers bigint not null,entry_point_blockers bigint not null,expired_bypasses bigint not null,open_blocking_cases bigint not null,blockers jsonb not null,
 actor_id varchar(128) not null,audit_reason text not null,correlation_id varchar(128),evaluated_at timestamptz not null default now());

create or replace view permission_shadow_observation_readiness with (security_invoker=true) as
select o.*,c.case_id::text,c.status case_status,c.owner_id,c.sla_due_at,
 exists(select 1 from permission_shadow_mismatch_waivers w where w.case_id=c.case_id and w.status='ACTIVE' and w.expires_at>now()) active_waiver,
 case when o.mismatch_category in('UNEXPECTED_ALLOW','SCOPE_WIDENED','VISIBILITY_WIDENED','TARGET_ERROR') then true
      when o.mismatch_category='CONTEXT_INCOMPLETE' and not exists(select 1 from permission_shadow_mismatch_waivers w where w.case_id=c.case_id and w.status='ACTIVE' and w.expires_at>now()) then true
      else false end cutover_blocker
from resource_shadow_decision_comparisons_v2 o left join permission_shadow_mismatch_cases c on c.source_tenant_id=o.tenant_id and c.comparison_id=o.comparison_id;
