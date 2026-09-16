'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';

type Row = Record<string, unknown>;
type Tab = 'recommendations' | 'candidates' | 'certifications' | 'hints';

async function readJson(path: string, init?: RequestInit): Promise<unknown> {
  const response = await fetch(path, { ...init, headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) }, cache: 'no-store' });
  const text = await response.text();
  if (!response.ok) throw new Error(text || `HTTP ${response.status}`);
  return text ? JSON.parse(text) : null;
}

function idOf(row: Row): string {
  return String(row.recommendation_id ?? row.recommendationId ?? row.candidate_id ?? row.candidateId ?? row.certification_id ?? row.certificationId ?? row.hint_id ?? row.hintId ?? '');
}

function statusOf(row: Row): string { return String(row.status ?? row.result ?? ''); }

export function LearningGovernanceWorkspace() {
  const [tab, setTab] = useState<Tab>('recommendations');
  const [rows, setRows] = useState<Row[]>([]);
  const [selectedId, setSelectedId] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const endpoint = useMemo(() => ({
    recommendations: '/core-api/admin/learning-governance/recommendations?limit=200',
    candidates: '/core-api/admin/learning-governance/fast-path/candidates?limit=200',
    certifications: '/core-api/admin/learning-governance/fast-path/certifications?limit=200',
    hints: '/core-api/admin/learning-governance/fast-path/runtime-hints?limit=200',
  })[tab], [tab]);

  const load = useCallback(async () => {
    setBusy(true); setError('');
    try {
      const value = await readJson(endpoint);
      const list = Array.isArray(value) ? value as Row[] : [];
      setRows(list);
      setSelectedId((current) => current && list.some((r) => idOf(r) === current) ? current : idOf(list[0] ?? {}));
    } catch (e) { setError(e instanceof Error ? e.message : 'Unable to load Learning Governance data.'); }
    finally { setBusy(false); }
  }, [endpoint]);

  useEffect(() => { void load(); }, [load]);

  const selected = rows.find((row) => idOf(row) === selectedId) ?? rows[0];

  async function generateRecommendations() {
    setBusy(true); setError('');
    try { await readJson('/core-api/admin/learning-governance/recommendations/from-memory?limit=200', { method: 'POST' }); await load(); }
    catch (e) { setError(e instanceof Error ? e.message : 'Recommendation generation failed.'); setBusy(false); }
  }

  return (
    <div className="space-y-4">
      <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <div className="text-xs font-black uppercase tracking-[0.18em] text-slate-500">Governed learning</div>
            <h2 className="mt-1 text-xl font-black text-slate-950">Memory may recommend. It never authorizes.</h2>
            <p className="mt-2 max-w-4xl text-sm leading-6 text-slate-600">Recommendations, Replay, Shadow and human Certification are governance evidence. A certified Runtime Hint is semantic only: current WHO CAN, WHO MAY, WHO SHOULD, HOW, authorization epoch, lease and fencing remain mandatory.</p>
          </div>
          <button disabled={busy} onClick={() => void generateRecommendations()} className="rounded-xl border border-blue-200 bg-blue-50 px-4 py-2 text-sm font-black text-blue-800 disabled:opacity-50">Generate recommendations from memory</button>
        </div>
      </section>

      <section className="rounded-3xl border border-slate-200 bg-white shadow-sm">
        <div className="flex flex-wrap gap-2 border-b border-slate-200 p-4">
          {(['recommendations','candidates','certifications','hints'] as const).map((value) => (
            <button key={value} onClick={() => { setTab(value); setSelectedId(''); }} className={`rounded-xl px-3 py-2 text-xs font-black uppercase tracking-wide ${tab === value ? 'bg-slate-950 text-white' : 'bg-slate-100 text-slate-700'}`}>{value}</button>
          ))}
        </div>
        {error ? <div className="m-4 rounded-2xl border border-rose-200 bg-rose-50 p-3 text-sm font-bold text-rose-800">{error}</div> : null}
        <div className="grid min-h-[480px] lg:grid-cols-[minmax(320px,0.8fr)_minmax(0,1.2fr)]">
          <div className="border-r border-slate-200 p-3">
            <div className="mb-2 flex items-center justify-between text-xs font-black uppercase tracking-wide text-slate-500"><span>{rows.length} records</span><button onClick={() => void load()} className="text-blue-700">Refresh</button></div>
            <div className="space-y-2">
              {rows.map((row) => {
                const id = idOf(row); const selectedRow = id === selectedId;
                return <button key={id} onClick={() => setSelectedId(id)} className={`w-full rounded-2xl border p-3 text-left ${selectedRow ? 'border-amber-300 bg-amber-50' : 'border-slate-200 bg-white hover:bg-slate-50'}`}>
                  <div className="truncate text-sm font-black text-slate-950">{String(row.title ?? row.problem_signature ?? row.problemSignature ?? id)}</div>
                  <div className="mt-1 flex justify-between gap-2 text-xs text-slate-500"><span className="truncate">{id}</span><span className="font-black">{statusOf(row)}</span></div>
                </button>;
              })}
              {!busy && rows.length === 0 ? <div className="rounded-2xl border border-dashed border-slate-300 p-6 text-center text-sm text-slate-500">No records.</div> : null}
            </div>
          </div>
          <div className="p-5">
            <div className="text-xs font-black uppercase tracking-wide text-slate-500">Evidence detail</div>
            {selected ? <pre className="mt-3 max-h-[650px] overflow-auto rounded-2xl bg-slate-950 p-4 text-xs leading-5 text-slate-100">{JSON.stringify(selected, null, 2)}</pre> : <p className="mt-3 text-sm text-slate-500">Select a record to inspect immutable evidence and governance state.</p>}
          </div>
        </div>
      </section>
    </div>
  );
}
