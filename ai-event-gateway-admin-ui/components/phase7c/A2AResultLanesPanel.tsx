'use client';

import type { A2AOperationsDetail, A2AAuthorityEvidence } from '@/lib/api/domains/a2aOperationsApi';
import { buildA2AResultLanes } from '@/lib/phase7c/taskA2aUx';

function summary(value?: A2AAuthorityEvidence | null): { status?: string; summary?: string; authority?: string; evidenceReference?: string } | null {
  if (!value) return null;
  const facts = value.facts || {};
  return {
    status: value.status,
    authority: value.authority,
    evidenceReference: value.evidenceId,
    summary: facts.summary || facts.resultSummary || facts.safeSummary || `${value.evidenceType} evidence is ${value.status}.`,
  };
}

function projection(detail: A2AOperationsDetail) {
  const event = detail.timeline.find((item) => /ISSUE|PROJECTION/.test(`${item.stage} ${item.eventType}`.toUpperCase()));
  if (!event) return null;
  return { status: event.status, summary: event.message || 'External issue projection evidence is available.', authority: 'External projection adapter', evidenceReference: event.evidenceReference || undefined };
}

export function A2AResultLanesPanel({ detail }: Readonly<{ detail: A2AOperationsDetail }>) {
  const lanes = buildA2AResultLanes({ result: summary(detail.result), aggregation: summary(detail.aggregation), projection: projection(detail) });
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm" aria-label="A2A result authorities">
      <p className="text-xs font-black uppercase tracking-[0.18em] text-indigo-700">Result authorities</p><h3 className="mt-1 text-lg font-black text-slate-950">Do not collapse Agent, human, and external states</h3>
      <div className="mt-4 grid gap-3 xl:grid-cols-3">
        {lanes.map((lane) => <div key={lane.lane} className="rounded-2xl border border-slate-200 bg-slate-50 p-4"><p className="text-xs font-black uppercase text-slate-500">{lane.lane.replaceAll('_', ' ')}</p><h4 className="mt-1 font-black text-slate-950">{lane.title}</h4><span className="mt-2 inline-flex rounded-full bg-white px-2.5 py-1 text-xs font-black text-slate-700">{lane.status}</span><p className="mt-3 text-sm leading-6 text-slate-700">{lane.summary}</p><p className="mt-3 text-xs text-slate-500">Authority: {lane.authority}</p>{lane.evidenceReference ? <p className="mt-1 break-all text-xs text-slate-400">Evidence: {lane.evidenceReference}</p> : null}</div>)}
      </div>
      <p className="mt-4 rounded-xl border border-blue-200 bg-blue-50 p-3 text-sm text-blue-900">An external Jira or Redmine issue becoming Closed does not, by itself, complete the OpenDispatch Task.</p>
    </section>
  );
}
