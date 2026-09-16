import type { ReactNode } from 'react';

export interface GuidedEmptyStateProps {
  title: string;
  description: ReactNode;
  nextStep?: ReactNode;
  primaryAction?: ReactNode;
  secondaryAction?: ReactNode;
  tone?: 'neutral' | 'info' | 'warning';
}

export function GuidedEmptyState({
  title,
  description,
  nextStep,
  primaryAction,
  secondaryAction,
  tone = 'neutral',
}: Readonly<GuidedEmptyStateProps>) {
  const toneClass = tone === 'warning'
    ? 'border-amber-200 bg-amber-50'
    : tone === 'info'
      ? 'border-blue-200 bg-blue-50'
      : 'border-slate-300 bg-slate-50';
  const iconClass = tone === 'warning'
    ? 'bg-amber-100 text-amber-800'
    : tone === 'info'
      ? 'bg-blue-100 text-blue-800'
      : 'bg-white text-slate-600';

  return (
    <div className={`rounded-2xl border border-dashed p-6 text-center ${toneClass}`}>
      <div className={`mx-auto flex size-9 items-center justify-center rounded-full text-sm font-black ${iconClass}`} aria-hidden="true">i</div>
      <h3 className="mt-3 text-sm font-black text-slate-950">{title}</h3>
      <div className="mx-auto mt-2 max-w-2xl text-sm leading-6 text-slate-600">{description}</div>
      {nextStep ? <div className="mx-auto mt-3 max-w-2xl rounded-xl bg-white/80 px-3 py-2 text-xs font-semibold leading-5 text-slate-600"><span className="font-black text-slate-800">Next:</span> {nextStep}</div> : null}
      {primaryAction || secondaryAction ? <div className="mt-4 flex flex-wrap justify-center gap-2">{primaryAction}{secondaryAction}</div> : null}
    </div>
  );
}

export interface RecoveryAction {
  label: string;
  count?: number;
  description?: string;
  action?: ReactNode;
}

export function BlockerRecoveryPanel({
  title,
  description,
  items,
  tone = 'warning',
}: Readonly<{
  title: string;
  description: string;
  items: RecoveryAction[];
  tone?: 'warning' | 'danger';
}>) {
  const shell = tone === 'danger' ? 'border-rose-300 bg-rose-50 text-rose-950' : 'border-amber-300 bg-amber-50 text-amber-950';
  const itemShell = tone === 'danger' ? 'border-rose-200 bg-white' : 'border-amber-200 bg-white';
  return (
    <section className={`rounded-2xl border p-4 ${shell}`}>
      <h3 className="text-sm font-black">{title}</h3>
      <p className="mt-1 text-sm leading-6">{description}</p>
      <div className="mt-3 grid gap-2 sm:grid-cols-2">
        {items.filter((item) => (item.count ?? 1) > 0).map((item) => (
          <div key={item.label} className={`rounded-xl border p-3 ${itemShell}`}>
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="text-xs font-black text-slate-900">{item.label}{typeof item.count === 'number' ? ` · ${item.count}` : ''}</p>
                {item.description ? <p className="mt-1 text-xs leading-5 text-slate-600">{item.description}</p> : null}
              </div>
              {item.action ? <div className="shrink-0">{item.action}</div> : null}
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}

export function TechnicalDetails({
  summary = 'Technical details',
  children,
  className = '',
}: Readonly<{ summary?: string; children: ReactNode; className?: string }>) {
  return (
    <details className={`rounded-2xl border border-slate-200 bg-slate-50 p-4 text-xs text-slate-600 ${className}`}>
      <summary className="cursor-pointer font-black text-slate-800">{summary}</summary>
      <div className="mt-3">{children}</div>
    </details>
  );
}

export function ReadOnlyBoundary({
  title = 'Read-only access',
  description,
}: Readonly<{ title?: string; description: string }>) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm text-slate-700">
      <p className="font-black text-slate-900">{title}</p>
      <p className="mt-1 text-xs leading-5 text-slate-600">{description}</p>
    </div>
  );
}
