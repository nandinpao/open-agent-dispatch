import { StatusBadge } from '@/components/common/StatusBadge';
import type { GatewayTaskDetail } from '@/lib/types/admin';
import { formatDateTime } from '@/lib/utils/format';

function buildSuggestion(task: GatewayTaskDetail): string {
  if (!task.failureReason) return '目前沒有失敗原因；先檢查 trace timeline 是否停在 routing、assignment 或 agent processing。';
  const reason = task.failureReason.toLowerCase();
  if (reason.includes('timeout')) return '建議先檢查 Agent heartbeat、平均延遲、MCP/tool-call timeout 設定，再決定是否 retry。';
  if (reason.includes('capability')) return '建議檢查 Agent capability registry 是否包含此 action，或調整 routing rule。';
  if (reason.includes('disconnect')) return '建議先確認 Agent 是否自動重連成功，再執行 retry，避免再次指派到離線節點。';
  return '建議先查看 Task Logs 與 request payload，確認是否為資料格式、權限或 Agent runtime 錯誤。';
}

export function FailureAnalysisPanel({ task }: Readonly<{ task: GatewayTaskDetail }>) {
  const failed = task.status === 'FAILED';
  return (
    <section className={`rounded-2xl border p-5 shadow-sm ${failed ? 'border-rose-200 bg-rose-50/70' : 'border-slate-200 bg-white'}`}>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className={`text-base font-bold ${failed ? 'text-rose-900' : 'text-slate-900'}`}>Failure Analysis</h2>
          <p className={`mt-1 text-sm ${failed ? 'text-rose-700' : 'text-slate-500'}`}>針對失敗任務提供 retry 前的判斷依據。</p>
        </div>
        <StatusBadge status={task.status} />
      </div>
      <div className="mt-4 grid gap-3 md:grid-cols-3">
        <div className="rounded-xl bg-white px-4 py-3">
          <div className="text-xs font-semibold text-slate-400">Failed At</div>
          <div className="mt-1 text-sm font-bold text-slate-800">{task.failedAt ? formatDateTime(task.failedAt) : '-'}</div>
        </div>
        <div className="rounded-xl bg-white px-4 py-3">
          <div className="text-xs font-semibold text-slate-400">Retry Count</div>
          <div className="mt-1 text-sm font-bold text-slate-800">{task.retryCount}</div>
        </div>
        <div className="rounded-xl bg-white px-4 py-3">
          <div className="text-xs font-semibold text-slate-400">Agent</div>
          <div className="mt-1 break-all text-sm font-bold text-slate-800">{task.assignedAgentId ?? '-'}</div>
        </div>
      </div>
      <div className="mt-4 rounded-xl bg-white p-4">
        <div className="text-xs font-semibold text-slate-400">Failure Reason</div>
        <p className="mt-1 text-sm font-semibold text-slate-800">{task.failureReason ?? '-'}</p>
      </div>
      <div className="mt-3 rounded-xl bg-white p-4">
        <div className="text-xs font-semibold text-slate-400">Suggested Action</div>
        <p className="mt-1 text-sm text-slate-700">{buildSuggestion(task)}</p>
      </div>
    </section>
  );
}
