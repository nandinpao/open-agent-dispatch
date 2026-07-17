'use client';

import { StatusBadge } from '@/components/common/StatusBadge';
import type { RuntimeEventCenterSummary } from '@/lib/realtime/runtimeEventCenter';
import { formatDateTime, formatNumber } from '@/lib/utils/format';

export function RuntimeEventSummaryPanel({ summary }: Readonly<{ summary: RuntimeEventCenterSummary }>) {
  return (
    <section className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="text-sm font-bold text-slate-950">Runtime Event Summary</div>
          <p className="mt-1 text-xs text-slate-500">Netty runtime stream 事件摘要。這裡呈現 runtime-plane 變化，仍需與 Core snapshot 定期 reconcile。</p>
        </div>
        <div className="text-xs text-slate-500">Latest：{summary.latestAt ? formatDateTime(summary.latestAt) : '-'}</div>
      </div>

      <div className="grid grid-cols-2 gap-3 md:grid-cols-4 xl:grid-cols-8">
        <SummaryCard label="Events" value={summary.totalEvents} />
        <SummaryCard label="Agent Auth" value={summary.agentAuthEvents} />
        <SummaryCard label="Denied" value={summary.deniedAuthorizations} tone={summary.deniedAuthorizations > 0 ? 'WARNING' : 'OK'} />
        <SummaryCard label="Security" value={summary.securityEvents} tone={summary.securityEvents > 0 ? 'WARNING' : 'OK'} />
        <SummaryCard label="Delivery Failed" value={summary.deliveryFailures} tone={summary.deliveryFailures > 0 ? 'ERROR' : 'OK'} />
        <SummaryCard label="Callback Failed" value={summary.callbackFailures} tone={summary.callbackFailures > 0 ? 'ERROR' : 'OK'} />
        <SummaryCard label="Task" value={summary.taskEvents} />
        <SummaryCard label="Risk" value={summary.failedOrRiskEvents} tone={summary.failedOrRiskEvents > 0 ? 'WARNING' : 'OK'} />
      </div>

      <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-5">
        {summary.categories.filter((category) => category.count > 0).map((category) => (
          <div key={category.category} className="rounded-xl border border-slate-100 bg-slate-50 p-3">
            <div className="flex items-start justify-between gap-2">
              <div>
                <div className="text-xs font-semibold text-slate-700">{category.label}</div>
                <div className="mt-1 text-xl font-bold text-slate-950">{formatNumber(category.count)}</div>
              </div>
              {category.errorCount > 0 ? <StatusBadge status="ERROR" /> : category.warningCount > 0 ? <StatusBadge status="WARNING" /> : <StatusBadge status="OK" />}
            </div>
            <div className="mt-2 text-xs text-slate-500">Latest：{category.latestAt ? formatDateTime(category.latestAt) : '-'}</div>
          </div>
        ))}
      </div>
    </section>
  );
}

function SummaryCard({ label, value, tone = 'INFO' }: Readonly<{ label: string; value: number; tone?: string }>) {
  return (
    <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
      <div className="flex items-center justify-between gap-2">
        <div className="text-xs font-medium text-slate-500">{label}</div>
        <StatusBadge status={tone} />
      </div>
      <div className="mt-2 text-2xl font-bold text-slate-950">{formatNumber(value)}</div>
    </div>
  );
}
