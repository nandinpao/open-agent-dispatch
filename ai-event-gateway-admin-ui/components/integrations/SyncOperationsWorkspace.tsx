'use client';

import { useMemo, useState } from 'react';
import { ProviderWebhookConflictGovernancePanel } from '@/components/integrations/ProviderWebhookConflictGovernancePanel';
import { SyncOperationsOverviewPanel } from '@/components/integrations/SyncOperationsOverviewPanel';

const sections = [
  { id: 'overview', label: 'Connector Overview', description: 'Current ISSUE_TRACKING AdapterAction execution and Redmine Provider health.' },
  { id: 'webhooks', label: 'Provider Observations', description: 'Verified Webhook ingress, observation replay and read-only historical evidence.' },
] as const;

type SectionId = (typeof sections)[number]['id'];

export function SyncOperationsWorkspace() {
  const [active, setActive] = useState<SectionId>('overview');
  const current = useMemo(() => sections.find((item) => item.id === active) ?? sections[0], [active]);
  return (
    <div className="space-y-5">
      <section className="rounded-2xl border border-indigo-200 bg-indigo-50 p-5">
        <div className="text-xs font-black uppercase tracking-[.2em] text-indigo-700">Integration Operations</div>
        <h2 className="mt-2 text-xl font-black text-indigo-950">Redmine Connector Operations</h2>
        <p className="mt-2 max-w-4xl text-sm leading-6 text-indigo-900">Operate the current Redmine Connector without changing Task, Dispatch, A2A or Redmine Issue authority. Legacy desired-state projection, conflict arbitration, cross-project relay and bidirectional Issue governance are retired from normal operations.</p>
      </section>

      <div className="grid gap-5 xl:grid-cols-[minmax(15rem,0.28fr)_minmax(0,0.72fr)]">
        <nav aria-label="Integration Operations sections" className="rounded-2xl border border-slate-200 bg-white p-3 shadow-sm">
          <ul className="space-y-1">{sections.map((item) => <li key={item.id}><button type="button" onClick={() => setActive(item.id)} aria-current={active === item.id ? 'page' : undefined} className={`w-full rounded-xl px-3 py-3 text-left ${active === item.id ? 'bg-slate-900 text-white' : 'text-slate-700 hover:bg-slate-100'}`}><span className="block text-sm font-black">{item.label}</span><span className={`mt-1 block text-xs leading-5 ${active === item.id ? 'text-slate-300' : 'text-slate-500'}`}>{item.description}</span></button></li>)}</ul>
        </nav>
        <section aria-labelledby="sync-workspace-section-title" className="min-w-0 space-y-5">
          <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm"><h3 id="sync-workspace-section-title" className="font-black text-slate-950">{current.label}</h3><p className="mt-1 text-sm text-slate-600">{current.description}</p></div>
          {active === 'overview' ? <SyncOperationsOverviewPanel/> : null}
          {active === 'webhooks' ? <ProviderWebhookConflictGovernancePanel/> : null}
        </section>
      </div>
    </div>
  );
}
