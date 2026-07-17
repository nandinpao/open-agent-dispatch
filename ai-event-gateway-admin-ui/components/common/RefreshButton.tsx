'use client';

import { formatDateTime } from '@/lib/utils/format';

interface RefreshButtonProps {
  refreshing?: boolean;
  lastUpdatedAt?: string | null;
  onRefresh: () => void | Promise<void>;
}

export function RefreshButton({ refreshing, lastUpdatedAt, onRefresh }: RefreshButtonProps) {
  return (
    <div className="flex items-center gap-3">
      {lastUpdatedAt ? <span className="hidden text-xs text-slate-500 sm:inline">更新：{formatDateTime(lastUpdatedAt)}</span> : null}
      <button
        type="button"
        onClick={() => void onRefresh()}
        disabled={refreshing}
        className="rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-700 shadow-sm hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
      >
        {refreshing ? '更新中...' : '手動更新'}
      </button>
    </div>
  );
}
