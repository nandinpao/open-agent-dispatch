import fs from 'node:fs';

const requiredFiles = [
  '../ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/routing/EligibilityEngineMode.java',
  '../ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java',
  '../ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingProperties.java',
  'components/tasks/TaskDetailView.tsx',
  'docs/P3_I_ROUTING_SHADOW_CONTROLLED_ENFORCEMENT.md'
];
for (const file of requiredFiles) {
  if (!fs.existsSync(file)) throw new Error(`Missing required file: ${file}`);
}

const mode = fs.readFileSync('../ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/routing/EligibilityEngineMode.java', 'utf8');
for (const token of ['LEGACY_ONLY', 'SHADOW', 'WARN', 'ENFORCE', 'usesV2', 'enforce']) {
  if (!mode.includes(token)) throw new Error(`EligibilityEngineMode missing token: ${token}`);
}

const routing = fs.readFileSync('../ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java', 'utf8');
for (const token of [
  'DispatchEligibilityServiceV2',
  'V2RoutingComparison',
  'shouldFailClosedBeforeLegacy',
  'applyV2ScoreAnnotations',
  'userFacingV2EnforcementError',
  'DISPATCH_ELIGIBILITY_V2_BLOCKED',
  'eligibilityV2ScoreBreakdown',
  'eligibilityMode.enforce()'
]) {
  if (!routing.includes(token)) throw new Error(`RoutingDecisionService missing token: ${token}`);
}

const props = fs.readFileSync('../ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingProperties.java', 'utf8');
if (!props.includes('eligibilityEngineMode') || !props.includes('resolvedEligibilityEngineMode')) {
  throw new Error('RoutingProperties missing P3-I eligibility engine mode setting');
}

const app = fs.readFileSync('../ai-event-gateway-core/control-plane-app/src/main/resources/application.yml', 'utf8');
if (!app.includes('ROUTING_ELIGIBILITY_ENGINE_MODE')) {
  throw new Error('application.yml missing ROUTING_ELIGIBILITY_ENGINE_MODE');
}

const ui = fs.readFileSync('components/tasks/TaskDetailView.tsx', 'utf8');
for (const token of ['P3-I · Eligibility V2 Comparison', 'Legacy only', 'V2 only', 'legacyAgents']) {
  if (!ui.includes(token)) throw new Error(`TaskDetailView missing P3-I comparison token: ${token}`);
}

console.log('OK P3-I routing shadow comparison and controlled enforcement scaffolding is present.');
