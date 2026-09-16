-- Phase 6B Readiness Binding / Cutover Control.
-- Creates immutable evidence bindings, approval-separated cutover plans, idempotent publication,
-- runtime snapshot refresh evidence and INSTANCE-scoped IAM permissions.

select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','phase6b-migration',true);

-- Publish the Phase 6B permission catalog revision.
insert into permission_catalog_revisions(revision_id,revision_code,revision_number,status,content_hash,description,supersedes_revision_id,created_at,created_by,published_at,published_by,version)
select '00000000-0000-0000-0000-000000000009'::uuid,'PHASE6B-0.8.2',coalesce(max(revision_number),0)+1,'DRAFT','DRAFT:UNPUBLISHED',
 'Phase 6B immutable readiness binding, cutover-plan approval and authority revision publication permissions.',
 (select revision_id from permission_catalog_active_revision where singleton_id='ACTIVE'),now(),'phase6b-migration',null,null,1
from permission_catalog_revisions on conflict(revision_id) do nothing;

insert into permission_catalog_revision_entries(revision_id,permission_code,owner_module,resource_type,action_code,description,risk_level,risk_lane,lifecycle,allowed_scope_types,system_managed,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by,version)
select '00000000-0000-0000-0000-000000000009'::uuid,e.permission_code,e.owner_module,e.resource_type,e.action_code,e.description,e.risk_level,e.risk_lane,e.lifecycle,e.allowed_scope_types,e.system_managed,e.replacement_permission_code,e.introduced_at,e.deprecated_at,e.retired_at,now(),'phase6b-migration',1
from permission_catalog_revision_entries e join permission_catalog_active_revision a on a.singleton_id='ACTIVE' and a.revision_id=e.revision_id
on conflict(revision_id,permission_code) do nothing;
insert into permission_catalog_revision_aliases(revision_id,alias_code,canonical_permission_code,alias_type,valid_from,valid_until,reason,created_by,created_at,version)
select '00000000-0000-0000-0000-000000000009'::uuid,a.alias_code,a.canonical_permission_code,a.alias_type,a.valid_from,a.valid_until,a.reason,'phase6b-migration',now(),1
from permission_catalog_revision_aliases a join permission_catalog_active_revision active on active.singleton_id='ACTIVE' and active.revision_id=a.revision_id
on conflict(revision_id,alias_code) do nothing;

insert into permission_catalog_revision_entries(revision_id,permission_code,owner_module,resource_type,action_code,description,risk_level,risk_lane,lifecycle,allowed_scope_types,system_managed,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by,version)
values
 ('00000000-0000-0000-0000-000000000009','permission.enforcement_cutover.read','enforcement-activation','ENFORCEMENT_CUTOVER_PLAN','READ','Read Phase 6 cutover plans, immutable evidence bindings and runtime snapshot refresh status.','HIGH','READ','ACTIVE',array['INSTANCE'],true,null,now(),null,null,now(),'phase6b-migration',1),
 ('00000000-0000-0000-0000-000000000009','permission.enforcement_cutover.plan','enforcement-activation','ENFORCEMENT_CUTOVER_PLAN','MANAGE','Create draft cutover plans, replace routes and bind immutable readiness evidence.','CRITICAL','ADMIN','ACTIVE',array['INSTANCE'],true,null,now(),null,null,now(),'phase6b-migration',1),
 ('00000000-0000-0000-0000-000000000009','permission.enforcement_cutover.review','enforcement-activation','ENFORCEMENT_CUTOVER_PLAN','REVIEW','Submit a complete cutover plan for independent review.','CRITICAL','CRITICAL','ACTIVE',array['INSTANCE'],true,null,now(),null,null,now(),'phase6b-migration',1),
 ('00000000-0000-0000-0000-000000000009','permission.enforcement_cutover.approve','enforcement-activation','ENFORCEMENT_CUTOVER_PLAN','APPROVE','Independently approve or reject a submitted cutover plan.','CRITICAL','CRITICAL','ACTIVE',array['INSTANCE'],true,null,now(),null,null,now(),'phase6b-migration',1),
 ('00000000-0000-0000-0000-000000000009','permission.enforcement_cutover.publish','enforcement-activation','ENFORCEMENT_AUTHORITY_REVISION','PUBLISH','Publish an approved immutable Authority Revision and atomically refresh the local router.','CRITICAL','CRITICAL','ACTIVE',array['INSTANCE'],true,null,now(),null,null,now(),'phase6b-migration',1)
on conflict(revision_id,permission_code) do update set owner_module=excluded.owner_module,resource_type=excluded.resource_type,action_code=excluded.action_code,description=excluded.description,risk_level=excluded.risk_level,risk_lane=excluded.risk_lane,lifecycle='ACTIVE',allowed_scope_types=excluded.allowed_scope_types,updated_at=excluded.updated_at,updated_by=excluded.updated_by,version=permission_catalog_revision_entries.version+1;

select set_config('app.permission_catalog_publish_revision_id','00000000-0000-0000-0000-000000000009',true);
insert into permission_definitions(permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,active,version,owner_module,risk_lane,lifecycle,catalog_revision_id,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by)
select permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,lifecycle<>'RETIRED',1,owner_module,risk_lane,lifecycle,revision_id,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by
from permission_catalog_revision_entries where revision_id='00000000-0000-0000-0000-000000000009'::uuid
on conflict(permission_code) do update set resource_type=excluded.resource_type,action_code=excluded.action_code,description=excluded.description,risk_level=excluded.risk_level,allowed_scope_types=excluded.allowed_scope_types,system_managed=excluded.system_managed,active=excluded.active,owner_module=excluded.owner_module,risk_lane=excluded.risk_lane,lifecycle=excluded.lifecycle,catalog_revision_id=excluded.catalog_revision_id,replacement_permission_code=excluded.replacement_permission_code,deprecated_at=excluded.deprecated_at,retired_at=excluded.retired_at,updated_at=excluded.updated_at,updated_by=excluded.updated_by,version=permission_definitions.version+1;
update permission_catalog_revisions set status='SUPERSEDED',version=version+1 where revision_id=(select revision_id from permission_catalog_active_revision where singleton_id='ACTIVE') and revision_id<>'00000000-0000-0000-0000-000000000009'::uuid and status='PUBLISHED';
update permission_catalog_revisions set status='PUBLISHED',content_hash=(with catalog_lines as (
 select 'P|'||permission_code||'|'||owner_module||'|'||resource_type||'|'||action_code||'|'||description||'|'||risk_level||'|'||risk_lane||'|'||lifecycle||'|'||coalesce(array_to_string(allowed_scope_types,','),'')||'|'||system_managed::text||'|'||coalesce(replacement_permission_code,'') line from permission_catalog_revision_entries where revision_id='00000000-0000-0000-0000-000000000009'::uuid
 union all select 'A|'||alias_code||'|'||canonical_permission_code||'|'||alias_type||'|'||coalesce(to_char(valid_from at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'')||'|'||coalesce(to_char(valid_until at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'')||'|'||reason from permission_catalog_revision_aliases where revision_id='00000000-0000-0000-0000-000000000009'::uuid)
 select 'sha256:'||encode(sha256(convert_to(coalesce(string_agg(line,E'\n' order by line),''),'UTF8')),'hex') from catalog_lines),published_at=now(),published_by='phase6b-migration',version=version+1
where revision_id='00000000-0000-0000-0000-000000000009'::uuid and status='DRAFT';
update permission_catalog_active_revision set revision_id='00000000-0000-0000-0000-000000000009'::uuid,activated_at=now(),activated_by='phase6b-migration',version=version+1 where singleton_id='ACTIVE';
insert into permission_catalog_publication_events(publication_id,revision_id,previous_revision_id,content_hash,entry_count,alias_count,actor_id,audit_reason,correlation_id,published_at)
select '00000000-0000-0000-0000-000000009009'::uuid,r.revision_id,r.supersedes_revision_id,r.content_hash,(select count(*)::integer from permission_catalog_revision_entries e where e.revision_id=r.revision_id),(select count(*)::integer from permission_catalog_revision_aliases a where a.revision_id=r.revision_id),'phase6b-migration','Phase 6B migration-owned Catalog publication','phase6b-migration',coalesce(r.published_at,now()) from permission_catalog_revisions r where r.revision_id='00000000-0000-0000-0000-000000000009'::uuid on conflict(publication_id) do nothing;

insert into rbac_role_permissions(grant_id,tenant_id,role_id,permission_point,created_at,created_by,version) values
 ('grant-system-admin-enforcement-cutover-read',null,'role-system-admin','permission.enforcement_cutover.read',now(),'phase6b-migration',1),
 ('grant-system-admin-enforcement-cutover-plan',null,'role-system-admin','permission.enforcement_cutover.plan',now(),'phase6b-migration',1),
 ('grant-system-admin-enforcement-cutover-review',null,'role-system-admin','permission.enforcement_cutover.review',now(),'phase6b-migration',1),
 ('grant-system-admin-enforcement-cutover-approve',null,'role-system-admin','permission.enforcement_cutover.approve',now(),'phase6b-migration',1),
 ('grant-system-admin-enforcement-cutover-publish',null,'role-system-admin','permission.enforcement_cutover.publish',now(),'phase6b-migration',1),
 ('grant-security-admin-enforcement-cutover-read',null,'role-security-admin','permission.enforcement_cutover.read',now(),'phase6b-migration',1),
 ('grant-security-admin-enforcement-cutover-plan',null,'role-security-admin','permission.enforcement_cutover.plan',now(),'phase6b-migration',1),
 ('grant-security-admin-enforcement-cutover-review',null,'role-security-admin','permission.enforcement_cutover.review',now(),'phase6b-migration',1),
 ('grant-security-admin-enforcement-cutover-approve',null,'role-security-admin','permission.enforcement_cutover.approve',now(),'phase6b-migration',1),
 ('grant-security-admin-enforcement-cutover-publish',null,'role-security-admin','permission.enforcement_cutover.publish',now(),'phase6b-migration',1),
 ('grant-auditor-enforcement-cutover-read',null,'role-auditor','permission.enforcement_cutover.read',now(),'phase6b-migration',1)
on conflict(grant_id) do nothing;

create table if not exists enforcement_cutover_plans(
 plan_id uuid primary key,title varchar(160) not null,description text not null default '',status varchar(24) not null check(status in('DRAFT','IN_REVIEW','APPROVED','REJECTED','PUBLISHED')),
 created_by varchar(128) not null,created_at timestamptz not null,submitted_by varchar(128),submitted_at timestamptz,approved_by varchar(128),approved_at timestamptz,rejected_by varchar(128),rejected_at timestamptz,rejection_reason text,published_revision bigint,published_by varchar(128),published_at timestamptz,
 last_audit_reason text not null,correlation_id varchar(128) not null,version bigint not null default 1 check(version>0));
create index if not exists idx_enforcement_cutover_plan_status on enforcement_cutover_plans(status,created_at desc);

create table if not exists enforcement_cutover_plan_routes(
 plan_id uuid not null references enforcement_cutover_plans(plan_id),route_order integer not null check(route_order>=0),tenant_id varchar(64) not null check(tenant_id<>'*'),domain_code varchar(64) not null,unit_code varchar(128) not null,risk_lane varchar(64) not null,entry_point_id varchar(220) not null,
 authority_mode varchar(32) not null check(authority_mode in('LEGACY_ONLY','SHADOW','TARGET_CANARY','TARGET_PRIMARY','TARGET_ONLY','PAUSED')),target_basis_points integer not null check(target_basis_points between 0 and 10000),include_cohorts text[] not null default '{}',exclude_cohorts text[] not null default '{}',reason_code varchar(128) not null,created_at timestamptz not null default now(),
 primary key(plan_id,tenant_id,domain_code,unit_code,risk_lane,entry_point_id),unique(plan_id,route_order),
 constraint ck_cutover_plan_route_mode_bps check((authority_mode in('LEGACY_ONLY','SHADOW','PAUSED') and target_basis_points=0) or (authority_mode='TARGET_CANARY' and target_basis_points between 1 and 9999) or (authority_mode in('TARGET_PRIMARY','TARGET_ONLY') and target_basis_points=10000)),
 constraint ck_cutover_plan_route_cohort_overlap check(not(include_cohorts && exclude_cohorts)),
 constraint ck_cutover_plan_phase6b_mode check(authority_mode<>'TARGET_ONLY'),
 constraint ck_cutover_plan_target_only_cohorts check(authority_mode<>'TARGET_ONLY' or(cardinality(include_cohorts)=0 and cardinality(exclude_cohorts)=0)));

create table if not exists enforcement_cutover_evidence_bindings(
 plan_id uuid not null references enforcement_cutover_plans(plan_id),evidence_type varchar(32) not null check(evidence_type in('PHASE6_ELIGIBILITY','DOMAIN_READINESS','RUNTIME_CERTIFICATION')),evidence_id uuid not null,tenant_id varchar(64) not null,domain_code varchar(64) not null default '',evidence_status varchar(32) not null,evidence_checksum varchar(71) not null check(evidence_checksum like 'sha256:%'),evaluated_at timestamptz not null,expires_at timestamptz,source_revision varchar(128) not null default '',immutable_payload jsonb not null,bound_by varchar(128) not null,bound_at timestamptz not null,primary key(plan_id,evidence_type,evidence_id));
create index if not exists idx_cutover_evidence_plan_tenant on enforcement_cutover_evidence_bindings(plan_id,tenant_id,evidence_type);

create table if not exists enforcement_cutover_plan_events(
 event_id uuid primary key default gen_random_uuid(),plan_id uuid not null references enforcement_cutover_plans(plan_id),event_type varchar(64) not null,actor_id varchar(128) not null,audit_reason text not null,correlation_id varchar(128) not null,details jsonb not null default '{}'::jsonb,occurred_at timestamptz not null default now());
create index if not exists idx_cutover_plan_events_plan on enforcement_cutover_plan_events(plan_id,occurred_at desc);

create table if not exists enforcement_cutover_idempotency(
 operation_code varchar(64) not null,idempotency_key varchar(200) not null,request_hash varchar(71) not null check(request_hash like 'sha256:%'),result_plan_id uuid not null references enforcement_cutover_plans(plan_id),result_revision_id bigint,created_at timestamptz not null default now(),primary key(operation_code,idempotency_key));

create table if not exists enforcement_snapshot_refresh_status(
 singleton_id varchar(16) primary key check(singleton_id='ACTIVE'),state varchar(24) not null check(state in('BOOTSTRAP','APPLIED','FAILED')),active_revision bigint not null check(active_revision>=0),attempted_revision bigint not null check(attempted_revision>=0),last_known_good_revision bigint not null check(last_known_good_revision>=0),active_checksum varchar(71) not null,failure_code varchar(128) not null default '',failure_message text not null default '',updated_at timestamptz not null,updated_by varchar(128) not null,correlation_id varchar(128) not null,version bigint not null default 1);
insert into enforcement_snapshot_refresh_status(singleton_id,state,active_revision,attempted_revision,last_known_good_revision,active_checksum,updated_at,updated_by,correlation_id) values('ACTIVE','BOOTSTRAP',0,0,0,'BOOTSTRAP_LEGACY_ONLY',now(),'phase6b-migration','phase6b-migration') on conflict(singleton_id) do nothing;
create table if not exists enforcement_snapshot_refresh_events(
 event_id uuid primary key default gen_random_uuid(),state varchar(24) not null,active_revision bigint not null,attempted_revision bigint not null,last_known_good_revision bigint not null,active_checksum varchar(71) not null,failure_code varchar(128) not null default '',failure_message text not null default '',actor_id varchar(128) not null,correlation_id varchar(128) not null,occurred_at timestamptz not null default now());

create sequence if not exists enforcement_authority_revision_seq;
select setval('enforcement_authority_revision_seq',coalesce((select max(revision_id)+1 from enforcement_authority_revisions),1),false);
alter table enforcement_authority_revisions add column if not exists plan_id uuid;
do $$ begin
 if not exists(select 1 from pg_constraint where conname='fk_enforcement_authority_revision_plan') then
  alter table enforcement_authority_revisions add constraint fk_enforcement_authority_revision_plan foreign key(plan_id) references enforcement_cutover_plans(plan_id);
 end if;
end $$;
create unique index if not exists uq_enforcement_authority_revision_plan on enforcement_authority_revisions(plan_id) where plan_id is not null;


-- V99 adds plan_id after the Phase 6A trigger was compiled. Rebind the immutable
-- revision guard so plan_id cannot be attached, changed or removed after insert.
create or replace function phase6a_guard_revision_transition() returns trigger language plpgsql as $$
begin
 if TG_OP='DELETE' then raise exception 'PHASE6A_REVISION_DELETE_FORBIDDEN' using errcode='55000'; end if;
 if row(new.revision_id,new.snapshot_checksum,new.readiness_evidence_refs,new.reason,new.created_by,new.created_at,new.plan_id)
    is distinct from row(old.revision_id,old.snapshot_checksum,old.readiness_evidence_refs,old.reason,old.created_by,old.created_at,old.plan_id) then
  raise exception 'PHASE6A_REVISION_CONTENT_IMMUTABLE' using errcode='55000';
 end if;
 if old.status='DRAFT' and new.status='APPROVED'
    and old.approved_by is null and old.approved_at is null
    and new.approved_by is not null and new.approved_at is not null
    and new.published_by is null and new.published_at is null then return new; end if;
 if old.status='DRAFT' and new.status='REJECTED'
    and new.approved_by is null and new.approved_at is null
    and new.published_by is null and new.published_at is null then return new; end if;
 if old.status='APPROVED' and new.status='PUBLISHED'
    and new.approved_by is not distinct from old.approved_by
    and new.approved_at is not distinct from old.approved_at
    and new.published_by is not null and new.published_at is not null then return new; end if;
 raise exception 'PHASE6A_REVISION_TRANSITION_INVALID' using errcode='23514';
end $$;

create or replace function phase6b_guard_plan_mutation() returns trigger language plpgsql as $$
begin
 if TG_OP='DELETE' then raise exception 'PHASE6B_PLAN_DELETE_FORBIDDEN' using errcode='55000'; end if;
 if new.version<>old.version+1 then raise exception 'PHASE6B_PLAN_VERSION_INVALID' using errcode='23514'; end if;
 if row(new.plan_id,new.created_by,new.created_at) is distinct from row(old.plan_id,old.created_by,old.created_at) then raise exception 'PHASE6B_PLAN_IDENTITY_IMMUTABLE' using errcode='55000'; end if;
 if old.status<>'DRAFT' and row(new.title,new.description) is distinct from row(old.title,old.description) then raise exception 'PHASE6B_APPROVED_PLAN_CONTENT_IMMUTABLE' using errcode='55000'; end if;
 if old.status='DRAFT' and new.status='DRAFT' then return new; end if;
 if old.status='DRAFT' and new.status='IN_REVIEW' and new.submitted_by is not null and new.submitted_at is not null then return new; end if;
 if old.status='IN_REVIEW' and new.status='APPROVED' and new.approved_by is not null and new.approved_at is not null and new.approved_by<>new.created_by and new.approved_by<>new.submitted_by then return new; end if;
 if old.status='IN_REVIEW' and new.status='REJECTED' and new.rejected_by is not null and new.rejected_at is not null and length(trim(coalesce(new.rejection_reason,'')))>=12 and new.rejected_by<>new.created_by and new.rejected_by<>new.submitted_by then return new; end if;
 if old.status='APPROVED' and new.status='PUBLISHED' and new.published_revision is not null and new.published_by is not null and new.published_at is not null and new.published_by<>new.created_by then return new; end if;
 raise exception 'PHASE6B_PLAN_TRANSITION_INVALID' using errcode='23514';
end $$;
create trigger trg_phase6b_plan_guard before update or delete on enforcement_cutover_plans for each row execute function phase6b_guard_plan_mutation();

create or replace function phase6b_require_draft_plan() returns trigger language plpgsql as $$
declare v_status varchar;
begin
 select status into v_status from enforcement_cutover_plans where plan_id=coalesce(new.plan_id,old.plan_id);
 if v_status is distinct from 'DRAFT' then raise exception 'PHASE6B_PLAN_CONTENT_REQUIRES_DRAFT' using errcode='23514'; end if;
 if TG_OP='DELETE' then return old; end if;
 return new;
end $$;
create trigger trg_phase6b_plan_route_draft before insert or update or delete on enforcement_cutover_plan_routes for each row execute function phase6b_require_draft_plan();
create trigger trg_phase6b_evidence_insert_draft before insert on enforcement_cutover_evidence_bindings for each row execute function phase6b_require_draft_plan();

create or replace function phase6b_reject_immutable_mutation() returns trigger language plpgsql as $$ begin raise exception 'PHASE6B_EVIDENCE_APPEND_ONLY' using errcode='55000'; end $$;
create trigger trg_phase6b_evidence_immutable before update or delete on enforcement_cutover_evidence_bindings for each row execute function phase6b_reject_immutable_mutation();
create trigger trg_phase6b_plan_event_immutable before update or delete on enforcement_cutover_plan_events for each row execute function phase6b_reject_immutable_mutation();
create trigger trg_phase6b_idempotency_immutable before update or delete on enforcement_cutover_idempotency for each row execute function phase6b_reject_immutable_mutation();
create trigger trg_phase6b_refresh_event_immutable before update or delete on enforcement_snapshot_refresh_events for each row execute function phase6b_reject_immutable_mutation();

alter table enforcement_cutover_plans enable row level security;alter table enforcement_cutover_plans force row level security;
create policy phase6b_cutover_plan_instance_scope on enforcement_cutover_plans using(iam_current_tenant_id()='INSTANCE') with check(iam_current_tenant_id()='INSTANCE');
alter table enforcement_cutover_plan_routes enable row level security;alter table enforcement_cutover_plan_routes force row level security;
create policy phase6b_cutover_route_instance_scope on enforcement_cutover_plan_routes using(iam_current_tenant_id()='INSTANCE') with check(iam_current_tenant_id()='INSTANCE');
alter table enforcement_cutover_evidence_bindings enable row level security;alter table enforcement_cutover_evidence_bindings force row level security;
create policy phase6b_cutover_evidence_instance_scope on enforcement_cutover_evidence_bindings using(iam_current_tenant_id()='INSTANCE') with check(iam_current_tenant_id()='INSTANCE');
alter table enforcement_cutover_plan_events enable row level security;alter table enforcement_cutover_plan_events force row level security;
create policy phase6b_cutover_event_instance_scope on enforcement_cutover_plan_events using(iam_current_tenant_id()='INSTANCE') with check(iam_current_tenant_id()='INSTANCE');
alter table enforcement_cutover_idempotency enable row level security;alter table enforcement_cutover_idempotency force row level security;
create policy phase6b_cutover_idempotency_instance_scope on enforcement_cutover_idempotency using(iam_current_tenant_id()='INSTANCE') with check(iam_current_tenant_id()='INSTANCE');
alter table enforcement_snapshot_refresh_status enable row level security;alter table enforcement_snapshot_refresh_status force row level security;
create policy phase6b_snapshot_status_instance_scope on enforcement_snapshot_refresh_status using(iam_current_tenant_id()='INSTANCE') with check(iam_current_tenant_id()='INSTANCE');
alter table enforcement_snapshot_refresh_events enable row level security;alter table enforcement_snapshot_refresh_events force row level security;
create policy phase6b_snapshot_event_instance_scope on enforcement_snapshot_refresh_events using(iam_current_tenant_id()='INSTANCE') with check(iam_current_tenant_id()='INSTANCE');

comment on table enforcement_cutover_plans is 'Phase 6B approval-separated cutover plan aggregate. Routes and evidence are mutable only while DRAFT.';
comment on table enforcement_cutover_evidence_bindings is 'Immutable checksum-bound references to Phase 5I/5J readiness evidence.';
comment on table enforcement_snapshot_refresh_status is 'Last runtime refresh state; FAILED preserves the Last-known-good local snapshot.';
