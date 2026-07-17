"use client";

import { useState, type ReactNode } from "react";
import Link from "next/link";
import { CommandMessage } from "@/components/common/CommandMessage";
import { coreAdminApi } from "@/lib/api/coreAdminApi";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorBox } from "@/components/common/ErrorBox";
import { RawDiagnosticsPanel } from "@/components/common/RawDiagnosticsPanel";
import { LoadingBox } from "@/components/common/LoadingBox";
import { RefreshButton } from "@/components/common/RefreshButton";
import { DispatchUserFacingReason } from "@/components/common/DispatchUserFacingReason";
import { DispatchLifecycleStepper } from "@/components/tasks/DispatchLifecycleStepper";
import { TaskActionDialog, type TaskActionDialogValues } from "@/components/tasks/TaskActionDialog";
import {
  TaskDiagnosticsTabs,
  type TaskDiagnosticsTab,
} from "@/components/tasks/TaskDiagnosticsTabs";
import {
  TaskControlConsoleTabs,
  type TaskControlConsoleTab,
} from "@/components/tasks/TaskControlConsoleTabs";
import { TaskLifecycleOperatorPanel } from "@/components/tasks/TaskLifecycleOperatorPanel";
import { CapabilityResolutionMatrix } from "@/components/dispatch-evidence/StandardRelationshipComponents";
import { EntityRelationshipStrip } from "@/components/dispatch-evidence/StandardRelationshipComponents";
import { TaskWorkbenchPanel } from "@/components/tasks/TaskWorkbenchPanel";
import { BeginnerTaskFlowPanel } from "@/components/tasks/BeginnerTaskFlowPanel";
import { RoutingExplainabilityPanel } from "@/components/tasks/RoutingExplainabilityPanel";
import { DispatchAssignmentEvidencePanel } from "@/components/dispatch-evidence/DispatchAssignmentEvidencePanel";
import { TaskRuntimeVerificationPanel } from "@/components/tasks/TaskRuntimeVerificationPanel";
import { StatusBadge } from "@/components/common/StatusBadge";
import {
  buildCapabilityMatrix,
  buildTaskRelationshipSteps,
} from "@/lib/dispatch-readiness/beginnerWorkflow";
import type { DispatchOperatorCommand } from "@/lib/dispatch-readiness/dispatchOperatorActions";
import { useTaskDetail } from "@/hooks/useTaskDetail";
import type {
  RuntimeAttemptSummary,
  TaskDispatchDashboardRow,
} from "@/lib/dashboard/taskDispatchMerge";
import type {
  CoreCallbackInboxEntry,
  CoreCallbackInboxSummary,
  CoreDispatchAttemptHistoryRecord,
  CoreDispatchAttemptLedger,
  CoreDispatchTimelineResponse,
  CoreTaskDispatchEvidenceView,
  CoreTaskRuntimeVerificationView,
  CoreRoutingDecisionRecord,
  CoreTaskDispatchRequirements,
  CoreTaskEligibleAgentsResponse,
  CoreDispatchEligibilityV2Response,
  CoreTaskCaseTimelineView,
  CoreTaskCaseTimelineStepView,
  CoreTaskRuntimeView,
  CoreRecoveryGovernanceActionRequest,
} from "@/lib/types/core";
import { formatDateTime, formatDurationMs } from "@/lib/utils/format";
import { taskAuthorityDisclaimer } from "@/lib/runtime/callbackTruth";
import {
  buildStandardDispatchTimeline,
  deriveTaskDispatchDiagnosis,
  type StandardDispatchTimelineStep,
  type TaskDispatchDiagnosis,
} from "@/lib/tasks/dispatchLifecycle";

function KeyValue({
  label,
  value,
}: Readonly<{ label: string; value: ReactNode }>) {
  return (
    <div className="rounded-xl border border-slate-100 bg-slate-50 px-4 py-3">
      <div className="text-xs font-semibold uppercase tracking-wide text-slate-400">
        {label}
      </div>
      <div className="mt-1 break-all text-sm font-semibold text-slate-800">
        {value}
      </div>
    </div>
  );
}


function diagnosisToneClasses(tone: TaskDispatchDiagnosis["tone"]): string {
  if (tone === "success") return "border-emerald-200 bg-emerald-50 text-emerald-950";
  if (tone === "danger") return "border-rose-200 bg-rose-50 text-rose-950";
  if (tone === "warning") return "border-amber-200 bg-amber-50 text-amber-950";
  return "border-blue-200 bg-blue-50 text-blue-950";
}

function timelineStateClasses(state: StandardDispatchTimelineStep["state"]): string {
  if (state === "done") return "border-emerald-300 bg-emerald-50 text-emerald-900";
  if (state === "failed") return "border-rose-300 bg-rose-50 text-rose-900";
  if (state === "blocked") return "border-amber-300 bg-amber-50 text-amber-900";
  if (state === "current") return "border-blue-300 bg-blue-50 text-blue-900";
  return "border-slate-200 bg-white text-slate-500";
}

function TaskPrimaryDiagnosisPanel({
  task,
  diagnosis,
  retrying,
  allowRetry,
  onRetry,
}: Readonly<{
  task: CoreTaskRuntimeView;
  diagnosis: TaskDispatchDiagnosis;
  retrying: boolean;
  allowRetry: boolean;
  onRetry: () => void;
}>) {
  return (
    <section className={`rounded-3xl border p-6 shadow-sm ${diagnosisToneClasses(diagnosis.tone)}`}>
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div className="max-w-4xl">
          <div className="text-xs font-black uppercase tracking-wide opacity-70">派工主因 · {diagnosis.code}</div>
          <h2 className="mt-1 text-xl font-black">{diagnosis.title}</h2>
          <p className="mt-2 text-sm font-semibold leading-6">{diagnosis.reason}</p>
          <p className="mt-3 text-sm leading-6"><span className="font-black">下一步：</span>{diagnosis.nextAction}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          {diagnosis.actionHref && diagnosis.actionLabel ? (
            <Link href={diagnosis.actionHref} className="rounded-xl bg-slate-950 px-4 py-2 text-sm font-black text-white hover:bg-slate-800">
              {diagnosis.actionLabel}
            </Link>
          ) : null}
          {allowRetry ? (
            <button type="button" onClick={onRetry} disabled={retrying} className="rounded-xl border border-slate-300 bg-white px-4 py-2 text-sm font-black text-slate-800 hover:bg-slate-50 disabled:opacity-50">
              {retrying ? "重新派工中…" : "重新派工"}
            </button>
          ) : null}
        </div>
      </div>
      <div className="mt-5 grid gap-3 sm:grid-cols-2 xl:grid-cols-6">
        <KeyValue label="Task" value={task.taskId} />
        <KeyValue label="Flow" value={diagnosis.flowId ? <Link href={`/dispatch-flows?flowId=${encodeURIComponent(diagnosis.flowId)}`} className="text-blue-700 hover:underline">{diagnosis.flowId}</Link> : "尚未命中"} />
        <KeyValue label="Rule" value={diagnosis.ruleId ?? "尚未命中"} />
        <KeyValue label="Pool" value={task.targetPoolId ?? task.assignedPoolId ?? "尚未決定"} />
        <KeyValue label="Agent" value={diagnosis.agentId ? <Link href={`/agents/${encodeURIComponent(diagnosis.agentId)}`} className="text-blue-700 hover:underline">{diagnosis.agentId}</Link> : "尚未指派"} />
        <KeyValue label="Trace" value={diagnosis.traceId ? <Link href={`/traces/${encodeURIComponent(diagnosis.traceId)}`} className="text-blue-700 hover:underline">{diagnosis.traceId}</Link> : "-"} />
      </div>
      {diagnosis.missingCapabilities.length ? <div className="mt-4 text-sm"><span className="font-black">必要特殊能力：</span>{diagnosis.missingCapabilities.join("、")}</div> : null}
    </section>
  );
}

function StandardDispatchTimelinePanel({ task, timeline, diagnosis }: Readonly<{
  task: CoreTaskRuntimeView;
  timeline?: CoreDispatchTimelineResponse;
  diagnosis: TaskDispatchDiagnosis;
}>) {
  const steps = buildStandardDispatchTimeline(task, timeline, diagnosis);
  return (
    <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
      <div>
        <div className="text-xs font-black uppercase tracking-wide text-purple-700">標準派工時間線</div>
        <h2 className="mt-1 text-lg font-black text-slate-950">Event → Task → Flow → Agent → Result</h2>
        <p className="mt-1 text-sm leading-6 text-slate-600">這條時間線直接使用正式 Task、Flow、Assignment、Netty delivery 與 callback 證據，不使用獨立測試工具或舊 Scope/Agent 推論。</p>
      </div>
      <ol className="mt-5 grid gap-3 lg:grid-cols-4">
        {steps.map((step, index) => (
          <li key={step.id} className={`rounded-2xl border p-4 ${timelineStateClasses(step.state)}`}>
            <div className="flex items-center justify-between gap-3">
              <span className="text-xs font-black uppercase tracking-wide opacity-70">{index + 1}. {step.state}</span>
              {step.timestamp ? <span className="text-[11px] opacity-70">{formatDateTime(step.timestamp)}</span> : null}
            </div>
            <div className="mt-2 font-black">{step.title}</div>
            <p className="mt-1 text-sm leading-5 opacity-90">{step.detail}</p>
          </li>
        ))}
      </ol>
    </section>
  );
}

function normalizeContractToken(value?: string | null): string {
  return (value ?? "").trim().toUpperCase();
}

function isTaskContractUnresolved(requirements?: CoreTaskDispatchRequirements, eligibleAgents?: CoreTaskEligibleAgentsResponse): boolean {
  const resolvedRequirements = requirements ?? eligibleAgents?.requirements;
  const source = normalizeContractToken(resolvedRequirements?.requirementSource);
  return ["NO_MATCHING_FLOW", "NO_MATCHING_RULE", "NO_FLOW_AGENT"].includes(source);
}

function TaskAuthoritySummary({
  task,
}: Readonly<{ task: CoreTaskRuntimeView }>) {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="text-base font-bold text-slate-900">
            Core Task Authority
          </h2>
          <p className="mt-1 text-sm text-slate-500">
            Task 基本資料、狀態、指派 Agent、Dispatch Request 與 Callback
            persisted status 以 Core 為準；Gateway runtime 只提供 transport
            observation。
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <StatusBadge status={task.status} />
          {task.dispatchStatus ? (
            <StatusBadge status={task.dispatchStatus} />
          ) : null}
        </div>
      </div>
      <div className="mt-4 grid gap-3 md:grid-cols-4">
        <KeyValue label="Task ID" value={task.taskId} />
        <KeyValue
          label="Trace ID"
          value={
            task.traceId ? (
              <Link
                href={`/traces/${encodeURIComponent(task.traceId)}`}
                className="text-blue-600 hover:text-blue-700"
              >
                {task.traceId}
              </Link>
            ) : (
              "-"
            )
          }
        />
        <KeyValue label="Incident" value={task.incidentId ?? "-"} />
        <KeyValue label="Type" value={task.taskType ?? "-"} />
        <KeyValue label="Priority" value={task.priority ?? "-"} />
        <KeyValue
          label="Assigned Agent"
          value={
            task.assignedAgentId ? (
              <Link
                href={`/agents/${encodeURIComponent(task.assignedAgentId)}`}
                className="text-blue-600 hover:text-blue-700"
              >
                {task.assignedAgentId}
              </Link>
            ) : (
              "-"
            )
          }
        />
        <KeyValue
          label="Dispatch Request"
          value={task.dispatchRequestId ?? "-"}
        />
        <KeyValue label="Dispatch Status" value={task.dispatchStatus ?? "-"} />
        <KeyValue
          label="Execution Status"
          value={task.dispatchExecutionStatus ?? "-"}
        />
        <KeyValue
          label="Delivery Status"
          value={task.dispatchDeliveryStatus ?? "-"}
        />
        <KeyValue label="Next Action" value={task.nextAction ?? "-"} />
        <KeyValue label="Callback Status" value={task.callbackStatus ?? "-"} />
        <KeyValue
          label="Dispatch Recovery Attempt"
          value={task.dispatchAttemptCount ?? 0}
        />
        <KeyValue
          label="Next Dispatch Attempt"
          value={
            task.nextDispatchAttemptAt
              ? formatDateTime(task.nextDispatchAttemptAt)
              : "-"
          }
        />
        <KeyValue
          label="Recovery Claim"
          value={
            task.dispatchRecoveryClaimedBy
              ? `${task.dispatchRecoveryClaimedBy}${task.dispatchRecoveryClaimUntil ? ` until ${formatDateTime(task.dispatchRecoveryClaimUntil)}` : ""}`
              : "-"
          }
        />
        <KeyValue
          label="Created"
          value={task.createdAt ? formatDateTime(task.createdAt) : "-"}
        />
        <KeyValue
          label="Updated"
          value={task.updatedAt ? formatDateTime(task.updatedAt) : "-"}
        />
        <KeyValue
          label="Capability Tags（查詢參考）"
          value={task.requiredCapabilities?.join(", ") || "-"}
        />
        <KeyValue label="Target Pool" value={task.targetPoolId ?? "-"} />
        <KeyValue label="Assigned Pool" value={task.assignedPoolId ?? "-"} />
        <KeyValue label="Classification" value={task.classificationStatus ?? "-"} />
      </div>
      {task.blockedReason ? (
        <p className="mt-4 rounded-xl bg-amber-50 p-4 text-sm font-semibold text-amber-800">
          Blocked: {task.blockedReason}
          {task.nextAction ? ` · Next action: ${task.nextAction}` : ""}
        </p>
      ) : null}
      {task.lifecycleReason &&
      !task.dispatchWaitReason &&
      !task.dispatchRetryReason &&
      !task.failureReason ? (
        <p className="mt-4 rounded-xl bg-slate-50 p-4 text-sm font-semibold text-slate-700">
          State reason: {task.lifecycleReason}
        </p>
      ) : null}
      {task.dispatchWaitReason ? (
        <div className="mt-4 rounded-xl bg-amber-50 p-4 text-sm font-semibold text-amber-800">
          <div className="mb-2 text-xs font-black uppercase tracking-wide text-amber-700">
            Dispatch wait / delayed recovery
          </div>
          <DispatchUserFacingReason
            value={task.dispatchWaitReason}
            error={task.userFacingDispatchError}
            codeClassName="inline-flex rounded-full bg-amber-900 px-2.5 py-1 text-[11px] font-black uppercase tracking-wide text-white"
            detailsClassName="rounded-xl border border-amber-100 bg-white/70 px-3 py-2 text-xs font-semibold"
            technicalClassName="mt-2 break-words whitespace-pre-wrap font-mono leading-5 text-amber-900"
          />
        </div>
      ) : null}
      {task.failureReason ? (
        <div className="mt-4 rounded-xl bg-rose-50 p-4 text-sm font-semibold text-rose-800">
          <div className="mb-2 text-xs font-black uppercase tracking-wide text-rose-700">
            Failure
          </div>
          <DispatchUserFacingReason
            value={task.failureReason}
            codeClassName="inline-flex rounded-full bg-rose-900 px-2.5 py-1 text-[11px] font-black uppercase tracking-wide text-white"
            detailsClassName="rounded-xl border border-rose-100 bg-white/70 px-3 py-2 text-xs font-semibold"
            technicalClassName="mt-2 break-words whitespace-pre-wrap font-mono leading-5 text-rose-900"
          />
        </div>
      ) : null}
    </section>
  );
}

function TaskDispatchEligibilityContractPanel({
  requirements,
  eligibleAgents,
  requirementsError,
  eligibleAgentsError,
}: Readonly<{
  requirements?: CoreTaskDispatchRequirements;
  eligibleAgents?: CoreTaskEligibleAgentsResponse;
  requirementsError?: string;
  eligibleAgentsError?: string;
}>) {
  const requiredCapabilities = requirements?.requiredCapabilities ?? [];
  const requiredFeatures = requirements?.requiredRuntimeFeatures ?? [];
  const contractUnresolved = isTaskContractUnresolved(requirements, eligibleAgents);
  const eligible = contractUnresolved ? [] : eligibleAgents?.eligibleAgents ?? [];
  const blocked = contractUnresolved ? [] : eligibleAgents?.blockedAgents ?? [];
  return (
    <section className="rounded-2xl border border-indigo-100 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <div className="text-xs font-black uppercase tracking-wide text-indigo-500">
Support · Agent Pool / Runtime Evidence
          </div>
          <h2 className="mt-1 text-base font-bold text-slate-900">
Pool evidence 外的候選診斷
          </h2>
          <p className="mt-1 text-sm leading-6 text-slate-500">
這裡僅顯示 Agent Pool、Agent runtime 與容量的候選證據；Capability 只作為能力標籤參考，不是第一版派單 gate。
          </p>
        </div>
        <StatusBadge
          status={contractUnresolved ? "CONTRACT_NOT_RESOLVED" : eligible.length ? "HAS_ELIGIBLE_AGENT" : "NO_ELIGIBLE_AGENT"}
        />
      </div>
      {requirementsError ? (
        <p className="mt-3 rounded-xl bg-amber-50 p-3 text-sm font-semibold text-amber-800">
          Requirements API: {requirementsError}
        </p>
      ) : null}
      {eligibleAgentsError ? (
        <p className="mt-3 rounded-xl bg-amber-50 p-3 text-sm font-semibold text-amber-800">
          Eligible Agents API: {eligibleAgentsError}
        </p>
      ) : null}
      <div className="mt-4 grid gap-4 lg:grid-cols-3">
        <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
          <div className="text-sm font-black text-slate-900">
Capability Tags Reference
          </div>
          <div className="mt-3 flex flex-wrap gap-2">
            {requiredCapabilities.length ? (
              requiredCapabilities.map((capability) => (
                <span
                  key={capability}
                  className="rounded-full border border-indigo-200 bg-indigo-50 px-3 py-1.5 text-xs font-bold text-indigo-800"
                >
                  {capability}
                </span>
              ))
            ) : (
              <span className="text-sm text-slate-500">
沒有 Capability tag；這不會阻擋 Phase 32 派單。
              </span>
            )}
          </div>
        </div>
        <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
          <div className="text-sm font-black text-slate-900">
            Required Runtime Features
          </div>
          <div className="mt-3 flex flex-wrap gap-2">
            {requiredFeatures.length ? (
              requiredFeatures.map((feature) => (
                <span
                  key={feature}
                  className="rounded-full border border-slate-200 bg-white px-3 py-1.5 text-xs font-bold text-slate-700"
                >
                  {feature}
                </span>
              ))
            ) : (
              <span className="text-sm text-slate-500">
                此 task 沒有明確 runtime feature requirement。
              </span>
            )}
          </div>
        </div>
        <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
          <div className="text-sm font-black text-slate-900">
            Eligible Agents
          </div>
          <div className="mt-3 text-sm leading-6 text-slate-700">
            <div>
              <span className="font-bold text-emerald-700">Eligible：</span>
              {eligible.length}
            </div>
            <div>
              <span className="font-bold text-rose-700">Blocked：</span>
              {blocked.length}
            </div>
            <div>
              <span className="font-bold">Source：</span>
              {requirements?.requirementSource ?? "-"}
            </div>
            {contractUnresolved ? (
              <div className="mt-2 rounded-xl border border-amber-200 bg-amber-50 px-3 py-2 text-xs font-bold leading-5 text-amber-900">
Candidate evaluation was not executed. 先回上方 Source Flow / Agent Pool 修復中心補齊 default pool、target pool 或 pool members。
              </div>
            ) : null}
          </div>
        </div>
      </div>
      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <div className="rounded-2xl border border-emerald-100 bg-emerald-50/70 p-4">
          <div className="text-sm font-black text-emerald-950">
            Top Eligible Agents
          </div>
          <div className="mt-3 space-y-2">
            {eligible.slice(0, 5).map((agent) => (
              <div
                key={agent.agentId}
                className="rounded-xl border border-emerald-100 bg-white px-3 py-2 text-xs text-emerald-900"
              >
                <div className="font-black">
                  <Link
                    href={`/agents/${encodeURIComponent(agent.agentId)}`}
                    className="hover:text-emerald-700"
                  >
                    {agent.agentId}
                  </Link>
                </div>
                <div className="mt-1">
                  Archived agent:{" "}
                  {agent.profileCode ??
                    agent.matchedProfiles?.join(", ") ??
                    "-"}{" "}
                  · Score: {agent.score ?? "-"}
                </div>
              </div>
            ))}
            {!eligible.length ? (
              <div className="text-sm text-emerald-800">
                {contractUnresolved ? "Flow evidence 尚未完整，尚未執行有效候選比對。" : "目前沒有符合此 Task 的 eligible Agent。"}
              </div>
            ) : null}
          </div>
        </div>
        <div className={`rounded-2xl border p-4 ${contractUnresolved ? "border-amber-100 bg-amber-50/70" : "border-rose-100 bg-rose-50/70"}`}>
          <div className={`text-sm font-black ${contractUnresolved ? "text-amber-950" : "text-rose-950"}`}>
            {contractUnresolved ? "Candidate Evaluation" : "Blocked Agents"}
          </div>
          <div className="mt-3 space-y-2">
            {contractUnresolved ? (
              <div className="rounded-xl border border-amber-100 bg-white px-3 py-2 text-xs leading-5 text-amber-900">
                <div className="font-black">Not executed</div>
                <div className="mt-1">
                  This task does not have complete Flow-owned evidence yet, so Agent selection should be repaired from Dispatch Flow first.
                </div>
                <div className="mt-2 font-mono text-[11px]">
                  requirementSource={requirements?.requirementSource ?? "-"}
                </div>
              </div>
            ) : blocked.slice(0, 5).map((agent) => (
              <div
                key={agent.agentId}
                className="rounded-xl border border-rose-100 bg-white px-3 py-2 text-xs text-rose-900"
              >
                <div className="font-black">
                  <Link
                    href={`/agents/${encodeURIComponent(agent.agentId)}`}
                    className="hover:text-rose-700"
                  >
                    {agent.agentId}
                  </Link>
                </div>
                <div className="mt-1">
                  {agent.reason ?? agent.dispatchStatus ?? "BLOCKED"}
                </div>
              </div>
            ))}
            {!contractUnresolved && !blocked.length ? (
              <div className="text-sm text-rose-800">沒有被阻擋的 Agent。</div>
            ) : null}
          </div>
        </div>
      </div>
    </section>
  );
}

// Backward verification token: P3-H · Eligibility V2 Shadow
function TaskDispatchEligibilityV2Panel({
  response,
  archivedAgents,
  error,
}: Readonly<{
  response?: CoreDispatchEligibilityV2Response;
  archivedAgents?: CoreTaskEligibleAgentsResponse;
  error?: string;
}>) {
  const policies = response?.applicablePolicies ?? [];
  const eligible = response?.eligibleCandidates ?? [];
  const blocked = response?.blockedCandidates ?? [];
  const globalReasons = response?.globalBlockingReasons ?? [];
  const archivedEligibleIds = new Set<string>(
    (archivedAgents?.eligibleAgents ?? [])
      .map((agent) => agent.agentId)
      .filter((agentId): agentId is string => Boolean(agentId)),
  );
  const v2EligibleIds = new Set<string>(
    eligible
      .map((candidate) => candidate.agentId)
      .filter((agentId): agentId is string => Boolean(agentId)),
  );
  const common = Array.from(archivedEligibleIds).filter((agentId) =>
    v2EligibleIds.has(agentId),
  );
  const archivedOnly = Array.from(archivedEligibleIds).filter(
    (agentId) => !v2EligibleIds.has(agentId),
  );
  const v2Only = Array.from(v2EligibleIds).filter(
    (agentId) => !archivedEligibleIds.has(agentId),
  );
  return (
    <section className="rounded-2xl border border-violet-100 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <div className="text-xs font-black uppercase tracking-wide text-violet-500">
            Advanced Flow Agent Eligibility
          </div>
          <h2 className="mt-1 text-base font-bold text-slate-900">
            進階 Agent 候選比對
          </h2>
          <p className="mt-1 text-sm leading-6 text-slate-500">
            此區塊只作為進階診斷，正式修復仍以 Source Flow、Agent Pool、Pool member Agent 與 Runtime delivery 為主；Capability 僅作為標籤參考。
          </p>
        </div>
        <StatusBadge status={response?.engineMode ?? "SHADOW"} />
      </div>
      {error ? (
        <p className="mt-3 rounded-xl bg-amber-50 p-3 text-sm font-semibold text-amber-800">
          Eligibility V2 API: {error}
        </p>
      ) : null}
      <div className="mt-4 grid gap-3 md:grid-cols-3">
        <div className="rounded-2xl border border-slate-200 bg-slate-50 p-3">
          <div className="text-xs font-black uppercase tracking-wide text-slate-500">
            Common
          </div>
          <div className="mt-1 text-2xl font-black text-slate-900">
            {common.length}
          </div>
          <div className="mt-1 text-xs text-slate-500">
            兩個候選來源都允許的 Agent。
          </div>
        </div>
        <div className="rounded-2xl border border-amber-100 bg-amber-50 p-3">
          <div className="text-xs font-black uppercase tracking-wide text-amber-600">
            Flow evidence only
          </div>
          <div className="mt-1 text-2xl font-black text-amber-900">
            {archivedOnly.length}
          </div>
          <div className="mt-1 text-xs text-amber-700">
            進階候選模型沒有看到；請以 Flow Agent assignment 為主。
          </div>
        </div>
        <div className="rounded-2xl border border-violet-100 bg-violet-50 p-3">
          <div className="text-xs font-black uppercase tracking-wide text-violet-600">
            V2 only
          </div>
          <div className="mt-1 text-2xl font-black text-violet-900">
            {v2Only.length}
          </div>
          <div className="mt-1 text-xs text-violet-700">
            進階候選模型新增的 Agent。
          </div>
        </div>
      </div>
      {archivedOnly.length ? (
        <div className="mt-3 rounded-2xl border border-amber-100 bg-amber-50 p-3 text-xs text-amber-900">
          <span className="font-black">Flow evidence only agents:</span>{" "}
          {archivedOnly.slice(0, 8).join(", ")}
        </div>
      ) : null}
      {globalReasons.length ? (
        <div className="mt-4 rounded-2xl border border-amber-100 bg-amber-50 p-4 text-sm text-amber-900">
          <div className="font-black">Global blocking reasons</div>
          <ul className="mt-2 list-disc space-y-1 pl-5">
            {globalReasons.map((reason, index) => (
              <li key={`${reason.code}-${index}`}>
                {reason.code}: {reason.message}
              </li>
            ))}
          </ul>
        </div>
      ) : null}
      <div className="mt-4 grid gap-4 lg:grid-cols-3">
        <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
          <div className="text-sm font-black text-slate-900">
            Applicable Rules
          </div>
          <div className="mt-3 space-y-2">
            {policies.slice(0, 5).map((policy) => (
              <div
                key={policy.policyCode}
                className="rounded-xl border border-violet-100 bg-white px-3 py-2 text-xs text-violet-900"
              >
                <div className="font-black">{policy.policyCode}</div>
                <div className="mt-1">
                  Capabilities:{" "}
                  {(policy.requiredCapabilities ?? []).join(", ") || "-"}
                </div>
                <div>
                  Runtime:{" "}
                  {(policy.requiredRuntimeFeatures ?? []).join(", ") || "-"}
                </div>
              </div>
            ))}
            {!policies.length ? (
              <span className="text-sm text-slate-500">
                尚未找到覆蓋此 Task 的 Dispatch Policy v2 scope。
              </span>
            ) : null}
          </div>
        </div>
        <div className="rounded-2xl border border-emerald-100 bg-emerald-50/70 p-4">
          <div className="text-sm font-black text-emerald-950">
            Top V2 Candidates
          </div>
          <div className="mt-3 space-y-2">
            {eligible.slice(0, 5).map((candidate) => (
              <div
                key={`${candidate.agentId}-${candidate.supplyProfileCode}`}
                className="rounded-xl border border-emerald-100 bg-white px-3 py-2 text-xs text-emerald-900"
              >
                <div className="font-black">{candidate.agentId}</div>
                <div>
                  Candidate source: {candidate.supplyProfileCode ?? "-"} · Score:{" "}
                  {candidate.score ?? "-"}
                </div>
                <div>Quality: {candidate.qualityGrade ?? "-"}</div>
              </div>
            ))}
            {!eligible.length ? (
              <span className="text-sm text-emerald-800">
                目前目前沒有進階 eligible candidate。
              </span>
            ) : null}
          </div>
        </div>
        <div className="rounded-2xl border border-rose-100 bg-rose-50/70 p-4">
          <div className="text-sm font-black text-rose-950">
            Blocked V2 Candidates
          </div>
          <div className="mt-3 space-y-2">
            {blocked.slice(0, 5).map((candidate) => (
              <div
                key={`${candidate.agentId}-${candidate.supplyProfileCode}`}
                className="rounded-xl border border-rose-100 bg-white px-3 py-2 text-xs text-rose-900"
              >
                <div className="font-black">{candidate.agentId ?? "-"}</div>
                <div>
                  Candidate source: {candidate.supplyProfileCode ?? "-"} · Score:{" "}
                  {candidate.score ?? "-"}
                </div>
                <div>
                  {candidate.blockingReasons?.[0]?.code ??
                    candidate.dispatchStatus ??
                    "BLOCKED_V2_SHADOW"}
                </div>
              </div>
            ))}
            {!blocked.length ? (
              <span className="text-sm text-rose-800">
                目前沒有進階 blocked candidate。
              </span>
            ) : null}
          </div>
        </div>
      </div>
    </section>
  );
}

function RuntimeAttemptPanel({
  title,
  description,
  attempt,
  warning,
}: Readonly<{
  title: string;
  description: string;
  attempt?: RuntimeAttemptSummary;
  warning?: string;
}>) {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="text-base font-bold text-slate-900">{title}</h2>
          <p className="mt-1 text-sm text-slate-500">{description}</p>
        </div>
        <StatusBadge
          status={
            attempt ? "NETTY_OK" : warning ? "NETTY_UNAVAILABLE" : "MISSING"
          }
        />
      </div>
      {warning ? (
        <p className="mt-4 rounded-xl bg-amber-50 p-4 text-sm font-semibold text-amber-700">
          {warning}
        </p>
      ) : null}
      {!warning && !attempt ? (
        <EmptyState
          title="沒有符合此 Task 的 runtime attempt"
          description="Netty runtime 只代表即時 delivery / relay 觀測；沒有資料不代表 Core Task 不存在。"
        />
      ) : null}
      {attempt ? (
        <>
          <div className="mt-4 grid gap-3 md:grid-cols-4">
            <KeyValue label="Status" value={attempt.status ?? "-"} />
            <KeyValue
              label="Gateway Node"
              value={attempt.gatewayNodeId ?? "-"}
            />
            <KeyValue label="Session" value={attempt.sessionId ?? "-"} />
            <KeyValue label="Agent" value={attempt.agentId ?? "-"} />
            <KeyValue
              label="Occurred"
              value={
                attempt.occurredAt ? formatDateTime(attempt.occurredAt) : "-"
              }
            />
            <KeyValue
              label="Latency"
              value={formatDurationMs(attempt.latencyMs)}
            />
            <KeyValue label="Reason" value={attempt.reason ?? "-"} />
          </div>
          {attempt.payload ? (
            <div className="mt-4">
              <RawDiagnosticsPanel
                title="Runtime attempt raw payload"
                value={attempt.payload}
              />
            </div>
          ) : null}
        </>
      ) : null}
    </section>
  );
}

function payloadFromHistory(
  record: CoreDispatchAttemptHistoryRecord,
): unknown | undefined {
  if (!record.payloadJson) return undefined;
  try {
    return JSON.parse(record.payloadJson);
  } catch {
    return record.payloadJson;
  }
}

function RecoveryVisibilityPanel({
  task,
}: Readonly<{ task: CoreTaskRuntimeView }>) {
  const waiting = Boolean(task.nextDispatchAttemptAt);
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="text-base font-bold text-slate-900">
            Task-level Delayed Recovery
          </h2>
          <p className="mt-1 text-sm text-slate-500">
            權威狀態：判斷此 Task 是真的未派出，還是正在等待 scanner
            到期重新派工。
          </p>
        </div>
        <StatusBadge
          status={waiting ? "DELAYED_RECOVERY_WAITING" : "NO_DELAY"}
        />
      </div>
      <div className="mt-4 grid gap-3 md:grid-cols-4">
        <KeyValue label="Waiting Recovery" value={waiting ? "YES" : "NO"} />
        <KeyValue
          label="Task Dispatch Attempts"
          value={task.dispatchAttemptCount ?? 0}
        />
        <KeyValue
          label="Next Attempt At"
          value={
            task.nextDispatchAttemptAt
              ? formatDateTime(task.nextDispatchAttemptAt)
              : "-"
          }
        />
        <KeyValue
          label="Claimed By"
          value={task.dispatchRecoveryClaimedBy ?? "-"}
        />
        <KeyValue
          label="Claim Until"
          value={
            task.dispatchRecoveryClaimUntil
              ? formatDateTime(task.dispatchRecoveryClaimUntil)
              : "-"
          }
        />
        <KeyValue
          label="Reason"
          value={
            task.dispatchWaitReason ? (
              <DispatchUserFacingReason
                value={task.dispatchWaitReason}
                error={task.userFacingDispatchError}
              />
            ) : (
              "-"
            )
          }
        />
      </div>
    </section>
  );
}

function StandardDispatchActionsPanel({
  task,
  retrying,
  onRetry,
}: Readonly<{
  task: CoreTaskRuntimeView;
  retrying: boolean;
  onRetry: () => void;
}>) {
  const flowHref = task.matchedFlowId ? `/dispatch-flows?flowId=${encodeURIComponent(task.matchedFlowId)}` : "/dispatch-flows";
  const agentHref = task.assignedAgentId ? `/agents/${encodeURIComponent(task.assignedAgentId)}` : "/agents";
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="text-base font-bold text-slate-900">Standard Dispatch Actions</h2>
          <p className="mt-1 text-sm text-slate-500">
            Phase 32 standard Task CTA: Source Flow, Agent Pool, Agent runtime, or Retry Dispatch only. Capability is metadata and does not block first-version routing.
          </p>
        </div>
        <StatusBadge status="STANDARD_DISPATCH_ACTIONS" />
      </div>
      <div className="mt-4 grid gap-3 md:grid-cols-4">
        <Link href={flowHref} className="rounded-xl border border-blue-200 px-4 py-2 text-center text-sm font-bold text-blue-700 hover:bg-blue-50">Open Source Flow</Link>
        <Link href={task.targetPoolId ? `/agents?poolId=${encodeURIComponent(task.targetPoolId)}` : "/agents"} className="rounded-xl border border-slate-200 px-4 py-2 text-center text-sm font-bold text-slate-700 hover:bg-slate-50">Review Agent Pool</Link>
        <Link href={agentHref} className="rounded-xl border border-violet-200 px-4 py-2 text-center text-sm font-bold text-violet-700 hover:bg-violet-50">Open Agent Runtime</Link>
        <button type="button" onClick={onRetry} disabled={retrying} className="rounded-xl bg-blue-600 px-4 py-2 text-sm font-bold text-white shadow-sm hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-slate-300">
          {retrying ? "Retrying..." : "Retry Dispatch"}
        </button>
      </div>
    </section>
  );
}

function DispatchTimelinePanel({
  timeline,
  error,
}: Readonly<{ timeline?: CoreDispatchTimelineResponse; error?: string }>) {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="text-base font-bold text-slate-900">
            Dispatch Timeline
          </h2>
          <p className="mt-1 text-sm text-slate-500">
            Core canonical timeline：task、dispatch
            attempt、offer、assignment、lease、execution、callback、retry、DLQ
            與 audit history。
          </p>
        </div>
        <StatusBadge
          status={
            error
              ? "TIMELINE_UNAVAILABLE"
              : timeline?.events?.length
                ? "TIMELINE_READY"
                : "NO_TIMELINE"
          }
        />
      </div>
      {error ? (
        <p className="mt-4 rounded-xl bg-amber-50 p-4 text-sm font-semibold text-amber-700">
          {error}
        </p>
      ) : null}
      {!error && (!timeline?.events || timeline.events.length === 0) ? (
        <EmptyState
          title="尚無 dispatch timeline"
          description="Core 尚未回傳 timeline events，或此任務尚未進入 dispatch flow。"
        />
      ) : null}
      {!error && timeline?.events?.length ? (
        <>
          <div className="mt-4 flex flex-wrap gap-2 text-xs">
            {Object.entries(timeline.counts ?? {}).map(([stage, count]) => (
              <span
                key={stage}
                className="rounded-full bg-slate-100 px-2.5 py-1 font-semibold text-slate-600"
              >
                {stage}: {count}
              </span>
            ))}
            {timeline.generatedAt ? (
              <span className="rounded-full bg-slate-100 px-2.5 py-1 font-semibold text-slate-600">
                Generated: {formatDateTime(timeline.generatedAt)}
              </span>
            ) : null}
          </div>
          <div className="mt-4 overflow-hidden rounded-xl border border-slate-100">
            <table className="min-w-full divide-y divide-slate-100 text-sm">
              <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-4 py-3">#</th>
                  <th className="px-4 py-3">Occurred</th>
                  <th className="px-4 py-3">Stage</th>
                  <th className="px-4 py-3">Action</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3">Message</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 bg-white">
                {timeline.events.map((event) => (
                  <tr
                    key={`${event.sequence}-${event.stage}-${event.action}`}
                    className="align-top hover:bg-slate-50"
                  >
                    <td className="px-4 py-3 text-slate-500">
                      {event.sequence}
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-slate-600">
                      {event.occurredAt
                        ? formatDateTime(event.occurredAt)
                        : "-"}
                    </td>
                    <td className="px-4 py-3">
                      <StatusBadge status={event.stage} />
                    </td>
                    <td className="px-4 py-3">
                      <StatusBadge status={event.action} />
                    </td>
                    <td className="px-4 py-3 text-slate-600">
                      {event.status ? (
                        <StatusBadge status={event.status} />
                      ) : (
                        "-"
                      )}
                    </td>
                    <td className="max-w-2xl px-4 py-3 text-slate-600">
                      <div className="font-medium">{event.message ?? "-"}</div>
                      <div className="mt-1 text-xs text-slate-400">
                        {Object.entries(event.references ?? {})
                          .map(([key, value]) => `${key}=${value}`)
                          .join(" · ")}
                      </div>
                      {event.details && Object.keys(event.details).length ? (
                        <div className="mt-2">
                          <RawDiagnosticsPanel
                            title="Timeline event details"
                            value={event.details}
                          />
                        </div>
                      ) : null}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      ) : null}
    </section>
  );
}

function CallbackInboxPanel({
  entries,
  summary,
  error,
}: Readonly<{
  entries?: CoreCallbackInboxEntry[];
  summary?: CoreCallbackInboxSummary;
  error?: string;
}>) {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="text-base font-bold text-slate-900">
            Durable Callback Inbox
          </h2>
          <p className="mt-1 text-sm text-slate-500">
            Core persisted callback inbox：ACK / PROGRESS / RESULT / ERROR
            由任意 Gateway relay 後寫入 Core。這是 callback receiving 與
            idempotent processing 的 truth，不依賴原 Gateway node 是否仍在線。
          </p>
        </div>
        <StatusBadge
          status={
            error
              ? "CALLBACK_INBOX_UNAVAILABLE"
              : entries?.length
                ? "CALLBACK_INBOX_READY"
                : "NO_CALLBACK"
          }
        />
      </div>
      {error ? (
        <p className="mt-4 rounded-xl bg-amber-50 p-4 text-sm font-semibold text-amber-700">
          {error}
        </p>
      ) : null}
      {!error && summary ? (
        <div className="mt-4 grid gap-3 md:grid-cols-4">
          <KeyValue
            label="Total callbacks"
            value={summary.totalCallbacks ?? 0}
          />
          <KeyValue label="Accepted" value={summary.acceptedCallbacks ?? 0} />
          <KeyValue label="Rejected" value={summary.rejectedCallbacks ?? 0} />
          <KeyValue label="Duplicate" value={summary.duplicateCallbacks ?? 0} />
          <KeyValue
            label="Latest callback"
            value={summary.latestCallbackId ?? "-"}
          />
          <KeyValue
            label="Latest type"
            value={summary.latestCallbackType ?? "-"}
          />
          <KeyValue
            label="Process status"
            value={summary.latestProcessStatus ?? "-"}
          />
          <KeyValue label="Next action" value={summary.nextAction ?? "-"} />
        </div>
      ) : null}
      <div className="mt-4 rounded-xl border border-indigo-100 bg-indigo-50 p-3 text-xs leading-5 text-indigo-900">
        Callback Inbox 是 Core truth。若 cluster node 中斷、Nginx 重新分流或
        cluster/single 拓樸切換，Agent 可透過任一可用 Gateway 重送
        callback；Core 以 callbackId / idempotencyKey 去重並處理。
      </div>
      {!error && (!entries || entries.length === 0) ? (
        <EmptyState
          title="尚無 callback inbox record"
          description="Core 尚未收到此 Task 的 ACK / RESULT / ERROR callback。若 Gateway 已接受派工，請確認 Agent worker 是否以 process-result 模式執行，或檢查 Agent reconnect replay。"
        />
      ) : null}
      {!error && entries?.length ? (
        <div className="mt-4 overflow-hidden rounded-xl border border-slate-100 bg-white">
          <table className="min-w-full divide-y divide-slate-100 text-sm">
            <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-4 py-3">Processed</th>
                <th className="px-4 py-3">Type</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Received by</th>
                <th className="px-4 py-3">Message</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {entries.map((entry, index) => (
                <tr
                  key={`${entry.callbackId ?? entry.idempotencyKey ?? index}`}
                  className="align-top hover:bg-slate-50"
                >
                  <td className="whitespace-nowrap px-4 py-3 text-slate-600">
                    {entry.processedAt
                      ? formatDateTime(entry.processedAt)
                      : entry.receivedAt
                        ? formatDateTime(entry.receivedAt)
                        : "-"}
                  </td>
                  <td className="px-4 py-3">
                    <StatusBadge status={entry.callbackType ?? "UNKNOWN"} />
                  </td>
                  <td className="px-4 py-3">
                    <StatusBadge
                      status={
                        entry.processStatus ??
                        (entry.accepted ? "ACCEPTED" : "REJECTED")
                      }
                    />
                  </td>
                  <td className="px-4 py-3 text-slate-600">
                    <div>{entry.receivedByGatewayNodeId ?? "-"}</div>
                    <div className="mt-1 text-xs text-slate-400">
                      session={entry.receivedAgentSessionId ?? "-"}
                    </div>
                  </td>
                  <td className="max-w-xl px-4 py-3 text-slate-600">
                    <div className="font-medium">
                      {entry.message ??
                        entry.ignoredReason ??
                        entry.errorMessage ??
                        "-"}
                    </div>
                    <div className="mt-1 text-xs text-slate-400">
                      callback={entry.callbackId ?? "-"} · idem=
                      {entry.idempotencyKey ?? "-"}
                    </div>
                    {entry.payload && Object.keys(entry.payload).length ? (
                      <div className="mt-2">
                        <RawDiagnosticsPanel
                          title="Callback payload"
                          value={entry.payload}
                        />
                      </div>
                    ) : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
    </section>
  );
}

function DispatchLedgerPanel({
  ledger,
  error,
}: Readonly<{ ledger?: CoreDispatchAttemptLedger[]; error?: string }>) {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="text-base font-bold text-slate-900">
            Durable Dispatch Attempt Ledger
          </h2>
          <p className="mt-1 text-sm text-slate-500">
            Core authoritative ledger：由 persisted dispatch request 與
            persisted callback inbox records 組成，不依賴 Gateway node
            memory；single / cluster 拓樸切換後仍可查詢。
          </p>
        </div>
        <StatusBadge
          status={
            error
              ? "LEDGER_UNAVAILABLE"
              : ledger?.length
                ? "LEDGER_READY"
                : "NO_LEDGER"
          }
        />
      </div>
      {error ? (
        <p className="mt-4 rounded-xl bg-amber-50 p-4 text-sm font-semibold text-amber-700">
          {error}
        </p>
      ) : null}
      {!error && (!ledger || ledger.length === 0) ? (
        <EmptyState
          title="尚無 dispatch ledger"
          description="Core 尚未建立 dispatch request，或此任務尚未進入派工流程。Gateway diagnostics 不會被混入此權威 ledger。"
        />
      ) : null}
      {!error && ledger?.length ? (
        <div className="mt-4 space-y-4">
          {ledger.map((item) => (
            <div
              key={item.dispatchRequestId}
              className="rounded-xl border border-slate-100 bg-slate-50 p-4"
            >
              <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                <div>
                  <div className="text-xs font-black uppercase tracking-wide text-slate-400">
                    Dispatch Request
                  </div>
                  <div className="mt-1 font-mono text-sm font-bold text-slate-900">
                    {item.dispatchRequestId}
                  </div>
                  <div className="mt-1 text-xs text-slate-500">
                    Task {item.taskId} · Agent {item.agentId ?? "-"} · Attempt{" "}
                    {item.attemptNo ?? "-"}
                  </div>
                </div>
                <div className="flex flex-wrap gap-2">
                  <StatusBadge
                    status={
                      item.deliveryState ?? item.dispatchStatus ?? "UNKNOWN"
                    }
                  />
                  <StatusBadge status={item.callbackState ?? "NO_CALLBACK"} />
                  <StatusBadge status={item.resultState ?? "PENDING"} />
                </div>
              </div>
              <div className="mt-4 grid gap-3 md:grid-cols-4">
                <KeyValue
                  label="Gateway"
                  value={item.lastKnownGatewayNodeId ?? "-"}
                />
                <KeyValue
                  label="Session"
                  value={item.lastKnownAgentSessionId ?? "-"}
                />
                <KeyValue
                  label="Last callback"
                  value={item.lastCallbackId ?? "-"}
                />
                <KeyValue label="Next action" value={item.nextAction ?? "-"} />
                <KeyValue
                  label="Dispatched"
                  value={
                    item.dispatchedAt ? formatDateTime(item.dispatchedAt) : "-"
                  }
                />
                <KeyValue
                  label="ACK"
                  value={
                    item.ackReceivedAt
                      ? formatDateTime(item.ackReceivedAt)
                      : "-"
                  }
                />
                <KeyValue
                  label="Result"
                  value={
                    item.resultReceivedAt
                      ? formatDateTime(item.resultReceivedAt)
                      : "-"
                  }
                />
                <KeyValue
                  label="Recovery"
                  value={item.recoveryRequired ? "Required" : "Not required"}
                />
              </div>
              <div className="mt-4 rounded-xl border border-blue-100 bg-blue-50 p-3 text-xs leading-5 text-blue-900">
                Dispatch Ledger 是 Core truth；Gateway node diagnostics
                只能協助定位 live transport / relay 問題，不能作為 callback
                recovery 的唯一依據。
              </div>
              {item.events?.length ? (
                <div className="mt-4 overflow-hidden rounded-xl border border-slate-100 bg-white">
                  <table className="min-w-full divide-y divide-slate-100 text-sm">
                    <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                      <tr>
                        <th className="px-4 py-3">Occurred</th>
                        <th className="px-4 py-3">Source</th>
                        <th className="px-4 py-3">Event</th>
                        <th className="px-4 py-3">Status</th>
                        <th className="px-4 py-3">Reason</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {item.events.map((event, index) => (
                        <tr
                          key={`${event.eventId ?? event.eventType}-${index}`}
                          className="align-top hover:bg-slate-50"
                        >
                          <td className="whitespace-nowrap px-4 py-3 text-slate-600">
                            {event.occurredAt
                              ? formatDateTime(event.occurredAt)
                              : "-"}
                          </td>
                          <td className="px-4 py-3">
                            <StatusBadge status={event.source ?? "UNKNOWN"} />
                          </td>
                          <td className="px-4 py-3">
                            <StatusBadge status={event.eventType} />
                          </td>
                          <td className="px-4 py-3">
                            <StatusBadge status={event.status ?? "-"} />
                          </td>
                          <td className="max-w-xl px-4 py-3 text-slate-600">
                            <div className="font-medium">
                              {event.reason ?? event.errorMessage ?? "-"}
                            </div>
                            <div className="mt-1 text-xs text-slate-400">
                              callback={event.callbackId ?? "-"} · idem=
                              {event.idempotencyKey ?? "-"}
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : null}
            </div>
          ))}
        </div>
      ) : null}
    </section>
  );
}

function AttemptHistoryPanel({
  history,
  error,
}: Readonly<{ history?: CoreDispatchAttemptHistoryRecord[]; error?: string }>) {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="text-base font-bold text-slate-900">
            Core Dispatch Attempt History
          </h2>
          <p className="mt-1 text-sm text-slate-500">
            Core append-only timeline：assignment、dispatch request、Netty
            delivery、runtime backoff、delayed requeue 與 scanner claim。
          </p>
        </div>
        <StatusBadge
          status={
            error
              ? "HISTORY_UNAVAILABLE"
              : history?.length
                ? "HISTORY_READY"
                : "NO_HISTORY"
          }
        />
      </div>
      {error ? (
        <p className="mt-4 rounded-xl bg-amber-50 p-4 text-sm font-semibold text-amber-700">
          {error}
        </p>
      ) : null}
      {!error && (!history || history.length === 0) ? (
        <EmptyState
          title="尚無 dispatch attempt history"
          description="新建任務或尚未產生 history instrumentation 的資料可能沒有 timeline。"
        />
      ) : null}
      {!error && history?.length ? (
        <div className="mt-4 overflow-hidden rounded-xl border border-slate-100">
          <table className="min-w-full divide-y divide-slate-100 text-sm">
            <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-4 py-3">Occurred</th>
                <th className="px-4 py-3">Event</th>
                <th className="px-4 py-3">Agent / Assignment</th>
                <th className="px-4 py-3">Attempt</th>
                <th className="px-4 py-3">Next / Backoff</th>
                <th className="px-4 py-3">Reason</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 bg-white">
              {history.map((item) => (
                <tr
                  key={item.historyId}
                  className="align-top hover:bg-slate-50"
                >
                  <td className="whitespace-nowrap px-4 py-3 text-slate-600">
                    {item.occurredAt ? formatDateTime(item.occurredAt) : "-"}
                  </td>
                  <td className="px-4 py-3">
                    <StatusBadge status={item.eventType} />
                  </td>
                  <td className="px-4 py-3 text-slate-600">
                    <div>
                      {item.agentId ? (
                        <Link
                          href={`/agents/${encodeURIComponent(item.agentId)}`}
                          className="font-semibold text-blue-600 hover:text-blue-700"
                        >
                          {item.agentId}
                        </Link>
                      ) : (
                        "-"
                      )}
                    </div>
                    <div className="mt-1 text-xs text-slate-400">
                      {item.assignmentId ??
                        item.dispatchRequestId ??
                        item.routingDecisionId ??
                        "-"}
                    </div>
                  </td>
                  <td className="px-4 py-3 text-slate-600">
                    <div>dispatch: {item.attemptNo ?? "-"}</div>
                    <div className="text-xs text-slate-400">
                      task recovery: {item.taskDispatchAttemptNo ?? "-"}
                    </div>
                  </td>
                  <td className="px-4 py-3 text-slate-600">
                    <div>
                      {item.nextAttemptAt
                        ? `next ${formatDateTime(item.nextAttemptAt)}`
                        : "-"}
                    </div>
                    <div className="mt-1 text-xs text-slate-400">
                      {item.runtimeBackoffUntil
                        ? `backoff ${formatDateTime(item.runtimeBackoffUntil)}`
                        : ""}
                    </div>
                  </td>
                  <td className="max-w-xl px-4 py-3 text-slate-600">
                    <div className="break-words font-medium">
                      {item.reason ?? item.errorMessage ?? "-"}
                    </div>
                    {item.errorCode ? (
                      <div className="mt-1 text-xs font-semibold text-rose-500">
                        {item.errorCode}
                      </div>
                    ) : null}
                    {item.payloadJson ? (
                      <div className="mt-2">
                        <RawDiagnosticsPanel
                          title="Attempt payload"
                          value={payloadFromHistory(item)}
                        />
                      </div>
                    ) : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
    </section>
  );
}


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
    case "MISSING_SOURCE_FLOW": return "缺少 Source Flow 或 default Pool";
    case "MISSING_FLOW_RULE": return "缺少 Flow Rule override，可先走 default Pool";
    case "MISSING_AGENT_POOL": return "缺少 Agent Pool / Pool 成員";
    case "NO_POOL_AGENT_AVAILABLE": return "Pool 內尚未選出可用 Agent";
    case "POOL_AGENT_OFFLINE": return "Pool 內 Agent runtime 不可用";
    case "POOL_AGENT_CAPACITY_FULL": return "Pool 內 Agent 容量已滿";
    case "POOL_AGENT_BACKOFF": return "Pool 內 Agent 暫時 backoff";
    case "AGENT_OFFLINE": return "Agent runtime 不可用";
    case "NO_DISPATCH_REQUEST": return "尚未建立 Dispatch Request";
    case "NO_RESULT_CALLBACK": return "等待 Agent RESULT callback";
    default: return "Flow 派工鏈已具備正式 evidence";
  }
}

function flowRepairAction(code: FlowRepairCode, task: CoreTaskRuntimeView, caseTimeline?: CoreTaskCaseTimelineView): string {
  switch (code) {
    case "MISSING_SOURCE_FLOW":
      return "到 Dispatch Flows 建立此 sourceSystem 的 Source Flow，並設定 default Agent Pool；未知事件會先派到 default TRIAGE_POOL。";
    case "MISSING_FLOW_RULE":
      return "若是已知事件，建立 Flow Rule override 並指定 target Pool；未知事件可以先由 Source Flow default Pool 承接。";
    case "MISSING_AGENT_POOL":
      return "建立或啟用 target Agent Pool，並加入至少一個已核准、已連線且容量可用的 Agent。";
    case "NO_POOL_AGENT_AVAILABLE":
      return `在 target Pool 加入至少一個已核准 Agent，並確認 eventStage=${task.eventStage ?? "EXTERNAL"} 的 Pool member 有 runtime binding。`;
    case "POOL_AGENT_OFFLINE":
      return "打開 Agent Runtime，確認 Pool 內 Agent online、credential active、heartbeat healthy。";
    case "POOL_AGENT_CAPACITY_FULL":
      return "等待 Pool 內 Agent 釋放容量、提高 maxConcurrentTasks，或加入更多 Agent。";
    case "POOL_AGENT_BACKOFF":
      return "檢查 Pool 內 Agent 的近期失敗紀錄，清除 backoff 或改派其他 Pool member。";
    case "AGENT_OFFLINE":
      return "打開 Agent Runtime，確認 Agent online、capacity、backoff 與 credential 狀態。";
    case "NO_DISPATCH_REQUEST":
      return "Flow evidence 已存在但尚未送出 Dispatch Request；先 Retry Dispatch，若仍失敗再檢查 dispatch ledger。";
    case "NO_RESULT_CALLBACK":
      return "Dispatch 已送出但尚未收到 Agent RESULT；檢查 Callback Inbox / Gateway callback relay。";
    default:
      return caseTimeline?.fixAction ?? task.nextAction ?? "確認 Source Flow / Rule target Pool / Agent Pool member / Runtime delivery / RESULT callback evidence 一致。";
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
  if (code.includes("SKILL") || code.includes("CAPABILITY")) return "Capability Tag Reference";
  if (code.includes("AGENT_ASSIGNMENT")) return "Pool Agent Selection";
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
        ? `matchedFlowId=${task.matchedFlowId ?? "source-default"} · matchedRuleId=${task.matchedRuleId ?? "SOURCE_DEFAULT"}`
        : "缺少 Source Flow 或 default Agent Pool。請回 Dispatch Flows 建立來源系統的 Source Flow。",
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
      message: poolReady ? `targetPool=${task.targetPoolId ?? task.assignedPoolId}` : "尚未決定 target Pool。請設定 Source Flow default Pool 或 Flow Rule target Pool。",
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
      message: agentId ?? "target Pool 內尚未選出可用 Agent。請檢查 Pool members、Agent approval、online、capacity。",
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
      message: dispatchRequestReady ? `dispatchRequestId=${task.dispatchRequestId ?? "from timeline"}` : "尚未建立 Dispatch Request。可先 Retry Dispatch。",
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
      message: ackReady ? "Agent ACK observed." : "等待 Agent ACK / runtime ledger 更新。",
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
      message: resultReady ? `callbackStatus=${task.callbackStatus ?? "observed"}` : "等待 Agent RESULT callback。",
      details: {
        callbackStatus: task.callbackStatus,
        status: task.status,
      },
    },
    {
      sequence: 8,
      stage: "ISSUE_UPDATE",
      title: "Issue Update",
      status: issueReady ? "PASS" : "PENDING",
      message: issueReady ? "Issue link / ticket update observed." : "若此 Flow 有 Issue policy，等待 Issue sync。",
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

function TaskCaseTimelineRepairPanel({
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
          <h2 className="text-base font-bold text-slate-900">Source Flow / Agent Pool 修復中心</h2>
          <p className="mt-1 text-sm text-slate-500">
            Task Detail 只用 Phase 32 標準派工鏈定位問題：Event → Source Flow → Rule override / default Pool → Agent Pool → Pool member Agent → Runtime Delivery → RESULT。失敗診斷會優先顯示 Pool blocker 與下一步修復動作。
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

function TaskLink({ taskId, label }: Readonly<{ taskId?: string | null; label?: string }>) {
  if (!taskId) return <span>-</span>;
  return (
    <Link href={`/tasks/${encodeURIComponent(taskId)}`} className="font-bold text-blue-700 hover:underline">
      {label ?? taskId}
    </Link>
  );
}

function TaskA2AEvidenceChainPanel({
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
          <div className="text-xs font-black uppercase tracking-wide text-cyan-600">Phase 32-F · A2A Evidence Chain</div>
          <h2 className="mt-1 text-lg font-black text-slate-950">TRIAGE → Classification → RESOLUTION</h2>
          <p className="mt-1 text-sm leading-6 text-slate-600">
            這裡用新手看得懂的方式串起 parent TRIAGE task、分類結果、child RESOLUTION task，以及最後的 Source Flow / Agent Pool / Agent 證據。
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
            <KeyValue label="Source" value={triageTask?.sourceSystem ?? task.sourceSystem ?? "-"} />
            <KeyValue label="Original Event" value={triageTask?.eventType ?? (isResolutionTask ? "UNKNOWN / parent lookup pending" : task.eventType ?? "UNKNOWN")} />
          </div>
        </div>
        <div className="rounded-2xl border border-cyan-200 bg-cyan-50 p-4">
          <div className="text-xs font-black uppercase tracking-wide text-cyan-700">2. Classification Result</div>
          <div className="mt-2 text-sm font-bold leading-6 text-cyan-950">
            {classifiedEventType ? `判斷為 ${classifiedEventType}` : "尚未看到分類結果"}
          </div>
          <div className="mt-3 grid gap-2">
            <KeyValue label="Object Type" value={classifiedObjectType ?? "-"} />
            <KeyValue label="Event Type" value={classifiedEventType ?? "-"} />
            <KeyValue label="Error Code" value={classifiedErrorCode ?? "-"} />
            <KeyValue label="Confidence" value={confidence === undefined ? "-" : `${Math.round(confidence * 100)}%`} />
            <KeyValue label="Recommended Pool" value={recommendedPoolCode ?? "-"} />
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
            <p className="mt-3 text-sm font-semibold text-indigo-900">尚未建立 Resolution child task。</p>
          )}
        </div>
        <div className="rounded-2xl border border-emerald-100 bg-emerald-50 p-4">
          <div className="text-xs font-black uppercase tracking-wide text-emerald-700">4. Pool / Agent Evidence</div>
          <div className="mt-3 grid gap-2">
            <KeyValue label="Matched Flow" value={primaryResolutionTask?.matchedFlowId ?? task.matchedFlowId ?? "-"} />
            <KeyValue label="Matched Rule" value={primaryResolutionTask?.matchedRuleId ?? task.matchedRuleId ?? "SOURCE_DEFAULT / -"} />
            <KeyValue label="Target Pool" value={primaryResolutionTask?.targetPoolId ?? primaryResolutionTask?.assignedPoolId ?? task.targetPoolId ?? task.assignedPoolId ?? "-"} />
            <KeyValue label="Selected Agent" value={primaryResolutionTask?.assignedAgentId ? <Link href={`/agents/${encodeURIComponent(primaryResolutionTask.assignedAgentId)}`} className="text-blue-700 hover:underline">{primaryResolutionTask.assignedAgentId}</Link> : task.assignedAgentId ? <Link href={`/agents/${encodeURIComponent(task.assignedAgentId)}`} className="text-blue-700 hover:underline">{task.assignedAgentId}</Link> : "-"} />
            <KeyValue label="Routing Path" value={primaryResolutionTask?.routingPath ?? task.routingPath ?? "-"} />
          </div>
        </div>
      </div>
      <div className="mt-4 rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm leading-6 text-slate-700">
        <span className="font-black text-slate-900">操作說明：</span>
        未分類事件先由 Source Flow default TRIAGE_POOL 承接；Triage Agent 回報 classification result 後，系統建立 Resolution child task，並重新依分類後 eventType 命中 Flow Rule target Pool。Capability 在此畫面只做能力標籤參考，不作為第一版派單阻擋條件。
      </div>
    </section>
  );
}

function PayloadPanel({ task }: Readonly<{ task: CoreTaskRuntimeView }>) {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <h2 className="text-base font-bold text-slate-900">
        Core Payload / Runtime View
      </h2>
      <p className="mt-1 text-sm text-slate-500">
        顯示 Core task runtime-view 回傳的 payload。完整 payload 權限與遮蔽應由
        Core 控制。
      </p>
      <div className="mt-4">
        {task.payload ? (
          <RawDiagnosticsPanel title="Core raw payload" value={task.payload} />
        ) : (
          <EmptyState
            title="沒有 Payload"
            description="Core runtime-view 未回傳 payload，或目前角色無權檢視 payload。"
          />
        )}
      </div>
    </section>
  );
}

const MODERATE_CONFIRMATION = "CONFIRM_RECOVERY_ACTION";
const HIGH_RISK_CONFIRMATION = "CONFIRM_HIGH_RISK_RECOVERY";

type TaskPendingAction =
  | "retry"
  | "cancel"
  | "reassign"
  | "triggerRecovery"
  | "deadLetter"
  | "restoreDeadLetter"
  | "escalate";

function isTerminal(task: CoreTaskRuntimeView): boolean {
  return ["COMPLETED", "FAILED", "CANCELLED", "TIMED_OUT", "TIMEOUT"].includes(
    String(task.status ?? "").toUpperCase(),
  );
}

function shouldAllowRetry(task: CoreTaskRuntimeView): boolean {
  const taskStatus = String(task.status ?? "").toUpperCase();
  const dispatchStatus = String(task.dispatchStatus ?? "").toUpperCase();
  return (
    ["FAILED", "TIMEOUT", "CANCELLED"].includes(taskStatus) ||
    ["DELIVERY_FAILED", "DEAD_LETTER", "RETRY_PENDING"].includes(dispatchStatus)
  );
}

function buildTaskDiagnosticsTabs(
  input: Readonly<{
    row: TaskDispatchDashboardRow;
    task: CoreTaskRuntimeView;
    attemptHistory: CoreDispatchAttemptHistoryRecord[];
    dispatchLedger: CoreDispatchAttemptLedger[];
    dispatchLedgerError?: string;
    callbackInbox: CoreCallbackInboxEntry[];
    callbackInboxSummary?: CoreCallbackInboxSummary;
    callbackInboxError?: string;
    timeline?: CoreDispatchTimelineResponse;
    timelineError?: string;
    caseTimeline?: CoreTaskCaseTimelineView;
    caseTimelineError?: string;
    taskFamily?: { parentTask?: CoreTaskRuntimeView; childTasks: CoreTaskRuntimeView[] };
    taskFamilyError?: string;
    attemptHistoryError?: string;
    routingDecisions?: CoreRoutingDecisionRecord[];
    routingDecisionsError?: string;
    delivery?: RuntimeAttemptSummary;
    deliveryRuntimeError?: string;
    callbackRelay?: RuntimeAttemptSummary;
    callbackRelayRuntimeError?: string;
    triggeringRecovery: boolean;
    movingDeadLetter: boolean;
    retrying: boolean;
    restoringDeadLetter: boolean;
    onRetryDispatch: () => void;
    onTriggerRecoveryNow: () => void;
    onMoveToDeadLetter: () => void;
    onRestoreFromDeadLetter: () => void;
  }>,
): TaskDiagnosticsTab[] {
  return [
    {
      id: "operator-lifecycle",
      label: "Lifecycle 詳細",
      description:
        "以工程師視角檢查每個 lifecycle step 的原始狀態、badge、時間與 reference。上方 Operator Stepper 負責快速定位卡點，這裡保留完整階段解釋。",
      badge: input.row.task.dispatchStatus ?? input.row.task.status,
      content: <DispatchLifecycleStepper row={input.row} />,
    },
    {
      id: "timeline-event-log",
      label: "Timeline Event Log",
      description:
        "Core canonical timeline event log。用於追查每個 routing、assignment、dispatch、callback、retry、DLQ 與 audit event。",
      badge: input.timeline?.events?.length
        ? `${input.timeline.events.length}`
        : undefined,
      content: (
        <DispatchTimelinePanel
          timeline={input.timeline}
          error={input.timelineError}
        />
      ),
    },
    {
      id: "routing-agent-runtime",
      label: "Agent & Runtime",
      description:
        "檢查為什麼選這個 Agent、Netty 是否完成 delivery、callback relay 是否有收到 Agent 回覆。",
      content: (
        <>
          <RoutingExplainabilityPanel
            decisions={input.routingDecisions}
            error={input.routingDecisionsError}
          />
          <RuntimeAttemptPanel
            title="Netty Delivery Runtime"
            description="顯示 Netty command delivery 的最近 attempt。這是 transport runtime 資料，不是 Task 權威狀態。"
            attempt={input.delivery}
            warning={input.deliveryRuntimeError}
          />
          <RuntimeAttemptPanel
            title="Netty Callback Relay Runtime"
            description="顯示 Netty 收到 Agent callback 後 relay 到 Core 的最近 attempt。Core 是否接受 callback 仍以 Core callback persisted status 為準。"
            attempt={input.callbackRelay}
            warning={input.callbackRelayRuntimeError}
          />
        </>
      ),
    },
    {
      id: "recovery-control",
      label: "Recovery",
      description:
        "標準處理動作只提供 Source Flow、Agent Pool、Agent Runtime 與 Retry Dispatch。",
      content: (
        <>
          <RecoveryVisibilityPanel task={input.task} />
          <StandardDispatchActionsPanel
            task={input.task}
            retrying={input.retrying}
            onRetry={input.onRetryDispatch}
          />
        </>
      ),
    },
    {
      id: "dispatch-ledger",
      label: "Dispatch Ledger",
      description:
        "Core authoritative dispatch ledger。這是後續 callback inbox / topology recovery 的查詢基礎，不能用 Gateway node telemetry 取代。",
      badge: input.dispatchLedger.length
        ? `${input.dispatchLedger.length}`
        : undefined,
      content: (
        <DispatchLedgerPanel
          ledger={input.dispatchLedger}
          error={input.dispatchLedgerError}
        />
      ),
    },
    {
      id: "callback-inbox",
      label: "Callback Inbox",
      description:
        "Core durable callback inbox。ACK / RESULT / ERROR 可以由任意 Gateway relay，Core 以 persisted callback record 與 idempotency key 作為處理真相。",
      badge: input.callbackInbox.length
        ? `${input.callbackInbox.length}`
        : undefined,
      content: (
        <CallbackInboxPanel
          entries={input.callbackInbox}
          summary={input.callbackInboxSummary}
          error={input.callbackInboxError}
        />
      ),
    },
    {
      id: "attempt-history",
      label: "Attempt History",
      description:
        "Core append-only dispatch attempt history。用於確認 delayed recovery、runtime backoff、scanner claim 與 retry 次數。",
      badge: input.attemptHistory.length
        ? `${input.attemptHistory.length}`
        : undefined,
      content: (
        <AttemptHistoryPanel
          history={input.attemptHistory}
          error={input.attemptHistoryError}
        />
      ),
    },
    {
      id: "raw-diagnostics",
      label: "Raw Diagnostics",
      description:
        "最低層 raw payload 與 Core authority 資料。預設只給工程師/SRE 用來複製與比對 API response。",
      content: (
        <>
          <TaskAuthoritySummary task={input.task} />
          <PayloadPanel task={input.task} />
        </>
      ),
    },
  ];
}

function buildTaskControlConsoleTabs(
  input: Readonly<{
    row: TaskDispatchDashboardRow;
    task: CoreTaskRuntimeView;
    diagnosis: TaskDispatchDiagnosis;
    dispatchRequests?: import("@/lib/types/core").CoreDispatchRequest[];
    attemptHistory: CoreDispatchAttemptHistoryRecord[];
    dispatchLedger: CoreDispatchAttemptLedger[];
    dispatchLedgerError?: string;
    callbackInbox: CoreCallbackInboxEntry[];
    callbackInboxSummary?: CoreCallbackInboxSummary;
    callbackInboxError?: string;
    timeline?: CoreDispatchTimelineResponse;
    timelineError?: string;
    caseTimeline?: CoreTaskCaseTimelineView;
    caseTimelineError?: string;
    taskFamily?: { parentTask?: CoreTaskRuntimeView; childTasks: CoreTaskRuntimeView[] };
    taskFamilyError?: string;
    dispatchEvidence?: CoreTaskDispatchEvidenceView;
    dispatchEvidenceError?: string;
    runtimeVerification?: CoreTaskRuntimeVerificationView;
    runtimeVerificationError?: string;
    attemptHistoryError?: string;
    routingDecisions?: CoreRoutingDecisionRecord[];
    routingDecisionsError?: string;
    dispatchRequirements?: CoreTaskDispatchRequirements;
    dispatchRequirementsError?: string;
    eligibleAgents?: CoreTaskEligibleAgentsResponse;
    eligibleAgentsError?: string;
    eligibleAgentsV2?: CoreDispatchEligibilityV2Response;
    eligibleAgentsV2Error?: string;
    delivery?: RuntimeAttemptSummary;
    deliveryRuntimeError?: string;
    callbackRelay?: RuntimeAttemptSummary;
    callbackRelayRuntimeError?: string;
    triggeringRecovery: boolean;
    movingDeadLetter: boolean;
    retrying: boolean;
    restoringDeadLetter: boolean;
    retryingIssueSyncActionId?: string;
    onRetryIssueSync: (actionId: string) => void;
    onRetryDispatch: () => void;
    onDispatchOperatorCommand: (command: DispatchOperatorCommand) => void;
    onTriggerRecoveryNow: () => void;
    onMoveToDeadLetter: () => void;
    onRestoreFromDeadLetter: () => void;
  }>,
): TaskControlConsoleTab[] {
  const dispatchStatus = input.task.dispatchStatus ?? input.task.status;
  const eligibleCount = input.eligibleAgents?.eligibleAgents?.length ?? 0;
  const blockedCount = input.eligibleAgents?.blockedAgents?.length ?? 0;
  return [
    {
      id: "overview",
      label: "總覽",
      badge: dispatchStatus,
      description:
        "先用 Operator 視角確認此 Task 的 authority boundary、關係鏈、目前 lifecycle 卡點與 beginner flow。這一頁不放 raw diagnostics。",
      content: (
        <>
          <section className="rounded-2xl border border-blue-100 bg-blue-50 p-5 text-sm text-blue-900 shadow-sm">
            <h2 className="text-base font-bold">Callback Truth Boundary</h2>
            <p className="mt-1">{taskAuthorityDisclaimer()}</p>
          </section>
          <StandardDispatchTimelinePanel
            task={input.task}
            timeline={input.timeline}
            diagnosis={input.diagnosis}
          />
          <EntityRelationshipStrip
            title="Task 派工關係鏈"
            description="用同一條鏈看懂此 Task 從 Source Flow、Agent Pool、Pool Agent、Runtime delivery 到 callback / Issue 的證據。Capability 只作為能力標籤參考。"
            steps={buildTaskRelationshipSteps(input.row)}
          />
          <TaskA2AEvidenceChainPanel
            task={input.task}
            parentTask={input.taskFamily?.parentTask}
            childTasks={input.taskFamily?.childTasks}
            caseTimeline={input.caseTimeline}
            familyError={input.taskFamilyError}
          />
          <TaskRuntimeVerificationPanel
            verification={input.runtimeVerification}
            error={input.runtimeVerificationError}
            retrying={input.retrying}
            onRetry={() => input.onRetryDispatch()}
          />
          <TaskLifecycleOperatorPanel row={input.row} />
          <BeginnerTaskFlowPanel row={input.row} />
        </>
      ),
    },
    {
      id: "dispatch-lifecycle",
      label: "派工時間線",
      badge: input.timeline?.events?.length
        ? `${input.timeline.events.length}`
        : undefined,
      description:
        "查看 Core lifecycle、timeline event、attempt history 與 dispatch ledger。這裡用來判斷任務卡在哪個派工階段。",
      content: (
        <>
          <StandardDispatchTimelinePanel task={input.task} timeline={input.timeline} diagnosis={input.diagnosis} />
          <DispatchLifecycleStepper row={input.row} />
          <DispatchTimelinePanel
            timeline={input.timeline}
            error={input.timelineError}
          />
          <AttemptHistoryPanel
            history={input.attemptHistory}
            error={input.attemptHistoryError}
          />
          <DispatchLedgerPanel
            ledger={input.dispatchLedger}
            error={input.dispatchLedgerError}
          />
        </>
      ),
    },
    {
      id: "agent-selection",
      label: "Pool / Agent 判定",
      badge: eligibleCount
        ? `${eligibleCount} eligible`
        : blockedCount
          ? `${blockedCount} blocked`
          : undefined,
      description:
        "集中檢查 Source Flow、Agent Pool、Pool member Agent、Runtime 與 routing evidence。Capability 只顯示為能力標籤參考，不作為第一版 routing gate。",
      content: (
        <>
          <CapabilityResolutionMatrix
            rows={buildCapabilityMatrix({
              taskRequiredCapabilities: input.task.requiredCapabilities,
            })}
            title="Task 需要能力與 Agent 關係"
            description="Task Detail 顯示 archived requiredCapabilities；R8 正式修復以 Flow-owned Rule / Capability / Agent assignment 為準。"
          />
          <TaskDispatchEligibilityV2Panel
            response={input.eligibleAgentsV2}
            archivedAgents={input.eligibleAgents}
            error={input.eligibleAgentsV2Error}
          />
          <RoutingExplainabilityPanel
            decisions={input.routingDecisions}
            error={input.routingDecisionsError}
          />
        </>
      ),
    },
    {
      id: "issue-result",
      label: "結果與 Issue",
      badge: input.task.callbackStatus,
      description:
        "集中查看 Issue Tracking、adapter actions、callback inbox 與任務結果。這裡避免混入 Agent selection 或 routing score。",
      content: (
        <>
          <TaskWorkbenchPanel
            row={input.row}
            onRetryIssueSync={input.onRetryIssueSync}
            retryingIssueSyncActionId={input.retryingIssueSyncActionId}
          />
          <CallbackInboxPanel
            entries={input.callbackInbox}
            summary={input.callbackInboxSummary}
            error={input.callbackInboxError}
          />
        </>
      ),
    },
    {
      id: "troubleshooting",
      label: "處理建議",
      badge: input.diagnosis.code,
      description:
        "依單一標準派工主因處理；舊 Scope、Agent 與外部診斷修復不再是一般操作入口。",
      content: (
        <>
          <TaskPrimaryDiagnosisPanel
            task={input.task}
            diagnosis={input.diagnosis}
            retrying={input.retrying}
            allowRetry={false}
            onRetry={() => input.onRetryDispatch()}
          />
          <TaskA2AEvidenceChainPanel
            task={input.task}
            parentTask={input.taskFamily?.parentTask}
            childTasks={input.taskFamily?.childTasks}
            caseTimeline={input.caseTimeline}
            familyError={input.taskFamilyError}
          />
          <RecoveryVisibilityPanel task={input.task} />
          <StandardDispatchActionsPanel
            task={input.task}
            retrying={input.retrying}
            onRetry={input.onRetryDispatch}
          />
        </>
      ),
    },
    {
      id: "debug",
      label: "Support Debug",
      description:
        "工程師/SRE 才需要的完整 raw diagnostics。包含Core runtime-view payload 與底層 runtime/ledger/callback 資料。",
      content: (
        <>
          <DispatchAssignmentEvidencePanel
            task={input.task}
            routingDecisions={input.routingDecisions}
            issueTracking={input.task.issueTracking}
            dispatchRequests={input.dispatchRequests}
            deliveryAttempt={input.delivery}
            callbackRelayAttempt={input.callbackRelay}
            callbackInbox={input.callbackInbox}
            callbackInboxSummary={input.callbackInboxSummary}
            callbackInboxError={input.callbackInboxError}
          />
          <TaskDispatchEligibilityContractPanel
            requirements={input.dispatchRequirements}
            eligibleAgents={input.eligibleAgents}
            requirementsError={input.dispatchRequirementsError}
            eligibleAgentsError={input.eligibleAgentsError}
          />
          <TaskCaseTimelineRepairPanel
            task={input.task}
            timeline={input.timeline}
            caseTimeline={input.caseTimeline}
            timelineError={input.timelineError}
            caseTimelineError={input.caseTimelineError}
          />
          <TaskDiagnosticsTabs
            tabs={buildTaskDiagnosticsTabs({
            row: input.row,
            task: input.task,
            attemptHistory: input.attemptHistory,
            dispatchLedger: input.dispatchLedger,
            dispatchLedgerError: input.dispatchLedgerError,
            callbackInbox: input.callbackInbox,
            callbackInboxSummary: input.callbackInboxSummary,
            callbackInboxError: input.callbackInboxError,
            timeline: input.timeline,
            timelineError: input.timelineError,
            caseTimeline: input.caseTimeline,
            caseTimelineError: input.caseTimelineError,
            attemptHistoryError: input.attemptHistoryError,
            routingDecisions: input.routingDecisions,
            routingDecisionsError: input.routingDecisionsError,
            delivery: input.delivery,
            deliveryRuntimeError: input.deliveryRuntimeError,
            callbackRelay: input.callbackRelay,
            callbackRelayRuntimeError: input.callbackRelayRuntimeError,
            triggeringRecovery: input.triggeringRecovery,
            movingDeadLetter: input.movingDeadLetter,
            retrying: input.retrying,
            restoringDeadLetter: input.restoringDeadLetter,
            onRetryDispatch: input.onRetryDispatch,
            onTriggerRecoveryNow: input.onTriggerRecoveryNow,
            onMoveToDeadLetter: input.onMoveToDeadLetter,
            onRestoreFromDeadLetter: input.onRestoreFromDeadLetter,
            })}
          />
        </>
      ),
    },
  ];
}

export function TaskDetailView({ taskId }: Readonly<{ taskId: string }>) {
  const {
    data,
    loading,
    refreshing,
    error,
    lastUpdatedAt,
    refresh,
    commandMessage,
    retrying,
    cancelling,
    reassigning,
    triggeringRecovery,
    movingDeadLetter,
    restoringDeadLetter,
    retryingIssueSyncActionId,
    retryTask,
    cancelTask,
    reassignTask,
    triggerRecoveryNow,
    moveToDeadLetter,
    restoreFromDeadLetter,
    retryIssueSync,
  } = useTaskDetail(taskId);
  const [pendingAction, setPendingAction] = useState<TaskPendingAction | null>(null);
  const [actionMessage, setActionMessage] = useState<string | null>(null);

  if (loading)
    return (
      <LoadingBox label={`讀取 ${taskId} Core Task / Dispatch runtime...`} />
    );
  if (error) return <ErrorBox message={error} />;
  if (!data)
    return (
      <EmptyState
        title="找不到任務"
        description="Core 沒有回傳此 Task runtime-view。"
      />
    );

  const { task, delivery, callbackRelay } = data.row;
  const attemptHistory = data.attemptHistory ?? [];
  const diagnosis = deriveTaskDispatchDiagnosis({
    task,
    evidence: data.dispatchEvidence,
    runtimeVerification: data.runtimeVerification,
  });

  function runDispatchOperatorCommand(command: DispatchOperatorCommand) {
    if (command === "triggerRecoveryNow") setPendingAction("triggerRecovery");
    else if (command === "manualRetry") setPendingAction("retry");
    else if (command === "escalate") setPendingAction("escalate");
    else if (command === "deadLetter") setPendingAction("deadLetter");
  }

  async function executePendingAction(values: TaskActionDialogValues) {
    if (!pendingAction) return;
    const controlRequest = (risk: "MODERATE" | "HIGH"): CoreRecoveryGovernanceActionRequest => ({
      operatorId: "admin-ui",
      reason: values.reason,
      riskAcknowledged: true,
      confirmationPhrase: values.confirmationPhrase ?? (risk === "HIGH" ? HIGH_RISK_CONFIRMATION : MODERATE_CONFIRMATION),
      requestId: `admin-ui-${pendingAction}-${Date.now()}`,
    });

    if (pendingAction === "retry") await retryTask();
    else if (pendingAction === "cancel") await cancelTask();
    else if (pendingAction === "reassign") await reassignTask(values.targetAgentId);
    else if (pendingAction === "triggerRecovery") await triggerRecoveryNow(controlRequest("MODERATE"));
    else if (pendingAction === "deadLetter") await moveToDeadLetter(controlRequest("HIGH"));
    else if (pendingAction === "restoreDeadLetter") await restoreFromDeadLetter({ ...controlRequest("HIGH"), resetAttempts: true, immediate: true });
    else if (pendingAction === "escalate") {
      const result = await coreAdminApi.escalateTask(task.taskId, { reason: values.reason });
      setActionMessage(result.message ?? `Task ${task.taskId} 已送出 Escalate。`);
      await refresh();
    }
    setPendingAction(null);
  }


  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <Link
            href="/tasks"
            className="text-sm font-semibold text-blue-600 hover:text-blue-700"
          >
            ← 返回 Task 清單
          </Link>
          <h1 className="mt-2 text-2xl font-bold text-slate-900">
            Task 詳細資料
          </h1>
          <p className="mt-1 break-all text-sm text-slate-500">{task.taskId}</p>
        </div>
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
          {shouldAllowRetry(task) ? (
            <button
              type="button"
              onClick={() => setPendingAction("retry")}
              disabled={retrying}
              className="rounded-xl bg-blue-600 px-4 py-2 text-sm font-bold text-white shadow-sm hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-slate-300"
            >
              {retrying ? "重新派工中…" : "重新派工"}
            </button>
          ) : null}
          {!isTerminal(task) ? (
            <button
              type="button"
              onClick={() => setPendingAction("reassign")}
              disabled={reassigning}
              className="rounded-xl border border-slate-200 px-4 py-2 text-sm font-bold text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:bg-slate-100"
            >
              {reassigning ? "改派中…" : "改派 Agent"}
            </button>
          ) : null}
          {!isTerminal(task) ? (
            <button
              type="button"
              onClick={() => setPendingAction("cancel")}
              disabled={cancelling}
              className="rounded-xl border border-rose-200 px-4 py-2 text-sm font-bold text-rose-700 hover:bg-rose-50 disabled:cursor-not-allowed disabled:bg-slate-100"
            >
              {cancelling ? "取消中…" : "取消 Task"}
            </button>
          ) : null}
          <RefreshButton
            refreshing={refreshing}
            lastUpdatedAt={lastUpdatedAt}
            onRefresh={refresh}
          />
        </div>
      </div>

      <CommandMessage message={actionMessage ?? commandMessage} />

      <TaskPrimaryDiagnosisPanel
        task={task}
        diagnosis={diagnosis}
        retrying={retrying}
        allowRetry={shouldAllowRetry(task)}
        onRetry={() => setPendingAction("retry")}
      />

      <TaskA2AEvidenceChainPanel
        task={task}
        parentTask={data.taskFamily?.parentTask}
        childTasks={data.taskFamily?.childTasks}
        caseTimeline={data.caseTimeline}
        familyError={data.taskFamilyError}
      />

      <TaskControlConsoleTabs
        tabs={buildTaskControlConsoleTabs({
          row: data.row,
          task,
          diagnosis,
          dispatchRequests: data.dispatchRequests,
          attemptHistory,
          dispatchLedger: data.dispatchLedger ?? [],
          dispatchLedgerError: data.dispatchLedgerError,
          callbackInbox: data.callbackInbox ?? [],
          callbackInboxSummary: data.callbackInboxSummary,
          callbackInboxError: data.callbackInboxError,
          timeline: data.timeline,
          timelineError: data.timelineError,
          caseTimeline: data.caseTimeline,
          caseTimelineError: data.caseTimelineError,
          taskFamily: data.taskFamily,
          taskFamilyError: data.taskFamilyError,
          dispatchEvidence: data.dispatchEvidence,
          dispatchEvidenceError: data.dispatchEvidenceError,
          runtimeVerification: data.runtimeVerification,
          runtimeVerificationError: data.runtimeVerificationError,
          attemptHistoryError: data.attemptHistoryError,
          routingDecisions: data.routingDecisions,
          routingDecisionsError: data.routingDecisionsError,
          dispatchRequirements: data.dispatchRequirements,
          dispatchRequirementsError: data.dispatchRequirementsError,
          eligibleAgents: data.eligibleAgents,
          eligibleAgentsError: data.eligibleAgentsError,
          eligibleAgentsV2: data.eligibleAgentsV2,
          eligibleAgentsV2Error: data.eligibleAgentsV2Error,
          delivery,
          deliveryRuntimeError: data.deliveryRuntimeError,
          callbackRelay,
          callbackRelayRuntimeError: data.callbackRelayRuntimeError,
          triggeringRecovery,
          movingDeadLetter,
          restoringDeadLetter,
                      retrying,
          retryingIssueSyncActionId: retryingIssueSyncActionId ?? undefined,
          onRetryIssueSync: retryIssueSync,
          onRetryDispatch: () => setPendingAction("retry"),
          onDispatchOperatorCommand: runDispatchOperatorCommand,
          onTriggerRecoveryNow: () => setPendingAction("triggerRecovery"),
          onMoveToDeadLetter: () => setPendingAction("deadLetter"),
          onRestoreFromDeadLetter: () => setPendingAction("restoreDeadLetter"),
        })}
      />
      <TaskActionDialog
        open={pendingAction !== null}
        title={
          pendingAction === "cancel" ? "取消 Task" :
          pendingAction === "reassign" ? "改派 Agent" :
          pendingAction === "triggerRecovery" ? "立即執行 Recovery" :
          pendingAction === "deadLetter" ? "移至 Dead Letter" :
          pendingAction === "restoreDeadLetter" ? "還原 Dead Letter" :
          pendingAction === "escalate" ? "升級人工處理" : "重新派工"
        }
        target={task.taskId}
        description="所有操作都由 Core 權威狀態機執行，原因與結果會寫入 Task timeline。"
        confirmLabel={pendingAction === "cancel" ? "確認取消" : pendingAction === "deadLetter" ? "確認移至 Dead Letter" : pendingAction === "restoreDeadLetter" ? "確認還原" : pendingAction === "escalate" ? "確認升級" : pendingAction === "reassign" ? "確認改派" : "確認執行"}
        tone={pendingAction === "cancel" || pendingAction === "deadLetter" ? "danger" : "warning"}
        isRunning={retrying || cancelling || reassigning || triggeringRecovery || movingDeadLetter || restoringDeadLetter}
        allowTargetAgent={pendingAction === "reassign"}
        requiredPhrase={pendingAction === "deadLetter" || pendingAction === "restoreDeadLetter" ? HIGH_RISK_CONFIRMATION : pendingAction === "triggerRecovery" || pendingAction === "escalate" ? MODERATE_CONFIRMATION : undefined}
        onCancel={() => setPendingAction(null)}
        onConfirm={executePendingAction}
      />
    </div>
  );
}
