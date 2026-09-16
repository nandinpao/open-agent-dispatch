'use client';

import Link from 'next/link';
import type { CoreTaskLineageEvidence } from '@/lib/types/domains/task';

function label(value?: string) { return value?.trim() || '—'; }

export function TaskLineageEvidencePanel({ evidence, error }: Readonly<{ evidence?: CoreTaskLineageEvidence[]; error?: string }>) {
  const rows = evidence ?? [];
  const investigationTaskId = rows.find((item) => item.rootTaskId)?.rootTaskId ?? rows[0]?.taskId;
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="text-xs font-black uppercase tracking-wide text-slate-400">Workload provenance</div>
          <h3 className="mt-1 text-lg font-black text-slate-950">Task & Agent Lineage</h3>
          <p className="mt-1 max-w-3xl text-sm leading-6 text-slate-500">Immutable creation, A2A delegation, Agent assignment/reassignment and outcome evidence. Current permissions are still evaluated by RBAC and Resource Access.</p>
        </div>
        <div className="flex flex-wrap items-center gap-2"><span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-bold text-slate-600">{rows.length} evidence</span>{investigationTaskId ? <Link href={`/issues-events?view=incidents&taskId=${encodeURIComponent(investigationTaskId)}&action=open`} className="rounded-xl border border-rose-200 bg-rose-50 px-3 py-1.5 text-xs font-black text-rose-800">Open security incident</Link> : null}</div>
      </div>
      {error ? <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">{error}</div> : null}
      {!error && rows.length === 0 ? <div className="mt-4 rounded-xl bg-slate-50 p-4 text-sm text-slate-500">No lineage evidence is visible for this Task.</div> : null}
      <div className="mt-4 space-y-3">
        {rows.map((item) => (
          <article key={item.evidenceId} className="rounded-xl border border-slate-200 bg-slate-50 p-4">
            <div className="flex flex-wrap items-center gap-2">
              <span className="rounded-full bg-white px-2.5 py-1 text-xs font-black text-slate-700">{item.eventType}</span>
              {item.failureDomain && item.failureDomain !== 'NONE' ? <span className="rounded-full bg-rose-100 px-2.5 py-1 text-xs font-bold text-rose-800">{item.failureDomain}</span> : null}
              <span className="text-xs text-slate-400">{item.occurredAt ? new Date(item.occurredAt).toLocaleString() : 'time unavailable'}</span>
            </div>
            <div className="mt-3 grid gap-2 text-sm md:grid-cols-2 xl:grid-cols-4">
              <div><span className="text-slate-400">Task</span><div className="font-bold text-slate-800">{label(item.taskId)}</div></div>
              <div><span className="text-slate-400">Actor</span><div className="font-bold text-slate-800">{item.actorPrincipalType} · {label(item.actorPrincipalId)}</div></div>
              <div><span className="text-slate-400">Executor</span><div className="font-bold text-slate-800">{label(item.executorAgentId)}</div></div>
              <div><span className="text-slate-400">Organization</span><div className="font-bold text-slate-800">{label(item.departmentId)} / {label(item.groupId)}</div></div>
            </div>
            {item.reason || item.failureCode ? <p className="mt-3 text-sm text-slate-600">{item.failureCode ? `${item.failureCode} · ` : ''}{item.reason ?? ''}</p> : null}
          </article>
        ))}
      </div>
    </section>
  );
}
