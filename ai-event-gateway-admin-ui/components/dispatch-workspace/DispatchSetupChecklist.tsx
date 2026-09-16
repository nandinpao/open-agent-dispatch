'use client';

import Link from 'next/link';
import { StatusBadge } from '@/components/common/StatusBadge';
import { Button } from '@/components/ui/Button';
import type {
  CoreAgentPoolView,
  CoreDispatchFlowAgentOptionView,
  CoreDispatchFlowView,
  CoreDispatchSimulationResponse,
  CoreEventIntakeDecisionResponse,
  CoreSourceSystem,
} from '@/lib/types/core';
import { flowDisplay, flowHealthIssues, poolDisplay } from './dispatchWorkspaceModel';

type ChecklistStatus = 'DONE' | 'ACTION_REQUIRED' | 'WAITING' | 'OPTIONAL';

type ChecklistItem = {
  id: string;
  title: string;
  description: string;
  status: ChecklistStatus;
  actionLabel?: string;
  actionHref?: string;
  onAction?: () => void;
};

function statusLabel(status: ChecklistStatus): string {
  switch (status) {
    case 'DONE': return 'DONE';
    case 'WAITING': return 'WAITING';
    case 'OPTIONAL': return 'OPTIONAL';
    default: return 'ACTION_REQUIRED';
  }
}

function itemTone(status: ChecklistStatus): string {
  switch (status) {
    case 'DONE': return 'border-emerald-200 bg-emerald-50';
    case 'WAITING': return 'border-blue-200 bg-blue-50';
    case 'OPTIONAL': return 'border-slate-200 bg-slate-50';
    default: return 'border-amber-200 bg-amber-50';
  }
}

function hasApprovedAgent(agents: CoreDispatchFlowAgentOptionView[]): boolean {
  return agents.some((agent) => {
    const approved = String(agent.approvalStatus ?? '').toUpperCase();
    return agent.enabled !== false && ['APPROVED', 'ACTIVE', 'READY'].includes(approved);
  });
}

function hasRuntimeReadyAgent(agents: CoreDispatchFlowAgentOptionView[], pool?: CoreAgentPoolView | null): boolean {
  if ((pool?.availableAgentCount ?? 0) > 0) return true;
  return agents.some((agent) => agent.selectable === true || (agent.runtimeConnected === true && agent.capacityAvailable !== false && agent.heartbeatHealthy !== false));
}

function hasPoolMembers(pool?: CoreAgentPoolView | null): boolean {
  if (!pool) return false;
  return (pool.memberCount ?? pool.members?.length ?? 0) > 0;
}

function simulationDone(result?: CoreDispatchSimulationResponse | null): boolean {
  if (!result) return false;
  return result.sideEffectFree === true && (result.dispatchable === true || result.manualOnly === true || result.status === 'READY' || result.status === 'MANUAL_ASSIGNMENT_REQUIRED');
}

function realEventDone(result?: CoreEventIntakeDecisionResponse | null): boolean {
  if (!result) return false;
  return Boolean(result.eventId || result.taskId || result.taskCreated || result.decisionType);
}

export function DispatchSetupChecklist({
  open,
  sourceSystems,
  pools,
  agents,
  selectedFlow,
  selectedPool,
  sourceError,
  flowError,
  poolError,
  agentError,
  simulationResult,
  realTestResult,
  onCreateSource,
  onCreateFlow,
  onToggleOpen,
}: Readonly<{
  open: boolean;
  sourceSystems: CoreSourceSystem[];
  pools: CoreAgentPoolView[];
  agents: CoreDispatchFlowAgentOptionView[];
  selectedFlow?: CoreDispatchFlowView | null;
  selectedPool?: CoreAgentPoolView | null;
  sourceError?: string | null;
  flowError?: string | null;
  poolError?: string | null;
  agentError?: string | null;
  simulationResult?: CoreDispatchSimulationResponse | null;
  realTestResult?: CoreEventIntakeDecisionResponse | null;
  onCreateSource: () => void;
  onCreateFlow: () => void;
  onToggleOpen: () => void;
}>) {
  const sourceReady = sourceSystems.length > 0 || Boolean(selectedFlow?.sourceSystem);
  const flowReady = Boolean(selectedFlow?.flowId);
  const classificationReady = (selectedFlow?.rules ?? []).some((rule) => rule.enabled !== false);
  const capabilityCount = (selectedFlow?.requiredCapabilities ?? selectedFlow?.requiredSkills ?? []).filter((item) => item.required !== false).length;
  const poolReady = Boolean(selectedPool?.poolId && selectedFlow?.defaultPoolId);
  const membersReady = hasPoolMembers(selectedPool);
  const agentReady = hasApprovedAgent(agents) || membersReady;
  const runtimeReady = hasRuntimeReadyAgent(agents, selectedPool);
  const configIssues = flowHealthIssues(selectedFlow, pools);
  const simulationReady = simulationDone(simulationResult);
  const realReady = realEventDone(realTestResult);
  const sourceLoadProblem = sourceError || flowError || poolError || agentError;

  const items: ChecklistItem[] = [
    {
      id: 'source-system',
      title: 'Source System',
      description: sourceReady ? `${sourceSystems.length || 1} Source System${(sourceSystems.length || 1) === 1 ? '' : 's'} available.` : 'Create the business source that will send work to OpenDispatch.',
      status: sourceReady ? 'DONE' : 'ACTION_REQUIRED',
      actionLabel: sourceReady ? undefined : 'Create Source System',
      onAction: sourceReady ? undefined : onCreateSource,
    },
    {
      id: 'classification',
      title: 'Flow & Classification',
      description: !flowReady ? 'Create a Dispatch Flow for the Source System.' : classificationReady ? `${flowDisplay(selectedFlow)} has an active classification rule.` : 'Add a classification rule for known work. Unmatched work stays in Triage.',
      status: flowReady && classificationReady && configIssues.length === 0 ? 'DONE' : flowReady ? 'WAITING' : 'ACTION_REQUIRED',
      actionLabel: flowReady ? undefined : 'Create Source Flow',
      onAction: flowReady ? undefined : onCreateFlow,
    },
    {
      id: 'capability',
      title: 'Required Capability',
      description: capabilityCount ? `${capabilityCount} Canonical Capabilit${capabilityCount === 1 ? 'y is' : 'ies are'} required by this Flow.` : 'Add at least one Required Capability. Pool membership defines where to search; it does not prove that an Agent is qualified for the Task.',
      status: capabilityCount ? 'DONE' : 'ACTION_REQUIRED',
      actionLabel: 'Review Capabilities',
      actionHref: '#dispatch-workspace-capabilities',
    },
    {
      id: 'pool',
      title: 'Agent Pool',
      description: poolReady && membersReady ? `${poolDisplay(selectedPool, selectedFlow?.defaultPoolId)} has Agent members.` : !agentReady ? 'Create and approve an Agent, then add it to an Agent Pool.' : 'Choose a default Agent Pool and add one or more approved Agents.',
      status: poolReady && membersReady ? 'DONE' : poolReady ? 'WAITING' : 'ACTION_REQUIRED',
      actionLabel: !agentReady ? 'Open Agents' : undefined,
      actionHref: !agentReady ? '/agents' : undefined,
    },
    {
      id: 'preview',
      title: 'Safe Preview',
      description: simulationReady ? 'The saved Flow passed a side-effect-free dispatch preview.' : 'Preview the saved Flow before activation. No production Task or Assignment is created.',
      status: simulationReady ? 'DONE' : poolReady && membersReady ? 'WAITING' : 'OPTIONAL',
      actionLabel: 'Open Test & Activate',
      actionHref: '#dispatch-workspace-test',
    },
    {
      id: 'live-test',
      title: 'Activate & Live Test',
      description: realReady ? `The governed test entered production intake${realTestResult?.taskId ? ` as Task ${realTestResult.taskId}` : ''}.` : runtimeReady ? 'Activate the Flow, check Live Readiness, then send one governed test event.' : 'Confirm Agent runtime readiness before the governed live test.',
      status: realReady ? 'DONE' : simulationReady ? 'WAITING' : 'OPTIONAL',
      actionLabel: 'Open Live Test',
      actionHref: '#dispatch-workspace-real-test',
    },
  ];

  const doneCount = items.filter((item) => item.status === 'DONE').length;
  const actionRequiredCount = items.filter((item) => item.status === 'ACTION_REQUIRED').length;
  const requiredItems = items.filter((item) => item.status !== 'OPTIONAL');
  const setupReady = requiredItems.length > 0 && requiredItems.every((item) => item.status === 'DONE') && !sourceLoadProblem;
  const summaryStatus = setupReady ? 'READY' : actionRequiredCount ? 'NOT_READY' : 'IN_PROGRESS';

  return (
    <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm" id="dispatch-workspace-setup-check">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <div className="text-xs font-black uppercase tracking-wide text-purple-700">Guided Dispatch Setup</div>
          <h2 className="mt-1 text-xl font-black text-slate-950">Follow the business setup path</h2>
          <p className="mt-2 max-w-4xl text-sm leading-6 text-slate-600">
            Source System → Flow & Classification → Required Capability → Agent Pool → Safe Preview → Activate & Live Test. Advanced routing and migration evidence are not required for normal setup.
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <StatusBadge status={summaryStatus} />
          <Button size="xs" onClick={onToggleOpen}>{open ? 'Hide steps' : 'Show steps'}</Button>
        </div>
      </div>

      {sourceLoadProblem ? (
        <div className="mt-4 rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-bold leading-6 text-rose-900">
          Some setup data could not be loaded: {[sourceError, flowError, poolError, agentError].filter(Boolean).join('; ')}
        </div>
      ) : null}

      {open ? (
        <div className="mt-5 grid gap-3 xl:grid-cols-6">
          {items.map((item, index) => (
            <div key={item.id} className={`rounded-2xl border p-4 ${itemTone(item.status)}`}>
              <div className="flex items-start justify-between gap-3">
                <div className="rounded-full bg-white px-2.5 py-1 text-xs font-black text-slate-600">{index + 1}</div>
                <StatusBadge status={statusLabel(item.status)} />
              </div>
              <h3 className="mt-3 text-sm font-black text-slate-950">{item.title}</h3>
              <p className="mt-2 text-xs font-bold leading-5 text-slate-600">{item.description}</p>
              {item.actionHref ? (
                <Link className="mt-3 inline-flex rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-black text-slate-700 hover:bg-slate-50" href={item.actionHref}>{item.actionLabel}</Link>
              ) : item.onAction ? (
                <Button size="xs" className="mt-3" onClick={item.onAction}>{item.actionLabel}</Button>
              ) : null}
            </div>
          ))}
        </div>
      ) : (
        <div className="mt-4 rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm font-bold text-slate-600">
          {doneCount} of {items.length} setup steps currently complete. Open the checklist for the next action. 
        </div>
      )}
    </section>
  );
}
