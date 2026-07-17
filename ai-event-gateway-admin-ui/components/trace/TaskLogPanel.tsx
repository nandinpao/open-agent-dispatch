import { JsonViewer } from '@/components/common/JsonViewer';
import { StatusBadge } from '@/components/common/StatusBadge';
import type { TaskLogRecord } from '@/lib/types/admin';
import { formatDateTime } from '@/lib/utils/format';

export function TaskLogPanel({ logs }: Readonly<{ logs?: TaskLogRecord[] | null }>) {
  const safeLogs = Array.isArray(logs) ? logs : [];

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <h2 className="text-base font-bold text-slate-900">Task Logs</h2>
      <p className="mt-1 text-sm text-slate-500">保留 Gateway / Agent 執行過程中的關鍵 log，方便定位錯誤階段。</p>
      <div className="mt-4 space-y-3">
        {safeLogs.length === 0 ? (
          <div className="rounded-2xl border border-dashed border-slate-200 p-4 text-sm text-slate-500">目前沒有 Task logs。</div>
        ) : safeLogs.map((log) => (
          <div key={log.logId} className="rounded-2xl border border-slate-200 p-4">
            <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
              <div>
                <div className="flex items-center gap-2">
                  <StatusBadge status={log.level} />
                  <span className="font-bold text-slate-900">{log.message}</span>
                </div>
                <div className="mt-1 text-xs text-slate-500">{formatDateTime(log.timestamp)}</div>
              </div>
              <div className="text-xs font-semibold text-slate-500">{log.logId}</div>
            </div>
            {log.payload ? <div className="mt-3"><JsonViewer value={log.payload} /></div> : null}
          </div>
        ))}
      </div>
    </section>
  );
}
