import Link from "next/link";
import { AggregatedEligibilityBlocker } from "@/components/agents/AggregatedEligibilityBlocker";
import { KeyValue } from "@/components/tasks/detail/TaskDetailPrimitives";
import type { CoreDispatchTimelineResponse, CoreTaskRuntimeView } from "@/lib/types/domains/task";
import { formatDateTime } from "@/lib/utils/format";
import {
  buildStandardDispatchTimeline,
  type StandardDispatchTimelineStep,
  type TaskDispatchDiagnosis,
} from "@/lib/tasks/dispatchLifecycle";
import {
  taskDiagnosisCategoryLabel,
  taskDiagnosisOwnerPlaneLabel,
  type TaskDiagnosisReadModel,
  type TaskDiagnosisSeverity,
} from "@/lib/tasks/taskDiagnosisReadModel";
import type { TaskRemediationCommandDefinition } from "@/lib/tasks/taskRemediationCommands";

function timelineStateClasses(state: StandardDispatchTimelineStep["state"]): string {
  if (state === "done") return "border-emerald-300 bg-emerald-50 text-emerald-900";
  if (state === "failed") return "border-rose-300 bg-rose-50 text-rose-900";
  if (state === "blocked") return "border-amber-300 bg-amber-50 text-amber-900";
  if (state === "current") return "border-blue-300 bg-blue-50 text-blue-900";
  return "border-slate-200 bg-white text-slate-500";
}
function diagnosisReadModelSeverityClasses(severity: TaskDiagnosisSeverity): string {
  if (severity === "success") return "border-emerald-200 bg-emerald-50 text-emerald-950";
  if (severity === "danger") return "border-rose-200 bg-rose-50 text-rose-950";
  if (severity === "warning") return "border-amber-200 bg-amber-50 text-amber-950";
  return "border-blue-200 bg-blue-50 text-blue-950";
}

function diagnosisReadModelStageClasses(status: TaskDiagnosisReadModel["timeline"][number]["status"]): string {
  if (status === "done") return "border-emerald-200 bg-emerald-50 text-emerald-900";
  if (status === "failed") return "border-rose-200 bg-rose-50 text-rose-900";
  if (status === "blocked") return "border-amber-200 bg-amber-50 text-amber-900";
  if (status === "current") return "border-blue-200 bg-blue-50 text-blue-900";
  return "border-slate-200 bg-white text-slate-500";
}


export function TaskDiagnosisReadModelPanel({ model }: Readonly<{ model: TaskDiagnosisReadModel }>) {
  const blocker = model.primaryBlocker;
  return (
    <section className={`rounded-3xl border p-6 shadow-sm ${diagnosisReadModelSeverityClasses(blocker.severity)}`}>
      <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
        <div className="max-w-4xl">
          <div className="text-xs font-black uppercase tracking-wide opacity-70">Task Diagnosis Read Model · {blocker.code}</div>
          <h2 className="mt-1 text-xl font-black">{blocker.userTitle}</h2>
          <p className="mt-2 text-sm font-semibold leading-6">{blocker.userDescription}</p>
          <div className="mt-4 flex flex-wrap gap-2 text-xs font-black">
            <span className="rounded-full bg-white/70 px-3 py-1">Stage: {model.currentStage}</span>
            <span className="rounded-full bg-white/70 px-3 py-1">Category: {taskDiagnosisCategoryLabel(blocker.category)}</span>
            <span className="rounded-full bg-white/70 px-3 py-1">Owner: {taskDiagnosisOwnerPlaneLabel(blocker.ownerPlane)}</span>
            <span className="rounded-full bg-white/70 px-3 py-1">Retryable: {blocker.retryable ? "Yes" : "No"}</span>
            {blocker.evidencePointer ? <span className="rounded-full bg-white/70 px-3 py-1">Evidence: {blocker.evidencePointer}</span> : null}
          </div>
        </div>
        <div className="flex min-w-64 flex-col gap-2">
          {blocker.recommendedActions.map((action) => action.href && action.enabled ? (
            <Link key={`${action.type}-${action.label}`} href={action.href} className="rounded-xl bg-slate-950 px-4 py-2 text-center text-sm font-black text-white hover:bg-slate-800">
              {action.label}
            </Link>
          ) : (
            <div key={`${action.type}-${action.label}`} className="rounded-xl border border-white/60 bg-white/70 px-4 py-2 text-sm font-bold">
              <div>{action.label}</div>
              <p className="mt-1 text-xs font-semibold opacity-75">{action.description}</p>
            </div>
          ))}
        </div>
      </div>

      <div className="mt-5 grid gap-3 md:grid-cols-2 xl:grid-cols-6">
        <KeyValue label="Flow" value={model.routingEvidence.flowId ? <Link href={`/dispatch-flows?flowId=${encodeURIComponent(model.routingEvidence.flowId)}`} className="text-blue-700 hover:underline">{model.routingEvidence.flowId}</Link> : "Not matched"} />
        <KeyValue label="Rule" value={model.routingEvidence.ruleId ?? "Default / Not matched"} />
        <KeyValue label="Pool" value={model.routingEvidence.targetPoolId ?? "Not determined"} />
        <KeyValue label="Eligible" value={`${model.poolEligibility.eligible} / ${model.poolEligibility.totalMembers}`} />
        <KeyValue label="Agent" value={model.routingEvidence.selectedAgentId ? <Link href={`/agents/${encodeURIComponent(model.routingEvidence.selectedAgentId)}`} className="text-blue-700 hover:underline">{model.routingEvidence.selectedAgentId}</Link> : "Not assigned"} />
        <KeyValue label="Delivery" value={model.delivery.latestLedgerDeliveryState ?? model.delivery.dispatchDeliveryStatus ?? model.delivery.dispatchStatus ?? "Unknown"} />
      </div>

      <div className="mt-5 grid gap-4 xl:grid-cols-[1.1fr_0.9fr]">
        <div className="rounded-2xl border border-white/60 bg-white/70 p-4">
          <div className="text-xs font-black uppercase tracking-wide opacity-70">Unified Timeline · Event → Task → Routing → Assignment → Delivery → ACK → Result → Retry → Manual Action → Issue → Completion</div>
          <ol className="mt-3 grid gap-2 md:grid-cols-2">
            {model.timeline.map((step) => (
              <li key={`${step.stage}-${step.occurredAt ?? step.label}`} className={`rounded-xl border p-3 ${diagnosisReadModelStageClasses(step.status)}`}>
                <div className="flex items-center justify-between gap-2">
                  <span className="text-xs font-black uppercase tracking-wide">{step.stage}</span>
                  <span className="text-xs font-black uppercase">{step.status}</span>
                </div>
                <div className="mt-1 text-sm font-black">{step.label}</div>
                <p className="mt-1 text-xs font-semibold leading-5 opacity-80">{step.detail}</p>
                {step.occurredAt ? <div className="mt-2 text-xs font-semibold opacity-70">{formatDateTime(step.occurredAt)}</div> : null}
                {step.source ? <div className="mt-2 text-xs font-black uppercase opacity-60">{step.source}</div> : null}
              </li>
            ))}
          </ol>
        </div>
        <div className="rounded-2xl border border-white/60 bg-white/70 p-4">
          <div className="text-xs font-black uppercase tracking-wide opacity-70">Active Issue Dedup</div>
          <div className="mt-3 grid gap-2 sm:grid-cols-2">
            <KeyValue label="Issue Type" value={model.issueDedup.issueType} />
            <KeyValue label="Active Status" value={model.issueDedup.activeStatus} />
            <KeyValue label="Occurrences" value={model.issueDedup.occurrenceCount} />
            <KeyValue label="Policy" value={model.issueDedup.autoResolutionPolicy} />
          </div>
          <p className="mt-3 break-all text-xs font-semibold leading-5 opacity-80">Dedup key: {model.issueDedup.activeIssueKey}</p>
          <p className="mt-2 text-xs font-semibold leading-5 opacity-80">{model.issueDedup.summary}</p>
          <p className="mt-2 text-xs font-black uppercase opacity-60">{model.issueDedup.dedupRule} · {model.issueDedup.repeatedOccurrenceBehavior}</p>
          {model.issueDedup.externalIssueUrl ? (
            <Link href={model.issueDedup.externalIssueUrl} target="_blank" className="mt-3 inline-flex rounded-xl bg-slate-950 px-3 py-2 text-xs font-black text-white hover:bg-slate-800">
              Open External Issue
            </Link>
          ) : null}
        </div>
        <div className="rounded-2xl border border-white/60 bg-white/70 p-4">
          <div className="text-xs font-black uppercase tracking-wide opacity-70">Pool Eligibility Summary</div>
          <div className="mt-3 grid gap-2 sm:grid-cols-2">
            <KeyValue label="Members" value={model.poolEligibility.totalMembers} />
            <KeyValue label="Connected" value={model.poolEligibility.connected} />
            <KeyValue label="Runtime Found" value={model.poolEligibility.runtimeFound} />
            <KeyValue label="Source" value={model.poolEligibility.source} />
          </div>
          <AggregatedEligibilityBlocker
            className="mt-4"
            totalCandidates={model.poolEligibility.totalMembers}
            eligibleCandidates={model.poolEligibility.eligible}
            blockedCounts={model.poolEligibility.blockedCounts}
          />
        </div>
      </div>

      {model.secondaryBlockers.length ? (
        <div className="mt-4 rounded-2xl border border-white/60 bg-white/70 p-4">
          <div className="text-xs font-black uppercase tracking-wide opacity-70">Secondary Blockers</div>
          <div className="mt-2 grid gap-2 md:grid-cols-2">
            {model.secondaryBlockers.map((secondary) => (
              <div key={secondary.code} className="rounded-xl border border-white/70 bg-white/70 p-3 text-sm">
                <div className="font-black">{secondary.code} · {taskDiagnosisCategoryLabel(secondary.category)}</div>
                <p className="mt-1 font-semibold opacity-80">{secondary.userDescription}</p>
              </div>
            ))}
          </div>
        </div>
      ) : null}
    </section>
  );
}

export function TaskRemediationActionsPanel({
  commands,
  runningCommand,
  onCommand,
}: Readonly<{
  commands: TaskRemediationCommandDefinition[];
  runningCommand?: string | null;
  onCommand: (command: TaskRemediationCommandDefinition) => void;
}>) {
  if (!commands.length) {
    return (
      <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="text-xs font-black uppercase tracking-wide text-slate-500">Task Remediation Command Model</div>
        <h2 className="mt-1 text-lg font-black text-slate-950">No remediation command is available</h2>
        <p className="mt-2 text-sm leading-6 text-slate-600">This Task is terminal or its current authoritative state does not permit a remediation mutation. Review evidence and audit history instead.</p>
      </section>
    );
  }
  return (
    <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <div className="text-xs font-black uppercase tracking-wide text-indigo-700">Task Remediation Command Model</div>
          <h2 className="mt-1 text-lg font-black text-slate-950">Governed remediation commands</h2>
          <p className="mt-2 text-sm leading-6 text-slate-600">Each command declares its scope and risk level, uses one logical idempotency key, and is reauthorized against the current Task version by Core.</p>
        </div>
        <span className="rounded-full bg-indigo-50 px-3 py-1 text-xs font-black text-indigo-700">Actions</span>
      </div>
      <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-4">
        {commands.map((command) => {
          const danger = command.tone === 'danger';
          const primary = command.tone === 'primary';
          const running = runningCommand === command.commandType;
          return (
            <button
              type="button"
              key={command.commandType}
              onClick={() => onCommand(command)}
              disabled={Boolean(runningCommand)}
              className={`rounded-2xl border p-4 text-left shadow-sm transition disabled:cursor-not-allowed disabled:opacity-60 ${danger ? 'border-rose-200 bg-rose-50 text-rose-900 hover:bg-rose-100' : primary ? 'border-blue-200 bg-blue-50 text-blue-900 hover:bg-blue-100' : 'border-amber-200 bg-amber-50 text-amber-900 hover:bg-amber-100'}`}
            >
              <div className="flex items-center justify-between gap-2">
                <div className="text-sm font-black">{running ? `Running ${command.label}…` : command.label}</div>
                <span className="rounded-full bg-white/70 px-2 py-1 text-[10px] font-black uppercase tracking-wide">{command.scope} · {command.riskLevel}</span>
              </div>
              <p className="mt-1 text-xs font-semibold leading-5 opacity-80">{command.description}</p>
              {command.requiredPayload ? <div className="mt-2 text-xs font-black uppercase opacity-70">{command.requiredPayload}</div> : null}
            </button>
          );
        })}
      </div>
    </section>
  );
}

export function StandardDispatchTimelinePanel({ task, timeline, diagnosis }: Readonly<{
  task: CoreTaskRuntimeView;
  timeline?: CoreDispatchTimelineResponse;
  diagnosis: TaskDispatchDiagnosis;
}>) {
  const steps = buildStandardDispatchTimeline(task, timeline, diagnosis);
  return (
    <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
      <div>
        <div className="text-xs font-black uppercase tracking-wide text-purple-700">Dispatch Evidence</div>
        <h2 className="mt-1 text-lg font-black text-slate-950">Event → Task → Flow → Agent → Result</h2>
        <p className="mt-1 text-sm leading-6 text-slate-600"> Task,Flow,Assignment,Netty delivery and callback  Scope/Agent </p>
      </div>
      <ol className="mt-5 grid gap-3 lg:grid-cols-4">
        {steps.map((step, index) => (
          <li key={step.id} className={`rounded-2xl border p-4 ${timelineStateClasses(step.state)}`}>
            <div className="flex items-center justify-between gap-3">
              <span className="text-xs font-black uppercase tracking-wide opacity-70">{index + 1}. {step.state}</span>
              {step.timestamp ? <span className="text-xs opacity-70">{formatDateTime(step.timestamp)}</span> : null}
            </div>
            <div className="mt-2 font-black">{step.title}</div>
            <p className="mt-1 text-sm leading-5 opacity-90">{step.detail}</p>
          </li>
        ))}
      </ol>
    </section>
  );
}
