-- Phase 6A Unified Enforcement Kernel: immutable published revisions and local-router source records.

create table if not exists enforcement_authority_revisions (
  revision_id bigint primary key,
  status varchar(24) not null check(status in('DRAFT','APPROVED','PUBLISHED','REJECTED')),
  snapshot_checksum varchar(71) not null check(snapshot_checksum like 'sha256:%'),
  readiness_evidence_refs jsonb not null default '[]'::jsonb,
  reason text not null,
  created_by varchar(128) not null,
  created_at timestamptz not null default now(),
  approved_by varchar(128),
  approved_at timestamptz,
  published_by varchar(128),
  published_at timestamptz,
  constraint ck_enforcement_authority_publication check(
    (status='PUBLISHED' and published_by is not null and published_at is not null)
    or status<>'PUBLISHED')
);

create table if not exists enforcement_authority_routes (
  revision_id bigint not null references enforcement_authority_revisions(revision_id),
  route_order integer not null check(route_order>=0),
  tenant_id varchar(64) not null,
  domain_code varchar(64) not null,
  unit_code varchar(128) not null,
  risk_lane varchar(64) not null,
  entry_point_id varchar(220) not null,
  authority_mode varchar(32) not null check(authority_mode in('LEGACY_ONLY','SHADOW','TARGET_CANARY','TARGET_PRIMARY','TARGET_ONLY','PAUSED')),
  target_basis_points integer not null check(target_basis_points between 0 and 10000),
  include_cohorts text[] not null default '{}',
  exclude_cohorts text[] not null default '{}',
  reason_code varchar(128) not null,
  created_at timestamptz not null default now(),
  primary key(revision_id,tenant_id,domain_code,unit_code,risk_lane,entry_point_id),
  constraint ck_enforcement_authority_mode_bps check(
    (authority_mode in('LEGACY_ONLY','SHADOW','PAUSED') and target_basis_points=0)
    or (authority_mode='TARGET_CANARY' and target_basis_points between 1 and 9999)
    or (authority_mode in('TARGET_PRIMARY','TARGET_ONLY') and target_basis_points=10000)),
  constraint ck_enforcement_authority_cohort_overlap check(not(include_cohorts && exclude_cohorts)),
  constraint ck_enforcement_target_only_cohorts check(authority_mode<>'TARGET_ONLY' or (cardinality(include_cohorts)=0 and cardinality(exclude_cohorts)=0))
);
create index if not exists idx_enforcement_authority_routes_lookup on enforcement_authority_routes(revision_id,tenant_id,domain_code,risk_lane,entry_point_id);

create table if not exists enforcement_authority_events (
  event_id uuid primary key default gen_random_uuid(),
  revision_id bigint not null references enforcement_authority_revisions(revision_id),
  event_type varchar(64) not null,
  previous_revision_id bigint,
  actor_id varchar(128) not null,
  audit_reason text not null,
  correlation_id varchar(128),
  details jsonb not null default '{}'::jsonb,
  occurred_at timestamptz not null default now()
);

create or replace function phase6a_guard_revision_transition() returns trigger language plpgsql as $$
begin
 if TG_OP='DELETE' then raise exception 'PHASE6A_REVISION_DELETE_FORBIDDEN' using errcode='55000'; end if;
 if row(new.revision_id,new.snapshot_checksum,new.readiness_evidence_refs,new.reason,new.created_by,new.created_at)
    is distinct from row(old.revision_id,old.snapshot_checksum,old.readiness_evidence_refs,old.reason,old.created_by,old.created_at) then
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
create trigger trg_phase6a_revision_transition before update or delete on enforcement_authority_revisions for each row execute function phase6a_guard_revision_transition();

create or replace function phase6a_guard_route_insert() returns trigger language plpgsql as $$
declare v_status varchar;
begin
 select status into v_status from enforcement_authority_revisions where revision_id=new.revision_id;
 if v_status is distinct from 'DRAFT' then raise exception 'PHASE6A_ROUTE_INSERT_REQUIRES_DRAFT_REVISION' using errcode='23514'; end if;
 return new;
end $$;
create trigger trg_phase6a_route_insert_guard before insert on enforcement_authority_routes for each row execute function phase6a_guard_route_insert();

create or replace function phase6a_reject_append_only_mutation() returns trigger language plpgsql as $$
begin raise exception 'PHASE6A_AUTHORITY_EVIDENCE_APPEND_ONLY' using errcode='55000'; end $$;
create trigger trg_phase6a_route_immutable before update or delete on enforcement_authority_routes for each row execute function phase6a_reject_append_only_mutation();
create trigger trg_phase6a_event_immutable before update or delete on enforcement_authority_events for each row execute function phase6a_reject_append_only_mutation();

alter table enforcement_authority_revisions enable row level security;
alter table enforcement_authority_revisions force row level security;
create policy phase6a_revision_instance_scope on enforcement_authority_revisions using(iam_current_tenant_id()='INSTANCE') with check(iam_current_tenant_id()='INSTANCE');
alter table enforcement_authority_routes enable row level security;
alter table enforcement_authority_routes force row level security;
create policy phase6a_route_instance_scope on enforcement_authority_routes using(iam_current_tenant_id()='INSTANCE') with check(iam_current_tenant_id()='INSTANCE');
alter table enforcement_authority_events enable row level security;
alter table enforcement_authority_events force row level security;
create policy phase6a_event_instance_scope on enforcement_authority_events using(iam_current_tenant_id()='INSTANCE') with check(iam_current_tenant_id()='INSTANCE');

comment on table enforcement_authority_revisions is 'Immutable Phase 6 authority snapshot revisions; runtime routers consume only PUBLISHED revisions.';
comment on table enforcement_authority_routes is 'Domain-neutral route keys: tenant + domain + unit + risk lane + entry point.';
comment on table enforcement_authority_events is 'Append-only publish and future protected-rollback audit evidence.';
