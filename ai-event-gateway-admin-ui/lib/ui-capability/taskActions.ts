export const TASK_DETAIL_CONTEXT = 'task.detail' as const;

export const TASK_UI_ACTIONS = {
  view: 'task.detail.view',
  update: 'task.detail.update',
  manageParticipants: 'task.participant.manage',
  executeRemediation: 'task.remediation.execute',
  retry: 'task.retry.execute',
  cancel: 'task.cancel.execute',
  reassign: 'task.reassign.execute',
  runRecovery: 'task.recovery.execute',
  moveDeadLetter: 'task.dead-letter.execute',
  restoreDeadLetter: 'task.dead-letter.restore',
  escalate: 'task.escalate.execute',
  retryIssueSync: 'task.issue-sync.retry',
  retryHandoff: 'task.handoff.retry',
  reconcileA2AResult: 'task.a2a-result.reconcile',
  createA2ARequest: 'task.a2a-request.create',
} as const;

export type TaskUiActionId = (typeof TASK_UI_ACTIONS)[keyof typeof TASK_UI_ACTIONS];
