'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/components/auth/AuthProvider';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import type { CoreCanonicalCapabilityDefinition } from '@/lib/types/core';
import { AdvancedSection, FormField, MultiSelectPicker, SelectField, TextAreaField, TextField } from '@/components/forms';

const EMPTY: CoreCanonicalCapabilityDefinition = {
  capabilityCode: '',
  displayName: '',
  description: '',
  semanticDomain: '',
  category: '',
  capabilityType: 'SERVICE',
  operations: [],
  inputSchema: {},
  outputSchema: {},
  resourceTypes: [],
  dataClasses: [],
  status: 'DRAFT',
  serviceCodes: [],
};

const CAPABILITY_TYPES = [
  ['SERVICE', 'General service'],
  ['DATA_READ', 'Deterministic data read'],
  ['DETERMINISTIC_ACTION', 'Deterministic action'],
  ['REASONING', 'Reasoning'],
  ['DIAGNOSIS', 'Diagnosis'],
  ['PLANNING', 'Planning'],
  ['SYNTHESIS', 'Synthesis'],
] as const;

const DOMAIN_OPTIONS = ['Enterprise Applications', 'Integration', 'Data', 'Operations', 'Security', 'Support'] as const;
const CATEGORY_OPTIONS = ['Support', 'Query', 'Diagnosis', 'Action', 'Integration', 'Automation', 'Governance'] as const;
const OPERATION_OPTIONS = ['ANALYZE', 'READ', 'CREATE', 'COMMENT', 'UPDATE', 'EXECUTE', 'DIAGNOSE', 'RESOLVE'] as const;
const DATA_CLASS_OPTIONS = ['PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED'] as const;

function csv(values?: string[]): string { return (values ?? []).join(', '); }
function splitCsv(value: string): string[] { return [...new Set(value.split(',').map((item) => item.trim()).filter(Boolean))]; }
function selectOptions<T extends readonly string[]>(options: T, current?: string): string[] { const value = current?.trim(); return value && !options.includes(value as T[number]) ? [value, ...options] : [...options]; }
function message(cause: unknown, fallback: string): string { return cause instanceof Error && cause.message ? cause.message : fallback; }
function pretty(value?: Record<string, unknown>): string { return JSON.stringify(value ?? {}, null, 2); }

export function CapabilityCatalogConsole() {
  const { activeTenantId: tenantId } = useAuth();
  const [items, setItems] = useState<CoreCanonicalCapabilityDefinition[]>([]);
  const [selectedCode, setSelectedCode] = useState('');
  const [draft, setDraft] = useState<CoreCanonicalCapabilityDefinition>(EMPTY);
  const [operations, setOperations] = useState<string[]>([]);
  const [resourceTypes, setResourceTypes] = useState('');
  const [dataClasses, setDataClasses] = useState<string[]>([]);
  const [serviceCodes, setServiceCodes] = useState('');
  const [inputSchema, setInputSchema] = useState('{}');
  const [outputSchema, setOutputSchema] = useState('{}');
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const load = useCallback(async () => {
    if (!tenantId) return;
    setLoading(true); setError('');
    try {
      setItems(await coreAdminApi.getCanonicalCapabilities(undefined, undefined, undefined, tenantId));
    } catch (cause) { setError(message(cause, 'Unable to load the Canonical Capability Catalog.')); }
    finally { setLoading(false); }
  }, [tenantId]);

  useEffect(() => { void load(); }, [load]);

  function edit(item?: CoreCanonicalCapabilityDefinition) {
    const next = item ?? EMPTY;
    setSelectedCode(item?.capabilityCode ?? '');
    setDraft({ ...EMPTY, ...next });
    setOperations(next.operations ?? []);
    setResourceTypes(csv(next.resourceTypes));
    setDataClasses(next.dataClasses ?? []);
    setServiceCodes(csv(next.serviceCodes));
    setInputSchema(pretty(next.inputSchema));
    setOutputSchema(pretty(next.outputSchema));
    setError(''); setNotice('');
  }

  async function save() {
    if (!tenantId || !draft.capabilityCode.trim() || !draft.displayName.trim()) return;
    setSaving(true); setError(''); setNotice('');
    try {
      const parsedInput = JSON.parse(inputSchema || '{}') as Record<string, unknown>;
      const parsedOutput = JSON.parse(outputSchema || '{}') as Record<string, unknown>;
      const code = draft.capabilityCode.trim().toLowerCase();
      const saved = await coreAdminApi.upsertCanonicalCapability(code, {
        ...draft,
        capabilityCode: code,
        displayName: draft.displayName.trim(),
        description: draft.description?.trim(),
        semanticDomain: draft.semanticDomain?.trim(),
        category: draft.category?.trim(),
        operations,
        resourceTypes: splitCsv(resourceTypes),
        dataClasses,
        serviceCodes: splitCsv(serviceCodes),
        inputSchema: parsedInput,
        outputSchema: parsedOutput,
      }, tenantId);
      setSelectedCode(saved.capabilityCode);
      setDraft(saved);
      setOperations(saved.operations ?? []); setResourceTypes(csv(saved.resourceTypes)); setDataClasses(saved.dataClasses ?? []);
      setServiceCodes(csv(saved.serviceCodes)); setInputSchema(pretty(saved.inputSchema)); setOutputSchema(pretty(saved.outputSchema));
      setNotice(`Capability ${saved.capabilityCode} saved as semantic WHAT contract version ${saved.version ?? 1}.`);
      await load();
    } catch (cause) { setError(message(cause, 'Capability Definition could not be saved. Check semantic code and JSON schemas.')); }
    finally { setSaving(false); }
  }

  const visible = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return items;
    return items.filter((item) => [item.capabilityCode, item.displayName, item.semanticDomain, item.category, item.description]
      .some((value) => value?.toLowerCase().includes(q)));
  }, [items, search]);

  if (!tenantId) return <div className="rounded-2xl border border-amber-200 bg-amber-50 p-5 text-sm font-semibold text-amber-950">Select an administration workspace before managing Canonical Capabilities.</div>;

  return <div className="space-y-5">
    <section className="rounded-3xl border border-blue-200 bg-blue-50 p-5">
      <div className="text-xs font-black uppercase tracking-wide text-blue-700">Phase 1 architecture boundary</div>
      <h2 className="mt-1 text-lg font-black text-blue-950">Capability answers WHAT — never who or where</h2>
      <p className="mt-2 max-w-5xl text-sm leading-6 text-blue-900">Define reusable enterprise semantics without target System, target Domain, Agent Pool, Agent, endpoint, credential, A2A or MCP transport. Semantic Domain is taxonomy/search metadata only; it is not a routing destination.</p>
    </section>

    {error ? <div className="rounded-2xl border border-red-200 bg-red-50 p-4 text-sm font-semibold text-red-900">{error}</div> : null}
    {notice ? <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-semibold text-emerald-900">{notice}</div> : null}

    <div className="grid gap-5 xl:grid-cols-[minmax(320px,0.8fr)_minmax(560px,1.6fr)]">
      <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex items-start justify-between gap-3"><div><div className="text-xs font-black uppercase tracking-wide text-slate-500">Canonical catalog</div><h2 className="mt-1 text-lg font-black text-slate-950">Semantic capabilities</h2></div><button type="button" onClick={() => edit()} className="rounded-xl bg-blue-700 px-3 py-2 text-xs font-black text-white">New capability</button></div>
        <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search capability, taxonomy or category" className="mt-4 w-full rounded-xl border border-slate-300 px-3 py-2 text-sm outline-none focus:border-blue-500" />
        <div className="mt-4 space-y-2">
          {loading ? <div className="rounded-xl bg-slate-50 p-4 text-sm text-slate-500">Loading catalog…</div> : null}
          {!loading && !visible.length ? <div className="rounded-xl border border-dashed border-slate-200 p-4 text-sm text-slate-500">No Canonical Capabilities yet. Create the first semantic WHAT contract.</div> : null}
          {visible.map((item) => <button key={item.capabilityCode} type="button" onClick={() => edit(item)} className={`w-full rounded-2xl border p-4 text-left ${selectedCode === item.capabilityCode ? 'border-blue-300 bg-blue-50' : 'border-slate-200 bg-slate-50 hover:border-blue-200'}`}>
            <div className="flex items-start justify-between gap-2"><div className="font-black text-slate-950">{item.displayName}</div><span className="rounded-full border border-slate-200 bg-white px-2 py-1 text-xs font-black text-slate-600">{item.status}</span></div>
            <div className="mt-1 break-all font-mono text-xs font-bold text-blue-700">{item.capabilityCode}</div>
            <div className="mt-2 text-xs text-slate-500">{[item.semanticDomain, item.category, item.capabilityType].filter(Boolean).join(' · ') || 'Unclassified semantic capability'}</div>
            <div className="mt-2 text-xs text-slate-500">{item.serviceCodes?.length ?? 0} known Service Code mapping(s) · v{item.version ?? 1}</div>
          </button>)}
        </div>
      </section>

      <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between"><div><div className="text-xs font-black uppercase tracking-wide text-slate-500">WHAT contract</div><h2 className="mt-1 text-lg font-black text-slate-950">{selectedCode ? 'Edit Capability Definition' : 'Create Capability Definition'}</h2></div>{selectedCode ? <span className="text-xs font-bold text-slate-500">Current revision v{draft.version ?? 1}</span> : null}</div>
        <div className="mt-5 grid gap-4 md:grid-cols-2">
          <FormField id="capability-display-name" label="Display name" required><TextField id="capability-display-name" value={draft.displayName} onChange={(value) => setDraft((current) => ({ ...current, displayName: value }))} placeholder="Read inventory availability" /></FormField>
          <FormField id="capability-lifecycle" label="Lifecycle"><SelectField id="capability-lifecycle" value={draft.status ?? 'DRAFT'} onChange={(value) => setDraft((current) => ({ ...current, status: value }))} options={[{value:'DRAFT',label:'Draft'},{value:'ACTIVE',label:'Active'},{value:'DISABLED',label:'Disabled'},{value:'RETIRED',label:'Retired'}]} /></FormField>
          <FormField id="capability-domain" label="Semantic Domain" help="Taxonomy only; never a routing destination."><SelectField id="capability-domain" value={draft.semanticDomain ?? ''} onChange={(value) => setDraft((current) => ({ ...current, semanticDomain: value }))} placeholder="Select a domain" options={selectOptions(DOMAIN_OPTIONS, draft.semanticDomain).map((value) => ({value,label:value}))} /></FormField>
          <FormField id="capability-category" label="Category"><SelectField id="capability-category" value={draft.category ?? ''} onChange={(value) => setDraft((current) => ({ ...current, category: value }))} placeholder="Select a category" options={selectOptions(CATEGORY_OPTIONS, draft.category).map((value) => ({value,label:value}))} /></FormField>
          <FormField id="capability-type" label="Capability type"><SelectField id="capability-type" value={draft.capabilityType ?? 'SERVICE'} onChange={(value) => setDraft((current) => ({ ...current, capabilityType: value }))} options={CAPABILITY_TYPES.map(([value,label]) => ({value,label}))} /></FormField>
        </div>
        <div className="mt-4"><FormField id="capability-description" label="Description"><TextAreaField id="capability-description" value={draft.description ?? ''} onChange={(value) => setDraft((current) => ({ ...current, description: value }))} rows={3} placeholder="Describe the business capability independently of any enterprise application." /></FormField></div>
        <div className="mt-4 grid gap-4 md:grid-cols-2">
          <FormField id="capability-operations" label="Operations" help="Select all operations this semantic capability supports."><MultiSelectPicker id="capability-operations" values={operations} onChange={setOperations} placeholder="Select operations" options={OPERATION_OPTIONS.map((value)=>({value,label:value}))} />{!operations.length ? <p className="mt-2 text-xs font-semibold text-amber-700">Select at least one operation before activating the Capability.</p> : null}</FormField>
          <FormField id="capability-data-classes" label="Data classes" help="Optional data sensitivity categories."><MultiSelectPicker id="capability-data-classes" values={dataClasses} onChange={setDataClasses} placeholder="Select data classes" options={DATA_CLASS_OPTIONS.map((value)=>({value,label:value}))} /></FormField>
        </div>
        <div className="mt-4">
          <AdvancedSection title="Advanced capability contract" description="code, service mapping, resource types, JSON schemas">
            <div className="grid gap-4 md:grid-cols-2">
              <FormField id="capability-code" label="Capability code" help="Lower-case semantic dot notation. Inline creation normally generates this automatically."><TextField id="capability-code" disabled={Boolean(selectedCode)} value={draft.capabilityCode} onChange={(value) => setDraft((current) => ({ ...current, capabilityCode: value }))} placeholder="inventory.availability.read" /></FormField>
              <FormField id="capability-service-codes" label="Known Service Codes" help="Optional classification → WHAT mappings; never provider mappings. No canonical Service Code catalog exists yet, so this technical compatibility field remains text-only in Advanced."><TextField id="capability-service-codes" value={serviceCodes} onChange={setServiceCodes} placeholder="ORDER_STATUS_QUERY, INVENTORY_CHECK" /></FormField>
              <FormField id="capability-resource-types" label="Resource types" help="Technical semantic resource identifiers. Kept in Advanced until a canonical resource-type catalog exists."><TextField id="capability-resource-types" value={resourceTypes} onChange={setResourceTypes} placeholder="ORDER, INVENTORY_ITEM" /></FormField>
            </div>
            <div className="mt-4 grid gap-4 lg:grid-cols-2">
              <FormField id="capability-input-schema" label="Input schema" help="JSON Schema-compatible semantic input contract."><TextAreaField id="capability-input-schema" value={inputSchema} onChange={setInputSchema} rows={8} technical /></FormField>
              <FormField id="capability-output-schema" label="Output schema" help="JSON Schema-compatible semantic output contract."><TextAreaField id="capability-output-schema" value={outputSchema} onChange={setOutputSchema} rows={8} technical /></FormField>
            </div>
          </AdvancedSection>
        </div>
        <div className="mt-5 flex justify-end gap-2"><button type="button" onClick={() => edit(selectedCode ? items.find((item) => item.capabilityCode === selectedCode) : undefined)} className="rounded-xl border border-slate-300 px-4 py-2 text-sm font-black text-slate-700">Reset</button><button type="button" disabled={saving || !draft.capabilityCode.trim() || !draft.displayName.trim()} onClick={() => { void save(); }} className="rounded-xl bg-blue-700 px-5 py-2 text-sm font-black text-white disabled:bg-slate-300">{saving ? 'Saving…' : 'Save Capability'}</button></div>
      </section>
    </div>
  </div>;
}

