'use client';

import { useEffect, useMemo, useState, type ReactNode } from 'react';
import Link from 'next/link';
import { AdminTabLayout, type AdminTabItem } from '@/components/layout/AdminTabLayout';
import { AgentCredentialIssueDialog } from '@/components/agents/AgentCredentialIssueDialog';
import { AgentEnrollmentReviewDialog } from '@/components/agents/AgentEnrollmentReviewDialog';
import { AgentProfileEditDialog } from '@/components/agents/AgentProfileEditDialog';
import { DispatchAssignmentEvidencePanel } from '@/components/dispatch-evidence/DispatchAssignmentEvidencePanel';
import { CommandMessage } from '@/components/common/CommandMessage';
import { DataSourceBadge, dataSourceKindFromFlags } from '@/components/common/DataSourceBadge';
import { EmptyState } from '@/components/common/EmptyState';
import { ErrorBox } from '@/components/common/ErrorBox';
import { LiveDataUnavailable } from '@/components/common/LiveDataUnavailable';
import { LoadingBox } from '@/components/common/LoadingBox';
import { RefreshButton } from '@/components/common/RefreshButton';
import { StatusBadge } from '@/components/common/StatusBadge';
import { useAgentDetail, type AgentDetailBundle } from '@/hooks/useAgentDetail';
import { useI18n } from '@/hooks/useI18n';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import type { CommandResult } from '@/lib/types/admin';
import type { CoreAgentCapability, CoreAgentCapabilityAssignment, CoreAgentCapabilityCatalog, CoreAgentCapabilityCommand, CoreAgentProfile, CoreAgentSetupReadinessResponse, CoreAgentConnectionRepairAction, CoreAgentConnectionRepairActionResult, CoreAgentRuntimeBinding, CoreTaskRuntimeView } from '@/lib/types/core';
import { formatDateTime } from '@/lib/utils/format';

type AgentDetailTab = 'overview' | 'connection' | 'capabilities' | 'flows' | 'tasks' | 'advanced';

type SetupStep = {
  id: string;
  label: string;
  complete: boolean;
  actionLabel: string;
  tab: AgentDetailTab;
  description: string;
};

type CommandFn<TBody> = (body: TBody) => Promise<CommandResult>;

const buttonBaseClassName = 'rounded-lg border px-3 py-2 text-xs font-black disabled:cursor-not-allowed disabled:opacity-50';

function normalizeCode(value: string): string {
  return value.trim().toUpperCase().replace(/[^A-Z0-9_]/g, '_').replace(/^_+|_+$/g, '');
}

function normalizeLifecycleStatus(value?: string | null): string {
  return (value ?? '').trim().toUpperCase();
}

const NON_DISPATCH_TASK_ALIAS_CAPABILITIES = new Set(['INCIDENT_RESPONSE', 'INCIDENT_ESCALATION', 'INCIDENT_ANALYSIS']);

function isOperatorRuntimeCapability(value?: string | null): boolean {
  const code = normalizeCode(value ?? '');
  return code.length > 0 && !NON_DISPATCH_TASK_ALIAS_CAPABILITIES.has(code);
}

function activeCapabilityAssignments(assignments: CoreAgentCapabilityAssignment[]): CoreAgentCapabilityAssignment[] {
  return assignments.filter((assignment) => normalizeLifecycleStatus(assignment.status) !== 'REVOKED' && isOperatorRuntimeCapability(assignment.capabilityCode));
}

function firstNonBlankValue(...values: Array<string | undefined | null>): string | undefined {
  return values.find((value) => typeof value === 'string' && value.trim().length > 0)?.trim();
}

function agentReturnHref(agentId: string): string {
  return `/agents/${encodeURIComponent(agentId)}`;
}

function managementHref(path: string, agentId: string): string {
  const params = new URLSearchParams({ returnTo: agentReturnHref(agentId), agentId });
  return `${path}?${params.toString()}`;
}

interface AgentDetailProductViewProps {
  agentId: string;
}

function Panel({ title, description, children, action }: Readonly<{ title: ReactNode; description?: ReactNode; children: ReactNode; action?: ReactNode }>) {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="mb-4 flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <h2 className="text-base font-bold text-slate-900">{title}</h2>
          {description ? <p className="mt-1 text-sm leading-6 text-slate-500">{description}</p> : null}
        </div>
        {action ? <div className="shrink-0">{action}</div> : null}
      </div>
      {children}
    </section>
  );
}

function StatCard({ label, value, tone = 'neutral' }: Readonly<{ label: string; value: ReactNode; tone?: 'neutral' | 'good' | 'warn' | 'bad' }>) {
  const toneClassName = {
    neutral: 'border-slate-200 bg-slate-50 text-slate-900',
    good: 'border-emerald-200 bg-emerald-50 text-emerald-950',
    warn: 'border-amber-200 bg-amber-50 text-amber-950',
    bad: 'border-rose-200 bg-rose-50 text-rose-950',
  }[tone];
  return (
    <div className={`rounded-2xl border px-4 py-3 ${toneClassName}`}>
      <div className="text-xs font-black uppercase tracking-wide opacity-60">{label}</div>
      <div className="mt-2 break-all text-sm font-black">{value}</div>
    </div>
  );
}

function InlineManageLink({ href, children }: Readonly<{ href: string; children: ReactNode }>) {
  return (
    <Link href={href} className="rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-bold text-slate-700 hover:bg-slate-50">
      {children}
    </Link>
  );
}

function ModalShell({ title, description, children, onClose }: Readonly<{ title: string; description: string; children: ReactNode; onClose: () => void }>) {
  const { t } = useI18n();
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/50 p-4">
      <div className="max-h-[90vh] w-full max-w-3xl overflow-auto rounded-3xl bg-white p-6 shadow-2xl">
        <div className="flex items-start justify-between gap-4 border-b border-slate-100 pb-4">
          <div>
            <h2 className="text-lg font-black text-slate-950">{title}</h2>
            <p className="mt-1 text-sm leading-6 text-slate-500">{description}</p>
          </div>
          <button type="button" onClick={onClose} className="rounded-full border border-slate-200 px-3 py-1 text-sm font-black text-slate-500 hover:bg-slate-50" aria-label={t('agent.detail.dialog.close')}>
            ×
          </button>
        </div>
        <div className="mt-5">{children}</div>
      </div>
    </div>
  );
}

function FormInput({ label, value, onChange, placeholder }: Readonly<{ label: string; value: string; onChange: (value: string) => void; placeholder?: string }>) {
  return (
    <label className="text-sm font-bold text-slate-700">
      {label}
      <input value={value} onChange={(event) => onChange(event.target.value)} placeholder={placeholder} className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2 text-sm font-normal text-slate-800 placeholder:text-slate-300" />
    </label>
  );
}

function FormTextArea({ label, value, onChange, placeholder, rows = 3 }: Readonly<{ label: string; value: string; onChange: (value: string) => void; placeholder?: string; rows?: number }>) {
  return (
    <label className="text-sm font-bold text-slate-700">
      {label}
      <textarea value={value} onChange={(event) => onChange(event.target.value)} placeholder={placeholder} rows={rows} className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2 text-sm font-normal text-slate-800 placeholder:text-slate-300" />
    </label>
  );
}

function DialogNotice({ tone, children }: Readonly<{ tone: 'success' | 'error' | 'info'; children: ReactNode }>) {
  const className = {
    success: 'border-emerald-200 bg-emerald-50 text-emerald-800',
    error: 'border-rose-200 bg-rose-50 text-rose-800',
    info: 'border-blue-200 bg-blue-50 text-blue-800',
  }[tone];
  return <div className={`rounded-2xl border px-4 py-3 text-sm font-bold ${className}`}>{children}</div>;
}

function isCredentialReady(profile?: CoreAgentProfile): boolean {
  const status = profile?.credential?.credentialStatus;
  return status === 'ACTIVE' || status === 'ROTATE_REQUIRED';
}

function isProfileReady(profile?: CoreAgentProfile): boolean {
  return Boolean(profile && profile.approvalStatus === 'APPROVED' && profile.enabled !== false);
}

function runtimeIsConnected(data: AgentDetailBundle): boolean {
  const connectedCount = data.row.runtimeSummary?.connectedCount;
  if (typeof connectedCount === 'number') return connectedCount > 0;
  return data.runtime?.connected === true;
}

function approvedCapabilityCount(data: AgentDetailBundle): number {
  const governed = (data.capabilityAssignments ?? []).filter((item) => item.status === 'APPROVED').length;
  const profileCapabilities = (data.profile?.capabilities ?? []).filter((item) => item.enabled !== false).length;
  return Math.max(governed, profileCapabilities);
}

function blockingChecks(data: AgentDetailBundle) {
  const checks: Array<{ code?: string; status?: string; blocking?: boolean; message?: string; label?: string }> = [];
  if (!isProfileReady(data.profile)) checks.push({ code: 'AGENT_PROFILE_NOT_READY', status: 'BLOCKED', blocking: true, label: 'Agent 尚未核准', message: '請先核准並啟用 Agent Profile。' });
  if (!isCredentialReady(data.profile)) checks.push({ code: 'AGENT_CREDENTIAL_NOT_ACTIVE', status: 'BLOCKED', blocking: true, label: 'Credential 尚未啟用', message: '請先建立有效的 Agent Credential。' });
  if (!runtimeIsConnected(data)) checks.push({ code: 'AGENT_RUNTIME_OFFLINE', status: 'BLOCKED', blocking: true, label: 'Agent 尚未連線', message: '請啟動 Agent Runtime 並確認 heartbeat 正常。' });
  return checks;
}

function buildSetupSteps(data: AgentDetailBundle): SetupStep[] {
  const profileReady = isProfileReady(data.profile);
  const credentialReady = isCredentialReady(data.profile);
  const connected = runtimeIsConnected(data);
  const flowReady = (data.agentPools ?? []).length > 0 || (data.dispatchFlows ?? []).length > 0;
  return [
    { id: 'profile', label: 'Agent 已核准並啟用', complete: profileReady, actionLabel: profileReady ? '查看連線設定' : '核准或編輯 Agent', tab: 'connection', description: 'Agent Profile 是身分與管理權威。' },
    { id: 'credential', label: 'Credential 有效', complete: credentialReady, actionLabel: credentialReady ? '查看 Credential' : '建立 Credential', tab: 'connection', description: 'Runtime 必須使用有效 Credential 才能連線。' },
    { id: 'runtime', label: 'Runtime 已連線', complete: connected, actionLabel: connected ? '查看連線' : '啟動 Agent', tab: 'connection', description: 'Agent 必須連線並持續回報 heartbeat。' },
    { id: 'flows', label: '至少加入一個 Agent Pool', complete: flowReady, actionLabel: flowReady ? '查看 Pool / Source Flow' : '加入 Agent Pool', tab: 'flows', description: 'Phase 32-G 的派工主體是 Agent Pool；Source Flow 指到 Pool，再由 Pool 選 Agent。' },
    { id: 'capabilities', label: '能力標籤僅供查詢', complete: true, actionLabel: '查看能力標籤', tab: 'capabilities', description: '一般工作不需要 Capability；第一版派單不以 Capability 作為 gate。' },
  ];
}

function readinessTitle(data: AgentDetailBundle, t: ReturnType<typeof useI18n>['t']): string {
  if (!isProfileReady(data.profile)) return t('agent.detail.readiness.setupIncomplete');
  if (!isCredentialReady(data.profile)) return 'Credential 尚未完成';
  if (!runtimeIsConnected(data)) return t('agent.detail.readiness.offline');
  if (!(data.agentPools ?? []).length && !(data.dispatchFlows ?? []).length) return '尚未加入 Agent Pool';
  return t('agent.detail.readiness.ready');
}

function readinessTone(data: AgentDetailBundle): 'good' | 'warn' | 'bad' {
  if (!isProfileReady(data.profile) || !isCredentialReady(data.profile) || !runtimeIsConnected(data)) return 'bad';
  if (!(data.agentPools ?? []).length && !(data.dispatchFlows ?? []).length) return 'warn';
  return 'good';
}

function readinessStatus(data: AgentDetailBundle): string {
  if (!isProfileReady(data.profile)) return 'SETUP_REQUIRED';
  if (!isCredentialReady(data.profile)) return 'CREDENTIAL_REQUIRED';
  if (!runtimeIsConnected(data)) return 'OFFLINE';
  if (!(data.agentPools ?? []).length && !(data.dispatchFlows ?? []).length) return 'NO_AGENT_POOL';
  return 'READY';
}

function explainReadiness(data: AgentDetailBundle, steps: SetupStep[], t: ReturnType<typeof useI18n>['t']): string {
  const firstMissing = steps.find((step) => !step.complete);
  if (firstMissing) return firstMissing.description;
  return t('agent.detail.readiness.completeExplanation');
}

function runtimeReadyStatus(data: AgentDetailBundle): string {
  const runtime = data.operationalView?.runtime;
  if (runtime?.online === true && runtime?.assignable === true) return 'READY';
  if (runtime?.online === true) return 'ONLINE_NOT_ASSIGNABLE';
  if (runtimeIsConnected(data)) return 'ONLINE';
  return 'OFFLINE';
}

function runtimeReadyTone(data: AgentDetailBundle): 'good' | 'warn' | 'bad' {
  const status = runtimeReadyStatus(data);
  if (status === 'READY' || status === 'ONLINE') return 'good';
  if (status === 'ONLINE_NOT_ASSIGNABLE') return 'warn';
  return 'bad';
}

function taskContractStatus(data: AgentDetailBundle): string {
  return (data.agentPools ?? []).length ? 'POOL_MEMBER' : (data.dispatchFlows ?? []).length ? 'LEGACY_FLOW_AGENT' : 'NO_AGENT_POOL';
}

function taskContractTone(data: AgentDetailBundle): 'good' | 'warn' | 'bad' {
  return ((data.agentPools ?? []).length || (data.dispatchFlows ?? []).length) ? 'good' : 'warn';
}

function TwoLayerReadinessPanel({ data }: Readonly<{ data: AgentDetailBundle }>) {
  return (
    <Panel title="派工準備狀態" description="Phase 32-G 標準流程只檢查 Agent 身分、Runtime 與 Agent Pool 關聯。Capability 是能力標籤，不是第一版 routing gate。">
      <div className="grid gap-3 lg:grid-cols-2">
        <div className="rounded-2xl border border-emerald-100 bg-emerald-50 p-4">
          <div className="flex items-start justify-between gap-3"><div><div className="text-xs font-black uppercase tracking-wide text-emerald-700">Agent Runtime</div><h3 className="mt-1 text-lg font-black text-slate-950">Runtime 是否可接收工作？</h3></div><StatusBadge status={runtimeReadyStatus(data)} /></div>
          <p className="mt-2 text-sm leading-6 text-slate-700">檢查連線、heartbeat、Credential、容量與 draining 狀態。</p>
          <div className="mt-3 grid gap-2 sm:grid-cols-3"><StatCard label="Connection" value={runtimeIsConnected(data) ? 'Online' : 'Offline'} tone={runtimeIsConnected(data) ? 'good' : 'bad'} /><StatCard label="Assignable" value={data.operationalView?.runtime?.assignable === true ? 'Yes' : 'Check'} tone={runtimeReadyTone(data)} /><StatCard label="Slots" value={data.operationalView?.runtime?.availableSlots ?? '-'} tone="good" /></div>
        </div>
        <div className="rounded-2xl border border-purple-100 bg-purple-50 p-4">
          <div className="flex items-start justify-between gap-3"><div><div className="text-xs font-black uppercase tracking-wide text-purple-700">Agent Pool</div><h3 className="mt-1 text-lg font-black text-slate-950">哪些 Pool 可以派給此 Agent？</h3></div><StatusBadge status={taskContractStatus(data)} /></div>
          <p className="mt-2 text-sm leading-6 text-slate-700">目前加入 {data.agentPools?.length ?? 0} 個 Agent Pool。Source Flow 指到 Pool，再由 Pool 選 Agent。</p>
          <div className="mt-3"><Link href="/dispatch-flows" className="rounded-xl bg-purple-700 px-4 py-2 text-sm font-black text-white hover:bg-purple-800">建立或查看 Agent Pool / Source Flow</Link></div>
        </div>
      </div>
    </Panel>
  );
}

function CapabilityList({
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
      <div className="rounded-2xl border border-blue-200 bg-blue-50 p-4 text-sm text-blue-900">
        <div className="font-black">Admin-managed capability source of truth</div>
        <p className="mt-1 leading-6">Dispatch usable is based on Admin UI/Core approval only. Runtime observation is optional diagnostics and never blocks an approved capability.</p>
      </div>

      {rowCodes.length > 0 ? (
        <div className="overflow-hidden rounded-2xl border border-slate-200">
        <table className="min-w-full divide-y divide-slate-200 text-sm">
          <thead className="bg-slate-50 text-left text-xs font-black uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3">{t('agent.detail.table.capability')}</th>
              <th className="px-4 py-3">Core Approval</th>
              <th className="px-4 py-3">Runtime Observation (optional)</th>
              <th className="px-4 py-3">Dispatch Usable</th>
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
          <p className="mt-1 leading-6">These values are not used as the working capability source for Dispatch Flow matching: {Array.from(new Set(ignoredNonWorkingCapabilities)).sort().join(', ')}</p>
        </div>
      ) : null}

      {revokedAssignments.length > 0 ? (
        <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
          <div className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
            <div>
              <div className="text-sm font-black text-slate-900">Revoked capability records</div>
              <p className="mt-1 text-xs leading-5 text-slate-500">These records are no longer dispatch-usable. Remove them from the working list after revocation if the Agent capability set changed.</p>
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

function QuickCapabilityDialog({
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
  const [catalog, setCatalog] = useState<CoreAgentCapabilityCatalog[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [mode, setMode] = useState<'existing' | 'new'>('existing');
  const [selectedCode, setSelectedCode] = useState('');
  const [newCode, setNewCode] = useState('');
  const [newName, setNewName] = useState('');
  const [description, setDescription] = useState('');
  const [reason, setReason] = useState('Assigned from Agent detail inline management.');
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    if (!tenantId.trim()) {
      setError('Tenant is required before loading capabilities.');
      setCatalog([]);
      setLoading(false);
      return;
    }
    let cancelled = false;
    setLoading(true);
    setMessage(null);
    setError(null);
    coreAdminApi.getCapabilities('ACTIVE', undefined, tenantId)
      .then((items) => {
        if (cancelled) return;
        setCatalog(items);
        setSelectedCode((current) => current || items[0]?.capabilityCode || '');
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof Error ? err.message : String(err));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [open, tenantId]);

  if (!open) return null;

  const existing = new Set(existingCodes.map(normalizeCode));
  const availableCatalog = catalog.filter((capability) => !existing.has(normalizeCode(capability.capabilityCode)));
  const capabilityCode = mode === 'new' ? normalizeCode(newCode) : selectedCode;

  async function submit() {
    if (!capabilityCode) {
      setError(t('agent.detail.validation.selectCapability'));
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      if (mode === 'new') {
        await coreAdminApi.upsertCapability(capabilityCode, {
          capabilityCode,
          capabilityName: newName || capabilityCode,
          description,
          status: 'ACTIVE',
          riskLevel: 'MIDDLE',
          capabilityType: 'SERVICE',
          domain: 'GENERAL',
          resourceType: 'GENERIC_RESOURCE',
          operation: 'EXECUTE',
          dataClass: 'INTERNAL',
          serviceLevel: 'STANDARD',
          requiresApproval: true,
          dispatchEligible: true,
          legacyTaskCoupling: false,
          migrationStatus: 'CURRENT',
          metadata: { source: 'AGENT_DETAIL_INLINE_CREATE', agentId, tenantId },
        }, tenantId);
      }
      await requestAgentCapability({
        tenantId,
        capabilityCode,
        source: 'AGENT_DETAIL_INLINE_MANAGEMENT',
        evidenceRef: `agent-detail:${agentId}`,
        reason,
        operatorId: 'admin-ui',
      });
      setMessage(t('agent.detail.success.capabilityRequested', { capabilityCode }));
      await onChanged();
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <ModalShell title={t('agent.detail.dialog.assignCapability.title')} description={t('agent.detail.dialog.assignCapability.description')} onClose={onClose}>
      <div className="space-y-4">
        {message ? <DialogNotice tone="success">{message}</DialogNotice> : null}
        {error ? <DialogNotice tone="error">{error}</DialogNotice> : null}
        <div className="grid gap-3 md:grid-cols-2">
          <button type="button" onClick={() => setMode('existing')} className={`rounded-2xl border p-4 text-left ${mode === 'existing' ? 'border-blue-300 bg-blue-50' : 'border-slate-200 bg-white hover:bg-slate-50'}`}>
            <div className="font-black text-slate-900">{t('agent.detail.dialog.useExistingCapability')}</div>
            <div className="mt-1 text-sm text-slate-500">{t('agent.detail.dialog.useExistingCapability.description')}</div>
          </button>
          <button type="button" onClick={() => setMode('new')} className={`rounded-2xl border p-4 text-left ${mode === 'new' ? 'border-blue-300 bg-blue-50' : 'border-slate-200 bg-white hover:bg-slate-50'}`}>
            <div className="font-black text-slate-900">{t('agent.detail.dialog.createSimpleCapability')}</div>
            <div className="mt-1 text-sm text-slate-500">{t('agent.detail.dialog.createSimpleCapability.description')}</div>
          </button>
        </div>

        {mode === 'existing' ? (
          <label className="text-sm font-bold text-slate-700">
            Capability
            <select value={selectedCode} onChange={(event) => setSelectedCode(event.target.value)} disabled={loading || saving} className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2 text-sm font-normal text-slate-800">
              {availableCatalog.length ? availableCatalog.map((capability) => <option key={capability.capabilityCode} value={capability.capabilityCode}>{capability.capabilityName ?? capability.capabilityCode} ({capability.capabilityCode})</option>) : <option value="">No available ACTIVE capability</option>}
            </select>
          </label>
        ) : (
          <div className="grid gap-4 md:grid-cols-2">
            <FormInput label="Capability Code" value={newCode} onChange={(value) => setNewCode(normalizeCode(value))} placeholder="REDMINE_ISSUE_CREATE" />
            <FormInput label="Capability Name" value={newName} onChange={setNewName} placeholder="Create Redmine Issue" />
            <div className="md:col-span-2"><FormTextArea label="Description" value={description} onChange={setDescription} placeholder="Describe what this capability allows the agent to do." /></div>
          </div>
        )}

        <FormTextArea label="Reason" value={reason} onChange={setReason} rows={2} />
        <div className="flex flex-wrap justify-end gap-2 border-t border-slate-100 pt-4">
          <button type="button" onClick={onClose} className={`${buttonBaseClassName} border-slate-200 bg-white text-slate-700 hover:bg-slate-50`}>Close</button>
          <button type="button" onClick={() => void submit()} disabled={saving || !capabilityCode} className="rounded-lg bg-blue-600 px-3 py-2 text-xs font-black text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50">{saving ? 'Saving...' : 'Request Capability'}</button>
        </div>
      </div>
    </ModalShell>
  );
}

function RecentTasks({ tasks }: Readonly<{ tasks: CoreTaskRuntimeView[] }>) {
  if (!tasks.length) {
    return <EmptyState title="No recent tasks" description="This agent has not received any Core task runtime records yet." />;
  }
  return (
    <div className="overflow-hidden rounded-2xl border border-slate-200">
      <table className="min-w-full divide-y divide-slate-200 text-sm">
        <thead className="bg-slate-50 text-left text-xs font-black uppercase tracking-wide text-slate-500">
          <tr>
            <th className="px-4 py-3">Task</th>
            <th className="px-4 py-3">Result</th>
            <th className="px-4 py-3">Dispatch</th>
            <th className="px-4 py-3">Issue</th>
            <th className="px-4 py-3">Updated</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {tasks.map((task) => (
            <tr key={task.taskId}>
              <td className="px-4 py-3">
                <Link href={`/tasks/${encodeURIComponent(task.taskId)}`} className="font-black text-blue-700 hover:text-blue-800">{task.taskId}</Link>
                <div className="text-xs text-slate-500">{task.taskType ?? '-'}</div>
                <div className="mt-3">
                  <DispatchAssignmentEvidencePanel task={task} routingDecisions={task.latestRoutingDecision ? [task.latestRoutingDecision] : undefined} compact />
                </div>
              </td>
              <td className="px-4 py-3"><StatusBadge status={task.status} /></td>
              <td className="px-4 py-3"><StatusBadge status={task.dispatchStatus ?? task.dispatchExecutionStatus ?? '-'} /></td>
              <td className="px-4 py-3 text-slate-600">
                {task.issueTracking?.issueUrl ? (
                  <a href={task.issueTracking.issueUrl} target="_blank" rel="noreferrer" className="font-bold text-blue-700 hover:text-blue-800">
                    {task.issueTracking.issueVendor ?? 'Issue'} {task.issueTracking.issueId ?? ''}
                  </a>
                ) : task.issueTracking?.issueId ? (
                  `${task.issueTracking.issueVendor ?? 'Issue'} ${task.issueTracking.issueId}`
                ) : '-'}
              </td>
              <td className="px-4 py-3 text-slate-600">{formatDateTime(task.updatedAt ?? task.createdAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}


function AgentDispatchFlowsPanel({ data, agentId }: Readonly<{ data: AgentDetailBundle; agentId: string }>) {
  const flows = data.dispatchFlows ?? [];
  const pools = data.agentPools ?? [];
  return (
    <div className="space-y-5">
      <Panel
        title="Agent Pool / Work Queue"
        description="Phase 32-G 的標準派單關聯是 Agent Pool。Source Flow 指到 Pool，再由 Pool 內策略選 Agent；Capability 僅是能力標籤參考。"
        action={<Link href="/dispatch-flows" className="rounded-xl bg-purple-700 px-4 py-2 text-sm font-black text-white hover:bg-purple-800">管理 Pool / Source Flow</Link>}
      >
        {pools.length ? (
          <div className="grid gap-3 md:grid-cols-2">
            {pools.map((pool) => {
              const relation = (pool.members ?? []).find((member) => member.agentId === agentId);
              return (
                <Link key={pool.poolId} href="/dispatch-flows" className="rounded-2xl border border-slate-200 bg-slate-50 p-4 hover:border-purple-200 hover:bg-purple-50">
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <div className="font-black text-slate-950">{pool.poolName ?? pool.poolCode ?? pool.poolId}</div>
                      <div className="mt-1 text-xs text-slate-500">{pool.sourceSystem ?? '共用'} · {pool.poolType ?? 'RESOLUTION'} · {pool.poolCode ?? '-'}</div>
                    </div>
                    <StatusBadge status={pool.status ?? relation?.memberStatus ?? 'ACTIVE'} />
                  </div>
                  <div className="mt-3 flex flex-wrap gap-2 text-xs font-bold text-slate-600">
                    <span className="rounded-full bg-white px-2 py-1">策略 {pool.selectionStrategy ?? 'LOWEST_LOAD'}</span>
                    <span className="rounded-full bg-white px-2 py-1">成員 {pool.memberCount ?? pool.members?.length ?? 0}</span>
                    <span className="rounded-full bg-white px-2 py-1">可用 {pool.availableAgentCount ?? 0}</span>
                  </div>
                </Link>
              );
            })}
          </div>
        ) : (
          <EmptyState title="尚未加入 Agent Pool" description="請在 Dispatch Flows 頁建立 TRIAGE_POOL 或處理池，並把此 Agent 加入 Pool。" />
        )}
      </Panel>

      <Panel
        title="相容：直接選用此 Agent 的舊 Dispatch Flow"
        description="這裡只作歷史相容參考。Phase 32-G 新流程應以 Agent Pool 為派單主體。"
      >
        {flows.length ? (
          <div className="grid gap-3 md:grid-cols-2">
            {flows.map((flow) => {
              const relation = (flow.agents ?? []).find((agent) => agent.agentId === agentId);
              return (
                <Link key={flow.flowId} href={`/dispatch-flows?flowId=${encodeURIComponent(flow.flowId)}`} className="rounded-2xl border border-slate-200 bg-slate-50 p-4 hover:border-purple-200 hover:bg-purple-50">
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <div className="font-black text-slate-950">{flow.flowName ?? flow.flowCode ?? flow.flowId}</div>
                      <div className="mt-1 text-xs text-slate-500">{flow.sourceSystem ?? '-'} · {relation?.agentRole ?? 'HANDLER'}</div>
                    </div>
                    <StatusBadge status={flow.status ?? relation?.assignmentStatus ?? 'DRAFT'} />
                  </div>
                  <div className="mt-3 flex flex-wrap gap-2 text-xs font-bold text-slate-600">
                    <span className="rounded-full bg-white px-2 py-1">Default Pool {flow.defaultPoolId ? '設定済' : '未設定'}</span>
                    <span className="rounded-full bg-white px-2 py-1">Rule {flow.rules?.length ?? flow.externalRuleCount ?? 0}</span>
                  </div>
                </Link>
              );
            })}
          </div>
        ) : (
          <EmptyState title="沒有舊式直接 Flow Agent 關聯" description="這是正常狀態；Phase 32-G 標準流程會顯示在 Agent Pool 區塊。" />
        )}
      </Panel>
    </div>
  );
}

function AdvancedAgentData({ data }: Readonly<{ data: AgentDetailBundle }>) {
  return (
    <div className="space-y-5">
      <Panel title="活動紀錄" description="顯示與此 Agent 相關的近期安全與 Runtime 事件。原始 payload 與底層診斷不再透過一般操作模式切換顯示。">
        {data.securityEvents.length ? (
          <div className="space-y-3">
            {data.securityEvents.slice(0, 20).map((event) => (
              <div key={event.eventId} className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
                <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                  <div><div className="font-black text-slate-900">{event.eventType}</div><div className="mt-1 text-xs text-slate-500">{formatDateTime(event.occurredAt)}</div></div>
                  <StatusBadge status={event.severity ?? 'INFO'} />
                </div>
                <p className="mt-2 text-sm text-slate-700">{event.reason ?? '-'}</p>
              </div>
            ))}
          </div>
        ) : <EmptyState title="沒有活動紀錄" description="目前沒有找到此 Agent 的安全或 Runtime 事件。" />}
      </Panel>
    </div>
  );
}

function OverviewPanel({ data, setActiveTab }: Readonly<{ data: AgentDetailBundle; setActiveTab: (tab: AgentDetailTab) => void }>) {
  const { t } = useI18n();
  const steps = useMemo(() => buildSetupSteps(data), [data]);
  const completedSteps = steps.filter((step) => step.complete).length;
  const tone = readinessTone(data);
  const toneClassName = {
    good: 'border-emerald-200 bg-emerald-50 text-emerald-950',
    warn: 'border-amber-200 bg-amber-50 text-amber-950',
    bad: 'border-rose-200 bg-rose-50 text-rose-950',
  }[tone];
  const blockers = blockingChecks(data);
  const firstMissing = steps.find((step) => !step.complete);

  return (
    <div className="space-y-5">
      <section className={`rounded-3xl border p-5 shadow-sm ${toneClassName}`}>
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <div className="text-xs font-black uppercase tracking-wide opacity-70">Agent Readiness</div>
            <h2 className="mt-1 text-2xl font-black">{readinessTitle(data, t)}</h2>
            <p className="mt-2 max-w-4xl text-sm leading-6 opacity-85">{explainReadiness(data, steps, t)}</p>
          </div>
          <StatusBadge status={readinessStatus(data)} />
        </div>

        <div className="mt-5 grid gap-3 md:grid-cols-2 xl:grid-cols-6">
          <StatCard label="Readiness Source" value={data.operationalView ? 'Operational View' : data.setupReadiness ? 'Core Backend' : 'Fallback UI'} tone={data.operationalView || data.setupReadiness ? 'good' : 'warn'} />
          <StatCard label="Setup Progress" value={`${completedSteps} / ${steps.length}`} tone={completedSteps === steps.length ? 'good' : 'warn'} />
          <StatCard label="Agent Runtime Ready" value={runtimeReadyStatus(data)} tone={runtimeReadyTone(data)} />
          <StatCard label="Agent Pools" value={data.agentPools?.length ?? 0} tone={taskContractTone(data)} />
          <StatCard label="Capabilities" value={approvedCapabilityCount(data)} tone={approvedCapabilityCount(data) ? 'good' : 'warn'} />
          <StatCard label="Blocking Checks" value={blockers.length} tone={blockers.length ? 'bad' : 'good'} />
        </div>

        <div className="mt-5 flex flex-wrap gap-2">
          {firstMissing ? (
            <button type="button" onClick={() => setActiveTab(firstMissing.tab)} className="rounded-xl bg-indigo-600 px-4 py-2 text-sm font-black text-white hover:bg-indigo-700">
              {firstMissing.actionLabel}
            </button>
          ) : null}
          <button type="button" onClick={() => setActiveTab('tasks')} className="rounded-xl border border-slate-300 bg-white px-4 py-2 text-sm font-black text-slate-700 hover:bg-slate-50">
            View Recent Tasks
          </button>
          <Link href={`/dispatch-flows?agentId=${encodeURIComponent(data.profile?.agentId ?? '')}`} className="rounded-xl border border-purple-200 bg-purple-50 px-4 py-2 text-sm font-black text-purple-700 hover:bg-purple-100">
            Open Dispatch Flows
          </Link>
        </div>
      </section>

      <TwoLayerReadinessPanel data={data} />

      <Panel title="Setup Checklist" description="This checklist replaces scattered technical tabs. Select the action beside the missing item to continue setup.">
        <div className="space-y-3">
          {steps.map((step) => (
            <div key={step.id} className="flex flex-col gap-3 rounded-2xl border border-slate-200 bg-slate-50 p-4 lg:flex-row lg:items-start lg:justify-between">
              <div>
                <div className="flex items-center gap-2">
                  <StatusBadge status={step.complete ? 'COMPLETE' : 'MISSING'} />
                  <span className="font-black text-slate-900">{step.label}</span>
                </div>
                <p className="mt-2 text-sm leading-6 text-slate-600">{step.description}</p>
              </div>
              <button type="button" onClick={() => setActiveTab(step.tab)} className="shrink-0 rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-black text-slate-700 hover:bg-slate-50">
                {step.actionLabel}
              </button>
            </div>
          ))}
        </div>
      </Panel>
    </div>
  );
}

function RepairActionDialog({
  agentId,
  action,
  onCompleted,
}: Readonly<{
  agentId: string;
  action: CoreAgentConnectionRepairAction;
  onCompleted: (result: CoreAgentConnectionRepairActionResult) => void;
}>) {
  const [open, setOpen] = useState(false);
  const [credentialToken, setCredentialToken] = useState('');
  const [reason, setReason] = useState(action.description ?? 'Connection repair action');
  const [running, setRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (action.actionType === 'NAVIGATE') {
    return (
      <Link href={action.endpoint ?? `/agents/${encodeURIComponent(agentId)}`} className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-black text-slate-700 hover:bg-slate-50">
        {action.label ?? action.actionCode}
      </Link>
    );
  }

  const execute = async () => {
    setRunning(true);
    setError(null);
    try {
      const result = await coreAdminApi.executeAgentConnectionRepairAction(agentId, action.actionCode, {
        operatorId: 'admin-ui',
        reason,
        credentialToken: credentialToken.trim() || undefined,
        revokeExisting: true,
        enableAfterRepair: true,
      });
      onCompleted(result);
      setOpen(false);
      setCredentialToken('');
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Repair action failed');
    } finally {
      setRunning(false);
    }
  };

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        disabled={action.enabled === false}
        className={`rounded-lg border px-3 py-2 text-xs font-black disabled:cursor-not-allowed disabled:opacity-50 ${action.highRisk ? 'border-rose-200 bg-rose-50 text-rose-700 hover:bg-rose-100' : 'border-indigo-200 bg-indigo-50 text-indigo-700 hover:bg-indigo-100'}`}
        title={action.disabledReason}
      >
        {action.label ?? action.actionCode}
      </button>
      {open ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
          <div className="w-full max-w-xl rounded-2xl bg-white p-5 shadow-2xl">
            <div className="flex items-start justify-between gap-3">
              <div>
                <h3 className="text-base font-black text-slate-950">{action.label ?? action.actionCode}</h3>
                <p className="mt-1 text-sm leading-6 text-slate-600">{action.description}</p>
              </div>
              <button type="button" onClick={() => setOpen(false)} className="rounded-lg border border-slate-200 px-2 py-1 text-xs font-black text-slate-500 hover:bg-slate-50">Close</button>
            </div>
            {action.disabledReason ? <div className="mt-3 rounded-xl border border-amber-200 bg-amber-50 p-3 text-xs font-bold text-amber-800">{action.disabledReason}</div> : null}
            {action.nextStep ? <div className="mt-3 rounded-xl border border-blue-200 bg-blue-50 p-3 text-xs leading-5 text-blue-800">{action.nextStep}</div> : null}
            {action.requiresCredentialToken ? (
              <label className="mt-4 block text-xs font-black uppercase tracking-wide text-slate-500">
                New credential token
                <input
                  type="password"
                  value={credentialToken}
                  onChange={(event) => setCredentialToken(event.target.value)}
                  className="mt-2 w-full rounded-xl border border-slate-200 px-3 py-2 text-sm font-semibold text-slate-900 outline-none focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
                  placeholder="Paste the new runtime token"
                />
              </label>
            ) : null}
            <label className="mt-4 block text-xs font-black uppercase tracking-wide text-slate-500">
              Repair reason
              <textarea
                value={reason}
                onChange={(event) => setReason(event.target.value)}
                className="mt-2 min-h-20 w-full rounded-xl border border-slate-200 px-3 py-2 text-sm font-semibold text-slate-900 outline-none focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
              />
            </label>
            {error ? <div className="mt-3 rounded-xl border border-rose-200 bg-rose-50 p-3 text-xs font-bold text-rose-700">{error}</div> : null}
            <div className="mt-5 flex flex-wrap justify-end gap-2">
              <button type="button" onClick={() => setOpen(false)} className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-black text-slate-700 hover:bg-slate-50">Cancel</button>
              <button
                type="button"
                onClick={execute}
                disabled={running || action.enabled === false || (action.requiresCredentialToken && !credentialToken.trim())}
                className="rounded-lg bg-indigo-600 px-3 py-2 text-xs font-black text-white hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {running ? 'Running…' : 'Run repair action'}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </>
  );
}

function RepairActionsGrid({ agentId, actions, onCompleted }: Readonly<{ agentId: string; actions?: CoreAgentConnectionRepairAction[]; onCompleted: (result: CoreAgentConnectionRepairActionResult) => void }>) {
  const visibleActions = actions ?? [];
  if (!visibleActions.length) return null;
  return (
    <div className="rounded-2xl border border-indigo-200 bg-indigo-50 p-4">
      <div className="text-sm font-black text-indigo-950">Repair Actions</div>
      <p className="mt-1 text-xs leading-5 text-indigo-800">These actions are generated by the Core backend from the latest runtime authorization failure. Run only the action that matches the verified failure reason.</p>
      <div className="mt-3 grid gap-3 lg:grid-cols-2">
        {visibleActions.map((action) => (
          <div key={action.actionCode} className="rounded-xl border border-white/70 bg-white p-3 shadow-sm">
            <div className="flex items-start justify-between gap-3">
              <div>
                <div className="text-sm font-black text-slate-950">{action.label ?? action.actionCode}</div>
                {action.description ? <p className="mt-1 text-xs leading-5 text-slate-600">{action.description}</p> : null}
                {action.disabledReason ? <p className="mt-2 text-xs font-bold text-amber-700">{action.disabledReason}</p> : null}
              </div>
              {action.highRisk ? <span className="rounded-full bg-rose-100 px-2 py-0.5 text-[10px] font-black uppercase text-rose-700">High risk</span> : null}
            </div>
            <div className="mt-3 flex flex-wrap gap-2">
              <RepairActionDialog agentId={agentId} action={action} onCompleted={onCompleted} />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function LatestAuthFailurePanel({ data, agentId, onRefresh }: Readonly<{ data: AgentDetailBundle; agentId: string; onRefresh: () => void }>) {
  const failure = data.latestAuthFailure;
  const repairActions = data.connectionRepairActions?.actions ?? failure?.repairActions ?? [];
  const [repairResult, setRepairResult] = useState<CoreAgentConnectionRepairActionResult | null>(null);
  const securityEventHref = failure?.securityEventLink ?? `/security-events?agentId=${encodeURIComponent(agentId)}`;
  const completeRepair = (result: CoreAgentConnectionRepairActionResult) => {
    setRepairResult(result);
    onRefresh();
  };

  if (!failure) {
    return (
      <Panel title="Runtime Auth Failure" description="Core latest-auth-failure contract is unavailable. The page will not guess the last denied runtime reason from incomplete data.">
        <div className="flex flex-wrap items-center gap-3 rounded-2xl border border-amber-200 bg-amber-50 p-4">
          <div className="min-w-0 flex-1">
            <div className="text-sm font-black text-amber-950">Latest auth failure is unavailable</div>
            <p className="mt-1 text-xs leading-5 text-amber-800">Refresh the page or check Core Admin API availability before troubleshooting runtime credentials.</p>
          </div>
          <button type="button" onClick={onRefresh} className="rounded-lg border border-amber-200 bg-white px-3 py-2 text-xs font-black text-amber-700 hover:bg-amber-100">Refresh</button>
        </div>
      </Panel>
    );
  }

  if (!failure.hasFailure) {
    return (
      <Panel title="Runtime Auth Failure" description="Core stores denied runtime authorization attempts so operators can troubleshoot the actual last failure reason.">
        <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-4">
          <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
            <div>
              <div className="text-sm font-black text-emerald-950">No runtime authorization failure recorded</div>
              <p className="mt-1 text-xs leading-5 text-emerald-800">{failure.summary ?? 'Core has not recorded a denied authorization attempt for this Agent.'}</p>
            </div>
            <StatusBadge status="CLEAR" />
          </div>
        </div>
      </Panel>
    );
  }

  const troubleshooting = failure.troubleshooting ?? [];
  return (
    <Panel
      title="Runtime Auth Failure"
      description="This is the latest denied runtime authorization event stored by Core. Use it before guessing token, Agent ID, or approval problems."
      action={<Link href={securityEventHref} className="rounded-lg border border-rose-200 bg-white px-3 py-2 text-xs font-black text-rose-700 hover:bg-rose-50">Open Security Event</Link>}
    >
      <div className="space-y-4">
        <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4">
          <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
            <div>
              <div className="text-sm font-black text-rose-950">{failure.denyReason ?? failure.eventType ?? 'AUTH_DENIED'}</div>
              <p className="mt-1 text-sm leading-6 text-rose-900">{failure.summary ?? failure.reason ?? 'Core denied the latest runtime authorization attempt.'}</p>
              <div className="mt-2 text-xs leading-5 text-rose-700">
                Security event: <span className="font-black">{failure.securityEventId ?? '-'}</span> · Gateway: <span className="font-black">{failure.gatewayNodeId ?? '-'}</span> · Remote: <span className="font-black">{failure.remoteAddress ?? '-'}</span>
              </div>
            </div>
            <StatusBadge status="AUTH_FAILED" />
          </div>
        </div>

        {repairResult ? (
          <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-sm leading-6 text-emerald-900">
            <div className="font-black text-emerald-950">Repair action completed: {repairResult.actionCode}</div>
            <p className="mt-1">{repairResult.message ?? 'Refresh readiness and restart the runtime if the action changed credentials or profile state.'}</p>
          </div>
        ) : null}

        <RepairActionsGrid agentId={agentId} actions={repairActions} onCompleted={completeRepair} />

        {troubleshooting.length ? (
          <div className="grid gap-3 lg:grid-cols-2">
            {troubleshooting.map((step) => (
              <div key={step.code || step.label} className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="text-sm font-black text-slate-950">{step.label || step.code}</span>
                  {step.severity ? <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-black uppercase text-slate-600">{step.severity}</span> : null}
                </div>
                {step.description ? <p className="mt-2 text-xs leading-5 text-slate-600">{step.description}</p> : null}
                {step.action ? <div className="mt-2 text-xs font-black text-slate-800">Action: {step.action}</div> : null}
                {step.command ? <pre className="mt-3 overflow-auto whitespace-pre-wrap rounded-xl bg-slate-950 p-3 text-[11px] leading-5 text-slate-100">{step.command}</pre> : null}
              </div>
            ))}
          </div>
        ) : null}
      </div>
    </Panel>
  );
}

function RuntimeBindingPanel({
  bindings,
  onCreateOrActivate,
  onActivate,
}: Readonly<{
  bindings: CoreAgentRuntimeBinding[];
  onCreateOrActivate: () => void;
  onActivate: (bindingId: string) => void;
}>) {
  const activeBinding = bindings.find((binding) => String(binding.bindingStatus ?? '').toUpperCase() === 'ACTIVE');
  return (
    <Panel
      title="Runtime Binding"
      description="Core requires an ACTIVE Runtime Binding before an online runtime can receive dispatch assignments. Runtime online status alone is telemetry; the active binding grants dispatch authority."
      action={<button type="button" onClick={onCreateOrActivate} className="rounded-lg bg-blue-600 px-3 py-2 text-xs font-black text-white hover:bg-blue-700">Create / Activate Binding</button>}
    >
      {activeBinding ? (
        <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-4">
          <div className="text-xs font-black uppercase tracking-wide text-emerald-700">Dispatch authority granted</div>
          <div className="mt-3 grid gap-3 md:grid-cols-2 xl:grid-cols-4">
            <StatCard label="Status" value={activeBinding.bindingStatus ?? 'ACTIVE'} tone="good" />
            <StatCard label="Binding" value={activeBinding.bindingId ?? '-'} />
            <StatCard label="Runtime" value={activeBinding.runtimeCode ?? activeBinding.runtimeId ?? '-'} />
            <StatCard label="Approved" value={formatDateTime(activeBinding.approvedAt ?? activeBinding.updatedAt)} />
          </div>
        </div>
      ) : (
        <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4">
          <div className="text-xs font-black uppercase tracking-wide text-rose-700">Runtime binding is not active</div>
          <p className="mt-2 text-sm leading-6 text-rose-900">The Agent may be online, but Core has not activated the Agent + runtime binding required for dispatch authority. Create or activate the binding, then refresh readiness.</p>
        </div>
      )}
      {bindings.length > 0 ? (
        <div className="mt-4 overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50 text-left text-xs font-black uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-3 py-2">Binding</th>
                <th className="px-3 py-2">Runtime</th>
                <th className="px-3 py-2">Status</th>
                <th className="px-3 py-2">Capacity</th>
                <th className="px-3 py-2">Updated</th>
                <th className="px-3 py-2">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {bindings.map((binding) => {
                const status = String(binding.bindingStatus ?? '').toUpperCase();
                return (
                  <tr key={binding.bindingId ?? `${binding.runtimeId}-${status}`}>
                    <td className="px-3 py-2 font-bold text-slate-800">{binding.bindingId ?? '-'}</td>
                    <td className="px-3 py-2 text-slate-600">{binding.runtimeCode ?? binding.runtimeId ?? '-'}</td>
                    <td className="px-3 py-2"><StatusBadge status={status || 'UNKNOWN'} /></td>
                    <td className="px-3 py-2 text-slate-600">{binding.capacityLimit ?? '-'}</td>
                    <td className="px-3 py-2 text-slate-500">{formatDateTime(binding.updatedAt ?? binding.approvedAt)}</td>
                    <td className="px-3 py-2">
                      {status !== 'ACTIVE' && binding.bindingId ? (
                        <button type="button" onClick={() => onActivate(binding.bindingId ?? '')} className="rounded-lg border border-emerald-200 bg-white px-2 py-1 text-xs font-black text-emerald-700 hover:bg-emerald-50">Activate</button>
                      ) : <span className="text-xs font-bold text-slate-400">-</span>}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      ) : null}
    </Panel>
  );
}

function ConnectionPanel({
  data,
  agentId,
  refreshing,
  onRefresh,
  onPing,
  onDisconnect,
  onDisconnectAll,
  onCreateOrActivateRuntimeBinding,
  onActivateRuntimeBinding,
}: Readonly<{
  data: AgentDetailBundle;
  agentId: string;
  refreshing: boolean;
  onRefresh: () => void;
  onPing: () => void;
  onDisconnect: () => void;
  onDisconnectAll: () => void;
  onCreateOrActivateRuntimeBinding: () => void;
  onActivateRuntimeBinding: (bindingId: string) => void;
}>) {
  const profile = data.profile;
  const runtime = data.runtime;
  const descriptor = data.runtimeDescriptor;
  const backendStartCommand = data.setupReadiness?.startCommand;
  const fallbackStartCommand = `export OPENSOCKET_AGENT_ID=${profile?.agentId ?? runtime?.agentId ?? 'agent-id'}
export OPENSOCKET_GATEWAY_URL=ws://localhost:18081/ws/agents
export OPENSOCKET_AGENT_TOKEN=<issued-token>
# Start your OpenClaw / worker process with the environment above`;
  const commandVariants = [
    { label: 'Recommended', value: backendStartCommand?.command },
    { label: 'Docker', value: backendStartCommand?.dockerCommand },
    { label: 'Local process', value: backendStartCommand?.localCommand },
    { label: 'Remote host', value: backendStartCommand?.remoteCommand },
    { label: 'Gateway health check', value: backendStartCommand?.healthCheckCommand },
    { label: 'Verify authorization', value: backendStartCommand?.verifyConnectionCommand },
    { label: 'Runtime logs', value: backendStartCommand?.logsCommand },
  ].filter((entry): entry is { label: string; value: string } => Boolean(entry.value));
  const troubleshooting = data.setupReadiness?.troubleshooting ?? backendStartCommand?.troubleshooting ?? [];
  return (
    <div className="space-y-5">
      <Panel
        title="Connection"
        description="Configure and verify how this agent connects to the gateway. A connected runtime is required before the agent can receive tasks."
        action={<InlineManageLink href={managementHref('/settings/runtime-resources', agentId)}>Manage Gateways</InlineManageLink>}
      >
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
          <StatCard label="Runtime Status" value={runtimeIsConnected(data) ? 'Online' : 'Offline'} tone={runtimeIsConnected(data) ? 'good' : 'bad'} />
          <StatCard label="Agent ID" value={profile?.agentId ?? runtime?.agentId ?? '-'} />
          <StatCard label="Gateway" value={runtime?.gatewayNodeId ?? runtime?.nodeId ?? descriptor?.ownerGatewayNodeId ?? '-'} />
          <StatCard label="Last Heartbeat" value={formatDateTime(runtime?.lastHeartbeatAt ?? descriptor?.lastHeartbeatAt ?? descriptor?.lastSeenAt)} />
        </div>
        <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-4">
          <StatCard label="Runtime Type" value={profile?.agentType ?? descriptor?.agentType ?? '-'} />
          <StatCard label="Plugin" value={descriptor?.pluginName ?? '-'} />
          <StatCard label="Active Tasks" value={descriptor?.activeTasks ?? runtime?.activeTaskCount ?? '-'} />
          <StatCard label="Available Slots" value={descriptor?.availableSlots ?? data.runtimeLoad?.availableSlots ?? '-'} />
        </div>
        <div className="mt-5 flex flex-wrap gap-2">
          <button type="button" onClick={onPing} className="rounded-lg bg-blue-600 px-3 py-2 text-xs font-black text-white hover:bg-blue-700">Ping Agent</button>
          <button type="button" onClick={onRefresh} disabled={refreshing} className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-black text-slate-700 hover:bg-slate-50 disabled:opacity-50">Refresh Connection</button>
          <button type="button" onClick={onDisconnect} className="rounded-lg border border-amber-200 bg-white px-3 py-2 text-xs font-black text-amber-700 hover:bg-amber-50">Disconnect Session</button>
          <button type="button" onClick={onDisconnectAll} className="rounded-lg border border-rose-200 bg-white px-3 py-2 text-xs font-black text-rose-700 hover:bg-rose-50">Disconnect All Sessions</button>
        </div>
        <p className="mt-3 text-xs font-semibold text-slate-500">After starting the runtime, use Refresh Connection to re-read the Core backend readiness contract and confirm RUNTIME_CONNECTED changed to ready.</p>
      </Panel>

      <RuntimeBindingPanel
        bindings={data.runtimeBindings ?? []}
        onCreateOrActivate={onCreateOrActivateRuntimeBinding}
        onActivate={onActivateRuntimeBinding}
      />

      <Panel title="Core Profile" description="Core profile is the managed identity for this agent.">
        {profile ? (
          <div className="space-y-4">
            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
              <StatCard label="Approval" value={profile.approvalStatus} tone={profile.approvalStatus === 'APPROVED' ? 'good' : 'warn'} />
              <StatCard label="Enabled" value={profile.enabled === false ? 'Disabled' : 'Enabled'} tone={profile.enabled === false ? 'bad' : 'good'} />
              <StatCard label="Risk" value={profile.riskStatus ?? 'Unknown'} />
              <StatCard label="Credential" value={profile.credential?.credentialStatus ?? 'Missing'} tone={isCredentialReady(profile) ? 'good' : 'bad'} />
            </div>
            <div className="flex flex-wrap gap-2">
              <AgentProfileEditDialog profile={profile} triggerLabel="Edit Profile" onSaved={onRefresh} />
              <AgentCredentialIssueDialog profile={profile} onSaved={onRefresh} />
            </div>
          </div>
        ) : data.row.enrollment || data.runtime ? (
          <div className="space-y-4">
            <EmptyState title="No approved Core profile" description="Review or approve the enrollment before this agent can be managed as a production worker." />
            <div className="flex flex-wrap gap-2">
              <AgentEnrollmentReviewDialog row={data.row} triggerLabel="Edit Enrollment" intent="edit" onChanged={onRefresh} />
              <AgentEnrollmentReviewDialog row={data.row} triggerLabel="Approve Enrollment" intent="approve" onChanged={onRefresh} />
              <AgentEnrollmentReviewDialog row={data.row} triggerLabel="Reject Enrollment" intent="reject" onChanged={onRefresh} />
            </div>
          </div>
        ) : (
          <EmptyState title="No profile or enrollment found" description="Create an agent enrollment first, then return to this page for setup." />
        )}
      </Panel>

      <LatestAuthFailurePanel data={data} agentId={agentId} onRefresh={onRefresh} />

      <Panel title="Start Command & Diagnostics" description="Use the backend-owned startup contract. Token material is redacted after setup; rotate or issue a credential if the runtime does not already have one.">
        <pre className="overflow-auto rounded-2xl bg-slate-950 p-4 text-xs leading-6 text-slate-100">{backendStartCommand?.command ?? fallbackStartCommand}</pre>
        {backendStartCommand?.expectedCapabilities?.length ? (
          <div className="mt-4 rounded-2xl border border-indigo-200 bg-indigo-50 p-4">
            <div className="text-xs font-black uppercase tracking-wide text-indigo-700">Admin-managed dispatch capabilities</div>
            <p className="mt-1 text-xs leading-5 text-indigo-900">These capabilities are managed in Core/Admin UI and are not required in the Agent startup environment. The runtime only needs identity, credential, gateway URL, heartbeat, and capacity.</p>
            <pre className="mt-3 overflow-auto rounded-xl bg-slate-950 p-3 text-[11px] leading-5 text-slate-100">{backendStartCommand.expectedCapabilities.join(',')}</pre>
          </div>
        ) : null}
        {commandVariants.length > 0 ? (
          <div className="mt-4 grid gap-3 lg:grid-cols-2">
            {commandVariants.map((entry) => (
              <details key={entry.label} className="rounded-2xl border border-slate-200 bg-slate-50 p-3">
                <summary className="cursor-pointer text-xs font-black text-slate-700">{entry.label}</summary>
                <pre className="mt-2 overflow-auto whitespace-pre-wrap rounded-xl bg-slate-950 p-3 text-[11px] leading-5 text-slate-100">{entry.value}</pre>
              </details>
            ))}
          </div>
        ) : null}
        {backendStartCommand?.startupSteps?.length ? (
          <div className="mt-4 rounded-2xl border border-blue-200 bg-blue-50 p-4">
            <div className="text-xs font-black uppercase tracking-wide text-blue-700">Startup checklist</div>
            <ol className="mt-2 list-decimal space-y-1 pl-5 text-sm leading-6 text-blue-900">
              {backendStartCommand.startupSteps.map((step) => <li key={step}>{step}</li>)}
            </ol>
          </div>
        ) : null}
        {troubleshooting.length > 0 ? (
          <div className="mt-4 rounded-2xl border border-amber-200 bg-amber-50 p-4">
            <div className="text-xs font-black uppercase tracking-wide text-amber-700">Connection troubleshooting</div>
            <div className="mt-3 grid gap-3">
              {troubleshooting.map((step) => (
                <div key={step.code || step.label} className="rounded-xl bg-white p-3 text-sm leading-6 text-amber-950 shadow-sm">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-black">{step.label || step.code}</span>
                    {step.severity ? <span className="rounded-full bg-amber-100 px-2 py-0.5 text-[10px] font-black uppercase text-amber-700">{step.severity}</span> : null}
                  </div>
                  {step.description ? <p className="mt-1 text-xs leading-5 text-amber-800">{step.description}</p> : null}
                  {step.action ? <div className="mt-2 text-xs font-black text-amber-900">Action: {step.action}</div> : null}
                  {step.command ? <pre className="mt-2 overflow-auto rounded-xl bg-slate-950 p-3 text-[11px] leading-5 text-slate-100">{step.command}</pre> : null}
                </div>
              ))}
            </div>
          </div>
        ) : null}
      </Panel>
    </div>
  );
}

export function AgentDetailProductView({ agentId }: AgentDetailProductViewProps) {
  const { t } = useI18n();
  const {
    data,
    loading,
    refreshing,
    error,
    lastUpdatedAt,
    refresh,
    commandMessage,
    pingAgent,
    disconnectAgent,
    disconnectAllAgentSessions,
    createRuntimeBindingFromCurrentRuntime,
    activateRuntimeBinding,
    requestAgentCapability,
    approveAgentCapability,
    suspendAgentCapability,
    resumeAgentCapability,
    revokeAgentCapability,
    removeAgentCapability,
  } = useAgentDetail(agentId);
  const [activeTab, setActiveTab] = useState<AgentDetailTab>('overview');
  const [capabilityDialogOpen, setCapabilityDialogOpen] = useState(false);

  if (loading) return <LoadingBox label={`Loading agent ${agentId}...`} />;
  if (error) return <ErrorBox message={error} />;
  if (!data) return <EmptyState title="Agent not found" description="Core and Gateway did not return this agent." />;

  const hasSourceErrors = Object.values(data.sourceErrors).some(Boolean);
  const dataSource = dataSourceKindFromFlags({
    hasLiveData: Boolean(data.profile || data.runtime || data.runtimes.length > 0 || data.tasks.length > 0),
    hasSourceErrors,
  });
  const liveDataUnavailable = Boolean(
    !data.profile
    && data.runtimes.length === 0
    && data.sourceErrors.coreProfile
    && data.sourceErrors.nettyRuntimeAgents
  );

  if (liveDataUnavailable) {
    return (
      <LiveDataUnavailable
        title={`Live data is unavailable for ${agentId}`}
        description="The agent detail page cannot verify the Core profile or Gateway runtime state. It will not infer setup status from missing data."
        details={Object.entries(data.sourceErrors).filter(([, value]) => value).map(([key, value]) => `${key}: ${value}`).join(' | ')}
        action={(
          <button type="button" onClick={() => void refresh()} className="rounded-xl border border-rose-200 bg-white px-3 py-2 text-xs font-black text-rose-700 hover:bg-rose-100">
            Retry Live Load
          </button>
        )}
      />
    );
  }

  const agentTenantId = firstNonBlankValue(
    data.profile?.tenantId,
    data.capabilityAssignments.find((item) => normalizeLifecycleStatus(item.status) !== 'REVOKED')?.tenantId,
  ) ?? '';

  const steps = buildSetupSteps(data);
  const completedSteps = steps.filter((step) => step.complete).length;
  const tabs: AdminTabItem[] = [
    { id: 'overview', label: t('agent.detail.tab.overview'), badge: <StatusBadge status={readinessStatus(data)} /> },
    { id: 'connection', label: t('agent.detail.tab.connection'), badge: <StatusBadge status={runtimeIsConnected(data) ? 'ONLINE' : 'OFFLINE'} /> },
    { id: 'capabilities', label: t('agent.detail.tab.capabilities'), badge: <span className="rounded-full bg-blue-100 px-2 py-0.5 text-[11px] font-bold text-blue-700">{approvedCapabilityCount(data)}</span> },
    { id: 'flows', label: 'Pool / Source Flow', badge: <span className="rounded-full bg-purple-100 px-2 py-0.5 text-[11px] font-bold text-purple-700">{data.agentPools?.length ?? 0}</span> },
    { id: 'tasks', label: t('agent.detail.tab.recentTasks'), badge: <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-bold text-slate-700">{data.tasks.length}</span> },
    { id: 'advanced', label: '活動紀錄' },
  ];

  function runWithConfirm(message: string, action: () => Promise<unknown> | unknown) {
    if (window.confirm(message)) void action();
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <Link href="/agents" className="text-sm font-semibold text-blue-600 hover:text-blue-700">← Back to Agents</Link>
          <h1 className="mt-2 text-2xl font-black text-slate-900">{data.profile?.agentName ?? agentId}</h1>
          <p className="mt-1 text-sm leading-6 text-slate-500">
            從單一頁面管理連線、Agent Pool、能力標籤、最近 Task 與活動紀錄。
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <DataSourceBadge source={dataSource} detail={hasSourceErrors ? 'Some live API calls failed' : 'Core + Gateway runtime'} />
          <RefreshButton refreshing={refreshing} lastUpdatedAt={lastUpdatedAt} onRefresh={refresh} />
        </div>
      </div>

      <CommandMessage message={commandMessage} />

      <div className="grid gap-3 md:grid-cols-6">
        <StatCard label="Readiness" value={readinessStatus(data)} tone={readinessTone(data)} />
        <StatCard label="Source" value={data.operationalView ? 'Operational View' : data.setupReadiness ? 'Core Backend' : 'Fallback UI'} tone={data.operationalView || data.setupReadiness ? 'good' : 'warn'} />
        <StatCard label="Setup" value={`${completedSteps} / ${steps.length}`} tone={completedSteps === steps.length ? 'good' : 'warn'} />
        <StatCard label="Runtime Ready" value={runtimeReadyStatus(data)} tone={runtimeReadyTone(data)} />
        <StatCard label="Agent Pools" value={data.agentPools?.length ?? 0} tone={taskContractTone(data)} />
        <StatCard label="Last Updated" value={formatDateTime(lastUpdatedAt)} />
      </div>

      <AdminTabLayout tabs={tabs} activeTab={activeTab} onTabChange={(tabId) => setActiveTab(tabId as AgentDetailTab)}>
        {activeTab === 'overview' ? <OverviewPanel data={data} setActiveTab={setActiveTab} /> : null}

        {activeTab === 'connection' ? (
          <ConnectionPanel
            data={data}
            agentId={agentId}
            refreshing={refreshing}
            onRefresh={refresh}
            onPing={() => void pingAgent()}
            onDisconnect={() => runWithConfirm(`Disconnect the current runtime session for ${agentId}?`, disconnectAgent)}
            onDisconnectAll={() => runWithConfirm(`Disconnect all runtime sessions for ${agentId}?`, disconnectAllAgentSessions)}
            onCreateOrActivateRuntimeBinding={() => runWithConfirm(`Create or activate runtime binding for ${agentId}?`, createRuntimeBindingFromCurrentRuntime)}
            onActivateRuntimeBinding={(bindingId) => runWithConfirm(`Activate runtime binding ${bindingId} for ${agentId}?`, () => activateRuntimeBinding(bindingId))}
          />
        ) : null}

        {activeTab === 'capabilities' ? (
          <div className="space-y-5">
            <Panel
              title="特殊能力"
              description="Capability 只表示 Agent 的特殊技術能力與查詢標籤。Phase 32-G 第一版派單由 Source Flow / Agent Pool 決定，不以 Capability 作為 gate。"
              action={<button type="button" onClick={() => setCapabilityDialogOpen(true)} className="rounded-xl bg-blue-700 px-4 py-2 text-sm font-black text-white hover:bg-blue-800">Assign Capability</button>}
            >
              <CapabilityList
                assignments={data.capabilityAssignments}
                profileCapabilities={data.profile?.capabilities}
                setupReadiness={data.setupReadiness}
                onApprove={(assignment) => runWithConfirm(`Approve capability ${assignment.capabilityCode}?`, () => approveAgentCapability(assignment.assignmentId ?? '', { operatorId: 'admin-ui', reason: 'Approved from Agent detail inline action.' }))}
                onSuspend={(assignment) => runWithConfirm(`Suspend capability ${assignment.capabilityCode}?`, () => suspendAgentCapability(assignment.assignmentId ?? '', { operatorId: 'admin-ui', reason: 'Suspended from Agent detail inline action.' }))}
                onResume={(assignment) => runWithConfirm(`Resume capability ${assignment.capabilityCode}?`, () => resumeAgentCapability(assignment.assignmentId ?? '', { operatorId: 'admin-ui', reason: 'Resumed from Agent detail inline action.' }))}
                onRevoke={(assignment) => runWithConfirm(`Revoke capability ${assignment.capabilityCode}?`, () => revokeAgentCapability(assignment.assignmentId ?? '', { operatorId: 'admin-ui', reason: 'Revoked from Agent detail inline action.' }))}
                onRemove={(assignment) => runWithConfirm(`Remove capability record ${assignment.capabilityCode}?`, () => removeAgentCapability(assignment.assignmentId ?? '', { operatorId: 'admin-ui', reason: 'Removed from Agent detail inline action after capability set adjustment.' }))}
                onRevokeAndRemove={(assignment) => runWithConfirm(`Revoke and remove ${assignment.capabilityCode}?`, async () => {
                  await revokeAgentCapability(assignment.assignmentId ?? '', { operatorId: 'admin-ui', reason: 'Revoked before removal from Agent detail inline action.' });
                  await removeAgentCapability(assignment.assignmentId ?? '', { operatorId: 'admin-ui', reason: 'Removed after revoke from Agent detail inline action.' });
                })}
              />
            </Panel>
          </div>
        ) : null}

        {activeTab === 'flows' ? <AgentDispatchFlowsPanel data={data} agentId={agentId} /> : null}


        {activeTab === 'tasks' ? (
          <Panel title="Recent Tasks" description="Recent Core task records assigned to this agent, including dispatch status and issue link when available.">
            <RecentTasks tasks={data.tasks} />
          </Panel>
        ) : null}

        {activeTab === 'advanced' ? <AdvancedAgentData data={data} /> : null}
      </AdminTabLayout>

      <QuickCapabilityDialog
        agentId={agentId}
        tenantId={agentTenantId}
        open={capabilityDialogOpen}
        onClose={() => setCapabilityDialogOpen(false)}
        existingCodes={activeCapabilityAssignments(data.capabilityAssignments).map((item) => item.capabilityCode)}
        requestAgentCapability={requestAgentCapability}
        onChanged={refresh}
      />


    </div>
  );
}


// Historical R5 verifier markers. Not rendered in the Stage 4 Agent UI.
