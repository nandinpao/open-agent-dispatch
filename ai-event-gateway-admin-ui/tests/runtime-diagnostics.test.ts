import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { deriveRuntimeDiagnostics } from '../lib/agents/runtimeDiagnostics';

const approvedProfile = {
  agentId: 'openclaw-agent-001',
  approvalStatus: 'APPROVED',
  enabled: true,
  riskStatus: 'NORMAL',
  capabilities: [
    { capabilityCode: 'ISSUE_ANALYSIS', enabled: true },
    { capabilityCode: 'PROPOSE_ONLY', enabled: true }
  ],
  authorizationScopes: [
    { taskType: 'ISSUE_ANALYSIS', systemCode: 'JIRA', enabled: true }
  ]
};

describe('runtime diagnostics', () => {
  it('derives effective capabilities from approved and reported capability items', () => {
    const result = deriveRuntimeDiagnostics({
      profile: approvedProfile,
      runtimeCapabilityItems: [
        { agentId: 'openclaw-agent-001', capabilityKind: 'taskType', capabilityValue: 'ISSUE_ANALYSIS' },
        { agentId: 'openclaw-agent-001', capabilityKind: 'issueProvider', capabilityValue: 'JIRA' },
        { agentId: 'openclaw-agent-001', capabilityKind: 'toolPolicy', capabilityValue: 'PROPOSE_ONLY' },
        { agentId: 'openclaw-agent-001', capabilityKind: 'flat', capabilityValue: 'ISSUE_ANALYSIS' }
      ],
      runtimeLoad: {
        agentId: 'openclaw-agent-001',
        activeTasks: 0,
        maxConcurrentTasks: 2,
        availableSlots: 2,
        capacityUtilization: 0,
        heartbeatAt: new Date().toISOString()
      }
    });

    assert.equal(result.status, 'OK');
    assert.deepEqual(result.effectiveCapabilityCodes, ['ISSUE_ANALYSIS', 'PROPOSE_ONLY']);
    assert.deepEqual(result.effectiveTaskTypes, ['ISSUE_ANALYSIS']);
    assert.deepEqual(result.effectiveIssueProviders, ['JIRA']);
  });

  it('warns when runtime reports providers or task types outside Core authorization scope', () => {
    const result = deriveRuntimeDiagnostics({
      profile: approvedProfile,
      runtimeCapabilityItems: [
        { agentId: 'openclaw-agent-001', capabilityKind: 'taskType', capabilityValue: 'GENERIC_ANALYSIS' },
        { agentId: 'openclaw-agent-001', capabilityKind: 'issueProvider', capabilityValue: 'GITLAB' }
      ]
    });

    assert.equal(result.status, 'WARN');
    assert.equal(result.issues.some((issue) => issue.code === 'UNAPPROVED_TASK_TYPE_REPORTED'), true);
    assert.equal(result.issues.some((issue) => issue.code === 'UNAPPROVED_PROVIDER_REPORTED'), true);
  });

  it('blocks dispatch diagnostics when Core disables profile or runtime is draining', () => {
    const result = deriveRuntimeDiagnostics({
      profile: { ...approvedProfile, enabled: false },
      runtimeLoad: { agentId: 'openclaw-agent-001', draining: true, availableSlots: 1 }
    });

    assert.equal(result.status, 'BLOCKED');
    assert.equal(result.issues.some((issue) => issue.code === 'DISABLED_BY_CORE'), true);
    assert.equal(result.issues.some((issue) => issue.code === 'AGENT_DRAINING'), true);
  });
});
