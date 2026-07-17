/**
 * P20 public TypeScript contract for OpenDispatch HTTP API envelopes.
 *
 * Core and Netty management/business APIs return HTTP 200 and encode business
 * success or failure in the `code` field. Admin UI code should consume these
 * helpers instead of branching on transport status for business outcomes.
 */
export interface StandardApiEnvelope<T> {
  code: string;
  message: string;
  data: T | null;
  timestamp: string;
}

export interface LegacyApiEnvelope<T> {
  success: boolean;
  data: T;
  error?: {
    code?: string;
    message?: string;
    detail?: unknown;
  } | null;
  timestamp?: string;
  traceId?: string;
}

export const STANDARD_SUCCESS_CODE = 'OK';

export const UNAUTHORIZED_API_CODES = new Set([
  'UNAUTHORIZED',
  'AUTH_UNAUTHORIZED',
  'AUTH_TOKEN_EXPIRED',
  'ADMIN_AUTH_REQUIRED',
  'ADMIN_TOKEN_EXPIRED',
  'GATEWAY_AGENT_AUTH_FAILED'
]);

export const NOT_FOUND_OR_UNSUPPORTED_CODES = new Set([
  'NOT_FOUND',
  'METHOD_NOT_ALLOWED',
  'CORE_TASK_NOT_FOUND',
  'CORE_AGENT_NOT_FOUND',
  'CORE_INCIDENT_NOT_FOUND',
  'GATEWAY_AGENT_NOT_FOUND',
  'GATEWAY_CLUSTER_PEER_UNAVAILABLE'
]);

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

export function isLegacyApiEnvelope(value: unknown): value is LegacyApiEnvelope<unknown> {
  return isRecord(value) && typeof value.success === 'boolean' && ('data' in value || 'error' in value);
}

export function isStandardApiEnvelope(value: unknown): value is StandardApiEnvelope<unknown> {
  return isRecord(value)
    && typeof value.code === 'string'
    && typeof value.message === 'string'
    && 'data' in value
    && typeof value.timestamp === 'string';
}

export function standardEnvelopeCode(value: unknown): string | undefined {
  return isStandardApiEnvelope(value) ? value.code : undefined;
}

export function isUnauthorizedApiCode(code: string | undefined): boolean {
  return code !== undefined && UNAUTHORIZED_API_CODES.has(code);
}

export function isNotFoundOrUnsupportedCode(code: string | undefined): boolean {
  return code !== undefined && NOT_FOUND_OR_UNSUPPORTED_CODES.has(code);
}

export function makeStandardApiEnvelope<T>(code: string, message: string, data: T | null = null): StandardApiEnvelope<T> {
  return {
    code,
    message,
    data,
    timestamp: new Date().toISOString()
  };
}
