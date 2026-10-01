-- V40-9D-HF1: Migration Cutover & Single Effective Authority Closure.
-- Corrects Batch 3 governance semantics without rewriting V250 history.
-- MIGRATION_READY is the legal dual-read observation/cutover state. MIGRATED is allowed only
-- after a complete active Runtime Configuration revision exists. Required-node convergence is
-- additionally enforced by RuntimeConfigurationMigrationCutoverService before finalization.

-- V250 advanced these keys to MIGRATED as source evidence before a baseline revision was
-- necessarily materialized. V247 intentionally forbids normal backward lifecycle transitions,
-- therefore this migration uses a transaction-local, exact-key reopen token. The exception exists
-- only while V251 repairs the three V250 false-MIGRATED rows; the strict V247 guard is restored
-- immediately afterwards so application/runtime callers retain forward-only lifecycle semantics.
create or replace function guard_runtime_config_inventory_governance_transition() returns trigger language plpgsql as $$
begin
  if new.source_observation_hash is distinct from (select source_observation_hash from runtime_config_inventory_observations where configuration_key=new.configuration_key) then
    raise exception 'SOURCE_OBSERVATION_DRIFT';
  end if;

  if old.status='MIGRATED'
     and new.status='MIGRATION_READY'
     and new.configuration_key in (
       'adapter-executor.circuit-breaker.enabled',
       'adapter-executor.circuit-breaker.failure-threshold',
       'adapter-executor.circuit-breaker.open-duration'
     )
     and current_setting('app.runtime_config_governance_reopen_stage',true)='V40_9D_HF1_MIGRATION_CUTOVER_CLOSURE' then
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

select set_config('app.runtime_config_governance_reopen_stage','V40_9D_HF1_MIGRATION_CUTOVER_CLOSURE',true);

with reopened as (
  update runtime_config_inventory_governance
     set status='MIGRATION_READY',
         reason='V40-9D-HF1 reopened cutover: MIGRATED requires a complete active revision and applied-node evidence',
         version=version+1,
         updated_at=now()
   where configuration_key in (
     'adapter-executor.circuit-breaker.enabled',
     'adapter-executor.circuit-breaker.failure-threshold',
     'adapter-executor.circuit-breaker.open-duration'
   )
     and status='MIGRATED'
  returning configuration_key,source_observation_hash
)
insert into runtime_config_inventory_governance_events(
  configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'CUTOVER_REOPENED','MIGRATED','MIGRATION_READY','v40-9d-hf1-cutover-closure',
       'V40-9D-HF1 reopened cutover: MIGRATED requires a complete active revision and applied-node evidence',
       source_observation_hash,
       '{"stage":"V40_9D_HF1_MIGRATION_CUTOVER_CLOSURE","reason":"single-effective-authority"}'::jsonb
  from reopened;

-- Fail deterministically if the preceding V250 state is not exactly the state V251 was designed
-- to repair. This prevents a partial or unexpected lifecycle rewrite from being accepted silently.
do $$
declare
  v_reopened integer;
begin
  select count(*) into v_reopened
    from runtime_config_inventory_governance
   where configuration_key in (
     'adapter-executor.circuit-breaker.enabled',
     'adapter-executor.circuit-breaker.failure-threshold',
     'adapter-executor.circuit-breaker.open-duration'
   )
     and status='MIGRATION_READY'
     and reason='V40-9D-HF1 reopened cutover: MIGRATED requires a complete active revision and applied-node evidence';
  if v_reopened<>3 then
    raise exception 'CONFIGURATION_CUTOVER_REOPEN_INCOMPLETE: expected=3 actual=%',v_reopened;
  end if;
end $$;

select set_config('app.runtime_config_governance_reopen_stage','',true);

-- Restore the strict V247 forward-only lifecycle guard. No permanent MIGRATED -> MIGRATION_READY
-- application transition is introduced by this historical correction migration.
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


-- Database invariant: no migration-governance row may enter MIGRATED unless every Config Set for
-- that definition's set key has an active revision containing the definition value. This prevents
-- direct SQL/API state advancement from recreating the V250 false-MIGRATED condition.
create or replace function guard_runtime_config_migrated_requires_active_value() returns trigger language plpgsql as $$
declare
  v_set_key varchar(255);
  v_set_count integer;
  v_complete_count integer;
begin
  if new.status='MIGRATED' and old.status is distinct from 'MIGRATED' then
    select config_set_key into v_set_key
      from runtime_config_definitions
     where definition_key=new.configuration_key
       and migration_authorized=true;

    if v_set_key is null then
      raise exception 'CONFIGURATION_CUTOVER_DEFINITION_REQUIRED: key=%',new.configuration_key;
    end if;

    select count(*) into v_set_count
      from runtime_config_sets s
     where s.set_key=v_set_key and s.status='ACTIVE';

    if v_set_count=0 then
      raise exception 'CONFIGURATION_CUTOVER_CONFIG_SET_REQUIRED: key=% setKey=%',new.configuration_key,v_set_key;
    end if;

    select count(*) into v_complete_count
      from runtime_config_sets s
      join runtime_config_active_revisions a on a.config_set_id=s.config_set_id
      join runtime_config_revision_items i on i.revision_id=a.revision_id and i.definition_key=new.configuration_key
     where s.set_key=v_set_key and s.status='ACTIVE';

    if v_complete_count<>v_set_count then
      raise exception 'CONFIGURATION_CUTOVER_BASELINE_REQUIRED: key=% setKey=% activeSets=% completeSets=%',
        new.configuration_key,v_set_key,v_set_count,v_complete_count;
    end if;
  end if;
  return new;
end $$;

comment on function guard_runtime_config_migrated_requires_active_value() is
 'V40-9D-HF1 single-authority gate: MIGRATED requires every active Config Set to point at a revision containing the migrated key.';

drop trigger if exists trg_runtime_config_migrated_requires_active_value on runtime_config_inventory_governance;
create trigger trg_runtime_config_migrated_requires_active_value
  before update of status on runtime_config_inventory_governance
  for each row execute function guard_runtime_config_migrated_requires_active_value();
