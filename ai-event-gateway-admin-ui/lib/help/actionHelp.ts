export interface ActionHelpEntry {
  label: string;
  description: string;
  requiresConfirm?: boolean;
  requiresReason?: boolean;
  danger?: boolean;
}

export const actionHelp = {
  create: {
    label: 'Create',
    description: '建立新資料。大型表單應放在 Drawer 或 Wizard，不應直接塞進 List 頁。'
  },
  edit: {
    label: 'Edit',
    description: '修改既有資料。Detail 頁預設唯讀，按 Edit 後才進 Drawer。'
  },
  delete: {
    label: 'Delete',
    description: '刪除尚未生效或可安全移除的資料。Active 或有 audit trail 的資料通常不可硬刪。',
    requiresConfirm: true,
    danger: true
  },
  disable: {
    label: 'Disable',
    description: '停用 Profile、Policy 或設定，使其不再被新流程使用。影響範圍可能很大，應先看 Impact Preview。',
    requiresConfirm: true,
    danger: true
  },
  remove: {
    label: 'Remove',
    description: '移除尚未生效的 assignment 或 binding。Pending qualification 用 Remove，不用 Revoke。',
    requiresConfirm: true
  },
  revoke: {
    label: 'Revoke',
    description: '撤銷已生效或曾生效的授權，必須保留 audit history。',
    requiresConfirm: true,
    requiresReason: true,
    danger: true
  },
  suspend: {
    label: 'Suspend',
    description: '暫停已核准資格或資源，通常可 Resume。適合短期風險控管。',
    requiresConfirm: true,
    requiresReason: true
  },
  resume: {
    label: 'Resume',
    description: '恢復 suspended / expired 類授權。恢復前仍應檢查 credential、certification 與 runtime eligibility。',
    requiresConfirm: true
  },
  simulate: {
    label: 'Simulate',
    description: '使用 sample payload 或測試條件預演，不會直接改變 production state。'
  },
  debug: {
    label: 'Debug',
    description: '工程診斷資料，例如 raw payload、trace、score breakdown。Debug 應預設收合，避免干擾 Operator。'
  },
  retry: {
    label: 'Retry',
    description: '重新嘗試派工或執行流程。Retry 前應確認阻擋原因已修正。',
    requiresConfirm: true
  }
} as const satisfies Record<string, ActionHelpEntry>;

export type ActionHelpKey = keyof typeof actionHelp;

export function getActionHelp(key: ActionHelpKey): ActionHelpEntry {
  return actionHelp[key];
}
