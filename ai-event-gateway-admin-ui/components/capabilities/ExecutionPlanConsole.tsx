'use client';

import { useEffect, useMemo, useState } from 'react';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import type { CoreBindingAuthorizationEnvelope, CoreExecutionPlan, CoreExecutionPlanDecision, CoreExecutionPlanPolicy, CoreExecutionPlanRequest, CoreExecutionPlanStep, CorePlanAdmissionDecision, CorePlanAdmissionPolicy } from '@/lib/types/core';


const EMPTY_ADMISSION_POLICY: CorePlanAdmissionPolicy = {
  policyId: 'default-plan-admission-policy', displayName: 'Default Plan Admission Policy',
  maxCandidateBindingsPerStep: 200, envelopeTtlSeconds: 900,
  allowedBindingClasses: ['LOCAL_DETERMINISTIC', 'LOCAL_AGENT'], allowedDataClasses: ['PUBLIC', 'INTERNAL'],
  maxSensitivityLevel: 'INTERNAL', externalEgressAllowed: false, allowedRegions: [], maxSideEffect: 'READ',
  securityCeilingRef: 'security-ceiling/default', dataPolicyRef: 'data-policy/default', egressPolicyRef: 'egress-policy/default',
  residencyConstraintRef: 'residency/default', status: 'DRAFT',
};

const EMPTY_POLICY: CoreExecutionPlanPolicy = {
  policyId: 'default-execution-plan-policy',
  displayName: 'Default Execution Plan Policy',
  maxSteps: 20,
  maxPlanDepth: 8,
  maxConcurrentBranches: 8,
  maxCapabilityInvocations: 20,
  maxTokenBudget: 100000,
  maxEstimatedCost: 100,
  maxExecutionTimeSeconds: 3600,
  requireHumanReviewOnPlanChange: false,
  status: 'DRAFT',
};

function parseRequirements(text: string) {
  return text.split('\n').map((line) => line.trim()).filter(Boolean).map((line) => {
    const [capabilityCode, operation = 'READ'] = line.split(/\s+/);
    return { capabilityCode, operation, inputContext: {}, resourceConstraints: {}, qualityPreference: 'BALANCED' };
  });
}

function parseSideEffect(value: string): CoreExecutionPlanStep['sideEffect'] {
  const normalized = value.trim().toUpperCase();
  if (!normalized) return undefined;
  if (normalized === 'NONE' || normalized === 'READ' || normalized === 'WRITE') return normalized;
  throw new Error(`Invalid sideEffect "${value}". Expected NONE, READ, or WRITE.`);
}

function parseWriteSemantics(value: string): CoreExecutionPlanStep['writeSemantics'] {
  const normalized = value.trim().toUpperCase();
  if (!normalized) return undefined;
  if (normalized === 'IDEMPOTENT' || normalized === 'COMPENSATABLE' || normalized === 'NON_COMPENSATABLE') return normalized;
  throw new Error(`Invalid writeSemantics "${value}". Expected IDEMPOTENT, COMPENSATABLE, or NON_COMPENSATABLE.`);
}

function parseSteps(text: string): CoreExecutionPlanStep[] {
  // Format: stepId | capability.code OPERATION | dep1,dep2 | purpose | SIDE_EFFECT | WRITE_SEMANTICS | compensationBindingId
  return text.split('\n').map((line) => line.trim()).filter(Boolean).map((line, index): CoreExecutionPlanStep => {
    const [stepIdRaw, capabilityRaw, depsRaw = '', purposeRaw = '', sideEffectRaw = '', writeSemanticsRaw = '', compensationBindingIdRaw = ''] = line.split('|').map((part) => part.trim());
    const [capabilityCode, operation = 'READ'] = (capabilityRaw || '').split(/\s+/);
    return {
      stepId: stepIdRaw || `step-${index + 1}`,
      requiredCapability: { capabilityCode, operation, inputContext: {}, resourceConstraints: {}, qualityPreference: 'BALANCED' },
      dependsOn: depsRaw ? depsRaw.split(',').map((value) => value.trim()).filter(Boolean) : [],
      purpose: purposeRaw || 'Planner-proposed semantic step.',
      required: true,
      sequenceHint: index + 1,
      sideEffect: parseSideEffect(sideEffectRaw),
      writeSemantics: parseWriteSemantics(writeSemanticsRaw),
      compensationBindingId: compensationBindingIdRaw || undefined,
      maxBindingFallback: 0,
    };
  });
}

export function ExecutionPlanConsole() {
  const [policies, setPolicies] = useState<CoreExecutionPlanPolicy[]>([]);
  const [policy, setPolicy] = useState<CoreExecutionPlanPolicy>(EMPTY_POLICY);
  const [requests, setRequests] = useState<CoreExecutionPlanRequest[]>([]);
  const [plans, setPlans] = useState<CoreExecutionPlan[]>([]);
  const [selectedRequestId, setSelectedRequestId] = useState('');
  const [selectedPlanId, setSelectedPlanId] = useState('');
  const [classificationCode, setClassificationCode] = useState('COMPLEX_OPERATIONAL_PROBLEM');
  const [initialRequirementsText, setInitialRequirementsText] = useState('');
  const [stepsText, setStepsText] = useState('');
  const [decision, setDecision] = useState<CoreExecutionPlanDecision>();
  const [admissionPolicies, setAdmissionPolicies] = useState<CorePlanAdmissionPolicy[]>([]);
  const [admissionPolicy, setAdmissionPolicy] = useState<CorePlanAdmissionPolicy>(EMPTY_ADMISSION_POLICY);
  const [admissionDecision, setAdmissionDecision] = useState<CorePlanAdmissionDecision>();
  const [envelopes, setEnvelopes] = useState<CoreBindingAuthorizationEnvelope[]>([]);
  const [status, setStatus] = useState('');

  const activePolicy = useMemo(() => policies.find((item) => item.status === 'ACTIVE'), [policies]);

  async function refresh() {
    try {
      const [p, r, pl, ap] = await Promise.all([
        coreAdminApi.getExecutionPlanPolicies(undefined, '', 100),
        coreAdminApi.getExecutionPlanRequests(undefined, '', 100),
        coreAdminApi.getExecutionPlans(undefined, '', undefined, 100),
        coreAdminApi.getPlanAdmissionPolicies(undefined, '', 100),
      ]);
      setPolicies(p); setRequests(r); setPlans(pl); setAdmissionPolicies(ap);
      const activeAdmission = ap.find((item) => item.status === 'ACTIVE'); if (activeAdmission) setAdmissionPolicy(activeAdmission);
      if (!selectedRequestId) setSelectedRequestId(r.find((item) => item.status === 'PLANNING_REQUIRED')?.requestId ?? '');
      if (!selectedPlanId) setSelectedPlanId(pl.find((item) => item.status === 'SEMANTICALLY_VALIDATED')?.planId ?? '');
      setStatus('');
    } catch (error) { setStatus(error instanceof Error ? error.message : String(error)); }
  }

  useEffect(() => { void refresh(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  async function savePolicy() {
    setStatus('Saving Execution Plan Policy…');
    try {
      const saved = await coreAdminApi.upsertExecutionPlanPolicy(policy.policyId, policy, '', policies.some((item) => item.policyId === policy.policyId) ? 'Phase 7 policy update from Execution Plan console' : undefined);
      setPolicy(saved); await refresh(); setStatus('Execution Plan Policy saved. Structural limits do not authorize Providers or execution.');
    } catch (error) { setStatus(error instanceof Error ? error.message : String(error)); }
  }

  async function createPlanningRequest() {
    setStatus('Creating planning request without invoking a Planner Agent…');
    try {
      const result = await coreAdminApi.resolveExecutionPlanPreview({
        classificationCode: classificationCode || undefined,
        initialRequirements: parseRequirements(initialRequirementsText),
        contextRefs: ['admin-preview://phase7'],
      });
      setDecision(result); setSelectedRequestId(result.requestId); await refresh(); setStatus(`Planning decision: ${result.result}`);
    } catch (error) { setStatus(error instanceof Error ? error.message : String(error)); }
  }

  async function validateProposal() {
    if (!selectedRequestId) { setStatus('Select a PLANNING_REQUIRED request first.'); return; }
    setStatus('Validating Planner proposal as WHAT + dependency graph only…');
    try {
      const result = await coreAdminApi.submitExecutionPlanProposal(selectedRequestId, {
        proposerType: 'PLANNER_AGENT',
        proposerRef: 'preview-planner-agent',
        steps: parseSteps(stepsText),
        rationale: 'Planner proposes Canonical Capability steps and dependencies only. OpenDispatch validates before any WHO CAN/WHO MAY/WHO SHOULD/HOW stage.',
      });
      setDecision(result); if (result.planId) setSelectedPlanId(result.planId); await refresh(); setStatus(`Plan decision: ${result.result}`);
    } catch (error) { setStatus(error instanceof Error ? error.message : String(error)); }
  }

  async function amendPlan() {
    if (!selectedPlanId) { setStatus('Select a semantically validated plan first.'); return; }
    setStatus('Validating plan amendment…');
    try {
      const result = await coreAdminApi.amendExecutionPlan(selectedPlanId, {
        proposerType: 'PLANNER_AGENT',
        proposerRef: 'preview-planner-agent',
        steps: parseSteps(stepsText),
        rationale: 'Proposed plan revision.',
        changeReason: 'Phase 7 preview amendment',
      });
      setDecision(result); await refresh(); setStatus(`Plan amendment decision: ${result.result}`);
    } catch (error) { setStatus(error instanceof Error ? error.message : String(error)); }
  }


  async function saveAdmissionPolicy() {
    setStatus('Saving Plan Admission security ceiling…');
    try {
      const exists = admissionPolicies.some((item) => item.policyId === admissionPolicy.policyId);
      const saved = await coreAdminApi.upsertPlanAdmissionPolicy(admissionPolicy.policyId, admissionPolicy, '', exists ? 'A0-R5 Plan Admission policy update' : undefined);
      setAdmissionPolicy(saved); await refresh(); setStatus('Plan Admission Policy saved. It authorizes a candidate boundary only; it does not route or assign.');
    } catch (error) { setStatus(error instanceof Error ? error.message : String(error)); }
  }

  async function admitPlan() {
    const selected = plans.find((item) => item.planId === selectedPlanId);
    if (!selected) { setStatus('Select a semantically validated plan first.'); return; }
    setStatus('Evaluating Plan Admission — WHO MAY enter routing…');
    try {
      const result = await coreAdminApi.admitExecutionPlan(selected.planId, selected.currentRevision);
      setAdmissionDecision(result.decision); setEnvelopes(result.envelopes);
      setStatus(`Plan Admission: ${result.decision.result}. ${result.envelopes.length} step envelope(s) issued.`);
    } catch (error) { setStatus(error instanceof Error ? error.message : String(error)); }
  }

  async function loadAdmissionEvidence() {
    const selected = plans.find((item) => item.planId === selectedPlanId);
    if (!selected) return;
    try {
      const [ds, es] = await Promise.all([coreAdminApi.getPlanAdmissionDecisions(selected.planId), coreAdminApi.getBindingAuthorizationEnvelopes(selected.planId, selected.currentRevision)]);
      setAdmissionDecision(ds[0]); setEnvelopes(es);
    } catch (error) { setStatus(error instanceof Error ? error.message : String(error)); }
  }

  return <div className="space-y-5">
    <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="text-xs font-black uppercase tracking-wide text-indigo-700">Phase 7 + MRS A0-R5 · Plan + Admission</div>
      <h2 className="mt-1 text-xl font-black text-slate-950">Plan WHAT first; then authorize WHO MAY enter routing</h2>
      <p className="mt-2 text-sm leading-6 text-slate-600">A Planner Agent may propose Canonical Capability requirements and a dependency DAG only. It cannot choose a Provider, Agent, Agent Pool, target Domain, A2A, MCP, Netty, endpoint or credential. <b>PLAN_VALIDATED</b> means semantic/graph validation only; every Step must later traverse WHO CAN → WHO MAY → WHO SHOULD → HOW.</p>
      <div className="mt-4 rounded-2xl bg-slate-50 p-4 text-sm text-slate-700"><b>Guardrails:</b> cycle detection, plan depth, concurrent branches, step/invocation ceilings and immutable revisions are enforced by OpenDispatch. Planner output never becomes executable authority by itself.</div>
    </section>

    <section className="grid gap-4 lg:grid-cols-2">
      <div className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
        <h3 className="font-black text-slate-950">Plan Policy</h3>
        <p className="mt-1 text-sm text-slate-600">Tenant structural/budget ceilings. These are not Provider routing weights and do not authorize execution.</p>
        <div className="mt-4 grid gap-3 md:grid-cols-2">
          <label className="text-sm">Policy ID<input className="mt-1 w-full rounded-xl border p-2" value={policy.policyId} onChange={(e)=>setPolicy({...policy,policyId:e.target.value})}/></label>
          <label className="text-sm">Status<select className="mt-1 w-full rounded-xl border p-2" value={policy.status} onChange={(e)=>setPolicy({...policy,status:e.target.value})}><option>DRAFT</option><option>ACTIVE</option><option>DISABLED</option><option>RETIRED</option></select></label>
          <label className="text-sm">Max steps<input type="number" className="mt-1 w-full rounded-xl border p-2" value={policy.maxSteps} onChange={(e)=>setPolicy({...policy,maxSteps:Number(e.target.value)})}/></label>
          <label className="text-sm">Max plan depth<input type="number" className="mt-1 w-full rounded-xl border p-2" value={policy.maxPlanDepth} onChange={(e)=>setPolicy({...policy,maxPlanDepth:Number(e.target.value)})}/></label>
          <label className="text-sm">Max concurrent branches<input type="number" className="mt-1 w-full rounded-xl border p-2" value={policy.maxConcurrentBranches} onChange={(e)=>setPolicy({...policy,maxConcurrentBranches:Number(e.target.value)})}/></label>
          <label className="text-sm">Max Capability invocations<input type="number" className="mt-1 w-full rounded-xl border p-2" value={policy.maxCapabilityInvocations} onChange={(e)=>setPolicy({...policy,maxCapabilityInvocations:Number(e.target.value)})}/></label>
        </div>
        <label className="mt-3 flex gap-2 text-sm"><input type="checkbox" checked={policy.requireHumanReviewOnPlanChange} onChange={(e)=>setPolicy({...policy,requireHumanReviewOnPlanChange:e.target.checked})}/> Require Human review before Planner-proposed plan amendments are applied</label>
        <button className="mt-4 rounded-xl bg-slate-950 px-4 py-2 text-sm font-black text-white" onClick={()=>void savePolicy()}>Save policy</button>
        <p className="mt-3 text-xs text-slate-500">Current ACTIVE policy: {activePolicy ? `${activePolicy.policyId} v${activePolicy.version ?? 1}` : 'none — fail closed'}</p>
      </div>

      <div className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
        <h3 className="font-black text-slate-950">Create planning request</h3>
        <label className="mt-3 block text-sm">Classification taxonomy<input className="mt-1 w-full rounded-xl border p-2" value={classificationCode} onChange={(e)=>setClassificationCode(e.target.value)} /></label>
        <label className="mt-3 block text-sm">Initial Canonical WHAT — one per line: <code>capability.code OPERATION</code><textarea className="mt-1 min-h-28 w-full rounded-xl border p-2 font-mono text-xs" value={initialRequirementsText} onChange={(e)=>setInitialRequirementsText(e.target.value)} placeholder={'fulfillment.root-cause.analyze ANALYZE\ninventory.availability.read READ'}/></label>
        <button className="mt-4 rounded-xl bg-indigo-700 px-4 py-2 text-sm font-black text-white" onClick={()=>void createPlanningRequest()}>Create planning request</button>
        <p className="mt-3 text-xs text-slate-500">This Admin preview does not invoke a live Planner Agent.</p>
      </div>
    </section>

    <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
      <h3 className="font-black text-slate-950">Planner Proposal validation</h3>
      <div className="mt-4 grid gap-3 md:grid-cols-2">
        <label className="text-sm">PLANNING_REQUIRED request<select className="mt-1 w-full rounded-xl border p-2" value={selectedRequestId} onChange={(e)=>setSelectedRequestId(e.target.value)}><option value="">Select…</option>{requests.filter((r)=>r.status==='PLANNING_REQUIRED').map((r)=><option key={r.requestId} value={r.requestId}>{r.requestId}</option>)}</select></label>
        <label className="text-sm">Validated plan for amendment<select className="mt-1 w-full rounded-xl border p-2" value={selectedPlanId} onChange={(e)=>setSelectedPlanId(e.target.value)}><option value="">Select…</option>{plans.map((p)=><option key={p.planId} value={p.planId}>{p.planId} · r{p.currentRevision}</option>)}</select></label>
      </div>
      <label className="mt-3 block text-sm">Steps — one per line: <code>stepId | capability OPERATION | dep1,dep2 | purpose | SIDE_EFFECT | WRITE_SEMANTICS | compensationBindingId</code><textarea className="mt-1 min-h-40 w-full rounded-xl border p-2 font-mono text-xs" value={stepsText} onChange={(e)=>setStepsText(e.target.value)} placeholder={'order | sales.order.fulfillment-status.read READ | | Read order fulfillment state\nproduction | production.execution-status.read READ | | Read production state\ninventory | inventory.availability.read READ | | Read inventory\nsynthesis | fulfillment.root-cause.analyze ANALYZE | order,production,inventory | Synthesize evidence'}/></label>
      <div className="mt-4 flex flex-wrap gap-2"><button className="rounded-xl bg-indigo-700 px-4 py-2 text-sm font-black text-white" onClick={()=>void validateProposal()}>Validate initial plan</button><button className="rounded-xl border border-slate-300 px-4 py-2 text-sm font-black text-slate-800" onClick={()=>void amendPlan()}>Validate amendment</button></div>
      <p className="mt-3 text-xs text-slate-500">Dependency cycles, unknown Capability/operation, excessive depth/branches/steps and policy limits fail closed. No Provider, authorization, ranking or execution adapter is selected.</p>
    </section>

    <section className="rounded-3xl border border-amber-200 bg-white p-5 shadow-sm">
      <div className="text-xs font-black uppercase tracking-wide text-amber-700">A0-R5 · Plan Admission Authority</div>
      <h3 className="mt-1 font-black text-slate-950">Authorize the maximum Binding boundary — do not select an executor</h3>
      <p className="mt-2 text-sm leading-6 text-slate-600">Formal Admission evaluates current IAM/delegation, Capability/version, data class, sensitivity, side effect, egress, residency, credential and Provider trust. An <b>ADMITTED</b> decision issues one step-scoped BindingAuthorizationEnvelope. The Router may only consider bindings inside that envelope.</p>
      <div className="mt-4 grid gap-3 lg:grid-cols-3">
        <label className="text-sm">Policy status<select className="mt-1 w-full rounded-xl border p-2" value={admissionPolicy.status} onChange={(e)=>setAdmissionPolicy({...admissionPolicy,status:e.target.value})}><option>DRAFT</option><option>ACTIVE</option><option>DISABLED</option><option>RETIRED</option></select></label>
        <label className="text-sm">Max sensitivity<select className="mt-1 w-full rounded-xl border p-2" value={admissionPolicy.maxSensitivityLevel} onChange={(e)=>setAdmissionPolicy({...admissionPolicy,maxSensitivityLevel:e.target.value})}>{['PUBLIC','INTERNAL','CONFIDENTIAL','RESTRICTED','CRITICAL'].map(v=><option key={v}>{v}</option>)}</select></label>
        <label className="text-sm">Max side effect<select className="mt-1 w-full rounded-xl border p-2" value={admissionPolicy.maxSideEffect} onChange={(e)=>setAdmissionPolicy({...admissionPolicy,maxSideEffect:e.target.value})}><option>NONE</option><option>READ</option><option>WRITE</option></select></label>
        <label className="text-sm lg:col-span-2">Allowed Binding Classes<div className="mt-1 flex flex-wrap gap-3 rounded-xl border p-2">{['LOCAL_DETERMINISTIC','LOCAL_AGENT','EXTERNAL_TOOL','EXTERNAL_AGENT'].map((v)=><label key={v} className="flex items-center gap-1 text-xs"><input type="checkbox" checked={admissionPolicy.allowedBindingClasses.includes(v)} onChange={(e)=>setAdmissionPolicy({...admissionPolicy,allowedBindingClasses:e.target.checked?[...admissionPolicy.allowedBindingClasses,v]:admissionPolicy.allowedBindingClasses.filter(x=>x!==v)})}/>{v}</label>)}</div></label>
        <label className="flex items-end gap-2 text-sm"><input type="checkbox" checked={admissionPolicy.externalEgressAllowed} onChange={(e)=>setAdmissionPolicy({...admissionPolicy,externalEgressAllowed:e.target.checked})}/> Allow external egress</label>
      </div>
      <div className="mt-4 flex flex-wrap gap-2"><button className="rounded-xl border border-slate-300 px-4 py-2 text-sm font-black" onClick={()=>void saveAdmissionPolicy()}>Save admission policy</button><button className="rounded-xl bg-amber-600 px-4 py-2 text-sm font-black text-white" onClick={()=>void admitPlan()}>Run formal Plan Admission</button><button className="rounded-xl border border-slate-300 px-4 py-2 text-sm font-black" onClick={()=>void loadAdmissionEvidence()}>Refresh evidence</button></div>
      {admissionDecision ? <div className="mt-4 rounded-2xl bg-slate-50 p-4"><div className="grid gap-3 md:grid-cols-5 text-sm"><div><span className="text-xs text-slate-500">Result</span><div className="font-black">{admissionDecision.result}</div></div><div><span className="text-xs text-slate-500">Steps</span><div className="font-black">{admissionDecision.stepCount}</div></div><div><span className="text-xs text-slate-500">Admitted</span><div className="font-black">{admissionDecision.admittedStepCount}</div></div><div><span className="text-xs text-slate-500">Waiting</span><div className="font-black">{admissionDecision.waitingApprovalStepCount}</div></div><div><span className="text-xs text-slate-500">Bindings</span><div className="font-black">{admissionDecision.admittedBindingCount}/{admissionDecision.candidateBindingCount}</div></div></div><p className="mt-2 text-xs text-slate-500">{admissionDecision.reasonCodes.join(' · ')}</p></div> : null}
      {envelopes.length ? <div className="mt-4 overflow-x-auto"><table className="min-w-full text-left text-xs"><thead><tr className="border-b"><th className="p-2">Step</th><th className="p-2">Capability</th><th className="p-2">Named bindings</th><th className="p-2">Classes</th><th className="p-2">Side effect</th><th className="p-2">Status / expiry</th></tr></thead><tbody>{envelopes.map((e)=><tr key={e.envelopeId} className="border-b align-top"><td className="p-2 font-bold">{e.stepId}</td><td className="p-2">{e.capabilityCode}@{e.capabilityVersion}</td><td className="p-2">{e.admittedBindingIds.join(', ') || 'none'}</td><td className="p-2">{e.admittedBindingClasses.join(', ') || 'named only'}</td><td className="p-2">{e.maxSideEffect}</td><td className="p-2">{e.status}<div className="text-slate-400">{e.validUntil}</div></td></tr>)}</tbody></table></div> : null}
      <p className="mt-3 text-xs font-medium text-amber-800">NON_COMPENSATABLE WRITE never receives blanket cross-trust Binding Class authority. Runtime Authorization is a child envelope and cannot widen these named bindings.</p>
    </section>

    {decision ? <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm"><h3 className="font-black text-slate-950">Plan evidence</h3><div className="mt-3 grid gap-3 md:grid-cols-4"><div><span className="text-xs text-slate-500">Result</span><div className="font-black">{decision.result}</div></div><div><span className="text-xs text-slate-500">Plan</span><div className="font-black">{decision.planId ?? 'none'}</div></div><div><span className="text-xs text-slate-500">Depth</span><div className="font-black">{decision.maxDepthObserved ?? '—'}</div></div><div><span className="text-xs text-slate-500">Max branches</span><div className="font-black">{decision.maxConcurrentBranchesObserved ?? '—'}</div></div></div><pre className="mt-4 overflow-auto rounded-2xl bg-slate-950 p-4 text-xs text-slate-100">{JSON.stringify(decision, null, 2)}</pre><p className="mt-3 text-xs text-slate-500">No Provider, Agent, Pool, A2A, MCP, Netty or Internal Service execution was performed.</p></section> : null}
    {status ? <div className="rounded-2xl border border-slate-200 bg-slate-50 p-3 text-sm text-slate-700">{status}</div> : null}
  </div>;
}
