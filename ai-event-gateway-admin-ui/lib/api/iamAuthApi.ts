import { ApiError } from '@/lib/api/errors';
import {
  clearCsrfState,
  getCsrfState,
  notifyAuthSessionChanged,
  saveCsrfState,
} from '@/lib/auth/session';
import type { AdminCsrfResponse, AdminUser, LoginRequest } from '@/lib/types/admin';
import type { IamLoginResponse, IamUiSession, MfaEnrollment } from '@/lib/iam/types';
import { createUuid } from '@/lib/utils/uuid';

const SESSION_BASE = '/api/session';
const CSRF_ERROR_CODES = new Set([
  'AUTH_CSRF_TOKEN_MISSING',
  'AUTH_CSRF_TOKEN_INVALID',
  'AUTH_CSRF_TOKEN_EXPIRED',
]);

async function read(response: Response): Promise<unknown> {
  const type = response.headers.get('content-type') ?? '';
  if (type.includes('json')) return response.json();
  const text = await response.text();
  return text || undefined;
}

function errorFields(body: unknown): { message?: string; code?: string; correlationId?: string } {
  if (typeof body !== 'object' || body === null || Array.isArray(body)) return {};
  const record = body as Record<string, unknown>;
  const nested = typeof record.error === 'object' && record.error !== null
    ? record.error as Record<string, unknown>
    : {};
  const message = record.message ?? nested.message;
  const code = record.code ?? record.error_code ?? record.errorCode ?? nested.code;
  const correlationId = record.correlationId ?? record.correlation_id ?? record.traceId ?? nested.correlationId;
  return {
    message: typeof message === 'string' ? message : undefined,
    code: typeof code === 'string' ? code : undefined,
    correlationId: typeof correlationId === 'string' ? correlationId : undefined,
  };
}

async function raw<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`${SESSION_BASE}${path}`, {
    ...init,
    credentials: 'include',
    cache: 'no-store',
    headers: { Accept: 'application/json', ...init.headers },
  });
  const body = await read(response);
  if (!response.ok) {
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

async function csrf(force = false): Promise<AdminCsrfResponse> {
  const current = getCsrfState();
  if (current && !force) return current;
  const value = await raw<AdminCsrfResponse>('/csrf');
  saveCsrfState(value);
  return value;
}

function isCsrfFailure(error: unknown): error is ApiError {
  return error instanceof ApiError
    && error.status === 403
    && typeof error.code === 'string'
    && CSRF_ERROR_CODES.has(error.code);
}

async function mutate<T>(path: string, body?: unknown): Promise<T> {
  let token = await csrf();
  const idempotencyKey = createUuid();
  const call = () => raw<T>(path, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      [token.headerName]: token.token,
      'Idempotency-Key': idempotencyKey,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  try {
    return await call();
  } catch (error) {
    if (!isCsrfFailure(error)) throw error;
    clearCsrfState();
    token = await csrf(true);
    return call();
  }
}

function canonicalUser(session: IamUiSession): AdminUser {
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
    authenticationMethods: session.authenticationMethods,
  };
}

async function afterSessionRotation<T>(reason: 'login' | 'tenant-changed' | 'password-changed', action: () => Promise<T>): Promise<T> {
  const result = await action();
  clearCsrfState();
  notifyAuthSessionChanged(reason, { tenantChanged: reason === 'tenant-changed' });
  return result;
}

export const iamAuthApi = {
  login: (payload: LoginRequest) => afterSessionRotation('login', () => mutate<IamLoginResponse>('/login', {
    username: payload.username,
    password: payload.password,
  })),
  verifyMfa: (challengeId: string, code: string, recoveryCode = false) =>
    afterSessionRotation('login', () => mutate<IamLoginResponse>('/mfa/verify', { challengeId, code, recoveryCode })),
  uiSession: async () => canonicalUser(await raw<IamUiSession>('')),
  preflight: () => raw('/preflight'),
  logout: async () => {
    await mutate<void>('/logout');
    clearCsrfState();
    notifyAuthSessionChanged('logout');
  },
  forgot: (username: string) => mutate<void>('/forgot-password', { username }),
  reset: (token: string, newPassword: string) => mutate<void>('/reset-password', { token, newPassword }),
  activateInvitation: (token: string, newPassword: string) => mutate<void>('/activate-invitation', { token, newPassword }),
  beginMfaEnrollment: (accountLabel: string, issuer: string) => mutate<MfaEnrollment>('/mfa/enrollment/start', { accountLabel, issuer }),
  confirmMfaEnrollment: (methodId: string, code: string, acknowledgeRecoveryCodesSaved: boolean, expectedVersion: number) =>
    afterSessionRotation('login', () => mutate<void>('/mfa/enrollment/confirm', { methodId, code, acknowledgeRecoveryCodesSaved, expectedVersion })),
  changePassword: (currentPassword: string, newPassword: string, expectedVersion: number) =>
    afterSessionRotation('password-changed', () => mutate<void>('/change-password', {
      currentPassword,
      newPassword,
      expectedVersion,
    })),
};
