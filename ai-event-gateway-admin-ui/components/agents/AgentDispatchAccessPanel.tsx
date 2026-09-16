"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { useAuth } from "@/components/auth/AuthProvider";
import { useDialogAccessibility } from "@/hooks/useDialogAccessibility";
import { coreAdminApi } from "@/lib/api/coreAdminApi";
import type { CoreAgentAuthorizationScope, CoreDispatchFlowView } from "@/lib/types/core";

function normalized(value?: string | null): string {
  return String(value ?? "").trim();
}

function isActive(value?: string | null): boolean {
  return normalized(value).toUpperCase() === "ACTIVE";
}

function accessKey(scope: CoreAgentAuthorizationScope): string {
  return `${normalized(scope.systemCode).toUpperCase()}|${normalized(scope.taskType).toUpperCase()}`;
}

function uniqueScopes(scopes: CoreAgentAuthorizationScope[]): CoreAgentAuthorizationScope[] {
  const seen = new Set<string>();
  return scopes.filter((scope) => {
    const key = accessKey(scope);
    if (!normalized(scope.systemCode) || !normalized(scope.taskType) || seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

function activeScopes(scopes: CoreAgentAuthorizationScope[]): CoreAgentAuthorizationScope[] {
  return scopes.filter((scope) => scope.enabled !== false);
}

type WorkOption = {
  systemCode: string;
  taskType: string;
  sourceLabel: string;
  workLabel: string;
  flowLabel: string;
};

function workOptionsFromFlows(flows: CoreDispatchFlowView[]): WorkOption[] {
  const seen = new Set<string>();
  const options: WorkOption[] = [];
  for (const flow of flows.filter((item) => isActive(item.status))) {
    const systemCode = normalized(flow.sourceSystem);
    if (!systemCode) continue;
    const flowLabel = normalized(flow.flowName ?? flow.flowCode) || systemCode;
    for (const rule of (flow.rules ?? []).filter((item) => item.enabled !== false)) {
      const taskType = normalized(rule.serviceCode);
      if (!taskType) continue;
      const key = `${systemCode.toUpperCase()}|${taskType.toUpperCase()}`;
      if (seen.has(key)) continue;
      seen.add(key);
      options.push({
        systemCode,
        taskType,
        sourceLabel: systemCode,
        workLabel: normalized(rule.ruleName ?? rule.ruleCode) || taskType,
        flowLabel,
      });
    }
  }
  return options.sort((a, b) => `${a.sourceLabel}|${a.workLabel}`.localeCompare(`${b.sourceLabel}|${b.workLabel}`));
}

function AccessBadge({ scope, onRemove }: Readonly<{ scope: CoreAgentAuthorizationScope; onRemove?: () => void }>) {
  return (
    <div className="flex items-center gap-2 rounded-xl border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-950">
      <div className="min-w-0 flex-1">
        <div className="font-black">{normalized(scope.systemCode) || "Unknown source"}</div>
        <div className="mt-0.5 text-xs font-semibold opacity-75">{normalized(scope.taskType) || "Unknown work"}</div>
      </div>
      {onRemove ? <button type="button" onClick={onRemove} className="rounded-lg border border-current/20 bg-white px-2 py-1 text-xs font-black hover:bg-white/70">Remove</button> : null}
    </div>
  );
}

export function AgentDispatchAccessPanel({
  agentId,
  scopes,
  flows,
  onSaved,
}: Readonly<{
  agentId: string;
  scopes: CoreAgentAuthorizationScope[];
  flows: CoreDispatchFlowView[];
  onSaved?: () => Promise<void> | void;
}>) {
  const { activeTenantId } = useAuth();
  const [open, setOpen] = useState(false);
  const dialogRef = useDialogAccessibility(open, () => setOpen(false));
  const [draftScopes, setDraftScopes] = useState<CoreAgentAuthorizationScope[]>([]);
  const [sourceSystem, setSourceSystem] = useState("");
  const [taskType, setTaskType] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const enabled = useMemo(() => activeScopes(scopes), [scopes]);
  const availableWork = useMemo(() => workOptionsFromFlows(flows), [flows]);
  const sourceOptions = useMemo(() => [...new Set(availableWork.map((item) => item.systemCode))], [availableWork]);
  const sourceWork = useMemo(() => availableWork.filter((item) => item.systemCode === sourceSystem), [availableWork, sourceSystem]);
  const recommended = useMemo(() => uniqueScopes(availableWork.map((item) => ({
    tenantId: activeTenantId ?? "",
    systemCode: item.systemCode,
    taskType: item.taskType,
    enabled: true,
  }))), [activeTenantId, availableWork]);

  useEffect(() => {
    if (!open) return;
    setError(null);
    setDraftScopes(activeScopes(scopes));
    const first = availableWork[0];
    setSourceSystem(first?.systemCode ?? "");
    setTaskType(first?.taskType ?? "");
  }, [availableWork, open, scopes]);

  useEffect(() => {
    if (sourceWork.some((item) => item.taskType === taskType)) return;
    setTaskType(sourceWork[0]?.taskType ?? "");
  }, [sourceWork, taskType]);

  function addSelectedRule() {
    if (!activeTenantId || !sourceSystem || !taskType) return;
    setDraftScopes((current) => uniqueScopes([...current, { tenantId: activeTenantId, systemCode: sourceSystem, taskType, enabled: true }]));
  }

  function addRecommendedRules() {
    setDraftScopes((current) => uniqueScopes([...current, ...recommended]));
  }

  function removeRule(index: number) {
    setDraftScopes((current) => current.filter((_, currentIndex) => currentIndex !== index));
  }

  async function save() {
    if (!activeTenantId) {
      setError("Select an administration workspace before changing allowed work.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await coreAdminApi.updateAgentDispatchAccess(agentId, {
        tenantId: activeTenantId,
        scopes: draftScopes.map((scope) => ({
          tenantId: activeTenantId,
          systemCode: normalized(scope.systemCode),
          taskType: normalized(scope.taskType),
          enabled: true,
        })),
        reason: "Updated Allowed Work from the guided Agent workspace",
      });
      await onSaved?.();
      setOpen(false);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : String(reason));
    } finally {
      setSaving(false);
    }
  }

  return (
    <section id="dispatch-access" className={`rounded-2xl border p-5 ${enabled.length === 0 ? "border-amber-200 bg-amber-50" : "border-emerald-200 bg-emerald-50"}`}>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h3 className="font-black text-slate-950">Allowed Work</h3>
          <p className="mt-1 max-w-3xl text-sm leading-6 text-slate-700">Choose which work from active Dispatch Flows this Agent is allowed to execute. Capability approval still decides what the Agent can do; this list only limits where that work may come from.</p>
        </div>
        <button type="button" onClick={() => setOpen(true)} className="rounded-xl bg-blue-700 px-4 py-2 text-sm font-black text-white hover:bg-blue-800">
          {enabled.length === 0 ? "Choose allowed work" : "Change allowed work"}
        </button>
      </div>

      <div className="mt-4 grid gap-2 md:grid-cols-2">
        {enabled.length > 0 ? enabled.map((scope, index) => <AccessBadge key={`${accessKey(scope)}-${index}`} scope={scope} />) : (
          <div className="md:col-span-2 rounded-xl border border-amber-200 bg-white/80 p-4 text-sm text-amber-950">
            No work is allowed yet. The Agent may connect, but it will not be permitted to execute assigned work until at least one active Flow/work pair is selected.
          </div>
        )}
      </div>

      {open ? (
        <div ref={dialogRef} tabIndex={-1} className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/45 p-4 outline-none" role="dialog" aria-modal="true" aria-label={`Allowed Work for ${agentId}`}>
          <div className="max-h-[92vh] w-full max-w-3xl overflow-y-auto rounded-3xl bg-white p-6 shadow-2xl">
            <div className="flex items-start justify-between gap-4 border-b border-slate-100 pb-4">
              <div>
                <h2 className="text-xl font-black text-slate-950">Choose allowed work</h2>
                <p className="mt-1 text-sm leading-6 text-slate-600">The choices below come directly from active Dispatch Flows. You do not need to create or understand a separate Task Definition.</p>
              </div>
              <button type="button" onClick={() => setOpen(false)} disabled={saving} className="rounded-lg px-3 py-1 text-sm font-bold text-slate-500 hover:bg-slate-100">Close</button>
            </div>

            {error ? <div className="mt-4 rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm font-semibold text-rose-900">{error}</div> : null}

            {availableWork.length === 0 ? (
              <div className="mt-5 rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-950">
                <div className="font-black">No active work is available yet</div>
                <p className="mt-1 leading-6">Create or activate a Dispatch Flow with at least one enabled work rule first. Then return here and choose the work this Agent may receive.</p>
                <Link href="/dispatch-flows" className="mt-3 inline-block font-black underline">Open Dispatch Flows</Link>
              </div>
            ) : (
              <>
                <section className="mt-5 rounded-2xl border border-blue-200 bg-blue-50 p-4">
                  <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                    <div>
                      <div className="font-black text-blue-950">Suggested from active flows</div>
                      <p className="mt-1 text-sm leading-6 text-blue-900">Apply all currently active work pairs, then remove anything this Agent should not execute.</p>
                    </div>
                    <button type="button" onClick={addRecommendedRules} className="rounded-xl border border-blue-300 bg-white px-3 py-2 text-sm font-black text-blue-800 hover:bg-blue-100">Use active-flow work</button>
                  </div>
                </section>

                <section className="mt-5 rounded-2xl border border-slate-200 p-4">
                  <div className="font-black text-slate-950">Add one work item</div>
                  <div className="mt-3 grid gap-4 md:grid-cols-2">
                    <label className="text-sm font-bold text-slate-700">Source
                      <select value={sourceSystem} onChange={(event) => setSourceSystem(event.target.value)} className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2 text-sm">
                        {sourceOptions.map((source) => <option key={source} value={source}>{source}</option>)}
                      </select>
                    </label>
                    <label className="text-sm font-bold text-slate-700">Work
                      <select value={taskType} onChange={(event) => setTaskType(event.target.value)} className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2 text-sm">
                        {sourceWork.map((item) => <option key={`${item.systemCode}-${item.taskType}`} value={item.taskType}>{item.workLabel} · {item.flowLabel}</option>)}
                      </select>
                    </label>
                  </div>
                  <button type="button" onClick={addSelectedRule} disabled={!sourceSystem || !taskType} className="mt-4 rounded-xl bg-slate-900 px-4 py-2 text-sm font-black text-white disabled:bg-slate-300">Add work</button>
                </section>
              </>
            )}

            <section className="mt-5">
              <div className="font-black text-slate-950">Allowed after save</div>
              <div className="mt-3 grid gap-2 md:grid-cols-2">
                {draftScopes.length > 0 ? draftScopes.map((scope, index) => <AccessBadge key={`draft-${accessKey(scope)}-${index}`} scope={scope} onRemove={() => removeRule(index)} />) : <div className="md:col-span-2 rounded-xl border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600">No work will be allowed.</div>}
              </div>
            </section>

            <div className="mt-6 flex flex-wrap justify-between gap-3 border-t border-slate-100 pt-4">
              <Link href="/dispatch-flows" className="rounded-xl border border-slate-200 px-4 py-2 text-sm font-black text-slate-700 hover:bg-slate-50">Open Dispatch Flows</Link>
              <div className="flex gap-2">
                <button type="button" onClick={() => setOpen(false)} disabled={saving} className="rounded-xl border border-slate-200 px-4 py-2 text-sm font-black text-slate-700 hover:bg-slate-50">Cancel</button>
                <button type="button" onClick={() => void save()} disabled={saving} className="rounded-xl bg-blue-700 px-4 py-2 text-sm font-black text-white hover:bg-blue-800 disabled:bg-slate-300">{saving ? "Saving…" : "Save allowed work"}</button>
              </div>
            </div>
          </div>
        </div>
      ) : null}
    </section>
  );
}
