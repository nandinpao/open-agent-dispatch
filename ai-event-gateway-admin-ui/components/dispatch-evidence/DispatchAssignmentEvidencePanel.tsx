import type { ReactNode } from "react";
import Link from "next/link";
import { EmptyState } from "@/components/common/EmptyState";
import {
  normalizeOperatorDispatchFailureReason,
  type OperatorFailureReason,
} from "@/lib/dispatch-evidence/operatorFailureReasons";
import { StatusBadge } from "@/components/common/StatusBadge";
import type {
  CoreAgentSetupReadinessResponse,
  CoreCallbackInboxEntry,
  CoreCallbackInboxSummary,
  CoreDispatchRequest,
  CoreRoutingDecisionRecord,
  CoreTaskIssueTracking,
  CoreTaskRuntimeView,
} from "@/lib/types/core";
import { formatDateTime } from "@/lib/utils/format";
import type { RuntimeAttemptSummary } from "@/lib/dashboard/taskDispatchMerge";

interface DispatchAssignmentEvidencePanelProps {
  task?: CoreTaskRuntimeView;
  routingDecisions?: CoreRoutingDecisionRecord[];
  dispatchRequests?: CoreDispatchRequest[];
  issueTracking?: CoreTaskIssueTracking;
  setupReadiness?: CoreAgentSetupReadinessResponse;
  runtimeReportedCapabilities?: string[];
  deliveryAttempt?: RuntimeAttemptSummary;
  callbackRelayAttempt?: RuntimeAttemptSummary;
  callbackInbox?: CoreCallbackInboxEntry[];
  callbackInboxSummary?: CoreCallbackInboxSummary;
  callbackInboxError?: string;
  title?: string;
  description?: string;
  compact?: boolean;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function normalize(value: string): string {
  return value
    .trim()
    .toUpperCase()
    .replace(/[.\-\s]+/g, "_");
}

function unique(values: string[]): string[] {
  const seen = new Set<string>();
  const result: string[] = [];
  for (const value of values) {
    const normalized = normalize(value);
    if (!normalized || seen.has(normalized)) continue;
    seen.add(normalized);
    result.push(normalized);
  }
  return result;
}

function asStringArray(value: unknown): string[] {
  if (Array.isArray(value))
    return unique(value.map((item) => String(item ?? "")).filter(Boolean));
  if (typeof value === "string")
    return unique(
      value
        .split(",")
        .map((item) => item.trim())
        .filter(Boolean),
    );
  return [];
}

function selectedCandidate(decision?: CoreRoutingDecisionRecord) {
  if (!decision?.candidates?.length) return undefined;
  return (
    decision.candidates.find(
      (candidate) => candidate.agentId === decision.selectedAgentId,
    ) ?? decision.candidates[0]
  );
}

function extractBreakdownList(
  decision: CoreRoutingDecisionRecord | undefined,
  keys: string[],
): string[] {
  const candidate = selectedCandidate(decision);
  const breakdown = candidate?.scoreBreakdown;
  if (!isRecord(breakdown)) return [];
  for (const key of keys) {
    const values = asStringArray(breakdown[key]);
    if (values.length) return values;
  }
  return [];
}

function latestDispatchRequest(
  task: CoreTaskRuntimeView | undefined,
  dispatchRequests: CoreDispatchRequest[] | undefined,
): CoreDispatchRequest | undefined {
  if (!dispatchRequests?.length) return undefined;
  if (task?.dispatchRequestId) {
    const linked = dispatchRequests.find(
      (request) => request.dispatchRequestId === task.dispatchRequestId,
    );
    if (linked) return linked;
  }
  return [...dispatchRequests].sort((left, right) => {
    const leftTime = Date.parse(left.updatedAt ?? left.createdAt ?? "") || 0;
    const rightTime = Date.parse(right.updatedAt ?? right.createdAt ?? "") || 0;
    return rightTime - leftTime;
  })[0];
}

function normalizedStatus(value: unknown): string {
  return normalize(String(value ?? ""));
}

function hasAnyStatus(value: unknown, statuses: string[]): boolean {
  const normalized = normalizedStatus(value);
  return statuses.some((status) => normalized.includes(normalize(status)));
}

function callbackType(entry: CoreCallbackInboxEntry): string {
  return normalizedStatus(
    entry.callbackType ??
      entry.payload?.callbackType ??
      entry.payload?.eventType,
  );
}

function acceptedCallback(entry: CoreCallbackInboxEntry): boolean {
  return entry.accepted !== false && !entry.duplicate && !entry.ignoredReason;
}

function hasAckCallback(entries: CoreCallbackInboxEntry[]): boolean {
  return entries.some(
    (entry) =>
      acceptedCallback(entry) &&
      ["TASK_ACK", "ACK", "AI_TASK_ACK"].includes(callbackType(entry)),
  );
}

function hasTerminalCallback(
  entries: CoreCallbackInboxEntry[],
  summary?: CoreCallbackInboxSummary,
): boolean {
  if (summary?.terminalCallbackReceived) return true;
  return entries.some((entry) => {
    const type = callbackType(entry);
    return (
      acceptedCallback(entry) &&
      [
        "TASK_RESULT",
        "RESULT",
        "TASK_ERROR",
        "ERROR",
        "AI_TASK_RESULT",
        "AI_TASK_ERROR",
      ].includes(type)
    );
  });
}

function latestTerminalCallback(
  entries: CoreCallbackInboxEntry[],
): CoreCallbackInboxEntry | undefined {
  return [...entries]
    .filter((entry) =>
      [
        "TASK_RESULT",
        "RESULT",
        "TASK_ERROR",
        "ERROR",
        "AI_TASK_RESULT",
        "AI_TASK_ERROR",
      ].includes(callbackType(entry)),
    )
    .sort((left, right) => {
      const leftTime =
        Date.parse(
          left.processedAt ?? left.receivedAt ?? left.occurredAt ?? "",
        ) || 0;
      const rightTime =
        Date.parse(
          right.processedAt ?? right.receivedAt ?? right.occurredAt ?? "",
        ) || 0;
      return rightTime - leftTime;
    })[0];
}

function buildDeliveryConfirmation(
  input: DispatchAssignmentEvidencePanelProps,
  latestRequest?: CoreDispatchRequest,
) {
  const callbackInbox = input.callbackInbox ?? [];
  const delivered = Boolean(
    latestRequest?.dispatchedAt ||
    hasAnyStatus(latestRequest?.status, ["DISPATCHED", "COMPLETED"]) ||
    hasAnyStatus(input.task?.dispatchDeliveryStatus, [
      "DELIVERED",
      "DISPATCHED",
    ]) ||
    hasAnyStatus(input.deliveryAttempt?.status, [
      "DELIVERED",
      "SUCCESS",
      "ACCEPTED",
    ]),
  );
  const acked = hasAckCallback(callbackInbox);
  const terminal = hasTerminalCallback(
    callbackInbox,
    input.callbackInboxSummary,
  );
  const latestTerminal = latestTerminalCallback(callbackInbox);
  const completed = Boolean(
    hasAnyStatus(input.task?.status, ["COMPLETED", "SUCCEEDED", "SUCCESS"]) ||
    hasAnyStatus(latestRequest?.status, ["COMPLETED"]) ||
    hasAnyStatus(latestTerminal?.newTaskStatus, [
      "COMPLETED",
      "SUCCEEDED",
      "SUCCESS",
    ]),
  );
  const callbackRelay = Boolean(
    input.callbackRelayAttempt ||
    (callbackInbox.length > 0 && !input.callbackInboxError),
  );
  const issueLinked = Boolean(
    input.issueTracking?.issueUrl ||
    input.issueTracking?.issueId ||
    input.task?.issueTracking?.issueUrl ||
    input.task?.issueTracking?.issueId,
  );
  return {
    delivered,
    acked,
    terminal,
    latestTerminal,
    completed,
    callbackRelay,
    issueLinked,
    callbackCount: callbackInbox.length,
  };
}

function DeliveryStep({
  label,
  done,
  detail,
}: Readonly<{ label: string; done: boolean; detail?: ReactNode }>) {
  return (
    <div
      className={`rounded-xl border px-3 py-2 ${done ? "border-emerald-200 bg-emerald-50" : "border-slate-200 bg-slate-50"}`}
    >
      <div className="flex items-center justify-between gap-2">
        <span className="text-xs font-black uppercase tracking-wide text-slate-700">
          {label}
        </span>
        <StatusBadge status={done ? "CONFIRMED" : "PENDING"} />
      </div>
      {detail ? (
        <div className="mt-1 text-xs font-semibold leading-5 text-slate-600">
          {detail}
        </div>
      ) : null}
    </div>
  );
}

function ChipList({
  values,
  empty = "-",
}: Readonly<{ values: string[]; empty?: string }>) {
  if (!values.length)
    return (
      <span className="text-sm font-semibold text-slate-400">{empty}</span>
    );
  return (
    <div className="flex flex-wrap gap-1.5">
      {values.map((value) => (
        <span
          key={value}
          className="rounded-full border border-slate-200 bg-white px-2.5 py-1 text-[11px] font-black uppercase tracking-wide text-slate-700"
        >
          {value}
        </span>
      ))}
    </div>
  );
}

function EvidenceCard({
  label,
  children,
  tone = "neutral",
}: Readonly<{
  label: string;
  children: ReactNode;
  tone?: "neutral" | "good" | "warn" | "bad";
}>) {
  const className = {
    neutral: "border-slate-200 bg-slate-50",
    good: "border-emerald-200 bg-emerald-50",
    warn: "border-amber-200 bg-amber-50",
    bad: "border-rose-200 bg-rose-50",
  }[tone];
  return (
    <div className={`rounded-2xl border px-4 py-3 ${className}`}>
      <div className="text-[11px] font-black uppercase tracking-wide text-slate-500">
        {label}
      </div>
      <div className="mt-2 min-h-6 text-sm font-bold text-slate-900">
        {children}
      </div>
    </div>
  );
}

export function buildDispatchAssignmentEvidence(
  input: DispatchAssignmentEvidencePanelProps,
) {
  const latestDecision =
    input.routingDecisions?.[0] ?? input.task?.latestRoutingDecision;
  const selected = selectedCandidate(latestDecision);
  const latestRequest = latestDispatchRequest(
    input.task,
    input.dispatchRequests,
  );
  const issue = input.issueTracking ?? input.task?.issueTracking;
  const rawRequirements = unique([
    ...asStringArray(input.task?.requiredCapabilities),
    ...extractBreakdownList(latestDecision, [
      "rawTaskRequirements",
      "requiredCapabilities",
    ]),
  ]);
  const explicitEffectiveCapabilities = unique([
    ...extractBreakdownList(latestDecision, [
      "effectiveDispatchCapabilities",
      "effectiveCapabilities",
    ]),
    ...asStringArray(selected?.matchedCapabilities),
  ]);
  // P3-Y: Admin-managed capabilities are valid direct dispatch contracts. If a task carries
  // a raw requirement such as CMS_CONTENT_REVIEW or a user-created capability and no candidate
  // has been selected yet, do not call it CONTRACT_NOT_RESOLVED merely because routing has not
  // persisted an explicit effectiveCapabilities array. Treat the raw requirement as the
  // effective direct capability until backend evidence says otherwise.
  const effectiveCapabilities = explicitEffectiveCapabilities.length
    ? explicitEffectiveCapabilities
    : rawRequirements;
  const runtimeCapabilities = unique([
    ...asStringArray(input.runtimeReportedCapabilities),
    ...asStringArray(input.setupReadiness?.runtimeReportedCapabilities),
    ...asStringArray(selected?.matchedCapabilities),
  ]);
  const selectedAgentId =
    latestDecision?.selectedAgentId ??
    latestRequest?.agentId ??
    input.task?.assignedAgentId;
  const deliveryConfirmation = buildDeliveryConfirmation(input, latestRequest);
  return {
    latestDecision,
    selected,
    latestRequest,
    issue,
    rawRequirements,
    effectiveCapabilities,
    runtimeCapabilities,
    selectedAgentId,
    deliveryConfirmation,
  };
}

function OperatorFailureReasonCard({
  reason,
}: Readonly<{ reason: OperatorFailureReason }>) {
  const toneClassName: Record<OperatorFailureReason["tone"], string> = {
    success: "border-emerald-200 bg-emerald-50 text-emerald-950",
    warning: "border-amber-200 bg-amber-50 text-amber-950",
    danger: "border-rose-200 bg-rose-50 text-rose-950",
    info: "border-blue-200 bg-blue-50 text-blue-950",
    neutral: "border-slate-200 bg-slate-50 text-slate-950",
  };
  return (
    <div
      className={`mt-4 rounded-2xl border px-4 py-3 ${toneClassName[reason.tone]}`}
    >
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <div className="text-[11px] font-black uppercase tracking-wide opacity-70">
            Operator-readable failure reason
          </div>
          <div className="mt-1 flex flex-wrap items-center gap-2">
            <span className="rounded-full bg-white/70 px-2.5 py-1 text-[11px] font-black uppercase tracking-wide">
              {reason.code}
            </span>
            <span className="text-base font-black">{reason.title}</span>
          </div>
          <p className="mt-2 text-sm font-semibold leading-6 opacity-90">
            {reason.message}
          </p>
          <p className="mt-2 text-sm font-bold leading-6">
            <span className="opacity-70">Next action: </span>
            {reason.nextAction}
          </p>
        </div>
        {reason.technicalCodes.length ? (
          <div className="min-w-0 rounded-xl border border-white/70 bg-white/60 px-3 py-2 text-xs font-bold">
            <div className="text-[10px] font-black uppercase tracking-wide opacity-60">
              Technical codes
            </div>
            <div className="mt-1 flex max-w-lg flex-wrap gap-1">
              {reason.technicalCodes.slice(0, 8).map((code) => (
                <span
                  key={code}
                  className="rounded-full bg-white px-2 py-0.5 font-mono text-[10px] uppercase tracking-wide text-slate-700"
                >
                  {code}
                </span>
              ))}
            </div>
          </div>
        ) : null}
      </div>
      {reason.actions.length ? (
        <div className="mt-3 grid gap-2 md:grid-cols-2 xl:grid-cols-3">
          {reason.actions.map((action) =>
            action.href ? (
              <Link
                key={`${action.label}:${action.href}`}
                href={action.href}
                className="rounded-xl border border-white/70 bg-white/80 px-3 py-2 text-sm font-black text-blue-700 hover:bg-white"
              >
                {action.label}
                {action.description ? (
                  <div className="mt-1 text-xs font-semibold leading-5 text-slate-600">
                    {action.description}
                  </div>
                ) : null}
              </Link>
            ) : (
              <div
                key={action.label}
                className="rounded-xl border border-white/70 bg-white/80 px-3 py-2 text-sm font-black"
              >
                {action.label}
                {action.description ? (
                  <div className="mt-1 text-xs font-semibold leading-5 text-slate-600">
                    {action.description}
                  </div>
                ) : null}
              </div>
            ),
          )}
        </div>
      ) : null}
    </div>
  );
}

export function DispatchAssignmentEvidencePanel(
  props: Readonly<DispatchAssignmentEvidencePanelProps>,
) {
  const evidence = buildDispatchAssignmentEvidence(props);
  const dispatchStatus =
    evidence.latestRequest?.eligibilityStatus ??
    evidence.latestRequest?.status ??
    props.task?.dispatchStatus;
  const issueLabel = evidence.issue?.issueId
    ? `${evidence.issue.issueVendor ?? "Issue"} ${evidence.issue.issueId}`
    : undefined;
  const operatorReason = normalizeOperatorDispatchFailureReason({
    task: props.task,
    latestDecision: evidence.latestDecision,
    latestRequest: evidence.latestRequest,
    selectedAgentId: evidence.selectedAgentId,
    rawRequirements: evidence.rawRequirements,
    effectiveCapabilities: evidence.effectiveCapabilities,
    runtimeCapabilities: evidence.runtimeCapabilities,
    setupReadiness: props.setupReadiness,
  });

  if (props.compact) {
    return (
      <div className="space-y-2 rounded-2xl border border-slate-200 bg-slate-50 p-3 text-xs">
        <div className="flex flex-wrap items-center gap-2">
          <span className="rounded-full bg-white px-2 py-0.5 font-black uppercase tracking-wide text-slate-600">
            {operatorReason.code}
          </span>
          <span className="font-bold text-slate-700">
            {operatorReason.title}
          </span>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-black text-slate-700">Raw</span>
          <ChipList values={evidence.rawRequirements} />
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-black text-slate-700">Effective</span>
          <ChipList values={evidence.effectiveCapabilities} />
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-black text-slate-700">Selected</span>
          {evidence.selectedAgentId ? (
            <Link
              href={`/agents/${encodeURIComponent(evidence.selectedAgentId)}`}
              className="font-black text-blue-700 hover:text-blue-800"
            >
              {evidence.selectedAgentId}
            </Link>
          ) : (
            <span className="text-slate-400">-</span>
          )}
          {dispatchStatus ? <StatusBadge status={dispatchStatus} /> : null}
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-black text-slate-700">Delivery</span>
          <StatusBadge
            status={
              evidence.deliveryConfirmation.delivered ? "DELIVERED" : "PENDING"
            }
          />
          <StatusBadge
            status={
              evidence.deliveryConfirmation.terminal ? "RESULT" : "NO_RESULT"
            }
          />
        </div>
      </div>
    );
  }

  const hasEvidence =
    evidence.rawRequirements.length ||
    evidence.effectiveCapabilities.length ||
    evidence.selectedAgentId ||
    evidence.latestRequest ||
    evidence.issue;

  return (
    <section className="rounded-2xl border border-blue-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <div className="text-[11px] font-black uppercase tracking-wide text-blue-600">
            Dispatch Assignment Evidence
          </div>
          <h2 className="mt-1 text-base font-black text-slate-950">
            {props.title ?? "Why this task was assigned or blocked"}
          </h2>
          <p className="mt-1 text-sm leading-6 text-slate-600">
            {props.description ??
              "Evidence from Core routing, optional Required Capability, dispatch request, runtime delivery, callback inbox, and issue tracking. This is the operator view for explaining assignment success or failure."}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          {evidence.latestDecision?.status ? (
            <StatusBadge status={evidence.latestDecision.status} />
          ) : null}
          {dispatchStatus ? <StatusBadge status={dispatchStatus} /> : null}
        </div>
      </div>

      <OperatorFailureReasonCard reason={operatorReason} />

      {!hasEvidence ? (
        <div className="mt-4">
          <EmptyState
            title="No dispatch assignment evidence yet"
            description="Core has not created a routing decision, assignment, dispatch request, or issue link for this task yet."
          />
        </div>
      ) : null}

      {hasEvidence ? (
        <>
          <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-4">
            <EvidenceCard
              label="Required Capability"
              tone={evidence.rawRequirements.length ? "good" : "neutral"}
            >
              <ChipList values={evidence.rawRequirements} empty="No Required Capability configured" />
            </EvidenceCard>
            <EvidenceCard
              label="Effective Capabilities"
              tone={evidence.effectiveCapabilities.length ? "good" : "warn"}
            >
              <ChipList
                values={evidence.effectiveCapabilities}
                empty="No resolved capability contract"
              />
            </EvidenceCard>
            <EvidenceCard
              label="Selected Agent"
              tone={evidence.selectedAgentId ? "good" : "warn"}
            >
              {evidence.selectedAgentId ? (
                <Link
                  href={`/agents/${encodeURIComponent(evidence.selectedAgentId)}`}
                  className="text-blue-700 hover:text-blue-800"
                >
                  {evidence.selectedAgentId}
                </Link>
              ) : (
                "-"
              )}
              {evidence.latestDecision?.selectedScore !== undefined ? (
                <div className="mt-1 text-xs text-slate-500">
                  score: {evidence.latestDecision.selectedScore}
                </div>
              ) : null}
            </EvidenceCard>
            <EvidenceCard
              label="Runtime Reported"
              tone={evidence.runtimeCapabilities.length ? "good" : "warn"}
            >
              <ChipList
                values={evidence.runtimeCapabilities}
                empty="No runtime capability evidence"
              />
            </EvidenceCard>
            <EvidenceCard
              label="Dispatch Request"
              tone={
                dispatchStatus && !String(dispatchStatus).includes("BLOCK")
                  ? "good"
                  : "warn"
              }
            >
              <div className="break-all">
                {evidence.latestRequest?.dispatchRequestId ??
                  props.task?.dispatchRequestId ??
                  "-"}
              </div>
              <div className="mt-1 flex flex-wrap gap-1.5">
                {dispatchStatus ? (
                  <StatusBadge status={dispatchStatus} />
                ) : null}
                {evidence.latestRequest?.status ? (
                  <StatusBadge status={evidence.latestRequest.status} />
                ) : null}
              </div>
            </EvidenceCard>
            <EvidenceCard
              label="Issue Link"
              tone={
                evidence.issue?.issueUrl || evidence.issue?.issueId
                  ? "good"
                  : "neutral"
              }
            >
              {evidence.issue?.issueUrl ? (
                <a
                  href={evidence.issue.issueUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="break-all text-blue-700 hover:text-blue-800"
                >
                  {issueLabel ?? evidence.issue.issueUrl}
                </a>
              ) : issueLabel ? (
                <span>{issueLabel}</span>
              ) : (
                "-"
              )}
            </EvidenceCard>
            <EvidenceCard label="Updated" tone="neutral">
              {evidence.latestRequest?.updatedAt ||
              evidence.latestDecision?.createdAt ||
              props.task?.updatedAt
                ? formatDateTime(
                    evidence.latestRequest?.updatedAt ??
                      evidence.latestDecision?.createdAt ??
                      props.task?.updatedAt,
                  )
                : "-"}
            </EvidenceCard>
          </div>

          <div className="mt-4 rounded-2xl border border-slate-200 bg-white p-4">
            <div className="flex flex-col gap-2 lg:flex-row lg:items-start lg:justify-between">
              <div>
                <div className="text-[11px] font-black uppercase tracking-wide text-slate-500">
                  Runtime Delivery Confirmation
                </div>
                <h3 className="mt-1 text-sm font-black text-slate-950">
                  Dispatch request → gateway delivery → agent ACK → agent RESULT
                </h3>
                <p className="mt-1 text-xs font-semibold leading-5 text-slate-600">
                  This confirms the assignment did not stop at Core selection.
                  Delivery and callback truth are confirmed by Core dispatch
                  state and persisted callback inbox records.
                </p>
              </div>
              <div className="flex flex-wrap gap-2">
                <StatusBadge
                  status={
                    evidence.deliveryConfirmation.delivered
                      ? "DELIVERED"
                      : "DELIVERY_PENDING"
                  }
                />
                <StatusBadge
                  status={
                    evidence.deliveryConfirmation.terminal
                      ? "RESULT_RECEIVED"
                      : "NO_RESULT"
                  }
                />
              </div>
            </div>
            <div className="mt-3 grid gap-2 md:grid-cols-2 xl:grid-cols-4">
              <DeliveryStep
                label="Gateway delivered"
                done={evidence.deliveryConfirmation.delivered}
                detail={
                  props.deliveryAttempt?.reason ??
                  evidence.latestRequest?.reason ??
                  props.task?.dispatchDeliveryStatus ??
                  undefined
                }
              />
              <DeliveryStep
                label="Agent ACK"
                done={evidence.deliveryConfirmation.acked}
                detail={
                  evidence.deliveryConfirmation.callbackCount
                    ? `${evidence.deliveryConfirmation.callbackCount} callback record(s)`
                    : "No ACK callback persisted yet"
                }
              />
              <DeliveryStep
                label="Agent RESULT"
                done={evidence.deliveryConfirmation.terminal}
                detail={
                  evidence.deliveryConfirmation.latestTerminal?.callbackId ??
                  props.callbackInboxSummary?.latestCallbackId ??
                  "No terminal callback persisted yet"
                }
              />
              <DeliveryStep
                label="Task completed"
                done={evidence.deliveryConfirmation.completed}
                detail={
                  props.task?.status ??
                  evidence.deliveryConfirmation.latestTerminal?.newTaskStatus ??
                  undefined
                }
              />
            </div>
          </div>

          {evidence.latestDecision?.decisionReason ? (
            <div className="mt-4 rounded-2xl border border-blue-100 bg-blue-50 px-4 py-3 text-sm font-semibold text-blue-900">
              <div className="text-[11px] font-black uppercase tracking-wide text-blue-600">
                Routing reason
              </div>
              <div className="mt-1 break-words">
                {evidence.latestDecision.decisionReason}
              </div>
            </div>
          ) : null}
        </>
      ) : null}
    </section>
  );
}
