'use client';

import Link from 'next/link';
import { useState } from 'react';
import { AgentCredentialIssueDialog } from '@/components/agents/AgentCredentialIssueDialog';
import { AgentEnrollmentReviewDialog } from '@/components/agents/AgentEnrollmentReviewDialog';
import { AgentProfileEditDialog } from '@/components/agents/AgentProfileEditDialog';
import { EmptyState } from '@/components/common/EmptyState';
import { StatusBadge } from '@/components/common/StatusBadge';
import type { AgentDetailBundle } from '@/hooks/useAgentDetail';
import type { CoreAgentConnectionRepairActionResult, CoreAgentRuntimeBinding } from '@/lib/types/core';
import { formatDateTime } from '@/lib/utils/format';
import { InlineManageLink, Panel, StatCard, managementHref } from '@/components/agents/AgentDetailUi';
import { isCredentialReady, runtimeIsConnected } from '@/components/agents/AgentOverviewWorkspace';
import { RepairActionsGrid } from '@/components/agents/AgentRepairActions';

export function AdvancedAgentData({ data }: Readonly<{ data: AgentDetailBundle }>) {
  return (
    <div className="space-y-5">
      <Panel title="Activity" description="Review recent security and runtime events for this Agent.">
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
        ) : <EmptyState title="No recent activity" description="No Agent security or runtime events are available." />}
      </Panel>
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
              <div className="text-sm font-black text-emerald-950">No current runtime authorization failure</div>
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
                  {step.severity ? <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-black uppercase text-slate-600">{step.severity}</span> : null}
                </div>
                {step.description ? <p className="mt-2 text-xs leading-5 text-slate-600">{step.description}</p> : null}
                {step.action ? <div className="mt-2 text-xs font-black text-slate-800">Action: {step.action}</div> : null}
                {step.command ? <pre className="mt-3 overflow-auto whitespace-pre-wrap rounded-xl bg-slate-950 p-3 text-xs leading-5 text-slate-100">{step.command}</pre> : null}
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
  governanceReady,
  onCreateOrActivate,
  onActivate,
}: Readonly<{
  bindings: CoreAgentRuntimeBinding[];
  governanceReady: boolean;
  onCreateOrActivate: () => void;
  onActivate: (bindingId: string) => void;
}>) {
  const activeBinding = bindings.find((binding) => String(binding.bindingStatus ?? '').toUpperCase() === 'ACTIVE');
  return (
    <Panel
      title="Runtime Binding"
      description="Core requires an ACTIVE Runtime Binding before an online runtime can receive dispatch assignments. Runtime online status alone is telemetry; the active binding grants dispatch authority."
      action={<button type="button" onClick={onCreateOrActivate} disabled={!governanceReady} title={!governanceReady ? "Approve Agent Governance before activating dispatch authority" : undefined} className="rounded-lg bg-blue-600 px-3 py-2 text-xs font-black text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-slate-300">Create / Activate Binding</button>}
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
      ) : governanceReady ? (
        <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4">
          <div className="text-xs font-black uppercase tracking-wide text-rose-700">Runtime binding is not active</div>
          <p className="mt-2 text-sm leading-6 text-rose-900">Agent Governance is approved, but Core has not activated the Agent + runtime binding required for dispatch authority. Create or activate the binding, then refresh readiness.</p>
        </div>
      ) : (
        <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4">
          <div className="text-xs font-black uppercase tracking-wide text-amber-700">Governance approval required first</div>
          <p className="mt-2 text-sm leading-6 text-amber-900">This runtime is visible for discovery, but dispatch authority must remain inactive until Agent Governance is approved with an eligible Business Owner, Department, Responsibility and credential.</p>
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

export function ConnectionPanel({
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
        action={<InlineManageLink href={managementHref('/agents/runtime', agentId)}>Manage Gateways</InlineManageLink>}
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
        governanceReady={Boolean(profile?.approvalStatus === 'APPROVED' && profile.enabled !== false)}
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
            <div className="text-xs font-black uppercase tracking-wide text-indigo-700">Core-approved capability qualification</div>
            <p className="mt-1 text-xs leading-5 text-indigo-900">Core APPROVED capability assignments are the blocking qualification authority for Task Required Capabilities inside the selected Agent Pool. The Agent does not need to self-report these values at startup; runtime-reported capability observations are diagnostics only. Startup still requires identity, credential, gateway URL, heartbeat and capacity.</p>
            <pre className="mt-3 overflow-auto rounded-xl bg-slate-950 p-3 text-xs leading-5 text-slate-100">{backendStartCommand.expectedCapabilities.join(',')}</pre>
          </div>
        ) : null}
        {commandVariants.length > 0 ? (
          <div className="mt-4 grid gap-3 lg:grid-cols-2">
            {commandVariants.map((entry) => (
              <details key={entry.label} className="rounded-2xl border border-slate-200 bg-slate-50 p-3">
                <summary className="cursor-pointer text-xs font-black text-slate-700">{entry.label}</summary>
                <pre className="mt-2 overflow-auto whitespace-pre-wrap rounded-xl bg-slate-950 p-3 text-xs leading-5 text-slate-100">{entry.value}</pre>
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
                    {step.severity ? <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs font-black uppercase text-amber-700">{step.severity}</span> : null}
                  </div>
                  {step.description ? <p className="mt-1 text-xs leading-5 text-amber-800">{step.description}</p> : null}
                  {step.action ? <div className="mt-2 text-xs font-black text-amber-900">Action: {step.action}</div> : null}
                  {step.command ? <pre className="mt-2 overflow-auto rounded-xl bg-slate-950 p-3 text-xs leading-5 text-slate-100">{step.command}</pre> : null}
                </div>
              ))}
            </div>
          </div>
        ) : null}
      </Panel>
    </div>
  );
}


