'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { EntitlementPageGuard } from '@/components/auth/EntitlementPageGuard';
import { PageHeader } from '@/components/common/PageHeader';
import { BusinessEventConsole } from '@/components/issues-events/BusinessEventConsole';
import { BusinessIssueConsole } from '@/components/issues-events/BusinessIssueConsole';
import { SecurityIncidentConsole } from '@/components/issues-events/SecurityIncidentConsole';
import { BeginnerGuideButton } from '@/components/resource-scope/EnterpriseAccessUi';
import { RightDrawer } from '@/components/ui/RightDrawer';
import { useUiEntitlements } from '@/lib/navigation/useUiEntitlements';
import { actionAllowed, featureAllowed } from '@/lib/navigation/uiEntitlements';

type PrimaryView = 'issues' | 'events' | 'incidents';

const MORE_TOOLS = [
  { href: '/settings/integrations', label: 'Integration settings', description: 'Provider connections, scoped principals, secret references, and project mappings.' },
  { href: '/operations/integration-sync', label: 'Sync operations', description: 'Projection queues, Webhooks, conflicts, relay topology, reconciliation, and Dead Letters.' },
  { href: '/tasks/failure-queue', label: 'Failure queue', description: 'Specialized recovery view for failed Tasks.' },
  { href: '/security-events', label: 'Security events', description: 'Authorization, trust, and runtime security evidence.' },
  { href: '/events', label: 'Runtime event diagnostics', description: 'Developer/runtime diagnostics; not the governed Business Event surface.' },
  { href: '/runtime/rejected-connections', label: 'Rejected connections', description: 'Investigate rejected Agent connection attempts.' },
  { href: '/websocket', label: 'Runtime stream', description: 'Inspect live runtime streams for diagnostics.' },
];

export default function IssuesEventsPage() {
  const entitlements = useUiEntitlements();
  const router = useRouter(); const pathname = usePathname(); const searchParams = useSearchParams();
  const canIssues = featureAllowed(entitlements.value, 'business-issues');
  const canEvents = featureAllowed(entitlements.value, 'business-events');
  const canIncidents = actionAllowed(entitlements.value, 'issues-events.operate');
  const available = useMemo<PrimaryView[]>(() => [canIssues ? 'issues' : null, canEvents ? 'events' : null, canIncidents ? 'incidents' : null].filter(Boolean) as PrimaryView[], [canEvents, canIncidents, canIssues]);
  const [view, setView] = useState<PrimaryView>('issues');
  const [toolsOpen, setToolsOpen] = useState(false);
  const requestedView = searchParams.get('view');

  useEffect(() => {
    if ((requestedView === 'issues' || requestedView === 'events' || requestedView === 'incidents') && available.includes(requestedView)) { setView(requestedView); return; }
    if (!available.includes(view) && available[0]) setView(available[0]);
  }, [available, requestedView, view]);

  function chooseView(next: PrimaryView) {
    setView(next);
    const params = new URLSearchParams(searchParams.toString()); params.set('view', next);
    router.replace(`${pathname}?${params.toString()}`, { scroll: false });
  }

  return (
    <EntitlementPageGuard featureId="issues-events">
      <main className="space-y-5">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <PageHeader title="Issues & Events" description="Review governed Business Issues and Business Events from one workspace. Use the extra tools only when you are troubleshooting integration or runtime behavior." />
          <div className="flex flex-wrap items-center gap-2">
            <BeginnerGuideButton title="Issues & Events in one workspace" description="Start with the business record. Technical synchronization and runtime diagnostics stay out of the normal workflow." steps={[{title:'Choose Issues or Events',description:'Switch views here without navigating to another page.'},{title:'Open the record in-place',description:'Payloads, Issue details, ownership and access explanations use drawers/popups so you keep your list context.'},{title:'Use More tools only for troubleshooting',description:'Integration sync, failure queues and runtime diagnostics are advanced workflows and remain one click away.'}]} />
            <button type="button" onClick={() => setToolsOpen(true)} className="rounded-xl border border-slate-300 bg-white px-3 py-2 text-xs font-black text-slate-700 hover:bg-slate-50">More tools</button>
          </div>
        </div>

        <section className="rounded-2xl border border-slate-200 bg-white p-2 shadow-sm">
          <div className="flex flex-wrap gap-2" role="tablist" aria-label="Issues and events view">
            {canIssues ? <button role="tab" aria-selected={view === 'issues'} type="button" onClick={() => chooseView('issues')} className={`rounded-xl px-4 py-2 text-sm font-black ${view === 'issues' ? 'bg-blue-700 text-white' : 'text-slate-700 hover:bg-slate-100'}`}>Business Issues</button> : null}
            {canEvents ? <button role="tab" aria-selected={view === 'events'} type="button" onClick={() => chooseView('events')} className={`rounded-xl px-4 py-2 text-sm font-black ${view === 'events' ? 'bg-blue-700 text-white' : 'text-slate-700 hover:bg-slate-100'}`}>Business Events</button> : null}
            {canIncidents ? <button role="tab" aria-selected={view === 'incidents'} type="button" onClick={() => chooseView('incidents')} className={`rounded-xl px-4 py-2 text-sm font-black ${view === 'incidents' ? 'bg-rose-700 text-white' : 'text-slate-700 hover:bg-slate-100'}`}>Security Incidents</button> : null}
          </div>
        </section>

        {view === 'issues' && canIssues ? <BusinessIssueConsole /> : null}
        {view === 'events' && canEvents ? <BusinessEventConsole /> : null}
        {view === 'incidents' && canIncidents ? <SecurityIncidentConsole /> : null}
        {!available.length ? <div className="rounded-2xl border border-slate-200 bg-white p-6 text-sm text-slate-600">Your current Responsibility does not include Business Issue, Business Event, or Security Incident access.</div> : null}

        <RightDrawer open={toolsOpen} title="More issue and event tools" onClose={() => setToolsOpen(false)}>
          <div className="space-y-3">
            <p className="text-sm leading-6 text-slate-600">These are specialized troubleshooting or configuration workspaces. Most daily work should stay on the Issues & Events page.</p>
            {MORE_TOOLS.map((tool) => (
              <Link key={tool.href} href={tool.href} onClick={() => setToolsOpen(false)} className="block rounded-2xl border border-slate-200 p-4 hover:border-blue-300 hover:bg-blue-50">
                <span className="block text-sm font-black text-slate-900">{tool.label}</span>
                <span className="mt-1 block text-xs leading-5 text-slate-500">{tool.description}</span>
              </Link>
            ))}
          </div>
        </RightDrawer>
      </main>
    </EntitlementPageGuard>
  );
}
