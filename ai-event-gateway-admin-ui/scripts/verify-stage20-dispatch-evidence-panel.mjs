#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
function text(rel) { return fs.readFileSync(path.join(root, rel), 'utf8'); }
function require(rel, tokens) {
  const source = text(rel);
  for (const token of tokens) if (!source.includes(token)) throw new Error(`${rel} missing ${token}`);
}

require('components/tasks/TaskDetailView.tsx', [
  'TaskPrimaryDiagnosisPanel',
  'StandardDispatchTimelinePanel',
  'buildStandardDispatchTimeline',
  'deriveTaskDispatchDiagnosis',
  'id: "debug"',
  '<DispatchAssignmentEvidencePanel',
]);
const task = text('components/tasks/TaskDetailView.tsx');
if (task.indexOf('<DispatchAssignmentEvidencePanel') < task.indexOf('id: "debug"')) {
  throw new Error('DispatchAssignmentEvidencePanel must remain support-only under Debug, not before the standard Task diagnosis.');
}
require('lib/tasks/dispatchLifecycle.ts', [
  'NO_ACTIVE_FLOW_RULE', 'REQUIRED_CAPABILITY_MISSING', 'AGENT_OFFLINE', 'NO_ELIGIBLE_AGENT',
  'Event', 'Assignment', 'delivery', 'result'
]);
require('components/agents/AgentDetailProductView.tsx', [
  '@/components/dispatch-evidence/DispatchAssignmentEvidencePanel'
]);
console.log('[stage20] authoritative Task diagnosis is standard; detailed assignment evidence remains available only in support/debug contexts.');
