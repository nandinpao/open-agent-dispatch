import { getPublicEnv } from '@/lib/constants/env';
import type {
  AgentInfo,
  ClusterNodeDetail,
  GatewayEventRecord,
  GatewayTaskDetail,
  GatewayTaskRecord,
  TraceDetail
} from '@/lib/types/admin';

export interface ContractIssue {
  path: string;
  message: string;
}

export type ContractValidator<T> = (value: T) => ContractIssue[];

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}

function isFiniteNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}

function requireString(issues: ContractIssue[], path: string, value: unknown): void {
  if (!isNonEmptyString(value)) issues.push({ path, message: 'must be a non-empty string' });
}

function requireNumber(issues: ContractIssue[], path: string, value: unknown): void {
  if (!isFiniteNumber(value)) issues.push({ path, message: 'must be a finite number' });
}

function requireArray(issues: ContractIssue[], path: string, value: unknown): void {
  if (!Array.isArray(value)) issues.push({ path, message: 'must be an array' });
}

function prefixIssues(prefix: string, issues: ContractIssue[]): ContractIssue[] {
  return issues.map((issue) => ({ ...issue, path: `${prefix}.${issue.path}` }));
}

export function validateAgentInfo(value: AgentInfo): ContractIssue[] {
  const issues: ContractIssue[] = [];
  requireString(issues, 'agentId', value.agentId);
  requireString(issues, 'nodeId', value.nodeId);
  requireString(issues, 'connectedAt', value.connectedAt);
  requireString(issues, 'lastSeenAt', value.lastSeenAt);
  requireNumber(issues, 'errorCount', value.errorCount);
  return issues;
}

export function validateGatewayEventRecord(value: GatewayEventRecord): ContractIssue[] {
  const issues: ContractIssue[] = [];
  requireString(issues, 'eventId', value.eventId);
  requireString(issues, 'traceId', value.traceId);
  requireString(issues, 'sourceSystem', value.sourceSystem);
  requireString(issues, 'eventType', value.eventType);
  requireString(issues, 'receivedAt', value.receivedAt);
  return issues;
}

export function validateGatewayTaskRecord(value: GatewayTaskRecord): ContractIssue[] {
  const issues: ContractIssue[] = [];
  requireString(issues, 'taskId', value.taskId);
  requireString(issues, 'traceId', value.traceId);
  requireString(issues, 'createdAt', value.createdAt);
  requireNumber(issues, 'retryCount', value.retryCount);
  return issues;
}

export function validateTraceDetail(value: TraceDetail): ContractIssue[] {
  const issues: ContractIssue[] = [];
  requireString(issues, 'traceId', value.traceId);
  requireString(issues, 'startedAt', value.startedAt);
  requireArray(issues, 'steps', value.steps);
  return issues;
}

export function validateGatewayTaskDetail(value: GatewayTaskDetail): ContractIssue[] {
  return [
    ...validateGatewayTaskRecord(value),
    ...prefixIssues('trace', validateTraceDetail(value.trace)),
    ...(Array.isArray(value.attempts) ? [] : [{ path: 'attempts', message: 'must be an array' }]),
    ...(Array.isArray(value.logs) ? [] : [{ path: 'logs', message: 'must be an array' }])
  ];
}

export function validateClusterNodeDetail(value: ClusterNodeDetail): ContractIssue[] {
  const issues: ContractIssue[] = [];
  requireString(issues, 'nodeId', value.nodeId);
  requireString(issues, 'host', value.host);
  requireString(issues, 'advertisedAddress', value.advertisedAddress);
  requireString(issues, 'lastHeartbeatAt', value.lastHeartbeatAt);
  requireNumber(issues, 'tcpPort', value.tcpPort);
  requireNumber(issues, 'websocketPort', value.websocketPort);
  requireNumber(issues, 'adminPort', value.adminPort);
  requireNumber(issues, 'agentCount', value.agentCount);
  requireArray(issues, 'peers', value.peers);
  requireArray(issues, 'agents', value.agents);
  requireArray(issues, 'recentTasks', value.recentTasks);
  value.agents.forEach((agent, index) => issues.push(...prefixIssues(`agents[${index}]`, validateAgentInfo(agent))));
  value.recentTasks.forEach((task, index) => issues.push(...prefixIssues(`recentTasks[${index}]`, validateGatewayTaskRecord(task))));
  return issues;
}

export function validateArrayContract<T>(label: string, values: T[], validator: ContractValidator<T>): T[] {
  values.forEach((value, index) => validateContract(`${label}[${index}]`, value, validator));
  return values;
}

export function validateContract<T>(label: string, value: T, validator: ContractValidator<T>): T {
  const issues = validator(value);
  if (issues.length === 0) return value;

  const message = `${label} contract mismatch: ${issues.map((issue) => `${issue.path} ${issue.message}`).join('; ')}`;
  const mode = getPublicEnv().apiContractMode;
  if (mode === 'strict') throw new Error(message);
  if (mode === 'warn' && typeof console !== 'undefined') console.warn(message);
  return value;
}
