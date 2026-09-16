'use client';

import Link from 'next/link';
import type { CapabilityDelegationDetail, CapabilityDelegationDecisionStage } from '@/lib/api/domains/a2aOperationsApi';
import { humanizeA2ACode } from '@/lib/a2a/contracts';
import { A2AStatusBadge } from './A2AStatusBadge';

function DetailRows({ values }: { values: Record<string, string> }) {
  const entries = Object.entries(values).filter(([, value]) => value?.trim());
  if (entries.length === 0) return <p className="text-xs text-slate-500">No additional evidence is available.</p>;
  return (
    <dl className="grid gap-x-5 gap-y-3 text-xs sm:grid-cols-2">
      {entries.map(([key, value]) => (
        <div key={key}>
          <dt className="font-black uppercase tracking-wide text-slate-500">{humanizeA2ACode(key)}</dt>
          <dd className="mt-1 break-all text-slate-800">{value}</dd>
        </div>
      ))}
    </dl>
  );
}

function DecisionCard({ stage }: { stage: CapabilityDelegationDecisionStage }) {
  return (
    <article className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-xs font-black uppercase tracking-[0.16em] text-indigo-700">{stage.code.replaceAll('_', ' ')}</p>
          <h3 className="mt-1 text-base font-black text-slate-950">{stage.label}</h3>
        </div>
        <A2AStatusBadge value={stage.status} />
      </div>
      <p className="mt-3 text-sm leading-6 text-slate-600">{stage.summary}</p>
      <p className="mt-3 text-xs font-bold text-slate-500">Authority: {humanizeA2ACode(stage.authority)}</p>
      {stage.reasons.length > 0 ? (
        <div className="mt-3 flex flex-wrap gap-2">
          {stage.reasons.map((reason) => <span key={reason} className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-bold text-slate-700">{humanizeA2ACode(reason)}</span>)}
        </div>
      ) : null}
      {Object.keys(stage.details).length > 0 || stage.evidenceId ? (
        <details className="mt-4 rounded-xl border border-slate-200 bg-slate-50 p-3">
          <summary className="cursor-pointer text-xs font-black uppercase tracking-wide text-slate-600">Decision evidence</summary>
          {stage.evidenceId ? <p className="mt-3 break-all font-mono text-xs text-slate-500">{stage.evidenceId}</p> : null}
          <div className="mt-3"><DetailRows values={stage.details} /></div>
        </details>
      ) : null}
    </article>
  );
}

export function CapabilityDelegationDetailView({ detail }: { detail: CapabilityDelegationDetail }) {
  const { summary } = detail;
  const provider = summary.selectedProviderName || summary.selectedProviderId;
  return (
    <div className="space-y-5">
      <section className="rounded-2xl border border-indigo-200 bg-indigo-50/40 p-5 shadow-sm">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="text-xs font-black uppercase tracking-[0.18em] text-indigo-700">Capability-first delegation</p>
            <h1 className="mt-1 text-2xl font-black text-slate-950">{humanizeA2ACode(summary.capabilityCode)}</h1>
            <p className="mt-2 text-sm text-slate-700">
              Operation: <span className="font-black">{humanizeA2ACode(summary.operation)}</span>
              {provider ? <> · Selected provider: <span className="font-black">{provider}</span></> : null}
            </p>
            <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-600">
              The requesting Agent declared WHAT it needs. Core resolved WHO CAN, WHO MAY, WHO SHOULD, and HOW server-side. Provider, binding, domain, pool, and transport are evidence outputs, never request authority.
            </p>
          </div>
          <A2AStatusBadge value={summary.status} />
        </div>
      </section>

      <section className="rounded-2xl border border-emerald-200 bg-emerald-50/60 p-5 shadow-sm" aria-labelledby="delegation-closure-heading">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <p className="text-xs font-black uppercase tracking-[0.18em] text-emerald-700">Execution closure</p>
            <h2 id="delegation-closure-heading" className="mt-1 text-lg font-black text-slate-950">Did the delegated work actually finish?</h2>
            <p className="mt-1 max-w-3xl text-sm leading-6 text-slate-600">A2A is not complete when a Child Task is merely queued. Completion requires provider execution evidence and a result delivered back to the Parent workflow.</p>
          </div>
          <A2AStatusBadge value={summary.resultNotificationStatus === 'DELIVERED' ? 'COMPLETED' : summary.status} />
        </div>
        <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <div className="rounded-xl border border-white bg-white/80 p-3"><div className="text-xs font-bold uppercase text-slate-400">Child Task</div><div className="mt-1 break-all text-sm font-black text-slate-900">{summary.childTaskId || 'Not created'}</div></div>
          <div className="rounded-xl border border-white bg-white/80 p-3"><div className="text-xs font-bold uppercase text-slate-400">Provider</div><div className="mt-1 text-sm font-black text-slate-900">{provider || 'Not selected'}</div></div>
          <div className="rounded-xl border border-white bg-white/80 p-3"><div className="text-xs font-bold uppercase text-slate-400">Provider result</div><div className="mt-1 text-sm font-black text-slate-900">{summary.resultStatus ? humanizeA2ACode(summary.resultStatus) : 'Waiting'}</div></div>
          <div className="rounded-xl border border-white bg-white/80 p-3"><div className="text-xs font-bold uppercase text-slate-400">Parent notification</div><div className="mt-1 text-sm font-black text-slate-900">{summary.resultNotificationStatus ? humanizeA2ACode(summary.resultNotificationStatus) : 'Waiting'}</div></div>
        </div>
      </section>

      <section aria-labelledby="capability-decision-chain-heading">
        <div className="mb-3">
          <p className="text-xs font-black uppercase tracking-[0.18em] text-slate-500">Authority decision chain</p>
          <h2 id="capability-decision-chain-heading" className="mt-1 text-lg font-black text-slate-950">WHO CAN → WHO MAY → WHO SHOULD → HOW → Execution</h2>
        </div>
        <div className="grid gap-4 xl:grid-cols-5">
          {detail.stages.map((stage) => <DecisionCard key={stage.code} stage={stage} />)}
        </div>
      </section>

      {detail.reasonCodes.length > 0 ? (
        <section className="rounded-2xl border border-amber-200 bg-amber-50 p-4">
          <p className="text-xs font-black uppercase tracking-[0.16em] text-amber-800">Current reason evidence</p>
          <div className="mt-3 flex flex-wrap gap-2">
            {detail.reasonCodes.map((reason) => <span key={reason} className="rounded-full bg-white px-3 py-1 text-xs font-black text-amber-900">{humanizeA2ACode(reason)}</span>)}
          </div>
        </section>
      ) : null}

      <div className="grid gap-5 xl:grid-cols-2">
        <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <p className="text-xs font-black uppercase tracking-[0.18em] text-slate-500">Requested capability evidence</p>
          <div className="mt-4"><DetailRows values={detail.request} /></div>
        </section>
        <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <p className="text-xs font-black uppercase tracking-[0.18em] text-slate-500">Selected execution evidence</p>
          <div className="mt-4"><DetailRows values={detail.execution} /></div>
        </section>
      </div>

      <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="text-xs font-black uppercase tracking-[0.18em] text-slate-500">Related authoritative work</p>
            <p className="mt-1 text-sm text-slate-600">Open canonical Task evidence when execution diagnosis is required.</p>
          </div>
          <div className="flex flex-wrap gap-2 text-sm font-black">
            {summary.parentTaskId ? <Link className="rounded-lg border px-3 py-2 text-indigo-700 hover:bg-indigo-50" href={`/tasks/${encodeURIComponent(summary.parentTaskId)}`}>Parent Task</Link> : null}
            {summary.childTaskId ? <Link className="rounded-lg border px-3 py-2 text-indigo-700 hover:bg-indigo-50" href={`/tasks/${encodeURIComponent(summary.childTaskId)}`}>Child Task</Link> : null}
          </div>
        </div>
      </section>

      <details className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <summary className="cursor-pointer text-sm font-black text-slate-900">Technical event evidence</summary>
        <p className="mt-2 text-sm leading-6 text-slate-600">Append-only delegation events are operational evidence only. They do not create provider-selection authority.</p>
        <ol className="mt-4 space-y-3">
          {detail.events.map((event) => (
            <li key={event.eventId} className="rounded-xl border border-slate-200 bg-slate-50 p-3 text-xs">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="font-black text-slate-900">{humanizeA2ACode(event.eventType)}</span>
                <span className="text-slate-500">{event.occurredAt || 'Time unavailable'}</span>
              </div>
              <p className="mt-1 text-slate-600">{humanizeA2ACode(event.fromStatus)} → {humanizeA2ACode(event.toStatus)}</p>
              {event.reasons.length > 0 ? <p className="mt-2 text-slate-600">{event.reasons.map(humanizeA2ACode).join(' · ')}</p> : null}
            </li>
          ))}
        </ol>
      </details>

      <details className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <summary className="cursor-pointer text-sm font-black text-slate-900">Technical identity</summary>
        <div className="mt-4"><DetailRows values={{ delegationId: summary.delegationId, selectedProviderId: summary.selectedProviderId || '', selectedProviderType: summary.selectedProviderType || '', executionKind: summary.executionKind || '' }} /></div>
      </details>
    </div>
  );
}
