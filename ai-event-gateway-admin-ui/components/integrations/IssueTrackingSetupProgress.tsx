'use client';

interface Step {
  label: string;
  ready: boolean;
  detail: string;
}

export function IssueTrackingSetupProgress({
  connectionReady,
  credentialReady,
  mappingReady,
  active,
}: Readonly<{
  connectionReady: boolean;
  credentialReady: boolean;
  mappingReady: boolean;
  active: boolean;
}>) {
  const steps: Step[] = [
    { label: 'Connect Redmine', ready: connectionReady, detail: 'Connection' },
    { label: 'Authenticate', ready: credentialReady, detail: 'Service Account + API Key' },
    { label: 'Choose destination', ready: mappingReady, detail: 'Project + Tracker' },
    { label: 'Activate', ready: active, detail: 'Source-level connector context' },
  ];
  const firstPending = steps.findIndex((step) => !step.ready);
  return (
    <section className="rounded-2xl border border-slate-200 bg-slate-50 p-4" aria-label="Issue Tracking setup progress">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <div className="text-xs font-black uppercase tracking-wide text-slate-500">Setup progress</div>
          <p className="mt-1 text-sm font-bold text-slate-800">Configure one Source-level connector context. OpenDispatch Task Issue Policy decides when it is used.</p>
        </div>
        <span className="rounded-full bg-white px-3 py-1 text-xs font-black text-slate-600">{steps.filter((step) => step.ready).length}/{steps.length} ready</span>
      </div>
      <div className="mt-4 grid gap-2 sm:grid-cols-2 xl:grid-cols-4">
        {steps.map((step, index) => {
          const current = !step.ready && index === firstPending;
          return <div key={step.label} className={`rounded-xl border p-3 ${step.ready ? 'border-emerald-200 bg-emerald-50' : current ? 'border-amber-300 bg-amber-50' : 'border-slate-200 bg-white'}`}>
            <div className="flex items-center justify-between gap-2"><span className="text-xs font-black uppercase text-slate-500">{index + 1}</span><span className={`text-xs font-black ${step.ready ? 'text-emerald-700' : current ? 'text-amber-800' : 'text-slate-400'}`}>{step.ready ? 'READY' : current ? 'NEXT' : 'WAITING'}</span></div>
            <div className="mt-1 text-sm font-black text-slate-900">{step.label}</div>
            <div className="mt-1 text-xs text-slate-500">{step.detail}</div>
          </div>;
        })}
      </div>
    </section>
  );
}
