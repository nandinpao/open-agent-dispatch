-- V41-C0: False-MIGRATED authority correction for Adapter Action Worker + MCP.
--
-- V248/V249 advanced nine keys to MIGRATED while their runtime consumers intentionally retained
-- startup YAML/ENV fallback. Under the V40-9D-HF1 single-effective-authority definition,
-- MIGRATED forbids fallback and therefore those rows are false-MIGRATED governance evidence.
--
-- This forward migration does not rewrite V248/V249 history. It installs a transaction-local,
-- exact-key reopen exception, moves exactly nine rows back to MIGRATION_READY, records audit events,
-- asserts the postcondition, clears the token, and restores the strict forward-only V247 guard.

create or replace function guard_runtime_config_inventory_governance_transition() returns trigger language plpgsql as $$
begin
  if new.source_observation_hash is distinct from (select source_observation_hash from runtime_config_inventory_observations where configuration_key=new.configuration_key) then
    raise exception 'SOURCE_OBSERVATION_DRIFT';
  end if;

  if old.status='MIGRATED'
     and new.status='MIGRATION_READY'
     and new.configuration_key in (
       'adapter-actions.worker.retry-enabled',
       'adapter-actions.worker.max-attempts',
       'adapter-actions.worker.initial-backoff',
       'adapter-actions.worker.max-backoff',
       'adapter-actions.worker.expired-lease-scan-batch-size',
       'adapter-actions.mcp.enabled',
       'adapter-actions.mcp.run-on-completed-task',
       'adapter-actions.mcp.run-on-failed-task',
       'adapter-actions.mcp.one-per-task'
     )
     and current_setting('app.runtime_config_governance_reopen_stage',true)='V41_C0_FALSE_MIGRATED_WORKER_MCP_CORRECTION' then
    new.updated_at=now();
    return new;
  end if;

  if old.status='DISCOVERED' and new.status not in ('DISCOVERED','CLASSIFIED') then raise exception 'CONFIGURATION_GOVERNANCE_TRANSITION_DENIED'; end if;
  if old.status='CLASSIFIED' and new.status not in ('CLASSIFIED','OWNER_REVIEWED') then raise exception 'CONFIGURATION_GOVERNANCE_TRANSITION_DENIED'; end if;
  if old.status='OWNER_REVIEWED' and new.status not in ('OWNER_REVIEWED','ARCHITECTURE_APPROVED') then raise exception 'CONFIGURATION_GOVERNANCE_TRANSITION_DENIED'; end if;
  if old.status='ARCHITECTURE_APPROVED' and new.status not in ('ARCHITECTURE_APPROVED','MIGRATION_READY') then raise exception 'CONFIGURATION_GOVERNANCE_TRANSITION_DENIED'; end if;
  if old.status='MIGRATION_READY' and new.status not in ('MIGRATION_READY','MIGRATED') then raise exception 'CONFIGURATION_GOVERNANCE_TRANSITION_DENIED'; end if;
  if old.status='MIGRATED' and new.status not in ('MIGRATED','LEGACY_RETIRED') then raise exception 'CONFIGURATION_GOVERNANCE_TRANSITION_DENIED'; end if;
  if old.status='LEGACY_RETIRED' and new.status<>'LEGACY_RETIRED' then raise exception 'CONFIGURATION_GOVERNANCE_TRANSITION_DENIED'; end if;
  new.updated_at=now(); return new;
end $$;

select set_config('app.runtime_config_governance_reopen_stage','V41_C0_FALSE_MIGRATED_WORKER_MCP_CORRECTION',true);

with reopened as (
  update runtime_config_inventory_governance
     set status='MIGRATION_READY',
         reason='V41-C0 reopened false-MIGRATED authority: Worker/MCP consumers still permit startup YAML/ENV fallback and therefore have not reached single-effective-authority cutover',
         version=version+1,
         updated_at=now()
   where configuration_key in (
     'adapter-actions.worker.retry-enabled',
     'adapter-actions.worker.max-attempts',
     'adapter-actions.worker.initial-backoff',
     'adapter-actions.worker.max-backoff',
     'adapter-actions.worker.expired-lease-scan-batch-size',
     'adapter-actions.mcp.enabled',
     'adapter-actions.mcp.run-on-completed-task',
     'adapter-actions.mcp.run-on-failed-task',
     'adapter-actions.mcp.one-per-task'
   )
     and status='MIGRATED'
  returning configuration_key,source_observation_hash
)
insert into runtime_config_inventory_governance_events(
  configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'CUTOVER_REOPENED','MIGRATED','MIGRATION_READY','v41-c0-authority-correction',
       'V41-C0 reopened false-MIGRATED authority: Worker/MCP consumers still permit startup YAML/ENV fallback and therefore have not reached single-effective-authority cutover',
       source_observation_hash,
       '{"stage":"V41_C0_FALSE_MIGRATED_WORKER_MCP_CORRECTION","reason":"startup-fallback-still-authoritative","legacyAuthorityRetired":false}'::jsonb
  from reopened;

-- Fresh V1..V252 migration must observe exactly the nine V248/V249 rows reopened by this stage.
-- A non-nine result indicates drift in historical migration semantics and fails closed.
do $$
declare
  v_reopened integer;
begin
  select count(*) into v_reopened
    from runtime_config_inventory_governance
   where configuration_key in (
     'adapter-actions.worker.retry-enabled',
     'adapter-actions.worker.max-attempts',
     'adapter-actions.worker.initial-backoff',
     'adapter-actions.worker.max-backoff',
     'adapter-actions.worker.expired-lease-scan-batch-size',
     'adapter-actions.mcp.enabled',
     'adapter-actions.mcp.run-on-completed-task',
     'adapter-actions.mcp.run-on-failed-task',
     'adapter-actions.mcp.one-per-task'
   )
     and status='MIGRATION_READY'
     and reason='V41-C0 reopened false-MIGRATED authority: Worker/MCP consumers still permit startup YAML/ENV fallback and therefore have not reached single-effective-authority cutover';
  if v_reopened<>9 then
    raise exception 'V41_C0_FALSE_MIGRATED_REOPEN_INCOMPLETE: expected=9 actual=%',v_reopened;
  end if;
end $$;

select set_config('app.runtime_config_governance_reopen_stage','',true);

-- Restore the strict forward-only lifecycle guard. V41-C0 introduces no permanent application
-- path from MIGRATED back to MIGRATION_READY.
create or replace function guard_runtime_config_inventory_governance_transition() returns trigger language plpgsql as $$
begin
  if new.source_observation_hash is distinct from (select source_observation_hash from runtime_config_inventory_observations where configuration_key=new.configuration_key) then
    raise exception 'SOURCE_OBSERVATION_DRIFT';
  end if;
  if old.status='DISCOVERED' and new.status not in ('DISCOVERED','CLASSIFIED') then raise exception 'CONFIGURATION_GOVERNANCE_TRANSITION_DENIED'; end if;
  if old.status='CLASSIFIED' and new.status not in ('CLASSIFIED','OWNER_REVIEWED') then raise exception 'CONFIGURATION_GOVERNANCE_TRANSITION_DENIED'; end if;
  if old.status='OWNER_REVIEWED' and new.status not in ('OWNER_REVIEWED','ARCHITECTURE_APPROVED') then raise exception 'CONFIGURATION_GOVERNANCE_TRANSITION_DENIED'; end if;
  if old.status='ARCHITECTURE_APPROVED' and new.status not in ('ARCHITECTURE_APPROVED','MIGRATION_READY') then raise exception 'CONFIGURATION_GOVERNANCE_TRANSITION_DENIED'; end if;
  if old.status='MIGRATION_READY' and new.status not in ('MIGRATION_READY','MIGRATED') then raise exception 'CONFIGURATION_GOVERNANCE_TRANSITION_DENIED'; end if;
  if old.status='MIGRATED' and new.status not in ('MIGRATED','LEGACY_RETIRED') then raise exception 'CONFIGURATION_GOVERNANCE_TRANSITION_DENIED'; end if;
  if old.status='LEGACY_RETIRED' and new.status<>'LEGACY_RETIRED' then raise exception 'CONFIGURATION_GOVERNANCE_TRANSITION_DENIED'; end if;
  new.updated_at=now(); return new;
end $$;
