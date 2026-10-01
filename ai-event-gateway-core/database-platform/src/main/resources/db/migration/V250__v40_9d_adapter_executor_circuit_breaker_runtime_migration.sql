-- V40-9D: Domain Migration Batch 3 — Adapter Executor circuit-breaker operational tuning.
-- Three circuit-breaker controls move to authenticated local Runtime Configuration snapshots.
-- Executor mode, static schedules, MCP endpoint/secret/identity and Issue authority are deliberately excluded.
-- Startup YAML/ENV bindings remain migration fallback only until V40-11 legacy-authority retirement.

insert into runtime_config_definitions(
 definition_key,display_name,owner,domain_owner,authority_class,scope,scope_ref,data_type,unit,risk,mutability,consumer_contract,
 target_mutability,target_consumer_contract,environment_applicability,validation_rule,requires_approval,admin_editable,initial_seed,introduced_version,
 ui_metadata,config_set_key,review_status,migration_authorized,schema_version,source_ref,source_fingerprint)
values
  ('adapter-executor.circuit-breaker.enabled','Circuit breaker enabled','ADAPTER_EXECUTION','ADAPTER_EXECUTION','RUNTIME_TUNABLE','COMPONENT','ADAPTER_EXECUTION','BOOLEAN','boolean','MEDIUM','HOT_IMMEDIATE','RUNTIME_SNAPSHOT','HOT_IMMEDIATE','RUNTIME_SNAPSHOT','["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,'{}'::jsonb,true,true,'{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb,'V40-9','{"categoryId":"adapter-execution","displayName":"Circuit breaker enabled","description":"Whether Adapter Executor circuit breaking protects newly evaluated execution attempts.","recommended":"Enabled for normal production operation","effect":"Subsequent circuit-breaker decisions","impactPositive":"Prevents repeated calls to a failing executor from continuously consuming capacity.","impactTradeoff":"Disabling removes the circuit-breaker protection for subsequent executions.","advancedKeyVisible":true}'::jsonb,'RUNTIME/ADAPTER_EXECUTION/SYSTEM','MIGRATION_READY',true,3,'contracts/current/configuration/definitions/adapter-executor-circuit-breaker-batch3-definitions.json','6dac3963efa063f1c0e8be9f1073bc40fd551c336dc12f3113e49a3d13a0510c'),
  ('adapter-executor.circuit-breaker.failure-threshold','Circuit breaker failure threshold','ADAPTER_EXECUTION','ADAPTER_EXECUTION','RUNTIME_TUNABLE','COMPONENT','ADAPTER_EXECUTION','INTEGER','failures','MEDIUM','HOT_IMMEDIATE','RUNTIME_SNAPSHOT','HOT_IMMEDIATE','RUNTIME_SNAPSHOT','["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,'{"minimum":1}'::jsonb,true,true,'{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb,'V40-9','{"categoryId":"adapter-execution","displayName":"Circuit breaker failure threshold","description":"Consecutive executor failures required before a circuit enters the open state.","recommended":"5 failures unless provider-specific evidence supports another threshold","effect":"Subsequent failure recording","impactPositive":"Lets operators tune sensitivity to repeated executor failures without restart.","impactTradeoff":"A threshold that is too low can open on transient failures; too high delays protection.","advancedKeyVisible":true}'::jsonb,'RUNTIME/ADAPTER_EXECUTION/SYSTEM','MIGRATION_READY',true,3,'contracts/current/configuration/definitions/adapter-executor-circuit-breaker-batch3-definitions.json','5ec1582cecb72151a4c60989bfc58eec1020dd59cc45d44825b2cef7e0cfd8da'),
  ('adapter-executor.circuit-breaker.open-duration','Circuit breaker open duration','ADAPTER_EXECUTION','ADAPTER_EXECUTION','RUNTIME_TUNABLE','COMPONENT','ADAPTER_EXECUTION','DURATION','duration','MEDIUM','HOT_IMMEDIATE','RUNTIME_SNAPSHOT','HOT_IMMEDIATE','RUNTIME_SNAPSHOT','["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,'{"minimum":"PT0.001S"}'::jsonb,true,true,'{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb,'V40-9','{"categoryId":"adapter-execution","displayName":"Circuit breaker open duration","description":"How long an opened executor circuit remains unavailable before normal evaluation resumes.","recommended":"PT1M unless provider recovery behavior requires another duration","effect":"Subsequent circuit opening","impactPositive":"Allows provider recovery windows to be tuned without restarting Core.","impactTradeoff":"Long durations delay recovery after a provider becomes healthy; very short durations can increase retry pressure.","advancedKeyVisible":true}'::jsonb,'RUNTIME/ADAPTER_EXECUTION/SYSTEM','MIGRATION_READY',true,3,'contracts/current/configuration/definitions/adapter-executor-circuit-breaker-batch3-definitions.json','100f79c68b1f9c00c28b9c60f5fe3f76efe9049b7798cbc7837ed23835abc29d')
on conflict(definition_key) do update set
 display_name=excluded.display_name,owner=excluded.owner,domain_owner=excluded.domain_owner,authority_class=excluded.authority_class,
 scope=excluded.scope,scope_ref=excluded.scope_ref,data_type=excluded.data_type,unit=excluded.unit,risk=excluded.risk,mutability=excluded.mutability,
 consumer_contract=excluded.consumer_contract,target_mutability=excluded.target_mutability,target_consumer_contract=excluded.target_consumer_contract,
 environment_applicability=excluded.environment_applicability,validation_rule=excluded.validation_rule,requires_approval=excluded.requires_approval,
 admin_editable=excluded.admin_editable,initial_seed=excluded.initial_seed,introduced_version=excluded.introduced_version,ui_metadata=excluded.ui_metadata,
 config_set_key=excluded.config_set_key,review_status=excluded.review_status,migration_authorized=excluded.migration_authorized,schema_version=excluded.schema_version,
 source_ref=excluded.source_ref,source_fingerprint=excluded.source_fingerprint,synchronized_at=now();

-- Governance evidence advances one legal state at a time. Actors remain distinct for SoD.
update runtime_config_inventory_governance set
 status='CLASSIFIED',domain_owner='ADAPTER_EXECUTION',authority_class='RUNTIME_TUNABLE',scope='COMPONENT',risk='MEDIUM',mutability='HOT_IMMEDIATE',
 consumer_contract='RUNTIME_SNAPSHOT',admin_editable=true,requires_approval=true,classified_by='adapter-executor-circuit-breaker-classifier',classified_at=now(),
 reason='V40-9D Batch 3 human classification for Adapter Executor circuit-breaker operational controls',version=version+1
where configuration_key in ('adapter-executor.circuit-breaker.enabled','adapter-executor.circuit-breaker.failure-threshold','adapter-executor.circuit-breaker.open-duration') and status='DISCOVERED';
insert into runtime_config_inventory_governance_events(configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'CLASSIFIED','DISCOVERED','CLASSIFIED','adapter-executor-circuit-breaker-classifier',
 'V40-9D Batch 3 human classification for Adapter Executor circuit-breaker operational controls',source_observation_hash,
 '{"stage":"V40_9D_DOMAIN_MIGRATION_BATCH_3","domain":"ADAPTER_EXECUTOR_CIRCUIT_BREAKER"}'::jsonb
from runtime_config_inventory_governance where configuration_key in ('adapter-executor.circuit-breaker.enabled','adapter-executor.circuit-breaker.failure-threshold','adapter-executor.circuit-breaker.open-duration') and status='CLASSIFIED';

update runtime_config_inventory_governance set status='OWNER_REVIEWED',owner_reviewed_by='adapter-execution-domain-owner',owner_reviewed_at=now(),
 reason='Adapter Execution domain owner approved circuit-breaker semantics, validation and operational risk',version=version+1
where configuration_key in ('adapter-executor.circuit-breaker.enabled','adapter-executor.circuit-breaker.failure-threshold','adapter-executor.circuit-breaker.open-duration') and status='CLASSIFIED';
insert into runtime_config_inventory_governance_events(configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'OWNER_REVIEWED','CLASSIFIED','OWNER_REVIEWED','adapter-execution-domain-owner',
 'Adapter Execution domain owner approved circuit-breaker semantics, validation and operational risk',source_observation_hash,
 '{"stage":"V40_9D_DOMAIN_MIGRATION_BATCH_3","domain":"ADAPTER_EXECUTOR_CIRCUIT_BREAKER"}'::jsonb
from runtime_config_inventory_governance where configuration_key in ('adapter-executor.circuit-breaker.enabled','adapter-executor.circuit-breaker.failure-threshold','adapter-executor.circuit-breaker.open-duration') and status='OWNER_REVIEWED';

update runtime_config_inventory_governance set status='ARCHITECTURE_APPROVED',architecture_approved_by='configuration-architecture-reviewer',architecture_approved_at=now(),
 reason='Configuration architecture approved authenticated local-snapshot circuit-breaker migration; topology, secrets, Issue authority and static schedules excluded',version=version+1
where configuration_key in ('adapter-executor.circuit-breaker.enabled','adapter-executor.circuit-breaker.failure-threshold','adapter-executor.circuit-breaker.open-duration') and status='OWNER_REVIEWED';
insert into runtime_config_inventory_governance_events(configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'ARCHITECTURE_APPROVED','OWNER_REVIEWED','ARCHITECTURE_APPROVED','configuration-architecture-reviewer',
 'Configuration architecture approved authenticated local-snapshot circuit-breaker migration; topology, secrets, Issue authority and static schedules excluded',source_observation_hash,
 '{"stage":"V40_9D_DOMAIN_MIGRATION_BATCH_3","domain":"ADAPTER_EXECUTOR_CIRCUIT_BREAKER"}'::jsonb
from runtime_config_inventory_governance where configuration_key in ('adapter-executor.circuit-breaker.enabled','adapter-executor.circuit-breaker.failure-threshold','adapter-executor.circuit-breaker.open-duration') and status='ARCHITECTURE_APPROVED';

update runtime_config_inventory_governance set status='MIGRATION_READY',migration_authorized_by='configuration-migration-authorizer',migration_authorized_at=now(),
 reason='Migration Ready gate passed with source-controlled definitions and typed Adapter Executor runtime consumer',version=version+1
where configuration_key in ('adapter-executor.circuit-breaker.enabled','adapter-executor.circuit-breaker.failure-threshold','adapter-executor.circuit-breaker.open-duration') and status='ARCHITECTURE_APPROVED';
insert into runtime_config_inventory_governance_events(configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'MIGRATION_READY','ARCHITECTURE_APPROVED','MIGRATION_READY','configuration-migration-authorizer',
 'Migration Ready gate passed with source-controlled definitions and typed Adapter Executor runtime consumer',source_observation_hash,
 '{"stage":"V40_9D_DOMAIN_MIGRATION_BATCH_3","domain":"ADAPTER_EXECUTOR_CIRCUIT_BREAKER","consumer":"AdapterExecutorCircuitBreaker"}'::jsonb
from runtime_config_inventory_governance where configuration_key in ('adapter-executor.circuit-breaker.enabled','adapter-executor.circuit-breaker.failure-threshold','adapter-executor.circuit-breaker.open-duration') and status='MIGRATION_READY';

update runtime_config_inventory_governance set status='MIGRATED',
 reason='V40-9D circuit-breaker consumer migration complete; startup binding retained only as V40-11 retirement fallback',version=version+1
where configuration_key in ('adapter-executor.circuit-breaker.enabled','adapter-executor.circuit-breaker.failure-threshold','adapter-executor.circuit-breaker.open-duration') and status='MIGRATION_READY';
insert into runtime_config_inventory_governance_events(configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'MIGRATED','MIGRATION_READY','MIGRATED','v40-9d-domain-migration',
 'V40-9D circuit-breaker consumer migration complete; startup binding retained only as V40-11 retirement fallback',source_observation_hash,
 '{"stage":"V40_9D_DOMAIN_MIGRATION_BATCH_3","domain":"ADAPTER_EXECUTOR_CIRCUIT_BREAKER","legacyAuthorityRetired":false}'::jsonb
from runtime_config_inventory_governance where configuration_key in ('adapter-executor.circuit-breaker.enabled','adapter-executor.circuit-breaker.failure-threshold','adapter-executor.circuit-breaker.open-duration') and status='MIGRATED';

-- Definition-driven complete-snapshot guard remains authoritative for every migrated key in the set.
create or replace function guard_v40_5_pilot_revision_completeness() returns trigger language plpgsql as $$
declare
  v_set_key varchar(255);
  v_expected text[];
  v_actual integer;
begin
  if new.state is distinct from old.state and new.state in ('VALIDATED','PENDING_APPROVAL','APPROVED','PUBLISHED') then
    select set_key into v_set_key from runtime_config_sets where config_set_id=new.config_set_id;
    select array_agg(definition_key order by definition_key) into v_expected
      from runtime_config_definitions
     where config_set_key=v_set_key and migration_authorized=true and review_status='MIGRATION_READY';
    if v_expected is not null and cardinality(v_expected)>0 then
      select count(distinct i.definition_key) into v_actual
        from runtime_config_revision_items i
       where i.revision_id=new.revision_id and i.definition_key=any(v_expected);
      if v_actual <> cardinality(v_expected) then
        raise exception 'RUNTIME_CONFIG_SNAPSHOT_INCOMPLETE: setKey=% revision=% expected=% actual=%',v_set_key,new.revision_id,cardinality(v_expected),v_actual;
      end if;
    end if;
  end if;
  return new;
end $$;

comment on function guard_v40_5_pilot_revision_completeness() is
 'V40-9D definition-driven complete-snapshot guard. Legacy function name retained for migration compatibility.';
