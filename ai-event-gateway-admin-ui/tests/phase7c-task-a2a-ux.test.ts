import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  buildA2AContextPreview,
  buildA2AResultLanes,
  normalizeTaskBlockingReason,
  type A2APolicyView,
  type CreateA2ARequestInput,
} from '@/lib/phase7c/taskA2aUx';

test('service-scope failures map to the standard blocking reason', () => {
  const value = normalizeTaskBlockingReason({ blockedReason: 'DISPATCH_PROFILE_NOT_CONFIGURED / NO_SERVICE_SCOPE', status: 'ASSIGNED' });
  assert.equal(value?.code, 'NO_SERVICE_SCOPE');
  assert.equal(value?.userCanFix, true);
});

test('approval waits are distinct from manual review', () => {
  const value = normalizeTaskBlockingReason({ dispatchWaitReason: 'WAITING_APPROVAL', status: 'WAITING_APPROVAL' });
  assert.equal(value?.code, 'WAITING_APPROVAL');
  assert.equal(value?.userCanFix, false);
});

test('terminal tasks do not report an active blocker', () => {
  assert.equal(normalizeTaskBlockingReason({ status: 'COMPLETED' }), null);
});

test('context preview never renders payload content and separates dispositions', () => {
  const policy: A2APolicyView = {
    policyId: 'legacy-policy-1', approvalMode: 'DUAL_APPROVAL', version: 3,
  };
  const request: CreateA2ARequestInput = {
    requestedTaskType: 'INCIDENT_RESPONSE',
    requestedCapabilityCodes: ['production.execution-status.read'], reason: 'Investigate the failed production event.',
    inputPayloadRef: 'vault://sensitive-reference', sensitivityLevel: 'CONFIDENTIAL',
  };
  const preview = buildA2AContextPreview({ taskId: 'task-1', policy, request });
  assert.equal(preview.find((item) => item.key === 'payload-ref')?.disposition, 'MASKED');
  assert.equal(preview.some((item) => item.value.includes('vault://')), false);
  assert.equal(preview.find((item) => item.key === 'attachments')?.disposition, 'OMITTED');
  assert.equal(preview.find((item) => item.key === 'approval')?.disposition, 'REQUIRES_APPROVAL');
});

test('result lanes preserve three independent authorities', () => {
  const lanes = buildA2AResultLanes({
    result: { status: 'ACCEPTED', summary: 'Agent supplied a result.' },
    aggregation: { status: 'WAIT_HUMAN', summary: 'Human acceptance is pending.' },
    projection: { status: 'CLOSED', summary: 'External issue is closed.' },
  });
  assert.deepEqual(lanes.map((lane) => lane.lane), ['AGENT_RESULT', 'HUMAN_ACCEPTED_RESULT', 'EXTERNAL_ISSUE_PROJECTION']);
  assert.equal(lanes[2].status, 'CLOSED');
  assert.notEqual(lanes[1].status, lanes[2].status);
});
