import { NextRequest, NextResponse } from 'next/server';
import { makeStandardApiEnvelope, type StandardApiEnvelope } from '@/lib/api/envelope';
import { fetchBackend } from '@/lib/server/backendOrigins';

const HOP_BY_HOP_HEADERS = new Set([
  'connection',
  'keep-alive',
  'proxy-authenticate',
  'proxy-authorization',
  'te',
  'trailer',
  'transfer-encoding',
  'upgrade',
  'host',
  'content-length',
  'content-encoding'
]);

// The browser talks same-origin to the Next.js proxy. Forwarding these browser CORS
// headers to an internal Core/Netty hop incorrectly makes the backend treat the
// server-to-server request as a cross-origin browser request.
const BROWSER_CORS_REQUEST_HEADERS = new Set([
  'origin',
  'access-control-request-method',
  'access-control-request-headers'
]);

export type BackendPlane = 'core' | 'netty' | 'gateway';

function prefixFor(plane: BackendPlane): string {
  if (plane === 'core') return '/core-api';
  if (plane === 'netty') return '/netty-api';
  return '/gateway-api';
}

function envBoolean(name: string, fallback = false): boolean {
  const value = process.env[name];
  if (value === undefined || value === '') return fallback;
  return ['1', 'true', 'yes', 'on'].includes(value.toLowerCase());
}

function firstNonBlank(...values: Array<string | undefined>): string | undefined {
  return values.find((value) => value !== undefined && value.trim() !== '')?.trim();
}

function isRecoveryActionPath(path: string[]): boolean {
  return path.length >= 3 && path[0] === 'admin' && path[1] === 'recovery' && path[2] === 'actions';
}

function isRecoveryApprovalMutationPath(path: string[]): boolean {
  const last = path[path.length - 1];
  return path.length >= 4 && path[0] === 'admin' && path[1] === 'recovery' && path[2] === 'approval-requests' && ['approve', 'reject'].includes(last);
}

function isHighRiskRecoveryActionPath(path: string[]): boolean {
  const last = path[path.length - 1];
  return isRecoveryActionPath(path) && (last === 'dead-letter' || last === 'restore-dead-letter');
}

function isDispatchCutoverPath(path: string[]): boolean {
  return path.length >= 3 && path[0] === 'admin' && path[1] === 'dispatch-governance' && path[2] === 'cutover';
}

function isActionGovernancePath(path: string[]): boolean {
  return path.length >= 3 && path[0] === 'admin' && path[1] === 'dispatch-governance' && path[2] === 'actions';
}

function isActionApprovalPath(path: string[]): boolean {
  const last = path[path.length - 1];
  return isActionGovernancePath(path) && (last === 'approve' || last === 'decide');
}

function isActionAdminPath(path: string[]): boolean {
  const last = path[path.length - 1];
  return isActionGovernancePath(path)
    && (path.includes('catalog') || path.includes('grants') || (path.includes('manual-cases') && last === 'resolve'));
}

function recoveryTokenFor(path: string[]): string | undefined {
  if (isActionApprovalPath(path)) {
    return firstNonBlank(
      process.env.CORE_BACKEND_RECOVERY_APPROVER_TOKEN,
      process.env.CORE_RECOVERY_APPROVER_TOKEN,
      process.env.CORE_RECOVERY_APPROVER_INTERNAL_TOKEN,
      process.env.CORE_BACKEND_RECOVERY_ADMIN_TOKEN,
      process.env.CORE_BACKEND_OPERATOR_TOKEN
    );
  }
  if (isDispatchCutoverPath(path)) {
    return firstNonBlank(
      process.env.CORE_BACKEND_RECOVERY_ADMIN_TOKEN,
      process.env.CORE_RECOVERY_ADMIN_TOKEN,
      process.env.CORE_RECOVERY_ADMIN_INTERNAL_TOKEN,
      process.env.CORE_BACKEND_OPERATOR_TOKEN
    );
  }
  if (isActionAdminPath(path)) {
    return firstNonBlank(
      process.env.CORE_BACKEND_RECOVERY_ADMIN_TOKEN,
      process.env.CORE_RECOVERY_ADMIN_TOKEN,
      process.env.CORE_RECOVERY_ADMIN_INTERNAL_TOKEN,
      process.env.CORE_BACKEND_OPERATOR_TOKEN
    );
  }
  if (isActionGovernancePath(path)) {
    return firstNonBlank(
      process.env.CORE_BACKEND_RECOVERY_OPERATOR_TOKEN,
      process.env.CORE_RECOVERY_OPERATOR_TOKEN,
      process.env.CORE_RECOVERY_OPERATOR_INTERNAL_TOKEN,
      process.env.CORE_BACKEND_OPERATOR_TOKEN
    );
  }
  if (isRecoveryApprovalMutationPath(path)) {
    return firstNonBlank(
      process.env.CORE_BACKEND_RECOVERY_APPROVER_TOKEN,
      process.env.CORE_RECOVERY_APPROVER_TOKEN,
      process.env.CORE_RECOVERY_APPROVER_INTERNAL_TOKEN,
      process.env.CORE_BACKEND_RECOVERY_ADMIN_TOKEN,
      process.env.CORE_RECOVERY_ADMIN_TOKEN,
      process.env.CORE_RECOVERY_ADMIN_INTERNAL_TOKEN,
      process.env.CORE_BACKEND_OPERATOR_TOKEN,
      process.env.CORE_OPERATOR_TOKEN,
      process.env.AI_EVENT_GATEWAY_CORE_OPERATOR_TOKEN
    );
  }
  if (isHighRiskRecoveryActionPath(path)) {
    return firstNonBlank(
      process.env.CORE_BACKEND_RECOVERY_ADMIN_TOKEN,
      process.env.CORE_RECOVERY_ADMIN_TOKEN,
      process.env.CORE_RECOVERY_ADMIN_INTERNAL_TOKEN,
      process.env.CORE_BACKEND_OPERATOR_TOKEN,
      process.env.CORE_OPERATOR_TOKEN,
      process.env.AI_EVENT_GATEWAY_CORE_OPERATOR_TOKEN
    );
  }
  if (isRecoveryActionPath(path)) {
    return firstNonBlank(
      process.env.CORE_BACKEND_RECOVERY_OPERATOR_TOKEN,
      process.env.CORE_RECOVERY_OPERATOR_TOKEN,
      process.env.CORE_RECOVERY_OPERATOR_INTERNAL_TOKEN,
      process.env.CORE_BACKEND_OPERATOR_TOKEN,
      process.env.CORE_OPERATOR_TOKEN,
      process.env.AI_EVENT_GATEWAY_CORE_OPERATOR_TOKEN
    );
  }
  return firstNonBlank(
    process.env.CORE_BACKEND_OPERATOR_TOKEN,
    process.env.CORE_OPERATOR_TOKEN,
    process.env.AI_EVENT_GATEWAY_CORE_OPERATOR_TOKEN
  );
}

function injectCoreOperatorToken(headers: Headers, path: string[]): void {
  if (path[0] === 'admin') {
    headers.delete('authorization');
    headers.delete('x-cluster-token');
    headers.set('x-admin-ui-auth-mode', 'core-session');
    return;
  }
  const operatorToken = recoveryTokenFor(path);
  if (!operatorToken) return;

  const headerName = firstNonBlank(
    process.env.CORE_BACKEND_OPERATOR_TOKEN_HEADER,
    process.env.CORE_INTERNAL_TOKEN_HEADER
  ) ?? 'X-Cluster-Token';

  headers.set(headerName, operatorToken);

  if (!envBoolean('CORE_FORWARD_BROWSER_AUTHORIZATION', false)) {
    headers.delete('authorization');
  }

  headers.set('x-admin-ui-proxy-plane', 'core');
  headers.set('x-admin-ui-proxy-path', `/${path.join('/')}`);
  headers.set('x-recovery-rbac-proxy-role', isActionApprovalPath(path) || isRecoveryApprovalMutationPath(path) ? 'RECOVERY_APPROVER' : isDispatchCutoverPath(path) || isActionAdminPath(path) || isHighRiskRecoveryActionPath(path) ? 'RECOVERY_ADMIN' : isActionGovernancePath(path) || isRecoveryActionPath(path) ? 'RECOVERY_OPERATOR' : 'OPERATOR');
}

function nettyMachineToken(): string | undefined {
  return [process.env.NETTY_MACHINE_ADMIN_TOKEN, process.env.NETTY_BACKEND_ADMIN_TOKEN]
    .find((value) => value?.trim())?.trim();
}

function injectNettyMachineToken(headers: Headers): void {
  if (headers.has('authorization')) return;
  const token = nettyMachineToken();
  if (token) headers.set('authorization', `Bearer ${token}`);
}

function forwardRequestHeaders(request: NextRequest, plane: BackendPlane, path: string[]): Headers {
  const headers = new Headers();
  request.headers.forEach((value, key) => {
    const normalizedKey = key.toLowerCase();
    if (!HOP_BY_HOP_HEADERS.has(normalizedKey) && !BROWSER_CORS_REQUEST_HEADERS.has(normalizedKey)) {
      headers.set(key, value);
    }
  });
  headers.set('x-forwarded-host', request.headers.get('host') ?? '');
  headers.set('x-forwarded-proto', request.nextUrl.protocol.replace(':', ''));
  headers.set('x-forwarded-prefix', prefixFor(plane));

  if (plane === 'core') {
    injectCoreOperatorToken(headers, path);
  } else if (plane === 'netty' || plane === 'gateway') {
    injectNettyMachineToken(headers);
  }

  return headers;
}

function forwardResponseHeaders(response: Response): Headers {
  const headers = new Headers();
  response.headers.forEach((value, key) => {
    if (!HOP_BY_HOP_HEADERS.has(key.toLowerCase())) headers.set(key, value);
  });
  return headers;
}

function proxyErrorCode(plane: BackendPlane): string {
  if (plane === 'core') return 'ADMIN_PROXY_CORE_UNAVAILABLE';
  if (plane === 'netty') return 'ADMIN_PROXY_NETTY_UNAVAILABLE';
  return 'ADMIN_PROXY_GATEWAY_UNAVAILABLE';
}

function proxyBackendErrorCode(plane: BackendPlane): string {
  if (plane === 'core') return 'ADMIN_PROXY_CORE_ERROR';
  if (plane === 'netty') return 'ADMIN_PROXY_NETTY_ERROR';
  return 'ADMIN_PROXY_GATEWAY_ERROR';
}

function standardEnvelope<T>(code: string, message: string, data: T | null = null): StandardApiEnvelope<T> {
  return makeStandardApiEnvelope(code, message, data);
}

function standardEnvelopeResponse<T>(code: string, message: string, data: T | null = null): NextResponse {
  return NextResponse.json(standardEnvelope(code, message, data), { status: 200 });
}

async function readBackendErrorMessage(response: Response): Promise<string> {
  const contentType = response.headers.get('content-type') ?? '';
  try {
    if (contentType.includes('application/json')) {
      const body = await response.json() as unknown;
      if (typeof body === 'object' && body !== null) {
        const record = body as Record<string, unknown>;
        const message = record.message ?? record.error ?? record.detail;
        if (typeof message === 'string' && message.trim()) return message;
      }
    }
    const text = await response.text();
    if (text.trim()) return text.trim();
  } catch {
    // Ignore unreadable backend body and fall back to status text.
  }
  return response.statusText || `Backend returned HTTP ${response.status}`;
}

export async function proxyToBackend(
  request: NextRequest,
  context: { params: Promise<{ path?: string[] }> },
  plane: BackendPlane
): Promise<NextResponse> {
  const { path = [] } = await context.params;
  const method = request.method.toUpperCase();
  const hasRequestBody = !['GET', 'HEAD'].includes(method);
  const query = request.nextUrl.searchParams.toString();
  const pathname = `/${path.map(encodeURIComponent).join('/')}`;
  const pathAndQuery = query ? `${pathname}?${query}` : pathname;
  const body = hasRequestBody ? new Uint8Array(await request.arrayBuffer()) : undefined;

  try {
    const { response, origin, attempts } = await fetchBackend(plane, pathAndQuery, {
      method,
      headers: forwardRequestHeaders(request, plane, path),
      body
    });
    if (attempts.length > 0) {
      console.warn(`[admin-backend-proxy] ${plane} fallback origin selected:`, origin, attempts);
    }

    const preserveBackendStatus = plane === 'core' && path[0] === 'admin';

    if (!response.ok && !preserveBackendStatus) {
      const message = await readBackendErrorMessage(response);
      return standardEnvelopeResponse(proxyBackendErrorCode(plane), message);
    }

    return new NextResponse(response.body, {
      status: preserveBackendStatus ? response.status : 200,
      statusText: preserveBackendStatus ? response.statusText : 'OK',
      headers: forwardResponseHeaders(response)
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Backend proxy request failed.';
    return standardEnvelopeResponse(proxyErrorCode(plane), message);
  }
}
