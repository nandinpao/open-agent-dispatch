import type { ReactNode } from 'react';

function readNode(row: unknown, key: string): ReactNode | undefined {
  if (!row || typeof row !== 'object') return undefined;
  const value = (row as Record<string, unknown>)[key];
  return value as ReactNode | undefined;
}

export function CapabilityResolutionMatrix({
  rows,
  title,
  description,
}: Readonly<{
  rows?: readonly unknown[] | null;
  title: string;
  description?: string;
}>) {
  const safeRows = rows ?? [];
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-4 text-sm shadow-sm">
      <h3 className="font-black text-slate-950">{title}</h3>
      {description ? <p className="mt-1 leading-6 text-slate-600">{description}</p> : null}
      <div className="mt-3 space-y-2">
        {safeRows.length ? safeRows.map((row, index) => {
          const label = readNode(row, 'label') ?? readNode(row, 'capability') ?? `Capability ${index + 1}`;
          const status = readNode(row, 'status');
          const rowDescription = readNode(row, 'description');
          const id = readNode(row, 'id');
          return (
            <div key={String(id ?? label ?? index)} className="rounded-xl border border-slate-100 bg-slate-50 p-3">
              <div className="font-bold text-slate-900">{label}</div>
              {status ? <div className="mt-1 text-xs font-semibold text-slate-600">{status}</div> : null}
              {rowDescription ? <div className="mt-1 text-xs leading-5 text-slate-500">{rowDescription}</div> : null}
            </div>
          );
        }) : <div className="rounded-xl border border-dashed border-slate-200 p-3 text-slate-500">No Required Capability is configured for this standard dispatch path.</div>}
      </div>
    </section>
  );
}

export function EntityRelationshipStrip({
  title,
  description,
  steps,
}: Readonly<{
  title: string;
  description?: string;
  steps?: Array<{ id?: string; label?: ReactNode; title?: ReactNode; description?: ReactNode; status?: ReactNode } | string> | null;
}>) {
  const safeSteps = steps ?? [];
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-4 text-sm shadow-sm">
      <h3 className="font-black text-slate-950">{title}</h3>
      {description ? <p className="mt-1 leading-6 text-slate-600">{description}</p> : null}
      <div className="mt-3 flex flex-wrap gap-2">
        {safeSteps.map((step, index) => {
          const objectStep = typeof step === 'string' ? null : step;
          const label = typeof step === 'string' ? step : (objectStep?.label ?? objectStep?.title ?? `Step ${index + 1}`);
          return (
            <div key={typeof step === 'string' ? `${step}-${index}` : (objectStep?.id ?? String(index))} className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1.5 font-semibold text-slate-700">
              {label}
            </div>
          );
        })}
        {!safeSteps.length ? <div className="text-slate-500">No relationship evidence has been recorded yet.</div> : null}
      </div>
    </section>
  );
}
