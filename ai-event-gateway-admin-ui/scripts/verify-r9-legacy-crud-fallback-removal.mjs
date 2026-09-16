#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';

const repoRoot = path.resolve(process.cwd(), '..');
function read(rel) {
  const p = path.join(repoRoot, rel);
  if (!fs.existsSync(p)) throw new Error(`Missing ${rel}`);
  return fs.readFileSync(p, 'utf8');
}
function requireIncludes(label, text, needles) {
  for (const needle of needles) {
    if (!text.includes(needle)) throw new Error(`${label} missing ${needle}`);
  }
}

const migration = read('ai-event-gateway-core/database-platform/src/main/resources/db/migration/V102__r9_legacy_crud_fallback_removal.sql');
requireIncludes('R9 migration', migration, [
  'legacy_readonly',
  'R9_LEGACY_READONLY_NO_FLOW_ID',
  'dispatch_r9_formal_routing_gate_report',
  'LEGACY_FALLBACK_NOT_FORMAL_SUCCESS',
  'FLOW_RULE_REQUIRED_BLOCKED',
  'dispatch_r9_legacy_crud_gate_report',
]);

const props = read('ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingProperties.java');
requireIncludes('RoutingProperties', props, [
  'flowRuleLegacyFallbackEnabled = false',
  'formalSuccessRequiresFlowRule = true',
  'standaloneDispatchPolicyWritesEnabled = false',
  'flowRuleRoutingEnabled = true',
]);

const flowResolver = read('ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/dispatch/flow/FlowRuleRoutingService.java');
requireIncludes('FlowRuleRoutingService', flowResolver, [
  'R9 requires persisted Flow-owned Rule evidence',
  'Formal evidence fields are matchedFlowId and matchedRuleId',
  'requestedSkill is required',
  'Synthetic source/capability fallback is disabled',
]);

const taskDecision = read('ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/task/TaskDecisionService.java');
requireIncludes('TaskDecisionService', taskDecision, [
  'applyR9FormalRoutingGate',
  'FLOW_RULE_REQUIRED_BLOCKED',
  'NO_ACTIVE_FLOW_RULE: an ACTIVE Dispatch Flow Rule',
]);

const routingDecision = read('ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java');
requireIncludes('RoutingDecisionService', routingDecision, [
  'Historical profile,',
  'comparisons are support-only and never participate in routing',
  'RoutingPolicy.MANUAL_REVIEW',
]);

const controller = read('ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentAssignmentController.java');
requireIncludes('AgentAssignmentController', controller, [
  'requireStandaloneDispatchPolicyWriteAllowed',
  'R9_STANDALONE_DISPATCH_RULE_CRUD_DISABLED',
  'isStandaloneDispatchPolicyWritesEnabled',
]);

const page = read('ai-event-gateway-admin-ui/app/dispatch-policies/page.tsx');
requireIncludes('Dispatch Rules page', page, [
  'R9_STANDALONE_RULE_CRUD_DISABLED',
  'Standalone Dispatch Rules CRUD is disabled',
  'diagnostics-only registry',
]);

const consoleText = read('ai-event-gateway-admin-ui/components/dispatch-policies/DispatchPolicyV2Console.tsx');
requireIncludes('DispatchPolicyV2Console', consoleText, [
  'Read-only legacy rule catalog',
  'standalone writes return R9_STANDALONE_DISPATCH_RULE_CRUD_DISABLED',
]);

const dispatchCapabilities = read('ai-event-gateway-admin-ui/app/dispatch-capabilities/page.tsx');
requireIncludes('Dispatch Capabilities redirect', dispatchCapabilities, ['redirect(buildDispatchFlowsHref']);

for (const rel of [
  'docs/R9_LEGACY_CRUD_FALLBACK_REMOVAL/README.md',
  'docs/R9_LEGACY_CRUD_FALLBACK_REMOVAL/r9_change_log.md',
  'docs/R9_LEGACY_CRUD_FALLBACK_REMOVAL/r9_to_r10_handoff.md',
  'ai-event-gateway-admin-ui/docs/R9_LEGACY_CRUD_FALLBACK_REMOVAL.md',
]) read(rel);

console.log('OK R9 legacy CRUD/fallback removal gates, readonly UI, formal routing reports, docs, and verification gate are present.');
