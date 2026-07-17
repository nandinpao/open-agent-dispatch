import type {
  AdminWebSocketEvent,
  AgentDetail,
  AgentErrorLog,
  AgentInfo,
  ClusterNode,
  ClusterNodeDetail,
  ClusterTopology,
  CommandResult,
  GatewayEventDetail,
  GatewayEventRecord,
  GatewayHealth,
  GatewayMetrics,
  GatewayTaskDetail,
  GatewayTaskRecord,
  RetryAttempt,
  TaskLogRecord,
  TraceDetail,
  TraceStep
} from '@/lib/types/admin';

export const mockGatewayHealth: GatewayHealth = {
  status: 'RUNNING',
  nodeId: 'gateway-node-01',
  version: '0.5.0-dev',
  uptimeSeconds: 86420,
  activeConnections: 42,
  connectedAgents: 8,
  pendingTasks: 5,
  failedTasks: 1,
  timestamp: new Date().toISOString()
};

export const mockGatewayMetrics: GatewayMetrics = {
  nodeId: 'gateway-node-01',
  cpuUsagePercent: 28.6,
  memoryUsedMb: 768,
  memoryMaxMb: 2048,
  nettyEventLoopThreads: 8,
  workerThreads: 16,
  queueSize: 5,
  inboundEventsPerMinute: 128,
  routedEventsPerMinute: 124,
  failedEventsPerMinute: 1,
  timestamp: new Date().toISOString()
};

export const mockClusterNodes: ClusterNode[] = [
  {
    nodeId: 'gateway-node-01',
    host: '192.168.10.11',
    tcpPort: 17070,
    websocketPort: 18081,
    adminPort: 18080,
    status: 'ONLINE',
    agentCount: 5,
    lastHeartbeatAt: new Date().toISOString(),
    discoveryMode: 'UDP',
    role: 'LEADER',
    drainStatus: 'ACTIVE',
    acceptsNewTasks: true
  },
  {
    nodeId: 'gateway-node-02',
    host: '192.168.10.12',
    tcpPort: 17070,
    websocketPort: 18081,
    adminPort: 18080,
    status: 'ONLINE',
    agentCount: 3,
    lastHeartbeatAt: new Date().toISOString(),
    discoveryMode: 'UDP',
    role: 'FOLLOWER',
    drainStatus: 'DRAINING',
    acceptsNewTasks: false
  },
  {
    nodeId: 'gateway-node-03',
    host: '192.168.10.13',
    tcpPort: 17070,
    websocketPort: 18081,
    adminPort: 18080,
    status: 'OFFLINE',
    agentCount: 0,
    lastHeartbeatAt: '2026-05-28T09:45:00+08:00',
    discoveryMode: 'DOCKER',
    role: 'FOLLOWER',
    drainStatus: 'MAINTENANCE',
    acceptsNewTasks: false
  }
];

export const mockAgents: AgentInfo[] = [
  {
    agentId: 'openclaw-agent-001',
    agentType: 'OPENCLAW',
    status: 'BUSY',
    nodeId: 'gateway-node-01',
    transport: 'WEBSOCKET',
    connectedAt: '2026-05-28T09:10:00+08:00',
    lastSeenAt: new Date().toISOString(),
    currentTaskId: 'task-20260528-0001',
    errorCount: 0
  },
  {
    agentId: 'hermes-agent-001',
    agentType: 'HERMES',
    status: 'IDLE',
    nodeId: 'gateway-node-01',
    transport: 'WEBSOCKET',
    connectedAt: '2026-05-28T09:20:00+08:00',
    lastSeenAt: new Date().toISOString(),
    errorCount: 1
  },
  {
    agentId: 'tao-agent-001',
    agentType: 'TAO',
    status: 'CONNECTED',
    nodeId: 'gateway-node-02',
    transport: 'TCP',
    connectedAt: '2026-05-28T09:25:00+08:00',
    lastSeenAt: new Date().toISOString(),
    errorCount: 0
  },
  {
    agentId: 'custom-agent-qa-001',
    agentType: 'CUSTOM',
    status: 'ERROR',
    nodeId: 'gateway-node-02',
    transport: 'HTTP_CALLBACK',
    connectedAt: '2026-05-28T08:55:00+08:00',
    lastSeenAt: '2026-05-28T10:01:00+08:00',
    errorCount: 4
  }
];

export const mockEvents: GatewayEventRecord[] = [
  {
    eventId: 'evt-20260528-000001',
    traceId: 'trace-20260528-000001',
    sourceSystem: 'MES',
    eventType: 'QUALITY_EXCEPTION',
    status: 'ROUTED',
    receivedAt: '2026-05-28T10:05:00+08:00',
    routedAt: '2026-05-28T10:05:02+08:00',
    message: 'MES quality exception routed to OpenClaw agent',
    payload: { lotNo: 'LOT-20260528-001', station: 'AOI', defectCode: 'D102' }
  },
  {
    eventId: 'evt-20260528-000002',
    traceId: 'trace-20260528-000002',
    sourceSystem: 'ERP',
    eventType: 'PURCHASE_ORDER_RISK',
    status: 'RECEIVED',
    receivedAt: '2026-05-28T10:08:00+08:00',
    message: 'Waiting for routing decision',
    payload: { poNo: 'PO-20260528-0099', vendor: 'V-1024' }
  },
  {
    eventId: 'evt-20260528-000003',
    traceId: 'trace-20260528-000003',
    sourceSystem: 'BPM',
    eventType: 'APPROVAL_TIMEOUT',
    status: 'FAILED',
    receivedAt: '2026-05-28T10:11:00+08:00',
    failedAt: '2026-05-28T10:11:03+08:00',
    message: 'No eligible agent capability found',
    payload: { formNo: 'BPM-20260528-7788', ownerDept: 'IT' }
  }
];

export const mockTasks: GatewayTaskRecord[] = [
  {
    taskId: 'task-20260528-0001',
    traceId: 'trace-20260528-000001',
    status: 'PROCESSING',
    assignedAgentId: 'openclaw-agent-001',
    assignedNodeId: 'gateway-node-01',
    createdAt: '2026-05-28T10:05:02+08:00',
    assignedAt: '2026-05-28T10:05:03+08:00',
    startedAt: '2026-05-28T10:05:04+08:00',
    retryCount: 0,
    requestPayload: { action: 'ANALYZE_QUALITY_EXCEPTION', traceId: 'trace-20260528-000001' }
  },
  {
    taskId: 'task-20260528-0002',
    traceId: 'trace-20260528-000002',
    status: 'PENDING',
    assignedAgentId: 'hermes-agent-001',
    assignedNodeId: 'gateway-node-01',
    createdAt: '2026-05-28T10:08:02+08:00',
    assignedAt: '2026-05-28T10:08:04+08:00',
    retryCount: 0,
    requestPayload: { action: 'CHECK_PO_RISK', traceId: 'trace-20260528-000002' }
  },
  {
    taskId: 'task-20260528-0003',
    traceId: 'trace-20260528-000003',
    status: 'FAILED',
    assignedAgentId: 'custom-agent-qa-001',
    assignedNodeId: 'gateway-node-02',
    createdAt: '2026-05-28T10:10:00+08:00',
    assignedAt: '2026-05-28T10:10:01+08:00',
    startedAt: '2026-05-28T10:10:02+08:00',
    failedAt: '2026-05-28T10:10:08+08:00',
    durationMs: 6000,
    retryCount: 1,
    failureReason: 'Agent returned timeout error',
    requestPayload: { action: 'CHECK_APPROVAL_TIMEOUT', traceId: 'trace-20260528-000003' }
  },
  {
    taskId: 'task-20260528-0004',
    traceId: 'trace-20260528-000004',
    status: 'COMPLETED',
    assignedAgentId: 'tao-agent-001',
    assignedNodeId: 'gateway-node-02',
    createdAt: '2026-05-28T09:50:00+08:00',
    assignedAt: '2026-05-28T09:50:01+08:00',
    startedAt: '2026-05-28T09:50:03+08:00',
    completedAt: '2026-05-28T09:50:09+08:00',
    durationMs: 6000,
    retryCount: 0,
    requestPayload: { action: 'SYNC_TOOL_STATUS', traceId: 'trace-20260528-000004' },
    responsePayload: { result: 'OK' }
  }
];

export const mockAgentErrorLogs: AgentErrorLog[] = [
  {
    logId: 'log-custom-001',
    agentId: 'custom-agent-qa-001',
    timestamp: '2026-05-28T10:10:08+08:00',
    level: 'ERROR',
    taskId: 'task-20260528-0003',
    traceId: 'trace-20260528-000003',
    message: 'Agent returned timeout error',
    detail: { timeoutMs: 6000, phase: 'tool-call' }
  },
  {
    logId: 'log-hermes-001',
    agentId: 'hermes-agent-001',
    timestamp: '2026-05-28T09:58:20+08:00',
    level: 'WARN',
    traceId: 'trace-20260528-000002',
    message: 'Capability risk-check latency is higher than baseline',
    detail: { latencyMs: 840 }
  }
];

const capabilityTemplates = {
  OPENCLAW: [
    {
      capabilityId: 'tool-call',
      name: 'Tool Call Dispatcher',
      description: '支援由 Gateway 派發工具呼叫請求，適合串 MCP / A2A adapter。',
      status: 'ENABLED' as const,
      version: '1.0.0',
      supportedActions: ['ANALYZE_QUALITY_EXCEPTION', 'QUERY_MCP_TOOL', 'CREATE_ISSUE']
    },
    {
      capabilityId: 'stream-status',
      name: 'Streaming Status',
      description: '支援將 Agent 執行狀態即時回報給 Netty WebSocket。',
      status: 'ENABLED' as const,
      version: '1.0.0',
      supportedActions: ['STATUS_BUSY', 'STATUS_IDLE', 'STATUS_FAILED']
    }
  ],
  HERMES: [
    {
      capabilityId: 'risk-check',
      name: 'Risk Check',
      description: '支援 ERP / 採購 / 庫存風險初判。',
      status: 'DEGRADED' as const,
      version: '0.9.5',
      supportedActions: ['CHECK_PO_RISK', 'CHECK_VENDOR_RISK']
    }
  ],
  TAO: [
    {
      capabilityId: 'tool-sync',
      name: 'Tool Sync',
      description: '同步 Agent 本地工具狀態與 Gateway Registry。',
      status: 'ENABLED' as const,
      version: '0.8.0',
      supportedActions: ['SYNC_TOOL_STATUS', 'HEALTH_CHECK']
    }
  ],
  CUSTOM: [
    {
      capabilityId: 'qa-check',
      name: 'Custom QA Check',
      description: '企業客製化品質檢查能力。',
      status: 'ENABLED' as const,
      version: '0.1.0',
      supportedActions: ['CHECK_APPROVAL_TIMEOUT', 'CHECK_CUSTOM_RULE']
    }
  ]
};

export const mockAgentDetails: AgentDetail[] = mockAgents.map((agent) => ({
  ...agent,
  displayName: `${agent.agentType} ${agent.agentId}`,
  description: `${agent.agentType} agent registered through ai-event-gateway-netty.`,
  labels: [agent.agentType.toLowerCase(), agent.transport.toLowerCase(), agent.nodeId],
  capabilities: capabilityTemplates[agent.agentType],
  runtime: {
    processId: `${agent.agentType.toLowerCase()}-${agent.agentId.split('-').at(-1)}`,
    host: agent.nodeId === 'gateway-node-01' ? 'agent-host-01' : 'agent-host-02',
    runtimeVersion: agent.agentType === 'OPENCLAW' ? 'openclaw-runtime@0.4.0' : `${agent.agentType.toLowerCase()}-runtime@0.1.0`,
    protocolVersion: 'ai-event-protocol@0.4.0',
    heartbeatIntervalMs: 30000,
    lastHeartbeatAt: agent.lastSeenAt,
    averageLatencyMs: agent.status === 'ERROR' ? 950 : 120,
    activeTaskCount: agent.currentTaskId ? 1 : 0,
    maxConcurrentTasks: agent.agentType === 'OPENCLAW' ? 4 : 2
  },
  recentErrors: mockAgentErrorLogs.filter((log) => log.agentId === agent.agentId)
}));

export function getMockAgentDetail(agentId: string): AgentDetail {
  const detail = mockAgentDetails.find((agent) => agent.agentId === agentId);
  if (!detail) {
    return {
      agentId,
      agentType: 'CUSTOM',
      status: 'OFFLINE',
      nodeId: '-',
      transport: 'WEBSOCKET',
      connectedAt: new Date().toISOString(),
      lastSeenAt: new Date().toISOString(),
      errorCount: 0,
      displayName: agentId,
      description: 'Mock fallback agent detail.',
      labels: ['mock', 'fallback'],
      capabilities: [],
      runtime: {},
      recentErrors: []
    };
  }
  return detail;
}

export function getMockAgentTasks(agentId: string): GatewayTaskRecord[] {
  return mockTasks.filter((task) => task.assignedAgentId === agentId);
}

export function getMockAgentErrors(agentId: string): AgentErrorLog[] {
  return mockAgentErrorLogs.filter((log) => log.agentId === agentId);
}

export function getMockCommandResult(message: string): CommandResult {
  return {
    success: true,
    message,
    timestamp: new Date().toISOString()
  };
}

export const mockCommandResult: CommandResult = getMockCommandResult('Mock command accepted');

export const mockAdminWebSocketEvents: AdminWebSocketEvent[] = [
  {
    eventType: 'NODE_DRAINING',
    timestamp: new Date().toISOString(),
    nodeId: 'gateway-node-02',
    status: 'ONLINE',
    message: 'Node is draining and will not accept new tasks',
    payload: { drainStatus: 'DRAINING', acceptsNewTasks: false, agentCount: 3 }
  },
  {
    eventType: 'AGENT_CONNECTED',
    timestamp: new Date().toISOString(),
    nodeId: 'gateway-node-01',
    agentId: 'openclaw-agent-001',
    status: 'CONNECTED',
    message: 'OpenClaw agent connected',
    payload: { agentType: 'OPENCLAW', transport: 'WEBSOCKET' }
  },
  {
    eventType: 'AGENT_STATUS_CHANGED',
    timestamp: new Date().toISOString(),
    nodeId: 'gateway-node-01',
    agentId: 'openclaw-agent-001',
    taskId: 'task-20260528-0001',
    traceId: 'evt-20260528-000001',
    status: 'BUSY',
    message: 'Agent started processing task',
    payload: { currentTaskId: 'task-20260528-0001' }
  },
  {
    eventType: 'TASK_ASSIGNED',
    timestamp: new Date().toISOString(),
    nodeId: 'gateway-node-01',
    agentId: 'openclaw-agent-001',
    taskId: 'task-20260528-0001',
    traceId: 'evt-20260528-000001',
    status: 'ASSIGNED',
    message: 'Task assigned to OpenClaw agent'
  },
  {
    eventType: 'TASK_COMPLETED',
    timestamp: new Date().toISOString(),
    nodeId: 'gateway-node-01',
    agentId: 'openclaw-agent-001',
    taskId: 'task-20260528-0001',
    traceId: 'evt-20260528-000001',
    status: 'COMPLETED',
    message: 'Task completed successfully',
    payload: { durationMs: 1680, responsePayload: { result: 'OK' } }
  },
  {
    eventType: 'TASK_FAILED',
    timestamp: new Date().toISOString(),
    nodeId: 'gateway-node-02',
    agentId: 'hermes-agent-001',
    taskId: 'task-20260528-0002',
    traceId: 'evt-20260528-000002',
    status: 'FAILED',
    message: 'Agent execution timeout',
    payload: { durationMs: 30000, failureReason: 'Agent execution timeout', retryCount: 1 }
  },
  {
    eventType: 'NODE_JOINED',
    timestamp: new Date().toISOString(),
    nodeId: 'gateway-node-04',
    traceId: 'cluster-20260528-000004',
    status: 'ONLINE',
    message: 'New gateway node joined cluster',
    payload: { host: '10.10.10.14', tcpPort: 19090, websocketPort: 19091, adminPort: 18083, agentCount: 0, discoveryMode: 'UDP' }
  },
  {
    eventType: 'NODE_HEARTBEAT_TIMEOUT',
    timestamp: new Date().toISOString(),
    nodeId: 'gateway-node-03',
    traceId: 'cluster-20260528-000003',
    status: 'OFFLINE',
    message: 'Cluster heartbeat timeout detected'
  },
  {
    eventType: 'AGENT_DISCONNECTED',
    timestamp: new Date().toISOString(),
    nodeId: 'gateway-node-03',
    agentId: 'tao-agent-001',
    status: 'OFFLINE',
    message: 'TAO agent disconnected'
  }
];


function buildTraceSteps(traceId: string): TraceStep[] {
  const event = mockEvents.find((item) => item.traceId === traceId);
  const tasks = mockTasks.filter((task) => task.traceId === traceId);
  const firstTask = tasks[0];
  const steps: TraceStep[] = [];

  if (event) {
    steps.push({
      stepId: `${traceId}-receive`,
      traceId,
      stage: 'EVENT_RECEIVED',
      status: event.status === 'FAILED' ? 'FAILED' : 'SUCCESS',
      actorType: 'SOURCE_SYSTEM',
      actorId: event.sourceSystem,
      timestamp: event.receivedAt,
      message: `${event.sourceSystem} sent ${event.eventType}`,
      payload: event.payload
    });
  }

  if (event?.routedAt || firstTask?.createdAt) {
    steps.push({
      stepId: `${traceId}-route`,
      traceId,
      stage: 'ROUTING_DECISION',
      status: event?.status === 'FAILED' ? 'FAILED' : 'SUCCESS',
      actorType: 'ROUTER',
      actorId: 'gateway-router',
      timestamp: event?.routedAt ?? firstTask?.createdAt ?? event?.receivedAt ?? new Date().toISOString(),
      durationMs: event?.routedAt && event.receivedAt ? new Date(event.routedAt).getTime() - new Date(event.receivedAt).getTime() : undefined,
      message: event?.status === 'FAILED' ? event.message : `Routed to ${firstTask?.assignedAgentId ?? 'pending agent'}`,
      payload: firstTask ? { assignedAgentId: firstTask.assignedAgentId, assignedNodeId: firstTask.assignedNodeId } : undefined
    });
  }

  for (const task of tasks) {
    steps.push({
      stepId: `${traceId}-${task.taskId}-created`,
      traceId,
      stage: 'TASK_CREATED',
      status: 'SUCCESS',
      actorType: 'GATEWAY',
      actorId: task.assignedNodeId,
      timestamp: task.createdAt,
      message: `Task ${task.taskId} created`,
      payload: task.requestPayload
    });

    if (task.assignedAt) {
      steps.push({
        stepId: `${traceId}-${task.taskId}-assigned`,
        traceId,
        stage: 'TASK_ASSIGNED',
        status: 'SUCCESS',
        actorType: 'CLUSTER',
        actorId: task.assignedNodeId,
        timestamp: task.assignedAt,
        message: `Assigned to ${task.assignedAgentId ?? '-'}`,
        payload: { agentId: task.assignedAgentId, nodeId: task.assignedNodeId }
      });
    }

    if (task.startedAt) {
      steps.push({
        stepId: `${traceId}-${task.taskId}-started`,
        traceId,
        stage: 'AGENT_PROCESSING',
        status: task.status === 'PROCESSING' ? 'RUNNING' : 'SUCCESS',
        actorType: 'AGENT',
        actorId: task.assignedAgentId,
        timestamp: task.startedAt,
        message: `Agent started processing ${task.taskId}`
      });
    }

    if (task.completedAt) {
      steps.push({
        stepId: `${traceId}-${task.taskId}-completed`,
        traceId,
        stage: 'TASK_COMPLETED',
        status: 'SUCCESS',
        actorType: 'AGENT',
        actorId: task.assignedAgentId,
        timestamp: task.completedAt,
        durationMs: task.durationMs,
        message: 'Task completed successfully',
        payload: task.responsePayload
      });
    }

    if (task.failedAt) {
      steps.push({
        stepId: `${traceId}-${task.taskId}-failed`,
        traceId,
        stage: 'TASK_FAILED',
        status: 'FAILED',
        actorType: 'AGENT',
        actorId: task.assignedAgentId,
        timestamp: task.failedAt,
        durationMs: task.durationMs,
        message: task.failureReason ?? 'Task failed',
        payload: { failureReason: task.failureReason, retryCount: task.retryCount }
      });
    }
  }

  return steps.sort((a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime());
}

export function getMockTraceDetail(traceId: string): TraceDetail {
  const event = mockEvents.find((item) => item.traceId === traceId);
  const tasks = mockTasks.filter((task) => task.traceId === traceId);
  const firstTask = tasks[0];
  const lastTask = tasks.at(-1);
  const steps = buildTraceSteps(traceId);
  const hasFailure = event?.status === 'FAILED' || tasks.some((task) => task.status === 'FAILED');
  const hasRunning = tasks.some((task) => task.status === 'PROCESSING');
  const startedAt = event?.receivedAt ?? firstTask?.createdAt ?? new Date().toISOString();
  const endedAt = event?.failedAt ?? lastTask?.completedAt ?? lastTask?.failedAt;

  return {
    traceId,
    eventId: event?.eventId,
    taskId: firstTask?.taskId,
    sourceSystem: event?.sourceSystem,
    status: hasFailure ? 'FAILED' : hasRunning ? 'PROCESSING' : lastTask?.status ?? event?.status ?? 'RECEIVED',
    startedAt,
    endedAt,
    durationMs: endedAt ? new Date(endedAt).getTime() - new Date(startedAt).getTime() : undefined,
    steps
  };
}

export function getMockEventDetail(eventId: string): GatewayEventDetail {
  const event = mockEvents.find((item) => item.eventId === eventId) ?? {
    eventId,
    traceId: `trace-${eventId}`,
    sourceSystem: 'UNKNOWN',
    eventType: 'UNKNOWN_EVENT',
    status: 'RECEIVED' as const,
    receivedAt: new Date().toISOString(),
    message: 'Mock fallback event detail'
  };
  const relatedTasks = mockTasks.filter((task) => task.traceId === event.traceId);
  const firstTask = relatedTasks[0];
  const trace = getMockTraceDetail(event.traceId);

  return {
    ...event,
    routingDecision: {
      strategy: firstTask ? 'CAPABILITY_AND_LOAD_SCORE' : 'WAITING_FOR_ROUTE',
      selectedAgentId: firstTask?.assignedAgentId,
      selectedNodeId: firstTask?.assignedNodeId,
      reason: event.status === 'FAILED' ? event.message : 'Matched agent capability and lowest queue size',
      score: firstTask ? 0.86 : undefined
    },
    relatedTasks,
    trace
  };
}

function buildMockAttempts(task: GatewayTaskRecord): RetryAttempt[] {
  const attempts: RetryAttempt[] = [
    {
      attemptNo: 1,
      taskId: task.taskId,
      requestedAt: task.createdAt,
      requestedBy: 'gateway-router',
      status: task.status,
      message: task.failureReason ?? 'Initial dispatch'
    }
  ];

  for (let i = 1; i <= task.retryCount; i += 1) {
    attempts.push({
      attemptNo: i + 1,
      taskId: `${task.taskId}-retry-${i}`,
      requestedAt: task.failedAt ?? task.createdAt,
      requestedBy: 'operator',
      status: i === task.retryCount ? task.status : 'FAILED',
      message: i === task.retryCount && task.status !== 'FAILED' ? 'Retry accepted' : task.failureReason ?? 'Retry failed'
    });
  }

  return attempts;
}

function buildMockTaskLogs(task: GatewayTaskRecord): TaskLogRecord[] {
  const logs: TaskLogRecord[] = [
    {
      logId: `${task.taskId}-log-created`,
      taskId: task.taskId,
      timestamp: task.createdAt,
      level: 'INFO',
      message: 'Task created by gateway router',
      payload: task.requestPayload
    }
  ];

  if (task.assignedAt) {
    logs.push({
      logId: `${task.taskId}-log-assigned`,
      taskId: task.taskId,
      timestamp: task.assignedAt,
      level: 'INFO',
      message: `Assigned to ${task.assignedAgentId ?? '-'}`,
      payload: { assignedAgentId: task.assignedAgentId, assignedNodeId: task.assignedNodeId }
    });
  }

  if (task.failedAt) {
    logs.push({
      logId: `${task.taskId}-log-failed`,
      taskId: task.taskId,
      timestamp: task.failedAt,
      level: 'ERROR',
      message: task.failureReason ?? 'Task failed',
      payload: { retryCount: task.retryCount, failureReason: task.failureReason }
    });
  }

  if (task.completedAt) {
    logs.push({
      logId: `${task.taskId}-log-completed`,
      taskId: task.taskId,
      timestamp: task.completedAt,
      level: 'INFO',
      message: 'Task completed',
      payload: task.responsePayload
    });
  }

  return logs;
}

export function getMockTaskDetail(taskId: string): GatewayTaskDetail {
  const task = mockTasks.find((item) => item.taskId === taskId) ?? {
    taskId,
    traceId: `trace-${taskId}`,
    status: 'PENDING' as const,
    createdAt: new Date().toISOString(),
    retryCount: 0
  };
  const sourceEvent = mockEvents.find((event) => event.traceId === task.traceId);

  return {
    ...task,
    sourceEvent,
    trace: getMockTraceDetail(task.traceId),
    attempts: buildMockAttempts(task),
    logs: buildMockTaskLogs(task)
  };
}


export const mockClusterNodeDetails: ClusterNodeDetail[] = mockClusterNodes.map((node, index) => {
  const agents = mockAgents.filter((agent) => agent.nodeId === node.nodeId);
  const recentTasks = mockTasks.filter((task) => task.assignedNodeId === node.nodeId);
  const isOffline = node.status === 'OFFLINE';
  const isDraining = node.nodeId === 'gateway-node-02';

  return {
    ...node,
    role: index === 0 ? 'LEADER' : 'FOLLOWER',
    drainStatus: isOffline ? 'MAINTENANCE' : isDraining ? 'DRAINING' : 'ACTIVE',
    acceptsNewTasks: !isOffline && !isDraining,
    advertisedAddress: `${node.host}:${node.adminPort}`,
    startedAt: isOffline ? '2026-05-28T08:40:00+08:00' : '2026-05-28T08:00:00+08:00',
    uptimeSeconds: isOffline ? 6300 : 10200 + index * 700,
    region: 'tw-local-lab',
    zone: index === 0 ? 'zone-a' : 'zone-b',
    lastDiscoveryAt: isOffline ? '2026-05-28T09:45:00+08:00' : new Date().toISOString(),
    metrics: {
      cpuUsagePercent: isOffline ? 0 : 24 + index * 11,
      memoryUsedMb: isOffline ? 0 : 512 + index * 180,
      memoryMaxMb: 2048,
      nettyEventLoopThreads: 8,
      workerThreads: 16,
      queueSize: isOffline ? 0 : recentTasks.filter((task) => task.status === 'PENDING').length,
      activeTaskCount: recentTasks.filter((task) => task.status === 'PROCESSING' || task.status === 'ASSIGNED').length,
      inboundEventsPerMinute: isOffline ? 0 : 80 - index * 12,
      routedEventsPerMinute: isOffline ? 0 : 76 - index * 11,
      failedEventsPerMinute: node.nodeId === 'gateway-node-02' ? 1 : 0,
      averageLatencyMs: isOffline ? undefined : 42 + index * 20,
      timestamp: new Date().toISOString()
    },
    peers: mockClusterNodes
      .filter((peer) => peer.nodeId !== node.nodeId)
      .map((peer) => ({
        nodeId: peer.nodeId,
        status: peer.status,
        relation: node.discoveryMode === 'DOCKER' || peer.discoveryMode === 'DOCKER' ? 'DOCKER_NETWORK' : 'UDP_DISCOVERY',
        latencyMs: peer.status === 'OFFLINE' ? undefined : 8 + index * 5,
        lastSeenAt: peer.lastHeartbeatAt
      })),
    agents,
    recentTasks
  };
});

export const mockClusterTopology: ClusterTopology = {
  generatedAt: new Date().toISOString(),
  summary: {
    totalNodes: mockClusterNodeDetails.length,
    onlineNodes: mockClusterNodeDetails.filter((node) => node.status === 'ONLINE').length,
    offlineNodes: mockClusterNodeDetails.filter((node) => node.status === 'OFFLINE').length,
    drainingNodes: mockClusterNodeDetails.filter((node) => node.drainStatus === 'DRAINING' || node.drainStatus === 'DRAINED').length,
    totalAgents: mockClusterNodeDetails.reduce((sum, node) => sum + node.agentCount, 0),
    activeTasks: mockClusterNodeDetails.reduce((sum, node) => sum + node.metrics.activeTaskCount, 0),
    queueSize: mockClusterNodeDetails.reduce((sum, node) => sum + node.metrics.queueSize, 0)
  },
  nodes: mockClusterNodeDetails,
  links: [
    {
      fromNodeId: 'gateway-node-01',
      toNodeId: 'gateway-node-02',
      relation: 'UDP_DISCOVERY',
      status: 'UP',
      latencyMs: 12,
      lastSeenAt: new Date().toISOString()
    },
    {
      fromNodeId: 'gateway-node-01',
      toNodeId: 'gateway-node-03',
      relation: 'DOCKER_NETWORK',
      status: 'DOWN',
      lastSeenAt: '2026-05-28T09:45:00+08:00'
    },
    {
      fromNodeId: 'gateway-node-02',
      toNodeId: 'gateway-node-03',
      relation: 'DOCKER_NETWORK',
      status: 'DEGRADED',
      latencyMs: 180,
      lastSeenAt: '2026-05-28T09:45:00+08:00'
    }
  ]
};

export function getMockClusterNodeDetail(nodeId: string): ClusterNodeDetail {
  const detail = mockClusterNodeDetails.find((node) => node.nodeId === nodeId);
  if (detail) return detail;

  return {
    nodeId,
    host: '-',
    tcpPort: 0,
    websocketPort: 0,
    adminPort: 0,
    status: 'OFFLINE',
    agentCount: 0,
    lastHeartbeatAt: new Date().toISOString(),
    discoveryMode: 'STATIC',
    role: 'STANDALONE',
    drainStatus: 'MAINTENANCE',
    acceptsNewTasks: false,
    advertisedAddress: '-',
    metrics: {
      cpuUsagePercent: 0,
      memoryUsedMb: 0,
      memoryMaxMb: 0,
      nettyEventLoopThreads: 0,
      workerThreads: 0,
      queueSize: 0,
      activeTaskCount: 0,
      inboundEventsPerMinute: 0,
      routedEventsPerMinute: 0,
      failedEventsPerMinute: 0,
      timestamp: new Date().toISOString()
    },
    peers: [],
    agents: [],
    recentTasks: []
  };
}

export function getMockClusterNodeAgents(nodeId: string): AgentInfo[] {
  return mockAgents.filter((agent) => agent.nodeId === nodeId);
}

export function getMockClusterNodeTasks(nodeId: string): GatewayTaskRecord[] {
  return mockTasks.filter((task) => task.assignedNodeId === nodeId);
}
