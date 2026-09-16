'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { useAuth } from '@/components/auth/AuthProvider';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import type {
  CoreCanonicalCapabilityDefinition,
  CoreCapabilityBinding,
  CoreDelegationAuthorizationDecision,
  CoreDelegationPolicy,
  CoreDelegationPolicyAuditEvent,
  CoreDelegationPolicyVersion,
} from '@/lib/types/core';

const PAGE_SIZE = 50;
const PRINCIPAL_TYPES = ['HUMAN', 'AGENT', 'SERVICE_ACCOUNT', 'SYSTEM'];
const ACCESS_MODES = ['READ', 'WRITE', 'EXECUTE'];
const PROVIDER_TYPES = ['MANAGED_AGENT', 'REMOTE_A2A_AGENT', 'MCP_TOOL', 'INTERNAL_SERVICE'];
const SENSITIVITY = ['PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED', 'CRITICAL'];

function message(cause: unknown, fallback: string) { return cause instanceof Error && cause.message ? cause.message : fallback; }
function csv(value: string): string[] { return [...new Set(value.split(',').map((item) => item.trim()).filter(Boolean))]; }
function join(values?: string[]) { return (values ?? []).join(', '); }
function num(value: string): number | undefined { if (!value.trim()) return undefined; const parsed = Number(value); return Number.isFinite(parsed) ? parsed : undefined; }
function emptyPolicy(): CoreDelegationPolicy {
  return {
    policyId: '', displayName: '', description: '', effect: 'ALLOW', status: 'DRAFT',
    requesterPrincipalTypes: ['AGENT'], requesterDepartmentIds: [], requesterGroupIds: [], requesterRoleCodes: [],
    capabilityCodes: [], operations: [], resourceConstraints: {}, allowedDataClasses: [], maxSensitivityLevel: 'INTERNAL',
    allowedAccessModes: ['READ'], requiredProviderTypes: [], requiredProviderCertifications: [], approvalMode: 'NONE',
    priority: 100,
  };
}

export function DelegationGovernanceConsole() {
  const { activeTenantId: tenantId } = useAuth();
  const [capabilities, setCapabilities] = useState<CoreCanonicalCapabilityDefinition[]>([]);
  const [capabilitySearch, setCapabilitySearch] = useState('');
  const [policies, setPolicies] = useState<CoreDelegationPolicy[]>([]);
  const [policySearch, setPolicySearch] = useState('');
  const [policyHasMore, setPolicyHasMore] = useState(false);
  const [draft, setDraft] = useState<CoreDelegationPolicy>(emptyPolicy());
  const [changeReason, setChangeReason] = useState('');
  const [departmentCsv, setDepartmentCsv] = useState('');
  const [groupCsv, setGroupCsv] = useState('');
  const [roleCsv, setRoleCsv] = useState('');
  const [dataClassCsv, setDataClassCsv] = useState('');
  const [providerCertCsv, setProviderCertCsv] = useState('');
  const [resourceJson, setResourceJson] = useState('{}');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [policyVersions, setPolicyVersions] = useState<CoreDelegationPolicyVersion[]>([]);
  const [policyAudit, setPolicyAudit] = useState<CoreDelegationPolicyAuditEvent[]>([]);

  const selectedCapability = draft.capabilityCodes?.[0] ?? '';
  const selectedCapabilityDefinition = capabilities.find((item) => item.capabilityCode === selectedCapability);
  const [bindings, setBindings] = useState<CoreCapabilityBinding[]>([]);
  const [previewBindingId, setPreviewBindingId] = useState('');
  const [previewPrincipalType, setPreviewPrincipalType] = useState('AGENT');
  const [previewDepartment, setPreviewDepartment] = useState('');
  const [previewGroupCsv, setPreviewGroupCsv] = useState('');
  const [previewRoleCsv, setPreviewRoleCsv] = useState('');
  const [previewAccessMode, setPreviewAccessMode] = useState('READ');
  const [previewDataClass, setPreviewDataClass] = useState('INTERNAL');
  const [previewSensitivity, setPreviewSensitivity] = useState('INTERNAL');
  const [previewApprovalCount, setPreviewApprovalCount] = useState('0');
  const [previewDecision, setPreviewDecision] = useState<CoreDelegationAuthorizationDecision>();

  const loadCapabilities = useCallback(async () => {
    if (!tenantId) return;
    try {
      const rows = await coreAdminApi.getCanonicalCapabilities('ACTIVE', capabilitySearch || undefined, undefined, tenantId, PAGE_SIZE);
      setCapabilities(rows);
    } catch (cause) { setError(message(cause, 'Unable to search Canonical Capabilities.')); }
  }, [tenantId, capabilitySearch]);

  const loadPolicies = useCallback(async (append = false) => {
    if (!tenantId) return;
    try {
      const after = append && policies.length ? policies[policies.length - 1].policyId : undefined;
      const rows = await coreAdminApi.getDelegationPolicies(undefined, undefined, undefined, policySearch || undefined, tenantId, after, PAGE_SIZE);
      setPolicies((current) => append ? [...current, ...rows.filter((row) => !current.some((item) => item.policyId === row.policyId))] : rows);
      setPolicyHasMore(rows.length === PAGE_SIZE);
    } catch (cause) { setError(message(cause, 'Unable to load Delegation Policies.')); }
  }, [tenantId, policySearch, policies]);

  const loadPolicyHistory = useCallback(async (policyId: string) => {
    if (!tenantId || !policyId) { setPolicyVersions([]); setPolicyAudit([]); return; }
    try {
      const [versions, audit] = await Promise.all([
        coreAdminApi.getDelegationPolicyVersions(policyId, tenantId),
        coreAdminApi.getDelegationPolicyAuditEvents(policyId, tenantId),
      ]);
      setPolicyVersions(versions); setPolicyAudit(audit);
    } catch (cause) { setError(message(cause, 'Unable to load Delegation Policy history.')); }
  }, [tenantId]);

  const loadBindings = useCallback(async () => {
    if (!tenantId || !selectedCapability) { setBindings([]); setPreviewBindingId(''); return; }
    try {
      const rows = await coreAdminApi.getCapabilityBindings(selectedCapability, undefined, undefined, tenantId, undefined, 100);
      setBindings(rows);
      if (!rows.some((item) => item.bindingId === previewBindingId)) setPreviewBindingId(rows[0]?.bindingId ?? '');
    } catch (cause) { setError(message(cause, 'Unable to load eligible capability providers for governance preview.')); }
  }, [tenantId, selectedCapability, previewBindingId]);

  useEffect(() => { const timer = window.setTimeout(() => { void loadCapabilities(); }, 250); return () => window.clearTimeout(timer); }, [loadCapabilities]);
  useEffect(() => { const timer = window.setTimeout(() => { void loadPolicies(false); }, 250); return () => window.clearTimeout(timer); }, [tenantId, policySearch]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { void loadBindings(); }, [tenantId, selectedCapability]); // eslint-disable-line react-hooks/exhaustive-deps

  const operations = useMemo(() => selectedCapabilityDefinition?.operations ?? [], [selectedCapabilityDefinition]);

  function edit(policy: CoreDelegationPolicy) {
    setDraft(policy);
    setDepartmentCsv(join(policy.requesterDepartmentIds)); setGroupCsv(join(policy.requesterGroupIds)); setRoleCsv(join(policy.requesterRoleCodes));
    setDataClassCsv(join(policy.allowedDataClasses)); setProviderCertCsv(join(policy.requiredProviderCertifications));
    setResourceJson(JSON.stringify(policy.resourceConstraints ?? {}, null, 2));
    setChangeReason(''); setPreviewDecision(undefined); setError(''); setNotice('');
    if (policy.createdAt) void loadPolicyHistory(policy.policyId); else { setPolicyVersions([]); setPolicyAudit([]); }
  }

  async function savePolicy() {
    if (!tenantId || !draft.policyId.trim() || !draft.displayName.trim() || !selectedCapability) return;
    let resourceConstraints: Record<string, unknown> = {};
    try { resourceConstraints = resourceJson.trim() ? JSON.parse(resourceJson) as Record<string, unknown> : {}; }
    catch { setError('Resource constraints must be a JSON object.'); return; }
    setSaving(true); setError(''); setNotice('');
    try {
      const body: CoreDelegationPolicy = {
        ...draft,
        policyId: draft.policyId.trim(), displayName: draft.displayName.trim(),
        requesterDepartmentIds: csv(departmentCsv), requesterGroupIds: csv(groupCsv), requesterRoleCodes: csv(roleCsv),
        allowedDataClasses: csv(dataClassCsv).map((item) => item.toUpperCase()), requiredProviderCertifications: csv(providerCertCsv).map((item) => item.toUpperCase()),
        resourceConstraints,
      };
      const saved = await coreAdminApi.upsertDelegationPolicy(body.policyId, body, tenantId, changeReason.trim() || undefined);
      edit(saved);
      setNotice(`Policy ${saved.policyId} v${saved.version ?? 1} saved. It authorizes WHO MAY only and does not select a Provider.`);
      await loadPolicies(false);
    } catch (cause) { setError(message(cause, 'Delegation Policy could not be saved.')); }
    finally { setSaving(false); }
  }

  async function evaluatePreview() {
    if (!tenantId || !selectedCapability || !previewBindingId || !draft.operations?.[0]) {
      setError('Choose a Capability, operation and capability provider before previewing authorization.'); return;
    }
    setSaving(true); setError(''); setNotice(''); setPreviewDecision(undefined);
    try {
      const decision = await coreAdminApi.evaluateDelegationAuthorizationPreview({
        requirement: {
          capabilityCode: selectedCapability,
          operation: draft.operations[0],
          resourceConstraints: resourceJson.trim() ? JSON.parse(resourceJson) as Record<string, unknown> : {},
          dataClassification: previewDataClass,
        },
        bindingId: previewBindingId,
        requesterPrincipalType: previewPrincipalType,
        requesterDepartmentId: previewDepartment || undefined,
        requesterGroupIds: csv(previewGroupCsv), requesterRoleCodes: csv(previewRoleCsv),
        accessMode: previewAccessMode, sensitivityLevel: previewSensitivity, approvalCount: num(previewApprovalCount) ?? 0,
      }, tenantId);
      setPreviewDecision(decision);
    } catch (cause) { setError(message(cause, 'WHO MAY preview could not be evaluated.')); }
    finally { setSaving(false); }
  }

  function toggle(list: string[] | undefined, value: string): string[] {
    const current = list ?? []; return current.includes(value) ? current.filter((item) => item !== value) : [...current, value];
  }

  if (!tenantId) return <div className="rounded-2xl border border-amber-200 bg-amber-50 p-5 text-sm font-semibold text-amber-950">Select an administration workspace before managing Delegation Governance.</div>;

  return <div className="space-y-5">
    <section className="rounded-3xl border border-emerald-200 bg-emerald-50 p-5">
      <div className="text-xs font-black uppercase tracking-wide text-emerald-700">Phase 3 architecture boundary</div>
      <h2 className="mt-1 text-lg font-black text-emerald-950">WHO MAY is authorization — never routing</h2>
      <p className="mt-2 max-w-5xl text-sm leading-6 text-emerald-900">Policies decide whether a requester may use a Canonical Capability with a candidate provider. The result is PASS, FAIL or WAITING_APPROVAL. There is no Target Domain, Target System, Agent Pool, Agent selection, routing score or execution protocol here.</p>
      <p className="mt-2 text-xs font-semibold text-emerald-800">A matching DENY always wins. Equal-priority, equal-specificity ALLOW policies fail closed as POLICY_AMBIGUOUS instead of picking one by name.</p>
    </section>

    {error ? <div className="rounded-2xl border border-red-200 bg-red-50 p-4 text-sm font-semibold text-red-900">{error}</div> : null}
    {notice ? <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-semibold text-emerald-900">{notice}</div> : null}

    <div className="grid gap-5 xl:grid-cols-[0.8fr_1.5fr]">
      <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="text-xs font-black uppercase tracking-wide text-slate-500">Policy directory</div>
        <div className="mt-3 flex gap-2"><input className="input" value={policySearch} onChange={(e) => setPolicySearch(e.target.value)} placeholder="Server search policies" /><button className="mini" onClick={() => { setDraft(emptyPolicy()); setDepartmentCsv(''); setGroupCsv(''); setRoleCsv(''); setDataClassCsv(''); setProviderCertCsv(''); setResourceJson('{}'); setChangeReason(''); setPolicyVersions([]); setPolicyAudit([]); }}>New</button></div>
        <div className="mt-3 space-y-2">{policies.map((policy) => <button key={policy.policyId} type="button" onClick={() => edit(policy)} className="w-full rounded-2xl border border-slate-200 bg-slate-50 p-3 text-left hover:border-emerald-200"><div className="flex items-center justify-between gap-2"><span className="font-black text-slate-950">{policy.displayName}</span><span className={`text-xs font-black ${policy.effect === 'DENY' ? 'text-red-700' : 'text-emerald-700'}`}>{policy.effect} · {policy.status}</span></div><div className="mt-1 font-mono text-xs text-emerald-700">{policy.policyId}</div><div className="mt-1 text-xs text-slate-500">{(policy.capabilityCodes ?? []).join(', ')} · priority {policy.priority ?? 100}</div></button>)}</div>
        {policyHasMore ? <button className="mini mt-3" onClick={() => { void loadPolicies(true); }}>Load more policies</button> : null}
      </section>

      <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex flex-wrap items-start justify-between gap-3"><div><div className="text-xs font-black uppercase tracking-wide text-slate-500">Delegation Policy</div><h3 className="mt-1 text-lg font-black text-slate-950">Who may request this capability?</h3></div><div className="text-xs font-semibold text-slate-500">Version {draft.version ?? 'new'}</div></div>

        <div className="mt-4 grid gap-3 md:grid-cols-2">
          <input className="input" disabled={Boolean(draft.createdAt)} placeholder="policy-id" value={draft.policyId} onChange={(e) => setDraft((v) => ({ ...v, policyId: e.target.value }))} />
          <input className="input" placeholder="Policy name" value={draft.displayName} onChange={(e) => setDraft((v) => ({ ...v, displayName: e.target.value }))} />
          <select className="input" value={draft.effect ?? 'ALLOW'} onChange={(e) => setDraft((v) => e.target.value === 'DENY' ? ({ ...v, effect: 'DENY', approvalMode: 'NONE', maxSensitivityLevel: 'CRITICAL', maxEstimatedCost: undefined, maxDelegationDepth: undefined, maxAgentCalls: undefined, maxExecutionTimeMs: undefined }) : ({ ...v, effect: 'ALLOW' }))}><option>ALLOW</option><option>DENY</option></select>
          <select className="input" value={draft.status ?? 'DRAFT'} onChange={(e) => setDraft((v) => ({ ...v, status: e.target.value }))}><option>DRAFT</option><option>ACTIVE</option><option>SUSPENDED</option><option>RETIRED</option></select>
          <textarea className="input md:col-span-2" placeholder="Why does this policy exist?" value={draft.description ?? ''} onChange={(e) => setDraft((v) => ({ ...v, description: e.target.value }))} />
        </div>

        <GovernanceSection title="1 · Who can request?" help="Requester identity is governance scope, not a source routing node.">
          <div className="grid gap-2 md:grid-cols-4">{PRINCIPAL_TYPES.map((value) => <label key={value} className="check"><input type="checkbox" checked={(draft.requesterPrincipalTypes ?? []).includes(value)} onChange={() => setDraft((v) => ({ ...v, requesterPrincipalTypes: toggle(v.requesterPrincipalTypes, value) }))} />{value}</label>)}</div>
          <div className="mt-3 grid gap-3 md:grid-cols-3"><input className="input" value={departmentCsv} onChange={(e) => setDepartmentCsv(e.target.value)} placeholder="Department IDs (optional)" /><input className="input" value={groupCsv} onChange={(e) => setGroupCsv(e.target.value)} placeholder="Group IDs (optional)" /><input className="input" value={roleCsv} onChange={(e) => setRoleCsv(e.target.value)} placeholder="Role codes (optional)" /></div>
        </GovernanceSection>

        <GovernanceSection title="2 · What may they request?" help="Choose Canonical Capability and operations. No target application topology is required.">
          <input className="input" value={capabilitySearch} onChange={(e) => setCapabilitySearch(e.target.value)} placeholder="Search Canonical Capabilities" />
          <select className="input mt-2" value={selectedCapability} onChange={(e) => setDraft((v) => ({ ...v, capabilityCodes: e.target.value ? [e.target.value] : [], operations: [] }))}><option value="">Select Capability</option>{capabilities.map((item) => <option key={item.capabilityCode} value={item.capabilityCode}>{item.displayName} · {item.capabilityCode}</option>)}</select>
          <div className="mt-3 flex flex-wrap gap-2">{operations.map((op) => <label key={op} className="check"><input type="checkbox" checked={(draft.operations ?? []).includes(op)} onChange={() => setDraft((v) => ({ ...v, operations: toggle(v.operations, op) }))} />{op}</label>)}</div>
        </GovernanceSection>

        <GovernanceSection title="3 · Data and resource scope" help="Resource constraints are authorization scope. Semantic Domain remains taxonomy and is not a destination.">
          <div className="grid gap-3 md:grid-cols-2"><input className="input" value={dataClassCsv} onChange={(e) => setDataClassCsv(e.target.value)} placeholder="Allowed data classes, e.g. INTERNAL, CONFIDENTIAL" /><select className="input" disabled={draft.effect === 'DENY'} value={draft.effect === 'DENY' ? 'CRITICAL' : draft.maxSensitivityLevel ?? 'INTERNAL'} onChange={(e) => setDraft((v) => ({ ...v, maxSensitivityLevel: e.target.value }))}>{SENSITIVITY.map((item) => <option key={item}>{item}</option>)}</select></div>
          <textarea className="input mt-3 min-h-24 font-mono" value={resourceJson} onChange={(e) => setResourceJson(e.target.value)} placeholder='Resource constraints JSON, e.g. {"plantId":"TW-01"}' />
          <div className="mt-3 flex flex-wrap gap-2">{ACCESS_MODES.map((mode) => <label key={mode} className="check"><input type="checkbox" checked={(draft.allowedAccessModes ?? []).includes(mode)} onChange={() => setDraft((v) => ({ ...v, allowedAccessModes: toggle(v.allowedAccessModes, mode) }))} />{mode}</label>)}</div>
        </GovernanceSection>

        <GovernanceSection title="4 · Provider trust requirements" help="These are authorization attributes only; they do not select a Provider or transport.">
          <div className="grid gap-2 md:grid-cols-4">{PROVIDER_TYPES.map((type) => <label key={type} className="check"><input type="checkbox" checked={(draft.requiredProviderTypes ?? []).includes(type)} onChange={() => setDraft((v) => ({ ...v, requiredProviderTypes: toggle(v.requiredProviderTypes, type) }))} />{type}</label>)}</div>
          <input className="input mt-3" value={providerCertCsv} onChange={(e) => setProviderCertCsv(e.target.value)} placeholder="Required certifications (optional)" />
        </GovernanceSection>

        <GovernanceSection title="5 · Safety limits" help={draft.effect === 'DENY' ? "DENY uses scope selectors and immediately FAILs. ALLOW ceilings and approval do not apply to DENY." : "Hard constraints are evaluated before any future WHO SHOULD ranking."}>
          <div className="grid gap-3 md:grid-cols-3"><select className="input" disabled={draft.effect === 'DENY'} value={draft.effect === 'DENY' ? 'NONE' : draft.approvalMode ?? 'NONE'} onChange={(e) => setDraft((v) => ({ ...v, approvalMode: e.target.value }))}><option>NONE</option><option>SINGLE</option><option>DUAL</option></select><input className="input" disabled={draft.effect === 'DENY'} type="number" min="0" step="0.01" placeholder="Max estimated cost" value={draft.maxEstimatedCost ?? ''} onChange={(e) => setDraft((v) => ({ ...v, maxEstimatedCost: num(e.target.value) }))} /><input className="input" disabled={draft.effect === 'DENY'} type="number" min="0" placeholder="Max delegation depth" value={draft.maxDelegationDepth ?? ''} onChange={(e) => setDraft((v) => ({ ...v, maxDelegationDepth: num(e.target.value) }))} /><input className="input" disabled={draft.effect === 'DENY'} type="number" min="0" placeholder="Max agent calls" value={draft.maxAgentCalls ?? ''} onChange={(e) => setDraft((v) => ({ ...v, maxAgentCalls: num(e.target.value) }))} /><input className="input" disabled={draft.effect === 'DENY'} type="number" min="0" placeholder="Max execution ms" value={draft.maxExecutionTimeMs ?? ''} onChange={(e) => setDraft((v) => ({ ...v, maxExecutionTimeMs: num(e.target.value) }))} /><input className="input" type="number" min="0" placeholder="Priority" value={draft.priority ?? 100} onChange={(e) => setDraft((v) => ({ ...v, priority: num(e.target.value) ?? 100 }))} /></div>
          <input className="input mt-3" value={changeReason} onChange={(e) => setChangeReason(e.target.value)} placeholder="Change reason (required for ACTIVE creation or any existing policy change)" />
        </GovernanceSection>

        <div className="mt-4 flex justify-end"><button disabled={saving || !draft.policyId.trim() || !draft.displayName.trim() || !selectedCapability} onClick={() => { void savePolicy(); }} className="rounded-xl bg-emerald-700 px-4 py-2 text-sm font-black text-white disabled:bg-slate-300">Save WHO MAY policy</button></div>
        {draft.createdAt ? <GovernanceSection title="Policy evidence" help="Append-only snapshots let auditors reconstruct the exact WHO MAY policy version used by a decision.">
          <div className="grid gap-3 lg:grid-cols-2">
            <div><div className="text-xs font-black uppercase tracking-wide text-slate-500">Versions</div><div className="mt-2 max-h-44 space-y-2 overflow-auto">{policyVersions.map((version) => <div key={version.version} className="rounded-xl border border-slate-200 bg-white p-2 text-xs"><div className="font-black">v{version.version} · {version.actorRef}</div><div className="mt-1 text-slate-600">{version.changeReason}</div><div className="mt-1 text-slate-400">{new Date(version.createdAt).toLocaleString()}</div></div>)}</div></div>
            <div><div className="text-xs font-black uppercase tracking-wide text-slate-500">Audit events</div><div className="mt-2 max-h-44 space-y-2 overflow-auto">{policyAudit.map((event) => <div key={event.eventId} className="rounded-xl border border-slate-200 bg-white p-2 text-xs"><div className="font-black">{event.action} · v{event.policyVersion}</div><div className="mt-1 text-slate-600">{event.reason}</div><div className="mt-1 text-slate-400">{event.actorRef} · {new Date(event.occurredAt).toLocaleString()}</div></div>)}</div></div>
          </div>
        </GovernanceSection> : null}
      </section>
    </div>

    <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="text-xs font-black uppercase tracking-wide text-slate-500">Fail-closed authorization preview</div>
      <h3 className="mt-1 text-lg font-black text-slate-950">Test WHO MAY without dispatching anything</h3>
      <p className="mt-1 text-xs text-slate-500">This writes PREVIEW decision evidence only. It does not select a provider, create a child Task or invoke A2A/MCP/Netty.</p>
      <div className="mt-4 grid gap-3 md:grid-cols-3"><select className="input" value={previewBindingId} onChange={(e) => setPreviewBindingId(e.target.value)}><option value="">Select capability provider</option>{bindings.map((binding) => <option key={binding.bindingId} value={binding.bindingId}>{binding.providerDisplayName ?? binding.providerId} · {binding.trustStatus}</option>)}</select><select className="input" value={previewPrincipalType} onChange={(e) => setPreviewPrincipalType(e.target.value)}>{PRINCIPAL_TYPES.map((item) => <option key={item}>{item}</option>)}</select><select className="input" value={previewAccessMode} onChange={(e) => setPreviewAccessMode(e.target.value)}>{ACCESS_MODES.map((item) => <option key={item}>{item}</option>)}</select><input className="input" value={previewDepartment} onChange={(e) => setPreviewDepartment(e.target.value)} placeholder="Requester Department ID" /><input className="input" value={previewGroupCsv} onChange={(e) => setPreviewGroupCsv(e.target.value)} placeholder="Requester Group IDs" /><input className="input" value={previewRoleCsv} onChange={(e) => setPreviewRoleCsv(e.target.value)} placeholder="Requester Role codes" /><input className="input" value={previewDataClass} onChange={(e) => setPreviewDataClass(e.target.value)} placeholder="Data class, e.g. INTERNAL" /><select className="input" value={previewSensitivity} onChange={(e) => setPreviewSensitivity(e.target.value)}>{SENSITIVITY.map((item) => <option key={item}>{item}</option>)}</select><select className="input" value={previewApprovalCount} onChange={(e) => setPreviewApprovalCount(e.target.value)}><option value="0">0 approvals</option><option value="1">1 approval</option><option value="2">2 approvals</option></select></div>
      <div className="mt-3 flex justify-end"><button disabled={saving || !previewBindingId || !draft.operations?.[0]} className="rounded-xl bg-slate-950 px-4 py-2 text-sm font-black text-white disabled:bg-slate-300" onClick={() => { void evaluatePreview(); }}>Evaluate WHO MAY</button></div>
      {previewDecision ? <div className={`mt-4 rounded-2xl border p-4 ${previewDecision.result === 'PASS' ? 'border-emerald-200 bg-emerald-50' : previewDecision.result === 'WAITING_APPROVAL' ? 'border-amber-200 bg-amber-50' : 'border-red-200 bg-red-50'}`}><div className="flex flex-wrap items-center justify-between gap-2"><span className="text-lg font-black">{previewDecision.result}</span><span className="font-mono text-xs">{previewDecision.decisionId}</span></div><div className="mt-2 text-sm">{(previewDecision.reasonCodes ?? []).join(' · ')}</div><div className="mt-2 text-xs text-slate-600">Selected authorization policy: {previewDecision.selectedPolicyId ?? 'none'} {previewDecision.selectedPolicyVersion ? `v${previewDecision.selectedPolicyVersion}` : ''} · Approval: {previewDecision.approvalMode ?? 'NONE'}</div><div className="mt-1 text-xs text-slate-500">Considered: {(previewDecision.consideredPolicyIds ?? []).join(', ') || 'none'}</div></div> : null}
    </section>

    <style jsx>{`.input{width:100%;border:1px solid rgb(203 213 225);border-radius:.75rem;padding:.65rem .75rem;font-size:.875rem;outline:none;background:white}.input:focus{border-color:rgb(5 150 105)}.input:disabled{background:rgb(248 250 252);color:rgb(100 116 139)}.mini{border:1px solid rgb(203 213 225);border-radius:.65rem;padding:.45rem .7rem;font-size:.72rem;font-weight:800;color:rgb(51 65 85);background:white}.check{display:flex;gap:.45rem;align-items:center;border:1px solid rgb(226 232 240);border-radius:.7rem;padding:.55rem .65rem;font-size:.72rem;font-weight:800;color:rgb(51 65 85);background:rgb(248 250 252)}`}</style>
  </div>;
}

function GovernanceSection({ title, help, children }: Readonly<{ title: string; help: string; children: ReactNode }>) {
  return <div className="mt-5 rounded-2xl border border-slate-200 bg-slate-50 p-4"><div className="font-black text-slate-900">{title}</div><p className="mt-1 text-xs leading-5 text-slate-500">{help}</p><div className="mt-3">{children}</div></div>;
}
