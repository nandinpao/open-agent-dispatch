#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
function source(rel) {
  const file = path.resolve(root, rel);
  if (!fs.existsSync(file)) throw new Error(`missing ${rel}`);
  return fs.readFileSync(file, 'utf8');
}
function includes(rel, tokens) {
  const text = source(rel);
  for (const token of tokens) {
    if (!text.includes(token)) throw new Error(`${rel} missing ${token}`);
  }
}
function excludes(rel, tokens) {
  const text = source(rel);
  for (const token of tokens) {
    if (text.includes(token)) throw new Error(`${rel} contains retired standard-flow token ${token}`);
  }
}

includes('../scripts/acceptance/stage0-dispatch-characterization.mjs', [
  '/api/events/intake', 'STAGE_1_BACKEND_GOLDEN_PATH', '${scenarioPrefix}-01 no capability Backend Golden Path', '${scenarioPrefix}-02 explicit capability Backend Golden Path', 'sourceSystem'
]);
includes('../ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchFlowController.java', [
  '@PostMapping("/{flowId}/test-event")', 'eventIntakeApplicationService.intake(request)', 'openDispatchRealTestEvent'
]);
includes('components/dispatch-contract-builder/DispatchContractBuilderConsole.tsx', [
  '發送真實測試事件', 'createDispatchFlowRealTestEvent', '查看真實 Task 與時間線'
]);
excludes('components/dispatch-contract-builder/DispatchContractBuilderConsole.tsx', [
  'dryRunDispatchFlow(', 'createDispatchReadinessTestEvent('
]);
includes('lib/tasks/dispatchLifecycle.ts', [
  'buildStandardDispatchTimeline', 'NO_ACTIVE_FLOW_RULE', 'REQUIRED_CAPABILITY_MISSING', 'AGENT_OFFLINE'
]);
includes('../Makefile', ['characterize-stage1-strict', 'stage5-real-event-task-diagnosis']);

console.log('OK Stage 19 compatibility now verifies the source-neutral real Event -> Task -> Flow -> Agent -> Result path without a parallel readiness gate.');
