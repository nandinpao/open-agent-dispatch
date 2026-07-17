import { normalizeAdminAuthMode, type AdminAuthMode } from '@/lib/auth/mode';

export type AdminBackendMode = 'core' | 'netty' | 'dual';

export interface PublicEnv {
  appName: string;
  /** Legacy single-backend API base. Kept for existing Netty-oriented pages. */
  apiBaseUrl: string;
  /** Core control-plane API base used by P3B dashboard/governance pages. */
  coreApiBaseUrl: string;
  /** Netty runtime-plane API base used by P3B runtime pages. */
  nettyApiBaseUrl: string;
  /** Active Admin UI backend mode. */
  adminBackendMode: AdminBackendMode;
  /** Legacy websocket URL alias. */
  wsUrl: string;
  /** Netty runtime websocket stream URL. */
  nettyRuntimeWsUrl: string;
  refreshIntervalMs: number;
  useMock: boolean;
  requestedMock: boolean;
  allowMockData: boolean;
  allowFixtureData: boolean;
  productionMode: boolean;
  authEnabled: boolean;
  requestTimeoutMs: number;
  wsReconnectIntervalMs: number;
  wsReconnectMaxIntervalMs: number;
  wsHeartbeatIntervalMs: number;
  wsMaxEvents: number;
  realtimeTransport: 'sse';
  adminAuthMode: AdminAuthMode;
  apiContractMode: 'off' | 'warn' | 'strict';
}

function normalizeBaseUrl(value: string): string {
  if (value === '/') return '';
  return value.replace(/\/+$/, '');
}

function toNumber(value: string | undefined, fallback: number): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

function toBoolean(value: string | undefined, fallback: boolean): boolean {
  if (value === undefined || value === '') return fallback;
  return value.toLowerCase() === 'true';
}


function toApiContractMode(value: string | undefined): PublicEnv['apiContractMode'] {
  if (value === 'off' || value === 'strict') return value;
  return 'warn';
}

function toEnvironmentName(value: string | undefined): string {
  return (value ?? process.env.NODE_ENV ?? 'development').trim().toLowerCase();
}

function toAdminBackendMode(value: string | undefined): AdminBackendMode {
  if (value === 'core' || value === 'netty' || value === 'dual') return value;
  return 'dual';
}

export function getPublicEnv(): PublicEnv {
  const coreApiBaseUrl = normalizeBaseUrl(process.env.NEXT_PUBLIC_CORE_API_BASE_URL ?? '/core-api');
  const nettyApiBaseUrl = normalizeBaseUrl(process.env.NEXT_PUBLIC_NETTY_API_BASE_URL ?? process.env.NEXT_PUBLIC_GATEWAY_API_BASE_URL ?? '/netty-api');
  const nettyRuntimeWsUrl = process.env.NEXT_PUBLIC_NETTY_RUNTIME_WS_URL ?? process.env.NEXT_PUBLIC_GATEWAY_WS_URL ?? '/api/admin/runtime/stream';
  const environmentName = toEnvironmentName(process.env.NEXT_PUBLIC_ADMIN_UI_ENV);
  const productionMode = environmentName === 'production' || environmentName === 'prod';
  const requestedMock = toBoolean(process.env.NEXT_PUBLIC_USE_MOCK, false);
  const allowMockData = toBoolean(process.env.NEXT_PUBLIC_ALLOW_MOCK_DATA, !productionMode);
  const allowFixtureData = toBoolean(process.env.NEXT_PUBLIC_ALLOW_FIXTURE_DATA, !productionMode);

  return {
    appName: process.env.NEXT_PUBLIC_APP_NAME ?? 'AI Event Gateway Admin',
    apiBaseUrl: normalizeBaseUrl(process.env.NEXT_PUBLIC_GATEWAY_API_BASE_URL ?? nettyApiBaseUrl),
    coreApiBaseUrl,
    nettyApiBaseUrl,
    adminBackendMode: toAdminBackendMode(process.env.NEXT_PUBLIC_ADMIN_BACKEND_MODE),
    wsUrl: process.env.NEXT_PUBLIC_GATEWAY_WS_URL ?? nettyRuntimeWsUrl,
    nettyRuntimeWsUrl,
    refreshIntervalMs: toNumber(process.env.NEXT_PUBLIC_REFRESH_INTERVAL_MS, 5000),
    useMock: requestedMock && allowMockData,
    requestedMock,
    allowMockData,
    allowFixtureData,
    productionMode,
    authEnabled: toBoolean(process.env.NEXT_PUBLIC_AUTH_ENABLED, true),
    requestTimeoutMs: toNumber(process.env.NEXT_PUBLIC_REQUEST_TIMEOUT_MS, 10000),
    wsReconnectIntervalMs: toNumber(process.env.NEXT_PUBLIC_WS_RECONNECT_INTERVAL_MS, 3000),
    wsReconnectMaxIntervalMs: toNumber(process.env.NEXT_PUBLIC_WS_RECONNECT_MAX_INTERVAL_MS, 15000),
    wsHeartbeatIntervalMs: toNumber(process.env.NEXT_PUBLIC_WS_HEARTBEAT_INTERVAL_MS, 30000),
    wsMaxEvents: toNumber(process.env.NEXT_PUBLIC_WS_MAX_EVENTS, 200),
    realtimeTransport: 'sse',
    adminAuthMode: normalizeAdminAuthMode(),
    apiContractMode: toApiContractMode(process.env.NEXT_PUBLIC_API_CONTRACT_MODE)
  };
}
