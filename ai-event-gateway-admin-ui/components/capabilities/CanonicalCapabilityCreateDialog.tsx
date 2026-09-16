'use client';

import { useEffect, useMemo, useState } from 'react';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import type { CoreCanonicalCapabilityDefinition } from '@/lib/types/core';

const DOMAIN_OPTIONS = [
  ['enterprise-application', 'Enterprise Applications'],
  ['integration', 'Integration'],
  ['data', 'Data'],
  ['operations', 'Operations'],
  ['security', 'Security'],
  ['support', 'Support'],
] as const;

const PURPOSE_OPTIONS = [
  { value: 'READ', label: 'Read information', help: 'Look up or retrieve information without changing it.', category: 'Query', capabilityType: 'DATA_READ', operations: ['READ'] },
  { value: 'DIAGNOSE', label: 'Diagnose a problem', help: 'Analyze an incident, alarm, error, or abnormal condition.', category: 'Diagnosis', capabilityType: 'DIAGNOSIS', operations: ['DIAGNOSE', 'ANALYZE'] },
  { value: 'CREATE', label: 'Create something', help: 'Create a record, issue, request, or other governed object.', category: 'Action', capabilityType: 'DETERMINISTIC_ACTION', operations: ['CREATE'] },
  { value: 'UPDATE', label: 'Change something', help: 'Update an existing governed record or state.', category: 'Action', capabilityType: 'DETERMINISTIC_ACTION', operations: ['UPDATE'] },
  { value: 'EXECUTE', label: 'Run an action', help: 'Execute a governed action in a connected system.', category: 'Action', capabilityType: 'DETERMINISTIC_ACTION', operations: ['EXECUTE'] },
  { value: 'ANALYZE', label: 'Analyze or reason', help: 'Interpret information and produce a conclusion or recommendation.', category: 'Support', capabilityType: 'REASONING', operations: ['ANALYZE'] },
] as const;

const CATEGORY_OPTIONS = ['Support', 'Query', 'Diagnosis', 'Action', 'Integration', 'Automation', 'Governance'] as const;
const OPERATION_OPTIONS = ['ANALYZE', 'READ', 'CREATE', 'COMMENT', 'UPDATE', 'EXECUTE', 'DIAGNOSE', 'RESOLVE'] as const;
const TYPE_OPTIONS = [
  ['SERVICE', 'General service'],
  ['DATA_READ', 'Data read'],
  ['DETERMINISTIC_ACTION', 'Deterministic action'],
  ['REASONING', 'Reasoning'],
  ['DIAGNOSIS', 'Diagnosis'],
  ['PLANNING', 'Planning'],
  ['SYNTHESIS', 'Synthesis'],
] as const;

function slug(value: string): string {
  return value.trim().toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '') || 'capability';
}

function message(cause: unknown): string {
  return cause instanceof Error && cause.message ? cause.message : 'Capability could not be created.';
}

export function CanonicalCapabilityCreateDialog({
  open,
  tenantId,
  onClose,
  onCreated,
}: Readonly<{
  open: boolean;
  tenantId: string;
  onClose: () => void;
  onCreated: (capability: CoreCanonicalCapabilityDefinition) => Promise<void> | void;
}>) {
  const [displayName, setDisplayName] = useState('');
  const [purpose, setPurpose] = useState<(typeof PURPOSE_OPTIONS)[number]['value']>('DIAGNOSE');
  const [domain, setDomain] = useState<(typeof DOMAIN_OPTIONS)[number][0]>('enterprise-application');
  const [category, setCategory] = useState<string>('Diagnosis');
  const [capabilityType, setCapabilityType] = useState('DIAGNOSIS');
  const [operations, setOperations] = useState<string[]>(['DIAGNOSE', 'ANALYZE']);
  const [description, setDescription] = useState('');
  const [codeOverride, setCodeOverride] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!open) return;
    setDisplayName(''); setPurpose('DIAGNOSE'); setDomain('enterprise-application'); setCategory('Diagnosis');
    setCapabilityType('DIAGNOSIS'); setOperations(['DIAGNOSE', 'ANALYZE']); setDescription(''); setCodeOverride(''); setError('');
  }, [open]);

  const generatedCode = useMemo(() => `${domain}.${slug(displayName)}`, [displayName, domain]);
  const effectiveCode = codeOverride.trim().toLowerCase() || generatedCode;

  function choosePurpose(value: (typeof PURPOSE_OPTIONS)[number]['value']) {
    const selected = PURPOSE_OPTIONS.find((item) => item.value === value) ?? PURPOSE_OPTIONS[0];
    setPurpose(value);
    setCategory(selected.category);
    setCapabilityType(selected.capabilityType);
    setOperations([...selected.operations]);
  }

  function toggleOperation(operation: string) {
    setOperations((current) => current.includes(operation) ? current.filter((item) => item !== operation) : [...current, operation]);
  }

  async function create() {
    if (!tenantId.trim()) { setError('Select an administration workspace first.'); return; }
    if (!displayName.trim()) { setError('Enter a clear capability name.'); return; }
    if (!operations.length) { setError('Choose what this capability does.'); return; }
    setSaving(true); setError('');
    try {
      const saved = await coreAdminApi.upsertCanonicalCapability(effectiveCode, {
        capabilityCode: effectiveCode,
        displayName: displayName.trim(),
        description: description.trim() || undefined,
        semanticDomain: DOMAIN_OPTIONS.find(([value]) => value === domain)?.[1] ?? domain,
        category,
        capabilityType,
        operations,
        inputSchema: {},
        outputSchema: {},
        resourceTypes: [],
        dataClasses: [],
        status: 'ACTIVE',
        serviceCodes: [],
      }, tenantId);
      await onCreated(saved);
      onClose();
    } catch (cause) {
      setError(message(cause));
    } finally {
      setSaving(false);
    }
  }

  return (
    <ConfirmDialog
      open={open}
      title="Add a capability"
      description="Describe what the Agent can do in business language. OpenDispatch creates the technical capability identity automatically."
      confirmLabel="Create & use"
      cancelLabel="Cancel"
      tone="primary"
      isRunning={saving}
      onCancel={onClose}
      onConfirm={() => { void create(); }}
    >
      <div className="space-y-5">
        {error ? <div className="rounded-xl border border-rose-200 bg-rose-50 px-3 py-2 text-sm font-semibold text-rose-800">{error}</div> : null}

        <label className="block text-sm font-bold text-slate-800">What should this capability be called?
          <input autoFocus value={displayName} onChange={(event) => setDisplayName(event.target.value)} placeholder="Diagnose ERP issue" className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2.5 font-normal" />
        </label>

        <fieldset>
          <legend className="text-sm font-bold text-slate-800">What does it mainly do?</legend>
          <div className="mt-2 grid gap-2 sm:grid-cols-2">
            {PURPOSE_OPTIONS.map((item) => {
              const selected = purpose === item.value;
              return <button key={item.value} type="button" onClick={() => choosePurpose(item.value)} className={`rounded-2xl border p-3 text-left ${selected ? 'border-blue-400 bg-blue-50' : 'border-slate-200 bg-white hover:bg-slate-50'}`}>
                <div className={`font-black ${selected ? 'text-blue-900' : 'text-slate-900'}`}>{selected ? '✓ ' : ''}{item.label}</div>
                <div className="mt-1 text-xs leading-5 text-slate-600">{item.help}</div>
              </button>;
            })}
          </div>
        </fieldset>

        <label className="block text-sm font-bold text-slate-800">Description <span className="font-normal text-slate-400">optional</span>
          <textarea value={description} onChange={(event) => setDescription(event.target.value)} rows={2} className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2.5 font-normal" placeholder="Example: Inspect ERP order errors and explain the likely cause." />
        </label>

        <details className="rounded-xl border border-slate-200 p-3">
          <summary className="cursor-pointer text-sm font-black text-slate-700">Advanced settings</summary>
          <div className="mt-3 space-y-3">
            <div className="grid gap-3 sm:grid-cols-2">
              <label className="block text-sm font-bold text-slate-700">Domain
                <select value={domain} onChange={(event) => setDomain(event.target.value as typeof domain)} className="mt-1 w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 font-normal">
                  {DOMAIN_OPTIONS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                </select>
              </label>
              <label className="block text-sm font-bold text-slate-700">Category
                <select value={category} onChange={(event) => setCategory(event.target.value)} className="mt-1 w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 font-normal">
                  {CATEGORY_OPTIONS.map((value) => <option key={value} value={value}>{value}</option>)}
                </select>
              </label>
            </div>
            <label className="block text-sm font-bold text-slate-700">Capability type
              <select value={capabilityType} onChange={(event) => setCapabilityType(event.target.value)} className="mt-1 w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 font-normal">
                {TYPE_OPTIONS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
              </select>
            </label>
            <fieldset>
              <legend className="text-sm font-bold text-slate-700">Operations</legend>
              <div className="mt-2 flex flex-wrap gap-2">
                {OPERATION_OPTIONS.map((operation) => <button key={operation} type="button" onClick={() => toggleOperation(operation)} className={`rounded-full border px-3 py-1.5 text-xs font-black ${operations.includes(operation) ? 'border-blue-300 bg-blue-50 text-blue-800' : 'border-slate-200 bg-white text-slate-600'}`}>{operations.includes(operation) ? '✓ ' : ''}{operation}</button>)}
              </div>
            </fieldset>
            <label className="block text-sm font-bold text-slate-700">Generated code
              <input value={codeOverride} onChange={(event) => setCodeOverride(event.target.value)} placeholder={generatedCode} className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2 font-mono text-sm font-normal" />
            </label>
            <p className="text-xs leading-5 text-slate-500">Leave this blank unless you are integrating with an existing capability code. Generated value: <code>{effectiveCode}</code></p>
          </div>
        </details>
      </div>
    </ConfirmDialog>
  );
}
