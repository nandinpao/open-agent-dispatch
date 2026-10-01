-- V41-C3R2B: Dispatch Domain Consumer Closure.
-- 15 previously PROPOSED Dispatch runtime definitions become MIGRATION_READY.
-- All 26 Dispatch runtime targets are normalized to the typed local-snapshot consumer contract.
-- No key is advanced to MIGRATED; C3R3 Single Authority cutover remains mandatory.
--
-- Historical governance repair prelude:
-- V247 reconstructed migrationAuthorized pilot definitions as MIGRATED even though no
-- complete active revision + node convergence / Single Authority evidence existed.
-- V256 is the first still-pending closure migration that detects the resulting false
-- MIGRATED state. Reopen only the exact 18 V247 historical-import rows, under a
-- transaction-local token and exact provenance predicates, then restore the strict guard.

create or replace function guard_runtime_config_inventory_governance_transition() returns trigger language plpgsql as $$
begin
  if new.source_observation_hash is distinct from (select source_observation_hash from runtime_config_inventory_observations where configuration_key=new.configuration_key) then
    raise exception 'SOURCE_OBSERVATION_DRIFT';
  end if;

  if old.status='MIGRATED'
     and new.status='MIGRATION_READY'
     and new.configuration_key in (
       'adapter-executor.batch-size',
       'adapter-executor.execution-timeout',
       'adapter-executor.initial-backoff',
       'adapter-executor.issue.link-projection-reconciliation-delay',
       'adapter-executor.max-attempts',
       'adapter-executor.max-backoff',
       'dispatch.retry.initial-backoff',
       'dispatch.retry.jitter-percent',
       'dispatch.retry.max-attempts',
       'dispatch.retry.max-backoff',
       'issue-projection.reconcile-batch-size',
       'issue-projection.reconcile-delay-ms',
       'issue-projection.retry-delay-seconds',
       'task.dispatch-recovery.initial-delay',
       'task.dispatch-recovery.interval-ms',
       'task.dispatch-recovery.max-attempts',
       'task.dispatch-recovery.max-batch-size',
       'task.dispatch-recovery.max-delay'
     )
     and current_setting('app.runtime_config_governance_reopen_stage',true)='V41_C3R2B_V247_HISTORICAL_FALSE_MIGRATED_CORRECTION' then
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

select set_config('app.runtime_config_governance_reopen_stage','V41_C3R2B_V247_HISTORICAL_FALSE_MIGRATED_CORRECTION',true);

with reopened as (
  update runtime_config_inventory_governance
     set status='MIGRATION_READY',
         reason='V41-C3R2B corrected V247 historical-import false-MIGRATED authority; migration authorization is not Single Authority cutover evidence',
         version=version+1,
         updated_at=now()
   where configuration_key in (
       'adapter-executor.batch-size',
       'adapter-executor.execution-timeout',
       'adapter-executor.initial-backoff',
       'adapter-executor.issue.link-projection-reconciliation-delay',
       'adapter-executor.max-attempts',
       'adapter-executor.max-backoff',
       'dispatch.retry.initial-backoff',
       'dispatch.retry.jitter-percent',
       'dispatch.retry.max-attempts',
       'dispatch.retry.max-backoff',
       'issue-projection.reconcile-batch-size',
       'issue-projection.reconcile-delay-ms',
       'issue-projection.retry-delay-seconds',
       'task.dispatch-recovery.initial-delay',
       'task.dispatch-recovery.interval-ms',
       'task.dispatch-recovery.max-attempts',
       'task.dispatch-recovery.max-batch-size',
       'task.dispatch-recovery.max-delay'
     )
     and status='MIGRATED'
     and classified_by='v40-history-import'
     and owner_reviewed_by='v40-history-import'
     and architecture_approved_by='v40-history-import'
     and migration_authorized_by='v40-history-import'
     and reason='Historical migration reconstructed from source-controlled authorized definition'
  returning configuration_key,source_observation_hash
 )
insert into runtime_config_inventory_governance_events(
  configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'CUTOVER_REOPENED','MIGRATED','MIGRATION_READY','v41-c3r2b-v247-history-correction',
       'V41-C3R2B corrected V247 historical-import false-MIGRATED authority; migration authorization is not Single Authority cutover evidence',
       source_observation_hash,
       '{"stage":"V41_C3R2B_V247_HISTORICAL_FALSE_MIGRATED_CORRECTION","reason":"historical-import-was-not-cutover-evidence","legacyAuthorityRetired":false}'::jsonb
  from reopened;

do $$
declare v_reopened integer;
begin
  select count(*) into v_reopened
    from runtime_config_inventory_governance
   where configuration_key in (
       'adapter-executor.batch-size',
       'adapter-executor.execution-timeout',
       'adapter-executor.initial-backoff',
       'adapter-executor.issue.link-projection-reconciliation-delay',
       'adapter-executor.max-attempts',
       'adapter-executor.max-backoff',
       'dispatch.retry.initial-backoff',
       'dispatch.retry.jitter-percent',
       'dispatch.retry.max-attempts',
       'dispatch.retry.max-backoff',
       'issue-projection.reconcile-batch-size',
       'issue-projection.reconcile-delay-ms',
       'issue-projection.retry-delay-seconds',
       'task.dispatch-recovery.initial-delay',
       'task.dispatch-recovery.interval-ms',
       'task.dispatch-recovery.max-attempts',
       'task.dispatch-recovery.max-batch-size',
       'task.dispatch-recovery.max-delay'
     )
     and status='MIGRATION_READY'
     and reason='V41-C3R2B corrected V247 historical-import false-MIGRATED authority; migration authorization is not Single Authority cutover evidence';
  if v_reopened<>18 then
    raise exception 'V41_C3R2B_V247_FALSE_MIGRATED_REOPEN_INCOMPLETE expected=18 actual=%',v_reopened;
  end if;
end $$;

select set_config('app.runtime_config_governance_reopen_stage','',true);

create or replace function guard_runtime_config_inventory_governance_transition() returns trigger language plpgsql as $$
begin
  if new.source_observation_hash is distinct from (select source_observation_hash from runtime_config_inventory_observations where configuration_key=new.configuration_key) then raise exception 'SOURCE_OBSERVATION_DRIFT'; end if;
  if old.status='DISCOVERED' and new.status not in ('DISCOVERED','CLASSIFIED') then raise exception 'CONFIGURATION_GOVERNANCE_TRANSITION_DENIED'; end if;
  if old.status='CLASSIFIED' and new.status not in ('CLASSIFIED','OWNER_REVIEWED') then raise exception 'CONFIGURATION_GOVERNANCE_TRANSITION_DENIED'; end if;
  if old.status='OWNER_REVIEWED' and new.status not in ('OWNER_REVIEWED','ARCHITECTURE_APPROVED') then raise exception 'CONFIGURATION_GOVERNANCE_TRANSITION_DENIED'; end if;
  if old.status='ARCHITECTURE_APPROVED' and new.status not in ('ARCHITECTURE_APPROVED','MIGRATION_READY') then raise exception 'CONFIGURATION_GOVERNANCE_TRANSITION_DENIED'; end if;
  if old.status='MIGRATION_READY' and new.status not in ('MIGRATION_READY','MIGRATED') then raise exception 'CONFIGURATION_GOVERNANCE_TRANSITION_DENIED'; end if;
  if old.status='MIGRATED' and new.status not in ('MIGRATED','LEGACY_RETIRED') then raise exception 'CONFIGURATION_GOVERNANCE_TRANSITION_DENIED'; end if;
  if old.status='LEGACY_RETIRED' and new.status<>'LEGACY_RETIRED' then raise exception 'CONFIGURATION_GOVERNANCE_TRANSITION_DENIED'; end if;
  new.updated_at=now(); return new;
end $$;

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='DURATION',
  risk='MEDIUM',
  mutability='HOT_IMMEDIATE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_IMMEDIATE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"minimum":"PT0.25S","maximum":"PT1H"}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"dispatch-routing","categoryDisplayName":"Dispatch Runtime & Delivery","categoryDescription":"Dispatch execution, gateway delivery, retry, failure recovery and runtime scheduling settings.","categoryOrder":20,"displayName":"Claim Lease","description":"Runtime-controlled Dispatch setting dispatch.claim-lease. Startup/YAML remains a pre-cutover fallback only until C3R3.","recommended":"Use the current proven production value as the initial Runtime Configuration seed.","effect":"New value applies to the next relevant Dispatch operation/request.","impactPositive":"Allows governed operational tuning without rebuilding the service.","impactTradeoff":"Incorrect values can affect delivery latency, gateway reachability or dispatch throughput; approval and rollout evidence remain required.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=6,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2b.json',
  source_fingerprint='aa2c467a47289e7682501f02edb7cb755a400877c33375345b2958054c8417c6', synchronized_at=now()
where definition_key='dispatch.claim-lease';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='LONG',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='DYNAMIC_SCHEDULER',
  target_mutability='HOT_NEXT_CYCLE',
  target_consumer_contract='DYNAMIC_SCHEDULER',
  validation_rule='{"minimum":250,"maximum":3600000}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"dispatch-routing","categoryDisplayName":"Dispatch Runtime & Delivery","categoryDescription":"Dispatch execution, gateway delivery, retry, failure recovery and runtime scheduling settings.","categoryOrder":20,"displayName":"Auto Execute Interval Ms","description":"Runtime-controlled Dispatch setting dispatch.client.auto-execute-interval-ms. Startup/YAML remains a pre-cutover fallback only until C3R3.","recommended":"Use the current proven production value as the initial Runtime Configuration seed.","effect":"New cadence applies to the next Dispatch scheduler cycle.","impactPositive":"Allows governed operational tuning without rebuilding the service.","impactTradeoff":"Incorrect values can affect delivery latency, gateway reachability or dispatch throughput; approval and rollout evidence remain required.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=6,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2b.json',
  source_fingerprint='5c907ac465b4be489dfa6b486384d95df03e028e13ba9d0a87d540114a10d4bd', synchronized_at=now()
where definition_key='dispatch.client.auto-execute-interval-ms';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='DURATION',
  risk='MEDIUM',
  mutability='HOT_IMMEDIATE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_IMMEDIATE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"minimum":"PT0.1S","maximum":"PT5M"}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"dispatch-routing","categoryDisplayName":"Dispatch Runtime & Delivery","categoryDescription":"Dispatch execution, gateway delivery, retry, failure recovery and runtime scheduling settings.","categoryOrder":20,"displayName":"Connect Timeout","description":"Runtime-controlled Dispatch setting dispatch.client.connect-timeout. Startup/YAML remains a pre-cutover fallback only until C3R3.","recommended":"Use the current proven production value as the initial Runtime Configuration seed.","effect":"New value applies to the next relevant Dispatch operation/request.","impactPositive":"Allows governed operational tuning without rebuilding the service.","impactTradeoff":"Incorrect values can affect delivery latency, gateway reachability or dispatch throughput; approval and rollout evidence remain required.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=6,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2b.json',
  source_fingerprint='9359bcf4fb9e1afe8033060ed22801a153caa95bcdf665cac6c0c90fba5fc440', synchronized_at=now()
where definition_key='dispatch.client.connect-timeout';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='URI',
  risk='MEDIUM',
  mutability='HOT_IMMEDIATE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_IMMEDIATE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"allowedSchemes":["http","https"],"maxLength":2048}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"dispatch-routing","categoryDisplayName":"Dispatch Runtime & Delivery","categoryDescription":"Dispatch execution, gateway delivery, retry, failure recovery and runtime scheduling settings.","categoryOrder":20,"displayName":"Default Gateway Base Url","description":"Runtime-controlled Dispatch setting dispatch.client.default-gateway-base-url. Startup/YAML remains a pre-cutover fallback only until C3R3.","recommended":"Use the current proven production value as the initial Runtime Configuration seed.","effect":"New value applies to the next relevant Dispatch operation/request.","impactPositive":"Allows governed operational tuning without rebuilding the service.","impactTradeoff":"Incorrect values can affect delivery latency, gateway reachability or dispatch throughput; approval and rollout evidence remain required.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=6,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2b.json',
  source_fingerprint='5df465ad93b59b9ee58255f0e2f969c2012801ff6574f34589b12e6b98f7238d', synchronized_at=now()
where definition_key='dispatch.client.default-gateway-base-url';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='STRING',
  risk='MEDIUM',
  mutability='HOT_IMMEDIATE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_IMMEDIATE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"allowedSchemes":["http","https"],"maxLength":2048}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"dispatch-routing","categoryDisplayName":"Dispatch Runtime & Delivery","categoryDescription":"Dispatch execution, gateway delivery, retry, failure recovery and runtime scheduling settings.","categoryOrder":20,"displayName":"Gateway Tnn 001","description":"Runtime-controlled Dispatch setting dispatch.client.gateway-base-urls.gateway-tnn-001. Startup/YAML remains a pre-cutover fallback only until C3R3.","recommended":"Use the current proven production value as the initial Runtime Configuration seed.","effect":"New value applies to the next relevant Dispatch operation/request.","impactPositive":"Allows governed operational tuning without rebuilding the service.","impactTradeoff":"Incorrect values can affect delivery latency, gateway reachability or dispatch throughput; approval and rollout evidence remain required.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=6,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2b.json',
  source_fingerprint='1320a617c1552a286530c1f2be1f2b4df8fffc0c6c91c4e2a95f84d5bee48a46', synchronized_at=now()
where definition_key='dispatch.client.gateway-base-urls.gateway-tnn-001';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='STRING',
  risk='MEDIUM',
  mutability='HOT_IMMEDIATE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_IMMEDIATE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"allowedSchemes":["http","https"],"maxLength":2048}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"dispatch-routing","categoryDisplayName":"Dispatch Runtime & Delivery","categoryDescription":"Dispatch execution, gateway delivery, retry, failure recovery and runtime scheduling settings.","categoryOrder":20,"displayName":"Gateway Tpe 001","description":"Runtime-controlled Dispatch setting dispatch.client.gateway-base-urls.gateway-tpe-001. Startup/YAML remains a pre-cutover fallback only until C3R3.","recommended":"Use the current proven production value as the initial Runtime Configuration seed.","effect":"New value applies to the next relevant Dispatch operation/request.","impactPositive":"Allows governed operational tuning without rebuilding the service.","impactTradeoff":"Incorrect values can affect delivery latency, gateway reachability or dispatch throughput; approval and rollout evidence remain required.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=6,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2b.json',
  source_fingerprint='8b28913345686f34b38d181433895e1ba558f90276b7d72cfa4633bbc046dc22', synchronized_at=now()
where definition_key='dispatch.client.gateway-base-urls.gateway-tpe-001';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='STRING',
  risk='MEDIUM',
  mutability='HOT_IMMEDIATE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_IMMEDIATE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"allowedSchemes":["http","https"],"maxLength":2048}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"dispatch-routing","categoryDisplayName":"Dispatch Runtime & Delivery","categoryDescription":"Dispatch execution, gateway delivery, retry, failure recovery and runtime scheduling settings.","categoryOrder":20,"displayName":"Gateway Tyn 001","description":"Runtime-controlled Dispatch setting dispatch.client.gateway-base-urls.gateway-tyn-001. Startup/YAML remains a pre-cutover fallback only until C3R3.","recommended":"Use the current proven production value as the initial Runtime Configuration seed.","effect":"New value applies to the next relevant Dispatch operation/request.","impactPositive":"Allows governed operational tuning without rebuilding the service.","impactTradeoff":"Incorrect values can affect delivery latency, gateway reachability or dispatch throughput; approval and rollout evidence remain required.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=6,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2b.json',
  source_fingerprint='c0246fb6b9782ff679d110b1c3fd9e4db15b95c2ad78c8ea6f0e5c13102e2246', synchronized_at=now()
where definition_key='dispatch.client.gateway-base-urls.gateway-tyn-001';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='INTEGER',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_NEXT_CYCLE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"minimum":1,"maximum":1000}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"dispatch-routing","categoryDisplayName":"Dispatch Runtime & Delivery","categoryDescription":"Dispatch execution, gateway delivery, retry, failure recovery and runtime scheduling settings.","categoryOrder":20,"displayName":"Max Batch Size","description":"Runtime-controlled Dispatch setting dispatch.client.max-batch-size. Startup/YAML remains a pre-cutover fallback only until C3R3.","recommended":"Use the current proven production value as the initial Runtime Configuration seed.","effect":"New value applies to the next relevant Dispatch operation/request.","impactPositive":"Allows governed operational tuning without rebuilding the service.","impactTradeoff":"Incorrect values can affect delivery latency, gateway reachability or dispatch throughput; approval and rollout evidence remain required.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=6,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2b.json',
  source_fingerprint='e79369bcb4dd784402fc2aa482367873c2118dcd3a278f83a99960ce7b6554ab', synchronized_at=now()
where definition_key='dispatch.client.max-batch-size';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='DURATION',
  risk='MEDIUM',
  mutability='HOT_IMMEDIATE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_IMMEDIATE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"minimum":"PT0.1S","maximum":"PT30M"}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"dispatch-routing","categoryDisplayName":"Dispatch Runtime & Delivery","categoryDescription":"Dispatch execution, gateway delivery, retry, failure recovery and runtime scheduling settings.","categoryOrder":20,"displayName":"Request Timeout","description":"Runtime-controlled Dispatch setting dispatch.client.request-timeout. Startup/YAML remains a pre-cutover fallback only until C3R3.","recommended":"Use the current proven production value as the initial Runtime Configuration seed.","effect":"New value applies to the next relevant Dispatch operation/request.","impactPositive":"Allows governed operational tuning without rebuilding the service.","impactTradeoff":"Incorrect values can affect delivery latency, gateway reachability or dispatch throughput; approval and rollout evidence remain required.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=6,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2b.json',
  source_fingerprint='279aa972e7ee093f24da9c115c06c555278af2c5b8cddb047fb6ddd1224009ac', synchronized_at=now()
where definition_key='dispatch.client.request-timeout';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='STRING',
  risk='HIGH',
  mutability='HOT_IMMEDIATE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_IMMEDIATE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"allowedValues":["AUTO_AFTER_ASSIGNMENT","PAUSED","MANUAL_HOLD"]}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"dispatch-routing","categoryDisplayName":"Dispatch Runtime & Delivery","categoryDescription":"Dispatch execution, gateway delivery, retry, failure recovery and runtime scheduling settings.","categoryOrder":20,"displayName":"Execution Policy","description":"Runtime-controlled Dispatch setting dispatch.execution-policy. Startup/YAML remains a pre-cutover fallback only until C3R3.","recommended":"Use the current proven production value as the initial Runtime Configuration seed.","effect":"New value applies to the next relevant Dispatch operation/request.","impactPositive":"Allows governed operational tuning without rebuilding the service.","impactTradeoff":"Incorrect values can affect delivery latency, gateway reachability or dispatch throughput; approval and rollout evidence remain required.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=6,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2b.json',
  source_fingerprint='758d607edcffe9eafd7bd3bef47f6d432b01e54258991de1ad64299a438ccd94', synchronized_at=now()
where definition_key='dispatch.execution-policy';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='BOOLEAN',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_NEXT_CYCLE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"dispatch-routing","displayName":"Runtime failure requeue enabled","description":"Runtime-controlled Dispatch setting dispatch.failure-requeue.enabled. Startup/YAML remains a pre-cutover fallback only until C3R3.","recommended":"Use the current proven production value as the initial Runtime Configuration seed.","effect":"New value applies to the next relevant Dispatch operation/request.","impactPositive":"Allows governed operational tuning without rebuilding the service.","impactTradeoff":"Incorrect values can affect delivery latency, gateway reachability or dispatch throughput; approval and rollout evidence remain required.","advancedKeyVisible":true,"categoryDisplayName":"Dispatch Runtime & Delivery","categoryDescription":"Dispatch execution, gateway delivery, retry, failure recovery and runtime scheduling settings.","categoryOrder":20}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=6,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2b.json',
  source_fingerprint='d7e10e1811fd6e38a659f58860e243eb20f20545ce6ab32ea5a1cf6cf63699f8', synchronized_at=now()
where definition_key='dispatch.failure-requeue.enabled';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='INTEGER',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_NEXT_CYCLE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"minimum":0,"maximum":20}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"dispatch-routing","displayName":"Runtime failure maximum reassignments","description":"Runtime-controlled Dispatch setting dispatch.failure-requeue.max-reassignments. Startup/YAML remains a pre-cutover fallback only until C3R3.","recommended":"Use the current proven production value as the initial Runtime Configuration seed.","effect":"New value applies to the next relevant Dispatch operation/request.","impactPositive":"Allows governed operational tuning without rebuilding the service.","impactTradeoff":"Incorrect values can affect delivery latency, gateway reachability or dispatch throughput; approval and rollout evidence remain required.","advancedKeyVisible":true,"categoryDisplayName":"Dispatch Runtime & Delivery","categoryDescription":"Dispatch execution, gateway delivery, retry, failure recovery and runtime scheduling settings.","categoryOrder":20}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=6,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2b.json',
  source_fingerprint='d9ff30717d9272ac701ba1a312e31a9afc7f917feb4294975b77c4d028f07cb3', synchronized_at=now()
where definition_key='dispatch.failure-requeue.max-reassignments';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='INTEGER',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_NEXT_CYCLE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"minimum":1,"maximum":100}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"dispatch-routing","displayName":"Poison Agent failure threshold","description":"Runtime-controlled Dispatch setting dispatch.failure-requeue.poison-agent-failure-threshold. Startup/YAML remains a pre-cutover fallback only until C3R3.","recommended":"Use the current proven production value as the initial Runtime Configuration seed.","effect":"New value applies to the next relevant Dispatch operation/request.","impactPositive":"Allows governed operational tuning without rebuilding the service.","impactTradeoff":"Incorrect values can affect delivery latency, gateway reachability or dispatch throughput; approval and rollout evidence remain required.","advancedKeyVisible":true,"categoryDisplayName":"Dispatch Runtime & Delivery","categoryDescription":"Dispatch execution, gateway delivery, retry, failure recovery and runtime scheduling settings.","categoryOrder":20}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=6,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2b.json',
  source_fingerprint='823f1195009057321584538308081bc068fb6752306c845ce0dd1fadd139fa05', synchronized_at=now()
where definition_key='dispatch.failure-requeue.poison-agent-failure-threshold';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='DURATION',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_NEXT_CYCLE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"minimum":"PT0.001S","maximum":"PT1H"}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"dispatch-routing","displayName":"Runtime failure initial backoff","description":"Runtime-controlled Dispatch setting dispatch.failure-requeue.runtime-initial-backoff. Startup/YAML remains a pre-cutover fallback only until C3R3.","recommended":"Use the current proven production value as the initial Runtime Configuration seed.","effect":"New value applies to the next relevant Dispatch operation/request.","impactPositive":"Allows governed operational tuning without rebuilding the service.","impactTradeoff":"Incorrect values can affect delivery latency, gateway reachability or dispatch throughput; approval and rollout evidence remain required.","advancedKeyVisible":true,"categoryDisplayName":"Dispatch Runtime & Delivery","categoryDescription":"Dispatch execution, gateway delivery, retry, failure recovery and runtime scheduling settings.","categoryOrder":20}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=6,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2b.json',
  source_fingerprint='4c5ad2d30dda54ac1f943ebd2496b9cb211d5b1c2ad7fd00b9b5a435fea21230', synchronized_at=now()
where definition_key='dispatch.failure-requeue.runtime-initial-backoff';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='INTEGER',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_NEXT_CYCLE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"minimum":0,"maximum":100}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"dispatch-routing","displayName":"Runtime failure requeue jitter","description":"Runtime-controlled Dispatch setting dispatch.failure-requeue.runtime-jitter-percent. Startup/YAML remains a pre-cutover fallback only until C3R3.","recommended":"Use the current proven production value as the initial Runtime Configuration seed.","effect":"New value applies to the next relevant Dispatch operation/request.","impactPositive":"Allows governed operational tuning without rebuilding the service.","impactTradeoff":"Incorrect values can affect delivery latency, gateway reachability or dispatch throughput; approval and rollout evidence remain required.","advancedKeyVisible":true,"categoryDisplayName":"Dispatch Runtime & Delivery","categoryDescription":"Dispatch execution, gateway delivery, retry, failure recovery and runtime scheduling settings.","categoryOrder":20}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=6,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2b.json',
  source_fingerprint='770d8563f62038062e5d77015fa9010b18cb7aededd720bdf0e4964c9bc25d38', synchronized_at=now()
where definition_key='dispatch.failure-requeue.runtime-jitter-percent';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='DURATION',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_NEXT_CYCLE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"minimum":"PT0.001S","maximum":"PT24H","greaterThanOrEqualKey":"dispatch.failure-requeue.runtime-initial-backoff"}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"dispatch-routing","displayName":"Runtime failure maximum backoff","description":"Runtime-controlled Dispatch setting dispatch.failure-requeue.runtime-max-backoff. Startup/YAML remains a pre-cutover fallback only until C3R3.","recommended":"Use the current proven production value as the initial Runtime Configuration seed.","effect":"New value applies to the next relevant Dispatch operation/request.","impactPositive":"Allows governed operational tuning without rebuilding the service.","impactTradeoff":"Incorrect values can affect delivery latency, gateway reachability or dispatch throughput; approval and rollout evidence remain required.","advancedKeyVisible":true,"categoryDisplayName":"Dispatch Runtime & Delivery","categoryDescription":"Dispatch execution, gateway delivery, retry, failure recovery and runtime scheduling settings.","categoryOrder":20}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=6,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2b.json',
  source_fingerprint='43b89a67476ce110727cd49cb70c47fb71b558f2617e663b2d442b3d76117f28', synchronized_at=now()
where definition_key='dispatch.failure-requeue.runtime-max-backoff';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='STRING',
  risk='MEDIUM',
  mutability='HOT_IMMEDIATE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_IMMEDIATE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"minLength":1,"maxLength":512,"pattern":"^/"}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"dispatch-routing","categoryDisplayName":"Dispatch Runtime & Delivery","categoryDescription":"Dispatch execution, gateway delivery, retry, failure recovery and runtime scheduling settings.","categoryOrder":20,"displayName":"Gateway Dispatch Path","description":"Runtime-controlled Dispatch setting dispatch.gateway-dispatch-path. Startup/YAML remains a pre-cutover fallback only until C3R3.","recommended":"Use the current proven production value as the initial Runtime Configuration seed.","effect":"New value applies to the next relevant Dispatch operation/request.","impactPositive":"Allows governed operational tuning without rebuilding the service.","impactTradeoff":"Incorrect values can affect delivery latency, gateway reachability or dispatch throughput; approval and rollout evidence remain required.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=6,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2b.json',
  source_fingerprint='68b9688891b5e4bdc6d2fc3a8944748dfde97ddd63b6755978f15d2207f7e6a4', synchronized_at=now()
where definition_key='dispatch.gateway-dispatch-path';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='BOOLEAN',
  risk='MEDIUM',
  mutability='HOT_IMMEDIATE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_IMMEDIATE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"type":"boolean"}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"dispatch-routing","categoryDisplayName":"Dispatch Runtime & Delivery","categoryDescription":"Dispatch execution, gateway delivery, retry, failure recovery and runtime scheduling settings.","categoryOrder":20,"displayName":"Require Assignable Agent","description":"Runtime-controlled Dispatch setting dispatch.require-assignable-agent. Startup/YAML remains a pre-cutover fallback only until C3R3.","recommended":"Use the current proven production value as the initial Runtime Configuration seed.","effect":"New value applies to the next relevant Dispatch operation/request.","impactPositive":"Allows governed operational tuning without rebuilding the service.","impactTradeoff":"Incorrect values can affect delivery latency, gateway reachability or dispatch throughput; approval and rollout evidence remain required.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=6,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2b.json',
  source_fingerprint='1e20f67c232064e56386b0830251d1c96be80ebc2bd93b020dcb5cc7168a3c80', synchronized_at=now()
where definition_key='dispatch.require-assignable-agent';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='BOOLEAN',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_NEXT_CYCLE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"dispatch-routing","displayName":"Dispatch retry enabled","description":"Runtime-controlled Dispatch setting dispatch.retry.enabled. Startup/YAML remains a pre-cutover fallback only until C3R3.","recommended":"Use the current proven production value as the initial Runtime Configuration seed.","effect":"New value applies to the next relevant Dispatch operation/request.","impactPositive":"Allows governed operational tuning without rebuilding the service.","impactTradeoff":"Incorrect values can affect delivery latency, gateway reachability or dispatch throughput; approval and rollout evidence remain required.","advancedKeyVisible":true,"categoryDisplayName":"Dispatch Runtime & Delivery","categoryDescription":"Dispatch execution, gateway delivery, retry, failure recovery and runtime scheduling settings.","categoryOrder":20}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=6,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2b.json',
  source_fingerprint='994d51b9cf4ab43ea5f3db8b4300d1b587651966885ed529be4f838d813aac9a', synchronized_at=now()
where definition_key='dispatch.retry.enabled';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='DURATION',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_NEXT_CYCLE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"minimum":"PT0.001S","maximum":"PT1H"}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"dispatch-routing","displayName":"Dispatch initial retry delay","description":"Runtime-controlled Dispatch setting dispatch.retry.initial-backoff. Startup/YAML remains a pre-cutover fallback only until C3R3.","recommended":"Use the current proven production value as the initial Runtime Configuration seed.","effect":"New value applies to the next relevant Dispatch operation/request.","impactPositive":"Allows governed operational tuning without rebuilding the service.","impactTradeoff":"Incorrect values can affect delivery latency, gateway reachability or dispatch throughput; approval and rollout evidence remain required.","advancedKeyVisible":true,"categoryDisplayName":"Dispatch Runtime & Delivery","categoryDescription":"Dispatch execution, gateway delivery, retry, failure recovery and runtime scheduling settings.","categoryOrder":20}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=6,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2b.json',
  source_fingerprint='5532ccc9ccb6f53bdf3eecea1f42517c329a4628799646f00bbbef61c2acc0fa', synchronized_at=now()
where definition_key='dispatch.retry.initial-backoff';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='INTEGER',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_NEXT_CYCLE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"minimum":0,"maximum":100}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"dispatch-routing","displayName":"Dispatch retry jitter","description":"Runtime-controlled Dispatch setting dispatch.retry.jitter-percent. Startup/YAML remains a pre-cutover fallback only until C3R3.","recommended":"Use the current proven production value as the initial Runtime Configuration seed.","effect":"New value applies to the next relevant Dispatch operation/request.","impactPositive":"Allows governed operational tuning without rebuilding the service.","impactTradeoff":"Incorrect values can affect delivery latency, gateway reachability or dispatch throughput; approval and rollout evidence remain required.","advancedKeyVisible":true,"categoryDisplayName":"Dispatch Runtime & Delivery","categoryDescription":"Dispatch execution, gateway delivery, retry, failure recovery and runtime scheduling settings.","categoryOrder":20}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=6,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2b.json',
  source_fingerprint='fe81a414a68a1a81340d158bb1edb2e5b454ebdebfa5e0e8a4349de4938d702a', synchronized_at=now()
where definition_key='dispatch.retry.jitter-percent';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='INTEGER',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_NEXT_CYCLE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"minimum":1,"maximum":20}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"dispatch-routing","displayName":"Maximum dispatch retry attempts","description":"Runtime-controlled Dispatch setting dispatch.retry.max-attempts. Startup/YAML remains a pre-cutover fallback only until C3R3.","recommended":"Use the current proven production value as the initial Runtime Configuration seed.","effect":"New value applies to the next relevant Dispatch operation/request.","impactPositive":"Allows governed operational tuning without rebuilding the service.","impactTradeoff":"Incorrect values can affect delivery latency, gateway reachability or dispatch throughput; approval and rollout evidence remain required.","advancedKeyVisible":true,"categoryDisplayName":"Dispatch Runtime & Delivery","categoryDescription":"Dispatch execution, gateway delivery, retry, failure recovery and runtime scheduling settings.","categoryOrder":20}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=6,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2b.json',
  source_fingerprint='b910b4fadca0e4e9d112638ea4fc747b95c84e7b4b777a67b8a8122310bf49c9', synchronized_at=now()
where definition_key='dispatch.retry.max-attempts';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='DURATION',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_NEXT_CYCLE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"minimum":"PT0.001S","maximum":"PT24H","greaterThanOrEqualKey":"dispatch.retry.initial-backoff"}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"dispatch-routing","displayName":"Dispatch maximum retry delay","description":"Runtime-controlled Dispatch setting dispatch.retry.max-backoff. Startup/YAML remains a pre-cutover fallback only until C3R3.","recommended":"Use the current proven production value as the initial Runtime Configuration seed.","effect":"New value applies to the next relevant Dispatch operation/request.","impactPositive":"Allows governed operational tuning without rebuilding the service.","impactTradeoff":"Incorrect values can affect delivery latency, gateway reachability or dispatch throughput; approval and rollout evidence remain required.","advancedKeyVisible":true,"categoryDisplayName":"Dispatch Runtime & Delivery","categoryDescription":"Dispatch execution, gateway delivery, retry, failure recovery and runtime scheduling settings.","categoryOrder":20}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=6,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2b.json',
  source_fingerprint='9fc6d760af6285c66d1e45bf8fbeabc5643fc6eb9db3f6dc4bdcfa5b28212f9e', synchronized_at=now()
where definition_key='dispatch.retry.max-backoff';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='STRING',
  risk='MEDIUM',
  mutability='HOT_IMMEDIATE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_IMMEDIATE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"allowedValues":["NOT_REQUIRED","AUTO_APPROVE","MANUAL_REVIEW","DISABLED"]}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"dispatch-routing","categoryDisplayName":"Dispatch Runtime & Delivery","categoryDescription":"Dispatch execution, gateway delivery, retry, failure recovery and runtime scheduling settings.","categoryOrder":20,"displayName":"Review Mode","description":"Runtime-controlled Dispatch setting dispatch.review-mode. Startup/YAML remains a pre-cutover fallback only until C3R3.","recommended":"Use the current proven production value as the initial Runtime Configuration seed.","effect":"New value applies to the next relevant Dispatch operation/request.","impactPositive":"Allows governed operational tuning without rebuilding the service.","impactTradeoff":"Incorrect values can affect delivery latency, gateway reachability or dispatch throughput; approval and rollout evidence remain required.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=6,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2b.json',
  source_fingerprint='076599be46ee66e8d0c573ca241045ab4fa97d54eec0b8b36d0b538ef81ed6fb', synchronized_at=now()
where definition_key='dispatch.review-mode';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='STRING',
  risk='MEDIUM',
  mutability='HOT_IMMEDIATE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_IMMEDIATE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"minLength":1,"maxLength":128}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"dispatch-routing","categoryDisplayName":"Dispatch Runtime & Delivery","categoryDescription":"Dispatch execution, gateway delivery, retry, failure recovery and runtime scheduling settings.","categoryOrder":20,"displayName":"Source Node Id","description":"Runtime-controlled Dispatch setting dispatch.source-node-id. Startup/YAML remains a pre-cutover fallback only until C3R3.","recommended":"Use the current proven production value as the initial Runtime Configuration seed.","effect":"New value applies to the next relevant Dispatch operation/request.","impactPositive":"Allows governed operational tuning without rebuilding the service.","impactTradeoff":"Incorrect values can affect delivery latency, gateway reachability or dispatch throughput; approval and rollout evidence remain required.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=6,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2b.json',
  source_fingerprint='759034a755525b579cdc40ebd885fa34f6554be59fc6025b105a594ea7a823a2', synchronized_at=now()
where definition_key='dispatch.source-node-id';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='STRING',
  risk='MEDIUM',
  mutability='HOT_IMMEDIATE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_IMMEDIATE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"minLength":1,"maxLength":128}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"dispatch-routing","categoryDisplayName":"Dispatch Runtime & Delivery","categoryDescription":"Dispatch execution, gateway delivery, retry, failure recovery and runtime scheduling settings.","categoryOrder":20,"displayName":"Worker Id","description":"Runtime-controlled Dispatch setting dispatch.worker-id. Startup/YAML remains a pre-cutover fallback only until C3R3.","recommended":"Use the current proven production value as the initial Runtime Configuration seed.","effect":"New value applies to the next relevant Dispatch operation/request.","impactPositive":"Allows governed operational tuning without rebuilding the service.","impactTradeoff":"Incorrect values can affect delivery latency, gateway reachability or dispatch throughput; approval and rollout evidence remain required.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=6,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2b.json',
  source_fingerprint='735575e5356a90d0b1a30670972c2509f2053f1c11132a9301e831f045e36911', synchronized_at=now()
where definition_key='dispatch.worker-id';

-- Legal governance progression only for the 15 newly implemented Dispatch consumers.
with advanced as (
 update runtime_config_inventory_governance g set status='CLASSIFIED',domain_owner=d.domain_owner,authority_class=d.authority_class,scope=d.scope,risk=d.risk,mutability=d.target_mutability,consumer_contract=d.target_consumer_contract,admin_editable=d.admin_editable,requires_approval=d.requires_approval,classified_by='v41-c3r2b-dispatch-consumer-closure',classified_at=now(),reason='V41-C3R2B Dispatch typed consumer implemented',version=version+1
 from runtime_config_definitions d where g.configuration_key=d.definition_key and g.configuration_key in (
    'dispatch.claim-lease',
    'dispatch.client.auto-execute-interval-ms',
    'dispatch.client.connect-timeout',
    'dispatch.client.default-gateway-base-url',
    'dispatch.client.gateway-base-urls.gateway-tnn-001',
    'dispatch.client.gateway-base-urls.gateway-tpe-001',
    'dispatch.client.gateway-base-urls.gateway-tyn-001',
    'dispatch.client.max-batch-size',
    'dispatch.client.request-timeout',
    'dispatch.execution-policy',
    'dispatch.gateway-dispatch-path',
    'dispatch.require-assignable-agent',
    'dispatch.review-mode',
    'dispatch.source-node-id',
    'dispatch.worker-id'
 ) and g.status='DISCOVERED' returning g.configuration_key,g.source_observation_hash
)
insert into runtime_config_inventory_governance_events(configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'CLASSIFIED','DISCOVERED','CLASSIFIED','v41-c3r2b-dispatch-consumer-closure','Dispatch consumer classification confirmed by implemented typed runtime view',source_observation_hash,'{"stage":"V41_C3R2B_DISPATCH_DOMAIN_CONSUMER_CLOSURE"}'::jsonb from advanced;

with advanced as (
 update runtime_config_inventory_governance set status='OWNER_REVIEWED',owner_reviewed_by='dispatch-domain-owner',owner_reviewed_at=now(),reason='Dispatch domain consumer and validation bounds reviewed',version=version+1 where configuration_key in (
    'dispatch.claim-lease',
    'dispatch.client.auto-execute-interval-ms',
    'dispatch.client.connect-timeout',
    'dispatch.client.default-gateway-base-url',
    'dispatch.client.gateway-base-urls.gateway-tnn-001',
    'dispatch.client.gateway-base-urls.gateway-tpe-001',
    'dispatch.client.gateway-base-urls.gateway-tyn-001',
    'dispatch.client.max-batch-size',
    'dispatch.client.request-timeout',
    'dispatch.execution-policy',
    'dispatch.gateway-dispatch-path',
    'dispatch.require-assignable-agent',
    'dispatch.review-mode',
    'dispatch.source-node-id',
    'dispatch.worker-id'
 ) and status='CLASSIFIED' returning configuration_key,source_observation_hash
)
insert into runtime_config_inventory_governance_events(configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'OWNER_REVIEWED','CLASSIFIED','OWNER_REVIEWED','dispatch-domain-owner','Dispatch domain consumer and validation bounds reviewed',source_observation_hash,'{}'::jsonb from advanced;

with advanced as (
 update runtime_config_inventory_governance set status='ARCHITECTURE_APPROVED',architecture_approved_by='configuration-architecture-reviewer',architecture_approved_at=now(),reason='DB authority + Redis distribution + authenticated local snapshot architecture approved',version=version+1 where configuration_key in (
    'dispatch.claim-lease',
    'dispatch.client.auto-execute-interval-ms',
    'dispatch.client.connect-timeout',
    'dispatch.client.default-gateway-base-url',
    'dispatch.client.gateway-base-urls.gateway-tnn-001',
    'dispatch.client.gateway-base-urls.gateway-tpe-001',
    'dispatch.client.gateway-base-urls.gateway-tyn-001',
    'dispatch.client.max-batch-size',
    'dispatch.client.request-timeout',
    'dispatch.execution-policy',
    'dispatch.gateway-dispatch-path',
    'dispatch.require-assignable-agent',
    'dispatch.review-mode',
    'dispatch.source-node-id',
    'dispatch.worker-id'
 ) and status='OWNER_REVIEWED' returning configuration_key,source_observation_hash
)
insert into runtime_config_inventory_governance_events(configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'ARCHITECTURE_APPROVED','OWNER_REVIEWED','ARCHITECTURE_APPROVED','configuration-architecture-reviewer','DB authority + Redis distribution + authenticated local snapshot architecture approved',source_observation_hash,'{}'::jsonb from advanced;

with advanced as (
 update runtime_config_inventory_governance set status='MIGRATION_READY',migration_authorized_by='v41-c3r2b-dispatch-consumer-closure',migration_authorized_at=now(),reason='Typed Dispatch consumers implemented; Single Authority cutover remains pending',version=version+1 where configuration_key in (
    'dispatch.claim-lease',
    'dispatch.client.auto-execute-interval-ms',
    'dispatch.client.connect-timeout',
    'dispatch.client.default-gateway-base-url',
    'dispatch.client.gateway-base-urls.gateway-tnn-001',
    'dispatch.client.gateway-base-urls.gateway-tpe-001',
    'dispatch.client.gateway-base-urls.gateway-tyn-001',
    'dispatch.client.max-batch-size',
    'dispatch.client.request-timeout',
    'dispatch.execution-policy',
    'dispatch.gateway-dispatch-path',
    'dispatch.require-assignable-agent',
    'dispatch.review-mode',
    'dispatch.source-node-id',
    'dispatch.worker-id'
 ) and status='ARCHITECTURE_APPROVED' returning configuration_key,source_observation_hash
)
insert into runtime_config_inventory_governance_events(configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'MIGRATION_READY','ARCHITECTURE_APPROVED','MIGRATION_READY','v41-c3r2b-dispatch-consumer-closure','Typed Dispatch consumers implemented; Single Authority cutover remains pending',source_observation_hash,'{"cutover":"NOT_FINALIZED"}'::jsonb from advanced;

-- Closure guards. This stage proves consumer readiness only, never Single Authority cutover.
do $$ declare v_ready integer; begin
  select count(*) into v_ready from runtime_config_definitions where definition_key in (
    'dispatch.claim-lease',
    'dispatch.client.auto-execute-interval-ms',
    'dispatch.client.connect-timeout',
    'dispatch.client.default-gateway-base-url',
    'dispatch.client.gateway-base-urls.gateway-tnn-001',
    'dispatch.client.gateway-base-urls.gateway-tpe-001',
    'dispatch.client.gateway-base-urls.gateway-tyn-001',
    'dispatch.client.max-batch-size',
    'dispatch.client.request-timeout',
    'dispatch.execution-policy',
    'dispatch.failure-requeue.enabled',
    'dispatch.failure-requeue.max-reassignments',
    'dispatch.failure-requeue.poison-agent-failure-threshold',
    'dispatch.failure-requeue.runtime-initial-backoff',
    'dispatch.failure-requeue.runtime-jitter-percent',
    'dispatch.failure-requeue.runtime-max-backoff',
    'dispatch.gateway-dispatch-path',
    'dispatch.require-assignable-agent',
    'dispatch.retry.enabled',
    'dispatch.retry.initial-backoff',
    'dispatch.retry.jitter-percent',
    'dispatch.retry.max-attempts',
    'dispatch.retry.max-backoff',
    'dispatch.review-mode',
    'dispatch.source-node-id',
    'dispatch.worker-id'
  ) and review_status='MIGRATION_READY' and migration_authorized=true and admin_editable=true;
  if v_ready<>26 then raise exception 'C3R2B_DISPATCH_DEFINITION_CLOSURE_INCOMPLETE expected=26 actual=%',v_ready; end if;
  if exists(select 1 from runtime_config_inventory_governance where configuration_key in (
    'dispatch.claim-lease',
    'dispatch.client.auto-execute-interval-ms',
    'dispatch.client.connect-timeout',
    'dispatch.client.default-gateway-base-url',
    'dispatch.client.gateway-base-urls.gateway-tnn-001',
    'dispatch.client.gateway-base-urls.gateway-tpe-001',
    'dispatch.client.gateway-base-urls.gateway-tyn-001',
    'dispatch.client.max-batch-size',
    'dispatch.client.request-timeout',
    'dispatch.execution-policy',
    'dispatch.failure-requeue.enabled',
    'dispatch.failure-requeue.max-reassignments',
    'dispatch.failure-requeue.poison-agent-failure-threshold',
    'dispatch.failure-requeue.runtime-initial-backoff',
    'dispatch.failure-requeue.runtime-jitter-percent',
    'dispatch.failure-requeue.runtime-max-backoff',
    'dispatch.gateway-dispatch-path',
    'dispatch.require-assignable-agent',
    'dispatch.retry.enabled',
    'dispatch.retry.initial-backoff',
    'dispatch.retry.jitter-percent',
    'dispatch.retry.max-attempts',
    'dispatch.retry.max-backoff',
    'dispatch.review-mode',
    'dispatch.source-node-id',
    'dispatch.worker-id'
  ) and status in ('MIGRATED','LEGACY_RETIRED')) then raise exception 'C3R2B_PREMATURE_SINGLE_AUTHORITY_CUTOVER'; end if;
  if not exists(select 1 from runtime_config_definitions where definition_key='dispatch.client.auto-execute-interval-ms' and consumer_contract='DYNAMIC_SCHEDULER' and mutability='HOT_NEXT_CYCLE') then raise exception 'C3R2B_DYNAMIC_SCHEDULER_CONTRACT_MISSING'; end if;
end $$;
