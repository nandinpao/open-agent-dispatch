import 'server-only';
import { cache } from 'react';
import { loadUiPageBootstrap } from '@/lib/server/uiPageBootstrap';
import type { UiCapability } from '@/lib/ui-capability/contracts';
import { TASK_UI_ACTIONS } from '@/lib/ui-capability/taskActions';

function taskCompatibilityCapabilities(): readonly UiCapability[] {
  return Object.values(TASK_UI_ACTIONS).map((uiActionId) => uiActionId === TASK_UI_ACTIONS.view
    ? {
        uiActionId,
        displayMode: 'ENABLED' as const,
        stepUpRequired: false,
        approvalRequired: false,
        visibilityCeiling: 'FULL' as const,
        requestAccessAllowed: false,
      }
    : {
        uiActionId,
        displayMode: 'DISABLE_WITH_REASON' as const,
        reasonCategory: 'READ_ONLY_ACCESS' as const,
        stepUpRequired: false,
        approvalRequired: false,
        visibilityCeiling: 'FULL' as const,
        requestAccessAllowed: false,
      });
}

export const loadTaskDetailBootstrap = cache(async (taskId: string) => loadUiPageBootstrap({
  routeContext: 'task.detail',
  resourceId: taskId,
  returnPath: `/tasks/${encodeURIComponent(taskId)}`,
  compatibilityRead: {
    probePath: `/admin/tasks/${encodeURIComponent(taskId)}/operations-view?include=overview`,
    pageCapabilities: taskCompatibilityCapabilities(),
  },
}));
