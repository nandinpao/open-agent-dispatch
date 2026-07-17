import fs from 'node:fs';
import path from 'node:path';

const root = path.resolve(process.cwd(), '..');
function requireFile(rel) {
  const abs = path.join(root, rel);
  if (!fs.existsSync(abs)) throw new Error(`Missing file: ${rel}`);
  return fs.readFileSync(abs, 'utf8');
}
function requireIncludes(name, text, tokens) {
  const missing = tokens.filter((token) => !text.includes(token));
  if (missing.length) throw new Error(`${name} missing expected tokens:\n${missing.map((token) => ` - ${token}`).join('\n')}`);
}
function requireExcludes(name, text, tokens) {
  const found = tokens.filter((token) => text.includes(token));
  if (found.length) throw new Error(`${name} contains retired Stage 4 workflow tokens:\n${found.map((token) => ` - ${token}`).join('\n')}`);
}

const migration = requireFile('ai-event-gateway-core/database-platform/src/main/resources/db/migration/V99__r6_flow_rule_routing_primary.sql');
requireIncludes('R6 migration', migration, ['dispatch_r6_flow_rule_routing_report', 'FLOW_RULE_ROUTED', 'LEGACY_FALLBACK', 'idx_tasks_r6_flow_rule_path']);

const policy = requireFile('ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/routing/RoutingPolicy.java');
requireIncludes('RoutingPolicy', policy, ['FLOW_RULE']);

const plan = requireFile('ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/FlowRuleRoutingPlan.java');
requireIncludes('FlowRuleRoutingPlan', plan, ['class FlowRuleRoutingPlan', 'flowId', 'ruleId', 'requestedSkill', 'routingPath', 'requiredSkills']);

const resolver = requireFile('ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/dispatch/flow/FlowRuleRoutingService.java');
requireIncludes('FlowRuleRoutingService', resolver, ['class FlowRuleRoutingService', 'resolve(TaskRecord task)', 'applyToTask', 'FLOW_RULE', 'A2A_DISPATCH', 'RESULT_CALLBACK', 'ISSUE_TRACKING']);

const taskDecision = requireFile('ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/task/TaskDecisionService.java');
requireIncludes('TaskDecisionService', taskDecision, ['FlowRuleRoutingService', 'resolveAndApplyFlowRulePlan', 'appendFlowRuleReason', 'Flow Rule matched:', 'Flow Rule matching failed:']);
requireExcludes('TaskDecisionService public wording', taskDecision, ['R6 flowRule=', 'R9 flowRule=']);

const routing = requireFile('ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java');
requireIncludes('RoutingDecisionService', routing, ['isFlowRuleTask', 'flowRuleRequiredSkills', 'RoutingPolicy.FLOW_RULE', 'flowRuleDecisionSuffix', 'scoreBreakdown.put("matchedFlowId"', 'scoreBreakdown.put("matchedRuleId"', 'scoreBreakdown.put("requestedSkill"']);

const props = requireFile('ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingProperties.java');
requireIncludes('RoutingProperties', props, ['flowRuleRoutingEnabled', 'flowRuleLegacyFallbackEnabled', 'isFlowRuleRoutingEnabled']);

const controller = requireFile('ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchFlowController.java');
requireIncludes('DispatchFlowController compatibility API', controller, [
  'R6_FLOW_RULE_PRIMARY_PREVIEW',
  'Flow-owned Rule routing is now the primary routing contract',
  '@PostMapping("/{flowId}/rules")',
]);

const flowConsole = requireFile('ai-event-gateway-admin-ui/components/dispatch-contract-builder/DispatchContractBuilderConsole.tsx');
requireIncludes('Stage 4 Dispatch Flow console', flowConsole, [
  'candidatePoolMode: \'EXPLICIT_FLOW_AGENTS\'',
  'requestedSkill: undefined',
  'coreAdminApi.createDispatchFlow',
  'coreAdminApi.updateDispatchFlow',
  '真正的 Event → Task → Agent 派送由正常 Task 流程驗證',
]);
requireExcludes('Stage 4 Dispatch Flow console', flowConsole, [
  'coreAdminApi.upsertDispatchFlowRule',
]);

const docs = [
  'docs/R6_FLOW_RULE_ROUTING_ENGINE/README.md',
  'docs/R6_FLOW_RULE_ROUTING_ENGINE/r6_change_log.md',
  'docs/R6_FLOW_RULE_ROUTING_ENGINE/r6_to_r7_handoff.md',
  'ai-event-gateway-admin-ui/docs/R6_FLOW_RULE_ROUTING_ENGINE.md',
];
for (const doc of docs) requireIncludes(doc, requireFile(doc), ['R6', 'Flow']);

const packageJson = requireFile('ai-event-gateway-admin-ui/package.json');
requireIncludes('package.json', packageJson, ['verify:r6-flow-rule-routing-engine']);
const makefile = requireFile('Makefile');
requireIncludes('Makefile', makefile, ['verify-r6-flow-rule-routing-engine']);

console.log('OK R6 Flow Rule routing remains the backend authority, while Stage 4 exposes it only through the complete beginner Dispatch Flow aggregate.');
