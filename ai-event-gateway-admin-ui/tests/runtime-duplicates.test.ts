import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { deriveAgentRuntimeWarning, mergeAgentDashboardRows } from '../lib/dashboard/agentMerge';

describe('agent runtime duplicate detection', () => {
  it('keeps all runtime sessions and flags duplicate connected sessions', () => {
    const rows = mergeAgentDashboardRows(
      [{ agentId: 'agent-001', approvalStatus: 'APPROVED', enabled: true, riskStatus: 'NORMAL' }],
      [
        { agentId: 'agent-001', connected: true, gatewayNodeId: 'gateway-node-001', sessionId: 'ws-001' },
        { agentId: 'agent-001', connected: true, gatewayNodeId: 'gateway-node-002', sessionId: 'ws-002' }
      ]
    );

    assert.equal(rows.length, 1);
    assert.equal(rows[0].runtimes?.length, 2);
    assert.equal(rows[0].runtimeSummary?.connectedCount, 2);
    assert.equal(rows[0].runtimeSummary?.duplicateRuntimeDetected, true);
    assert.deepEqual(rows[0].runtimeSummary?.connectedGatewayNodeIds, ['gateway-node-001', 'gateway-node-002']);
    assert.match(deriveAgentRuntimeWarning(rows[0]) ?? '', /Duplicate runtime sessions detected/);
  });

  it('does not flag duplicate when only one session is connected', () => {
    const rows = mergeAgentDashboardRows(
      [{ agentId: 'agent-001', approvalStatus: 'APPROVED', enabled: true, riskStatus: 'NORMAL' }],
      [
        { agentId: 'agent-001', connected: true, gatewayNodeId: 'gateway-node-001', sessionId: 'ws-001' },
        { agentId: 'agent-001', connected: false, gatewayNodeId: 'gateway-node-002', sessionId: 'ws-old' }
      ]
    );

    assert.equal(rows[0].runtimeSummary?.sessionCount, 2);
    assert.equal(rows[0].runtimeSummary?.connectedCount, 1);
    assert.equal(rows[0].runtimeSummary?.duplicateRuntimeDetected, false);
  });
});
