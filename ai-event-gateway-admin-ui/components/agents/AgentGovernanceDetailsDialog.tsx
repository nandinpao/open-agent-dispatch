'use client';

import { useState } from 'react';
import { JsonViewer } from '@/components/common/JsonViewer';
import { StatusBadge } from '@/components/common/StatusBadge';
import {
  getAgentConnectionStatus,
  getAgentWorkloadStatus,
  getHeartbeatAgeMs,
  getHeartbeatStatus,
  getRowReviewTimestamp,
  getRuntimeBacklogLabel,
  getRuntimeCapabilityLabel,
  getRuntimeCapacityLabel,
  getRuntimeLatencyLabel,
  getSuccessfulConnectedAt
} from '@/lib/agents/agentRuntimeDisplay';
import { deriveAgentRuntimeWarning } from '@/lib/dashboard/agentMerge';
import type { AgentDashboardRow } from '@/lib/types/dashboard';
import { formatDateTime, formatDurationMs } from '@/lib/utils/format';

interface AgentGovernanceDetailsDialogProps {
  row: AgentDashboardRow;
  triggerLabel?: string;
}

function capabilityRows(row: AgentDashboardRow): string[] {
  return (row.profile?.capabilities ?? []).map((capability) => capability.capabilityCode).filter(Boolean);
}

function scopeRows(row: AgentDashboardRow): string[] {
  return (row.profile?.authorizationScopes ?? [])
    .map((scope) => [scope.systemCode ?? '*', scope.taskType ?? '*', scope.siteCode].filter(Boolean).join('/'))
    .filter(Boolean);
}

function Field({ label, value }: Readonly<{ label: string; value?: string | number | boolean | null }>) {
  return (
    <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
      <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">{label}</div>
      <div className="mt-1 break-words text-sm font-medium text-slate-900">{value === undefined || value === null || value === '' ? '-' : String(value)}</div>
    </div>
  );
}

export function AgentGovernanceDetailsDialog({ row, triggerLabel = 'Details' }: Readonly<AgentGovernanceDetailsDialogProps>) {
  const [open, setOpen] = useState(false);
  const review = getRowReviewTimestamp(row);
  const warning = deriveAgentRuntimeWarning(row);
  const capabilities = capabilityRows(row);
  const scopes = scopeRows(row);

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="rounded-lg border border-slate-200 px-2 py-1 text-xs font-semibold text-slate-700 hover:bg-slate-50"
      >
        {triggerLabel}
      </button>

      {open ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/45 p-4" role="dialog" aria-modal="true">
          <div className="max-h-[90vh] w-full max-w-5xl overflow-y-auto rounded-2xl bg-white p-5 shadow-2xl">
            <div className="flex items-start justify-between gap-4 border-b border-slate-100 pb-4">
              <div>
                <h2 className="text-lg font-bold text-slate-900">Agent Governance Details</h2>
                <p className="mt-1 text-sm text-slate-500">{row.agentId}</p>
              </div>
              <button type="button" onClick={() => setOpen(false)} className="rounded-lg px-3 py-1 text-sm font-semibold text-slate-500 hover:bg-slate-100">Close</button>
            </div>

            {warning ? <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm font-semibold text-amber-800">{warning}</div> : null}

            <div className="mt-4 grid gap-4 lg:grid-cols-3">
              <section className="space-y-3 rounded-2xl border border-slate-200 p-4">
                <div className="text-sm font-bold text-slate-900">Core Profile</div>
                <div className="flex flex-wrap gap-2">
                  <StatusBadge status={row.profile?.approvalStatus ?? 'NO_CORE_PROFILE'} />
                  <StatusBadge status={row.profile ? (row.profile.enabled ? 'ENABLED' : 'DISABLED') : 'NO_CORE_PROFILE'} />
                  <StatusBadge status={row.profile?.riskStatus ?? 'UNKNOWN'} />
                </div>
                <Field label="Agent Name" value={row.profile?.agentName} />
                <Field label="Agent Type" value={row.profile?.agentType} />
                <Field label="Tenant" value={row.profile?.tenantId} />
                <Field label="Owner Team" value={row.profile?.ownerTeam} />
                <Field label="Credential" value={row.profile?.credential?.credentialStatus ?? 'MISSING'} />
                <Field label="Profile Updated" value={formatDateTime(row.profile?.updatedAt)} />
              </section>

              <section className="space-y-3 rounded-2xl border border-slate-200 p-4">
                <div className="text-sm font-bold text-slate-900">Runtime</div>
                <div className="flex flex-wrap gap-2">
                  <StatusBadge status={getAgentConnectionStatus(row.runtime)} />
                  <StatusBadge status={getAgentWorkloadStatus(row.runtime)} />
                  <StatusBadge status={getHeartbeatStatus(row.runtime)} />
                </div>
                <Field label="Gateway Node" value={row.runtime?.gatewayNodeId ?? row.runtime?.nodeId} />
                <Field label="Session / Connection" value={row.runtime?.sessionId ?? row.runtime?.connectionId} />
                <Field label="Transport" value={row.runtime?.transport} />
                <Field label="Authorization" value={row.runtime?.authorizationState} />
                <Field label="Successful Connected At" value={formatDateTime(getSuccessfulConnectedAt(row.runtime))} />
                <Field label="Last Heartbeat" value={formatDateTime(row.runtime?.lastHeartbeatAt ?? row.runtime?.lastSeenAt)} />
                <Field label="Heartbeat Age" value={formatDurationMs(getHeartbeatAgeMs(row.runtime))} />
                <Field label="Latency" value={getRuntimeLatencyLabel(row.runtime)} />
                <Field label="Capacity" value={getRuntimeCapacityLabel(row.runtime)} />
                <Field label="Runtime Backlog" value={getRuntimeBacklogLabel(row.runtime)} />
                <Field label="Capability / Plugin" value={getRuntimeCapabilityLabel(row.runtime)} />
              </section>

              <section className="space-y-3 rounded-2xl border border-slate-200 p-4">
                <div className="text-sm font-bold text-slate-900">Enrollment / Review</div>
                <div className="flex flex-wrap gap-2">
                  <StatusBadge status={row.enrollment?.status ?? 'NO_ENROLLMENT'} />
                </div>
                <Field label="Enrollment ID" value={row.enrollment?.enrollmentId} />
                <Field label="Submitted At" value={formatDateTime(row.enrollment?.submittedAt)} />
                <Field label={review.label} value={formatDateTime(review.value)} />
                <Field label="Reviewed By" value={row.enrollment?.reviewedBy} />
                <Field label="Remote Address" value={row.enrollment?.remoteAddress ?? row.runtime?.remoteAddress} />
                <Field label="Review Comment" value={row.enrollment?.reviewComment} />
              </section>
            </div>

            <div className="mt-4 grid gap-4 lg:grid-cols-2">
              <section className="rounded-2xl border border-slate-200 p-4">
                <div className="text-sm font-bold text-slate-900">Capabilities</div>
                <div className="mt-3 flex flex-wrap gap-2">
                  {capabilities.length > 0 ? capabilities.map((capability) => <StatusBadge key={capability} status={capability} />) : <span className="text-sm text-slate-500">-</span>}
                </div>
              </section>
              <section className="rounded-2xl border border-slate-200 p-4">
                <div className="text-sm font-bold text-slate-900">Authorization Scopes</div>
                <div className="mt-3 flex flex-wrap gap-2">
                  {scopes.length > 0 ? scopes.map((scope) => <StatusBadge key={scope} status={scope} />) : <span className="text-sm text-slate-500">-</span>}
                </div>
              </section>
            </div>

            <div className="mt-4 rounded-2xl border border-slate-200 p-4">
              <div className="text-sm font-bold text-slate-900">Raw merged row</div>
              <div className="mt-3"><JsonViewer value={row} /></div>
            </div>
          </div>
        </div>
      ) : null}
    </>
  );
}
