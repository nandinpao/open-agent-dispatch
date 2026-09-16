'use client';

import type { A2AContextPreviewItem } from '@/lib/phase7c/taskA2aUx';

const styles: Record<A2AContextPreviewItem['disposition'], string> = {
  INCLUDED: 'border-emerald-200 bg-emerald-50 text-emerald-900',
  MASKED: 'border-blue-200 bg-blue-50 text-blue-900',
  OMITTED: 'border-slate-200 bg-slate-50 text-slate-700',
  REQUIRES_APPROVAL: 'border-amber-200 bg-amber-50 text-amber-900',
};

export function A2AContextPreview({ items }: Readonly<{ items: A2AContextPreviewItem[] }>) {
  return (
    <section aria-label="A2A context preview">
      <h4 className="text-sm font-black text-slate-950">Context preview</h4>
      <p className="mt-1 text-xs text-slate-500">This preview explains request metadata. Backend policy still authorizes and redacts the released snapshot.</p>
      <div className="mt-3 grid gap-2">
        {items.map((item) => (
          <div key={item.key} className={`rounded-xl border p-3 ${styles[item.disposition]}`}>
            <div className="flex flex-wrap items-start justify-between gap-2"><p className="text-sm font-black">{item.label}</p><span className="rounded-full bg-white/80 px-2 py-0.5 text-xs font-black">{item.disposition.replaceAll('_', ' ')}</span></div>
            <p className="mt-1 break-words text-sm">{item.value}</p><p className="mt-1 text-xs opacity-80">{item.explanation}</p>
          </div>
        ))}
      </div>
    </section>
  );
}
