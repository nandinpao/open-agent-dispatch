import type {
  CoreAgentSetupReadinessResponse,
  CoreCallbackInboxEntry,
  CoreCallbackInboxSummary,
  CoreDispatchRequest,
  CoreRoutingDecisionRecord,
  CoreTaskIssueTracking,
  CoreTaskRuntimeView,
} from "@/lib/types/core";
import type { RuntimeAttemptSummary } from "@/lib/dashboard/taskDispatchMerge";

export interface DispatchAssignmentEvidencePanelProps {
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
  // Required Capability is a canonical blocking qualification inside the Flow-selected Agent Pool.
  // Preserve raw/effective capability evidence so operators can explain why a candidate qualified or was blocked.
  // Runtime-reported capability observations remain diagnostics and are modeled separately.
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

