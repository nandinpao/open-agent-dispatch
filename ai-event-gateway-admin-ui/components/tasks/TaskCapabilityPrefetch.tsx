'use client';

import { useUiCapability } from '@/components/ui-capability/UiPageBootstrapProvider';
import { TASK_DETAIL_CONTEXT, TASK_UI_ACTIONS } from '@/lib/ui-capability/taskActions';

/**
 * Coalesces the Task detail pilot's secondary action hydration through the shared
 * debounce coordinator. This component renders no UI and does not authorize mutations.
 */
export function TaskCapabilityPrefetch() {
  useUiCapability(TASK_DETAIL_CONTEXT, TASK_UI_ACTIONS.executeRemediation);
  useUiCapability(TASK_DETAIL_CONTEXT, TASK_UI_ACTIONS.retry);
  useUiCapability(TASK_DETAIL_CONTEXT, TASK_UI_ACTIONS.cancel);
  useUiCapability(TASK_DETAIL_CONTEXT, TASK_UI_ACTIONS.reassign);
  useUiCapability(TASK_DETAIL_CONTEXT, TASK_UI_ACTIONS.runRecovery);
  useUiCapability(TASK_DETAIL_CONTEXT, TASK_UI_ACTIONS.moveDeadLetter);
  useUiCapability(TASK_DETAIL_CONTEXT, TASK_UI_ACTIONS.restoreDeadLetter);
  useUiCapability(TASK_DETAIL_CONTEXT, TASK_UI_ACTIONS.escalate);
  useUiCapability(TASK_DETAIL_CONTEXT, TASK_UI_ACTIONS.retryIssueSync);
  useUiCapability(TASK_DETAIL_CONTEXT, TASK_UI_ACTIONS.retryHandoff);
  useUiCapability(TASK_DETAIL_CONTEXT, TASK_UI_ACTIONS.reconcileA2AResult);
  useUiCapability(TASK_DETAIL_CONTEXT, TASK_UI_ACTIONS.createA2ARequest);
  return null;
}
