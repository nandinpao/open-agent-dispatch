#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';

const root = path.resolve(process.cwd(), '..');
const checks = [
  ['AgentAssignmentService auto-activates runtime binding from runtime observation', 'ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/assignment/AgentAssignmentService.java', [
    'ensureActiveRuntimeBindingForRuntimeObservation',
    'dispatchAuthority',
    'ACTIVE_RUNTIME_BINDING',
  ]],
  ['AgentDirectoryService calls runtime binding activation on connected/heartbeat', 'ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/AgentDirectoryService.java', [
    'ensureRuntimeBindingAuthority',
    'ensureActiveRuntimeBindingForRuntimeObservation',
    'runtime-observation',
  ]],
  ['Cluster bootstrap creates active runtime binding', 'ai-event-gateway-netty/scripts/core-bootstrap-cluster-agents.js', [
    'ensureRuntimeBinding',
    '/admin/runtime-resources/',
    '/runtime-bindings',
    'runtimeBinding=',
  ]],
  ['Agent detail loads and operates runtime bindings', 'ai-event-gateway-admin-ui/hooks/useAgentDetail.ts', [
    'getAgentRuntimeBindings',
    'createRuntimeBindingFromCurrentRuntime',
    'activateRuntimeBinding',
  ]],
  ['Agent detail renders Runtime Binding panel', 'ai-event-gateway-admin-ui/components/agents/AgentDetailProductView.tsx', [
    'Runtime Binding',
    'Create / Activate Binding',
    'Runtime online status alone is telemetry',
  ]],
  ['Operator reason maps runtime binding missing', 'ai-event-gateway-admin-ui/lib/dispatch-evidence/operatorFailureReasons.ts', [
    'RUNTIME_BINDING_MISSING',
    'Runtime binding is not active',
    'Core has not activated the runtime binding',
  ]],
  ['Stage19 E2E asserts runtime binding gate', 'scripts/acceptance/dispatch-assignment-e2e-gate.mjs', [
    'RUNTIME_BINDING_ACTIVE',
    'setup-readiness READY',
  ]],
];

for (const [label, rel, needles] of checks) {
  const file = path.join(root, rel);
  if (!fs.existsSync(file)) {
    throw new Error(`${label}: missing file ${rel}`);
  }
  const content = fs.readFileSync(file, 'utf8');
  for (const needle of needles) {
    if (!content.includes(needle)) {
      throw new Error(`${label}: missing ${needle} in ${rel}`);
    }
  }
}

console.log('[stage22] Runtime binding activation and dispatch authority verification passed.');
