'use client';

import type { ReactNode } from 'react';
import { useDialogAccessibility } from '@/hooks/useDialogAccessibility';

export function WorkspaceLoading({ title = 'Loading workspace', description = 'Retrieving the current Tenant information.' }: Readonly<{ title?: string; description?: string }>) {
  return (
    <section className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm" aria-live="polite" aria-busy="true">
      <div className="flex items-center gap-3">
        <span className="size-5 animate-spin rounded-full border-2 border-blue-200 border-t-blue-700" aria-hidden="true" />
        <div><h2 className="text-base font-black text-slate-950">{title}</h2><p className="mt-1 text-sm text-slate-600">{description}</p></div>
      </div>
      <div className="mt-5 grid gap-3 sm:grid-cols-2 xl:grid-cols-4" aria-hidden="true">
        {[0, 1, 2, 3].map((item) => <div key={item} className="h-28 animate-pulse rounded-2xl bg-slate-100" />)}
      </div>
    </section>
  );
}

export function WorkspaceEmpty({ title, description, nextAction, action }: Readonly<{ title: string; description: string; nextAction?: ReactNode; action?: ReactNode }>) {
  return (
    <section className="rounded-3xl border border-dashed border-slate-300 bg-slate-50 p-8 text-center">
      <div className="mx-auto flex size-11 items-center justify-center rounded-2xl bg-white text-xl shadow-sm" aria-hidden="true">＋</div>
      <h2 className="mt-4 text-lg font-black text-slate-950">{title}</h2>
      <p className="mx-auto mt-2 max-w-2xl text-sm leading-6 text-slate-600">{description}</p>
      {nextAction ? <div className="mx-auto mt-3 max-w-2xl rounded-xl bg-white px-3 py-2 text-xs font-semibold leading-5 text-slate-600"><span className="font-black text-slate-800">Next:</span> {nextAction}</div> : null}
      {action ? <div className="mt-5 flex justify-center">{action}</div> : null}
    </section>
  );
}

export function WorkspaceError({ title = 'The workspace could not be loaded', message, status, code, correlationId, onRetry }: Readonly<{ title?: string; message: string; status?: number; code?: string; correlationId?: string; onRetry?: () => void }>) {
  return (
    <section className="rounded-3xl border border-rose-200 bg-rose-50 p-6 shadow-sm" role="alert">
      <h2 className="text-lg font-black text-rose-950">{title}</h2>
      <p className="mt-2 text-sm leading-6 text-rose-900">{message}</p>
      {onRetry ? <button type="button" onClick={onRetry} className="mt-4 rounded-xl bg-rose-900 px-4 py-2 text-sm font-black text-white">Retry</button> : null}
      {status || code || correlationId ? <details className="mt-4 rounded-2xl border border-rose-200 bg-white/70 p-3 text-xs text-rose-800">
        <summary className="cursor-pointer font-black">Technical details for support</summary>
        <dl className="mt-2 grid gap-1 sm:grid-cols-2">
          {status ? <div><dt className="inline font-black">HTTP </dt><dd className="inline">{status}</dd></div> : null}
          {code ? <div><dt className="inline font-black">Error code </dt><dd className="inline break-all">{code}</dd></div> : null}
          {correlationId ? <div className="sm:col-span-2"><dt className="inline font-black">Support reference </dt><dd className="inline break-all">{correlationId}</dd></div> : null}
        </dl>
      </details> : null}
    </section>
  );
}

export function WorkspaceModal({ open, title, description, onClose, children, width = 'max-w-3xl' }: Readonly<{ open: boolean; title: string; description?: string; onClose: () => void; children: ReactNode; width?: string }>) {
  const dialogRef = useDialogAccessibility<HTMLElement>(open, onClose);
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/45 p-4" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <section ref={dialogRef} tabIndex={-1} role="dialog" aria-modal="true" aria-labelledby="workspace-dialog-title" className={`max-h-[92vh] w-full ${width} overflow-y-auto rounded-3xl bg-white shadow-2xl outline-none`}>
        <header className="sticky top-0 z-10 flex items-start justify-between gap-4 border-b border-slate-200 bg-white px-6 py-5">
          <div><h2 id="workspace-dialog-title" className="text-xl font-black text-slate-950">{title}</h2>{description ? <p className="mt-1 text-sm leading-6 text-slate-600">{description}</p> : null}</div>
          <button type="button" onClick={onClose} aria-label="Close dialog" className="rounded-xl border border-slate-200 px-3 py-2 text-sm font-black text-slate-600 hover:bg-slate-50">Close</button>
        </header>
        <div className="p-6">{children}</div>
      </section>
    </div>
  );
}

export function Breadcrumbs({ items }: Readonly<{ items: Array<{ label: string; href?: string }> }>) {
  return (
    <nav aria-label="Breadcrumb" className="text-sm text-slate-500">
      <ol className="flex flex-wrap items-center gap-2">
        {items.map((item, index) => <li key={`${item.label}-${index}`} className="flex items-center gap-2">{index ? <span aria-hidden="true">/</span> : null}{item.href ? <a href={item.href} className="font-bold text-blue-700 hover:underline">{item.label}</a> : <span className="font-bold text-slate-700" aria-current="page">{item.label}</span>}</li>)}
      </ol>
    </nav>
  );
}
