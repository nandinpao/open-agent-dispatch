"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { CommandMessage } from "@/components/common/CommandMessage";
import { DecisionHeader } from "@/components/common/DecisionHeader";
import { DispatchOperatorActions } from "@/components/common/DispatchOperatorActions";
import { DispatchUserFacingReason } from "@/components/common/DispatchUserFacingReason";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorBox } from "@/components/common/ErrorBox";
import {
  ListFilterBar,
  type SelectFilterConfig,
} from "@/components/common/ListFilterBar";
import { LoadingBox } from "@/components/common/LoadingBox";
import { PaginationControls } from "@/components/common/PaginationControls";
import { RefreshButton } from "@/components/common/RefreshButton";
import { StatusBadge } from "@/components/common/StatusBadge";
import { TaskActionDialog } from "@/components/tasks/TaskActionDialog";
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
import { buildDispatchOperatorActions } from "@/lib/dispatch-readiness/dispatchOperatorActions";
import { formatDateTime } from "@/lib/utils/format";
import {
  paginateItems,
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

function severityBadgeClass(code: string): string {
  const normalized = String(code ?? "").toUpperCase();
  if (normalized === "CRITICAL")
    return "border-rose-300 bg-rose-50 text-rose-700";
  if (normalized === "HIGH")
    return "border-orange-300 bg-orange-50 text-orange-700";
  if (normalized === "MIDDLE" || normalized === "MEDIUM")
    return "border-amber-300 bg-amber-50 text-amber-700";
  if (normalized === "LOW")
    return "border-emerald-300 bg-emerald-50 text-emerald-700";
  return "border-slate-200 bg-slate-50 text-slate-600";
}

function TaskIssueLink({
  issueId,
  issueUrl,
  vendor,
  status,
}: Readonly<{
  issueId?: string;
  issueUrl?: string;
  vendor?: string;
  status: string;
}>) {
  const label =
    vendor && issueId
      ? `${vendor} ${issueId}`
      : issueId
        ? `Issue ${issueId}`
        : status === "NOT_LINKED"
          ? "尚未連結"
          : status;
  if (!issueUrl) return <span>{label}</span>;
  return (
    <a
      href={issueUrl}
      target="_blank"
      rel="noreferrer"
      className="font-semibold text-blue-600 hover:text-blue-700"
    >
      {label}
    </a>
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
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [queueLane, setQueueLane] = useState<DispatchRecipeLane>("all");

  const filteredRows = useMemo(
    () =>
      rows
        .filter(
          (row) => queueLane === "all" || taskQueueLane(row) === queueLane,
        )
        .filter((row) =>
          matchesTask(
            row,
            search,
            statusFilter,
            agentFilter,
            dispatchStatusFilter,
          ),
        ),
    [agentFilter, dispatchStatusFilter, queueLane, rows, search, statusFilter],
  );

  const blockedRows = useMemo(
    () => rows.filter((row) => taskQueueLane(row) === "needs-action"),
    [rows],
  );
  const waitingRows = useMemo(
    () => rows.filter((row) => taskQueueLane(row) === "waiting"),
    [rows],
  );
  const doneRows = useMemo(
    () => rows.filter((row) => taskQueueLane(row) === "done"),
    [rows],
  );
  const dispatchErrorGroups = useMemo(
    () => buildDispatchErrorGroups(rows),
    [rows],
  );
  const primaryRow = blockedRows[0] ?? waitingRows[0] ?? rows[0];
  const queueDecision = primaryRow
    ? taskDecisionSummary(primaryRow)
    : undefined;

  const pagination = useMemo(
    () => paginateItems(filteredRows, { page, pageSize }),
    [filteredRows, page, pageSize],
  );

  useEffect(() => {
    setPage(1);
  }, [
    search,
    statusFilter,
    agentFilter,
    dispatchStatusFilter,
    queueLane,
    pageSize,
  ]);

  const filters = useMemo<SelectFilterConfig[]>(() => {
    const statuses = uniqueSortedValues(rows.map((row) => row.task.status));
    const dispatchStatuses = uniqueSortedValues(
      rows.map((row) => row.task.dispatchStatus),
    );
    const agents = uniqueSortedValues(
      rows.map((row) => row.task.assignedAgentId),
    );

    return [
      {
        id: "status",
        label: "Task Status",
        value: statusFilter,
        onChange: setStatusFilter,
        options: [
          { value: allValue, label: "All Task Statuses" },
          ...statuses.map((status) => ({ value: status, label: status })),
        ],
      },
      {
        id: "dispatchStatus",
        label: "Dispatch",
        value: dispatchStatusFilter,
        onChange: setDispatchStatusFilter,
        options: [
          { value: allValue, label: "All Dispatch Statuses" },
          ...dispatchStatuses.map((status) => ({
            value: status,
            label: status,
          })),
        ],
      },
      {
        id: "agent",
        label: "Agent",
        value: agentFilter,
        onChange: setAgentFilter,
        options: [
          { value: allValue, label: "All Agents" },
          ...agents.map((agentId) => ({ value: agentId, label: agentId })),
        ],
      },
    ];
  }, [agentFilter, dispatchStatusFilter, rows, statusFilter]);

  function clearFilters() {
    setSearch("");
    setStatusFilter(allValue);
    setAgentFilter(allValue);
    setDispatchStatusFilter(allValue);
  }

  function confirmRetry(row: TaskDispatchDashboardRow) {
    setPendingAction({ type: "retry", row });
  }

  function confirmCancel(row: TaskDispatchDashboardRow) {
    setPendingAction({ type: "cancel", row });
  }

  if (loading)
    return <LoadingBox label="讀取 Core Tasks 與 Netty delivery runtime..." />;
  if (error) return <ErrorBox message={error} />;
  if (rows.length === 0)
    return (
      <EmptyState
        title="目前沒有 Core Task 資料"
        description="請確認 Core /admin/tasks/runtime-view 是否有回傳任務權威資料。"
      />
    );

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <CommandMessage message={commandMessage} />
        <div className="sm:ml-auto">
          <RefreshButton
            refreshing={refreshing}
            lastUpdatedAt={lastUpdatedAt}
            onRefresh={refresh}
          />
        </div>
      </div>

      {queueDecision ? (
        <DecisionHeader
          eyebrow="Task queue decision header"
          title={
            blockedRows.length > 0
              ? `${blockedRows.length} 個 Task 需要處理`
              : waitingRows.length > 0
                ? `${waitingRows.length} 個 Task 正在等待`
                : "Task queue 目前穩定"
          }
          subtitle="這裡先回答 Operator 最關心的問題：目前有沒有卡住、要不要重試、是否需要改派。"
          statusCode={queueDecision.statusCode}
          statusLabel={queueDecision.statusLabel}
          blockingReason={queueDecision.blockingReason}
          nextAction={queueDecision.nextAction}
          tone={
            blockedRows.length > 0
              ? "danger"
              : waitingRows.length > 0
                ? "warning"
                : "success"
          }
          facts={[
            { label: "Total", value: rows.length },
            { label: "Blocked", value: blockedRows.length },
            { label: "Waiting", value: waitingRows.length },
            { label: "Filtered", value: filteredRows.length },
          ]}
          primaryAction={{
            label: "Open Failure Queue",
            href: "/tasks/failure-queue",
            tone: blockedRows.length > 0 ? "danger" : "secondary",
          }}
          secondaryActions={[
            {
              label: "Create Test Task",
              href: "/testing/dispatch-recipes",
              tone: "secondary",
            },
          ]}
        />
      ) : null}

      <div className="rounded-2xl border border-blue-200 bg-blue-50 p-4 text-sm text-blue-800">
        <div className="font-semibold">OpenDispatch Task Control Console</div>
        <p className="mt-1 leading-6">
          Admin UI 顯示任務摘要、Agent 執行狀態與 Issue Tracking
          連結；完整討論與 Agent 分析歷程應進入
          Redmine/GitLab/Jira。平台工程診斷保留在任務詳細頁的進階區塊。
        </p>
        <div className="mt-2 flex flex-wrap gap-2 text-xs">
          <StatusBadge status="CORE_OK" />
          <span className="rounded-full bg-white px-2.5 py-1 font-semibold text-blue-700">
            Core tasks：{coreTaskCount}
          </span>
          <StatusBadge
            status={deliveryRuntimeAvailable ? "NETTY_OK" : "NETTY_UNAVAILABLE"}
          />
          <span className="rounded-full bg-white px-2.5 py-1 font-semibold text-blue-700">
            Delivery runtime：
            {deliveryRuntimeAvailable ? "available" : "unavailable"}
          </span>
          <StatusBadge
            status={
              callbackRelayRuntimeAvailable ? "NETTY_OK" : "NETTY_UNAVAILABLE"
            }
          />
          <span className="rounded-full bg-white px-2.5 py-1 font-semibold text-blue-700">
            Callback relay：
            {callbackRelayRuntimeAvailable ? "available" : "unavailable"}
          </span>
          {generatedAt ? (
            <span className="rounded-full bg-white px-2.5 py-1 font-semibold text-blue-700">
              Generated：{formatDateTime(generatedAt)}
            </span>
          ) : null}
        </div>
        {deliveryError ? (
          <p className="mt-2 text-xs font-semibold text-amber-700">
            Delivery runtime warning：{deliveryError}
          </p>
        ) : null}
        {callbackRelayError ? (
          <p className="mt-1 text-xs font-semibold text-amber-700">
            Callback relay warning：{callbackRelayError}
          </p>
        ) : null}
      </div>

      {dispatchErrorGroups.length > 0 ? (
        <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4">
          <div className="flex flex-col gap-1 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <div className="text-sm font-black text-amber-950">
                Dispatch error code summary
              </div>
              <p className="mt-1 text-xs leading-5 text-amber-800">
                依最新 routing decision / recovery reason
                彙總。點選代碼可直接用該 code 篩選 Task。
              </p>
            </div>
            <div className="text-xs font-bold text-amber-800">
              {dispatchErrorGroups.reduce((sum, group) => sum + group.count, 0)}{" "}
              筆有派工阻擋代碼
            </div>
          </div>
          <div className="mt-3 grid gap-2 lg:grid-cols-2 xl:grid-cols-3">
            {dispatchErrorGroups.map((group) => (
              <div
                key={group.code}
                className="rounded-xl border border-amber-200 bg-white/80 p-3 text-left text-sm shadow-sm"
              >
                <button
                  type="button"
                  onClick={() => setSearch(group.code)}
                  className="block w-full text-left hover:opacity-80"
                >
                  <div className="flex items-center justify-between gap-3">
                    <span className="break-all font-black text-amber-950">
                      {group.code}
                    </span>
                    <span className="shrink-0 rounded-full bg-amber-900 px-2.5 py-1 text-xs font-black text-white">
                      {group.count}
                    </span>
                  </div>
                  <div className="mt-1 text-xs leading-5 text-amber-900">
                    {group.message}
                  </div>
                  <div className="mt-2 flex flex-wrap gap-2 text-[11px] font-bold text-amber-800">
                    <span>Needs action：{group.blocked}</span>
                    <span>Waiting：{group.waiting}</span>
                    {group.nextAction ? (
                      <span className="break-words">
                        Next：{group.nextAction}
                      </span>
                    ) : null}
                  </div>
                </button>
                <div className="mt-3 border-t border-amber-100 pt-3">
                  <DispatchOperatorActions
                    compact
                    actions={buildDispatchOperatorActions(group, { includeRunbook: false })}
                  />
                </div>
              </div>
            ))}
          </div>
        </div>
      ) : null}

      <div className="grid gap-2 md:grid-cols-4">
        {(
          ["needs-action", "waiting", "done", "all"] as DispatchRecipeLane[]
        ).map((lane) => {
          const count =
            lane === "needs-action"
              ? blockedRows.length
              : lane === "waiting"
                ? waitingRows.length
                : lane === "done"
                  ? doneRows.length
                  : rows.length;
          const active = queueLane === lane;
          return (
            <button
              key={lane}
              type="button"
              onClick={() => setQueueLane(lane)}
              className={`rounded-2xl border px-4 py-3 text-left text-sm transition ${active ? "border-blue-300 bg-blue-50 text-blue-900 shadow-sm" : "border-slate-200 bg-white text-slate-700 hover:bg-slate-50"}`}
            >
              <div className="font-black">{taskQueueLaneLabel(lane)}</div>
              <div className="mt-1 text-xs opacity-80">{count} 筆任務</div>
            </button>
          );
        })}
      </div>

      <ListFilterBar
        search={search}
        searchPlaceholder="搜尋 Task ID、Issue、Agent、事件、錯誤訊息"
        onSearchChange={setSearch}
        filters={filters}
        onClear={clearFilters}
      />

      {filteredRows.length === 0 ? (
        <EmptyState
          title="沒有符合條件的任務"
          description="請調整關鍵字、Task 狀態、Dispatch 狀態或 Agent 篩選條件。"
        />
      ) : (
        <>
          <div className="space-y-3">
            {pagination.items.map((row) => {
              const task = row.task;
              const display = buildTaskWorkbenchDisplay(row);
              const issue = display.issueBridge;
              const showLatestRoutingError = Boolean(
                (task.userFacingDispatchError ??
                  task.latestRoutingDecision?.userFacingError ??
                  task.latestRoutingDecision?.decisionReason) &&
                !task.dispatchWaitReason &&
                !task.dispatchRetryReason &&
                !task.blockedReason &&
                !task.failureReason,
              );
              return (
                <div
                  key={task.taskId}
                  className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"
                >
                  <div className="flex flex-col gap-3 xl:flex-row xl:items-start xl:justify-between">
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <StatusBadge
                          status={display.businessStatusCode}
                          label={display.businessStatus}
                        />
                        <span
                          className={`rounded-full border px-2.5 py-1 text-xs font-black ${severityBadgeClass(display.severity.code)}`}
                        >
                          Severity {display.severity.label}
                        </span>
                        <StatusBadge
                          status={issue.status}
                          label={
                            issue.status === "LINKED"
                              ? "Issue linked"
                              : issue.status === "SYNC_FAILED"
                                ? "Issue sync failed"
                                : issue.status === "SYNC_PENDING"
                                  ? "Issue sync pending"
                                  : "No issue link"
                          }
                        />
                      </div>
                      <Link
                        href={`/tasks/${encodeURIComponent(task.taskId)}`}
                        className="mt-2 block break-words text-base font-bold text-slate-950 hover:text-blue-700"
                      >
                        {display.title}
                      </Link>
                      <div className="mt-1 break-all text-xs text-slate-500">
                        {display.subtitle}
                      </div>
                    </div>
                    <div className="flex flex-wrap items-center gap-2">
                      <Link
                        href={`/tasks/${encodeURIComponent(task.taskId)}`}
                        className="rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                      >
                        Open console
                      </Link>
                      {shouldAllowRetry(row) ? (
                        <button
                          type="button"
                          onClick={() => void confirmRetry(row)}
                          className="rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                        >
                          Retry via Core
                        </button>
                      ) : null}
                      {shouldAllowCancel(row) ? (
                        <button
                          type="button"
                          onClick={() => void confirmCancel(row)}
                          className="rounded-lg border border-rose-200 px-3 py-1.5 text-xs font-semibold text-rose-700 hover:bg-rose-50"
                        >
                          Cancel via Core
                        </button>
                      ) : null}
                    </div>
                  </div>

                  <div className="mt-4 grid grid-cols-1 gap-3 text-sm text-slate-600 md:grid-cols-2 xl:grid-cols-4">
                    <div>
                      <span className="font-semibold text-slate-400">
                        來源：
                      </span>
                      {display.sourceLabel}
                    </div>
                    <div>
                      <span className="font-semibold text-slate-400">
                        對象：
                      </span>
                      {display.targetLabel}
                    </div>
                    <div>
                      <span className="font-semibold text-slate-400">
                        事件：
                      </span>
                      {display.eventLabel}
                    </div>
                    <div>
                      <span className="font-semibold text-slate-400">
                        Severity：
                      </span>
                      <span
                        className={`font-bold ${display.severity.isHighImpact ? "text-rose-700" : "text-slate-700"}`}
                      >
                        {display.severity.label}
                      </span>
                    </div>
                    <div>
                      <span className="font-semibold text-slate-400">
                        Agent：
                      </span>
                      {task.assignedAgentId ?? "尚未指派"}
                    </div>
                    <div>
                      <span className="font-semibold text-slate-400">
                        Issue：
                      </span>
                      <TaskIssueLink
                        vendor={issue.vendor}
                        issueId={issue.issueId}
                        issueUrl={issue.issueUrl}
                        status={issue.status}
                      />
                    </div>
                    <div>
                      <span className="font-semibold text-slate-400">
                        下一步：
                      </span>
                      {display.nextStep}
                    </div>
                    <div>
                      <span className="font-semibold text-slate-400">
                        建立：
                      </span>
                      {task.createdAt ? formatDateTime(task.createdAt) : "-"}
                    </div>
                    <div>
                      <span className="font-semibold text-slate-400">
                        更新：
                      </span>
                      {task.updatedAt ? formatDateTime(task.updatedAt) : "-"}
                    </div>
                  </div>

                  <div className="mt-4 rounded-xl border border-slate-100 bg-slate-50 p-3 text-sm text-slate-700">
                    <div className="text-xs font-bold uppercase tracking-wide text-slate-400">
                      Agent 最新摘要
                    </div>
                    <p className="mt-1 leading-6">
                      {display.latestAgentSummary}
                    </p>
                  </div>

                  {task.blockedReason ? (
                    <p className="mt-3 text-sm font-medium text-amber-800">
                      Blocked：{task.blockedReason}
                      {task.nextAction
                        ? ` · Next action：${task.nextAction}`
                        : ""}
                    </p>
                  ) : null}
                  {task.dispatchWaitReason ? (
                    <div className="mt-3 rounded-xl bg-amber-50 p-3 text-sm font-medium text-amber-800">
                      <div className="mb-1 text-xs font-black uppercase tracking-wide text-amber-700">
                        Dispatch wait / recovery
                      </div>
                      <DispatchUserFacingReason
                        value={task.dispatchWaitReason}
                        error={task.userFacingDispatchError}
                        showOperatorActions
                        actionContext={{ taskId: task.taskId, agentId: task.assignedAgentId, reasonCategory: task.reasonCategory, includeRunbook: false }}
                        codeClassName="inline-flex rounded-full bg-amber-900 px-2.5 py-1 text-[11px] font-black uppercase tracking-wide text-white"
                        detailsClassName="rounded-xl border border-amber-100 bg-white/70 px-3 py-2 text-xs font-semibold"
                        technicalClassName="mt-2 break-words whitespace-pre-wrap font-mono leading-5 text-amber-900"
                      />
                    </div>
                  ) : null}
                  {showLatestRoutingError ? (
                    <div className="mt-3 rounded-xl bg-amber-50 p-3 text-sm font-medium text-amber-800">
                      <div className="mb-1 text-xs font-black uppercase tracking-wide text-amber-700">
                        Latest routing decision
                      </div>
                      <DispatchUserFacingReason
                        value={task.latestRoutingDecision?.decisionReason}
                        error={
                          task.userFacingDispatchError ??
                          task.latestRoutingDecision?.userFacingError
                        }
                        showOperatorActions
                        actionContext={{ taskId: task.taskId, agentId: task.assignedAgentId, reasonCategory: task.reasonCategory, includeRunbook: false }}
                        codeClassName="inline-flex rounded-full bg-amber-900 px-2.5 py-1 text-[11px] font-black uppercase tracking-wide text-white"
                        detailsClassName="rounded-xl border border-amber-100 bg-white/70 px-3 py-2 text-xs font-semibold"
                        technicalClassName="mt-2 break-words whitespace-pre-wrap font-mono leading-5 text-amber-900"
                      />
                    </div>
                  ) : null}
                  {task.failureReason ? (
                    <div className="mt-3 rounded-xl bg-rose-50 p-3 text-sm font-medium text-rose-800">
                      <div className="mb-1 text-xs font-black uppercase tracking-wide text-rose-700">
                        Failure
                      </div>
                      <DispatchUserFacingReason
                        value={task.failureReason}
                        error={task.userFacingDispatchError}
                        showOperatorActions
                        actionContext={{ taskId: task.taskId, agentId: task.assignedAgentId, reasonCategory: task.reasonCategory, includeRunbook: false }}
                        codeClassName="inline-flex rounded-full bg-rose-900 px-2.5 py-1 text-[11px] font-black uppercase tracking-wide text-white"
                        detailsClassName="rounded-xl border border-rose-100 bg-white/70 px-3 py-2 text-xs font-semibold"
                        technicalClassName="mt-2 break-words whitespace-pre-wrap font-mono leading-5 text-rose-900"
                      />
                    </div>
                  ) : null}
                </div>
              );
            })}
          </div>

          <PaginationControls
            page={pagination.page}
            pageSize={pagination.pageSize}
            totalItems={pagination.totalItems}
            totalPages={pagination.totalPages}
            startItem={pagination.startItem}
            endItem={pagination.endItem}
            onPageChange={setPage}
            onPageSizeChange={setPageSize}
          />
        </>
      )}
      <TaskActionDialog
        open={pendingAction !== null}
        title={pendingAction?.type === "cancel" ? "取消 Task" : "重新派工"}
        target={pendingAction?.row.task.taskId ?? ""}
        description={pendingAction?.type === "cancel" ? "取消後 Task 將由 Core 權威狀態機進入取消狀態。" : "重新派工會使用目前已儲存的 Dispatch Flow、Agent 與 Capability 設定。"}
        confirmLabel={pendingAction?.type === "cancel" ? "確認取消" : "確認重新派工"}
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
