import type { CoreDispatchReadinessCheck } from '@/lib/types/core';

export interface BeginnerLabel {
  label: string;
  description: string;
  shortLabel?: string;
  beginnerAction?: string;
}

export const capabilityBeginnerLabels: Record<string, BeginnerLabel> = {
  TASK_EXECUTION: {
    label: '任務執行能力',
    description: 'Agent 可接收並執行已通過治理與派單規則的任務。'
  },
  GENERAL_AGENT: {
    label: '一般 Agent 能力',
    description: 'Agent 的基本平台能力；實際可處理範圍仍由後台 Capability、Dispatch Flow Agent assignment 與 Action Grant 決定。'
  }
};

export const statusBeginnerLabels: Record<string, BeginnerLabel> = {
  APPROVED: { label: '已核准', description: 'Core Governance 已允許此 Agent 或派工請求。' },
  REJECTED: { label: '已拒絕', description: 'Core Governance 明確拒絕，需重新審核。' },
  REVOKED: { label: '已撤銷', description: 'Credential 或治理狀態已撤銷，不能派工。' },
  SUSPENDED: { label: '已暫停', description: 'Agent 暫時不可被派工。' },
  NORMAL: { label: '風險正常', description: '目前沒有 quarantine、revoked 或 compromised 風險。' },
  CONNECTED: { label: '已連線', description: 'Runtime Agent 目前與 Gateway 有 active session。' },
  OFFLINE: { label: '離線', description: 'Runtime Agent 目前沒有可用連線。' },
  IDLE: { label: '空閒', description: 'Runtime 未回報正在執行任務；不代表它沒有收到 Gateway 派送。' },
  RUNNING: { label: '執行中', description: 'Agent 或 Task 已進入執行狀態。' },
  ASSIGNED: { label: '已選中 Agent', description: 'Core routing 已選出要處理此 Task 的 Agent。' },
  DISPATCH_REQUESTED: { label: '已建立派工請求', description: 'Core 已建立 Dispatch Request，等待送到 Gateway。' },
  DISPATCHED: { label: '已送出派工', description: 'Gateway delivery 已觀測到任務送出。' },
  ACKED: { label: 'Agent 已確認', description: 'Agent 已回應 ACK，等待進一步結果。' },
  COMPLETED: { label: '已完成', description: 'Task 已成功完成。' },
  FAILED: { label: '失敗', description: 'Task 或派工流程失敗，需要查看原因與重試。' },
  CANCELLED: { label: '已取消', description: 'Task 已被取消。' },
  PENDING: { label: '等待中', description: '流程尚未開始或等待下一步。' },
  QUEUED: { label: '已排隊', description: '等待 worker 或 Gateway 處理。' },
  DELIVERED_TO_GATEWAY: { label: '已送達 Gateway', description: 'Netty Gateway 已接到派工；下一步通常是等待 Agent callback。' },
  WAIT_FOR_AGENT_ACK: { label: '等待 Agent 確認', description: 'Gateway 已接收派工，Core 正等待 Agent ACK / callback。' },
  WAIT_FOR_AGENT_RESULT: { label: '等待 Agent 結果', description: 'Agent 已回應，Core 等待 RESULT 或 ERROR callback。' },
  WAITING_AGENT: { label: '等待可用 Agent', description: 'Core 還沒有找到可派工 Agent 或尚未完成指派。' },
  WAIT_GATEWAY_ACK: { label: '等待 Gateway 確認', description: '派工請求正在等待 Gateway 接收或確認。' },
  NETTY_ACK: { label: 'Gateway 已接受', description: 'Netty 已接受派工，仍需等待 Agent callback。' },
  CALLBACK_RELAY: { label: 'Callback Relay', description: 'Gateway 正將 Agent callback relay 回 Core。' }
};

export const policyBeginnerLabels: Record<string, BeginnerLabel> = {
  READ_ONLY: { label: '只能讀取', description: 'Agent 只能讀取資料，不可直接修改系統。' },
  PROPOSE_ONLY: { label: '只能提出建議', description: 'Agent 可提出建議，但不可自動執行高風險動作。' },
  SAFE_COMMAND_ALLOWED: { label: '允許安全命令', description: 'Agent 可執行已被允許的低風險命令。' },
  ANALYZE: { label: '分析', description: 'Agent 可分析事件、資料與上下文。' },
  PROPOSE: { label: '提出建議', description: 'Agent 可回報建議修復或處置方式。' },
  READ: { label: '讀取', description: 'Agent 可讀取必要資料。' }
};

export const readinessCheckLabels: Record<string, string> = {
  TASK_REQUIRES_CAPABILITY: 'Task Requirement 已解析',
  CAPABILITY_DEFINED: 'Capability Catalog 已存在',
  DISPATCH_CONTRACT_RESOLVED: 'Task 需求可轉成派工資格合約',
  GOVERNANCE_PROFILE: 'Agent 身份已通過治理核准',
  GOVERNANCE_APPROVED_CAPABILITY: 'Agent 已取得後台 Flow Agent approval',
  RUNTIME_AGENT_ONLINE: 'Agent Runtime 目前在線且可接任務',
  RUNTIME_REPORTED_CAPABILITY: 'Runtime protocol feature 檢查',
  AGENT_CAPACITY_AVAILABLE: 'Agent 還有可用容量',
  CAPABILITY_CONTRACT_ELIGIBLE: 'Dispatch Eligibility 總檢查通過'
};

export const readinessCheckBeginnerHints: Record<string, string> = {
  TASK_REQUIRES_CAPABILITY: 'Task 必須先解析出任務類型、工具政策與後台派工需求，系統才知道需要哪種 Agent Dispatch Flow Agent assignment。',
  CAPABILITY_DEFINED: '後台必須明確建立 Capability 或選擇安全的 Source Baseline；系統不會依來源名稱自動推論。',
  DISPATCH_CONTRACT_RESOLVED: '派工合約會把 Task 的需求轉成可檢查的 required profile、runtime feature、tool policy 與風險限制。',
  GOVERNANCE_PROFILE: 'Agent 身份必須先被公司治理核准，不能只靠 runtime 自己宣稱。',
  GOVERNANCE_APPROVED_CAPABILITY: 'Agent 必須有後台核准的 Agent Dispatch Flow Agent assignment / Flow Agent approval，才可以接此類任務。',
  RUNTIME_AGENT_ONLINE: 'Agent 必須真的連上 Gateway，否則 Core 找得到 profile 也送不出去。',
  RUNTIME_REPORTED_CAPABILITY: 'Agent runtime 必須支援必要 protocol feature，例如 TASK_ACK、TASK_RESULT；業務 capability 由 Admin UI/Core 管理，不要求 runtime 自行宣告。',
  AGENT_CAPACITY_AVAILABLE: 'Agent 忙碌或 slots 用完時，暫時不應再接新任務。',
  CAPABILITY_CONTRACT_ELIGIBLE: '這是 Dispatch Eligibility 總檢查。若失敗，請依序檢查 Dispatch Flow Agent assignment、Capability Grant、Operation Profile、Action Grant、Governance、Runtime 與 Capacity。'
};

export function normalizeCode(value?: string): string {
  return value?.trim().replace(/[.-]/g, '_').toUpperCase() ?? '';
}

function fallbackCodeLabel(value?: string): string {
  const normalized = normalizeCode(value);
  if (!normalized) return '-';
  return normalized;
}

export function beginnerCapabilityLabel(capabilityCode?: string): string {
  const normalized = normalizeCode(capabilityCode);
  return capabilityBeginnerLabels[normalized]?.label ?? fallbackCodeLabel(capabilityCode);
}

export function beginnerCapabilityDescription(capabilityCode?: string): string {
  const normalized = normalizeCode(capabilityCode);
  return capabilityBeginnerLabels[normalized]?.description ?? '尚未設定友善說明；請確認 capability policy definition。';
}

export function beginnerStatusLabel(status?: string): string {
  const normalized = normalizeCode(status);
  return statusBeginnerLabels[normalized]?.label ?? policyBeginnerLabels[normalized]?.label ?? fallbackCodeLabel(status);
}

export function beginnerStatusDescription(status?: string): string {
  const normalized = normalizeCode(status);
  return statusBeginnerLabels[normalized]?.description ?? policyBeginnerLabels[normalized]?.description ?? '保留原始狀態碼供工程師追蹤。';
}

export function beginnerCheckLabel(check?: CoreDispatchReadinessCheck): string {
  const key = normalizeCode(check?.key);
  return readinessCheckLabels[key] ?? check?.label ?? key;
}

export function beginnerCheckHint(check?: CoreDispatchReadinessCheck): string | undefined {
  const key = normalizeCode(check?.key);
  return check?.beginnerHint ?? readinessCheckBeginnerHints[key];
}

export function humanizedCodeLabel(code?: string, type?: 'skill' | 'capability' | 'status' | 'policy' | 'operation' | 'taskType' | 'generic'): string {
  const normalized = normalizeCode(code);
  if (!normalized) return '-';
  if (type === 'skill' || type === 'capability') return beginnerCapabilityLabel(normalized);
  if (type === 'status') return beginnerStatusLabel(normalized);
  if (type === 'policy' || type === 'operation') return policyBeginnerLabels[normalized]?.label ?? fallbackCodeLabel(normalized);
  return capabilityBeginnerLabels[normalized]?.label
    ?? statusBeginnerLabels[normalized]?.label
    ?? policyBeginnerLabels[normalized]?.label
    ?? fallbackCodeLabel(normalized);
}

export function humanizedCodeDescription(code?: string, type?: 'skill' | 'capability' | 'status' | 'policy' | 'operation' | 'taskType' | 'generic'): string {
  const normalized = normalizeCode(code);
  if (!normalized) return '沒有可顯示的 code。';
  if (type === 'skill' || type === 'capability') return beginnerCapabilityDescription(normalized);
  if (type === 'status') return beginnerStatusDescription(normalized);
  if (type === 'policy' || type === 'operation') return policyBeginnerLabels[normalized]?.description ?? '保留原始 policy / operation code。';
  return capabilityBeginnerLabels[normalized]?.description
    ?? statusBeginnerLabels[normalized]?.description
    ?? policyBeginnerLabels[normalized]?.description
    ?? '保留原始 code 供工程師追蹤。';
}


export const beginnerSkillLabel = beginnerCapabilityLabel;
export const beginnerSkillDescription = beginnerCapabilityDescription;
