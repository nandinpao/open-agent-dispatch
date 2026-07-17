import Link from 'next/link';

interface LegacyRuntimePlaneNoticeProps {
  title?: string;
  description?: string;
  compact?: boolean;
}

export function LegacyRuntimePlaneNotice({
  title = 'Legacy Netty runtime view',
  description = '此頁仍保留 Netty runtime / local diagnostic 語意，適合觀察 Gateway、Cluster、Event、Trace 等 transport runtime；它不是 Agent / Task / Dispatch 的 Core 權威資料來源。',
  compact = false
}: Readonly<LegacyRuntimePlaneNoticeProps>) {
  return (
    <div className={`rounded-2xl border border-amber-200 bg-amber-50 text-amber-950 shadow-sm ${compact ? 'p-4' : 'p-5'}`}>
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <div className="text-sm font-bold">{title}</div>
          <p className="mt-1 text-sm leading-6 text-amber-900">{description}</p>
        </div>
        <div className="flex flex-wrap gap-2 text-xs font-semibold">
          <Link href="/dashboard" className="rounded-xl border border-amber-300 bg-white px-3 py-2 text-amber-900 hover:bg-amber-100">
            Dual dashboard
          </Link>
          <Link href="/settings" className="rounded-xl border border-amber-300 bg-white px-3 py-2 text-amber-900 hover:bg-amber-100">
            Diagnostics
          </Link>
        </div>
      </div>
    </div>
  );
}
