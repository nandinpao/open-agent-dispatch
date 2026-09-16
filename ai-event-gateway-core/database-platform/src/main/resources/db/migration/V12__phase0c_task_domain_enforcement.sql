-- Phase 0C: Task Domain and Relationship Core - Enforcement.
-- This migration intentionally fails when V11 reported unresolved hierarchy conflicts.

do $$
declare
  unresolved_count bigint;
begin
  select count(*) into unresolved_count
    from task_domain_backfill_conflicts
   where resolved_at is null;
  if unresolved_count > 0 then
    raise exception 'PHASE0C_TASK_DOMAIN_BACKFILL_CONFLICTS: % unresolved conflict(s). Run the integrity report and repair workflow before V12.', unresolved_count;
  end if;

  if exists (
      select 1 from tasks
       where task_key is null or btrim(task_key) = ''
          or title is null or btrim(title) = ''
          or severity is null or btrim(severity) = ''
          or root_task_id is null
          or issue_sync_policy is null or btrim(issue_sync_policy) = ''
          or created_by_type is null or btrim(created_by_type) = ''
          or created_by_id is null or btrim(created_by_id) = ''
          or version is null or version < 1
  ) then
    raise exception 'PHASE0C_TASK_AGGREGATE_REQUIRED_FIELDS_UNRESOLVED';
  end if;
end $$;

alter table tasks alter column task_key set not null;
alter table tasks alter column title set not null;
alter table tasks alter column severity set not null;
alter table tasks alter column root_task_id set not null;
alter table tasks alter column issue_sync_policy set not null;
alter table tasks alter column created_by_type set not null;
alter table tasks alter column created_by_id set not null;
alter table tasks alter column version set not null;
alter table tasks alter column version set default 1;

-- Tenant-aware Task authority keys.
do $$
begin
  if not exists (select 1 from pg_constraint where conname = 'uq_tasks_tenant_task_key') then
    alter table tasks add constraint uq_tasks_tenant_task_key unique (tenant_id, task_key);
  end if;
  if not exists (select 1 from pg_constraint where conname = 'uq_task_relationship_natural_key') then
    alter table task_relationships add constraint uq_task_relationship_natural_key
      unique (tenant_id, from_task_id, to_task_id, relationship_type);
  end if;
  if not exists (select 1 from pg_constraint where conname = 'uq_task_participant_natural_key') then
    alter table task_participants add constraint uq_task_participant_natural_key
      unique (tenant_id, task_id, participant_type, participant_ref_id, participant_role);
  end if;
end $$;

-- The legacy open-Task uniqueness key was global. Replace it with a Tenant-aware
-- authority key before Current task creation starts using the new conflict target.
drop index if exists uq_tasks_open_incident_type;
create unique index if not exists uq_tasks_open_tenant_incident_type
  on tasks(tenant_id, incident_id, task_type)
  where status not in ('SUCCEEDED','FAILED','ESCALATED','DEAD_LETTER','SUPPRESSED','COMPLETED','TIMED_OUT','EXPIRED','CANCELLED');

create unique index if not exists uq_tasks_creation_idempotency
  on tasks(tenant_id, creation_idempotency_key)
  where creation_idempotency_key is not null;
create unique index if not exists uq_task_relationship_idempotency
  on task_relationships(tenant_id, idempotency_key)
  where idempotency_key is not null;
create unique index if not exists uq_task_state_history_idempotency
  on task_state_history(tenant_id, idempotency_key)
  where idempotency_key is not null;

-- Tenant-aware references. V10 already supplies tasks(tenant_id, task_id) as a
-- referenced unique authority key.
do $$
begin
  if not exists (select 1 from pg_constraint where conname = 'fk_tasks_root_task') then
    alter table tasks add constraint fk_tasks_root_task
      foreign key (tenant_id, root_task_id) references tasks(tenant_id, task_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_tasks_parent_task') then
    alter table tasks add constraint fk_tasks_parent_task
      foreign key (tenant_id, parent_task_id) references tasks(tenant_id, task_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_tasks_source_task') then
    alter table tasks add constraint fk_tasks_source_task
      foreign key (tenant_id, source_task_id) references tasks(tenant_id, task_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_tasks_requesting_task') then
    alter table tasks add constraint fk_tasks_requesting_task
      foreign key (tenant_id, requesting_task_id) references tasks(tenant_id, task_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_task_relationships_tenant') then
    alter table task_relationships add constraint fk_task_relationships_tenant
      foreign key (tenant_id) references tenants(tenant_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_task_relationships_from') then
    alter table task_relationships add constraint fk_task_relationships_from
      foreign key (tenant_id, from_task_id) references tasks(tenant_id, task_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_task_relationships_to') then
    alter table task_relationships add constraint fk_task_relationships_to
      foreign key (tenant_id, to_task_id) references tasks(tenant_id, task_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_task_participants_tenant') then
    alter table task_participants add constraint fk_task_participants_tenant
      foreign key (tenant_id) references tenants(tenant_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_task_participants_task') then
    alter table task_participants add constraint fk_task_participants_task
      foreign key (tenant_id, task_id) references tasks(tenant_id, task_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_task_state_history_tenant') then
    alter table task_state_history add constraint fk_task_state_history_tenant
      foreign key (tenant_id) references tenants(tenant_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_task_state_history_task') then
    alter table task_state_history add constraint fk_task_state_history_task
      foreign key (tenant_id, task_id) references tasks(tenant_id, task_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_task_mutation_idempotency_tenant') then
    alter table task_mutation_idempotency add constraint fk_task_mutation_idempotency_tenant
      foreign key (tenant_id) references tenants(tenant_id) not valid;
  end if;
end $$;

-- Value domains. Legacy lifecycle states remain accepted while Current callers move
-- through the formal Phase 0C transition service.
do $$
begin
  if not exists (select 1 from pg_constraint where conname = 'ck_tasks_severity') then
    alter table tasks add constraint ck_tasks_severity
      check (severity in ('LOW','MEDIUM','HIGH','CRITICAL'));
  end if;
  if not exists (select 1 from pg_constraint where conname = 'ck_tasks_issue_sync_policy') then
    alter table tasks add constraint ck_tasks_issue_sync_policy
      check (issue_sync_policy in ('NONE','OPTIONAL','REQUIRED','MANUAL'));
  end if;
  if not exists (select 1 from pg_constraint where conname = 'ck_tasks_created_by_type') then
    alter table tasks add constraint ck_tasks_created_by_type
      check (created_by_type in ('SYSTEM','AGENT','USER','POLICY','INTEGRATION','MIGRATION'));
  end if;
  if not exists (select 1 from pg_constraint where conname = 'ck_tasks_version_positive') then
    alter table tasks add constraint ck_tasks_version_positive check (version >= 1);
  end if;
  if not exists (select 1 from pg_constraint where conname = 'ck_task_relationship_distinct_tasks') then
    alter table task_relationships add constraint ck_task_relationship_distinct_tasks check (from_task_id <> to_task_id);
  end if;
  if not exists (select 1 from pg_constraint where conname = 'ck_task_relationship_type') then
    alter table task_relationships add constraint ck_task_relationship_type check (relationship_type in (
      'REFERENCES','RELATED_TO','DEPENDS_ON','BLOCKS','DUPLICATES','CAUSED_BY','RESULT_OF',
      'SUPERSEDES','RESOLVES','CONTRIBUTES_TO','DERIVED_FROM','VALIDATES'
    ));
  end if;
  if not exists (select 1 from pg_constraint where conname = 'ck_task_relationship_created_by_type') then
    alter table task_relationships add constraint ck_task_relationship_created_by_type check (created_by_type in ('SYSTEM','AGENT','USER','POLICY','INTEGRATION','MIGRATION'));
  end if;
  if not exists (select 1 from pg_constraint where conname = 'ck_task_relationship_direction') then
    alter table task_relationships add constraint ck_task_relationship_direction check (direction in ('DIRECTED','BIDIRECTIONAL'));
  end if;
  if not exists (select 1 from pg_constraint where conname = 'ck_task_reference_visibility') then
    alter table task_relationships add constraint ck_task_reference_visibility check (reference_visibility in ('VISIBLE','OPAQUE'));
  end if;
  if not exists (select 1 from pg_constraint where conname = 'ck_task_participant_type') then
    alter table task_participants add constraint ck_task_participant_type check (participant_type in ('DEPARTMENT','GROUP','SERVICE_DOMAIN','USER','AGENT'));
  end if;
  if not exists (select 1 from pg_constraint where conname = 'ck_task_participant_role') then
    alter table task_participants add constraint ck_task_participant_role check (participant_role in ('OWNER','REQUESTER','EXECUTOR','OBSERVER','APPROVER','SUPPORTER','AUDITOR'));
  end if;
  if not exists (select 1 from pg_constraint where conname = 'ck_task_participant_visibility') then
    alter table task_participants add constraint ck_task_participant_visibility check (visibility_level in ('SUMMARY','STANDARD','FULL'));
  end if;
  if not exists (select 1 from pg_constraint where conname = 'ck_task_participant_operation') then
    alter table task_participants add constraint ck_task_participant_operation check (operation_level in ('READ_ONLY','COMMENT','OPERATE','APPROVE','ADMIN'));
  end if;
  if not exists (select 1 from pg_constraint where conname = 'ck_task_participant_version_positive') then
    alter table task_participants add constraint ck_task_participant_version_positive check (version >= 1);
  end if;
end $$;

-- Normalize Root/Parent fields and reject hierarchy cycles at the database boundary.
create or replace function phase0c_enforce_task_hierarchy()
returns trigger as $$
declare
  parent_root varchar(128);
  cycle_found boolean;
begin
  if new.parent_task_id = new.task_id then
    raise exception 'TASK_HIERARCHY_SELF_REFERENCE taskId=%', new.task_id;
  end if;

  if new.parent_task_id is null then
    new.root_task_id := new.task_id;
  else
    select root_task_id into parent_root
      from tasks
     where tenant_id = new.tenant_id
       and task_id = new.parent_task_id;
    if parent_root is null then
      raise exception 'TASK_PARENT_NOT_FOUND tenantId=% taskId=% parentTaskId=%', new.tenant_id, new.task_id, new.parent_task_id;
    end if;
    new.root_task_id := parent_root;

    with recursive ancestors as (
      select task_id, parent_task_id, array[task_id]::varchar[] as path
        from tasks
       where tenant_id = new.tenant_id and task_id = new.parent_task_id
      union all
      select parent.task_id, parent.parent_task_id, ancestors.path || parent.task_id
        from ancestors
        join tasks parent
          on parent.tenant_id = new.tenant_id
         and parent.task_id = ancestors.parent_task_id
       where ancestors.parent_task_id is not null
         and not parent.task_id = any(ancestors.path)
    )
    select exists(select 1 from ancestors where task_id = new.task_id) into cycle_found;
    if cycle_found then
      raise exception 'TASK_HIERARCHY_CYCLE_DETECTED tenantId=% taskId=% parentTaskId=%', new.tenant_id, new.task_id, new.parent_task_id;
    end if;
  end if;
  return new;
end;
$$ language plpgsql;

drop trigger if exists trg_phase0c_enforce_task_hierarchy on tasks;
create trigger trg_phase0c_enforce_task_hierarchy
before insert or update of tenant_id, task_id, parent_task_id, root_task_id on tasks
for each row execute function phase0c_enforce_task_hierarchy();

-- Task version is immutable from a stale caller. Compatibility upserts that submit the
-- current value are advanced by the trigger; explicit optimistic writes still use
-- WHERE version = expectedVersion in TaskDao.transitionGovernanceState.
create or replace function phase0c_task_version_touch()
returns trigger as $$
begin
  new.updated_at := case when new.updated_at is distinct from old.updated_at then new.updated_at else now() end;
  if new.version is null or new.version <= old.version then
    new.version := old.version + 1;
  end if;
  return new;
end;
$$ language plpgsql;

drop trigger if exists trg_phase0c_task_version_touch on tasks;
create trigger trg_phase0c_task_version_touch
before update on tasks
for each row execute function phase0c_task_version_touch();

-- Capture every status change, including legacy update paths that have not yet migrated
-- to the Phase 0C state transition API.
create or replace function phase0c_record_task_state_history()
returns trigger as $$
declare
  event_time timestamptz := coalesce(new.updated_at, now());
  explicit_transition boolean := false;
  previous_status varchar(32) := null;
begin
  if tg_op = 'UPDATE' then
    previous_status := old.status;
    explicit_transition :=
      new.last_transition_idempotency_key is distinct from old.last_transition_idempotency_key
      or new.last_transition_reason_code is distinct from old.last_transition_reason_code
      or new.last_transition_actor_type is distinct from old.last_transition_actor_type
      or new.last_transition_actor_id is distinct from old.last_transition_actor_id;
  end if;

  if tg_op = 'INSERT' or previous_status is distinct from new.status then
    insert into task_state_history (
      tenant_id, history_id, task_id, from_status, to_status, reason_code, reason,
      actor_type, actor_id, correlation_id, transition_at, task_version, idempotency_key
    ) values (
      new.tenant_id,
      concat('tsh-', md5(concat(new.tenant_id, ':', new.task_id, ':', event_time::text, ':', random()::text))),
      new.task_id,
      previous_status,
      new.status,
      case when explicit_transition then coalesce(new.last_transition_reason_code, 'TASK_STATUS_CHANGED')
           when tg_op = 'INSERT' then 'TASK_CREATED' else 'TASK_STATUS_CHANGED' end,
      coalesce(new.lifecycle_reason, new.created_reason),
      case when explicit_transition then coalesce(new.last_transition_actor_type, 'SYSTEM') else 'SYSTEM' end,
      case when explicit_transition then coalesce(new.last_transition_actor_id, 'DB_TRIGGER') else 'DB_TRIGGER' end,
      new.correlation_id,
      event_time,
      new.version,
      case when explicit_transition then new.last_transition_idempotency_key else null end
    );
  end if;
  return new;
end;
$$ language plpgsql;

drop trigger if exists trg_phase0c_record_task_state_history on tasks;
create trigger trg_phase0c_record_task_state_history
after insert or update of status on tasks
for each row execute function phase0c_record_task_state_history();

-- Keep descendant root_task_id values aligned when a Task is re-parented.
create or replace function phase0c_propagate_descendant_root()
returns trigger as $$
begin
  if new.root_task_id is distinct from old.root_task_id then
    with recursive descendants as (
      select task_id
        from tasks
       where tenant_id = new.tenant_id and parent_task_id = new.task_id
      union all
      select child.task_id
        from descendants
        join tasks child
          on child.tenant_id = new.tenant_id
         and child.parent_task_id = descendants.task_id
    )
    update tasks
       set root_task_id = new.root_task_id
     where tenant_id = new.tenant_id
       and task_id in (select task_id from descendants)
       and root_task_id is distinct from new.root_task_id;
  end if;
  return new;
end;
$$ language plpgsql;

drop trigger if exists trg_phase0c_propagate_descendant_root on tasks;
create trigger trg_phase0c_propagate_descendant_root
after update of parent_task_id, root_task_id on tasks
for each row execute function phase0c_propagate_descendant_root();

-- Validate polymorphic organizational participant references inside the same Tenant.
create or replace function phase0c_validate_task_participant_scope()
returns trigger as $$
declare
  valid_reference boolean := true;
begin
  case new.participant_type
    when 'DEPARTMENT' then
      select exists(select 1 from departments where tenant_id = new.tenant_id and department_id = new.participant_ref_id) into valid_reference;
    when 'GROUP' then
      select exists(select 1 from organization_groups where tenant_id = new.tenant_id and group_id = new.participant_ref_id) into valid_reference;
    when 'SERVICE_DOMAIN' then
      select exists(select 1 from service_domains where tenant_id = new.tenant_id and service_domain_id = new.participant_ref_id) into valid_reference;
    when 'AGENT' then
      select exists(select 1 from agent_profiles where tenant_id = new.tenant_id and agent_id = new.participant_ref_id) into valid_reference;
    when 'USER' then
      valid_reference := true; -- IAM authority is introduced in Phase 1.
    else
      valid_reference := false;
  end case;
  if not valid_reference then
    raise exception 'TASK_PARTICIPANT_SCOPE_NOT_FOUND tenantId=% participantType=% participantRefId=%',
      new.tenant_id, new.participant_type, new.participant_ref_id;
  end if;
  return new;
end;
$$ language plpgsql;

drop trigger if exists trg_phase0c_validate_task_participant_scope on task_participants;
create trigger trg_phase0c_validate_task_participant_scope
before insert or update of tenant_id, participant_type, participant_ref_id on task_participants
for each row execute function phase0c_validate_task_participant_scope();

-- State history is append-only. Corrections are represented by a new audit entry.
create or replace function phase0c_reject_state_history_mutation()
returns trigger as $$
begin
  raise exception 'TASK_STATE_HISTORY_IMMUTABLE tenantId=% historyId=%', old.tenant_id, old.history_id;
end;
$$ language plpgsql;

drop trigger if exists trg_phase0c_reject_state_history_mutation on task_state_history;
create trigger trg_phase0c_reject_state_history_mutation
before update or delete on task_state_history
for each row execute function phase0c_reject_state_history_mutation();

alter table tasks validate constraint fk_tasks_root_task;
alter table tasks validate constraint fk_tasks_parent_task;
alter table tasks validate constraint fk_tasks_source_task;
alter table tasks validate constraint fk_tasks_requesting_task;
alter table task_relationships validate constraint fk_task_relationships_tenant;
alter table task_relationships validate constraint fk_task_relationships_from;
alter table task_relationships validate constraint fk_task_relationships_to;
alter table task_participants validate constraint fk_task_participants_tenant;
alter table task_participants validate constraint fk_task_participants_task;
alter table task_state_history validate constraint fk_task_state_history_tenant;
alter table task_state_history validate constraint fk_task_state_history_task;
alter table task_mutation_idempotency validate constraint fk_task_mutation_idempotency_tenant;
