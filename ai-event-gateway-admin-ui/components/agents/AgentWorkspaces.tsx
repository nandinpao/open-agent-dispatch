'use client';

import Link from 'next/link';
import { DispatchAssignmentEvidencePanel } from '@/components/dispatch-evidence/DispatchAssignmentEvidencePanel';
import { TaskLineageEvidencePanel } from '@/components/tasks/TaskLineageEvidencePanel';
import { EmptyState } from '@/components/common/EmptyState';
import { StatusBadge } from '@/components/common/StatusBadge';
import type { AgentDetailBundle } from '@/hooks/useAgentDetail';
import type { CoreTaskRuntimeView } from '@/lib/types/core';
import { formatDateTime } from '@/lib/utils/format';
import { Panel } from '@/components/agents/AgentDetailUi';

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
        description="Dispatch uses Source Flow → Agent Pool → Required Capability eligibility → Runtime Eligibility → Routing Score. Pool membership alone never substitutes for a Task-required Capability."
        action={<Link href="/dispatch-flows" className="rounded-xl bg-purple-700 px-4 py-2 text-sm font-black text-white hover:bg-purple-800">Manage in Dispatch</Link>}
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
                      <div className="mt-1 text-xs text-slate-500">{pool.sourceSystem ?? 'Shared'} · {pool.poolType ?? 'RESOLUTION'} · {pool.poolCode ?? '-'}</div>
                    </div>
                    <StatusBadge status={pool.status ?? relation?.memberStatus ?? 'ACTIVE'} />
                  </div>
                  <div className="mt-3 flex flex-wrap gap-2 text-xs font-bold text-slate-600">
                    <span className="rounded-full bg-white px-2 py-1">Strategy: {pool.selectionStrategy ?? 'LOWEST_LOAD'}</span>
                    <span className="rounded-full bg-white px-2 py-1">Members {pool.memberCount ?? pool.members?.length ?? 0}</span>
                    <span className="rounded-full bg-white px-2 py-1">Available: {pool.availableAgentCount ?? 0}</span>
                  </div>
                </Link>
              );
            })}
          </div>
        ) : (
          <EmptyState title="Not added to an Agent Pool" description="Open Dispatch, create or select an Agent Pool, and add this Agent as a Pool member." />
        )}
      </Panel>

      <Panel
        title="Related Dispatch Flows"
        description="Source Flows that currently reference an Agent Pool containing this Agent."
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
                    <span className="rounded-full bg-white px-2 py-1">Default Pool {flow.defaultPoolId ? 'Configuration' : 'Not configured'}</span>
                    <span className="rounded-full bg-white px-2 py-1">Rule {flow.rules?.length ?? flow.externalRuleCount ?? 0}</span>
                  </div>
                </Link>
              );
            })}
          </div>
        ) : (
          <EmptyState title="No related Dispatch Flows" description="Add this Agent to an Agent Pool used by a Source Flow." />
        )}
      </Panel>
    </div>
  );
}


export function AgentPoolsWorkspace({ data, agentId }: Readonly<{ data: AgentDetailBundle; agentId: string }>) {
  return <AgentDispatchFlowsPanel data={data} agentId={agentId} />;
}

export function AgentRecentWorkWorkspace({ data }: Readonly<{ data: AgentDetailBundle }>) {
  return (
    <div className="space-y-5">
      <Panel title="Recent Work" description="Recent Core Tasks assigned to this Agent. Open a Task for the full execution journey and evidence.">
        <RecentTasks tasks={data.tasks} />
      </Panel>
      <details className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <summary className="cursor-pointer text-sm font-black text-slate-800">Advanced task lineage evidence</summary>
        <div className="mt-5"><TaskLineageEvidencePanel evidence={data.taskLineage} error={data.sourceErrors.coreTaskLineage} /></div>
      </details>
    </div>
  );
}

