'use client';

import { useCallback, useEffect, useState } from 'react';
import { accessManagementApi } from '@/lib/api/accessManagementApi';
import { formatIamError } from '@/lib/iam/errorPresentation';
import type { AccessLifecycleSummary, AccessReviewCandidate } from '@/lib/iam/types';
import { EmptyState, ErrorNotice } from '../ui';
import { HumanStatus, SelectField } from '../shared/beginnerUi';

export function AccessReviewPanel({ tenantId, refreshKey, onOpenAssignment }: Readonly<{ tenantId: string; refreshKey: number; onOpenAssignment: (bindingId: string) => void }>) {
  const [summary, setSummary] = useState<AccessLifecycleSummary | null>(null);
  const [items, setItems] = useState<AccessReviewCandidate[]>([]);
  const [reason, setReason] = useState('');
  const [risk, setRisk] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true); setError('');
    try {
      const [nextSummary, page] = await Promise.all([
        accessManagementApi.accessLifecycleSummary(tenantId),
        accessManagementApi.accessReviewCandidates(tenantId, 100, '', reason, risk),
      ]);
      setSummary(nextSummary); setItems(page.items);
    } catch (cause) { setError(formatIamError(cause, 'Unable to load access lifecycle review.')); }
    finally { setLoading(false); }
  }, [reason, risk, tenantId]);
  useEffect(() => { void load(); }, [load, refreshKey]);

  return <div className="space-y-5"><ErrorNotice message={error} />
    <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
      <SummaryCard label="Active assignments" value={summary?.activeAssignments ?? 0} note="Currently effective" />
      <SummaryCard label="Expiring" value={summary?.expiringAssignments ?? 0} note="Within 30 days" warning />
      <SummaryCard label="Pending approvals" value={summary?.pendingApprovals ?? 0} note="Independent decision required" warning />
      <SummaryCard label="Review due" value={summary?.reviewDueAssignments ?? 0} note="Owner confirmation required" warning />
      <SummaryCard label="Critical access" value={summary?.criticalAssignments ?? 0} note="Privileged responsibilities" />
      <SummaryCard label="Scheduled" value={summary?.scheduledAssignments ?? 0} note="Not effective yet" />
      <SummaryCard label="Expired records" value={summary?.expiredAssignments ?? 0} note="Requires lifecycle cleanup" warning />
      <SummaryCard label="Orphan assignments" value={summary?.orphanAssignments ?? 0} note="Role or recipient needs remediation" warning />
    </section>
    <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm"><div className="flex flex-col gap-3 sm:flex-row sm:items-end"><div className="flex-1"><p className="text-xs font-black uppercase tracking-[.16em] text-blue-700">Access review</p><h2 className="mt-1 text-lg font-black text-slate-950">Assignments requiring attention</h2><p className="mt-2 text-sm text-slate-600">Review expiring, privileged and overdue access without exposing Atomic Permission internals.</p></div><div className="min-w-48"><label htmlFor="review-reason" className="mb-1.5 block text-sm font-black text-slate-800">Review reason</label><SelectField id="review-reason" value={reason} onChange={setReason} options={[{ value: 'EXPIRING', label: 'Expiring' }, { value: 'REVIEW_DUE', label: 'Review due' }, { value: 'CRITICAL_ROLE', label: 'Critical responsibility' }, { value: 'SCHEDULED', label: 'Scheduled review' }]} placeholder="All reasons" /></div><div className="min-w-44"><label htmlFor="review-risk" className="mb-1.5 block text-sm font-black text-slate-800">Risk</label><SelectField id="review-risk" value={risk} onChange={setRisk} options={[{ value: 'CRITICAL', label: 'Critical' }, { value: 'HIGH', label: 'High' }, { value: 'MEDIUM', label: 'Medium' }, { value: 'LOW', label: 'Low' }]} placeholder="All risk levels" /></div></div></section>
    {loading ? <div className="rounded-2xl bg-slate-50 p-5 text-sm text-slate-500">Loading review candidates…</div> : null}
    {!loading && !items.length ? <EmptyState>No assignment currently requires review for the selected filters.</EmptyState> : null}
    <div className="space-y-3">{items.map((item) => <article key={item.bindingId} className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm"><div className="flex flex-wrap items-start justify-between gap-3"><div><p className="font-black text-slate-950">{item.roleName}</p><p className="mt-1 text-sm text-slate-700">{item.principalName} · {item.scopeName}</p><p className="mt-1 text-xs text-slate-500">{item.expiresAt ? `Expires ${new Date(item.expiresAt).toLocaleDateString()}` : 'No planned expiry'}{item.nextReviewAt ? ` · review by ${new Date(item.nextReviewAt).toLocaleDateString()}` : ''}</p></div><div className="flex gap-2"><HumanStatus value={item.reviewReason} /><span className="rounded-full border border-slate-200 bg-slate-100 px-2.5 py-1 text-xs font-black text-slate-700">{item.riskLevel} risk</span></div></div><button type="button" onClick={() => onOpenAssignment(item.bindingId)} className="mt-4 rounded-xl border border-blue-300 px-4 py-2 text-sm font-black text-blue-800">Open assignment and remediate</button></article>)}</div>
  </div>;
}
function SummaryCard({ label, value, note, warning = false }: Readonly<{ label: string; value: number; note: string; warning?: boolean }>) { return <article className={`rounded-3xl border p-4 shadow-sm ${warning && value > 0 ? 'border-amber-200 bg-amber-50' : 'border-slate-200 bg-white'}`}><p className="text-xs font-black uppercase tracking-wide text-slate-500">{label}</p><p className="mt-2 text-3xl font-black text-slate-950">{value}</p><p className="mt-1 text-xs text-slate-600">{note}</p></article>; }
