'use client';

import { useState } from 'react';
import { AgentTable } from '@/components/agents/AgentTable';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import type { CoreRuntimeDisconnectReconcileReport } from '@/lib/types/core';

export function AgentGovernanceConsole() {
  const [reconcileResult, setReconcileResult] = useState<CoreRuntimeDisconnectReconcileReport | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function reconcileBlockedRuntimes() {
    setBusy(true);
    setError(null);
    try {
      const result = await coreAdminApi.reconcileBlockedAgentRuntimes({ operatorId: 'admin-ui', limit: 500 });
      setReconcileResult(result);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : '無法重新整理被阻擋的 Agent Runtime session。');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="space-y-5">
      <div className="rounded-2xl border border-blue-100 bg-blue-50 p-4 text-sm text-blue-900">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <div className="font-semibold">Agent 操作</div>
            <div className="mt-1">
              從這裡管理 Agent 建立、核准、連線與可處理能力。派工設定請回到派工流程；不要在 Agent 頁建立另一套 Scope、Profile 或 Participation。
            </div>
          </div>
          <button type="button" onClick={reconcileBlockedRuntimes} disabled={busy} className="rounded-xl border border-blue-300 bg-white px-3 py-2 text-sm font-bold text-blue-700 hover:bg-blue-50 disabled:opacity-50">
            {busy ? '處理中...' : '重新整理 Runtime 狀態'}
          </button>
        </div>
        {error ? <div className="mt-3 rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-red-700">{error}</div> : null}
        {reconcileResult ? (
          <div className="mt-3 grid gap-2 text-xs sm:grid-cols-4">
            <div className="rounded-lg bg-white px-3 py-2">evaluated={reconcileResult.evaluated ?? 0}</div>
            <div className="rounded-lg bg-white px-3 py-2">attempted={reconcileResult.attempted ?? 0}</div>
            <div className="rounded-lg bg-white px-3 py-2">closed={reconcileResult.closed ?? 0}</div>
            <div className="rounded-lg bg-white px-3 py-2">failed={reconcileResult.failed ?? 0}</div>
          </div>
        ) : null}
      </div>
      <AgentTable />
    </div>
  );
}
