'use client';

import { useCallback, useEffect, useState } from 'react';
import { useAuth } from '@/components/auth/AuthProvider';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import type { CoreCanonicalCapabilityDefinition, CoreCapabilityBinding, CoreCapabilityProvider } from '@/lib/types/core';

const PROVIDER_TYPES = [
  ['MANAGED_AGENT', 'Managed Agent'],
  ['REMOTE_A2A_AGENT', 'Remote A2A Agent'],
  ['MCP_TOOL', 'MCP Tool'],
  ['INTERNAL_SERVICE', 'Internal Service'],
] as const;
const NEW_BINDING_TRUST = ['DISCOVERED', 'PROPOSED'] as const;
const PAGE_SIZE = 50;
const BINDING_PAGE_SIZE = 100;

function msg(cause: unknown, fallback: string) { return cause instanceof Error && cause.message ? cause.message : fallback; }
function splitCsv(value: string): string[] { return [...new Set(value.split(',').map((item) => item.trim()).filter(Boolean))]; }

export function CapabilityProviderRegistryConsole() {
  const { activeTenantId: tenantId } = useAuth();
  const [capabilities, setCapabilities] = useState<CoreCanonicalCapabilityDefinition[]>([]);
  const [providers, setProviders] = useState<CoreCapabilityProvider[]>([]);
  const [bindings, setBindings] = useState<CoreCapabilityBinding[]>([]);
  const [selectedCapability, setSelectedCapability] = useState('');
  const [capabilitySearch, setCapabilitySearch] = useState('');
  const [providerSearch, setProviderSearch] = useState('');
  const [providerHasMore, setProviderHasMore] = useState(false);
  const [bindingHasMore, setBindingHasMore] = useState(false);
  const [providerDraft, setProviderDraft] = useState<CoreCapabilityProvider>({ providerId: '', providerType: 'MANAGED_AGENT', displayName: '', providerRef: '', registrationSource: 'MANUAL', catalogStatus: 'REGISTERED', metadata: {} });
  const [bindingProviderId, setBindingProviderId] = useState('');
  const [bindingOperations, setBindingOperations] = useState('');
  const [bindingTrust, setBindingTrust] = useState('PROPOSED');
  const [trustReason, setTrustReason] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [saving, setSaving] = useState(false);
  const providerExists = Boolean(providerDraft.createdAt);

  const loadCapabilities = useCallback(async () => {
    if (!tenantId) return;
    try {
      const rows = await coreAdminApi.getCanonicalCapabilities('ACTIVE', capabilitySearch || undefined, undefined, tenantId, PAGE_SIZE);
      setCapabilities(rows);
      if (selectedCapability && !rows.some((row) => row.capabilityCode === selectedCapability)) {
        const exact = await coreAdminApi.getCanonicalCapability(selectedCapability, tenantId).catch(() => undefined);
        if (exact) setCapabilities((current) => [exact, ...current.filter((row) => row.capabilityCode !== exact.capabilityCode)]);
      }
      if (!selectedCapability && rows.length) setSelectedCapability(rows[0].capabilityCode);
    } catch (cause) { setError(msg(cause, 'Unable to search Canonical Capabilities.')); }
  }, [tenantId, capabilitySearch, selectedCapability]);

  const loadProviders = useCallback(async (append = false) => {
    if (!tenantId) return;
    try {
      const after = append && providers.length ? providers[providers.length - 1].providerId : undefined;
      const rows = await coreAdminApi.getCapabilityProviders(undefined, undefined, providerSearch || undefined, tenantId, after, PAGE_SIZE);
      setProviders((current) => append ? [...current, ...rows.filter((row) => !current.some((item) => item.providerId === row.providerId))] : rows);
      setProviderHasMore(rows.length === PAGE_SIZE);
    } catch (cause) { setError(msg(cause, 'Unable to search Capability Providers.')); }
  }, [tenantId, providerSearch, providers]);

  const loadBindings = useCallback(async (append = false) => {
    if (!tenantId || !selectedCapability) { setBindings([]); setBindingHasMore(false); return; }
    try {
      const after = append && bindings.length ? bindings[bindings.length - 1].bindingId : undefined;
      const rows = await coreAdminApi.getCapabilityBindings(selectedCapability, undefined, undefined, tenantId, after, BINDING_PAGE_SIZE);
      setBindings((current) => append ? [...current, ...rows.filter((row) => !current.some((item) => item.bindingId === row.bindingId))] : rows);
      setBindingHasMore(rows.length === BINDING_PAGE_SIZE);
    } catch (cause) { setError(msg(cause, 'Unable to load capability providers.')); }
  }, [tenantId, selectedCapability, bindings]);

  useEffect(() => { const timer = window.setTimeout(() => { void loadCapabilities(); }, 250); return () => window.clearTimeout(timer); }, [loadCapabilities]);
  useEffect(() => { const timer = window.setTimeout(() => { void loadProviders(false); }, 250); return () => window.clearTimeout(timer); }, [tenantId, providerSearch]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { void loadBindings(false); }, [tenantId, selectedCapability]); // eslint-disable-line react-hooks/exhaustive-deps

  async function refresh() {
    await Promise.all([loadCapabilities(), loadProviders(false), loadBindings(false)]);
  }

  async function saveProvider() {
    if (!tenantId || !providerDraft.providerId.trim() || !providerDraft.displayName.trim() || !providerDraft.providerRef.trim()) return;
    setSaving(true); setError(''); setNotice('');
    try {
      const saved = await coreAdminApi.upsertCapabilityProvider(providerDraft.providerId.trim(), {
        ...providerDraft,
        providerId: providerDraft.providerId.trim(), displayName: providerDraft.displayName.trim(), providerRef: providerDraft.providerRef.trim(),
      }, tenantId);
      setProviderDraft(saved); setBindingProviderId(saved.providerId);
      setNotice(`Provider ${saved.providerId} registered for WHO CAN discovery. This does not authorize execution.`);
      await refresh();
    } catch (cause) { setError(msg(cause, 'Provider could not be registered.')); }
    finally { setSaving(false); }
  }

  async function changeTrust(binding: CoreCapabilityBinding, nextTrust: string) {
    if (!tenantId) return;
    if (!trustReason.trim()) { setError('Enter a trust change reason before changing binding state.'); return; }
    setSaving(true); setError(''); setNotice('');
    try {
      const saved = await coreAdminApi.upsertCapabilityBinding(binding.bindingId, { ...binding, trustStatus: nextTrust }, tenantId, trustReason.trim());
      setNotice(`Binding ${saved.bindingId} moved to ${saved.trustStatus}. This remains catalog trust only; Phase 3 still controls WHO MAY.`);
      setTrustReason('');
      await loadBindings(false);
    } catch (cause) { setError(msg(cause, 'Binding trust state could not be changed.')); }
    finally { setSaving(false); }
  }

  async function saveBinding() {
    if (!tenantId || !selectedCapability || !bindingProviderId) return;
    setSaving(true); setError(''); setNotice('');
    try {
      const existing = bindings.find((binding) => binding.capabilityCode === selectedCapability && binding.providerId === bindingProviderId);
      const bindingId = existing?.bindingId ?? `binding-${crypto.randomUUID()}`;
      const trustStatus = existing?.trustStatus ?? bindingTrust;
      const saved = await coreAdminApi.upsertCapabilityBinding(bindingId, {
        bindingId,
        capabilityCode: selectedCapability,
        providerId: bindingProviderId,
        supportedOperations: splitCsv(bindingOperations),
        trustStatus,
      }, tenantId);
      setNotice(`Binding ${saved.bindingId} records WHO CAN only. Trust state ${saved.trustStatus} is not WHO MAY authorization.`);
      await loadBindings(false);
    } catch (cause) { setError(msg(cause, 'Capability provider qualification could not be saved.')); }
    finally { setSaving(false); }
  }

  const selectedCapabilityDefinition = capabilities.find((capability) => capability.capabilityCode === selectedCapability);

  if (!tenantId) return <div className="rounded-2xl border border-amber-200 bg-amber-50 p-5 text-sm font-semibold text-amber-950">Select an administration workspace before managing Capability Providers.</div>;

  return <div className="space-y-5">
    <section className="rounded-3xl border border-violet-200 bg-violet-50 p-5">
      <div className="text-xs font-black uppercase tracking-wide text-violet-700">Phase 2 architecture boundary</div>
      <h2 className="mt-1 text-lg font-black text-violet-950">Provider Registry answers WHO CAN — not WHO MAY, WHO SHOULD or HOW</h2>
      <p className="mt-2 max-w-5xl text-sm leading-6 text-violet-900">Register provider identities and bind them to Canonical Capabilities. Provider references are opaque registry identities: do not put endpoints or credentials here. APPROVED is catalog trust only; Phase 3 will decide execution authorization.</p>
      <p className="mt-2 text-xs font-semibold text-violet-800">Provider and Binding searches are server-side and bounded; this workspace does not preload an enterprise-wide provider table into the browser.</p>
    </section>

    {error ? <div className="rounded-2xl border border-red-200 bg-red-50 p-4 text-sm font-semibold text-red-900">{error}</div> : null}
    {notice ? <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-semibold text-emerald-900">{notice}</div> : null}

    <div className="grid gap-5 xl:grid-cols-2">
      <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="text-xs font-black uppercase tracking-wide text-slate-500">Provider registry</div>
        <h3 className="mt-1 text-lg font-black text-slate-950">Provider identities</h3>
        <p className="mt-1 text-xs text-slate-500">Managed Agent, Remote A2A Agent, MCP Tool and Internal Service are supply categories only.</p>
        <div className="mt-4 grid gap-3 md:grid-cols-2">
          <input disabled={providerExists} className="input" placeholder="provider-id" value={providerDraft.providerId} onChange={(e) => setProviderDraft((v) => ({ ...v, providerId: e.target.value }))} />
          <select disabled={providerExists} className="input" value={providerDraft.providerType} onChange={(e) => setProviderDraft((v) => ({ ...v, providerType: e.target.value }))}>{PROVIDER_TYPES.map(([value,label]) => <option key={value} value={value}>{label}</option>)}</select>
          <input className="input" placeholder="Display name" value={providerDraft.displayName} onChange={(e) => setProviderDraft((v) => ({ ...v, displayName: e.target.value }))} />
          <input disabled={providerExists} className="input" placeholder="Opaque registry reference, e.g. agent-42" value={providerDraft.providerRef} onChange={(e) => setProviderDraft((v) => ({ ...v, providerRef: e.target.value }))} />
          <select disabled={providerExists} className="input" value={providerDraft.registrationSource ?? 'MANUAL'} onChange={(e) => setProviderDraft((v) => ({ ...v, registrationSource: e.target.value }))}><option>MANUAL</option><option>MANAGED_REGISTRY</option><option>AGENT_CARD</option><option>MCP_CATALOG</option><option>INTERNAL_CATALOG</option></select>
          <select className="input" value={providerDraft.catalogStatus ?? 'REGISTERED'} onChange={(e) => setProviderDraft((v) => ({ ...v, catalogStatus: e.target.value }))}><option>REGISTERED</option><option>OBSERVED</option><option>DISABLED</option><option>RETIRED</option></select>
        </div>
        <div className="mt-3 flex justify-between gap-2"><button type="button" onClick={() => setProviderDraft({ providerId: '', providerType: 'MANAGED_AGENT', displayName: '', providerRef: '', registrationSource: 'MANUAL', catalogStatus: 'REGISTERED', metadata: {} })} className="mini">New provider</button><button disabled={saving} onClick={() => { void saveProvider(); }} className="rounded-xl bg-violet-700 px-4 py-2 text-sm font-black text-white disabled:bg-slate-300">Save provider</button></div>
        <input value={providerSearch} onChange={(e) => setProviderSearch(e.target.value)} placeholder="Server search providers by ID, name or registry reference" className="input mt-5" />
        <div className="mt-3 space-y-2">{providers.map((provider) => <button key={provider.providerId} type="button" onClick={() => { setProviderDraft(provider); setBindingProviderId(provider.providerId); }} className="w-full rounded-2xl border border-slate-200 bg-slate-50 p-3 text-left hover:border-violet-200"><div className="flex justify-between gap-2"><span className="font-black text-slate-900">{provider.displayName}</span><span className="text-xs font-black text-slate-500">{provider.catalogStatus}</span></div><div className="mt-1 font-mono text-xs text-violet-700">{provider.providerId}</div><div className="mt-1 text-xs text-slate-500">{provider.providerType} · ref {provider.providerRef}</div></button>)}</div>
        {providerHasMore ? <button type="button" onClick={() => { void loadProviders(true); }} className="mini mt-3">Load more providers</button> : null}
      </section>

      <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="text-xs font-black uppercase tracking-wide text-slate-500">Provider qualification</div>
        <h3 className="mt-1 text-lg font-black text-slate-950">Who can provide this capability?</h3>
        <input className="input mt-4" value={capabilitySearch} onChange={(e) => setCapabilitySearch(e.target.value)} placeholder="Server search Canonical Capabilities" />
        <select className="input mt-2" value={selectedCapability} onChange={(e) => { setSelectedCapability(e.target.value); setBindingOperations(''); }}><option value="">Select Canonical Capability</option>{capabilities.map((capability) => <option key={capability.capabilityCode} value={capability.capabilityCode}>{capability.displayName} · {capability.capabilityCode}</option>)}</select>
        {selectedCapabilityDefinition ? <div className="mt-2 text-xs text-slate-500">Allowed operations: {(selectedCapabilityDefinition.operations ?? []).join(', ') || 'none'}</div> : null}
        <div className="mt-4 grid gap-3 md:grid-cols-2">
          <select className="input" value={bindingProviderId} onChange={(e) => setBindingProviderId(e.target.value)}><option value="">Select Provider from current server-search result</option>{providers.filter((provider) => !['DISABLED','RETIRED'].includes(provider.catalogStatus ?? '')).map((provider) => <option key={provider.providerId} value={provider.providerId}>{provider.displayName} · {provider.providerType}</option>)}</select>
          <input className="input" value={bindingOperations} onChange={(e) => setBindingOperations(e.target.value)} placeholder="Supported operations; blank = all canonical" />
          <select className="input" value={bindingTrust} onChange={(e) => setBindingTrust(e.target.value)}>{NEW_BINDING_TRUST.map((state) => <option key={state}>{state}</option>)}</select>
          <input className="input" value={trustReason} onChange={(e) => setTrustReason(e.target.value)} placeholder="Trust transition reason (required when state changes)" />
          <div className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-xs leading-5 text-slate-600 md:col-span-2">New manual provider qualifications may start DISCOVERED or PROPOSED. Agent Card / MCP Catalog discoveries must start DISCOVERED. Every later trust transition records the authenticated actor and requires a reason.</div>
        </div>
        <div className="mt-3 flex justify-end"><button disabled={saving || !selectedCapability || !bindingProviderId} onClick={() => { void saveBinding(); }} className="rounded-xl bg-violet-700 px-4 py-2 text-sm font-black text-white disabled:bg-slate-300">Register capability provider</button></div>
        <div className="mt-5 space-y-2">{bindings.length ? bindings.map((binding) => <div key={binding.bindingId} className="rounded-2xl border border-slate-200 p-4"><div className="flex flex-wrap items-center justify-between gap-2"><div><div className="font-black text-slate-950">{binding.providerDisplayName ?? binding.providerId}</div><div className="mt-1 text-xs text-slate-500">{binding.providerType} · {(binding.supportedOperations ?? []).join(', ') || 'all canonical operations'}</div></div><span className="rounded-full border border-violet-200 bg-violet-50 px-2 py-1 text-xs font-black text-violet-800">{binding.trustStatus}</span></div><div className="mt-2 text-xs font-semibold text-slate-500">Catalog trust only — not execution authorization, workload eligibility or routing selection.</div><div className="mt-3 flex flex-wrap gap-2">{binding.trustStatus === 'DISCOVERED' ? <button className="mini" onClick={() => { void changeTrust(binding, 'PROPOSED'); }}>Propose</button> : null}{binding.trustStatus === 'PROPOSED' ? <button className="mini" onClick={() => { void changeTrust(binding, 'VERIFIED'); }}>Verify</button> : null}{binding.trustStatus === 'VERIFIED' ? <button className="mini" onClick={() => { void changeTrust(binding, 'APPROVED'); }}>Approve catalog trust</button> : null}{!['SUSPENDED','REVOKED','STALE'].includes(binding.trustStatus ?? '') ? <button className="mini" onClick={() => { void changeTrust(binding, 'SUSPENDED'); }}>Suspend</button> : null}{!['REVOKED','STALE'].includes(binding.trustStatus ?? '') ? <button className="mini" onClick={() => { void changeTrust(binding, 'STALE'); }}>Mark stale</button> : null}{['SUSPENDED','STALE'].includes(binding.trustStatus ?? '') ? <button className="mini" onClick={() => { void changeTrust(binding, 'PROPOSED'); }}>Re-propose</button> : null}{binding.trustStatus !== 'REVOKED' ? <button className="mini" onClick={() => { void changeTrust(binding, 'REVOKED'); }}>Revoke</button> : null}</div></div>) : <div className="rounded-2xl border border-dashed border-slate-200 p-4 text-sm text-slate-500">No provider is registered for the selected Capability yet.</div>}</div>
        {bindingHasMore ? <button type="button" onClick={() => { void loadBindings(true); }} className="mini mt-3">Load more bindings</button> : null}
      </section>
    </div>
    <style jsx>{`.input{width:100%;border:1px solid rgb(203 213 225);border-radius:.75rem;padding:.65rem .75rem;font-size:.875rem;outline:none;background:white}.input:focus{border-color:rgb(124 58 237)}.input:disabled{background:rgb(248 250 252);color:rgb(100 116 139)}.mini{border:1px solid rgb(203 213 225);border-radius:.65rem;padding:.4rem .65rem;font-size:.75rem;font-weight:800;color:rgb(51 65 85);background:white}.mini:hover{border-color:rgb(167 139 250)}`}</style>
  </div>;
}
