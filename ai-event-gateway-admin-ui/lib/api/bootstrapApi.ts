import { ApiError } from '@/lib/api/errors';
import { refreshCsrfToken } from '@/lib/api/authApi';
import { clearCsrfState } from '@/lib/auth/session';
import type * as T from '@/lib/iam/types';
import { createIdempotencyKey } from '@/lib/utils/uuid';

const CORE = '/core-api';
const BASE = '/api/bootstrap';
const CSRF_ERROR_CODES = new Set(['AUTH_CSRF_TOKEN_MISSING', 'AUTH_CSRF_TOKEN_INVALID']);

async function bodyOf(response: Response): Promise<unknown> {
  const type = response.headers.get('content-type') ?? '';
  if (type.includes('json')) return response.json();
  const text = await response.text();
  return text || undefined;
}

function apiError(method: string, path: string, response: Response, body: unknown): ApiError {
  const value = (body ?? {}) as Record<string, unknown>;
  const code = String(value.code ?? value.error_code ?? '').trim() || undefined;
  const fallback = `${method} ${path} failed`;
  const message = CSRF_ERROR_CODES.has(code ?? '')
    ? 'The setup security token expired. A fresh token was requested; retry the action.'
    : String(value.message ?? fallback);
  return new ApiError(message, response.status, body, code);
}

async function request<R>(path: string, init: RequestInit = {}): Promise<R> {
  const method = (init.method ?? 'GET').toUpperCase();
  const mutation = method !== 'GET' && method !== 'HEAD';
  const idempotencyKey = mutation ? createIdempotencyKey('bootstrap') : '';

  const invoke = async (forceCsrf: boolean): Promise<R> => {
    const headers: Record<string, string> = {
      Accept: 'application/json',
      ...(init.headers as Record<string, string> | undefined),
    };
    if (mutation) {
      const csrf = await refreshCsrfToken(forceCsrf);
      headers[csrf.headerName] = csrf.token;
      headers['Idempotency-Key'] = idempotencyKey;
    }
    const response = await fetch(`${CORE}${BASE}${path}`, {
      ...init,
      headers,
      credentials: 'include',
      cache: 'no-store',
    });
    const body = await bodyOf(response);
    if (!response.ok) throw apiError(method, path, response, body);
    return body as R;
  };

  try {
    return await invoke(mutation);
  } catch (error) {
    if (mutation && error instanceof ApiError && error.status === 403) {
      clearCsrfState();
      return invoke(true);
    }
    throw error;
  }
}

const json = (body: unknown): RequestInit => ({
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
});

export const bootstrapApi = {
  status: () => request<T.BootstrapStatus>('/status'),
  beginRootMfa: (accountLabel: string, issuer: string) => request<T.MfaEnrollment>('/root/mfa', json({ accountLabel, issuer })),
  confirmRootMfa: (
    methodId: string,
    code: string,
    acknowledgeRecoveryCodesSaved: boolean,
    expectedVersion: number,
  ) => request<T.Session>('/root/mfa/confirm', json({
    methodId,
    code,
    acknowledgeRecoveryCodesSaved,
    expectedVersion,
  })),
  createFirstTenant: (value: Record<string, unknown>) => request<T.Tenant>('/tenant', json(value)),
  createFirstTenantAdmin: (value: Record<string, unknown>) => request<T.User>('/tenant-admin', json(value)),
  complete: (expectedVersion: number) => request<T.BootstrapStatus>('/complete', json({ expectedVersion })),
};
