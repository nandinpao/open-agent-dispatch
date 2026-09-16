'use client';

import Link from 'next/link';
import { AgentBlockingCard } from '@/components/phase7d/AgentBlockingCard';
import { StatusBadge } from '@/components/common/StatusBadge';
import type { AgentDetailBundle } from '@/hooks/useAgentDetail';
import { deriveAgentBlockingExperience } from '@/lib/phase7d/issueAgentUx';
import type { CoreAgentProfile } from '@/lib/types/core';
import { Panel, type AgentDetailTab } from '@/components/agents/AgentDetailUi';

export function isCredentialReady(profile?: CoreAgentProfile): boolean {
  const status = profile?.credential?.credentialStatus;
  return status === 'ACTIVE' || status === 'ROTATE_REQUIRED';
}

export function isProfileReady(profile?: CoreAgentProfile): boolean {
  return Boolean(profile && profile.approvalStatus === 'APPROVED' && profile.enabled !== false);
}

export function runtimeIsConnected(data: AgentDetailBundle): boolean {
  const connectedCount = data.row.runtimeSummary?.connectedCount;
  if (typeof connectedCount === 'number') return connectedCount > 0;
  return data.runtime?.connected === true;
}

export function approvedCapabilityCount(data: AgentDetailBundle): number {
  const governed = (data.capabilityAssignments ?? []).filter((item) => item.status === 'APPROVED').length;
  const profileCapabilities = (data.profile?.capabilities ?? []).filter((item) => item.enabled !== false).length;
  return Math.max(governed, profileCapabilities);
}

function allowedWorkReady(data: AgentDetailBundle): boolean {
  return (data.profile?.authorizationScopes ?? []).some((scope) => scope.enabled !== false);
}

function routingReady(data: AgentDetailBundle): boolean {
  return (data.agentPools ?? []).length > 0 || (data.dispatchFlows ?? []).some((flow) => String(flow.status ?? '').toUpperCase() === 'ACTIVE');
}

export function agentReadinessStatus(data: AgentDetailBundle): string {
  if (!isProfileReady(data.profile)) return 'APPROVAL_REQUIRED';
  if (!isCredentialReady(data.profile)) return 'CREDENTIAL_REQUIRED';
  if (!runtimeIsConnected(data)) return 'OFFLINE';
  if (!allowedWorkReady(data)) return 'ALLOWED_WORK_REQUIRED';
  if (!routingReady(data)) return 'FLOW_REQUIRED';
  return 'READY';
}

export function agentReadinessTone(data: AgentDetailBundle): 'good' | 'warn' | 'bad' {
  const status = agentReadinessStatus(data);
  if (status === 'READY') return 'good';
  if (status === 'FLOW_REQUIRED') return 'warn';
  return 'bad';
}

type SetupAction = {
  key: string;
  label: string;
  description: string;
  ready: boolean;
  tab: AgentDetailTab;
  action: string;
};

function setupActions(data: AgentDetailBundle): SetupAction[] {
  return [
    { key: 'profile', label: 'Approve Agent', description: 'Approve and enable this Agent before it can receive company work.', ready: isProfileReady(data.profile), tab: 'advanced', action: 'Approve Agent' },
    { key: 'credential', label: 'Connection credential', description: 'Give the Agent a valid credential for connecting to OpenDispatch.', ready: isCredentialReady(data.profile), tab: 'advanced', action: 'Set credential' },
    { key: 'runtime', label: 'Agent connection', description: 'Confirm the Agent is online and connected to OpenDispatch.', ready: runtimeIsConnected(data), tab: 'advanced', action: 'Check connection' },
    { key: 'allowed-work', label: 'Allowed work', description: 'Choose which active Flow work this Agent is allowed to execute.', ready: allowedWorkReady(data), tab: 'dispatch-access', action: 'Choose work' },
    { key: 'routing', label: 'Work routing', description: 'Make sure an active Dispatch Flow or Agent Pool can route work to this Agent.', ready: routingReady(data), tab: 'pools', action: 'Set routing' },
  ];
}

function capabilityNames(data: AgentDetailBundle): string[] {
  const fromAssignments = (data.capabilityAssignments ?? [])
    .filter((item) => item.status === 'APPROVED')
    .map((item) => String(item.capabilityName ?? item.capabilityCode ?? '').trim())
    .filter(Boolean);
  const fromProfile = (data.profile?.capabilities ?? [])
    .filter((item) => item.enabled !== false)
    .map((item) => String(item.capabilityCode ?? '').trim())
    .filter(Boolean);
  return [...new Set([...fromAssignments, ...fromProfile])];
}

function activeFlowNames(data: AgentDetailBundle): string[] {
  return (data.dispatchFlows ?? [])
    .filter((flow) => String(flow.status ?? '').toUpperCase() === 'ACTIVE')
    .map((flow) => String(flow.flowName ?? flow.flowCode ?? flow.sourceSystem ?? '').trim())
    .filter(Boolean);
}

export function AgentOverviewWorkspace({ data, setActiveTab }: Readonly<{ data: AgentDetailBundle; setActiveTab: (tab: AgentDetailTab) => void }>) {
  const actions = setupActions(data);
  const completed = actions.filter((item) => item.ready).length;
  const firstMissing = actions.find((item) => !item.ready);
  const activeBinding = (data.runtimeBindings ?? []).some((binding) => String(binding.bindingStatus ?? '').toUpperCase() === 'ACTIVE');
  const activeFlows = activeFlowNames(data);
  const capabilities = capabilityNames(data);
  const dispatchAccessRules = (data.profile?.authorizationScopes ?? []).filter((scope) => scope.enabled !== false).length;
  const blockingExperience = deriveAgentBlockingExperience({
    hasProfile: Boolean(data.profile),
    approvalStatus: data.profile?.approvalStatus,
    enabled: data.profile?.enabled,
    riskStatus: data.profile?.riskStatus,
    credentialStatus: data.profile?.credential?.credentialStatus,
    credentialExpiresAt: data.profile?.credential?.expiresAt,
    runtimeConnected: Boolean(data.runtime?.connected),
    runtimeBindingActive: activeBinding,
    dispatchAccessRuleCount: dispatchAccessRules,
    activeDispatchFlowCount: activeFlows.length,
    availableSlots: data.runtimeLoad?.availableSlots ?? data.runtimeDescriptor?.availableSlots ?? data.runtime?.availableSlots,
    draining: data.runtimeLoad?.draining ?? data.runtimeDescriptor?.draining ?? data.runtime?.draining,
  });

  return (
    <div className="space-y-5">
      <section className={`rounded-3xl border p-5 shadow-sm ${agentReadinessTone(data) === 'good' ? 'border-emerald-200 bg-emerald-50' : agentReadinessTone(data) === 'warn' ? 'border-amber-200 bg-amber-50' : 'border-rose-200 bg-rose-50'}`}>
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <div className="text-xs font-black uppercase tracking-wide text-slate-600">Agent status</div>
            <h2 className="mt-1 text-2xl font-black text-slate-950">{agentReadinessStatus(data) === 'READY' ? 'Ready to receive work' : 'Needs setup'}</h2>
            <p className="mt-2 max-w-4xl text-sm leading-6 text-slate-700">{firstMissing?.description ?? 'This Agent is approved, connected, allowed to execute work, and available to active routing.'}</p>
          </div>
          <StatusBadge status={agentReadinessStatus(data)} />
        </div>

        <div className="mt-5 grid gap-3 lg:grid-cols-3">
          <div className="rounded-2xl border border-white/70 bg-white/80 p-4">
            <div className="text-xs font-black uppercase tracking-wide text-slate-500">Can do</div>
            {capabilities.length ? <ul className="mt-2 space-y-1 text-sm font-bold text-slate-900">{capabilities.slice(0, 4).map((name) => <li key={name}>✓ {name}</li>)}</ul> : <p className="mt-2 text-sm text-slate-600">No approved capability yet.</p>}
          </div>
          <div className="rounded-2xl border border-white/70 bg-white/80 p-4">
            <div className="text-xs font-black uppercase tracking-wide text-slate-500">Receives work from</div>
            {activeFlows.length ? <ul className="mt-2 space-y-1 text-sm font-bold text-slate-900">{activeFlows.slice(0, 4).map((name) => <li key={name}>• {name}</li>)}</ul> : <p className="mt-2 text-sm text-slate-600">No active Dispatch Flow yet.</p>}
          </div>
          <div className="rounded-2xl border border-white/70 bg-white/80 p-4">
            <div className="text-xs font-black uppercase tracking-wide text-slate-500">Next step</div>
            <p className="mt-2 text-sm font-bold text-slate-900">{firstMissing ? firstMissing.label : 'No setup action required'}</p>
            {firstMissing ? <button type="button" onClick={() => setActiveTab(firstMissing.tab)} className="mt-3 rounded-xl bg-indigo-700 px-4 py-2 text-sm font-black text-white hover:bg-indigo-800">{firstMissing.action}</button> : null}
          </div>
        </div>

        <div className="mt-4 flex flex-wrap gap-2">
          <button type="button" onClick={() => setActiveTab('work')} className="rounded-xl border border-slate-300 bg-white px-4 py-2 text-sm font-black text-slate-700 hover:bg-slate-50">Recent work</button>
          <Link href={`/dispatch-flows?agentId=${encodeURIComponent(data.profile?.agentId ?? data.runtime?.agentId ?? '')}`} className="rounded-xl border border-purple-200 bg-white px-4 py-2 text-sm font-black text-purple-700 hover:bg-purple-50">Dispatch Flows</Link>
        </div>
      </section>

      <AgentBlockingCard experience={blockingExperience} agentId={data.profile?.agentId ?? data.runtime?.agentId} />

      <Panel title="Setup checklist" description="Only the decisions needed to make this Agent usable are shown here. Technical IDs, runtime evidence, and repair tools remain under Advanced.">
        <div className="space-y-3">
          {actions.map((item) => (
            <div key={item.key} className="flex flex-col gap-3 rounded-2xl border border-slate-200 bg-slate-50 p-4 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <div className="flex items-center gap-2"><StatusBadge status={item.ready ? 'READY' : 'MISSING'} /><span className="font-black text-slate-900">{item.label}</span></div>
                <p className="mt-1 text-sm leading-6 text-slate-600">{item.description}</p>
              </div>
              <button type="button" onClick={() => setActiveTab(item.tab)} className="shrink-0 rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-black text-slate-700 hover:bg-slate-50">{item.ready ? 'Review' : item.action}</button>
            </div>
          ))}
        </div>
        <div className="mt-4 text-xs font-semibold text-slate-500">Setup progress: {completed} / {actions.length}. Configure Redmine only when this Agent&apos;s work actually needs external issue tracking.</div>
      </Panel>
    </div>
  );
}
