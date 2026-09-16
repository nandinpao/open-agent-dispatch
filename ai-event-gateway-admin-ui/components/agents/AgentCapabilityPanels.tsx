'use client';

import Link from 'next/link';
import { type ReactNode } from 'react';
import { CanonicalCapabilityPicker } from '@/components/capabilities/CanonicalCapabilityPicker';
import { StatusBadge } from '@/components/common/StatusBadge';
import { EmptyState } from '@/components/common/EmptyState';
import { useI18n } from '@/hooks/useI18n';
import type { CommandResult } from '@/lib/types/admin';
import type { CoreAgentCapability, CoreAgentCapabilityAssignment, CoreAgentCapabilityCatalog, CoreAgentCapabilityCommand, CoreAgentSetupReadinessResponse, CoreAgentQualityMetricsWindow } from '@/lib/types/core';
import { formatDateTime } from '@/lib/utils/format';
import { useAgentCapabilityDialogController } from '@/components/agents/useAgentCapabilityDialogController';
import type { AgentDetailBundle } from '@/hooks/useAgentDetail';
import { Panel, StatCard, ModalShell, FormTextArea, DialogNotice, activeCapabilityAssignments, buttonBaseClassName, isOperatorRuntimeCapability, normalizeCode, normalizeLifecycleStatus } from '@/components/agents/AgentDetailUi';

type CommandFn<TBody> = (body: TBody) => Promise<CommandResult>;

function numberValue(value: unknown): number | undefined {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) return parsed;
  }
  return undefined;
}

function percentText(value: unknown): string {
  const numeric = numberValue(value);
  if (numeric === undefined) return '-';
  const percent = numeric <= 1 ? numeric * 100 : numeric;
  return `${percent.toFixed(percent >= 10 ? 1 : 2)}%`;
}

function latencyText(value: unknown): string {
  const numeric = numberValue(value);
  if (numeric === undefined) return '-';
  if (numeric >= 1000) return `${(numeric / 1000).toFixed(1)}s`;
  return `${numeric.toFixed(0)}ms`;
}

function latestQualityWindow(windows: CoreAgentQualityMetricsWindow[]): CoreAgentQualityMetricsWindow | undefined {
  return [...windows].sort((left, right) => {
    const leftTime = Date.parse(left.calculatedAt ?? left.windowEnd ?? left.updatedAt ?? left.createdAt ?? '') || 0;
    const rightTime = Date.parse(right.calculatedAt ?? right.windowEnd ?? right.updatedAt ?? right.createdAt ?? '') || 0;
    return rightTime - leftTime;
  })[0];
}

function qualityObservationValue(window: CoreAgentQualityMetricsWindow | undefined, field: keyof CoreAgentQualityMetricsWindow, metadataKey: string): unknown {
  return window?.[field] ?? window?.metadata?.[metadataKey];
}

function qualityResponsibility(window: CoreAgentQualityMetricsWindow | undefined): string {
  const scope = String(window?.responsibilityScope ?? window?.metadata?.responsibilityScope ?? window?.metadata?.qualityResponsibility ?? 'UNKNOWN').toUpperCase();
  if (scope === 'AGENT') return 'Agent-responsible sample';
  if (scope === 'UPSTREAM_PAYLOAD') return 'Upstream payload / source-system responsible';
  if (scope === 'SYSTEM_CONFIGURATION') return 'System configuration responsible';
  return 'Responsibility not classified';
}

export function AgentQualityObservationPanel({ data }: Readonly<{ data: AgentDetailBundle }>) {
  const latest = latestQualityWindow(data.agentQualityWindows ?? []);
  const p95 = qualityObservationValue(latest, 'p95CompletionLatencyMs', 'p95CompletionLatencyMs');
  const retryRate = qualityObservationValue(latest, 'retryRate', 'retryRate');
  const manualReassignmentRate = qualityObservationValue(latest, 'manualReassignmentRate', 'manualReassignmentRate');
  const recentHealthScore = qualityObservationValue(latest, 'recentHealthScore', 'recentHealthScore');
  const minimumSample = Number(latest?.minimumSample ?? latest?.metadata?.minimumSample ?? 30);
  const sampleSize = latest?.sampleSize ?? 0;
  const sampleReady = sampleSize >= minimumSample;
  const decayWindow = String(latest?.decayWindow ?? latest?.metadata?.decayWindow ?? '7d');
  const observationWindow = String(latest?.observationWindow ?? latest?.metricWindow ?? '24h');
  return (
    <Panel
      title="Agent Quality Observation"
      description="Phase 9B quality metrics are observation-only. They help operators understand recent behavior, but Selection Strategy and Runtime Eligibility must not consume this score."
    >
      <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm leading-6 text-amber-950">
        <div className="font-black">Observation-only / no selection impact</div>
        <p className="mt-1">Use P95 latency, ACK timeouts, result failures, and retries to diagnose the Source Flow, Agent Pool, Pool membership, runtime eligibility, and selection strategy. </p>
      </div>
      <div className="mt-4 grid gap-3 md:grid-cols-4">
        <StatCard label="Success Rate" value={percentText(latest?.successRate)} tone={sampleReady ? 'good' : 'warn'} />
        <StatCard label="Avg Completion" value={latencyText(latest?.avgCompletionLatencyMs)} tone="neutral" />
        <StatCard label="P95 Completion" value={latencyText(p95)} tone="neutral" />
        <StatCard label="Health Score" value={recentHealthScore === undefined ? '-' : String(recentHealthScore)} tone="neutral" />
      </div>
      <div className="mt-3 grid gap-3 md:grid-cols-4">
        <StatCard label="ACK Timeout" value={percentText(qualityObservationValue(latest, 'ackTimeoutRate', 'ackTimeoutRate') ?? latest?.timeoutRate)} tone="neutral" />
        <StatCard label="Result Failure" value={percentText(qualityObservationValue(latest, 'resultFailureRate', 'resultFailureRate') ?? latest?.failureRate)} tone="neutral" />
        <StatCard label="Retry Rate" value={percentText(retryRate)} tone="neutral" />
        <StatCard label="Manual Reassign" value={percentText(manualReassignmentRate)} tone="neutral" />
      </div>
      <div className="mt-4 grid gap-3 md:grid-cols-3">
        <MetricLine label="Observation window" value={`${observationWindow} · decay=${decayWindow}`} />
        <MetricLine label="Minimum sample" value={`${sampleSize} / ${minimumSample} ${sampleReady ? 'ready' : 'insufficient sample'}`} />
        <MetricLine label="Responsibility" value={qualityResponsibility(latest)} />
      </div>
      <div className="mt-4 rounded-2xl border border-slate-200 bg-slate-50 p-4 text-xs leading-5 text-slate-600">
        selectionImpact=NONE · observationOnly=true · source={latest?.source ?? 'quality-observation-read-model'} · calculatedAt={formatDateTime(latest?.calculatedAt ?? latest?.updatedAt)}
      </div>
    </Panel>
  );
}

function MetricLine({ label, value }: Readonly<{ label: string; value: ReactNode }>) {
  return <div className="rounded-2xl border border-slate-200 bg-white p-4"><div className="text-xs font-black uppercase tracking-wide text-slate-500">{label}</div><div className="mt-1 text-sm font-black text-slate-900">{value}</div></div>;
}

function metadataText(metadata: Record<string, unknown> | undefined, key: string): string | undefined {
  const value = metadata?.[key];
  return typeof value === 'string' && value.trim().length > 0 ? value.trim() : undefined;
}

function capabilityRegistrySource(assignment: CoreAgentCapabilityAssignment, catalog?: CoreAgentCapabilityCatalog): string {
  return assignment.source || metadataText(assignment.metadata, 'source') || metadataText(catalog?.metadata, 'source') || 'Admin-managed reference';
}

function capabilityRegistryVersion(assignment: CoreAgentCapabilityAssignment, catalog?: CoreAgentCapabilityCatalog): string {
  return metadataText(assignment.metadata, 'capabilityVersion') || metadataText(catalog?.metadata, 'capabilityVersion') || (catalog?.version ? `v${catalog.version}` : '-');
}

function capabilityRegistryCertification(assignment: CoreAgentCapabilityAssignment, catalog?: CoreAgentCapabilityCatalog): string {
  const explicit = assignment.evidenceRef || metadataText(assignment.metadata, 'certificationRef') || metadataText(catalog?.metadata, 'certificationRef');
  if (explicit) return explicit;
  return catalog?.requiresCertification ? 'Certification required' : '-';
}

function capabilityCertificationEvidenceCount(assignments: CoreAgentCapabilityAssignment[], catalogs: CoreAgentCapabilityCatalog[]): number {
  const catalogByCode = new Map(catalogs.map((item) => [normalizeCode(item.capabilityCode), item]));
  return assignments.filter((assignment) => {
    const catalog = catalogByCode.get(normalizeCode(assignment.capabilityCode));
    return Boolean(assignment.evidenceRef || metadataText(assignment.metadata, 'certificationRef') || metadataText(catalog?.metadata, 'certificationRef'));
  }).length;
}

export function CapabilityRegistryPanel({ data }: Readonly<{ data: AgentDetailBundle }>) {
  const catalogByCode = new Map((data.capabilityCatalog ?? []).map((item) => [normalizeCode(item.capabilityCode), item]));
  const rows = activeCapabilityAssignments(data.capabilityAssignments)
    .map((assignment) => ({ assignment, catalog: catalogByCode.get(normalizeCode(assignment.capabilityCode)) }))
    .sort((left, right) => normalizeCode(left.assignment.capabilityCode).localeCompare(normalizeCode(right.assignment.capabilityCode)));
  const runtimeReported = new Set((data.runtimeCapabilityItems ?? []).map((item) => normalizeCode(item.capabilityValue)).filter(Boolean));

  return (
    <Panel
      title="Canonical Capabilities"
      description="Approved Canonical Capability assignments describe WHAT this Agent may provide. A Dispatch Flow can require them; Pool membership, Dispatch Access, credentials, and runtime health remain separate eligibility checks."
    >
      <div className="grid gap-3 md:grid-cols-4">
        <StatCard label="Catalog" value={data.capabilityCatalog?.length ?? 0} tone="neutral" />
        <StatCard label="Assignments" value={rows.length} tone={rows.length ? 'good' : 'warn'} />
        <StatCard label="Runtime Reported" value={runtimeReported.size} tone="neutral" />
        <StatCard label="Certification refs" value={capabilityCertificationEvidenceCount(rows.map((row) => row.assignment), data.capabilityCatalog ?? [])} tone="neutral" />
      </div>
      <div className="mt-4 rounded-2xl border border-indigo-200 bg-indigo-50 p-4 text-sm leading-6 text-indigo-900">
        <div className="font-black">Capability is one eligibility dimension</div>
        <p className="mt-1">A Flow requirement can require an approved Capability. The Agent must still belong to the selected Pool and pass Dispatch Access, credential, connection, capacity, and backoff checks.</p>
      </div>
      <div className="mt-4 overflow-hidden rounded-2xl border border-slate-200">
        <table className="min-w-full divide-y divide-slate-200 text-sm">
          <thead className="bg-slate-50 text-left text-xs font-black uppercase tracking-wide text-slate-500">
            <tr><th className="px-4 py-3">Capability</th><th className="px-4 py-3">Admin Approval</th><th className="px-4 py-3">Source / Version</th><th className="px-4 py-3">Last Reported</th><th className="px-4 py-3">Certification</th><th className="px-4 py-3">Flow Eligibility</th></tr>
          </thead>
          <tbody className="divide-y divide-slate-100 bg-white">
            {rows.map(({ assignment, catalog }) => {
              const code = normalizeCode(assignment.capabilityCode);
              const lastReported = assignment.updatedAt || assignment.approvedAt || assignment.requestedAt || catalog?.updatedAt || '-';
              return (
                <tr key={assignment.assignmentId ?? code}>
                  <td className="px-4 py-3"><div className="font-black text-slate-900">{code}</div><div className="text-xs text-slate-500">{assignment.capabilityName || catalog?.capabilityName || '-'}</div></td>
                  <td className="px-4 py-3"><StatusBadge status={assignment.status ?? 'UNKNOWN'} /></td>
                  <td className="px-4 py-3 text-xs leading-5 text-slate-600"><b>{capabilityRegistrySource(assignment, catalog)}</b><br />{capabilityRegistryVersion(assignment, catalog)}</td>
                  <td className="px-4 py-3 text-xs leading-5 text-slate-600">{lastReported}<br />Runtime observed: {runtimeReported.has(code) ? 'yes' : 'optional / no'}</td>
                  <td className="px-4 py-3 text-xs leading-5 text-slate-600">{capabilityRegistryCertification(assignment, catalog)}</td>
                  <td className="px-4 py-3"><StatusBadge status="CAN_BE_REQUIRED" label="Can be required" title="A Dispatch Flow may require this Canonical Capability." /></td>
                </tr>
              );
            })}
            {!rows.length ? <tr><td colSpan={6} className="px-4 py-4 text-sm font-bold text-slate-500">No active Canonical Capability assignments are available. Use Assign Capability to approve what this Agent can provide.</td></tr> : null}
          </tbody>
        </table>
      </div>
    </Panel>
  );
}

export function CapabilityList({
  assignments,
  profileCapabilities,
  setupReadiness,
  onApprove,
  onSuspend,
  onResume,
  onRevoke,
  onRemove,
  onRevokeAndRemove,
}: Readonly<{
  assignments: CoreAgentCapabilityAssignment[];
  profileCapabilities?: CoreAgentCapability[];
  setupReadiness?: CoreAgentSetupReadinessResponse;
  onApprove: (assignment: CoreAgentCapabilityAssignment) => void;
  onSuspend: (assignment: CoreAgentCapabilityAssignment) => void;
  onResume: (assignment: CoreAgentCapabilityAssignment) => void;
  onRevoke: (assignment: CoreAgentCapabilityAssignment) => void;
  onRemove: (assignment: CoreAgentCapabilityAssignment) => void;
  onRevokeAndRemove: (assignment: CoreAgentCapabilityAssignment) => void;
}>) {
  const { t } = useI18n();
  const runtimeReported = new Set((setupReadiness?.runtimeReportedCapabilities ?? []).map(normalizeCode).filter(isOperatorRuntimeCapability));
  const runtimeObservedOnly = new Set((setupReadiness?.runtimeReportedCapabilities ?? []).map(normalizeCode).filter(isOperatorRuntimeCapability));
  const activeAssignments = activeCapabilityAssignments(assignments);
  const revokedAssignments = assignments
    .filter((assignment) => normalizeLifecycleStatus(assignment.status) === 'REVOKED' && isOperatorRuntimeCapability(assignment.capabilityCode))
    .sort((left, right) => normalizeCode(left.capabilityCode).localeCompare(normalizeCode(right.capabilityCode)));
  // The working capability list is driven by approved Agent Capability assignments only.
  // Other observed values must not resurrect revoked rows or become a hidden dispatch gate.
  const assignmentMap = new Map(activeAssignments.map((assignment) => [normalizeCode(assignment.capabilityCode), assignment]));
  const rowCodes = Array.from(new Set(activeAssignments.map((assignment) => normalizeCode(assignment.capabilityCode)).filter(Boolean))).sort();
  const ignoredNonWorkingCapabilities = [
    ...(profileCapabilities ?? []).map((capability) => normalizeCode(capability.capabilityCode)),
    ...(setupReadiness?.profileCapabilities ?? []).map(normalizeCode),
  ].filter((code) => code && !rowCodes.includes(code));

  if (!rowCodes.length && !revokedAssignments.length) {
    return <EmptyState title={t('agent.detail.empty.capabilities.title')} description={t('agent.detail.empty.capabilities.description')} />;
  }

  return (
    <div className="space-y-4">
      <div className="rounded-2xl border border-indigo-200 bg-indigo-50 p-4 text-sm text-indigo-900">
        <div className="font-black">Canonical Capability assignments</div>
        <p className="mt-1 leading-6">These assignments define WHAT this Agent is approved to provide. A Flow may require one or more Capabilities; Pool membership and runtime eligibility are still required.</p>
        <Link href="/dispatch-flows" className="mt-3 inline-flex rounded-lg border border-indigo-200 bg-white px-3 py-1.5 text-xs font-black text-indigo-700 hover:bg-indigo-50">Open Source Flow / Agent Pool setup</Link>
      </div>

      {rowCodes.length > 0 ? (
        <div className="overflow-hidden rounded-2xl border border-slate-200">
        <table className="min-w-full divide-y divide-slate-200 text-sm">
          <thead className="bg-slate-50 text-left text-xs font-black uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3">{t('agent.detail.table.capability')}</th>
              <th className="px-4 py-3">Core Approval</th>
              <th className="px-4 py-3">Runtime Observation (optional)</th>
              <th className="px-4 py-3">Reference Status</th>
              <th className="px-4 py-3">{t('agent.detail.table.source')}</th>
              <th className="px-4 py-3 text-right">{t('agent.detail.table.actions')}</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {rowCodes.map((code) => {
              const assignment = assignmentMap.get(code);
              const status = assignment?.status ?? 'EXPECTED';
              const coreApproved = assignment?.status === 'APPROVED';
              const reported = runtimeReported.has(code) || runtimeObservedOnly.has(code);
              const dispatchUsable = coreApproved;
              const dispatchStatus = dispatchUsable ? 'READY' : 'NOT_USABLE';
              const assignmentReady = Boolean(assignment?.assignmentId);
              return (
                <tr key={code}>
                  <td className="px-4 py-3">
                    <div className="font-black text-slate-900">{code}</div>
                    <div className="text-xs text-slate-500">{assignment?.capabilityName ?? '-'}</div>
                  </td>
                  <td className="px-4 py-3"><StatusBadge status={coreApproved ? 'APPROVED' : status} /></td>
                  <td className="px-4 py-3"><StatusBadge status={reported ? 'OBSERVED' : 'NOT_OBSERVED'} label={reported ? 'OBSERVED' : 'NOT OBSERVED'} title="Runtime capability observation is optional diagnostics only." /></td>
                  <td className="px-4 py-3"><StatusBadge status={dispatchStatus} label={dispatchUsable ? 'READY' : 'NOT USABLE'} title={dispatchUsable ? 'Admin UI/Core approved; runtime observation is not required.' : 'Capability is not currently approved in Core.'} /></td>
                  <td className="px-4 py-3 text-slate-600">{assignment?.source ?? assignment?.evidenceRef ?? 'Governed assignment'}</td>
                  <td className="px-4 py-3 text-right">
                    {assignment ? (
                      <div className="flex flex-wrap justify-end gap-2">
                        {(status === 'DECLARED' || status === 'PENDING_APPROVAL') ? <button type="button" disabled={!assignmentReady} onClick={() => onApprove(assignment)} className="rounded-lg border border-emerald-200 px-2 py-1 text-xs font-bold text-emerald-700 hover:bg-emerald-50 disabled:opacity-50">{t('agent.detail.action.approve')}</button> : null}
                        {status === 'APPROVED' ? <button type="button" disabled={!assignmentReady} onClick={() => onSuspend(assignment)} className="rounded-lg border border-amber-200 px-2 py-1 text-xs font-bold text-amber-700 hover:bg-amber-50 disabled:opacity-50">{t('agent.detail.action.suspend')}</button> : null}
                        {(status === 'SUSPENDED' || status === 'EXPIRED') ? <button type="button" disabled={!assignmentReady} onClick={() => onResume(assignment)} className="rounded-lg border border-blue-200 px-2 py-1 text-xs font-bold text-blue-700 hover:bg-blue-50 disabled:opacity-50">{t('agent.detail.action.resume')}</button> : null}
                        {(status === 'APPROVED' || status === 'SUSPENDED' || status === 'EXPIRED') ? <button type="button" disabled={!assignmentReady} onClick={() => onRevoke(assignment)} className="rounded-lg border border-rose-200 px-2 py-1 text-xs font-bold text-rose-700 hover:bg-rose-50 disabled:opacity-50">{t('agent.detail.action.revoke')}</button> : null}
                        {(status === 'APPROVED' || status === 'SUSPENDED' || status === 'EXPIRED') ? <button type="button" disabled={!assignmentReady} onClick={() => onRevokeAndRemove(assignment)} className="rounded-lg border border-rose-300 bg-rose-50 px-2 py-1 text-xs font-black text-rose-800 hover:bg-rose-100 disabled:opacity-50">Revoke & remove</button> : null}
                        {(status === 'DECLARED' || status === 'PENDING_APPROVAL' || status === 'REJECTED' || status === 'REVOKED') ? <button type="button" disabled={!assignmentReady} onClick={() => onRemove(assignment)} className="rounded-lg border border-slate-200 px-2 py-1 text-xs font-bold text-slate-700 hover:bg-slate-50 disabled:opacity-50">Remove</button> : null}
                      </div>
                    ) : <span className="text-xs font-semibold text-slate-400">Managed through Agent capabilities</span>}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      ) : (
        <div className="rounded-2xl border border-slate-200 bg-white p-4 text-sm text-slate-500">No active governed capability assignments. Use Assign Capability to add one.</div>
      )}

      {ignoredNonWorkingCapabilities.length > 0 ? (
        <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
          <div className="font-black">Non-working capability values ignored</div>
          <p className="mt-1 leading-6">These legacy/profile-only values are preserved for diagnostics but do not satisfy Task Required Capability eligibility. Only Core APPROVED capability assignments qualify an Agent inside the selected Agent Pool: {Array.from(new Set(ignoredNonWorkingCapabilities)).sort().join(', ')}</p>
        </div>
      ) : null}

      {revokedAssignments.length > 0 ? (
        <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
          <div className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
            <div>
              <div className="text-sm font-black text-slate-900">Revoked capability records</div>
              <p className="mt-1 text-xs leading-5 text-slate-500">These records no longer satisfy Required Capability eligibility. They are retained for audit and diagnostics until removed by an operator.</p>
            </div>
          </div>
          <div className="mt-3 flex flex-wrap gap-2">
            {revokedAssignments.map((assignment) => (
              <button
                key={assignment.assignmentId ?? assignment.capabilityCode}
                type="button"
                disabled={!assignment.assignmentId}
                onClick={() => onRemove(assignment)}
                className="rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-bold text-slate-700 hover:bg-slate-100 disabled:opacity-50"
              >
                Remove {normalizeCode(assignment.capabilityCode)}
              </button>
            ))}
          </div>
        </div>
      ) : null}
    </div>
  );
}

export function QuickCapabilityDialog({
  agentId,
  tenantId,
  open,
  onClose,
  existingCodes,
  requestAgentCapability,
  onChanged,
}: Readonly<{
  agentId: string;
  tenantId: string;
  open: boolean;
  onClose: () => void;
  existingCodes: string[];
  requestAgentCapability: CommandFn<CoreAgentCapabilityCommand>;
  onChanged: () => Promise<void> | void;
}>) {
  const { t } = useI18n();
  const controller = useAgentCapabilityDialogController({ agentId, tenantId, open, existingCodes, requestAgentCapability, onChanged });
  if (!open) return null;
  const { availableCatalog, loading, saving, selectedCode, reason, message, error, setSelectedCode, setReason, setMessage, capabilityCreated } = controller;
  const capabilityCode = selectedCode;

  async function submit() {
    const assigned = await controller.submit(t('agent.detail.validation.selectCapability'));
    if (assigned) setMessage(t('agent.detail.success.capabilityRequested', { capabilityCode: assigned }));
  }

  return (
    <ModalShell title={t('agent.detail.dialog.assignCapability.title')} description={t('agent.detail.dialog.assignCapability.description')} onClose={onClose}>
      <div className="space-y-4">
        {message ? <DialogNotice tone="success">{message}</DialogNotice> : null}
        {error ? <DialogNotice tone="error">{error}</DialogNotice> : null}
        <div className="rounded-2xl border border-blue-200 bg-blue-50 p-4 text-sm leading-6 text-blue-950">
          <div className="font-black">Canonical Capability assignment</div>
          <p className="mt-1">Choose what this Agent is approved to do. Capability is the WHAT contract; Agent Pool membership, Dispatch Access, credentials, and runtime connectivity are evaluated separately.</p>
          <Link href="/settings/capabilities" className="mt-2 inline-flex font-black text-blue-700">Manage the full Capability Catalog →</Link>
        </div>
        <CanonicalCapabilityPicker
          tenantId={tenantId}
          capabilities={availableCatalog}
          value={selectedCode}
          onChange={setSelectedCode}
          disabled={loading || saving}
          label="Capability"
          emptyLabel={loading ? 'Loading capabilities…' : 'Select a capability'}
          onCreated={capabilityCreated}
        />

        <FormTextArea label="Reason" value={reason} onChange={setReason} rows={2} />
        <div className="flex flex-wrap justify-end gap-2 border-t border-slate-100 pt-4">
          <button type="button" onClick={onClose} className={`${buttonBaseClassName} border-slate-200 bg-white text-slate-700 hover:bg-slate-50`}>Close</button>
          <button type="button" onClick={() => void submit()} disabled={saving || !capabilityCode} className="rounded-lg bg-blue-600 px-3 py-2 text-xs font-black text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50">{saving ? 'Saving...' : 'Assign Capability'}</button>
        </div>
      </div>
    </ModalShell>
  );
}


