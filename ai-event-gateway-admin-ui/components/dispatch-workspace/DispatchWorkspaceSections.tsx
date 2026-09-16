'use client';

import Link from 'next/link';
import type { ReactNode } from 'react';
import { StatusBadge } from '@/components/common/StatusBadge';
import { Button } from '@/components/ui/Button';
import { CanonicalCapabilityPicker } from '@/components/capabilities/CanonicalCapabilityPicker';
import type { CoreDispatchFlowView, CoreDispatchSimulationCandidateView, CoreDispatchSimulationResponse, CoreEventIntakeDecisionResponse, CoreAgentPoolView, CoreSourceSystem } from '@/lib/types/core';
import { flowDisplay, isActiveStatus, poolDisplay, ruleConditionSummary, statusTone } from './dispatchWorkspaceModel';
import { WorkspaceStateBlock } from './WorkspaceStateBlock';
import { AdvisoryRecommendationPanel, AdvancedSelectionStrategyContractPanel, CapabilityCandidateLookupPanel, ExplicitCapabilityPolicyPanel } from './DispatchAdvancedGovernancePanels';
import { PoolEditorDrawer, RuleEditorDrawer } from './DispatchWorkspaceDrawers';
import { ruleTargetPool } from './dispatchWorkspaceEditorModel';
import { useDispatchWorkspaceController } from './useDispatchWorkspaceController';

const inputClass = 'mt-1 w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm outline-none transition focus:border-purple-400 focus:ring-2 focus:ring-purple-100';
const labelClass = 'text-sm font-black text-slate-800';

function simulationStatusTone(result?: CoreDispatchSimulationResponse | null): string { if (!result) return 'UNKNOWN'; if (result.status === 'READY') return 'READY'; if (result.status === 'MANUAL_ASSIGNMENT_REQUIRED') return 'MANUAL'; return result.status ?? 'BLOCKED'; }
function candidateSummary(candidate: CoreDispatchSimulationCandidateView): string { const reasons=candidate.blockingReasons?.filter(Boolean) ?? []; if(candidate.selected) return 'Expected selection'; if(candidate.eligible) return 'Eligible candidate'; return reasons.length ? reasons.join(', ') : candidate.reason ?? 'Not eligible'; }

function SectionShell({ title, eyebrow, description, children, actions, state='ready', error, emptyTitle='Nothing is configured here yet', emptyDescription='Complete the preceding setup step or create the required record before continuing.' }: Readonly<{ title:string; eyebrow:string; description?:string; children:ReactNode; actions?:ReactNode; state?:'loading'|'ready'|'empty'|'error'; error?:string|null; emptyTitle?:string; emptyDescription?:string }>) {
  return <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm"><div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between"><div><div className="text-xs font-black uppercase tracking-wide text-purple-700">{eyebrow}</div><h3 className="mt-1 text-lg font-black text-slate-950">{title}</h3>{description ? <p className="mt-1 text-sm leading-6 text-slate-600">{description}</p>:null}</div>{actions?<div className="flex flex-wrap gap-2">{actions}</div>:null}</div><div className="mt-4"><WorkspaceStateBlock state={state} title={emptyTitle} description={emptyDescription} error={error}/>{state==='ready'?children:null}</div></section>;
}
function MetricCard({label,value,detail}:Readonly<{label:string;value:string|number;detail?:string}>){ return <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4"><div className="text-xs font-black text-slate-500">{label}</div><div className="mt-1 break-all text-base font-black text-slate-950">{value}</div>{detail?<div className="mt-1 text-xs font-bold leading-5 text-slate-500">{detail}</div>:null}</div>; }

export function DispatchWorkspaceSections({ tenantId, flow, sourceSystems, pools, sourceLoading, sourceError, poolLoading, poolError, onReload, onSimulationResultChange, onRealTestResultChange }: Readonly<{ tenantId:string; flow:CoreDispatchFlowView|null; sourceSystems:CoreSourceSystem[]; pools:CoreAgentPoolView[]; sourceLoading:boolean; sourceError?:string|null; poolLoading:boolean; poolError?:string|null; onReload:()=>void; onSimulationResultChange?:(result:CoreDispatchSimulationResponse|null)=>void; onRealTestResultChange?:(result:CoreEventIntakeDecisionResponse|null)=>void }>) {
  const controller = useDispatchWorkspaceController({ tenantId, flow, sourceSystems, pools, sourceLoading, sourceError, poolLoading, poolError, onReload, onSimulationResultChange, onRealTestResultChange });
  if (!controller.hasFlow) return <section className="rounded-3xl border border-dashed border-slate-300 bg-white p-8 text-center shadow-sm"><div className="text-xs font-black uppercase tracking-wide text-purple-700">Dispatch Workspace</div><h2 className="mt-2 text-2xl font-black text-slate-950">Select Source Flow</h2><p className="mx-auto mt-2 max-w-2xl text-sm leading-6 text-slate-600">Select or create a Source Flow to configure classification, capability, Agent Pool, readiness and testing.</p></section>;
  const {
    selectedSource, defaultPool, rules, primaryRule, sourceState, poolState, scopedTenantId, agents, busy, message, actionError, poolIntent, poolEditorOpen, poolEditor, ruleEditorOpen, ruleEditor,
    simulationForm, simulationResult, simulationBusy, simulationError, runtimeReadinessResult, runtimeReadinessBusy, runtimeReadinessError, realTestResult, realTestBusy, realTestError, capabilityCatalog, canonicalCapabilities, pendingCapabilityCode,
    capabilityAssignmentsByAgent, qualityByAgent, capabilityLookupLoading, capabilityLookupError, ruleConflicts, ruleConflictError, compatibilityBridge, equivalenceEvidence, equivalenceReadiness, migrationLoading, migrationError, advancedDiagnosticsOpen,
    currentFlow, configurationIssues, simulationIssues, lifecycleActivationIssues, activationReadinessIssues, currentSimulation, simulationReady, runtimeReady, runtimeStatus, configuredEventTypes, configuredObjectTypes, configuredErrorCodes, severityOptions,
    setPendingCapabilityCode, setSimulationForm, setAdvancedDiagnosticsOpen, setPoolEditor, setPoolEditorOpen, setRuleEditor, setRuleEditorOpen, refreshCompatibilityBridge, backfillLegacyEquivalence, currentRequiredCapabilityCodes, addRequiredCapability, removeRequiredCapability, refreshCanonicalCapabilities,
    handleDefaultPoolChange, handleIssueSyncPolicyChange, openCreatePool, openEditPool, savePool, openCreateRule, openEditRule, saveRule, runDispatchSimulation, refreshRuntimeReadiness, runRealTestEvent, setFlowStatus,
  } = controller;
  const effectiveIssueSyncPolicy = String(primaryRule?.issueSyncPolicy || currentFlow.defaultIssueSyncPolicy || 'OPTIONAL').trim().toUpperCase();
  const issuePolicySourceLabel = primaryRule?.issueSyncPolicy ? 'Rule override' : 'Flow default';
  const issuePolicyOutcome = effectiveIssueSyncPolicy === 'REQUIRED'
    ? 'A successfully closed Task is expected to create an external Issue.'
    : effectiveIssueSyncPolicy === 'OPTIONAL'
      ? 'Failure-only policy: a successful Task will intentionally NOT create an external Issue.'
      : effectiveIssueSyncPolicy === 'NONE'
        ? 'External Issue creation is disabled for Tasks on this path.'
        : 'External Issue creation requires an administrator decision.';
  return (
    <div className="space-y-5">
      {message ? <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-bold text-emerald-900">{message}</div> : null}
      {actionError ? <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-bold text-rose-900">{actionError}</div> : null}

      <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <div className="text-xs font-black uppercase tracking-wide text-purple-700">Flow setup status</div>
            <h2 className="mt-1 text-2xl font-black text-slate-950">Set up, preview, activate, then verify the live path</h2>
            <p className="mt-2 text-sm leading-6 text-slate-600">OpenDispatch validates the saved configuration first. Preview is side-effect free. After activation, Core verifies current Agent availability before a live test is allowed.</p>
          </div>
          <Link href="#dispatch-workspace-test" className="rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-black text-slate-700 hover:bg-slate-50">Go to Test & Activate ↓</Link>
        </div>
        <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-4">
          <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4"><div className="flex items-center justify-between gap-2"><div className="text-xs font-black text-slate-500">Setup</div><StatusBadge status={configurationIssues.length ? 'BLOCKED' : 'READY'} /></div><div className="mt-2 text-sm font-bold leading-6 text-slate-700">{configurationIssues.length ? configurationIssues.join('; ') : 'Source, rules and Agent Pool are configured.'}</div></div>
          <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4"><div className="flex items-center justify-between gap-2"><div className="text-xs font-black text-slate-500">Preview</div><StatusBadge status={!simulationResult ? 'NOT_RUN' : simulationReady ? 'READY' : 'BLOCKED'} /></div><div className="mt-2 text-sm font-bold leading-6 text-slate-700">{!simulationResult ? 'Run a safe preview before activation.' : !currentSimulation ? 'Preview is stale because the Flow changed.' : simulationReady ? `Preview passed for Flow v${simulationResult.flowVersion ?? currentFlow.version ?? '-'}.` : simulationResult.blockerReason ?? 'Preview is blocked.'}</div></div>
          <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4"><div className="flex items-center justify-between gap-2"><div className="text-xs font-black text-slate-500">Activation</div><StatusBadge status={isActiveStatus(currentFlow.status) ? 'ACTIVE' : activationReadinessIssues.length ? 'BLOCKED' : 'READY'} /></div><div className="mt-2 text-sm font-bold leading-6 text-slate-700">{isActiveStatus(currentFlow.status) ? 'Flow is active for production intake.' : activationReadinessIssues.length ? activationReadinessIssues.join('; ') : 'Ready for Core to revalidate and activate.'}</div></div>
          <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4"><div className="flex items-center justify-between gap-2"><div className="text-xs font-black text-slate-500">Live readiness</div><StatusBadge status={runtimeStatus} /></div><div className="mt-2 text-sm font-bold leading-6 text-slate-700">{runtimeStatus === 'NOT_ACTIVE' ? 'Activate the Flow before checking the live path.' : runtimeStatus === 'UNKNOWN' ? 'Not checked for the current Flow version.' : runtimeReady ? `${runtimeReadinessResult?.eligibleAgentCount ?? 0} Agent(s) are currently eligible.` : runtimeReadinessResult?.blockerReason ?? 'The live path is blocked.'}</div></div>
        </div>
      </section>

      <SectionShell
        title="Source Flow"
        eyebrow="1 / 5"
        description="Confirm where the work comes from and keep the Flow as Draft until the setup is ready."
        state={sourceState}
        error={sourceError}
        emptyTitle="No Source System is available for this flow"
        emptyDescription="Create or gain access to a Source System, then return here to continue."
        actions={<Button size="xs" onClick={() => setFlowStatus('DRAFT')} disabled={busy || String(currentFlow.status ?? 'DRAFT').toUpperCase() === 'DRAFT'}>Save Draft</Button>}
      >
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <MetricCard label="Flow" value={flowDisplay(currentFlow)} detail={currentFlow.flowCode ?? currentFlow.flowId} />
          <MetricCard label="Source System" value={selectedSource?.displayName ?? currentFlow.sourceSystem ?? 'Not selected'} detail={currentFlow.sourceSystem} />
          <MetricCard label="Status" value={currentFlow.status ?? 'DRAFT'} detail={statusTone(currentFlow.status) === 'READY' ? 'Enabled' : 'Not enabled'} />
          <MetricCard label="Version" value={`v${currentFlow.version ?? '-'}`} detail={currentFlow.updatedAt ? `Updated ${currentFlow.updatedAt}` : 'No update timestamp yet.'} />
        </div>
        <div className="mt-4 rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm leading-6 text-slate-600">{currentFlow.description || 'No Source Flow description has been provided.'}</div>
        <div className="mt-4 grid gap-4 rounded-2xl border border-indigo-200 bg-indigo-50 p-4 md:grid-cols-[minmax(0,1fr)_minmax(260px,360px)] md:items-center">
          <div><div className="text-sm font-black text-indigo-950">External Issue behavior</div><p className="mt-1 text-xs leading-5 text-indigo-900">Canonical Route B policy for Tasks created by this Flow. Rules may override it for specific classifications.</p></div>
          <label className={labelClass}>Default behavior
            <select className={inputClass} value={currentFlow.defaultIssueSyncPolicy ?? 'OPTIONAL'} onChange={(event) => { void handleIssueSyncPolicyChange(event.target.value); }} disabled={busy}>
              <option value="NONE">Never create an Issue</option>
              <option value="OPTIONAL">OPTIONAL — Create only when Task fails</option>
              <option value="REQUIRED">REQUIRED — Always create after Task closes</option>
              <option value="MANUAL">Administrator decides</option>
            </select>
          </label>
        </div>
      </SectionShell>

      <SectionShell
        title="Workload Classification"
        eyebrow="2 / 5"
        description="Define which incoming work belongs to this Flow and which Agent Pool should handle it. Unmatched work enters Triage."
        actions={<Button size="xs" tone="primary" onClick={openCreateRule} disabled={!pools.length}>+ Classification Rule</Button>}
      >
        {ruleConflictError ? <div className="mb-3 rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm text-amber-950">{ruleConflictError}</div> : null}
        {ruleConflicts.length > 0 ? <div className="mb-3 rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-950"><strong>Overlapping rules need attention.</strong> {ruleConflicts.length} same-priority overlap{ruleConflicts.length === 1 ? '' : 's'} must be resolved before activation.</div> : null}
        <div className="overflow-hidden rounded-2xl border border-slate-200">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50 text-left text-xs font-black uppercase tracking-wide text-slate-500">
              <tr><th className="px-4 py-3">Priority</th><th className="px-4 py-3">When</th><th className="px-4 py-3">Send to</th><th className="px-4 py-3">Issue behavior</th><th className="px-4 py-3">Status</th><th className="px-4 py-3">Actions</th></tr>
            </thead>
            <tbody className="divide-y divide-slate-100 bg-white">
              {rules.map((rule) => (
                <tr key={rule.ruleId ?? rule.ruleCode ?? `${rule.priority}-${rule.eventType}`}>
                  <td className="px-4 py-3 font-black text-slate-900">{rule.priority ?? 100}</td>
                  <td className="px-4 py-3 font-bold text-slate-700">{ruleConditionSummary(rule)}</td>
                  <td className="px-4 py-3 font-bold text-slate-700">{poolDisplay(ruleTargetPool(rule, pools), rule.targetPoolCode ?? rule.targetPoolId ?? currentFlow.defaultPoolId)}</td>
                  <td className="px-4 py-3 text-xs font-bold text-slate-600">{rule.issueSyncPolicy || `Inherit (${currentFlow.defaultIssueSyncPolicy ?? 'OPTIONAL'})`}</td>
                  <td className="px-4 py-3"><StatusBadge status={rule.enabled === false ? 'DISABLED' : 'ACTIVE'} /></td>
                  <td className="px-4 py-3"><Button size="xs" onClick={() => openEditRule(rule)}>Edit</Button></td>
                </tr>
              ))}
              {!rules.length ? <tr><td colSpan={6} className="px-4 py-5 text-sm font-bold text-slate-500">No explicit classification rule yet. Add a rule for known work; unmatched work remains in Triage.</td></tr> : null}
            </tbody>
          </table>
        </div>
      </SectionShell>

      <div id="dispatch-workspace-capabilities"><SectionShell
        title="Required Capability"
        eyebrow="3 / 5"
        description="Choose what an Agent must be able to do. At least one Canonical Required Capability is needed for the normal Dispatch workflow; Pool membership only defines where to search for qualified Agents."
      >
        <div className="rounded-2xl border border-blue-200 bg-blue-50 p-4">
          <div className="flex flex-col gap-2 lg:flex-row lg:items-start lg:justify-between">
            <div>
              <div className="text-sm font-black text-blue-950">What must the assigned Agent be able to do?</div>
              <p className="mt-1 text-xs leading-5 text-blue-900">Capabilities are separate from Dispatch Access and live runtime eligibility.</p>
            </div>
            <Link href="/settings/capabilities" className="text-xs font-black text-blue-700 hover:underline">Manage full catalog →</Link>
          </div>
          <div className="mt-4 grid gap-3 lg:grid-cols-[minmax(0,1fr)_auto]">
            <CanonicalCapabilityPicker
              tenantId={scopedTenantId}
              capabilities={canonicalCapabilities}
              value={pendingCapabilityCode}
              onChange={setPendingCapabilityCode}
              onCreated={refreshCanonicalCapabilities}
              disabled={busy}
              label="Capability"
              emptyLabel="Select an ACTIVE capability"
            />
            <div className="flex items-end"><Button tone="primary" onClick={() => { void addRequiredCapability(); }} disabled={busy || !pendingCapabilityCode}>Add</Button></div>
          </div>
          <div className="mt-4 flex flex-wrap gap-2">
            {currentRequiredCapabilityCodes().map((code) => {
              const definition = canonicalCapabilities.find((item) => item.capabilityCode.toLowerCase() === code.toLowerCase());
              return <span key={code} className="inline-flex items-center gap-2 rounded-full border border-blue-200 bg-white px-3 py-2 text-xs font-black text-blue-900"><span>{definition?.displayName ?? code}</span><button type="button" onClick={() => { void removeRequiredCapability(code); }} disabled={busy} className="rounded-full px-1 text-blue-500 hover:bg-blue-100 hover:text-blue-900 disabled:opacity-50" aria-label={`Remove ${definition?.displayName ?? code}`}>×</button></span>;
            })}
            {!currentRequiredCapabilityCodes().length ? <span className="text-xs font-bold text-blue-800">No required capability is configured.</span> : null}
          </div>
        </div>
      </SectionShell></div>

      <SectionShell
        title="Agent Pool"
        eyebrow="4 / 5"
        description="Choose the default Pool and manage which approved Agents may be considered for this Flow. Core still rechecks live eligibility at dispatch time."
        state={poolState}
        error={poolError}
        emptyTitle="No Agent Pool is ready"
        emptyDescription="Create an Agent Pool and add approved Agents before testing dispatch."
        actions={<><Button size="xs" onClick={() => openCreatePool('defaultPool')}>+ Agent Pool</Button>{defaultPool ? <Button size="xs" onClick={() => openEditPool(defaultPool, 'memberDrawer')}>Manage Members</Button> : null}</>}
      >
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
          <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
            <label className={labelClass}>Default Agent Pool
              <select className={inputClass} value={currentFlow.defaultPoolId ?? ''} onChange={(event) => { void handleDefaultPoolChange(event.target.value); }} disabled={busy}>
                <option value="">Select an Agent Pool</option>
                {pools.map((pool) => <option key={pool.poolId} value={pool.poolId}>{poolDisplay(pool)}</option>)}
              </select>
            </label>
          </div>
          <MetricCard label="Members" value={defaultPool?.memberCount ?? defaultPool?.members?.length ?? 0} detail="Approved Agents in this Pool" />
          <MetricCard label="Available now" value={defaultPool?.availableAgentCount ?? 0} detail="Current Core/runtime evidence" />
          <MetricCard label="Pool status" value={defaultPool?.status ?? (currentFlow.defaultPoolId ? 'UNKNOWN' : 'NOT_CONFIGURED')} detail={defaultPool ? poolDisplay(defaultPool) : 'Select or create a Pool'} />
        </div>
        <div className="mt-4 rounded-2xl border border-blue-200 bg-blue-50 p-4 text-sm leading-6 text-blue-900">Normal setup only requires Pool membership. Routing strategy, candidate scoring evidence and migration history are available under Engineering Diagnostics below.</div>
      </SectionShell>

      <div id="dispatch-workspace-test">
        <SectionShell title="Test and Activate" eyebrow="5 / 5" description="Preview the saved Flow without side effects, activate it, verify current live readiness, then send one governed test event.">
          {lifecycleActivationIssues.length ? <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm font-bold text-amber-950">{lifecycleActivationIssues.join('; ')} You can still run a safe preview.</div> : null}
          <div className="mt-4 grid gap-4 md:grid-cols-2">
            <label className={labelClass}>Event Type
              <select className={inputClass} value={simulationForm.eventType} onChange={(event) => setSimulationForm((current) => ({ ...current, eventType: event.target.value }))}>
                <option value="">Use saved Flow/default</option>
                {simulationForm.eventType && !configuredEventTypes.includes(simulationForm.eventType) ? <option value={simulationForm.eventType}>{simulationForm.eventType}</option> : null}
                {configuredEventTypes.map((value) => <option key={value} value={value}>{value}</option>)}
              </select>
            </label>
            <label className={labelClass}>Object Type
              <select className={inputClass} value={simulationForm.objectType} onChange={(event) => setSimulationForm((current) => ({ ...current, objectType: event.target.value }))}>
                <option value="">No restriction</option>
                {simulationForm.objectType && !configuredObjectTypes.includes(simulationForm.objectType) ? <option value={simulationForm.objectType}>{simulationForm.objectType}</option> : null}
                {configuredObjectTypes.map((value) => <option key={value} value={value}>{value}</option>)}
              </select>
            </label>
            <label className={labelClass}>Error Code
              <select className={inputClass} value={simulationForm.errorCode} onChange={(event) => setSimulationForm((current) => ({ ...current, errorCode: event.target.value }))}>
                <option value="">No restriction</option>
                {simulationForm.errorCode && !configuredErrorCodes.includes(simulationForm.errorCode) ? <option value={simulationForm.errorCode}>{simulationForm.errorCode}</option> : null}
                {configuredErrorCodes.map((value) => <option key={value} value={value}>{value}</option>)}
              </select>
            </label>
            <label className={labelClass}>Severity
              <select className={inputClass} value={simulationForm.severity} onChange={(event) => setSimulationForm((current) => ({ ...current, severity: event.target.value }))}>
                <option value="">No restriction</option>
                {simulationForm.severity && !severityOptions.includes(simulationForm.severity) ? <option value={simulationForm.severity}>{simulationForm.severity}</option> : null}
                {severityOptions.map((value) => <option key={value} value={value}>{value}</option>)}
              </select>
            </label>
          </div>
          <details className="mt-4 rounded-2xl border border-slate-200 bg-slate-50">
            <summary className="cursor-pointer list-none px-4 py-3 text-sm font-black text-slate-700">Advanced test payload <span className="ml-2 text-xs font-medium text-slate-500">Custom attributes only</span></summary>
            <div className="border-t border-slate-200 p-4"><label className={labelClass}>Attributes JSON<textarea className={`${inputClass} font-mono`} rows={4} value={simulationForm.attributesJson} onChange={(event) => setSimulationForm((current) => ({ ...current, attributesJson: event.target.value }))} /></label></div>
          </details>
          {simulationError ? <div className="mt-4 rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-bold text-rose-900">{simulationError}</div> : null}
          {simulationResult ? (
            <div className="mt-5 space-y-4 rounded-3xl border border-slate-200 bg-white p-5">
              <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                <div><div className="text-xs font-black uppercase tracking-wide text-purple-700">Preview result</div><h3 className="mt-1 text-xl font-black text-slate-950">{simulationResult.summary ?? 'Dispatch preview completed'}</h3><p className="mt-2 text-sm leading-6 text-slate-600">No Task, Assignment or delivery is created by this preview.</p></div>
                <StatusBadge status={simulationStatusTone(simulationResult)} />
              </div>
              <div className="grid gap-3 md:grid-cols-3">
                <MetricCard label="Matched rule" value={simulationResult.matchedRuleId ?? 'NO_MATCH'} detail={simulationResult.matchedRuleId ? 'Saved classification result' : 'Unmatched work enters Triage'} />
                <MetricCard label="Agent Pool" value={simulationResult.targetPoolCode ?? simulationResult.targetPoolId ?? 'Not resolved'} detail="Pool selected for this preview" />
                <MetricCard label="Eligible Agents" value={simulationResult.eligibleAgentCount ?? 0} detail={`Expected Agent: ${simulationResult.selectedAgentId ?? 'None'}`} />
              </div>
              {simulationResult.blockerCode ? <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm leading-6 text-amber-900"><b>{simulationResult.blockerCode}</b>: {simulationResult.blockerReason}</div> : null}
              <details className="rounded-2xl border border-slate-200 bg-slate-50">
                <summary className="cursor-pointer list-none px-4 py-3 text-sm font-black text-slate-700">Candidate diagnostics <span className="ml-2 text-xs font-medium text-slate-500">Scores and blocker evidence</span></summary>
                <div className="overflow-hidden border-t border-slate-200">
                  <table className="min-w-full divide-y divide-slate-200 text-sm"><thead className="bg-slate-50 text-left text-xs font-black uppercase tracking-wide text-slate-500"><tr><th className="px-4 py-3">Agent</th><th className="px-4 py-3">Status</th><th className="px-4 py-3">Score</th><th className="px-4 py-3">Result</th></tr></thead><tbody className="divide-y divide-slate-100 bg-white">
                    {(simulationResult.candidateEvidence ?? []).map((candidate) => <tr key={`candidate-${candidate.agentId}`}><td className="px-4 py-3 font-black text-slate-900">{candidate.agentId}</td><td className="px-4 py-3 font-bold text-slate-700">{candidate.status ?? '-'}</td><td className="px-4 py-3 font-bold text-slate-700">{candidate.score ?? '-'}</td><td className="px-4 py-3 font-bold text-slate-700">{candidateSummary(candidate)}</td></tr>)}
                    {(simulationResult.blockedCandidates ?? []).map((candidate) => <tr key={`blocked-${candidate.agentId}-${candidateSummary(candidate)}`} className="bg-rose-50"><td className="px-4 py-3 font-black text-rose-900">{candidate.agentId}</td><td className="px-4 py-3 font-bold text-rose-800">Blocked</td><td className="px-4 py-3 font-bold text-rose-800">-</td><td className="px-4 py-3 font-bold text-rose-800">{candidateSummary(candidate)}</td></tr>)}
                    {!(simulationResult.candidateEvidence?.length || simulationResult.blockedCandidates?.length) ? <tr><td className="px-4 py-4 text-sm font-bold text-slate-500" colSpan={4}>No candidate diagnostics are available.</td></tr> : null}
                  </tbody></table>
                </div>
              </details>
            </div>
          ) : null}

          <div className="mt-5 rounded-3xl border border-cyan-200 bg-cyan-50 p-5">
            <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between"><div><div className="text-xs font-black uppercase tracking-wide text-cyan-700">Live readiness</div><h3 className="mt-1 text-lg font-black text-slate-950">Can this Flow execute right now?</h3><p className="mt-2 text-sm leading-6 text-cyan-900">Core checks the active Flow against current Agent eligibility. The browser does not calculate a READY result.</p></div><StatusBadge status={runtimeStatus} /></div>
            {runtimeReadinessError ? <div className="mt-4 rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-bold text-rose-900">{runtimeReadinessError}</div> : null}
            {runtimeReadinessResult ? <div className="mt-4 grid gap-3 md:grid-cols-3"><MetricCard label="Matched rule" value={runtimeReadinessResult.matchedRuleId ?? 'NO_MATCH'} detail={runtimeReadinessResult.resolutionType ?? '-'} /><MetricCard label="Eligible Agents" value={runtimeReadinessResult.eligibleAgentCount ?? 0} detail={`Selected: ${runtimeReadinessResult.selectedAgentId ?? 'None'}`} /><MetricCard label="Result" value={runtimeReady ? 'READY' : 'BLOCKED'} detail={runtimeReadinessResult.blockerReason ?? runtimeReadinessResult.summary ?? '-'} /></div> : null}
          </div>

          <div id="dispatch-workspace-real-test" className="mt-5 rounded-3xl border border-amber-200 bg-amber-50 p-5">
            <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between"><div><div className="text-xs font-black uppercase tracking-wide text-amber-700">Live test event</div><h3 className="mt-1 text-lg font-black text-slate-950">Send one governed event through the active intake path</h3><p className="mt-2 text-sm leading-6 text-amber-900">Available only after activation and a fresh Live Readiness result. This test may create real Task, Assignment and Delivery evidence.</p></div>{realTestResult ? <StatusBadge status={realTestResult.taskCreated ? 'TASK_CREATED' : realTestResult.decisionType ?? 'SENT'} /> : null}</div>
            <div className="mt-4 rounded-2xl border border-amber-300 bg-white/70 p-4 text-sm leading-6 text-amber-950">
              <div className="font-black">External Issue preflight: {effectiveIssueSyncPolicy} <span className="font-bold text-amber-700">({issuePolicySourceLabel})</span></div>
              <div className="mt-1 font-bold">{issuePolicyOutcome}</div>
              {effectiveIssueSyncPolicy !== 'REQUIRED' ? <div className="mt-1 text-xs font-bold text-amber-800">If this test is intended to prove Issue creation after a successful Task, change the Flow or selected Rule policy to REQUIRED before sending it.</div> : null}
            </div>
            {realTestError ? <div className="mt-4 rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-bold text-rose-900">{realTestError}</div> : null}
            {realTestResult ? <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-4"><MetricCard label="Event" value={realTestResult.eventId ?? 'Not returned'} detail={realTestResult.decisionType ?? realTestResult.reason ?? '-'} /><MetricCard label="Task" value={realTestResult.taskId ?? 'Not created'} detail={realTestResult.taskCreated ? 'Production Task created' : 'Review the setup and try again.'} /><MetricCard label="Assignment" value={realTestResult.assignmentId ?? 'Not created'} detail={realTestResult.selectedAgentId ? `Agent: ${realTestResult.selectedAgentId}` : realTestResult.primaryReasonCode ?? '-'} /><MetricCard label="Delivery" value={realTestResult.dispatchRequestId ?? 'Not created'} detail={realTestResult.dispatchRequestCreated ? 'Delivery request created' : realTestResult.nextAction ?? '-'} /></div> : null}
          </div>

          <div className="mt-4 flex flex-wrap gap-2">
            <Button tone="primary" onClick={() => void runDispatchSimulation()} disabled={simulationBusy || busy || simulationIssues.length > 0}>{simulationBusy ? 'Running Preview…' : '1. Run Safe Preview'}</Button>
            <Button tone="primary" onClick={() => setFlowStatus('ACTIVE')} disabled={busy || isActiveStatus(currentFlow.status) || activationReadinessIssues.length > 0}>{isActiveStatus(currentFlow.status) ? '2. Flow Active' : '2. Activate Flow'}</Button>
            <Button onClick={() => void refreshRuntimeReadiness()} disabled={runtimeReadinessBusy || busy || !isActiveStatus(currentFlow.status)}>{runtimeReadinessBusy ? 'Checking…' : '3. Check Live Readiness'}</Button>
            <Button tone="warning" onClick={() => void runRealTestEvent()} disabled={realTestBusy || busy || !isActiveStatus(currentFlow.status) || !runtimeReady}>{realTestBusy ? 'Sending Test…' : '4. Send Live Test'}</Button>
            <Link href={`/tasks${currentFlow.flowId ? `?flowId=${encodeURIComponent(currentFlow.flowId)}` : ''}`} className="rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-bold text-slate-700 shadow-sm hover:bg-slate-50">View Tasks</Link>
          </div>
        </SectionShell>
      </div>

      <details
        className="rounded-3xl border border-slate-200 bg-slate-50 shadow-sm"
        open={advancedDiagnosticsOpen}
        onToggle={(event) => setAdvancedDiagnosticsOpen(event.currentTarget.open)}
      >
        <summary className="cursor-pointer list-none px-5 py-4">
          <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between"><div><span className="text-sm font-black text-slate-900">Engineering Diagnostics</span><span className="ml-2 text-xs font-medium text-slate-500">Routing internals, candidate evidence and migration history</span></div><span className="text-xs font-black text-slate-500">Advanced</span></div>
        </summary>
        <div className="space-y-5 border-t border-slate-200 bg-white p-5">
          <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
            <div className="text-xs font-black uppercase tracking-wide text-slate-500">Routing engine values</div>
            <div className="mt-3 grid gap-3 md:grid-cols-2"><MetricCard label="Selection Strategy" value={currentFlow.defaultRoutingStrategy ?? defaultPool?.selectionStrategy ?? primaryRule?.routingStrategy ?? 'LOWEST_LOAD'} detail="Core routing strategy; normal setup does not need to change this." /><MetricCard label="Candidate Source" value={currentFlow.defaultCandidatePoolMode ?? primaryRule?.candidatePoolMode ?? 'AGENT_POOL'} detail="Technical candidate source used by Core." /></div>
          </div>
          <CapabilityCandidateLookupPanel agents={agents} selectedPool={defaultPool} capabilityCatalog={capabilityCatalog} assignmentsByAgent={capabilityAssignmentsByAgent} qualityByAgent={qualityByAgent} loading={capabilityLookupLoading} error={capabilityLookupError} />
          <ExplicitCapabilityPolicyPanel selectedPool={defaultPool} scopedTenantId={scopedTenantId} capabilityCatalog={canonicalCapabilities} />
          <AdvisoryRecommendationPanel selectedPool={defaultPool} scopedTenantId={scopedTenantId} />
          <AdvancedSelectionStrategyContractPanel />

          <section className="rounded-2xl border border-indigo-200 bg-indigo-50 p-4">
            <div className="text-xs font-black uppercase tracking-wide text-indigo-700">Historical migration evidence</div>
            <h3 className="mt-1 text-base font-black text-indigo-950">Compatibility and equivalence diagnostics</h3>
            <p className="mt-1 text-sm leading-6 text-indigo-900">Historical bridge/equivalence records are diagnostics only. They cannot create Assignment, DispatchRequest or production side effects.</p>
            {migrationError ? <div className="mt-4 rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-bold text-rose-900">{migrationError}</div> : null}
            <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-4"><MetricCard label="Migration state" value={equivalenceReadiness?.migrationState ?? 'Not evaluated'} detail={equivalenceReadiness?.recommendedState ?? 'Historical evidence'} /><MetricCard label="Bridge ready / blocked" value={`${compatibilityBridge.filter((row) => row.bridgeStatus === 'READY').length} / ${equivalenceReadiness?.blockedBridgeRows ?? 0}`} detail={`Revision ${equivalenceReadiness?.compatibilityBridgeRevision ?? 0}`} /><MetricCard label="Selected executor equivalence" value={`${Math.round((equivalenceReadiness?.selectedEquivalenceRate ?? 0) * 10000) / 100}%`} detail={`${equivalenceReadiness?.selectedExecutorEquivalentCount ?? 0} / ${equivalenceReadiness?.sampleSize ?? 0} samples`} /><MetricCard label="Exact candidate equivalence" value={`${equivalenceReadiness?.exactEquivalenceCount ?? 0}`} detail={`Minimum samples ${equivalenceReadiness?.minimumSampleSize ?? 0}`} /></div>
            {(equivalenceReadiness?.blockers ?? []).length ? <div className="mt-4 rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm font-bold text-amber-950">Blockers: {(equivalenceReadiness?.blockers ?? []).join(', ')}</div> : null}
            <div className="mt-4 flex flex-wrap gap-2"><Button onClick={() => void refreshCompatibilityBridge()} disabled={migrationLoading}>{migrationLoading ? 'Working…' : 'Refresh historical bridge'}</Button><Button onClick={() => void backfillLegacyEquivalence()} disabled={migrationLoading}>{migrationLoading ? 'Working…' : 'Queue historical evidence'}</Button></div>
            <div className="mt-5 overflow-hidden rounded-2xl border border-indigo-200 bg-white"><table className="min-w-full divide-y divide-slate-200 text-sm"><thead className="bg-slate-50 text-left text-xs font-black uppercase tracking-wide text-slate-500"><tr><th className="px-4 py-3">Historical Agent</th><th className="px-4 py-3">Legacy Skill</th><th className="px-4 py-3">Canonical Capability</th><th className="px-4 py-3">Bridge</th></tr></thead><tbody className="divide-y divide-slate-100 bg-white">{compatibilityBridge.slice(0, 50).map((row) => <tr key={row.bridgeId ?? `${row.flowAgentAssignmentId}-${row.legacySkillCode}`}><td className="px-4 py-3 font-black text-slate-900">{row.agentId ?? '-'}</td><td className="px-4 py-3 font-mono text-xs text-slate-700">{row.legacySkillCode ?? '-'}</td><td className="px-4 py-3 font-mono text-xs text-slate-700">{row.capabilityCode ?? 'UNMAPPED'}</td><td className="px-4 py-3"><StatusBadge status={row.bridgeStatus ?? 'UNKNOWN'} /></td></tr>)}{!compatibilityBridge.length ? <tr><td colSpan={4} className="px-4 py-4 text-sm font-bold text-slate-500">No historical bridge rows are loaded.</td></tr> : null}</tbody></table></div>
            {equivalenceEvidence.length ? <div className="mt-4 text-xs font-bold text-indigo-800">Latest evidence: {equivalenceEvidence[0]?.comparisonResult ?? '-'} · Task {equivalenceEvidence[0]?.taskId ?? '-'} · selected executor preserved={String(equivalenceEvidence[0]?.selectedExecutorEquivalent ?? false)}</div> : null}
          </section>
        </div>
      </details>

      <PoolEditorDrawer
        open={poolEditorOpen}
        intent={poolIntent}
        editor={poolEditor}
        sourceSystems={sourceSystems}
        agents={agents}
        busy={busy}
        error={actionError}
        onChange={(patch) => setPoolEditor((current) => ({ ...current, ...patch }))}
        onClose={() => setPoolEditorOpen(false)}
        onSave={() => void savePool()}
      />
      <RuleEditorDrawer
        open={ruleEditorOpen}
        editor={ruleEditor}
        pools={pools}
        eventTypeOptions={configuredEventTypes}
        objectTypeOptions={configuredObjectTypes}
        busy={busy}
        error={actionError}
        onChange={(patch) => setRuleEditor((current) => ({ ...current, ...patch }))}
        onClose={() => setRuleEditorOpen(false)}
        onSave={() => void saveRule()}
      />
    </div>
  );
}
