#!/usr/bin/env node
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

const root = process.cwd();
function read(path) { return readFileSync(join(root, path), 'utf8'); }
function assertContains(path, needles) {
  const text = read(path);
  for (const needle of needles) {
    if (!text.includes(needle)) throw new Error(`${path} missing ${needle}`);
  }
}

assertContains('components/dispatch-evidence/DispatchAssignmentEvidencePanel.tsx', [
  'Runtime Delivery Confirmation',
  'Gateway delivered',
  'Agent ACK',
  'Agent RESULT',
  'callbackInboxSummary',
  'deliveryAttempt',
  'callbackRelayAttempt',
]);
assertContains('components/tasks/TaskDetailView.tsx', [
  'callbackInbox={data.callbackInbox}',
  'callbackInboxSummary={data.callbackInboxSummary}',
  'deliveryAttempt={data.row.delivery}',
  'callbackRelayAttempt={data.row.callbackRelay}',
]);
assertContains('hooks/useTaskDetail.ts', [
  'getTaskDispatchRequests(taskId, 100)',
  'dispatchRequests: dispatchRequests.data ?? []',
]);
assertContains('../scripts/acceptance/dispatch-delivery-confirmation-e2e.mjs', [
  'Stage 23 real runtime dispatch delivery confirmation gate',
  'execute dispatch request',
  'agent ACK callback',
  'agent RESULT callback',
  'callback-inbox',
  'terminalCallbackReceived',
]);
assertContains('package.json', ['verify:stage23-delivery-confirmation']);
console.log('OK Stage 23 delivery confirmation artifacts verified.');
