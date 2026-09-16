"use client";

import { useCallback, useEffect, useState } from "react";
import { taskAdminApi } from "@/lib/api/domains/taskAdminApi";
import type { CoreTaskFinalizationAuthorityView } from "@/lib/types/domains/taskOperations";

function value(v?: string | number | null) { return v === undefined || v === null || String(v).trim() === "" ? "—" : String(v); }

export function TaskFinalizationAuthorityPanel({ taskId }: Readonly<{ taskId: string }>) {
  const [data, setData] = useState<CoreTaskFinalizationAuthorityView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [retrying, setRetrying] = useState(false);
  const load = useCallback(async () => {
    try { setError(null); setData(await taskAdminApi.getTaskFinalizationAuthority(taskId)); }
    catch (e) { setError(e instanceof Error ? e.message : "Unable to load canonical Task state authority."); }
  }, [taskId]);
  useEffect(() => { void load(); }, [load]);
  const retry = async () => {
    setRetrying(true);
    try { setData(await taskAdminApi.retryTaskFinalization(taskId, "Operator retry from Task Detail")); setError(null); }
    catch (e) { setError(e instanceof Error ? e.message : "Finalization retry failed."); }
    finally { setRetrying(false); }
  };
  if (error && !data) return <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">Canonical state authority unavailable: {error}</div>;
  if (!data) return <div className="rounded-xl border border-slate-200 bg-white p-4 text-sm text-slate-500">Loading canonical Task state…</div>;
  const t=data.task; const recoverable=["RETRY_PENDING","MANUAL_RECOVERY_REQUIRED"].includes(t.finalizationState);
  return <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
    <div className="flex flex-wrap items-start justify-between gap-3">
      <div><p className="text-sm font-black text-slate-950">Canonical Task State Authority</p><p className="mt-1 text-xs text-slate-500">A0-R2: legacy status is compatibility-only for closure. CLOSED is committed only by FinalizationCoordinator.</p></div>
      {recoverable && <button type="button" disabled={retrying} onClick={() => void retry()} className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-xs font-bold text-slate-800 disabled:opacity-50">{retrying ? "Retrying…" : "Retry finalization"}</button>}
    </div>
    {error && <p className="mt-3 rounded-lg bg-amber-50 p-2 text-xs text-amber-900">{error}</p>}
    <div className="mt-4 grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
      {[['Lifecycle',t.lifecycle],['Phase',t.phase],['Outcome',t.outcome],['Legacy Status',t.legacyStatus],['Terminalization',t.terminalizationReason],['Finalization',t.finalizationState],['Checkpoint',t.checkpoint],['Attempts',t.attemptCount]].map(([label,v]) => <div key={String(label)} className="rounded-xl bg-slate-50 p-3"><p className="text-xs font-bold uppercase tracking-wide text-slate-500">{label}</p><p className="mt-1 break-words text-sm font-semibold text-slate-900">{value(v as string | number)}</p></div>)}
    </div>
    {t.lastErrorCode && <div className="mt-3 rounded-xl border border-rose-200 bg-rose-50 p-3 text-xs text-rose-900"><strong>{t.lastErrorCode}</strong>{t.lastErrorMessage ? ` — ${t.lastErrorMessage}` : ''}</div>}
    {data.steps.length > 0 && <div className="mt-4"><p className="mb-2 text-xs font-black uppercase tracking-wide text-slate-500">Finalization checkpoints</p><div className="grid gap-2 md:grid-cols-2">{data.steps.map(step => <div key={step.name} className="flex items-center justify-between rounded-lg border border-slate-200 px-3 py-2 text-xs"><span className="font-semibold text-slate-800">{step.order}. {step.name}</span><span className="font-bold text-slate-600">{step.status}</span></div>)}</div></div>}
    {data.conditions.filter(c=>c.state==='ACTIVE').length > 0 && <div className="mt-4"><p className="mb-2 text-xs font-black uppercase tracking-wide text-slate-500">Active conditions</p><div className="space-y-2">{data.conditions.filter(c=>c.state==='ACTIVE').map(c => <div key={c.type} className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-950"><strong>{c.type}</strong> · {c.resolution}{c.reason ? ` — ${c.reason}` : ''}</div>)}</div></div>}
  </div>;
}
