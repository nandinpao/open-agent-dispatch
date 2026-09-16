"use client";

import { TaskWorkbenchPanel } from "@/components/tasks/TaskWorkbenchPanel";
import type { TaskDispatchDashboardRow } from "@/lib/dashboard/taskDispatchMerge";

/** Canonical Issue Automation / provider-operation evidence for one Task. Legacy Issue Projection authority is retired. */
export function TaskIssueSynchronizationPanel({
  row,
  onRetryIssueSync,
  retryingIssueSyncActionId,
}: Readonly<{
  row: TaskDispatchDashboardRow;
  onRetryIssueSync: (actionId: string) => void;
  retryingIssueSyncActionId?: string;
}>) {
  return (
    <TaskWorkbenchPanel
      row={row}
      onRetryIssueSync={onRetryIssueSync}
      retryingIssueSyncActionId={retryingIssueSyncActionId}
    />
  );
}
