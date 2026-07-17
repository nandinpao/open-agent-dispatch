export type GatewayStatus = 'RUNNING' | 'DEGRADED' | 'DOWN';

export interface GatewayHealth {
  status: GatewayStatus;
  nodeId: string;
  version: string;
  uptimeSeconds: number;
  activeConnections: number;
  connectedAgents: number;
  pendingTasks: number;
  failedTasks: number;
  timestamp: string;
}

export interface GatewayMetrics {
  nodeId: string;
  cpuUsagePercent: number;
  memoryUsedMb: number;
  memoryMaxMb: number;
  memoryUsedPercent?: number;
  nettyEventLoopThreads: number;
  workerThreads: number;
  queueSize: number;
  inboundEventsPerMinute: number;
  routedEventsPerMinute: number;
  failedEventsPerMinute: number;
  timestamp: string;
}

export type ClusterNodeStatus = 'ONLINE' | 'OFFLINE' | 'STARTING' | 'LEAVING' | 'DISCOVERED' | 'DEGRADED';
export type ClusterPeerRelationKind =
  | 'UDP_DISCOVERY'
  | 'STATIC_PEER'
  | 'STATIC_SEED'
  | 'DOCKER_NETWORK'
  | 'DISCOVERED'
  | 'HEARTBEAT'
  | 'SELF'
  | 'UNKNOWN';
export type ClusterPeerSyncStatus = 'SYNCED' | 'FAILED' | 'STALE' | 'UNKNOWN';
export type ClusterPeerHeartbeatStatus = 'OK' | 'WARNING' | 'LOST' | 'UNKNOWN';
export type ClusterPeerHealthStatus = 'ONLINE' | 'DEGRADED' | 'OFFLINE' | 'UNKNOWN';
export type ClusterNodeRole = 'LEADER' | 'FOLLOWER' | 'STANDALONE';
export type ClusterDrainStatus = 'ACTIVE' | 'DRAINING' | 'DRAINED' | 'MAINTENANCE';

export interface ClusterNode {
  nodeId: string;
  host: string;
  tcpPort: number;
  websocketPort: number;
  adminPort: number;
  status: ClusterNodeStatus;
  agentCount: number;
  lastHeartbeatAt: string;
  discoveryMode: 'UDP' | 'STATIC' | 'DOCKER';
  role?: ClusterNodeRole;
  drainStatus?: ClusterDrainStatus;
  acceptsNewTasks?: boolean;
  /** True when this node is the backend node that handled the current Admin API request. */
  isCurrentRequestNode?: boolean;
  /** Raw backend status before UI health normalization, e.g. SELF / SYNCED / DISCOVERED. */
  sourceStatus?: string;
  /** Cluster synchronization status from /api/cluster/overview.remoteStates[].syncStatus. */
  syncStatus?: string;
}

export interface ClusterNodeMetrics {
  cpuUsagePercent: number;
  memoryUsedMb: number;
  memoryMaxMb: number;
  memoryUsedPercent?: number;
  nettyEventLoopThreads: number;
  workerThreads: number;
  queueSize: number;
  activeTaskCount: number;
  inboundEventsPerMinute: number;
  routedEventsPerMinute: number;
  failedEventsPerMinute: number;
  averageLatencyMs?: number;
  timestamp: string;
}

export interface ClusterNodePeer {
  nodeId: string;
  status: ClusterNodeStatus;
  relation: ClusterPeerRelationKind;
  syncStatus?: ClusterPeerSyncStatus;
  heartbeatStatus?: ClusterPeerHeartbeatStatus;
  healthStatus?: ClusterPeerHealthStatus;
  latencyMs?: number;
  heartbeatLatencyMs?: number;
  lastSeenAt?: string;
  lastSyncAt?: string;
  lastHeartbeatAt?: string;
  missedHeartbeatCount?: number;
  lastError?: string | null;
  /** The backend node whose perspective produced this peer relation. */
  perspectiveNodeId?: string;
}

export interface ClusterPeersResponse {
  localNodeId?: string;
  generatedAt: string;
  peers: ClusterNodePeer[];
}

export type ClusterDataScope = 'CLUSTER' | 'LOCAL';
export type ClusterDataScopeLabel = 'CLUSTER_AGGREGATED' | 'LOCAL_SCOPE' | 'LOCAL_EVENT_SCOPE';

export interface ClusterScopedCollection<T> {
  scope: ClusterDataScope;
  localNodeId?: string;
  generatedAt: string;
  items: T[];
  byNode: Record<string, T[]>;
  fallbackReason?: string;
}

export type ClusterAgentsResponse = ClusterScopedCollection<AgentInfo>;
export type ClusterTasksResponse = ClusterScopedCollection<GatewayTaskRecord>;

export interface ClusterNodeDetail extends ClusterNode {
  advertisedAddress: string;
  role: ClusterNodeRole;
  drainStatus: ClusterDrainStatus;
  acceptsNewTasks: boolean;
  startedAt?: string;
  uptimeSeconds?: number;
  region?: string;
  zone?: string;
  lastDiscoveryAt?: string;
  metrics: ClusterNodeMetrics;
  peers: ClusterNodePeer[];
  agents: AgentInfo[];
  recentTasks: GatewayTaskRecord[];
}

export interface ClusterTopologyLink {
  fromNodeId: string;
  toNodeId: string;
  relation: ClusterPeerRelationKind;
  status: 'UP' | 'DOWN' | 'DEGRADED';
  syncStatus?: ClusterPeerSyncStatus;
  heartbeatStatus?: ClusterPeerHeartbeatStatus;
  missedHeartbeatCount?: number;
  lastError?: string | null;
  latencyMs?: number;
  lastSeenAt?: string;
}

export interface ClusterTopology {
  generatedAt: string;
  summary: {
    totalNodes: number;
    onlineNodes: number;
    offlineNodes: number;
    drainingNodes: number;
    totalAgents: number;
    activeTasks: number;
    queueSize: number;
  };
  nodes: ClusterNodeDetail[];
  links: ClusterTopologyLink[];
}

export type AgentStatus = 'CONNECTED' | 'AUTHORIZED' | 'UNVERIFIED' | 'DENIED' | 'IDLE' | 'BUSY' | 'BUSY_ACCEPTING' | 'DRAINING' | 'DEGRADED' | 'DISCONNECTED' | 'TIMEOUT' | 'OFFLINE' | 'ERROR';
export type AgentType = 'OPENCLAW' | 'HERMES' | 'TAO' | 'CUSTOM';
export type AgentTransport = 'WEBSOCKET' | 'TCP' | 'HTTP_CALLBACK';
export type AgentCapabilityStatus = 'ENABLED' | 'DISABLED' | 'DEGRADED';
export type AgentCommand = 'PING' | 'DISCONNECT';
export type AgentCommandStatus = 'READY' | 'RUNNING' | 'SUCCESS' | 'FAILED';

export interface AgentInfo {
  agentId: string;
  agentType: AgentType;
  status: AgentStatus;
  /** Owner Gateway node that owns this live Agent connection. */
  ownerNodeId?: string;
  nodeId: string;
  transport: AgentTransport;
  connectedAt: string;
  lastSeenAt: string;
  currentTaskId?: string;
  errorCount: number;
}

export interface AgentCapability {
  capabilityId: string;
  name: string;
  description: string;
  status: AgentCapabilityStatus;
  version?: string;
  supportedActions: string[];
  metadata?: Record<string, unknown>;
}

export interface AgentRuntimeInfo {
  processId?: string;
  host?: string;
  runtimeVersion?: string;
  protocolVersion?: string;
  heartbeatIntervalMs?: number;
  lastHeartbeatAt?: string;
  averageLatencyMs?: number;
  activeTaskCount?: number;
  maxConcurrentTasks?: number;
}

export interface AgentErrorLog {
  logId: string;
  agentId: string;
  timestamp: string;
  level: 'WARN' | 'ERROR';
  message: string;
  traceId?: string;
  taskId?: string;
  detail?: unknown;
}

export interface AgentDetail extends AgentInfo {
  displayName?: string;
  description?: string;
  labels?: string[];
  capabilities: AgentCapability[];
  runtime: AgentRuntimeInfo;
  recentErrors: AgentErrorLog[];
}

export type AdminEventType =
  | 'NODE_JOINED'
  | 'NODE_LEFT'
  | 'NODE_HEARTBEAT_TIMEOUT'
  | 'NODE_DRAINING'
  | 'NODE_DRAINED'
  | 'NODE_RESUMED'
  | 'NODE_METRICS_UPDATED'
  | 'CLUSTER_METRICS_UPDATED'
  | 'GATEWAY_METRICS_UPDATED'
  | 'METRICS_UPDATED'
  | 'AGENT_CONNECTED'
  | 'AGENT_AUTHORIZED'
  | 'AGENT_AUTHORIZATION_DENIED'
  | 'AGENT_DISCONNECTED'
  | 'AGENT_STATUS_CHANGED'
  | 'DELIVERY_STARTED'
  | 'DELIVERY_SUCCEEDED'
  | 'DELIVERY_FAILED'
  | 'CALLBACK_RECEIVED'
  | 'CALLBACK_RELAYED'
  | 'CALLBACK_RELAY_FAILED'
  | 'SECURITY_CONNECTION_REJECTED'
  | 'SECURITY_CREDENTIAL_REVOKED_ATTEMPT'
  | 'TASK_CREATED'
  | 'TASK_ASSIGNED'
  | 'TASK_COMPLETED'
  | 'TASK_FAILED'
  | 'TASK_CANCELLED'
  | 'SYSTEM_MESSAGE';

export interface AdminWebSocketEvent {
  eventType: AdminEventType;
  timestamp: string;
  nodeId?: string;
  agentId?: string;
  taskId?: string;
  traceId?: string;
  status?: string;
  message?: string;
  payload?: unknown;
}

export type GatewayEventStatus = 'RECEIVED' | 'ROUTED' | 'FAILED' | 'UNKNOWN';

export interface GatewayEventRecord {
  eventId: string;
  traceId: string;
  sourceSystem: string;
  eventType: string;
  status: GatewayEventStatus;
  receivedAt: string;
  routedAt?: string;
  failedAt?: string;
  message?: string;
  payload?: unknown;
}

export type GatewayTaskStatus =
  | 'CREATED'
  | 'WAITING_APPROVAL'
  | 'PENDING'
  | 'ASSIGNED'
  | 'DISPATCH_REQUESTED'
  | 'DISPATCHED'
  | 'ACKED'
  | 'PROCESSING'
  | 'RUNNING'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED'
  | 'TIMEOUT'
  | 'REASSIGNING';

export interface GatewayTaskRecord {
  taskId: string;
  traceId: string;
  status: GatewayTaskStatus;
  assignedAgentId?: string;
  /** Owner Gateway node that accepted and owns this task record. */
  ownerNodeId?: string;
  assignedNodeId?: string;
  createdAt: string;
  assignedAt?: string;
  startedAt?: string;
  completedAt?: string;
  failedAt?: string;
  durationMs?: number;
  retryCount: number;
  failureReason?: string;
  requestPayload?: unknown;
  responsePayload?: unknown;
}


export type TraceStepStatus = 'SUCCESS' | 'RUNNING' | 'FAILED' | 'SKIPPED' | 'WAITING';
export type TraceActorType = 'SOURCE_SYSTEM' | 'GATEWAY' | 'ROUTER' | 'CLUSTER' | 'AGENT' | 'ADAPTER' | 'UNKNOWN';

export interface TraceStep {
  stepId: string;
  traceId: string;
  stage: string;
  status: TraceStepStatus;
  actorType: TraceActorType;
  actorId?: string;
  timestamp: string;
  durationMs?: number;
  message?: string;
  payload?: unknown;
}

export interface TraceDetail {
  traceId: string;
  status: GatewayTaskStatus | GatewayEventStatus | 'PARTIAL_FAILED';
  sourceSystem?: string;
  eventId?: string;
  taskId?: string;
  startedAt: string;
  endedAt?: string;
  durationMs?: number;
  steps: TraceStep[];
}

export interface RetryAttempt {
  attemptNo: number;
  taskId: string;
  requestedAt: string;
  requestedBy?: string;
  status: GatewayTaskStatus;
  message?: string;
}

export interface TaskLogRecord {
  logId: string;
  taskId: string;
  timestamp: string;
  level: 'INFO' | 'WARN' | 'ERROR';
  message: string;
  payload?: unknown;
}

export interface GatewayEventDetail extends GatewayEventRecord {
  routingDecision?: {
    strategy: string;
    selectedAgentId?: string;
    selectedNodeId?: string;
    reason?: string;
    score?: number;
    skillAware?: boolean;
    skillEligible?: boolean;
    matchedSkills?: string[];
    missingSkillRequirements?: string[];
  };
  relatedTasks: GatewayTaskRecord[];
  trace: TraceDetail;
}

export interface GatewayTaskDetail extends GatewayTaskRecord {
  sourceEvent?: GatewayEventRecord;
  trace: TraceDetail;
  attempts: RetryAttempt[];
  logs: TaskLogRecord[];
}


export type ApiDiagnosticScope = 'CLUSTER' | 'LOCAL' | 'REALTIME' | 'ADMIN';
export type ApiDiagnosticStatus = 'AVAILABLE' | 'UNAVAILABLE' | 'ERROR';

export interface ApiDiagnosticProbe {
  id: string;
  label: string;
  path: string;
  scope: ApiDiagnosticScope;
  status: ApiDiagnosticStatus;
  httpStatus?: number;
  responseTimeMs: number;
  recordCount?: number;
  lastSuccessAt?: string;
  message?: string;
}

export interface ApiDiagnosticsReport {
  generatedAt: string;
  adminApiBaseUrl: string;
  adminWebSocketUrl: string;
  probes: ApiDiagnosticProbe[];
}

export interface CommandResult {
  success: boolean;
  message: string;
  timestamp: string;
}

export type AdminRole = 'ADMIN' | 'OPERATOR' | 'VIEWER' | 'RECOVERY_APPROVER' | 'SUPPORT' | 'DEVELOPER';

export interface AdminUser {
  authenticationType?: string;
  userId: string;
  username?: string;
  displayName: string;
  roles: AdminRole[];
  permissions?: string[];
  allowedTenantIds?: string[];
  selectedTenantId?: string;
  authenticatedAt?: string;
  expiresAt?: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface AdminSessionResponse {
  authenticationType: string;
  userId: string;
  username: string;
  displayName: string;
  roles: AdminRole[];
  permissions: string[];
  allowedTenantIds: string[];
  selectedTenantId: string;
  authenticatedAt: string;
  expiresAt: string;
}

export interface AdminCsrfResponse {
  headerName: string;
  parameterName: string;
  token: string;
}

export interface AdminPermissionResponse {
  roles: AdminRole[];
  permissions: string[];
}

export interface AdminPermissionsResponse {
  roles: AdminRole[];
  permissions: string[];
}

export interface AdminTenantOption {
  tenantId: string;
  selected: boolean;
}

export interface AdminTenantsResponse {
  selectedTenantId: string;
  tenants: AdminTenantOption[];
}

export interface AdminSessionDescriptor {
  sessionReference: string;
  username: string;
  userId: string;
  selectedTenantId: string;
  createdAt: string;
  lastAccessedAt: string;
  expiresAt: string;
  current: boolean;
  expired: boolean;
}

export interface AdminSessionsResponse {
  currentSessionReference: string;
  sessions: AdminSessionDescriptor[];
}

export interface AdminSessionRevocationResponse {
  status: 'REVOKED';
  sessionReference: string;
  currentSession: boolean;
  revokedAt: string;
}

export interface AdminSecurityAuditEvent {
  eventId: string;
  occurredAt: string;
  eventType: string;
  outcome: string;
  username: string;
  userId: string;
  tenantId: string;
  sessionReference: string;
  sourceAddress: string;
  userAgent: string;
  reason: string;
}

export interface AdminSecurityAuditResponse {
  events: AdminSecurityAuditEvent[];
}
