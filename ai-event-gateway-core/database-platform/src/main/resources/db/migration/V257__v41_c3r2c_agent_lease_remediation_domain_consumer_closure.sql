-- V41-C3R2C: Agent / Lease / Remediation Domain Consumer Closure.
-- Four real runtime consumers become MIGRATION_READY; four existing remediation consumers are normalized.
-- Two Agent authentication enforcement switches are corrected to SECURITY_INVARIANT.
-- Five status-only remediation alert values are removed from Runtime target scope until an execution consumer exists.
-- No key is advanced to MIGRATED; C3R3 Single Authority cutover remains mandatory.

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='INTEGER',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='DYNAMIC_SCHEDULER',
  target_mutability='HOT_NEXT_CYCLE',
  target_consumer_contract='DYNAMIC_SCHEDULER',
  validation_rule='{"minimum":1000,"maximum":3600000}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"agent-directory","categoryDisplayName":"Agent Directory","categoryDescription":"Agent directory lease expiry and cleanup runtime policy.","categoryOrder":1080,"displayName":"Fixed Delay","description":"Runtime-controlled setting agent-directory.lease-reaper.fixed-delay. YAML/ENV remains a pre-cutover fallback only until C3R3.","recommended":"Seed the Runtime Configuration revision from the current proven startup value.","effect":"New cadence applies on the next scheduler cycle.","impactPositive":"Allows governed operational tuning without service rebuild.","impactTradeoff":"Incorrect values can affect Agent availability detection, lease cleanup, or remediation telemetry.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=7,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2c.json',
  source_fingerprint='856e4c75684463803aa527f1b55128fb224bde81337b97684bfb43d975afbdc5', synchronized_at=now()
where definition_key='agent-directory.lease-reaper.fixed-delay';

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
  ui_metadata='{"categoryId":"agent-remediation","displayName":"Stale remediation lease recovery enabled","description":"Controls scheduled recovery of expired Agent remediation workflow execution leases.","recommended":"Enabled in normal operation","effect":"Next scheduler cycle","impactPositive":"Recovers abandoned workflow execution leases automatically.","impactTradeoff":"Disabling leaves expired leases for manual recovery.","advancedKeyVisible":true,"categoryDisplayName":"Agent Remediation","categoryDescription":"Agent remediation workflow recovery controls.","categoryOrder":60}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=7,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2c.json',
  source_fingerprint='68490e4d124e0381b5a517aab477d51c52d08bfef3bd85c489034579f816545d', synchronized_at=now()
where definition_key='agent-remediation.workflow.stale-lease-reaper.enabled';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='LONG',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='DYNAMIC_SCHEDULER',
  target_mutability='HOT_NEXT_CYCLE',
  target_consumer_contract='DYNAMIC_SCHEDULER',
  validation_rule='{"minimum":1000,"maximum":86400000}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"agent-remediation","displayName":"Stale lease recovery interval","description":"Delay between completed stale remediation lease recovery scans.","recommended":"30000–300000","effect":"Next scheduler cycle","impactPositive":"Shorter intervals recover abandoned workflow leases sooner.","impactTradeoff":"Very short intervals add unnecessary database scanning load.","advancedKeyVisible":true,"categoryDisplayName":"Agent Remediation","categoryDescription":"Agent remediation workflow recovery controls.","categoryOrder":60}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=7,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2c.json',
  source_fingerprint='a850cc6af1b80950350c0334494a5c5d2f44b03bb6586c9df089ac8ed343d7b6', synchronized_at=now()
where definition_key='agent-remediation.workflow.stale-lease-reaper.fixed-delay-ms';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='LONG',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='DYNAMIC_SCHEDULER',
  target_mutability='HOT_NEXT_CYCLE',
  target_consumer_contract='DYNAMIC_SCHEDULER',
  validation_rule='{"minimum":1000,"maximum":86400000}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"agent-remediation","displayName":"Stale lease recovery initial delay","description":"Initial delay before the first stale remediation lease recovery scan after process startup.","recommended":"10000–120000","effect":"Next scheduler lifecycle start","impactPositive":"Allows dependencies to stabilize before recovery begins.","impactTradeoff":"Long values postpone recovery after restart.","advancedKeyVisible":true,"categoryDisplayName":"Agent Remediation","categoryDescription":"Agent remediation workflow recovery controls.","categoryOrder":60}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=7,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2c.json',
  source_fingerprint='f73c8a5d3b3c67a0480f39aaef0c5a93dfef09b4dc4113ddb1c2b162c10eabd2', synchronized_at=now()
where definition_key='agent-remediation.workflow.stale-lease-reaper.initial-delay-ms';

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
  ui_metadata='{"categoryId":"agent-remediation","displayName":"Stale lease recovery batch limit","description":"Maximum expired remediation workflow leases processed per scan.","recommended":"25–200","effect":"Next recovery scan","impactPositive":"Larger values clear accumulated expired leases faster.","impactTradeoff":"Very large batches can create recovery bursts.","advancedKeyVisible":true,"categoryDisplayName":"Agent Remediation","categoryDescription":"Agent remediation workflow recovery controls.","categoryOrder":60}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=7,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2c.json',
  source_fingerprint='6f3d94aaabeee6bb33bf2950080f89b4f3cc04f95e2abdf02b892131edbad6bc', synchronized_at=now()
where definition_key='agent-remediation.workflow.stale-lease-reaper.limit';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='LONG',
  risk='MEDIUM',
  mutability='HOT_IMMEDIATE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_IMMEDIATE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"minimum":5,"maximum":3600}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"agent-lifecycle","categoryDisplayName":"Agent Lifecycle","categoryDescription":"Agent transport heartbeat and timeout detection runtime policy.","categoryOrder":1070,"displayName":"Heartbeat Timeout Seconds","description":"Runtime-controlled setting agent.heartbeat-timeout-seconds. YAML/ENV remains a pre-cutover fallback only until C3R3.","recommended":"Seed the Runtime Configuration revision from the current proven startup value.","effect":"New value applies to the next relevant runtime operation.","impactPositive":"Allows governed operational tuning without service rebuild.","impactTradeoff":"Incorrect values can affect Agent availability detection, lease cleanup, or remediation telemetry.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=7,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2c.json',
  source_fingerprint='3e4f067087ebc0ec6e013f4a11a653cffcab149b9ffd6e46965db9bf94aff344', synchronized_at=now()
where definition_key='agent.heartbeat-timeout-seconds';

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
  ui_metadata='{"categoryId":"agent-lifecycle","categoryDisplayName":"Agent Lifecycle","categoryDescription":"Agent transport heartbeat and timeout detection runtime policy.","categoryOrder":1070,"displayName":"Timeout Scan Interval Ms","description":"Runtime-controlled setting agent.timeout-scan-interval-ms. YAML/ENV remains a pre-cutover fallback only until C3R3.","recommended":"Seed the Runtime Configuration revision from the current proven startup value.","effect":"New cadence applies on the next scheduler cycle.","impactPositive":"Allows governed operational tuning without service rebuild.","impactTradeoff":"Incorrect values can affect Agent availability detection, lease cleanup, or remediation telemetry.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=7,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2c.json',
  source_fingerprint='a73f6cc070bc0fee0dc80dce40a26b693b3df862116d192047235b07cf4756e3', synchronized_at=now()
where definition_key='agent.timeout-scan-interval-ms';

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
  ui_metadata='{"categoryId":"agent-remediation-observability","categoryDisplayName":"Agent Remediation Observability","categoryDescription":"Runtime enablement for low-cardinality remediation workflow metrics.","categoryOrder":1171,"displayName":"Enabled","description":"Runtime-controlled setting core.observability.remediation-workflow-metrics.enabled. YAML/ENV remains a pre-cutover fallback only until C3R3.","recommended":"Seed the Runtime Configuration revision from the current proven startup value.","effect":"New value applies to the next relevant runtime operation.","impactPositive":"Allows governed operational tuning without service rebuild.","impactTradeoff":"Incorrect values can affect Agent availability detection, lease cleanup, or remediation telemetry.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=7,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2c.json',
  source_fingerprint='f5243adc33106f4f63135a1bcdc574755246983cf761bb118dc77ec7f8bc2501', synchronized_at=now()
where definition_key='core.observability.remediation-workflow-metrics.enabled';

update runtime_config_definitions set authority_class='SECURITY_INVARIANT', risk='CRITICAL', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', target_mutability='RESTART_REQUIRED', target_consumer_contract='STARTUP_BINDING', review_status='RETIRED', migration_authorized=false, admin_editable=false, validation_rule='{"type":"boolean","classificationCorrection":"SECURITY_ENFORCEMENT_SWITCH"}'::jsonb, source_ref='contracts/current/configuration/definitions/definition-registry-v41-c3r2c.json', source_fingerprint='6c1ac44419df874665abe47966d5b418cc1fa636798d4722a58bafbea28380c8', synchronized_at=now() where definition_key='agent.auth-enabled';

update runtime_config_definitions set authority_class='SECURITY_INVARIANT', risk='CRITICAL', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', target_mutability='RESTART_REQUIRED', target_consumer_contract='STARTUP_BINDING', review_status='RETIRED', migration_authorized=false, admin_editable=false, validation_rule='{"type":"boolean","classificationCorrection":"SECURITY_ENFORCEMENT_SWITCH"}'::jsonb, source_ref='contracts/current/configuration/definitions/definition-registry-v41-c3r2c.json', source_fingerprint='2a1c06dcced0984d8b5533aff7be1675882e91d7be9c49375e37b05409ab337d', synchronized_at=now() where definition_key='agent.web-socket-handshake-auth-enabled';

update runtime_config_definitions set authority_class='DOMAIN_CONFIG', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', target_mutability='RESTART_REQUIRED', target_consumer_contract='STARTUP_BINDING', review_status='RETIRED', migration_authorized=false, admin_editable=false, validation_rule='{"classificationCorrection":"NO_EXECUTION_CONSUMER","evidence":"Repository usage is status/read-model only; no alert evaluator consumes this value."}'::jsonb, source_ref='contracts/current/configuration/definitions/definition-registry-v41-c3r2c.json', source_fingerprint='fe4b9856c86874300fe3539e79df3d896824230a2ba7293a20fe77809e17cd05', synchronized_at=now() where definition_key='core.observability.remediation-workflow-metrics.action-failure-ratio-critical';

update runtime_config_definitions set authority_class='DOMAIN_CONFIG', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', target_mutability='RESTART_REQUIRED', target_consumer_contract='STARTUP_BINDING', review_status='RETIRED', migration_authorized=false, admin_editable=false, validation_rule='{"classificationCorrection":"NO_EXECUTION_CONSUMER","evidence":"Repository usage is status/read-model only; no alert evaluator consumes this value."}'::jsonb, source_ref='contracts/current/configuration/definitions/definition-registry-v41-c3r2c.json', source_fingerprint='42259887565a8c7b86274a2a1abd49ae9ad19b9a39da68b5cb93edfe947219d1', synchronized_at=now() where definition_key='core.observability.remediation-workflow-metrics.action-failure-ratio-warning';

update runtime_config_definitions set authority_class='DOMAIN_CONFIG', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', target_mutability='RESTART_REQUIRED', target_consumer_contract='STARTUP_BINDING', review_status='RETIRED', migration_authorized=false, admin_editable=false, validation_rule='{"classificationCorrection":"NO_EXECUTION_CONSUMER","evidence":"Repository usage is status/read-model only; no alert evaluator consumes this value."}'::jsonb, source_ref='contracts/current/configuration/definitions/definition-registry-v41-c3r2c.json', source_fingerprint='8a0350881ca32e19e81510f836dcc435f3b835f7a65751afe606111dd437a994', synchronized_at=now() where definition_key='core.observability.remediation-workflow-metrics.approval-latency-critical';

update runtime_config_definitions set authority_class='DOMAIN_CONFIG', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', target_mutability='RESTART_REQUIRED', target_consumer_contract='STARTUP_BINDING', review_status='RETIRED', migration_authorized=false, admin_editable=false, validation_rule='{"classificationCorrection":"NO_EXECUTION_CONSUMER","evidence":"Repository usage is status/read-model only; no alert evaluator consumes this value."}'::jsonb, source_ref='contracts/current/configuration/definitions/definition-registry-v41-c3r2c.json', source_fingerprint='fce9e6d12f20005ca6ec66bb242d52f11d98d629d01aaa5ff102edec1d98c725', synchronized_at=now() where definition_key='core.observability.remediation-workflow-metrics.approval-latency-warning';

update runtime_config_definitions set authority_class='DOMAIN_CONFIG', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', target_mutability='RESTART_REQUIRED', target_consumer_contract='STARTUP_BINDING', review_status='RETIRED', migration_authorized=false, admin_editable=false, validation_rule='{"classificationCorrection":"NO_EXECUTION_CONSUMER","evidence":"Repository usage is status/read-model only; no alert evaluator consumes this value."}'::jsonb, source_ref='contracts/current/configuration/definitions/definition-registry-v41-c3r2c.json', source_fingerprint='e75ec542965b6e58d3eade79348bdbf3e992ef2fb6f212bb23c8dc70168bc068', synchronized_at=now() where definition_key='core.observability.remediation-workflow-metrics.stale-lease-alert-window';

with advanced as (
 update runtime_config_inventory_governance g set status='CLASSIFIED',domain_owner=d.domain_owner,authority_class=d.authority_class,scope=d.scope,risk=d.risk,mutability=d.target_mutability,consumer_contract=d.target_consumer_contract,admin_editable=d.admin_editable,requires_approval=d.requires_approval,classified_by='v41-c3r2c-agent-remediation-consumer-closure',classified_at=now(),reason='V41-C3R2C typed runtime consumer implemented',version=version+1
 from runtime_config_definitions d where g.configuration_key=d.definition_key and g.configuration_key in (
    'agent-directory.lease-reaper.fixed-delay',
    'agent.heartbeat-timeout-seconds',
    'agent.timeout-scan-interval-ms',
    'core.observability.remediation-workflow-metrics.enabled'
 ) and g.status='DISCOVERED' returning g.configuration_key,g.source_observation_hash
)
insert into runtime_config_inventory_governance_events(configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'CLASSIFIED','DISCOVERED','CLASSIFIED','v41-c3r2c-agent-remediation-consumer-closure','Consumer classification confirmed by implemented typed runtime view',source_observation_hash,'{"stage":"V41_C3R2C_AGENT_LEASE_REMEDIATION_DOMAIN_CONSUMER_CLOSURE"}'::jsonb from advanced;

with advanced as (
 update runtime_config_inventory_governance set status='OWNER_REVIEWED',owner_reviewed_by='agent-remediation-domain-owner',owner_reviewed_at=now(),reason='Agent/lease/remediation consumer and validation bounds reviewed',version=version+1 where configuration_key in (
    'agent-directory.lease-reaper.fixed-delay',
    'agent.heartbeat-timeout-seconds',
    'agent.timeout-scan-interval-ms',
    'core.observability.remediation-workflow-metrics.enabled'
 ) and status='CLASSIFIED' returning configuration_key,source_observation_hash
)
insert into runtime_config_inventory_governance_events(configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'OWNER_REVIEWED','CLASSIFIED','OWNER_REVIEWED','agent-remediation-domain-owner','Agent/lease/remediation consumer and validation bounds reviewed',source_observation_hash,'{"cutover":"NOT_FINALIZED"}'::jsonb from advanced;

with advanced as (
 update runtime_config_inventory_governance set status='ARCHITECTURE_APPROVED',architecture_approved_by='configuration-architecture-reviewer',architecture_approved_at=now(),reason='DB authority + Redis distribution + authenticated local snapshot architecture approved',version=version+1 where configuration_key in (
    'agent-directory.lease-reaper.fixed-delay',
    'agent.heartbeat-timeout-seconds',
    'agent.timeout-scan-interval-ms',
    'core.observability.remediation-workflow-metrics.enabled'
 ) and status='OWNER_REVIEWED' returning configuration_key,source_observation_hash
)
insert into runtime_config_inventory_governance_events(configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'ARCHITECTURE_APPROVED','OWNER_REVIEWED','ARCHITECTURE_APPROVED','configuration-architecture-reviewer','DB authority + Redis distribution + authenticated local snapshot architecture approved',source_observation_hash,'{"cutover":"NOT_FINALIZED"}'::jsonb from advanced;

with advanced as (
 update runtime_config_inventory_governance set status='MIGRATION_READY',migration_authorized_by='v41-c3r2c-agent-remediation-consumer-closure',migration_authorized_at=now(),reason='Typed consumers implemented; Single Authority cutover remains pending',version=version+1 where configuration_key in (
    'agent-directory.lease-reaper.fixed-delay',
    'agent.heartbeat-timeout-seconds',
    'agent.timeout-scan-interval-ms',
    'core.observability.remediation-workflow-metrics.enabled'
 ) and status='ARCHITECTURE_APPROVED' returning configuration_key,source_observation_hash
)
insert into runtime_config_inventory_governance_events(configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'MIGRATION_READY','ARCHITECTURE_APPROVED','MIGRATION_READY','v41-c3r2c-agent-remediation-consumer-closure','Typed consumers implemented; Single Authority cutover remains pending',source_observation_hash,'{"cutover":"NOT_FINALIZED"}'::jsonb from advanced;

update runtime_config_inventory_governance set domain_owner='AGENT', authority_class='SECURITY_INVARIANT', risk='CRITICAL', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', admin_editable=false, requires_approval=true, reason='Agent authentication enforcement is a startup Security Invariant, not an admin-editable runtime toggle', version=version+1, updated_at=now() where configuration_key='agent.auth-enabled' and status in ('DISCOVERED','CLASSIFIED');

update runtime_config_inventory_governance set domain_owner='AGENT', authority_class='SECURITY_INVARIANT', risk='CRITICAL', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', admin_editable=false, requires_approval=true, reason='Agent authentication enforcement is a startup Security Invariant, not an admin-editable runtime toggle', version=version+1, updated_at=now() where configuration_key='agent.web-socket-handshake-auth-enabled' and status in ('DISCOVERED','CLASSIFIED');

update runtime_config_inventory_governance set authority_class='DOMAIN_CONFIG', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', admin_editable=false, reason='No alert evaluator/runtime decision consumer exists; status-only metadata is not Runtime Configuration', version=version+1, updated_at=now() where configuration_key='core.observability.remediation-workflow-metrics.action-failure-ratio-critical' and status in ('DISCOVERED','CLASSIFIED');

update runtime_config_inventory_governance set authority_class='DOMAIN_CONFIG', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', admin_editable=false, reason='No alert evaluator/runtime decision consumer exists; status-only metadata is not Runtime Configuration', version=version+1, updated_at=now() where configuration_key='core.observability.remediation-workflow-metrics.action-failure-ratio-warning' and status in ('DISCOVERED','CLASSIFIED');

update runtime_config_inventory_governance set authority_class='DOMAIN_CONFIG', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', admin_editable=false, reason='No alert evaluator/runtime decision consumer exists; status-only metadata is not Runtime Configuration', version=version+1, updated_at=now() where configuration_key='core.observability.remediation-workflow-metrics.approval-latency-critical' and status in ('DISCOVERED','CLASSIFIED');

update runtime_config_inventory_governance set authority_class='DOMAIN_CONFIG', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', admin_editable=false, reason='No alert evaluator/runtime decision consumer exists; status-only metadata is not Runtime Configuration', version=version+1, updated_at=now() where configuration_key='core.observability.remediation-workflow-metrics.approval-latency-warning' and status in ('DISCOVERED','CLASSIFIED');

update runtime_config_inventory_governance set authority_class='DOMAIN_CONFIG', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', admin_editable=false, reason='No alert evaluator/runtime decision consumer exists; status-only metadata is not Runtime Configuration', version=version+1, updated_at=now() where configuration_key='core.observability.remediation-workflow-metrics.stale-lease-alert-window' and status in ('DISCOVERED','CLASSIFIED');

do $$ declare v_ready integer; begin
  select count(*) into v_ready from runtime_config_definitions where definition_key in (
    'agent-directory.lease-reaper.fixed-delay',
    'agent-remediation.workflow.stale-lease-reaper.enabled',
    'agent-remediation.workflow.stale-lease-reaper.fixed-delay-ms',
    'agent-remediation.workflow.stale-lease-reaper.initial-delay-ms',
    'agent-remediation.workflow.stale-lease-reaper.limit',
    'agent.heartbeat-timeout-seconds',
    'agent.timeout-scan-interval-ms',
    'core.observability.remediation-workflow-metrics.enabled'
  ) and review_status='MIGRATION_READY' and migration_authorized=true and admin_editable=true;
  if v_ready<>8 then raise exception 'C3R2C_DOMAIN_DEFINITION_CLOSURE_INCOMPLETE expected=8 actual=%',v_ready; end if;
  if exists(select 1 from runtime_config_definitions where definition_key in ('agent.auth-enabled','agent.web-socket-handshake-auth-enabled','core.observability.remediation-workflow-metrics.action-failure-ratio-critical','core.observability.remediation-workflow-metrics.action-failure-ratio-warning','core.observability.remediation-workflow-metrics.approval-latency-critical','core.observability.remediation-workflow-metrics.approval-latency-warning','core.observability.remediation-workflow-metrics.stale-lease-alert-window') and (migration_authorized=true or admin_editable=true or review_status<>'RETIRED')) then raise exception 'C3R2C_RECLASSIFIED_AUTHORITY_LEAK'; end if;
  if exists(select 1 from runtime_config_inventory_governance where configuration_key in (
    'agent-directory.lease-reaper.fixed-delay',
    'agent-remediation.workflow.stale-lease-reaper.enabled',
    'agent-remediation.workflow.stale-lease-reaper.fixed-delay-ms',
    'agent-remediation.workflow.stale-lease-reaper.initial-delay-ms',
    'agent-remediation.workflow.stale-lease-reaper.limit',
    'agent.heartbeat-timeout-seconds',
    'agent.timeout-scan-interval-ms',
    'core.observability.remediation-workflow-metrics.enabled'
  ) and status in ('MIGRATED','LEGACY_RETIRED')) then raise exception 'C3R2C_PREMATURE_SINGLE_AUTHORITY_CUTOVER'; end if;
end $$;
