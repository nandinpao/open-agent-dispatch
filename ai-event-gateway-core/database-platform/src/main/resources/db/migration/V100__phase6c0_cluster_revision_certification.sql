-- Phase 6C-0 Environment Certification and cluster-safe Authority Revision convergence.
-- Separates the global desired target from per-node Runtime status. No Canary entry point is enabled here.

select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','phase6c0-migration',true);

create table if not exists enforcement_authority_activation_target(
  singleton_id varchar(16) primary key check(singleton_id='ACTIVE'),
  target_revision bigint not null check(target_revision>=0),
  target_checksum varchar(71) not null,
  generation bigint not null check(generation>=0),
  updated_at timestamptz not null,
  updated_by varchar(128) not null,
  correlation_id varchar(128) not null
);

insert into enforcement_authority_activation_target(
  singleton_id,target_revision,target_checksum,generation,updated_at,updated_by,correlation_id)
select 'ACTIVE',r.revision_id,r.snapshot_checksum,1,now(),'phase6c0-migration','phase6c0-migration'
from enforcement_authority_revisions r
where r.status='PUBLISHED'
order by r.revision_id desc
limit 1
on conflict(singleton_id) do nothing;

insert into enforcement_authority_activation_target(
  singleton_id,target_revision,target_checksum,generation,updated_at,updated_by,correlation_id)
values('ACTIVE',0,'BOOTSTRAP_LEGACY_ONLY',0,now(),'phase6c0-migration','phase6c0-migration')
on conflict(singleton_id) do nothing;

create table if not exists enforcement_snapshot_node_status(
  node_id varchar(128) primary key,
  state varchar(24) not null check(state in('BOOTSTRAP','APPLIED','FAILED')),
  active_revision bigint not null check(active_revision>=0),
  attempted_revision bigint not null check(attempted_revision>=0),
  last_known_good_revision bigint not null check(last_known_good_revision>=0),
  active_checksum varchar(71) not null,
  failure_code varchar(128) not null default '',
  failure_message text not null default '',
  updated_at timestamptz not null,
  updated_by varchar(128) not null,
  correlation_id varchar(128) not null,
  version bigint not null default 1 check(version>0)
);
create index if not exists idx_enforcement_snapshot_node_status_revision
  on enforcement_snapshot_node_status(active_revision,state,updated_at desc);

create table if not exists enforcement_snapshot_node_events(
  event_id uuid primary key default gen_random_uuid(),
  node_id varchar(128) not null,
  state varchar(24) not null check(state in('BOOTSTRAP','APPLIED','FAILED')),
  active_revision bigint not null check(active_revision>=0),
  attempted_revision bigint not null check(attempted_revision>=0),
  last_known_good_revision bigint not null check(last_known_good_revision>=0),
  active_checksum varchar(71) not null,
  failure_code varchar(128) not null default '',
  failure_message text not null default '',
  actor_id varchar(128) not null,
  correlation_id varchar(128) not null,
  occurred_at timestamptz not null default now()
);
create index if not exists idx_enforcement_snapshot_node_events_node
  on enforcement_snapshot_node_events(node_id,occurred_at desc);

create or replace function phase6c0_validate_activation_target() returns trigger language plpgsql as $$
declare
  v_checksum varchar(71);
begin
  if new.target_revision=0 then
    if new.target_checksum<>'BOOTSTRAP_LEGACY_ONLY' or new.generation<>0 then
      raise exception 'PHASE6C0_BOOTSTRAP_TARGET_INVALID' using errcode='23514';
    end if;
    return new;
  end if;
  select snapshot_checksum into v_checksum
  from enforcement_authority_revisions
  where revision_id=new.target_revision and status='PUBLISHED';
  if v_checksum is null then
    raise exception 'PHASE6C0_TARGET_REVISION_NOT_PUBLISHED' using errcode='23514';
  end if;
  if v_checksum is distinct from new.target_checksum then
    raise exception 'PHASE6C0_TARGET_CHECKSUM_MISMATCH' using errcode='23514';
  end if;
  if TG_OP='UPDATE' then
    if new.target_revision<=old.target_revision then
      raise exception 'PHASE6C0_TARGET_REVISION_MUST_ADVANCE' using errcode='23514';
    end if;
    if new.generation<>old.generation+1 then
      raise exception 'PHASE6C0_TARGET_GENERATION_INVALID' using errcode='23514';
    end if;
  elsif new.generation<1 then
    raise exception 'PHASE6C0_TARGET_GENERATION_INVALID' using errcode='23514';
  end if;
  return new;
end $$;
create trigger trg_phase6c0_activation_target_guard
before insert or update on enforcement_authority_activation_target
for each row execute function phase6c0_validate_activation_target();

create or replace function phase6c0_reject_activation_target_delete() returns trigger language plpgsql as $$
begin
  raise exception 'PHASE6C0_ACTIVATION_TARGET_DELETE_FORBIDDEN' using errcode='55000';
end $$;
create trigger trg_phase6c0_activation_target_delete_guard
before delete on enforcement_authority_activation_target
for each row execute function phase6c0_reject_activation_target_delete();

create or replace function phase6c0_reject_node_event_mutation() returns trigger language plpgsql as $$
begin
  raise exception 'PHASE6C0_NODE_EVENT_APPEND_ONLY' using errcode='55000';
end $$;
create trigger trg_phase6c0_node_event_immutable
before update or delete on enforcement_snapshot_node_events
for each row execute function phase6c0_reject_node_event_mutation();

alter table enforcement_authority_activation_target enable row level security;
alter table enforcement_authority_activation_target force row level security;
create policy phase6c0_activation_target_instance_scope
  on enforcement_authority_activation_target
  using(iam_current_tenant_id()='INSTANCE')
  with check(iam_current_tenant_id()='INSTANCE');

alter table enforcement_snapshot_node_status enable row level security;
alter table enforcement_snapshot_node_status force row level security;
create policy phase6c0_node_status_instance_scope
  on enforcement_snapshot_node_status
  using(iam_current_tenant_id()='INSTANCE')
  with check(iam_current_tenant_id()='INSTANCE');

alter table enforcement_snapshot_node_events enable row level security;
alter table enforcement_snapshot_node_events force row level security;
create policy phase6c0_node_event_instance_scope
  on enforcement_snapshot_node_events
  using(iam_current_tenant_id()='INSTANCE')
  with check(iam_current_tenant_id()='INSTANCE');

comment on table enforcement_authority_activation_target is 'Cluster-wide desired published Authority Revision. Runtime nodes poll and converge without reading mutable plan state.';
comment on table enforcement_snapshot_node_status is 'Per-node Last-known-good Runtime Snapshot state; nodes never overwrite another node status row.';
comment on table enforcement_snapshot_node_events is 'Append-only per-node snapshot convergence, failure and restart evidence.';
