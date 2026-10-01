-- V40-9C: Domain Migration Batch 2 — Adapter Action MCP orchestration operational toggles.
-- Four governed MCP orchestration toggles move to authenticated local Runtime Configuration snapshots.
-- MCP adapter-name and all Issue automation settings are deliberately excluded from this batch.
-- Startup YAML/ENV bindings remain migration fallback only until V40-11 legacy-authority retirement.

insert into runtime_config_definitions(
 definition_key,display_name,owner,domain_owner,authority_class,scope,scope_ref,data_type,unit,risk,mutability,consumer_contract,
 target_mutability,target_consumer_contract,environment_applicability,validation_rule,requires_approval,admin_editable,initial_seed,introduced_version,
 ui_metadata,config_set_key,review_status,migration_authorized,schema_version,source_ref,source_fingerprint)
values
  ('adapter-actions.mcp.enabled','MCP adapter action enabled','ADAPTER_ACTION','ADAPTER_ACTION','RUNTIME_TUNABLE','COMPONENT','ADAPTER_ACTION','BOOLEAN','boolean','MEDIUM','HOT_IMMEDIATE','RUNTIME_SNAPSHOT','HOT_IMMEDIATE','RUNTIME_SNAPSHOT','["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,'{}'::jsonb,true,true,'{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb,'V40-9','{"categoryId":"adapter-action-mcp","displayName":"MCP adapter action enabled","description":"Whether terminal task callbacks may create governed MCP adapter actions.","recommended":"Enable only where MCP post-task orchestration is intentionally active","effect":"Next terminal task callback decision","impactPositive":"Allows MCP post-task enrichment to be enabled or paused without restarting Core.","impactTradeoff":"Disabling prevents new MCP adapter actions while existing persisted actions remain unchanged.","advancedKeyVisible":true}'::jsonb,'RUNTIME/ADAPTER_ACTION/SYSTEM','MIGRATION_READY',true,3,'contracts/current/configuration/definitions/adapter-action-mcp-batch2-definitions.json','743a7f631f6c7449ead9c8641b256e06608672473af6b4c7895d94cbf204118e'),
  ('adapter-actions.mcp.run-on-completed-task','Run MCP action after completed task','ADAPTER_ACTION','ADAPTER_ACTION','RUNTIME_TUNABLE','COMPONENT','ADAPTER_ACTION','BOOLEAN','boolean','MEDIUM','HOT_IMMEDIATE','RUNTIME_SNAPSHOT','HOT_IMMEDIATE','RUNTIME_SNAPSHOT','["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,'{}'::jsonb,true,true,'{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb,'V40-9','{"categoryId":"adapter-action-mcp","displayName":"Run MCP action after completed task","description":"Whether a successfully completed task is eligible for MCP post-processing.","recommended":"Enabled when completed-task MCP enrichment is required","effect":"Next completed terminal task callback","impactPositive":"Lets operators pause or resume completed-task MCP orchestration without restart.","impactTradeoff":"Disabling skips MCP enrichment for newly evaluated completed tasks.","advancedKeyVisible":true}'::jsonb,'RUNTIME/ADAPTER_ACTION/SYSTEM','MIGRATION_READY',true,3,'contracts/current/configuration/definitions/adapter-action-mcp-batch2-definitions.json','159b8e546c6b5162c4af5f6390159667deebbd796fbecc35a4fbf43301ed9fb2'),
  ('adapter-actions.mcp.run-on-failed-task','Run MCP action after failed task','ADAPTER_ACTION','ADAPTER_ACTION','RUNTIME_TUNABLE','COMPONENT','ADAPTER_ACTION','BOOLEAN','boolean','MEDIUM','HOT_IMMEDIATE','RUNTIME_SNAPSHOT','HOT_IMMEDIATE','RUNTIME_SNAPSHOT','["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,'{}'::jsonb,true,true,'{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb,'V40-9','{"categoryId":"adapter-action-mcp","displayName":"Run MCP action after failed task","description":"Whether a failed terminal task is eligible for MCP post-processing.","recommended":"Disabled unless failure-context MCP processing is explicitly required","effect":"Next failed terminal task callback","impactPositive":"Allows failure-context enrichment to be enabled without restart.","impactTradeoff":"Enabling may increase MCP workload during incident bursts and failed-task storms.","advancedKeyVisible":true}'::jsonb,'RUNTIME/ADAPTER_ACTION/SYSTEM','MIGRATION_READY',true,3,'contracts/current/configuration/definitions/adapter-action-mcp-batch2-definitions.json','840f0070879c30ee4c5211bfac4da72a3117ae87c8fd77a8a5c320e040ed6d48'),
  ('adapter-actions.mcp.one-per-task','One MCP action per task','ADAPTER_ACTION','ADAPTER_ACTION','RUNTIME_TUNABLE','COMPONENT','ADAPTER_ACTION','BOOLEAN','boolean','MEDIUM','HOT_IMMEDIATE','RUNTIME_SNAPSHOT','HOT_IMMEDIATE','RUNTIME_SNAPSHOT','["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,'{}'::jsonb,true,true,'{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb,'V40-9','{"categoryId":"adapter-action-mcp","displayName":"One MCP action per task","description":"Controls MCP idempotency scope for newly evaluated terminal task callbacks.","recommended":"Enabled for deterministic one-action-per-task behavior","effect":"Next MCP idempotency-key generation","impactPositive":"Prevents duplicate MCP work for repeated evaluation of the same task.","impactTradeoff":"Disabling permits distinct MCP actions for repeated task evaluation and increases downstream workload.","advancedKeyVisible":true}'::jsonb,'RUNTIME/ADAPTER_ACTION/SYSTEM','MIGRATION_READY',true,3,'contracts/current/configuration/definitions/adapter-action-mcp-batch2-definitions.json','cdf8c86335a83c3d6e9589817fa6ae361a6f591ff6d75ada130545cf9e40fd08')
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
 status='CLASSIFIED',domain_owner='ADAPTER_ACTION',authority_class='RUNTIME_TUNABLE',scope='COMPONENT',risk='MEDIUM',mutability='HOT_IMMEDIATE',
 consumer_contract='RUNTIME_SNAPSHOT',admin_editable=true,requires_approval=true,classified_by='adapter-action-mcp-classifier',classified_at=now(),
 reason='V40-9C Batch 2 human classification for Adapter Action MCP orchestration toggles',version=version+1
where configuration_key in ('adapter-actions.mcp.enabled','adapter-actions.mcp.run-on-completed-task','adapter-actions.mcp.run-on-failed-task','adapter-actions.mcp.one-per-task') and status='DISCOVERED';
insert into runtime_config_inventory_governance_events(configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'CLASSIFIED','DISCOVERED','CLASSIFIED','adapter-action-mcp-classifier',
 'V40-9C Batch 2 human classification for Adapter Action MCP orchestration toggles',source_observation_hash,
 '{"stage":"V40_9C_DOMAIN_MIGRATION_BATCH_2","domain":"ADAPTER_ACTION_MCP"}'::jsonb
from runtime_config_inventory_governance where configuration_key in ('adapter-actions.mcp.enabled','adapter-actions.mcp.run-on-completed-task','adapter-actions.mcp.run-on-failed-task','adapter-actions.mcp.one-per-task') and status='CLASSIFIED';

update runtime_config_inventory_governance set status='OWNER_REVIEWED',owner_reviewed_by='adapter-action-domain-owner',owner_reviewed_at=now(),
 reason='Adapter Action domain owner approved Batch 2 MCP orchestration semantics and operational bounds',version=version+1
where configuration_key in ('adapter-actions.mcp.enabled','adapter-actions.mcp.run-on-completed-task','adapter-actions.mcp.run-on-failed-task','adapter-actions.mcp.one-per-task') and status='CLASSIFIED';
insert into runtime_config_inventory_governance_events(configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'OWNER_REVIEWED','CLASSIFIED','OWNER_REVIEWED','adapter-action-domain-owner',
 'Adapter Action domain owner approved Batch 2 MCP orchestration semantics and operational bounds',source_observation_hash,
 '{"stage":"V40_9C_DOMAIN_MIGRATION_BATCH_2","domain":"ADAPTER_ACTION_MCP"}'::jsonb
from runtime_config_inventory_governance where configuration_key in ('adapter-actions.mcp.enabled','adapter-actions.mcp.run-on-completed-task','adapter-actions.mcp.run-on-failed-task','adapter-actions.mcp.one-per-task') and status='OWNER_REVIEWED';

update runtime_config_inventory_governance set status='ARCHITECTURE_APPROVED',architecture_approved_by='configuration-architecture-reviewer',architecture_approved_at=now(),
 reason='Configuration architecture approved local-snapshot MCP consumer migration; adapter identity and Issue behavior excluded',version=version+1
where configuration_key in ('adapter-actions.mcp.enabled','adapter-actions.mcp.run-on-completed-task','adapter-actions.mcp.run-on-failed-task','adapter-actions.mcp.one-per-task') and status='OWNER_REVIEWED';
insert into runtime_config_inventory_governance_events(configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'ARCHITECTURE_APPROVED','OWNER_REVIEWED','ARCHITECTURE_APPROVED','configuration-architecture-reviewer',
 'Configuration architecture approved local-snapshot MCP consumer migration; adapter identity and Issue behavior excluded',source_observation_hash,
 '{"stage":"V40_9C_DOMAIN_MIGRATION_BATCH_2","domain":"ADAPTER_ACTION_MCP"}'::jsonb
from runtime_config_inventory_governance where configuration_key in ('adapter-actions.mcp.enabled','adapter-actions.mcp.run-on-completed-task','adapter-actions.mcp.run-on-failed-task','adapter-actions.mcp.one-per-task') and status='ARCHITECTURE_APPROVED';

update runtime_config_inventory_governance set status='MIGRATION_READY',migration_authorized_by='configuration-migration-authorizer',migration_authorized_at=now(),
 reason='Migration Ready gate passed with source-controlled definitions and typed Adapter Action MCP runtime consumer',version=version+1
where configuration_key in ('adapter-actions.mcp.enabled','adapter-actions.mcp.run-on-completed-task','adapter-actions.mcp.run-on-failed-task','adapter-actions.mcp.one-per-task') and status='ARCHITECTURE_APPROVED';
insert into runtime_config_inventory_governance_events(configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'MIGRATION_READY','ARCHITECTURE_APPROVED','MIGRATION_READY','configuration-migration-authorizer',
 'Migration Ready gate passed with source-controlled definitions and typed Adapter Action MCP runtime consumer',source_observation_hash,
 '{"stage":"V40_9C_DOMAIN_MIGRATION_BATCH_2","domain":"ADAPTER_ACTION_MCP","consumer":"AdapterActionMcpRuntimeConfigurationView"}'::jsonb
from runtime_config_inventory_governance where configuration_key in ('adapter-actions.mcp.enabled','adapter-actions.mcp.run-on-completed-task','adapter-actions.mcp.run-on-failed-task','adapter-actions.mcp.one-per-task') and status='MIGRATION_READY';

update runtime_config_inventory_governance set status='MIGRATED',
 reason='V40-9C MCP consumer migration complete; startup binding retained only as V40-11 retirement fallback',version=version+1
where configuration_key in ('adapter-actions.mcp.enabled','adapter-actions.mcp.run-on-completed-task','adapter-actions.mcp.run-on-failed-task','adapter-actions.mcp.one-per-task') and status='MIGRATION_READY';
insert into runtime_config_inventory_governance_events(configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'MIGRATED','MIGRATION_READY','MIGRATED','v40-9c-domain-migration',
 'V40-9C MCP consumer migration complete; startup binding retained only as V40-11 retirement fallback',source_observation_hash,
 '{"stage":"V40_9C_DOMAIN_MIGRATION_BATCH_2","domain":"ADAPTER_ACTION_MCP","legacyAuthorityRetired":false}'::jsonb
from runtime_config_inventory_governance where configuration_key in ('adapter-actions.mcp.enabled','adapter-actions.mcp.run-on-completed-task','adapter-actions.mcp.run-on-failed-task','adapter-actions.mcp.one-per-task') and status='MIGRATED';

-- Definition-driven complete-snapshot guard.
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
 'V40-9C definition-driven complete-snapshot guard. Legacy function name retained for migration compatibility.';
