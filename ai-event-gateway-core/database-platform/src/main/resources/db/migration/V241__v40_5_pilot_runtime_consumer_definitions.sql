-- V40-5: Pilot runtime consumer definitions and complete-snapshot publication guard.
-- The migration authorizes only the 15 reviewed pilot RUNTIME_TUNABLE keys.

insert into runtime_config_definitions(
  definition_key,owner,authority_class,scope,data_type,risk,mutability,consumer_contract,
  target_mutability,target_consumer_contract,environment_applicability,validation_rule,review_status,
  migration_authorized,schema_version,source_ref) values
('dispatch.retry.max-attempts','DISPATCH','RUNTIME_TUNABLE','COMPONENT','INTEGER','MEDIUM','HOT_NEXT_CYCLE','RUNTIME_SNAPSHOT','HOT_NEXT_CYCLE','RUNTIME_SNAPSHOT','["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,'{"rule":"1..20"}'::jsonb,'MIGRATION_READY',true,2,'contracts/current/configuration/definitions/provisional-pilot-definitions.json'),
('dispatch.retry.initial-backoff','DISPATCH','RUNTIME_TUNABLE','COMPONENT','DURATION','MEDIUM','HOT_NEXT_CYCLE','RUNTIME_SNAPSHOT','HOT_NEXT_CYCLE','RUNTIME_SNAPSHOT','["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,'{"rule":">0"}'::jsonb,'MIGRATION_READY',true,2,'contracts/current/configuration/definitions/provisional-pilot-definitions.json'),
('dispatch.retry.max-backoff','DISPATCH','RUNTIME_TUNABLE','COMPONENT','DURATION','MEDIUM','HOT_NEXT_CYCLE','RUNTIME_SNAPSHOT','HOT_NEXT_CYCLE','RUNTIME_SNAPSHOT','["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,'{"rule":">=initial"}'::jsonb,'MIGRATION_READY',true,2,'contracts/current/configuration/definitions/provisional-pilot-definitions.json'),
('dispatch.retry.jitter-percent','DISPATCH','RUNTIME_TUNABLE','COMPONENT','INTEGER','MEDIUM','HOT_NEXT_CYCLE','RUNTIME_SNAPSHOT','HOT_NEXT_CYCLE','RUNTIME_SNAPSHOT','["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,'{"rule":"0..100"}'::jsonb,'MIGRATION_READY',true,2,'contracts/current/configuration/definitions/provisional-pilot-definitions.json'),
('adapter-executor.batch-size','ADAPTER_EXECUTION','RUNTIME_TUNABLE','COMPONENT','INTEGER','MEDIUM','HOT_NEXT_CYCLE','RUNTIME_SNAPSHOT','HOT_NEXT_CYCLE','RUNTIME_SNAPSHOT','["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,'{"rule":"1..1000"}'::jsonb,'MIGRATION_READY',true,2,'contracts/current/configuration/definitions/provisional-pilot-definitions.json'),
('adapter-executor.execution-timeout','ADAPTER_EXECUTION','RUNTIME_TUNABLE','COMPONENT','DURATION','MEDIUM','HOT_NEXT_CYCLE','RUNTIME_SNAPSHOT','HOT_NEXT_CYCLE','RUNTIME_SNAPSHOT','["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,'{"rule":"bounded positive"}'::jsonb,'MIGRATION_READY',true,2,'contracts/current/configuration/definitions/provisional-pilot-definitions.json'),
('adapter-executor.initial-backoff','ADAPTER_EXECUTION','RUNTIME_TUNABLE','COMPONENT','DURATION','MEDIUM','HOT_NEXT_CYCLE','RUNTIME_SNAPSHOT','HOT_NEXT_CYCLE','RUNTIME_SNAPSHOT','["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,'{"rule":">0"}'::jsonb,'MIGRATION_READY',true,2,'contracts/current/configuration/definitions/provisional-pilot-definitions.json'),
('adapter-executor.max-attempts','ADAPTER_EXECUTION','RUNTIME_TUNABLE','COMPONENT','INTEGER','MEDIUM','HOT_NEXT_CYCLE','RUNTIME_SNAPSHOT','HOT_NEXT_CYCLE','RUNTIME_SNAPSHOT','["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,'{"rule":">=1"}'::jsonb,'MIGRATION_READY',true,2,'contracts/current/configuration/definitions/provisional-pilot-definitions.json'),
('adapter-executor.max-backoff','ADAPTER_EXECUTION','RUNTIME_TUNABLE','COMPONENT','DURATION','MEDIUM','HOT_NEXT_CYCLE','RUNTIME_SNAPSHOT','HOT_NEXT_CYCLE','RUNTIME_SNAPSHOT','["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,'{"rule":">=initial"}'::jsonb,'MIGRATION_READY',true,2,'contracts/current/configuration/definitions/provisional-pilot-definitions.json'),
('issue-projection.reconcile-batch-size','ISSUE','RUNTIME_TUNABLE','COMPONENT','INTEGER','MEDIUM','HOT_NEXT_CYCLE','RUNTIME_SNAPSHOT','HOT_NEXT_CYCLE','RUNTIME_SNAPSHOT','["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,'{"rule":">=1"}'::jsonb,'MIGRATION_READY',true,2,'contracts/current/configuration/definitions/provisional-pilot-definitions.json'),
('issue-projection.retry-delay-seconds','ISSUE','RUNTIME_TUNABLE','COMPONENT','LONG','MEDIUM','HOT_NEXT_CYCLE','RUNTIME_SNAPSHOT','HOT_NEXT_CYCLE','RUNTIME_SNAPSHOT','["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,'{"rule":">=1"}'::jsonb,'MIGRATION_READY',true,2,'contracts/current/configuration/definitions/provisional-pilot-definitions.json'),
('task.dispatch-recovery.max-batch-size','TASK','RUNTIME_TUNABLE','COMPONENT','INTEGER','MEDIUM','HOT_NEXT_CYCLE','RUNTIME_SNAPSHOT','HOT_NEXT_CYCLE','RUNTIME_SNAPSHOT','["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,'{"rule":"1..1000"}'::jsonb,'MIGRATION_READY',true,2,'contracts/current/configuration/definitions/provisional-pilot-definitions.json'),
('task.dispatch-recovery.max-attempts','TASK','RUNTIME_TUNABLE','COMPONENT','INTEGER','MEDIUM','HOT_NEXT_CYCLE','RUNTIME_SNAPSHOT','HOT_NEXT_CYCLE','RUNTIME_SNAPSHOT','["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,'{"rule":"0..1000"}'::jsonb,'MIGRATION_READY',true,2,'contracts/current/configuration/definitions/provisional-pilot-definitions.json'),
('task.dispatch-recovery.initial-delay','TASK','RUNTIME_TUNABLE','COMPONENT','DURATION','MEDIUM','HOT_NEXT_CYCLE','RUNTIME_SNAPSHOT','HOT_NEXT_CYCLE','RUNTIME_SNAPSHOT','["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,'{"rule":">0"}'::jsonb,'MIGRATION_READY',true,2,'contracts/current/configuration/definitions/provisional-pilot-definitions.json'),
('task.dispatch-recovery.max-delay','TASK','RUNTIME_TUNABLE','COMPONENT','DURATION','MEDIUM','HOT_NEXT_CYCLE','RUNTIME_SNAPSHOT','HOT_NEXT_CYCLE','RUNTIME_SNAPSHOT','["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,'{"rule":">=initial"}'::jsonb,'MIGRATION_READY',true,2,'contracts/current/configuration/definitions/provisional-pilot-definitions.json')
on conflict (definition_key) do update set
  owner=excluded.owner,authority_class=excluded.authority_class,scope=excluded.scope,data_type=excluded.data_type,
  risk=excluded.risk,mutability=excluded.mutability,consumer_contract=excluded.consumer_contract,
  target_mutability=excluded.target_mutability,target_consumer_contract=excluded.target_consumer_contract,
  environment_applicability=excluded.environment_applicability,validation_rule=excluded.validation_rule,
  review_status=excluded.review_status,migration_authorized=excluded.migration_authorized,
  schema_version=excluded.schema_version,source_ref=excluded.source_ref,synchronized_at=now();

create or replace function guard_v40_5_pilot_revision_completeness() returns trigger language plpgsql as $$
declare
  v_set_key varchar(255);
  v_expected text[];
  v_actual integer;
begin
  if new.state is distinct from old.state and new.state in ('VALIDATED','PENDING_APPROVAL','APPROVED','PUBLISHED') then
    select set_key into v_set_key from runtime_config_sets where config_set_id=new.config_set_id;
    v_expected := case v_set_key
      when 'RUNTIME/DISPATCH/SYSTEM' then array['dispatch.retry.max-attempts','dispatch.retry.initial-backoff','dispatch.retry.max-backoff','dispatch.retry.jitter-percent']
      when 'RUNTIME/ADAPTER_EXECUTION/SYSTEM' then array['adapter-executor.batch-size','adapter-executor.execution-timeout','adapter-executor.initial-backoff','adapter-executor.max-attempts','adapter-executor.max-backoff']
      when 'RUNTIME/ISSUE/SYSTEM' then array['issue-projection.reconcile-batch-size','issue-projection.retry-delay-seconds']
      when 'RUNTIME/TASK/SYSTEM' then array['task.dispatch-recovery.max-batch-size','task.dispatch-recovery.max-attempts','task.dispatch-recovery.initial-delay','task.dispatch-recovery.max-delay']
      else null
    end;
    if v_expected is not null then
      select count(distinct i.definition_key) into v_actual
        from runtime_config_revision_items i
       where i.revision_id=new.revision_id and i.definition_key=any(v_expected);
      if v_actual <> cardinality(v_expected) then
        raise exception 'RUNTIME_CONFIG_PILOT_SNAPSHOT_INCOMPLETE: setKey=% revision=% expected=% actual=%',v_set_key,new.revision_id,cardinality(v_expected),v_actual;
      end if;
    end if;
  end if;
  return new;
end $$;

drop trigger if exists trg_v40_5_pilot_revision_completeness on runtime_config_revisions;
create trigger trg_v40_5_pilot_revision_completeness
  before update of state on runtime_config_revisions
  for each row execute function guard_v40_5_pilot_revision_completeness();
