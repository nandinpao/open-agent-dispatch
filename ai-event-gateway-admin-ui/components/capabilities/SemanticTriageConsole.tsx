'use client';

import { useEffect, useMemo, useState } from 'react';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import type { CoreTriageDecision, CoreTriagePolicy, CoreTriageRequest } from '@/lib/types/core';

const EMPTY_POLICY: CoreTriagePolicy = {
  policyId: 'default-semantic-triage',
  displayName: 'Default Semantic Triage Policy',
  minClassificationConfidence: 0.8,
  minCapabilityResolutionConfidence: 0.8,
  maxCapabilitySuggestions: 10,
  requireHumanReviewOnCapabilityGap: true,
  status: 'DRAFT',
};

export function SemanticTriageConsole() {
  const [policies, setPolicies] = useState<CoreTriagePolicy[]>([]);
  const [policy, setPolicy] = useState<CoreTriagePolicy>(EMPTY_POLICY);
  const [serviceCode, setServiceCode] = useState('');
  const [problem, setProblem] = useState('');
  const [decision, setDecision] = useState<CoreTriageDecision>();
  const [requests, setRequests] = useState<CoreTriageRequest[]>([]);
  const [selectedRequestId, setSelectedRequestId] = useState('');
  const [classificationCode, setClassificationCode] = useState('UNKNOWN_OPERATIONAL_PROBLEM');
  const [classificationConfidence, setClassificationConfidence] = useState('0.85');
  const [capabilityConfidence, setCapabilityConfidence] = useState('0.85');
  const [capabilitiesText, setCapabilitiesText] = useState('');
  const [status, setStatus] = useState('');

  const activePolicy = useMemo(() => policies.find((item) => item.status === 'ACTIVE'), [policies]);

  async function refresh() {
    try {
      const [p, r] = await Promise.all([
        coreAdminApi.getSemanticTriagePolicies(undefined, '', 100),
        coreAdminApi.getSemanticTriageRequests(undefined, '', 100),
      ]);
      setPolicies(p); setRequests(r);
      if (!selectedRequestId) setSelectedRequestId(r.find((item) => item.status === 'TRIAGE_REQUIRED')?.requestId ?? '');
      setStatus('');
    } catch (error) { setStatus(error instanceof Error ? error.message : String(error)); }
  }

  useEffect(() => { void refresh(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  async function savePolicy() {
    setStatus('Saving Triage Policy…');
    try {
      const saved = await coreAdminApi.upsertSemanticTriagePolicy(policy.policyId, policy, '', policies.some((p) => p.policyId === policy.policyId) ? 'Phase 6 policy update from Semantic Triage console' : undefined);
      setPolicy(saved); await refresh(); setStatus('Triage Policy saved. Thresholds govern semantic acceptance only; they do not rank providers.');
    } catch (error) { setStatus(error instanceof Error ? error.message : String(error)); }
  }

  async function resolve() {
    setStatus('Resolving known versus unknown WHAT…');
    try {
      const result = await coreAdminApi.resolveSemanticTriagePreview({ serviceCode: serviceCode || undefined, problemStatement: problem || undefined, dataClassification: 'INTERNAL' });
      setDecision(result); await refresh(); setSelectedRequestId(result.requestId); setStatus(result.result === 'KNOWN_FAST_PATH' ? 'Known Fast Path: no Triage Agent was invoked.' : 'Unknown problem: TRIAGE_REQUIRED.');
    } catch (error) { setStatus(error instanceof Error ? error.message : String(error)); }
  }

  async function submitProposal() {
    if (!selectedRequestId) { setStatus('Select a TRIAGE_REQUIRED request first.'); return; }
    const suggestions = capabilitiesText.split('\n').map((line) => line.trim()).filter(Boolean).map((line) => {
      const [capabilityCode, operation = 'READ'] = line.split(/\s+/);
      return { capabilityCode, operation, confidence: Number(capabilityConfidence), rationale: 'Semantic WHAT suggestion for OpenDispatch validation.' };
    });
    setStatus('Validating Triage proposal against Canonical Capability and Triage Policy…');
    try {
      const result = await coreAdminApi.submitSemanticTriageProposal(selectedRequestId, {
        proposerType: 'TRIAGE_AGENT',
        proposerRef: 'preview-triage-agent',
        classification: { classificationCode, confidence: Number(classificationConfidence), semanticTaxonomy: 'Operational', rationale: 'Preview semantic classification only.' },
        capabilityResolutionConfidence: Number(capabilityConfidence),
        capabilitySuggestions: suggestions,
        investigationSuggestions: ['Collect evidence required by accepted Canonical Capabilities.'],
        explanatoryTaxonomies: ['Taxonomy is descriptive only; it is not a routing destination.'],
        rationale: 'Agent proposes WHAT; OpenDispatch validates before any WHO CAN/WHO MAY/WHO SHOULD/HOW stage.',
      });
      setDecision(result); await refresh(); setStatus(`Triage decision: ${result.result}`);
    } catch (error) { setStatus(error instanceof Error ? error.message : String(error)); }
  }

  return <div className="space-y-5">
    <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="text-xs font-black uppercase tracking-wide text-violet-700">Phase 6 · UNKNOWN Problem / Semantic Triage</div>
      <h2 className="mt-1 text-xl font-black text-slate-950">Resolve WHAT when exact classification is unavailable</h2>
      <p className="mt-2 text-sm leading-6 text-slate-600">Known Service Code mappings stay on the deterministic Fast Path. Only unknown work enters semantic triage. A Triage Agent can propose classification and Canonical Capabilities only; it cannot choose a Provider, Agent, Pool, A2A, MCP, Netty, endpoint or credential.</p>
      <div className="mt-4 rounded-2xl bg-slate-50 p-4 text-sm text-slate-700"><b>Architecture chain:</b> Known → Service Code → WHAT. Unknown → Triage Proposal → OpenDispatch validation → WHAT. Only after WHAT is accepted may later phases run WHO CAN → WHO MAY → WHO SHOULD → HOW.</div>
    </section>

    <section className="grid gap-4 lg:grid-cols-2">
      <div className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
        <h3 className="font-black text-slate-950">Triage Policy</h3>
        <p className="mt-1 text-sm text-slate-600">Confidence thresholds are tenant configuration, not hard-coded routing numbers. No ACTIVE policy means semantic proposals fail closed.</p>
        <div className="mt-4 grid gap-3 md:grid-cols-2">
          <label className="text-sm">Policy ID<input className="mt-1 w-full rounded-xl border p-2" value={policy.policyId} onChange={(e)=>setPolicy({...policy,policyId:e.target.value})}/></label>
          <label className="text-sm">Status<select className="mt-1 w-full rounded-xl border p-2" value={policy.status} onChange={(e)=>setPolicy({...policy,status:e.target.value})}><option>DRAFT</option><option>ACTIVE</option><option>DISABLED</option><option>RETIRED</option></select></label>
          <label className="text-sm">Classification confidence<input type="number" min="0" max="1" step="0.01" className="mt-1 w-full rounded-xl border p-2" value={policy.minClassificationConfidence} onChange={(e)=>setPolicy({...policy,minClassificationConfidence:Number(e.target.value)})}/></label>
          <label className="text-sm">Capability confidence<input type="number" min="0" max="1" step="0.01" className="mt-1 w-full rounded-xl border p-2" value={policy.minCapabilityResolutionConfidence} onChange={(e)=>setPolicy({...policy,minCapabilityResolutionConfidence:Number(e.target.value)})}/></label>
        </div>
        <button className="mt-4 rounded-xl bg-slate-950 px-4 py-2 text-sm font-black text-white" onClick={()=>void savePolicy()}>Save policy</button>
        <p className="mt-3 text-xs text-slate-500">Current ACTIVE policy: {activePolicy ? `${activePolicy.policyId} v${activePolicy.version ?? 1}` : 'none — fail closed'}</p>
      </div>

      <div className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
        <h3 className="font-black text-slate-950">Known / Unknown preview</h3>
        <label className="mt-3 block text-sm">Known Service Code (optional)<input className="mt-1 w-full rounded-xl border p-2" value={serviceCode} onChange={(e)=>setServiceCode(e.target.value)} placeholder="EXACT_KNOWN_SERVICE_CODE"/></label>
        <label className="mt-3 block text-sm">Problem statement<textarea className="mt-1 min-h-24 w-full rounded-xl border p-2" value={problem} onChange={(e)=>setProblem(e.target.value)} placeholder="Describe an unknown business/operational problem without choosing a target system or Agent."/></label>
        <button className="mt-4 rounded-xl bg-violet-700 px-4 py-2 text-sm font-black text-white" onClick={()=>void resolve()}>Resolve WHAT</button>
      </div>
    </section>

    <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
      <h3 className="font-black text-slate-950">Triage Proposal validation</h3>
      <p className="mt-1 text-sm text-slate-600">This simulates the semantic output contract only. No LLM/provider invocation occurs in this Admin preview.</p>
      <div className="mt-4 grid gap-3 lg:grid-cols-3">
        <label className="text-sm">TRIAGE_REQUIRED request<select className="mt-1 w-full rounded-xl border p-2" value={selectedRequestId} onChange={(e)=>setSelectedRequestId(e.target.value)}><option value="">Select…</option>{requests.filter((r)=>r.status==='TRIAGE_REQUIRED').map((r)=><option key={r.requestId} value={r.requestId}>{r.requestId}</option>)}</select></label>
        <label className="text-sm">Classification code<input className="mt-1 w-full rounded-xl border p-2" value={classificationCode} onChange={(e)=>setClassificationCode(e.target.value)}/></label>
        <label className="text-sm">Classification confidence<input className="mt-1 w-full rounded-xl border p-2" value={classificationConfidence} onChange={(e)=>setClassificationConfidence(e.target.value)}/></label>
      </div>
      <label className="mt-3 block text-sm">Canonical Capability suggestions — one per line: <code>capability.code OPERATION</code><textarea className="mt-1 min-h-28 w-full rounded-xl border p-2 font-mono text-xs" value={capabilitiesText} onChange={(e)=>setCapabilitiesText(e.target.value)} placeholder={'inventory.availability.read READ\nfulfillment.root-cause.analyze ANALYZE'}/></label>
      <label className="mt-3 block text-sm">Capability resolution confidence<input className="mt-1 w-full rounded-xl border p-2" value={capabilityConfidence} onChange={(e)=>setCapabilityConfidence(e.target.value)}/></label>
      <button className="mt-4 rounded-xl bg-violet-700 px-4 py-2 text-sm font-black text-white" onClick={()=>void submitProposal()}>Validate proposal</button>
    </section>

    {decision ? <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
      <h3 className="font-black text-slate-950">Triage evidence</h3>
      <div className="mt-3 grid gap-3 md:grid-cols-3"><div><span className="text-xs text-slate-500">Result</span><div className="font-black">{decision.result}</div></div><div><span className="text-xs text-slate-500">Human review</span><div className="font-black">{decision.requiresHumanReview ? 'Required' : 'No'}</div></div><div><span className="text-xs text-slate-500">Accepted WHAT</span><div className="font-black">{decision.acceptedRequirements?.length ?? 0}</div></div></div>
      <pre className="mt-4 overflow-auto rounded-2xl bg-slate-950 p-4 text-xs text-slate-100">{JSON.stringify(decision, null, 2)}</pre>
      <p className="mt-3 text-xs text-slate-500">No Provider, authorization, ranking, A2A, MCP, Netty or Internal Service execution was performed.</p>
    </section> : null}
    {status ? <div className="rounded-2xl border border-slate-200 bg-slate-50 p-3 text-sm text-slate-700">{status}</div> : null}
  </div>;
}
