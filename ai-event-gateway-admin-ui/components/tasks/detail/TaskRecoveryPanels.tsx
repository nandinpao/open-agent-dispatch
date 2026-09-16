"use client";

import Link from "next/link";
import { StatusBadge } from "@/components/common/StatusBadge";
import { KeyValue } from "@/components/tasks/detail/TaskDetailPrimitives";
import type {
  CoreDispatchTimelineResponse,
  CoreTaskCaseTimelineStepView,
  CoreTaskCaseTimelineView,
  CoreTaskRuntimeView,
} from "@/lib/types/domains/task";

/**
 * Source Flow, Agent Pool and Task case-timeline recovery diagnostics.
 */
function displayValue(value?: string | number | null): string {
  if (value === undefined || value === null || String(value).trim() === "") return "-";
  return String(value);
}

type FlowRepairCode =
  | "READY"
  | "MISSING_SOURCE_FLOW"
  | "MISSING_FLOW_RULE"
  | "MISSING_AGENT_POOL"
  | "NO_POOL_AGENT_AVAILABLE"
  | "POOL_AGENT_OFFLINE"
  | "POOL_AGENT_CAPACITY_FULL"
  | "POOL_AGENT_BACKOFF"
  | "AGENT_OFFLINE"
  | "NO_DISPATCH_REQUEST"
  | "NO_RESULT_CALLBACK";

const terminalTaskStatuses = new Set(["COMPLETED", "FAILED", "CANCELLED", "TIMED_OUT", "TIMEOUT"]);
const activeTaskStatuses = new Set(["ASSIGNED", "DISPATCHING", "DISPATCHED", "RUNNING", "IN_PROGRESS", "ACKED", "DELIVERED"]);

function upperToken(value?: string | null): string {
  return String(value ?? "").trim().toUpperCase();
}

function selectedFlowAgentId(task: CoreTaskRuntimeView, caseTimeline?: CoreTaskCaseTimelineView): string | undefined {
  return task.assignedAgentId ?? caseTimeline?.steps?.find((step) => step.selectedAgentId)?.selectedAgentId;
}

function hasPoolRoutingEvidence(task: CoreTaskRuntimeView, caseTimeline?: CoreTaskCaseTimelineView): boolean {
  const routingPath = upperToken(task.routingPath ?? caseTimeline?.routingPath);
  return Boolean(
    task.targetPoolId ||
      task.assignedPoolId ||
      routingPath.includes("SOURCE_FLOW") ||
      routingPath.includes("POOL") ||
      task.matchedFlowId ||
      caseTimeline?.matchedFlowId,
  );
}

function timelineHasStage(timeline: CoreDispatchTimelineResponse | undefined, patterns: string[]): boolean {
  const normalized = patterns.map((pattern) => pattern.toUpperCase());
  return Boolean(
    timeline?.events?.some((event) => {
      const values = [event.stage, event.action, event.source, event.message, event.details?.eventStage, event.details?.status]
        .map((value) => upperToken(String(value ?? "")))
        .join(" ");
      return normalized.some((pattern) => values.includes(pattern));
    }),
  );
}

function taskPoolBlockerText(task: CoreTaskRuntimeView): string {
  const latest = task.latestRoutingDecision?.userFacingError ?? task.userFacingDispatchError;
  const technical = latest?.technicalDetails;
  const technicalText = typeof technical === "string" ? technical : JSON.stringify(technical ?? {});
  return [
    task.blockedReason,
    task.dispatchWaitReason,
    task.dispatchRetryReason,
    task.failureReason,
    task.lifecycleReason,
    latest?.code,
    latest?.message,
    latest?.nextAction,
    technicalText,
  ]
    .map((value) => upperToken(String(value ?? "")))
    .filter((value) => value && value !== "{}")
    .join(" ");
}

function caseTimelineFailureCode(task: CoreTaskRuntimeView, caseTimeline?: CoreTaskCaseTimelineView): FlowRepairCode | undefined {
  const raw = [
    caseTimeline?.failureStage,
    caseTimeline?.steps?.find((step) => upperToken(step.status) === "BLOCKED")?.failureStage,
    taskPoolBlockerText(task),
  ].map((value) => upperToken(String(value ?? ""))).join(" ");
  if (raw.includes("SOURCE_FLOW_NOT_FOUND")) return "MISSING_SOURCE_FLOW";
  if (raw.includes("SOURCE_FLOW_HAS_NO_DEFAULT_POOL") || raw.includes("RULE_TARGET_POOL_NOT_FOUND") || raw.includes("POOL_HAS_NO_ACTIVE_MEMBER")) return "MISSING_AGENT_POOL";
  if (raw.includes("POOL_AGENT_RUNTIME_NOT_FOUND") || raw.includes("NO_ELIGIBLE_AGENT_IN_POOL")) return "NO_POOL_AGENT_AVAILABLE";
  if (raw.includes("POOL_AGENT_CAPACITY_FULL")) return "POOL_AGENT_CAPACITY_FULL";
  if (raw.includes("POOL_AGENT_BACKOFF")) return "POOL_AGENT_BACKOFF";
  if (raw.includes("POOL_AGENT_OFFLINE") || raw.includes("NO_AGENT_ONLINE") || raw.includes("AGENT_OFFLINE")) return "POOL_AGENT_OFFLINE";
  if (raw.includes("MISSING_FLOW_RULE") || raw.includes("FLOW_RULE_MATCH")) return "MISSING_FLOW_RULE";
  if (raw.includes("DISPATCH_REQUEST")) return "NO_DISPATCH_REQUEST";
  if (raw.includes("RESULT") || raw.includes("CALLBACK")) return "NO_RESULT_CALLBACK";
  return undefined;
}

function deriveFlowRepairCode(
  task: CoreTaskRuntimeView,
  caseTimeline?: CoreTaskCaseTimelineView,
  timeline?: CoreDispatchTimelineResponse,
): FlowRepairCode {
  const explicit = caseTimelineFailureCode(task, caseTimeline);
  if (explicit) return explicit;
  if (!hasPoolRoutingEvidence(task, caseTimeline)) return "MISSING_SOURCE_FLOW";
  if (!task.targetPoolId && !task.assignedPoolId && !upperToken(task.routingPath).includes("POOL")) return "MISSING_AGENT_POOL";
  if (!selectedFlowAgentId(task, caseTimeline)) return "NO_POOL_AGENT_AVAILABLE";
  if (!task.dispatchRequestId && !timelineHasStage(timeline, ["DISPATCH_REQUEST", "DELIVERY", "DISPATCHED"])) return "NO_DISPATCH_REQUEST";
  if (!task.callbackStatus && !terminalTaskStatuses.has(upperToken(task.status)) && upperToken(task.dispatchStatus) === "DELIVERED") return "NO_RESULT_CALLBACK";
  return "READY";
}

function flowRepairTitle(code: FlowRepairCode): string {
  switch (code) {
    case "MISSING_SOURCE_FLOW": return "missing Source Flow or default Pool";
    case "MISSING_FLOW_RULE": return "missing Flow Rule override default Pool";
    case "MISSING_AGENT_POOL": return "missing Agent Pool / Pool Members";
    case "NO_POOL_AGENT_AVAILABLE": return "Pool  Agent";
    case "POOL_AGENT_OFFLINE": return "Pool  Agent runtime unavailable";
    case "POOL_AGENT_CAPACITY_FULL": return "Pool  Agent ";
    case "POOL_AGENT_BACKOFF": return "Pool  Agent  backoff";
    case "AGENT_OFFLINE": return "Agent runtime unavailable";
    case "NO_DISPATCH_REQUEST": return "Not created yet Dispatch Request";
    case "NO_RESULT_CALLBACK": return "waiting Agent RESULT callback";
    default: return "Source Flow dispatch evidence";
  }
}

function flowRepairAction(code: FlowRepairCode, task: CoreTaskRuntimeView, caseTimeline?: CoreTaskCaseTimelineView): string {
  switch (code) {
    case "MISSING_SOURCE_FLOW":
      return "Open Dispatch, create a Source Flow for this Source System, and configure its Default Agent Pool. Route unknown events to a triage Pool when appropriate.";
    case "MISSING_FLOW_RULE":
      return "Create Flow Rule override and assign target Pool;UnknownEvent details Source Flow default Pool ";
    case "MISSING_AGENT_POOL":
      return "Create or enable the target Agent Pool and add at least one approved Agent.";
    case "NO_POOL_AGENT_AVAILABLE":
      return `in target Pool  Agent, and confirm eventStage=${task.eventStage ?? "EXTERNAL"}  Pool member has runtime binding.`;
    case "POOL_AGENT_OFFLINE":
      return "open Agent Runtime, confirm Pool  Agent online,credential active,heartbeat healthy.";
    case "POOL_AGENT_CAPACITY_FULL":
      return "waiting Pool  Agent  maxConcurrentTasks Agent.";
    case "POOL_AGENT_BACKOFF":
      return "The target Pool has no eligible Agent. Review Pool membership, runtime health, capacity, credentials, and backoff status.";
    case "AGENT_OFFLINE":
      return "open Agent Runtime, confirm Agent online,capacity,backoff and credential Status.";
    case "NO_DISPATCH_REQUEST":
      return "Flow evidence is incomplete or no Dispatch Request was created. Review the dispatch ledger before retrying.";
    case "NO_RESULT_CALLBACK":
      return "Dispatch Not received yet Agent RESULT Callback Inbox / Gateway callback relay.";
    default:
      return caseTimeline?.fixAction ?? task.nextAction ?? "confirm Source Flow / Rule target Pool / Agent Pool member / Runtime delivery / RESULT callback evidence ";
  }
}

type TaskCaseTimelineStep = {
  sequence: number;
  stage: string;
  title: string;
  status: string;
  message: string;
  details: Record<string, string | number | string[] | null | undefined>;
};

function stepFromBackend(step: CoreTaskCaseTimelineStepView): TaskCaseTimelineStep {
  return {
    sequence: step.sequence,
    stage: step.stepCode ?? String(step.eventStage ?? "FLOW_STEP"),
    title: flowStepTitle(step.stepCode ?? String(step.eventStage ?? "")),
    status: upperToken(step.status) || "UNKNOWN",
    message: step.message ?? step.fixAction ?? "-",
    details: {
      eventStage: step.eventStage,
      sourceSystem: step.sourceSystem,
      targetSystem: step.targetSystem,
      eventType: step.eventType,
      matchedFlowId: step.matchedFlowId,
      matchedRuleId: step.matchedRuleId,
      requestedSkill: step.requestedSkill,
      selectedAgentId: step.selectedAgentId,
      routingPath: step.routingPath,
      failureStage: step.failureStage,
      correlationId: step.correlationId,
    },
  };
}

function flowStepTitle(stepCode?: string): string {
  const code = upperToken(stepCode);
  if (code.includes("SOURCE_FLOW")) return "Source Flow Match";
  if (code.includes("FLOW_RULE")) return "Flow Rule Override";
  if (code.includes("POOL")) return "Agent Pool Target";
  if (code.includes("SKILL") || code.includes("CAPABILITY")) return "Required Capability Qualification";
  if (code.includes("AGENT_ASSIGNMENT")) return "Agent Pool Candidate Selection";
  if (code.includes("DISPATCH_REQUEST") || code.includes("RUNTIME_DELIVERY")) return "Runtime Delivery";
  if (code.includes("ACK")) return "Agent ACK";
  if (code.includes("RESULT") || code.includes("CALLBACK")) return "Agent RESULT";
  if (code.includes("ISSUE")) return "Issue Update";
  if (code.includes("A2A")) return "A2A Linkage";
  if (code.includes("INTAKE")) return "Event Received";
  return stepCode ?? "Flow step";
}

function buildTaskCaseTimelineSteps(
  task: CoreTaskRuntimeView,
  caseTimeline?: CoreTaskCaseTimelineView,
  timeline?: CoreDispatchTimelineResponse,
) {
  if (caseTimeline?.steps?.length) {
    return caseTimeline.steps.map(stepFromBackend);
  }
  const agentId = selectedFlowAgentId(task, caseTimeline);
  const flowReady = hasPoolRoutingEvidence(task, caseTimeline);
  const poolReady = Boolean(task.targetPoolId || task.assignedPoolId || upperToken(task.routingPath).includes("POOL"));
  const dispatchRequestReady = Boolean(task.dispatchRequestId) || timelineHasStage(timeline, ["DISPATCH_REQUEST", "DISPATCHED", "DELIVERY"]);
  const ackReady = timelineHasStage(timeline, ["ACK", "ACKED", "ACCEPTED"]);
  const resultReady = Boolean(task.callbackStatus) || timelineHasStage(timeline, ["RESULT", "CALLBACK", "COMPLETED"]);
  const issueReady = Boolean(task.issueTracking?.issueKey || task.issueTracking?.issueUrl || task.issueTracking?.issueStatus) || timelineHasStage(timeline, ["ISSUE", "TICKET"]);
  return [
    {
      sequence: 1,
      stage: task.eventStage ?? "EXTERNAL",
      title: "Event Received",
      status: "PASS",
      message: `${task.sourceSystem ?? "source"} / ${task.eventType ?? task.taskType ?? "event"}`,
      details: {
        sourceSystem: task.sourceSystem,
        originSourceSystem: task.originSourceSystem,
        targetSystem: task.targetSystem,
        objectType: task.objectType,
        eventType: task.eventType,
        errorCode: task.errorCode,
        correlationId: task.correlationId,
        parentTaskId: task.parentTaskId,
      },
    },
    {
      sequence: 2,
      stage: "SOURCE_FLOW_OR_RULE",
      title: "Source Flow / Rule",
      status: flowReady ? "PASS" : "BLOCKED",
      message: flowReady
        ? `matchedFlowId=${task.matchedFlowId ?? "-"} · matchedRuleId=${task.matchedRuleId ?? "NO_MATCH"}`
        : "The Source Flow or Default Agent Pool is missing. Open Dispatch and configure the Source System, Source Flow, and Pool.",
      details: {
        matchedFlowId: task.matchedFlowId,
        matchedRuleId: task.matchedRuleId,
        routingPath: task.routingPath,
        routingPolicy: task.routingPolicy,
      },
    },
    {
      sequence: 3,
      stage: "AGENT_POOL_TARGET",
      title: "Agent Pool Target",
      status: poolReady ? "PASS" : flowReady ? "BLOCKED" : "PENDING",
      message: poolReady ? `targetPool=${task.targetPoolId ?? task.assignedPoolId}` : "Not determined target Pool.Please configuration Source Flow default Pool or Flow Rule target Pool.",
      details: {
        targetPoolId: task.targetPoolId,
        assignedPoolId: task.assignedPoolId,
        classificationStatus: task.classificationStatus,
      },
    },
    {
      sequence: 4,
      stage: "POOL_AGENT_SELECTION",
      title: "Pool Agent Selection",
      status: agentId ? "PASS" : poolReady ? "BLOCKED" : "PENDING",
      message: agentId ?? "No eligible Agent was found in the target Pool. Review Pool membership, Agent approval, online status, and capacity.",
      details: {
        assignedAgentId: agentId,
        targetSystem: task.targetSystem,
        handoffMode: task.handoffMode,
      },
    },
    {
      sequence: 5,
      stage: "RUNTIME_DELIVERY",
      title: "Runtime Delivery",
      status: dispatchRequestReady ? "PASS" : agentId ? "BLOCKED" : "PENDING",
      message: dispatchRequestReady ? `dispatchRequestId=${task.dispatchRequestId ?? "from timeline"}` : "No Dispatch Request has been created. Review the blocker before retrying dispatch.",
      details: {
        dispatchRequestId: task.dispatchRequestId,
        dispatchStatus: task.dispatchStatus,
        status: task.status,
      },
    },
    {
      sequence: 6,
      stage: "AGENT_ACK",
      title: "Agent ACK",
      status: ackReady ? "PASS" : activeTaskStatuses.has(upperToken(task.status)) ? "PENDING" : dispatchRequestReady ? "PENDING" : "PENDING",
      message: ackReady ? "Agent ACK observed." : "waiting Agent ACK / runtime ledger update.",
      details: {
        assignedAgentId: agentId,
        dispatchStatus: task.dispatchStatus,
      },
    },
    {
      sequence: 7,
      stage: "AGENT_RESULT",
      title: "Agent RESULT",
      status: resultReady ? "PASS" : terminalTaskStatuses.has(upperToken(task.status)) ? "BLOCKED" : "PENDING",
      message: resultReady ? `callbackStatus=${task.callbackStatus ?? "observed"}` : "waiting Agent RESULT callback.",
      details: {
        callbackStatus: task.callbackStatus,
        status: task.status,
      },
    },
    {
      sequence: 8,
      stage: "ISSUE_OPERATION",
      title: "Issue Operation",
      status: issueReady ? "PASS" : "PENDING",
      message: issueReady ? "Issue provider-operation evidence observed." : "If this Task policy requires an Issue operation, wait for Issue Automation / AdapterAction evidence; otherwise no Issue operation is required.",
      details: {
        issueKey: String(task.issueTracking?.issueKey ?? "") || undefined,
        issueUrl: task.issueTracking?.issueUrl,
        issueStatus: task.issueTracking?.issueStatus,
      },
    },
  ];
}

function flowRepairHref(task: CoreTaskRuntimeView, code: FlowRepairCode, caseTimeline?: CoreTaskCaseTimelineView): string {
  const flowId = task.matchedFlowId ?? caseTimeline?.matchedFlowId;
  const base = flowId
    ? `/dispatch-flows?flowId=${encodeURIComponent(flowId)}`
    : `/dispatch-flows?sourceSystem=${encodeURIComponent(task.sourceSystem ?? "")}&eventType=${encodeURIComponent(task.eventType ?? "")}`;
  switch (code) {
    case "MISSING_FLOW_RULE": return flowId ? `${base}&panel=${upperToken(task.eventStage) === "A2A" ? "a2a-rules" : "intake-rules"}` : base;
    case "NO_POOL_AGENT_AVAILABLE": return `${base}&panel=agent-pools`;
    case "POOL_AGENT_CAPACITY_FULL": return `${base}&panel=agent-pools&focus=capacity`;
    case "POOL_AGENT_BACKOFF": return `${base}&panel=agent-pools&focus=backoff`;
    case "POOL_AGENT_OFFLINE":
    case "AGENT_OFFLINE": return selectedFlowAgentId(task, caseTimeline) ? `/agents/${encodeURIComponent(selectedFlowAgentId(task, caseTimeline)!)}` : "/agents/runtime";
    case "NO_DISPATCH_REQUEST": return `/tasks/${encodeURIComponent(task.taskId)}?tab=dispatch-lifecycle`;
    case "NO_RESULT_CALLBACK": return `/tasks/${encodeURIComponent(task.taskId)}?tab=issue-result`;
    default: return flowId ? `${base}&panel=test-trace` : "/dispatch-flows";
  }
}

export function TaskCaseTimelineRepairPanel({
  task,
  timeline,
  caseTimeline,
  timelineError,
  caseTimelineError,
}: Readonly<{
  task: CoreTaskRuntimeView;
  timeline?: CoreDispatchTimelineResponse;
  caseTimeline?: CoreTaskCaseTimelineView;
  timelineError?: string;
  caseTimelineError?: string;
}>) {
  const repairCode = deriveFlowRepairCode(task, caseTimeline, timeline);
  const fixAction = flowRepairAction(repairCode, task, caseTimeline);
  const steps = buildTaskCaseTimelineSteps(task, caseTimeline, timeline);
  const flowHref = task.matchedFlowId ?? caseTimeline?.matchedFlowId
    ? `/dispatch-flows?flowId=${encodeURIComponent(task.matchedFlowId ?? caseTimeline!.matchedFlowId!)}`
    : "/dispatch-flows";
  const ruleHref = flowRepairHref(task, "MISSING_FLOW_RULE", caseTimeline);
  const agentHref = flowRepairHref(task, "NO_POOL_AGENT_AVAILABLE", caseTimeline);
  const runtimeHref = flowRepairHref(task, "AGENT_OFFLINE", caseTimeline);
  const traceHref = task.matchedFlowId ?? caseTimeline?.matchedFlowId
    ? `/dispatch-flows?flowId=${encodeURIComponent(task.matchedFlowId ?? caseTimeline!.matchedFlowId!)}&panel=test-trace`
    : "/dispatch-flows?panel=test-trace";
  return (
    <section className="rounded-2xl border border-blue-100 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="text-base font-bold text-slate-900">Source Flow / Agent Pool </h2>
          <p className="mt-1 text-sm text-slate-500">
            Task dispatch path: Event → deterministic Flow Match → governed execution path → runtime delivery → RESULT. NO_MATCH enters Triage; the Default Pool is legacy execution compatibility only. Review the primary blocker and next action when the path fails.
          </p>
        </div>
        <StatusBadge status={repairCode === "READY" ? "POOL_FIRST_READY" : repairCode} />
      </div>
      <div className="mt-4 grid gap-3 md:grid-cols-4">
        <KeyValue label="Event Stage" value={displayValue(task.eventStage ?? caseTimeline?.eventStage)} />
        <KeyValue label="Matched Flow" value={task.matchedFlowId ?? caseTimeline?.matchedFlowId ? <Link href={flowHref} className="text-blue-600 hover:text-blue-700">{task.matchedFlowId ?? caseTimeline?.matchedFlowId}</Link> : "-"} />
        <KeyValue label="Matched Rule" value={task.matchedRuleId ?? caseTimeline?.matchedRuleId ? <Link href={ruleHref} className="text-blue-600 hover:text-blue-700">{task.matchedRuleId ?? caseTimeline?.matchedRuleId}</Link> : "-"} />
        <KeyValue label="Target Pool" value={displayValue(task.targetPoolId ?? task.assignedPoolId)} />
        <KeyValue label="Ability Tag Reference" value={displayValue(task.requestedSkill ?? caseTimeline?.requestedSkill)} />
        <KeyValue label="Pool Blocker" value={displayValue(taskPoolBlockerText(task) || caseTimeline?.failureStage)} />
        <KeyValue label="Routing Path" value={displayValue(task.routingPath ?? caseTimeline?.routingPath)} />
        <KeyValue label="Selected Agent" value={displayValue(selectedFlowAgentId(task, caseTimeline))} />
        <KeyValue label="Correlation" value={displayValue(task.correlationId ?? caseTimeline?.correlationId)} />
        <KeyValue label="Target System" value={displayValue(task.targetSystem)} />
      </div>
      <div className="mt-4 rounded-xl border border-amber-100 bg-amber-50 p-4 text-sm text-amber-900">
        <div className="font-bold">{flowRepairTitle(repairCode)}</div>
        <p className="mt-1">{fixAction}</p>
      </div>
      <div className="mt-4 grid gap-2 sm:grid-cols-2 lg:grid-cols-6">
        <Link href={flowHref} className="rounded-xl border border-blue-200 px-3 py-2 text-center text-sm font-bold text-blue-700 hover:bg-blue-50">Fix Source Flow</Link>
        <Link href={ruleHref} className="rounded-xl border border-blue-200 px-3 py-2 text-center text-sm font-bold text-blue-700 hover:bg-blue-50">Fix Rule Target Pool</Link>
        <Link href={agentHref} className="rounded-xl border border-blue-200 px-3 py-2 text-center text-sm font-bold text-blue-700 hover:bg-blue-50">Manage Pool Members</Link>
        <Link href={runtimeHref} className="rounded-xl border border-blue-200 px-3 py-2 text-center text-sm font-bold text-blue-700 hover:bg-blue-50">Start Agent Runtime</Link>
        <Link href={`/tasks/${encodeURIComponent(task.taskId)}?tab=dispatch-lifecycle`} className="rounded-xl border border-blue-200 px-3 py-2 text-center text-sm font-bold text-blue-700 hover:bg-blue-50">Retry / Ledger</Link>
        <Link href={traceHref} className="rounded-xl border border-blue-200 px-3 py-2 text-center text-sm font-bold text-blue-700 hover:bg-blue-50">Run Pool Trace</Link>
      </div>
      {timelineError || caseTimelineError ? (
        <p className="mt-4 rounded-xl bg-rose-50 p-3 text-sm font-semibold text-rose-700">Timeline warning: {caseTimelineError ?? timelineError}</p>
      ) : null}
      <div className="mt-5 space-y-3">
        {steps.map((step) => (
          <div key={`${step.sequence}-${step.stage}`} className="rounded-xl border border-slate-100 bg-slate-50 p-4">
            <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
              <div>
                <div className="text-xs font-bold uppercase tracking-wide text-slate-400">{step.sequence}. {step.stage}</div>
                <div className="mt-1 text-sm font-bold text-slate-900">{step.title}</div>
                <div className="mt-1 text-sm text-slate-600">{step.message}</div>
              </div>
              <StatusBadge status={step.status} />
            </div>
            <div className="mt-3 grid gap-2 md:grid-cols-3">
              {Object.entries(step.details).map(([key, value]) => (
                <KeyValue key={key} label={key} value={Array.isArray(value) ? value.join(", ") || "-" : displayValue(value as string | number | null | undefined)} />
              ))}
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
