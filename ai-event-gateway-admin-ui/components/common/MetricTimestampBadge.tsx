import { formatDateTime } from '@/lib/utils/format';

export function MetricTimestampBadge({
  label = 'Metrics updated',
  timestamp
}: Readonly<{
  label?: string;
  timestamp?: string | null;
}>) {
  return (
    <span className="inline-flex rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-xs font-semibold text-slate-600">
      {label}：{timestamp ? formatDateTime(timestamp) : '-'}
    </span>
  );
}
