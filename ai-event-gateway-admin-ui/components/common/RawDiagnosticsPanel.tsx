import { JsonViewer } from '@/components/common/JsonViewer';
import { EmptyState } from '@/components/common/EmptyState';

export function RawDiagnosticsPanel({
  title = '工程師原始資料',
  description = '這裡保留 raw JSON / payload 供工程師追蹤。一般使用者可先看上方摘要與下一步。',
  value,
  defaultOpen = false
}: Readonly<{
  title?: string;
  description?: string;
  value: unknown;
  defaultOpen?: boolean;
}>) {
  const empty = value === undefined || value === null || (typeof value === 'object' && !Array.isArray(value) && Object.keys(value as Record<string, unknown>).length === 0);
  return (
    <details open={defaultOpen} className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
      <summary className="cursor-pointer text-sm font-black text-slate-800">{title}</summary>
      <p className="mt-2 text-xs leading-5 text-slate-500">{description}</p>
      <div className="mt-3">
        {empty ? <EmptyState title="沒有原始資料" description="目前 API 沒有回傳 raw payload，或此角色無權檢視。" /> : <JsonViewer value={value} />}
      </div>
    </details>
  );
}
