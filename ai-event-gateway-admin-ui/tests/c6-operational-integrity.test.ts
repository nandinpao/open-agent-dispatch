import assert from 'node:assert/strict';
import test from 'node:test';
import { generateAgentCredentialToken } from '../lib/agents/credentialToken.ts';
import {
  buildTaskRemediationCommandRequest,
  deriveAllowedTaskRemediationCommands,
  taskRuntimeVersion,
} from '../lib/tasks/taskRemediationCommands.ts';
import type { CoreTaskRuntimeView } from '../lib/types/core.ts';

test('C6-A uses a cryptographic 256-bit Agent credential token', () => {
  const first = generateAgentCredentialToken();
  const second = generateAgentCredentialToken();
  assert.match(first, /^agt_[0-9a-f]{64}$/);
  assert.match(second, /^agt_[0-9a-f]{64}$/);
  assert.notEqual(first, second);
});

test('C6-D task remediation requires the authoritative resource version', () => {
  const task = { taskId: 'task-c6', status: 'FAILED', version: 42 } as CoreTaskRuntimeView;
  assert.equal(taskRuntimeVersion(task), 42);
  assert.throws(
    () => taskRuntimeVersion({ taskId: 'task-stale', status: 'FAILED' } as CoreTaskRuntimeView),
    /resource version is unavailable/i,
  );
});

test('C6-C preserves the logical idempotency key and does not submit an operator identity', () => {
  const task = { taskId: 'task-c6', status: 'FAILED', version: 7 } as CoreTaskRuntimeView;
  const request = buildTaskRemediationCommandRequest({
    task,
    commandType: 'RETRY_TASK',
    reason: 'Retry after operator review.',
    idempotencyKey: 'task-remediation-c6-logical-operation',
  });
  assert.equal(request.expectedTaskVersion, 7);
  assert.equal(request.idempotencyKey, 'task-remediation-c6-logical-operation');
  assert.equal('operatorId' in request, false);
});

test('C6-E destructive Task commands have distinct labels and shared high-risk confirmation contracts', () => {
  const task = { taskId: 'task-c6', status: 'FAILED', version: 11 } as CoreTaskRuntimeView;
  const definitions = deriveAllowedTaskRemediationCommands(task);
  const cancel = definitions.find((item) => item.commandType === 'CANCEL_TASK');
  const ignore = definitions.find((item) => item.commandType === 'IGNORE_TASK');
  assert.equal(cancel?.label, 'Cancel Task');
  assert.equal(cancel?.riskLevel, 'HIGH');
  assert.equal(cancel?.requiredPhrase, 'CONFIRM_CANCEL_TASK');
  assert.equal(ignore?.label, 'Ignore Task');
  assert.equal(ignore?.riskLevel, 'HIGH');
  assert.equal(ignore?.requiredPhrase, 'CONFIRM_IGNORE_TASK');
  assert.notEqual(cancel?.label, ignore?.label);
  assert.notEqual(cancel?.label, 'Details');
  assert.notEqual(ignore?.label, 'Details');
});
