export interface StatusHelpEntry {
  label: string;
  description: string;
  operatorAction?: string;
}

export const statusHelp = {
  ACTIVE: {
    label: 'ACTIVE',
    description: '目前啟用並可被相關流程使用。',
    operatorAction: '若要停止使用，先確認 Impact Preview，再執行 Disable 或 Retire。'
  },
  DISABLED: {
    label: 'DISABLED',
    description: '已停用，不應再進入新的 routing 或治理判斷。',
    operatorAction: '若要刪除，確認沒有既有 qualification / binding / audit 需求。'
  },
  PENDING: {
    label: 'PENDING',
    description: '尚未核准、尚未生效。Routing 不會使用 pending qualification。',
    operatorAction: '可 Approve 使其生效，或 Remove 取消未生效 assignment。'
  },
  APPROVED: {
    label: 'APPROVED',
    description: '已核准且可參與 routing eligibility。',
    operatorAction: '若需暫停用 Suspend；若需永久撤銷用 Revoke。'
  },
  SUSPENDED: {
    label: 'SUSPENDED',
    description: '曾經生效但目前暫停。Routing 不應使用 suspended qualification。',
    operatorAction: '可 Resume 恢復，或 Revoke 撤銷。'
  },
  REVOKED: {
    label: 'REVOKED',
    description: '已撤銷並保留歷史。此授權不再可用。',
    operatorAction: '若要重新授權，建立新的 assignment / qualification。'
  },
  EXPIRED: {
    label: 'EXPIRED',
    description: '授權或憑證已過期，不應參與 routing。',
    operatorAction: '依治理規則 Resume、Renew 或重新 assign。'
  },
  ELIGIBLE: {
    label: 'ELIGIBLE',
    description: '目前符合派工條件，可被 routing 選取。'
  },
  BLOCKED: {
    label: 'BLOCKED',
    description: '目前無法派工，通常是缺 Profile、Policy、Qualification、Credential、Capacity 或 Runtime Feature。',
    operatorAction: '進 Troubleshooting Wizard 依步驟修正第一個 FAILED item。'
  },
  RETRY_WAIT: {
    label: 'RETRY_WAIT',
    description: '等待下一次 dispatch recovery，不是 terminal failure。',
    operatorAction: '可等待 scanner，或在 Task Detail 觸發 Recovery / Manual Retry。'
  },
  DEAD_LETTER: {
    label: 'DEAD_LETTER',
    description: '已進入 DLQ / dead letter，需要人工調查或重送。'
  }
} as const satisfies Record<string, StatusHelpEntry>;

export type StatusHelpKey = keyof typeof statusHelp;

export function getStatusHelp(status: string | null | undefined): StatusHelpEntry | undefined {
  const normalized = (status ?? '').trim().toUpperCase();
  return statusHelp[normalized as StatusHelpKey];
}
