import Link from 'next/link';
import { HumanizedCode } from '@/components/common/HumanizedCode';
import { StatusBadge } from '@/components/common/StatusBadge';
import type { TaskDispatchDashboardRow } from '@/lib/dashboard/taskDispatchMerge';
import { beginnerToneClass, taskBeginnerHeadline } from '@/lib/dispatch-readiness/beginnerWorkflow';
import { beginnerSkillDescription, beginnerSkillLabel, normalizeCode } from '@/lib/dispatch-readiness/labels';
import { buildDispatchLifecycleSummary } from '@/lib/tasks/dispatchLifecycle';

function toneToClass(tone: 'ok' | 'waiting' | 'blocked' | 'done'): string {
  if (tone === 'done') return beginnerToneClass('done');
  if (tone === 'ok') return beginnerToneClass('ok');
  if (tone === 'blocked') return beginnerToneClass('blocked');
  return beginnerToneClass('waiting');
}

function dot(status: string): string {
  if (status === 'done') return '✅';
  if (status === 'current') return '⏳';
  if (status === 'failed') return '❌';
  if (status === 'blocked') return '⛔';
  return '○';
}

export function BeginnerTaskFlowPanel({ row }: Readonly<{ row: TaskDispatchDashboardRow }>) {
  const task = row.task;
  const headline = taskBeginnerHeadline(row);
  const lifecycle = buildDispatchLifecycleSummary(row);
  const required = task.requiredCapabilities ?? [];
  const selectedAgent = task.assignedAgentId;

  return (
    <section className="rounded-2xl border border-indigo-100 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
        <div>
          <div className="text-xs font-black uppercase tracking-wide text-indigo-500">Beginner Task View</div>
          <h2 className="mt-1 text-xl font-black text-slate-950">{headline.title}</h2>
          <p className="mt-2 max-w-4xl text-sm leading-6 text-slate-600">{headline.nextAction}</p>
        </div>
        <div className={`rounded-2xl border px-4 py-3 text-sm font-bold ${toneToClass(headline.tone)}`}>
          <div>Current status:{headline.tone === 'done' ? 'Completed' : headline.tone === 'blocked' ? 'Action required' : headline.tone === 'ok' ? 'In progress' : 'Waiting'}</div>
          <div className="mt-1 text-xs font-normal opacity-80">raw: task={task.status} dispatch={task.dispatchExecutionStatus ?? task.dispatchStatus ?? '-'}</div>
        </div>
      </div>

      <div className="mt-5 grid gap-4 xl:grid-cols-[1fr_1fr_1fr]">
        <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
          <div className="text-sm font-black text-slate-900">Required Capabilities</div>
          <div className="mt-3 space-y-2">
            {required.length > 0 ? required.map((capability) => (
              <div key={capability} className="rounded-xl border border-white bg-white p-3 shadow-sm">
                <div className="font-bold text-slate-900">{beginnerSkillLabel(capability)}</div>
                <div className="mt-1 text-xs leading-5 text-slate-500">{beginnerSkillDescription(capability)}</div>
                <div className="mt-2"><HumanizedCode code={capability} type="skill" compact /></div>
              </div>
            )) : <div className="text-sm text-amber-700">No Required Capability is declared. A normal Dispatch Task cannot determine qualified Agents until this is configured.</div>}
          </div>
        </div>

        <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
          <div className="text-sm font-black text-slate-900">Selected Agent</div>
          {selectedAgent ? (
            <div className="mt-3 rounded-xl border border-white bg-white p-3 shadow-sm">
              <div className="font-bold text-slate-900"><Link href={`/agents/${encodeURIComponent(selectedAgent)}`} className="text-blue-700 hover:text-blue-800">{selectedAgent}</Link></div>
              <div className="mt-2 grid gap-2 text-xs text-slate-600">
                <div>Dispatch Request: {task.dispatchRequestId ?? 'Not created'}</div>
                <div>Next step: <HumanizedCode code={task.nextAction} type="status" compact /></div>
                <div>Delivery:<HumanizedCode code={task.dispatchDeliveryStatus ?? task.dispatchStatus} type="status" compact /></div>
              </div>
            </div>
          ) : (
            <div className="mt-3 rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">No Agent is assigned yet. Core is still evaluating eligibility, runtime state, and Task requirements.</div>
          )}
        </div>

        <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
          <div className="text-sm font-black text-slate-900">Agent  Task </div>
          <p className="mt-2 text-xs leading-5 text-slate-600">
            An Agent may appear IDLE while Core is waiting for Gateway delivery or callback evidence. Runtime status alone does not determine Task completion.
          </p>
          <div className="mt-3 flex flex-wrap gap-2">
            <StatusBadge status={task.callbackStatus ?? 'NO_CALLBACK'} />
            <HumanizedCode code={row.callbackRelay?.status ?? task.callbackStatus ?? 'WAIT_FOR_AGENT_RESULT'} type="status" compact />
          </div>
        </div>
      </div>

      <div className="mt-5 rounded-2xl border border-slate-200 bg-slate-50 p-4">
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <div className="text-sm font-black text-slate-900">Lifecycle evidence</div>
            <div className="mt-1 text-xs text-slate-500">Timeline Event Log </div>
          </div>
          <StatusBadge status={lifecycle.overallStatus} />
        </div>
        <div className="mt-4 grid gap-2 md:grid-cols-3 xl:grid-cols-6">
          {lifecycle.steps.map((step) => (
            <div key={step.id} className="rounded-xl border border-white bg-white p-3 shadow-sm">
              <div className="flex items-center gap-2 text-sm font-bold text-slate-800"><span>{dot(normalizeCode(step.status).toLowerCase())}</span><span>{step.title}</span></div>
              <div className="mt-1 text-xs leading-5 text-slate-500">{step.description}</div>
              <div className="mt-2"><HumanizedCode code={step.badge ?? step.status} type="status" compact /></div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
