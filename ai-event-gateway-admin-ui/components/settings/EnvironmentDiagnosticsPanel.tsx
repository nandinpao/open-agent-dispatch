'use client';

import { useEffect, useMemo, useState } from 'react';
import { JsonViewer } from '@/components/common/JsonViewer';
import { MetricCard } from '@/components/common/MetricCard';
import { StatusBadge } from '@/components/common/StatusBadge';
import { useEnvironmentDiagnostics } from '@/hooks/useEnvironmentDiagnostics';
import { formatDateTime } from '@/lib/utils/format';
import type { AuthTokenDiagnostics, EnvironmentProbeResult } from '@/lib/settings/environmentDiagnostics';

function boolText(value: boolean): string {
  return value ? 'YES' : 'NO';
}

function planeLabel(probe: EnvironmentProbeResult): string {
  if (probe.plane === 'CORE') return 'Core control-plane';
  if (probe.plane === 'NETTY') return 'Netty runtime-plane';
  if (probe.plane === 'RUNTIME_STREAM') return 'Runtime stream';
  if (probe.plane === 'AUTH') return 'Auth';
  return 'Config';
}

function latencyLabel(probe: EnvironmentProbeResult): string {
  return probe.latencyMs === undefined ? '-' : `${probe.latencyMs} ms`;
}

function ProbeTable({ probes }: Readonly<{ probes: EnvironmentProbeResult[] }>) {
  return (
    <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
      <div className="border-b border-slate-200 px-5 py-4">
        <div className="text-sm font-bold text-slate-950">Backend / runtime probes</div>
        <div className="mt-1 text-xs text-slate-500">Core、Netty、runtime stream 與設定檢查結果。Core 代表權威資料面，Netty 代表即時 runtime 面。</div>
      </div>
      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-slate-200 text-sm">
          <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 text-left">Plane</th>
              <th className="px-4 py-3 text-left">Probe</th>
              <th className="px-4 py-3 text-left">Target</th>
              <th className="px-4 py-3 text-left">Status</th>
              <th className="px-4 py-3 text-left">Latency</th>
              <th className="px-4 py-3 text-left">Message</th>
              <th className="px-4 py-3 text-left">Checked</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {probes.map((probe) => (
              <tr key={probe.id} className="align-top">
                <td className="whitespace-nowrap px-4 py-3 font-medium text-slate-700">{planeLabel(probe)}</td>
                <td className="px-4 py-3 text-slate-900">{probe.label}</td>
                <td className="max-w-xs break-all px-4 py-3 text-xs text-slate-500">{probe.target}</td>
                <td className="px-4 py-3"><StatusBadge status={probe.status} /></td>
                <td className="whitespace-nowrap px-4 py-3 text-slate-700">{latencyLabel(probe)}</td>
                <td className="max-w-sm px-4 py-3 text-slate-700">{probe.message}</td>
                <td className="whitespace-nowrap px-4 py-3 text-xs text-slate-500">{formatDateTime(probe.checkedAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function AuthDiagnosticsPanel({ auth }: Readonly<{ auth: AuthTokenDiagnostics }>) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex items-start justify-between gap-4">
        <div>
          <div className="text-sm font-bold text-slate-950">Authentication session diagnostics</div>
          <p className="mt-1 text-xs leading-5 text-slate-500">Browser credentials are held in HttpOnly cookies. No access or refresh token is stored in Web Storage.</p>
        </div>
        <StatusBadge status={auth.authEnabled ? 'ENABLED' : 'DISABLED'} />
      </div>
      <div className="mt-4 grid grid-cols-1 gap-3 text-sm md:grid-cols-2 xl:grid-cols-4">
        <div className="rounded-xl bg-slate-50 p-3">
          <div className="text-xs font-medium text-slate-500">Auth mode</div>
          <div className="mt-1 font-semibold text-slate-900">{auth.authMode}</div>
        </div>
        <div className="rounded-xl bg-slate-50 p-3">
          <div className="text-xs font-medium text-slate-500">Cookie session</div>
          <div className="mt-1 font-semibold text-slate-900">{boolText(auth.cookieSession)}</div>
        </div>
        <div className="rounded-xl bg-slate-50 p-3">
          <div className="text-xs font-medium text-slate-500">CSRF loaded</div>
          <div className="mt-1 font-semibold text-slate-900">{boolText(auth.csrfTokenLoaded)}</div>
        </div>
        <div className="rounded-xl bg-slate-50 p-3">
          <div className="text-xs font-medium text-slate-500">Realtime transport</div>
          <div className="mt-1 font-semibold text-slate-900">{auth.realtimeTransport.toUpperCase()}</div>
        </div>
      </div>
      <div className="mt-3 rounded-xl bg-slate-50 p-3 text-xs text-slate-500">
        Expires at: <span className="font-medium text-slate-700">{auth.expiresAtIso ? formatDateTime(auth.expiresAtIso) : '-'}</span> · User: <span className="font-medium text-slate-700">{boolText(auth.userPresent)}</span> · Tenant: <span className="font-medium text-slate-700">{auth.selectedTenantId || '-'}</span> · Allowed tenants: <span className="font-medium text-slate-700">{auth.allowedTenantCount}</span>
      </div>
    </div>
  );
}

export function EnvironmentDiagnosticsPanel() {
  const { summary, overallStatus, loading, lastCheckedAt, runDiagnostics } = useEnvironmentDiagnostics();
  const [showRaw, setShowRaw] = useState(false);

  useEffect(() => {
    void runDiagnostics();
  }, [runDiagnostics]);

  const counts = useMemo(() => ({
    errors: summary.probes.filter((probe) => probe.status === 'ERROR').length,
    warnings: summary.probes.filter((probe) => probe.status === 'WARNING').length,
    ok: summary.probes.filter((probe) => probe.status === 'OK').length,
    disabled: summary.probes.filter((probe) => probe.status === 'DISABLED').length
  }), [summary.probes]);

  return (
    <div className="space-y-5">
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
        <MetricCard title="Overall diagnostics" value={overallStatus} subtitle={lastCheckedAt ? `Last checked ${formatDateTime(lastCheckedAt)}` : 'Not checked yet'} />
        <MetricCard title="Backend mode" value={summary.adminBackendMode.toUpperCase()} subtitle="core / netty / dual" />
        <MetricCard title="Probe errors" value={counts.errors} subtitle={`${counts.warnings} warnings, ${counts.ok} OK, ${counts.disabled} disabled`} />
        <MetricCard title="Runtime stream" value={summary.probes.find((probe) => probe.id === 'netty.runtime-stream')?.status ?? 'UNKNOWN'} subtitle={summary.nettyRuntimeWsUrl} />
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <div className="flex items-center gap-3">
              <div className="text-sm font-bold text-slate-950">Resolved environment</div>
              <StatusBadge status={overallStatus} />
            </div>
            <p className="mt-2 max-w-4xl text-sm leading-6 text-slate-600">
              Core 是 Agent / Task / Dispatch 權威資料來源；Netty 是 Agent session / delivery / callback relay runtime 資料來源。此頁用來確認雙後端 proxy、health probe、runtime stream 與 auth/token 設定是否一致。
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              onClick={() => void runDiagnostics()}
              disabled={loading}
              className="rounded-xl bg-blue-600 px-4 py-2 text-xs font-semibold text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-blue-300"
            >
              {loading ? 'Checking...' : 'Run Diagnostics'}
            </button>
            <button
              type="button"
              onClick={() => setShowRaw((value) => !value)}
              className="rounded-xl border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50"
            >
              {showRaw ? 'Hide Raw JSON' : 'Show Raw JSON'}
            </button>
          </div>
        </div>

        <div className="mt-5 grid grid-cols-1 gap-3 text-sm lg:grid-cols-2">
          <div className="rounded-xl bg-slate-50 p-3">
            <div className="text-xs font-medium text-slate-500">Core API base URL</div>
            <div className="mt-1 break-all font-semibold text-slate-900">{summary.coreApiBaseUrl}</div>
          </div>
          <div className="rounded-xl bg-slate-50 p-3">
            <div className="text-xs font-medium text-slate-500">Netty API base URL</div>
            <div className="mt-1 break-all font-semibold text-slate-900">{summary.nettyApiBaseUrl}</div>
          </div>
          <div className="rounded-xl bg-slate-50 p-3">
            <div className="text-xs font-medium text-slate-500">Netty runtime WS URL</div>
            <div className="mt-1 break-all font-semibold text-slate-900">{summary.nettyRuntimeWsUrl}</div>
          </div>
          <div className="rounded-xl bg-slate-50 p-3">
            <div className="text-xs font-medium text-slate-500">Legacy aliases</div>
            <div className="mt-1 break-all text-xs text-slate-700">Gateway API: {summary.legacyGatewayApiBaseUrl}<br />Gateway WS: {summary.legacyGatewayWsUrl}</div>
          </div>
        </div>
      </div>

      <ProbeTable probes={summary.probes} />
      <AuthDiagnosticsPanel auth={summary.auth} />

      <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="text-sm font-bold text-slate-950">Runtime settings</div>
        <div className="mt-4 grid grid-cols-1 gap-3 text-sm md:grid-cols-2 xl:grid-cols-4">
          <div className="rounded-xl bg-slate-50 p-3"><div className="text-xs text-slate-500">Request timeout</div><div className="mt-1 font-semibold text-slate-900">{summary.requestTimeoutMs} ms</div></div>
          <div className="rounded-xl bg-slate-50 p-3"><div className="text-xs text-slate-500">Refresh interval</div><div className="mt-1 font-semibold text-slate-900">{summary.refreshIntervalMs} ms</div></div>
          <div className="rounded-xl bg-slate-50 p-3"><div className="text-xs text-slate-500">WS reconnect</div><div className="mt-1 font-semibold text-slate-900">{summary.wsReconnectIntervalMs} / {summary.wsReconnectMaxIntervalMs} ms</div></div>
          <div className="rounded-xl bg-slate-50 p-3"><div className="text-xs text-slate-500">WS max events</div><div className="mt-1 font-semibold text-slate-900">{summary.wsMaxEvents}</div></div>
          <div className="rounded-xl bg-slate-50 p-3"><div className="text-xs text-slate-500">Mock mode</div><div className="mt-1 font-semibold text-slate-900">{boolText(summary.useMock)}</div></div>
          <div className="rounded-xl bg-slate-50 p-3"><div className="text-xs text-slate-500">Auth enabled</div><div className="mt-1 font-semibold text-slate-900">{boolText(summary.authEnabled)}</div></div>
          <div className="rounded-xl bg-slate-50 p-3"><div className="text-xs text-slate-500">API contract mode</div><div className="mt-1 font-semibold text-slate-900">{summary.apiContractMode}</div></div>
          <div className="rounded-xl bg-slate-50 p-3"><div className="text-xs text-slate-500">WS heartbeat</div><div className="mt-1 font-semibold text-slate-900">{summary.wsHeartbeatIntervalMs} ms</div></div>
        </div>
      </div>

      {showRaw ? <JsonViewer value={summary} /> : null}
    </div>
  );
}
