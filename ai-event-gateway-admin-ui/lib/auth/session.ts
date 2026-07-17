export const AUTH_SESSION_CHANGED_EVENT = 'ai-event-gateway-admin:auth-session-changed';
export const UNAUTHORIZED_EVENT = 'ai-event-gateway-admin:unauthorized';

export type AuthSessionChangeReason =
  | 'login'
  | 'user-updated'
  | 'tenant-changed'
  | 'logout'
  | 'cleared';

export interface AuthSessionChangedDetail {
  reason: AuthSessionChangeReason;
  sessionChanged: boolean;
  tenantChanged: boolean;
}

let csrfState: { headerName: string; parameterName: string; token: string } | null = null;

function isBrowser(): boolean {
  return typeof window !== 'undefined';
}

export function getCsrfState(): { headerName: string; parameterName: string; token: string } | null {
  return csrfState;
}

export function saveCsrfState(value: { headerName: string; parameterName: string; token: string }): void {
  csrfState = value;
}

export function clearCsrfState(): void {
  csrfState = null;
}

export function notifyAuthSessionChanged(
  reason: AuthSessionChangeReason,
  options: { tenantChanged?: boolean } = {}
): void {
  if (!isBrowser()) return;
  window.dispatchEvent(new CustomEvent<AuthSessionChangedDetail>(AUTH_SESSION_CHANGED_EVENT, {
    detail: {
      reason,
      sessionChanged: true,
      tenantChanged: Boolean(options.tenantChanged)
    }
  }));
}

export function clearAuthSession(reason: AuthSessionChangeReason = 'cleared'): void {
  clearCsrfState();
  notifyAuthSessionChanged(reason);
}

export function dispatchUnauthorized(): void {
  if (!isBrowser()) return;
  window.dispatchEvent(new CustomEvent(UNAUTHORIZED_EVENT));
}
