import { fetchBackend } from '@/lib/server/backendOrigins';
import { ROOT_ADMIN_TENANT_COOKIE_KEY } from '@/lib/auth/workspaceTenantContext';

export const OPERATIONAL_RESOURCE_TYPES = ['TASK', 'AGENT', 'SOURCE_SYSTEM', 'EVENT', 'ISSUE', 'INCIDENT'] as const;
export type OperationalResourceType = (typeof OPERATIONAL_RESOURCE_TYPES)[number];

export interface OperationalHit {
  type: OperationalResourceType;
  id: string;
  title: string;
  subtitle?: string;
  status?: string;
  ownerDepartmentId?: string;
  ownerGroupId?: string;
  updatedAt?: string;
  href: string;
}
export interface OperationalCount { type: OperationalResourceType; value: number; capped: boolean; }
export interface ScopedOperationalData {
  counts: OperationalCount[];
  recent: OperationalHit[];
  accessibleTypes: OperationalResourceType[];
  tenantWideRuntimeDiagnosticsAllowed: boolean;
  partial: boolean;
}

type JsonRecord = Record<string, unknown>;
const MAX_SCAN = 500;

function record(value: unknown): JsonRecord | null { return value && typeof value === 'object' && !Array.isArray(value) ? value as JsonRecord : null; }
function stringValue(value: unknown): string | undefined { return typeof value === 'string' && value.trim() ? value.trim() : undefined; }
function nested(value: JsonRecord, key: string): unknown { return value[key]; }
function first(value: JsonRecord, ...keys: string[]): string | undefined {
  for (const key of keys) { const found = stringValue(nested(value, key)); if (found) return found; }
  return undefined;
}
function unwrapList(value: unknown): unknown[] {
  if (Array.isArray(value)) return value;
  const root = record(value); if (!root) return [];
  for (const key of ['items', 'content', 'data', 'records', 'rows', 'results']) {
    const candidate = root[key]; if (Array.isArray(candidate)) return candidate;
  }
  return [];
}
function safeHref(type: OperationalResourceType, id: string): string {
  if (type === 'TASK') return `/tasks/${encodeURIComponent(id)}`;
  if (type === 'AGENT') return `/agents/${encodeURIComponent(id)}`;
  if (type === 'SOURCE_SYSTEM') return '/source-systems';
  if (type === 'EVENT') return '/issues-events?view=events';
  if (type === 'ISSUE') return '/issues-events?view=issues';
  return '/issues-events?view=events';
}
function normalize(type: OperationalResourceType, raw: unknown): OperationalHit | null {
  const item = record(raw); if (!item) return null;
  const id = type === 'TASK' ? first(item, 'taskId', 'id')
    : type === 'AGENT' ? first(item, 'agentId', 'id')
    : type === 'SOURCE_SYSTEM' ? first(item, 'sourceSystemId', 'sourceSystem', 'id')
    : type === 'EVENT' ? first(item, 'eventId', 'id')
    : type === 'ISSUE' ? first(item, 'linkId', 'externalIssueKey', 'id')
    : first(item, 'incidentId', 'id');
  if (!id) return null;
  const title = type === 'TASK' ? first(item, 'taskType', 'taskTypeCode', 'effectiveTaskTypeCode', 'eventType') ?? `Task ${id}`
    : type === 'AGENT' ? first(item, 'agentName', 'displayName') ?? id
    : type === 'SOURCE_SYSTEM' ? first(item, 'displayName', 'sourceSystemId') ?? id
    : type === 'EVENT' ? first(item, 'eventType', 'normalizedMessage') ?? `Event ${id}`
    : type === 'ISSUE' ? first(item, 'externalIssueKey', 'externalIssueId') ?? `Issue ${id}`
    : first(item, 'eventType', 'errorCode', 'title') ?? `Incident ${id}`;
  const subtitle = type === 'TASK' ? first(item, 'sourceSystem', 'originSourceSystem', 'incidentId')
    : type === 'AGENT' ? first(item, 'agentType', 'description')
    : type === 'SOURCE_SYSTEM' ? first(item, 'description')
    : type === 'EVENT' ? first(item, 'sourceSystem', 'normalizedMessage')
    : type === 'ISSUE' ? first(item, 'providerType', 'externalProjectId', 'taskId')
    : first(item, 'sourceSystem', 'objectId', 'errorCode');
  return {
    type, id, title, subtitle,
    status: first(item, 'status', 'issueStatus', 'syncStatus', 'approvalStatus', 'decisionType', 'scopeStatus'),
    ownerDepartmentId: first(item, 'ownerDepartmentId', 'originScopeDepartmentId'),
    ownerGroupId: first(item, 'ownerGroupId', 'originScopeGroupId'),
    updatedAt: first(item, 'updatedAt', 'decidedAt', 'occurredAt', 'createdAt'),
    href: safeHref(type, id),
  };
}
function endpoint(type: OperationalResourceType, limit: number): string {
  const safeLimit = Math.max(1, Math.min(limit, MAX_SCAN));
  if (type === 'TASK') return `/api/tasks?limit=${safeLimit}`;
  if (type === 'AGENT') return `/admin/agents?limit=${safeLimit}`;
  if (type === 'SOURCE_SYSTEM') return '/admin/source-systems';
  if (type === 'EVENT') return `/admin/business-events?limit=${safeLimit}`;
  if (type === 'ISSUE') return `/api/issues?limit=${safeLimit}`;
  return `/api/incidents?limit=${safeLimit}`;
}
function cookieValue(cookie: string, name: string): string | undefined {
  const encodedName = encodeURIComponent(name);
  for (const part of cookie.split(';')) {
    const [rawName, ...rawValue] = part.trim().split('=');
    if (rawName !== name && rawName !== encodedName) continue;
    const value = rawValue.join('=').trim();
    if (!value) return undefined;
    try { return decodeURIComponent(value).trim() || undefined; } catch { return value.trim() || undefined; }
  }
  return undefined;
}
function sessionTenant(value: unknown): string | undefined {
  const root = record(value); if (!root) return undefined;
  const data = record(root.data);
  return first(root, 'selectedTenantId') ?? (data ? first(data, 'selectedTenantId') : undefined);
}
function scopedHeaders(cookie: string, tenantId: string): Record<string, string> {
  return {
    ...(cookie ? { cookie } : {}),
    Accept: 'application/json',
    'X-Tenant-Id': tenantId,
  };
}
async function resolveOperationalTenantId(cookie: string): Promise<string | undefined> {
  // INSTANCE_ROOT has no Session home Tenant. The root administration cookie is a transport hint
  // only; Core still authorizes X-Tenant-Id against the authenticated root principal.
  const rootAdministrationTenant = cookieValue(cookie, ROOT_ADMIN_TENANT_COOKIE_KEY);
  if (rootAdministrationTenant) return rootAdministrationTenant;
  try {
    const { response } = await fetchBackend('core', '/api/session', { headers: cookie ? { cookie, Accept: 'application/json' } : { Accept: 'application/json' } });
    if (!response.ok) return undefined;
    return sessionTenant(await response.json());
  } catch {
    return undefined;
  }
}
async function scopedRows(cookie: string, tenantId: string, type: OperationalResourceType, limit = MAX_SCAN): Promise<{ rows: OperationalHit[]; denied: boolean; failed: boolean }> {
  try {
    const { response } = await fetchBackend('core', endpoint(type, limit), { headers: scopedHeaders(cookie, tenantId) });
    if (response.status === 401 || response.status === 403 || response.status === 404) return { rows: [], denied: true, failed: false };
    if (!response.ok) return { rows: [], denied: false, failed: true };
    const rows = unwrapList(await response.json()).map((item) => normalize(type, item)).filter((item): item is OperationalHit => Boolean(item));
    return { rows, denied: false, failed: false };
  } catch { return { rows: [], denied: false, failed: true }; }
}
export async function tenantWideRuntimeDiagnosticsAllowed(cookie: string, resolvedTenantId?: string): Promise<boolean> {
  try {
    const tenantId = resolvedTenantId ?? await resolveOperationalTenantId(cookie);
    if (!tenantId) return false;
    const { response } = await fetchBackend('core', `/admin/tenants/${encodeURIComponent(tenantId)}/dashboard/snapshot?limit=1`, { headers: scopedHeaders(cookie, tenantId) });
    return response.ok;
  } catch { return false; }
}
export async function loadScopedOperationalData(cookie: string, scanLimit = MAX_SCAN): Promise<ScopedOperationalData> {
  const tenantId = await resolveOperationalTenantId(cookie);
  if (!tenantId) {
    return { counts: OPERATIONAL_RESOURCE_TYPES.map((type) => ({ type, value: 0, capped: false })), recent: [], accessibleTypes: [], tenantWideRuntimeDiagnosticsAllowed: false, partial: true };
  }
  const results = await Promise.all(OPERATIONAL_RESOURCE_TYPES.map(async (type) => ({ type, result: await scopedRows(cookie, tenantId, type, scanLimit) })));
  const recent: OperationalHit[] = [];
  const counts: OperationalCount[] = [];
  const accessibleTypes: OperationalResourceType[] = [];
  let partial = false;
  for (const { type, result } of results) {
    if (!result.denied && !result.failed) accessibleTypes.push(type);
    if (result.failed) partial = true;
    counts.push({ type, value: result.rows.length, capped: result.rows.length >= Math.min(scanLimit, MAX_SCAN) });
    recent.push(...result.rows);
  }
  recent.sort((a, b) => Date.parse(b.updatedAt ?? '') - Date.parse(a.updatedAt ?? ''));
  return { counts, recent: recent.slice(0, 24), accessibleTypes, tenantWideRuntimeDiagnosticsAllowed: await tenantWideRuntimeDiagnosticsAllowed(cookie, tenantId), partial };
}
export async function searchScopedOperationalData(cookie: string, query: string, types: OperationalResourceType[], limit: number): Promise<OperationalHit[]> {
  const tenantId = await resolveOperationalTenantId(cookie);
  if (!tenantId) return [];
  const chosen = types.length ? types : [...OPERATIONAL_RESOURCE_TYPES];
  const term = query.trim().toLowerCase();
  const results = await Promise.all(chosen.map((type) => scopedRows(cookie, tenantId, type, MAX_SCAN)));
  return results.flatMap((result) => result.rows).filter((item) => !term || [item.title, item.subtitle, item.status, item.id].some((value) => value?.toLowerCase().includes(term))).sort((a,b) => Date.parse(b.updatedAt ?? '') - Date.parse(a.updatedAt ?? '')).slice(0, Math.max(1, Math.min(limit, 200)));
}
export function parseOperationalTypes(values: string[]): OperationalResourceType[] {
  const allowed = new Set<string>(OPERATIONAL_RESOURCE_TYPES);
  return [...new Set(values.map((value) => value.trim().toUpperCase()).filter((value): value is OperationalResourceType => allowed.has(value)))];
}
