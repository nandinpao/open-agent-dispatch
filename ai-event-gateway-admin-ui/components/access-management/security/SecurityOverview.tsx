'use client';

import type { SecurityWorkspaceSummary } from '@/lib/iam/types';
import { WorkspaceLoading } from '../shared/workspaceUi';

export function SecurityOverview({ summary, loading, onOpen }: Readonly<{ summary: SecurityWorkspaceSummary | null; loading: boolean; onOpen: (view: string) => void }>) {
  if (loading && !summary) return <WorkspaceLoading title="Loading security posture" description="Collecting sessions, credentials, decisions and administrative changes." />;
  if (!summary) return null;
  const attention = summary.sessionsExpiringSoon + summary.serviceAccountsReviewDue + summary.tokensExpiringSoon + summary.dormantTokens + summary.deniedDecisions24h;
  const cards = [
    { label: 'Active sessions', value: summary.activeSessions, detail: `${summary.sessionsExpiringSoon} expiring soon`, view: 'SESSIONS' },
    { label: 'Service Accounts', value: summary.activeServiceAccounts, detail: `${summary.serviceAccountsReviewDue} review due`, view: 'MACHINE' },
    { label: 'Active credentials', value: summary.activeTokens, detail: `${summary.tokensExpiringSoon} expiring · ${summary.dormantTokens} dormant`, view: 'MACHINE' },
    { label: 'Denied decisions (24h)', value: summary.deniedDecisions24h, detail: `${summary.administrativeChanges24h} administrative changes`, view: 'AUDIT' },
  ];
  return <div className="space-y-5">
    <section className={`rounded-3xl border p-5 ${attention ? 'border-amber-200 bg-amber-50' : 'border-emerald-200 bg-emerald-50'}`}>
      <p className="text-xs font-black uppercase tracking-[.16em] text-slate-600">Security posture</p>
      <h2 className="mt-1 text-xl font-black text-slate-950">{attention ? `${attention} item${attention === 1 ? '' : 's'} need review` : 'No immediate security action required'}</h2>
      <p className="mt-2 text-sm leading-6 text-slate-600">Use the nearby workspace links to review the record that produced each counter. Technical evidence remains available without becoming the primary workflow.</p>
    </section>
    <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
      {cards.map((card) => <button key={card.label} type="button" onClick={() => onOpen(card.view)} className="rounded-3xl border border-slate-200 bg-white p-5 text-left shadow-sm transition hover:border-blue-300 hover:shadow-md">
        <span className="text-xs font-black uppercase tracking-wide text-slate-500">{card.label}</span>
        <span className="mt-2 block text-3xl font-black text-slate-950">{card.value}</span>
        <span className="mt-2 block text-sm text-slate-600">{card.detail}</span>
        <span className="mt-4 inline-flex text-xs font-black text-blue-700">Review →</span>
      </button>)}
    </div>
    <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
      <h2 className="text-lg font-black text-slate-950">Recommended review sequence</h2>
      <ol className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-4">
        {[
          ['1', 'Sign-in sessions', 'Revoke sessions that no longer represent active work.', 'SESSIONS'],
          ['2', 'Protection rules', 'Confirm password, MFA, session and token safeguards.', 'POLICIES'],
          ['3', 'Machine Access', 'Review Service Account ownership, Source Systems and client credentials.', 'MACHINE'],
          ['4', 'Audit', 'Read business actions first, then expand technical evidence.', 'AUDIT'],
        ].map(([number, title, description, view]) => <button key={title} type="button" onClick={() => onOpen(view)} className="rounded-2xl border border-slate-200 p-4 text-left hover:border-blue-300">
          <span className="flex size-8 items-center justify-center rounded-full bg-slate-950 text-xs font-black text-white">{number}</span>
          <span className="mt-3 block text-sm font-black text-slate-950">{title}</span>
          <span className="mt-1 block text-xs leading-5 text-slate-500">{description}</span>
        </button>)}
      </ol>
    </section>
  </div>;
}
