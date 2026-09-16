-- Phase 0B Backfill: deterministic tenant propagation and organizational defaults.
--
-- Ambiguous or unresolved ownership is never guessed. It is written to
-- tenant_backfill_conflicts and V10 refuses to enforce the boundary until the
-- conflict is resolved.

-- Discover every tenant already represented by Current configuration, runtime,
-- task, event, or integration rows.
insert into tenants (tenant_id, display_name, status)
select distinct discovered.tenant_id, discovered.tenant_id, 'ACTIVE'
from (
  select tenant_id from source_systems
  union all select tenant_id from agent_enrollment_requests
  union all select tenant_id from agent_profiles
  union all select tenant_id from agent_pools
  union all select tenant_id from agent_pool_members
  union all select tenant_id from runtime_resources
  union all select tenant_id from agent_runtime_bindings
  union all select tenant_id from dispatch_flows
  union all select tenant_id from dispatch_policies
  union all select tenant_id from tasks
  union all select tenant_id from task_assignments
  union all select tenant_id from dispatch_requests
  union all select tenant_id from routing_decisions
  union all select tenant_id from event_decisions
  union all select tenant_id from event_dedup_state
  union all select tenant_id from agent_runtime_feature_observations
  union all select tenant_id from agent_runtime_feature_trust
) discovered
where discovered.tenant_id is not null and btrim(discovered.tenant_id) <> ''
on conflict (tenant_id) do nothing;

-- Every pre-existing tenant receives explicit placeholders. These values are
-- migration scaffolding, not a substitute for organization administration.
insert into departments (
  tenant_id, department_id, department_code, department_name, description, status
)
select t.tenant_id, 'UNASSIGNED', 'UNASSIGNED', 'Unassigned Department',
       'Migration placeholder. Replace with the accountable Department.', 'ACTIVE'
from tenants t
on conflict (tenant_id, department_id) do nothing;

insert into organization_groups (
  tenant_id, group_id, group_code, group_name, group_type, description, status
)
select t.tenant_id, 'UNASSIGNED', 'UNASSIGNED', 'Unassigned Group',
       'COLLABORATION', 'Migration placeholder. Group remains optional.', 'ACTIVE'
from tenants t
on conflict (tenant_id, group_id) do nothing;

insert into service_domains (
  tenant_id, service_domain_id, domain_code, domain_name, description, status
)
select t.tenant_id, 'UNASSIGNED', 'UNASSIGNED', 'Unassigned Service Domain',
       'Migration placeholder. Replace with the executor Service Domain.', 'ACTIVE'
from tenants t
on conflict (tenant_id, service_domain_id) do nothing;

insert into trust_zones (
  tenant_id, trust_zone_id, trust_zone_code, trust_zone_name,
  isolation_mode, sensitivity_ceiling, description, status
)
select t.tenant_id, 'DEFAULT', 'DEFAULT', 'Default Trust Zone',
       'PER_TRUST_ZONE', 'INTERNAL',
       'Migration default. Production integration mappings must be reviewed.', 'ACTIVE'
from tenants t
on conflict (tenant_id, trust_zone_id) do nothing;

insert into department_group_bindings (
  tenant_id, department_id, group_id, binding_role, enabled
)
select t.tenant_id, 'UNASSIGNED', 'UNASSIGNED', 'MEMBER', true
from tenants t
on conflict (tenant_id, department_id, group_id) do nothing;

insert into department_service_domain_bindings (
  tenant_id, department_id, service_domain_id, ownership_role, is_primary, enabled
)
select t.tenant_id, 'UNASSIGNED', 'UNASSIGNED', 'OWNER', true, true
from tenants t
on conflict (tenant_id, department_id, service_domain_id) do nothing;

insert into group_service_domain_bindings (
  tenant_id, group_id, service_domain_id, participation_role, enabled
)
select t.tenant_id, 'UNASSIGNED', 'UNASSIGNED', 'SUPPORTER', true
from tenants t
on conflict (tenant_id, group_id, service_domain_id) do nothing;

insert into trust_zone_department_bindings (
  tenant_id, trust_zone_id, department_id, enabled
)
select t.tenant_id, 'DEFAULT', 'UNASSIGNED', true
from tenants t
on conflict (tenant_id, trust_zone_id, department_id) do nothing;

-- Resolve Agent tenant from authoritative or lifecycle evidence.
with agent_tenant_candidates as (
  select agent_id, tenant_id from agent_profiles
   where tenant_id is not null and btrim(tenant_id) <> ''
  union all
  select agent_id, tenant_id from agent_pool_members
   where tenant_id is not null and btrim(tenant_id) <> ''
  union all
  select agent_id, tenant_id from agent_runtime_bindings
   where tenant_id is not null and btrim(tenant_id) <> ''
  union all
  select claimed_agent_id as agent_id, tenant_id from agent_enrollment_requests
   where claimed_agent_id is not null
     and tenant_id is not null and btrim(tenant_id) <> ''
  union all
  select agent_id, tenant_id from task_assignments
   where agent_id is not null
     and tenant_id is not null and btrim(tenant_id) <> ''
  union all
  select agent_id, tenant_id from dispatch_requests
   where agent_id is not null
     and tenant_id is not null and btrim(tenant_id) <> ''
),
resolved as (
  select agent_id,
         min(tenant_id) as resolved_tenant_id,
         count(distinct tenant_id) as tenant_count,
         to_jsonb(array_agg(distinct tenant_id)) as candidate_tenants
    from agent_tenant_candidates
   where agent_id is not null
   group by agent_id
)
update agents a
   set tenant_id = r.resolved_tenant_id
  from resolved r
 where a.agent_id = r.agent_id
   and a.tenant_id is null
   and r.tenant_count = 1;

with agent_tenant_candidates as (
  select agent_id, tenant_id from agent_profiles
   where tenant_id is not null and btrim(tenant_id) <> ''
  union all
  select agent_id, tenant_id from agent_pool_members
   where tenant_id is not null and btrim(tenant_id) <> ''
  union all
  select agent_id, tenant_id from agent_runtime_bindings
   where tenant_id is not null and btrim(tenant_id) <> ''
  union all
  select claimed_agent_id as agent_id, tenant_id from agent_enrollment_requests
   where claimed_agent_id is not null
     and tenant_id is not null and btrim(tenant_id) <> ''
  union all
  select agent_id, tenant_id from task_assignments
   where agent_id is not null
     and tenant_id is not null and btrim(tenant_id) <> ''
  union all
  select agent_id, tenant_id from dispatch_requests
   where agent_id is not null
     and tenant_id is not null and btrim(tenant_id) <> ''
),
ambiguous as (
  select agent_id,
         count(distinct tenant_id) as tenant_count,
         to_jsonb(array_agg(distinct tenant_id)) as candidate_tenants
    from agent_tenant_candidates
   where agent_id is not null
   group by agent_id
  having count(distinct tenant_id) > 1
)
insert into tenant_backfill_conflicts (
  conflict_id, table_name, record_key, conflict_type,
  candidate_tenant_ids, details
)
select 'tenant-conflict-' || md5('agents:' || agent_id),
       'agents', agent_id, 'AMBIGUOUS_TENANT',
       candidate_tenants,
       'Agent ID appears in more than one tenant-scoped authority source.'
from ambiguous
on conflict (table_name, record_key, conflict_type)
do update set candidate_tenant_ids = excluded.candidate_tenant_ids,
              details = excluded.details,
              updated_at = now();

insert into tenant_backfill_conflicts (
  conflict_id, table_name, record_key, conflict_type,
  candidate_tenant_ids, details
)
select 'tenant-conflict-' || md5('agents-unresolved:' || agent_id),
       'agents', agent_id, 'UNRESOLVED_TENANT',
       '[]'::jsonb,
       'No tenant-scoped profile, pool membership, enrollment, runtime binding, assignment, or dispatch request identifies this Agent.'
from agents
where tenant_id is null
on conflict (table_name, record_key, conflict_type)
do update set details = excluded.details, updated_at = now();

-- Propagate Agent tenant to credential, metadata, and runtime projections.
update agent_credentials c
   set tenant_id = a.tenant_id
  from agents a
 where c.agent_id = a.agent_id
   and c.tenant_id is null
   and a.tenant_id is not null;

update agent_capabilities c
   set tenant_id = a.tenant_id
  from agents a
 where c.agent_id = a.agent_id
   and c.tenant_id is null
   and a.tenant_id is not null;

update agent_runtime_capability_profiles p
   set tenant_id = a.tenant_id
  from agents a
 where p.agent_id = a.agent_id
   and p.tenant_id is null
   and a.tenant_id is not null;

update agent_runtime_capability_items i
   set tenant_id = a.tenant_id
  from agents a
 where i.agent_id = a.agent_id
   and i.tenant_id is null
   and a.tenant_id is not null;

update agent_runtime_load_snapshots s
   set tenant_id = a.tenant_id
  from agents a
 where s.agent_id = a.agent_id
   and s.tenant_id is null
   and a.tenant_id is not null;

update agent_runtime_descriptors d
   set tenant_id = a.tenant_id
  from agents a
 where d.agent_id = a.agent_id
   and d.tenant_id is null
   and a.tenant_id is not null;

-- Lifecycle evidence follows the authoritative Task tenant.
update dispatch_requests dr
   set tenant_id = t.tenant_id
  from tasks t
 where dr.tenant_id is null
   and dr.task_id = t.task_id
   and t.tenant_id is not null;

update dispatch_requests dr
   set tenant_id = ta.tenant_id
  from task_assignments ta
 where dr.tenant_id is null
   and dr.assignment_id = ta.assignment_id
   and ta.tenant_id is not null;

update task_dispatch_attempts a
   set tenant_id = t.tenant_id
  from tasks t
 where a.task_id = t.task_id
   and a.tenant_id is null
   and t.tenant_id is not null;

update task_execution_attempts a
   set tenant_id = t.tenant_id
  from tasks t
 where a.task_id = t.task_id
   and a.tenant_id is null
   and t.tenant_id is not null;

update task_dispatch_attempts a
   set tenant_id = ta.tenant_id
  from task_assignments ta
 where a.tenant_id is null
   and a.assignment_id = ta.assignment_id
   and ta.tenant_id is not null;

update task_dispatch_attempts a
   set tenant_id = dr.tenant_id
  from dispatch_requests dr
 where a.tenant_id is null
   and a.dispatch_request_id = dr.dispatch_request_id
   and dr.tenant_id is not null;

update task_execution_attempts a
   set tenant_id = ta.tenant_id
  from task_assignments ta
 where a.tenant_id is null
   and a.assignment_id = ta.assignment_id
   and ta.tenant_id is not null;

update task_execution_attempts a
   set tenant_id = dr.tenant_id
  from dispatch_requests dr
 where a.tenant_id is null
   and a.dispatch_request_id = dr.dispatch_request_id
   and dr.tenant_id is not null;

update task_callbacks c
   set tenant_id = t.tenant_id
  from tasks t
 where c.task_id = t.task_id
   and c.tenant_id is null
   and t.tenant_id is not null;

update task_callbacks c
   set tenant_id = ta.tenant_id
  from task_assignments ta
 where c.tenant_id is null
   and c.assignment_id = ta.assignment_id
   and ta.tenant_id is not null;

update task_callbacks c
   set tenant_id = dr.tenant_id
  from dispatch_requests dr
 where c.tenant_id is null
   and c.dispatch_request_id = dr.dispatch_request_id
   and dr.tenant_id is not null;

update dispatch_attempt_history h
   set tenant_id = t.tenant_id
  from tasks t
 where h.task_id = t.task_id
   and h.tenant_id is null
   and t.tenant_id is not null;

update dispatch_attempt_history h
   set tenant_id = dr.tenant_id
  from dispatch_requests dr
 where h.tenant_id is null
   and h.dispatch_request_id = dr.dispatch_request_id
   and dr.tenant_id is not null;

update task_issue_links l
   set tenant_id = t.tenant_id
  from tasks t
 where l.task_id = t.task_id
   and l.tenant_id is null
   and t.tenant_id is not null;

update routing_decisions d
   set tenant_id = t.tenant_id
  from tasks t
 where d.task_id = t.task_id
   and d.tenant_id is null
   and t.tenant_id is not null;

-- Record any remaining rows instead of silently assigning a global tenant.
insert into tenant_backfill_conflicts (
  conflict_id, table_name, record_key, conflict_type,
  candidate_tenant_ids, details
)
select 'tenant-conflict-' || md5(source.table_name || ':' || source.record_key),
       source.table_name, source.record_key, 'UNRESOLVED_TENANT',
       '[]'::jsonb, source.details
from (
  select 'agent_credentials'::varchar as table_name,
         credential_id::varchar as record_key,
         'Credential cannot be associated with a tenant-scoped Agent.'::text as details
    from agent_credentials where tenant_id is null
  union all
  select 'agent_capabilities', agent_id || ':' || capability_code,
         'Capability metadata cannot be associated with a tenant-scoped Agent.'
    from agent_capabilities where tenant_id is null
  union all
  select 'agent_runtime_capability_profiles', agent_id,
         'Runtime capability profile cannot be associated with a tenant-scoped Agent.'
    from agent_runtime_capability_profiles where tenant_id is null
  union all
  select 'agent_runtime_capability_items', agent_id || ':' || capability_kind || ':' || capability_value,
         'Runtime capability item cannot be associated with a tenant-scoped Agent.'
    from agent_runtime_capability_items where tenant_id is null
  union all
  select 'agent_runtime_load_snapshots', agent_id,
         'Runtime load snapshot cannot be associated with a tenant-scoped Agent.'
    from agent_runtime_load_snapshots where tenant_id is null
  union all
  select 'agent_runtime_descriptors', agent_id,
         'Runtime descriptor cannot be associated with a tenant-scoped Agent.'
    from agent_runtime_descriptors where tenant_id is null
  union all
  select 'tasks', task_id,
         'Task has no tenant context and cannot be enforced.'
    from tasks where tenant_id is null
  union all
  select 'task_assignments', assignment_id,
         'Assignment has no tenant context and cannot be enforced.'
    from task_assignments where tenant_id is null
  union all
  select 'dispatch_requests', dispatch_request_id,
         'Dispatch request cannot be associated with its Task or Assignment tenant.'
    from dispatch_requests where tenant_id is null
  union all
  select 'routing_decisions', decision_id,
         'Routing decision cannot be associated with its Task tenant.'
    from routing_decisions where tenant_id is null
  union all
  select 'task_dispatch_attempts', attempt_id,
         'Dispatch attempt cannot be associated with its Task tenant.'
    from task_dispatch_attempts where tenant_id is null
  union all
  select 'task_execution_attempts', attempt_id,
         'Execution attempt cannot be associated with its Task tenant.'
    from task_execution_attempts where tenant_id is null
  union all
  select 'task_callbacks', callback_id,
         'Callback cannot be associated with its Task tenant.'
    from task_callbacks where tenant_id is null
  union all
  select 'dispatch_attempt_history', attempt_id,
         'Dispatch history cannot be associated with its Task tenant.'
    from dispatch_attempt_history where tenant_id is null
  union all
  select 'task_issue_links', link_id,
         'Task-Issue link cannot be associated with its Task tenant.'
    from task_issue_links where tenant_id is null
) source
on conflict (table_name, record_key, conflict_type)
do update set details = excluded.details, updated_at = now();

-- Ownership defaults preserve existing behavior while making missing ownership
-- explicit and queryable.
update agent_profiles
   set owner_department_id = coalesce(owner_department_id, 'UNASSIGNED'),
       owner_group_id = coalesce(owner_group_id, 'UNASSIGNED'),
       service_domain_id = coalesce(service_domain_id, 'UNASSIGNED'),
       trust_zone_id = coalesce(trust_zone_id, 'DEFAULT');

update tasks
   set owner_department_id = coalesce(owner_department_id, 'UNASSIGNED'),
       owner_group_id = coalesce(owner_group_id, 'UNASSIGNED'),
       requester_department_id = coalesce(requester_department_id, 'UNASSIGNED'),
       requester_group_id = coalesce(requester_group_id, 'UNASSIGNED'),
       requester_domain_id = coalesce(requester_domain_id, 'UNASSIGNED'),
       executor_department_id = coalesce(executor_department_id, 'UNASSIGNED'),
       executor_group_id = coalesce(executor_group_id, 'UNASSIGNED'),
       executor_domain_id = coalesce(executor_domain_id, 'UNASSIGNED'),
       visibility_policy = coalesce(visibility_policy, 'TENANT'),
       sensitivity_level = coalesce(sensitivity_level, 'INTERNAL');
