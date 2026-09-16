import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { callbackTruthPrinciples, callbackTruthSummary, gatewayDiagnosticsDisclaimer } from '../lib/runtime/callbackTruth';
import { correlateGatewayNodeTasks, gatewayTasksFromDeliveryRuntime, gatewayTelemetryMissingEmptyText, nodeTaskCorrelationLabel } from '../lib/cluster/nodeTaskCorrelation';

describe('Phase 3H-P0 callback truth and gateway diagnostics boundaries', () => {
  it('prefers Gateway node runtime telemetry when available but labels it diagnostics only', () => {
    const result = correlateGatewayNodeTasks({
      nodeId: 'gateway-node-001',
      nettyTasks: [{
        taskId: 'netty-task-1',
        traceId: 'trace-1',
        status: 'DISPATCHED',
        ownerNodeId: 'gateway-node-001',
        assignedNodeId: 'gateway-node-001',
        assignedAgentId: 'agent-1',
        createdAt: '2026-07-01T00:00:00.000Z',
        retryCount: 0
      }],
      coreTasks: [{ taskId: 'core-task-1', status: 'COMPLETED', createdAt: '2026-07-01T00:01:00.000Z' }],
      nodeCount: 1
    });

    assert.equal(result.source, 'NETTY_NODE');
    assert.equal(result.truthSource, 'GATEWAY_RUNTIME_DIAGNOSTICS');
    assert.equal(result.telemetryMissing, false);
    assert.equal(result.tasks[0]?.taskId, 'netty-task-1');
    assert.match(result.reason, /diagnostics|not the authoritative/i);
  });

  it('does not mix Core recent tasks into Gateway node diagnostics in single-node mode', () => {
    const result = correlateGatewayNodeTasks({
      nodeId: 'gateway-node-001',
      nettyTasks: [],
      coreTasks: [
        {
          taskId: 'core-task-1',
          traceId: 'trace-core-1',
          status: 'DISPATCHED',
          assignedAgentId: 'agent-1',
          dispatchAttemptCount: 2,
          dispatchDeliveryStatus: 'DELIVERED_TO_GATEWAY',
          createdAt: '2026-07-01T00:00:00.000Z'
        }
      ],
      nodeCount: 1
    });

    assert.equal(result.source, 'TELEMETRY_MISSING');
    assert.equal(result.telemetryMissing, true);
    assert.equal(result.coreRecentTasksCount, 1);
    assert.equal(result.tasks.length, 0);
    assert.match(result.reason, /Core.*Task Detail.*Dispatch Ledger.*Callback Inbox/i);
    assert.match(gatewayTelemetryMissingEmptyText(result), /must not be mixed/i);
  });

  it('does not infer Gateway node deliveries from Agent ownership in cluster mode', () => {
    const result = correlateGatewayNodeTasks({
      nodeId: 'gateway-node-002',
      nettyTasks: [],
      coreTasks: [
        { taskId: 'task-owned-by-agent', status: 'RUNNING', assignedAgentId: 'agent-on-node-2', createdAt: '2026-07-01T00:00:00.000Z' }
      ],
      nodeCount: 3,
      agentIds: ['agent-on-node-2']
    });

    assert.equal(result.source, 'TELEMETRY_MISSING');
    assert.equal(result.tasks.length, 0);
    assert.equal(result.agentOwnershipHintCount, 1);
    assert.equal(nodeTaskCorrelationLabel(result.source), 'Gateway telemetry missing');
  });


  it('converts Netty runtime delivery history into node diagnostics rows', () => {
    const tasks = gatewayTasksFromDeliveryRuntime({
      history: {
        records: [
          {
            attemptId: 'delivery-1',
            commandId: 'cmd-1',
            traceId: 'trace-delivery-1',
            agentId: 'agent-1',
            gatewayNodeId: 'gateway-node-001',
            messageType: 'TASK_DISPATCH',
            taskId: 'task-delivered-1',
            assignmentId: 'assignment-1',
            dispatchRequestId: 'dispatch-1',
            attemptNo: 1,
            deliveryStatus: 'DELIVERED',
            requestedAt: '2026-07-01T00:00:00.000Z',
            completedAt: '2026-07-01T00:00:01.000Z',
            durationMillis: 1000,
            message: 'Delivered to local TCP session'
          }
        ]
      }
    }, 'gateway-node-001');

    assert.equal(tasks.length, 1);
    assert.equal(tasks[0]?.taskId, 'task-delivered-1');
    assert.equal(tasks[0]?.status, 'DISPATCHED');
    assert.equal(tasks[0]?.ownerNodeId, 'gateway-node-001');
    assert.equal(tasks[0]?.assignedAgentId, 'agent-1');
  });

  it('documents Core DB / Dispatch Ledger / Callback Inbox as authoritative and Gateway telemetry as non-authoritative', () => {
    const principles = callbackTruthPrinciples();
    assert.equal(principles.filter((principle) => principle.authoritative).length, 3);
    assert.equal(principles.find((principle) => principle.layer === 'GATEWAY_DIAGNOSTICS')?.authoritative, false);
    assert.match(callbackTruthSummary(), /Core DB.*Dispatch Attempt Ledger.*Callback Inbox/i);
    assert.match(gatewayDiagnosticsDisclaimer(), /cluster.*single|single.*cluster/i);
  });
});
