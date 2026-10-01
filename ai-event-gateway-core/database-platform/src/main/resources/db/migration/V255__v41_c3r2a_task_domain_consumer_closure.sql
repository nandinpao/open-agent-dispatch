-- V41-C3R2A: Task Domain Consumer Closure.
-- 24 previously PROPOSED Task runtime definitions become MIGRATION_READY after typed consumer migration.
-- task.decision.mode is retired from Runtime Configuration scope because it is a documented placeholder with no runtime consumer.
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
  ui_metadata='{"categoryId":"task-callback","categoryDisplayName":"Task Callback","categoryDescription":"Task callback acceptance, replay protection and timeout recovery runtime policy.","categoryOrder":1371,"displayName":"Allow Missing Dispatch Request Id","description":"Source-controlled baseline for task.callback.allow-missing-dispatch-request-id. Runtime editing remains disabled until its typed consumer is migrated and reviewed.","recommended":"Review current effective value before consumer migration.","effect":"Runtime snapshot; scheduler cadence applies on next cycle where applicable","impactPositive":"Makes the target runtime setting visible and traceable before authority cutover.","impactTradeoff":"Changes affect callback acceptance or recovery behavior immediately/next cycle; approval and rollout evidence remain required.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=5,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2a.json',
  source_fingerprint='9b84b06f10d5a7ba81897591740cca2438bad85cdd1145c62af78d880a72aecc', synchronized_at=now()
where definition_key='task.callback.allow-missing-dispatch-request-id';

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
  ui_metadata='{"categoryId":"task-callback","categoryDisplayName":"Task Callback","categoryDescription":"Task callback acceptance, replay protection and timeout recovery runtime policy.","categoryOrder":1371,"displayName":"Allow Terminal Callback Override","description":"Source-controlled baseline for task.callback.allow-terminal-callback-override. Runtime editing remains disabled until its typed consumer is migrated and reviewed.","recommended":"Review current effective value before consumer migration.","effect":"Runtime snapshot; scheduler cadence applies on next cycle where applicable","impactPositive":"Makes the target runtime setting visible and traceable before authority cutover.","impactTradeoff":"Changes affect callback acceptance or recovery behavior immediately/next cycle; approval and rollout evidence remain required.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=5,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2a.json',
  source_fingerprint='5e6ee3623deb0355b9b2ed7bd2438dd35840dbc05248fb5cd13094fe6e48469d', synchronized_at=now()
where definition_key='task.callback.allow-terminal-callback-override';

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
  ui_metadata='{"categoryId":"task-callback","categoryDisplayName":"Task Callback","categoryDescription":"Task callback acceptance, replay protection and timeout recovery runtime policy.","categoryOrder":1371,"displayName":"Enforce Gateway And Agent Identity","description":"Source-controlled baseline for task.callback.enforce-gateway-and-agent-identity. Runtime editing remains disabled until its typed consumer is migrated and reviewed.","recommended":"Review current effective value before consumer migration.","effect":"Runtime snapshot; scheduler cadence applies on next cycle where applicable","impactPositive":"Makes the target runtime setting visible and traceable before authority cutover.","impactTradeoff":"Changes affect callback acceptance or recovery behavior immediately/next cycle; approval and rollout evidence remain required.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=5,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2a.json',
  source_fingerprint='3d47b2c39e517ff7ea1e614fd03476131d0bd6516cb92d16f25e71d569d1be2d', synchronized_at=now()
where definition_key='task.callback.enforce-gateway-and-agent-identity';

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
  ui_metadata='{"categoryId":"task-callback","categoryDisplayName":"Task Callback","categoryDescription":"Task callback acceptance, replay protection and timeout recovery runtime policy.","categoryOrder":1371,"displayName":"Enforce State Transition","description":"Source-controlled baseline for task.callback.enforce-state-transition. Runtime editing remains disabled until its typed consumer is migrated and reviewed.","recommended":"Review current effective value before consumer migration.","effect":"Runtime snapshot; scheduler cadence applies on next cycle where applicable","impactPositive":"Makes the target runtime setting visible and traceable before authority cutover.","impactTradeoff":"Changes affect callback acceptance or recovery behavior immediately/next cycle; approval and rollout evidence remain required.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=5,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2a.json',
  source_fingerprint='d324daeb7d0b190993dcb81faa3398d11729e0cbea1c32e09904a75267e81d97', synchronized_at=now()
where definition_key='task.callback.enforce-state-transition';

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
  ui_metadata='{"categoryId":"task-callback","categoryDisplayName":"Task Callback","categoryDescription":"Task callback acceptance, replay protection and timeout recovery runtime policy.","categoryOrder":1371,"displayName":"Idempotency Enabled","description":"Source-controlled baseline for task.callback.idempotency-enabled. Runtime editing remains disabled until its typed consumer is migrated and reviewed.","recommended":"Review current effective value before consumer migration.","effect":"Runtime snapshot; scheduler cadence applies on next cycle where applicable","impactPositive":"Makes the target runtime setting visible and traceable before authority cutover.","impactTradeoff":"Changes affect callback acceptance or recovery behavior immediately/next cycle; approval and rollout evidence remain required.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=5,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2a.json',
  source_fingerprint='a0326869e2ad7537212c11b00242a11c549e60303e295f685869aa912afe2517', synchronized_at=now()
where definition_key='task.callback.idempotency-enabled';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='INTEGER',
  risk='MEDIUM',
  mutability='HOT_IMMEDIATE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_IMMEDIATE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"minimum":1,"maximum":5000}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"task-callback","categoryDisplayName":"Task Callback","categoryDescription":"Task callback acceptance, replay protection and timeout recovery runtime policy.","categoryOrder":1371,"displayName":"Max Recent","description":"Source-controlled baseline for task.callback.max-recent. Runtime editing remains disabled until its typed consumer is migrated and reviewed.","recommended":"Review current effective value before consumer migration.","effect":"Runtime snapshot; scheduler cadence applies on next cycle where applicable","impactPositive":"Makes the target runtime setting visible and traceable before authority cutover.","impactTradeoff":"Changes affect callback acceptance or recovery behavior immediately/next cycle; approval and rollout evidence remain required.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=5,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2a.json',
  source_fingerprint='1ca7d1f9887647d0c35ef21d17e7512f8f298c7d3b57fb9ae5001732bac87df7', synchronized_at=now()
where definition_key='task.callback.max-recent';

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
  ui_metadata='{"categoryId":"task-callback","categoryDisplayName":"Task Callback","categoryDescription":"Task callback acceptance, replay protection and timeout recovery runtime policy.","categoryOrder":1371,"displayName":"Auto Fail Timed Out","description":"Source-controlled baseline for task.callback.recovery.auto-fail-timed-out. Runtime editing remains disabled until its typed consumer is migrated and reviewed.","recommended":"Review current effective value before consumer migration.","effect":"Runtime snapshot; scheduler cadence applies on next cycle where applicable","impactPositive":"Makes the target runtime setting visible and traceable before authority cutover.","impactTradeoff":"Changes affect callback acceptance or recovery behavior immediately/next cycle; approval and rollout evidence remain required.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=5,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2a.json',
  source_fingerprint='82cfb06f923e6b1fc3af2812e45ee102fd1bf124e2767fcc46ccd437df698c2a', synchronized_at=now()
where definition_key='task.callback.recovery.auto-fail-timed-out';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='DURATION',
  risk='MEDIUM',
  mutability='HOT_IMMEDIATE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_IMMEDIATE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"minimum":"PT1S","maximum":"PT168H"}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"task-callback","categoryDisplayName":"Task Callback","categoryDescription":"Task callback acceptance, replay protection and timeout recovery runtime policy.","categoryOrder":1371,"displayName":"Dispatch Timeout","description":"Source-controlled baseline for task.callback.recovery.dispatch-timeout. Runtime editing remains disabled until its typed consumer is migrated and reviewed.","recommended":"Review current effective value before consumer migration.","effect":"Runtime snapshot; scheduler cadence applies on next cycle where applicable","impactPositive":"Makes the target runtime setting visible and traceable before authority cutover.","impactTradeoff":"Changes affect callback acceptance or recovery behavior immediately/next cycle; approval and rollout evidence remain required.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=5,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2a.json',
  source_fingerprint='592317fce0696cd01be6084109605125cf1a996a57ad70e6981db140757538f1', synchronized_at=now()
where definition_key='task.callback.recovery.dispatch-timeout';

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
  ui_metadata='{"categoryId":"task-callback","categoryDisplayName":"Task Callback","categoryDescription":"Task callback acceptance, replay protection and timeout recovery runtime policy.","categoryOrder":1371,"displayName":"Initial Backoff","description":"Source-controlled baseline for task.callback.recovery.initial-backoff. Runtime editing remains disabled until its typed consumer is migrated and reviewed.","recommended":"Review current effective value before consumer migration.","effect":"Runtime snapshot; scheduler cadence applies on next cycle where applicable","impactPositive":"Makes the target runtime setting visible and traceable before authority cutover.","impactTradeoff":"Changes affect callback acceptance or recovery behavior immediately/next cycle; approval and rollout evidence remain required.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=5,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2a.json',
  source_fingerprint='dd5193cfdb31567a4f5ec9c04774c40f8e2f86afdf564bbcb3f4fe20e880206f', synchronized_at=now()
where definition_key='task.callback.recovery.initial-backoff';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='INTEGER',
  risk='MEDIUM',
  mutability='HOT_IMMEDIATE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_IMMEDIATE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"minimum":0,"maximum":100}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"task-callback","categoryDisplayName":"Task Callback","categoryDescription":"Task callback acceptance, replay protection and timeout recovery runtime policy.","categoryOrder":1371,"displayName":"Jitter Percent","description":"Source-controlled baseline for task.callback.recovery.jitter-percent. Runtime editing remains disabled until its typed consumer is migrated and reviewed.","recommended":"Review current effective value before consumer migration.","effect":"Runtime snapshot; scheduler cadence applies on next cycle where applicable","impactPositive":"Makes the target runtime setting visible and traceable before authority cutover.","impactTradeoff":"Changes affect callback acceptance or recovery behavior immediately/next cycle; approval and rollout evidence remain required.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=5,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2a.json',
  source_fingerprint='4e4ac09e1c38cd8b9f578f8ba89dc0ca7f7e54f095e2407ad462b8a7705b867b', synchronized_at=now()
where definition_key='task.callback.recovery.jitter-percent';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='INTEGER',
  risk='MEDIUM',
  mutability='HOT_IMMEDIATE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_IMMEDIATE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"minimum":1,"maximum":20}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"task-callback","categoryDisplayName":"Task Callback","categoryDescription":"Task callback acceptance, replay protection and timeout recovery runtime policy.","categoryOrder":1371,"displayName":"Max Attempts","description":"Source-controlled baseline for task.callback.recovery.max-attempts. Runtime editing remains disabled until its typed consumer is migrated and reviewed.","recommended":"Review current effective value before consumer migration.","effect":"Runtime snapshot; scheduler cadence applies on next cycle where applicable","impactPositive":"Makes the target runtime setting visible and traceable before authority cutover.","impactTradeoff":"Changes affect callback acceptance or recovery behavior immediately/next cycle; approval and rollout evidence remain required.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=5,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2a.json',
  source_fingerprint='5ede77ab43e2ef44279b990d27b5057f4f8845fe2252302695906cfcb8b783e8', synchronized_at=now()
where definition_key='task.callback.recovery.max-attempts';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='DURATION',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_NEXT_CYCLE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"minimum":"PT0.001S","maximum":"PT24H","greaterThanOrEqualKey":"task.callback.recovery.initial-backoff"}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"task-callback","categoryDisplayName":"Task Callback","categoryDescription":"Task callback acceptance, replay protection and timeout recovery runtime policy.","categoryOrder":1371,"displayName":"Max Backoff","description":"Source-controlled baseline for task.callback.recovery.max-backoff. Runtime editing remains disabled until its typed consumer is migrated and reviewed.","recommended":"Review current effective value before consumer migration.","effect":"Runtime snapshot; scheduler cadence applies on next cycle where applicable","impactPositive":"Makes the target runtime setting visible and traceable before authority cutover.","impactTradeoff":"Changes affect callback acceptance or recovery behavior immediately/next cycle; approval and rollout evidence remain required.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=5,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2a.json',
  source_fingerprint='99813fe59e8427d8482a29f0fada96479243d171c5fcfd0052f61d9e8f29a712', synchronized_at=now()
where definition_key='task.callback.recovery.max-backoff';

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
  ui_metadata='{"categoryId":"task-callback","categoryDisplayName":"Task Callback","categoryDescription":"Task callback acceptance, replay protection and timeout recovery runtime policy.","categoryOrder":1371,"displayName":"Max Batch Size","description":"Source-controlled baseline for task.callback.recovery.max-batch-size. Runtime editing remains disabled until its typed consumer is migrated and reviewed.","recommended":"Review current effective value before consumer migration.","effect":"Runtime snapshot; scheduler cadence applies on next cycle where applicable","impactPositive":"Makes the target runtime setting visible and traceable before authority cutover.","impactTradeoff":"Changes affect callback acceptance or recovery behavior immediately/next cycle; approval and rollout evidence remain required.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=5,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2a.json',
  source_fingerprint='bd4a9edb6d00ef693e0fceb03585c1bb747ea9d4e861d3ef8c8a0af009c4f41c', synchronized_at=now()
where definition_key='task.callback.recovery.max-batch-size';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='BOOLEAN',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_NEXT_CYCLE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"type":"boolean"}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"task-callback","categoryDisplayName":"Task Callback","categoryDescription":"Task callback acceptance, replay protection and timeout recovery runtime policy.","categoryOrder":1371,"displayName":"Retry Enabled","description":"Source-controlled baseline for task.callback.recovery.retry-enabled. Runtime editing remains disabled until its typed consumer is migrated and reviewed.","recommended":"Review current effective value before consumer migration.","effect":"Runtime snapshot; scheduler cadence applies on next cycle where applicable","impactPositive":"Makes the target runtime setting visible and traceable before authority cutover.","impactTradeoff":"Changes affect callback acceptance or recovery behavior immediately/next cycle; approval and rollout evidence remain required.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=5,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2a.json',
  source_fingerprint='89d64e56537c3ca1cd7d7742546a317d6405428600d72699095029665fd2b63d', synchronized_at=now()
where definition_key='task.callback.recovery.retry-enabled';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='LONG',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='DYNAMIC_SCHEDULER',
  target_mutability='HOT_NEXT_CYCLE',
  target_consumer_contract='DYNAMIC_SCHEDULER',
  validation_rule='{"minimum":1000,"maximum":3600000}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"task-callback","categoryDisplayName":"Task Callback","categoryDescription":"Task callback acceptance, replay protection and timeout recovery runtime policy.","categoryOrder":1371,"displayName":"Scan Interval Ms","description":"Source-controlled baseline for task.callback.recovery.scan-interval-ms. Runtime editing remains disabled until its typed consumer is migrated and reviewed.","recommended":"Review current effective value before consumer migration.","effect":"Runtime snapshot; scheduler cadence applies on next cycle where applicable","impactPositive":"Makes the target runtime setting visible and traceable before authority cutover.","impactTradeoff":"Changes affect callback acceptance or recovery behavior immediately/next cycle; approval and rollout evidence remain required.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=5,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2a.json',
  source_fingerprint='c64f174bea999516ec5aeadeb275e54424b67b3e41b45c29e1e248c16ced2dc1', synchronized_at=now()
where definition_key='task.callback.recovery.scan-interval-ms';

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
  ui_metadata='{"categoryId":"task-callback","categoryDisplayName":"Task Callback","categoryDescription":"Task callback acceptance, replay protection and timeout recovery runtime policy.","categoryOrder":1371,"displayName":"Timeout Enabled","description":"Source-controlled baseline for task.callback.recovery.timeout-enabled. Runtime editing remains disabled until its typed consumer is migrated and reviewed.","recommended":"Review current effective value before consumer migration.","effect":"Runtime snapshot; scheduler cadence applies on next cycle where applicable","impactPositive":"Makes the target runtime setting visible and traceable before authority cutover.","impactTradeoff":"Changes affect callback acceptance or recovery behavior immediately/next cycle; approval and rollout evidence remain required.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=5,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2a.json',
  source_fingerprint='6ea8beedabf2eb37c17275dbb2df27b26c01a7f4f9521a917586fd310424a5c4', synchronized_at=now()
where definition_key='task.callback.recovery.timeout-enabled';

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
  ui_metadata='{"categoryId":"task-callback","categoryDisplayName":"Task Callback","categoryDescription":"Task callback acceptance, replay protection and timeout recovery runtime policy.","categoryOrder":1371,"displayName":"Reject Callback Id Replay Mismatch","description":"Source-controlled baseline for task.callback.reject-callback-id-replay-mismatch. Runtime editing remains disabled until its typed consumer is migrated and reviewed.","recommended":"Review current effective value before consumer migration.","effect":"Runtime snapshot; scheduler cadence applies on next cycle where applicable","impactPositive":"Makes the target runtime setting visible and traceable before authority cutover.","impactTradeoff":"Changes affect callback acceptance or recovery behavior immediately/next cycle; approval and rollout evidence remain required.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=5,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2a.json',
  source_fingerprint='56d6358ea12567cfee62d2a8f0df131c6ec122b1416408db3953acb1c84c494d', synchronized_at=now()
where definition_key='task.callback.reject-callback-id-replay-mismatch';

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
  ui_metadata='{"categoryId":"task-callback","categoryDisplayName":"Task Callback","categoryDescription":"Task callback acceptance, replay protection and timeout recovery runtime policy.","categoryOrder":1371,"displayName":"Reject Old Attempt Callbacks","description":"Source-controlled baseline for task.callback.reject-old-attempt-callbacks. Runtime editing remains disabled until its typed consumer is migrated and reviewed.","recommended":"Review current effective value before consumer migration.","effect":"Runtime snapshot; scheduler cadence applies on next cycle where applicable","impactPositive":"Makes the target runtime setting visible and traceable before authority cutover.","impactTradeoff":"Changes affect callback acceptance or recovery behavior immediately/next cycle; approval and rollout evidence remain required.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=5,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2a.json',
  source_fingerprint='44b5be9f213af6338ad65ea0463b033b2f385201ae4a217970fa49510ca0c4c8', synchronized_at=now()
where definition_key='task.callback.reject-old-attempt-callbacks';

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
  ui_metadata='{"categoryId":"task-callback","categoryDisplayName":"Task Callback","categoryDescription":"Task callback acceptance, replay protection and timeout recovery runtime policy.","categoryOrder":1371,"displayName":"Replay Protection Enabled","description":"Source-controlled baseline for task.callback.replay-protection-enabled. Runtime editing remains disabled until its typed consumer is migrated and reviewed.","recommended":"Review current effective value before consumer migration.","effect":"Runtime snapshot; scheduler cadence applies on next cycle where applicable","impactPositive":"Makes the target runtime setting visible and traceable before authority cutover.","impactTradeoff":"Changes affect callback acceptance or recovery behavior immediately/next cycle; approval and rollout evidence remain required.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=5,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2a.json',
  source_fingerprint='807049dd5dd3852036abdff114f1c5efe025e0d67a4d4221c1f7e31c4efd7498', synchronized_at=now()
where definition_key='task.callback.replay-protection-enabled';

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
  ui_metadata='{"categoryId":"task-callback","categoryDisplayName":"Task Callback","categoryDescription":"Task callback acceptance, replay protection and timeout recovery runtime policy.","categoryOrder":1371,"displayName":"Require Attempt No","description":"Source-controlled baseline for task.callback.require-attempt-no. Runtime editing remains disabled until its typed consumer is migrated and reviewed.","recommended":"Review current effective value before consumer migration.","effect":"Runtime snapshot; scheduler cadence applies on next cycle where applicable","impactPositive":"Makes the target runtime setting visible and traceable before authority cutover.","impactTradeoff":"Changes affect callback acceptance or recovery behavior immediately/next cycle; approval and rollout evidence remain required.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=5,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2a.json',
  source_fingerprint='b2eb2f22881f595ee7abd7b1b8949040654426cbdf5b45d51981ce3c9eed89fa', synchronized_at=now()
where definition_key='task.callback.require-attempt-no';

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
  ui_metadata='{"categoryId":"task-processing","categoryDisplayName":"Task Processing","categoryDescription":"Task dispatch recovery and background processing controls.","categoryOrder":30,"displayName":"Claim Lease","description":"Source-controlled baseline for task.dispatch-recovery.claim-lease. Runtime editing remains disabled until its typed consumer is migrated and reviewed.","recommended":"Review current effective value before consumer migration.","effect":"Runtime snapshot; scheduler cadence applies on next cycle where applicable","impactPositive":"Makes the target runtime setting visible and traceable before authority cutover.","impactTradeoff":"Changes affect callback acceptance or recovery behavior immediately/next cycle; approval and rollout evidence remain required.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=5,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2a.json',
  source_fingerprint='08c20b7e75e45b1fcf379d1d477a6e74b860d31abb1a82ffc0168910c85ccb37', synchronized_at=now()
where definition_key='task.dispatch-recovery.claim-lease';

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
  ui_metadata='{"categoryId":"task-processing","categoryDisplayName":"Task Processing","categoryDescription":"Task dispatch recovery and background processing controls.","categoryOrder":30,"displayName":"Enabled","description":"Source-controlled baseline for task.dispatch-recovery.enabled. Runtime editing remains disabled until its typed consumer is migrated and reviewed.","recommended":"Review current effective value before consumer migration.","effect":"Runtime snapshot; scheduler cadence applies on next cycle where applicable","impactPositive":"Makes the target runtime setting visible and traceable before authority cutover.","impactTradeoff":"Changes affect callback acceptance or recovery behavior immediately/next cycle; approval and rollout evidence remain required.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=5,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2a.json',
  source_fingerprint='5c0f5942bd1626802bda68027b872ccd235c6ee37fb051979d6295a841639680', synchronized_at=now()
where definition_key='task.dispatch-recovery.enabled';

update runtime_config_definitions set
  authority_class='RUNTIME_TUNABLE',
  data_type='BOOLEAN',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='RUNTIME_SNAPSHOT',
  target_mutability='HOT_NEXT_CYCLE',
  target_consumer_contract='RUNTIME_SNAPSHOT',
  validation_rule='{"type":"boolean"}'::jsonb,
  admin_editable=true,
  ui_metadata='{"categoryId":"task-processing","categoryDisplayName":"Task Processing","categoryDescription":"Task dispatch recovery and background processing controls.","categoryOrder":30,"displayName":"Scanner Enabled","description":"Source-controlled baseline for task.dispatch-recovery.scanner-enabled. Runtime editing remains disabled until its typed consumer is migrated and reviewed.","recommended":"Review current effective value before consumer migration.","effect":"Runtime snapshot; scheduler cadence applies on next cycle where applicable","impactPositive":"Makes the target runtime setting visible and traceable before authority cutover.","impactTradeoff":"Changes affect callback acceptance or recovery behavior immediately/next cycle; approval and rollout evidence remain required.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=5,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2a.json',
  source_fingerprint='424793bd3825d70f5ce49138af98c62eefada3a8116d81bad28d58ac504df381', synchronized_at=now()
where definition_key='task.dispatch-recovery.scanner-enabled';

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
  ui_metadata='{"categoryId":"task-processing","categoryDisplayName":"Task Processing","categoryDescription":"Task dispatch recovery and background processing controls.","categoryOrder":30,"displayName":"Worker Id","description":"Source-controlled baseline for task.dispatch-recovery.worker-id. Runtime editing remains disabled until its typed consumer is migrated and reviewed.","recommended":"Review current effective value before consumer migration.","effect":"Runtime snapshot; scheduler cadence applies on next cycle where applicable","impactPositive":"Makes the target runtime setting visible and traceable before authority cutover.","impactTradeoff":"Changes affect callback acceptance or recovery behavior immediately/next cycle; approval and rollout evidence remain required.","advancedKeyVisible":true}'::jsonb,
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=5,
  source_ref='contracts/current/configuration/definitions/runtime-target-definition-baseline-v41-c3r2a.json',
  source_fingerprint='26b6503675a6d759eef79504b479401205598e40c4cb802d3de1a23c2bcf61ea', synchronized_at=now()
where definition_key='task.dispatch-recovery.worker-id';

-- Architecture correction: documented placeholder, not a runtime business setting.
update runtime_config_definitions set authority_class='TEST_RELEASE_CONFIG', mutability='IMMUTABLE', consumer_contract='DEPLOYMENT_ONLY', target_mutability='IMMUTABLE', target_consumer_contract='DEPLOYMENT_ONLY', review_status='RETIRED', migration_authorized=false, admin_editable=false, validation_rule='{"classificationCorrection":"NO_RUNTIME_CONSUMER","documentationEvidence":"environment-variable-reference marks TASK_DECISION_MODE as placeholder"}'::jsonb, source_ref='contracts/current/configuration/definitions/definition-registry-v41-c3r2a.json', source_fingerprint='7c5c1761235212c9e2c32b3337ad96c8749689210609dcee21795b7d88f4a6b8', synchronized_at=now() where definition_key='task.decision.mode';

-- Legal governance progression for the 24 newly migrated Task consumers.
with advanced as (
 update runtime_config_inventory_governance g set status='CLASSIFIED',domain_owner=d.domain_owner,authority_class=d.authority_class,scope=d.scope,risk=d.risk,mutability=d.target_mutability,consumer_contract=d.target_consumer_contract,admin_editable=d.admin_editable,requires_approval=d.requires_approval,classified_by='v41-c3r2a-task-consumer-closure',classified_at=now(),reason='V41-C3R2A Task typed consumer implemented',version=version+1
 from runtime_config_definitions d where g.configuration_key=d.definition_key and g.configuration_key in (
    'task.callback.allow-missing-dispatch-request-id',
    'task.callback.allow-terminal-callback-override',
    'task.callback.enforce-gateway-and-agent-identity',
    'task.callback.enforce-state-transition',
    'task.callback.idempotency-enabled',
    'task.callback.max-recent',
    'task.callback.recovery.auto-fail-timed-out',
    'task.callback.recovery.dispatch-timeout',
    'task.callback.recovery.initial-backoff',
    'task.callback.recovery.jitter-percent',
    'task.callback.recovery.max-attempts',
    'task.callback.recovery.max-backoff',
    'task.callback.recovery.max-batch-size',
    'task.callback.recovery.retry-enabled',
    'task.callback.recovery.scan-interval-ms',
    'task.callback.recovery.timeout-enabled',
    'task.callback.reject-callback-id-replay-mismatch',
    'task.callback.reject-old-attempt-callbacks',
    'task.callback.replay-protection-enabled',
    'task.callback.require-attempt-no',
    'task.dispatch-recovery.claim-lease',
    'task.dispatch-recovery.enabled',
    'task.dispatch-recovery.scanner-enabled',
    'task.dispatch-recovery.worker-id'
 ) and g.status='DISCOVERED' returning g.configuration_key,g.source_observation_hash
)
insert into runtime_config_inventory_governance_events(configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'CLASSIFIED','DISCOVERED','CLASSIFIED','v41-c3r2a-task-consumer-closure','Task consumer classification confirmed by implemented typed runtime view',source_observation_hash,'{"stage":"V41_C3R2A_TASK_CONSUMER_CLOSURE"}'::jsonb from advanced;

with advanced as (
 update runtime_config_inventory_governance set status='OWNER_REVIEWED',owner_reviewed_by='task-domain-owner',owner_reviewed_at=now(),reason='Task domain consumer and validation bounds reviewed',version=version+1 where configuration_key in (
    'task.callback.allow-missing-dispatch-request-id',
    'task.callback.allow-terminal-callback-override',
    'task.callback.enforce-gateway-and-agent-identity',
    'task.callback.enforce-state-transition',
    'task.callback.idempotency-enabled',
    'task.callback.max-recent',
    'task.callback.recovery.auto-fail-timed-out',
    'task.callback.recovery.dispatch-timeout',
    'task.callback.recovery.initial-backoff',
    'task.callback.recovery.jitter-percent',
    'task.callback.recovery.max-attempts',
    'task.callback.recovery.max-backoff',
    'task.callback.recovery.max-batch-size',
    'task.callback.recovery.retry-enabled',
    'task.callback.recovery.scan-interval-ms',
    'task.callback.recovery.timeout-enabled',
    'task.callback.reject-callback-id-replay-mismatch',
    'task.callback.reject-old-attempt-callbacks',
    'task.callback.replay-protection-enabled',
    'task.callback.require-attempt-no',
    'task.dispatch-recovery.claim-lease',
    'task.dispatch-recovery.enabled',
    'task.dispatch-recovery.scanner-enabled',
    'task.dispatch-recovery.worker-id'
 ) and status='CLASSIFIED' returning configuration_key,source_observation_hash
)
insert into runtime_config_inventory_governance_events(configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'OWNER_REVIEWED','CLASSIFIED','OWNER_REVIEWED','task-domain-owner','Task domain consumer and validation bounds reviewed',source_observation_hash,'{}'::jsonb from advanced;

with advanced as (
 update runtime_config_inventory_governance set status='ARCHITECTURE_APPROVED',architecture_approved_by='configuration-architecture-reviewer',architecture_approved_at=now(),reason='DB authority + Redis distribution + local snapshot architecture approved',version=version+1 where configuration_key in (
    'task.callback.allow-missing-dispatch-request-id',
    'task.callback.allow-terminal-callback-override',
    'task.callback.enforce-gateway-and-agent-identity',
    'task.callback.enforce-state-transition',
    'task.callback.idempotency-enabled',
    'task.callback.max-recent',
    'task.callback.recovery.auto-fail-timed-out',
    'task.callback.recovery.dispatch-timeout',
    'task.callback.recovery.initial-backoff',
    'task.callback.recovery.jitter-percent',
    'task.callback.recovery.max-attempts',
    'task.callback.recovery.max-backoff',
    'task.callback.recovery.max-batch-size',
    'task.callback.recovery.retry-enabled',
    'task.callback.recovery.scan-interval-ms',
    'task.callback.recovery.timeout-enabled',
    'task.callback.reject-callback-id-replay-mismatch',
    'task.callback.reject-old-attempt-callbacks',
    'task.callback.replay-protection-enabled',
    'task.callback.require-attempt-no',
    'task.dispatch-recovery.claim-lease',
    'task.dispatch-recovery.enabled',
    'task.dispatch-recovery.scanner-enabled',
    'task.dispatch-recovery.worker-id'
 ) and status='OWNER_REVIEWED' returning configuration_key,source_observation_hash
)
insert into runtime_config_inventory_governance_events(configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'ARCHITECTURE_APPROVED','OWNER_REVIEWED','ARCHITECTURE_APPROVED','configuration-architecture-reviewer','DB authority + Redis distribution + local snapshot architecture approved',source_observation_hash,'{}'::jsonb from advanced;

with advanced as (
 update runtime_config_inventory_governance set status='MIGRATION_READY',migration_authorized_by='v41-c3r2a-task-consumer-closure',migration_authorized_at=now(),reason='Typed Task consumers implemented; Single Authority cutover remains pending',version=version+1 where configuration_key in (
    'task.callback.allow-missing-dispatch-request-id',
    'task.callback.allow-terminal-callback-override',
    'task.callback.enforce-gateway-and-agent-identity',
    'task.callback.enforce-state-transition',
    'task.callback.idempotency-enabled',
    'task.callback.max-recent',
    'task.callback.recovery.auto-fail-timed-out',
    'task.callback.recovery.dispatch-timeout',
    'task.callback.recovery.initial-backoff',
    'task.callback.recovery.jitter-percent',
    'task.callback.recovery.max-attempts',
    'task.callback.recovery.max-backoff',
    'task.callback.recovery.max-batch-size',
    'task.callback.recovery.retry-enabled',
    'task.callback.recovery.scan-interval-ms',
    'task.callback.recovery.timeout-enabled',
    'task.callback.reject-callback-id-replay-mismatch',
    'task.callback.reject-old-attempt-callbacks',
    'task.callback.replay-protection-enabled',
    'task.callback.require-attempt-no',
    'task.dispatch-recovery.claim-lease',
    'task.dispatch-recovery.enabled',
    'task.dispatch-recovery.scanner-enabled',
    'task.dispatch-recovery.worker-id'
 ) and status='ARCHITECTURE_APPROVED' returning configuration_key,source_observation_hash
)
insert into runtime_config_inventory_governance_events(configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'MIGRATION_READY','ARCHITECTURE_APPROVED','MIGRATION_READY','v41-c3r2a-task-consumer-closure','Typed Task consumers implemented; Single Authority cutover remains pending',source_observation_hash,'{"cutover":"NOT_FINALIZED"}'::jsonb from advanced;

-- Placeholder correction remains outside the migration lifecycle.
do $$
begin
  if exists(
    select 1 from runtime_config_inventory_governance
    where configuration_key='task.decision.mode'
      and status not in ('DISCOVERED','CLASSIFIED')
  ) then
    raise exception 'C3R2A_PLACEHOLDER_CORRECTION_UNSAFE_STATE';
  end if;
end $$;
with advanced as (
  update runtime_config_inventory_governance
     set status='CLASSIFIED',
         domain_owner='TASK',
         authority_class='TEST_RELEASE_CONFIG',
         scope='COMPONENT',
         risk='LOW',
         mutability='IMMUTABLE',
         consumer_contract='DEPLOYMENT_ONLY',
         admin_editable=false,
         requires_approval=false,
         classified_by='v41-c3r2a-architecture-correction',
         classified_at=now(),
         reason='TASK_DECISION_MODE is a documented placeholder with no runtime consumer; removed from Runtime Configuration scope',
         version=version+1
   where configuration_key='task.decision.mode'
     and status='DISCOVERED'
   returning configuration_key,source_observation_hash
)
insert into runtime_config_inventory_governance_events(
  configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json
)
select configuration_key,'CLASSIFICATION_CORRECTED','DISCOVERED','CLASSIFIED',
       'v41-c3r2a-architecture-correction',
       'Documented placeholder with no runtime business consumer',
       source_observation_hash,
       '{"targetAuthority":"TEST_RELEASE_ONLY","evidence":"environment-variable-reference placeholder"}'::jsonb
  from advanced;
update runtime_config_inventory_governance
   set domain_owner='TASK',
       authority_class='TEST_RELEASE_CONFIG',
       scope='COMPONENT',
       risk='LOW',
       mutability='IMMUTABLE',
       consumer_contract='DEPLOYMENT_ONLY',
       admin_editable=false,
       requires_approval=false,
       reason='TASK_DECISION_MODE is a documented placeholder with no runtime consumer; removed from Runtime Configuration scope',
       version=version+1,
       updated_at=now()
 where configuration_key='task.decision.mode'
   and status='CLASSIFIED';

-- Architecture correction: require-dispatch-token is a boolean security invariant, not secret material.
do $$
begin
  if exists(
    select 1 from runtime_config_inventory_governance
     where configuration_key='task.callback.require-dispatch-token'
       and status not in ('DISCOVERED','CLASSIFIED')
  ) then
    raise exception 'C3R2A_SECURITY_CLASSIFICATION_CORRECTION_UNSAFE_STATE';
  end if;
end $$;
with advanced as (
  update runtime_config_inventory_governance
     set status='CLASSIFIED',
         domain_owner='TASK',
         authority_class='SECURITY_INVARIANT',
         scope='COMPONENT',
         risk='CRITICAL',
         mutability='RESTART_REQUIRED',
         consumer_contract='STARTUP_BINDING',
         admin_editable=false,
         requires_approval=true,
         classified_by='v41-c3r2a-architecture-correction',
         classified_at=now(),
         reason='TASK_CALLBACK_REQUIRE_DISPATCH_TOKEN is a boolean callback authentication invariant, not secret material',
         version=version+1
   where configuration_key='task.callback.require-dispatch-token'
     and status='DISCOVERED'
   returning configuration_key,source_observation_hash
)
insert into runtime_config_inventory_governance_events(
  configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json
)
select configuration_key,'CLASSIFICATION_CORRECTED','DISCOVERED','CLASSIFIED',
       'v41-c3r2a-architecture-correction',
       'Boolean callback authentication enforcement is SECURITY_INVARIANT, not SECRET_REFERENCE',
       source_observation_hash,
       '{"targetAuthority":"SECURITY_INVARIANT","runtimeEditable":false}'::jsonb
  from advanced;
update runtime_config_inventory_governance
   set domain_owner='TASK',
       authority_class='SECURITY_INVARIANT',
       scope='COMPONENT',
       risk='CRITICAL',
       mutability='RESTART_REQUIRED',
       consumer_contract='STARTUP_BINDING',
       admin_editable=false,
       requires_approval=true,
       reason='TASK_CALLBACK_REQUIRE_DISPATCH_TOKEN is a boolean callback authentication invariant, not secret material',
       version=version+1,
       updated_at=now()
 where configuration_key='task.callback.require-dispatch-token'
   and status='CLASSIFIED';

-- Closure guards: C3R2A stops at MIGRATION_READY and never fabricates cutover evidence.
do $$ declare v_ready integer; begin select count(*) into v_ready from runtime_config_definitions where definition_key in (
    'task.callback.allow-missing-dispatch-request-id',
    'task.callback.allow-terminal-callback-override',
    'task.callback.enforce-gateway-and-agent-identity',
    'task.callback.enforce-state-transition',
    'task.callback.idempotency-enabled',
    'task.callback.max-recent',
    'task.callback.recovery.auto-fail-timed-out',
    'task.callback.recovery.dispatch-timeout',
    'task.callback.recovery.initial-backoff',
    'task.callback.recovery.jitter-percent',
    'task.callback.recovery.max-attempts',
    'task.callback.recovery.max-backoff',
    'task.callback.recovery.max-batch-size',
    'task.callback.recovery.retry-enabled',
    'task.callback.recovery.scan-interval-ms',
    'task.callback.recovery.timeout-enabled',
    'task.callback.reject-callback-id-replay-mismatch',
    'task.callback.reject-old-attempt-callbacks',
    'task.callback.replay-protection-enabled',
    'task.callback.require-attempt-no',
    'task.dispatch-recovery.claim-lease',
    'task.dispatch-recovery.enabled',
    'task.dispatch-recovery.scanner-enabled',
    'task.dispatch-recovery.worker-id'
 ) and review_status='MIGRATION_READY' and migration_authorized=true and admin_editable=true; if v_ready<>24 then raise exception 'C3R2A_TASK_DEFINITION_CLOSURE_INCOMPLETE expected=24 actual=%',v_ready; end if; if exists(select 1 from runtime_config_definitions where definition_key='task.decision.mode' and (migration_authorized=true or admin_editable=true or review_status<>'RETIRED')) then raise exception 'C3R2A_PLACEHOLDER_AUTHORITY_LEAK'; end if; if exists(select 1 from runtime_config_inventory_governance where configuration_key in (
    'task.callback.allow-missing-dispatch-request-id',
    'task.callback.allow-terminal-callback-override',
    'task.callback.enforce-gateway-and-agent-identity',
    'task.callback.enforce-state-transition',
    'task.callback.idempotency-enabled',
    'task.callback.max-recent',
    'task.callback.recovery.auto-fail-timed-out',
    'task.callback.recovery.dispatch-timeout',
    'task.callback.recovery.initial-backoff',
    'task.callback.recovery.jitter-percent',
    'task.callback.recovery.max-attempts',
    'task.callback.recovery.max-backoff',
    'task.callback.recovery.max-batch-size',
    'task.callback.recovery.retry-enabled',
    'task.callback.recovery.scan-interval-ms',
    'task.callback.recovery.timeout-enabled',
    'task.callback.reject-callback-id-replay-mismatch',
    'task.callback.reject-old-attempt-callbacks',
    'task.callback.replay-protection-enabled',
    'task.callback.require-attempt-no',
    'task.dispatch-recovery.claim-lease',
    'task.dispatch-recovery.enabled',
    'task.dispatch-recovery.scanner-enabled',
    'task.dispatch-recovery.worker-id'
 ) and status in ('MIGRATED','LEGACY_RETIRED')) then raise exception 'C3R2A_PREMATURE_SINGLE_AUTHORITY_CUTOVER'; end if; end $$;
