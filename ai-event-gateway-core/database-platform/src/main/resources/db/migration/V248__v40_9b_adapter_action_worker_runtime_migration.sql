-- V40-9B: Domain Migration Batch 1 — Adapter Action external-worker operational tunables.
-- Five governed values move to authenticated local Runtime Configuration snapshots.
-- The static expired-lease scan interval is deliberately excluded and remains a later Dynamic Scheduler candidate.
-- Startup YAML/ENV bindings remain migration fallback only until V40-11 legacy-authority retirement.

insert into runtime_config_definitions(
 definition_key,display_name,owner,domain_owner,authority_class,scope,scope_ref,data_type,unit,risk,mutability,consumer_contract,
 target_mutability,target_consumer_contract,environment_applicability,validation_rule,requires_approval,admin_editable,initial_seed,introduced_version,
 ui_metadata,config_set_key,review_status,migration_authorized,schema_version,source_ref,source_fingerprint)
values
  ('adapter-actions.worker.retry-enabled','Adapter worker retry enabled','ADAPTER_ACTION','ADAPTER_ACTION','RUNTIME_TUNABLE','COMPONENT','ADAPTER_ACTION','BOOLEAN','boolean','MEDIUM','HOT_NEXT_CYCLE','RUNTIME_SNAPSHOT','HOT_NEXT_CYCLE','RUNTIME_SNAPSHOT','["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,'{}'::jsonb,true,true,'{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb,'V40-9','{"categoryId":"adapter-action-worker","displayName":"Adapter worker retry enabled","description":"Whether failed or expired external-worker adapter actions may enter governed retry waiting.","recommended":"Enabled unless an operator is intentionally stopping automatic retries","effect":"Next worker failure or lease-recovery decision","impactPositive":"Allows transient worker/provider failures to recover without manual intervention.","impactTradeoff":"Disabling retries increases manual recovery work and can leave recoverable actions failed.","advancedKeyVisible":true}'::jsonb,'RUNTIME/ADAPTER_ACTION/SYSTEM','MIGRATION_READY',true,3,'contracts/current/configuration/definitions/adapter-action-worker-batch1-definitions.json','ce8b05a52c780672c7587dda5e535b99cf4556371b20ca873ab9aada7219c415'),
  ('adapter-actions.worker.max-attempts','Adapter worker maximum attempts','ADAPTER_ACTION','ADAPTER_ACTION','RUNTIME_TUNABLE','COMPONENT','ADAPTER_ACTION','INTEGER','attempts','MEDIUM','HOT_NEXT_CYCLE','RUNTIME_SNAPSHOT','HOT_NEXT_CYCLE','RUNTIME_SNAPSHOT','["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,'{"minimum":1,"maximum":20}'::jsonb,true,true,'{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb,'V40-9','{"categoryId":"adapter-action-worker","displayName":"Adapter worker maximum attempts","description":"Maximum governed attempts captured for newly created adapter actions.","recommended":"2–5","effect":"New adapter action creation; existing actions retain their stored attempt cap","impactPositive":"More attempts improve resilience to transient worker/provider failures.","impactTradeoff":"Higher values delay terminal failure and can increase provider load.","advancedKeyVisible":true}'::jsonb,'RUNTIME/ADAPTER_ACTION/SYSTEM','MIGRATION_READY',true,3,'contracts/current/configuration/definitions/adapter-action-worker-batch1-definitions.json','ee845108b98de7950bd78d87cf3984a6b0bc98b3530909b82f12c506b16ed004'),
  ('adapter-actions.worker.initial-backoff','Adapter worker initial retry delay','ADAPTER_ACTION','ADAPTER_ACTION','RUNTIME_TUNABLE','COMPONENT','ADAPTER_ACTION','DURATION','duration','MEDIUM','HOT_NEXT_CYCLE','RUNTIME_SNAPSHOT','HOT_NEXT_CYCLE','RUNTIME_SNAPSHOT','["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,'{"minimum":"PT0.001S","maximum":"PT1H"}'::jsonb,true,true,'{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb,'V40-9','{"categoryId":"adapter-action-worker","displayName":"Adapter worker initial retry delay","description":"Initial delay used when an adapter action enters retry waiting after a worker failure or expired lease.","recommended":"PT5S–PT1M","effect":"Next retry scheduling decision","impactPositive":"Provides backpressure while allowing transient failures to recover quickly.","impactTradeoff":"Long delays slow recovery; very short delays can create retry pressure.","advancedKeyVisible":true}'::jsonb,'RUNTIME/ADAPTER_ACTION/SYSTEM','MIGRATION_READY',true,3,'contracts/current/configuration/definitions/adapter-action-worker-batch1-definitions.json','c1f8ab3e29213fe56fe804e99ce81d73ad13adada085dce14895b6c257361710'),
  ('adapter-actions.worker.max-backoff','Adapter worker maximum retry delay','ADAPTER_ACTION','ADAPTER_ACTION','RUNTIME_TUNABLE','COMPONENT','ADAPTER_ACTION','DURATION','duration','MEDIUM','HOT_NEXT_CYCLE','RUNTIME_SNAPSHOT','HOT_NEXT_CYCLE','RUNTIME_SNAPSHOT','["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,'{"minimum":"PT0.001S","maximum":"PT24H","greaterThanOrEqualKey":"adapter-actions.worker.initial-backoff"}'::jsonb,true,true,'{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb,'V40-9','{"categoryId":"adapter-action-worker","displayName":"Adapter worker maximum retry delay","description":"Upper bound for exponential retry delay after repeated worker failures.","recommended":"At least the initial delay; typically PT1M–PT15M","effect":"Next retry scheduling decision","impactPositive":"Caps retry pressure during longer worker or provider outages.","impactTradeoff":"Large values delay recovery after the external dependency becomes healthy.","advancedKeyVisible":true}'::jsonb,'RUNTIME/ADAPTER_ACTION/SYSTEM','MIGRATION_READY',true,3,'contracts/current/configuration/definitions/adapter-action-worker-batch1-definitions.json','d4c76734d4fbc326dfe0b2698ca652421c4122d157013c1846d9c5cc70665e9a'),
  ('adapter-actions.worker.expired-lease-scan-batch-size','Expired worker lease recovery batch size','ADAPTER_ACTION','ADAPTER_ACTION','RUNTIME_TUNABLE','COMPONENT','ADAPTER_ACTION','INTEGER','items','MEDIUM','HOT_NEXT_CYCLE','RUNTIME_SNAPSHOT','HOT_NEXT_CYCLE','RUNTIME_SNAPSHOT','["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,'{"minimum":1,"maximum":1000}'::jsonb,true,true,'{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb,'V40-9','{"categoryId":"adapter-action-worker","displayName":"Expired worker lease recovery batch size","description":"Maximum claimed adapter actions examined during one expired external-worker lease recovery cycle.","recommended":"25–200","effect":"Next lease-recovery cycle","impactPositive":"Larger batches can clear accumulated expired leases faster.","impactTradeoff":"Very large batches increase database and recovery burst load.","advancedKeyVisible":true}'::jsonb,'RUNTIME/ADAPTER_ACTION/SYSTEM','MIGRATION_READY',true,3,'contracts/current/configuration/definitions/adapter-action-worker-batch1-definitions.json','79349c526bef791c5f103ccfaddefb860197428ffd724dcd622b15e4c3cc0cc6')
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
 status='CLASSIFIED',domain_owner='ADAPTER_ACTION',authority_class='RUNTIME_TUNABLE',scope='COMPONENT',risk='MEDIUM',mutability='HOT_NEXT_CYCLE',
 consumer_contract='RUNTIME_SNAPSHOT',admin_editable=true,requires_approval=true,classified_by='adapter-action-domain-classifier',classified_at=now(),
 reason='V40-9B Batch 1 human classification for Adapter Action worker operational tuning',version=version+1
where configuration_key in ('adapter-actions.worker.retry-enabled','adapter-actions.worker.max-attempts','adapter-actions.worker.initial-backoff','adapter-actions.worker.max-backoff','adapter-actions.worker.expired-lease-scan-batch-size') and status='DISCOVERED';
insert into runtime_config_inventory_governance_events(configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'CLASSIFIED','DISCOVERED','CLASSIFIED','adapter-action-domain-classifier',
 'V40-9B Batch 1 human classification for Adapter Action worker operational tuning',source_observation_hash,
 '{"stage":"V40_9B_DOMAIN_MIGRATION_BATCH_1","domain":"ADAPTER_ACTION_WORKER"}'::jsonb
from runtime_config_inventory_governance where configuration_key in ('adapter-actions.worker.retry-enabled','adapter-actions.worker.max-attempts','adapter-actions.worker.initial-backoff','adapter-actions.worker.max-backoff','adapter-actions.worker.expired-lease-scan-batch-size') and status='CLASSIFIED';

update runtime_config_inventory_governance set status='OWNER_REVIEWED',owner_reviewed_by='adapter-action-domain-owner',owner_reviewed_at=now(),
 reason='Adapter Action domain owner approved Batch 1 runtime semantics and operational bounds',version=version+1
where configuration_key in ('adapter-actions.worker.retry-enabled','adapter-actions.worker.max-attempts','adapter-actions.worker.initial-backoff','adapter-actions.worker.max-backoff','adapter-actions.worker.expired-lease-scan-batch-size') and status='CLASSIFIED';
insert into runtime_config_inventory_governance_events(configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'OWNER_REVIEWED','CLASSIFIED','OWNER_REVIEWED','adapter-action-domain-owner',
 'Adapter Action domain owner approved Batch 1 runtime semantics and operational bounds',source_observation_hash,
 '{"stage":"V40_9B_DOMAIN_MIGRATION_BATCH_1","domain":"ADAPTER_ACTION_WORKER"}'::jsonb
from runtime_config_inventory_governance where configuration_key in ('adapter-actions.worker.retry-enabled','adapter-actions.worker.max-attempts','adapter-actions.worker.initial-backoff','adapter-actions.worker.max-backoff','adapter-actions.worker.expired-lease-scan-batch-size') and status='OWNER_REVIEWED';

update runtime_config_inventory_governance set status='ARCHITECTURE_APPROVED',architecture_approved_by='configuration-architecture-reviewer',architecture_approved_at=now(),
 reason='Configuration architecture approved local-snapshot consumer migration; static scan interval excluded',version=version+1
where configuration_key in ('adapter-actions.worker.retry-enabled','adapter-actions.worker.max-attempts','adapter-actions.worker.initial-backoff','adapter-actions.worker.max-backoff','adapter-actions.worker.expired-lease-scan-batch-size') and status='OWNER_REVIEWED';
insert into runtime_config_inventory_governance_events(configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'ARCHITECTURE_APPROVED','OWNER_REVIEWED','ARCHITECTURE_APPROVED','configuration-architecture-reviewer',
 'Configuration architecture approved local-snapshot consumer migration; static scan interval excluded',source_observation_hash,
 '{"stage":"V40_9B_DOMAIN_MIGRATION_BATCH_1","domain":"ADAPTER_ACTION_WORKER"}'::jsonb
from runtime_config_inventory_governance where configuration_key in ('adapter-actions.worker.retry-enabled','adapter-actions.worker.max-attempts','adapter-actions.worker.initial-backoff','adapter-actions.worker.max-backoff','adapter-actions.worker.expired-lease-scan-batch-size') and status='ARCHITECTURE_APPROVED';

update runtime_config_inventory_governance set status='MIGRATION_READY',migration_authorized_by='configuration-migration-authorizer',migration_authorized_at=now(),
 reason='Migration Ready gate passed with source-controlled definitions and typed Adapter Action worker runtime consumer',version=version+1
where configuration_key in ('adapter-actions.worker.retry-enabled','adapter-actions.worker.max-attempts','adapter-actions.worker.initial-backoff','adapter-actions.worker.max-backoff','adapter-actions.worker.expired-lease-scan-batch-size') and status='ARCHITECTURE_APPROVED';
insert into runtime_config_inventory_governance_events(configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'MIGRATION_READY','ARCHITECTURE_APPROVED','MIGRATION_READY','configuration-migration-authorizer',
 'Migration Ready gate passed with source-controlled definitions and typed Adapter Action worker runtime consumer',source_observation_hash,
 '{"stage":"V40_9B_DOMAIN_MIGRATION_BATCH_1","domain":"ADAPTER_ACTION_WORKER","consumer":"AdapterActionWorkerRuntimeConfigurationView"}'::jsonb
from runtime_config_inventory_governance where configuration_key in ('adapter-actions.worker.retry-enabled','adapter-actions.worker.max-attempts','adapter-actions.worker.initial-backoff','adapter-actions.worker.max-backoff','adapter-actions.worker.expired-lease-scan-batch-size') and status='MIGRATION_READY';

update runtime_config_inventory_governance set status='MIGRATED',
 reason='V40-9B consumer migration complete; startup binding retained only as V40-11 retirement fallback',version=version+1
where configuration_key in ('adapter-actions.worker.retry-enabled','adapter-actions.worker.max-attempts','adapter-actions.worker.initial-backoff','adapter-actions.worker.max-backoff','adapter-actions.worker.expired-lease-scan-batch-size') and status='MIGRATION_READY';
insert into runtime_config_inventory_governance_events(configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'MIGRATED','MIGRATION_READY','MIGRATED','v40-9b-domain-migration',
 'V40-9B consumer migration complete; startup binding retained only as V40-11 retirement fallback',source_observation_hash,
 '{"stage":"V40_9B_DOMAIN_MIGRATION_BATCH_1","domain":"ADAPTER_ACTION_WORKER","legacyAuthorityRetired":false}'::jsonb
from runtime_config_inventory_governance where configuration_key in ('adapter-actions.worker.retry-enabled','adapter-actions.worker.max-attempts','adapter-actions.worker.initial-backoff','adapter-actions.worker.max-backoff','adapter-actions.worker.expired-lease-scan-batch-size') and status='MIGRATED';

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
 'V40-9B definition-driven complete-snapshot guard. Legacy function name retained for migration compatibility.';
