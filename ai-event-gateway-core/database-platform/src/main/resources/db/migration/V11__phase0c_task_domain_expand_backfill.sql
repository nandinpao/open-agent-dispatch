-- Phase 0C: Task Domain and Relationship Core - Expand and deterministic backfill.
--
-- This migration is additive. V12 performs the enforcement after operators resolve
-- any historical hierarchy conflicts recorded in task_domain_backfill_conflicts.

alter table tasks add column if not exists task_key varchar(160);
alter table tasks add column if not exists title varchar(256);
alter table tasks add column if not exists description text;
alter table tasks add column if not exists severity varchar(32);
alter table tasks add column if not exists root_task_id varchar(128);
alter table tasks add column if not exists source_task_id varchar(128);
alter table tasks add column if not exists requesting_task_id varchar(128);
alter table tasks add column if not exists issue_sync_policy varchar(64);
alter table tasks add column if not exists created_by_type varchar(32);
alter table tasks add column if not exists created_by_id varchar(128);
alter table tasks add column if not exists version bigint;
alter table tasks add column if not exists creation_idempotency_key varchar(255);
alter table tasks add column if not exists last_transition_reason_code varchar(64);
alter table tasks add column if not exists last_transition_actor_type varchar(32);
alter table tasks add column if not exists last_transition_actor_id varchar(128);
alter table tasks add column if not exists last_transition_idempotency_key varchar(255);

create table if not exists task_domain_backfill_conflicts (
  conflict_id bigserial primary key,
  tenant_id varchar(64) not null,
  task_id varchar(128) not null,
  conflict_type varchar(64) not null,
  conflicting_reference varchar(128),
  detail text not null,
  detected_at timestamptz not null default now(),
  resolved_at timestamptz,
  resolved_by varchar(128),
  resolution_note text,
  unique (tenant_id, task_id, conflict_type, conflicting_reference)
);

create table if not exists task_relationships (
  tenant_id varchar(64) not null,
  relationship_id varchar(128) not null,
  from_task_id varchar(128) not null,
  to_task_id varchar(128) not null,
  relationship_type varchar(64) not null,
  direction varchar(32) not null default 'DIRECTED',
  reason_code varchar(64),
  description text,
  reference_visibility varchar(32) not null default 'VISIBLE',
  idempotency_key varchar(255),
  created_by_type varchar(32) not null,
  created_by_id varchar(128) not null,
  created_at timestamptz not null default now(),
  version bigint not null default 1,
  primary key (tenant_id, relationship_id)
);

create table if not exists task_participants (
  tenant_id varchar(64) not null,
  participant_id varchar(128) not null,
  task_id varchar(128) not null,
  participant_type varchar(32) not null,
  participant_ref_id varchar(128) not null,
  participant_role varchar(32) not null,
  visibility_level varchar(32) not null default 'STANDARD',
  operation_level varchar(32) not null default 'READ_ONLY',
  created_at timestamptz not null default now(),
  created_by varchar(128) not null,
  version bigint not null default 1,
  primary key (tenant_id, participant_id)
);

create table if not exists task_state_history (
  tenant_id varchar(64) not null,
  history_id varchar(128) not null,
  task_id varchar(128) not null,
  from_status varchar(32),
  to_status varchar(32) not null,
  reason_code varchar(64),
  reason text,
  actor_type varchar(32) not null default 'SYSTEM',
  actor_id varchar(128) not null default 'DB_TRIGGER',
  correlation_id varchar(128),
  transition_at timestamptz not null default now(),
  task_version bigint not null,
  idempotency_key varchar(255),
  primary key (tenant_id, history_id)
);

create table if not exists task_mutation_idempotency (
  tenant_id varchar(64) not null,
  operation_scope varchar(128) not null,
  idempotency_key varchar(255) not null,
  resource_type varchar(64) not null,
  resource_id varchar(128),
  request_hash varchar(128),
  response_hash varchar(128),
  mutation_status varchar(32) not null default 'COMPLETED',
  created_at timestamptz not null default now(),
  expires_at timestamptz,
  primary key (tenant_id, operation_scope, idempotency_key)
);

-- Deterministic Task Aggregate backfill. Existing globally unique task_id values are
-- retained as task_key values; no business identifier is fabricated from another tenant.
update tasks
   set task_key = task_id
 where task_key is null or btrim(task_key) = '';

update tasks
   set title = left(concat(coalesce(nullif(task_type_code, ''), nullif(task_type, ''), 'Task'), ' ', task_id), 256)
 where title is null or btrim(title) = '';

update tasks
   set description = created_reason
 where description is null and created_reason is not null;

update tasks
   set severity = case upper(coalesce(priority, ''))
       when 'CRITICAL' then 'CRITICAL'
       when 'URGENT' then 'CRITICAL'
       when 'HIGH' then 'HIGH'
       when 'LOW' then 'LOW'
       else 'MEDIUM'
   end
 where severity is null or btrim(severity) = '';

update tasks set source_task_id = parent_task_id
 where source_task_id is null and parent_task_id is not null;
update tasks set requesting_task_id = parent_task_id
 where requesting_task_id is null and parent_task_id is not null;
update tasks set issue_sync_policy = 'OPTIONAL'
 where issue_sync_policy is null or btrim(issue_sync_policy) = '';
update tasks set created_by_type = 'SYSTEM'
 where created_by_type is null or btrim(created_by_type) = '';
update tasks set created_by_id = 'PHASE0C_MIGRATION'
 where created_by_id is null or btrim(created_by_id) = '';
update tasks set version = 1 where version is null or version < 1;

-- Resolve roots only through same-tenant Parent links. A repeated node is excluded from
-- expansion; the unresolved task is recorded below and V12 refuses enforcement.
with recursive hierarchy as (
  select t.tenant_id,
         t.task_id,
         t.task_id as resolved_root_task_id,
         array[t.task_id]::varchar[] as path
    from tasks t
   where t.parent_task_id is null
  union all
  select child.tenant_id,
         child.task_id,
         hierarchy.resolved_root_task_id,
         hierarchy.path || child.task_id
    from hierarchy
    join tasks child
      on child.tenant_id = hierarchy.tenant_id
     and child.parent_task_id = hierarchy.task_id
   where not child.task_id = any(hierarchy.path)
)
update tasks target
   set root_task_id = hierarchy.resolved_root_task_id
  from hierarchy
 where target.tenant_id = hierarchy.tenant_id
   and target.task_id = hierarchy.task_id
   and target.root_task_id is null;

insert into task_domain_backfill_conflicts
  (tenant_id, task_id, conflict_type, conflicting_reference, detail)
select child.tenant_id,
       child.task_id,
       'MISSING_PARENT',
       child.parent_task_id,
       'Parent Task does not exist in the same Tenant.'
  from tasks child
  left join tasks parent
    on parent.tenant_id = child.tenant_id
   and parent.task_id = child.parent_task_id
 where child.parent_task_id is not null
   and parent.task_id is null
on conflict do nothing;

insert into task_domain_backfill_conflicts
  (tenant_id, task_id, conflict_type, conflicting_reference, detail)
select tenant_id,
       task_id,
       'UNRESOLVED_ROOT_OR_CYCLE',
       parent_task_id,
       'Root Task could not be resolved. Repair a missing Parent or hierarchy cycle before V12.'
  from tasks
 where root_task_id is null
on conflict do nothing;

-- Establish an immutable baseline entry for Tasks that predate the Phase 0C trigger.
insert into task_state_history(
  tenant_id, history_id, task_id, from_status, to_status, reason_code, reason,
  actor_type, actor_id, correlation_id, transition_at, task_version, idempotency_key
)
select tenant_id,
       concat('tsh-', md5(concat(tenant_id, ':', task_id, ':PHASE0C_BASELINE'))),
       task_id, null, status, 'TASK_MIGRATED',
       'Task lifecycle baseline recorded during Task domain migration.',
       'MIGRATION', 'PHASE0C_MIGRATION', correlation_id, coalesce(created_at, now()), version,
       concat('phase0c-baseline:', task_id)
  from tasks
on conflict do nothing;

-- Materialize the Phase 0B ownership columns as explicit Task Participants.
insert into task_participants(
  tenant_id, participant_id, task_id, participant_type, participant_ref_id, participant_role,
  visibility_level, operation_level, created_by
)
select tenant_id, concat('tp-', md5(concat(tenant_id, ':', task_id, ':OWNER:DEPARTMENT:', owner_department_id))),
       task_id, 'DEPARTMENT', owner_department_id, 'OWNER', 'FULL', 'ADMIN', 'PHASE0C_MIGRATION'
  from tasks
on conflict do nothing;

insert into task_participants(
  tenant_id, participant_id, task_id, participant_type, participant_ref_id, participant_role,
  visibility_level, operation_level, created_by
)
select tenant_id, concat('tp-', md5(concat(tenant_id, ':', task_id, ':REQUESTER:DEPARTMENT:', requester_department_id))),
       task_id, 'DEPARTMENT', requester_department_id, 'REQUESTER', 'STANDARD', 'COMMENT', 'PHASE0C_MIGRATION'
  from tasks
on conflict do nothing;

insert into task_participants(
  tenant_id, participant_id, task_id, participant_type, participant_ref_id, participant_role,
  visibility_level, operation_level, created_by
)
select tenant_id, concat('tp-', md5(concat(tenant_id, ':', task_id, ':EXECUTOR:SERVICE_DOMAIN:', executor_domain_id))),
       task_id, 'SERVICE_DOMAIN', executor_domain_id, 'EXECUTOR', 'FULL', 'OPERATE', 'PHASE0C_MIGRATION'
  from tasks
on conflict do nothing;

create index if not exists idx_tasks_tenant_task_key on tasks(tenant_id, task_key);
create index if not exists idx_tasks_tenant_root on tasks(tenant_id, root_task_id, created_at);
create index if not exists idx_tasks_tenant_parent on tasks(tenant_id, parent_task_id, created_at);
create index if not exists idx_task_relationships_from on task_relationships(tenant_id, from_task_id, created_at desc);
create index if not exists idx_task_relationships_to on task_relationships(tenant_id, to_task_id, created_at desc);
create index if not exists idx_task_participants_task on task_participants(tenant_id, task_id, participant_role);
create index if not exists idx_task_state_history_task on task_state_history(tenant_id, task_id, transition_at desc);
