import type { CoreDispatchUserFacingError } from "@/lib/types/core";

export interface ParsedDispatchUserFacingError {
  code?: string;
  severity?: string;
  message: string;
  nextAction?: string;
  runbookRef?: string;
  technicalDetails?: string;
  context?: Record<string, unknown>;
}

const technicalMarker = "Technical details:";
const emptyReason = "No clear dispatch blocking reason has been reported yet.";

const OPERATOR_REASON_BY_CODE: Record<
  string,
  { message: string; nextAction: string; runbookRef?: string }
> = {
  NO_CANDIDATE: {
    message:
      "No agent matched the effective capabilities and dispatch conditions.",
    nextAction:
      "Check effective capabilities approved in Core, approved Dispatch Flow Agent selection, dispatch rules, runtime connection, and agent capacity.",
    runbookRef: "runbooks/dispatch/no-matching-agent",
  },
  DISPATCH_ELIGIBILITY_WAITING: {
    message: "Dispatch eligibility is waiting on one or more required gates.",
    nextAction:
      "Fix the first missing gate: Dispatch Flow Agent selection, dispatch rule, Core capability approval, runtime connection/capacity, or selected agent.",
    runbookRef: "runbooks/dispatch/eligibility-waiting",
  },
  SERVICE_SCOPE_PENDING: {
    message:
      "The selected agent has the capability contract, but its Agent Dispatch Flow Agent assignment is still pending approval.",
    nextAction:
      "Approve the Agent Dispatch Flow Agent assignment, then re-run dispatch readiness or retry assignment.",
    runbookRef: "runbooks/dispatch/service-scope-pending",
  },
  RUNTIME_BINDING_MISSING: {
    message:
      "The agent is online, but Core has not activated the runtime binding required for dispatch authority.",
    nextAction:
      "Create or activate the runtime binding for this Agent and Gateway runtime, then retry dispatch.",
    runbookRef: "runbooks/dispatch/runtime-binding-missing",
  },
  RUNTIME_BINDING_ACTIVE: {
    message:
      "The agent is online, but Core has not activated the runtime binding required for dispatch authority.",
    nextAction:
      "Create or activate the runtime binding from Agent Detail > Connection, then refresh readiness.",
    runbookRef: "runbooks/dispatch/runtime-binding-missing",
  },
  P3H_ACTIVE_RUNTIME_BINDING_REQUIRED: {
    message:
      "The agent is online, but Core has not activated the runtime binding required for dispatch authority.",
    nextAction:
      "Create or activate the runtime binding for this Agent and Gateway runtime.",
    runbookRef: "runbooks/dispatch/runtime-binding-missing",
  },
  P3H_RUNTIME_BINDING_NOT_ACTIVE: {
    message:
      "The Agent Runtime Binding exists but is not active.",
    nextAction:
      "Activate the runtime binding from Agent Detail > Connection or Runtime Resources.",
    runbookRef: "runbooks/dispatch/runtime-binding-missing",
  },
  RUNTIME_CAPABILITY_MISSING: {
    message:
      "The dispatch capability was not approved in Core/Admin UI or the Agent lacks an active Dispatch Flow Agent selection.",
    nextAction:
      "Approve the effective capabilities in Core/Admin UI and confirm the runtime is connected with capacity. Runtime capability self-reporting is diagnostic only.",
    runbookRef: "runbooks/dispatch/admin-managed-capability-missing",
  },
  DISPATCH_RULE_MISSING: {
    message:
      "The agent has Dispatch Flow Agent selection, but no active dispatch rule grants this task.",
    nextAction:
      "Apply or activate a dispatch rule for the active Agent Dispatch Flow Agent assignment.",
    runbookRef: "runbooks/dispatch/dispatch-rule-missing",
  },
  ASSIGNMENT_NOT_CREATED: {
    message:
      "Core selected an agent or created a dispatch artifact, but no assignment link was created.",
    nextAction:
      "Check routing decision evidence and task orchestration logs, then retry assignment.",
    runbookRef: "runbooks/dispatch/assignment-not-created",
  },
  DISPATCH_REQUEST_NOT_CREATED: {
    message:
      "Assignment evidence exists, but no dispatch request was created for delivery.",
    nextAction:
      "Check dispatch request creation errors and retry the task after fixing the first failed gate.",
    runbookRef: "runbooks/dispatch/dispatch-request-not-created",
  },
  EFFECTIVE_CAPABILITY_NOT_RESOLVED: {
    message:
      "The raw task requirement was not converted into effective dispatch capabilities.",
    nextAction:
      "Fix the task definition or dispatch contract mapping before retrying assignment.",
    runbookRef: "runbooks/dispatch/effective-capability-contract",
  },
};

function normalizeCode(value?: string): string | undefined {
  const normalized = value
    ?.trim()
    .toUpperCase()
    .replace(/[.\-\s]+/g, "_");
  return normalized || undefined;
}

function operatorReasonForCode(
  code?: string,
): { message: string; nextAction: string; runbookRef?: string } | undefined {
  if (!code) return undefined;
  return OPERATOR_REASON_BY_CODE[normalizeCode(code) ?? ""];
}

function stringifyDiagnostics(value: unknown): string | undefined {
  if (value === undefined || value === null) return undefined;
  if (typeof value === "string") return value.trim() || undefined;
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}

function splitTechnicalDetails(value: string): {
  main: string;
  technicalDetails?: string;
} {
  const marker = value.indexOf(technicalMarker);
  if (marker < 0) return { main: value.trim() };
  return {
    main: value.slice(0, marker).trim(),
    technicalDetails:
      value.slice(marker + technicalMarker.length).trim() || undefined,
  };
}

function splitNextAction(value: string): {
  message: string;
  nextAction?: string;
} {
  const marker = value.indexOf("下一步：");
  if (marker < 0) return { message: value.trim() || emptyReason };
  const message = value.slice(0, marker).trim();
  let rest = value.slice(marker + "下一步：".length).trim();
  const extraMarkers = [" 原因：", " 下次重試：", " Next retry:", " Reason:"];
  const extraIndex = extraMarkers
    .map((item) => rest.indexOf(item))
    .filter((index) => index >= 0)
    .sort((a, b) => a - b)[0];
  if (extraIndex !== undefined) {
    const extra = rest.slice(extraIndex).trim();
    rest = rest.slice(0, extraIndex).trim();
    return {
      message: [message, extra].filter(Boolean).join(" "),
      nextAction: rest || undefined,
    };
  }
  return { message: message || emptyReason, nextAction: rest || undefined };
}

export function parseDispatchUserFacingError(
  value?: string | null,
  structured?: CoreDispatchUserFacingError | null,
): ParsedDispatchUserFacingError {
  if (structured) {
    const code = normalizeCode(structured.code);
    const mapped = operatorReasonForCode(code);
    const message = structured.message?.trim();
    return {
      code,
      severity: structured.severity,
      message:
        message && message !== code && message !== emptyReason
          ? message
          : (mapped?.message ?? emptyReason),
      nextAction: structured.nextAction?.trim() || mapped?.nextAction,
      runbookRef: structured.runbookRef ?? mapped?.runbookRef,
      technicalDetails: stringifyDiagnostics(structured.technicalDetails),
      context: structured.context,
    };
  }
  if (!value?.trim()) return { message: emptyReason };
  const { main, technicalDetails } = splitTechnicalDetails(value);
  const match = main.match(/^([A-Z0-9_]+):\s*(.*)$/);
  const loneCode = !match && /^[A-Z0-9_]+$/.test(main) ? main : undefined;
  const code = match?.[1] ?? loneCode;
  const body = match?.[2] ?? (loneCode ? "" : main);
  const { message, nextAction } = splitNextAction(body);
  const normalizedCode = normalizeCode(code);
  const mapped = operatorReasonForCode(normalizedCode);
  return {
    code: normalizedCode,
    message:
      message && message !== normalizedCode && message !== emptyReason
        ? message
        : (mapped?.message ?? message),
    nextAction: nextAction || mapped?.nextAction,
    runbookRef: mapped?.runbookRef,
    technicalDetails,
  };
}

export function dispatchUserFacingNextAction(
  value?: string | null,
  structured?: CoreDispatchUserFacingError | null,
): string | undefined {
  return parseDispatchUserFacingError(value, structured).nextAction;
}
