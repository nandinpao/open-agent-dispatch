'use client';

import { useMemo } from 'react';
import { useDialogAccessibility } from '@/hooks/useDialogAccessibility';
import { StatusBadge } from '@/components/common/StatusBadge';
import { Button } from '@/components/ui/Button';
import type { CoreAgentPoolView, CoreDispatchFlowAgentOptionView, CoreSourceSystem } from '@/lib/types/core';
import { AgentPoolContextualDrawer } from './drawers/AgentPoolContextualDrawer';
import { poolDisplay } from './dispatchWorkspaceModel';
import {
  type EditablePoolMember,
  type PoolEditorIntent,
  type PoolEditorState,
  type RuleEditorState,
  memberStatus,
  newMemberForAgent,
  normalizeCode,
  normalizePositiveInteger,
  normalizePriority,
  sourceDisplay,
} from './dispatchWorkspaceEditorModel';

const inputClass = 'mt-1 w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm outline-none transition focus:border-purple-400 focus:ring-2 focus:ring-purple-100';
const labelClass = 'text-sm font-black text-slate-800';

export function PoolEditorDrawer({
  open,
  intent,
  editor,
  sourceSystems,
  agents,
  busy,
  error,
  onChange,
  onClose,
  onSave,
}: Readonly<{
  open: boolean;
  intent: PoolEditorIntent;
  editor: PoolEditorState;
  sourceSystems: CoreSourceSystem[];
  agents: CoreDispatchFlowAgentOptionView[];
  busy: boolean;
  error?: string | null;
  onChange: (patch: Partial<PoolEditorState>) => void;
  onClose: () => void;
  onSave: () => void;
}>) {
  const selectedAgentIds = useMemo(() => new Set(editor.members.map((member) => member.agentId)), [editor.members]);

  function updateMember(agentId: string, patch: Partial<EditablePoolMember>) {
    onChange({ members: editor.members.map((member) => member.agentId === agentId ? { ...member, ...patch } : member) });
  }

  function toggleAgent(agent: CoreDispatchFlowAgentOptionView) {
    if (selectedAgentIds.has(agent.agentId)) {
      onChange({ members: editor.members.filter((member) => member.agentId !== agent.agentId) });
      return;
    }
    onChange({ members: [...editor.members, newMemberForAgent(agent, editor)] });
  }

  return (
    <AgentPoolContextualDrawer
      open={open}
      onClose={onClose}
      title={editor.poolId ? 'Edit Agent Pool' : 'Create Agent Pool'}
      description={intent === 'defaultPool'
        ? 'Save this Pool and assign it as the Source Flow Default Pool.'
        : 'Manage Pool membership, weight, priority, member status, and runtime evidence.'}
      version={editor.version ? `version=${editor.version} · updatedAt=${editor.updatedAt ?? '-'}` : undefined}
      footer={(
        <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
          <Button onClick={onClose} disabled={busy}>Cancel</Button>
          <Button tone="primary" onClick={onSave} disabled={busy}>{busy ? 'Saving…' : 'Save Agent Pool'}</Button>
        </div>
      )}
    >
      <div className="space-y-6">
        {error ? <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-bold text-rose-900" role="alert">{error}</div> : null}
        <section className="rounded-3xl border border-slate-200 bg-slate-50 p-5">
          <div className="text-xs font-black uppercase tracking-wide text-slate-500">Basic Information</div>
          <div className="mt-4 grid gap-4 md:grid-cols-2">
            <label className={labelClass}>Source System
              <select className={inputClass} value={editor.sourceSystem} onChange={(event) => onChange({ sourceSystem: event.target.value })}>
                <option value="">Shared Pool</option>
                {sourceSystems.map((source) => <option key={source.sourceSystemId} value={source.sourceSystemId}>{sourceDisplay(source)}</option>)}
              </select>
            </label>
            <label className={labelClass}>Pool Type
              <select className={inputClass} value={editor.poolType} onChange={(event) => onChange({ poolType: event.target.value })}>
                <option value="RESOLUTION">Resolution</option>
                <option value="TRIAGE">Triage</option>
                <option value="ESCALATION">Escalation</option>
                <option value="MANUAL_REVIEW">Manual Review</option>
              </select>
            </label>
            <label className={labelClass}>Pool Name
              <input className={inputClass} value={editor.poolName} onChange={(event) => onChange({ poolName: event.target.value })} />
            </label>
            <label className={labelClass}>Status
              <select className={inputClass} value={editor.status} onChange={(event) => onChange({ status: event.target.value })}>
                <option value="ACTIVE">Active</option>
                <option value="INACTIVE">Inactive</option>
                <option value="DRAFT">Draft</option>
              </select>
            </label>
            <label className={`${labelClass} md:col-span-2`}>Description
              <textarea className={inputClass} rows={3} value={editor.description} onChange={(event) => onChange({ description: event.target.value })} />
            </label>
          </div>
          <details className="mt-4 rounded-2xl border border-slate-200 bg-white">
            <summary className="cursor-pointer list-none px-4 py-3 text-sm font-black text-slate-700">Advanced Pool settings <span className="ml-2 text-xs font-medium text-slate-500">Technical code and routing strategy</span></summary>
            <div className="grid gap-4 border-t border-slate-200 p-4 md:grid-cols-2">
              <label className={labelClass}>Pool Code
                <input className={inputClass} value={editor.poolCode} onChange={(event) => onChange({ poolCode: normalizeCode(event.target.value) })} />
              </label>
              <label className={labelClass}>Selection Strategy
                <select className={inputClass} value={editor.selectionStrategy} onChange={(event) => onChange({ selectionStrategy: event.target.value })}>
                  <option value="LOWEST_LOAD">Lowest current load</option>
                  <option value="WEIGHTED_SCORE">Weighted score</option>
                  <option value="MANUAL_ONLY">Manual assignment only</option>
                </select>
              </label>
            </div>
          </details>
        </section>

        <section className="rounded-3xl border border-slate-200 bg-slate-50 p-5">
          <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
            <div>
              <div className="text-xs font-black uppercase tracking-wide text-slate-500">Pool Members</div>
              <h3 className="mt-1 text-base font-black text-slate-950">Membership and Selection Inputs</h3>
              <p className="mt-1 text-sm leading-6 text-slate-600">Select approved Agents and configure weight, priority, and member status. Runtime eligibility remains authoritative at dispatch time.</p>
            </div>
            <div className="rounded-full bg-white px-3 py-1.5 text-xs font-black text-slate-600">{editor.members.length} members</div>
          </div>
          <div className="mt-4 space-y-3">
            {agents.map((agent) => {
              const selected = selectedAgentIds.has(agent.agentId);
              const member = editor.members.find((row) => row.agentId === agent.agentId);
              return (
                <div key={agent.agentId} className={`rounded-2xl border p-4 ${selected ? 'border-purple-200 bg-white' : 'border-slate-200 bg-white/70'}`}>
                  <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
                    <label className="flex min-w-0 items-start gap-3 text-sm font-black text-slate-900">
                      <input type="checkbox" className="mt-1 h-4 w-4 rounded border-slate-300 text-purple-600" checked={selected} onChange={() => toggleAgent(agent)} />
                      <span className="min-w-0"><span className="block truncate">{agent.agentName ?? agent.agentId}</span><span className="mt-1 block truncate text-xs font-bold text-slate-500">{agent.agentId}</span></span>
                    </label>
                    <div className="flex flex-wrap gap-2"><StatusBadge status={agent.approvalStatus ?? 'UNKNOWN'} /><StatusBadge status={agent.runtimeStatus ?? 'UNKNOWN'} /></div>
                  </div>
                  {selected && member ? (
                    <details className="mt-4 rounded-xl border border-slate-200 bg-slate-50">
                      <summary className="cursor-pointer list-none px-3 py-2 text-xs font-black text-slate-600">Advanced member routing inputs</summary>
                      <div className="grid gap-3 border-t border-slate-200 p-3 sm:grid-cols-4">
                        <label className="text-xs font-black text-slate-700">Member Status
                          <select className={inputClass} value={memberStatus(member.memberStatus)} onChange={(event) => updateMember(agent.agentId, { memberStatus: event.target.value })}>
                            <option value="ACTIVE">Active</option><option value="INACTIVE">Inactive</option><option value="DISABLED">Disabled</option>
                          </select>
                        </label>
                        <label className="text-xs font-black text-slate-700">Weight<input type="number" min={1} className={inputClass} value={member.weight ?? 1} onChange={(event) => updateMember(agent.agentId, { weight: normalizePositiveInteger(event.target.value, 1) })} /></label>
                        <label className="text-xs font-black text-slate-700">Priority<input type="number" min={0} className={inputClass} value={member.priority ?? 100} onChange={(event) => updateMember(agent.agentId, { priority: normalizePriority(event.target.value, 100) })} /></label>
                        <div className="rounded-xl bg-white px-3 py-2 text-xs font-semibold leading-5 text-slate-600">{Object.keys(member.metadata ?? {}).length} metadata fields</div>
                      </div>
                    </details>
                  ) : null}
                </div>
              );
            })}
            {!agents.length ? <div className="rounded-2xl border border-dashed border-slate-300 bg-white p-4 text-sm font-bold text-slate-600">No approved Agents are available. Create and approve an Agent before adding Pool members.</div> : null}
          </div>
        </section>
      </div>
    </AgentPoolContextualDrawer>
  );
}


export function RuleEditorDrawer({
  open,
  editor,
  pools,
  eventTypeOptions,
  objectTypeOptions,
  busy,
  error,
  onChange,
  onClose,
  onSave,
}: Readonly<{
  open: boolean;
  editor: RuleEditorState;
  pools: CoreAgentPoolView[];
  eventTypeOptions: string[];
  objectTypeOptions: string[];
  busy: boolean;
  error?: string | null;
  onChange: (patch: Partial<RuleEditorState>) => void;
  onClose: () => void;
  onSave: () => void;
}>) {
  const dialogRef = useDialogAccessibility(open, onClose);
  if (!open) return null;
  return (
    <div ref={dialogRef} tabIndex={-1} className="fixed inset-0 z-[90] flex justify-end bg-slate-950/60 outline-none" role="dialog" aria-modal="true" aria-label="Classification Rule editor">
      <div className="flex h-full w-full max-w-3xl flex-col overflow-hidden bg-white shadow-2xl">
        <div className="flex items-start justify-between gap-4 border-b border-slate-200 px-6 py-5">
          <div>
            <div className="text-xs font-black uppercase tracking-wide text-purple-700">Classification Rule</div>
            <h2 className="mt-1 text-xl font-black text-slate-950">{editor.ruleId ? 'Edit Classification Rule' : 'Add Classification Rule'}</h2>
            <p className="mt-1 text-sm leading-6 text-slate-600">Create deterministic rules for known work. Unmatched events enter Triage; same-priority overlaps fail closed.</p>
          </div>
          <button type="button" onClick={onClose} className="rounded-xl border border-slate-200 px-3 py-2 text-sm font-black text-slate-600 hover:bg-slate-50" aria-label="Close">×</button>
        </div>
        <div className="flex-1 overflow-y-auto p-6">
          {error ? <div className="mb-4 rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-bold text-rose-900">{error}</div> : null}
          <div className="grid gap-4 md:grid-cols-2">
            <label className={labelClass}>Rule Name
              <input className={inputClass} value={editor.ruleName} onChange={(event) => onChange({ ruleName: event.target.value })} />
            </label>
            <label className={labelClass}>Target Agent Pool
              <select className={inputClass} value={editor.targetPoolId} onChange={(event) => onChange({ targetPoolId: event.target.value })}>
                <option value="">Select Rule target Pool</option>
                {pools.map((pool) => <option key={pool.poolId} value={pool.poolId}>{poolDisplay(pool)}</option>)}
              </select>
            </label>
            <label className={labelClass}>Event Type
              {eventTypeOptions.length ? <select className={inputClass} value={editor.eventType} onChange={(event) => onChange({ eventType: event.target.value })}><option value="">Any configured Event Type</option>{editor.eventType && !eventTypeOptions.includes(editor.eventType) ? <option value={editor.eventType}>{editor.eventType}</option> : null}{eventTypeOptions.map((value) => <option key={value} value={value}>{value}</option>)}</select> : <input className={inputClass} placeholder="No Source contract values are available; enter a value or leave blank" value={editor.eventType} onChange={(event) => onChange({ eventType: event.target.value })} />}
            </label>
            <label className={labelClass}>Object Type
              {objectTypeOptions.length ? <select className={inputClass} value={editor.objectType} onChange={(event) => onChange({ objectType: event.target.value })}><option value="">Any configured Object Type</option>{editor.objectType && !objectTypeOptions.includes(editor.objectType) ? <option value={editor.objectType}>{editor.objectType}</option> : null}{objectTypeOptions.map((value) => <option key={value} value={value}>{value}</option>)}</select> : <input className={inputClass} placeholder="No Source contract values are available; enter a value or leave blank" value={editor.objectType} onChange={(event) => onChange({ objectType: event.target.value })} />}
            </label>
            <label className={labelClass}>Error Code
              <input className={inputClass} placeholder="for example TEMP_HIGH;Leave blank for no restriction" value={editor.errorCode} onChange={(event) => onChange({ errorCode: event.target.value })} />
            </label>
            <label className={labelClass}>Severity
              <select className={inputClass} value={editor.severity} onChange={(event) => onChange({ severity: event.target.value })}>
                <option value="">Any severity</option>
                <option value="CRITICAL">Critical</option>
                <option value="HIGH">High</option>
                <option value="MEDIUM">Medium</option>
                <option value="LOW">Low</option>
                <option value="INFO">Info</option>
              </select>
            </label>
            <label className={labelClass}>External Issue override
              <select className={inputClass} value={editor.issueSyncPolicy} onChange={(event) => onChange({ issueSyncPolicy: event.target.value as RuleEditorState['issueSyncPolicy'] })}>
                <option value="">Inherit Source Flow</option>
                <option value="NONE">Never create</option>
                <option value="OPTIONAL">On Task failure</option>
                <option value="REQUIRED">Always after Task closes</option>
                <option value="MANUAL">Administrator decides</option>
              </select>
            </label>
            <label className="flex items-center gap-2 rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm font-black text-slate-800 md:col-span-2">
              <input type="checkbox" className="h-4 w-4 rounded border-slate-300 text-purple-600" checked={editor.enabled} onChange={(event) => onChange({ enabled: event.target.checked })} />
              Enable this rule
            </label>
          </div>
          <details className="mt-4 rounded-2xl border border-slate-200 bg-slate-50">
            <summary className="cursor-pointer list-none px-4 py-3 text-sm font-black text-slate-700">Advanced rule settings <span className="ml-2 text-xs font-medium text-slate-500">Technical identifier, Task Type and priority</span></summary>
            <div className="grid gap-4 border-t border-slate-200 p-4 md:grid-cols-3">
              <label className={labelClass}>Rule Code
                <input className={inputClass} value={editor.ruleCode} onChange={(event) => onChange({ ruleCode: normalizeCode(event.target.value) })} />
              </label>
              <label className={labelClass}>Task Type / Service Code
                <input className={inputClass} placeholder="Only when the Source contract requires one" value={editor.serviceCode} onChange={(event) => onChange({ serviceCode: normalizeCode(event.target.value) })} />
              </label>
              <label className={labelClass}>Priority
                <input type="number" min={0} className={inputClass} value={editor.priority} onChange={(event) => onChange({ priority: normalizePriority(event.target.value, 100) })} />
              </label>
            </div>
          </details>
        </div>
        <div className="flex flex-col-reverse gap-3 border-t border-slate-200 px-6 py-4 sm:flex-row sm:justify-end">
          <Button onClick={onClose} disabled={busy}>Cancel</Button>
          <Button tone="primary" onClick={onSave} disabled={busy}>{busy ? 'Saving…' : 'Save Rule'}</Button>
        </div>
      </div>
    </div>
  );
}

