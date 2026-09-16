import { ApiError } from '@/lib/api/errors';

function detailValue(detail: unknown, keys: string[]): string | undefined {
  if (!detail || typeof detail !== 'object') return undefined;
  const record = detail as Record<string, unknown>;
  for (const key of keys) {
    const value = record[key];
    if (typeof value === 'string' && value.trim()) return value.trim();
  }
  const error = record.error;
  if (error && typeof error === 'object') {
    const nested = error as Record<string, unknown>;
    for (const key of keys) {
      const value = nested[key];
      if (typeof value === 'string' && value.trim()) return value.trim();
    }
  }
  return undefined;
}

export function formatIamError(cause: unknown, fallback: string): string {
  if (!(cause instanceof ApiError)) return cause instanceof Error ? cause.message : fallback;
  const code = cause.code ?? detailValue(cause.detail, ['code', 'errorCode']);
  const correlation = cause.correlationId ?? detailValue(cause.detail, ['correlationId', 'traceId', 'requestId']);
  const friendly: Record<string, string> = {
    DEPARTMENT_DISABLE_BLOCKED: 'This Department still has people, child Departments, or owned Groups. Move those dependencies first, then try again.',
    GROUP_DISABLE_BLOCKED: 'This Group still has people or child Groups. Remove or move those dependencies first, then try again.',
    DEPARTMENT_CODE_CONFLICT: 'An active Department already uses this code. Choose another code. A previously deleted Department does not reserve its code.',
    DEPARTMENT_ID_CONFLICT: 'This Department internal identifier already exists. Retry the create action so OpenDispatch can generate a fresh identifier.',
    GROUP_CODE_CONFLICT: 'An active Group already uses this code. Choose another code. A previously deleted Group does not reserve its code.',
    GROUP_ID_CONFLICT: 'This Group internal identifier already exists. Retry the create action so OpenDispatch can generate a fresh identifier.',
    TENANT_MEMBERSHIP_ALREADY_EXISTS: 'This person is already admitted to this workspace. Refresh People before trying to add them again.',
    IDENTITY_LAST_TENANT_ADMIN_PROTECTED: 'This person is the final effective Tenant Administrator. Assign another active Tenant Administrator before removing them from the workspace.',
    IDENTITY_USERNAME_CONFLICT: 'That sign-in name already exists. Search for the existing person and add them to this workspace instead of creating a duplicate account.',
    IDENTITY_EMAIL_CONFLICT: 'That email address already belongs to an existing identity. Search for the existing person and add them to this workspace.',
    IDENTITY_EXISTING_USER_NOT_ADMITTABLE: 'That sign-in identity still exists globally, but its account state does not allow workspace re-admission. Review the identity lifecycle state instead of creating a duplicate account.',
    IDENTITY_ATTRIBUTES_CONFLICT: 'The sign-in name and email belong to different existing identities. Search the global identity directory and select the correct person explicitly.',
    IDENTITY_INITIAL_RESPONSIBILITY_REQUIRED: 'Choose an initial Responsibility for this Person, or explicitly select No application access yet. OpenDispatch will not silently create an interactive account with empty workspace access.',
    IDENTITY_APPLICATION_ACCESS_DEFERRED_WITH_ROLE: 'Application access cannot be both deferred and assigned. Choose a Responsibility or choose No application access yet.',
    RBAC_ROOT_STEP_UP_REQUIRED: 'This platform-level Root action requires recent MFA verification. Sign in again before retrying the platform operation.',
    AUTH_CSRF_TOKEN_MISSING: 'The browser security token was missing. OpenDispatch will refresh the token automatically; retry the action if it was not completed.',
    AUTH_CSRF_TOKEN_INVALID: 'The browser security token expired. OpenDispatch refreshed it automatically; retry the action if it was not completed.',
  };
  const message = code && friendly[code] ? friendly[code] : cause.message;
  const diagnostics = [
    code,
    cause.status ? `HTTP ${cause.status}` : undefined,
    correlation ? `Correlation ${correlation}` : undefined,
  ].filter(Boolean);
  return diagnostics.length ? `${message} (${diagnostics.join(' · ')})` : message;
}
