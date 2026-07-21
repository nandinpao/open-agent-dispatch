-- Phase 2-7-2 Batch Validate Current Configuration Constraints
-- Migration file: V5__dispatch_validate_current_configuration_constraints.sql
--
-- Purpose:
--   Promote the Current dispatch configuration constraints introduced by:
--     * V2__dispatch_referential_integrity.sql
--     * V3__dispatch_enum_checks.sql
--   from NOT VALID to validated PostgreSQL constraints.
--
-- Scope:
--   This migration validates only Current configuration constraints:
--     1. Current configuration CHECK constraints.
--     2. Source Flow / Dispatch Policy / Agent Pool foreign keys.
--     3. Agent Pool Member foreign keys.
--
-- Out of scope:
--   Active lifecycle foreign keys remain NOT VALID for a later phase:
--     * fk_task_assignments_task
--     * fk_dispatch_requests_assignment
--     * fk_dispatch_requests_task
--
-- Safety model:
--   * This migration is intentionally split away from callback/history/evidence
--     and active lifecycle validation.
--   * It performs explicit preflight checks before ALTER TABLE ... VALIDATE CONSTRAINT.
--   * Run scripts/db/phase2-7-1-dispatch-constraint-validate-readiness-report.sql
--     before applying this migration in an existing environment.

-- -----------------------------------------------------------------------------
-- Preflight 1: all selected constraints must exist.
-- -----------------------------------------------------------------------------
DO $$
DECLARE
  missing_constraints text;
BEGIN
  WITH expected_constraints(constraint_name, table_name) AS (
    VALUES
      ('ck_agent_pools_selection_strategy_supported', 'agent_pools'),
      ('ck_agent_pools_status_supported', 'agent_pools'),
      ('ck_agent_pool_members_status_supported', 'agent_pool_members'),
      ('ck_agent_pool_members_weight_positive', 'agent_pool_members'),
      ('ck_agent_pool_members_priority_non_negative', 'agent_pool_members'),
      ('ck_dispatch_flows_status_supported', 'dispatch_flows'),
      ('ck_dispatch_policies_priority_non_negative', 'dispatch_policies'),
      ('ck_dispatch_policies_status_supported', 'dispatch_policies'),
      ('ck_dispatch_policies_routing_strategy_supported', 'dispatch_policies'),
      ('fk_dispatch_flows_source_system', 'dispatch_flows'),
      ('fk_dispatch_flows_default_pool', 'dispatch_flows'),
      ('fk_dispatch_policies_flow', 'dispatch_policies'),
      ('fk_dispatch_policies_source_system', 'dispatch_policies'),
      ('fk_dispatch_policies_target_pool', 'dispatch_policies'),
      ('fk_agent_pool_members_pool', 'agent_pool_members'),
      ('fk_agent_pool_members_agent_profile', 'agent_pool_members')
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
    RAISE EXCEPTION 'Phase 2-7-2 blocked: expected constraints are missing: %', missing_constraints;
  END IF;
END $$;

-- -----------------------------------------------------------------------------
-- Preflight 2: selected CHECK constraints must have zero violating rows.
-- -----------------------------------------------------------------------------
DO $$
DECLARE
  blocker_summary text;
BEGIN
  WITH violations AS (
    SELECT 'ck_agent_pools_selection_strategy_supported' AS constraint_name, count(*)::bigint AS violation_count
      FROM agent_pools p
     WHERE upper(trim(coalesce(p.selection_strategy, ''))) NOT IN ('LOWEST_LOAD', 'WEIGHTED_SCORE', 'MANUAL_ONLY')

    UNION ALL
    SELECT 'ck_agent_pools_status_supported', count(*)::bigint
      FROM agent_pools p
     WHERE upper(trim(coalesce(p.status, ''))) NOT IN ('ACTIVE', 'ENABLED', 'DISABLED', 'INACTIVE', 'RETIRED', 'DRAFT')

    UNION ALL
    SELECT 'ck_agent_pool_members_status_supported', count(*)::bigint
      FROM agent_pool_members m
     WHERE upper(trim(coalesce(m.member_status, ''))) NOT IN ('ACTIVE', 'ENABLED', 'DISABLED', 'INACTIVE', 'RETIRED', 'SUSPENDED')

    UNION ALL
    SELECT 'ck_agent_pool_members_weight_positive', count(*)::bigint
      FROM agent_pool_members m
     WHERE m.weight IS NULL OR m.weight <= 0

    UNION ALL
    SELECT 'ck_agent_pool_members_priority_non_negative', count(*)::bigint
      FROM agent_pool_members m
     WHERE m.priority IS NULL OR m.priority < 0

    UNION ALL
    SELECT 'ck_dispatch_flows_status_supported', count(*)::bigint
      FROM dispatch_flows f
     WHERE upper(trim(coalesce(f.status, ''))) NOT IN ('DRAFT', 'ACTIVE', 'ENABLED', 'DISABLED', 'INACTIVE', 'RETIRED')

    UNION ALL
    SELECT 'ck_dispatch_policies_priority_non_negative', count(*)::bigint
      FROM dispatch_policies r
     WHERE r.priority IS NULL OR r.priority < 0

    UNION ALL
    SELECT 'ck_dispatch_policies_status_supported', count(*)::bigint
      FROM dispatch_policies r
     WHERE upper(trim(coalesce(r.status, ''))) NOT IN ('DRAFT', 'ACTIVE', 'ENABLED', 'DISABLED', 'INACTIVE', 'RETIRED')

    UNION ALL
    SELECT 'ck_dispatch_policies_routing_strategy_supported', count(*)::bigint
      FROM dispatch_policies r
     WHERE upper(trim(coalesce(r.routing_strategy, ''))) NOT IN ('LOWEST_LOAD', 'WEIGHTED_SCORE', 'MANUAL_ONLY')
  )
  SELECT string_agg(constraint_name || '=' || violation_count, ', ' ORDER BY constraint_name)
    INTO blocker_summary
    FROM violations
   WHERE violation_count > 0;

  IF blocker_summary IS NOT NULL THEN
    RAISE EXCEPTION 'Phase 2-7-2 blocked: CHECK constraint validation has violating rows: %', blocker_summary;
  END IF;
END $$;

-- -----------------------------------------------------------------------------
-- Preflight 3: selected Current configuration foreign keys must have zero
-- violating rows.
-- -----------------------------------------------------------------------------
DO $$
DECLARE
  blocker_summary text;
BEGIN
  WITH violations AS (
    SELECT 'fk_dispatch_flows_source_system' AS constraint_name, count(*)::bigint AS violation_count
      FROM dispatch_flows f
      LEFT JOIN source_systems s
        ON s.tenant_id = f.tenant_id
       AND s.source_system_id = f.source_system
     WHERE s.source_system_id IS NULL

    UNION ALL
    SELECT 'fk_dispatch_flows_default_pool', count(*)::bigint
      FROM dispatch_flows f
      LEFT JOIN agent_pools p
        ON p.tenant_id = f.tenant_id
       AND p.pool_id = f.default_pool_id
     WHERE f.default_pool_id IS NOT NULL
       AND p.pool_id IS NULL

    UNION ALL
    SELECT 'fk_dispatch_policies_flow', count(*)::bigint
      FROM dispatch_policies r
      LEFT JOIN dispatch_flows f
        ON f.tenant_id = r.tenant_id
       AND f.flow_id = r.flow_id
     WHERE f.flow_id IS NULL

    UNION ALL
    SELECT 'fk_dispatch_policies_source_system', count(*)::bigint
      FROM dispatch_policies r
      LEFT JOIN source_systems s
        ON s.tenant_id = r.tenant_id
       AND s.source_system_id = r.source_system
     WHERE s.source_system_id IS NULL

    UNION ALL
    SELECT 'fk_dispatch_policies_target_pool', count(*)::bigint
      FROM dispatch_policies r
      LEFT JOIN agent_pools p
        ON p.tenant_id = r.tenant_id
       AND p.pool_id = r.target_pool_id
     WHERE r.target_pool_id IS NOT NULL
       AND p.pool_id IS NULL

    UNION ALL
    SELECT 'fk_agent_pool_members_pool', count(*)::bigint
      FROM agent_pool_members m
      LEFT JOIN agent_pools p
        ON p.tenant_id = m.tenant_id
       AND p.pool_id = m.pool_id
     WHERE p.pool_id IS NULL

    UNION ALL
    SELECT 'fk_agent_pool_members_agent_profile', count(*)::bigint
      FROM agent_pool_members m
      LEFT JOIN agent_profiles ap
        ON ap.tenant_id = m.tenant_id
       AND ap.agent_id = m.agent_id
     WHERE ap.agent_id IS NULL
  )
  SELECT string_agg(constraint_name || '=' || violation_count, ', ' ORDER BY constraint_name)
    INTO blocker_summary
    FROM violations
   WHERE violation_count > 0;

  IF blocker_summary IS NOT NULL THEN
    RAISE EXCEPTION 'Phase 2-7-2 blocked: FK validation has violating rows: %', blocker_summary;
  END IF;
END $$;

-- -----------------------------------------------------------------------------
-- Batch A: validate Current configuration CHECK constraints first.
-- -----------------------------------------------------------------------------
ALTER TABLE agent_pools
  VALIDATE CONSTRAINT ck_agent_pools_selection_strategy_supported;

ALTER TABLE agent_pools
  VALIDATE CONSTRAINT ck_agent_pools_status_supported;

ALTER TABLE agent_pool_members
  VALIDATE CONSTRAINT ck_agent_pool_members_status_supported;

ALTER TABLE agent_pool_members
  VALIDATE CONSTRAINT ck_agent_pool_members_weight_positive;

ALTER TABLE agent_pool_members
  VALIDATE CONSTRAINT ck_agent_pool_members_priority_non_negative;

ALTER TABLE dispatch_flows
  VALIDATE CONSTRAINT ck_dispatch_flows_status_supported;

ALTER TABLE dispatch_policies
  VALIDATE CONSTRAINT ck_dispatch_policies_priority_non_negative;

ALTER TABLE dispatch_policies
  VALIDATE CONSTRAINT ck_dispatch_policies_status_supported;

ALTER TABLE dispatch_policies
  VALIDATE CONSTRAINT ck_dispatch_policies_routing_strategy_supported;

-- -----------------------------------------------------------------------------
-- Batch B: validate Source Flow / Agent Pool / Dispatch Policy foreign keys.
-- -----------------------------------------------------------------------------
ALTER TABLE dispatch_flows
  VALIDATE CONSTRAINT fk_dispatch_flows_source_system;

ALTER TABLE dispatch_flows
  VALIDATE CONSTRAINT fk_dispatch_flows_default_pool;

ALTER TABLE dispatch_policies
  VALIDATE CONSTRAINT fk_dispatch_policies_flow;

ALTER TABLE dispatch_policies
  VALIDATE CONSTRAINT fk_dispatch_policies_source_system;

ALTER TABLE dispatch_policies
  VALIDATE CONSTRAINT fk_dispatch_policies_target_pool;

-- -----------------------------------------------------------------------------
-- Batch C: validate Agent Pool Member foreign keys.
-- -----------------------------------------------------------------------------
ALTER TABLE agent_pool_members
  VALIDATE CONSTRAINT fk_agent_pool_members_pool;

ALTER TABLE agent_pool_members
  VALIDATE CONSTRAINT fk_agent_pool_members_agent_profile;
