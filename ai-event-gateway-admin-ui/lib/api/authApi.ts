import { ApiError } from '@/lib/api/errors';
import {
  clearCsrfState,
  dispatchUnauthorized,
  getCsrfState,
  notifyAuthSessionChanged,
  saveCsrfState
} from '@/lib/auth/session';
import type {
  AdminCsrfResponse,
  AdminPermissionsResponse,
  AdminTenantsResponse,
  AdminUser,
  LoginRequest
} from '@/lib/types/admin';
import type { FederationProviderPublic, IamLoginResponse, IamUiSession, OidcStartResponse } from '@/lib/iam/types';
import { createUuid } from '@/lib/utils/uuid';

/**
 * R2 compatibility facade for callers that only need canonical browser-session
 * authentication and CSRF. This module no longer represents a second Core
 * authentication plane; every request is sent to /api/session.
 */
const SESSION_BASE = '/api/session';
const CSRF_ERROR_CODES = new Set([
  'AUTH_CSRF_TOKEN_MISSING',
  'AUTH_CSRF_TOKEN_INVALID',
  'AUTH_CSRF_TOKEN_EXPIRED'
]);

async function readBody(response: Response): Promise<unknown> {
  const contentType = response.headers.get('content-type') ?? '';
  if (contentType.includes('application/json')) return response.json();
  const text = await response.text();
  return text || undefined;
}

function errorFields(body: unknown): { message?: string; code?: string; correlationId?: string } {
  if (typeof body !== 'object' || body === null || Array.isArray(body)) return {};
  const record = body as Record<string, unknown>;
  const nested = typeof record.error === 'object' && record.error !== null
    ? record.error as Record<string, unknown>
    : {};
  const message = record.message ?? nested.message ?? record.detail;
  const code = record.code ?? record.error_code ?? record.errorCode ?? nested.code;
  const correlationId = record.correlationId ?? record.correlation_id ?? record.traceId ?? nested.correlationId;
  return {
    message: typeof message === 'string' ? message : undefined,
    code: typeof code === 'string' ? code : undefined,
    correlationId: typeof correlationId === 'string' ? correlationId : undefined,
  };
}

async function rawSessionFetch<T>(path: string, init: RequestInit = {}, notifyUnauthorized = true): Promise<T> {
  const response = await fetch(`${SESSION_BASE}${path}`, {
    ...init,
    credentials: 'include',
    cache: 'no-store',
    headers: {
      Accept: 'application/json',
      ...init.headers
    }
  });
  const body = await readBody(response);
  if (!response.ok) {
    if (response.status === 401 && notifyUnauthorized) {
      dispatchUnauthorized({ status: 401, path: `${SESSION_BASE}${path}`, plane: 'canonical-session' });
    }
    const fields = errorFields(body);
    throw new ApiError(
      fields.message ?? `${init.method ?? 'GET'} ${path || '/'} failed`,
      response.status,
      body,
      fields.code,
      response.headers.get('x-correlation-id') ?? fields.correlationId,
    );
  }
  return body as T;
}

function toAdminUser(session: IamUiSession): AdminUser {
  return {
    userId: session.userId,
    username: session.username,
    displayName: session.displayName,
    roles: session.roles,
    permissions: session.permissions,
    permissionScopes: session.permissionScopes ?? {},
    allowedTenantIds: session.tenantChoices.map(tenant => tenant.tenantId),
    selectedTenantId: session.selectedTenantId,
    authenticatedAt: session.authenticatedAt,
    expiresAt: session.expiresAt,
    tenantChoices: session.tenantChoices,
    requiredActions: session.requiredActions,
    credentialVersion: session.credentialVersion,
    authenticationMethods: session.authenticationMethods
  };
}

export async function refreshCsrfToken(force = false): Promise<AdminCsrfResponse> {
  const cached = getCsrfState();
  if (!force && cached) return cached;
  const csrf = await rawSessionFetch<AdminCsrfResponse>('/csrf', {}, false);
  saveCsrfState(csrf);
  return csrf;
}

async function sessionMutation<T>(path: string, body?: unknown, notifyUnauthorized = true): Promise<T> {
  let csrf = await refreshCsrfToken();
  const idempotencyKey = createUuid();
  const invoke = () => rawSessionFetch<T>(path, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      [csrf.headerName]: csrf.token,
      'Idempotency-Key': idempotencyKey
    },
    body: body === undefined ? undefined : JSON.stringify(body)
  }, notifyUnauthorized);

  try {
    return await invoke();
  } catch (error) {
    const csrfFailure = error instanceof ApiError
      && error.status === 403
      && typeof error.code === 'string'
      && CSRF_ERROR_CODES.has(error.code);
    if (!csrfFailure) throw error;
    clearCsrfState();
    csrf = await refreshCsrfToken(true);
    return invoke();
  }
}

export async function csrfHeader(): Promise<Record<string, string>> {
  const csrf = await refreshCsrfToken();
  return { [csrf.headerName]: csrf.token };
}

/**
 * Transitional API surface retained for older Admin UI call sites. R3/R5 can
 * remove this facade after all callers use the canonical IAM API directly.
 */
async function loadCanonicalUser(): Promise<AdminUser> {
  return toAdminUser(await rawSessionFetch<IamUiSession>('', {}, false));
}

export const authApi = {
  csrf: () => refreshCsrfToken(true),

  federationProviders: (tenant: string): Promise<FederationProviderPublic[]> =>
    rawSessionFetch<FederationProviderPublic[]>(`/federation/providers?tenant=${encodeURIComponent(tenant.trim())}`, {}, false),

  startOidc: (tenantId: string, providerId: string, returnTo = '/'): Promise<OidcStartResponse> =>
    sessionMutation<OidcStartResponse>('/federation/oidc/start', { tenantId, providerId, returnTo }, false),

  async login(payload: LoginRequest): Promise<AdminUser> {
    const result = await sessionMutation<IamLoginResponse>('/login', {
      username: payload.username,
      password: payload.password
    }, false);
    clearCsrfState();
    notifyAuthSessionChanged('login');
    if (result.state !== 'AUTHENTICATED') {
      throw new ApiError(`Canonical login requires additional action: ${result.state}`, 409, result);
    }
    return loadCanonicalUser();
  },

  async me(): Promise<AdminUser> {
    return loadCanonicalUser();
  },

  async permissions(): Promise<AdminPermissionsResponse> {
    const user = await loadCanonicalUser();
    return { roles: user.roles, permissions: user.permissions ?? [] };
  },

  async tenants(): Promise<AdminTenantsResponse> {
    const user = await loadCanonicalUser();
    return {
      selectedTenantId: user.selectedTenantId ?? '',
      tenants: (user.tenantChoices ?? []).map(tenant => ({
        tenantId: tenant.tenantId,
        tenantCode: tenant.tenantCode,
        tenantName: tenant.tenantName,
        membershipStatus: tenant.membershipStatus,
        selected: tenant.tenantId === user.selectedTenantId
      }))
    };
  },

  async logout(): Promise<void> {
    await sessionMutation('/logout', undefined, false);
    clearCsrfState();
    notifyAuthSessionChanged('logout');
  }
};
