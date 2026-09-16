"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { taskAdminApi } from "@/lib/api/domains/taskAdminApi";
import type { CoreFlowRuleEvaluationEvidence, CoreTaskFlowMatchAuthorityView } from "@/lib/types/domains/taskOperations";

function show(value?: string | number | null) { return value === undefined || value === null || String(value).trim() === "" ? "—" : String(value); }
function resultTone(result: string) {
  if (result === "MATCHED") return "border-emerald-200 bg-emerald-50 text-emerald-900";
  if (result === "AMBIGUOUS") return "border-rose-200 bg-rose-50 text-rose-900";
  return "border-amber-200 bg-amber-50 text-amber-950";
}
function ruleLabel(rule: CoreFlowRuleEvaluationEvidence) { return rule.ruleCode ?? rule.ruleId ?? "Unnamed rule"; }

export function TaskFlowMatchAuthorityPanel({ taskId }: Readonly<{ taskId: string }>) {
  const [data, setData] = useState<CoreTaskFlowMatchAuthorityView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const load = useCallback(async () => {
    try { setError(null); setData(await taskAdminApi.getTaskFlowMatchAuthority(taskId)); }
    catch (caught) { setError(caught instanceof Error ? caught.message : "Unable to load Flow Match authority."); }
  }, [taskId]);
  useEffect(() => { void load(); }, [load]);
  const evaluated = useMemo(() => data?.evaluationSet?.evaluatedRules ?? [], [data]);
  if (error && !data) return <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600">Flow Match authority is not available for this Task: {error}</div>;
  if (!data) return <div className="rounded-xl border border-slate-200 bg-white p-4 text-sm text-slate-500">Loading Flow Match authority…</div>;
  const d = data.decision;
  return <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
    <div className="flex flex-wrap items-start justify-between gap-3">
      <div>
        <p className="text-sm font-black text-slate-950">Deterministic Flow Match Authority</p>
        <p className="mt-1 text-xs text-slate-500">A0-R3 evaluates all eligible deterministic rules. Priority selects the minimum matched bucket; it never breaks a same-priority tie.</p>
      </div>
      <span className={`rounded-full border px-3 py-1 text-xs font-black ${resultTone(d.matchResult)}`}>{d.matchResult}</span>
    </div>
    <div className="mt-4 grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
      {[['Flow', d.flowId], ['Flow Version', d.flowVersion], ['Matched Rule', d.matchedRuleId], ['Priority', d.matchedRulePriority], ['Service Code', d.outputServiceCode], ['Evaluated Rules', d.evaluatedRuleCount], ['Evaluator', d.evaluatorVersion], ['Authority', d.decisionAuthorityVersion]].map(([label,value]) => <div key={String(label)} className="rounded-xl bg-slate-50 p-3"><p className="text-xs font-bold uppercase tracking-wide text-slate-500">{label}</p><p className="mt-1 break-words text-sm font-semibold text-slate-900">{show(value as string | number)}</p></div>)}
    </div>
    {data.issuePolicyAuthority ? <div className="mt-4 rounded-xl border border-blue-100 bg-blue-50/60 p-3">
      <p className="text-xs font-black uppercase tracking-wide text-blue-700">Issue Policy Authority</p>
      <div className="mt-2 grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
        {[['Rule policy', data.issuePolicyAuthority.ruleIssueSyncPolicy], ['Flow policy', data.issuePolicyAuthority.flowIssueSyncPolicy], ['Resolution source', data.issuePolicyAuthority.issueSyncPolicySource], ['Effective policy', data.issuePolicyAuthority.effectiveIssueSyncPolicy]].map(([label,value]) => <div key={String(label)} className="rounded-lg bg-white p-2.5"><p className="text-[11px] font-bold uppercase tracking-wide text-slate-500">{label}</p><p className="mt-1 text-sm font-semibold text-slate-900">{show(value as string)}</p></div>)}
      </div>
    </div> : null}
    {d.matchResult === 'NO_MATCH' && <div className="mt-3 rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm text-amber-950"><strong>No deterministic rule matched.</strong> The canonical next step is Triage, not the Source default Agent Pool.{d.closestRuleId ? ` Closest rule: ${d.closestRuleId}; first missing/mismatched criterion: ${d.closestRuleFailedCriterion ?? '—'}.` : ''}</div>}
    {d.matchResult === 'AMBIGUOUS' && <div className="mt-3 rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-950"><strong>Configuration ambiguity.</strong> Multiple rules matched at the same minimum priority. OpenDispatch fails closed; rule ID or update time is never used as an authority tie-breaker.</div>}
    {d.outputCapabilityRequirements?.length > 0 && <div className="mt-4"><p className="text-xs font-black uppercase tracking-wide text-slate-500">Required capabilities</p><div className="mt-2 flex flex-wrap gap-2">{d.outputCapabilityRequirements.map(code => <span key={code} className="rounded-full bg-blue-50 px-2.5 py-1 text-xs font-bold text-blue-900">{code}</span>)}</div></div>}
    {evaluated.length > 0 && <details className="mt-4 rounded-xl border border-slate-200"><summary className="cursor-pointer px-3 py-2 text-xs font-black text-slate-700">Evaluation evidence · {evaluated.length} rules</summary><div className="overflow-x-auto border-t border-slate-200"><table className="min-w-full text-xs"><thead className="bg-slate-50 text-left text-slate-500"><tr><th className="px-3 py-2">Rule</th><th className="px-3 py-2">Priority</th><th className="px-3 py-2">Matched</th><th className="px-3 py-2">First failed criterion</th><th className="px-3 py-2">Diagnostic ratio</th></tr></thead><tbody className="divide-y divide-slate-100">{evaluated.map((rule,index) => <tr key={`${rule.ruleId ?? rule.ruleCode ?? 'rule'}-${index}`}><td className="px-3 py-2 font-semibold text-slate-800">{ruleLabel(rule)}</td><td className="px-3 py-2">{show(rule.priority)}</td><td className="px-3 py-2">{rule.matched ? 'YES' : 'NO'}</td><td className="px-3 py-2">{show(rule.failedCriterion)}</td><td className="px-3 py-2">{rule.matchRatio === undefined ? '—' : `${Math.round(rule.matchRatio * 100)}%`}</td></tr>)}</tbody></table></div></details>}
  </div>;
}
