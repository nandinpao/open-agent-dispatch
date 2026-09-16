import assert from 'node:assert/strict';
import test from 'node:test';
import { allowedConflictResolutions, credentialRotationImpact, deriveAgentBlockingExperience, syncQueueAction } from '@/lib/phase7d/issueAgentUx';

test('Agent blocking classification prioritizes security and credential authority', () => {
  assert.equal(deriveAgentBlockingExperience({ hasProfile: false }).reason, 'MISSING_PROFILE');
  assert.equal(deriveAgentBlockingExperience({ hasProfile: true, approvalStatus: 'APPROVED', enabled: true, riskStatus: 'QUARANTINED', credentialStatus: 'ACTIVE', runtimeConnected: true }).reason, 'QUARANTINED');
  assert.equal(deriveAgentBlockingExperience({ hasProfile: true, approvalStatus: 'APPROVED', enabled: true, riskStatus: 'NORMAL', credentialStatus: 'MISSING', runtimeConnected: true }).reason, 'CREDENTIAL_MISSING');
});

test('Agent Dispatch Access and Dispatch Flow remain distinct readiness checks', () => {
  const noScope = deriveAgentBlockingExperience({ hasProfile: true, approvalStatus: 'APPROVED', enabled: true, riskStatus: 'NORMAL', credentialStatus: 'ACTIVE', runtimeConnected: true, runtimeBindingActive: true, dispatchAccessRuleCount: 0, activeDispatchFlowCount: 1 });
  assert.equal(noScope.reason, 'NO_SERVICE_SCOPE');
  assert.equal(noScope.title, 'Dispatch access is not configured');
  assert.match(noScope.safestNextAction, /Dispatch Access/i);
  assert.equal(noScope.repairTarget, 'DISPATCH_ACCESS');
  const noFlow = deriveAgentBlockingExperience({ hasProfile: true, approvalStatus: 'APPROVED', enabled: true, riskStatus: 'NORMAL', credentialStatus: 'ACTIVE', runtimeConnected: true, runtimeBindingActive: true, dispatchAccessRuleCount: 1, activeDispatchFlowCount: 0 });
  assert.equal(noFlow.reason, 'NO_DISPATCH_FLOW');
});

test('Credential rotation impact exposes metadata and blast radius, not secret values', () => {
  const result = credentialRotationImpact({ status: 'ACTIVE', expiresAt: '2026-08-10T00:00:00Z', now: new Date('2026-08-01T00:00:00Z'), mappingCount: 4, principalCount: 2 });
  assert.equal(result.urgency, 'HIGH');
  assert.equal(result.blastRadius, 6);
  assert.equal(result.reconnectRequired, true);
});

test('Sync queue states produce governed next actions', () => {
  assert.match(syncQueueAction('DEAD_LETTER').action, /manual recovery/i);
  assert.match(syncQueueAction('WAITING_FOR_INDEX').safeReason, /read index/i);
  assert.match(syncQueueAction('CONFLICT').action, /conflict review/i);
});

test('Conflict resolutions preserve OpenDispatch Task authority', () => {
  const options = allowedConflictResolutions();
  assert.equal(options.length, 6);
  assert.ok(options.every((item) => !/external.*task authority/i.test(item.authorityEffect)));
  assert.ok(options.some((item) => item.code === 'OPENDISPATCH_WINS'));
  assert.ok(options.some((item) => item.code === 'MANUAL_REVIEW'));
});
