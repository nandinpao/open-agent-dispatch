import Link from 'next/link';
import type { ServerBootstrapDiagnostic } from '@/lib/server/uiPageBootstrap';
import type { UiPageBootstrapOutcome } from '@/lib/ui-capability/contracts';

const COPY: Record<Exclude<UiPageBootstrapOutcome, 'PAGE'>, { title: string; message: string }> = {
  STEP_UP_SHELL: { title: 'Reauthentication required', message: 'Confirm your identity before opening this protected resource.' },
  REQUEST_ACCESS_SHELL: { title: 'Access denied', message: 'Core denied access to this resource in your current workspace.' },
  SAFE_RESOURCE_CHANGED_SHELL: { title: 'Authorization context changed', message: 'The resource or authorization context changed while this page was opening. Reload the latest authorized state.' },
  ANTI_ENUMERATION_NOT_FOUND_SHELL: { title: 'Resource not found or unavailable', message: 'The resource may not exist or may not be available in your current workspace.' },
};

function DiagnosticDetails({ diagnostic }: Readonly<{ diagnostic?: ServerBootstrapDiagnostic }>) {
  if (!diagnostic) return null;
  return (
    <dl className="mt-5 grid gap-2 rounded-xl border border-current/15 bg-white/60 p-4 text-xs sm:grid-cols-2">
      <div><dt className="font-black uppercase tracking-wide opacity-60">Reason</dt><dd className="mt-1 break-words font-mono">{diagnostic.code}</dd></div>
      <div><dt className="font-black uppercase tracking-wide opacity-60">Correlation ID</dt><dd className="mt-1 break-all font-mono">{diagnostic.correlationId}</dd></div>
      {diagnostic.status ? <div><dt className="font-black uppercase tracking-wide opacity-60">HTTP status</dt><dd className="mt-1 font-mono">{diagnostic.status}</dd></div> : null}
      {diagnostic.origin ? <div><dt className="font-black uppercase tracking-wide opacity-60">Authorization origin</dt><dd className="mt-1 break-all font-mono">{diagnostic.origin}</dd></div> : null}
      <div className="sm:col-span-2"><dt className="font-black uppercase tracking-wide opacity-60">Detail</dt><dd className="mt-1 leading-5">{diagnostic.message}</dd></div>
    </dl>
  );
}

export function SafePageShell({ outcome, diagnostic, modal = false }: Readonly<{
  outcome: Exclude<UiPageBootstrapOutcome, 'PAGE'>;
  diagnostic?: ServerBootstrapDiagnostic;
  modal?: boolean;
}>) {
  const copy = COPY[outcome];
  return (
    <section role="status" aria-live="polite" className={modal ? 'rounded-2xl bg-white p-6' : 'mx-auto max-w-2xl rounded-2xl border border-slate-200 bg-white p-8 shadow-sm'}>
      <p className="text-xs font-black uppercase tracking-[0.18em] text-slate-500">Protected workspace</p>
      <h1 className="mt-2 text-2xl font-black text-slate-950">{copy.title}</h1>
      <p className="mt-3 text-sm leading-6 text-slate-600">{copy.message}</p>
      <DiagnosticDetails diagnostic={diagnostic} />
      <div className="mt-6 flex flex-wrap gap-3">
        {outcome === 'SAFE_RESOURCE_CHANGED_SHELL' ? <a href="" className="rounded-xl bg-slate-950 px-4 py-2 text-sm font-bold text-white">Reload authorization</a> : null}
        <Link href="/tasks" className="rounded-xl border border-slate-300 px-4 py-2 text-sm font-bold text-slate-700">Return to Tasks</Link>
      </div>
    </section>
  );
}

export function BootstrapUnavailableShell({ diagnostic, modal = false }: Readonly<{
  diagnostic: ServerBootstrapDiagnostic;
  modal?: boolean;
}>) {
  return (
    <section role="alert" className={modal ? 'rounded-2xl bg-white p-6' : 'mx-auto max-w-2xl rounded-2xl border border-amber-200 bg-amber-50 p-8 text-amber-950'}>
      <p className="text-xs font-black uppercase tracking-[0.18em]">Authorization verification unavailable</p>
      <h1 className="mt-2 text-xl font-black">OpenDispatch could not complete the server-side authorization guard</h1>
      <p className="mt-2 text-sm leading-6">Protected Task content was not rendered because Core could not be reached or the canonical session could not be verified. This is different from an access denial.</p>
      <DiagnosticDetails diagnostic={diagnostic} />
      <div className="mt-5 flex flex-wrap gap-3">
        {diagnostic.retryable ? <a href="" className="inline-flex rounded-xl bg-amber-950 px-4 py-2 text-sm font-bold text-white">Retry authorization</a> : null}
        <Link href="/tasks" className="inline-flex rounded-xl border border-current px-4 py-2 text-sm font-bold">Return to Tasks</Link>
      </div>
    </section>
  );
}

export function CapabilityDegradedNotice({ diagnostic }: Readonly<{ diagnostic?: ServerBootstrapDiagnostic }>) {
  if (!diagnostic) return null;
  return (
    <section role="status" aria-live="polite" className="mb-4 rounded-2xl border border-amber-200 bg-amber-50 p-4 text-amber-950">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-xs font-black uppercase tracking-[0.16em]">Server authorization passed · capability degraded</p>
          <p className="mt-1 text-sm font-semibold">The full Task console is available in read-only mode while fine-grained UI Capability projection is unavailable or disabled.</p>
        </div>
        {diagnostic.retryable ? <a href="" className="rounded-lg border border-current px-3 py-1.5 text-xs font-black">Refresh capability</a> : null}
      </div>
      <DiagnosticDetails diagnostic={diagnostic} />
    </section>
  );
}
