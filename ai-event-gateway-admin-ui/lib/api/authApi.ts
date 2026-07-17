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
  AdminSessionResponse,
  AdminSessionsResponse,
  AdminSessionRevocationResponse,
  AdminSecurityAuditResponse,
  AdminTenantsResponse,
  AdminUser,
  LoginRequest
} from '@/lib/types/admin';

const AUTH_BASE = '/api/auth';

function toAdminUser(session: AdminSessionResponse): AdminUser {
  return {
    authenticationType: session.authenticationType,
    userId: session.userId,
    username: session.username,
    displayName: session.displayName,
    roles: session.roles,
    permissions: session.permissions,
    allowedTenantIds: session.allowedTenantIds,
    selectedTenantId: session.selectedTenantId,
    authenticatedAt: session.authenticatedAt,
    expiresAt: session.expiresAt
  };
}

async function readBody(response: Response): Promise<unknown> {
  const contentType = response.headers.get('content-type') ?? '';
  if (contentType.includes('application/json')) return response.json();
  const text = await response.text();
  return text || undefined;
}

function errorMessage(body: unknown, fallback: string): string {
  if (typeof body === 'object' && body !== null) {
    const record = body as Record<string, unknown>;
    for (const key of ['message', 'error', 'detail']) {
      if (typeof record[key] === 'string' && record[key]) return record[key];
    }
  }
  return fallback;
}

async function rawAuthFetch<T>(path: string, init: RequestInit = {}, notifyUnauthorized = true): Promise<T> {
  const response = await fetch(`${AUTH_BASE}${path}`, {
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
    if (response.status === 401 && notifyUnauthorized) dispatchUnauthorized();
    throw new ApiError(errorMessage(body, `${init.method ?? 'GET'} ${path} failed`), response.status, body);
  }
  return body as T;
}

export async function refreshCsrfToken(force = false): Promise<AdminCsrfResponse> {
  const cached = getCsrfState();
  if (!force && cached) return cached;
  const csrf = await rawAuthFetch<AdminCsrfResponse>('/csrf', {}, false);
  saveCsrfState(csrf);
  return csrf;
}

async function authMutation<T>(path: string, body?: unknown, notifyUnauthorized = true): Promise<T> {
  const csrf = await refreshCsrfToken();
  try {
    return await rawAuthFetch<T>(path, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        [csrf.headerName]: csrf.token
      },
      body: body === undefined ? undefined : JSON.stringify(body)
    }, notifyUnauthorized);
  } catch (error) {
    if (error instanceof ApiError && error.status === 403) {
      clearCsrfState();
      const renewed = await refreshCsrfToken(true);
      return rawAuthFetch<T>(path, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          [renewed.headerName]: renewed.token
        },
        body: body === undefined ? undefined : JSON.stringify(body)
      }, notifyUnauthorized);
    }
    throw error;
  }
}

export async function csrfHeader(): Promise<Record<string, string>> {
  const csrf = await refreshCsrfToken();
  return { [csrf.headerName]: csrf.token };
}

export const authApi = {
  csrf: () => refreshCsrfToken(true),

  async login(payload: LoginRequest): Promise<AdminUser> {
    const session = await authMutation<AdminSessionResponse>('/login', payload, false);
    notifyAuthSessionChanged('login');
    return toAdminUser(session);
  },

  async me(): Promise<AdminUser> {
    const session = await rawAuthFetch<AdminSessionResponse>('/me');
    return toAdminUser(session);
  },

  permissions: () => rawAuthFetch<AdminPermissionsResponse>('/permissions'),

  tenants: () => rawAuthFetch<AdminTenantsResponse>('/tenants'),

  sessions: () => rawAuthFetch<AdminSessionsResponse>('/sessions'),

  revokeSession: (sessionReference: string) => authMutation<AdminSessionRevocationResponse>(
    `/sessions/${encodeURIComponent(sessionReference)}/revoke`
  ),

  securityAudit: (limit = 100) => rawAuthFetch<AdminSecurityAuditResponse>(
    `/security-audit?limit=${Math.max(1, Math.min(limit, 500))}`
  ),

  async selectTenant(tenantId: string): Promise<AdminUser> {
    const session = await authMutation<AdminSessionResponse>('/select-tenant', { tenantId });
    notifyAuthSessionChanged('tenant-changed', { tenantChanged: true });
    return toAdminUser(session);
  },

  async logout(): Promise<void> {
    await authMutation('/logout', undefined, false);
    clearCsrfState();
    notifyAuthSessionChanged('logout');
  }
};
