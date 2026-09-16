-- Phase 0B Enforce: tenant-aware core keys, organization ownership, and
-- cross-tenant rejection.
--
-- V8 expanded the schema and V9 performed deterministic backfill. This
-- migration intentionally fails with an actionable message when unresolved
-- records remain. Never assign an arbitrary tenant to make this migration pass.

do $$
declare
  violation text;
begin
  select table_name || ' has ' || row_count || ' row(s) without tenant_id'
    into violation
  from (
    select 'agents'::text as table_name, count(*)::bigint as row_count from agents where tenant_id is null
    union all select 'agent_credentials', count(*) from agent_credentials where tenant_id is null
    union all select 'agent_capabilities', count(*) from agent_capabilities where tenant_id is null
    union all select 'agent_runtime_capability_profiles', count(*) from agent_runtime_capability_profiles where tenant_id is null
    union all select 'agent_runtime_capability_items', count(*) from agent_runtime_capability_items where tenant_id is null
    union all select 'agent_runtime_load_snapshots', count(*) from agent_runtime_load_snapshots where tenant_id is null
    union all select 'agent_runtime_descriptors', count(*) from agent_runtime_descriptors where tenant_id is null
    union all select 'tasks', count(*) from tasks where tenant_id is null
    union all select 'task_assignments', count(*) from task_assignments where tenant_id is null
    union all select 'dispatch_requests', count(*) from dispatch_requests where tenant_id is null
    union all select 'routing_decisions', count(*) from routing_decisions where tenant_id is null
    union all select 'task_dispatch_attempts', count(*) from task_dispatch_attempts where tenant_id is null
    union all select 'task_execution_attempts', count(*) from task_execution_attempts where tenant_id is null
    union all select 'task_callbacks', count(*) from task_callbacks where tenant_id is null
    union all select 'dispatch_attempt_history', count(*) from dispatch_attempt_history where tenant_id is null
    union all select 'task_issue_links', count(*) from task_issue_links where tenant_id is null
  ) unresolved
  where row_count > 0
  order by table_name
  limit 1;

  if violation is not null then
    raise exception
      'Phase 0B tenant enforcement blocked: %. Review tenant_backfill_conflicts and run scripts/db/phase0b-tenant-backfill-repair.sql.',
      violation;
  end if;

  if exists (
    select 1
      from agent_credentials c
      join agent_profiles p on p.agent_id = c.agent_id
     where c.tenant_id <> p.tenant_id
  ) then
    raise exception 'Phase 0B tenant enforcement blocked: Agent Credential tenant differs from Agent Profile tenant.';
  end if;

  if exists (
    select 1
      from task_assignments a
      join tasks t on t.task_id = a.task_id
     where a.tenant_id <> t.tenant_id
  ) then
    raise exception 'Phase 0B tenant enforcement blocked: Assignment tenant differs from Task tenant.';
  end if;

  if exists (
    select 1
      from dispatch_requests r
      join tasks t on t.task_id = r.task_id
     where r.tenant_id <> t.tenant_id
  ) then
    raise exception 'Phase 0B tenant enforcement blocked: Dispatch Request tenant differs from Task tenant.';
  end if;

  if exists (
    select 1
      from task_issue_links l
      join tasks t on t.task_id = l.task_id
     where l.tenant_id <> t.tenant_id
  ) then
    raise exception 'Phase 0B tenant enforcement blocked: Task-Issue Link tenant differs from Task tenant.';
  end if;

  if exists (
    select 1 from agent_capabilities c
    left join agent_profiles p
      on p.tenant_id = c.tenant_id and p.agent_id = c.agent_id
    where p.agent_id is null
  ) then
    raise exception 'Phase 0B tenant enforcement blocked: Agent Capability has no same-tenant Agent Profile.';
  end if;

  if exists (
    select 1 from agent_runtime_capability_profiles r
    left join agents a
      on a.tenant_id = r.tenant_id and a.agent_id = r.agent_id
    where a.agent_id is null
  ) or exists (
    select 1 from agent_runtime_capability_items r
    left join agents a
      on a.tenant_id = r.tenant_id and a.agent_id = r.agent_id
    where a.agent_id is null
  ) or exists (
    select 1 from agent_runtime_load_snapshots r
    left join agents a
      on a.tenant_id = r.tenant_id and a.agent_id = r.agent_id
    where a.agent_id is null
  ) or exists (
    select 1 from agent_runtime_descriptors r
    left join agents a
      on a.tenant_id = r.tenant_id and a.agent_id = r.agent_id
    where a.agent_id is null
  ) then
    raise exception 'Phase 0B tenant enforcement blocked: Agent runtime projection has no same-tenant Agent authority row.';
  end if;

  if exists (
    select 1 from agent_runtime_bindings b
    left join agent_profiles p
      on p.tenant_id = b.tenant_id and p.agent_id = b.agent_id
    where p.agent_id is null
  ) then
    raise exception 'Phase 0B tenant enforcement blocked: Runtime Binding has no same-tenant Agent Profile.';
  end if;

  if exists (
    select 1 from agent_runtime_bindings b
    left join runtime_resources r
      on r.tenant_id = b.tenant_id and r.runtime_id = b.runtime_id
    where b.runtime_id is not null and r.runtime_id is null
  ) then
    raise exception 'Phase 0B tenant enforcement blocked: Runtime Binding has no same-tenant Runtime Resource.';
  end if;

  if exists (
    select 1 from task_assignments a
    left join agent_profiles p
      on p.tenant_id = a.tenant_id and p.agent_id = a.agent_id
    where p.agent_id is null
  ) then
    raise exception 'Phase 0B tenant enforcement blocked: Assignment has no same-tenant Agent Profile.';
  end if;

  if exists (
    select 1 from dispatch_requests r
    left join agent_profiles p
      on p.tenant_id = r.tenant_id and p.agent_id = r.agent_id
    where r.agent_id is not null and p.agent_id is null
  ) then
    raise exception 'Phase 0B tenant enforcement blocked: Dispatch Request has no same-tenant Agent Profile.';
  end if;

  if exists (
    select 1 from routing_decisions d
    left join tasks t on t.tenant_id = d.tenant_id and t.task_id = d.task_id
    where d.task_id is not null and t.task_id is null
  ) or exists (
    select 1 from task_dispatch_attempts e
    left join tasks t on t.tenant_id = e.tenant_id and t.task_id = e.task_id
    where e.task_id is not null and t.task_id is null
  ) or exists (
    select 1 from task_execution_attempts e
    left join tasks t on t.tenant_id = e.tenant_id and t.task_id = e.task_id
    where e.task_id is not null and t.task_id is null
  ) or exists (
    select 1 from task_callbacks e
    left join tasks t on t.tenant_id = e.tenant_id and t.task_id = e.task_id
    where e.task_id is not null and t.task_id is null
  ) or exists (
    select 1 from dispatch_attempt_history e
    left join tasks t on t.tenant_id = e.tenant_id and t.task_id = e.task_id
    where e.task_id is not null and t.task_id is null
  ) then
    raise exception 'Phase 0B tenant enforcement blocked: Task lifecycle evidence references a missing same-tenant Task.';
  end if;
end $$;

-- Legacy callers can omit tenant_id only when an existing tenant-scoped
-- authority row resolves it unambiguously.
create or replace function phase0b_set_agent_authority_tenant()
returns trigger as $$
declare
  resolved_tenant varchar(64);
begin
  if tg_op = 'UPDATE'
     and old.tenant_id is not null
     and new.tenant_id is distinct from old.tenant_id then
    raise exception 'TENANT_REASSIGNMENT_DENIED: Agent % cannot move from tenant % to tenant %.',
      new.agent_id, old.tenant_id, new.tenant_id;
  end if;

  select p.tenant_id
    into resolved_tenant
    from agent_profiles p
   where p.agent_id = new.agent_id;

  if new.tenant_id is not null
     and resolved_tenant is not null
     and new.tenant_id <> resolved_tenant then
    raise exception 'TENANT_MISMATCH: Agent % tenant % differs from Agent Profile tenant %.',
      new.agent_id, new.tenant_id, resolved_tenant;
  end if;

  if new.tenant_id is null then

    if resolved_tenant is null then
      select e.tenant_id
        into resolved_tenant
        from agent_enrollment_requests e
       where e.claimed_agent_id = new.agent_id
       order by e.updated_at desc nulls last, e.created_at desc
       limit 1;
    end if;

    if resolved_tenant is null then
      raise exception 'TENANT_CONTEXT_REQUIRED: Agent % has no tenant-scoped profile or enrollment.', new.agent_id;
    end if;
    new.tenant_id := resolved_tenant;
  end if;
  return new;
end;
$$ language plpgsql;

drop trigger if exists trg_agents_phase0b_tenant on agents;
create trigger trg_agents_phase0b_tenant
before insert or update on agents
for each row execute function phase0b_set_agent_authority_tenant();

create or replace function phase0b_set_agent_projection_tenant()
returns trigger as $$
declare
  resolved_tenant varchar(64);
begin
  select p.tenant_id
    into resolved_tenant
    from agent_profiles p
   where p.agent_id = new.agent_id;

  if resolved_tenant is null then
    select a.tenant_id
      into resolved_tenant
      from agents a
     where a.agent_id = new.agent_id;
  end if;

  if new.tenant_id is not null
     and resolved_tenant is not null
     and new.tenant_id <> resolved_tenant then
    raise exception 'TENANT_MISMATCH: Agent projection for % uses tenant %, authority tenant is %.',
      new.agent_id, new.tenant_id, resolved_tenant;
  end if;

  if new.tenant_id is null then
    if resolved_tenant is null then
      raise exception 'TENANT_CONTEXT_REQUIRED: Agent projection for % cannot resolve a tenant.', new.agent_id;
    end if;
    new.tenant_id := resolved_tenant;
  end if;
  return new;
end;
$$ language plpgsql;

drop trigger if exists trg_agent_credentials_phase0b_tenant on agent_credentials;
create trigger trg_agent_credentials_phase0b_tenant
before insert or update on agent_credentials
for each row execute function phase0b_set_agent_projection_tenant();

drop trigger if exists trg_agent_capabilities_phase0b_tenant on agent_capabilities;
create trigger trg_agent_capabilities_phase0b_tenant
before insert or update on agent_capabilities
for each row execute function phase0b_set_agent_projection_tenant();

drop trigger if exists trg_runtime_capability_profiles_phase0b_tenant on agent_runtime_capability_profiles;
create trigger trg_runtime_capability_profiles_phase0b_tenant
before insert or update on agent_runtime_capability_profiles
for each row execute function phase0b_set_agent_projection_tenant();

drop trigger if exists trg_runtime_capability_items_phase0b_tenant on agent_runtime_capability_items;
create trigger trg_runtime_capability_items_phase0b_tenant
before insert or update on agent_runtime_capability_items
for each row execute function phase0b_set_agent_projection_tenant();

drop trigger if exists trg_runtime_load_snapshots_phase0b_tenant on agent_runtime_load_snapshots;
create trigger trg_runtime_load_snapshots_phase0b_tenant
before insert or update on agent_runtime_load_snapshots
for each row execute function phase0b_set_agent_projection_tenant();

drop trigger if exists trg_runtime_descriptors_phase0b_tenant on agent_runtime_descriptors;
create trigger trg_runtime_descriptors_phase0b_tenant
before insert or update on agent_runtime_descriptors
for each row execute function phase0b_set_agent_projection_tenant();

create or replace function phase0b_set_task_evidence_tenant()
returns trigger as $$
declare
  row_json jsonb;
  resolved_tenant varchar(64);
  referenced_task_id varchar(128);
  referenced_assignment_id varchar(128);
  referenced_dispatch_request_id varchar(128);
begin
  row_json := to_jsonb(new);
  referenced_task_id := nullif(row_json ->> 'task_id', '');
  referenced_assignment_id := nullif(row_json ->> 'assignment_id', '');
  referenced_dispatch_request_id := nullif(row_json ->> 'dispatch_request_id', '');

  if referenced_task_id is not null then
    select t.tenant_id into resolved_tenant
      from tasks t where t.task_id = referenced_task_id;
  end if;

  if resolved_tenant is null and referenced_assignment_id is not null then
    select a.tenant_id into resolved_tenant
      from task_assignments a where a.assignment_id = referenced_assignment_id;
  end if;

  if resolved_tenant is null and referenced_dispatch_request_id is not null then
    select r.tenant_id into resolved_tenant
      from dispatch_requests r where r.dispatch_request_id = referenced_dispatch_request_id;
  end if;

  if resolved_tenant is null then
    raise exception 'TENANT_CONTEXT_REQUIRED: % row cannot resolve Task lifecycle tenant.', tg_table_name;
  end if;

  if new.tenant_id is not null and new.tenant_id <> resolved_tenant then
    raise exception 'TENANT_MISMATCH: % row tenant % differs from lifecycle tenant %.',
      tg_table_name, new.tenant_id, resolved_tenant;
  end if;

  new.tenant_id := resolved_tenant;
  return new;
end;
$$ language plpgsql;

drop trigger if exists trg_task_dispatch_attempts_phase0b_tenant on task_dispatch_attempts;
create trigger trg_task_dispatch_attempts_phase0b_tenant
before insert or update on task_dispatch_attempts
for each row execute function phase0b_set_task_evidence_tenant();

drop trigger if exists trg_task_execution_attempts_phase0b_tenant on task_execution_attempts;
create trigger trg_task_execution_attempts_phase0b_tenant
before insert or update on task_execution_attempts
for each row execute function phase0b_set_task_evidence_tenant();

drop trigger if exists trg_task_callbacks_phase0b_tenant on task_callbacks;
create trigger trg_task_callbacks_phase0b_tenant
before insert or update on task_callbacks
for each row execute function phase0b_set_task_evidence_tenant();

drop trigger if exists trg_dispatch_attempt_history_phase0b_tenant on dispatch_attempt_history;
create trigger trg_dispatch_attempt_history_phase0b_tenant
before insert or update on dispatch_attempt_history
for each row execute function phase0b_set_task_evidence_tenant();

drop trigger if exists trg_task_issue_links_phase0b_tenant on task_issue_links;
create trigger trg_task_issue_links_phase0b_tenant
before insert or update on task_issue_links
for each row execute function phase0b_set_task_evidence_tenant();

-- Once authority data has a tenant, an update cannot move it to another
-- tenant. Global identifiers remain only as transition keys and never grant
-- cross-tenant reassignment.
create or replace function phase0b_reject_tenant_reassignment()
returns trigger as $$
begin
  if old.tenant_id is not null and new.tenant_id is distinct from old.tenant_id then
    raise exception 'TENANT_REASSIGNMENT_DENIED: %.% cannot move from tenant % to tenant %.',
      tg_table_name,
      coalesce(to_jsonb(new) ->> 'task_id',
               to_jsonb(new) ->> 'assignment_id',
               to_jsonb(new) ->> 'dispatch_request_id',
               to_jsonb(new) ->> 'agent_id',
               to_jsonb(new) ->> 'source_system_id',
               to_jsonb(new) ->> 'pool_id',
               to_jsonb(new) ->> 'runtime_id',
               to_jsonb(new) ->> 'flow_id',
               to_jsonb(new) ->> 'policy_id',
               to_jsonb(new) ->> 'link_id',
               to_jsonb(new) ->> 'attempt_id',
               to_jsonb(new) ->> 'callback_id',
               '<unknown>'),
      old.tenant_id,
      new.tenant_id;
  end if;
  return new;
end;
$$ language plpgsql;

do $$
declare
  target_table text;
  trigger_name text;
begin
  foreach target_table in array array[
    'source_systems', 'agent_enrollment_requests', 'agent_profiles',
    'agent_pools', 'agent_pool_members', 'runtime_resources',
    'agent_runtime_bindings', 'dispatch_flows', 'dispatch_policies',
    'tasks', 'task_assignments', 'dispatch_requests', 'routing_decisions',
    'task_dispatch_attempts', 'task_execution_attempts', 'task_callbacks',
    'dispatch_attempt_history', 'task_issue_links'
  ] loop
    trigger_name := 'trg_' || target_table || '_phase0b_tenant_immutable';
    execute format('drop trigger if exists %I on %I', trigger_name, target_table);
    execute format(
      'create trigger %I before update of tenant_id on %I for each row execute function phase0b_reject_tenant_reassignment()',
      trigger_name, target_table
    );
  end loop;
end $$;

-- Tenant and ownership columns are now mandatory for Current authority rows.
alter table agents alter column tenant_id set not null;
alter table agent_credentials alter column tenant_id set not null;
alter table agent_capabilities alter column tenant_id set not null;
alter table agent_runtime_capability_profiles alter column tenant_id set not null;
alter table agent_runtime_capability_items alter column tenant_id set not null;
alter table agent_runtime_load_snapshots alter column tenant_id set not null;
alter table agent_runtime_descriptors alter column tenant_id set not null;
alter table tasks alter column tenant_id set not null;
alter table dispatch_requests alter column tenant_id set not null;
alter table routing_decisions alter column tenant_id set not null;
alter table task_dispatch_attempts alter column tenant_id set not null;
alter table task_execution_attempts alter column tenant_id set not null;
alter table task_callbacks alter column tenant_id set not null;
alter table dispatch_attempt_history alter column tenant_id set not null;
alter table task_issue_links alter column tenant_id set not null;
alter table task_issue_links alter column task_id set not null;

alter table agent_profiles
  alter column owner_department_id set default 'UNASSIGNED',
  alter column owner_department_id set not null,
  alter column service_domain_id set default 'UNASSIGNED',
  alter column service_domain_id set not null,
  alter column trust_zone_id set default 'DEFAULT',
  alter column trust_zone_id set not null;

alter table tasks
  alter column owner_department_id set default 'UNASSIGNED',
  alter column owner_department_id set not null,
  alter column requester_department_id set default 'UNASSIGNED',
  alter column requester_department_id set not null,
  alter column requester_domain_id set default 'UNASSIGNED',
  alter column requester_domain_id set not null,
  alter column executor_department_id set default 'UNASSIGNED',
  alter column executor_department_id set not null,
  alter column executor_domain_id set default 'UNASSIGNED',
  alter column executor_domain_id set not null,
  alter column visibility_policy set default 'TENANT',
  alter column visibility_policy set not null,
  alter column sensitivity_level set default 'INTERNAL',
  alter column sensitivity_level set not null;

-- Composite keys are the tenant-scoped authority contract. Historical global
-- primary keys remain temporarily for compatibility and are scheduled for
-- removal only after every API and repository supplies tenant context.
do $$
begin
  if not exists (select 1 from pg_constraint where conname = 'uq_agents_tenant_agent') then
    alter table agents add constraint uq_agents_tenant_agent unique (tenant_id, agent_id);
  end if;
  if not exists (select 1 from pg_constraint where conname = 'uq_agent_credentials_tenant_credential') then
    alter table agent_credentials add constraint uq_agent_credentials_tenant_credential unique (tenant_id, credential_id);
  end if;
  if not exists (select 1 from pg_constraint where conname = 'uq_agent_capabilities_tenant_agent_capability') then
    alter table agent_capabilities add constraint uq_agent_capabilities_tenant_agent_capability
      unique (tenant_id, agent_id, capability_code);
  end if;
  if not exists (select 1 from pg_constraint where conname = 'uq_runtime_capability_profiles_tenant_agent') then
    alter table agent_runtime_capability_profiles add constraint uq_runtime_capability_profiles_tenant_agent
      unique (tenant_id, agent_id);
  end if;
  if not exists (select 1 from pg_constraint where conname = 'uq_runtime_capability_items_tenant_agent_item') then
    alter table agent_runtime_capability_items add constraint uq_runtime_capability_items_tenant_agent_item
      unique (tenant_id, agent_id, capability_kind, capability_value);
  end if;
  if not exists (select 1 from pg_constraint where conname = 'uq_runtime_load_snapshots_tenant_agent') then
    alter table agent_runtime_load_snapshots add constraint uq_runtime_load_snapshots_tenant_agent
      unique (tenant_id, agent_id);
  end if;
  if not exists (select 1 from pg_constraint where conname = 'uq_runtime_descriptors_tenant_agent') then
    alter table agent_runtime_descriptors add constraint uq_runtime_descriptors_tenant_agent
      unique (tenant_id, agent_id);
  end if;
  if not exists (select 1 from pg_constraint where conname = 'uq_routing_decisions_tenant_decision') then
    alter table routing_decisions add constraint uq_routing_decisions_tenant_decision
      unique (tenant_id, decision_id);
  end if;
  if not exists (select 1 from pg_constraint where conname = 'uq_task_dispatch_attempts_tenant_attempt') then
    alter table task_dispatch_attempts add constraint uq_task_dispatch_attempts_tenant_attempt
      unique (tenant_id, attempt_id);
  end if;
  if not exists (select 1 from pg_constraint where conname = 'uq_task_execution_attempts_tenant_attempt') then
    alter table task_execution_attempts add constraint uq_task_execution_attempts_tenant_attempt
      unique (tenant_id, attempt_id);
  end if;
  if not exists (select 1 from pg_constraint where conname = 'uq_task_callbacks_tenant_callback') then
    alter table task_callbacks add constraint uq_task_callbacks_tenant_callback
      unique (tenant_id, callback_id);
  end if;
  if not exists (select 1 from pg_constraint where conname = 'uq_dispatch_attempt_history_tenant_attempt') then
    alter table dispatch_attempt_history add constraint uq_dispatch_attempt_history_tenant_attempt
      unique (tenant_id, attempt_id);
  end if;
  if not exists (select 1 from pg_constraint where conname = 'uq_task_issue_links_tenant_link') then
    alter table task_issue_links add constraint uq_task_issue_links_tenant_link
      unique (tenant_id, link_id);
  end if;
end $$;

-- Every registered Tenant receives explicit placeholder ownership records. These
-- values make migrated ownership visible without inventing a real Department.
create or replace function phase0b_seed_tenant_organization_defaults()
returns trigger as $$
begin
  insert into departments (tenant_id, department_id, department_code, department_name, status)
  values (new.tenant_id, 'UNASSIGNED', 'UNASSIGNED', 'Unassigned Department', 'ACTIVE')
  on conflict (tenant_id, department_id) do nothing;

  insert into organization_groups (tenant_id, group_id, group_code, group_name, group_type, status)
  values (new.tenant_id, 'UNASSIGNED', 'UNASSIGNED', 'Unassigned Group', 'COLLABORATION', 'ACTIVE')
  on conflict (tenant_id, group_id) do nothing;

  insert into service_domains (tenant_id, service_domain_id, domain_code, domain_name, status)
  values (new.tenant_id, 'UNASSIGNED', 'UNASSIGNED', 'Unassigned Service Domain', 'ACTIVE')
  on conflict (tenant_id, service_domain_id) do nothing;

  insert into trust_zones (tenant_id, trust_zone_id, trust_zone_code, trust_zone_name, isolation_mode, status)
  values (new.tenant_id, 'DEFAULT', 'DEFAULT', 'Default Trust Zone', 'PER_TRUST_ZONE', 'ACTIVE')
  on conflict (tenant_id, trust_zone_id) do nothing;

  insert into department_group_bindings (tenant_id, department_id, group_id, binding_role, enabled)
  values (new.tenant_id, 'UNASSIGNED', 'UNASSIGNED', 'MEMBER', true)
  on conflict (tenant_id, department_id, group_id) do nothing;

  insert into department_service_domain_bindings (
    tenant_id, department_id, service_domain_id, ownership_role, is_primary, enabled
  ) values (new.tenant_id, 'UNASSIGNED', 'UNASSIGNED', 'OWNER', true, true)
  on conflict (tenant_id, department_id, service_domain_id) do nothing;

  insert into group_service_domain_bindings (
    tenant_id, group_id, service_domain_id, participation_role, enabled
  ) values (new.tenant_id, 'UNASSIGNED', 'UNASSIGNED', 'SUPPORTER', true)
  on conflict (tenant_id, group_id, service_domain_id) do nothing;

  insert into trust_zone_department_bindings (tenant_id, trust_zone_id, department_id, enabled)
  values (new.tenant_id, 'DEFAULT', 'UNASSIGNED', true)
  on conflict (tenant_id, trust_zone_id, department_id) do nothing;

  return new;
end;
$$ language plpgsql;

drop trigger if exists trg_tenants_phase0b_defaults on tenants;
create trigger trg_tenants_phase0b_defaults
after insert on tenants
for each row execute function phase0b_seed_tenant_organization_defaults();

-- Compatibility bridge for the current release: a tenant-scoped authority row
-- may register a previously configured tenant before the tenant management API
-- exists. Phase 1 IAM Foundation will replace this bridge with explicit tenant
-- lifecycle administration.
create or replace function phase0b_register_tenant_from_resource()
returns trigger as $$
begin
  if new.tenant_id is null or btrim(new.tenant_id) = '' then
    raise exception 'TENANT_CONTEXT_REQUIRED: % requires tenant_id.', tg_table_name;
  end if;

  insert into tenants (tenant_id, display_name, status, metadata_json)
  values (
    new.tenant_id,
    new.tenant_id,
    'ACTIVE',
    jsonb_build_object('registrationSource', 'PHASE_0B_COMPATIBILITY_BRIDGE')
  )
  on conflict (tenant_id) do nothing;
  return new;
end;
$$ language plpgsql;

do $$
declare
  target_table text;
  trigger_name text;
begin
  foreach target_table in array array[
    'source_systems', 'agent_enrollment_requests', 'agent_profiles',
    'agent_pools', 'runtime_resources', 'dispatch_flows',
    'dispatch_policies', 'tasks'
  ] loop
    trigger_name := 'trg_' || target_table || '_phase0b_tenant_registry';
    execute format('drop trigger if exists %I on %I', trigger_name, target_table);
    execute format(
      'create trigger %I before insert or update on %I for each row execute function phase0b_register_tenant_from_resource()',
      trigger_name, target_table
    );
  end loop;
end $$;

-- Tenant registry and organization foreign keys.
do $$
begin
  if not exists (select 1 from pg_constraint where conname = 'fk_source_systems_tenant') then
    alter table source_systems add constraint fk_source_systems_tenant
      foreign key (tenant_id) references tenants(tenant_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_agent_enrollments_tenant') then
    alter table agent_enrollment_requests add constraint fk_agent_enrollments_tenant
      foreign key (tenant_id) references tenants(tenant_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_agent_profiles_tenant') then
    alter table agent_profiles add constraint fk_agent_profiles_tenant
      foreign key (tenant_id) references tenants(tenant_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_agents_tenant') then
    alter table agents add constraint fk_agents_tenant
      foreign key (tenant_id) references tenants(tenant_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_agent_pools_tenant') then
    alter table agent_pools add constraint fk_agent_pools_tenant
      foreign key (tenant_id) references tenants(tenant_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_runtime_resources_tenant') then
    alter table runtime_resources add constraint fk_runtime_resources_tenant
      foreign key (tenant_id) references tenants(tenant_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_dispatch_flows_tenant') then
    alter table dispatch_flows add constraint fk_dispatch_flows_tenant
      foreign key (tenant_id) references tenants(tenant_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_dispatch_policies_tenant') then
    alter table dispatch_policies add constraint fk_dispatch_policies_tenant
      foreign key (tenant_id) references tenants(tenant_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_tasks_tenant') then
    alter table tasks add constraint fk_tasks_tenant
      foreign key (tenant_id) references tenants(tenant_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_task_assignments_tenant') then
    alter table task_assignments add constraint fk_task_assignments_tenant
      foreign key (tenant_id) references tenants(tenant_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_dispatch_requests_tenant') then
    alter table dispatch_requests add constraint fk_dispatch_requests_tenant
      foreign key (tenant_id) references tenants(tenant_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_agent_pool_members_tenant') then
    alter table agent_pool_members add constraint fk_agent_pool_members_tenant
      foreign key (tenant_id) references tenants(tenant_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_agent_runtime_bindings_tenant') then
    alter table agent_runtime_bindings add constraint fk_agent_runtime_bindings_tenant
      foreign key (tenant_id) references tenants(tenant_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_agent_credentials_tenant') then
    alter table agent_credentials add constraint fk_agent_credentials_tenant
      foreign key (tenant_id) references tenants(tenant_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_agent_capabilities_tenant') then
    alter table agent_capabilities add constraint fk_agent_capabilities_tenant
      foreign key (tenant_id) references tenants(tenant_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_runtime_capability_profiles_tenant') then
    alter table agent_runtime_capability_profiles add constraint fk_runtime_capability_profiles_tenant
      foreign key (tenant_id) references tenants(tenant_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_runtime_capability_items_tenant') then
    alter table agent_runtime_capability_items add constraint fk_runtime_capability_items_tenant
      foreign key (tenant_id) references tenants(tenant_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_runtime_load_snapshots_tenant') then
    alter table agent_runtime_load_snapshots add constraint fk_runtime_load_snapshots_tenant
      foreign key (tenant_id) references tenants(tenant_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_runtime_descriptors_tenant') then
    alter table agent_runtime_descriptors add constraint fk_runtime_descriptors_tenant
      foreign key (tenant_id) references tenants(tenant_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_routing_decisions_tenant') then
    alter table routing_decisions add constraint fk_routing_decisions_tenant
      foreign key (tenant_id) references tenants(tenant_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_task_dispatch_attempts_tenant') then
    alter table task_dispatch_attempts add constraint fk_task_dispatch_attempts_tenant
      foreign key (tenant_id) references tenants(tenant_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_task_execution_attempts_tenant') then
    alter table task_execution_attempts add constraint fk_task_execution_attempts_tenant
      foreign key (tenant_id) references tenants(tenant_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_task_callbacks_tenant') then
    alter table task_callbacks add constraint fk_task_callbacks_tenant
      foreign key (tenant_id) references tenants(tenant_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_dispatch_attempt_history_tenant') then
    alter table dispatch_attempt_history add constraint fk_dispatch_attempt_history_tenant
      foreign key (tenant_id) references tenants(tenant_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_task_issue_links_tenant') then
    alter table task_issue_links add constraint fk_task_issue_links_tenant
      foreign key (tenant_id) references tenants(tenant_id) not valid;
  end if;
end $$;

-- Agent governance and runtime references.
do $$
begin
  if not exists (select 1 from pg_constraint where conname = 'fk_agent_credentials_profile') then
    alter table agent_credentials add constraint fk_agent_credentials_profile
      foreign key (tenant_id, agent_id)
      references agent_profiles(tenant_id, agent_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_agent_capabilities_profile') then
    alter table agent_capabilities add constraint fk_agent_capabilities_profile
      foreign key (tenant_id, agent_id)
      references agent_profiles(tenant_id, agent_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_runtime_capability_profiles_agent') then
    alter table agent_runtime_capability_profiles add constraint fk_runtime_capability_profiles_agent
      foreign key (tenant_id, agent_id)
      references agents(tenant_id, agent_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_runtime_capability_items_agent') then
    alter table agent_runtime_capability_items add constraint fk_runtime_capability_items_agent
      foreign key (tenant_id, agent_id)
      references agents(tenant_id, agent_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_runtime_load_snapshots_agent') then
    alter table agent_runtime_load_snapshots add constraint fk_runtime_load_snapshots_agent
      foreign key (tenant_id, agent_id)
      references agents(tenant_id, agent_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_runtime_descriptors_agent') then
    alter table agent_runtime_descriptors add constraint fk_runtime_descriptors_agent
      foreign key (tenant_id, agent_id)
      references agents(tenant_id, agent_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_agent_runtime_bindings_profile') then
    alter table agent_runtime_bindings add constraint fk_agent_runtime_bindings_profile
      foreign key (tenant_id, agent_id)
      references agent_profiles(tenant_id, agent_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_agent_runtime_bindings_runtime') then
    alter table agent_runtime_bindings add constraint fk_agent_runtime_bindings_runtime
      foreign key (tenant_id, runtime_id)
      references runtime_resources(tenant_id, runtime_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_task_assignments_agent_profile') then
    alter table task_assignments add constraint fk_task_assignments_agent_profile
      foreign key (tenant_id, agent_id)
      references agent_profiles(tenant_id, agent_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_dispatch_requests_agent_profile') then
    alter table dispatch_requests add constraint fk_dispatch_requests_agent_profile
      foreign key (tenant_id, agent_id)
      references agent_profiles(tenant_id, agent_id) not valid;
  end if;
end $$;

-- Task ownership and lifecycle evidence references.
do $$
begin
  if not exists (select 1 from pg_constraint where conname = 'fk_agent_profiles_owner_department') then
    alter table agent_profiles add constraint fk_agent_profiles_owner_department
      foreign key (tenant_id, owner_department_id)
      references departments(tenant_id, department_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_agent_profiles_owner_group') then
    alter table agent_profiles add constraint fk_agent_profiles_owner_group
      foreign key (tenant_id, owner_group_id)
      references organization_groups(tenant_id, group_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_agent_profiles_service_domain') then
    alter table agent_profiles add constraint fk_agent_profiles_service_domain
      foreign key (tenant_id, service_domain_id)
      references service_domains(tenant_id, service_domain_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_agent_profiles_trust_zone') then
    alter table agent_profiles add constraint fk_agent_profiles_trust_zone
      foreign key (tenant_id, trust_zone_id)
      references trust_zones(tenant_id, trust_zone_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_tasks_owner_department') then
    alter table tasks add constraint fk_tasks_owner_department
      foreign key (tenant_id, owner_department_id)
      references departments(tenant_id, department_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_tasks_owner_group') then
    alter table tasks add constraint fk_tasks_owner_group
      foreign key (tenant_id, owner_group_id)
      references organization_groups(tenant_id, group_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_tasks_requester_department') then
    alter table tasks add constraint fk_tasks_requester_department
      foreign key (tenant_id, requester_department_id)
      references departments(tenant_id, department_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_tasks_requester_group') then
    alter table tasks add constraint fk_tasks_requester_group
      foreign key (tenant_id, requester_group_id)
      references organization_groups(tenant_id, group_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_tasks_requester_domain') then
    alter table tasks add constraint fk_tasks_requester_domain
      foreign key (tenant_id, requester_domain_id)
      references service_domains(tenant_id, service_domain_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_tasks_executor_department') then
    alter table tasks add constraint fk_tasks_executor_department
      foreign key (tenant_id, executor_department_id)
      references departments(tenant_id, department_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_tasks_executor_group') then
    alter table tasks add constraint fk_tasks_executor_group
      foreign key (tenant_id, executor_group_id)
      references organization_groups(tenant_id, group_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_tasks_executor_domain') then
    alter table tasks add constraint fk_tasks_executor_domain
      foreign key (tenant_id, executor_domain_id)
      references service_domains(tenant_id, service_domain_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_routing_decisions_task') then
    alter table routing_decisions add constraint fk_routing_decisions_task
      foreign key (tenant_id, task_id)
      references tasks(tenant_id, task_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_task_dispatch_attempts_task') then
    alter table task_dispatch_attempts add constraint fk_task_dispatch_attempts_task
      foreign key (tenant_id, task_id)
      references tasks(tenant_id, task_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_task_execution_attempts_task') then
    alter table task_execution_attempts add constraint fk_task_execution_attempts_task
      foreign key (tenant_id, task_id)
      references tasks(tenant_id, task_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_task_callbacks_task') then
    alter table task_callbacks add constraint fk_task_callbacks_task
      foreign key (tenant_id, task_id)
      references tasks(tenant_id, task_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_dispatch_attempt_history_task') then
    alter table dispatch_attempt_history add constraint fk_dispatch_attempt_history_task
      foreign key (tenant_id, task_id)
      references tasks(tenant_id, task_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'fk_task_issue_links_task') then
    alter table task_issue_links add constraint fk_task_issue_links_task
      foreign key (tenant_id, task_id)
      references tasks(tenant_id, task_id) not valid;
  end if;
end $$;

-- Value-domain constraints required by the organization boundary.
do $$
begin
  if not exists (select 1 from pg_constraint where conname = 'ck_trust_zones_isolation_mode') then
    alter table trust_zones add constraint ck_trust_zones_isolation_mode
      check (isolation_mode in ('PER_PROJECT','PER_TRUST_ZONE','SHARED_SCOPED'));
  end if;
  if not exists (select 1 from pg_constraint where conname = 'ck_tasks_visibility_policy') then
    alter table tasks add constraint ck_tasks_visibility_policy
      check (visibility_policy in ('PRIVATE','PARTICIPANTS','DEPARTMENT','GROUP','TENANT'));
  end if;
  if not exists (select 1 from pg_constraint where conname = 'ck_tasks_sensitivity_level') then
    alter table tasks add constraint ck_tasks_sensitivity_level
      check (sensitivity_level in ('PUBLIC','INTERNAL','CONFIDENTIAL','RESTRICTED'));
  end if;
end $$;

-- Validate every Phase 0B foreign key against the backfilled data.
alter table source_systems validate constraint fk_source_systems_tenant;
alter table agent_enrollment_requests validate constraint fk_agent_enrollments_tenant;
alter table agent_profiles validate constraint fk_agent_profiles_tenant;
alter table agents validate constraint fk_agents_tenant;
alter table agent_pools validate constraint fk_agent_pools_tenant;
alter table runtime_resources validate constraint fk_runtime_resources_tenant;
alter table dispatch_flows validate constraint fk_dispatch_flows_tenant;
alter table dispatch_policies validate constraint fk_dispatch_policies_tenant;
alter table tasks validate constraint fk_tasks_tenant;
alter table task_assignments validate constraint fk_task_assignments_tenant;
alter table dispatch_requests validate constraint fk_dispatch_requests_tenant;
alter table agent_pool_members validate constraint fk_agent_pool_members_tenant;
alter table agent_runtime_bindings validate constraint fk_agent_runtime_bindings_tenant;
alter table agent_credentials validate constraint fk_agent_credentials_tenant;
alter table agent_capabilities validate constraint fk_agent_capabilities_tenant;
alter table agent_runtime_capability_profiles validate constraint fk_runtime_capability_profiles_tenant;
alter table agent_runtime_capability_items validate constraint fk_runtime_capability_items_tenant;
alter table agent_runtime_load_snapshots validate constraint fk_runtime_load_snapshots_tenant;
alter table agent_runtime_descriptors validate constraint fk_runtime_descriptors_tenant;
alter table routing_decisions validate constraint fk_routing_decisions_tenant;
alter table task_dispatch_attempts validate constraint fk_task_dispatch_attempts_tenant;
alter table task_execution_attempts validate constraint fk_task_execution_attempts_tenant;
alter table task_callbacks validate constraint fk_task_callbacks_tenant;
alter table dispatch_attempt_history validate constraint fk_dispatch_attempt_history_tenant;
alter table task_issue_links validate constraint fk_task_issue_links_tenant;
alter table agent_credentials validate constraint fk_agent_credentials_profile;
alter table agent_capabilities validate constraint fk_agent_capabilities_profile;
alter table agent_runtime_capability_profiles validate constraint fk_runtime_capability_profiles_agent;
alter table agent_runtime_capability_items validate constraint fk_runtime_capability_items_agent;
alter table agent_runtime_load_snapshots validate constraint fk_runtime_load_snapshots_agent;
alter table agent_runtime_descriptors validate constraint fk_runtime_descriptors_agent;
alter table agent_runtime_bindings validate constraint fk_agent_runtime_bindings_profile;
alter table agent_runtime_bindings validate constraint fk_agent_runtime_bindings_runtime;
alter table task_assignments validate constraint fk_task_assignments_agent_profile;
alter table dispatch_requests validate constraint fk_dispatch_requests_agent_profile;
alter table agent_profiles validate constraint fk_agent_profiles_owner_department;
alter table agent_profiles validate constraint fk_agent_profiles_owner_group;
alter table agent_profiles validate constraint fk_agent_profiles_service_domain;
alter table agent_profiles validate constraint fk_agent_profiles_trust_zone;
alter table tasks validate constraint fk_tasks_owner_department;
alter table tasks validate constraint fk_tasks_owner_group;
alter table tasks validate constraint fk_tasks_requester_department;
alter table tasks validate constraint fk_tasks_requester_group;
alter table tasks validate constraint fk_tasks_requester_domain;
alter table tasks validate constraint fk_tasks_executor_department;
alter table tasks validate constraint fk_tasks_executor_group;
alter table tasks validate constraint fk_tasks_executor_domain;
alter table routing_decisions validate constraint fk_routing_decisions_task;
alter table task_dispatch_attempts validate constraint fk_task_dispatch_attempts_task;
alter table task_execution_attempts validate constraint fk_task_execution_attempts_task;
alter table task_callbacks validate constraint fk_task_callbacks_task;
alter table dispatch_attempt_history validate constraint fk_dispatch_attempt_history_task;
alter table task_issue_links validate constraint fk_task_issue_links_task;
