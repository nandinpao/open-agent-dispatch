import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { deriveEffectiveDispatchPolicy } from '../lib/agents/dispatchPolicy';

const profile = {
  agentId: 'openclaw-agent-001',
  approvalStatus: 'APPROVED',
  enabled: true,
  riskStatus: 'NORMAL',
  credential: { credentialStatus: 'ACTIVE' },
  capabilities: [
    { capabilityCode: 'ISSUE_ANALYSIS', enabled: true },
    { capabilityCode: 'PROPOSE_ONLY', enabled: true }
  ],
  authorizationScopes: [
    { systemCode: 'JIRA', taskType: 'ISSUE_ANALYSIS', siteCode: 'TPE', enabled: true }
  ]
};

const items = [
  { agentId: 'openclaw-agent-001', capabilityKind: 'flat', capabilityValue: 'ISSUE_ANALYSIS' },
  { agentId: 'openclaw-agent-001', capabilityKind: 'taskType', capabilityValue: 'ISSUE_ANALYSIS' },
  { agentId: 'openclaw-agent-001', capabilityKind: 'issueProvider', capabilityValue: 'JIRA' },
  { agentId: 'openclaw-agent-001', capabilityKind: 'toolPolicy', capabilityValue: 'PROPOSE_ONLY' }
];

describe('effective dispatch policy', () => {
  it('builds an eligible policy from approved and reported skills', () => {
    const policy = deriveEffectiveDispatchPolicy({
      profile,
      runtime: { agentId: 'openclaw-agent-001', connected: true },
      runtimeCapabilityItems: items,
      runtimeLoad: { agentId: 'openclaw-agent-001', availableSlots: 1, activeTasks: 0, maxConcurrentTasks: 2, capacityUtilization: 0 }
    });

    assert.equal(policy.status, 'ELIGIBLE');
    assert.deepEqual(policy.effectiveTaskTypes, ['ISSUE_ANALYSIS']);
    assert.deepEqual(policy.effectiveProviders, ['JIRA']);
    assert.deepEqual(policy.effectiveCapabilities, ['ISSUE_ANALYSIS', 'PROPOSE_ONLY']);
  });

  it('flags a probe with a missing provider as degraded', () => {
    const policy = deriveEffectiveDispatchPolicy({
      profile,
      runtime: { agentId: 'openclaw-agent-001', connected: true },
      runtimeCapabilityItems: items,
      runtimeLoad: { agentId: 'openclaw-agent-001', availableSlots: 1 },
      probe: { taskType: 'ISSUE_ANALYSIS', issueProvider: 'GITLAB', requiredCapabilities: ['ISSUE_ANALYSIS'] }
    });

    assert.equal(policy.status, 'DEGRADED');
    assert.equal(policy.probe?.matched, false);
    assert.equal(policy.probe?.missing.includes('provider:GITLAB'), true);
  });

  it('blocks when governance is disabled or runtime is draining', () => {
    const policy = deriveEffectiveDispatchPolicy({
      profile: { ...profile, enabled: false },
      runtime: { agentId: 'openclaw-agent-001', connected: true },
      runtimeCapabilityItems: items,
      runtimeLoad: { agentId: 'openclaw-agent-001', availableSlots: 1, draining: true }
    });

    assert.equal(policy.status, 'BLOCKED');
    assert.equal(policy.gates.some((gate) => gate.code === 'ENABLED' && gate.status === 'BLOCK'), true);
    assert.equal(policy.gates.some((gate) => gate.code === 'DRAINING' && gate.status === 'BLOCK'), true);
  });
});
