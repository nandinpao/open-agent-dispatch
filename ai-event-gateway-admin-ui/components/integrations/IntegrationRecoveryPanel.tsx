'use client';

import Link from 'next/link';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  integrationConfigurationHref,
  loadIntegrationRecovery,
  preflightIntegrationRecovery,
  recoveryCategoryLabel,
  recoveryGuidance,
  retryIntegrationRecovery,
  type IntegrationRecoveryFailure,
  type IntegrationRecoveryPreflight,
  type IntegrationRecoverySnapshot,
} from '@/lib/integrations/integrationRecovery';

type Mode = 'failures' | 'recovery';

function timeValue(value?: string) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

export function IntegrationRecoveryPanel({ mode }: Readonly<{ mode: Mode }>) {
  const [snapshot, setSnapshot] = useState<IntegrationRecoverySnapshot | null>(null);
  const [selectedId, setSelectedId] = useState('');
  const [preflight, setPreflight] = useState<IntegrationRecoveryPreflight | null>(null);
  const [reason, setReason] = useState('');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');

  const load = useCallback(async () => {
    setLoading(true); setMessage('');
    try {
      const value = await loadIntegrationRecovery(300);
      setSnapshot(value);
      setSelectedId((current) => current || value.failures[0]?.actionId || '');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Unable to load Integration Recovery evidence.');
    } finally { setLoading(false); }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const selected = useMemo(() => snapshot?.failures.find((item) => item.actionId === selectedId) ?? null, [snapshot, selectedId]);

  async function runPreflight(failure: IntegrationRecoveryFailure) {
    if (!failure.actionId) return;
    setBusy(true); setMessage(''); setPreflight(null);
    try {
      const value = await preflightIntegrationRecovery(failure.actionId);
      setPreflight(value);
      setMessage(value.retryAllowed ? 'Recovery preflight passed. Review the evidence and provide a reason before retrying.' : 'Recovery preflight is blocked. Follow the repair guidance before retrying.');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Recovery preflight failed.');
    } finally { setBusy(false); }
  }

  async function governedRetry(failure: IntegrationRecoveryFailure) {
    if (!failure.actionId || !preflight?.retryAllowed || preflight.actionId !== failure.actionId) return;
    setBusy(true); setMessage('');
    try {
      await retryIntegrationRecovery(failure.actionId, reason);
      setMessage('Governed retry scheduled after a fresh server-side preflight.');
      setReason(''); setPreflight(null);
      await load();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Governed retry failed.');
    } finally { setBusy(false); }
  }

  if (loading) return <div role="status" className="rounded-2xl border bg-white p-5 text-sm text-slate-600">Loading timestamp-ordered AdapterAction audit evidence…</div>;
  if (!snapshot) return <div role="alert" className="rounded-2xl border border-rose-200 bg-rose-50 p-5 text-sm font-semibold text-rose-900">{message || 'Integration Recovery evidence is unavailable.'}</div>;

  return <div className="space-y-4">
    {message ? <div role="status" className="rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm font-semibold text-slate-700">{message}</div> : null}
    <div className="flex justify-end"><button type="button" disabled={busy} onClick={() => void load()} className="rounded-lg border px-3 py-2 text-sm font-bold disabled:opacity-50">Refresh failure evidence</button></div>

    {snapshot.failures.length === 0 ? <section className="rounded-2xl border border-emerald-200 bg-emerald-50 p-5 text-sm text-emerald-950"><h3 className="font-black">No failures in the current audit window</h3><p className="mt-1">The canonical Issue Tracking execution audit does not currently contain a failed provider operation.</p></section> : <div className="grid gap-4 xl:grid-cols-[minmax(18rem,.38fr)_minmax(0,.62fr)]">
      <section className="rounded-2xl border border-slate-200 bg-white p-3 shadow-sm">
        <div className="px-2 pb-2"><h3 className="font-black text-slate-950">Failure evidence</h3><p className="mt-1 text-xs leading-5 text-slate-500">Ordered by executor-audit <code>createdAt DESC</code>. This list is historical evidence; the badge shows whether the underlying AdapterAction still needs attention.</p></div>
        <div className="max-h-[42rem] space-y-2 overflow-auto">{snapshot.failures.map((failure) => <button key={`${failure.auditId}-${failure.actionId}`} type="button" onClick={() => { setSelectedId(failure.actionId ?? ''); setPreflight(null); setReason(''); }} className={`w-full rounded-xl border p-3 text-left ${selected?.auditId === failure.auditId ? 'border-slate-900 bg-slate-950 text-white' : 'border-slate-200 bg-white hover:bg-slate-50'}`}>
          <div className="flex items-start justify-between gap-2"><span className="text-sm font-black">{recoveryCategoryLabel(failure.category)}</span><span className={`rounded-full px-2 py-0.5 text-[10px] font-black ${selected?.auditId === failure.auditId ? 'bg-white/15' : failure.current ? 'bg-rose-100 text-rose-800' : 'bg-slate-100 text-slate-600'}`}>{failure.current ? 'CURRENT' : 'HISTORICAL'}</span></div>
          <div className={`mt-1 text-xs ${selected?.auditId === failure.auditId ? 'text-slate-300' : 'text-slate-500'}`}>{timeValue(failure.occurredAt)}</div>
          <div className={`mt-2 truncate text-xs font-semibold ${selected?.auditId === failure.auditId ? 'text-slate-200' : 'text-slate-700'}`}>{failure.providerFailureCode || failure.message || failure.actionId}</div>
        </button>)}</div>
      </section>

      {selected ? <section className="space-y-4">
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="flex flex-wrap items-start justify-between gap-3"><div><div className="text-xs font-black uppercase tracking-[.15em] text-slate-500">{mode === 'recovery' ? 'Recovery case' : 'Failure detail'}</div><h3 className="mt-1 text-xl font-black text-slate-950">{recoveryCategoryLabel(selected.category)}</h3><p className="mt-2 max-w-3xl text-sm leading-6 text-slate-600">{recoveryGuidance(selected.category)}</p></div><span className={`rounded-full px-3 py-1 text-xs font-black ${selected.providerHealthImpact === 'HEALTHY' || selected.providerHealthImpact === 'NONE' ? 'bg-emerald-100 text-emerald-800' : selected.providerHealthImpact === 'THROTTLED' ? 'bg-amber-100 text-amber-900' : 'bg-rose-100 text-rose-800'}`}>Provider health impact: {selected.providerHealthImpact || 'UNKNOWN'}</span></div>
          <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4"><Fact label="Action" value={selected.actionId}/><Fact label="Task" value={selected.taskId}/><Fact label="HTTP" value={selected.providerStatusCode == null ? '—' : String(selected.providerStatusCode)}/><Fact label="Occurred" value={timeValue(selected.occurredAt)}/><Fact label="Connection" value={selected.connectionId}/><Fact label="Project Mapping" value={selected.projectMappingId}/><Fact label="Failure code" value={selected.providerFailureCode}/><Fact label="Correlation" value={selected.correlationId}/></div>
          {selected.message ? <details className="mt-4 rounded-xl border border-slate-200 bg-slate-50 p-3"><summary className="cursor-pointer text-sm font-black text-slate-700">Technical provider evidence</summary><pre className="mt-3 whitespace-pre-wrap break-words text-xs leading-5 text-slate-700">{selected.message}</pre></details> : null}
          <div className="mt-4 flex flex-wrap gap-2"><Link href={integrationConfigurationHref(selected)} className="rounded-lg bg-slate-950 px-3 py-2 text-sm font-black text-white">Open Integration Configuration</Link>{selected.taskId ? <Link href={`/tasks/${encodeURIComponent(selected.taskId)}`} className="rounded-lg border px-3 py-2 text-sm font-bold">Open Task</Link> : null}</div>
        </div>

        {mode === 'recovery' ? <div className="rounded-2xl border border-indigo-200 bg-indigo-50 p-5">
          <div className="text-xs font-black uppercase tracking-[.15em] text-indigo-700">Detect → Diagnose → Repair → Validate → Retry → Verify</div>
          <h3 className="mt-1 text-lg font-black text-indigo-950">Governed recovery</h3>
          <ol className="mt-4 grid gap-3 md:grid-cols-3">
            <Step n="1" title="Repair configuration" text="Connections, credentials and mappings are edited only in Integration Configuration." done={false}/>
            <Step n="2" title="Recovery Preflight" text="Re-resolve Integration Identity, probe provider metadata and preview required fields." done={Boolean(preflight?.retryAllowed)}/>
            <Step n="3" title="Governed Retry" text="The server runs preflight again immediately before scheduling the retry." done={false}/>
          </ol>
          <div className="mt-4 flex flex-wrap gap-2"><Link href={integrationConfigurationHref(selected)} className="rounded-lg border border-indigo-300 bg-white px-3 py-2 text-sm font-black text-indigo-950">1 · Repair in Integration Configuration</Link><button type="button" disabled={busy || !selected.current || !selected.actionId || selected.category === 'OUTCOME_UNCERTAIN'} onClick={() => void runPreflight(selected)} className="rounded-lg bg-indigo-800 px-3 py-2 text-sm font-black text-white disabled:opacity-50">2 · Run Recovery Preflight</button></div>
          {selected.category === 'OUTCOME_UNCERTAIN' ? <div className="mt-4 rounded-xl border border-amber-300 bg-amber-50 p-3 text-sm font-semibold text-amber-950">Retry is intentionally disabled. This mutating operation must be reconciled against the provider first.</div> : null}
          {preflight && preflight.actionId === selected.actionId ? <div className={`mt-4 rounded-xl border p-4 ${preflight.retryAllowed ? 'border-emerald-300 bg-emerald-50' : 'border-rose-300 bg-rose-50'}`}>
            <div className="flex flex-wrap items-center justify-between gap-2"><div className="font-black">Preflight {preflight.retryAllowed ? 'PASS' : 'BLOCKED'}</div><span className="text-xs font-bold">Checked {timeValue(preflight.checkedAt)}</span></div>
            {preflight.checks.length ? <div className="mt-3"><div className="text-xs font-black uppercase tracking-wide">Checks</div><ul className="mt-1 list-disc space-y-1 pl-5 text-sm">{preflight.checks.map((item) => <li key={item}>{item}</li>)}</ul></div> : null}
            {preflight.blockers.length ? <div className="mt-3"><div className="text-xs font-black uppercase tracking-wide">Blockers</div><ul className="mt-1 list-disc space-y-1 pl-5 text-sm">{preflight.blockers.map((item) => <li key={item}>{item}</li>)}</ul></div> : null}
            {preflight.retryAllowed ? <div className="mt-4 grid gap-3"><label className="text-sm font-black">Recovery reason<textarea value={reason} onChange={(event) => setReason(event.target.value)} placeholder="Describe what was repaired or verified before this retry." className="mt-1 min-h-24 w-full rounded-xl border border-emerald-300 bg-white px-3 py-2 font-normal"/></label><div className="flex items-center justify-between gap-3"><span className="text-xs text-emerald-900">Minimum 12 characters. Retry re-runs server-side preflight to prevent stale approval.</span><button type="button" disabled={busy || reason.trim().length < 12} onClick={() => void governedRetry(selected)} className="rounded-lg bg-emerald-700 px-4 py-2 text-sm font-black text-white disabled:opacity-50">3 · Schedule Governed Retry</button></div></div> : null}
          </div> : null}
        </div> : null}
      </section> : null}
    </div>}
  </div>;
}

function Fact({ label, value }: { label: string; value?: string | null }) { return <div className="rounded-xl border border-slate-200 bg-slate-50 p-3"><div className="text-[11px] font-black uppercase tracking-wide text-slate-500">{label}</div><div className="mt-1 break-words text-sm font-bold text-slate-900">{value || '—'}</div></div>; }
function Step({ n, title, text, done }: { n: string; title: string; text: string; done: boolean }) { return <li className={`list-none rounded-xl border p-3 ${done ? 'border-emerald-300 bg-emerald-50' : 'border-indigo-200 bg-white'}`}><div className="text-xs font-black">{n} · {title}</div><p className="mt-1 text-xs leading-5">{text}</p></li>; }
