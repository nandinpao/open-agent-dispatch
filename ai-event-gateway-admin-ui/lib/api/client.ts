import { csrfHeader } from '@/lib/api/authApi';
import { ApiError } from '@/lib/api/errors';
import { clearCsrfState, dispatchUnauthorized } from '@/lib/auth/session';
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
  return selectedCoreTenantId;
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

function extractErrorMessage(body: unknown, fallback: string): { message: string; code?: string } {
  if (isStandardApiEnvelope(body)) return { message: body.message || fallback, code: body.code };
  if (isLegacyApiEnvelope(body) && body.error) {
    return { message: body.error.message ?? fallback, code: body.error.code };
  }
  if (isRecord(body)) {
    const message = body.message ?? body.error ?? body.detail;
    if (typeof message === 'string' && message.trim()) return { message };
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

function tenantFromPath(path: string): string {
  try {
    return new URL(path, 'http://opendispatch.local').searchParams.get('tenantId')?.trim() ?? '';
  } catch {
    return '';
  }
}

function authoritativeCoreQuery(path: string, options: ApiRequestOptions, plane: ApiClientPlane): ApiRequestOptions['query'] {
  if (plane !== 'core' || !path.startsWith('/admin/')) return options.query;
  const selectedTenantId = requireCoreTenantContext();
  const queryTenant = String(options.query?.tenantId ?? '').trim();
  const pathTenant = tenantFromPath(path);
  const bodyTenant = isRecord(options.body) && typeof options.body.tenantId === 'string'
    ? options.body.tenantId.trim()
    : '';
  for (const explicitTenant of [queryTenant, pathTenant, bodyTenant]) {
    if (explicitTenant && explicitTenant !== selectedTenantId) {
      throw new ApiError(
        `Request tenant ${explicitTenant} does not match selected workspace ${selectedTenantId}.`,
        409,
        { selectedTenantId, explicitTenant },
        'TENANT_CONTEXT_MISMATCH'
      );
    }
  }
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
  const requiresCsrf = plane === 'core' && isMutation(method) && !options.skipAuth && env.authEnabled && env.adminAuthMode === 'core-session';
  if (requiresCsrf) Object.assign(headers, await csrfHeader());

  if (options.signal) options.signal.addEventListener('abort', () => controller.abort(), { once: true });

  try {
    const requestQuery = authoritativeCoreQuery(path, options, plane);
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
      dispatchUnauthorized();
    }

    if (response.status === 403 && requiresCsrf && !csrfRetried) {
      clearCsrfState();
      return executeFetch<T>(path, options, plane, true);
    }

    if (!response.ok) {
      const { message, code } = extractErrorMessage(body, `${method} ${path} failed`);
      throw new ApiError(message, response.status, body, code);
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
