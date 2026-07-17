export type DataSourceKind = 'live' | 'partial' | 'fallback' | 'fixture' | 'mock' | 'unavailable';

const sourceClassName: Record<DataSourceKind, string> = {
  live: 'border-emerald-200 bg-emerald-50 text-emerald-700',
  partial: 'border-amber-200 bg-amber-50 text-amber-700',
  fallback: 'border-orange-200 bg-orange-50 text-orange-700',
  fixture: 'border-rose-200 bg-rose-50 text-rose-700',
  mock: 'border-fuchsia-200 bg-fuchsia-50 text-fuchsia-700',
  unavailable: 'border-slate-300 bg-slate-100 text-slate-600',
};

const sourceLabel: Record<DataSourceKind, string> = {
  live: 'Live Data',
  partial: 'Partial Live Data',
  fallback: 'Fallback Data',
  fixture: 'Fixture Data',
  mock: 'Mock Data',
  unavailable: 'Live Data Unavailable',
};

export function DataSourceBadge({
  source,
  label,
  detail,
}: Readonly<{
  source: DataSourceKind;
  label?: string;
  detail?: string;
}>) {
  return (
    <span className={`inline-flex max-w-full items-center gap-1 rounded-full border px-3 py-1 text-xs font-black ${sourceClassName[source]}`} title={detail}>
      <span className="shrink-0">{label ?? sourceLabel[source]}</span>
      {detail ? <span className="truncate font-semibold opacity-75">· {detail}</span> : null}
    </span>
  );
}

export function dataSourceKindFromFlags({
  hasLiveData,
  hasSourceErrors,
  usesFallback,
  usesFixture,
  usesMock,
}: {
  hasLiveData?: boolean;
  hasSourceErrors?: boolean;
  usesFallback?: boolean;
  usesFixture?: boolean;
  usesMock?: boolean;
}): DataSourceKind {
  if (usesMock) return 'mock';
  if (usesFixture) return 'fixture';
  if (usesFallback) return 'fallback';
  if (hasLiveData && hasSourceErrors) return 'partial';
  if (hasLiveData) return 'live';
  return 'unavailable';
}
