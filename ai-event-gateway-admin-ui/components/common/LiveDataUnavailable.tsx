import type { ReactNode } from 'react';

export function LiveDataUnavailable({
  title = 'Live data is unavailable',
  description = 'This page cannot verify the current production state because the live API did not return usable data. The UI will not display fixture, mock, or fallback data as if it were real.',
  details,
  action,
}: Readonly<{
  title?: string;
  description?: string;
  details?: ReactNode;
  action?: ReactNode;
}>) {
  return (
    <section className="rounded-3xl border border-rose-200 bg-rose-50 p-5 text-rose-900 shadow-sm">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <div className="text-xs font-black uppercase tracking-wide text-rose-700">Production data guard</div>
          <h2 className="mt-2 text-lg font-black text-rose-950">{title}</h2>
          <p className="mt-2 max-w-4xl text-sm leading-6">{description}</p>
          {details ? <div className="mt-3 rounded-2xl border border-rose-200 bg-white/70 p-3 text-xs font-semibold leading-5 text-rose-800">{details}</div> : null}
        </div>
        {action ? <div className="shrink-0">{action}</div> : null}
      </div>
    </section>
  );
}
