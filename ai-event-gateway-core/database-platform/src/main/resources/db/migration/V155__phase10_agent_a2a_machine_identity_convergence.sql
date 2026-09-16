-- Phase 10: Agent / A2A canonical machine identity convergence.
-- Agent runtime principals become first-class MachinePrincipalType.AGENT / A2A_AGENT identities.
-- A2A delegation evidence is append-on-create and current Agent governance changes advance resource epochs.

-- Flyway executes each versioned migration in its own transaction. V152's transaction-local
-- INSTANCE context does not survive into V155, while permission_entry_point_inventory is
-- FORCE RLS protected. Establish the canonical migration context for this migration itself.
select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','phase10-agent-a2a-machine-identity',true);

ALTER TABLE a2a_requests ADD COLUMN IF NOT EXISTS origin_principal_type varchar(32);
ALTER TABLE a2a_requests ADD COLUMN IF NOT EXISTS origin_principal_id varchar(128);
ALTER TABLE a2a_requests ADD COLUMN IF NOT EXISTS delegating_principal_type varchar(32);
ALTER TABLE a2a_requests ADD COLUMN IF NOT EXISTS delegating_principal_id varchar(128);
ALTER TABLE a2a_requests ADD COLUMN IF NOT EXISTS executing_principal_type varchar(32);
ALTER TABLE a2a_requests ADD COLUMN IF NOT EXISTS executing_principal_id varchar(128);
ALTER TABLE a2a_requests ADD COLUMN IF NOT EXISTS delegation_depth integer NOT NULL DEFAULT 0;

DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='ck_a2a_machine_delegation_depth') THEN
    ALTER TABLE a2a_requests ADD CONSTRAINT ck_a2a_machine_delegation_depth
      CHECK (delegation_depth BETWEEN 0 AND 16);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='ck_a2a_machine_delegation_evidence') THEN
    ALTER TABLE a2a_requests ADD CONSTRAINT ck_a2a_machine_delegation_evidence CHECK (
      (origin_principal_type IS NULL AND origin_principal_id IS NULL
       AND delegating_principal_type IS NULL AND delegating_principal_id IS NULL
       AND executing_principal_type IS NULL AND executing_principal_id IS NULL
       AND delegation_depth=0)
      OR
      (origin_principal_type IS NOT NULL AND origin_principal_id IS NOT NULL
       AND delegating_principal_type IS NOT NULL AND delegating_principal_id IS NOT NULL
       AND executing_principal_type IS NOT NULL AND executing_principal_id IS NOT NULL)
    );
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS ix_a2a_requests_executing_machine
  ON a2a_requests(tenant_id,executing_principal_type,executing_principal_id,created_at DESC)
  WHERE executing_principal_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_a2a_requests_origin_machine
  ON a2a_requests(tenant_id,origin_principal_type,origin_principal_id,created_at DESC)
  WHERE origin_principal_id IS NOT NULL;

-- Agent policy / credential / lifecycle changes already increment agent_profiles.policy_version.
-- Advance the canonical resource epochs for non-ownership governance mutations; RS3 retains its
-- dedicated ownership-change trigger, avoiding a duplicate epoch bump for owner re-scope updates.
CREATE OR REPLACE FUNCTION phase10_touch_agent_machine_epoch()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE actor varchar;
BEGIN
  IF OLD.policy_version IS NOT DISTINCT FROM NEW.policy_version THEN RETURN NEW; END IF;
  actor=coalesce(nullif(current_setting('app.current_actor_id',true),''),'phase10-agent-machine-identity');
  perform p4ra_advance_policy_revision(NEW.tenant_id,actor);
  INSERT INTO resource_security_epochs(tenant_id,resource_type,resource_id,resource_security_epoch,updated_at,updated_by)
  SELECT NEW.tenant_id,t,NEW.agent_id,1,now(),actor
    FROM unnest(array['AGENT','AGENT_SERVICE_SCOPE','AGENT_CREDENTIAL_METADATA']::varchar[]) t
  ON CONFLICT(tenant_id,resource_type,resource_id) DO UPDATE
    SET resource_security_epoch=resource_security_epochs.resource_security_epoch+1,
        updated_at=now(),updated_by=actor;
  RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS trg_phase10_agent_machine_epoch ON agent_profiles;
CREATE TRIGGER trg_phase10_agent_machine_epoch
AFTER UPDATE OF policy_version ON agent_profiles
FOR EACH ROW
WHEN (OLD.policy_version IS DISTINCT FROM NEW.policy_version
      AND OLD.owner_department_id IS NOT DISTINCT FROM NEW.owner_department_id
      AND OLD.owner_group_id IS NOT DISTINCT FROM NEW.owner_group_id)
EXECUTE FUNCTION phase10_touch_agent_machine_epoch();

-- Read-only evidence projection for audit/forensics. Existing Human/legacy rows remain valid and
-- simply have no machine delegation evidence.
CREATE OR REPLACE VIEW a2a_machine_delegation_evidence AS
SELECT tenant_id,a2a_request_id,requester_type,requesting_agent_id,
       origin_principal_type,origin_principal_id,
       delegating_principal_type,delegating_principal_id,
       executing_principal_type,executing_principal_id,
       delegation_depth,correlation_id,created_at
  FROM a2a_requests
 WHERE executing_principal_id IS NOT NULL;

INSERT INTO reason_code_catalog(reason_code,http_status,category,retryable,message_template) VALUES
 ('A2A_MACHINE_IDENTITY_REQUIRED',401,'AUTHENTICATION',false,'Authenticated Agent machine identity is required for this A2A request.'),
 ('A2A_MACHINE_TENANT_MISMATCH',403,'AUTHORIZATION',false,'Authenticated Agent Tenant does not match the A2A request Tenant.'),
 ('A2A_AGENT_IDENTITY_MISMATCH',403,'AUTHORIZATION',false,'Authenticated Agent identity does not match the claimed A2A requester.'),
 ('A2A_SOURCE_AGENT_NOT_ALLOWED',403,'AUTHORIZATION',false,'The selected directional A2A Policy does not allow this source Agent.'),
 ('A2A_DELEGATION_CHAIN_INVALID',403,'AUTHORIZATION',false,'Machine delegation evidence is invalid or crosses a Tenant boundary.')
ON CONFLICT(reason_code) DO UPDATE SET
 http_status=excluded.http_status,category=excluded.category,retryable=excluded.retryable,
 message_template=excluded.message_template,active=true,version=reason_code_catalog.version+1;

-- Refresh R3 entry-point evidence after the A2A controller began deriving Agent identity from
-- canonical MachineExecutionContext instead of accepting requester identity from the request body.
UPDATE permission_entry_point_inventory
SET source_hash='ac2112a5823badb6fc303692e92c90914b04b10bf9d57d281fb1478b1509a51a',
    manifest_revision='phase10-agent-a2a-machine-identity-2026-08-13',
    last_verified_at=now(),updated_at=now(),updated_by='phase10-agent-a2a-machine-identity',version=version+1
WHERE source_ref LIKE 'ai-event-gateway-core/a2a-api/src/main/java/com/opensocket/aievent/core/a2a/api/A2AGovernanceController.java#%';
