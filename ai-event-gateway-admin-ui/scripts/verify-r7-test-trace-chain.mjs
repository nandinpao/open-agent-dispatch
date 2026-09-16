#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
function read(rel) {
  const full = path.join(root, rel);
  if (!fs.existsSync(full)) throw new Error(`Missing ${rel}`);
  return fs.readFileSync(full, 'utf8');
}
function assertIncludes(rel, tokens) {
  const text = read(rel);
  for (const token of tokens) if (!text.includes(token)) throw new Error(`${rel} is missing token: ${token}`);
  return text;
}
function assertExcludes(rel, tokens) {
  const text = read(rel);
  for (const token of tokens) if (text.includes(token)) throw new Error(`${rel} still exposes retired Stage 4 token: ${token}`);
  return text;
}

assertIncludes('../ai-event-gateway-core/database-platform/src/main/resources/db/migration/V100__r7_dispatch_flow_trace_chain.sql', [
  'dispatch_flow_trace_events', 'dispatch_r7_trace_chain_report', 'event_stage', 'requested_skill', 'failure_stage', 'fix_action', 'correlation_id',
]);
assertIncludes('../ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowTraceStepView.java', [
  'matchedFlowId', 'matchedRuleId', 'requestedSkill', 'failureStage', 'fixAction', 'selectedAgentId',
]);
assertIncludes('../ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowTraceChainView.java', [
  'DispatchFlowTraceStepView', 'testMode', 'failureStage', 'fixAction',
]);
const controller = assertIncludes('../ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchFlowController.java', [
  'test-external', 'test-a2a', 'test-result', 'test-chain', 'skeletonTraceChain', 'A2A_AGENT_ELIGIBILITY', 'R7_TRACE_CHAIN_PREVIEW',
]);
if (controller.includes('Assign MES Agent to this Flow')) throw new Error('R7 compatibility controller must not contain source-specific MES remediation wording.');
assertIncludes('lib/types/core.ts', ['CoreDispatchFlowTraceStepView', 'CoreDispatchFlowTraceChainView', 'failureStage', 'fixAction']);
assertIncludes('lib/api/endpoints.ts', ['dispatchFlowTrace', 'dispatchFlowTestExternal', 'dispatchFlowTestA2a', 'dispatchFlowTestResult', 'dispatchFlowTestChain']);
assertIncludes('lib/api/coreAdminApi.ts', ['getDispatchFlowTrace', 'testDispatchFlowExternal', 'testDispatchFlowA2a', 'testDispatchFlowResult', 'testDispatchFlowChain']);

// R7 trace APIs remain available for support compatibility, but Stage 4 must
// not expose a parallel Test Dispatch Readiness / trace workflow in the normal
// Dispatch Flow console.
assertIncludes('components/dispatch-contract-builder/DispatchContractBuilderConsole.tsx', [
  '真正的 Event → Task → Agent 派送由正常 Task 流程驗證',
  '不使用另一套 Readiness Simulator',
]);
assertExcludes('components/dispatch-contract-builder/DispatchContractBuilderConsole.tsx', [
  'coreAdminApi.testDispatchFlowExternal(',
  'coreAdminApi.testDispatchFlowA2a(',
  'coreAdminApi.testDispatchFlowResult(',
  'coreAdminApi.testDispatchFlowChain(',
  'createDispatchContractTestTask(',
]);
assertIncludes('../docs/R7_TEST_TRACE_CHAIN/README.md', ['R7 Test / Trace Chain', 'EXTERNAL intake', 'A2A intake2', 'RESULT callback', 'NO_CANDIDATE']);
assertIncludes('docs/R7_TEST_TRACE_CHAIN.md', ['matchedFlowId', 'failureStage', 'fixAction']);

console.log('OK R7 trace persistence/API compatibility remains support-only; the Stage 4 beginner Flow UI validates through the real Event -> Task -> Agent path instead of a parallel simulator.');
