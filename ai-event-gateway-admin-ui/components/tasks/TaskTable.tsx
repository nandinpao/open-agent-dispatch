"use client";

import { useEffect, useMemo, useState } from "react";
import { CommandMessage } from "@/components/common/CommandMessage";
import { DecisionHeader } from "@/components/common/DecisionHeader";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorBox } from "@/components/common/ErrorBox";
import {
  ListFilterBar,
  type SelectFilterConfig,
} from "@/components/common/ListFilterBar";
import { LoadingBox } from "@/components/common/LoadingBox";
import { RefreshButton } from "@/components/common/RefreshButton";
import { StatusBadge } from "@/components/common/StatusBadge";
import { TaskActionDialog } from "@/components/tasks/TaskActionDialog";
import { TaskListRow } from "@/components/tasks/TaskListRow";
import { TaskListDetailPreview } from "@/components/tasks/TaskListDetailPreview";
import { AdminMasterDetailLayout } from "@/components/layout/AdminMasterDetailLayout";
import { VirtualizedResourceList } from "@/components/layout/VirtualizedResourceList";
import { BeginnerGuideButton } from "@/components/resource-scope/EnterpriseAccessUi";
import { UiListCapabilityProvider } from "@/components/ui-capability/UiListCapabilityProvider";
import { useTasks } from "@/hooks/useTasks";
import type { TaskDispatchDashboardRow } from "@/lib/dashboard/taskDispatchMerge";
import { buildTaskWorkbenchDisplay } from "@/lib/tasks/taskWorkbench";
import {
  taskDecisionSummary,
  taskQueueLane,
  taskQueueLaneLabel,
  type DispatchRecipeLane,
} from "@/lib/dispatch-readiness/beginnerWorkflow";
import {
  parseDispatchUserFacingError,
  type ParsedDispatchUserFacingError,
} from "@/lib/dispatch-readiness/dispatchUserFacingError";
import { formatDateTime } from "@/lib/utils/format";
import {
  recordIncludesQuery,
  uniqueSortedValues,
} from "@/lib/utils/list";

const allValue = "ALL";

function matchesTask(
  row: TaskDispatchDashboardRow,
  query: string,
  status: string,
  agentId: string,
  dispatchStatus: string,
): boolean {
  const task = row.task;
  const display = buildTaskWorkbenchDisplay(row);
  const parsedDispatchError = taskDispatchErrorForSummary(row);
  if (status !== allValue && task.status !== status) return false;
  if (agentId !== allValue && task.assignedAgentId !== agentId) return false;
  if (dispatchStatus !== allValue && task.dispatchStatus !== dispatchStatus)
    return false;

  return recordIncludesQuery(
    [
      task.taskId,
      task.traceId,
      task.incidentId,
      task.taskType,
      task.status,
      task.dispatchStatus,
      task.callbackStatus,
      task.dispatchExecutionStatus,
      task.dispatchDeliveryStatus,
      task.blockedReason,
      task.reasonCategory,
      task.dispatchWaitReason,
      task.nextAction,
      task.assignedAgentId,
      task.dispatchRequestId,
      row.delivery?.gatewayNodeId,
      row.delivery?.reason,
      row.callbackRelay?.reason,
      task.lifecycleReason,
      task.dispatchRetryReason,
      task.failureReason,
      task.userFacingDispatchError?.code,
      task.userFacingDispatchError?.message,
      task.userFacingDispatchError?.nextAction,
      task.latestRoutingDecision?.decisionReason,
      task.latestRoutingDecision?.userFacingError?.code,
      task.latestRoutingDecision?.userFacingError?.message,
      task.latestRoutingDecision?.userFacingError?.nextAction,
      parsedDispatchError?.code,
      parsedDispatchError?.message,
      parsedDispatchError?.nextAction,
      task.sourceSystem,
      task.siteId,
      task.plantId,
      task.objectType,
      task.objectId,
      task.eventType,
      task.errorCode,
      task.createdReason,
      display.title,
      display.businessStatus,
      display.sourceLabel,
      display.targetLabel,
      display.eventLabel,
      display.severity.code,
      display.severity.label,
      display.latestAgentSummary,
      display.issueBridge.vendor,
      display.issueBridge.issueId,
      display.issueBridge.issueUrl,
      display.issueBridge.issueStatus,
    ],
    query,
  );
}

function isClosedTaskStatus(status?: string): boolean {
  return ["COMPLETED", "SUCCEEDED", "RESOLVED", "CANCELLED", "CANCELED"].includes(
    String(status ?? "").toUpperCase(),
  );
}

function shouldAllowRetry(row: TaskDispatchDashboardRow): boolean {
  const taskStatus = String(row.task.status ?? "").toUpperCase();
  const dispatchStatus = String(row.task.dispatchStatus ?? "").toUpperCase();
  if (isClosedTaskStatus(taskStatus)) return false;
  return (
    ["FAILED", "TIMEOUT", "TIMED_OUT", "DEAD_LETTER"].includes(taskStatus) ||
    ["DELIVERY_FAILED", "DEAD_LETTER", "RETRY_PENDING"].includes(dispatchStatus)
  );
}

function shouldAllowCancel(row: TaskDispatchDashboardRow): boolean {
  const taskStatus = String(row.task.status ?? "").toUpperCase();
  return !["COMPLETED", "SUCCEEDED", "RESOLVED", "FAILED", "CANCELLED", "CANCELED", "TIMEOUT", "TIMED_OUT", "DEAD_LETTER"].includes(taskStatus);
}

interface DispatchErrorGroup {
  code: string;
  count: number;
  blocked: number;
  waiting: number;
  message: string;
  nextAction?: string;
}

function taskDispatchErrorForSummary(
  row: TaskDispatchDashboardRow,
): ParsedDispatchUserFacingError | undefined {
  const task = row.task;
  const structured =
    task.userFacingDispatchError ?? task.latestRoutingDecision?.userFacingError;
  const taskStatus = String(task.status ?? '').toUpperCase();
  if (["COMPLETED", "SUCCEEDED", "RESOLVED", "CANCELLED", "CANCELED"].includes(taskStatus)) return undefined;
  const value =
    task.dispatchWaitReason ??
    task.dispatchRetryReason ??
    task.blockedReason ??
    task.failureReason ??
    task.latestRoutingDecision?.decisionReason;
  const parsed = parseDispatchUserFacingError(value, structured);
  if (!parsed.code?.startsWith("DISPATCH_")) return undefined;
  return parsed;
}

function buildDispatchErrorGroups(
  rows: TaskDispatchDashboardRow[],
): DispatchErrorGroup[] {
  const groups = new Map<string, DispatchErrorGroup>();
  rows.forEach((row) => {
    const parsed = taskDispatchErrorForSummary(row);
    if (!parsed?.code) return;
    const lane = taskQueueLane(row);
    const current = groups.get(parsed.code) ?? {
      code: parsed.code,
      count: 0,
      blocked: 0,
      waiting: 0,
      message: parsed.message,
      nextAction: parsed.nextAction,
    };
    current.count += 1;
    if (lane === "needs-action") current.blocked += 1;
    if (lane === "waiting") current.waiting += 1;
    current.message = current.message || parsed.message;
    current.nextAction = current.nextAction || parsed.nextAction;
    groups.set(parsed.code, current);
  });
  return Array.from(groups.values()).sort((left, right) => {
    if (right.blocked !== left.blocked) return right.blocked - left.blocked;
    if (right.count !== left.count) return right.count - left.count;
    return left.code.localeCompare(right.code);
  });
}

export function TaskTable() {
  const {
    rows,
    loading,
    refreshing,
    error,
    lastUpdatedAt,
    refresh,
    commandMessage,
    retryTask,
    cancelTask,
    generatedAt,
    coreTaskCount,
    deliveryRuntimeAvailable,
    callbackRelayRuntimeAvailable,
    deliveryError,
    callbackRelayError,
  } = useTasks();
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState(allValue);
  const [agentFilter, setAgentFilter] = useState(allValue);
  const [dispatchStatusFilter, setDispatchStatusFilter] = useState(allValue);
  const [pendingAction, setPendingAction] = useState<{ type: "retry" | "cancel"; row: TaskDispatchDashboardRow } | null>(null);
  const [queueLane, setQueueLane] = useState<DispatchRecipeLane>("all");
  const [selectedTaskId, setSelectedTaskId] = useState<string | undefined>();

  const filteredRows = useMemo(
    () => rows
      .filter((row) => queueLane === "all" || taskQueueLane(row) === queueLane)
      .filter((row) => matchesTask(row, search, statusFilter, agentFilter, dispatchStatusFilter)),
    [agentFilter, dispatchStatusFilter, queueLane, rows, search, statusFilter],
  );
  const blockedRows = useMemo(() => rows.filter((row) => taskQueueLane(row) === "needs-action"), [rows]);
  const waitingRows = useMemo(() => rows.filter((row) => taskQueueLane(row) === "waiting"), [rows]);
  const doneRows = useMemo(() => rows.filter((row) => taskQueueLane(row) === "done"), [rows]);
  const dispatchErrorGroups = useMemo(() => buildDispatchErrorGroups(rows), [rows]);
  const primaryRow = blockedRows[0] ?? waitingRows[0] ?? rows[0];
  const queueDecision = primaryRow ? taskDecisionSummary(primaryRow) : undefined;
  const selectedRow = filteredRows.find((row) => row.task.taskId === selectedTaskId) ?? filteredRows[0];

  useEffect(() => {
    if (selectedTaskId && !filteredRows.some((row) => row.task.taskId === selectedTaskId)) setSelectedTaskId(undefined);
  }, [filteredRows, selectedTaskId]);

  const filters = useMemo<SelectFilterConfig[]>(() => {
    const statuses = uniqueSortedValues(rows.map((row) => row.task.status));
    const dispatchStatuses = uniqueSortedValues(rows.map((row) => row.task.dispatchStatus));
    const agents = uniqueSortedValues(rows.map((row) => row.task.assignedAgentId));
    return [
      { id: "status", label: "Task Status", value: statusFilter, onChange: setStatusFilter, options: [{ value: allValue, label: "All Task Statuses" }, ...statuses.map((status) => ({ value: status, label: status }))] },
      { id: "dispatchStatus", label: "Dispatch", value: dispatchStatusFilter, onChange: setDispatchStatusFilter, options: [{ value: allValue, label: "All Dispatch Statuses" }, ...dispatchStatuses.map((status) => ({ value: status, label: status }))] },
      { id: "agent", label: "Agent", value: agentFilter, onChange: setAgentFilter, options: [{ value: allValue, label: "All Agents" }, ...agents.map((agentId) => ({ value: agentId, label: agentId }))] },
    ];
  }, [agentFilter, dispatchStatusFilter, rows, statusFilter]);

  function clearFilters() {
    setSearch("");
    setStatusFilter(allValue);
    setAgentFilter(allValue);
    setDispatchStatusFilter(allValue);
    setQueueLane("all");
  }

  if (loading) return <LoadingBox label="Load Core Tasks and delivery runtime…" />;
  if (error) return <ErrorBox message={error} />;
  if (rows.length === 0) return <EmptyState title="No Core Task data" description="Verify the Core Task runtime view and active Tenant context." />;

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <CommandMessage message={commandMessage} />
        <div className="sm:ml-auto flex flex-wrap items-center gap-2"><BeginnerGuideButton title="Work Tasks without leaving the queue" description="Select a Task to see its summary beside the list. Retry and cancel actions open confirmation popups instead of sending you to another administration page." steps={[{title:'Choose a queue lane',description:'Start with Needs action, Waiting, Done, or All instead of building a complex query.'},{title:'Select one Task',description:'The detail preview stays beside the list and shows ownership, dispatch evidence and the safest next action.'},{title:'Use popup actions',description:'Retry and cancel are confirmed in-place. Open a full Task detail only when deeper evidence is necessary.'}]} /><RefreshButton refreshing={refreshing} lastUpdatedAt={lastUpdatedAt} onRefresh={refresh} /></div>
      </div>

      {queueDecision ? (
        <DecisionHeader
          eyebrow="Task queue"
          title={blockedRows.length ? `${blockedRows.length} Tasks need attention` : waitingRows.length ? `${waitingRows.length} Tasks are waiting` : "Task queue is healthy"}
          subtitle="Select one Task to review its blocking reason and safest next action without leaving the list."
          statusCode={queueDecision.statusCode}
          statusLabel={queueDecision.statusLabel}
          blockingReason={queueDecision.blockingReason}
          nextAction={queueDecision.nextAction}
          tone={blockedRows.length ? "danger" : waitingRows.length ? "warning" : "success"}
          facts={[{ label: "Total", value: rows.length }, { label: "Blocked", value: blockedRows.length }, { label: "Waiting", value: waitingRows.length }, { label: "Visible", value: filteredRows.length }]}
          primaryAction={{ label: blockedRows.length ? "Show tasks needing action" : "Show all tasks", onClick: () => setQueueLane(blockedRows.length ? "needs-action" : "all"), tone: blockedRows.length ? "danger" : "secondary" }}
        />
      ) : null}

      <section className="rounded-2xl border border-blue-200 bg-blue-50 p-4 text-sm text-blue-900">
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-black">Task operational status</span>
          <StatusBadge status="CORE_OK" />
          <span className="rounded-full bg-white px-2.5 py-1 text-xs font-bold">Core Tasks: {coreTaskCount}</span>
          <StatusBadge status={deliveryRuntimeAvailable ? "NETTY_OK" : "NETTY_UNAVAILABLE"} />
          <span className="rounded-full bg-white px-2.5 py-1 text-xs font-bold">Callback relay: {callbackRelayRuntimeAvailable ? "available" : "unavailable"}</span>
          {generatedAt ? <span className="rounded-full bg-white px-2.5 py-1 text-xs font-bold">Generated: {formatDateTime(generatedAt)}</span> : null}
        </div>
        {deliveryError ? <p className="mt-2 text-xs font-semibold text-amber-800">Delivery warning: {deliveryError}</p> : null}
        {callbackRelayError ? <p className="mt-1 text-xs font-semibold text-amber-800">Callback warning: {callbackRelayError}</p> : null}
      </section>

      {dispatchErrorGroups.length ? (
        <section className="rounded-2xl border border-amber-200 bg-amber-50 p-4">
          <div className="font-black text-amber-950">Blocking reason summary</div>
          <div className="mt-3 flex flex-wrap gap-2">
            {dispatchErrorGroups.slice(0, 8).map((group) => (
              <button key={group.code} type="button" onClick={() => setSearch(group.code)} className="rounded-xl border border-amber-200 bg-white px-3 py-2 text-left text-xs text-amber-950 shadow-sm">
                <strong>{group.code}</strong> · {group.count}
              </button>
            ))}
          </div>
        </section>
      ) : null}

      <div className="grid gap-2 md:grid-cols-4">
        {(["needs-action", "waiting", "done", "all"] as DispatchRecipeLane[]).map((lane) => {
          const count = lane === "needs-action" ? blockedRows.length : lane === "waiting" ? waitingRows.length : lane === "done" ? doneRows.length : rows.length;
          const active = queueLane === lane;
          return <button key={lane} type="button" onClick={() => setQueueLane(lane)} className={`rounded-2xl border px-4 py-3 text-left text-sm transition ${active ? "border-blue-300 bg-blue-50 text-blue-900 shadow-sm" : "border-slate-200 bg-white text-slate-700 hover:bg-slate-50"}`}><div className="font-black">{taskQueueLaneLabel(lane)}</div><div className="mt-1 text-xs opacity-80">{count} Tasks</div></button>;
        })}
      </div>

      <ListFilterBar search={search} searchPlaceholder="Search Task ID, issue, Agent, event, or error…" onSearchChange={setSearch} filters={filters} onClear={clearFilters} />

      <UiListCapabilityProvider>
        <AdminMasterDetailLayout
          listLabel="Task list"
          detailLabel="Selected Task summary"
          list={
            <VirtualizedResourceList
              items={filteredRows}
              getItemKey={(row) => row.task.taskId}
              rowHeight={210}
              height={720}
              empty={<EmptyState title="No matching Task" description="Adjust filters and try again." />}
              renderRow={(row) => (
                <TaskListRow
                  row={row}
                  selected={selectedRow?.task.taskId === row.task.taskId}
                  onSelect={() => setSelectedTaskId(row.task.taskId)}
                  onRetry={() => setPendingAction({ type: "retry", row })}
                  onCancel={() => setPendingAction({ type: "cancel", row })}
                  allowRetry={shouldAllowRetry(row)}
                  allowCancel={shouldAllowCancel(row)}
                />
              )}
            />
          }
          detail={<TaskListDetailPreview row={selectedRow} />}
        />
      </UiListCapabilityProvider>

      <TaskActionDialog
        open={pendingAction !== null}
        title={pendingAction?.type === "cancel" ? "Cancel Task" : "Retry Dispatch"}
        target={pendingAction?.row.task.taskId ?? ""}
        description={pendingAction?.type === "cancel" ? "Core will record the authoritative cancelled state after reauthorization." : "Core will reauthorize and retry the current dispatch path."}
        confirmLabel={pendingAction?.type === "cancel" ? "Confirm Cancellation" : "Confirm Retry Dispatch"}
        tone={pendingAction?.type === "cancel" ? "danger" : "warning"}
        requiredPhrase={pendingAction?.type === "cancel" ? "CONFIRM_CANCEL_TASK" : undefined}
        onCancel={() => setPendingAction(null)}
        onConfirm={async () => {
          if (!pendingAction) return;
          if (pendingAction.type === "cancel") await cancelTask(pendingAction.row);
          else await retryTask(pendingAction.row);
          setPendingAction(null);
        }}
      />
    </div>
  );
}
