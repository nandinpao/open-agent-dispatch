"use client";

import Link from "next/link";
import { EmptyState } from "@/components/common/EmptyState";
import { RawDiagnosticsPanel } from "@/components/common/RawDiagnosticsPanel";
import { StatusBadge } from "@/components/common/StatusBadge";
import { KeyValue } from "@/components/tasks/detail/TaskDetailPrimitives";
import type {
  CoreTaskCaseTimelineView,
  CoreTaskRuntimeView,
} from "@/lib/types/domains/task";

/**
 * A2A parent/child evidence chain and payload diagnostics.
 */
function isRecordValue(value: unknown): value is Record<string, unknown> {
  return Boolean(value && typeof value === "object" && !Array.isArray(value));
}

function parseEvidenceRecord(value: unknown): Record<string, unknown> | undefined {
  if (!value) return undefined;
  if (typeof value === "string") {
    try {
      const parsed = JSON.parse(value);
      return isRecordValue(parsed) ? parsed : { raw: value };
    } catch {
      return { raw: value };
    }
  }
  return isRecordValue(value) ? value : undefined;
}

function evidenceString(record: Record<string, unknown> | undefined, keys: string[]): string | undefined {
  if (!record) return undefined;
  for (const key of keys) {
    const value = record[key];
    if (value !== undefined && value !== null && String(value).trim() !== "") return String(value);
  }
  return undefined;
}

function evidenceNumber(record: Record<string, unknown> | undefined, keys: string[]): number | undefined {
  const value = evidenceString(record, keys);
  if (!value) return undefined;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

export function TaskLink({ taskId, label }: Readonly<{ taskId?: string | null; label?: string }>) {
  if (!taskId) return <span>-</span>;
  return (
    <Link href={`/tasks/${encodeURIComponent(taskId)}`} className="font-bold text-blue-700 hover:underline">
      {label ?? taskId}
    </Link>
  );
}

export function TaskA2AEvidenceChainPanel({
  task,
  parentTask,
  childTasks,
  caseTimeline,
  familyError,
}: Readonly<{
  task: CoreTaskRuntimeView;
  parentTask?: CoreTaskRuntimeView;
  childTasks?: CoreTaskRuntimeView[];
  caseTimeline?: CoreTaskCaseTimelineView;
  familyError?: string;
}>) {
  const isResolutionTask = Boolean(task.parentTaskId);
  const triageTask = parentTask ?? (isResolutionTask ? undefined : task);
  const resolutionTasks = (childTasks ?? []).length ? childTasks ?? [] : isResolutionTask ? [task] : [];
  const primaryResolutionTask = isResolutionTask ? task : resolutionTasks[0];
  const classificationEvidence = parseEvidenceRecord(triageTask?.classificationResultJson ?? task.classificationResultJson);
  const classifiedEventType = evidenceString(classificationEvidence, ["eventType", "event_type", "resolutionEventType"]);
  const classifiedObjectType = evidenceString(classificationEvidence, ["objectType", "object_type", "resolutionObjectType"]);
  const classifiedErrorCode = evidenceString(classificationEvidence, ["errorCode", "error_code", "resolutionErrorCode"]);
  const confidence = evidenceNumber(classificationEvidence, ["confidence", "classificationConfidence"]);
  const reason = evidenceString(classificationEvidence, ["reason", "message", "summary"]);
  const recommendedPoolCode = evidenceString(classificationEvidence, ["recommendedPoolCode", "recommended_pool_code", "targetPoolCode"]);
  const rootTaskId = evidenceString(classificationEvidence, ["rootTaskId", "root_task_id"]) ?? caseTimeline?.rootTaskId ?? (task.parentTaskId ? undefined : task.taskId);
  const correlationId = evidenceString(classificationEvidence, ["correlationId", "correlation_id"]) ?? caseTimeline?.correlationId ?? task.correlationId;
  const classificationVersion = evidenceString(classificationEvidence, ["classificationVersion", "classification_version"]) ?? "A2A_CLASSIFICATION_V1";
  const idempotencyKey = evidenceString(classificationEvidence, ["idempotencyKey", "idempotency_key"]);
  const a2aDepth = evidenceNumber(classificationEvidence, ["a2aDepth", "a2a_depth"]);
  const maxA2ADepth = evidenceNumber(classificationEvidence, ["maxA2ADepth", "max_a2a_depth"]);
  const coreOwnedTaskCreation = evidenceString(classificationEvidence, ["coreOwnedTaskCreation"]) ?? "true";
  const agentCreatedTask = evidenceString(classificationEvidence, ["agentCreatedTask"]) ?? "false";
  const childTaskCreationAuthority = evidenceString(classificationEvidence, ["childTaskCreationAuthority"]) ?? "CORE_ONLY";
  const childIdsFromTimeline = caseTimeline?.childTaskIds ?? [];
  const hasEvidence = Boolean(
    task.parentTaskId ||
      childIdsFromTimeline.length ||
      resolutionTasks.length ||
      task.classificationStatus ||
      classificationEvidence,
  );

  return (
    <section className="rounded-3xl border border-cyan-100 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <div className="text-xs font-black uppercase tracking-wide text-cyan-600">Dispatch Evidence Chain</div>
          <h2 className="mt-1 text-lg font-black text-slate-950">Core-owned Classification Continuation</h2>
          <p className="mt-1 text-sm leading-6 text-slate-600">
            The Agent submits only a classification result. This compatibility flow is not Capability Delegation/A2A execution authority. OpenDispatch Core may create a normal Resolution continuation Task, then Source Flow and Agent Pool authority perform direct dispatch. The Agent never creates a Task or selects an executor directly.
          </p>
        </div>
        <StatusBadge status={hasEvidence ? "A2A_CHAIN_VISIBLE" : "NO_A2A_CHAIN"} />
      </div>
      {familyError ? (
        <p className="mt-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm font-bold text-amber-900">
          Related task lookup warning: {familyError}
        </p>
      ) : null}
      <div className="mt-5 grid gap-4 xl:grid-cols-4">
        <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
          <div className="text-xs font-black uppercase tracking-wide text-slate-500">1. Triage Parent</div>
          <div className="mt-2 text-sm font-black text-slate-950">
            <TaskLink taskId={triageTask?.taskId ?? task.parentTaskId ?? task.taskId} label={triageTask?.taskId ?? task.parentTaskId ?? task.taskId} />
          </div>
          <div className="mt-3 grid gap-2">
            <KeyValue label="Task Type" value={triageTask?.taskType ?? triageTask?.taskTypeCode ?? (isResolutionTask ? "TRIAGE parent" : task.taskType ?? "-")} />
            <KeyValue label="Classification" value={triageTask?.classificationStatus ?? task.classificationStatus ?? "-"} />
            <KeyValue label="Root Task" value={<TaskLink taskId={rootTaskId} />} />
            <KeyValue label="Correlation" value={correlationId ?? "-"} />
            <KeyValue label="Source" value={triageTask?.sourceSystem ?? task.sourceSystem ?? "-"} />
            <KeyValue label="Original Event" value={triageTask?.eventType ?? (isResolutionTask ? "UNKNOWN / parent lookup pending" : task.eventType ?? "UNKNOWN")} />
          </div>
        </div>
        <div className="rounded-2xl border border-cyan-200 bg-cyan-50 p-4">
          <div className="text-xs font-black uppercase tracking-wide text-cyan-700">2. Classification Result</div>
          <div className="mt-2 text-sm font-bold leading-6 text-cyan-950">
            {classifiedEventType ? ` ${classifiedEventType}` : "Result"}
          </div>
          <div className="mt-3 grid gap-2">
            <KeyValue label="Object Type" value={classifiedObjectType ?? "-"} />
            <KeyValue label="Event Type" value={classifiedEventType ?? "-"} />
            <KeyValue label="Error Code" value={classifiedErrorCode ?? "-"} />
            <KeyValue label="Confidence" value={confidence === undefined ? "-" : `${Math.round(confidence * 100)}%`} />
            <KeyValue label="Recommended Pool" value={recommendedPoolCode ?? "-"} />
            <KeyValue label="Version" value={classificationVersion} />
            <KeyValue label="Idempotency" value={idempotencyKey ?? "-"} />
          </div>
          {reason ? <p className="mt-3 rounded-xl bg-white/80 px-3 py-2 text-xs font-semibold leading-5 text-cyan-900">{reason}</p> : null}
        </div>
        <div className="rounded-2xl border border-indigo-100 bg-indigo-50 p-4">
          <div className="text-xs font-black uppercase tracking-wide text-indigo-700">3. Resolution Child</div>
          {resolutionTasks.length ? (
            <div className="mt-3 space-y-2">
              {resolutionTasks.slice(0, 4).map((child) => (
                <div key={child.taskId} className="rounded-xl border border-indigo-100 bg-white px-3 py-2 text-xs text-indigo-950">
                  <div><TaskLink taskId={child.taskId} /></div>
                  <div className="mt-1">{child.taskType ?? child.taskTypeCode ?? "RESOLUTION"} · <StatusBadge status={child.status} /></div>
                  <div className="mt-1">event={child.eventType ?? "-"}</div>
                </div>
              ))}
            </div>
          ) : childIdsFromTimeline.length ? (
            <div className="mt-3 space-y-2">
              {childIdsFromTimeline.slice(0, 4).map((childId) => (
                <div key={childId} className="rounded-xl border border-indigo-100 bg-white px-3 py-2 text-xs text-indigo-950">
                  <TaskLink taskId={childId} />
                </div>
              ))}
            </div>
          ) : (
            <p className="mt-3 text-sm font-semibold text-indigo-900">Not created yet Resolution child task.</p>
          )}
        </div>
        <div className="rounded-2xl border border-emerald-100 bg-emerald-50 p-4">
          <div className="text-xs font-black uppercase tracking-wide text-emerald-700">4. Pool / Agent Evidence</div>
          <div className="mt-3 grid gap-2">
            <KeyValue label="Authority" value={childTaskCreationAuthority} />
            <KeyValue label="Core-owned" value={coreOwnedTaskCreation} />
            <KeyValue label="Agent created task" value={agentCreatedTask} />
            <KeyValue label="Depth" value={a2aDepth === undefined ? "-" : `${a2aDepth}/${maxA2ADepth ?? "?"}`} />
            <KeyValue label="Matched Flow" value={primaryResolutionTask?.matchedFlowId ?? task.matchedFlowId ?? "-"} />
            <KeyValue label="Matched Rule" value={primaryResolutionTask?.matchedRuleId ?? task.matchedRuleId ?? "NO_MATCH / -"} />
            <KeyValue label="Target Pool" value={primaryResolutionTask?.targetPoolId ?? primaryResolutionTask?.assignedPoolId ?? task.targetPoolId ?? task.assignedPoolId ?? "-"} />
            <KeyValue label="Selected Agent" value={primaryResolutionTask?.assignedAgentId ? <Link href={`/agents/${encodeURIComponent(primaryResolutionTask.assignedAgentId)}`} className="text-blue-700 hover:underline">{primaryResolutionTask.assignedAgentId}</Link> : task.assignedAgentId ? <Link href={`/agents/${encodeURIComponent(task.assignedAgentId)}`} className="text-blue-700 hover:underline">{task.assignedAgentId}</Link> : "-"} />
            <KeyValue label="Routing Path" value={primaryResolutionTask?.routingPath ?? task.routingPath ?? "-"} />
          </div>
        </div>
      </div>
      <div className="mt-4 rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm leading-6 text-slate-700">
        <span className="font-black text-slate-900">ActionsDescription:</span>
        The intake Source Flow routes the parent Task to the triage Agent. Core uses parentTaskId, rootTaskId, correlationId, classificationVersion, maxA2ADepth, cycleDetection, and idempotencyKey before creating a Resolution continuation. recommendedPoolCode is evidence only; Source Flow and Agent Pool remain the routing authority. Capability-first A2A delegation is a separate governed path.
      </div>
    </section>
  );
}

export function PayloadPanel({ task }: Readonly<{ task: CoreTaskRuntimeView }>) {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <h2 className="text-base font-bold text-slate-900">
        Core Payload / Runtime View
      </h2>
      <p className="mt-1 text-sm text-slate-500">
        Display the Core Task runtime-view payload.
        Core 
      </p>
      <div className="mt-4">
        {task.payload ? (
          <RawDiagnosticsPanel title="Core raw payload" value={task.payload} />
        ) : (
          <EmptyState
            title="No payload"
            description="Core runtime-view did not return payloadView details payload."
          />
        )}
      </div>
    </section>
  );
}
