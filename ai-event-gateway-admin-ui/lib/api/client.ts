import { csrfHeader } from '@/lib/api/authApi';
import { ApiError } from '@/lib/api/errors';
import { clearCsrfState, dispatchUnauthorized } from '@/lib/auth/session';
import { readStoredRootAdministrationTenant } from '@/lib/auth/workspaceTenantContext';
import { getPublicEnv } from '@/lib/constants/env';
import {
  STANDARD_SUCCESS_CODE,
  isLegacyApiEnvelope,
  isNotFoundOrUnsupportedCode,
  isRecord,
  isStandardApiEnvelope,
  isUnauthorizedApiCode,
  standardEnvelopeCode
} from '@/lib/api/envelope';

export { ApiError } from '@/lib/api/errors';

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
export type ApiClientPlane = 'legacy' | 'core' | 'netty';

let selectedCoreTenantId = '';

export function setCoreTenantContext(tenantId?: string | null): void {
  selectedCoreTenantId = String(tenantId ?? '').trim();
}

export function getCoreTenantContext(): string {
  // During a hard browser reload child effects may execute before AuthProvider has re-applied the
  // root administration scope. The persisted value is only a transport fallback; the server still
  // validates X-Tenant-Id against the authenticated INSTANCE_ROOT principal.
  return selectedCoreTenantId || readStoredRootAdministrationTenant();
}

export function requireCoreTenantContext(explicitTenantId?: string | null): string {
  const explicit = String(explicitTenantId ?? '').trim();
  const selected = getCoreTenantContext();
  if (explicit && selected && explicit !== selected) {
    throw new ApiError(
      `Request tenant ${explicit} does not match selected workspace ${selected}.`,
      409,
      { selectedTenantId: selected, explicitTenantId: explicit },
      'TENANT_CONTEXT_MISMATCH'
    );
  }
  const resolved = explicit || selected;
  if (!resolved) {
    throw new ApiError(
      'Select a workspace before using tenant-scoped administration APIs.',
      400,
      undefined,
      'TENANT_CONTEXT_REQUIRED'
    );
  }
  return resolved;
}

export interface ApiRequestOptions {
  method?: HttpMethod;
  body?: unknown;
  query?: Record<string, string | number | boolean | undefined | null>;
  signal?: AbortSignal;
  headers?: Record<string, string>;
  skipAuth?: boolean;
  /** @deprecated Cookie sessions do not use refresh tokens. */
  skipRefresh?: boolean;
  requireStandardEnvelope?: boolean;
  /** Marks a Core request as scoped to the currently selected Tenant workspace. */
  tenantScoped?: boolean;
}

function browserSetTimeout(callback: () => void, delayMs: number): ReturnType<typeof setTimeout> {
  return globalThis.setTimeout(callback, delayMs);
}

function browserClearTimeout(timer: ReturnType<typeof setTimeout>): void {
  globalThis.clearTimeout(timer);
}

export function isNotFoundOrUnsupportedApiError(error: unknown): error is ApiError {
  return error instanceof ApiError && isNotFoundOrUnsupportedCode(error.code);
}

function joinUrl(baseUrl: string, path: string): string {
  if (path.startsWith('http://') || path.startsWith('https://')) return path;
  const normalizedBase = baseUrl.replace(/\/+$/, '');
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  return normalizedBase ? `${normalizedBase}${normalizedPath}` : normalizedPath;
}

function buildUrl(baseUrl: string, path: string, query?: ApiRequestOptions['query']): string {
  const joinedUrl = joinUrl(baseUrl, path);
  const browserOrigin = typeof window === 'undefined' ? 'http://localhost' : window.location.origin;
  const url = new URL(joinedUrl, browserOrigin);
  Object.entries(query ?? {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, String(value));
  });
  if (url.origin === browserOrigin && !joinedUrl.startsWith('http://') && !joinedUrl.startsWith('https://')) {
    return `${url.pathname}${url.search}`;
  }
  return url.toString();
}

async function readResponseBody(response: Response): Promise<unknown> {
  const contentType = response.headers.get('content-type') ?? '';
  if (contentType.includes('application/json')) return response.json();
  const text = await response.text();
  return text || undefined;
}

function extractErrorMessage(body: unknown, fallback: string): { message: string; code?: string; correlationId?: string } {
  if (isStandardApiEnvelope(body)) return { message: body.message || fallback, code: body.code };
  if (isLegacyApiEnvelope(body) && body.error) {
    return { message: body.error.message ?? fallback, code: body.error.code, correlationId: body.traceId };
  }
  if (isRecord(body)) {
    const nested = isRecord(body.error) ? body.error : undefined;
    const messageValue = body.message ?? nested?.message ?? body.detail ?? body.error;
    const codeValue = body.code ?? body.error_code ?? body.errorCode ?? nested?.code;
    const correlationValue = body.correlationId ?? body.correlation_id ?? body.traceId ?? nested?.correlationId;
    return {
      message: typeof messageValue === 'string' && messageValue.trim() ? messageValue : fallback,
      code: typeof codeValue === 'string' && codeValue.trim() ? codeValue : undefined,
      correlationId: typeof correlationValue === 'string' && correlationValue.trim() ? correlationValue : undefined,
    };
  }
  return { message: fallback };
}

function unwrapResponseInternal<T>(body: unknown, status?: number, requireStandardEnvelope = false): T {
  if (isStandardApiEnvelope(body)) {
    if (body.code !== STANDARD_SUCCESS_CODE) {
      throw new ApiError(body.message || `API returned code=${body.code}.`, status, body, body.code);
    }
    return body.data as T;
  }
  if (requireStandardEnvelope) {
    throw new ApiError(
      'Expected standard API envelope with code/message/data/timestamp.',
      status,
      body,
      'API_ENVELOPE_REQUIRED'
    );
  }
  if (isLegacyApiEnvelope(body)) {
    if (!body.success) {
      throw new ApiError(body.error?.message ?? 'API returned success=false.', status, body, body.error?.code);
    }
    return body.data as T;
  }
  return body as T;
}

function apiBaseUrlFor(plane: ApiClientPlane): string {
  const env = getPublicEnv();
  if (plane === 'core') return env.coreApiBaseUrl;
  if (plane === 'netty') return env.nettyApiBaseUrl;
  return env.apiBaseUrl;
}

function isMutation(method: HttpMethod): boolean {
  return method !== 'GET';
}

const AUDIT_REASON_HEADER = 'x-audit-reason';
const AUDIT_REASON_UTF8_PREFIX = 'od-utf8:';
const HTTP_VISIBLE_ASCII = /^[\x20-\x7E]*$/;

function encodeAuditReasonHeaderValue(value: string): string {
  const normalized = value.trim();
  if (!normalized || normalized.startsWith(AUDIT_REASON_UTF8_PREFIX) || HTTP_VISIBLE_ASCII.test(normalized)) {
    return normalized;
  }
  return `${AUDIT_REASON_UTF8_PREFIX}${encodeURIComponent(normalized)}`;
}

function normalizeOutboundHeaders(headers: Record<string, string>): void {
  for (const [name, value] of Object.entries(headers)) {
    if (name.toLowerCase() === AUDIT_REASON_HEADER) {
      headers[name] = encodeAuditReasonHeaderValue(value);
    }
  }
}

const CSRF_FAILURE_CODES = new Set([
  'AUTH_CSRF_TOKEN_MISSING',
  'AUTH_CSRF_TOKEN_INVALID',
  'AUTH_CSRF_TOKEN_EXPIRED',
  'CSRF_TOKEN_INVALID',
  'CSRF_TOKEN_EXPIRED',
  'CSRF_REQUIRED',
  'INVALID_CSRF_TOKEN',
]);

function isVerifiedCsrfFailure(status: number, body: unknown): boolean {
  if (status !== 403) return false;
  const extracted = extractErrorMessage(body, '');
  const code = standardEnvelopeCode(body) ?? extracted.code;
  if (typeof code === 'string' && CSRF_FAILURE_CODES.has(code)) return true;

  // Older /admin/** Core security chains masked Missing/Invalid CSRF as a generic
  // Atomic Permission denial. Retry exactly once with a freshly issued CSRF token
  // so rolling upgrades and stale browser state recover without weakening RBAC.
  return code === 'FORBIDDEN' && extracted.message === 'Atomic Permission authorization denied.';
}

function tenantFromPath(path: string): string {
  try {
    const url = new URL(path, 'http://opendispatch.local');
    const queryTenant = url.searchParams.get('tenantId')?.trim() ?? '';
    if (queryTenant) return queryTenant;
    const match = url.pathname.match(/\/tenants\/([^/?#]+)/);
    return match?.[1] ? decodeURIComponent(match[1]).trim() : '';
  } catch {
    return '';
  }
}

function isTenantScopedCoreRequest(path: string, options: ApiRequestOptions, plane: ApiClientPlane): boolean {
  return plane === 'core' && (options.tenantScoped === true || path.startsWith('/admin/'));
}

function authoritativeCoreTenant(path: string, options: ApiRequestOptions, plane: ApiClientPlane): string {
  if (!isTenantScopedCoreRequest(path, options, plane)) return '';

  const selectedTenantId = getCoreTenantContext();
  const queryTenant = String(options.query?.tenantId ?? '').trim();
  const pathTenant = tenantFromPath(path);
  const bodyTenant = isRecord(options.body) && typeof options.body.tenantId === 'string'
    ? options.body.tenantId.trim()
    : '';
  const explicitTenants = [queryTenant, pathTenant, bodyTenant].filter(Boolean);
  const explicitTenant = explicitTenants[0] ?? '';

  for (const candidate of explicitTenants) {
    if (candidate !== explicitTenant) {
      throw new ApiError(
        `Request contains conflicting Tenant values (${explicitTenant}, ${candidate}).`,
        409,
        { explicitTenants },
        'TENANT_CONTEXT_MISMATCH'
      );
    }
  }
  if (selectedTenantId && explicitTenant && explicitTenant !== selectedTenantId) {
    throw new ApiError(
      `Request tenant ${explicitTenant} does not match selected workspace ${selectedTenantId}.`,
      409,
      { selectedTenantId, explicitTenant },
      'TENANT_CONTEXT_MISMATCH'
    );
  }
  const resolved = explicitTenant || selectedTenantId;
  if (!resolved) return requireCoreTenantContext();
  return resolved;
}

function authoritativeCoreQuery(
  path: string,
  options: ApiRequestOptions,
  selectedTenantId: string,
): ApiRequestOptions['query'] {
  if (!selectedTenantId) return options.query;
  const queryTenant = String(options.query?.tenantId ?? '').trim();
  const pathTenant = tenantFromPath(path);
  if (pathTenant || queryTenant) return options.query;
  return { ...(options.query ?? {}), tenantId: selectedTenantId };
}

async function executeFetch<T>(
  path: string,
  options: ApiRequestOptions,
  plane: ApiClientPlane = 'legacy',
  csrfRetried = false
): Promise<T> {
  const requireStandardEnvelope = options.requireStandardEnvelope ?? plane !== 'legacy';
  const env = getPublicEnv();
  const method = options.method ?? 'GET';
  const controller = new AbortController();
  const timeout = browserSetTimeout(() => controller.abort(), env.requestTimeoutMs);
  const headers: Record<string, string> = { Accept: 'application/json', ...options.headers };

  if (options.body !== undefined) headers['Content-Type'] = 'application/json';
  // Every browser mutation sent to the Core plane participates in the canonical
  // cookie/header CSRF contract. Do not couple CSRF protection to the optional
  // NEXT_PUBLIC_AUTH_ENABLED presentation flag or to a particular URL prefix:
  // tenant-scoped APIs such as /api/integrations/** are protected by the same
  // Spring Security CSRF chain as /admin/**. Sending the token to Core routes
  // that do not require it is harmless; omitting it from a protected mutation
  // fails closed with AUTH_CSRF_TOKEN_MISSING.
  const requiresCsrf = plane === 'core' && isMutation(method) && !options.skipAuth;
  if (requiresCsrf) Object.assign(headers, await csrfHeader());

  if (options.signal) options.signal.addEventListener('abort', () => controller.abort(), { once: true });

  try {
    const selectedTenantId = authoritativeCoreTenant(path, options, plane);
    if (selectedTenantId) headers['X-Tenant-Id'] = selectedTenantId;
    const requestQuery = authoritativeCoreQuery(path, options, selectedTenantId);
    normalizeOutboundHeaders(headers);
    const response = await fetch(buildUrl(apiBaseUrlFor(plane), path, requestQuery), {
      method,
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      credentials: 'include',
      cache: 'no-store',
      signal: controller.signal
    });
    const body = await readResponseBody(response);
    const bodyCode = standardEnvelopeCode(body);

    if ((response.status === 401 || isUnauthorizedApiCode(bodyCode)) && !options.skipAuth) {
      dispatchUnauthorized({ status: response.status, path, plane, ...(bodyCode ? { code: bodyCode } : {}) });
    }

    if (requiresCsrf && !csrfRetried && isVerifiedCsrfFailure(response.status, body)) {
      clearCsrfState();
      return executeFetch<T>(path, options, plane, true);
    }

    if (!response.ok) {
      const extracted = extractErrorMessage(body, `${method} ${path} failed`);
      const correlationId = response.headers.get('x-correlation-id')
        ?? response.headers.get('x-request-id')
        ?? extracted.correlationId;
      throw new ApiError(extracted.message, response.status, body, extracted.code, correlationId ?? undefined);
    }

    return unwrapResponseInternal<T>(body, response.status, requireStandardEnvelope);
  } catch (error) {
    if (error instanceof ApiError) throw error;
    if (error instanceof DOMException && error.name === 'AbortError') throw new ApiError(`${method} ${path} timeout`, 408);
    throw new ApiError(error instanceof Error ? error.message : 'Unknown API error');
  } finally {
    browserClearTimeout(timeout);
  }
}

export function apiRequest<T>(path: string, options: ApiRequestOptions = {}): Promise<T> {
  return executeFetch<T>(path, options, 'legacy');
}

export function apiRequestFor<T>(plane: ApiClientPlane, path: string, options: ApiRequestOptions = {}): Promise<T> {
  return executeFetch<T>(path, options, plane);
}

export function apiGet<T>(path: string, query?: ApiRequestOptions['query'], options?: Omit<ApiRequestOptions, 'method' | 'query'>): Promise<T> {
  return apiRequest<T>(path, { ...options, method: 'GET', query });
}

export function apiPost<T>(path: string, body?: unknown, options?: Omit<ApiRequestOptions, 'method' | 'body'>): Promise<T> {
  return apiRequest<T>(path, { ...options, method: 'POST', body });
}

export function coreApiGet<T>(path: string, query?: ApiRequestOptions['query'], options?: Omit<ApiRequestOptions, 'method' | 'query'>): Promise<T> {
  return apiRequestFor<T>('core', path, { ...options, method: 'GET', query });
}

export function coreApiPost<T>(path: string, body?: unknown, options?: Omit<ApiRequestOptions, 'method' | 'body'>): Promise<T> {
  return apiRequestFor<T>('core', path, { ...options, method: 'POST', body });
}

export function nettyApiGet<T>(path: string, query?: ApiRequestOptions['query'], options?: Omit<ApiRequestOptions, 'method' | 'query'>): Promise<T> {
  return apiRequestFor<T>('netty', path, { ...options, method: 'GET', query });
}

export function nettyApiPost<T>(path: string, body?: unknown, options?: Omit<ApiRequestOptions, 'method' | 'body'>): Promise<T> {
  return apiRequestFor<T>('netty', path, { ...options, method: 'POST', body });
}

export function coreApiPut<T>(path: string, body?: unknown, options?: Omit<ApiRequestOptions, 'method' | 'body'>): Promise<T> {
  return apiRequestFor<T>('core', path, { ...options, method: 'PUT', body });
}

export function coreApiDelete<T>(path: string, options?: Omit<ApiRequestOptions, 'method' | 'body'>): Promise<T> {
  return apiRequestFor<T>('core', path, { ...options, method: 'DELETE' });
}

export function coreTenantApiGet<T>(path: string, query?: ApiRequestOptions['query'], options?: Omit<ApiRequestOptions, 'method' | 'query' | 'tenantScoped'>): Promise<T> {
  return apiRequestFor<T>('core', path, { ...options, method: 'GET', query, tenantScoped: true });
}

export function coreTenantApiPost<T>(path: string, body?: unknown, options?: Omit<ApiRequestOptions, 'method' | 'body' | 'tenantScoped'>): Promise<T> {
  return apiRequestFor<T>('core', path, { ...options, method: 'POST', body, tenantScoped: true });
}

export function coreTenantApiPut<T>(path: string, body?: unknown, options?: Omit<ApiRequestOptions, 'method' | 'body' | 'tenantScoped'>): Promise<T> {
  return apiRequestFor<T>('core', path, { ...options, method: 'PUT', body, tenantScoped: true });
}

export function coreTenantApiDelete<T>(path: string, options?: Omit<ApiRequestOptions, 'method' | 'body' | 'tenantScoped'>): Promise<T> {
  return apiRequestFor<T>('core', path, { ...options, method: 'DELETE', tenantScoped: true });
}

