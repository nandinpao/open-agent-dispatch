-- Phase 2-4 Dispatch Enum / Value Check Constraints
-- Migration file: V3__dispatch_enum_checks.sql
--
-- Purpose:
--   Add value-domain safety for the Current dispatch configuration path.
--   These constraints prevent unsupported strategy/status/weight/priority values
--   from being written by Admin UI, APIs, scripts or future services.
--
-- Safety model:
--   * Constraints are added NOT VALID so existing rows are not full-table scanned
--     during rollout.
--   * PostgreSQL still enforces NOT VALID CHECK constraints for new inserts and
--     updates after the constraint is added.
--   * Phase 2-1 and Phase 2-2 reports remain the source for finding and planning
--     cleanup of pre-existing invalid rows.
--   * Preflight diagnostics remain available at:
--       scripts/db/phase2-1-dispatch-integrity-report.sql
--       scripts/db/phase2-2-dispatch-integrity-repair-dry-run.sql
--   * This migration intentionally does not add optimistic locking or validate FK
--     constraints.

-- -----------------------------------------------------------------------------
-- Agent Pool checks.
-- -----------------------------------------------------------------------------
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_agent_pools_selection_strategy_supported') THEN
    ALTER TABLE agent_pools
      ADD CONSTRAINT ck_agent_pools_selection_strategy_supported
      CHECK (upper(trim(coalesce(selection_strategy, ''))) IN ('LOWEST_LOAD', 'WEIGHTED_SCORE', 'MANUAL_ONLY'))
      NOT VALID;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_agent_pools_status_supported') THEN
    ALTER TABLE agent_pools
      ADD CONSTRAINT ck_agent_pools_status_supported
      CHECK (upper(trim(coalesce(status, ''))) IN ('ACTIVE', 'ENABLED', 'DISABLED', 'INACTIVE', 'RETIRED', 'DRAFT'))
      NOT VALID;
  END IF;
END $$;

COMMENT ON CONSTRAINT ck_agent_pools_selection_strategy_supported ON agent_pools IS
  'Phase 2-4: Current Agent Pool selection strategy values supported by Runtime/UI.';
COMMENT ON CONSTRAINT ck_agent_pools_status_supported ON agent_pools IS
  'Phase 2-4: Current Agent Pool lifecycle status value set.';

-- -----------------------------------------------------------------------------
-- Agent Pool Member checks.
-- -----------------------------------------------------------------------------
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_agent_pool_members_status_supported') THEN
    ALTER TABLE agent_pool_members
      ADD CONSTRAINT ck_agent_pool_members_status_supported
      CHECK (upper(trim(coalesce(member_status, ''))) IN ('ACTIVE', 'ENABLED', 'DISABLED', 'INACTIVE', 'RETIRED', 'SUSPENDED'))
      NOT VALID;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_agent_pool_members_weight_positive') THEN
    ALTER TABLE agent_pool_members
      ADD CONSTRAINT ck_agent_pool_members_weight_positive
      CHECK (weight > 0)
      NOT VALID;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_agent_pool_members_priority_non_negative') THEN
    ALTER TABLE agent_pool_members
      ADD CONSTRAINT ck_agent_pool_members_priority_non_negative
      CHECK (priority >= 0)
      NOT VALID;
  END IF;
END $$;

COMMENT ON CONSTRAINT ck_agent_pool_members_status_supported ON agent_pool_members IS
  'Phase 2-4: Current Agent Pool member lifecycle status value set.';
COMMENT ON CONSTRAINT ck_agent_pool_members_weight_positive ON agent_pool_members IS
  'Phase 2-4: Pool member weight must be positive for LOWEST_LOAD/WEIGHTED_SCORE selection.';
COMMENT ON CONSTRAINT ck_agent_pool_members_priority_non_negative ON agent_pool_members IS
  'Phase 2-4: Pool member priority must be zero or greater.';

-- -----------------------------------------------------------------------------
-- Source Flow checks.
-- -----------------------------------------------------------------------------
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_dispatch_flows_status_supported') THEN
    ALTER TABLE dispatch_flows
      ADD CONSTRAINT ck_dispatch_flows_status_supported
      CHECK (upper(trim(coalesce(status, ''))) IN ('DRAFT', 'ACTIVE', 'ENABLED', 'DISABLED', 'INACTIVE', 'RETIRED'))
      NOT VALID;
  END IF;
END $$;

COMMENT ON CONSTRAINT ck_dispatch_flows_status_supported ON dispatch_flows IS
  'Phase 2-4: Current Source Flow lifecycle status value set.';

-- -----------------------------------------------------------------------------
-- Dispatch Policy / Flow Rule checks.
-- dispatch_policies is the physical table for current Flow Rules.
-- -----------------------------------------------------------------------------
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_dispatch_policies_priority_non_negative') THEN
    ALTER TABLE dispatch_policies
      ADD CONSTRAINT ck_dispatch_policies_priority_non_negative
      CHECK (priority >= 0)
      NOT VALID;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_dispatch_policies_status_supported') THEN
    ALTER TABLE dispatch_policies
      ADD CONSTRAINT ck_dispatch_policies_status_supported
      CHECK (upper(trim(coalesce(status, ''))) IN ('DRAFT', 'ACTIVE', 'ENABLED', 'DISABLED', 'INACTIVE', 'RETIRED'))
      NOT VALID;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_dispatch_policies_routing_strategy_supported') THEN
    ALTER TABLE dispatch_policies
      ADD CONSTRAINT ck_dispatch_policies_routing_strategy_supported
      CHECK (upper(trim(coalesce(routing_strategy, ''))) IN ('LOWEST_LOAD', 'WEIGHTED_SCORE', 'MANUAL_ONLY'))
      NOT VALID;
  END IF;
END $$;

COMMENT ON CONSTRAINT ck_dispatch_policies_priority_non_negative ON dispatch_policies IS
  'Phase 2-4: Flow Rule priority must be zero or greater.';
COMMENT ON CONSTRAINT ck_dispatch_policies_status_supported ON dispatch_policies IS
  'Phase 2-4: Current Flow Rule lifecycle status value set.';
COMMENT ON CONSTRAINT ck_dispatch_policies_routing_strategy_supported ON dispatch_policies IS
  'Phase 2-4: Flow Rule routing strategy values aligned to supported current Pool selection strategies.';
