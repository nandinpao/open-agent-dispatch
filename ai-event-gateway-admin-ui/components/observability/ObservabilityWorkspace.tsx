'use client';

import { useEffect, useMemo, useState } from 'react';

type Row = Record<string, unknown>;
type Tab = 'trace' | 'economics' | 'memory';

async function readJson(path: string, init?: RequestInit): Promise<unknown> {
  const response = await fetch(path, { ...init, credentials: 'include', headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) } });
  if (!response.ok) throw new Error(`${response.status} ${response.statusText}`);
  return response.json();
}

function text(v: unknown): string { return v == null ? '—' : String(v); }

export function ObservabilityWorkspace() {
  const [tab, setTab] = useState<Tab>('trace');
  const [taskId, setTaskId] = useState('');
  const [trace, setTrace] = useState<Row[]>([]);
  const [economics, setEconomics] = useState<Row[]>([]);
  const [memory, setMemory] = useState<Row[]>([]);
  const [selected, setSelected] = useState<Row | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function loadTrace() {
    setLoading(true); setError(null);
    try {
      const q = taskId.trim() ? `?taskId=${encodeURIComponent(taskId.trim())}&limit=500` : '?limit=200';
      const rows = await readJson(`/core-api/admin/observability/decision-trace${q}`) as Row[];
      setTrace(rows); setSelected(rows[0] ?? null);
    } catch (e) { setError(e instanceof Error ? e.message : 'Unable to load Decision Trace.'); }
    finally { setLoading(false); }
  }
  async function loadEconomics() {
    setLoading(true); setError(null);
    try { setEconomics(await readJson('/core-api/admin/observability/economics/summary?limit=500') as Row[]); }
    catch (e) { setError(e instanceof Error ? e.message : 'Unable to load economics.'); }
    finally { setLoading(false); }
  }
  async function loadMemory() {
    setLoading(true); setError(null);
    try { setMemory(await readJson('/core-api/admin/observability/execution-memory?limit=500') as Row[]); }
    catch (e) { setError(e instanceof Error ? e.message : 'Unable to load Execution Memory.'); }
    finally { setLoading(false); }
  }
  async function refreshMemory() {
    setLoading(true); setError(null);
    try { await readJson('/core-api/admin/observability/execution-memory/refresh?days=30', { method: 'POST' }); await loadMemory(); }
    catch (e) { setError(e instanceof Error ? e.message : 'Unable to refresh Execution Memory.'); setLoading(false); }
  }

  // Initial trace bootstrap is intentionally unfiltered; subsequent loads are operator-triggered with the current Task ID.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { void loadTrace(); }, []);
  const traceRows = useMemo(() => trace, [trace]);

  return <div className="mx-auto max-w-[1600px] space-y-5 p-6">
    <div className="flex flex-wrap items-start justify-between gap-4">
      <div>
        <div className="text-xs font-bold uppercase tracking-[0.18em] text-slate-500">Operations / Observability</div>
        <h1 className="mt-1 text-2xl font-semibold text-slate-950">Decision Trace, Economics & Execution Memory</h1>
        <p className="mt-2 max-w-4xl text-sm text-slate-600">Read-only execution provenance plus immutable cost evidence and rebuildable executor-level statistics. These views never select a Provider or create an Assignment.</p>
      </div>
      <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs text-amber-900">Learning and Fast Path promotion remain unavailable until their current governance prerequisites are satisfied.</div>
    </div>

    <div className="flex gap-2 border-b border-slate-200">
      {([['trace','Decision Trace'],['economics','Economics'],['memory','Execution Memory']] as const).map(([id,label]) => <button key={id} onClick={() => { setTab(id); if(id==='economics') void loadEconomics(); if(id==='memory') void loadMemory(); }} className={`border-b-2 px-4 py-3 text-sm font-semibold ${tab===id?'border-slate-950 text-slate-950':'border-transparent text-slate-500'}`}>{label}</button>)}
    </div>

    {error && <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800">{error}</div>}

    {tab==='trace' && <div className="space-y-4">
      <div className="flex gap-2"><input value={taskId} onChange={e=>setTaskId(e.target.value)} placeholder="Task ID (optional)" className="min-w-[360px] rounded-lg border border-slate-300 px-3 py-2 text-sm"/><button onClick={()=>void loadTrace()} className="rounded-lg bg-slate-950 px-4 py-2 text-sm font-semibold text-white">Load trace</button></div>
      <div className="grid min-h-[560px] grid-cols-[minmax(0,1fr)_420px] overflow-hidden rounded-xl border border-slate-200 bg-white">
        <div className="overflow-auto border-r border-slate-200">
          <table className="w-full text-left text-xs"><thead className="sticky top-0 bg-slate-50 text-slate-500"><tr><th className="p-3">Time</th><th className="p-3">Stage</th><th className="p-3">Result</th><th className="p-3">Provider</th><th className="p-3">Tier</th></tr></thead><tbody>{traceRows.map((r,i)=><tr key={text(r.trace_event_id)+i} onClick={()=>setSelected(r)} className={`cursor-pointer border-t border-slate-100 ${selected===r?'bg-amber-50':'hover:bg-slate-50'}`}><td className="p-3 whitespace-nowrap">{text(r.occurred_at)}</td><td className="p-3 font-semibold">{text(r.stage)}</td><td className="p-3">{text(r.result)}</td><td className="p-3">{text(r.provider_type)}</td><td className="p-3">{text(r.retention_tier)}</td></tr>)}</tbody></table>
        </div>
        <div className="overflow-auto p-5"><h2 className="text-sm font-semibold text-slate-950">Evidence detail</h2>{selected ? <dl className="mt-4 space-y-3">{Object.entries(selected).map(([k,v])=><div key={k}><dt className="text-xs font-bold uppercase tracking-wide text-slate-400">{k}</dt><dd className="mt-1 break-words text-xs text-slate-800">{typeof v==='object'?JSON.stringify(v,null,2):text(v)}</dd></div>)}</dl> : <p className="mt-3 text-sm text-slate-500">Select an event.</p>}</div>
      </div>
    </div>}

    {tab==='economics' && <div className="overflow-auto rounded-xl border border-slate-200 bg-white"><table className="w-full text-left text-sm"><thead className="bg-slate-50 text-xs text-slate-500"><tr><th className="p-3">Capability</th><th className="p-3">Provider</th><th className="p-3">Samples</th><th className="p-3">Booked actual</th><th className="p-3">Normalized comparison</th><th className="p-3">Currency</th></tr></thead><tbody>{economics.map((r,i)=><tr key={i} className="border-t border-slate-100"><td className="p-3">{text(r.capability_code)}</td><td className="p-3">{text(r.provider_type)} / {text(r.provider_id)}</td><td className="p-3">{text(r.entry_count)}</td><td className="p-3">{text(r.booked_actual_cost)}</td><td className="p-3">{text(r.normalized_comparison_cost)}</td><td className="p-3">{text(r.currency)}</td></tr>)}</tbody></table></div>}

    {tab==='memory' && <div className="space-y-3"><div className="flex justify-end"><button disabled={loading} onClick={()=>void refreshMemory()} className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-semibold">Refresh last 30 days</button></div><div className="overflow-auto rounded-xl border border-slate-200 bg-white"><table className="w-full text-left text-sm"><thead className="bg-slate-50 text-xs text-slate-500"><tr><th className="p-3">Capability</th><th className="p-3">Provider</th><th className="p-3">Binding</th><th className="p-3">Samples</th><th className="p-3">Observed success</th><th className="p-3">Avg latency</th><th className="p-3">Unbooked cost</th></tr></thead><tbody>{memory.map((r,i)=><tr key={i} className="border-t border-slate-100"><td className="p-3">{text(r.capability_code)} / {text(r.operation)}</td><td className="p-3">{text(r.provider_type)} / {text(r.provider_id)}</td><td className="p-3">{text(r.binding_id)}</td><td className="p-3">{text(r.sample_count)}</td><td className="p-3">{text(r.observed_success_rate)}</td><td className="p-3">{text(r.average_latency_ms)} ms</td><td className="p-3">{text(r.unbooked_count)}</td></tr>)}</tbody></table></div></div>}
    {loading && <div className="text-xs text-slate-500">Refreshing…</div>}
  </div>;
}
