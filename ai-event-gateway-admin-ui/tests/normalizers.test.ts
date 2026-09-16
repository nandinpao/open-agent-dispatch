import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  normalizeClusterNodeDetail,
  normalizeGatewayEventRecord,
  normalizeGatewayTaskDetail
} from '../lib/api/adminApi';
import { parseAdminWebSocketMessage } from '../lib/websocket/message';

describe('admin API normalizers', () => {
  it('normalizes cluster node detail arrays instead of trusting raw backend shapes', () => {
    const detail = normalizeClusterNodeDetail({
      id: 'node-a',
      ip: '10.0.0.10',
      port: '18080',
      state: 'online',
      agentList: [
        {
          id: 'agent-1',
          type: 'openclaw',
          state: 'busy',
          gatewayNodeId: 'node-a',
          connectionType: 'websocket'
        }
      ],
      tasks: [
        {
          id: 'task-1',
          state: 'completed',
          agent_id: 'agent-1',
          createdAt: '2026-06-03T00:00:00.000Z'
        }
      ]
    });

    assert.equal(detail.nodeId, 'node-a');
    assert.equal(detail.status, 'ONLINE');
    assert.equal(detail.agents.length, 1);
    assert.equal(detail.agents[0].agentId, 'agent-1');
    assert.equal(detail.agents[0].agentType, 'OPENCLAW');
    assert.equal(detail.agents[0].status, 'BUSY');
    assert.equal(detail.recentTasks.length, 1);
    assert.equal(detail.recentTasks[0].taskId, 'task-1');
    assert.equal(detail.recentTasks[0].status, 'COMPLETED');
  });

  it('returns empty task detail arrays when backend omits attempts and logs', () => {
    const detail = normalizeGatewayTaskDetail({
      task_id: 'task-2',
      state: 'processing',
      trace_id: 'trace-2'
    });

    assert.equal(detail.taskId, 'task-2');
    assert.equal(detail.status, 'PROCESSING');
    assert.deepEqual(detail.attempts, []);
    assert.deepEqual(detail.logs, []);
    assert.deepEqual(detail.trace.steps, []);
  });

  it('keeps unknown event statuses visible instead of converting them to RECEIVED', () => {
    const event = normalizeGatewayEventRecord({
      eventId: 'event-1',
      state: 'dropped',
      source: 'MES',
      type: 'QUALITY_ALERT'
    });

    assert.equal(event.status, 'UNKNOWN');
  });
});

describe('admin websocket parser', () => {
  it('maps task cancelled websocket messages to TASK_CANCELLED', () => {
    const parsed = parseAdminWebSocketMessage(JSON.stringify({
      type: 'TASK_CANCELLED',
      taskId: 'task-3',
      timestamp: '2026-06-03T01:00:00.000Z'
    }));

    assert.equal(parsed.kind, 'event');
    if (parsed.kind === 'event') {
      assert.equal(parsed.event.eventType, 'TASK_CANCELLED');
      assert.equal(parsed.event.taskId, 'task-3');
    }
  });

  it('maps P3A runtime websocket messages to dedicated runtime event types', () => {
    const parsed = parseAdminWebSocketMessage(JSON.stringify({
      eventType: 'agent.authorization.denied',
      gatewayNodeId: 'gateway-node-001',
      agentId: 'agent-9',
      occurredAt: '2026-06-15T01:00:00.000Z',
      payload: { reason: 'AGENT_NOT_APPROVED' }
    }));

    assert.equal(parsed.kind, 'event');
    if (parsed.kind === 'event') {
      assert.equal(parsed.event.eventType, 'AGENT_AUTHORIZATION_DENIED');
      assert.equal(parsed.event.nodeId, 'gateway-node-001');
      assert.equal(parsed.event.agentId, 'agent-9');
    }
  });
});

describe('admin API contract validators', () => {
  it('detects invalid normalized task contracts before UI renders unsafe arrays', async () => {
    const { validateGatewayTaskDetail } = await import('../lib/api/contracts');
    const issues = validateGatewayTaskDetail({
      taskId: '',
      traceId: 'trace-1',
      status: 'PROCESSING',
      createdAt: '2026-06-03T00:00:00.000Z',
      retryCount: 0,
      trace: {
        traceId: 'trace-1',
        status: 'PROCESSING',
        startedAt: '2026-06-03T00:00:00.000Z',
        steps: []
      },
      attempts: [],
      logs: []
    });

    assert.equal(issues.some((issue) => issue.path === 'taskId'), true);
  });
});

describe('cluster P3.8 API compatibility', () => {
  it('normalizes runtimeMetrics and DISCOVERED status from /api/cluster/nodes as online health', () => {
    const detail = normalizeClusterNodeDetail({
      nodeId: 'gateway-node-002',
      status: 'DISCOVERED',
      host: '192.168.1.102',
      ports: {
        tcp: 19090,
        websocket: 18080,
        admin: 18080
      },
      runtimeMetrics: {
        cpuUsagePercent: 0.8,
        memoryUsedMb: 119,
        memoryMaxMb: 768,
        memoryUsedPercent: 15.5,
        nettyEventLoopThreads: 9,
        workerThreads: 41,
        queueSize: 2,
        activeTaskCount: 3,
        inboundEventsPerMinute: 10,
        routedEventsPerMinute: 9,
        failedEventsPerMinute: 1
      },
      heartbeat: {
        lastAt: '2026-06-03T04:00:00.000Z'
      },
      discovery: {
        mode: 'STATIC',
        lastDiscoveryAt: '2026-06-03T03:59:00.000Z'
      },
      runtime: {
        startedAt: '2026-06-03T02:00:00.000Z',
        uptimeSeconds: 7200
      }
    });

    assert.equal(detail.status, 'ONLINE');
    assert.equal(detail.tcpPort, 19090);
    assert.equal(detail.websocketPort, 18080);
    assert.equal(detail.adminPort, 18080);
    assert.equal(detail.discoveryMode, 'STATIC');
    assert.equal(detail.lastHeartbeatAt, '2026-06-03T04:00:00.000Z');
    assert.equal(detail.lastDiscoveryAt, '2026-06-03T03:59:00.000Z');
    assert.equal(detail.startedAt, '2026-06-03T02:00:00.000Z');
    assert.equal(detail.uptimeSeconds, 7200);
    assert.equal(detail.metrics.cpuUsagePercent, 0.8);
    assert.equal(detail.metrics.memoryUsedMb, 119);
    assert.equal(detail.metrics.memoryMaxMb, 768);
    assert.equal(detail.metrics.memoryUsedPercent, 15.5);
    assert.equal(detail.metrics.nettyEventLoopThreads, 9);
    assert.equal(detail.metrics.workerThreads, 41);
    assert.equal(detail.metrics.queueSize, 2);
    assert.equal(detail.metrics.activeTaskCount, 3);
  });

  it('maps SELF to online health and marks the current request handling node', () => {
    const detail = normalizeClusterNodeDetail({
      nodeId: 'gateway-node-001',
      host: 'gateway-node-001',
      status: 'SELF',
      self: true,
      runtimeMetrics: {
        cpuUsagePercent: 0.1,
        memoryUsedMb: 70,
        memoryMaxMb: 5880,
        memoryUsedPercent: 1.2,
        workerThreads: 140,
        timestamp: '2026-06-03T14:47:21.420530877+08:00'
      }
    });

    assert.equal(detail.status, 'ONLINE');
    assert.equal(detail.sourceStatus, 'SELF');
    assert.equal(detail.isCurrentRequestNode, true);
    assert.equal(detail.metrics.memoryUsedPercent, 1.2);
    assert.equal(detail.metrics.workerThreads, 140);
  });

  it('uses syncStatus for remote state health instead of remote snapshot SELF', () => {
    const detail = normalizeClusterNodeDetail({
      nodeId: 'gateway-node-002',
      host: 'gateway-node-002',
      status: 'SELF',
      sourceStatus: 'SELF',
      syncStatus: 'SYNCED',
      isCurrentRequestNode: false
    });

    assert.equal(detail.status, 'ONLINE');
    assert.equal(detail.sourceStatus, 'SELF');
    assert.equal(detail.syncStatus, 'SYNCED');
    assert.equal(detail.isCurrentRequestNode, false);
  });
});

describe('cluster P3.9 peer relation / heartbeat compatibility', () => {
  it('normalizes backend /api/cluster/peers response', async () => {
    const { normalizeClusterPeersResponse } = await import('../lib/api/adminApi');
    const response = normalizeClusterPeersResponse({
      localNodeId: 'gateway-node-001',
      generatedAt: '2026-06-03T14:47:21.420530877+08:00',
      peers: [
        {
          nodeId: 'gateway-node-002',
          relation: 'STATIC_SEED',
          syncStatus: 'SYNCED',
          heartbeatStatus: 'OK',
          lastSyncAt: '2026-06-03T14:46:09.718469802+08:00',
          lastHeartbeatAt: '2026-06-03T14:47:21.420530877+08:00',
          heartbeatLatencyMs: 2,
          missedHeartbeatCount: 0,
          lastError: null
        }
      ]
    });

    assert.equal(response.localNodeId, 'gateway-node-001');
    assert.equal(response.peers.length, 1);
    assert.equal(response.peers[0].nodeId, 'gateway-node-002');
    assert.equal(response.peers[0].relation, 'STATIC_SEED');
    assert.equal(response.peers[0].syncStatus, 'SYNCED');
    assert.equal(response.peers[0].heartbeatStatus, 'OK');
    assert.equal(response.peers[0].status, 'ONLINE');
    assert.equal(response.peers[0].heartbeatLatencyMs, 2);
    assert.equal(response.peers[0].missedHeartbeatCount, 0);
  });

  it('normalizes warning and lost peer heartbeat health', async () => {
    const { normalizeClusterPeersResponse } = await import('../lib/api/adminApi');
    const response = normalizeClusterPeersResponse({
      localNodeId: 'gateway-node-001',
      peers: [
        { nodeId: 'gateway-node-002', syncStatus: 'STALE', heartbeatStatus: 'WARNING', relation: 'STATIC_SEED' },
        { nodeId: 'gateway-node-003', syncStatus: 'FAILED', heartbeatStatus: 'LOST', relation: 'STATIC_SEED', missedHeartbeatCount: 3 }
      ]
    });

    assert.equal(response.peers[0].status, 'DEGRADED');
    assert.equal(response.peers[0].heartbeatStatus, 'WARNING');
    assert.equal(response.peers[1].status, 'OFFLINE');
    assert.equal(response.peers[1].heartbeatStatus, 'LOST');
    assert.equal(response.peers[1].missedHeartbeatCount, 3);
  });
});

describe('cluster P4 agent / task aggregation compatibility', () => {
  it('normalizes cluster-wide agents by node', async () => {
    const { normalizeClusterAgentsResponse } = await import('../lib/api/adminApi');
    const response = normalizeClusterAgentsResponse({
      localNodeId: 'gateway-node-001',
      agentsByNode: {
        'gateway-node-001': [
          { agentId: 'agent-node1-001', status: 'IDLE', agentType: 'OPENCLAW', connectionType: 'TCP' }
        ],
        'gateway-node-002': [
          { agentId: 'agent-node2-001', status: 'BUSY', agentType: 'HERMES', connectionType: 'WEBSOCKET' }
        ]
      }
    });

    assert.equal(response.scope, 'CLUSTER');
    assert.equal(response.items.length, 2);
    assert.equal(response.byNode['gateway-node-001'][0].nodeId, 'gateway-node-001');
    assert.equal(response.byNode['gateway-node-001'][0].ownerNodeId, 'gateway-node-001');
    assert.equal(response.byNode['gateway-node-002'][0].nodeId, 'gateway-node-002');
    assert.equal(response.byNode['gateway-node-002'][0].ownerNodeId, 'gateway-node-002');
    assert.equal(response.byNode['gateway-node-002'][0].status, 'BUSY');
  });

  it('normalizes cluster-wide tasks by node', async () => {
    const { normalizeClusterTasksResponse } = await import('../lib/api/adminApi');
    const response = normalizeClusterTasksResponse({
      localNodeId: 'gateway-node-001',
      tasksByNode: {
        'gateway-node-001': [
          { taskId: 'task-node1-001', status: 'COMPLETED', assignedAgentId: 'agent-node1-001', createdAt: '2026-06-03T00:00:00.000Z' }
        ],
        'gateway-node-003': [
          { taskId: 'task-node3-001', state: 'processing', agentId: 'agent-node3-001', createdAt: '2026-06-03T00:01:00.000Z' }
        ]
      }
    });

    assert.equal(response.scope, 'CLUSTER');
    assert.equal(response.items.length, 2);
    assert.equal(response.byNode['gateway-node-001'][0].ownerNodeId, 'gateway-node-001');
    assert.equal(response.byNode['gateway-node-001'][0].assignedNodeId, 'gateway-node-001');
    assert.equal(response.byNode['gateway-node-003'][0].ownerNodeId, 'gateway-node-003');
    assert.equal(response.byNode['gateway-node-003'][0].assignedNodeId, 'gateway-node-003');
    assert.equal(response.byNode['gateway-node-003'][0].status, 'PROCESSING');
  });

  it('supports direct by-node maps for agents and local fallback scope', async () => {
    const { normalizeClusterAgentsResponse } = await import('../lib/api/adminApi');
    const response = normalizeClusterAgentsResponse({
      'gateway-node-002': [
        { id: 'agent-node2-002', state: 'connected', type: 'custom' }
      ]
    }, 'LOCAL', 'gateway-node-001', 'fallback');

    assert.equal(response.scope, 'LOCAL');
    assert.equal(response.fallbackReason, 'fallback');
    assert.equal(response.byNode['gateway-node-002'][0].nodeId, 'gateway-node-002');
    assert.equal(response.byNode['gateway-node-002'][0].ownerNodeId, 'gateway-node-002');
  });
});

describe('P0 Core/Admin runtime-view contract normalization', () => {
  it('normalizes Core /admin/agents/runtime-view wrapper rows into flat Core profiles', async () => {
    const { normalizeCoreAgentRuntimeViewPayload } = await import('../lib/api/coreAdminApi');
    const profiles = normalizeCoreAgentRuntimeViewPayload([
      {
        profile: {
          agentId: 'agent-core-001',
          approvalStatus: 'APPROVED',
          enabled: true,
          riskStatus: 'NORMAL'
        },
        directory: {
          agentId: 'agent-core-001',
          status: 'ONLINE'
        },
        coreGovernanceAssignable: true,
        coreDirectoryAssignable: true,
        sourceOfTruth: 'CORE_PROFILE_PLUS_CORE_DIRECTORY'
      }
    ]);

    assert.equal(profiles.length, 1);
    assert.equal(profiles[0].agentId, 'agent-core-001');
    assert.equal(profiles[0].approvalStatus, 'APPROVED');
  });

  it('normalizes Core /admin/tasks/runtime-view snapshot wrapper into task rows', async () => {
    const { normalizeCoreTaskRuntimeViewPayload } = await import('../lib/api/coreAdminApi');
    const tasks = normalizeCoreTaskRuntimeViewPayload({
      tasks: [
        {
          taskId: 'task-001',
          status: 'ASSIGNED',
          taskType: 'MES_ERROR_ANALYSIS',
          requiredCapabilities: ['mes-analysis'],
          createdAt: '2026-06-16T01:00:00Z',
          updatedAt: '2026-06-16T01:01:00Z'
        }
      ],
      dispatchRequests: [
        {
          dispatchRequestId: 'dispatch-001',
          taskId: 'task-001',
          agentId: 'agent-core-001',
          status: 'DELIVERING',
          createdAt: '2026-06-16T01:02:00Z'
        }
      ],
      callbacks: [],
      tasksByStatus: { ASSIGNED: 1 },
      dispatchByStatus: { DELIVERING: 1 },
      generatedAt: '2026-06-16T01:03:00Z'
    });

    assert.equal(tasks.length, 1);
    assert.equal(tasks[0].taskId, 'task-001');
    assert.equal(tasks[0].dispatchRequestId, 'dispatch-001');
    assert.equal(tasks[0].assignedAgentId, 'agent-core-001');
    assert.equal(tasks[0].dispatchStatus, 'DELIVERING');
  });
});

describe('P1 Core/Admin runtime-view dispatch error summary normalization', () => {
  it('normalizes latest routing decisions into task rows with structured user-facing errors', async () => {
    const { normalizeCoreTaskRuntimeViewPayload } = await import('../lib/api/coreAdminApi');
    const tasks = normalizeCoreTaskRuntimeViewPayload({
      tasks: [
        {
          taskId: 'task-routing-001',
          status: 'CREATED',
          taskType: 'MES_ERROR_ANALYSIS',
          createdAt: '2026-07-05T01:00:00Z'
        }
      ],
      dispatchRequests: [],
      latestRoutingDecisions: {
        'task-routing-001': {
          decisionId: 'decision-001',
          taskId: 'task-routing-001',
          status: 'NO_CANDIDATE',
          decisionReason: 'DISPATCH_AGENT_NO_CAPACITY: Agent capacity is full 下一步：Start more agents Technical details: {slots=0}',
          userFacingError: {
            code: 'DISPATCH_AGENT_NO_CAPACITY',
            message: 'Agent capacity is full',
            nextAction: 'Start more agents',
            severity: 'HIGH',
            technicalDetails: { availableSlots: 0 }
          }
        }
      },
      generatedAt: '2026-07-05T01:01:00Z'
    });

    assert.equal(tasks.length, 1);
    assert.equal(tasks[0].latestRoutingDecision?.decisionId, 'decision-001');
    assert.equal(tasks[0].userFacingDispatchError?.code, 'DISPATCH_AGENT_NO_CAPACITY');
    assert.equal(tasks[0].userFacingDispatchError?.nextAction, 'Start more agents');
    assert.deepEqual((tasks[0].payload as Record<string, unknown>).userFacingDispatchError, tasks[0].userFacingDispatchError);
  });

  it('normalizes single task runtime-view latestRoutingDecision payload', async () => {
    const { normalizeCoreTaskRuntimeViewPayload } = await import('../lib/api/coreAdminApi');
    const tasks = normalizeCoreTaskRuntimeViewPayload({
      task: {
        taskId: 'task-detail-001',
        status: 'CREATED',
        taskType: 'MES_ERROR_ANALYSIS'
      },
      dispatchRequests: [],
      latestRoutingDecision: {
        decisionId: 'decision-detail-001',
        taskId: 'task-detail-001',
        status: 'NO_CANDIDATE',
        userFacingError: {
          code: 'DISPATCH_PROFILE_NOT_CONFIGURED',
          message: 'No profile configured',
          nextAction: 'Create an assignment profile'
        }
      }
    });

    assert.equal(tasks.length, 1);
    assert.equal(tasks[0].latestRoutingDecision?.decisionId, 'decision-detail-001');
    assert.equal(tasks[0].userFacingDispatchError?.code, 'DISPATCH_PROFILE_NOT_CONFIGURED');
  });
});

describe('P1-4 task reason semantics normalization', () => {
  it('keeps retry-wait reasons out of terminal failureReason', async () => {
    const { normalizeCoreTaskRuntimeViewPayload } = await import('../lib/api/coreAdminApi');
    const tasks = normalizeCoreTaskRuntimeViewPayload({
      tasks: [
        {
          taskId: 'task-retry-wait-001',
          status: 'RETRY_WAIT',
          taskType: 'MES_ERROR_ANALYSIS',
          dispatchRetryReason: 'DISPATCH_DELAYED_NO_ELIGIBLE_AGENT: Waiting for eligible agent 下一步：Wait for retry',
          nextDispatchAttemptAt: '2026-07-05T01:10:00Z'
        }
      ],
      dispatchRequests: [],
      generatedAt: '2026-07-05T01:01:00Z'
    });

    assert.equal(tasks[0].reasonCategory, 'WAITING_RETRY');
    assert.equal(tasks[0].failureReason, undefined);
    assert.equal(tasks[0].dispatchWaitReason?.startsWith('DISPATCH_DELAYED_NO_ELIGIBLE_AGENT'), true);
  });

  it('classifies structured routing errors as dispatch-blocked before terminal failure', async () => {
    const { normalizeCoreTaskRuntimeViewPayload } = await import('../lib/api/coreAdminApi');
    const tasks = normalizeCoreTaskRuntimeViewPayload({
      tasks: [
        {
          taskId: 'task-blocked-001',
          status: 'CREATED',
          taskType: 'MES_ERROR_ANALYSIS'
        }
      ],
      dispatchRequests: [],
      latestRoutingDecisions: {
        'task-blocked-001': {
          decisionId: 'decision-blocked-001',
          taskId: 'task-blocked-001',
          status: 'NO_CANDIDATE',
          userFacingError: {
            code: 'DISPATCH_AGENT_PROFILE_MISSING',
            message: 'Agent qualification is missing',
            nextAction: 'Approve agent qualification'
          }
        }
      }
    });

    assert.equal(tasks[0].reasonCategory, 'DISPATCH_BLOCKED');
    assert.equal(tasks[0].blockedReason, 'DISPATCH_AGENT_PROFILE_MISSING');
    assert.equal(tasks[0].failureReason, undefined);
  });
});
