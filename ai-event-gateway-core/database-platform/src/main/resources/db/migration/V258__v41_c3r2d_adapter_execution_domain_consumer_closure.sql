-- V41-C3R2D: Adapter Execution Domain Consumer Closure.
-- Fifteen real consumers become MIGRATION_READY; nine existing Adapter Execution consumers are normalized.
-- Five misclassified keys are retired from Runtime target scope: two security/authority invariants, two no-op scoped-identity aliases, one static-scheduler alias.
-- No key is advanced to MIGRATED; C3R3 Single Authority cutover remains mandatory.

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
  ui_metadata='{"categoryId":"adapter-execution","categoryDisplayName":"Adapter Execution","categoryDescription":"Adapter execution, issue projection, MCP delivery and resilience runtime settings.","categoryOrder":50,"displayName":"Payload Snapshot Enabled","description":"Runtime-controlled Adapter Execution setting adapter-executor.audit.payload-snapshot-enabled. Startup/YAML is pre-cutover fallback only until C3R3.","recommended":"Seed from the current proven startup value, then change through governed revisions.","effect":"New value applies to the next relevant Adapter execution operation.","impactPositive":"Allows governed operational tuning without rebuilding Core.","impactTradeoff":"Incorrect values can affect executor availability, provider projection, outbound MCP requests or audit volume.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=8,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2d.json',
  source_fingerprint='f75463f05d24af70e6cd6979d41002c91fab6ebf8f8a4a3ddbbc067ca1a3a75f', synchronized_at=now()
where definition_key='adapter-executor.audit.payload-snapshot-enabled';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='DURATION',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='DYNAMIC_SCHEDULER',
  target_mutability='HOT_NEXT_CYCLE',
  target_consumer_contract='DYNAMIC_SCHEDULER',
  validation_rule='{"type":"duration","minimum":"PT0.25S","maximum":"PT1H"}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"adapter-execution","categoryDisplayName":"Adapter Execution","categoryDescription":"Adapter execution, issue projection, MCP delivery and resilience runtime settings.","categoryOrder":50,"displayName":"Auto Execute Interval","description":"Runtime-controlled Adapter Execution setting adapter-executor.auto-execute-interval. Startup/YAML is pre-cutover fallback only until C3R3.","recommended":"Seed from the current proven startup value, then change through governed revisions.","effect":"New cadence applies on the next scheduler cycle.","impactPositive":"Allows governed operational tuning without rebuilding Core.","impactTradeoff":"Incorrect values can affect executor availability, provider projection, outbound MCP requests or audit volume.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=8,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2d.json',
  source_fingerprint='2233b1d75af9bf2496b95a196dab4e5ec3a5ff18e30015ce8f02ce784ffcb92f', synchronized_at=now()
where definition_key='adapter-executor.auto-execute-interval';

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
  ui_metadata='{"categoryId":"adapter-execution","displayName":"Adapter work batch size","description":"Maximum adapter actions claimed in one executor cycle.","recommended":"1–200","effect":"Next execution cycle","impactPositive":"Larger batches can improve throughput.","impactTradeoff":"Large batches can increase burst load and lock duration.","advancedKeyVisible":true,"categoryDisplayName":"Adapter Execution","categoryDescription":"Adapter execution limits, timeout and resilience controls.","categoryOrder":50}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=8,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2d.json',
  source_fingerprint='d6567e33a9cb86240ad1339a1abcc66845994bae5a0ab769b83d0f594e18c88e', synchronized_at=now()
where definition_key='adapter-executor.batch-size';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='BOOLEAN',
  risk='MEDIUM',
  mutability='HOT_IMMEDIATE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_IMMEDIATE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"adapter-execution","displayName":"Circuit breaker enabled","description":"Whether Adapter Executor circuit breaking protects newly evaluated execution attempts.","recommended":"Enabled for normal production operation","effect":"Subsequent circuit-breaker decisions","impactPositive":"Prevents repeated calls to a failing executor from continuously consuming capacity.","impactTradeoff":"Disabling removes the circuit-breaker protection for subsequent executions.","advancedKeyVisible":true,"categoryDisplayName":"Adapter Execution","categoryDescription":"Adapter execution limits, timeout and resilience controls.","categoryOrder":50}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=8,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2d.json',
  source_fingerprint='6948305e2322b26bb9b3053365f913f50504502b24774b33c93af0bc85fe0cf3', synchronized_at=now()
where definition_key='adapter-executor.circuit-breaker.enabled';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='INTEGER',
  risk='MEDIUM',
  mutability='HOT_IMMEDIATE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_IMMEDIATE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"minimum":1}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"adapter-execution","displayName":"Circuit breaker failure threshold","description":"Consecutive executor failures required before a circuit enters the open state.","recommended":"5 failures unless provider-specific evidence supports another threshold","effect":"Subsequent failure recording","impactPositive":"Lets operators tune sensitivity to repeated executor failures without restart.","impactTradeoff":"A threshold that is too low can open on transient failures; too high delays protection.","advancedKeyVisible":true,"categoryDisplayName":"Adapter Execution","categoryDescription":"Adapter execution limits, timeout and resilience controls.","categoryOrder":50}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=8,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2d.json',
  source_fingerprint='6fd484551df07f9165f457137ca38ca5561c1e9bb32abca0004a92485e584981', synchronized_at=now()
where definition_key='adapter-executor.circuit-breaker.failure-threshold';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='DURATION',
  risk='MEDIUM',
  mutability='HOT_IMMEDIATE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_IMMEDIATE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"minimum":"PT0.001S"}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"adapter-execution","displayName":"Circuit breaker open duration","description":"How long an opened executor circuit remains unavailable before normal evaluation resumes.","recommended":"PT1M unless provider recovery behavior requires another duration","effect":"Subsequent circuit opening","impactPositive":"Allows provider recovery windows to be tuned without restarting Core.","impactTradeoff":"Long durations delay recovery after a provider becomes healthy; very short durations can increase retry pressure.","advancedKeyVisible":true,"categoryDisplayName":"Adapter Execution","categoryDescription":"Adapter execution limits, timeout and resilience controls.","categoryOrder":50}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=8,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2d.json',
  source_fingerprint='7afe15b59c3c59fb6e90f4c04bc827c40136d8a4df5bc73382e7b5105821b8af', synchronized_at=now()
where definition_key='adapter-executor.circuit-breaker.open-duration';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='DURATION',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_NEXT_CYCLE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"minimum":"PT0.001S"}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"adapter-execution","displayName":"Adapter execution timeout","description":"Maximum time allowed for one governed adapter execution.","recommended":"PT10S–PT5M","effect":"Next execution cycle","impactPositive":"Allows slow but healthy providers enough time to respond.","impactTradeoff":"Long timeouts keep execution capacity occupied longer.","advancedKeyVisible":true,"categoryDisplayName":"Adapter Execution","categoryDescription":"Adapter execution limits, timeout and resilience controls.","categoryOrder":50}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=8,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2d.json',
  source_fingerprint='f544cb93c53fc0fc308127eac802b0f23eb0ca91e6ee41086a8bcd67d70e67e9', synchronized_at=now()
where definition_key='adapter-executor.execution-timeout';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='DURATION',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_NEXT_CYCLE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"minimum":"PT0.001S"}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"adapter-execution","displayName":"Adapter initial retry delay","description":"Delay before the first retry of an adapter action.","recommended":"PT1S–PT30S","effect":"Next execution cycle","impactPositive":"Avoids immediate repeated calls during transient provider failures.","impactTradeoff":"Long delays increase action completion latency.","advancedKeyVisible":true,"categoryDisplayName":"Adapter Execution","categoryDescription":"Adapter execution limits, timeout and resilience controls.","categoryOrder":50}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=8,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2d.json',
  source_fingerprint='c9d7e7043f51b3e94c3877ddbf7e23c4f184097972fec9700ba9c4393f4ade0e', synchronized_at=now()
where definition_key='adapter-executor.initial-backoff';

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
  ui_metadata='{"categoryId":"adapter-execution","categoryDisplayName":"Adapter Execution","categoryDescription":"Adapter execution, issue projection, MCP delivery and resilience runtime settings.","categoryOrder":50,"displayName":"Auto Execute Pending","description":"Runtime-controlled Adapter Execution setting adapter-executor.issue.auto-execute-pending. Startup/YAML is pre-cutover fallback only until C3R3.","recommended":"Seed from the current proven startup value, then change through governed revisions.","effect":"New value applies to the next relevant Adapter execution operation.","impactPositive":"Allows governed operational tuning without rebuilding Core.","impactTradeoff":"Incorrect values can affect executor availability, provider projection, outbound MCP requests or audit volume.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=8,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2d.json',
  source_fingerprint='fa0d0e453547bee0aa8739fecab2c3a2e16f374d7124f74546004a53ed679a0e', synchronized_at=now()
where definition_key='adapter-executor.issue.auto-execute-pending';

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
  ui_metadata='{"categoryId":"adapter-execution","categoryDisplayName":"Adapter Execution","categoryDescription":"Adapter execution, issue projection, MCP delivery and resilience runtime settings.","categoryOrder":50,"displayName":"Connector Runtime Enabled","description":"Runtime-controlled Adapter Execution setting adapter-executor.issue.connector-runtime-enabled. Startup/YAML is pre-cutover fallback only until C3R3.","recommended":"Seed from the current proven startup value, then change through governed revisions.","effect":"New value applies to the next relevant Adapter execution operation.","impactPositive":"Allows governed operational tuning without rebuilding Core.","impactTradeoff":"Incorrect values can affect executor availability, provider projection, outbound MCP requests or audit volume.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=8,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2d.json',
  source_fingerprint='8f56f902f771e66b3bafbb548d686031a8a5e9b1b8ceb65011219243c83b1623', synchronized_at=now()
where definition_key='adapter-executor.issue.connector-runtime-enabled';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='STRING',
  risk='MEDIUM',
  mutability='HOT_IMMEDIATE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_IMMEDIATE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"type":"string","enum":["","JIRA","REDMINE","GITLAB","MOCK"]}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"adapter-execution","categoryDisplayName":"Adapter Execution","categoryDescription":"Adapter execution, issue projection, MCP delivery and resilience runtime settings.","categoryOrder":50,"displayName":"Default Vendor","description":"Runtime-controlled Adapter Execution setting adapter-executor.issue.default-vendor. Startup/YAML is pre-cutover fallback only until C3R3.","recommended":"Seed from the current proven startup value, then change through governed revisions.","effect":"New value applies to the next relevant Adapter execution operation.","impactPositive":"Allows governed operational tuning without rebuilding Core.","impactTradeoff":"Incorrect values can affect executor availability, provider projection, outbound MCP requests or audit volume.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=8,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2d.json',
  source_fingerprint='c4a99b93fbb110f70b4701f3042e03b34baa2073accb9a4a8e7e55a06e1ba047', synchronized_at=now()
where definition_key='adapter-executor.issue.default-vendor';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='INTEGER',
  risk='MEDIUM',
  mutability='HOT_IMMEDIATE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_IMMEDIATE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"minimum":1,"maximum":1000}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"adapter-execution","categoryDisplayName":"Adapter Execution","categoryDescription":"Adapter execution, issue projection, MCP delivery and resilience runtime settings.","categoryOrder":50,"displayName":"Link Projection Batch Size","description":"Runtime-controlled Adapter Execution setting adapter-executor.issue.link-projection-batch-size. Startup/YAML is pre-cutover fallback only until C3R3.","recommended":"Seed from the current proven startup value, then change through governed revisions.","effect":"New value applies to the next relevant Adapter execution operation.","impactPositive":"Allows governed operational tuning without rebuilding Core.","impactTradeoff":"Incorrect values can affect executor availability, provider projection, outbound MCP requests or audit volume.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=8,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2d.json',
  source_fingerprint='b2f123a6d15a57053326d24947dcb54ac0ec0b6f06366459e1a4758326850697', synchronized_at=now()
where definition_key='adapter-executor.issue.link-projection-batch-size';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='DURATION',
  risk='MEDIUM',
  mutability='HOT_IMMEDIATE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_IMMEDIATE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"type":"duration","minimum":"PT0.25S","maximum":"PT1H"}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"adapter-execution","categoryDisplayName":"Adapter Execution","categoryDescription":"Adapter execution, issue projection, MCP delivery and resilience runtime settings.","categoryOrder":50,"displayName":"Link Projection Initial Backoff","description":"Runtime-controlled Adapter Execution setting adapter-executor.issue.link-projection-initial-backoff. Startup/YAML is pre-cutover fallback only until C3R3.","recommended":"Seed from the current proven startup value, then change through governed revisions.","effect":"New value applies to the next relevant Adapter execution operation.","impactPositive":"Allows governed operational tuning without rebuilding Core.","impactTradeoff":"Incorrect values can affect executor availability, provider projection, outbound MCP requests or audit volume.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=8,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2d.json',
  source_fingerprint='d866207f5d5aea2309b33f65cf1a8a7fc3069c42ce0dceaea1191b36a34c45d6', synchronized_at=now()
where definition_key='adapter-executor.issue.link-projection-initial-backoff';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='INTEGER',
  risk='MEDIUM',
  mutability='HOT_IMMEDIATE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_IMMEDIATE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"minimum":1,"maximum":1000}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"adapter-execution","categoryDisplayName":"Adapter Execution","categoryDescription":"Adapter execution, issue projection, MCP delivery and resilience runtime settings.","categoryOrder":50,"displayName":"Link Projection Max Attempts","description":"Runtime-controlled Adapter Execution setting adapter-executor.issue.link-projection-max-attempts. Startup/YAML is pre-cutover fallback only until C3R3.","recommended":"Seed from the current proven startup value, then change through governed revisions.","effect":"New value applies to the next relevant Adapter execution operation.","impactPositive":"Allows governed operational tuning without rebuilding Core.","impactTradeoff":"Incorrect values can affect executor availability, provider projection, outbound MCP requests or audit volume.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=8,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2d.json',
  source_fingerprint='6541bba215aaeb78d5ccf4cbda8c95548e8c14845cd658be484cb693eb0e7100', synchronized_at=now()
where definition_key='adapter-executor.issue.link-projection-max-attempts';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='DURATION',
  risk='MEDIUM',
  mutability='HOT_IMMEDIATE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_IMMEDIATE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"type":"duration","minimum":"PT0.25S","maximum":"PT24H"}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"adapter-execution","categoryDisplayName":"Adapter Execution","categoryDescription":"Adapter execution, issue projection, MCP delivery and resilience runtime settings.","categoryOrder":50,"displayName":"Link Projection Max Backoff","description":"Runtime-controlled Adapter Execution setting adapter-executor.issue.link-projection-max-backoff. Startup/YAML is pre-cutover fallback only until C3R3.","recommended":"Seed from the current proven startup value, then change through governed revisions.","effect":"New value applies to the next relevant Adapter execution operation.","impactPositive":"Allows governed operational tuning without rebuilding Core.","impactTradeoff":"Incorrect values can affect executor availability, provider projection, outbound MCP requests or audit volume.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=8,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2d.json',
  source_fingerprint='ddd5271587924d7659ff794cef4754981bc28d09801caedd043a25b99c706bf5', synchronized_at=now()
where definition_key='adapter-executor.issue.link-projection-max-backoff';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='DURATION',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='DYNAMIC_SCHEDULER',
  target_mutability=null,
  target_consumer_contract=null,
  validation_rule='{"minimum":"PT0.25S","maximum":"PT1H"}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"adapter-execution","displayName":"Issue link reconciliation interval","description":"How long the projection recovery scheduler waits after each completed cycle.","recommended":"PT5S–PT2M","effect":"Next scheduler cycle","impactPositive":"Shorter intervals can repair durable Issue links sooner.","impactTradeoff":"Very short intervals increase database reconciliation pressure.","advancedKeyVisible":true,"categoryDisplayName":"Adapter Execution","categoryDescription":"Adapter execution limits, timeout and resilience controls.","categoryOrder":50}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=8,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2d.json',
  source_fingerprint='b630252c8d97f37eb218a24268f7e7063963969cc3d333f9fc8b0635037d0c5d', synchronized_at=now()
where definition_key='adapter-executor.issue.link-projection-reconciliation-delay';

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
  ui_metadata='{"categoryId":"adapter-execution","categoryDisplayName":"Adapter Execution","categoryDescription":"Adapter execution, issue projection, MCP delivery and resilience runtime settings.","categoryOrder":50,"displayName":"Link Projection Reconciliation Enabled","description":"Runtime-controlled Adapter Execution setting adapter-executor.issue.link-projection-reconciliation-enabled. Startup/YAML is pre-cutover fallback only until C3R3.","recommended":"Seed from the current proven startup value, then change through governed revisions.","effect":"New value applies to the next relevant Adapter execution operation.","impactPositive":"Allows governed operational tuning without rebuilding Core.","impactTradeoff":"Incorrect values can affect executor availability, provider projection, outbound MCP requests or audit volume.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=8,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2d.json',
  source_fingerprint='942143bdc1c474f89f9344fe6e6ecfb52b430ebbf7b7a0088f50cd0afd85cdbd', synchronized_at=now()
where definition_key='adapter-executor.issue.link-projection-reconciliation-enabled';

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
  ui_metadata='{"categoryId":"adapter-execution","categoryDisplayName":"Adapter Execution","categoryDescription":"Adapter execution, issue projection, MCP delivery and resilience runtime settings.","categoryOrder":50,"displayName":"Mark Unavailable When No Executor","description":"Runtime-controlled Adapter Execution setting adapter-executor.mark-unavailable-when-no-executor. Startup/YAML is pre-cutover fallback only until C3R3.","recommended":"Seed from the current proven startup value, then change through governed revisions.","effect":"New value applies to the next relevant Adapter execution operation.","impactPositive":"Allows governed operational tuning without rebuilding Core.","impactTradeoff":"Incorrect values can affect executor availability, provider projection, outbound MCP requests or audit volume.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=8,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2d.json',
  source_fingerprint='4f6b89411ddd4c482edd90288440263d8c039ce4fa9a531fa1060e985857f37b', synchronized_at=now()
where definition_key='adapter-executor.mark-unavailable-when-no-executor';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='INTEGER',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_NEXT_CYCLE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"minimum":1}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"adapter-execution","displayName":"Adapter maximum attempts","description":"Maximum governed attempts for one adapter action.","recommended":"1–10","effect":"Next execution cycle","impactPositive":"Improves resilience to transient provider errors.","impactTradeoff":"More attempts can extend time before terminal failure.","advancedKeyVisible":true,"categoryDisplayName":"Adapter Execution","categoryDescription":"Adapter execution limits, timeout and resilience controls.","categoryOrder":50}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=8,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2d.json',
  source_fingerprint='f18480e5e5285e943a661c4b47453e1a38c0ce6ec3beb31e5ace74fa6e11ce7a', synchronized_at=now()
where definition_key='adapter-executor.max-attempts';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='DURATION',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_NEXT_CYCLE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"minimum":"PT0.001S","greaterThanOrEqualKey":"adapter-executor.initial-backoff"}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"adapter-execution","displayName":"Adapter maximum retry delay","description":"Upper bound for adapter retry backoff.","recommended":"At least the initial delay","effect":"Next execution cycle","impactPositive":"Limits provider pressure during longer outages.","impactTradeoff":"Large values make retries recover more slowly after the provider returns.","advancedKeyVisible":true,"categoryDisplayName":"Adapter Execution","categoryDescription":"Adapter execution limits, timeout and resilience controls.","categoryOrder":50}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=8,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2d.json',
  source_fingerprint='6b074a77f0d340198f5809f65f4c3c5ed660d0fa890b63d73cd5b9927ee81539', synchronized_at=now()
where definition_key='adapter-executor.max-backoff';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='STRING',
  risk='MEDIUM',
  mutability='HOT_IMMEDIATE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_IMMEDIATE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"type":"string","maxLength":2048}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"adapter-execution","categoryDisplayName":"Adapter Execution","categoryDescription":"Adapter execution, issue projection, MCP delivery and resilience runtime settings.","categoryOrder":50,"displayName":"Endpoint Url","description":"Runtime-controlled Adapter Execution setting adapter-executor.mcp.endpoint-url. Startup/YAML is pre-cutover fallback only until C3R3.","recommended":"Seed from the current proven startup value, then change through governed revisions.","effect":"New value applies to the next relevant Adapter execution operation.","impactPositive":"Allows governed operational tuning without rebuilding Core.","impactTradeoff":"Incorrect values can affect executor availability, provider projection, outbound MCP requests or audit volume.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=8,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2d.json',
  source_fingerprint='0619d8e5f21650af6921a0b95def43494308a1172972705f1b4ae5cdb7c4badd', synchronized_at=now()
where definition_key='adapter-executor.mcp.endpoint-url';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='STRING',
  risk='MEDIUM',
  mutability='HOT_IMMEDIATE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_IMMEDIATE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"type":"string","minLength":1,"maxLength":128}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"adapter-execution","categoryDisplayName":"Adapter Execution","categoryDescription":"Adapter execution, issue projection, MCP delivery and resilience runtime settings.","categoryOrder":50,"displayName":"Executor Name","description":"Runtime-controlled Adapter Execution setting adapter-executor.mcp.executor-name. Startup/YAML is pre-cutover fallback only until C3R3.","recommended":"Seed from the current proven startup value, then change through governed revisions.","effect":"New value applies to the next relevant Adapter execution operation.","impactPositive":"Allows governed operational tuning without rebuilding Core.","impactTradeoff":"Incorrect values can affect executor availability, provider projection, outbound MCP requests or audit volume.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=8,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2d.json',
  source_fingerprint='2738ead81e1efa4507d83053a4a83a52b734480a3e3ca7cb92a89647dda4d7c1', synchronized_at=now()
where definition_key='adapter-executor.mcp.executor-name';

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
  ui_metadata='{"categoryId":"adapter-execution","categoryDisplayName":"Adapter Execution","categoryDescription":"Adapter execution, issue projection, MCP delivery and resilience runtime settings.","categoryOrder":50,"displayName":"Http Enabled","description":"Runtime-controlled Adapter Execution setting adapter-executor.mcp.http-enabled. Startup/YAML is pre-cutover fallback only until C3R3.","recommended":"Seed from the current proven startup value, then change through governed revisions.","effect":"New value applies to the next relevant Adapter execution operation.","impactPositive":"Allows governed operational tuning without rebuilding Core.","impactTradeoff":"Incorrect values can affect executor availability, provider projection, outbound MCP requests or audit volume.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=8,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2d.json',
  source_fingerprint='47ea82d56208ab26824a51a2e94d7de0cba0bb66fec93423f56cc3d06244efd9', synchronized_at=now()
where definition_key='adapter-executor.mcp.http-enabled';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='DURATION',
  risk='MEDIUM',
  mutability='HOT_IMMEDIATE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_IMMEDIATE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"type":"duration","minimum":"PT1S","maximum":"PT5M"}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"adapter-execution","categoryDisplayName":"Adapter Execution","categoryDescription":"Adapter execution, issue projection, MCP delivery and resilience runtime settings.","categoryOrder":50,"displayName":"Timeout","description":"Runtime-controlled Adapter Execution setting adapter-executor.mcp.timeout. Startup/YAML is pre-cutover fallback only until C3R3.","recommended":"Seed from the current proven startup value, then change through governed revisions.","effect":"New value applies to the next relevant Adapter execution operation.","impactPositive":"Allows governed operational tuning without rebuilding Core.","impactTradeoff":"Incorrect values can affect executor availability, provider projection, outbound MCP requests or audit volume.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=8,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2d.json',
  source_fingerprint='8767b90c8656997d8bd34a2d484542851434f6edde414a0f58b91b707b679b08', synchronized_at=now()
where definition_key='adapter-executor.mcp.timeout';

update runtime_config_definitions set authority_class='SECURITY_INVARIANT', risk='CRITICAL', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', target_mutability='RESTART_REQUIRED', target_consumer_contract='STARTUP_BINDING', review_status='RETIRED', migration_authorized=false, admin_editable=false, validation_rule='{"classificationCorrection":"SECURITY_OR_AUTHORITY_ENFORCEMENT","type":"boolean"}'::jsonb, source_ref='contracts/current/configuration/definitions/definition-registry-v41-c3r2d.json', source_fingerprint='f1f80d425390b6fd4588e9aa493cd222593e7aa006f3c09899e125fbe819badd', synchronized_at=now() where definition_key='adapter-executor.issue.connector-runtime-required';

update runtime_config_definitions set authority_class='SECURITY_INVARIANT', risk='CRITICAL', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', target_mutability='RESTART_REQUIRED', target_consumer_contract='STARTUP_BINDING', review_status='RETIRED', migration_authorized=false, admin_editable=false, validation_rule='{"classificationCorrection":"SECURITY_OR_AUTHORITY_ENFORCEMENT","type":"string"}'::jsonb, source_ref='contracts/current/configuration/definitions/definition-registry-v41-c3r2d.json', source_fingerprint='70096649e11a2ddffb97d009dbfa94334c600ad8e8a25730397a2873b215cda4', synchronized_at=now() where definition_key='adapter-executor.issue.execution-authority';

update runtime_config_definitions set authority_class='DOMAIN_CONFIG', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', target_mutability='RESTART_REQUIRED', target_consumer_contract='STARTUP_BINDING', review_status='RETIRED', migration_authorized=false, admin_editable=false, validation_rule='{"classificationCorrection":"LEGACY_SCHEDULER_ALIAS","canonicalKey":"adapter-executor.auto-execute-interval"}'::jsonb, source_ref='contracts/current/configuration/definitions/definition-registry-v41-c3r2d.json', source_fingerprint='25130ea12c6385af8f78a735047a456033f737633901e89dbfbb22a428356f1a', synchronized_at=now() where definition_key='adapter-executor.auto-execute-interval-ms';

update runtime_config_definitions set authority_class='DOMAIN_CONFIG', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', target_mutability='RESTART_REQUIRED', target_consumer_contract='STARTUP_BINDING', review_status='RETIRED', migration_authorized=false, admin_editable=false, validation_rule='{"classificationCorrection":"NO_EXECUTION_CONSUMER","evidence":"Deprecated compatibility getter returns false and setter is a no-op."}'::jsonb, source_ref='contracts/current/configuration/definitions/definition-registry-v41-c3r2d.json', source_fingerprint='dc8b1899a95dcb697afb5c7291fb2744a1771c95b7e8e4bf12da05c55bca486e', synchronized_at=now() where definition_key='adapter-executor.issue.scoped-identity-enabled';

update runtime_config_definitions set authority_class='DOMAIN_CONFIG', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', target_mutability='RESTART_REQUIRED', target_consumer_contract='STARTUP_BINDING', review_status='RETIRED', migration_authorized=false, admin_editable=false, validation_rule='{"classificationCorrection":"NO_EXECUTION_CONSUMER","evidence":"Deprecated compatibility getter returns false and setter is a no-op."}'::jsonb, source_ref='contracts/current/configuration/definitions/definition-registry-v41-c3r2d.json', source_fingerprint='8cab66decdd50ec31ebb6ed365282401dbef89bf16ff8ebf16a473a46be5cfe0', synchronized_at=now() where definition_key='adapter-executor.issue.scoped-identity-required';

with advanced as (
 update runtime_config_inventory_governance g set status='CLASSIFIED',domain_owner=d.domain_owner,authority_class=d.authority_class,scope=d.scope,risk=d.risk,mutability=d.target_mutability,consumer_contract=d.target_consumer_contract,admin_editable=d.admin_editable,requires_approval=d.requires_approval,classified_by='v41-c3r2d-adapter-execution-consumer-closure',classified_at=now(),reason='V41-C3R2D typed runtime consumer implemented',version=version+1
 from runtime_config_definitions d where g.configuration_key=d.definition_key and g.configuration_key in (
    'adapter-executor.audit.payload-snapshot-enabled',
    'adapter-executor.auto-execute-interval',
    'adapter-executor.issue.auto-execute-pending',
    'adapter-executor.issue.connector-runtime-enabled',
    'adapter-executor.issue.default-vendor',
    'adapter-executor.issue.link-projection-batch-size',
    'adapter-executor.issue.link-projection-initial-backoff',
    'adapter-executor.issue.link-projection-max-attempts',
    'adapter-executor.issue.link-projection-max-backoff',
    'adapter-executor.issue.link-projection-reconciliation-enabled',
    'adapter-executor.mark-unavailable-when-no-executor',
    'adapter-executor.mcp.endpoint-url',
    'adapter-executor.mcp.executor-name',
    'adapter-executor.mcp.http-enabled',
    'adapter-executor.mcp.timeout'
 ) and g.status='DISCOVERED' returning g.configuration_key,g.source_observation_hash
)
insert into runtime_config_inventory_governance_events(configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'CLASSIFIED','DISCOVERED','CLASSIFIED','v41-c3r2d-adapter-execution-consumer-closure','Consumer classification confirmed by implemented typed runtime view',source_observation_hash,'{"stage":"V41_C3R2D_ADAPTER_EXECUTION_DOMAIN_CONSUMER_CLOSURE"}'::jsonb from advanced;

with advanced as (
 update runtime_config_inventory_governance set status='OWNER_REVIEWED',owner_reviewed_by='adapter-execution-domain-owner',owner_reviewed_at=now(),reason='Adapter Execution consumer and validation bounds reviewed',version=version+1 where configuration_key in (
    'adapter-executor.audit.payload-snapshot-enabled',
    'adapter-executor.auto-execute-interval',
    'adapter-executor.issue.auto-execute-pending',
    'adapter-executor.issue.connector-runtime-enabled',
    'adapter-executor.issue.default-vendor',
    'adapter-executor.issue.link-projection-batch-size',
    'adapter-executor.issue.link-projection-initial-backoff',
    'adapter-executor.issue.link-projection-max-attempts',
    'adapter-executor.issue.link-projection-max-backoff',
    'adapter-executor.issue.link-projection-reconciliation-enabled',
    'adapter-executor.mark-unavailable-when-no-executor',
    'adapter-executor.mcp.endpoint-url',
    'adapter-executor.mcp.executor-name',
    'adapter-executor.mcp.http-enabled',
    'adapter-executor.mcp.timeout'
 ) and status='CLASSIFIED' returning configuration_key,source_observation_hash
)
insert into runtime_config_inventory_governance_events(configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'OWNER_REVIEWED','CLASSIFIED','OWNER_REVIEWED','adapter-execution-domain-owner','Adapter Execution consumer and validation bounds reviewed',source_observation_hash,'{"cutover":"NOT_FINALIZED"}'::jsonb from advanced;

with advanced as (
 update runtime_config_inventory_governance set status='ARCHITECTURE_APPROVED',architecture_approved_by='configuration-architecture-reviewer',architecture_approved_at=now(),reason='DB authority + Redis distribution + authenticated local snapshot architecture approved',version=version+1 where configuration_key in (
    'adapter-executor.audit.payload-snapshot-enabled',
    'adapter-executor.auto-execute-interval',
    'adapter-executor.issue.auto-execute-pending',
    'adapter-executor.issue.connector-runtime-enabled',
    'adapter-executor.issue.default-vendor',
    'adapter-executor.issue.link-projection-batch-size',
    'adapter-executor.issue.link-projection-initial-backoff',
    'adapter-executor.issue.link-projection-max-attempts',
    'adapter-executor.issue.link-projection-max-backoff',
    'adapter-executor.issue.link-projection-reconciliation-enabled',
    'adapter-executor.mark-unavailable-when-no-executor',
    'adapter-executor.mcp.endpoint-url',
    'adapter-executor.mcp.executor-name',
    'adapter-executor.mcp.http-enabled',
    'adapter-executor.mcp.timeout'
 ) and status='OWNER_REVIEWED' returning configuration_key,source_observation_hash
)
insert into runtime_config_inventory_governance_events(configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'ARCHITECTURE_APPROVED','OWNER_REVIEWED','ARCHITECTURE_APPROVED','configuration-architecture-reviewer','DB authority + Redis distribution + authenticated local snapshot architecture approved',source_observation_hash,'{"cutover":"NOT_FINALIZED"}'::jsonb from advanced;

with advanced as (
 update runtime_config_inventory_governance set status='MIGRATION_READY',migration_authorized_by='v41-c3r2d-adapter-execution-consumer-closure',migration_authorized_at=now(),reason='Typed consumers implemented; Single Authority cutover remains pending',version=version+1 where configuration_key in (
    'adapter-executor.audit.payload-snapshot-enabled',
    'adapter-executor.auto-execute-interval',
    'adapter-executor.issue.auto-execute-pending',
    'adapter-executor.issue.connector-runtime-enabled',
    'adapter-executor.issue.default-vendor',
    'adapter-executor.issue.link-projection-batch-size',
    'adapter-executor.issue.link-projection-initial-backoff',
    'adapter-executor.issue.link-projection-max-attempts',
    'adapter-executor.issue.link-projection-max-backoff',
    'adapter-executor.issue.link-projection-reconciliation-enabled',
    'adapter-executor.mark-unavailable-when-no-executor',
    'adapter-executor.mcp.endpoint-url',
    'adapter-executor.mcp.executor-name',
    'adapter-executor.mcp.http-enabled',
    'adapter-executor.mcp.timeout'
 ) and status='ARCHITECTURE_APPROVED' returning configuration_key,source_observation_hash
)
insert into runtime_config_inventory_governance_events(configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'MIGRATION_READY','ARCHITECTURE_APPROVED','MIGRATION_READY','v41-c3r2d-adapter-execution-consumer-closure','Typed consumers implemented; Single Authority cutover remains pending',source_observation_hash,'{"cutover":"NOT_FINALIZED"}'::jsonb from advanced;

update runtime_config_inventory_governance set authority_class='SECURITY_INVARIANT', risk='CRITICAL', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', admin_editable=false, requires_approval=true, reason='Adapter execution authority/security enforcement is startup governed', version=version+1, updated_at=now() where configuration_key='adapter-executor.issue.connector-runtime-required' and status in ('DISCOVERED','CLASSIFIED');

update runtime_config_inventory_governance set authority_class='SECURITY_INVARIANT', risk='CRITICAL', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', admin_editable=false, requires_approval=true, reason='Adapter execution authority/security enforcement is startup governed', version=version+1, updated_at=now() where configuration_key='adapter-executor.issue.execution-authority' and status in ('DISCOVERED','CLASSIFIED');

update runtime_config_inventory_governance set authority_class='DOMAIN_CONFIG', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', admin_editable=false, reason='No active runtime consumer; retired compatibility/legacy alias', version=version+1, updated_at=now() where configuration_key='adapter-executor.auto-execute-interval-ms' and status in ('DISCOVERED','CLASSIFIED');

update runtime_config_inventory_governance set authority_class='DOMAIN_CONFIG', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', admin_editable=false, reason='No active runtime consumer; retired compatibility/legacy alias', version=version+1, updated_at=now() where configuration_key='adapter-executor.issue.scoped-identity-enabled' and status in ('DISCOVERED','CLASSIFIED');

update runtime_config_inventory_governance set authority_class='DOMAIN_CONFIG', mutability='RESTART_REQUIRED', consumer_contract='STARTUP_BINDING', admin_editable=false, reason='No active runtime consumer; retired compatibility/legacy alias', version=version+1, updated_at=now() where configuration_key='adapter-executor.issue.scoped-identity-required' and status in ('DISCOVERED','CLASSIFIED');

do $$ declare v_ready integer; begin
  select count(*) into v_ready from runtime_config_definitions where definition_key in (
    'adapter-executor.audit.payload-snapshot-enabled',
    'adapter-executor.auto-execute-interval',
    'adapter-executor.batch-size',
    'adapter-executor.circuit-breaker.enabled',
    'adapter-executor.circuit-breaker.failure-threshold',
    'adapter-executor.circuit-breaker.open-duration',
    'adapter-executor.execution-timeout',
    'adapter-executor.initial-backoff',
    'adapter-executor.issue.auto-execute-pending',
    'adapter-executor.issue.connector-runtime-enabled',
    'adapter-executor.issue.default-vendor',
    'adapter-executor.issue.link-projection-batch-size',
    'adapter-executor.issue.link-projection-initial-backoff',
    'adapter-executor.issue.link-projection-max-attempts',
    'adapter-executor.issue.link-projection-max-backoff',
    'adapter-executor.issue.link-projection-reconciliation-delay',
    'adapter-executor.issue.link-projection-reconciliation-enabled',
    'adapter-executor.mark-unavailable-when-no-executor',
    'adapter-executor.max-attempts',
    'adapter-executor.max-backoff',
    'adapter-executor.mcp.endpoint-url',
    'adapter-executor.mcp.executor-name',
    'adapter-executor.mcp.http-enabled',
    'adapter-executor.mcp.timeout'
  ) and review_status='MIGRATION_READY' and migration_authorized=true and admin_editable=true;
  if v_ready<>24 then raise exception 'C3R2D_DOMAIN_DEFINITION_CLOSURE_INCOMPLETE expected=24 actual=%',v_ready; end if;
  if exists(select 1 from runtime_config_definitions where definition_key in ('adapter-executor.auto-execute-interval-ms','adapter-executor.issue.connector-runtime-required','adapter-executor.issue.execution-authority','adapter-executor.issue.scoped-identity-enabled','adapter-executor.issue.scoped-identity-required') and (migration_authorized=true or admin_editable=true or review_status<>'RETIRED')) then raise exception 'C3R2D_RECLASSIFIED_AUTHORITY_LEAK'; end if;
  if exists(select 1 from runtime_config_inventory_governance where configuration_key in (
    'adapter-executor.audit.payload-snapshot-enabled',
    'adapter-executor.auto-execute-interval',
    'adapter-executor.batch-size',
    'adapter-executor.circuit-breaker.enabled',
    'adapter-executor.circuit-breaker.failure-threshold',
    'adapter-executor.circuit-breaker.open-duration',
    'adapter-executor.execution-timeout',
    'adapter-executor.initial-backoff',
    'adapter-executor.issue.auto-execute-pending',
    'adapter-executor.issue.connector-runtime-enabled',
    'adapter-executor.issue.default-vendor',
    'adapter-executor.issue.link-projection-batch-size',
    'adapter-executor.issue.link-projection-initial-backoff',
    'adapter-executor.issue.link-projection-max-attempts',
    'adapter-executor.issue.link-projection-max-backoff',
    'adapter-executor.issue.link-projection-reconciliation-delay',
    'adapter-executor.issue.link-projection-reconciliation-enabled',
    'adapter-executor.mark-unavailable-when-no-executor',
    'adapter-executor.max-attempts',
    'adapter-executor.max-backoff',
    'adapter-executor.mcp.endpoint-url',
    'adapter-executor.mcp.executor-name',
    'adapter-executor.mcp.http-enabled',
    'adapter-executor.mcp.timeout'
  ) and status in ('MIGRATED','LEGACY_RETIRED')) then raise exception 'C3R2D_PREMATURE_SINGLE_AUTHORITY_CUTOVER'; end if;
end $$;
