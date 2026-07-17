#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
function require(rel, patterns) {
  const file = path.join(root, rel);
  if (!fs.existsSync(file)) throw new Error(`Missing file: ${rel}`);
  const source = fs.readFileSync(file, 'utf8');
  for (const pattern of patterns) if (!source.includes(pattern)) throw new Error(`${rel} missing pattern: ${pattern}`);
}
function exclude(rel, patterns) {
  const source = fs.readFileSync(path.join(root, rel), 'utf8');
  for (const pattern of patterns) if (source.includes(pattern)) throw new Error(`${rel} contains retired standard reason: ${pattern}`);
}

require('lib/tasks/dispatchLifecycle.ts', [
  'deriveTaskDispatchDiagnosis',
  'NO_ACTIVE_FLOW_RULE',
  'REQUIRED_CAPABILITY_MISSING',
  'AGENT_OFFLINE',
  'AGENT_NO_CAPACITY',
  'NO_ELIGIBLE_AGENT',
  'DELIVERY_FAILED',
  'CALLBACK_FAILED',
]);
require('tests/stage5-real-event-task-diagnosis.test.ts', [
  'normalizes all missing-flow variants to one NO_ACTIVE_FLOW_RULE reason',
  'links an offline blocker directly to the selected Agent',
]);
require('components/tasks/TaskDetailView.tsx', [
  '派工主因', 'diagnosis.actionHref', 'StandardDispatchTimelinePanel'
]);
exclude('components/tasks/TaskDetailView.tsx', [
  'Open Dispatch Readiness', 'runTaskDispatchReadiness', 'repairDispatchContract'
]);
console.log('[stage21] dispatch failure reasons converge on the Stage 5 canonical Flow/Capability/Agent/Delivery vocabulary.');
