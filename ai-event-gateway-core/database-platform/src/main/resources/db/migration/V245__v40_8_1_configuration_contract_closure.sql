-- V40-8.1: configuration contract closure.
-- Source-controlled definition JSON remains the authoring authority. This migration materializes
-- operator metadata/runtime binding fields so Admin UI and runtime validation no longer need a
-- second handwritten metadata registry.

alter table runtime_config_definitions add column if not exists display_name varchar(255);
alter table runtime_config_definitions add column if not exists domain_owner varchar(128);
alter table runtime_config_definitions add column if not exists scope_ref varchar(255);
alter table runtime_config_definitions add column if not exists unit varchar(64);
alter table runtime_config_definitions add column if not exists requires_approval boolean not null default true;
alter table runtime_config_definitions add column if not exists admin_editable boolean not null default false;
alter table runtime_config_definitions add column if not exists initial_seed jsonb not null default '{"strategy":"NONE"}'::jsonb;
alter table runtime_config_definitions add column if not exists introduced_version varchar(64);
alter table runtime_config_definitions add column if not exists ui_metadata jsonb not null default '{}'::jsonb;
alter table runtime_config_definitions add column if not exists config_set_key varchar(255);

-- BEGIN GENERATED DEFINITION MATERIALIZATION
update runtime_config_definitions set
  display_name='Adapter work batch size',
  owner='ADAPTER_EXECUTION',
  domain_owner='ADAPTER_EXECUTION',
  authority_class='RUNTIME_TUNABLE',
  scope='COMPONENT',
  scope_ref='ADAPTER_EXECUTION',
  data_type='INTEGER',
  unit='items',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='RUNTIME_SNAPSHOT',
  environment_applicability='["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,
  validation_rule='{"minimum":1,"maximum":1000}'::jsonb,
  requires_approval=true,
  admin_editable=true,
  initial_seed='{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb,
  introduced_version='V40-5',
  ui_metadata='{"categoryId":"adapter-execution","displayName":"Adapter work batch size","description":"Maximum adapter actions claimed in one executor cycle.","recommended":"1–200","effect":"Next execution cycle","impactPositive":"Larger batches can improve throughput.","impactTradeoff":"Large batches can increase burst load and lock duration.","advancedKeyVisible":true}'::jsonb,
  config_set_key='RUNTIME/ADAPTER_EXECUTION/SYSTEM',
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=3,
  source_ref='contracts/current/configuration/definitions/provisional-pilot-definitions.json',
  source_fingerprint='252374efdaeaa951d4f8cc426ccc3d3a5c4a756bf02944f2efceab04a08465c8',
  synchronized_at=now()
where definition_key='adapter-executor.batch-size';

update runtime_config_definitions set
  display_name='Adapter execution timeout',
  owner='ADAPTER_EXECUTION',
  domain_owner='ADAPTER_EXECUTION',
  authority_class='RUNTIME_TUNABLE',
  scope='COMPONENT',
  scope_ref='ADAPTER_EXECUTION',
  data_type='DURATION',
  unit='duration',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='RUNTIME_SNAPSHOT',
  environment_applicability='["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,
  validation_rule='{"minimum":"PT0.001S"}'::jsonb,
  requires_approval=true,
  admin_editable=true,
  initial_seed='{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb,
  introduced_version='V40-5',
  ui_metadata='{"categoryId":"adapter-execution","displayName":"Adapter execution timeout","description":"Maximum time allowed for one governed adapter execution.","recommended":"PT10S–PT5M","effect":"Next execution cycle","impactPositive":"Allows slow but healthy providers enough time to respond.","impactTradeoff":"Long timeouts keep execution capacity occupied longer.","advancedKeyVisible":true}'::jsonb,
  config_set_key='RUNTIME/ADAPTER_EXECUTION/SYSTEM',
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=3,
  source_ref='contracts/current/configuration/definitions/provisional-pilot-definitions.json',
  source_fingerprint='97ee92a990cf09f39c86980dac7be083f2e8dbf6cb641d3ffa920d6158487694',
  synchronized_at=now()
where definition_key='adapter-executor.execution-timeout';

update runtime_config_definitions set
  display_name='Adapter initial retry delay',
  owner='ADAPTER_EXECUTION',
  domain_owner='ADAPTER_EXECUTION',
  authority_class='RUNTIME_TUNABLE',
  scope='COMPONENT',
  scope_ref='ADAPTER_EXECUTION',
  data_type='DURATION',
  unit='duration',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='RUNTIME_SNAPSHOT',
  environment_applicability='["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,
  validation_rule='{"minimum":"PT0.001S"}'::jsonb,
  requires_approval=true,
  admin_editable=true,
  initial_seed='{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb,
  introduced_version='V40-5',
  ui_metadata='{"categoryId":"adapter-execution","displayName":"Adapter initial retry delay","description":"Delay before the first retry of an adapter action.","recommended":"PT1S–PT30S","effect":"Next execution cycle","impactPositive":"Avoids immediate repeated calls during transient provider failures.","impactTradeoff":"Long delays increase action completion latency.","advancedKeyVisible":true}'::jsonb,
  config_set_key='RUNTIME/ADAPTER_EXECUTION/SYSTEM',
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=3,
  source_ref='contracts/current/configuration/definitions/provisional-pilot-definitions.json',
  source_fingerprint='30244faf0bcbf5171d9d0adef50bacdb181aaa20efd35efdc9061e6155336518',
  synchronized_at=now()
where definition_key='adapter-executor.initial-backoff';

update runtime_config_definitions set
  display_name='Issue link reconciliation interval',
  owner='ADAPTER_EXECUTION',
  domain_owner='ADAPTER_EXECUTION',
  authority_class='RUNTIME_TUNABLE',
  scope='COMPONENT',
  scope_ref='ADAPTER_EXECUTION',
  data_type='DURATION',
  unit='duration',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='DYNAMIC_SCHEDULER',
  environment_applicability='["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,
  validation_rule='{"minimum":"PT0.25S","maximum":"PT1H"}'::jsonb,
  requires_approval=true,
  admin_editable=true,
  initial_seed='{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb,
  introduced_version='V40-8',
  ui_metadata='{"categoryId":"adapter-execution","displayName":"Issue link reconciliation interval","description":"How long the projection recovery scheduler waits after each completed cycle.","recommended":"PT5S–PT2M","effect":"Next scheduler cycle","impactPositive":"Shorter intervals can repair durable Issue links sooner.","impactTradeoff":"Very short intervals increase database reconciliation pressure.","advancedKeyVisible":true}'::jsonb,
  config_set_key='RUNTIME/ADAPTER_EXECUTION/SYSTEM',
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=3,
  source_ref='contracts/current/configuration/definitions/dynamic-scheduler-definitions.json',
  source_fingerprint='d3c6253e32643be95ebd631f04f18bdeddfd496fa57bf7a91c9a251a1667661f',
  synchronized_at=now()
where definition_key='adapter-executor.issue.link-projection-reconciliation-delay';

update runtime_config_definitions set
  display_name='Adapter maximum attempts',
  owner='ADAPTER_EXECUTION',
  domain_owner='ADAPTER_EXECUTION',
  authority_class='RUNTIME_TUNABLE',
  scope='COMPONENT',
  scope_ref='ADAPTER_EXECUTION',
  data_type='INTEGER',
  unit='attempts',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='RUNTIME_SNAPSHOT',
  environment_applicability='["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,
  validation_rule='{"minimum":1}'::jsonb,
  requires_approval=true,
  admin_editable=true,
  initial_seed='{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb,
  introduced_version='V40-5',
  ui_metadata='{"categoryId":"adapter-execution","displayName":"Adapter maximum attempts","description":"Maximum governed attempts for one adapter action.","recommended":"1–10","effect":"Next execution cycle","impactPositive":"Improves resilience to transient provider errors.","impactTradeoff":"More attempts can extend time before terminal failure.","advancedKeyVisible":true}'::jsonb,
  config_set_key='RUNTIME/ADAPTER_EXECUTION/SYSTEM',
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=3,
  source_ref='contracts/current/configuration/definitions/provisional-pilot-definitions.json',
  source_fingerprint='84ee7dbf45dc874e37b967aebd999bc73691aa51cc7a360fa4a490c095e8fba4',
  synchronized_at=now()
where definition_key='adapter-executor.max-attempts';

update runtime_config_definitions set
  display_name='Adapter maximum retry delay',
  owner='ADAPTER_EXECUTION',
  domain_owner='ADAPTER_EXECUTION',
  authority_class='RUNTIME_TUNABLE',
  scope='COMPONENT',
  scope_ref='ADAPTER_EXECUTION',
  data_type='DURATION',
  unit='duration',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='RUNTIME_SNAPSHOT',
  environment_applicability='["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,
  validation_rule='{"minimum":"PT0.001S","greaterThanOrEqualKey":"adapter-executor.initial-backoff"}'::jsonb,
  requires_approval=true,
  admin_editable=true,
  initial_seed='{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb,
  introduced_version='V40-5',
  ui_metadata='{"categoryId":"adapter-execution","displayName":"Adapter maximum retry delay","description":"Upper bound for adapter retry backoff.","recommended":"At least the initial delay","effect":"Next execution cycle","impactPositive":"Limits provider pressure during longer outages.","impactTradeoff":"Large values make retries recover more slowly after the provider returns.","advancedKeyVisible":true}'::jsonb,
  config_set_key='RUNTIME/ADAPTER_EXECUTION/SYSTEM',
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=3,
  source_ref='contracts/current/configuration/definitions/provisional-pilot-definitions.json',
  source_fingerprint='decb210b99b25063d7d4bb7f6abf2828ab403706f0d856008358d012d39634f4',
  synchronized_at=now()
where definition_key='adapter-executor.max-backoff';

update runtime_config_definitions set
  display_name='Initial retry delay',
  owner='DISPATCH',
  domain_owner='DISPATCH',
  authority_class='RUNTIME_TUNABLE',
  scope='COMPONENT',
  scope_ref='DISPATCH',
  data_type='DURATION',
  unit='duration',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='RUNTIME_SNAPSHOT',
  environment_applicability='["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,
  validation_rule='{"minimum":"PT0.001S"}'::jsonb,
  requires_approval=true,
  admin_editable=true,
  initial_seed='{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb,
  introduced_version='V40-5',
  ui_metadata='{"categoryId":"dispatch-routing","displayName":"Initial retry delay","description":"How long dispatch waits before the first retry.","recommended":"PT1S–PT30S","effect":"Next execution cycle","impactPositive":"A short delay reduces immediate pressure on a temporarily unavailable dependency.","impactTradeoff":"A longer delay increases recovery latency.","advancedKeyVisible":true}'::jsonb,
  config_set_key='RUNTIME/DISPATCH/SYSTEM',
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=3,
  source_ref='contracts/current/configuration/definitions/provisional-pilot-definitions.json',
  source_fingerprint='c03b668aad082f5d8394fd187b68a8731edb4c8c0be655895bc4b4f12b16384d',
  synchronized_at=now()
where definition_key='dispatch.retry.initial-backoff';

update runtime_config_definitions set
  display_name='Retry jitter',
  owner='DISPATCH',
  domain_owner='DISPATCH',
  authority_class='RUNTIME_TUNABLE',
  scope='COMPONENT',
  scope_ref='DISPATCH',
  data_type='INTEGER',
  unit='percent',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='RUNTIME_SNAPSHOT',
  environment_applicability='["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,
  validation_rule='{"minimum":0,"maximum":100}'::jsonb,
  requires_approval=true,
  admin_editable=true,
  initial_seed='{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb,
  introduced_version='V40-5',
  ui_metadata='{"categoryId":"dispatch-routing","displayName":"Retry jitter","description":"Randomizes retry timing to reduce synchronized retry bursts.","recommended":"0–30","effect":"Next execution cycle","impactPositive":"Reduces thundering-herd behavior across concurrent work.","impactTradeoff":"Large jitter makes exact retry timing less predictable.","advancedKeyVisible":true}'::jsonb,
  config_set_key='RUNTIME/DISPATCH/SYSTEM',
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=3,
  source_ref='contracts/current/configuration/definitions/provisional-pilot-definitions.json',
  source_fingerprint='813e7bad12d6062971c45fe51eaa29fb9a9184aa184bab013ecae3069a5c6173',
  synchronized_at=now()
where definition_key='dispatch.retry.jitter-percent';

update runtime_config_definitions set
  display_name='Maximum retry attempts',
  owner='DISPATCH',
  domain_owner='DISPATCH',
  authority_class='RUNTIME_TUNABLE',
  scope='COMPONENT',
  scope_ref='DISPATCH',
  data_type='INTEGER',
  unit='attempts',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='RUNTIME_SNAPSHOT',
  environment_applicability='["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,
  validation_rule='{"minimum":1,"maximum":20}'::jsonb,
  requires_approval=true,
  admin_editable=true,
  initial_seed='{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb,
  introduced_version='V40-5',
  ui_metadata='{"categoryId":"dispatch-routing","displayName":"Maximum retry attempts","description":"How many dispatch attempts are allowed before work is finally failed.","recommended":"3–10","effect":"Next execution cycle","impactPositive":"Temporary failures have more opportunities to recover.","impactTradeoff":"Higher values can delay final failure and operator visibility.","advancedKeyVisible":true}'::jsonb,
  config_set_key='RUNTIME/DISPATCH/SYSTEM',
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=3,
  source_ref='contracts/current/configuration/definitions/provisional-pilot-definitions.json',
  source_fingerprint='1e35c32faacee00630d8480da1303893e990e20e52383e4e398b25a1eb5b9b85',
  synchronized_at=now()
where definition_key='dispatch.retry.max-attempts';

update runtime_config_definitions set
  display_name='Maximum retry delay',
  owner='DISPATCH',
  domain_owner='DISPATCH',
  authority_class='RUNTIME_TUNABLE',
  scope='COMPONENT',
  scope_ref='DISPATCH',
  data_type='DURATION',
  unit='duration',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='RUNTIME_SNAPSHOT',
  environment_applicability='["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,
  validation_rule='{"minimum":"PT0.001S","greaterThanOrEqualKey":"dispatch.retry.initial-backoff"}'::jsonb,
  requires_approval=true,
  admin_editable=true,
  initial_seed='{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb,
  introduced_version='V40-5',
  ui_metadata='{"categoryId":"dispatch-routing","displayName":"Maximum retry delay","description":"Upper bound for dispatch retry backoff.","recommended":"At least the initial delay","effect":"Next execution cycle","impactPositive":"Caps retry pressure during a prolonged outage.","impactTradeoff":"A large cap can delay eventual resolution.","advancedKeyVisible":true}'::jsonb,
  config_set_key='RUNTIME/DISPATCH/SYSTEM',
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=3,
  source_ref='contracts/current/configuration/definitions/provisional-pilot-definitions.json',
  source_fingerprint='71673314ab99326b333ec528213e89f0a8cbeda4145b29dcfb8861f129ee31aa',
  synchronized_at=now()
where definition_key='dispatch.retry.max-backoff';

update runtime_config_definitions set
  display_name='Issue reconciliation batch size',
  owner='ISSUE',
  domain_owner='ISSUE',
  authority_class='RUNTIME_TUNABLE',
  scope='COMPONENT',
  scope_ref='ISSUE',
  data_type='INTEGER',
  unit='items',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='RUNTIME_SNAPSHOT',
  environment_applicability='["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,
  validation_rule='{"minimum":1,"maximum":1000}'::jsonb,
  requires_approval=true,
  admin_editable=true,
  initial_seed='{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb,
  introduced_version='V40-5',
  ui_metadata='{"categoryId":"issue-integration","displayName":"Issue reconciliation batch size","description":"Maximum Issue projections reconciled in one cycle.","recommended":"1–200","effect":"Next reconciliation cycle","impactPositive":"Larger batches clear a backlog faster.","impactTradeoff":"Large batches increase provider and database load.","advancedKeyVisible":true}'::jsonb,
  config_set_key='RUNTIME/ISSUE/SYSTEM',
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=3,
  source_ref='contracts/current/configuration/definitions/provisional-pilot-definitions.json',
  source_fingerprint='6a837348eebd191814813dbca415c236749e6f889e784ab4003105f779a2e374',
  synchronized_at=now()
where definition_key='issue-projection.reconcile-batch-size';

update runtime_config_definitions set
  display_name='Issue reconciliation interval',
  owner='ISSUE',
  domain_owner='ISSUE',
  authority_class='RUNTIME_TUNABLE',
  scope='COMPONENT',
  scope_ref='ISSUE',
  data_type='LONG',
  unit='milliseconds',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='DYNAMIC_SCHEDULER',
  environment_applicability='["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,
  validation_rule='{"minimum":250,"maximum":3600000}'::jsonb,
  requires_approval=true,
  admin_editable=true,
  initial_seed='{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb,
  introduced_version='V40-8',
  ui_metadata='{"categoryId":"issue-integration","displayName":"Issue reconciliation interval","description":"How long the Issue projection scheduler waits after each completed reconciliation cycle.","recommended":"5000–120000","effect":"Next scheduler cycle","impactPositive":"Shorter intervals reduce reconciliation latency.","impactTradeoff":"Very short intervals increase database and provider-side pressure.","advancedKeyVisible":true}'::jsonb,
  config_set_key='RUNTIME/ISSUE/SYSTEM',
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=3,
  source_ref='contracts/current/configuration/definitions/dynamic-scheduler-definitions.json',
  source_fingerprint='7f25fedd1dbb8c6b8381415a9dfbdfd784480ef5c8d37f962078c7bade96c32e',
  synchronized_at=now()
where definition_key='issue-projection.reconcile-delay-ms';

update runtime_config_definitions set
  display_name='Issue retry delay',
  owner='ISSUE',
  domain_owner='ISSUE',
  authority_class='RUNTIME_TUNABLE',
  scope='COMPONENT',
  scope_ref='ISSUE',
  data_type='LONG',
  unit='seconds',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='RUNTIME_SNAPSHOT',
  environment_applicability='["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,
  validation_rule='{"minimum":1,"maximum":86400}'::jsonb,
  requires_approval=true,
  admin_editable=true,
  initial_seed='{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb,
  introduced_version='V40-5',
  ui_metadata='{"categoryId":"issue-integration","displayName":"Issue retry delay","description":"Delay before retrying a failed Issue projection.","recommended":"5–300","effect":"Next retry cycle","impactPositive":"Gives external Issue providers time to recover.","impactTradeoff":"Long delays postpone projection recovery.","advancedKeyVisible":true}'::jsonb,
  config_set_key='RUNTIME/ISSUE/SYSTEM',
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=3,
  source_ref='contracts/current/configuration/definitions/provisional-pilot-definitions.json',
  source_fingerprint='3b5f14ce4544e3feed36565bd4734afba8c802899b2b6ed6a2a6e4f91994d03b',
  synchronized_at=now()
where definition_key='issue-projection.retry-delay-seconds';

update runtime_config_definitions set
  display_name='Task recovery initial delay',
  owner='TASK',
  domain_owner='TASK',
  authority_class='RUNTIME_TUNABLE',
  scope='COMPONENT',
  scope_ref='TASK',
  data_type='DURATION',
  unit='duration',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='RUNTIME_SNAPSHOT',
  environment_applicability='["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,
  validation_rule='{"minimum":"PT0.001S"}'::jsonb,
  requires_approval=true,
  admin_editable=true,
  initial_seed='{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb,
  introduced_version='V40-5',
  ui_metadata='{"categoryId":"task-processing","displayName":"Task recovery initial delay","description":"Initial delay before a Task is reconsidered for dispatch recovery.","recommended":"PT5S–PT1M","effect":"Next recovery cycle","impactPositive":"Prevents immediate churn after a transient failure.","impactTradeoff":"Long delays slow recovery.","advancedKeyVisible":true}'::jsonb,
  config_set_key='RUNTIME/TASK/SYSTEM',
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=3,
  source_ref='contracts/current/configuration/definitions/provisional-pilot-definitions.json',
  source_fingerprint='b1b4d0ec48dd1b2cd7af0e77ea591e8f6f7d408baa5c0b4a7caa0c956f0f2271',
  synchronized_at=now()
where definition_key='task.dispatch-recovery.initial-delay';

update runtime_config_definitions set
  display_name='Task recovery scan interval',
  owner='TASK',
  domain_owner='TASK',
  authority_class='RUNTIME_TUNABLE',
  scope='COMPONENT',
  scope_ref='TASK',
  data_type='LONG',
  unit='milliseconds',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='DYNAMIC_SCHEDULER',
  environment_applicability='["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,
  validation_rule='{"minimum":250,"maximum":3600000}'::jsonb,
  requires_approval=true,
  admin_editable=true,
  initial_seed='{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb,
  introduced_version='V40-8',
  ui_metadata='{"categoryId":"task-processing","displayName":"Task recovery scan interval","description":"How long the dispatch-recovery scheduler waits after each completed scan.","recommended":"1000–30000","effect":"Next scheduler cycle","impactPositive":"Shorter intervals detect recoverable Tasks sooner.","impactTradeoff":"Very short intervals increase database and dispatch scanning pressure.","advancedKeyVisible":true}'::jsonb,
  config_set_key='RUNTIME/TASK/SYSTEM',
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=3,
  source_ref='contracts/current/configuration/definitions/dynamic-scheduler-definitions.json',
  source_fingerprint='b032a18230d1df83d60b8f4dcfa97b08099f33dc395f501aa668a34bed9eb65a',
  synchronized_at=now()
where definition_key='task.dispatch-recovery.interval-ms';

update runtime_config_definitions set
  display_name='Task recovery attempts',
  owner='TASK',
  domain_owner='TASK',
  authority_class='RUNTIME_TUNABLE',
  scope='COMPONENT',
  scope_ref='TASK',
  data_type='INTEGER',
  unit='attempts',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='RUNTIME_SNAPSHOT',
  environment_applicability='["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,
  validation_rule='{"minimum":0,"maximum":1000}'::jsonb,
  requires_approval=true,
  admin_editable=true,
  initial_seed='{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb,
  introduced_version='V40-5',
  ui_metadata='{"categoryId":"task-processing","displayName":"Task recovery attempts","description":"Maximum dispatch-recovery attempts for a Task.","recommended":"1–20","effect":"Next recovery cycle","impactPositive":"More attempts improve recovery from temporary runtime failures.","impactTradeoff":"More attempts can postpone escalation of persistent failures.","advancedKeyVisible":true}'::jsonb,
  config_set_key='RUNTIME/TASK/SYSTEM',
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=3,
  source_ref='contracts/current/configuration/definitions/provisional-pilot-definitions.json',
  source_fingerprint='5b41b326b54d90fbc4b3a7d94963a07d0f0a8bb4269fa511714a92ca1c586b76',
  synchronized_at=now()
where definition_key='task.dispatch-recovery.max-attempts';

update runtime_config_definitions set
  display_name='Task recovery batch size',
  owner='TASK',
  domain_owner='TASK',
  authority_class='RUNTIME_TUNABLE',
  scope='COMPONENT',
  scope_ref='TASK',
  data_type='INTEGER',
  unit='items',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='RUNTIME_SNAPSHOT',
  environment_applicability='["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,
  validation_rule='{"minimum":1,"maximum":1000}'::jsonb,
  requires_approval=true,
  admin_editable=true,
  initial_seed='{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb,
  introduced_version='V40-5',
  ui_metadata='{"categoryId":"task-processing","displayName":"Task recovery batch size","description":"Maximum Tasks examined in one dispatch-recovery cycle.","recommended":"1–200","effect":"Next recovery cycle","impactPositive":"Larger batches can clear recovery queues faster.","impactTradeoff":"Large batches increase short-term database and dispatch pressure.","advancedKeyVisible":true}'::jsonb,
  config_set_key='RUNTIME/TASK/SYSTEM',
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=3,
  source_ref='contracts/current/configuration/definitions/provisional-pilot-definitions.json',
  source_fingerprint='d374687ade511185cc004f9aa59e0e8abd1717bf8a0f86574969d48bf89eb735',
  synchronized_at=now()
where definition_key='task.dispatch-recovery.max-batch-size';

update runtime_config_definitions set
  display_name='Task recovery maximum delay',
  owner='TASK',
  domain_owner='TASK',
  authority_class='RUNTIME_TUNABLE',
  scope='COMPONENT',
  scope_ref='TASK',
  data_type='DURATION',
  unit='duration',
  risk='MEDIUM',
  mutability='HOT_NEXT_CYCLE',
  consumer_contract='RUNTIME_SNAPSHOT',
  environment_applicability='["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb,
  validation_rule='{"minimum":"PT0.001S","greaterThanOrEqualKey":"task.dispatch-recovery.initial-delay"}'::jsonb,
  requires_approval=true,
  admin_editable=true,
  initial_seed='{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb,
  introduced_version='V40-5',
  ui_metadata='{"categoryId":"task-processing","displayName":"Task recovery maximum delay","description":"Upper bound for Task dispatch-recovery backoff.","recommended":"At least the initial delay","effect":"Next recovery cycle","impactPositive":"Caps recovery pressure during persistent outages.","impactTradeoff":"Large values can delay eventual recovery.","advancedKeyVisible":true}'::jsonb,
  config_set_key='RUNTIME/TASK/SYSTEM',
  review_status='MIGRATION_READY',
  migration_authorized=true,
  schema_version=3,
  source_ref='contracts/current/configuration/definitions/provisional-pilot-definitions.json',
  source_fingerprint='999b5d6d56c9ca84c7f4165947fed72da348b282364491129005b177849db968',
  synchronized_at=now()
where definition_key='task.dispatch-recovery.max-delay';
-- END GENERATED DEFINITION MATERIALIZATION

alter table runtime_config_definitions drop constraint if exists chk_runtime_config_authorized_definition_contract;
alter table runtime_config_definitions add constraint chk_runtime_config_authorized_definition_contract check (
  not migration_authorized or (
    nullif(trim(display_name),'') is not null and
    nullif(trim(domain_owner),'') is not null and
    nullif(trim(scope_ref),'') is not null and
    nullif(trim(unit),'') is not null and
    nullif(trim(introduced_version),'') is not null and
    nullif(trim(config_set_key),'') is not null and
    jsonb_typeof(initial_seed)='object' and
    jsonb_typeof(ui_metadata)='object' and
    ui_metadata ? 'categoryId' and
    ui_metadata ? 'description' and
    ui_metadata ? 'recommended' and
    ui_metadata ? 'effect' and
    ui_metadata ? 'impactPositive' and
    ui_metadata ? 'impactTradeoff'
  )
);

comment on column runtime_config_definitions.ui_metadata is 'Materialized from source-controlled definition uiMetadata; not an authoring authority.';
comment on column runtime_config_definitions.initial_seed is 'Bootstrap-only seed policy from source definition. Migrated required keys must not silently fall back after single-authority cutover.';
comment on column runtime_config_definitions.config_set_key is 'Explicit runtime aggregate binding from source definition runtimeBinding.configSetKey.';
