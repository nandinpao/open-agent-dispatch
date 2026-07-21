-- Phase 2-7-3 Validate Active Lifecycle Constraints
-- Migration file: V6__dispatch_validate_active_lifecycle_constraints.sql
--
-- Purpose:
--   Promote the active lifecycle foreign keys introduced by
--   V2__dispatch_referential_integrity.sql from NOT VALID to validated
--   PostgreSQL constraints:
--     * fk_task_assignments_task
--     * fk_dispatch_requests_assignment
--     * fk_dispatch_requests_task
--
-- Scope:
--   This migration validates only the active Task -> Assignment -> Dispatch
--   Request lifecycle. It does not validate callback/history/evidence or
--   legacy reference-only rows.
--
-- Safety model:
--   * This migration performs explicit preflight checks before
--     ALTER TABLE ... VALIDATE CONSTRAINT.
--   * Run scripts/db/phase2-7-3-active-lifecycle-validate-readiness-report.sql
--     before applying this migration in an existing environment.
--   * The three constraints are tenant-aware and rely on the tenant_id support
--     added by V2 for task_assignments and dispatch_requests.

-- -----------------------------------------------------------------------------
-- Preflight 1: all selected active lifecycle constraints must exist.
-- -----------------------------------------------------------------------------
DO $$
DECLARE
  missing_constraints text;
BEGIN
  WITH expected_constraints(constraint_name, table_name) AS (
    VALUES
      ('fk_task_assignments_task', 'task_assignments'),
      ('fk_dispatch_requests_assignment', 'dispatch_requests'),
      ('fk_dispatch_requests_task', 'dispatch_requests')
  )
  SELECT string_agg(e.table_name || '.' || e.constraint_name, ', ' ORDER BY e.table_name, e.constraint_name)
    INTO missing_constraints
    FROM expected_constraints e
    LEFT JOIN pg_class rel
      ON rel.relname = e.table_name
    LEFT JOIN pg_constraint c
      ON c.conrelid = rel.oid
     AND c.conname = e.constraint_name
   WHERE c.oid IS NULL;

  IF missing_constraints IS NOT NULL THEN
    RAISE EXCEPTION 'Phase 2-7-3 blocked: expected active lifecycle constraints are missing: %', missing_constraints;
  END IF;
END $$;

-- -----------------------------------------------------------------------------
-- Preflight 2: active lifecycle foreign keys must have zero violating rows.
-- -----------------------------------------------------------------------------
DO $$
DECLARE
  blocker_summary text;
BEGIN
  WITH violations AS (
    SELECT 'fk_task_assignments_task' AS constraint_name, count(*)::bigint AS violation_count
      FROM task_assignments ta
      LEFT JOIN tasks t
        ON t.tenant_id = ta.tenant_id
       AND t.task_id = ta.task_id
     WHERE t.task_id IS NULL

    UNION ALL
    SELECT 'fk_dispatch_requests_assignment', count(*)::bigint
      FROM dispatch_requests dr
      LEFT JOIN task_assignments ta
        ON ta.tenant_id = dr.tenant_id
       AND ta.assignment_id = dr.assignment_id
     WHERE dr.assignment_id IS NOT NULL
       AND ta.assignment_id IS NULL

    UNION ALL
    SELECT 'fk_dispatch_requests_task', count(*)::bigint
      FROM dispatch_requests dr
      LEFT JOIN tasks t
        ON t.tenant_id = dr.tenant_id
       AND t.task_id = dr.task_id
     WHERE dr.task_id IS NOT NULL
       AND t.task_id IS NULL
  )
  SELECT string_agg(constraint_name || '=' || violation_count, ', ' ORDER BY constraint_name)
    INTO blocker_summary
    FROM violations
   WHERE violation_count > 0;

  IF blocker_summary IS NOT NULL THEN
    RAISE EXCEPTION 'Phase 2-7-3 blocked: active lifecycle FK validation has violating rows: %', blocker_summary;
  END IF;
END $$;

-- -----------------------------------------------------------------------------
-- Preflight 3: advisory task consistency must not hide a tenant mismatch.
-- -----------------------------------------------------------------------------
DO $$
DECLARE
  mismatch_count bigint;
BEGIN
  SELECT count(*)::bigint
    INTO mismatch_count
    FROM dispatch_requests dr
    JOIN task_assignments ta
      ON ta.tenant_id = dr.tenant_id
     AND ta.assignment_id = dr.assignment_id
   WHERE dr.assignment_id IS NOT NULL
     AND dr.task_id IS NOT NULL
     AND ta.task_id IS NOT NULL
     AND ta.task_id <> dr.task_id;

  IF mismatch_count > 0 THEN
    RAISE EXCEPTION 'Phase 2-7-3 blocked: dispatch_requests assignment/task mismatch rows must be reviewed before lifecycle validation. count=%', mismatch_count;
  END IF;
END $$;

-- -----------------------------------------------------------------------------
-- Batch D: validate active Task -> Assignment -> Dispatch Request lifecycle FKs.
-- -----------------------------------------------------------------------------
ALTER TABLE task_assignments
  VALIDATE CONSTRAINT fk_task_assignments_task;

ALTER TABLE dispatch_requests
  VALIDATE CONSTRAINT fk_dispatch_requests_assignment;

ALTER TABLE dispatch_requests
  VALIDATE CONSTRAINT fk_dispatch_requests_task;
