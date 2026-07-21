-- Phase 2-3 Dispatch Composite Foreign Key Migration
-- Migration file: V2__dispatch_referential_integrity.sql
--
-- Purpose:
--   Add tenant-aware referential integrity for the Current dispatch path:
--   Source System -> Source Flow -> Dispatch Rule -> Agent Pool -> Pool Member
--   and the active Task -> Assignment -> Dispatch Request lifecycle.
--
-- Safety model:
--   * All foreign keys below are composite where the referenced table is tenant-scoped.
--   * Historical evidence/reference-only rows are intentionally not constrained here.
--   * Foreign keys are added NOT VALID to enforce new writes while keeping rollout safer.
--   * Existing data must be checked with scripts/db/phase2-1-dispatch-integrity-report.sql
--     and scripts/db/phase2-2-dispatch-integrity-repair-dry-run.sql before this migration.

-- -----------------------------------------------------------------------------
-- Preflight: block only the defects that would make this FK migration unsafe.
-- -----------------------------------------------------------------------------
DO $$
BEGIN
  IF EXISTS (
    SELECT 1
      FROM dispatch_flows f
      LEFT JOIN source_systems s
        ON s.tenant_id = f.tenant_id
       AND s.source_system_id = f.source_system
     WHERE s.source_system_id IS NULL
  ) THEN
    RAISE EXCEPTION 'Phase 2-3 blocked: dispatch_flows.source_system has orphan references. Run phase2-1/phase2-2 integrity reports first.';
  END IF;

  IF EXISTS (
    SELECT 1
      FROM dispatch_flows f
      LEFT JOIN agent_pools p
        ON p.tenant_id = f.tenant_id
       AND p.pool_id = f.default_pool_id
     WHERE f.default_pool_id IS NOT NULL
       AND p.pool_id IS NULL
  ) THEN
    RAISE EXCEPTION 'Phase 2-3 blocked: dispatch_flows.default_pool_id has orphan references. Run phase2-1/phase2-2 integrity reports first.';
  END IF;

  IF EXISTS (
    SELECT 1
      FROM dispatch_policies r
      LEFT JOIN dispatch_flows f
        ON f.tenant_id = r.tenant_id
       AND f.flow_id = r.flow_id
     WHERE f.flow_id IS NULL
  ) THEN
    RAISE EXCEPTION 'Phase 2-3 blocked: dispatch_policies.flow_id has orphan references. Run phase2-1/phase2-2 integrity reports first.';
  END IF;

  IF EXISTS (
    SELECT 1
      FROM dispatch_policies r
      LEFT JOIN source_systems s
        ON s.tenant_id = r.tenant_id
       AND s.source_system_id = r.source_system
     WHERE s.source_system_id IS NULL
  ) THEN
    RAISE EXCEPTION 'Phase 2-3 blocked: dispatch_policies.source_system has orphan references. Run phase2-1/phase2-2 integrity reports first.';
  END IF;

  IF EXISTS (
    SELECT 1
      FROM dispatch_policies r
      LEFT JOIN agent_pools p
        ON p.tenant_id = r.tenant_id
       AND p.pool_id = r.target_pool_id
     WHERE r.target_pool_id IS NOT NULL
       AND p.pool_id IS NULL
  ) THEN
    RAISE EXCEPTION 'Phase 2-3 blocked: dispatch_policies.target_pool_id has orphan references. Run phase2-1/phase2-2 integrity reports first.';
  END IF;

  IF EXISTS (
    SELECT 1
      FROM agent_pool_members m
      LEFT JOIN agent_pools p
        ON p.tenant_id = m.tenant_id
       AND p.pool_id = m.pool_id
     WHERE p.pool_id IS NULL
  ) THEN
    RAISE EXCEPTION 'Phase 2-3 blocked: agent_pool_members.pool_id has orphan references. Run phase2-1/phase2-2 integrity reports first.';
  END IF;

  IF EXISTS (
    SELECT 1
      FROM agent_pool_members m
      LEFT JOIN agent_profiles ap
        ON ap.tenant_id = m.tenant_id
       AND ap.agent_id = m.agent_id
     WHERE ap.agent_id IS NULL
  ) THEN
    RAISE EXCEPTION 'Phase 2-3 blocked: agent_pool_members.agent_id has orphan Agent profile references. Run phase2-1/phase2-2 integrity reports first.';
  END IF;

  IF EXISTS (
    SELECT 1
      FROM task_assignments ta
      LEFT JOIN tasks t
        ON t.task_id = ta.task_id
     WHERE t.task_id IS NULL
        OR t.tenant_id IS NULL
  ) THEN
    RAISE EXCEPTION 'Phase 2-3 blocked: task_assignments.task_id cannot be tenant-bound. Repair missing task or null task tenant first.';
  END IF;

  IF EXISTS (
    SELECT 1
      FROM dispatch_requests dr
      LEFT JOIN task_assignments ta
        ON ta.assignment_id = dr.assignment_id
     WHERE dr.assignment_id IS NOT NULL
       AND ta.assignment_id IS NULL
  ) THEN
    RAISE EXCEPTION 'Phase 2-3 blocked: dispatch_requests.assignment_id has orphan references. Run phase2-1/phase2-2 integrity reports first.';
  END IF;

  IF EXISTS (
    SELECT 1
      FROM dispatch_requests dr
      LEFT JOIN tasks t
        ON t.task_id = dr.task_id
     WHERE dr.task_id IS NOT NULL
       AND t.task_id IS NULL
  ) THEN
    RAISE EXCEPTION 'Phase 2-3 blocked: dispatch_requests.task_id has orphan references. Run phase2-1/phase2-2 integrity reports first.';
  END IF;
END $$;

-- -----------------------------------------------------------------------------
-- Tenant-aware lifecycle support columns.
-- task_assignments and dispatch_requests historically used globally unique ids.
-- Add tenant_id so lifecycle FKs can remain tenant-aware.
-- -----------------------------------------------------------------------------
ALTER TABLE task_assignments
  ADD COLUMN IF NOT EXISTS tenant_id varchar(64);

ALTER TABLE dispatch_requests
  ADD COLUMN IF NOT EXISTS tenant_id varchar(64);

UPDATE task_assignments ta
   SET tenant_id = t.tenant_id
  FROM tasks t
 WHERE t.task_id = ta.task_id
   AND ta.tenant_id IS NULL;

UPDATE dispatch_requests dr
   SET tenant_id = t.tenant_id
  FROM tasks t
 WHERE t.task_id = dr.task_id
   AND dr.tenant_id IS NULL;

UPDATE dispatch_requests dr
   SET tenant_id = ta.tenant_id
  FROM task_assignments ta
 WHERE ta.assignment_id = dr.assignment_id
   AND dr.tenant_id IS NULL;

ALTER TABLE task_assignments
  ALTER COLUMN tenant_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_task_assignments_tenant_task
  ON task_assignments(tenant_id, task_id, status, created_at desc);

CREATE INDEX IF NOT EXISTS idx_dispatch_requests_tenant_task
  ON dispatch_requests(tenant_id, task_id, created_at desc);

CREATE INDEX IF NOT EXISTS idx_dispatch_requests_tenant_assignment
  ON dispatch_requests(tenant_id, assignment_id, created_at desc);

-- -----------------------------------------------------------------------------
-- Composite unique constraints required by tenant-aware foreign keys.
-- -----------------------------------------------------------------------------
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_agent_profiles_tenant_agent') THEN
    ALTER TABLE agent_profiles
      ADD CONSTRAINT uq_agent_profiles_tenant_agent UNIQUE (tenant_id, agent_id);
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_dispatch_policies_tenant_policy') THEN
    ALTER TABLE dispatch_policies
      ADD CONSTRAINT uq_dispatch_policies_tenant_policy UNIQUE (tenant_id, policy_id);
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_tasks_tenant_task') THEN
    ALTER TABLE tasks
      ADD CONSTRAINT uq_tasks_tenant_task UNIQUE (tenant_id, task_id);
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_task_assignments_tenant_assignment') THEN
    ALTER TABLE task_assignments
      ADD CONSTRAINT uq_task_assignments_tenant_assignment UNIQUE (tenant_id, assignment_id);
  END IF;
END $$;

-- -----------------------------------------------------------------------------
-- Tenant propagation triggers for lifecycle rows inserted by older callers.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION set_task_assignment_tenant_id()
RETURNS trigger AS $$
DECLARE
  resolved_tenant_id varchar(64);
BEGIN
  IF NEW.tenant_id IS NULL THEN
    SELECT t.tenant_id
      INTO resolved_tenant_id
      FROM tasks t
     WHERE t.task_id = NEW.task_id;

    IF resolved_tenant_id IS NULL THEN
      RAISE EXCEPTION 'Cannot resolve tenant_id for task_assignments.task_id=%', NEW.task_id;
    END IF;

    NEW.tenant_id := resolved_tenant_id;
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_task_assignments_set_tenant_id ON task_assignments;
CREATE TRIGGER trg_task_assignments_set_tenant_id
BEFORE INSERT OR UPDATE OF task_id, tenant_id ON task_assignments
FOR EACH ROW
EXECUTE FUNCTION set_task_assignment_tenant_id();

CREATE OR REPLACE FUNCTION set_dispatch_request_tenant_id()
RETURNS trigger AS $$
DECLARE
  resolved_tenant_id varchar(64);
BEGIN
  IF NEW.tenant_id IS NULL AND NEW.task_id IS NOT NULL THEN
    SELECT t.tenant_id
      INTO resolved_tenant_id
      FROM tasks t
     WHERE t.task_id = NEW.task_id;
  END IF;

  IF NEW.tenant_id IS NULL AND resolved_tenant_id IS NULL AND NEW.assignment_id IS NOT NULL THEN
    SELECT ta.tenant_id
      INTO resolved_tenant_id
      FROM task_assignments ta
     WHERE ta.assignment_id = NEW.assignment_id;
  END IF;

  IF NEW.tenant_id IS NULL AND resolved_tenant_id IS NOT NULL THEN
    NEW.tenant_id := resolved_tenant_id;
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_dispatch_requests_set_tenant_id ON dispatch_requests;
CREATE TRIGGER trg_dispatch_requests_set_tenant_id
BEFORE INSERT OR UPDATE OF task_id, assignment_id, tenant_id ON dispatch_requests
FOR EACH ROW
EXECUTE FUNCTION set_dispatch_request_tenant_id();

-- -----------------------------------------------------------------------------
-- Current configuration path foreign keys.
-- -----------------------------------------------------------------------------
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_dispatch_flows_source_system') THEN
    ALTER TABLE dispatch_flows
      ADD CONSTRAINT fk_dispatch_flows_source_system
      FOREIGN KEY (tenant_id, source_system)
      REFERENCES source_systems(tenant_id, source_system_id)
      ON UPDATE RESTRICT ON DELETE RESTRICT
      NOT VALID;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_dispatch_flows_default_pool') THEN
    ALTER TABLE dispatch_flows
      ADD CONSTRAINT fk_dispatch_flows_default_pool
      FOREIGN KEY (tenant_id, default_pool_id)
      REFERENCES agent_pools(tenant_id, pool_id)
      ON UPDATE RESTRICT ON DELETE RESTRICT
      NOT VALID;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_dispatch_policies_flow') THEN
    ALTER TABLE dispatch_policies
      ADD CONSTRAINT fk_dispatch_policies_flow
      FOREIGN KEY (tenant_id, flow_id)
      REFERENCES dispatch_flows(tenant_id, flow_id)
      ON UPDATE RESTRICT ON DELETE RESTRICT
      NOT VALID;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_dispatch_policies_source_system') THEN
    ALTER TABLE dispatch_policies
      ADD CONSTRAINT fk_dispatch_policies_source_system
      FOREIGN KEY (tenant_id, source_system)
      REFERENCES source_systems(tenant_id, source_system_id)
      ON UPDATE RESTRICT ON DELETE RESTRICT
      NOT VALID;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_dispatch_policies_target_pool') THEN
    ALTER TABLE dispatch_policies
      ADD CONSTRAINT fk_dispatch_policies_target_pool
      FOREIGN KEY (tenant_id, target_pool_id)
      REFERENCES agent_pools(tenant_id, pool_id)
      ON UPDATE RESTRICT ON DELETE RESTRICT
      NOT VALID;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_agent_pool_members_pool') THEN
    ALTER TABLE agent_pool_members
      ADD CONSTRAINT fk_agent_pool_members_pool
      FOREIGN KEY (tenant_id, pool_id)
      REFERENCES agent_pools(tenant_id, pool_id)
      ON UPDATE RESTRICT ON DELETE RESTRICT
      NOT VALID;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_agent_pool_members_agent_profile') THEN
    ALTER TABLE agent_pool_members
      ADD CONSTRAINT fk_agent_pool_members_agent_profile
      FOREIGN KEY (tenant_id, agent_id)
      REFERENCES agent_profiles(tenant_id, agent_id)
      ON UPDATE RESTRICT ON DELETE RESTRICT
      NOT VALID;
  END IF;
END $$;

-- -----------------------------------------------------------------------------
-- Active lifecycle foreign keys.
-- These are tenant-aware after adding task_assignments.tenant_id and
-- dispatch_requests.tenant_id above.
-- -----------------------------------------------------------------------------
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_task_assignments_task') THEN
    ALTER TABLE task_assignments
      ADD CONSTRAINT fk_task_assignments_task
      FOREIGN KEY (tenant_id, task_id)
      REFERENCES tasks(tenant_id, task_id)
      ON UPDATE RESTRICT ON DELETE RESTRICT
      NOT VALID;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_dispatch_requests_assignment') THEN
    ALTER TABLE dispatch_requests
      ADD CONSTRAINT fk_dispatch_requests_assignment
      FOREIGN KEY (tenant_id, assignment_id)
      REFERENCES task_assignments(tenant_id, assignment_id)
      ON UPDATE RESTRICT ON DELETE RESTRICT
      NOT VALID;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_dispatch_requests_task') THEN
    ALTER TABLE dispatch_requests
      ADD CONSTRAINT fk_dispatch_requests_task
      FOREIGN KEY (tenant_id, task_id)
      REFERENCES tasks(tenant_id, task_id)
      ON UPDATE RESTRICT ON DELETE RESTRICT
      NOT VALID;
  END IF;
END $$;
