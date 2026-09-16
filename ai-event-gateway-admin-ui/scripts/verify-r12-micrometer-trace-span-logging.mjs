#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';

const root = path.resolve(process.cwd(), '..');
const repoRoot = fs.existsSync(path.join(root, 'ai-event-gateway-core')) ? root : process.cwd();

function read(rel) {
  const file = path.join(repoRoot, rel);
  if (!fs.existsSync(file)) {
    throw new Error(`Missing required file: ${rel}`);
  }
  return fs.readFileSync(file, 'utf8');
}

function requireIncludes(rel, needles) {
  const text = read(rel);
  for (const needle of needles) {
    if (!text.includes(needle)) {
      throw new Error(`${rel} is missing required marker: ${needle}`);
    }
  }
}

function requireExcludes(rel, needles) {
  const text = read(rel);
  for (const needle of needles) {
    if (text.includes(needle)) {
      throw new Error(`${rel} still contains forbidden marker: ${needle}`);
    }
  }
}



const r2FlowOwnedMigration = read('ai-event-gateway-core/database-platform/src/main/resources/db/migration/V95__r2_flow_owned_dispatch_rule_model.sql');
for (const needle of [
  'alter table if exists dispatch_policies add column if not exists source_system varchar(128);',
  'idx_dispatch_policies_flow_event_lookup',
  'set source_system = coalesce(source_system, origin_source_system)'
]) {
  if (!r2FlowOwnedMigration.includes(needle)) {
    throw new Error(`V95__r2_flow_owned_dispatch_rule_model.sql is missing R12.8 Flyway compatibility marker: ${needle}`);
  }
}
if (r2FlowOwnedMigration.indexOf('alter table if exists dispatch_policies add column if not exists source_system varchar(128);') > r2FlowOwnedMigration.indexOf('create index if not exists idx_dispatch_policies_flow_event_lookup')) {
  throw new Error('V95 must add dispatch_policies.source_system before creating idx_dispatch_policies_flow_event_lookup.');
}

for (const rel of [
  'ai-event-gateway-core/control-plane-app/pom.xml',
  'ai-event-gateway-netty/gateway-app/pom.xml',
  'ai-event-gateway-core/adapter-worker-app/pom.xml'
]) {
  requireIncludes(rel, ['spring-boot-starter-opentelemetry']);
  requireExcludes(rel, [
    'micrometer-tracing-bridge-brave',
    'zipkin-reporter-brave',
    'micrometer-tracing-bridge-otel'
  ]);
}
requireIncludes('ai-event-gateway-core/task-orchestration/pom.xml', [
  'micrometer-tracing'
]);

for (const rel of [
  'ai-event-gateway-core/control-plane-app/src/main/resources/application.yml',
  'ai-event-gateway-netty/gateway-app/src/main/resources/application.yml',
  'ai-event-gateway-core/adapter-worker-app/src/main/resources/application.yml'
]) {
  requireIncludes(rel, [
    'management:',
    'tracing:',
    'sampling:',
    'probability: ${MANAGEMENT_TRACING_SAMPLING_PROBABILITY:1.0}',
    'export:',
    'enabled: ${MANAGEMENT_TRACING_EXPORT_OTLP_ENABLED:false}',
    'propagation:',
    'consume: ${MANAGEMENT_TRACING_PROPAGATION_CONSUME:w3c,b3}',
    'produce: ${MANAGEMENT_TRACING_PROPAGATION_PRODUCE:w3c}',
    'opentelemetry:',
    'endpoint: ${OTEL_EXPORTER_OTLP_TRACES_ENDPOINT:http://localhost:4318/v1/traces}',
    'otlp:',
    'url: ${OTEL_EXPORTER_OTLP_METRICS_ENDPOINT:http://localhost:4318/v1/metrics}'
  ]);
  requireExcludes(rel, [
    'MANAGEMENT_TRACING_PROPAGATION_TYPE',
    'MANAGEMENT_ZIPKIN_TRACING_EXPORT_ENABLED',
    'MANAGEMENT_ZIPKIN_TRACING_ENDPOINT',
    'zipkin:'
  ]);
}

for (const rel of [
  'ai-event-gateway-core/control-plane-app/src/main/resources/logback-spring.xml',
  'ai-event-gateway-netty/gateway-app/src/main/resources/logback-spring.xml'
]) {
  requireIncludes(rel, [
    'traceId=%X{traceId:-}',
    'spanId=%X{spanId:-}',
    'LOG_LEVEL_TRACING',
    'io.micrometer.tracing'
  ]);
}

requireIncludes('ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/EventIntakeController.java', [
  'io.micrometer.tracing.Span',
  'io.micrometer.tracing.Tracer',
  'dispatch.events.intake',
  'dispatch.task_created',
  'dispatch.assignment_created'
]);
requireIncludes('ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/dispatch/flow/FlowRuleRoutingService.java', [
  'io.micrometer.tracing.Span',
  'io.micrometer.tracing.Tracer',
  'dispatch.flow_rule.resolve',
  'dispatch.flow_rule.matched',
  'dispatch.routing_path'
]);

for (const rel of [
  'deploy/docker-compose.local.yml',
  'deploy/docker-compose.ci.yml',
  'deploy/env/.env.local.example'
]) {
  requireIncludes(rel, [
    'MANAGEMENT_TRACING_ENABLED',
    'MANAGEMENT_TRACING_SAMPLING_PROBABILITY',
    'MANAGEMENT_TRACING_EXPORT_OTLP_ENABLED',
    'MANAGEMENT_TRACING_PROPAGATION_CONSUME',
    'MANAGEMENT_TRACING_PROPAGATION_PRODUCE',
    'OTEL_EXPORTER_OTLP_TRACES_ENDPOINT',
    'MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED',
    'OTEL_EXPORTER_OTLP_METRICS_ENDPOINT',
    'OTEL_SERVICE_NAMESPACE',
    'DEPLOYMENT_ENVIRONMENT'
  ]);
  requireExcludes(rel, [
    'MANAGEMENT_TRACING_PROPAGATION_TYPE',
    'MANAGEMENT_ZIPKIN_TRACING_EXPORT_ENABLED',
    'MANAGEMENT_ZIPKIN_TRACING_ENDPOINT'
  ]);
}


requireIncludes('ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/task/TaskDecisionService.java', [
  'import com.opensocket.aievent.core.routing.RoutingProperties;',
  'private final RoutingProperties routingProperties;',
  'ObjectProvider<RoutingProperties> routingProperties',
  'routingProperties.isFlowRuleLegacyFallbackEnabled()'
]);

const taskDecisionService = read('ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/task/TaskDecisionService.java');
if (taskDecisionService.includes('properties.isFlowRuleLegacyFallbackEnabled()')) {
  throw new Error('TaskDecisionService must not call isFlowRuleLegacyFallbackEnabled() on TaskOrchestrationProperties; use RoutingProperties instead.');
}



requireIncludes('ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/task/timeline/TaskCaseTimelineView.java', [
  'private String fixAction;',
  'public String getFixAction() { return fixAction; }',
  'public void setFixAction(String fixAction) { this.fixAction = fixAction; }'
]);


requireIncludes('ai-event-gateway-admin-ui/components/agents/AgentDetailProductView.tsx', [
  '<DialogNotice tone="info">Loading dispatch rules...</DialogNotice>'
]);

const agentDetailProductView = read('ai-event-gateway-admin-ui/components/agents/AgentDetailProductView.tsx');
if (agentDetailProductView.includes('<DialogNotice tone="neutral"')) {
  throw new Error('AgentDetailProductView must not pass tone="neutral" to DialogNotice; supported tones are success, error, and info.');
}


requireIncludes('ai-event-gateway-admin-ui/components/dispatch-readiness/DispatchReadinessWizard.tsx', [
  'CoreEventIntakeDecisionResponse',
  'type CreatedTaskResponse = CoreEventIntakeDecisionResponse &',
  'function pickCreatedTaskId(response: CreatedTaskResponse): string | null',
  "if (task && typeof task.taskId === 'string') return task.taskId;"
]);

const dispatchReadinessWizard = read('ai-event-gateway-admin-ui/components/dispatch-readiness/DispatchReadinessWizard.tsx');
if (dispatchReadinessWizard.includes('function pickCreatedTaskId(response: Record<string, unknown>)')) {
  throw new Error('DispatchReadinessWizard must not require Record<string, unknown> for CoreEventIntakeDecisionResponse; use CreatedTaskResponse instead.');
}



requireIncludes('ai-event-gateway-admin-ui/lib/dispatch-recipes/recipeWorkflow.ts', [
  'CoreEventIntakeDecisionResponse',
  'CoreEventIntakeEnvelope',
  'export function recipeTestEventPayload(request: CoreDispatchReadinessEvaluationRequest, scenario: DispatchRecipeScenario): CoreEventIntakeEnvelope',
  'export function normalizeRecipeTestEventPayload(',
  'rawPayload: CoreEventIntakeEnvelope | Record<string, unknown> | null | undefined',
  "eventStage: 'EXTERNAL'",
  'requestedSkill: scenario.capability',
  'export function recipeCurlCommand(payload: CoreEventIntakeEnvelope): string',
  'type RecipeTaskIdNode = {',
  'type RecipeTaskIdResponse = Partial<CoreEventIntakeDecisionResponse> & RecipeTaskIdNode',
  'function pickRecipeTaskIdFromNode(node?: RecipeTaskIdNode | null): string | null',
  'export function pickRecipeTaskId(response: RecipeTaskIdResponse): string | null',
  'response?: CoreEventIntakeDecisionResponse | Record<string, unknown> | null'
]);

requireIncludes('ai-event-gateway-admin-ui/components/dispatch-recipes/DispatchRecipeWizard.tsx', [
  'CoreEventIntakeDecisionResponse',
  'CoreEventIntakeEnvelope',
  'useState<CoreEventIntakeEnvelope | null>(null)',
  'useState<CoreEventIntakeDecisionResponse | null>(null)',
  'normalizeRecipeTestEventPayload(recipeEvaluation.testEventPayload, request, selectedScenario)',
  'coreAdminApi.createDispatchReadinessTestEvent(payload)'
]);

const dispatchRecipeWizard = read('ai-event-gateway-admin-ui/components/dispatch-recipes/DispatchRecipeWizard.tsx');
if (dispatchRecipeWizard.includes('payload as CoreEventIntakeEnvelope')) {
  throw new Error('DispatchRecipeWizard must not cast Record<string, unknown> payload to CoreEventIntakeEnvelope; build a typed envelope instead.');
}
if (dispatchRecipeWizard.includes('useState<Record<string, unknown> | null>(null)')) {
  throw new Error('DispatchRecipeWizard must not keep recipe payload/sendResponse as Record<string, unknown>; use CoreEventIntakeEnvelope/CoreEventIntakeDecisionResponse.');
}

if (dispatchRecipeWizard.includes('setPayload(recipeEvaluation.testEventPayload)')) {
  throw new Error('DispatchRecipeWizard must normalize recipeEvaluation.testEventPayload before writing it into CoreEventIntakeEnvelope state.');
}


requireIncludes('ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx', [
  'type TaskCaseTimelineStep = {',
  'details: Record<string, string | number | string[] | null | undefined>;',
  'const steps: TaskCaseTimelineStep[] = ['
]);
requireIncludes('ai-event-gateway-admin-ui/lib/api/coreAdminApi.ts', [
  'CoreDispatchEventStage',
  'function pickDispatchEventStage(',
  'return normalized as CoreDispatchEventStage;',
  'eventStage: pickDispatchEventStage(taskRecord, ["eventStage", "event_stage"], task.eventStage)'
]);

const recipeWorkflow = read('ai-event-gateway-admin-ui/lib/dispatch-recipes/recipeWorkflow.ts');
if (recipeWorkflow.includes('recipeTestEventPayload(request: CoreDispatchReadinessEvaluationRequest, scenario: DispatchRecipeScenario): Record<string, unknown>')) {
  throw new Error('recipeTestEventPayload must return CoreEventIntakeEnvelope after R12.5.');
}
if (recipeWorkflow.includes('export function pickRecipeTaskId(response: Record<string, unknown>)')) {
  throw new Error('pickRecipeTaskId must accept the typed CoreEventIntakeDecisionResponse-compatible response after R12.5.');
}



requireIncludes('ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/FlowRuleRuntimeQuery.java', [
  'class FlowRuleRuntimeQuery',
  'private String tenantId;',
  'private String eventType;',
  'private String requestedSkill;'
]);
requireIncludes('ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/FlowRuleRuntimeMatch.java', [
  'class FlowRuleRuntimeMatch',
  'private String flowId;',
  'private String ruleId;',
  'private String requestedSkill;',
  'private List<String> requiredSkills'
]);
requireIncludes('ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/FlowRuleRoutingRepository.java', [
  'Optional<FlowRuleRuntimeMatch> findBestMatch(FlowRuleRuntimeQuery query);'
]);
requireIncludes('ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/dispatch/flow/JdbcFlowRuleRoutingRepository.java', [
  'implements FlowRuleRoutingRepository',
  'dispatch_policies p',
  'dispatch_flows f',
  'flow_required_skills frs',
  'findBestMatch(FlowRuleRuntimeQuery query)'
]);
requireIncludes('ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/dispatch/flow/FlowRuleRoutingService.java', [
  'private final FlowRuleRoutingRepository repository;',
  'resolvePersistedFlowRule',
  'repository.findBestMatch(query)',
  'flow_rule_db_match_resolved',
  'No ACTIVE Flow-owned Dispatch Rule matched this event'
]);
requireIncludes('ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java', [
  '!isFlowRuleTask(task) && !legacyProfileEligibilityDisabledFor(eligibilityMode) && isProfileNotConfigured(requirements)',
  'routing_flow_rule_skips_legacy_profile_gate'
]);
requireIncludes('ai-event-gateway-core/database-platform/src/main/resources/db/migration/V105__r12_9_flow_rule_runtime_matching_repair.sql', [
  'alter table if exists dispatch_policies add column if not exists object_type varchar(128);',
  'alter table if exists dispatch_policies add column if not exists error_code varchar(128);',
  'DISPATCH_EVENT_TASK_MAPPING_BRIDGE',
  'dispatch_r12_9_flow_rule_runtime_match_report'
]);


requireIncludes('ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java', [
  'import com.opensocket.aievent.core.dispatch.flow.FlowRuleRoutingPlan;',
  'import com.opensocket.aievent.core.dispatch.flow.FlowRuleRoutingService;',
  'private FlowRuleRoutingService flowRuleRoutingService;',
  'task = applyR12_11FlowRuleRuntimeRepair(task);',
  'final TaskRecord scoringTask = task;',
  '.map(agent -> score(scoringTask, agent, policy))',
  'routing_flow_rule_runtime_repaired',
  'task.setRoutingPath("FLOW_RULE");',
  'task.setRoutingPolicy("FLOW_RULE");',
  'task.setRequiredCapabilities(plan.getRequiredSkills() == null || plan.getRequiredSkills().isEmpty()'
]);
requireIncludes('ai-event-gateway-core/database-platform/src/main/resources/db/migration/V107__r12_11_reassign_existing_tasks_to_flow_rules.sql', [
  'dispatch_r12_11_existing_task_flow_repair_report',
  'matched_flow_id = br.flow_id',
  "routing_path = 'FLOW_RULE'",
  'requested_skill = br.requested_skill',
  'ERP_PAYMENT_RISK_TRIAGE',
  'ERP_VENDOR_MASTER_RISK_TRIAGE',
  'R12_11_FLOW_RUNTIME_REASSIGN_REPAIR'
]);
requireIncludes('docs/R12_11_REASSIGN_EXISTING_TASK_FLOW_RULE_REPAIR/README.md', [
  'DISPATCH_PROFILE_NOT_CONFIGURED',
  'FLOW_RULE_READY_FOR_REASSIGN',
  'ERP_PAYMENT_RISK_TRIAGE'
]);
requireIncludes('docs/R12_MICROMETER_TRACE_SPAN_LOGGING/r12_11_reassign_existing_task_flow_rule_repair.md', [
  'applyR12_11FlowRuleRuntimeRepair',
  'V107__r12_11_reassign_existing_tasks_to_flow_rules.sql'
]);

requireIncludes('ai-event-gateway-core/database-platform/src/main/resources/db/migration/V106__r12_10_default_dispatch_flow_templates.sql', [
  'FLOW_ERP_INCIDENT_RESPONSE',
  'VENDOR_MASTER_BANK_ACCOUNT_CHANGED',
  'VENDOR_BANK_ACCOUNT_CHANGE',
  'ERP_VENDOR_MASTER_RISK_TRIAGE',
  'agent-cluster-node-001-003',
  'flow_agent_assignments',
  'dispatch_r12_10_template_flow_acceptance_report'
]);
requireIncludes('ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java', [
  'R12.10: Flow Rule routing is authoritative.',
  'if (isFlowRuleTask(task) || legacyProfileEligibilityDisabledFor(properties.resolvedEligibilityEngineMode()))',
  'R12.10: do not merge legacy task required_capabilities_json into Flow Rule'
]);
requireIncludes('docs/R12_10_FLOW_RULE_TEMPLATE_RUNTIME_ASSIGNMENT_FIX/README.md', [
  'ERP_VENDOR_MASTER_RISK_TRIAGE',
  'READY_FOR_RUNTIME_MATCHING'
]);
for (const rel of [
  'ai-event-gateway-core/control-plane-app/src/main/resources/application-local.yml',
  'ai-event-gateway-core/control-plane-app/src/main/resources/application-dev.yml'
]) {
  requireIncludes(rel, [
    'task-min-occurrences: ${CORE_TASK_MIN_OCCURRENCES:1}',
    'immediate-task-severities: ${CORE_IMMEDIATE_TASK_SEVERITIES:CRITICAL,HIGH}'
  ]);
}
for (const rel of [
  'deploy/docker-compose.local.yml',
  'deploy/docker-compose.ci.yml'
]) {
  requireIncludes(rel, [
    'CORE_TASK_MIN_OCCURRENCES: "1"',
    'CORE_IMMEDIATE_TASK_SEVERITIES: "CRITICAL,HIGH"'
  ]);
}
requireIncludes('docs/R12_MICROMETER_TRACE_SPAN_LOGGING/r12_9_flow_rule_runtime_assignment_fix.md', [
  'DISPATCH_PROFILE_NOT_CONFIGURED',
  'FlowRuleRoutingService now resolves persisted Flow-owned rules from DB',
  'RoutingDecisionService skips the legacy profile gate for Flow Rule tasks'
]);


requireIncludes('ai-event-gateway-core/database-platform/src/main/resources/db/migration/V108__r12_13_flow_rule_seed_rerun_and_task_repair.sql', [
  'R12.13: Re-run Flow Rule template seed and repair existing tasks after V106/V107',
  'VENDOR_MASTER_BANK_ACCOUNT_CHANGED',
  'PAYMENT_BLOCKED_BY_RISK_RULE',
  'ERP_VENDOR_MASTER_RISK_TRIAGE',
  'ERP_PAYMENT_RISK_TRIAGE',
  'R12.13 backfilled task event fields from incident authority record',
  'R12.13 retrofitted existing task to Flow Rule',
  'dispatch_r12_11_existing_task_flow_repair_report',
  'dispatch_r12_13_flow_rule_repair_diagnostics',
  'FLOW_RULE_READY_FOR_REASSIGN',
  'drop view if exists dispatch_r12_13_flow_rule_repair_diagnostics',
  'drop view if exists dispatch_r12_11_existing_task_flow_repair_report',
  'rt.event_stage::varchar(64) as event_stage'
]);
requireIncludes('docs/R12_14_V108_VIEW_TYPE_FLYWAY_FIX/README.md', [
  'cannot change data type of view column',
  'event_stage',
  'varchar(64)',
  'drop view if exists dispatch_r12_11_existing_task_flow_repair_report'
]);
requireIncludes('docs/R12_MICROMETER_TRACE_SPAN_LOGGING/r12_14_v108_view_type_flyway_fix.md', [
  'V108__r12_13_flow_rule_seed_rerun_and_task_repair.sql',
  'event_stage',
  'varchar(64)'
]);
requireIncludes('docs/R12_13_FLOW_RULE_SEED_RERUN_TASK_REPAIR/README.md', [
  'already applied `V106` and `V107` would not re-run',
  'dispatch_r12_11_existing_task_flow_repair_report',
  'FLOW_RULE_READY_FOR_REASSIGN'
]);
requireIncludes('docs/R12_MICROMETER_TRACE_SPAN_LOGGING/r12_13_flow_rule_seed_rerun_task_repair.md', [
  'V108__r12_13_flow_rule_seed_rerun_and_task_repair.sql',
  'tasks` + `incidents` event fields'
]);

for (const rel of [
  'docs/R12_MICROMETER_TRACE_SPAN_LOGGING/README.md',
  'docs/R12_MICROMETER_TRACE_SPAN_LOGGING/r12_change_log.md',
  'docs/R12_MICROMETER_TRACE_SPAN_LOGGING/r12_tracing_runbook.md',
  'docs/R12_MICROMETER_TRACE_SPAN_LOGGING/r12_to_schema_cleanup_handoff.md',
  'docs/R12_MICROMETER_TRACE_SPAN_LOGGING/r12_compile_fix.md',
  'docs/R12_MICROMETER_TRACE_SPAN_LOGGING/r12_2_task_case_timeline_view_compile_fix.md',
  'docs/R12_MICROMETER_TRACE_SPAN_LOGGING/r12_3_agent_detail_dialog_notice_tone_fix.md',
  'docs/R12_MICROMETER_TRACE_SPAN_LOGGING/r12_4_dispatch_readiness_response_type_fix.md',
  'docs/R12_MICROMETER_TRACE_SPAN_LOGGING/r12_5_dispatch_recipe_envelope_type_fix.md',
  'docs/R12_MICROMETER_TRACE_SPAN_LOGGING/r12_6_dispatch_recipe_evaluation_payload_normalize_fix.md',
  'docs/R12_MICROMETER_TRACE_SPAN_LOGGING/r12_7_admin_ui_typecheck_fix.md',
  'docs/R12_MICROMETER_TRACE_SPAN_LOGGING/r12_8_v95_source_system_flyway_fix.md',
  'docs/R12_MICROMETER_TRACE_SPAN_LOGGING/r12_9_flow_rule_runtime_assignment_fix.md',
  'docs/R12_MICROMETER_TRACE_SPAN_LOGGING/r12_10_flow_rule_template_runtime_assignment_fix.md',
  'docs/R12_MICROMETER_TRACE_SPAN_LOGGING/r12_11_reassign_existing_task_flow_rule_repair.md',
  'docs/R12_MICROMETER_TRACE_SPAN_LOGGING/r12_12_routing_decision_lambda_compile_fix.md',
  'docs/R12_MICROMETER_TRACE_SPAN_LOGGING/r12_13_flow_rule_seed_rerun_task_repair.md',
  'docs/R12_MICROMETER_TRACE_SPAN_LOGGING/r12_14_v108_view_type_flyway_fix.md',
  'ai-event-gateway-admin-ui/docs/R12_MICROMETER_TRACE_SPAN_LOGGING.md',
  'docs/CURRENT_DISPATCH_DOMAIN_MODEL.md'
]) {
  requireIncludes(rel, ['R12']);
}

console.log('OK R12 Micrometer tracing with the P1-A Spring Boot OpenTelemetry dependency cutover, trace/span log correlation, Flow Rule runtime DB matching, R12.10 default Flow templates, R12.11 existing-task reassign repair, R12.12 lambda compile fix, R12.13 seed re-run/task repair, R12.14 V108 view type fix, legacy profile gate bypass, local HIGH task creation defaults, typecheck fixes, Flyway compatibility fixes, compose env, docs, and verification gate are present.');
