import type {
  AgentEnrollmentApprovalRequest,
  AgentEnrollmentRequest,
  AgentEnrollmentCreateRequest,
  CoreAgentAuthorizationScope
} from '@/lib/types/core';
import type { AgentDashboardRow } from '@/lib/types/dashboard';
import type { NettyAgentRuntime } from '@/lib/types/nettyRuntime';
import { requireCoreTenantContext } from '@/lib/api/coreClient';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function stringValue(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim() ? value.trim() : undefined;
}

export function runtimeEnrollmentId(runtime: NettyAgentRuntime): string {
  return `runtime:${runtime.gatewayNodeId ?? runtime.nodeId ?? 'unknown'}:${runtime.agentId}`;
}

export function isRuntimeObservedEnrollment(enrollment: AgentEnrollmentRequest): boolean {
  return enrollment.status === 'RUNTIME_OBSERVED' || enrollment.enrollmentId.startsWith('runtime:');
}

export function runtimeToEnrollmentRequest(runtime: NettyAgentRuntime): AgentEnrollmentCreateRequest {
  const payload = isRecord(runtime.payload) ? runtime.payload : {};
  const metadata = isRecord(payload.metadata) ? payload.metadata : {};
  return {
    claimedAgentId: runtime.agentId,
    tenantId: requireCoreTenantContext(stringValue(metadata.tenantId)),
    agentName: stringValue(metadata.agentName) ?? runtime.agentId,
    agentType: stringValue(payload.agentType) ?? stringValue(metadata.agentType) ?? 'UNKNOWN',
    submittedMetadataJson: {
      source: 'NETTY_RUNTIME_OBSERVATION',
      gatewayNodeId: runtime.gatewayNodeId ?? runtime.nodeId,
      authorizationState: runtime.authorizationState,
      connected: runtime.connected,
      transport: runtime.transport,
      connectionId: runtime.connectionId,
      sessionId: runtime.sessionId,
      lastHeartbeatAt: runtime.lastHeartbeatAt,
      metadata
    },
    evidenceJson: runtime.payload ?? runtime,
    fingerprint: stringValue(metadata.fingerprint),
    remoteAddress: runtime.remoteAddress,
    submittedAt: runtime.connectedAt ?? runtime.lastSeenAt ?? runtime.lastHeartbeatAt
  };
}

export function runtimeToEnrollmentCandidate(runtime: NettyAgentRuntime): AgentEnrollmentRequest {
  return {
    enrollmentId: runtimeEnrollmentId(runtime),
    status: 'RUNTIME_OBSERVED',
    ...runtimeToEnrollmentRequest(runtime)
  };
}

export function rowToEnrollmentRequest(row: AgentDashboardRow): AgentEnrollmentCreateRequest | null {
  if (!row.runtime) return null;
  return runtimeToEnrollmentRequest(row.runtime);
}

export function parseCapabilitiesCsv(value: string): string[] {
  return value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
    .filter((item, index, array) => array.indexOf(item) === index);
}

export function parseScopeCsv(value: string, tenantId?: string): CoreAgentAuthorizationScope[] {
  return value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
    .map((item) => {
      const [systemCode, taskType = '*', siteCode] = item.split('/').map((part) => part.trim()).filter(Boolean);
      return {
        tenantId: requireCoreTenantContext(tenantId),
        systemCode: systemCode || '*',
        taskType,
        siteCode,
        enabled: true
      };
    });
}

export function buildDefaultApprovalRequest(enrollment: AgentEnrollmentRequest, comment?: string): AgentEnrollmentApprovalRequest {
  const metadata = isRecord(enrollment.submittedMetadataJson) ? enrollment.submittedMetadataJson : {};
  const tenantId = requireCoreTenantContext(enrollment.tenantId ?? stringValue(metadata.tenantId));
  return {
    agentId: enrollment.claimedAgentId,
    tenantId,
    agentName: enrollment.agentName ?? enrollment.claimedAgentId,
    agentType: enrollment.agentType ?? 'UNKNOWN',
    comment,
    capabilities: [],
    scopes: [{ tenantId, systemCode: '*', taskType: '*', enabled: true }]
  };
}
