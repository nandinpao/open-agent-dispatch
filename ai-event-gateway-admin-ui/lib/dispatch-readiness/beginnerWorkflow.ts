import type { TaskDispatchDashboardRow } from '@/lib/dashboard/taskDispatchMerge';
import type { CoreAgentCapability, CoreAgentProfile, CoreAgentRuntimeCapabilityItem, CoreAgentRuntimeLoadSnapshot, CoreDispatchReadinessEvaluationResult, CoreDispatchReadinessScenarioTemplate } from '@/lib/types/core';
import { beginnerCapabilityLabel, beginnerStatusLabel, normalizeCode } from '@/lib/dispatch-readiness/labels';

export interface BeginnerWorkflowStep {
  id: string;
  title: string;
  description: string;
  status: 'done' | 'current' | 'blocked' | 'waiting';
  code?: string;
}

export interface BeginnerAction {
  label: string;
  description: string;
  tone: 'primary' | 'safe' | 'warning' | 'neutral';
}

export interface CapabilityMatrixRow {
  capabilityCode: string;
  label: string;
  registryDefined?: boolean;
  governanceApproved?: boolean;
  runtimeReported?: boolean;
  taskRequires: boolean;
  result: 'ready' | 'missing-governance' | 'missing-runtime' | 'not-required' | 'unknown';
  explanation: string;
}

function normalizeList(values?: string[] | null): string[] {
  return Array.from(new Set((values ?? []).map(normalizeCode).filter(Boolean)));
}

function checkStatus(result: CoreDispatchReadinessEvaluationResult | null | undefined, key: string): string {
  return normalizeCode(result?.checks?.find((check) => normalizeCode(check.key) === key)?.status);
}

function checkPassedAny(result: CoreDispatchReadinessEvaluationResult | null | undefined, keys: string[]): boolean {
  return keys.some((key) => checkStatus(result, key) === 'PASS');
}


export function buildDispatchSetupSteps(result?: CoreDispatchReadinessEvaluationResult | null): BeginnerWorkflowStep[] {
  const hasResult = Boolean(result);
  const ready = result?.ready === true;
  const sourceReady = ready || checkPassedAny(result, ['SOURCE_SYSTEM_CONFIGURED', 'SOURCE_SYSTEM_RESOLVED', 'DISPATCH_CONTRACT_RESOLVED']);
  const agentReady = ready || checkPassedAny(result, ['AGENT_APPROVED', 'GOVERNANCE_PROFILE', 'GOVERNANCE_AGENT_APPROVED']);
  const poolReady = ready || checkPassedAny(result, ['DEFAULT_POOL_CONFIGURED', 'AGENT_POOL_CONFIGURED', 'SOURCE_FLOW_POOL_RESOLVED']);
  const memberReady = ready || checkPassedAny(result, ['POOL_HAS_ACTIVE_MEMBER', 'POOL_MEMBER_CONFIGURED', 'HAS_ELIGIBLE_AGENT']);
  const flowReady = ready || checkPassedAny(result, ['SOURCE_FLOW_RESOLVED', 'FLOW_RESOLVED', 'DISPATCH_CONTRACT_RESOLVED']);
  const runtimeReady = ready || checkPassedAny(result, ['RUNTIME_AGENT_ONLINE', 'HAS_ELIGIBLE_AGENT', 'AGENT_CAPACITY_AVAILABLE']);

  return [
    {
      id: 'source-system',
      title: '建立來源系統',
      description: '先建立事件來源。來源名稱由事件資料明確提供，系統不會從 eventType、objectType 或 plantId 推測 ERP、MES 或其他系統。',
      status: !hasResult ? 'current' : sourceReady ? 'done' : 'blocked',
      code: 'SOURCE_SYSTEM_CONFIGURED'
    },
    {
      id: 'agent',
      title: '建立並核准 Agent',
      description: 'Agent 必須存在、已核准且未停用。Capability 只作描述與搜尋參考，不是 Current 派工必要條件。',
      status: !hasResult ? 'waiting' : agentReady ? 'done' : sourceReady ? 'current' : 'waiting',
      code: 'AGENT_APPROVED'
    },
    {
      id: 'agent-pool',
      title: '建立工作池並加入 Agent',
      description: 'Agent Pool 定義候選 Agent 範圍；至少加入一個啟用中的 Pool Member Agent。',
      status: !hasResult ? 'waiting' : poolReady && memberReady ? 'done' : agentReady ? 'current' : 'waiting',
      code: 'AGENT_POOL_CONFIGURED'
    },
    {
      id: 'source-flow',
      title: '建立 Source Flow 並指定預設工作池',
      description: 'Source Flow 決定事件預設進入哪個 Agent Pool；特殊分類規則只處理少數例外。',
      status: !hasResult ? 'waiting' : flowReady && poolReady ? 'done' : poolReady && memberReady ? 'current' : 'waiting',
      code: 'SOURCE_FLOW_RESOLVED'
    },
    {
      id: 'runtime',
      title: '確認工作池中有可接單 Agent',
      description: 'Runtime Eligibility 會檢查連線、容量、Backoff、Credential 與管理狀態。',
      status: !hasResult ? 'waiting' : runtimeReady ? 'done' : flowReady ? 'current' : 'waiting',
      code: 'RUNTIME_AGENT_ONLINE'
    },
    {
      id: 'simulation',
      title: '執行派工檢查或模擬',
      description: '確認命中的 Source Flow、Rule、目標 Agent Pool、候選 Agent 與排除原因，不建立正式 Task。',
      status: !hasResult ? 'waiting' : ready ? 'done' : runtimeReady ? 'current' : 'waiting'
    },
    {
      id: 'real-test',
      title: '傳送真實測試事件',
      description: '設定完成後建立真實 Task，並從 Task 頁確認 Assignment、Delivery、ACK 與 Result。',
      status: ready ? 'current' : 'waiting'
    }
  ];
}

export function summarizeReadinessForBeginner(result?: CoreDispatchReadinessEvaluationResult | null): { title: string; description: string; tone: 'ready' | 'blocked' | 'waiting' } {
  if (!result) {
    return { title: '尚未檢查派工設定', description: '請依序確認來源系統、Agent、Agent Pool、Source Flow 與 Runtime Eligibility。', tone: 'waiting' };
  }
  if (result.ready) {
    return { title: '派工設定已就緒', description: result.beginnerSummary ?? result.summary ?? 'Source Flow、Agent Pool、Pool Member Agent 與 Runtime Eligibility 已通過。', tone: 'ready' };
  }
  const failed = result.checks?.find((check) => normalizeCode(check.status) === 'FAIL');
  return {
    title: '派工設定尚未就緒',
    description: failed?.beginnerHint ?? failed?.message ?? result.beginnerSummary ?? '至少有一個 Source Flow、Agent Pool、Pool Member 或 Runtime Eligibility 條件未通過。',
    tone: 'blocked'
  };
}

export function recommendedBeginnerActions(result?: CoreDispatchReadinessEvaluationResult | null): BeginnerAction[] {
  if (!result) {
    return [{ label: '檢查派工設定', description: '讓 Core 依 Current Source Flow 與 Agent Pool 模型找出缺少的設定。', tone: 'primary' }];
  }
  if (result.ready) {
    return [
      { label: '傳送真實測試事件', description: '建立測試 Task，確認 Assignment → Delivery → ACK → Result。', tone: 'primary' },
      { label: '開啟 Task', description: '從 Task 頁查看完整派工證據與目前狀態。', tone: 'neutral' }
    ];
  }

  const actions: BeginnerAction[] = [];
  const failedKeys = new Set((result.checks ?? []).filter((check) => normalizeCode(check.status) === 'FAIL').map((check) => normalizeCode(check.key)));
  const failed = (...keys: string[]) => keys.some((key) => failedKeys.has(key));

  if (failed('SOURCE_SYSTEM_CONFIGURED', 'SOURCE_SYSTEM_RESOLVED')) {
    actions.push({ label: '建立來源系統', description: '確認事件提供真實 sourceSystem，並建立對應來源系統。', tone: 'safe' });
  }
  if (failed('SOURCE_FLOW_RESOLVED', 'FLOW_RESOLVED', 'DISPATCH_CONTRACT_RESOLVED', 'DISPATCH_RULE_MISSING')) {
    actions.push({ label: '檢查 Source Flow', description: '確認來源系統有啟用中的 Source Flow，並指定 Default Agent Pool。', tone: 'safe' });
  }
  if (failed('DEFAULT_POOL_CONFIGURED', 'AGENT_POOL_CONFIGURED', 'MISSING_AGENT_POOL', 'POOL_HAS_NO_ACTIVE_MEMBER')) {
    actions.push({ label: '檢查 Agent Pool', description: '建立或選擇工作池，並加入至少一個啟用中的 Agent。', tone: 'safe' });
  }
  if (failed('GOVERNANCE_PROFILE', 'AGENT_APPROVED', 'GOVERNANCE_AGENT_APPROVED')) {
    actions.push({ label: '核准 Agent', description: '確認 Agent 已建立、已核准且未被停用或撤銷。', tone: 'safe' });
  }
  if (failed('RUNTIME_AGENT_ONLINE', 'POOL_AGENT_RUNTIME_NOT_FOUND', 'POOL_AGENT_OFFLINE', 'AGENT_CAPACITY_AVAILABLE', 'POOL_AGENT_CAPACITY_FULL', 'POOL_AGENT_BACKOFF')) {
    actions.push({ label: '檢查 Agent Runtime', description: '確認 Pool 成員已連線、有容量、Credential 有效且不在 Backoff。', tone: 'warning' });
  }
  if (failed('CAPABILITY_POLICY_DEFINED', 'SKILL_DEFINED', 'GOVERNANCE_APPROVED_CAPABILITY', 'TASK_REQUIRES_CAPABILITY', 'RUNTIME_REPORTED_CAPABILITY')) {
    actions.push({ label: '改看 Current 派工設定', description: 'Capability 與舊 readiness 欄位只作參考；請以 Source Flow、Agent Pool、Pool Member Agent 與 Runtime Evidence 為準。', tone: 'neutral' });
  }
  if (actions.length === 0) {
    actions.push({ label: '開啟技術詳細資訊', description: '查看原始檢查碼與 Routing Evidence，確認尚未分類的失敗條件。', tone: 'neutral' });
  }
  return actions;
}

export function scenarioToCapability(scenario?: CoreDispatchReadinessScenarioTemplate | null): string {
  const fromRequired = normalizeList(scenario?.requiredCapabilities)[0];
  if (fromRequired) return fromRequired;
  const fromCapability = normalizeCode(scenario?.skillCode);
  return fromCapability || 'INCIDENT_ANALYSIS';
}

export function buildCapabilityMatrix(input: {
  taskRequiredCapabilities?: string[] | null;
  profileCapabilities?: CoreAgentCapability[] | null;
  runtimeCapabilities?: CoreAgentRuntimeCapabilityItem[] | null;
  runtimeLoad?: CoreAgentRuntimeLoadSnapshot | null;
  capabilityCodes?: string[] | null;
  skillCodes?: string[] | null;
}): CapabilityMatrixRow[] {
  const taskRequired = new Set(normalizeList(input.taskRequiredCapabilities));
  const governanceKnown = Array.isArray(input.profileCapabilities);
  const runtimeKnown = Array.isArray(input.runtimeCapabilities);
  const registryKnown = Array.isArray(input.capabilityCodes) || Array.isArray(input.skillCodes);
  const governance = new Set((input.profileCapabilities ?? []).filter((item) => item.enabled !== false).map((item) => normalizeCode(item.capabilityCode)).filter(Boolean));
  const runtime = new Set((input.runtimeCapabilities ?? []).map((item) => normalizeCode(item.capabilityValue)).filter(Boolean));
  const registry = new Set([...normalizeList(input.capabilityCodes), ...normalizeList(input.skillCodes)]);
  const all = Array.from(new Set([...taskRequired, ...governance, ...runtime, ...registry])).sort();

  return all.map((capabilityCode) => {
    const taskRequires = taskRequired.has(capabilityCode);
    const governanceApproved = governanceKnown ? governance.has(capabilityCode) : undefined;
    const runtimeReported = runtimeKnown ? runtime.has(capabilityCode) : undefined;
    const registryDefined = registryKnown ? registry.has(capabilityCode) : undefined;
    let result: CapabilityMatrixRow['result'] = 'unknown';
    let explanation = 'This capability appears in only some data sources. Use Dispatch Flow evidence to align task requirement, Core approval, runtime report, and dispatch rule.';
    if (taskRequires && governanceApproved === true) {
      result = 'ready';
      explanation = 'Task requires it and Admin UI/Core approved it for this Agent. Runtime capability observation is optional and does not block dispatch.';
    } else if (taskRequires && governanceApproved === false) {
      result = 'missing-governance';
      explanation = 'Task requires this capability, but Core has not approved it for the Agent.';
    } else if (taskRequires && governanceApproved === true && runtimeReported === false) {
      result = 'ready';
      explanation = 'Capability is approved in Core. Runtime did not observe it, but this is diagnostic only and does not block dispatch.';
    } else if (!taskRequires) {
      result = 'not-required';
      explanation = 'Core policy or runtime may contain this capability, but the current task does not require it.';
    } else if (taskRequires && (governanceApproved === undefined || runtimeReported === undefined)) {
      result = 'unknown';
      explanation = 'Task requires this capability, but this page is missing Core approval or runtime data. Check Agent Detail.';
    }
    return {
      capabilityCode,
      label: beginnerCapabilityLabel(capabilityCode),
      registryDefined,
      governanceApproved,
      runtimeReported,
      taskRequires,
      result,
      explanation
    };
  });
}

export interface DispatchReadinessChainStep {
  key: string;
  title: string;
  description: string;
  status: 'pass' | 'fail' | 'warn' | 'info' | 'waiting';
  rawStatus?: string;
  message?: string;
  beginnerHint?: string;
  fixLabel?: string;
}

const readinessChainCatalog: Array<{ key: string; title: string; description: string }> = [
  { key: 'DISPATCH_CONTRACT_RESOLVED', title: 'Source Flow 已解析', description: 'Current 派工必須解析到啟用中的 Source Flow 與 Default Agent Pool；特殊 Rule 只處理例外。' },
  { key: 'GOVERNANCE_PROFILE', title: 'Agent 已核准', description: '候選 Agent 必須存在、已核准、enabled，且未被撤銷或暫停。' },
  { key: 'RUNTIME_AGENT_ONLINE', title: 'Agent Runtime 在線', description: 'Pool Member Agent 必須實際連上 Gateway，才能進入 Runtime Eligibility。' },
  { key: 'AGENT_CAPACITY_AVAILABLE', title: 'Agent 容量可用', description: 'Agent 必須有可用 slot，且不在 Backoff 或 draining 狀態。' },
  { key: 'CAPABILITY_POLICY_DEFINED', title: 'Legacy readiness 參考', description: '舊 Capability／Profile readiness 僅保留相容診斷；Current 派工以 Source Flow、Agent Pool 與 Runtime Evidence 為準。' },
  { key: 'GOVERNANCE_APPROVED_CAPABILITY', title: 'Capability 核准參考', description: 'Capability 是 Agent 描述與治理資訊，不是 Current Agent Pool 派工必要條件。' },
  { key: 'RUNTIME_REPORTED_CAPABILITY', title: 'Runtime Capability 觀測參考', description: 'Runtime capability observation 只作診斷；TASK_ACK、TASK_RESULT 等 transport feature 仍由 Runtime Evidence 驗證。' },
  { key: 'TASK_REQUIRES_CAPABILITY', title: 'Task Capability Metadata', description: 'requiredCapabilities 僅作歷史與查詢參考，不應取代 Source Flow 與 Agent Pool。' },
  { key: 'SKILL_AWARE_ROUTING_ELIGIBLE', title: 'Runtime Eligibility 已通過', description: 'Source Flow、Agent Pool、Agent 管理狀態、Runtime、Credential、Capacity 與 Backoff 條件均通過後才可派工。' }
];

function mapReadinessStatus(status?: string): DispatchReadinessChainStep['status'] {
  const normalized = normalizeCode(status);
  if (normalized === 'PASS') return 'pass';
  if (normalized === 'FAIL') return 'fail';
  if (normalized === 'WARN') return 'warn';
  if (normalized === 'INFO') return 'info';
  return 'waiting';
}

export function buildDispatchReadinessChain(result?: CoreDispatchReadinessEvaluationResult | null): DispatchReadinessChainStep[] {
  return readinessChainCatalog.map((catalog) => {
    const check = result?.checks?.find((item) => normalizeCode(item.key) === catalog.key);
    return {
      key: catalog.key,
      title: catalog.title,
      description: catalog.description,
      status: mapReadinessStatus(check?.status),
      rawStatus: check?.status,
      message: check?.message,
      beginnerHint: check?.beginnerHint,
      fixLabel: check?.fixAction?.label
    };
  });
}

export function readinessChainSummary(result?: CoreDispatchReadinessEvaluationResult | null): { title: string; description: string; tone: 'ready' | 'blocked' | 'waiting' } {
  if (!result) return { title: '尚未建立派工判斷鏈', description: '請先檢查 Source Flow、Default Agent Pool、Pool Member Agent 與 Runtime Eligibility。', tone: 'waiting' };
  if (result.ready) return { title: '派工判斷鏈全部通過', description: 'Source Flow、Agent Pool、Agent 管理狀態、Runtime 與 Capacity 已通過。', tone: 'ready' };
  const failed = buildDispatchReadinessChain(result).find((step) => step.status === 'fail');
  return { title: '派工判斷鏈尚未通過', description: failed?.beginnerHint ?? failed?.message ?? '至少有一個 Current 派工條件未通過。請依序檢查 Source Flow、Agent Pool、Pool Member Agent、Runtime、Credential、Capacity 與 Backoff。', tone: 'blocked' };
}

export function taskBeginnerHeadline(row: TaskDispatchDashboardRow): { title: string; nextAction: string; tone: 'ok' | 'waiting' | 'blocked' | 'done' } {
  const task = row.task;
  const status = normalizeCode(task.status);
  const dispatch = normalizeCode(task.dispatchExecutionStatus ?? task.dispatchStatus);
  const delivery = normalizeCode(task.dispatchDeliveryStatus);
  const callback = normalizeCode(task.callbackStatus);

  if (['COMPLETED', 'SUCCEEDED'].includes(status) || dispatch === 'COMPLETED') {
    return { title: 'Agent 已完成任務', nextAction: '查看 Agent 摘要、Issue History 或工程師詳情。', tone: 'done' };
  }
  if (['CANCELLED', 'CANCELED'].includes(status)) {
    return { title: '任務已取消', nextAction: '此 Task 已由系統或 Operator 結案，不需要再派工；如需追蹤請開啟 Timeline / Issue。', tone: 'done' };
  }
  if (['FAILED', 'TIMEOUT', 'TIMED_OUT', 'DEAD_LETTER'].includes(status) || task.failureReason) {
    return { title: '任務處理失敗', nextAction: task.failureReason ?? task.nextAction ?? '查看失敗原因並決定是否重試。', tone: 'blocked' };
  }
  if (['ESCALATED', 'ORPHANED', 'RECONCILING'].includes(status)) {
    return { title: '任務需要人工處理', nextAction: task.nextAction ?? task.lifecycleReason ?? '查看 Failure Queue 並決定 Retry、Escalate 或 DLQ。', tone: 'blocked' };
  }
  if (task.blockedReason) {
    return { title: '任務派工被阻擋', nextAction: task.nextAction ?? task.blockedReason ?? '修正派工阻擋條件後再重試。', tone: 'blocked' };
  }
  if (task.dispatchWaitReason) {
    return { title: '等待下一次派工重試', nextAction: task.nextAction ?? task.dispatchWaitReason, tone: 'waiting' };
  }
  if (callback === 'CALLBACK_RECEIVED' || status === 'RUNNING' || dispatch === 'RUNNING') {
    return { title: 'Agent 已回應，等待最終結果', nextAction: '等待 RESULT / ERROR callback，或查看 Agent runtime 是否持續回報。', tone: 'ok' };
  }
  if (delivery === 'DELIVERED_TO_GATEWAY' || ['DELIVERED', 'DISPATCHED', 'ACKED'].includes(dispatch)) {
    return { title: 'Gateway 已接受派工，等待 Agent callback', nextAction: '如果 Agent 仍顯示 IDLE，表示 runtime 尚未回報 RUNNING；請看 callback relay。', tone: 'waiting' };
  }
  if (task.assignedAgentId) {
    return { title: '已選中 Agent，等待派工送出', nextAction: task.nextAction ?? '等待 Core dispatch worker 或手動 retry。', tone: 'waiting' };
  }
  return { title: '等待可派工 Agent', nextAction: '檢查 Source Flow、目標 Agent Pool、Pool Member Agent、Runtime、容量與 Backoff。', tone: 'waiting' };
}

export function agentBeginnerHeadline(profile?: CoreAgentProfile | null, load?: CoreAgentRuntimeLoadSnapshot | null): { title: string; description: string; tone: 'ok' | 'blocked' | 'waiting' } {
  if (!profile) return { title: 'Core 尚未建立 Agent Profile', description: '此 Agent 可能只是 runtime observation，需先完成 enrollment / approval。', tone: 'blocked' };
  if (profile.enabled === false) return { title: 'Agent 已停用，不能派工', description: '請先 Enable Agent，再檢查能力授權與 runtime。', tone: 'blocked' };
  if (normalizeCode(profile.approvalStatus) !== 'APPROVED') return { title: 'Agent 尚未核准', description: `目前治理狀態為 ${beginnerStatusLabel(profile.approvalStatus)}。`, tone: 'blocked' };
  if (normalizeCode(profile.riskStatus) && normalizeCode(profile.riskStatus) !== 'NORMAL') return { title: 'Agent 風險狀態異常', description: `目前風險狀態為 ${beginnerStatusLabel(profile.riskStatus)}。`, tone: 'blocked' };
  if (load && normalizeCode(load.status) && !['IDLE', 'READY', 'RUNNING'].includes(normalizeCode(load.status))) {
    return { title: 'Agent runtime 狀態需要確認', description: `Runtime load status=${load.status}，請確認是否可接新任務。`, tone: 'waiting' };
  }
  return { title: 'Agent 身份治理已通過', description: '仍需確認此 Agent 已加入目標 Agent Pool，且 Runtime、容量與 Credential 狀態可用。', tone: 'ok' };
}

export function beginnerToneClass(tone: string): string {
  if (tone === 'ready' || tone === 'ok' || tone === 'done' || tone === 'safe') return 'border-emerald-200 bg-emerald-50 text-emerald-900';
  if (tone === 'blocked' || tone === 'warning') return 'border-amber-200 bg-amber-50 text-amber-900';
  return 'border-slate-200 bg-slate-50 text-slate-800';
}

export interface DecisionSummary {
  title: string;
  subtitle: string;
  statusCode: string;
  statusLabel: string;
  blockingReason?: string;
  nextAction: string;
  tone: 'success' | 'warning' | 'danger' | 'info' | 'neutral';
}

export function taskDecisionSummary(row: TaskDispatchDashboardRow): DecisionSummary {
  const headline = taskBeginnerHeadline(row);
  const task = row.task;
  const statusCode = task.callbackStatus ?? task.dispatchDeliveryStatus ?? task.dispatchExecutionStatus ?? task.dispatchStatus ?? task.status ?? 'UNKNOWN';
  if (headline.tone === 'done') {
    return {
      title: headline.title,
      subtitle: '此 Task 已進入完成狀態。請查看 Agent 結果、Issue sync 或 Audit History。',
      statusCode,
      statusLabel: '任務完成',
      nextAction: '查看 Agent 結果或 Issue History。',
      tone: 'success'
    };
  }
  if (headline.tone === 'blocked') {
    return {
      title: headline.title,
      subtitle: '系統偵測到阻擋或失敗狀態，需要 Operator 判斷重試、取消或改派。',
      statusCode,
      statusLabel: '需要處理',
      blockingReason: task.failureReason ?? task.blockedReason ?? task.lifecycleReason ?? 'Task 或 dispatch 進入非正常狀態。',
      nextAction: task.nextAction ?? '查看 Recovery / Routing / Timeline，決定 Retry、Reassign 或 Cancel。',
      tone: 'danger'
    };
  }
  if (headline.tone === 'waiting') {
    return {
      title: headline.title,
      subtitle: '系統仍在等待下一個 runtime event。請先確認 Gateway、Agent callback 與 runtime 連線狀態。',
      statusCode,
      statusLabel: '等待中',
      blockingReason: task.dispatchWaitReason ?? task.dispatchRetryReason ?? task.lifecycleReason ?? '目前沒有失敗，只是尚未收到下一個派工或 callback 訊號。',
      nextAction: task.nextAction ?? headline.nextAction,
      tone: 'warning'
    };
  }
  return {
    title: headline.title,
    subtitle: '任務已進入派工或執行流程。',
    statusCode,
    statusLabel: '進行中',
    blockingReason: task.lifecycleReason ?? '未偵測到明確阻擋。',
    nextAction: task.nextAction ?? headline.nextAction,
    tone: 'info'
  };
}

export function agentDecisionSummary(profile?: CoreAgentProfile | null, load?: CoreAgentRuntimeLoadSnapshot | null): DecisionSummary {
  const headline = agentBeginnerHeadline(profile, load);
  const statusCode = load?.status ?? profile?.approvalStatus ?? 'UNKNOWN';
  if (headline.tone === 'blocked') {
    return {
      title: headline.title,
      subtitle: '此 Agent 目前不應被派工。請先修正治理、憑證或風險狀態。',
      statusCode,
      statusLabel: '不可派工',
      blockingReason: headline.description,
      nextAction: profile ? '檢查 Approval、Enabled、Risk、Credential 與 Agent Pool membership。' : '建立或核准 Agent。',
      tone: 'danger'
    };
  }
  if (headline.tone === 'waiting') {
    return {
      title: headline.title,
      subtitle: '此 Agent 可能可用，但需要先確認 runtime workload / backoff / slots。',
      statusCode,
      statusLabel: '需要確認',
      blockingReason: headline.description,
      nextAction: '刷新 Runtime、檢查 session、capacity 與必要 protocol feature。',
      tone: 'warning'
    };
  }
  return {
    title: headline.title,
    subtitle: '身份治理狀態正常；仍需確認 Agent Pool membership、Runtime、Capacity 與 Credential。',
    statusCode,
    statusLabel: '治理可派工',
    blockingReason: headline.description,
    nextAction: '查看此 Agent 所屬工作池與 Runtime Eligibility，確認是否可接指定 Task。',
    tone: 'success'
  };
}

export function capabilityDecisionSummary(capability?: { capabilityCode?: string; enabled?: boolean; riskLevel?: string; requiresHumanApproval?: boolean; maskingRequired?: boolean } | null): DecisionSummary {
  const code = normalizeCode(capability?.capabilityCode) || 'UNKNOWN';
  if (!capability?.capabilityCode) {
    return {
      title: '尚未選擇 Capability 參考資料',
      subtitle: 'Capability 用於 Agent 描述、搜尋與治理資訊，不是 Current Source Flow／Agent Pool 派工 Gate。',
      statusCode: 'MISSING',
      statusLabel: '尚未選擇',
      blockingReason: '沒有 Capability 參考資料不會阻擋 Agent Pool 派工。',
      nextAction: '一般派工請前往派工設定，檢查 Source Flow、Agent Pool 與 Pool Member Agent。',
      tone: 'neutral'
    };
  }
  if (capability.enabled === false) {
    return {
      title: `${beginnerCapabilityLabel(code)} 參考資料已停用`,
      subtitle: '停用只影響 Capability 查詢與治理呈現，不應改變 Current Pool-first 派工結果。',
      statusCode: 'DISABLED',
      statusLabel: '參考資料停用',
      blockingReason: 'Capability reference enabled=false；此狀態不是 Current routing blocker。',
      nextAction: '如仍需顯示或搜尋此能力，可重新啟用 Capability 參考資料。',
      tone: 'warning'
    };
  }
  return {
    title: `${beginnerCapabilityLabel(code)} Capability 參考資料`,
    subtitle: '此資訊可協助管理員理解與搜尋 Agent，但不取代 Agent Pool membership 或 Runtime Eligibility。',
    statusCode: capability.riskLevel ?? 'REFERENCE',
    statusLabel: '參考資料可用',
    blockingReason: capability.requiresHumanApproval ? '此 Capability 的治理變更需要人工核准，但不會隱性阻擋 Current routing。' : 'Capability 不參與第一版 Agent Pool 派工 Gate。',
    nextAction: '需要調整派工時，請修改 Source Flow、Agent Pool 或 Pool Member Agent。',
    tone: capability.requiresHumanApproval || capability.maskingRequired ? 'warning' : 'info'
  };
}

export type DispatchRecipeLane = 'needs-action' | 'waiting' | 'done' | 'all';

export interface EntityRelationshipStep {
  id: 'source-system' | 'source-flow' | 'agent-pool' | 'agent' | 'runtime' | 'task' | 'result' | 'capability-reference' | 'identity' | 'credential' | 'dispatch';
  title: string;
  description: string;
  status: 'done' | 'current' | 'blocked' | 'waiting' | 'info';
  code?: string;
  href?: string;
}

function hasAny(value?: string | null): boolean {
  return Boolean(normalizeCode(value ?? undefined));
}

export function taskQueueLane(row: TaskDispatchDashboardRow): DispatchRecipeLane {
  const summary = taskDecisionSummary(row);
  const status = normalizeCode(row.task.status);
  if (summary.tone === 'success' || ['COMPLETED', 'CANCELLED', 'CANCELED', 'SUCCEEDED', 'RESOLVED'].includes(status)) return 'done';
  if (summary.tone === 'danger') return 'needs-action';
  if (summary.tone === 'warning') return 'waiting';
  return 'waiting';
}

export function taskQueueLaneLabel(lane: DispatchRecipeLane): string {
  if (lane === 'needs-action') return '需要處理';
  if (lane === 'waiting') return '等待中';
  if (lane === 'done') return '已完成';
  return '全部';
}

export function buildTaskRelationshipSteps(row?: TaskDispatchDashboardRow | null): EntityRelationshipStep[] {
  const task = row?.task;
  const hasTask = Boolean(task?.taskId);
  const hasFlow = Boolean(task?.matchedFlowId);
  const poolId = task?.targetPoolId ?? task?.assignedPoolId;
  const hasPool = Boolean(poolId);
  const hasAgent = Boolean(task?.assignedAgentId);
  const delivered = hasAny(task?.dispatchDeliveryStatus) || hasAny(task?.dispatchExecutionStatus) || hasAny(task?.dispatchStatus);
  const callbackReceived = normalizeCode(task?.callbackStatus) === 'CALLBACK_RECEIVED' || ['COMPLETED', 'SUCCEEDED'].includes(normalizeCode(task?.status));
  const failed = ['FAILED', 'TIMEOUT', 'TIMED_OUT', 'DEAD_LETTER'].includes(normalizeCode(task?.status)) || Boolean(task?.failureReason);

  return [
    {
      id: 'source-flow',
      title: 'Source Flow',
      description: hasFlow ? `已命中 Source Flow：${task?.matchedFlowId}${task?.matchedRuleId ? `；Rule：${task.matchedRuleId}` : '；使用預設工作池或無 Rule override'}。` : '尚未取得 Source Flow 證據；請確認 sourceSystem 與啟用中的 Flow。',
      status: hasFlow ? 'done' : hasTask ? 'current' : 'waiting',
      code: task?.matchedFlowId,
      href: task?.matchedFlowId ? `/dispatch-flows?flowId=${encodeURIComponent(task.matchedFlowId)}` : '/dispatch-flows'
    },
    {
      id: 'agent-pool',
      title: 'Agent Pool',
      description: hasPool ? `目標工作池：${poolId}；Pool Member 數：${task?.poolMemberCount ?? '未回傳'}。` : '尚未解析目標 Agent Pool。',
      status: hasPool ? 'done' : hasFlow ? 'current' : 'waiting',
      code: poolId,
      href: '/dispatch-flows'
    },
    {
      id: 'agent',
      title: 'Pool Member Agent',
      description: hasAgent ? `已選中 ${task?.assignedAgentId}；選擇應以 Pool membership 與 Runtime Eligibility 證據為準。` : `尚未選中 Agent；目前 eligible=${task?.eligibleAgentCount ?? 0}。`,
      status: hasAgent ? 'done' : hasPool ? 'current' : 'waiting',
      code: task?.assignedAgentId,
      href: task?.assignedAgentId ? `/agents/${encodeURIComponent(task.assignedAgentId)}` : '/agents'
    },
    {
      id: 'runtime',
      title: 'Runtime / Delivery',
      description: delivered ? 'Gateway 已觀測到派工 delivery；下一步通常是等待 Agent ACK 與 Result。' : '尚未看到 Delivery，請檢查 Agent 連線、容量、Backoff、Credential 與 dispatch worker。',
      status: failed ? 'blocked' : delivered ? 'done' : hasAgent ? 'current' : 'waiting',
      code: task?.dispatchDeliveryStatus ?? task?.dispatchExecutionStatus ?? task?.dispatchStatus
    },
    {
      id: 'task',
      title: 'Task',
      description: hasTask ? 'Core Task 已建立，狀態以 Core runtime-view 為準。' : '尚未建立 Task。',
      status: failed ? 'blocked' : hasTask ? 'done' : 'waiting',
      code: task?.status,
      href: task?.taskId ? `/tasks/${encodeURIComponent(task.taskId)}` : '/tasks'
    },
    {
      id: 'result',
      title: 'Result / Issue',
      description: callbackReceived ? '已收到 callback，可查看 Agent Result 與 Issue sync。' : failed ? '任務失敗或被阻擋，請查看 blocker 與 recovery evidence。' : '尚未收到 Agent RESULT／ERROR callback。',
      status: callbackReceived ? 'done' : failed ? 'blocked' : hasTask ? 'current' : 'waiting',
      code: task?.callbackStatus
    }
  ];
}

export function buildCapabilityRelationshipSteps(capability?: { capabilityCode?: string; enabled?: boolean } | null): EntityRelationshipStep[] {
  const code = normalizeCode(capability?.capabilityCode);
  const enabled = capability?.enabled !== false;
  return [
    { id: 'capability-reference', title: 'Capability 參考資料', description: code ? `${beginnerCapabilityLabel(code)} 可供查詢與治理參考。` : '尚未選擇 Capability；這不會阻擋 Current Agent Pool 派工。', status: code ? enabled ? 'done' : 'info' : 'current', code },
    { id: 'source-flow', title: 'Source Flow', description: '正式派工入口是 Source Flow 與 Default／Rule Target Agent Pool。', status: 'info', href: '/dispatch-flows' },
    { id: 'agent-pool', title: 'Agent Pool', description: 'Pool membership 決定候選 Agent 範圍；Capability 不會自動建立 Pool membership。', status: 'info', href: '/dispatch-flows' },
    { id: 'runtime', title: 'Runtime Eligibility', description: '實際可接單狀態由連線、容量、Backoff、Credential 與管理狀態決定。', status: 'info' },
    { id: 'result', title: 'Task Evidence', description: '從 Task 查看 Flow、Pool、Agent、Delivery、ACK 與 Result 證據。', status: 'waiting', href: '/tasks' }
  ];
}

export function buildAgentRelationshipSteps(profile?: CoreAgentProfile | null, load?: CoreAgentRuntimeLoadSnapshot | null): EntityRelationshipStep[] {
  const approved = normalizeCode(profile?.approvalStatus) === 'APPROVED' && profile?.enabled !== false;
  const runtimeStatus = normalizeCode(load?.status);
  const runtimeReady = Boolean(load) && !['OFFLINE', 'DISCONNECTED', 'AUTH_DENIED', 'REVOKED'].includes(runtimeStatus);
  return [
    { id: 'agent', title: 'Agent 管理狀態', description: profile ? approved ? 'Core 已核准此 Agent，且 Agent 未停用。' : 'Agent 尚未核准、已停用或不可派工。' : 'Core 尚未建立 Agent。', status: profile ? approved ? 'done' : 'blocked' : 'current', code: profile?.approvalStatus, href: '/agents' },
    { id: 'agent-pool', title: 'Agent Pool Membership', description: 'Agent 必須被明確加入目標 Agent Pool；Capability 不會自動把 Agent 加入工作池。', status: approved ? 'current' : 'waiting', href: '/dispatch-flows' },
    { id: 'runtime', title: 'Runtime Eligibility', description: runtimeReady ? 'Runtime snapshot 可用；仍需確認容量、Backoff 與 Credential。' : 'Runtime 尚未可用或需要確認連線與授權。', status: runtimeReady ? 'done' : approved ? 'current' : 'waiting', code: load?.status },
    { id: 'source-flow', title: 'Source Flow', description: 'Source Flow 與 Rule 決定 Task 進入哪個 Agent Pool。', status: approved && runtimeReady ? 'info' : 'waiting', href: '/dispatch-flows' },
    { id: 'result', title: 'Task Evidence', description: '派工後從 Task 查看 Assignment、Delivery、ACK 與 Result。', status: 'waiting', href: '/tasks' }
  ];
}

export function buildRecipeRelationshipSteps(options?: { scenarioCapability?: string; agentId?: string; readinessReady?: boolean; readinessChecked?: boolean }): EntityRelationshipStep[] {
  const capability = normalizeCode(options?.scenarioCapability);
  const hasAgent = Boolean(options?.agentId);
  const checked = Boolean(options?.readinessChecked);
  const ready = Boolean(options?.readinessReady);
  return [
    { id: 'source-system', title: '來源系統', description: '測試事件必須提供真實 sourceSystem；系統不會依事件內容推測來源。', status: 'done' },
    { id: 'source-flow', title: 'Source Flow / Agent Pool', description: '測試會解析 Source Flow、Rule override 或 Default Pool。', status: checked ? ready ? 'done' : 'blocked' : 'current', href: '/dispatch-flows' },
    { id: 'agent', title: 'Pool Member Agent', description: hasAgent ? `同時查看指定 Agent ${options?.agentId} 的管理與 Runtime 狀態。` : '未指定單一 Agent時，由 Agent Pool 候選與 selection strategy 決定。', status: hasAgent ? 'done' : 'info', code: options?.agentId, href: '/agents' },
    { id: 'capability-reference', title: 'Capability 標籤（參考）', description: capability ? `${beginnerCapabilityLabel(capability)} 僅作搜尋與診斷參考。` : '此測試未提供 Capability 標籤；不影響 Agent Pool 派工。', status: 'info', code: capability },
    { id: 'runtime', title: 'Runtime Eligibility', description: checked ? '檢查 Agent 連線、容量、Backoff、Credential 與管理狀態。' : '執行檢查後才會取得 Runtime Eligibility。', status: checked ? ready ? 'done' : 'blocked' : 'waiting' },
    { id: 'task', title: '真實測試事件', description: ready ? '設定已就緒，可以建立真實 Task。' : '設定通過前先不要建立真實 Task。', status: ready ? 'current' : 'waiting', href: '/tasks' }
  ];
}

// Compatibility exports for renamed advanced screens.
// These wrappers preserve older imports while keeping Capability reference-only in Current routing.
export function skillDecisionSummary(skill?: { skillCode?: string; enabled?: boolean; riskLevel?: string; requiresHumanApproval?: boolean; maskingRequired?: boolean } | null): DecisionSummary {
  return capabilityDecisionSummary(skill ? { capabilityCode: skill.skillCode, enabled: skill.enabled, riskLevel: skill.riskLevel, requiresHumanApproval: skill.requiresHumanApproval, maskingRequired: skill.maskingRequired } : null);
}

export function buildSkillRelationshipSteps(skill?: { skillCode?: string; enabled?: boolean } | null): EntityRelationshipStep[] {
  return buildCapabilityRelationshipSteps(skill ? { capabilityCode: skill.skillCode, enabled: skill.enabled } : null);
}
