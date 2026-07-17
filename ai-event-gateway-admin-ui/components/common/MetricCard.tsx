export function MetricCard({
  title,
  value,
  subtitle
}: Readonly<{
  title: string;
  value: string | number;
  subtitle?: string;
}>) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="text-sm font-medium text-slate-500">{title}</div>
      <div className="mt-3 text-3xl font-bold text-slate-950">{value}</div>
      {subtitle ? <div className="mt-2 text-xs text-slate-500">{subtitle}</div> : null}
    </div>
  );
}
