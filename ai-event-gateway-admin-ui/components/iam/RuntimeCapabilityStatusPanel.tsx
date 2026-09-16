'use client';

import { useRuntimeCapabilities } from '@/components/providers/RuntimeCapabilityProvider';
import type { RuntimeCapabilityState } from '@/lib/runtime-capability/contracts';

function badgeClass(state: RuntimeCapabilityState): string {
  if (state === 'ENABLED') return 'border-emerald-200 bg-emerald-50 text-emerald-900';
  if (state === 'SHADOW' || state === 'PILOT') return 'border-amber-200 bg-amber-50 text-amber-900';
  return 'border-slate-200 bg-slate-100 text-slate-700';
}

function StateBadge({ value }: Readonly<{ value: RuntimeCapabilityState }>) {
  return <span className={`rounded-full border px-3 py-1 text-xs font-black ${badgeClass(value)}`}>{value.replaceAll('_', ' ')}</span>;
}

export function RuntimeCapabilityStatusPanel() {
  const { capabilities, loadState, error, refresh } = useRuntimeCapabilities();
  const surfaces = Object.entries(capabilities.surfaces) as Array<[string, RuntimeCapabilityState]>;
  const authoritative = loadState === 'READY' || loadState === 'REFRESHING';
  return <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
    <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
      <div>
        <p className="text-xs font-black uppercase tracking-[.18em] text-indigo-600">Backend runtime authority</p>
        <h2 className="mt-1 text-xl font-black text-slate-950">Canonical runtime capabilities</h2>
        <p className="mt-2 text-sm leading-6 text-slate-600">Core is the source of truth for the single browser session and enabled administration surfaces. Capability loading never blocks authentication or remounts the protected workspace.</p>
      </div>
      <button type="button" onClick={() => void refresh()} className="rounded-xl border border-slate-300 px-4 py-2 text-sm font-black text-slate-800 hover:bg-slate-50">{loadState === 'REFRESHING' ? 'Refreshing…' : 'Refresh'}</button>
    </div>
    {!authoritative ? <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm text-amber-950"><b>{loadState === 'LOADING' ? 'Loading in the background.' : 'Fail-closed degraded snapshot.'}</b>{error ? ` ${error}` : ' Optional surfaces remain disabled until Core responds.'}</div> : null}
    <div className="mt-5 grid gap-3 md:grid-cols-2 xl:grid-cols-3">
      <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
        <div className="text-xs font-black uppercase text-slate-500">Human browser session</div>
        <div className="mt-2 text-lg font-black text-slate-950">Canonical IAM</div>
        <div className="mt-1 break-all text-xs text-slate-500">Session endpoint: {capabilities.authentication.sessionPath}</div>
        <div className="mt-1 text-xs text-slate-500">Legacy password adapter: {capabilities.authentication.legacyPasswordAdapterEnabled ? 'enabled (credential verification only)' : 'disabled'}</div>
      </div>
      {surfaces.map(([name, state]) => <div key={name} className="rounded-xl border border-slate-200 bg-slate-50 p-4"><div className="text-xs font-black uppercase text-slate-500">{name.replace(/([A-Z])/g, ' $1')}</div><div className="mt-2"><StateBadge value={state} /></div></div>)}
    </div>
    <p className="mt-3 text-xs text-slate-500">Contract {capabilities.contractVersion} · {authoritative ? `Generated ${new Date(capabilities.generatedAt).toLocaleString()}` : 'Waiting for authoritative Core snapshot'}</p>
  </section>;
}
