'use client';

import Link from 'next/link';
import { StatusBadge } from '@/components/common/StatusBadge';
import { adminInformationLayers, getAdminInformationLayer, type AdminInformationLayerId } from '@/lib/navigation/adminInformationArchitecture';
import { canAccessAdminUiMode, getAdminUiModeOption } from '@/lib/navigation/adminUiMode';
import { useAdminUiMode } from '@/hooks/useAdminUiMode';

function cardTone(active: boolean): string {
  return active
    ? 'border-blue-300 bg-blue-50 text-blue-950 shadow-sm'
    : 'border-slate-200 bg-white text-slate-950 shadow-sm';
}

function linkTone(active: boolean): string {
  return active
    ? 'border-blue-200 bg-white text-blue-800 hover:bg-blue-100'
    : 'border-slate-200 bg-slate-50 text-slate-700 hover:bg-slate-100';
}

export function InformationArchitectureGuide({
  activeLayer,
  compact = false
}: Readonly<{
  activeLayer?: AdminInformationLayerId;
  compact?: boolean;
}>) {
  const { mode, setMode } = useAdminUiMode();

  if (compact && activeLayer) {
    const layer = getAdminInformationLayer(activeLayer);
    const hasAccess = canAccessAdminUiMode(mode, layer.requiredMode);
    const modeOption = getAdminUiModeOption(layer.requiredMode);
    return (
      <section className="rounded-2xl border border-blue-200 bg-blue-50 p-4 text-sm text-blue-950 shadow-sm">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <StatusBadge status={layer.badge} label={layer.title} />
              <span className="font-bold">Source of truth: {layer.sourceOfTruth}</span>
            </div>
            <p className="mt-2 leading-6">{layer.description}</p>
            <p className="mt-1 text-xs font-semibold text-blue-800">Navigation rule: {layer.warning}</p>
            {!hasAccess ? (
              <button type="button" onClick={() => setMode(layer.requiredMode)} className="mt-3 rounded-xl border border-blue-300 bg-white px-3 py-2 text-xs font-black text-blue-800 hover:bg-blue-100">
                Switch to {modeOption.shortLabel} Mode
              </button>
            ) : null}
          </div>
          <Link href="/dashboard" className="shrink-0 rounded-xl border border-blue-300 bg-white px-3 py-2 text-xs font-bold text-blue-800 hover:bg-blue-100">
            查看三層總覽 →
          </Link>
        </div>
      </section>
    );
  }

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-2 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <p className="text-xs font-black uppercase tracking-wide text-blue-600">Admin UI Information Architecture</p>
          <h2 className="mt-1 text-xl font-black text-slate-950">先判斷資料層，再判斷操作</h2>
          <p className="mt-2 max-w-5xl text-sm leading-6 text-slate-600">
            The left navigation is intentionally limited to the daily operator workflow: Dashboard, Dispatch Flows, Agents, Tasks, Issues & Events, and System Settings. Internal catalogs, standalone Dispatch Rules, legacy tools, migration screens, and developer diagnostics are supporting pages.
          </p>
        </div>
        <div className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-xs font-semibold text-slate-600">
          Core = 權威狀態；Netty = transport 診斷；兩者不可互相覆蓋。
        </div>
      </div>
      <div className="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-3">
        {adminInformationLayers.map((layer) => {
          const active = activeLayer === layer.id;
          const accessible = canAccessAdminUiMode(mode, layer.requiredMode);
          const visibleLinks = layer.primaryLinks.filter((item) => canAccessAdminUiMode(mode, item.requiredMode));
          const requiredOption = getAdminUiModeOption(layer.requiredMode);
          return (
            <div key={layer.id} className={`rounded-2xl border p-4 ${cardTone(active)}`}>
              <div className="flex flex-wrap items-center justify-between gap-2">
                <StatusBadge status={layer.badge} label={layer.title} />
                {active ? <span className="text-xs font-bold text-blue-700">Current area</span> : null}
                {!accessible ? <span className="text-xs font-bold text-amber-700">Requires {requiredOption.shortLabel}</span> : null}
              </div>
              <div className="mt-3 text-sm font-bold">Source of truth</div>
              <div className="mt-1 text-sm text-slate-700">{layer.sourceOfTruth}</div>
              <p className="mt-3 text-sm leading-6 text-slate-600">{layer.description}</p>
              <div className="mt-3 rounded-xl border border-white/70 bg-white/70 p-3 text-xs leading-5 text-slate-600">
                <span className="font-bold text-slate-800">Operator question: </span>{layer.operatorQuestion}
              </div>
              <div className="mt-3 flex flex-wrap gap-2">
                {visibleLinks.map((item) => (
                  <Link key={`${layer.id}-${item.href}`} href={item.href} title={item.purpose} className={`rounded-xl border px-3 py-2 text-xs font-bold ${linkTone(active)}`}>
                    {item.label}
                  </Link>
                ))}
              </div>
              {!visibleLinks.length ? (
                <button type="button" onClick={() => setMode(layer.requiredMode)} className="mt-3 rounded-xl border border-amber-300 bg-amber-50 px-3 py-2 text-xs font-black text-amber-800 hover:bg-amber-100">
                  Switch to {requiredOption.shortLabel} Mode to show these links
                </button>
              ) : null}
              <p className="mt-3 text-xs font-semibold text-amber-700">Note: {layer.warning}</p>
            </div>
          );
        })}
      </div>
    </section>
  );
}
