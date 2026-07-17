'use client';

import { AdminPerspectiveBanner } from '@/components/common/AdminPerspectiveBanner';
import { EmptyState } from '@/components/common/EmptyState';
import { ErrorBox } from '@/components/common/ErrorBox';
import { LoadingBox } from '@/components/common/LoadingBox';
import { RefreshButton } from '@/components/common/RefreshButton';
import { StatusBadge } from '@/components/common/StatusBadge';
import { useClusterDiagnostics } from '@/hooks/useClusterDiagnostics';
import type { ApiDiagnosticProbe } from '@/lib/types/admin';
import { formatDateTime, formatDurationMs, formatNumber } from '@/lib/utils/format';

function groupByScope(probes: ApiDiagnosticProbe[]): Record<ApiDiagnosticProbe['scope'], ApiDiagnosticProbe[]> {
  return probes.reduce<Record<ApiDiagnosticProbe['scope'], ApiDiagnosticProbe[]>>((accumulator, probe) => {
    accumulator[probe.scope] = [...(accumulator[probe.scope] ?? []), probe];
    return accumulator;
  }, { CLUSTER: [], LOCAL: [], REALTIME: [], ADMIN: [] });
}

function ProbeSummaryCard({ label, probes }: Readonly<{ label: string; probes: ApiDiagnosticProbe[] }>) {
  const available = probes.filter((probe) => probe.status === 'AVAILABLE').length;
  const error = probes.filter((probe) => probe.status === 'ERROR').length;
  const unavailable = probes.filter((probe) => probe.status === 'UNAVAILABLE').length;

  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className="text-xs font-semibold uppercase tracking-wide text-slate-400">{label}</div>
      <div className="mt-2 text-2xl font-bold text-slate-950">{available}/{probes.length}</div>
      <div className="mt-1 text-xs text-slate-500">Unavailable {unavailable} · Error {error}</div>
    </div>
  );
}

function DiagnosticTable({ title, description, probes }: Readonly<{ title: string; description: string; probes: ApiDiagnosticProbe[] }>) {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white shadow-sm">
      <div className="border-b border-slate-100 px-5 py-4">
        <h2 className="text-base font-bold text-slate-900">{title}</h2>
        <p className="mt-1 text-sm text-slate-500">{description}</p>
      </div>
      {probes.length === 0 ? (
        <div className="p-5"><EmptyState title="No probes" description="此分類沒有設定檢查項目。" /></div>
      ) : (
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-4 py-3">Capability</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Path</th>
                <th className="px-4 py-3">HTTP</th>
                <th className="px-4 py-3">Records</th>
                <th className="px-4 py-3">Latency</th>
                <th className="px-4 py-3">Last success</th>
                <th className="px-4 py-3">Message</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {probes.map((probe) => (
                <tr key={probe.id} className="hover:bg-slate-50">
                  <td className="px-4 py-3 font-semibold text-slate-900">{probe.label}</td>
                  <td className="px-4 py-3"><StatusBadge status={probe.status} /></td>
                  <td className="px-4 py-3 font-mono text-xs text-slate-600">{probe.path}</td>
                  <td className="px-4 py-3 text-slate-600">{probe.httpStatus ?? '-'}</td>
                  <td className="px-4 py-3 text-slate-600">{probe.recordCount === undefined ? '-' : formatNumber(probe.recordCount)}</td>
                  <td className="px-4 py-3 text-slate-600">{formatDurationMs(probe.responseTimeMs)}</td>
                  <td className="px-4 py-3 text-slate-600">{probe.lastSuccessAt ? formatDateTime(probe.lastSuccessAt) : '-'}</td>
                  <td className="px-4 py-3 text-slate-600">{probe.message ?? '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

export function ClusterDiagnosticsPanel() {
  const { data, loading, refreshing, error, lastUpdatedAt, refresh } = useClusterDiagnostics();

  if (loading) return <LoadingBox label="檢查 Cluster/Admin API capabilities..." />;
  if (error) return <ErrorBox message={error} />;
  if (!data) return <EmptyState title="沒有 diagnostics report" description="前端尚未取得 API diagnostics 結果。" />;

  const grouped = groupByScope(data.probes);

  return (
    <div className="space-y-5">
      <AdminPerspectiveBanner />

      <div className="flex flex-col gap-3 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm lg:flex-row lg:items-start lg:justify-between">
        <div>
          <h2 className="text-lg font-bold text-slate-900">API Capability Diagnostics</h2>
          <p className="mt-1 text-sm text-slate-500">
            這裡直接檢查 Admin UI 實際使用的 API：cluster aggregation、local fallback、events local scope 與 health。可用來判斷畫面缺資料是後端未提供、API 不可用，還是前端 fallback 到 local scope。
          </p>
          <div className="mt-3 grid gap-1 text-xs text-slate-500 md:grid-cols-2">
            <div>Resolved API base：{data.adminApiBaseUrl}</div>
            <div>Resolved WebSocket：{data.adminWebSocketUrl}</div>
            <div>Generated：{formatDateTime(data.generatedAt)}</div>
          </div>
        </div>
        <RefreshButton refreshing={refreshing} lastUpdatedAt={lastUpdatedAt ?? data.generatedAt} onRefresh={refresh} />
      </div>

      <div className="grid gap-4 md:grid-cols-4">
        <ProbeSummaryCard label="Cluster APIs" probes={grouped.CLUSTER} />
        <ProbeSummaryCard label="Local APIs" probes={grouped.LOCAL} />
        <ProbeSummaryCard label="Admin APIs" probes={grouped.ADMIN} />
        <ProbeSummaryCard label="Realtime APIs" probes={grouped.REALTIME} />
      </div>

      <DiagnosticTable
        title="Cluster Aggregation APIs"
        description="這些 API 應由目前 SELF Gateway 彙整其它節點資料；若 unavailable，相關頁面會 fallback 或顯示 local scope。"
        probes={grouped.CLUSTER}
      />
      <DiagnosticTable
        title="Local / SELF Node APIs"
        description="這些 API 只代表目前處理 request 的 local node，不應被解讀為整個 cluster 的完整資料。"
        probes={[...grouped.ADMIN, ...grouped.LOCAL]}
      />
    </div>
  );
}
