import type {
  CoreAgentSetupReadinessResponse,
  CoreDispatchRequest,
  CoreRoutingDecisionRecord,
  CoreTaskRuntimeView,
} from "@/lib/types/core";

export type OperatorFailureTone =
  "success" | "warning" | "danger" | "info" | "neutral";

export interface OperatorFailureAction {
  label: string;
  href?: string;
  description?: string;
}

export interface OperatorFailureReason {
  code: string;
  title: string;
  message: string;
  nextAction: string;
  tone: OperatorFailureTone;
  technicalCodes: string[];
  actions: OperatorFailureAction[];
}

export interface OperatorFailureReasonInput {
  task?: CoreTaskRuntimeView;
  latestDecision?: CoreRoutingDecisionRecord;
  latestRequest?: CoreDispatchRequest;
  selectedAgentId?: string;
  rawRequirements?: string[];
  effectiveCapabilities?: string[];
  runtimeCapabilities?: string[];
  setupReadiness?: CoreAgentSetupReadinessResponse;
}

function normalizeCode(value: unknown): string | undefined {
  if (value === undefined || value === null) return undefined;
  const normalized = String(value)
    .trim()
    .toUpperCase()
    .replace(/[.\-\s]+/g, "_");
  return normalized || undefined;
}

function unique(values: Array<string | undefined>): string[] {
  const seen = new Set<string>();
  const result: string[] = [];
  for (const value of values) {
    const normalized = normalizeCode(value);
    if (!normalized || seen.has(normalized)) continue;
    seen.add(normalized);
    result.push(normalized);
  }
  return result;
}

function includesAny(values: string[], candidates: string[]): boolean {
  return values.some((value) => candidates.includes(value));
}

function agentHref(agentId?: string): string {
  return agentId ? `/agents/${encodeURIComponent(agentId)}` : "/agents";
}

function taskHref(taskId?: string): string {
  return taskId ? `/tasks/${encodeURIComponent(taskId)}` : "/tasks";
}

function dispatchFlowsHref(): string {
  return "/dispatch-flows";
}

function hasUsableDispatchRequest(request?: CoreDispatchRequest): boolean {
  const statuses = unique([request?.eligibilityStatus, request?.status]);
  return includesAny(statuses, [
    "ELIGIBLE",
    "APPROVED",
    "DISPATCHED",
    "ACKED",
    "COMPLETED",
    "SUCCEEDED",
    "SUCCESS",
  ]);
}

export function normalizeOperatorDispatchFailureReason(
  input: OperatorFailureReasonInput,
): OperatorFailureReason {
  const task = input.task;
  const decision = input.latestDecision ?? task?.latestRoutingDecision;
  const request = input.latestRequest;
  const selectedAgentId =
    input.selectedAgentId ??
    decision?.selectedAgentId ??
    request?.agentId ??
    task?.assignedAgentId;
  const rawRequirements = unique(input.rawRequirements ?? []);
  const effectiveCapabilities = unique(input.effectiveCapabilities ?? []);
  const setupBlockingReasons = unique(
    input.setupReadiness?.blockingReasons ?? [],
  );
  const technicalCodes = unique([
    decision?.status,
    decision?.userFacingError?.code,
    request?.eligibilityStatus,
    request?.status,
    task?.dispatchStatus,
    task?.dispatchExecutionStatus,
    task?.dispatchDeliveryStatus,
    task?.blockedReason,
    task?.failureReason,
    task?.dispatchWaitReason,
    task?.userFacingDispatchError?.code,
    ...setupBlockingReasons,
  ]);

  if (selectedAgentId && hasUsableDispatchRequest(request)) {
    return {
      code: "DISPATCH_ASSIGNMENT_READY",
      title: "Dispatch assignment is ready",
      message: `Core selected ${selectedAgentId} and the dispatch request is eligible or already dispatched.`,
      nextAction:
        "Monitor callback/result status or open the linked issue if one exists.",
      tone: "success",
      technicalCodes,
      actions: [
        {
          label: "Open selected agent",
          href: agentHref(selectedAgentId),
          description:
            "Review Agent runtime, optional capabilities, and recent tasks.",
        },
        {
          label: "Open task detail",
          href: taskHref(task?.taskId),
          description: "Review task lifecycle and callback history.",
        },
      ],
    };
  }

  if (!effectiveCapabilities.length && rawRequirements.length) {
    return {
      code: "EFFECTIVE_CAPABILITY_NOT_RESOLVED",
      title: "Required capability was not resolved from Dispatch Flow",
      message:
        "Core received task requirement evidence, but standard dispatch now resolves required capabilities only from the matched Dispatch Flow.",
      nextAction:
        "Open the Dispatch Flow and add an optional required capability only if this work needs a specialized Agent capability.",
      tone: "danger",
      technicalCodes: unique([...technicalCodes, "REQUIRED_CAPABILITY_NOT_RESOLVED"]),
      actions: [
        {
          label: "Open Dispatch Flows",
          href: dispatchFlowsHref(),
          description: "Review Flow-owned event conditions, Agent selection, and optional required capabilities.",
        },
      ],
    };
  }


  if (
    includesAny(technicalCodes, [
      "RUNTIME_BINDING_ACTIVE",
      "RUNTIME_BINDING_MISSING",
      "ACTIVE_RUNTIME_BINDING_REQUIRED",
      "P3H_ACTIVE_RUNTIME_BINDING_REQUIRED",
      "P3H_RUNTIME_BINDING_NOT_ACTIVE",
    ])
  ) {
    return {
      code: "RUNTIME_BINDING_MISSING",
      title: "Runtime binding is not active",
      message:
        "The agent is online, but Core has not activated the runtime binding required for dispatch authority.",
      nextAction:
        "Create or activate the runtime binding for this Agent and Gateway runtime, then retry Dispatch Flow or assignment.",
      tone: "danger",
      technicalCodes: unique([...technicalCodes, "RUNTIME_BINDING_MISSING"]),
      actions: [
        {
          label: "Open selected agent",
          href: agentHref(selectedAgentId),
          description: "Open Agent Detail > Connection > Runtime Binding and activate the binding.",
        },
        {
          label: "Open runtime resources",
          href: "/settings/runtime-resources",
          description: "Review runtime resources and Agent runtime bindings.",
        },
      ],
    };
  }

  // Runtime capability observations are optional diagnostics. Do not classify an
  // otherwise Core-approved dispatch contract as failed only because runtimeCapabilities is empty.

  if (
    includesAny(technicalCodes, ["NO_CANDIDATE"]) ||
    (!selectedAgentId && effectiveCapabilities.length > 0)
  ) {
    return {
      code: "NO_MATCHING_AGENT",
      title: "No Flow-selected Agent matched the required capabilities",
      message:
        "Core resolved required capabilities from the Dispatch Flow, but no Flow-selected online and eligible Agent matched them.",
      nextAction:
        "Check Dispatch Flow Agent selection, optional required capability approval, runtime connection, and capacity for candidate Agents.",
      tone: "danger",
      technicalCodes: unique([...technicalCodes, "NO_CANDIDATE"]),
      actions: [
        {
          label: "Open agents",
          href: "/agents",
          description:
            "Find candidates and compare Core approval, runtime report, and dispatch usable status.",
        },
        {
          label: "Open Dispatch Flows",
          href: dispatchFlowsHref(),
          description: "Review Flow Agent selection and optional required capabilities.",
        },
      ],
    };
  }

  if (selectedAgentId && !request) {
    return {
      code: "DISPATCH_REQUEST_NOT_CREATED",
      title: "Agent was selected, but no dispatch request was created",
      message:
        "Core selected an agent, but there is no dispatch request evidence for delivery.",
      nextAction:
        "Check task orchestration logs and dispatch request creation errors, then retry assignment.",
      tone: "danger",
      technicalCodes: unique([
        ...technicalCodes,
        "DISPATCH_REQUEST_NOT_CREATED",
      ]),
      actions: [
        {
          label: "Open task detail",
          href: taskHref(task?.taskId),
          description: "Review lifecycle and routing decision.",
        },
        {
          label: "Open dispatch monitoring",
          href: "/enforce-observability",
          description:
            "Check production dispatch monitoring and routing audit.",
        },
      ],
    };
  }

  if (request && !selectedAgentId) {
    return {
      code: "ASSIGNMENT_NOT_CREATED",
      title: "Dispatch request exists, but no selected agent is linked",
      message:
        "A dispatch request or task record exists, but Core did not link it to a selected agent.",
      nextAction:
        "Check routing decision and assignment creation evidence before retrying dispatch.",
      tone: "danger",
      technicalCodes: unique([...technicalCodes, "ASSIGNMENT_NOT_CREATED"]),
      actions: [
        {
          label: "Open task detail",
          href: taskHref(task?.taskId),
          description: "Review routing decisions and assignment timeline.",
        },
      ],
    };
  }

  if (
    includesAny(technicalCodes, [
      "DISPATCH_RULE_MISSING",
      "POLICY_MISSING",
      "DISPATCH_PROFILE_POLICY_MISSING",
      "DISPATCH_POLICY_INACTIVE",
    ])
  ) {
    return {
      code: "DISPATCH_RULE_MISSING",
      title: "No active Dispatch Flow rule matches this task",
      message:
        "The agent may have an approved Dispatch Flow, but no active Dispatch Flow event condition matches this task.",
      nextAction:
        "Create or activate the matching Dispatch Flow rule, then retry dispatch.",
      tone: "warning",
      technicalCodes: unique([...technicalCodes, "DISPATCH_RULE_MISSING"]),
      actions: [
        {
          label: "Open Dispatch Flows",
          href: dispatchFlowsHref(),
          description:
            "Review active Flow-owned event conditions and Agent selection.",
        },
        {
          label: "Open Dispatch Flows",
          href: dispatchFlowsHref(),
          description: "Confirm the event condition and Agent selection belong to the active Dispatch Flow.",
        },
      ],
    };
  }

  if (
    includesAny(technicalCodes, [
      "DISPATCH_ELIGIBILITY_WAITING",
      "WAITING",
      "BLOCKED",
    ])
  ) {
    return {
      code: "DISPATCH_ELIGIBILITY_WAITING",
      title: "Dispatch eligibility is waiting on one or more gates",
      message:
        "Core has not marked this task as eligible for dispatch yet. Review the evidence cards to identify the first missing gate.",
      nextAction:
        "Fix the first missing gate in this order: Dispatch Flow, required capability, runtime report, selected Agent, dispatch request.",
      tone: "warning",
      technicalCodes,
      actions: [
        {
          label: "Open task detail",
          href: taskHref(task?.taskId),
          description: "Review full dispatch evidence and timeline.",
        },
        {
          label: "Open Dispatch Flows",
          href: dispatchFlowsHref(),
          description: "Fix Flow-owned event conditions, Agent selection, or optional required capabilities.",
        },
      ],
    };
  }

  return {
    code: "DISPATCH_EVIDENCE_INCOMPLETE",
    title: "Dispatch evidence is incomplete",
    message:
      "Core has not returned enough assignment evidence to determine the exact dispatch outcome.",
    nextAction:
      "Refresh the task detail, check Core API connectivity, and review routing diagnostics if the task remains stuck.",
    tone: "neutral",
    technicalCodes,
    actions: [
      {
        label: "Open task detail",
        href: taskHref(task?.taskId),
        description: "Review the latest task runtime view.",
      },
    ],
  };
}
