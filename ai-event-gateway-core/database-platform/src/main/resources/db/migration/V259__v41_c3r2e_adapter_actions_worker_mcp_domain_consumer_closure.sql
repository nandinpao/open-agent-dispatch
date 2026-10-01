-- V41-C3R2E: Adapter Actions / Worker / MCP Domain Consumer Closure.
-- Nine real consumers become MIGRATION_READY; nine misclassified keys leave Runtime target scope.
-- Adapter Action config-set identity is normalized to canonical RUNTIME/ADAPTER_ACTION/SYSTEM.
-- No key advances to MIGRATED; C3R3 Single Authority cutover remains mandatory.

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  risk='MEDIUM',
  mutability='HOT_IMMEDIATE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_IMMEDIATE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"type":"boolean"}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"adapter-actions","categoryDisplayName":"Adapter Actions","categoryDescription":"Adapter Actions runtime configuration definitions and migration status.","categoryOrder":1020,"displayName":"Create Suppressed Records","description":"adapter-actions.create-suppressed-records is backed by a typed runtime consumer and is migration-ready. Runtime snapshot wins before authority cutover.","recommended":"Review current effective value before consumer migration.","effect":"Runtime snapshot effective; startup fallback remains legal until C3R3 cutover","impactPositive":"Makes the target runtime setting visible and traceable before authority cutover.","impactTradeoff":"Changing this definition does not change runtime behavior until migration is authorized.","advancedKeyVisible":true}'::jsonb,
  config_set_key='RUNTIME/ADAPTER_ACTION/SYSTEM',
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=9,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2e.json',
  source_fingerprint='9121487aca3b59539fa8f07e65f1f3fac5a55516fbebc262d052162dade9575c', synchronized_at=now()
where definition_key='adapter-actions.create-suppressed-records';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  risk='MEDIUM',
  mutability='HOT_IMMEDIATE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_IMMEDIATE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"type":"string","minLength":1,"maxLength":128}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"adapter-actions","categoryDisplayName":"Adapter Actions","categoryDescription":"Adapter Actions runtime configuration definitions and migration status.","categoryOrder":1020,"displayName":"Adapter Name","description":"adapter-actions.issue.adapter-name is backed by a typed runtime consumer and is migration-ready. Runtime snapshot wins before authority cutover.","recommended":"Review current effective value before consumer migration.","effect":"Runtime snapshot effective; startup fallback remains legal until C3R3 cutover","impactPositive":"Makes the target runtime setting visible and traceable before authority cutover.","impactTradeoff":"Changing this definition does not change runtime behavior until migration is authorized.","advancedKeyVisible":true}'::jsonb,
  config_set_key='RUNTIME/ADAPTER_ACTION/SYSTEM',
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=9,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2e.json',
  source_fingerprint='80d8396df68b05cb91242b5396aa21556c411a7fa4613daf3395d7845439355d', synchronized_at=now()
where definition_key='adapter-actions.issue.adapter-name';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  risk='MEDIUM',
  mutability='HOT_IMMEDIATE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_IMMEDIATE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"type":"string","minLength":1,"maxLength":128}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"adapter-action-mcp","categoryDisplayName":"Adapter Action MCP","categoryDescription":"MCP action orchestration runtime controls.","categoryOrder":52,"displayName":"Adapter Name","description":"adapter-actions.mcp.adapter-name is backed by a typed runtime consumer and is migration-ready. Runtime snapshot wins before authority cutover.","recommended":"Review current effective value before consumer migration.","effect":"Runtime snapshot effective; startup fallback remains legal until C3R3 cutover","impactPositive":"Makes the target runtime setting visible and traceable before authority cutover.","impactTradeoff":"Changing this definition does not change runtime behavior until migration is authorized.","advancedKeyVisible":true}'::jsonb,
  config_set_key='RUNTIME/ADAPTER_ACTION/SYSTEM',
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=9,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2e.json',
  source_fingerprint='bf949d62b278712a6f5850a3f343cca310a366bf76f793eee8aae91f87e16670', synchronized_at=now()
where definition_key='adapter-actions.mcp.adapter-name';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  risk='MEDIUM',
  mutability='HOT_IMMEDIATE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_IMMEDIATE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"adapter-action-mcp","displayName":"MCP adapter action enabled","description":"adapter-actions.mcp.enabled is backed by a typed runtime consumer and is migration-ready. Runtime snapshot wins before authority cutover.","recommended":"Enable only where MCP post-task orchestration is intentionally active","effect":"Runtime snapshot effective; startup fallback remains legal until C3R3 cutover","impactPositive":"Allows MCP post-task enrichment to be enabled or paused without restarting Core.","impactTradeoff":"Disabling prevents new MCP adapter actions while existing persisted actions remain unchanged.","advancedKeyVisible":true,"categoryDisplayName":"Adapter Action MCP","categoryDescription":"MCP action orchestration runtime controls.","categoryOrder":52}'::jsonb,
  config_set_key='RUNTIME/ADAPTER_ACTION/SYSTEM',
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=9,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2e.json',
  source_fingerprint='45c891be100dbdcf0a3d4b8599c7a1c3721aa4b9000cc7d1024dd13452d31844', synchronized_at=now()
where definition_key='adapter-actions.mcp.enabled';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  risk='MEDIUM',
  mutability='HOT_IMMEDIATE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_IMMEDIATE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"adapter-action-mcp","displayName":"One MCP action per task","description":"adapter-actions.mcp.one-per-task is backed by a typed runtime consumer and is migration-ready. Runtime snapshot wins before authority cutover.","recommended":"Enabled for deterministic one-action-per-task behavior","effect":"Runtime snapshot effective; startup fallback remains legal until C3R3 cutover","impactPositive":"Prevents duplicate MCP work for repeated evaluation of the same task.","impactTradeoff":"Disabling permits distinct MCP actions for repeated task evaluation and increases downstream workload.","advancedKeyVisible":true,"categoryDisplayName":"Adapter Action MCP","categoryDescription":"MCP action orchestration runtime controls.","categoryOrder":52}'::jsonb,
  config_set_key='RUNTIME/ADAPTER_ACTION/SYSTEM',
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=9,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2e.json',
  source_fingerprint='141b24956a972c9a6df790296bf35bfb04289e50ad6155822b676f91b872c9af', synchronized_at=now()
where definition_key='adapter-actions.mcp.one-per-task';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  risk='MEDIUM',
  mutability='HOT_IMMEDIATE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_IMMEDIATE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"adapter-action-mcp","displayName":"Run MCP action after completed task","description":"adapter-actions.mcp.run-on-completed-task is backed by a typed runtime consumer and is migration-ready. Runtime snapshot wins before authority cutover.","recommended":"Enabled when completed-task MCP enrichment is required","effect":"Runtime snapshot effective; startup fallback remains legal until C3R3 cutover","impactPositive":"Lets operators pause or resume completed-task MCP orchestration without restart.","impactTradeoff":"Disabling skips MCP enrichment for newly evaluated completed tasks.","advancedKeyVisible":true,"categoryDisplayName":"Adapter Action MCP","categoryDescription":"MCP action orchestration runtime controls.","categoryOrder":52}'::jsonb,
  config_set_key='RUNTIME/ADAPTER_ACTION/SYSTEM',
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=9,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2e.json',
  source_fingerprint='22b3fed977f197143bf72b63268068557c5e3c7cf853b5de1040b6d3b42e1fcc', synchronized_at=now()
where definition_key='adapter-actions.mcp.run-on-completed-task';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  risk='MEDIUM',
  mutability='HOT_IMMEDIATE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_IMMEDIATE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"adapter-action-mcp","displayName":"Run MCP action after failed task","description":"adapter-actions.mcp.run-on-failed-task is backed by a typed runtime consumer and is migration-ready. Runtime snapshot wins before authority cutover.","recommended":"Disabled unless failure-context MCP processing is explicitly required","effect":"Runtime snapshot effective; startup fallback remains legal until C3R3 cutover","impactPositive":"Allows failure-context enrichment to be enabled without restart.","impactTradeoff":"Enabling may increase MCP workload during incident bursts and failed-task storms.","advancedKeyVisible":true,"categoryDisplayName":"Adapter Action MCP","categoryDescription":"MCP action orchestration runtime controls.","categoryOrder":52}'::jsonb,
  config_set_key='RUNTIME/ADAPTER_ACTION/SYSTEM',
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=9,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2e.json',
  source_fingerprint='b9293dd870f6dc961dd75a2e95aa455856e0f1ff2025286245186f8f46fa2783', synchronized_at=now()
where definition_key='adapter-actions.mcp.run-on-failed-task';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_NEXT_CYCLE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"minimum":1,"maximum":1000}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"adapter-action-worker","displayName":"Expired worker lease recovery batch size","description":"adapter-actions.worker.expired-lease-scan-batch-size is backed by a typed runtime consumer and is migration-ready. Runtime snapshot wins before authority cutover.","recommended":"25–200","effect":"Runtime snapshot effective; startup fallback remains legal until C3R3 cutover","impactPositive":"Larger batches can clear accumulated expired leases faster.","impactTradeoff":"Very large batches increase database and recovery burst load.","advancedKeyVisible":true,"categoryDisplayName":"Adapter Action Worker","categoryDescription":"External worker retry and lease recovery controls.","categoryOrder":51}'::jsonb,
  config_set_key='RUNTIME/ADAPTER_ACTION/SYSTEM',
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=9,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2e.json',
  source_fingerprint='234af133c178056a61c519df2239b4b494800d807fe3e2df136a87cd5943ba43', synchronized_at=now()
where definition_key='adapter-actions.worker.expired-lease-scan-batch-size';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='DYNAMIC_SCHEDULER',
  target_mutability='HOT_NEXT_CYCLE',
  target_consumer_contract='DYNAMIC_SCHEDULER',
  validation_rule='{"type":"integer","minimum":250,"maximum":3600000,"unit":"ms"}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"adapter-action-worker","categoryDisplayName":"Adapter Action Worker","categoryDescription":"External worker retry and lease recovery controls.","categoryOrder":51,"displayName":"Expired Lease Scan Interval Ms","description":"adapter-actions.worker.expired-lease-scan-interval-ms is backed by a typed runtime consumer and is migration-ready. Runtime snapshot wins before authority cutover.","recommended":"Review current effective value before consumer migration.","effect":"Runtime snapshot effective; startup fallback remains legal until C3R3 cutover","impactPositive":"Makes the target runtime setting visible and traceable before authority cutover.","impactTradeoff":"Changing this definition does not change runtime behavior until migration is authorized.","advancedKeyVisible":true}'::jsonb,
  config_set_key='RUNTIME/ADAPTER_ACTION/SYSTEM',
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=9,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2e.json',
  source_fingerprint='9ea41a2c116059cc2d87ac2a7031d90e2c5d6b40afc7137e0d0f5cd8ee49ab38', synchronized_at=now()
where definition_key='adapter-actions.worker.expired-lease-scan-interval-ms';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_NEXT_CYCLE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"minimum":"PT0.001S","maximum":"PT1H"}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"adapter-action-worker","displayName":"Adapter worker initial retry delay","description":"adapter-actions.worker.initial-backoff is backed by a typed runtime consumer and is migration-ready. Runtime snapshot wins before authority cutover.","recommended":"PT5S–PT1M","effect":"Runtime snapshot effective; startup fallback remains legal until C3R3 cutover","impactPositive":"Provides backpressure while allowing transient failures to recover quickly.","impactTradeoff":"Long delays slow recovery; very short delays can create retry pressure.","advancedKeyVisible":true,"categoryDisplayName":"Adapter Action Worker","categoryDescription":"External worker retry and lease recovery controls.","categoryOrder":51}'::jsonb,
  config_set_key='RUNTIME/ADAPTER_ACTION/SYSTEM',
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=9,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2e.json',
  source_fingerprint='1c44af1c82c9c75fd856058c73839e5d883131c7ce6f9e87c1800616d1da88d9', synchronized_at=now()
where definition_key='adapter-actions.worker.initial-backoff';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_NEXT_CYCLE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"minimum":1,"maximum":20}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"adapter-action-worker","displayName":"Adapter worker maximum attempts","description":"adapter-actions.worker.max-attempts is backed by a typed runtime consumer and is migration-ready. Runtime snapshot wins before authority cutover.","recommended":"2–5","effect":"Runtime snapshot effective; startup fallback remains legal until C3R3 cutover","impactPositive":"More attempts improve resilience to transient worker/provider failures.","impactTradeoff":"Higher values delay terminal failure and can increase provider load.","advancedKeyVisible":true,"categoryDisplayName":"Adapter Action Worker","categoryDescription":"External worker retry and lease recovery controls.","categoryOrder":51}'::jsonb,
  config_set_key='RUNTIME/ADAPTER_ACTION/SYSTEM',
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=9,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2e.json',
  source_fingerprint='d50c2b12897fcaafd29ac8f1dfd46674caccd909c17540d92ac165f7155200ed', synchronized_at=now()
where definition_key='adapter-actions.worker.max-attempts';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_NEXT_CYCLE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"minimum":"PT0.001S","maximum":"PT24H","greaterThanOrEqualKey":"adapter-actions.worker.initial-backoff"}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"adapter-action-worker","displayName":"Adapter worker maximum retry delay","description":"adapter-actions.worker.max-backoff is backed by a typed runtime consumer and is migration-ready. Runtime snapshot wins before authority cutover.","recommended":"At least the initial delay; typically PT1M–PT15M","effect":"Runtime snapshot effective; startup fallback remains legal until C3R3 cutover","impactPositive":"Caps retry pressure during longer worker or provider outages.","impactTradeoff":"Large values delay recovery after the external dependency becomes healthy.","advancedKeyVisible":true,"categoryDisplayName":"Adapter Action Worker","categoryDescription":"External worker retry and lease recovery controls.","categoryOrder":51}'::jsonb,
  config_set_key='RUNTIME/ADAPTER_ACTION/SYSTEM',
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=9,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2e.json',
  source_fingerprint='89c91fefab1a8fa8507bc5044704132b387d77e436b0a729a0b34acb172d8e88', synchronized_at=now()
where definition_key='adapter-actions.worker.max-backoff';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_NEXT_CYCLE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"adapter-action-worker","displayName":"Adapter worker retry enabled","description":"adapter-actions.worker.retry-enabled is backed by a typed runtime consumer and is migration-ready. Runtime snapshot wins before authority cutover.","recommended":"Enabled unless an operator is intentionally stopping automatic retries","effect":"Runtime snapshot effective; startup fallback remains legal until C3R3 cutover","impactPositive":"Allows transient worker/provider failures to recover without manual intervention.","impactTradeoff":"Disabling retries increases manual recovery work and can leave recoverable actions failed.","advancedKeyVisible":true,"categoryDisplayName":"Adapter Action Worker","categoryDescription":"External worker retry and lease recovery controls.","categoryOrder":51}'::jsonb,
  config_set_key='RUNTIME/ADAPTER_ACTION/SYSTEM',
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=9,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2e.json',
  source_fingerprint='38f25c0c3a7705e7752c4f640149aeb9fe7b0ecd4d2d30f4361f309ed537427d', synchronized_at=now()
where definition_key='adapter-actions.worker.retry-enabled';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_NEXT_CYCLE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"type":"string","allowedCsvValues":["MCP"],"minItems":1}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"adapter-worker","categoryDisplayName":"Adapter Worker","categoryDescription":"Adapter Worker runtime configuration definitions and migration status.","categoryOrder":1040,"displayName":"Adapter Types","description":"adapter-worker.adapter-types is backed by a typed runtime consumer and is migration-ready. Runtime snapshot wins before authority cutover.","recommended":"Review current effective value before consumer migration.","effect":"Runtime snapshot effective; startup fallback remains legal until C3R3 cutover","impactPositive":"Makes the target runtime setting visible and traceable before authority cutover.","impactTradeoff":"Changing this definition does not change runtime behavior until migration is authorized.","advancedKeyVisible":true}'::jsonb,
  config_set_key='RUNTIME/ADAPTER_WORKER/SYSTEM',
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=9,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2e.json',
  source_fingerprint='1630d95d6822aced991bd213d1befb9865913356a3a2121ec234154bb5ab3fd2', synchronized_at=now()
where definition_key='adapter-worker.adapter-types';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_NEXT_CYCLE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"type":"boolean"}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"adapter-worker","categoryDisplayName":"Adapter Worker","categoryDescription":"Adapter Worker runtime configuration definitions and migration status.","categoryOrder":1040,"displayName":"Enabled","description":"adapter-worker.enabled is backed by a typed runtime consumer and is migration-ready. Runtime snapshot wins before authority cutover.","recommended":"Review current effective value before consumer migration.","effect":"Runtime snapshot effective; startup fallback remains legal until C3R3 cutover","impactPositive":"Makes the target runtime setting visible and traceable before authority cutover.","impactTradeoff":"Changing this definition does not change runtime behavior until migration is authorized.","advancedKeyVisible":true}'::jsonb,
  config_set_key='RUNTIME/ADAPTER_WORKER/SYSTEM',
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=9,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2e.json',
  source_fingerprint='25e9316f81106c73774a8fc1403bcd014511822bc72725528726a9070efe9da4', synchronized_at=now()
where definition_key='adapter-worker.enabled';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  risk='MEDIUM',
  mutability='HOT_IMMEDIATE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_IMMEDIATE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"type":"integer","minimum":10,"maximum":3600,"unit":"seconds"}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"adapter-worker","categoryDisplayName":"Adapter Worker","categoryDescription":"Adapter Worker runtime configuration definitions and migration status.","categoryOrder":1040,"displayName":"Lease Seconds","description":"adapter-worker.lease-seconds is backed by a typed runtime consumer and is migration-ready. Runtime snapshot wins before authority cutover.","recommended":"Review current effective value before consumer migration.","effect":"Runtime snapshot effective; startup fallback remains legal until C3R3 cutover","impactPositive":"Makes the target runtime setting visible and traceable before authority cutover.","impactTradeoff":"Changing this definition does not change runtime behavior until migration is authorized.","advancedKeyVisible":true}'::jsonb,
  config_set_key='RUNTIME/ADAPTER_WORKER/SYSTEM',
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=9,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2e.json',
  source_fingerprint='b320191aee52f3188493a4b4462fb22b47ce5d03f36d9ebe30cfe2eda027b118', synchronized_at=now()
where definition_key='adapter-worker.lease-seconds';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='DYNAMIC_SCHEDULER',
  target_mutability='HOT_NEXT_CYCLE',
  target_consumer_contract='DYNAMIC_SCHEDULER',
  validation_rule='{"type":"integer","minimum":250,"maximum":3600000,"unit":"ms"}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"adapter-worker","categoryDisplayName":"Adapter Worker","categoryDescription":"Adapter Worker runtime configuration definitions and migration status.","categoryOrder":1040,"displayName":"Poll Interval Ms","description":"adapter-worker.poll-interval-ms is backed by a typed runtime consumer and is migration-ready. Runtime snapshot wins before authority cutover.","recommended":"Review current effective value before consumer migration.","effect":"Runtime snapshot effective; startup fallback remains legal until C3R3 cutover","impactPositive":"Makes the target runtime setting visible and traceable before authority cutover.","impactTradeoff":"Changing this definition does not change runtime behavior until migration is authorized.","advancedKeyVisible":true}'::jsonb,
  config_set_key='RUNTIME/ADAPTER_WORKER/SYSTEM',
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=9,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2e.json',
  source_fingerprint='ce3a66c788810598467becdc70e2aa04212e8f797f99727921b0ff32641ae638', synchronized_at=now()
where definition_key='adapter-worker.poll-interval-ms';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  risk='MEDIUM',
  mutability='HOT_IMMEDIATE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_IMMEDIATE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"type":"duration","minimum":"PT1S","maximum":"PT5M"}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"adapter-worker","categoryDisplayName":"Adapter Worker","categoryDescription":"Adapter Worker runtime configuration definitions and migration status.","categoryOrder":1040,"displayName":"Request Timeout","description":"adapter-worker.request-timeout is backed by a typed runtime consumer and is migration-ready. Runtime snapshot wins before authority cutover.","recommended":"Review current effective value before consumer migration.","effect":"Runtime snapshot effective; startup fallback remains legal until C3R3 cutover","impactPositive":"Makes the target runtime setting visible and traceable before authority cutover.","impactTradeoff":"Changing this definition does not change runtime behavior until migration is authorized.","advancedKeyVisible":true}'::jsonb,
  config_set_key='RUNTIME/ADAPTER_WORKER/SYSTEM',
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=9,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2e.json',
  source_fingerprint='4ac22986e6f04a1faf0f3bc43fc3473a7014b8381c2bb6013b8f6c43957b1257', synchronized_at=now()
where definition_key='adapter-worker.request-timeout';

update runtime_config_definitions set authority_class='SECURITY_INVARIANT', risk='CRITICAL', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', target_mutability='RESTART_REQUIRED', target_consumer_contract='STARTUP_BINDING', review_status='RETIRED', migration_authorized=false, admin_editable=false, validation_rule='{"reviewRequired":true,"baseline":"C3R1_STRUCTURAL_DEFINITION_ONLY","type":"boolean"}'::jsonb, source_ref='contracts/current/configuration/definitions/definition-registry-v41-c3r2e.json', source_fingerprint='06613ee81e485551b120407e5adad06b571b9f42f09af3f38bcb528b21336eaf', synchronized_at=now() where definition_key='adapter-actions.issue.legacy-write-enabled';

update runtime_config_definitions set authority_class='SECURITY_INVARIANT', risk='CRITICAL', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', target_mutability='RESTART_REQUIRED', target_consumer_contract='STARTUP_BINDING', review_status='RETIRED', migration_authorized=false, admin_editable=false, validation_rule='{"reviewRequired":true,"baseline":"C3R1_STRUCTURAL_DEFINITION_ONLY","type":"boolean"}'::jsonb, source_ref='contracts/current/configuration/definitions/definition-registry-v41-c3r2e.json', source_fingerprint='aef0d956cd003c1832789ad0fcd7758a96f6b07c7ed3487fadf4d72f0e29d966', synchronized_at=now() where definition_key='core.decision.issue-action-enabled';

update runtime_config_definitions set authority_class='SECURITY_INVARIANT', risk='CRITICAL', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', target_mutability='RESTART_REQUIRED', target_consumer_contract='STARTUP_BINDING', review_status='RETIRED', migration_authorized=false, admin_editable=false, validation_rule='{"reviewRequired":true,"baseline":"C3R1_STRUCTURAL_DEFINITION_ONLY","type":"boolean"}'::jsonb, source_ref='contracts/current/configuration/definitions/definition-registry-v41-c3r2e.json', source_fingerprint='6a6f4ea6173b667d83db34408f03b974d096f99b5365bad205ffac85f3c00603', synchronized_at=now() where definition_key='core.decision.mcp-action-enabled';

update runtime_config_definitions set authority_class='DOMAIN_CONFIG', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', target_mutability='RESTART_REQUIRED', target_consumer_contract='STARTUP_BINDING', review_status='RETIRED', migration_authorized=false, admin_editable=false, validation_rule='{"reviewRequired":true,"baseline":"C3R1_STRUCTURAL_DEFINITION_ONLY","type":"boolean"}'::jsonb, source_ref='contracts/current/configuration/definitions/definition-registry-v41-c3r2e.json', source_fingerprint='d58d5cbf354ad94588f1c6b9bb6f61ffa8c1eeb1178744866710c7e35c1e5b13', synchronized_at=now() where definition_key='adapter-actions.issue.create-on-completed-task';

update runtime_config_definitions set authority_class='DOMAIN_CONFIG', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', target_mutability='RESTART_REQUIRED', target_consumer_contract='STARTUP_BINDING', review_status='RETIRED', migration_authorized=false, admin_editable=false, validation_rule='{"reviewRequired":true,"baseline":"C3R1_STRUCTURAL_DEFINITION_ONLY","type":"boolean"}'::jsonb, source_ref='contracts/current/configuration/definitions/definition-registry-v41-c3r2e.json', source_fingerprint='eb6c992e68cdf45cb1654d76c6a302e463548c42bfd4552dc89b7e5bc4fdf325', synchronized_at=now() where definition_key='adapter-actions.issue.create-on-failed-task';

update runtime_config_definitions set authority_class='DOMAIN_CONFIG', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', target_mutability='RESTART_REQUIRED', target_consumer_contract='STARTUP_BINDING', review_status='RETIRED', migration_authorized=false, admin_editable=false, validation_rule='{"reviewRequired":true,"baseline":"C3R1_STRUCTURAL_DEFINITION_ONLY","type":"boolean"}'::jsonb, source_ref='contracts/current/configuration/definitions/definition-registry-v41-c3r2e.json', source_fingerprint='d1f17ed4417e1fe3c615f9f9bc578ae53d0be11b0afcf512912e3137cd9aa7fb', synchronized_at=now() where definition_key='adapter-actions.issue.enabled';

update runtime_config_definitions set authority_class='DOMAIN_CONFIG', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', target_mutability='RESTART_REQUIRED', target_consumer_contract='STARTUP_BINDING', review_status='RETIRED', migration_authorized=false, admin_editable=false, validation_rule='{"reviewRequired":true,"baseline":"C3R1_STRUCTURAL_DEFINITION_ONLY","type":"boolean"}'::jsonb, source_ref='contracts/current/configuration/definitions/definition-registry-v41-c3r2e.json', source_fingerprint='5bd255db0e2273e1030e3f2e2b729bd698cb82f180179eb55fd8ff9fdc756ead', synchronized_at=now() where definition_key='adapter-actions.issue.one-create-per-incident';

update runtime_config_definitions set authority_class='DOMAIN_CONFIG', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', target_mutability='RESTART_REQUIRED', target_consumer_contract='STARTUP_BINDING', review_status='RETIRED', migration_authorized=false, admin_editable=false, validation_rule='{"reviewRequired":true,"baseline":"C3R1_STRUCTURAL_DEFINITION_ONLY","type":"boolean"}'::jsonb, source_ref='contracts/current/configuration/definitions/definition-registry-v41-c3r2e.json', source_fingerprint='a1affdba57a5f21fea63a8f7a0ec93316db90c2a1b61490a2d5c43c0d7f7749c', synchronized_at=now() where definition_key='adapter-actions.issue.one-update-per-task';

update runtime_config_definitions set authority_class='DOMAIN_CONFIG', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', target_mutability='RESTART_REQUIRED', target_consumer_contract='STARTUP_BINDING', review_status='RETIRED', migration_authorized=false, admin_editable=false, validation_rule='{"reviewRequired":true,"baseline":"C3R1_STRUCTURAL_DEFINITION_ONLY","type":"boolean"}'::jsonb, source_ref='contracts/current/configuration/definitions/definition-registry-v41-c3r2e.json', source_fingerprint='5d3a8e766d9262525126b1b5b7626921c51f122b3a3c9db77001c3292f6de38c', synchronized_at=now() where definition_key='adapter-actions.issue.update-existing-issue-comment';

with advanced as (
 update runtime_config_inventory_governance g set status='CLASSIFIED',domain_owner=d.domain_owner,authority_class=d.authority_class,scope=d.scope,risk=d.risk,mutability=d.target_mutability,consumer_contract=d.target_consumer_contract,admin_editable=d.admin_editable,requires_approval=d.requires_approval,classified_by='v41-c3r2e-adapter-actions-worker-mcp-consumer-closure',classified_at=now(),reason='V41-C3R2E typed runtime consumer implemented',version=version+1
 from runtime_config_definitions d where g.configuration_key=d.definition_key and g.configuration_key in (
    'adapter-actions.create-suppressed-records',
    'adapter-actions.issue.adapter-name',
    'adapter-actions.mcp.adapter-name',
    'adapter-actions.worker.expired-lease-scan-interval-ms',
    'adapter-worker.adapter-types',
    'adapter-worker.enabled',
    'adapter-worker.lease-seconds',
    'adapter-worker.poll-interval-ms',
    'adapter-worker.request-timeout'
 ) and g.status='DISCOVERED' returning g.configuration_key,g.source_observation_hash
)
insert into runtime_config_inventory_governance_events(configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'CLASSIFIED','DISCOVERED','CLASSIFIED','v41-c3r2e-adapter-actions-worker-mcp-consumer-closure','Consumer classification confirmed by implemented typed runtime view',source_observation_hash,'{"stage":"V41_C3R2E_ADAPTER_ACTIONS_WORKER_MCP_DOMAIN_CONSUMER_CLOSURE"}'::jsonb from advanced;

with advanced as (
 update runtime_config_inventory_governance set status='OWNER_REVIEWED',owner_reviewed_by='adapter-actions-worker-domain-owner',owner_reviewed_at=now(),reason='Adapter Actions/Worker consumer and validation bounds reviewed',version=version+1 where configuration_key in (
    'adapter-actions.create-suppressed-records',
    'adapter-actions.issue.adapter-name',
    'adapter-actions.mcp.adapter-name',
    'adapter-actions.worker.expired-lease-scan-interval-ms',
    'adapter-worker.adapter-types',
    'adapter-worker.enabled',
    'adapter-worker.lease-seconds',
    'adapter-worker.poll-interval-ms',
    'adapter-worker.request-timeout'
 ) and status='CLASSIFIED' returning configuration_key,source_observation_hash
)
insert into runtime_config_inventory_governance_events(configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'OWNER_REVIEWED','CLASSIFIED','OWNER_REVIEWED','adapter-actions-worker-domain-owner','Adapter Actions/Worker consumer and validation bounds reviewed',source_observation_hash,'{"cutover":"NOT_FINALIZED"}'::jsonb from advanced;

with advanced as (
 update runtime_config_inventory_governance set status='ARCHITECTURE_APPROVED',architecture_approved_by='configuration-architecture-reviewer',architecture_approved_at=now(),reason='DB authority + Redis distribution + authenticated local snapshot architecture approved',version=version+1 where configuration_key in (
    'adapter-actions.create-suppressed-records',
    'adapter-actions.issue.adapter-name',
    'adapter-actions.mcp.adapter-name',
    'adapter-actions.worker.expired-lease-scan-interval-ms',
    'adapter-worker.adapter-types',
    'adapter-worker.enabled',
    'adapter-worker.lease-seconds',
    'adapter-worker.poll-interval-ms',
    'adapter-worker.request-timeout'
 ) and status='OWNER_REVIEWED' returning configuration_key,source_observation_hash
)
insert into runtime_config_inventory_governance_events(configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'ARCHITECTURE_APPROVED','OWNER_REVIEWED','ARCHITECTURE_APPROVED','configuration-architecture-reviewer','DB authority + Redis distribution + authenticated local snapshot architecture approved',source_observation_hash,'{"cutover":"NOT_FINALIZED"}'::jsonb from advanced;

with advanced as (
 update runtime_config_inventory_governance set status='MIGRATION_READY',migration_authorized_by='v41-c3r2e-adapter-actions-worker-mcp-consumer-closure',migration_authorized_at=now(),reason='Typed consumers implemented; Single Authority cutover remains pending',version=version+1 where configuration_key in (
    'adapter-actions.create-suppressed-records',
    'adapter-actions.issue.adapter-name',
    'adapter-actions.mcp.adapter-name',
    'adapter-actions.worker.expired-lease-scan-interval-ms',
    'adapter-worker.adapter-types',
    'adapter-worker.enabled',
    'adapter-worker.lease-seconds',
    'adapter-worker.poll-interval-ms',
    'adapter-worker.request-timeout'
 ) and status='ARCHITECTURE_APPROVED' returning configuration_key,source_observation_hash
)
insert into runtime_config_inventory_governance_events(configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'MIGRATION_READY','ARCHITECTURE_APPROVED','MIGRATION_READY','v41-c3r2e-adapter-actions-worker-mcp-consumer-closure','Typed consumers implemented; Single Authority cutover remains pending',source_observation_hash,'{"cutover":"NOT_FINALIZED"}'::jsonb from advanced;

update runtime_config_inventory_governance set authority_class='SECURITY_INVARIANT', risk='CRITICAL', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', admin_editable=false, requires_approval=true, reason='C3R2E architecture/security invariant; not runtime editable', version=version+1, updated_at=now() where configuration_key='adapter-actions.issue.legacy-write-enabled' and status in ('DISCOVERED','CLASSIFIED');

update runtime_config_inventory_governance set authority_class='SECURITY_INVARIANT', risk='CRITICAL', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', admin_editable=false, requires_approval=true, reason='C3R2E architecture/security invariant; not runtime editable', version=version+1, updated_at=now() where configuration_key='core.decision.issue-action-enabled' and status in ('DISCOVERED','CLASSIFIED');

update runtime_config_inventory_governance set authority_class='SECURITY_INVARIANT', risk='CRITICAL', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', admin_editable=false, requires_approval=true, reason='C3R2E architecture/security invariant; not runtime editable', version=version+1, updated_at=now() where configuration_key='core.decision.mcp-action-enabled' and status in ('DISCOVERED','CLASSIFIED');

update runtime_config_inventory_governance set authority_class='DOMAIN_CONFIG', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', admin_editable=false, reason='C3R2E legacy Route A key has no canonical execution consumer', version=version+1, updated_at=now() where configuration_key='adapter-actions.issue.create-on-completed-task' and status in ('DISCOVERED','CLASSIFIED');

update runtime_config_inventory_governance set authority_class='DOMAIN_CONFIG', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', admin_editable=false, reason='C3R2E legacy Route A key has no canonical execution consumer', version=version+1, updated_at=now() where configuration_key='adapter-actions.issue.create-on-failed-task' and status in ('DISCOVERED','CLASSIFIED');

update runtime_config_inventory_governance set authority_class='DOMAIN_CONFIG', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', admin_editable=false, reason='C3R2E legacy Route A key has no canonical execution consumer', version=version+1, updated_at=now() where configuration_key='adapter-actions.issue.enabled' and status in ('DISCOVERED','CLASSIFIED');

update runtime_config_inventory_governance set authority_class='DOMAIN_CONFIG', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', admin_editable=false, reason='C3R2E legacy Route A key has no canonical execution consumer', version=version+1, updated_at=now() where configuration_key='adapter-actions.issue.one-create-per-incident' and status in ('DISCOVERED','CLASSIFIED');

update runtime_config_inventory_governance set authority_class='DOMAIN_CONFIG', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', admin_editable=false, reason='C3R2E legacy Route A key has no canonical execution consumer', version=version+1, updated_at=now() where configuration_key='adapter-actions.issue.one-update-per-task' and status in ('DISCOVERED','CLASSIFIED');

update runtime_config_inventory_governance set authority_class='DOMAIN_CONFIG', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', admin_editable=false, reason='C3R2E legacy Route A key has no canonical execution consumer', version=version+1, updated_at=now() where configuration_key='adapter-actions.issue.update-existing-issue-comment' and status in ('DISCOVERED','CLASSIFIED');

do $$ declare v_ready integer; begin
  select count(*) into v_ready from runtime_config_definitions where definition_key in (
    'adapter-actions.create-suppressed-records',
    'adapter-actions.issue.adapter-name',
    'adapter-actions.mcp.adapter-name',
    'adapter-actions.mcp.enabled',
    'adapter-actions.mcp.one-per-task',
    'adapter-actions.mcp.run-on-completed-task',
    'adapter-actions.mcp.run-on-failed-task',
    'adapter-actions.worker.expired-lease-scan-batch-size',
    'adapter-actions.worker.expired-lease-scan-interval-ms',
    'adapter-actions.worker.initial-backoff',
    'adapter-actions.worker.max-attempts',
    'adapter-actions.worker.max-backoff',
    'adapter-actions.worker.retry-enabled',
    'adapter-worker.adapter-types',
    'adapter-worker.enabled',
    'adapter-worker.lease-seconds',
    'adapter-worker.poll-interval-ms',
    'adapter-worker.request-timeout'
  ) and review_status='MIGRATION_READY' and migration_authorized=true and admin_editable=true;
  if v_ready<>18 then raise exception 'C3R2E_DOMAIN_DEFINITION_CLOSURE_INCOMPLETE expected=18 actual=%',v_ready; end if;
  if exists(select 1 from runtime_config_definitions where definition_key in ('adapter-actions.issue.create-on-completed-task','adapter-actions.issue.create-on-failed-task','adapter-actions.issue.enabled','adapter-actions.issue.legacy-write-enabled','adapter-actions.issue.one-create-per-incident','adapter-actions.issue.one-update-per-task','adapter-actions.issue.update-existing-issue-comment','core.decision.issue-action-enabled','core.decision.mcp-action-enabled') and (migration_authorized=true or admin_editable=true or review_status<>'RETIRED')) then raise exception 'C3R2E_RECLASSIFIED_AUTHORITY_LEAK'; end if;
  if exists(select 1 from runtime_config_definitions where definition_key like 'adapter-actions.%' and migration_authorized=true and config_set_key<>'RUNTIME/ADAPTER_ACTION/SYSTEM') then raise exception 'C3R2E_ADAPTER_ACTION_CONFIG_SET_KEY_MISMATCH'; end if;
  if exists(select 1 from runtime_config_inventory_governance where configuration_key in (
    'adapter-actions.create-suppressed-records',
    'adapter-actions.issue.adapter-name',
    'adapter-actions.mcp.adapter-name',
    'adapter-actions.mcp.enabled',
    'adapter-actions.mcp.one-per-task',
    'adapter-actions.mcp.run-on-completed-task',
    'adapter-actions.mcp.run-on-failed-task',
    'adapter-actions.worker.expired-lease-scan-batch-size',
    'adapter-actions.worker.expired-lease-scan-interval-ms',
    'adapter-actions.worker.initial-backoff',
    'adapter-actions.worker.max-attempts',
    'adapter-actions.worker.max-backoff',
    'adapter-actions.worker.retry-enabled',
    'adapter-worker.adapter-types',
    'adapter-worker.enabled',
    'adapter-worker.lease-seconds',
    'adapter-worker.poll-interval-ms',
    'adapter-worker.request-timeout'
  ) and status in ('MIGRATED','LEGACY_RETIRED')) then raise exception 'C3R2E_PREMATURE_SINGLE_AUTHORITY_CUTOVER'; end if;
end $$;
