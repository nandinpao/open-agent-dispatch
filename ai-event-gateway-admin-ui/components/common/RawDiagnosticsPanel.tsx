import { JsonViewer } from '@/components/common/JsonViewer';
import { EmptyState } from '@/components/common/EmptyState';

export function RawDiagnosticsPanel({
  title = 'Engineering raw data',
  description = ' raw JSON / payload Next step.',
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
        {empty ? <EmptyState title="No raw data" description="The API did not return a raw payload, or this role is not authorized to view it." /> : <JsonViewer value={value} />}
      </div>
    </details>
  );
}
