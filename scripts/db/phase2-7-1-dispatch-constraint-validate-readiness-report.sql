-- Phase 2-7-1 Dispatch Constraint Validate Readiness Report
--
-- Purpose:
--   Read-only readiness report for validating the NOT VALID constraints added by:
--     * Phase 2-3 V2__dispatch_referential_integrity.sql
--     * Phase 2-4 V3__dispatch_enum_checks.sql
--
-- Safety:
--   This script is read-only. It does not run ALTER TABLE, VALIDATE CONSTRAINT,
--   UPDATE, DELETE, INSERT, CREATE, DROP or TRUNCATE.
--
-- Output:
--   1. validation readiness summary by readiness_state and constraint_type
--   2. validation readiness detail per expected constraint
--   3. blocker details for constraints with violating rows
--
-- Readiness states:
--   READY_TO_VALIDATE = constraint exists, is NOT VALID, and current data has no violations
--   ALREADY_VALIDATED = constraint already validated by PostgreSQL
--   BLOCKED          = constraint exists but current data still violates it
--   MISSING          = expected constraint is not present in the database catalog

\pset pager off
\pset null '<null>'

\echo '== Phase 2-7-1 Dispatch Constraint Validation Readiness Summary =='

with expected_constraints as (
  select * from (values
    ('fk_dispatch_flows_source_system', 'FOREIGN_KEY', 'V2', 'dispatch_flows', 'source_systems', 'Source Flow source_system must exist in the same tenant.'),
    ('fk_dispatch_flows_default_pool', 'FOREIGN_KEY', 'V2', 'dispatch_flows', 'agent_pools', 'Source Flow default_pool_id must exist in the same tenant when present.'),
    ('fk_dispatch_policies_flow', 'FOREIGN_KEY', 'V2', 'dispatch_policies', 'dispatch_flows', 'Dispatch Policy flow_id must exist in the same tenant.'),
    ('fk_dispatch_policies_source_system', 'FOREIGN_KEY', 'V2', 'dispatch_policies', 'source_systems', 'Dispatch Policy source_system must exist in the same tenant.'),
    ('fk_dispatch_policies_target_pool', 'FOREIGN_KEY', 'V2', 'dispatch_policies', 'agent_pools', 'Dispatch Policy target_pool_id must exist in the same tenant when present.'),
    ('fk_agent_pool_members_pool', 'FOREIGN_KEY', 'V2', 'agent_pool_members', 'agent_pools', 'Agent Pool Member pool_id must exist in the same tenant.'),
    ('fk_agent_pool_members_agent_profile', 'FOREIGN_KEY', 'V2', 'agent_pool_members', 'agent_profiles', 'Agent Pool Member agent_id must exist in agent_profiles for the same tenant.'),
    ('fk_task_assignments_task', 'FOREIGN_KEY', 'V2', 'task_assignments', 'tasks', 'Task Assignment task_id must exist in tasks for the same tenant.'),
    ('fk_dispatch_requests_assignment', 'FOREIGN_KEY', 'V2', 'dispatch_requests', 'task_assignments', 'Dispatch Request assignment_id must exist in task_assignments for the same tenant when present.'),
    ('fk_dispatch_requests_task', 'FOREIGN_KEY', 'V2', 'dispatch_requests', 'tasks', 'Dispatch Request task_id must exist in tasks for the same tenant when present.'),
    ('ck_agent_pools_selection_strategy_supported', 'CHECK', 'V3', 'agent_pools', null, 'Agent Pool selection_strategy must be LOWEST_LOAD, WEIGHTED_SCORE or MANUAL_ONLY.'),
    ('ck_agent_pools_status_supported', 'CHECK', 'V3', 'agent_pools', null, 'Agent Pool status must be in the current lifecycle value set.'),
    ('ck_agent_pool_members_status_supported', 'CHECK', 'V3', 'agent_pool_members', null, 'Agent Pool Member member_status must be in the current lifecycle value set.'),
    ('ck_agent_pool_members_weight_positive', 'CHECK', 'V3', 'agent_pool_members', null, 'Agent Pool Member weight must be positive.'),
    ('ck_agent_pool_members_priority_non_negative', 'CHECK', 'V3', 'agent_pool_members', null, 'Agent Pool Member priority must be zero or greater.'),
    ('ck_dispatch_flows_status_supported', 'CHECK', 'V3', 'dispatch_flows', null, 'Dispatch Flow status must be in the current lifecycle value set.'),
    ('ck_dispatch_policies_priority_non_negative', 'CHECK', 'V3', 'dispatch_policies', null, 'Dispatch Policy priority must be zero or greater.'),
    ('ck_dispatch_policies_status_supported', 'CHECK', 'V3', 'dispatch_policies', null, 'Dispatch Policy status must be in the current lifecycle value set.'),
    ('ck_dispatch_policies_routing_strategy_supported', 'CHECK', 'V3', 'dispatch_policies', null, 'Dispatch Policy routing_strategy must be LOWEST_LOAD, WEIGHTED_SCORE or MANUAL_ONLY.')
  ) as t(constraint_name, constraint_type, phase_origin, table_name, referenced_table_name, description)
), catalog_state as (
  select c.conname as constraint_name,
         c.convalidated,
         c.contype,
         rel.relname as table_name
    from pg_constraint c
    join pg_class rel
      on rel.oid = c.conrelid
), violation_counts as (
  select 'fk_dispatch_flows_source_system' as constraint_name, count(*)::bigint as violation_count
    from dispatch_flows f
    left join source_systems s
      on s.tenant_id = f.tenant_id
     and s.source_system_id = f.source_system
   where s.source_system_id is null

  union all
  select 'fk_dispatch_flows_default_pool', count(*)::bigint
    from dispatch_flows f
    left join agent_pools p
      on p.tenant_id = f.tenant_id
     and p.pool_id = f.default_pool_id
   where f.default_pool_id is not null
     and p.pool_id is null

  union all
  select 'fk_dispatch_policies_flow', count(*)::bigint
    from dispatch_policies r
    left join dispatch_flows f
      on f.tenant_id = r.tenant_id
     and f.flow_id = r.flow_id
   where f.flow_id is null

  union all
  select 'fk_dispatch_policies_source_system', count(*)::bigint
    from dispatch_policies r
    left join source_systems s
      on s.tenant_id = r.tenant_id
     and s.source_system_id = r.source_system
   where s.source_system_id is null

  union all
  select 'fk_dispatch_policies_target_pool', count(*)::bigint
    from dispatch_policies r
    left join agent_pools p
      on p.tenant_id = r.tenant_id
     and p.pool_id = r.target_pool_id
   where r.target_pool_id is not null
     and p.pool_id is null

  union all
  select 'fk_agent_pool_members_pool', count(*)::bigint
    from agent_pool_members m
    left join agent_pools p
      on p.tenant_id = m.tenant_id
     and p.pool_id = m.pool_id
   where p.pool_id is null

  union all
  select 'fk_agent_pool_members_agent_profile', count(*)::bigint
    from agent_pool_members m
    left join agent_profiles ap
      on ap.tenant_id = m.tenant_id
     and ap.agent_id = m.agent_id
   where ap.agent_id is null

  union all
  select 'fk_task_assignments_task', count(*)::bigint
    from task_assignments ta
    left join tasks t
      on t.tenant_id = ta.tenant_id
     and t.task_id = ta.task_id
   where t.task_id is null

  union all
  select 'fk_dispatch_requests_assignment', count(*)::bigint
    from dispatch_requests dr
    left join task_assignments ta
      on ta.tenant_id = dr.tenant_id
     and ta.assignment_id = dr.assignment_id
   where dr.assignment_id is not null
     and ta.assignment_id is null

  union all
  select 'fk_dispatch_requests_task', count(*)::bigint
    from dispatch_requests dr
    left join tasks t
      on t.tenant_id = dr.tenant_id
     and t.task_id = dr.task_id
   where dr.task_id is not null
     and t.task_id is null

  union all
  select 'ck_agent_pools_selection_strategy_supported', count(*)::bigint
    from agent_pools p
   where upper(trim(coalesce(p.selection_strategy, ''))) not in ('LOWEST_LOAD', 'WEIGHTED_SCORE', 'MANUAL_ONLY')

  union all
  select 'ck_agent_pools_status_supported', count(*)::bigint
    from agent_pools p
   where upper(trim(coalesce(p.status, ''))) not in ('ACTIVE', 'ENABLED', 'DISABLED', 'INACTIVE', 'RETIRED', 'DRAFT')

  union all
  select 'ck_agent_pool_members_status_supported', count(*)::bigint
    from agent_pool_members m
   where upper(trim(coalesce(m.member_status, ''))) not in ('ACTIVE', 'ENABLED', 'DISABLED', 'INACTIVE', 'RETIRED', 'SUSPENDED')

  union all
  select 'ck_agent_pool_members_weight_positive', count(*)::bigint
    from agent_pool_members m
   where m.weight is null or m.weight <= 0

  union all
  select 'ck_agent_pool_members_priority_non_negative', count(*)::bigint
    from agent_pool_members m
   where m.priority is null or m.priority < 0

  union all
  select 'ck_dispatch_flows_status_supported', count(*)::bigint
    from dispatch_flows f
   where upper(trim(coalesce(f.status, ''))) not in ('DRAFT', 'ACTIVE', 'ENABLED', 'DISABLED', 'INACTIVE', 'RETIRED')

  union all
  select 'ck_dispatch_policies_priority_non_negative', count(*)::bigint
    from dispatch_policies r
   where r.priority is null or r.priority < 0

  union all
  select 'ck_dispatch_policies_status_supported', count(*)::bigint
    from dispatch_policies r
   where upper(trim(coalesce(r.status, ''))) not in ('DRAFT', 'ACTIVE', 'ENABLED', 'DISABLED', 'INACTIVE', 'RETIRED')

  union all
  select 'ck_dispatch_policies_routing_strategy_supported', count(*)::bigint
    from dispatch_policies r
   where upper(trim(coalesce(r.routing_strategy, ''))) not in ('LOWEST_LOAD', 'WEIGHTED_SCORE', 'MANUAL_ONLY')
), readiness as (
  select e.constraint_name,
         e.constraint_type,
         e.phase_origin,
         e.table_name,
         e.referenced_table_name,
         e.description,
         coalesce(v.violation_count, 0) as violation_count,
         cs.convalidated,
         case
           when cs.constraint_name is null then 'MISSING'
           when coalesce(v.violation_count, 0) > 0 then 'BLOCKED'
           when cs.convalidated then 'ALREADY_VALIDATED'
           else 'READY_TO_VALIDATE'
         end as readiness_state,
         case
           when cs.constraint_name is null then 'Restore or apply the expected constraint migration before validation.'
           when coalesce(v.violation_count, 0) > 0 then 'Repair violating rows before running ALTER TABLE ... VALIDATE CONSTRAINT.'
           when cs.convalidated then 'No action required; PostgreSQL already marks this constraint as validated.'
           else 'Safe candidate for a later explicit VALIDATE CONSTRAINT migration.'
         end as next_action
    from expected_constraints e
    left join catalog_state cs
      on cs.constraint_name = e.constraint_name
     and cs.table_name = e.table_name
    left join violation_counts v
      on v.constraint_name = e.constraint_name
)
select readiness_state,
       constraint_type,
       count(*) as constraint_count,
       sum(violation_count) as total_violating_rows
  from readiness
 group by readiness_state, constraint_type
 order by readiness_state, constraint_type;

\echo ''
\echo '== Phase 2-7-1 Dispatch Constraint Validation Readiness Detail =='

with expected_constraints as (
  select * from (values
    ('fk_dispatch_flows_source_system', 'FOREIGN_KEY', 'V2', 'dispatch_flows', 'source_systems', 'Source Flow source_system must exist in the same tenant.'),
    ('fk_dispatch_flows_default_pool', 'FOREIGN_KEY', 'V2', 'dispatch_flows', 'agent_pools', 'Source Flow default_pool_id must exist in the same tenant when present.'),
    ('fk_dispatch_policies_flow', 'FOREIGN_KEY', 'V2', 'dispatch_policies', 'dispatch_flows', 'Dispatch Policy flow_id must exist in the same tenant.'),
    ('fk_dispatch_policies_source_system', 'FOREIGN_KEY', 'V2', 'dispatch_policies', 'source_systems', 'Dispatch Policy source_system must exist in the same tenant.'),
    ('fk_dispatch_policies_target_pool', 'FOREIGN_KEY', 'V2', 'dispatch_policies', 'agent_pools', 'Dispatch Policy target_pool_id must exist in the same tenant when present.'),
    ('fk_agent_pool_members_pool', 'FOREIGN_KEY', 'V2', 'agent_pool_members', 'agent_pools', 'Agent Pool Member pool_id must exist in the same tenant.'),
    ('fk_agent_pool_members_agent_profile', 'FOREIGN_KEY', 'V2', 'agent_pool_members', 'agent_profiles', 'Agent Pool Member agent_id must exist in agent_profiles for the same tenant.'),
    ('fk_task_assignments_task', 'FOREIGN_KEY', 'V2', 'task_assignments', 'tasks', 'Task Assignment task_id must exist in tasks for the same tenant.'),
    ('fk_dispatch_requests_assignment', 'FOREIGN_KEY', 'V2', 'dispatch_requests', 'task_assignments', 'Dispatch Request assignment_id must exist in task_assignments for the same tenant when present.'),
    ('fk_dispatch_requests_task', 'FOREIGN_KEY', 'V2', 'dispatch_requests', 'tasks', 'Dispatch Request task_id must exist in tasks for the same tenant when present.'),
    ('ck_agent_pools_selection_strategy_supported', 'CHECK', 'V3', 'agent_pools', null, 'Agent Pool selection_strategy must be LOWEST_LOAD, WEIGHTED_SCORE or MANUAL_ONLY.'),
    ('ck_agent_pools_status_supported', 'CHECK', 'V3', 'agent_pools', null, 'Agent Pool status must be in the current lifecycle value set.'),
    ('ck_agent_pool_members_status_supported', 'CHECK', 'V3', 'agent_pool_members', null, 'Agent Pool Member member_status must be in the current lifecycle value set.'),
    ('ck_agent_pool_members_weight_positive', 'CHECK', 'V3', 'agent_pool_members', null, 'Agent Pool Member weight must be positive.'),
    ('ck_agent_pool_members_priority_non_negative', 'CHECK', 'V3', 'agent_pool_members', null, 'Agent Pool Member priority must be zero or greater.'),
    ('ck_dispatch_flows_status_supported', 'CHECK', 'V3', 'dispatch_flows', null, 'Dispatch Flow status must be in the current lifecycle value set.'),
    ('ck_dispatch_policies_priority_non_negative', 'CHECK', 'V3', 'dispatch_policies', null, 'Dispatch Policy priority must be zero or greater.'),
    ('ck_dispatch_policies_status_supported', 'CHECK', 'V3', 'dispatch_policies', null, 'Dispatch Policy status must be in the current lifecycle value set.'),
    ('ck_dispatch_policies_routing_strategy_supported', 'CHECK', 'V3', 'dispatch_policies', null, 'Dispatch Policy routing_strategy must be LOWEST_LOAD, WEIGHTED_SCORE or MANUAL_ONLY.')
  ) as t(constraint_name, constraint_type, phase_origin, table_name, referenced_table_name, description)
), catalog_state as (
  select c.conname as constraint_name,
         c.convalidated,
         rel.relname as table_name
    from pg_constraint c
    join pg_class rel
      on rel.oid = c.conrelid
), violation_counts as (
  select 'fk_dispatch_flows_source_system' as constraint_name, count(*)::bigint as violation_count
    from dispatch_flows f left join source_systems s on s.tenant_id = f.tenant_id and s.source_system_id = f.source_system where s.source_system_id is null
  union all select 'fk_dispatch_flows_default_pool', count(*)::bigint from dispatch_flows f left join agent_pools p on p.tenant_id = f.tenant_id and p.pool_id = f.default_pool_id where f.default_pool_id is not null and p.pool_id is null
  union all select 'fk_dispatch_policies_flow', count(*)::bigint from dispatch_policies r left join dispatch_flows f on f.tenant_id = r.tenant_id and f.flow_id = r.flow_id where f.flow_id is null
  union all select 'fk_dispatch_policies_source_system', count(*)::bigint from dispatch_policies r left join source_systems s on s.tenant_id = r.tenant_id and s.source_system_id = r.source_system where s.source_system_id is null
  union all select 'fk_dispatch_policies_target_pool', count(*)::bigint from dispatch_policies r left join agent_pools p on p.tenant_id = r.tenant_id and p.pool_id = r.target_pool_id where r.target_pool_id is not null and p.pool_id is null
  union all select 'fk_agent_pool_members_pool', count(*)::bigint from agent_pool_members m left join agent_pools p on p.tenant_id = m.tenant_id and p.pool_id = m.pool_id where p.pool_id is null
  union all select 'fk_agent_pool_members_agent_profile', count(*)::bigint from agent_pool_members m left join agent_profiles ap on ap.tenant_id = m.tenant_id and ap.agent_id = m.agent_id where ap.agent_id is null
  union all select 'fk_task_assignments_task', count(*)::bigint from task_assignments ta left join tasks t on t.tenant_id = ta.tenant_id and t.task_id = ta.task_id where t.task_id is null
  union all select 'fk_dispatch_requests_assignment', count(*)::bigint from dispatch_requests dr left join task_assignments ta on ta.tenant_id = dr.tenant_id and ta.assignment_id = dr.assignment_id where dr.assignment_id is not null and ta.assignment_id is null
  union all select 'fk_dispatch_requests_task', count(*)::bigint from dispatch_requests dr left join tasks t on t.tenant_id = dr.tenant_id and t.task_id = dr.task_id where dr.task_id is not null and t.task_id is null
  union all select 'ck_agent_pools_selection_strategy_supported', count(*)::bigint from agent_pools p where upper(trim(coalesce(p.selection_strategy, ''))) not in ('LOWEST_LOAD', 'WEIGHTED_SCORE', 'MANUAL_ONLY')
  union all select 'ck_agent_pools_status_supported', count(*)::bigint from agent_pools p where upper(trim(coalesce(p.status, ''))) not in ('ACTIVE', 'ENABLED', 'DISABLED', 'INACTIVE', 'RETIRED', 'DRAFT')
  union all select 'ck_agent_pool_members_status_supported', count(*)::bigint from agent_pool_members m where upper(trim(coalesce(m.member_status, ''))) not in ('ACTIVE', 'ENABLED', 'DISABLED', 'INACTIVE', 'RETIRED', 'SUSPENDED')
  union all select 'ck_agent_pool_members_weight_positive', count(*)::bigint from agent_pool_members m where m.weight is null or m.weight <= 0
  union all select 'ck_agent_pool_members_priority_non_negative', count(*)::bigint from agent_pool_members m where m.priority is null or m.priority < 0
  union all select 'ck_dispatch_flows_status_supported', count(*)::bigint from dispatch_flows f where upper(trim(coalesce(f.status, ''))) not in ('DRAFT', 'ACTIVE', 'ENABLED', 'DISABLED', 'INACTIVE', 'RETIRED')
  union all select 'ck_dispatch_policies_priority_non_negative', count(*)::bigint from dispatch_policies r where r.priority is null or r.priority < 0
  union all select 'ck_dispatch_policies_status_supported', count(*)::bigint from dispatch_policies r where upper(trim(coalesce(r.status, ''))) not in ('DRAFT', 'ACTIVE', 'ENABLED', 'DISABLED', 'INACTIVE', 'RETIRED')
  union all select 'ck_dispatch_policies_routing_strategy_supported', count(*)::bigint from dispatch_policies r where upper(trim(coalesce(r.routing_strategy, ''))) not in ('LOWEST_LOAD', 'WEIGHTED_SCORE', 'MANUAL_ONLY')
), readiness as (
  select e.constraint_name,
         e.constraint_type,
         e.phase_origin,
         e.table_name,
         e.referenced_table_name,
         coalesce(v.violation_count, 0) as violation_count,
         cs.convalidated,
         case
           when cs.constraint_name is null then 'MISSING'
           when coalesce(v.violation_count, 0) > 0 then 'BLOCKED'
           when cs.convalidated then 'ALREADY_VALIDATED'
           else 'READY_TO_VALIDATE'
         end as readiness_state,
         case
           when cs.constraint_name is null then 'Restore or apply the expected constraint migration before validation.'
           when coalesce(v.violation_count, 0) > 0 then 'Repair violating rows before running ALTER TABLE ... VALIDATE CONSTRAINT.'
           when cs.convalidated then 'No action required.'
           else 'Candidate for explicit VALIDATE CONSTRAINT migration.'
         end as next_action
    from expected_constraints e
    left join catalog_state cs on cs.constraint_name = e.constraint_name and cs.table_name = e.table_name
    left join violation_counts v on v.constraint_name = e.constraint_name
)
select readiness_state,
       constraint_type,
       phase_origin,
       table_name,
       constraint_name,
       coalesce(referenced_table_name, '-') as referenced_table_name,
       violation_count,
       coalesce(convalidated::text, 'missing') as pg_convalidated,
       next_action
  from readiness
 order by case readiness_state when 'BLOCKED' then 1 when 'MISSING' then 2 when 'READY_TO_VALIDATE' then 3 else 4 end,
          constraint_type,
          table_name,
          constraint_name;

\echo ''
\echo '== Phase 2-7-1 Dispatch Constraint Validation Blocker Details =='

with blocker_details as (
  select 'fk_dispatch_flows_source_system' as constraint_name, f.tenant_id, 'dispatch_flows' as entity, f.flow_id as entity_id, f.source_system as offending_value,
         'Source Flow source_system does not exist in source_systems for the same tenant.' as message
    from dispatch_flows f left join source_systems s on s.tenant_id = f.tenant_id and s.source_system_id = f.source_system where s.source_system_id is null
  union all select 'fk_dispatch_flows_default_pool', f.tenant_id, 'dispatch_flows', f.flow_id, f.default_pool_id, 'Source Flow default_pool_id does not exist in agent_pools for the same tenant.' from dispatch_flows f left join agent_pools p on p.tenant_id = f.tenant_id and p.pool_id = f.default_pool_id where f.default_pool_id is not null and p.pool_id is null
  union all select 'fk_dispatch_policies_flow', r.tenant_id, 'dispatch_policies', r.policy_id, r.flow_id, 'Dispatch Policy flow_id does not exist in dispatch_flows for the same tenant.' from dispatch_policies r left join dispatch_flows f on f.tenant_id = r.tenant_id and f.flow_id = r.flow_id where f.flow_id is null
  union all select 'fk_dispatch_policies_source_system', r.tenant_id, 'dispatch_policies', r.policy_id, r.source_system, 'Dispatch Policy source_system does not exist in source_systems for the same tenant.' from dispatch_policies r left join source_systems s on s.tenant_id = r.tenant_id and s.source_system_id = r.source_system where s.source_system_id is null
  union all select 'fk_dispatch_policies_target_pool', r.tenant_id, 'dispatch_policies', r.policy_id, r.target_pool_id, 'Dispatch Policy target_pool_id does not exist in agent_pools for the same tenant.' from dispatch_policies r left join agent_pools p on p.tenant_id = r.tenant_id and p.pool_id = r.target_pool_id where r.target_pool_id is not null and p.pool_id is null
  union all select 'fk_agent_pool_members_pool', m.tenant_id, 'agent_pool_members', concat(m.pool_id, '/', m.agent_id), m.pool_id, 'Pool Member pool_id does not exist in agent_pools for the same tenant.' from agent_pool_members m left join agent_pools p on p.tenant_id = m.tenant_id and p.pool_id = m.pool_id where p.pool_id is null
  union all select 'fk_agent_pool_members_agent_profile', m.tenant_id, 'agent_pool_members', concat(m.pool_id, '/', m.agent_id), m.agent_id, 'Pool Member agent_id does not exist in agent_profiles for the same tenant.' from agent_pool_members m left join agent_profiles ap on ap.tenant_id = m.tenant_id and ap.agent_id = m.agent_id where ap.agent_id is null
  union all select 'fk_task_assignments_task', ta.tenant_id, 'task_assignments', ta.assignment_id, ta.task_id, 'Task Assignment task_id does not exist in tasks for the same tenant.' from task_assignments ta left join tasks t on t.tenant_id = ta.tenant_id and t.task_id = ta.task_id where t.task_id is null
  union all select 'fk_dispatch_requests_assignment', dr.tenant_id, 'dispatch_requests', dr.request_id, dr.assignment_id, 'Dispatch Request assignment_id does not exist in task_assignments for the same tenant.' from dispatch_requests dr left join task_assignments ta on ta.tenant_id = dr.tenant_id and ta.assignment_id = dr.assignment_id where dr.assignment_id is not null and ta.assignment_id is null
  union all select 'fk_dispatch_requests_task', dr.tenant_id, 'dispatch_requests', dr.request_id, dr.task_id, 'Dispatch Request task_id does not exist in tasks for the same tenant.' from dispatch_requests dr left join tasks t on t.tenant_id = dr.tenant_id and t.task_id = dr.task_id where dr.task_id is not null and t.task_id is null
  union all select 'ck_agent_pools_selection_strategy_supported', p.tenant_id, 'agent_pools', p.pool_id, p.selection_strategy, 'Agent Pool selection_strategy is outside the current supported strategy set.' from agent_pools p where upper(trim(coalesce(p.selection_strategy, ''))) not in ('LOWEST_LOAD', 'WEIGHTED_SCORE', 'MANUAL_ONLY')
  union all select 'ck_agent_pools_status_supported', p.tenant_id, 'agent_pools', p.pool_id, p.status, 'Agent Pool status is outside the current lifecycle status set.' from agent_pools p where upper(trim(coalesce(p.status, ''))) not in ('ACTIVE', 'ENABLED', 'DISABLED', 'INACTIVE', 'RETIRED', 'DRAFT')
  union all select 'ck_agent_pool_members_status_supported', m.tenant_id, 'agent_pool_members', concat(m.pool_id, '/', m.agent_id), m.member_status, 'Agent Pool Member member_status is outside the current lifecycle status set.' from agent_pool_members m where upper(trim(coalesce(m.member_status, ''))) not in ('ACTIVE', 'ENABLED', 'DISABLED', 'INACTIVE', 'RETIRED', 'SUSPENDED')
  union all select 'ck_agent_pool_members_weight_positive', m.tenant_id, 'agent_pool_members', concat(m.pool_id, '/', m.agent_id), m.weight::text, 'Agent Pool Member weight must be positive.' from agent_pool_members m where m.weight is null or m.weight <= 0
  union all select 'ck_agent_pool_members_priority_non_negative', m.tenant_id, 'agent_pool_members', concat(m.pool_id, '/', m.agent_id), m.priority::text, 'Agent Pool Member priority must be zero or greater.' from agent_pool_members m where m.priority is null or m.priority < 0
  union all select 'ck_dispatch_flows_status_supported', f.tenant_id, 'dispatch_flows', f.flow_id, f.status, 'Dispatch Flow status is outside the current lifecycle status set.' from dispatch_flows f where upper(trim(coalesce(f.status, ''))) not in ('DRAFT', 'ACTIVE', 'ENABLED', 'DISABLED', 'INACTIVE', 'RETIRED')
  union all select 'ck_dispatch_policies_priority_non_negative', r.tenant_id, 'dispatch_policies', r.policy_id, r.priority::text, 'Dispatch Policy priority must be zero or greater.' from dispatch_policies r where r.priority is null or r.priority < 0
  union all select 'ck_dispatch_policies_status_supported', r.tenant_id, 'dispatch_policies', r.policy_id, r.status, 'Dispatch Policy status is outside the current lifecycle status set.' from dispatch_policies r where upper(trim(coalesce(r.status, ''))) not in ('DRAFT', 'ACTIVE', 'ENABLED', 'DISABLED', 'INACTIVE', 'RETIRED')
  union all select 'ck_dispatch_policies_routing_strategy_supported', r.tenant_id, 'dispatch_policies', r.policy_id, r.routing_strategy, 'Dispatch Policy routing_strategy is outside the current supported strategy set.' from dispatch_policies r where upper(trim(coalesce(r.routing_strategy, ''))) not in ('LOWEST_LOAD', 'WEIGHTED_SCORE', 'MANUAL_ONLY')
)
select *
  from blocker_details
 order by constraint_name, tenant_id, entity, entity_id
 limit 500;
