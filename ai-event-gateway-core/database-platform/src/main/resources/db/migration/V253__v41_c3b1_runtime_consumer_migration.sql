-- V41-C3B1: Runtime Consumer Migration Batch 1 — Task lifecycle, Dispatch and Agent remediation.
-- Typed consumers are runtime-snapshot aware, but governance remains MIGRATION_READY until a complete
-- active revision is published and required-node convergence allows the existing cutover service to
-- finalize MIGRATED. Startup YAML/ENV is migration fallback only before cutover.

insert into runtime_config_definitions(
 definition_key,display_name,owner,domain_owner,authority_class,scope,scope_ref,data_type,unit,risk,mutability,consumer_contract,
 target_mutability,target_consumer_contract,environment_applicability,validation_rule,requires_approval,admin_editable,initial_seed,introduced_version,
 ui_metadata,config_set_key,review_status,migration_authorized,schema_version,source_ref,source_fingerprint)
values
  ('core.lifecycle.task.timeout-enabled', 'Task timeout processing enabled', 'TASK', 'TASK', 'RUNTIME_TUNABLE', 'COMPONENT', 'TASK', 'BOOLEAN', 'boolean', 'MEDIUM', 'HOT_IMMEDIATE', 'RUNTIME_SNAPSHOT', 'HOT_IMMEDIATE', 'RUNTIME_SNAPSHOT', '["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb, '{}'::jsonb, true, true, '{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb, 'V41-C3B1', '{"categoryId":"task-lifecycle","displayName":"Task timeout processing enabled","description":"Controls whether overdue Task lifecycle states are eligible for timeout processing.","recommended":"Enabled in normal operation","effect":"Next runtime decision","impactPositive":"Allows stale Tasks to be detected and recovered automatically.","impactTradeoff":"Disabling stops automatic timeout processing until re-enabled.","advancedKeyVisible":true}'::jsonb, 'RUNTIME/TASK/SYSTEM', 'MIGRATION_READY', true, 3, 'contracts/current/configuration/definitions/runtime-consumer-c3-batch1-definitions.json', '53e2c930ccaa6310ac500390af539c7fdd94588dd6c7c55a7dee1a895c8857f9'),
  ('core.lifecycle.task.auto-reassign-enabled', 'Task automatic reassignment enabled', 'TASK', 'TASK', 'RUNTIME_TUNABLE', 'COMPONENT', 'TASK', 'BOOLEAN', 'boolean', 'MEDIUM', 'HOT_IMMEDIATE', 'RUNTIME_SNAPSHOT', 'HOT_IMMEDIATE', 'RUNTIME_SNAPSHOT', '["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb, '{}'::jsonb, true, true, '{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb, 'V41-C3B1', '{"categoryId":"task-lifecycle","displayName":"Task automatic reassignment enabled","description":"Controls whether eligible timed-out Tasks may be reassigned automatically.","recommended":"Enabled when automated recovery is desired","effect":"Next runtime decision","impactPositive":"Reduces manual recovery for recoverable Task stalls.","impactTradeoff":"Disabling leaves reassignment to operators or other workflows.","advancedKeyVisible":true}'::jsonb, 'RUNTIME/TASK/SYSTEM', 'MIGRATION_READY', true, 3, 'contracts/current/configuration/definitions/runtime-consumer-c3-batch1-definitions.json', '16c81793083a1db2563b2d874ca703901f6de9d1937cb8250710c93d2311c18e'),
  ('core.lifecycle.task.scan-interval-ms', 'Task lifecycle scan interval', 'TASK', 'TASK', 'RUNTIME_TUNABLE', 'COMPONENT', 'TASK', 'LONG', 'milliseconds', 'MEDIUM', 'HOT_NEXT_CYCLE', 'DYNAMIC_SCHEDULER', 'HOT_NEXT_CYCLE', 'DYNAMIC_SCHEDULER', '["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb, '{"minimum":1000,"maximum":3600000}'::jsonb, true, true, '{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb, 'V41-C3B1', '{"categoryId":"task-lifecycle","displayName":"Task lifecycle scan interval","description":"Delay between completed Task lifecycle scans.","recommended":"5000–60000","effect":"Next scheduler cycle","impactPositive":"Shorter intervals detect stale Tasks sooner.","impactTradeoff":"Very short intervals increase database and orchestration pressure.","advancedKeyVisible":true}'::jsonb, 'RUNTIME/TASK/SYSTEM', 'MIGRATION_READY', true, 3, 'contracts/current/configuration/definitions/runtime-consumer-c3-batch1-definitions.json', '7376eddfa8524ce34ccdb1466ce8b8caaabaee37a971d536466eb9277f4a4b82'),
  ('core.lifecycle.task.created-timeout', 'Created Task timeout', 'TASK', 'TASK', 'RUNTIME_TUNABLE', 'COMPONENT', 'TASK', 'DURATION', 'duration', 'MEDIUM', 'HOT_IMMEDIATE', 'RUNTIME_SNAPSHOT', 'HOT_IMMEDIATE', 'RUNTIME_SNAPSHOT', '["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb, '{"minimum":"PT1S","maximum":"PT24H"}'::jsonb, true, true, '{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb, 'V41-C3B1', '{"categoryId":"task-lifecycle","displayName":"Created Task timeout","description":"Maximum time a Task may remain CREATED before lifecycle recovery evaluates it.","recommended":"PT1M–PT30M","effect":"Next runtime decision","impactPositive":"Prevents Tasks from remaining unprocessed indefinitely.","impactTradeoff":"Too-short values may recover legitimate slow startup flows prematurely.","advancedKeyVisible":true}'::jsonb, 'RUNTIME/TASK/SYSTEM', 'MIGRATION_READY', true, 3, 'contracts/current/configuration/definitions/runtime-consumer-c3-batch1-definitions.json', '2664db7ac5df80f330e4b76b9e2b41da223dd52f64067551a0ab40261023c3c5'),
  ('core.lifecycle.task.assigned-timeout', 'Assigned Task timeout', 'TASK', 'TASK', 'RUNTIME_TUNABLE', 'COMPONENT', 'TASK', 'DURATION', 'duration', 'MEDIUM', 'HOT_IMMEDIATE', 'RUNTIME_SNAPSHOT', 'HOT_IMMEDIATE', 'RUNTIME_SNAPSHOT', '["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb, '{"minimum":"PT1S","maximum":"PT24H"}'::jsonb, true, true, '{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb, 'V41-C3B1', '{"categoryId":"task-lifecycle","displayName":"Assigned Task timeout","description":"Maximum time a Task may remain ASSIGNED without progressing.","recommended":"PT1M–PT30M","effect":"Next runtime decision","impactPositive":"Detects assignment stalls promptly.","impactTradeoff":"Too-short values can cause unnecessary recovery churn.","advancedKeyVisible":true}'::jsonb, 'RUNTIME/TASK/SYSTEM', 'MIGRATION_READY', true, 3, 'contracts/current/configuration/definitions/runtime-consumer-c3-batch1-definitions.json', 'a3b659ee52677759454e3f9e9ec217915f0562fa2cf4a973640364f76b107e2f'),
  ('core.lifecycle.task.dispatched-timeout', 'Dispatched Task timeout', 'TASK', 'TASK', 'RUNTIME_TUNABLE', 'COMPONENT', 'TASK', 'DURATION', 'duration', 'MEDIUM', 'HOT_IMMEDIATE', 'RUNTIME_SNAPSHOT', 'HOT_IMMEDIATE', 'RUNTIME_SNAPSHOT', '["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb, '{"minimum":"PT1S","maximum":"PT24H"}'::jsonb, true, true, '{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb, 'V41-C3B1', '{"categoryId":"task-lifecycle","displayName":"Dispatched Task timeout","description":"Maximum time a Task may remain DISPATCHED without execution progress.","recommended":"PT1M–PT30M","effect":"Next runtime decision","impactPositive":"Detects lost or non-starting deliveries.","impactTradeoff":"Must allow enough time for normal Agent startup latency.","advancedKeyVisible":true}'::jsonb, 'RUNTIME/TASK/SYSTEM', 'MIGRATION_READY', true, 3, 'contracts/current/configuration/definitions/runtime-consumer-c3-batch1-definitions.json', '6857920523c9e5d803c8a401d80bf530707118562c3cb2c59063e3ad5e2fcec3'),
  ('core.lifecycle.task.running-timeout', 'Running Task timeout', 'TASK', 'TASK', 'RUNTIME_TUNABLE', 'COMPONENT', 'TASK', 'DURATION', 'duration', 'MEDIUM', 'HOT_IMMEDIATE', 'RUNTIME_SNAPSHOT', 'HOT_IMMEDIATE', 'RUNTIME_SNAPSHOT', '["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb, '{"minimum":"PT1S","maximum":"PT7D"}'::jsonb, true, true, '{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb, 'V41-C3B1', '{"categoryId":"task-lifecycle","displayName":"Running Task timeout","description":"Maximum RUNNING duration before lifecycle recovery evaluates the Task as stale.","recommended":"Match the longest legitimate Agent execution window","effect":"Next runtime decision","impactPositive":"Prevents indefinitely stuck RUNNING Tasks.","impactTradeoff":"Too-short values can interrupt legitimate long-running work.","advancedKeyVisible":true}'::jsonb, 'RUNTIME/TASK/SYSTEM', 'MIGRATION_READY', true, 3, 'contracts/current/configuration/definitions/runtime-consumer-c3-batch1-definitions.json', '4d923e34503a84aaad16551f22f3fe2f98590ec5fc5b4a0e281bcb07bded520e'),
  ('core.lifecycle.task.max-reassignments', 'Task maximum reassignments', 'TASK', 'TASK', 'RUNTIME_TUNABLE', 'COMPONENT', 'TASK', 'INTEGER', 'attempts', 'MEDIUM', 'HOT_IMMEDIATE', 'RUNTIME_SNAPSHOT', 'HOT_IMMEDIATE', 'RUNTIME_SNAPSHOT', '["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb, '{"minimum":0,"maximum":20}'::jsonb, true, true, '{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb, 'V41-C3B1', '{"categoryId":"task-lifecycle","displayName":"Task maximum reassignments","description":"Maximum automatic lifecycle reassignments allowed for one Task.","recommended":"1–5","effect":"Next runtime decision","impactPositive":"Bounds automatic recovery attempts.","impactTradeoff":"Higher values can extend recovery loops before terminal escalation.","advancedKeyVisible":true}'::jsonb, 'RUNTIME/TASK/SYSTEM', 'MIGRATION_READY', true, 3, 'contracts/current/configuration/definitions/runtime-consumer-c3-batch1-definitions.json', '1b2a324904cf91fb40080136e5b523be25f0708e862a18f840d60aff12a9e463'),
  ('core.lifecycle.task.max-batch-size', 'Task lifecycle scan batch size', 'TASK', 'TASK', 'RUNTIME_TUNABLE', 'COMPONENT', 'TASK', 'INTEGER', 'items', 'MEDIUM', 'HOT_NEXT_CYCLE', 'RUNTIME_SNAPSHOT', 'HOT_NEXT_CYCLE', 'RUNTIME_SNAPSHOT', '["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb, '{"minimum":1,"maximum":1000}'::jsonb, true, true, '{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb, 'V41-C3B1', '{"categoryId":"task-lifecycle","displayName":"Task lifecycle scan batch size","description":"Maximum Tasks evaluated during one lifecycle scan.","recommended":"25–200","effect":"Next lifecycle scan","impactPositive":"Larger batches clear stale-work backlogs faster.","impactTradeoff":"Very large batches increase database and orchestration burst load.","advancedKeyVisible":true}'::jsonb, 'RUNTIME/TASK/SYSTEM', 'MIGRATION_READY', true, 3, 'contracts/current/configuration/definitions/runtime-consumer-c3-batch1-definitions.json', '4976b174f6953426d0fc59000123813432d8fccc5bc8fc7730c456dce01655d5'),
  ('dispatch.retry.enabled', 'Dispatch retry enabled', 'DISPATCH', 'DISPATCH', 'RUNTIME_TUNABLE', 'COMPONENT', 'DISPATCH', 'BOOLEAN', 'boolean', 'MEDIUM', 'HOT_NEXT_CYCLE', 'RUNTIME_SNAPSHOT', 'HOT_NEXT_CYCLE', 'RUNTIME_SNAPSHOT', '["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb, '{}'::jsonb, true, true, '{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb, 'V41-C3B1', '{"categoryId":"dispatch-routing","displayName":"Dispatch retry enabled","description":"Controls whether retryable dispatch failures are scheduled for another attempt.","recommended":"Enabled in normal operation","effect":"Next dispatch failure","impactPositive":"Allows transient delivery failures to recover automatically.","impactTradeoff":"Disabling converts retryable failures into faster terminal handling.","advancedKeyVisible":true}'::jsonb, 'RUNTIME/DISPATCH/SYSTEM', 'MIGRATION_READY', true, 3, 'contracts/current/configuration/definitions/runtime-consumer-c3-batch1-definitions.json', '8ccaf3f8e071e2069a2b68876224460fbdbb7a896bcbeda566468a368577079d'),
  ('dispatch.retry.max-attempts', 'Maximum dispatch retry attempts', 'DISPATCH', 'DISPATCH', 'RUNTIME_TUNABLE', 'COMPONENT', 'DISPATCH', 'INTEGER', 'attempts', 'MEDIUM', 'HOT_NEXT_CYCLE', 'RUNTIME_SNAPSHOT', 'HOT_NEXT_CYCLE', 'RUNTIME_SNAPSHOT', '["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb, '{"minimum":1,"maximum":20}'::jsonb, true, true, '{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb, 'V41-C3B1', '{"categoryId":"dispatch-routing","displayName":"Maximum dispatch retry attempts","description":"Maximum dispatch attempts before work is dead-lettered.","recommended":"3–10","effect":"Next dispatch failure","impactPositive":"More attempts improve resilience to transient failures.","impactTradeoff":"High values delay terminal failure and operator visibility.","advancedKeyVisible":true}'::jsonb, 'RUNTIME/DISPATCH/SYSTEM', 'MIGRATION_READY', true, 3, 'contracts/current/configuration/definitions/runtime-consumer-c3-batch1-definitions.json', '99b30adbde3257ded74363dea78b65e7d64f127817b29885c108228835a9cc7f'),
  ('dispatch.retry.initial-backoff', 'Dispatch initial retry delay', 'DISPATCH', 'DISPATCH', 'RUNTIME_TUNABLE', 'COMPONENT', 'DISPATCH', 'DURATION', 'duration', 'MEDIUM', 'HOT_NEXT_CYCLE', 'RUNTIME_SNAPSHOT', 'HOT_NEXT_CYCLE', 'RUNTIME_SNAPSHOT', '["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb, '{"minimum":"PT0.001S","maximum":"PT1H"}'::jsonb, true, true, '{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb, 'V41-C3B1', '{"categoryId":"dispatch-routing","displayName":"Dispatch initial retry delay","description":"Delay before the first dispatch retry.","recommended":"PT1S–PT30S","effect":"Next retry scheduling decision","impactPositive":"Reduces immediate pressure on a temporarily unavailable dependency.","impactTradeoff":"Long delays increase recovery latency.","advancedKeyVisible":true}'::jsonb, 'RUNTIME/DISPATCH/SYSTEM', 'MIGRATION_READY', true, 3, 'contracts/current/configuration/definitions/runtime-consumer-c3-batch1-definitions.json', 'e82cddc7b433ca2c9528e8d5a2c9cdb791211ed8267d7ebb11a6c6cfb6be1a8b'),
  ('dispatch.retry.max-backoff', 'Dispatch maximum retry delay', 'DISPATCH', 'DISPATCH', 'RUNTIME_TUNABLE', 'COMPONENT', 'DISPATCH', 'DURATION', 'duration', 'MEDIUM', 'HOT_NEXT_CYCLE', 'RUNTIME_SNAPSHOT', 'HOT_NEXT_CYCLE', 'RUNTIME_SNAPSHOT', '["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb, '{"minimum":"PT0.001S","maximum":"PT24H","greaterThanOrEqualKey":"dispatch.retry.initial-backoff"}'::jsonb, true, true, '{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb, 'V41-C3B1', '{"categoryId":"dispatch-routing","displayName":"Dispatch maximum retry delay","description":"Upper bound for dispatch retry backoff.","recommended":"At least the initial retry delay","effect":"Next retry scheduling decision","impactPositive":"Caps repeated retry pressure during prolonged outages.","impactTradeoff":"Large values delay recovery after dependencies recover.","advancedKeyVisible":true}'::jsonb, 'RUNTIME/DISPATCH/SYSTEM', 'MIGRATION_READY', true, 3, 'contracts/current/configuration/definitions/runtime-consumer-c3-batch1-definitions.json', '25230e6ddb31617023fb9c9e4495ad0ebd8d7ebfa75b053a71ad61e5a3e8a6e4'),
  ('dispatch.retry.jitter-percent', 'Dispatch retry jitter', 'DISPATCH', 'DISPATCH', 'RUNTIME_TUNABLE', 'COMPONENT', 'DISPATCH', 'INTEGER', 'percent', 'MEDIUM', 'HOT_NEXT_CYCLE', 'RUNTIME_SNAPSHOT', 'HOT_NEXT_CYCLE', 'RUNTIME_SNAPSHOT', '["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb, '{"minimum":0,"maximum":100}'::jsonb, true, true, '{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb, 'V41-C3B1', '{"categoryId":"dispatch-routing","displayName":"Dispatch retry jitter","description":"Randomizes dispatch retry timing to reduce synchronized retry bursts.","recommended":"0–30","effect":"Next retry scheduling decision","impactPositive":"Reduces thundering-herd behavior.","impactTradeoff":"Large jitter makes exact retry timing less predictable.","advancedKeyVisible":true}'::jsonb, 'RUNTIME/DISPATCH/SYSTEM', 'MIGRATION_READY', true, 3, 'contracts/current/configuration/definitions/runtime-consumer-c3-batch1-definitions.json', '4dd15be489f221443f4ec198a3caa897a6b0ba5f327791f8912e80b51b78b6ed'),
  ('dispatch.failure-requeue.enabled', 'Runtime failure requeue enabled', 'DISPATCH', 'DISPATCH', 'RUNTIME_TUNABLE', 'COMPONENT', 'DISPATCH', 'BOOLEAN', 'boolean', 'MEDIUM', 'HOT_NEXT_CYCLE', 'RUNTIME_SNAPSHOT', 'HOT_NEXT_CYCLE', 'RUNTIME_SNAPSHOT', '["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb, '{}'::jsonb, true, true, '{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb, 'V41-C3B1', '{"categoryId":"dispatch-routing","displayName":"Runtime failure requeue enabled","description":"Controls Task requeue after an eligible runtime delivery failure.","recommended":"Enabled when automatic runtime recovery is desired","effect":"Next runtime failure","impactPositive":"Allows work to move away from a failing Agent automatically.","impactTradeoff":"Disabling requires manual or alternate recovery.","advancedKeyVisible":true}'::jsonb, 'RUNTIME/DISPATCH/SYSTEM', 'MIGRATION_READY', true, 3, 'contracts/current/configuration/definitions/runtime-consumer-c3-batch1-definitions.json', 'a5975ab71c9a06ad7342c70ec30e82302b268ccae40a0bfc14fcd9e271675afb'),
  ('dispatch.failure-requeue.max-reassignments', 'Runtime failure maximum reassignments', 'DISPATCH', 'DISPATCH', 'RUNTIME_TUNABLE', 'COMPONENT', 'DISPATCH', 'INTEGER', 'attempts', 'MEDIUM', 'HOT_NEXT_CYCLE', 'RUNTIME_SNAPSHOT', 'HOT_NEXT_CYCLE', 'RUNTIME_SNAPSHOT', '["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb, '{"minimum":0,"maximum":20}'::jsonb, true, true, '{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb, 'V41-C3B1', '{"categoryId":"dispatch-routing","displayName":"Runtime failure maximum reassignments","description":"Maximum Task reassignments caused by runtime dispatch failures.","recommended":"1–5","effect":"Next runtime failure","impactPositive":"Bounds repeated reassignment loops.","impactTradeoff":"Higher values can extend recovery churn.","advancedKeyVisible":true}'::jsonb, 'RUNTIME/DISPATCH/SYSTEM', 'MIGRATION_READY', true, 3, 'contracts/current/configuration/definitions/runtime-consumer-c3-batch1-definitions.json', '30c6ae29a5f4db9216b15e83d0fcd693c6d9e8e53dbb9af3113711ad5e0fde3c'),
  ('dispatch.failure-requeue.runtime-initial-backoff', 'Runtime failure initial backoff', 'DISPATCH', 'DISPATCH', 'RUNTIME_TUNABLE', 'COMPONENT', 'DISPATCH', 'DURATION', 'duration', 'MEDIUM', 'HOT_NEXT_CYCLE', 'RUNTIME_SNAPSHOT', 'HOT_NEXT_CYCLE', 'RUNTIME_SNAPSHOT', '["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb, '{"minimum":"PT0.001S","maximum":"PT1H"}'::jsonb, true, true, '{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb, 'V41-C3B1', '{"categoryId":"dispatch-routing","displayName":"Runtime failure initial backoff","description":"Initial delay before a Task becomes eligible after runtime failure requeue.","recommended":"PT5S–PT1M","effect":"Next runtime requeue","impactPositive":"Provides backpressure after a failing runtime attempt.","impactTradeoff":"Long delays increase recovery latency.","advancedKeyVisible":true}'::jsonb, 'RUNTIME/DISPATCH/SYSTEM', 'MIGRATION_READY', true, 3, 'contracts/current/configuration/definitions/runtime-consumer-c3-batch1-definitions.json', '7ce95f7f79569541ca57e5e57c2b6e36420efa246af2d3c071127a00054dfcdc'),
  ('dispatch.failure-requeue.runtime-max-backoff', 'Runtime failure maximum backoff', 'DISPATCH', 'DISPATCH', 'RUNTIME_TUNABLE', 'COMPONENT', 'DISPATCH', 'DURATION', 'duration', 'MEDIUM', 'HOT_NEXT_CYCLE', 'RUNTIME_SNAPSHOT', 'HOT_NEXT_CYCLE', 'RUNTIME_SNAPSHOT', '["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb, '{"minimum":"PT0.001S","maximum":"PT24H","greaterThanOrEqualKey":"dispatch.failure-requeue.runtime-initial-backoff"}'::jsonb, true, true, '{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb, 'V41-C3B1', '{"categoryId":"dispatch-routing","displayName":"Runtime failure maximum backoff","description":"Maximum requeue backoff after repeated runtime failures.","recommended":"At least the initial backoff","effect":"Next runtime requeue","impactPositive":"Caps pressure during repeated Agent/runtime failures.","impactTradeoff":"Large values delay eventual recovery.","advancedKeyVisible":true}'::jsonb, 'RUNTIME/DISPATCH/SYSTEM', 'MIGRATION_READY', true, 3, 'contracts/current/configuration/definitions/runtime-consumer-c3-batch1-definitions.json', '634c0d288d3b397d7784744bc955c49017918c2c59b4a844fc8e69b03b45a11e'),
  ('dispatch.failure-requeue.runtime-jitter-percent', 'Runtime failure requeue jitter', 'DISPATCH', 'DISPATCH', 'RUNTIME_TUNABLE', 'COMPONENT', 'DISPATCH', 'INTEGER', 'percent', 'MEDIUM', 'HOT_NEXT_CYCLE', 'RUNTIME_SNAPSHOT', 'HOT_NEXT_CYCLE', 'RUNTIME_SNAPSHOT', '["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb, '{"minimum":0,"maximum":100}'::jsonb, true, true, '{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb, 'V41-C3B1', '{"categoryId":"dispatch-routing","displayName":"Runtime failure requeue jitter","description":"Jitter applied to runtime failure requeue backoff.","recommended":"0–30","effect":"Next runtime requeue","impactPositive":"Spreads recovery work over time.","impactTradeoff":"Higher jitter reduces scheduling predictability.","advancedKeyVisible":true}'::jsonb, 'RUNTIME/DISPATCH/SYSTEM', 'MIGRATION_READY', true, 3, 'contracts/current/configuration/definitions/runtime-consumer-c3-batch1-definitions.json', '21ac1cad6d92e8dfa9393e4c538487d9e3cb21b4af5f3934198c95e3f8e79001'),
  ('dispatch.failure-requeue.poison-agent-failure-threshold', 'Poison Agent failure threshold', 'DISPATCH', 'DISPATCH', 'RUNTIME_TUNABLE', 'COMPONENT', 'DISPATCH', 'INTEGER', 'failures', 'MEDIUM', 'HOT_NEXT_CYCLE', 'RUNTIME_SNAPSHOT', 'HOT_NEXT_CYCLE', 'RUNTIME_SNAPSHOT', '["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb, '{"minimum":1,"maximum":100}'::jsonb, true, true, '{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb, 'V41-C3B1', '{"categoryId":"dispatch-routing","displayName":"Poison Agent failure threshold","description":"Consecutive Agent runtime failures before the Agent is treated as poison for reassignment decisions.","recommended":"3–10","effect":"Next runtime failure","impactPositive":"Avoids repeatedly routing work back to a persistently failing Agent.","impactTradeoff":"Too-low values can overreact to short transient failures.","advancedKeyVisible":true}'::jsonb, 'RUNTIME/DISPATCH/SYSTEM', 'MIGRATION_READY', true, 3, 'contracts/current/configuration/definitions/runtime-consumer-c3-batch1-definitions.json', '4b03e5677327fee54ca1c5e01a881f1bbf1048d797edc5e601421452a16ea9aa'),
  ('agent-remediation.workflow.stale-lease-reaper.enabled', 'Stale remediation lease recovery enabled', 'AGENT_REMEDIATION', 'AGENT_REMEDIATION', 'RUNTIME_TUNABLE', 'COMPONENT', 'AGENT_REMEDIATION', 'BOOLEAN', 'boolean', 'MEDIUM', 'HOT_NEXT_CYCLE', 'RUNTIME_SNAPSHOT', 'HOT_NEXT_CYCLE', 'RUNTIME_SNAPSHOT', '["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb, '{}'::jsonb, true, true, '{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb, 'V41-C3B1', '{"categoryId":"agent-remediation","displayName":"Stale remediation lease recovery enabled","description":"Controls scheduled recovery of expired Agent remediation workflow execution leases.","recommended":"Enabled in normal operation","effect":"Next scheduler cycle","impactPositive":"Recovers abandoned workflow execution leases automatically.","impactTradeoff":"Disabling leaves expired leases for manual recovery.","advancedKeyVisible":true}'::jsonb, 'RUNTIME/AGENT_REMEDIATION/SYSTEM', 'MIGRATION_READY', true, 3, 'contracts/current/configuration/definitions/runtime-consumer-c3-batch1-definitions.json', 'c00e4af5177fb4d3d89616f350f6f508ae8356d4d4c47be087be04b7ea6c117f'),
  ('agent-remediation.workflow.stale-lease-reaper.fixed-delay-ms', 'Stale lease recovery interval', 'AGENT_REMEDIATION', 'AGENT_REMEDIATION', 'RUNTIME_TUNABLE', 'COMPONENT', 'AGENT_REMEDIATION', 'LONG', 'milliseconds', 'MEDIUM', 'HOT_NEXT_CYCLE', 'DYNAMIC_SCHEDULER', 'HOT_NEXT_CYCLE', 'DYNAMIC_SCHEDULER', '["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb, '{"minimum":1000,"maximum":86400000}'::jsonb, true, true, '{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb, 'V41-C3B1', '{"categoryId":"agent-remediation","displayName":"Stale lease recovery interval","description":"Delay between completed stale remediation lease recovery scans.","recommended":"30000–300000","effect":"Next scheduler cycle","impactPositive":"Shorter intervals recover abandoned workflow leases sooner.","impactTradeoff":"Very short intervals add unnecessary database scanning load.","advancedKeyVisible":true}'::jsonb, 'RUNTIME/AGENT_REMEDIATION/SYSTEM', 'MIGRATION_READY', true, 3, 'contracts/current/configuration/definitions/runtime-consumer-c3-batch1-definitions.json', '9b2fa241381320dea58f11217a6d4bea161d05bbac3b0829dfdc5530594326cb'),
  ('agent-remediation.workflow.stale-lease-reaper.initial-delay-ms', 'Stale lease recovery initial delay', 'AGENT_REMEDIATION', 'AGENT_REMEDIATION', 'RUNTIME_TUNABLE', 'COMPONENT', 'AGENT_REMEDIATION', 'LONG', 'milliseconds', 'MEDIUM', 'HOT_NEXT_CYCLE', 'DYNAMIC_SCHEDULER', 'HOT_NEXT_CYCLE', 'DYNAMIC_SCHEDULER', '["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb, '{"minimum":1000,"maximum":86400000}'::jsonb, true, true, '{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb, 'V41-C3B1', '{"categoryId":"agent-remediation","displayName":"Stale lease recovery initial delay","description":"Initial delay before the first stale remediation lease recovery scan after process startup.","recommended":"10000–120000","effect":"Next scheduler lifecycle start","impactPositive":"Allows dependencies to stabilize before recovery begins.","impactTradeoff":"Long values postpone recovery after restart.","advancedKeyVisible":true}'::jsonb, 'RUNTIME/AGENT_REMEDIATION/SYSTEM', 'MIGRATION_READY', true, 3, 'contracts/current/configuration/definitions/runtime-consumer-c3-batch1-definitions.json', '017b86664ab9d35ad9ae63bd232da9fa36bf54eb2d9c2853a47c2850a2e9037e'),
  ('agent-remediation.workflow.stale-lease-reaper.limit', 'Stale lease recovery batch limit', 'AGENT_REMEDIATION', 'AGENT_REMEDIATION', 'RUNTIME_TUNABLE', 'COMPONENT', 'AGENT_REMEDIATION', 'INTEGER', 'items', 'MEDIUM', 'HOT_NEXT_CYCLE', 'RUNTIME_SNAPSHOT', 'HOT_NEXT_CYCLE', 'RUNTIME_SNAPSHOT', '["PRD","UAT","SIT","QA","DEV","LOCAL"]'::jsonb, '{"minimum":1,"maximum":1000}'::jsonb, true, true, '{"strategy":"CURRENT_EFFECTIVE_STARTUP_VALUE","purpose":"migration-bootstrap-only"}'::jsonb, 'V41-C3B1', '{"categoryId":"agent-remediation","displayName":"Stale lease recovery batch limit","description":"Maximum expired remediation workflow leases processed per scan.","recommended":"25–200","effect":"Next recovery scan","impactPositive":"Larger values clear accumulated expired leases faster.","impactTradeoff":"Very large batches can create recovery bursts.","advancedKeyVisible":true}'::jsonb, 'RUNTIME/AGENT_REMEDIATION/SYSTEM', 'MIGRATION_READY', true, 3, 'contracts/current/configuration/definitions/runtime-consumer-c3-batch1-definitions.json', '8f2502f04d85713e8a940dd056143c4d87131b4c7aed4ee17494ede4210f98db')
on conflict(definition_key) do update set
 display_name=excluded.display_name,owner=excluded.owner,domain_owner=excluded.domain_owner,authority_class=excluded.authority_class,
 scope=excluded.scope,scope_ref=excluded.scope_ref,data_type=excluded.data_type,unit=excluded.unit,risk=excluded.risk,mutability=excluded.mutability,
 consumer_contract=excluded.consumer_contract,target_mutability=excluded.target_mutability,target_consumer_contract=excluded.target_consumer_contract,
 environment_applicability=excluded.environment_applicability,validation_rule=excluded.validation_rule,requires_approval=excluded.requires_approval,
 admin_editable=excluded.admin_editable,initial_seed=excluded.initial_seed,introduced_version=excluded.introduced_version,ui_metadata=excluded.ui_metadata,
 config_set_key=excluded.config_set_key,
 review_status=case when runtime_config_definitions.review_status='RETIRED' then runtime_config_definitions.review_status else excluded.review_status end,
 migration_authorized=excluded.migration_authorized,schema_version=excluded.schema_version,source_ref=excluded.source_ref,
 source_fingerprint=excluded.source_fingerprint,synchronized_at=now();

-- Advance only rows that still need each legal lifecycle transition. Existing MIGRATION_READY/MIGRATED
-- rows are not downgraded and C3B1 never fabricates single-authority cutover evidence.
with advanced as (
 update runtime_config_inventory_governance g set
  status='CLASSIFIED',domain_owner=d.domain_owner,authority_class=d.authority_class,scope=d.scope,risk=d.risk,mutability=d.mutability,
  consumer_contract=d.consumer_contract,admin_editable=d.admin_editable,requires_approval=d.requires_approval,
  classified_by='v41-c3b1-domain-classifier',classified_at=now(),reason='V41-C3B1 reviewed runtime consumer migration scope',version=version+1
 from runtime_config_definitions d
 where g.configuration_key in (
    'core.lifecycle.task.timeout-enabled',
    'core.lifecycle.task.auto-reassign-enabled',
    'core.lifecycle.task.scan-interval-ms',
    'core.lifecycle.task.created-timeout',
    'core.lifecycle.task.assigned-timeout',
    'core.lifecycle.task.dispatched-timeout',
    'core.lifecycle.task.running-timeout',
    'core.lifecycle.task.max-reassignments',
    'core.lifecycle.task.max-batch-size',
    'dispatch.retry.enabled',
    'dispatch.retry.max-attempts',
    'dispatch.retry.initial-backoff',
    'dispatch.retry.max-backoff',
    'dispatch.retry.jitter-percent',
    'dispatch.failure-requeue.enabled',
    'dispatch.failure-requeue.max-reassignments',
    'dispatch.failure-requeue.runtime-initial-backoff',
    'dispatch.failure-requeue.runtime-max-backoff',
    'dispatch.failure-requeue.runtime-jitter-percent',
    'dispatch.failure-requeue.poison-agent-failure-threshold',
    'agent-remediation.workflow.stale-lease-reaper.enabled',
    'agent-remediation.workflow.stale-lease-reaper.fixed-delay-ms',
    'agent-remediation.workflow.stale-lease-reaper.initial-delay-ms',
    'agent-remediation.workflow.stale-lease-reaper.limit'
 ) and g.status='DISCOVERED' and d.definition_key=g.configuration_key
 returning g.configuration_key,g.source_observation_hash
)
insert into runtime_config_inventory_governance_events(configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'CLASSIFIED','DISCOVERED','CLASSIFIED','v41-c3b1-domain-classifier','V41-C3B1 reviewed runtime consumer migration scope',source_observation_hash,
 '{"stage":"V41_C3B1_RUNTIME_CONSUMER_MIGRATION"}'::jsonb from advanced;

with advanced as (
 update runtime_config_inventory_governance g set
  status='OWNER_REVIEWED',owner_reviewed_by='v41-c3b1-domain-owner',owner_reviewed_at=now(),
  reason='V41-C3B1 domain owner approved typed runtime snapshot semantics',version=version+1
 where g.configuration_key in (
    'core.lifecycle.task.timeout-enabled',
    'core.lifecycle.task.auto-reassign-enabled',
    'core.lifecycle.task.scan-interval-ms',
    'core.lifecycle.task.created-timeout',
    'core.lifecycle.task.assigned-timeout',
    'core.lifecycle.task.dispatched-timeout',
    'core.lifecycle.task.running-timeout',
    'core.lifecycle.task.max-reassignments',
    'core.lifecycle.task.max-batch-size',
    'dispatch.retry.enabled',
    'dispatch.retry.max-attempts',
    'dispatch.retry.initial-backoff',
    'dispatch.retry.max-backoff',
    'dispatch.retry.jitter-percent',
    'dispatch.failure-requeue.enabled',
    'dispatch.failure-requeue.max-reassignments',
    'dispatch.failure-requeue.runtime-initial-backoff',
    'dispatch.failure-requeue.runtime-max-backoff',
    'dispatch.failure-requeue.runtime-jitter-percent',
    'dispatch.failure-requeue.poison-agent-failure-threshold',
    'agent-remediation.workflow.stale-lease-reaper.enabled',
    'agent-remediation.workflow.stale-lease-reaper.fixed-delay-ms',
    'agent-remediation.workflow.stale-lease-reaper.initial-delay-ms',
    'agent-remediation.workflow.stale-lease-reaper.limit'
 ) and g.status='CLASSIFIED'
 returning g.configuration_key,g.source_observation_hash
)
insert into runtime_config_inventory_governance_events(configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'OWNER_REVIEWED','CLASSIFIED','OWNER_REVIEWED','v41-c3b1-domain-owner','V41-C3B1 domain owner approved typed runtime snapshot semantics',source_observation_hash,
 '{"stage":"V41_C3B1_RUNTIME_CONSUMER_MIGRATION"}'::jsonb from advanced;

with advanced as (
 update runtime_config_inventory_governance g set
  status='ARCHITECTURE_APPROVED',architecture_approved_by='v41-c3b1-configuration-architecture',architecture_approved_at=now(),
  reason='V41-C3B1 architecture approved DB authority plus Redis distribution and local snapshot reads',version=version+1
 where g.configuration_key in (
    'core.lifecycle.task.timeout-enabled',
    'core.lifecycle.task.auto-reassign-enabled',
    'core.lifecycle.task.scan-interval-ms',
    'core.lifecycle.task.created-timeout',
    'core.lifecycle.task.assigned-timeout',
    'core.lifecycle.task.dispatched-timeout',
    'core.lifecycle.task.running-timeout',
    'core.lifecycle.task.max-reassignments',
    'core.lifecycle.task.max-batch-size',
    'dispatch.retry.enabled',
    'dispatch.retry.max-attempts',
    'dispatch.retry.initial-backoff',
    'dispatch.retry.max-backoff',
    'dispatch.retry.jitter-percent',
    'dispatch.failure-requeue.enabled',
    'dispatch.failure-requeue.max-reassignments',
    'dispatch.failure-requeue.runtime-initial-backoff',
    'dispatch.failure-requeue.runtime-max-backoff',
    'dispatch.failure-requeue.runtime-jitter-percent',
    'dispatch.failure-requeue.poison-agent-failure-threshold',
    'agent-remediation.workflow.stale-lease-reaper.enabled',
    'agent-remediation.workflow.stale-lease-reaper.fixed-delay-ms',
    'agent-remediation.workflow.stale-lease-reaper.initial-delay-ms',
    'agent-remediation.workflow.stale-lease-reaper.limit'
 ) and g.status='OWNER_REVIEWED'
 returning g.configuration_key,g.source_observation_hash
)
insert into runtime_config_inventory_governance_events(configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'ARCHITECTURE_APPROVED','OWNER_REVIEWED','ARCHITECTURE_APPROVED','v41-c3b1-configuration-architecture','V41-C3B1 architecture approved DB authority plus Redis distribution and local snapshot reads',source_observation_hash,
 '{"stage":"V41_C3B1_RUNTIME_CONSUMER_MIGRATION"}'::jsonb from advanced;

with advanced as (
 update runtime_config_inventory_governance g set
  status='MIGRATION_READY',migration_authorized_by='v41-c3b1-consumer-migration',migration_authorized_at=now(),
  reason='V41-C3B1 typed consumers implemented; cutover still requires complete active revision and required-node convergence',version=version+1
 where g.configuration_key in (
    'core.lifecycle.task.timeout-enabled',
    'core.lifecycle.task.auto-reassign-enabled',
    'core.lifecycle.task.scan-interval-ms',
    'core.lifecycle.task.created-timeout',
    'core.lifecycle.task.assigned-timeout',
    'core.lifecycle.task.dispatched-timeout',
    'core.lifecycle.task.running-timeout',
    'core.lifecycle.task.max-reassignments',
    'core.lifecycle.task.max-batch-size',
    'dispatch.retry.enabled',
    'dispatch.retry.max-attempts',
    'dispatch.retry.initial-backoff',
    'dispatch.retry.max-backoff',
    'dispatch.retry.jitter-percent',
    'dispatch.failure-requeue.enabled',
    'dispatch.failure-requeue.max-reassignments',
    'dispatch.failure-requeue.runtime-initial-backoff',
    'dispatch.failure-requeue.runtime-max-backoff',
    'dispatch.failure-requeue.runtime-jitter-percent',
    'dispatch.failure-requeue.poison-agent-failure-threshold',
    'agent-remediation.workflow.stale-lease-reaper.enabled',
    'agent-remediation.workflow.stale-lease-reaper.fixed-delay-ms',
    'agent-remediation.workflow.stale-lease-reaper.initial-delay-ms',
    'agent-remediation.workflow.stale-lease-reaper.limit'
 ) and g.status='ARCHITECTURE_APPROVED'
 returning g.configuration_key,g.source_observation_hash
)
insert into runtime_config_inventory_governance_events(configuration_key,event_type,from_status,to_status,actor,reason,source_observation_hash,detail_json)
select configuration_key,'MIGRATION_READY','ARCHITECTURE_APPROVED','MIGRATION_READY','v41-c3b1-consumer-migration','V41-C3B1 typed consumers implemented; cutover remains convergence-gated',source_observation_hash,
 '{"stage":"V41_C3B1_RUNTIME_CONSUMER_MIGRATION","cutover":"NOT_FINALIZED"}'::jsonb from advanced;

-- Generic complete-snapshot gate: once a set contains migration-authorized definitions, every
-- revision promoted beyond DRAFT must carry the complete authorized set. This prevents partial
-- revisions from making an expanded C3 Config Set look healthy.
create or replace function guard_runtime_config_authorized_set_completeness() returns trigger language plpgsql as $$
declare
  v_set_key varchar(255);
  v_expected integer;
  v_actual integer;
begin
  if new.state in ('VALIDATED','PENDING_APPROVAL','APPROVED','PUBLISHED') and old.state is distinct from new.state then
    select s.set_key into v_set_key from runtime_config_sets s where s.config_set_id=new.config_set_id;
    select count(*) into v_expected from runtime_config_definitions d
     where d.config_set_key=v_set_key and d.migration_authorized=true and d.review_status='MIGRATION_READY';
    if v_expected>0 then
      select count(distinct i.definition_key) into v_actual
        from runtime_config_revision_items i
        join runtime_config_definitions d on d.definition_key=i.definition_key
       where i.revision_id=new.revision_id and d.config_set_key=v_set_key
         and d.migration_authorized=true and d.review_status='MIGRATION_READY';
      if v_actual<>v_expected then
        raise exception 'RUNTIME_CONFIG_SNAPSHOT_INCOMPLETE: setKey=% revision=% expected=% actual=%',v_set_key,new.revision_id,v_expected,v_actual;
      end if;
    end if;
  end if;
  return new;
end $$;

drop trigger if exists trg_runtime_config_authorized_set_completeness on runtime_config_revisions;
create trigger trg_runtime_config_authorized_set_completeness
  before update of state on runtime_config_revisions
  for each row execute function guard_runtime_config_authorized_set_completeness();

comment on function guard_runtime_config_authorized_set_completeness() is
 'V41-C3B1 generic complete-snapshot gate for every migration-authorized definition in the revision set.';
