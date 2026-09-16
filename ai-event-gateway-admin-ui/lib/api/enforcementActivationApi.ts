import { apiRequestFor, type HttpMethod } from '@/lib/api/client';
import type {
  ClusterSnapshotStatus,
  CutoverPlan,
  CutoverPlanStatus,
  PublishCutoverResult,
  ReadinessEvidenceType,
  RouteRequest,
  SnapshotRefreshStatus,
  TaskReadCertificationEvidence,
  TaskReadCertificationRequest,
  Wave0AdminSummaryView,
  Wave0PermissionCatalogView,
  Wave0ReadPilotEntryPoint,
  Wave0ReadPilotGateStatus,
  Wave0ReadPilotObservation,
  Wave0ReadPilotOverview,
  Wave0ReadPilotResponse,
  Wave0ReadinessEvidenceView,
  Wave0RuntimeStatusView,
} from '@/lib/types/enforcementActivation';
import { createIdempotencyKey } from '@/lib/utils/uuid';

const BASE = '/api/platform/enforcement-activation';
type Options = { reason?: string; version?: number; idempotent?: boolean; idempotencyKey?: string };
type ActivationRequest = { method?: HttpMethod; body?: unknown };

async function request<T>(path: string, init: ActivationRequest = {}, options: Options = {}): Promise<T> {
  const method = init.method ?? 'GET';
  const write = method !== 'GET';
  const headers: Record<string, string> = {};
  if (write && options.idempotent !== false) {
    headers['Idempotency-Key'] = options.idempotencyKey ?? createIdempotencyKey('phase6b');
  }
  if (options.reason) headers['X-Audit-Reason'] = options.reason;
  if (options.version !== undefined) headers['If-Match'] = String(options.version);
  return apiRequestFor<T>('core', `${BASE}${path}`, {
    method,
    body: init.body,
    headers,
    requireStandardEnvelope: false,
  });
}

function json(method: HttpMethod, body: unknown): ActivationRequest {
  return { method, body };
}

export const enforcementActivationApi = {
  plans: (status?: CutoverPlanStatus) => request<CutoverPlan[]>(`/plans${status ? `?status=${encodeURIComponent(status)}` : ''}`),
  plan: (id: string) => request<CutoverPlan>(`/plans/${encodeURIComponent(id)}`),
  runtime: () => request<SnapshotRefreshStatus>('/runtime'),
  clusterRuntime: () => request<ClusterSnapshotStatus>('/runtime/cluster'),
  create: (title: string, description: string, reason: string) => request<CutoverPlan>('/plans', json('POST', { title, description }), { reason }),
  replaceRoutes: (id: string, version: number, routes: RouteRequest[], reason: string) => request<CutoverPlan>(`/plans/${encodeURIComponent(id)}/routes`, json('PUT', { routes }), { version, reason }),
  bindEvidence: (id: string, version: number, evidenceType: ReadinessEvidenceType, evidenceId: string, reason: string) => request<CutoverPlan>(`/plans/${encodeURIComponent(id)}/evidence`, json('POST', { evidenceType, evidenceId }), { version, reason }),
  submit: (id: string, version: number, reason: string) => request<CutoverPlan>(`/plans/${encodeURIComponent(id)}/submit`, json('POST', {}), { version, reason }),
  approve: (id: string, version: number, reason: string) => request<CutoverPlan>(`/plans/${encodeURIComponent(id)}/approve`, json('POST', {}), { version, reason }),
  reject: (id: string, version: number, rejectionReason: string, reason: string) => request<CutoverPlan>(`/plans/${encodeURIComponent(id)}/reject`, json('POST', { reason: rejectionReason }), { version, reason }),
  publish: (id: string, version: number, reason: string) => request<PublishCutoverResult>(`/plans/${encodeURIComponent(id)}/publish`, json('POST', {}), { version, reason }),
  wave0Overview: () => request<Wave0ReadPilotOverview[]>('/wave0'),
  wave0Observations: (entryPoint: Wave0ReadPilotEntryPoint, limit = 100) => request<Wave0ReadPilotObservation[]>(`/wave0/observations?entryPoint=${encodeURIComponent(entryPoint)}&limit=${limit}`),
  wave0Pause: (entryPoint: Wave0ReadPilotEntryPoint, version: number, reason: string) => request<Wave0ReadPilotGateStatus>(`/wave0/entry-points/${encodeURIComponent(entryPoint)}/pause`, json('POST', {}), { version, reason }),
  wave0Resume: (entryPoint: Wave0ReadPilotEntryPoint, version: number, reason: string) => request<Wave0ReadPilotGateStatus>(`/wave0/entry-points/${encodeURIComponent(entryPoint)}/resume`, json('POST', {}), { version, reason }),
  wave0Evaluate: (entryPoint: Wave0ReadPilotEntryPoint, reason: string) => request<Wave0ReadPilotGateStatus>(`/wave0/entry-points/${encodeURIComponent(entryPoint)}/evaluate`, json('POST', {}), { reason }),
  wave0RuntimeRead: () => request<Wave0ReadPilotResponse<Wave0RuntimeStatusView>>('/wave0/read/runtime-status'),
  wave0ReadinessRead: (type: ReadinessEvidenceType, evidenceId: string) => request<Wave0ReadPilotResponse<Wave0ReadinessEvidenceView>>(`/wave0/read/readiness-evidence/${encodeURIComponent(type)}/${encodeURIComponent(evidenceId)}`),
  wave0PermissionCatalogRead: () => request<Wave0ReadPilotResponse<Wave0PermissionCatalogView>>('/wave0/read/permission-catalog'),
  wave0AdminSummaryRead: () => request<Wave0ReadPilotResponse<Wave0AdminSummaryView>>('/wave0/read/admin-summary'),
  taskReadCertifications: (tenantId: string, limit = 20) => request<TaskReadCertificationEvidence[]>(`/wave0/task-read-certifications?tenantId=${encodeURIComponent(tenantId)}&limit=${limit}`),
  taskReadCertify: (value: TaskReadCertificationRequest, reason: string) => request<TaskReadCertificationEvidence>('/wave0/task-read-certifications', json('POST', value), { reason }),
};
