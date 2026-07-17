import type { PublicEnv } from '@/lib/constants/env';

export type DiagnosticPlane = 'CORE' | 'NETTY' | 'RUNTIME_STREAM' | 'AUTH' | 'CONFIG';
export type DiagnosticStatus = 'OK' | 'WARNING' | 'ERROR' | 'DISABLED' | 'UNKNOWN';

export interface EnvironmentProbeResult {
  id: string;
  plane: DiagnosticPlane;
  label: string;
  target: string;
  status: DiagnosticStatus;
  message: string;
  checkedAt: string;
  latencyMs?: number;
  detail?: unknown;
}

export interface AuthTokenDiagnostics {
  authEnabled: boolean;
  authMode: PublicEnv['adminAuthMode'];
  cookieSession: boolean;
  csrfTokenLoaded: boolean;
  userPresent: boolean;
  selectedTenantId?: string;
  allowedTenantCount: number;
  expiresAtIso?: string;
  realtimeTransport: PublicEnv['realtimeTransport'];
}

export interface EnvironmentDiagnosticsSummary {
  generatedAt: string;
  adminBackendMode: PublicEnv['adminBackendMode'];
  coreApiBaseUrl: string;
  nettyApiBaseUrl: string;
  legacyGatewayApiBaseUrl: string;
  nettyRuntimeWsUrl: string;
  legacyGatewayWsUrl: string;
  useMock: boolean;
  authEnabled: boolean;
  requestTimeoutMs: number;
  refreshIntervalMs: number;
  wsReconnectIntervalMs: number;
  wsReconnectMaxIntervalMs: number;
  wsHeartbeatIntervalMs: number;
  wsMaxEvents: number;
  realtimeTransport: PublicEnv['realtimeTransport'];
  apiContractMode: PublicEnv['apiContractMode'];
  probes: EnvironmentProbeResult[];
  auth: AuthTokenDiagnostics;
}

export function statusRank(status: DiagnosticStatus): number {
  if (status === 'ERROR') return 4;
  if (status === 'WARNING') return 3;
  if (status === 'UNKNOWN') return 2;
  if (status === 'DISABLED') return 1;
  return 0;
}

export function overallDiagnosticStatus(probes: EnvironmentProbeResult[]): DiagnosticStatus {
  if (!probes.length) return 'UNKNOWN';
  return probes.reduce<DiagnosticStatus>((highest, probe) => (statusRank(probe.status) > statusRank(highest) ? probe.status : highest), 'OK');
}

export function buildConfigWarnings(env: PublicEnv): EnvironmentProbeResult[] {
  const now = new Date().toISOString();
  const warnings: EnvironmentProbeResult[] = [];

  if (env.adminBackendMode === 'dual' && env.coreApiBaseUrl === env.nettyApiBaseUrl) {
    warnings.push({
      id: 'config.same-core-netty-base-url',
      plane: 'CONFIG',
      label: 'Core and Netty base URL separation',
      target: `${env.coreApiBaseUrl} / ${env.nettyApiBaseUrl}`,
      status: 'WARNING',
      message: 'Dual mode should normally use separate Core and Netty API base URLs.',
      checkedAt: now
    });
  }

  if (env.nettyRuntimeWsUrl === env.wsUrl && env.nettyRuntimeWsUrl !== '/api/admin/runtime/stream') {
    warnings.push({
      id: 'config.legacy-ws-url',
      plane: 'CONFIG',
      label: 'Runtime stream URL',
      target: env.nettyRuntimeWsUrl,
      status: 'WARNING',
      message: 'Runtime stream is using the legacy Gateway WS alias. Prefer NEXT_PUBLIC_NETTY_RUNTIME_WS_URL=/api/admin/runtime/stream.',
      checkedAt: now
    });
  }


  if (!env.authEnabled && !env.useMock) {
    warnings.push({
      id: 'config.auth-disabled',
      plane: 'CONFIG',
      label: 'Admin auth',
      target: 'NEXT_PUBLIC_AUTH_ENABLED=false',
      status: 'WARNING',
      message: 'Authentication is disabled while mock mode is off. This should only be used in isolated development environments.',
      checkedAt: now
    });
  }

  if (env.useMock) {
    warnings.push({
      id: 'config.mock-enabled',
      plane: 'CONFIG',
      label: 'Mock mode',
      target: 'NEXT_PUBLIC_USE_MOCK=true',
      status: 'DISABLED',
      message: 'Mock mode is enabled. Backend probes may not reflect real Core or Netty availability.',
      checkedAt: now
    });
  }

  return warnings;
}
