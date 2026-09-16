'use client';

import { useCallback, useEffect, useState } from 'react';
import { useAuth } from '@/components/auth/AuthProvider';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import type { CoreFlowRoutingMigrationState, CoreRoutingAuthorityShadowResult } from '@/lib/types/core';

function errorMessage(cause: unknown, fallback: string) { return cause instanceof Error && cause.message ? cause.message : fallback; }
function value(v: unknown) { return v === undefined || v === null || v === '' ? '—' : String(v); }

export function RoutingAuthorityCutoverConsole() {
  const { activeTenantId: tenantId } = useAuth();
  const [planId, setPlanId] = useState('');
  const [revision, setRevision] = useState('');
  const [stepId, setStepId] = useState('');
  const [profileId, setProfileId] = useState('');
  const [result, setResult] = useState<CoreRoutingAuthorityShadowResult>();
  const [states, setStates] = useState<CoreFlowRoutingMigrationState[]>([]);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const loadStates = useCallback(async () => {
    if (!tenantId) return;
    try { setStates(await coreAdminApi.getFlowRoutingMigrationStates(tenantId)); }
    catch (cause) { setError(errorMessage(cause, 'Unable to load Flow migration states.')); }
  }, [tenantId]);
  useEffect(() => { void loadStates(); }, [loadStates]);

  async function evaluate() {
    if (!tenantId || !planId.trim() || !stepId.trim() || !profileId.trim()) { setError('Plan ID, Step ID and ACTIVE Routing Profile are required.'); return; }
    setBusy(true); setError(''); setNotice(''); setResult(undefined);
    try {
      const response = await coreAdminApi.evaluateRoutingAuthorityShadow(planId.trim(), {
        planRevision: revision.trim() ? Number(revision) : undefined, stepId: stepId.trim(), routingProfileId: profileId.trim(),
      }, tenantId);
      setResult(response);
      setNotice('Shadow evaluation recorded. Legacy routing remains authoritative; no Assignment was dispatched.');
    } catch (cause) { setError(errorMessage(cause, 'Shadow routing evaluation failed.')); }
    finally { setBusy(false); }
  }

  async function changeState(flowId: string, state: 'LEGACY_AUTHORITATIVE' | 'SHADOW') {
    if (!tenantId || !reason.trim()) { setError('A change reason is required for Flow migration state changes.'); return; }
    setBusy(true); setError(''); setNotice('');
    try { await coreAdminApi.setFlowRoutingMigrationState(flowId, state, tenantId, reason.trim()); setNotice(`${flowId} is now ${state}. NEW_AUTHORITATIVE is intentionally unavailable in A0-R6.`); setReason(''); await loadStates(); }
    catch (cause) { setError(errorMessage(cause, 'Flow migration state could not be changed.')); }
    finally { setBusy(false); }
  }

  return <div className="space-y-5">
    <section className="rounded-3xl border border-amber-200 bg-amber-50 p-5">
      <div className="text-xs font-black uppercase tracking-wide text-amber-700">A0-R6 · Shadow authority cutover</div>
      <h2 className="mt-1 text-xl font-black text-slate-950">Prove RoutingDecision and ExecutionAssignment before authority moves</h2>
      <p className="mt-2 max-w-4xl text-sm leading-6 text-slate-700">Only an A0-R5 ADMITTED step with an ACTIVE BindingAuthorizationEnvelope can enter this path. Routing selects Binding/Pool; the shadow Assignment resolves a runtime target for comparison only. It cannot create a lease, fencing token, DispatchIntent, canonical task assignment, or network send.</p>
      <div className="mt-3 rounded-2xl border border-amber-200 bg-white p-3 text-xs font-black text-slate-700">Legacy Routing = AUTHORITATIVE · New Routing = SHADOW · Side effects = DENIED</div>
    </section>

    {(error || notice) && <div className={`rounded-2xl border p-4 text-sm font-bold ${error ? 'border-red-200 bg-red-50 text-red-800' : 'border-emerald-200 bg-emerald-50 text-emerald-800'}`}>{error || notice}</div>}

    <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
      <h3 className="font-black text-slate-950">Evaluate an admitted Plan Step</h3>
      <div className="mt-4 grid gap-3 md:grid-cols-4">
        <label className="text-xs font-bold text-slate-600">Plan ID<input className="mt-1 w-full rounded-xl border px-3 py-2 text-sm" value={planId} onChange={(e) => setPlanId(e.target.value)} /></label>
        <label className="text-xs font-bold text-slate-600">Revision (optional)<input type="number" className="mt-1 w-full rounded-xl border px-3 py-2 text-sm" value={revision} onChange={(e) => setRevision(e.target.value)} /></label>
        <label className="text-xs font-bold text-slate-600">Step ID<input className="mt-1 w-full rounded-xl border px-3 py-2 text-sm" value={stepId} onChange={(e) => setStepId(e.target.value)} /></label>
        <label className="text-xs font-bold text-slate-600">ACTIVE Routing Profile<input className="mt-1 w-full rounded-xl border px-3 py-2 text-sm" value={profileId} onChange={(e) => setProfileId(e.target.value)} /></label>
      </div>
      <button disabled={busy} onClick={() => void evaluate()} className="mt-4 rounded-xl bg-slate-950 px-4 py-2 text-sm font-black text-white disabled:opacity-50">Run shadow evaluation</button>
    </section>

    {result && <div className="grid gap-4 lg:grid-cols-2">
      <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm"><h3 className="font-black">EligibilityDecision</h3><div className="mt-3 grid grid-cols-2 gap-2 text-sm"><b>Result</b><span>{value(result.eligibilityDecision.result)}</span><b>Candidates</b><span>{result.eligibilityDecision.candidateCount}</span><b>Eligible</b><span>{result.eligibilityDecision.eligibleCount}</span><b>Excluded</b><span>{result.eligibilityDecision.excludedCount}</span><b>Envelope</b><span className="break-all">{result.eligibilityDecision.envelopeId}</span></div><div className="mt-3 text-xs text-slate-500">Exclusion summary</div><pre className="mt-1 overflow-auto rounded-xl bg-slate-950 p-3 text-xs text-white">{JSON.stringify(result.eligibilityDecision.exclusionSummary ?? {}, null, 2)}</pre></section>
      <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm"><h3 className="font-black">RoutingDecision</h3><div className="mt-3 grid grid-cols-2 gap-2 text-sm"><b>Authority</b><span>{result.routingDecision.authorityMode}</span><b>Binding</b><span>{value(result.routingDecision.selectedBindingId)}</span><b>Provider</b><span>{value(result.routingDecision.selectedProviderId)}</span><b>Pool</b><span>{value(result.routingDecision.selectedAgentPoolId)}</span><b>Feature Snapshot</b><span className="break-all">{result.routingDecision.routingFeatureSnapshotRef}</span></div></section>
      <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm"><h3 className="font-black">ExecutionAssignment Shadow</h3><div className="mt-3 grid grid-cols-2 gap-2 text-sm"><b>Authority</b><span>{value(result.executionAssignment?.authorityMode)}</span><b>Side effect allowed</b><span>{String(result.executionAssignment?.sideEffectAllowed ?? false)}</span><b>Agent</b><span>{value(result.executionAssignment?.selectedAgentId)}</span><b>Session</b><span>{value(result.executionAssignment?.selectedSessionId)}</span><b>A2A Interface</b><span>{value(result.executionAssignment?.selectedPeerInterfaceId)}</span><b>MCP Server</b><span>{value(result.executionAssignment?.selectedMcpServerId)}</span></div></section>
      <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm"><h3 className="font-black">Legacy equivalence</h3><div className="mt-3 grid grid-cols-2 gap-2 text-sm"><b>Legacy Assignment</b><span>{value(result.legacyAssignmentId)}</span><b>Legacy Agent</b><span>{value(result.legacyAgentId)}</span><b>Legacy Pool</b><span>{value(result.legacyPoolId)}</span><b>Executor equivalent</b><span className={result.executorEquivalent ? 'font-black text-emerald-700' : 'font-black text-amber-700'}>{String(result.executorEquivalent)}</span></div></section>
    </div>}

    <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
      <h3 className="font-black text-slate-950">Per-Flow migration state</h3>
      <p className="mt-1 text-xs text-slate-500">R6 intentionally supports only LEGACY_AUTHORITATIVE and SHADOW. NEW_AUTHORITATIVE belongs to a later controlled cutover gate.</p>
      <label className="mt-3 block text-xs font-bold text-slate-600">Change reason<input className="mt-1 w-full rounded-xl border px-3 py-2 text-sm" value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Required for any state change" /></label>
      <div className="mt-4 space-y-2">{states.length === 0 ? <div className="text-sm text-slate-500">No explicit states yet; unmatched rows use the implicit LEGACY_AUTHORITATIVE default.</div> : states.map((row) => <div key={row.flowId} className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border p-3"><div><div className="font-black">{row.flowId}</div><div className="text-xs text-slate-500">{row.migrationState} · v{row.version}</div></div><div className="flex gap-2"><button disabled={busy} onClick={() => void changeState(row.flowId, 'LEGACY_AUTHORITATIVE')} className="rounded-xl border px-3 py-2 text-xs font-black">Legacy</button><button disabled={busy} onClick={() => void changeState(row.flowId, 'SHADOW')} className="rounded-xl bg-amber-600 px-3 py-2 text-xs font-black text-white">Shadow</button></div></div>)}</div>
    </section>
  </div>;
}
