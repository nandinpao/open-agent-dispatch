import { DispatchLifecycleStepper } from "@/components/tasks/DispatchLifecycleStepper";
import { TaskDiagnosticsTabs, type TaskDiagnosticsTab } from "@/components/tasks/TaskDiagnosticsTabs";
import type { TaskControlConsoleTab } from "@/components/tasks/TaskControlConsoleTabs";
import { TaskLifecycleOperatorPanel } from "@/components/tasks/TaskLifecycleOperatorPanel";
import { CapabilityResolutionMatrix, EntityRelationshipStrip } from "@/components/dispatch-evidence/StandardRelationshipComponents";
import { BeginnerTaskFlowPanel } from "@/components/tasks/BeginnerTaskFlowPanel";
import { RoutingExplainabilityPanel } from "@/components/tasks/RoutingExplainabilityPanel";
import { DispatchAssignmentEvidencePanel } from "@/components/dispatch-evidence/DispatchAssignmentEvidencePanel";
import { TaskRuntimeVerificationPanel } from "@/components/tasks/TaskRuntimeVerificationPanel";
import { buildCapabilityMatrix, buildTaskRelationshipSteps } from "@/lib/dispatch-readiness/beginnerWorkflow";
import type { DispatchOperatorCommand } from "@/lib/dispatch-readiness/dispatchOperatorActions";
import type { RuntimeAttemptSummary, TaskDispatchDashboardRow } from "@/lib/dashboard/taskDispatchMerge";
import type {
  CoreCallbackInboxEntry,
  CoreCallbackInboxSummary,
  CoreDispatchAttemptHistoryRecord,
  CoreDispatchAttemptLedger,
  CoreDispatchEligibilityV2Response,
  CoreDispatchRequest,
  CoreDispatchTimelineResponse,
  CoreRoutingDecisionRecord,
  CoreTaskCaseTimelineView,
  CoreTaskDispatchEvidenceView,
  CoreTaskDispatchRequirements,
  CoreTaskEligibleAgentsResponse,
  CoreTaskRuntimeVerificationView,
  CoreTaskRuntimeView,
} from "@/lib/types/domains/task";
import { taskAuthorityDisclaimer } from "@/lib/runtime/callbackTruth";
import type { TaskDispatchDiagnosis } from "@/lib/tasks/dispatchLifecycle";
import {
  StandardDispatchTimelinePanel,
} from "@/components/tasks/detail/TaskDiagnosisPanels";
import {
  TaskAuthoritySummary,
  TaskDispatchEligibilityContractPanel,
  TaskDispatchEligibilityV2Panel,
} from "@/components/tasks/detail/TaskEligibilityPanels";
import {
  DispatchTimelinePanel,
  RecoveryVisibilityPanel,
  RuntimeAttemptPanel,
} from "@/components/tasks/detail/TaskRuntimeOperationsPanels";
import {
  AttemptHistoryPanel,
  CallbackInboxPanel,
  DispatchLedgerPanel,
} from "@/components/tasks/detail/TaskCallbackOperationsPanels";
import { TaskCaseTimelineRepairPanel } from "@/components/tasks/detail/TaskRecoveryPanels";
import {
  PayloadPanel,
  TaskA2AEvidenceChainPanel,
} from "@/components/tasks/detail/TaskA2AEvidencePanels";
import { TaskIssueSynchronizationPanel } from "@/components/tasks/detail/TaskIssueSynchronizationPanel";

export function buildTaskDiagnosticsTabs(
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
    retrying: boolean;
  }>,
): TaskDiagnosticsTab[] {
  return [
    {
      id: "operator-lifecycle",
      label: "Lifecycle ",
      description:
        "Review raw lifecycle status, badge mapping, and Operator Stepper references.",
      badge: input.row.task.dispatchStatus ?? input.row.task.status,
      content: <DispatchLifecycleStepper row={input.row} />,
    },
    {
      id: "timeline-event-log",
      label: "Timeline Event Log",
      description:
        "Core canonical timeline for routing, assignment, dispatch, callbacks, retries, dead-letter events, and audit evidence.",
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
        " Agent,Netty  delivery,callback relay  Agent ",
      content: (
        <>
          <RoutingExplainabilityPanel
            decisions={input.routingDecisions}
            error={input.routingDecisionsError}
          />
          <RuntimeAttemptPanel
            title="Netty Delivery Runtime"
            description="Shows recent Netty command-delivery runtime evidence; it is not the authoritative Task status."
            attempt={input.delivery}
            warning={input.deliveryRuntimeError}
          />
          <RuntimeAttemptPanel
            title="Netty Callback Relay Runtime"
            description="Shows recent Netty Agent callbacks after relay to Core. Core callback records remain the persisted source of truth."
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
        "Review the Source Flow, Agent Pool, Agent runtime, and retry eligibility.",
      content: (
        <>
          <RecoveryVisibilityPanel task={input.task} />
        </>
      ),
    },
    {
      id: "dispatch-ledger",
      label: "Dispatch Ledger",
      description:
        "Core authoritative dispatch ledger, callback inbox, topology recovery, and Gateway node telemetry.",
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
        "Core durable callback inbox.ACK / RESULT / ERROR  Gateway relay,Core  persisted callback record and idempotency key ",
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
        "Core append-only dispatch attempt history delayed recovery,runtime backoff,scanner claim and retry ",
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
        " raw payload and Core authority /SRE  API response.",
      content: (
        <>
          <TaskAuthoritySummary task={input.task} />
          <PayloadPanel task={input.task} />
        </>
      ),
    },
  ];
}

export function buildTaskControlConsoleTabs(
  input: Readonly<{
    row: TaskDispatchDashboardRow;
    task: CoreTaskRuntimeView;
    diagnosis: TaskDispatchDiagnosis;
    dispatchRequests?: CoreDispatchRequest[];
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
    retrying: boolean;
    retryingIssueSyncActionId?: string;
    onRetryIssueSync: (actionId: string) => void;
    onDispatchOperatorCommand: (command: DispatchOperatorCommand) => void;
  }>,
): TaskControlConsoleTab[] {
  const dispatchStatus = input.task.dispatchStatus ?? input.task.status;
  const eligibleCount = input.eligibleAgents?.eligibleAgents?.length ?? 0;
  const blockedCount = input.eligibleAgents?.blockedAgents?.length ?? 0;
  return [
    {
      id: "overview",
      label: "Dashboard",
      badge: dispatchStatus,
      description:
        "Operator-focused Task lifecycle and authority boundaries, with advanced raw diagnostics available separately.",
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
            title="Task Dispatch Evidence"
            description="Trace the Task from Source Flow and Agent Pool through Core-approved Required Capability qualification, runtime eligibility, routing selection, delivery, callbacks, and External Issue evidence."
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
          />
          <TaskLifecycleOperatorPanel row={input.row} />
          <BeginnerTaskFlowPanel row={input.row} />
        </>
      ),
    },
    {
      id: "dispatch-lifecycle",
      label: "Dispatch Evidence",
      badge: input.timeline?.events?.length
        ? `${input.timeline.events.length}`
        : undefined,
      description:
        "Review Core lifecycle events, attempt history, and dispatch ledger evidence.",
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
      label: "Pool / Agent",
      badge: eligibleCount
        ? `${eligibleCount} eligible`
        : blockedCount
          ? `${blockedCount} blocked`
          : undefined,
      description:
        "Review Source Flow, Agent Pool, Required Capability, runtime eligibility, capacity, and routing evidence. Pool membership defines the search scope; approved capability determines qualification.",
      content: (
        <>
          <CapabilityResolutionMatrix
            rows={buildCapabilityMatrix({
              taskRequiredCapabilities: input.task.requiredCapabilities,
            })}
            title="Task Required Capabilities and Agent eligibility"
            description="Required Capabilities are blocking eligibility requirements. Core first limits candidates to the Agent Pool, then keeps only Agents with the required approved capabilities before runtime and routing evaluation."
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
      label: "Result & Issue Operations",
      badge: input.task.callbackStatus,
      description:
        "Review the Agent result, canonical Issue Automation / provider operations, and callback evidence. Routing score and raw technical evidence stay in their dedicated diagnostics views.",
      content: (
        <>
          <TaskIssueSynchronizationPanel
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
      label: "Technical Evidence",
      badge: input.diagnosis.code,
      description:
        "Read-only technical evidence for dispatch, A2A, and recovery state. Mutations remain in the Agent Assignment section.",
      content: (
        <>
          <TaskA2AEvidenceChainPanel
            task={input.task}
            parentTask={input.taskFamily?.parentTask}
            childTasks={input.taskFamily?.childTasks}
            caseTimeline={input.caseTimeline}
            familyError={input.taskFamilyError}
          />
          <RecoveryVisibilityPanel task={input.task} />
        </>
      ),
    },
    {
      id: "debug",
      label: "Support Debug",
      description:
        "Engineer and SRE diagnostics: Core runtime-view payload, runtime evidence, ledger records, and callback data.",
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
            retrying: input.retrying,
            })}
          />
        </>
      ),
    },
  ];
}
