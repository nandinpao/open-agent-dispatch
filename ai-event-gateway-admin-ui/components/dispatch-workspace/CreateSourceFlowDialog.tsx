'use client';

import { useEffect, useMemo, useState } from 'react';
import { useDialogAccessibility } from '@/hooks/useDialogAccessibility';
import { Button } from '@/components/ui/Button';
import { FieldAssist } from '@/components/resource-scope/EnterpriseAccessUi';
import { AdvancedSection, EntityPicker, FormField, InlineCreateButton, SelectField, TextAreaField, TextField } from '@/components/forms';
import type { CoreAgentPoolView, CoreDispatchFlowView, CoreSourceSystem } from '@/lib/types/core';

export interface CreateSourceFlowInput {
  sourceSystem: string;
  flowCode: string;
  flowName: string;
  defaultPoolId: string;
  defaultRoutingStrategy: string;
  defaultIssueSyncPolicy: '' | 'NONE' | 'OPTIONAL' | 'REQUIRED' | 'MANUAL';
  description: string;
}

const supportedStrategies = ['LOWEST_LOAD', 'WEIGHTED_SCORE', 'MANUAL_ONLY'] as const;

function normalizeCode(value: string): string {
  return value.trim().toUpperCase().replace(/[^A-Z0-9_.-]/g, '_').replace(/^_+|_+$/g, '');
}

function suggestedFlowCode(sourceSystem: string, flows: CoreDispatchFlowView[]): string {
  const source = normalizeCode(sourceSystem) || 'SOURCE';
  const base = `${source}_DEFAULT_FLOW`;
  const used = new Set(flows.map((flow) => String(flow.flowCode ?? '').trim().toUpperCase()).filter(Boolean));
  if (!used.has(base)) return base;
  let sequence = 2;
  while (used.has(`${source}_FLOW_${String(sequence).padStart(2, '0')}`)) sequence += 1;
  return `${source}_FLOW_${String(sequence).padStart(2, '0')}`;
}

function sourceDisplay(source: CoreSourceSystem): string {
  return source.displayName && source.displayName !== source.sourceSystemId
    ? `${source.displayName} (${source.sourceSystemId})`
    : source.sourceSystemId;
}

function poolDisplay(pool: CoreAgentPoolView): string {
  return pool.poolName && pool.poolName !== pool.poolCode ? `${pool.poolName} (${pool.poolCode ?? pool.poolId})` : pool.poolCode ?? pool.poolId;
}

function initialDraft(sourceSystem: string, flows: CoreDispatchFlowView[]): CreateSourceFlowInput {
  const normalizedSource = normalizeCode(sourceSystem);
  return {
    sourceSystem,
    flowCode: suggestedFlowCode(sourceSystem, flows),
    flowName: normalizedSource ? `${normalizedSource} Dispatch Flow` : '',
    defaultPoolId: '',
    defaultRoutingStrategy: 'LOWEST_LOAD',
    defaultIssueSyncPolicy: '',
    description: 'Routes Source System events through the Default Agent Pool. Add classification rules only for stable exceptions.',
  };
}

export function CreateSourceFlowDialog({
  open,
  sourceSystems,
  flows,
  pools,
  initialSourceSystem,
  busy,
  error,
  onClose,
  onCreateSource,
  onSubmit,
}: Readonly<{
  open: boolean;
  sourceSystems: CoreSourceSystem[];
  flows: CoreDispatchFlowView[];
  pools: CoreAgentPoolView[];
  initialSourceSystem?: string | null;
  busy: boolean;
  error?: string | null;
  onClose: () => void;
  onCreateSource: () => void;
  onSubmit: (input: CreateSourceFlowInput) => void;
}>) {
  const initialSource = useMemo(() => {
    const requested = String(initialSourceSystem ?? '').trim();
    if (requested && sourceSystems.some((source) => source.sourceSystemId === requested)) return requested;
    return sourceSystems[0]?.sourceSystemId ?? '';
  }, [initialSourceSystem, sourceSystems]);
  const [draft, setDraft] = useState<CreateSourceFlowInput>(() => initialDraft(initialSource, flows));
  const dialogRef = useDialogAccessibility(open, onClose);

  useEffect(() => {
    if (open) setDraft(initialDraft(initialSource, flows));
  }, [flows, initialSource, open]);

  if (!open) return null;

  function changeSource(sourceSystem: string) {
    setDraft((current) => ({
      ...current,
      sourceSystem,
      flowCode: suggestedFlowCode(sourceSystem, flows),
      flowName: `${normalizeCode(sourceSystem)} Dispatch Flow`,
    }));
  }

  return (
    <div ref={dialogRef} tabIndex={-1} className="fixed inset-0 z-[90] flex items-start justify-center overflow-y-auto bg-slate-950/60 p-4 outline-none sm:p-8" role="dialog" aria-modal="true" aria-label="Create Source Flow">
      <div className="w-full max-w-3xl rounded-3xl bg-white shadow-2xl">
        <div className="flex items-start justify-between gap-4 rounded-t-3xl border-b border-slate-200 px-6 py-5">
          <div>
            <div className="text-xs font-black uppercase tracking-wide text-purple-700">Dispatch Setup</div>
            <h2 className="mt-1 text-xl font-black text-slate-950">Create Source Flow</h2>
            <p className="mt-1 max-w-2xl text-sm leading-6 text-slate-600">Create a named dispatch flow for one Source System. The Flow Code is tenant-unique and becomes the stable business identifier for routing evidence.</p>
          </div>
          <button type="button" onClick={onClose} className="rounded-xl border border-slate-200 px-3 py-2 text-sm font-black text-slate-600 hover:bg-slate-50" aria-label="Close">×</button>
        </div>

        <div className="space-y-4 p-6">
          {error ? <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-bold text-rose-900">{error}</div> : null}
          {!sourceSystems.length ? <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm font-bold text-amber-950">No Source System is available. <button type="button" onClick={onCreateSource} className="font-black underline">Create one here</button> without leaving Dispatch.</div> : null}

          <div className="grid gap-4 md:grid-cols-2">
            <div><div className="mb-1 flex flex-wrap items-center justify-between gap-2"><span className="text-sm font-black text-slate-800">Source System <span className="text-rose-600">*</span></span><span className="flex items-center gap-2"><InlineCreateButton onClick={onCreateSource} label="Source System" /><FieldAssist help="The Flow inherits the Source System's canonical business ownership. Create one in this popup when it is missing; use the full workspace only for advanced Intake policy." href="/source-systems" linkLabel="Manage all" /></span></div><EntityPicker id="dispatch-flow-source" value={draft.sourceSystem} onChange={changeSource} disabled={busy || !sourceSystems.length} placeholder="Select a Source System" options={sourceSystems.map((source)=>({value:source.sourceSystemId,label:sourceDisplay(source),description:source.description||source.sourceSystemId}))}/></div>
            <FormField id="dispatch-flow-name" label="Flow Name" required><TextField id="dispatch-flow-name" value={draft.flowName} onChange={(value)=>setDraft((current)=>({...current,flowName:value}))} placeholder="ERP Standard Dispatch" disabled={busy} /></FormField>
            <div><div className="mb-1 flex flex-wrap items-center justify-between gap-2"><span className="text-sm font-black text-slate-800">Default Agent Pool</span><FieldAssist help="The Agent Pool decides which approved Agent can execute Tasks from this Flow. You can leave it empty and configure it in the same Dispatch workspace after creating the Draft." href="/agents" linkLabel="Review Agents" /></div><EntityPicker id="dispatch-flow-pool" value={draft.defaultPoolId} onChange={(value)=>setDraft((current)=>({...current,defaultPoolId:value}))} disabled={busy} placeholder="Configure after creating the Draft" options={pools.map((pool)=>({value:pool.poolId,label:poolDisplay(pool),description:pool.sourceSystem||pool.poolId}))}/></div>
          </div>

          <FormField id="dispatch-flow-description" label="Description"><TextAreaField id="dispatch-flow-description" value={draft.description} onChange={(value)=>setDraft((current)=>({...current,description:value}))} disabled={busy} rows={4} /></FormField>

          <FormField id="dispatch-flow-issue-behavior" label="External Issue behavior" help="Canonical Route B policy for Tasks created by this Source Flow. This is independent from legacy createOnCompletedTask settings.">
            <SelectField id="dispatch-flow-issue-behavior" value={draft.defaultIssueSyncPolicy} onChange={(value)=>setDraft((current)=>({...current,defaultIssueSyncPolicy:value as CreateSourceFlowInput['defaultIssueSyncPolicy']}))} disabled={busy} options={[
              {value:'',label:'Select external Issue behavior…'},
              {value:'NONE',label:'Never create an external Issue'},
              {value:'OPTIONAL',label:'OPTIONAL — Create only when Task fails'},
              {value:'REQUIRED',label:'REQUIRED — Always create after Task closes'},
              {value:'MANUAL',label:'Administrator decides'},
            ]} />
          </FormField>

          <AdvancedSection title="Advanced Flow identity and routing" description="generated Flow Code and engine strategy">
            <div className="grid gap-4 md:grid-cols-2"><FormField id="dispatch-flow-code" label="Flow Code" help="Generated automatically and stable after creation. Change only when an established business code must be preserved." required><TextField id="dispatch-flow-code" value={draft.flowCode} onChange={(value)=>setDraft((current)=>({...current,flowCode:normalizeCode(value)}))} placeholder="ERP_STANDARD" disabled={busy} /></FormField><FormField id="dispatch-flow-strategy" label="Selection Strategy" help="Engine-level routing choice. Normal setup can keep the recommended default."><SelectField id="dispatch-flow-strategy" value={draft.defaultRoutingStrategy} onChange={(value)=>setDraft((current)=>({...current,defaultRoutingStrategy:value}))} disabled={busy} options={supportedStrategies.map((value)=>({value,label:value==='LOWEST_LOAD'?'Lowest current load':value==='WEIGHTED_SCORE'?'Weighted score':'Manual assignment only'}))} /></FormField></div>
          </AdvancedSection>

          <div className="rounded-2xl border border-blue-100 bg-blue-50 p-4 text-sm leading-6 text-blue-900">
            New Source Flows are created as <b>Draft</b>. After creation, confirm the Default Agent Pool, Pool Members and runtime eligibility before activation. Classification rules are optional exception routing.
          </div>
        </div>

        <div className="flex justify-end gap-2 rounded-b-3xl border-t border-slate-200 bg-slate-50 px-6 py-4">
          <Button tone="secondary" onClick={onClose} disabled={busy}>Cancel</Button>
          <Button tone="primary" onClick={() => onSubmit(draft)} disabled={busy || !sourceSystems.length || !draft.defaultIssueSyncPolicy}>{busy ? 'Creating…' : 'Create Draft'}</Button>
        </div>
      </div>
    </div>
  );
}
