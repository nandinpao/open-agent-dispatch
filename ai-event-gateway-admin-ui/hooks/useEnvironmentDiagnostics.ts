'use client';

import { useCallback, useMemo, useState } from 'react';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import { useAuth } from '@/components/auth/AuthProvider';
import { nettyRuntimeApi } from '@/lib/api/nettyRuntimeApi';
import { getCsrfState } from '@/lib/auth/session';
import { getPublicEnv } from '@/lib/constants/env';
import {
  buildConfigWarnings,
  overallDiagnosticStatus,
  type AuthTokenDiagnostics,
  type DiagnosticPlane,
  type DiagnosticStatus,
  type EnvironmentDiagnosticsSummary,
  type EnvironmentProbeResult
} from '@/lib/settings/environmentDiagnostics';
import { useAdminWebSocket } from '@/hooks/useAdminWebSocket';
import type { WebSocketConnectionState } from '@/lib/types/realtime';

interface UseEnvironmentDiagnosticsResult {
  summary: EnvironmentDiagnosticsSummary;
  loading: boolean;
  lastCheckedAt?: string;
  overallStatus: DiagnosticStatus;
  runDiagnostics: () => Promise<void>;
}

function nowIso(): string {
  return new Date().toISOString();
}

function messageFromError(error: unknown): string {
  if (error instanceof Error && error.message) return error.message;
  return 'Probe failed with an unknown error.';
}

async function timedProbe(
  id: string,
  plane: DiagnosticPlane,
  label: string,
  target: string,
  request: () => Promise<unknown>
): Promise<EnvironmentProbeResult> {
  const startedAt = performance.now();
  const checkedAt = nowIso();
  try {
    const detail = await request();
    return {
      id,
      plane,
      label,
      target,
      status: 'OK',
      message: 'Reachable',
      checkedAt,
      latencyMs: Math.max(0, Math.round(performance.now() - startedAt)),
      detail
    };
  } catch (error) {
    return {
      id,
      plane,
      label,
      target,
      status: 'ERROR',
      message: messageFromError(error),
      checkedAt,
      latencyMs: Math.max(0, Math.round(performance.now() - startedAt)),
      detail: error instanceof Error ? { name: error.name, message: error.message } : error
    };
  }
}

function buildAuthDiagnostics(user: ReturnType<typeof useAuth>['user']): AuthTokenDiagnostics {
  const env = getPublicEnv();
  return {
    authEnabled: env.authEnabled,
    authMode: env.adminAuthMode,
    cookieSession: env.adminAuthMode === 'core-session',
    csrfTokenLoaded: Boolean(getCsrfState()),
    userPresent: Boolean(user),
    selectedTenantId: user?.selectedTenantId,
    allowedTenantCount: user?.allowedTenantIds?.length ?? 0,
    expiresAtIso: user?.expiresAt,
    realtimeTransport: env.realtimeTransport
  };
}

function buildStreamProbe(env: ReturnType<typeof getPublicEnv>, connection: WebSocketConnectionState): EnvironmentProbeResult {
  const status: DiagnosticStatus = env.useMock
    ? 'DISABLED'
    : connection.status === 'OPEN'
      ? 'OK'
      : connection.status === 'CONNECTING' || connection.status === 'RECONNECTING'
        ? 'WARNING'
        : 'ERROR';

  return {
    id: 'netty.runtime-stream',
    plane: 'RUNTIME_STREAM',
    label: 'Netty runtime stream',
    target: '/api/realtime/events',
    status,
    message: env.useMock ? 'Mock mode is enabled.' : connection.lastError ?? connection.status,
    checkedAt: nowIso(),
    detail: {
      status: connection.status,
      connectedAt: connection.connectedAt,
      lastMessageAt: connection.lastMessageAt,
      lastPongAt: connection.lastPongAt,
      reconnectAttempts: connection.reconnectAttempts,
      paused: connection.paused,
      unsupportedMessageCount: connection.unsupportedMessageCount
    }
  };
}

export function useEnvironmentDiagnostics(): UseEnvironmentDiagnosticsResult {
  const env = getPublicEnv();
  const { user } = useAuth();
  const { connection } = useAdminWebSocket();
  const [probeResults, setProbeResults] = useState<EnvironmentProbeResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [lastCheckedAt, setLastCheckedAt] = useState<string>();

  const runDiagnostics = useCallback(async () => {
    setLoading(true);
    try {
      const [coreProbe, nettyHealthProbe, nettyRuntimeProbe] = await Promise.all([
        timedProbe(
          'core.dashboard-snapshot',
          'CORE',
          'Core control-plane snapshot',
          `${env.coreApiBaseUrl}/admin/dashboard/snapshot`,
          () => coreAdminApi.getDashboardSnapshot()
        ),
        timedProbe(
          'netty.health',
          'NETTY',
          'Netty admin health',
          `${env.nettyApiBaseUrl}/api/admin/health`,
          () => nettyRuntimeApi.getHealth()
        ),
        timedProbe(
          'netty.runtime-snapshot',
          'NETTY',
          'Netty runtime snapshot',
          `${env.nettyApiBaseUrl}/api/admin/runtime/snapshot`,
          () => nettyRuntimeApi.getRuntimeSnapshot()
        )
      ]);
      setProbeResults([coreProbe, nettyHealthProbe, nettyRuntimeProbe]);
      setLastCheckedAt(nowIso());
    } finally {
      setLoading(false);
    }
  }, [env.coreApiBaseUrl, env.nettyApiBaseUrl]);

  const summary = useMemo<EnvironmentDiagnosticsSummary>(() => {
    const probes = [
      ...probeResults,
      ...buildConfigWarnings(env),
      buildStreamProbe(env, connection)
    ];

    return {
      generatedAt: nowIso(),
      adminBackendMode: env.adminBackendMode,
      coreApiBaseUrl: env.coreApiBaseUrl,
      nettyApiBaseUrl: env.nettyApiBaseUrl,
      legacyGatewayApiBaseUrl: env.apiBaseUrl,
      nettyRuntimeWsUrl: env.nettyRuntimeWsUrl,
      legacyGatewayWsUrl: env.wsUrl,
      useMock: env.useMock,
      authEnabled: env.authEnabled,
      requestTimeoutMs: env.requestTimeoutMs,
      refreshIntervalMs: env.refreshIntervalMs,
      wsReconnectIntervalMs: env.wsReconnectIntervalMs,
      wsReconnectMaxIntervalMs: env.wsReconnectMaxIntervalMs,
      wsHeartbeatIntervalMs: env.wsHeartbeatIntervalMs,
      wsMaxEvents: env.wsMaxEvents,
      realtimeTransport: env.realtimeTransport,
      apiContractMode: env.apiContractMode,
      probes,
      auth: buildAuthDiagnostics(user)
    };
  }, [connection, env, probeResults, user]);

  return {
    summary,
    loading,
    lastCheckedAt,
    overallStatus: overallDiagnosticStatus(summary.probes),
    runDiagnostics
  };
}
