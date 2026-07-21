-- Phase 2-5 Dispatch Optimistic Locking for Admin-owned Configuration
-- Migration file: V4__dispatch_optimistic_locking.sql
--
-- Purpose:
--   Add version / updated_by metadata and automatic version bumping to the
--   Admin-owned Current dispatch configuration tables. This prevents silent
--   last-write-wins overwrites once Phase 2-6 wires API/UI expected-version
--   checks and stale-write handling.
--
-- Scope:
--   * source_systems
--   * dispatch_flows
--   * dispatch_policies
--   * agent_pools
--   * agent_pool_members
--
-- Safety model:
--   * Additive columns only.
--   * Existing rows receive version = 1.
--   * updated_by defaults to null so historical ownership is not fabricated.
--   * A shared BEFORE UPDATE trigger increments version and updates updated_at.
--   * This migration intentionally does not add application-level stale-write behavior or
--     Admin UI stale-write handling; those are Phase 2-6 responsibilities.

-- -----------------------------------------------------------------------------
-- Admin-owned configuration version metadata.
-- -----------------------------------------------------------------------------
ALTER TABLE source_systems
  ADD COLUMN IF NOT EXISTS version int NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS updated_by varchar(128);

ALTER TABLE dispatch_flows
  ADD COLUMN IF NOT EXISTS version int NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS updated_by varchar(128);

ALTER TABLE dispatch_policies
  ADD COLUMN IF NOT EXISTS updated_by varchar(128);

-- dispatch_policies already has a version column from the clean baseline.
ALTER TABLE dispatch_policies
  ALTER COLUMN version SET DEFAULT 1;

UPDATE dispatch_policies
   SET version = 1
 WHERE version IS NULL;

ALTER TABLE dispatch_policies
  ALTER COLUMN version SET NOT NULL;

ALTER TABLE agent_pools
  ADD COLUMN IF NOT EXISTS version int NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS updated_by varchar(128);

ALTER TABLE agent_pool_members
  ADD COLUMN IF NOT EXISTS version int NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS updated_by varchar(128);

-- -----------------------------------------------------------------------------
-- Version value-domain checks for optimistic locking columns.
-- -----------------------------------------------------------------------------
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_source_systems_version_positive') THEN
    ALTER TABLE source_systems
      ADD CONSTRAINT ck_source_systems_version_positive
      CHECK (version >= 1)
      NOT VALID;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_dispatch_flows_version_positive') THEN
    ALTER TABLE dispatch_flows
      ADD CONSTRAINT ck_dispatch_flows_version_positive
      CHECK (version >= 1)
      NOT VALID;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_dispatch_policies_version_positive') THEN
    ALTER TABLE dispatch_policies
      ADD CONSTRAINT ck_dispatch_policies_version_positive
      CHECK (version >= 1)
      NOT VALID;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_agent_pools_version_positive') THEN
    ALTER TABLE agent_pools
      ADD CONSTRAINT ck_agent_pools_version_positive
      CHECK (version >= 1)
      NOT VALID;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_agent_pool_members_version_positive') THEN
    ALTER TABLE agent_pool_members
      ADD CONSTRAINT ck_agent_pool_members_version_positive
      CHECK (version >= 1)
      NOT VALID;
  END IF;
END $$;

COMMENT ON CONSTRAINT ck_source_systems_version_positive ON source_systems IS
  'Phase 2-5: optimistic-locking version must be positive for Source System admin configuration.';
COMMENT ON CONSTRAINT ck_dispatch_flows_version_positive ON dispatch_flows IS
  'Phase 2-5: optimistic-locking version must be positive for Source Flow admin configuration.';
COMMENT ON CONSTRAINT ck_dispatch_policies_version_positive ON dispatch_policies IS
  'Phase 2-5: optimistic-locking version must be positive for Flow Rule admin configuration.';
COMMENT ON CONSTRAINT ck_agent_pools_version_positive ON agent_pools IS
  'Phase 2-5: optimistic-locking version must be positive for Agent Pool admin configuration.';
COMMENT ON CONSTRAINT ck_agent_pool_members_version_positive ON agent_pool_members IS
  'Phase 2-5: optimistic-locking version must be positive for Agent Pool member admin configuration.';

-- -----------------------------------------------------------------------------
-- Shared optimistic-locking touch trigger.
--
-- The trigger increments version on each UPDATE unless the caller already moved
-- version forward. Phase 2-6 API code should still enforce expected-version
-- semantics with WHERE version = :expectedVersion and translate zero updated rows
-- into the API stale-write response. The trigger alone does not detect stale clients; it supplies
-- the monotonically increasing value that Phase 2-6 will compare.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION dispatch_admin_optimistic_lock_touch()
RETURNS trigger AS $$
DECLARE
  resolved_updated_by varchar(128);
BEGIN
  NEW.updated_at := now();

  IF NEW.version IS NULL OR NEW.version <= OLD.version THEN
    NEW.version := OLD.version + 1;
  END IF;

  resolved_updated_by := NULLIF(current_setting('app.updated_by', true), '');
  IF resolved_updated_by IS NOT NULL THEN
    NEW.updated_by := resolved_updated_by;
  ELSIF NEW.updated_by IS NULL THEN
    NEW.updated_by := OLD.updated_by;
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_source_systems_optimistic_lock_touch ON source_systems;
CREATE TRIGGER trg_source_systems_optimistic_lock_touch
BEFORE UPDATE ON source_systems
FOR EACH ROW
EXECUTE FUNCTION dispatch_admin_optimistic_lock_touch();

DROP TRIGGER IF EXISTS trg_dispatch_flows_optimistic_lock_touch ON dispatch_flows;
CREATE TRIGGER trg_dispatch_flows_optimistic_lock_touch
BEFORE UPDATE ON dispatch_flows
FOR EACH ROW
EXECUTE FUNCTION dispatch_admin_optimistic_lock_touch();

DROP TRIGGER IF EXISTS trg_dispatch_policies_optimistic_lock_touch ON dispatch_policies;
CREATE TRIGGER trg_dispatch_policies_optimistic_lock_touch
BEFORE UPDATE ON dispatch_policies
FOR EACH ROW
EXECUTE FUNCTION dispatch_admin_optimistic_lock_touch();

DROP TRIGGER IF EXISTS trg_agent_pools_optimistic_lock_touch ON agent_pools;
CREATE TRIGGER trg_agent_pools_optimistic_lock_touch
BEFORE UPDATE ON agent_pools
FOR EACH ROW
EXECUTE FUNCTION dispatch_admin_optimistic_lock_touch();

DROP TRIGGER IF EXISTS trg_agent_pool_members_optimistic_lock_touch ON agent_pool_members;
CREATE TRIGGER trg_agent_pool_members_optimistic_lock_touch
BEFORE UPDATE ON agent_pool_members
FOR EACH ROW
EXECUTE FUNCTION dispatch_admin_optimistic_lock_touch();

COMMENT ON FUNCTION dispatch_admin_optimistic_lock_touch() IS
  'Phase 2-5: shared touch trigger for Admin-owned Current dispatch configuration. Supplies monotonically increasing version values for Phase 2-6 API/UI conflict handling.';

COMMENT ON COLUMN source_systems.version IS 'Phase 2-5 optimistic-locking version for Admin-owned Source System configuration.';
COMMENT ON COLUMN source_systems.updated_by IS 'Phase 2-5 last Admin/API principal that updated this Source System when provided through app.updated_by.';
COMMENT ON COLUMN dispatch_flows.version IS 'Phase 2-5 optimistic-locking version for Admin-owned Source Flow configuration.';
COMMENT ON COLUMN dispatch_flows.updated_by IS 'Phase 2-5 last Admin/API principal that updated this Source Flow when provided through app.updated_by.';
COMMENT ON COLUMN dispatch_policies.version IS 'Phase 2-5 optimistic-locking version for Admin-owned Flow Rule configuration.';
COMMENT ON COLUMN dispatch_policies.updated_by IS 'Phase 2-5 last Admin/API principal that updated this Flow Rule when provided through app.updated_by.';
COMMENT ON COLUMN agent_pools.version IS 'Phase 2-5 optimistic-locking version for Admin-owned Agent Pool configuration.';
COMMENT ON COLUMN agent_pools.updated_by IS 'Phase 2-5 last Admin/API principal that updated this Agent Pool when provided through app.updated_by.';
COMMENT ON COLUMN agent_pool_members.version IS 'Phase 2-5 optimistic-locking version for Admin-owned Agent Pool member configuration.';
COMMENT ON COLUMN agent_pool_members.updated_by IS 'Phase 2-5 last Admin/API principal that updated this Agent Pool member when provided through app.updated_by.';
