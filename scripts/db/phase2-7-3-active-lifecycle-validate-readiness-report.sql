-- Phase 2-7-3 Active Lifecycle Constraint Validate Readiness Report
--
-- Purpose:
--   Read-only readiness report for validating the active lifecycle foreign keys
--   introduced by Phase 2-3 V2__dispatch_referential_integrity.sql:
--     * fk_task_assignments_task
--     * fk_dispatch_requests_assignment
--     * fk_dispatch_requests_task
--
-- Safety:
--   This script is read-only. It does not run schema-changing or data-mutating commands.
--
-- Output:
--   1. active lifecycle validation readiness summary
--   2. active lifecycle validation readiness detail
--   3. blocker details for orphan / tenant-mismatch rows
--   4. advisory consistency findings that are not FK validation blockers
--
-- Readiness states:
--   READY_TO_VALIDATE = constraint exists, is NOT VALID, and current data has no violations
--   ALREADY_VALIDATED = constraint already validated by PostgreSQL
--   BLOCKED          = constraint exists but current data still violates it
--   MISSING          = expected constraint is not present in the database catalog

\pset pager off
\pset null '<null>'

\echo '== Phase 2-7-3 Active Lifecycle Constraint Validation Readiness Summary =='

with expected_constraints as (
  select * from (values
    ('fk_task_assignments_task', 'FOREIGN_KEY', 'V2', 'task_assignments', 'tasks', 'Task Assignment task_id must exist in tasks for the same tenant.'),
    ('fk_dispatch_requests_assignment', 'FOREIGN_KEY', 'V2', 'dispatch_requests', 'task_assignments', 'Dispatch Request assignment_id must exist in task_assignments for the same tenant when present.'),
    ('fk_dispatch_requests_task', 'FOREIGN_KEY', 'V2', 'dispatch_requests', 'tasks', 'Dispatch Request task_id must exist in tasks for the same tenant when present.')
  ) as t(constraint_name, constraint_type, phase_origin, table_name, referenced_table_name, description)
), catalog_state as (
  select c.conname as constraint_name,
         c.convalidated,
         rel.relname as table_name
    from pg_constraint c
    join pg_class rel
      on rel.oid = c.conrelid
), violation_counts as (
  select 'fk_task_assignments_task' as constraint_name, count(*)::bigint as violation_count
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
           when cs.constraint_name is null then 'Restore or apply V2__dispatch_referential_integrity.sql before validation.'
           when coalesce(v.violation_count, 0) > 0 then 'Repair active lifecycle orphan or tenant-mismatch rows before validation.'
           when cs.convalidated then 'No action required; PostgreSQL already marks this constraint as validated.'
           else 'Safe candidate for V6 active lifecycle validation migration.'
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
\echo '== Phase 2-7-3 Active Lifecycle Constraint Validation Readiness Detail =='

with expected_constraints as (
  select * from (values
    ('fk_task_assignments_task', 'FOREIGN_KEY', 'V2', 'task_assignments', 'tasks', 'Task Assignment task_id must exist in tasks for the same tenant.'),
    ('fk_dispatch_requests_assignment', 'FOREIGN_KEY', 'V2', 'dispatch_requests', 'task_assignments', 'Dispatch Request assignment_id must exist in task_assignments for the same tenant when present.'),
    ('fk_dispatch_requests_task', 'FOREIGN_KEY', 'V2', 'dispatch_requests', 'tasks', 'Dispatch Request task_id must exist in tasks for the same tenant when present.')
  ) as t(constraint_name, constraint_type, phase_origin, table_name, referenced_table_name, description)
), catalog_state as (
  select c.conname as constraint_name,
         c.convalidated,
         rel.relname as table_name
    from pg_constraint c
    join pg_class rel
      on rel.oid = c.conrelid
), violation_counts as (
  select 'fk_task_assignments_task' as constraint_name, count(*)::bigint as violation_count
    from task_assignments ta left join tasks t on t.tenant_id = ta.tenant_id and t.task_id = ta.task_id where t.task_id is null
  union all
  select 'fk_dispatch_requests_assignment', count(*)::bigint
    from dispatch_requests dr left join task_assignments ta on ta.tenant_id = dr.tenant_id and ta.assignment_id = dr.assignment_id where dr.assignment_id is not null and ta.assignment_id is null
  union all
  select 'fk_dispatch_requests_task', count(*)::bigint
    from dispatch_requests dr left join tasks t on t.tenant_id = dr.tenant_id and t.task_id = dr.task_id where dr.task_id is not null and t.task_id is null
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
           when cs.constraint_name is null then 'Restore or apply V2__dispatch_referential_integrity.sql before validation.'
           when coalesce(v.violation_count, 0) > 0 then 'Repair active lifecycle orphan or tenant-mismatch rows before validation.'
           when cs.convalidated then 'No action required.'
           else 'Safe candidate for V6 active lifecycle validation.'
         end as next_action
    from expected_constraints e
    left join catalog_state cs on cs.constraint_name = e.constraint_name and cs.table_name = e.table_name
    left join violation_counts v on v.constraint_name = e.constraint_name
)
select constraint_name,
       table_name,
       referenced_table_name,
       readiness_state,
       violation_count,
       coalesce(convalidated, false) as pg_convalidated,
       description,
       next_action
  from readiness
 order by table_name, constraint_name;

\echo ''
\echo '== Phase 2-7-3 Active Lifecycle Blocker Details =='

with blockers as (
  select 'fk_task_assignments_task' as constraint_name,
         ta.tenant_id,
         'task_assignments' as entity,
         ta.assignment_id as entity_id,
         'task_id' as reference_type,
         ta.task_id as reference_id,
         'Task assignment references a task that does not exist in the same tenant.' as message
    from task_assignments ta
    left join tasks t
      on t.tenant_id = ta.tenant_id
     and t.task_id = ta.task_id
   where t.task_id is null

  union all
  select 'fk_dispatch_requests_assignment',
         dr.tenant_id,
         'dispatch_requests',
         dr.dispatch_request_id,
         'assignment_id',
         dr.assignment_id,
         'Dispatch request references an assignment that does not exist in the same tenant.'
    from dispatch_requests dr
    left join task_assignments ta
      on ta.tenant_id = dr.tenant_id
     and ta.assignment_id = dr.assignment_id
   where dr.assignment_id is not null
     and ta.assignment_id is null

  union all
  select 'fk_dispatch_requests_task',
         dr.tenant_id,
         'dispatch_requests',
         dr.dispatch_request_id,
         'task_id',
         dr.task_id,
         'Dispatch request references a task that does not exist in the same tenant.'
    from dispatch_requests dr
    left join tasks t
      on t.tenant_id = dr.tenant_id
     and t.task_id = dr.task_id
   where dr.task_id is not null
     and t.task_id is null
)
select *
  from blockers
 order by constraint_name, tenant_id, entity_id
 limit 500;

\echo ''
\echo '== Phase 2-7-3 Active Lifecycle Advisory Findings =='

with advisory as (
  select 'DISPATCH_REQUEST_ASSIGNMENT_TASK_MISMATCH' as advisory_id,
         dr.tenant_id,
         dr.dispatch_request_id,
         dr.assignment_id,
         dr.task_id as dispatch_request_task_id,
         ta.task_id as assignment_task_id,
         'Dispatch request task_id differs from the referenced assignment task_id. This is not enforced by the three FK validations, but should be reviewed.' as message
    from dispatch_requests dr
    join task_assignments ta
      on ta.tenant_id = dr.tenant_id
     and ta.assignment_id = dr.assignment_id
   where dr.assignment_id is not null
     and dr.task_id is not null
     and ta.task_id is not null
     and ta.task_id <> dr.task_id
)
select *
  from advisory
 order by tenant_id, dispatch_request_id
 limit 500;
