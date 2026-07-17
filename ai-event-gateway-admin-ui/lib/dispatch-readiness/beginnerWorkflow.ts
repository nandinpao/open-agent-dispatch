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

export function buildDispatchSetupSteps(result?: CoreDispatchReadinessEvaluationResult | null): BeginnerWorkflowStep[] {
  const hasResult = Boolean(result);
  const capabilityPolicyOk = checkStatus(result, 'CAPABILITY_POLICY_DEFINED') === 'PASS' || checkStatus(result, 'SKILL_DEFINED') === 'PASS';
  const governanceOk = checkStatus(result, 'GOVERNANCE_APPROVED_CAPABILITY') === 'PASS';
  const runtimeOk = checkStatus(result, 'RUNTIME_AGENT_ONLINE') === 'PASS';
  const taskOk = checkStatus(result, 'TASK_REQUIRES_CAPABILITY') === 'PASS' && checkStatus(result, 'DISPATCH_CONTRACT_RESOLVED') === 'PASS';
  const ready = result?.ready === true;

  return [
    {
      id: 'scenario',
      title: 'Choose a task scenario',
      description: 'Choose the business scenario first, such as MES alarm triage, ERP review, HR event, or general incident analysis.',
      status: 'done'
    },
    {
      id: 'capability-policy',
      title: 'Confirm capability policy and Dispatch Flow Agent selection',
      description: 'The task must resolve to an active Agent Dispatch Flow Agent assignment and dispatch rule. Only Flow-owned Required Capability values are used.',
      status: !hasResult ? 'waiting' : capabilityPolicyOk ? 'done' : 'blocked',
      code: 'CAPABILITY_POLICY_DEFINED'
    },
    {
      id: 'governance',
      title: 'Approve Agent service Flow Agent approval',
      description: 'An Agent cannot dispatch only by self-declared capability. Core must approve the Agent Dispatch Flow Agent assignment / Flow Agent approval.',
      status: !hasResult ? 'waiting' : governanceOk ? 'done' : capabilityPolicyOk ? 'current' : 'waiting',
      code: 'GOVERNANCE_APPROVED_CAPABILITY'
    },
    {
      id: 'runtime',
      title: 'Confirm runtime is online',
      description: 'The runtime must be online and have capacity. Business capabilities are approved in Admin UI/Core, not self-reported by the Agent.',
      status: !hasResult ? 'waiting' : runtimeOk ? 'done' : governanceOk ? 'current' : 'waiting',
      code: 'RUNTIME_REPORTED_CAPABILITY'
    },
    {
      id: 'task',
      title: 'Confirm Task Requirement',
      description: 'The Task Requirement must map to Agent Dispatch Flow Agent assignment and capabilities, not alternate capability wording.',
      status: !hasResult ? 'waiting' : taskOk ? 'done' : runtimeOk ? 'current' : 'waiting',
      code: 'TASK_REQUIRES_CAPABILITY'
    },
    {
      id: 'dispatch',
      title: 'Send test task and inspect result',
      description: 'After readiness passes, send a test event and inspect progress/callback from the Tasks page.',
      status: !hasResult ? 'waiting' : ready ? 'current' : 'waiting'
    }
  ];
}

export function summarizeReadinessForBeginner(result?: CoreDispatchReadinessEvaluationResult | null): { title: string; description: string; tone: 'ready' | 'blocked' | 'waiting' } {
  if (!result) {
    return { title: 'Dispatch readiness has not been checked', description: 'Choose a task scenario and Agent, then run the readiness check.', tone: 'waiting' };
  }
  if (result.ready) {
    return { title: 'Ready to dispatch', description: result.beginnerSummary ?? result.summary ?? 'Task requirement, Core Flow Agent approval, runtime capability facts, and capacity are aligned.', tone: 'ready' };
  }
  const failed = result.checks?.find((check) => normalizeCode(check.status) === 'FAIL');
  return {
    title: 'Blocked from dispatch',
    description: failed?.beginnerHint ?? failed?.message ?? result.beginnerSummary ?? 'At least one pre-dispatch check failed.',
    tone: 'blocked'
  };
}

export function recommendedBeginnerActions(result?: CoreDispatchReadinessEvaluationResult | null): BeginnerAction[] {
  if (!result) {
    return [{ label: 'Run dispatch readiness check', description: 'Let Core identify the missing readiness step first.', tone: 'primary' }];
  }
  if (result.ready) {
    return [
      { label: 'Send test event', description: 'Create a test task and confirm Task → Agent → Callback is working.', tone: 'primary' },
      { label: 'Open Tasks', description: 'After sending the task, inspect the current stage from Tasks.', tone: 'neutral' }
    ];
  }
  const actions: BeginnerAction[] = [];
  const failedKeys = new Set((result.checks ?? []).filter((check) => normalizeCode(check.status) === 'FAIL').map((check) => normalizeCode(check.key)));
  if (failedKeys.has('CAPABILITY_POLICY_DEFINED') || failedKeys.has('SKILL_DEFINED')) actions.push({ label: 'Review Agent Dispatch Flow Agent assignment, Dispatch Rule, and Runtime Binding', description: 'Confirm this task type has an approved Agent Dispatch Flow Agent assignment, active Dispatch Rule, and ACTIVE Runtime Binding. Runtime online status alone is not dispatch authority.', tone: 'safe' });
  if (failedKeys.has('GOVERNANCE_APPROVED_CAPABILITY') || failedKeys.has('GOVERNANCE_PROFILE')) actions.push({ label: 'Approve Agent service Flow Agent approval', description: 'Assign and approve the Agent Dispatch Flow Agent assignment. Runtime self-reporting is not enough for Core dispatch authority.', tone: 'safe' });
  if (failedKeys.has('RUNTIME_AGENT_ONLINE') || failedKeys.has('RUNTIME_REPORTED_CAPABILITY')) actions.push({ label: 'Check Agent runtime capabilities', description: 'Confirm the Agent is connected. Capabilities are approved in Admin UI/Core and are not required in the runtime startup environment.', tone: 'warning' });
  if (failedKeys.has('TASK_REQUIRES_CAPABILITY') || failedKeys.has('DISPATCH_CONTRACT_RESOLVED')) actions.push({ label: 'Review Task Requirement', description: 'Confirm the test payload/recipe produces the expected task type, tool policy, and required Dispatch Flow Agent selection.', tone: 'warning' });
  if (actions.length === 0) actions.push({ label: 'Open developer details', description: 'Expand evidence/raw response to inspect the advanced failing condition.', tone: 'neutral' });
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
  { key: 'TASK_REQUIRES_CAPABILITY', title: 'Task Requirement resolved', description: 'Task 必須先解析出任務類型、工具政策與後台派工需求。' },
  { key: 'CAPABILITY_POLICY_DEFINED', title: 'Agent Dispatch Flow Agent assignment, Dispatch Rule, and Runtime Binding are active', description: 'Core must resolve an approved Agent Dispatch Flow Agent assignment, an active Dispatch Rule, and an ACTIVE Runtime Binding; runtime online status alone is not dispatch authority.' },
  { key: 'DISPATCH_CONTRACT_RESOLVED', title: 'Dispatch contract resolved', description: 'Task 需求必須能解析成 required profile、runtime feature、tool policy 與風險限制。' },
  { key: 'GOVERNANCE_PROFILE', title: 'Agent identity approved', description: 'Agent profile 必須存在、已核准、enabled，且風險狀態正常。' },
  { key: 'GOVERNANCE_APPROVED_CAPABILITY', title: 'Core capability approval exists', description: 'Agent must receive Core-approved capability assignment and Dispatch Flow Agent selection / Flow Agent approval. Runtime self-report is not dispatch authority.' },
  { key: 'RUNTIME_AGENT_ONLINE', title: 'Runtime Agent online', description: 'Agent 必須實際連上 Gateway，否則 Core 找得到 profile 也送不出去。' },
  { key: 'RUNTIME_REPORTED_CAPABILITY', title: 'Runtime capability observation optional', description: '業務能力由 Admin UI/Core 管理；runtime capability observation 僅供診斷，不是派工硬門檻。' },
  { key: 'AGENT_CAPACITY_AVAILABLE', title: 'Agent capacity available', description: 'Agent 如果 slots 用完或忙碌，暫時不應再接新任務。' },
  { key: 'SKILL_AWARE_ROUTING_ELIGIBLE', title: 'Dispatch Eligibility passed', description: '前面資格、runtime、credential 與容量條件都通過後，才允許 dispatch。' }
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
  if (!result) return { title: '尚未建立派工判斷鏈', description: '請先執行 Dispatch Flow evidence，系統會把 Task Requirement、Dispatch Flow Agent selection、Agent Flow Agent approval、Runtime 與 Dispatch Decision 串成同一條檢查鏈。', tone: 'waiting' };
  if (result.ready) return { title: '派工判斷鏈全部通過', description: 'Task Requirement、後台 Flow Agent approval、Runtime facts 與 Dispatch Eligibility 已通過。', tone: 'ready' };
  const failed = buildDispatchReadinessChain(result).find((step) => step.status === 'fail');
  return { title: '派工判斷鏈尚未通過', description: failed?.beginnerHint ?? failed?.message ?? '至少有一個派工資格條件未通過。請依下方紅色步驟修正，通常是缺 Dispatch Flow Agent selection / Flow Agent approval / Capability approval 或 runtime feature。', tone: 'blocked' };
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
  return { title: '等待可派工 Agent', nextAction: '檢查 Task Requirement、Dispatch Flow Agent selection、Agent Flow Agent approval、Runtime facts 與容量。', tone: 'waiting' };
}

export function agentBeginnerHeadline(profile?: CoreAgentProfile | null, load?: CoreAgentRuntimeLoadSnapshot | null): { title: string; description: string; tone: 'ok' | 'blocked' | 'waiting' } {
  if (!profile) return { title: 'Core 尚未建立 Agent Profile', description: '此 Agent 可能只是 runtime observation，需先完成 enrollment / approval。', tone: 'blocked' };
  if (profile.enabled === false) return { title: 'Agent 已停用，不能派工', description: '請先 Enable Agent，再檢查能力授權與 runtime。', tone: 'blocked' };
  if (normalizeCode(profile.approvalStatus) !== 'APPROVED') return { title: 'Agent 尚未核准', description: `目前治理狀態為 ${beginnerStatusLabel(profile.approvalStatus)}。`, tone: 'blocked' };
  if (normalizeCode(profile.riskStatus) && normalizeCode(profile.riskStatus) !== 'NORMAL') return { title: 'Agent 風險狀態異常', description: `目前風險狀態為 ${beginnerStatusLabel(profile.riskStatus)}。`, tone: 'blocked' };
  if (load && normalizeCode(load.status) && !['IDLE', 'READY', 'RUNNING'].includes(normalizeCode(load.status))) {
    return { title: 'Agent runtime 狀態需要確認', description: `Runtime load status=${load.status}，請確認是否可接新任務。`, tone: 'waiting' };
  }
  return { title: 'Agent 身份治理已通過', description: '仍需確認後台 Flow Agent approval、Capability approval、Runtime facts 與容量是否符合此 Task。', tone: 'ok' };
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
      nextAction: profile ? '檢查 Approval、Enabled、Risk、Credential 與 approved capabilities。' : '建立或核准 Agent Profile。',
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
    subtitle: '身份治理狀態正常；仍需確認 Task 所需的 Dispatch Flow Agent selection / Flow Agent approval 與 Runtime facts。',
    statusCode,
    statusLabel: '治理可派工',
    blockingReason: headline.description,
    nextAction: '使用能力矩陣或 Dispatch Flow evidence 檢查指定 Task 是否可派給此 Agent。',
    tone: 'success'
  };
}

export function capabilityDecisionSummary(capability?: { capabilityCode?: string; enabled?: boolean; riskLevel?: string; requiresHumanApproval?: boolean; maskingRequired?: boolean } | null): DecisionSummary {
  const code = normalizeCode(capability?.capabilityCode) || 'INCIDENT_ANALYSIS';
  if (!capability?.capabilityCode) {
    return {
      title: '請先選擇進階派工政策',
      subtitle: 'Dispatch Policy Definition 是進階政策資料；Agent 派工主流程請使用 Dispatch Flow Agent selection / Flow Agent approval。',
      statusCode: 'MISSING',
      statusLabel: '尚未選擇',
      blockingReason: '目前沒有選取的 capability policy definition。',
      nextAction: '一般派工測試請先從 Dispatch Recipes 或 Dispatch Flow Agent selections 開始。',
      tone: 'warning'
    };
  }
  if (capability.enabled === false) {
    return {
      title: `${beginnerCapabilityLabel(code)} capability policy 已停用`,
      subtitle: '停用的 capability policy 不應被進階 policy-aware compatibility 使用。',
      statusCode: 'DISABLED',
      statusLabel: 'Policy 停用',
      blockingReason: 'capability policy definition enabled=false。',
      nextAction: '啟用進階政策或改用其他 Dispatch Flow Agent selection / policy。',
      tone: 'danger'
    };
  }
  return {
    title: `${beginnerCapabilityLabel(code)} 已可作為 capability policy definition`,
    subtitle: '下一步不是直接派工，而是確認哪些 Agent 已取得對應 Flow Agent approval，以及 runtime facts 是否符合。',
    statusCode: capability.riskLevel ?? 'LOW',
    statusLabel: 'Policy 已定義',
    blockingReason: capability.requiresHumanApproval ? '此 capability policy 要求人工核准，派工或執行前需納入治理流程。' : 'Advanced policy 已定義；是否能派工仍取決於 Dispatch Flow Agent selection、Agent Flow Agent approval、Capability approval 與 Runtime facts。',
    nextAction: '前往 Dispatch Flow Agent selections 檢查 required profile，或使用 Dispatch Recipes 檢查派工準備度。',
    tone: capability.requiresHumanApproval || capability.maskingRequired ? 'warning' : 'success'
  };
}

export type DispatchRecipeLane = 'needs-action' | 'waiting' | 'done' | 'all';

export interface EntityRelationshipStep {
  id: 'capability-policy' | 'agent' | 'runtime' | 'task' | 'result' | 'identity' | 'credential' | 'Flow Agent approval' | 'dispatch';
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
  const required = normalizeList(task?.requiredCapabilities);
  const hasTask = Boolean(task?.taskId);
  const hasAgent = Boolean(task?.assignedAgentId);
  const delivered = hasAny(task?.dispatchDeliveryStatus) || hasAny(task?.dispatchExecutionStatus) || hasAny(task?.dispatchStatus);
  const callbackReceived = normalizeCode(task?.callbackStatus) === 'CALLBACK_RECEIVED' || ['COMPLETED', 'SUCCEEDED'].includes(normalizeCode(task?.status));
  const failed = ['FAILED', 'TIMEOUT', 'TIMED_OUT', 'DEAD_LETTER'].includes(normalizeCode(task?.status)) || Boolean(task?.failureReason);

  return [
    {
      id: 'capability-policy',
      title: 'Task Requirement',
      description: required.length > 0 ? `Task 需求：${required.map(beginnerCapabilityLabel).join('、')}；後續需解析到 Dispatch Flow Agent selection。` : 'Task 尚未清楚標示 requiredCapabilities / task requirement。',
      status: required.length > 0 ? 'done' : hasTask ? 'current' : 'waiting',
      code: required[0],
      href: '/dispatch-flows'
    },
    {
      id: 'agent',
      title: 'Agent Flow Agent approval',
      description: hasAgent ? `已選中 ${task?.assignedAgentId}，仍需確認後台 Flow Agent approval / Capability approval。` : '尚未選中 Agent，請先完成 routing 或 reassign。',
      status: hasAgent ? 'done' : hasTask ? 'current' : 'waiting',
      code: task?.assignedAgentId,
      href: task?.assignedAgentId ? `/agents/${encodeURIComponent(task.assignedAgentId)}` : '/agents'
    },
    {
      id: 'runtime',
      title: 'Runtime facts',
      description: delivered ? 'Gateway 已觀測到派工 delivery；下一步通常是等待 Agent ACK / callback。' : '尚未看到 Gateway delivery，請檢查 dispatch worker 與 runtime。',
      status: failed ? 'blocked' : delivered ? 'done' : hasAgent ? 'current' : 'waiting',
      code: task?.dispatchDeliveryStatus ?? task?.dispatchExecutionStatus ?? task?.dispatchStatus
    },
    {
      id: 'task',
      title: 'Task 派工',
      description: hasTask ? 'Core Task 已建立，派工狀態以 Core runtime-view 為準。' : '尚未建立測試 Task。',
      status: failed ? 'blocked' : hasTask ? 'done' : 'waiting',
      code: task?.status,
      href: task?.taskId ? `/tasks/${encodeURIComponent(task.taskId)}` : '/tasks'
    },
    {
      id: 'result',
      title: 'Result / Issue',
      description: callbackReceived ? '已收到 callback，可查看 Agent 結果與 Issue sync。' : failed ? '任務失敗或被阻擋，請先處理 recovery。' : '尚未收到 Agent RESULT / ERROR callback 或 Issue history。',
      status: callbackReceived ? 'done' : failed ? 'blocked' : hasTask ? 'current' : 'waiting',
      code: task?.callbackStatus
    }
  ];
}

export function buildCapabilityRelationshipSteps(capability?: { capabilityCode?: string; enabled?: boolean } | null): EntityRelationshipStep[] {
  const code = normalizeCode(capability?.capabilityCode);
  const enabled = capability?.enabled !== false;
  return [
    { id: 'capability-policy', title: 'Dispatch Policy', description: code ? `${beginnerCapabilityLabel(code)} 已在進階政策定義中。` : '請先選擇一個 capability policy definition；一般派工請從 Dispatch Flow Agent selection 開始。', status: code ? enabled ? 'done' : 'blocked' : 'current', code, href: '/dispatch-flows' },
    { id: 'agent', title: 'Agent Flow Agent approval', description: '下一步要確認哪些 Agent 已被後台核准對應 Dispatch Flow Agent selection。', status: code && enabled ? 'current' : 'waiting', href: '/agents' },
    { id: 'runtime', title: 'Runtime facts', description: 'Agent runtime 必須在線並支援必要 protocol feature。', status: 'waiting' },
    { id: 'task', title: 'Task 派工', description: '建立測試 Task 前，先用派工方案檢查 readiness。', status: 'waiting', href: '/dispatch-flows' },
    { id: 'result', title: 'Result / Issue', description: 'Task 完成後再檢查 Agent callback 與 Issue sync。', status: 'waiting' }
  ];
}

export function buildAgentRelationshipSteps(profile?: CoreAgentProfile | null, load?: CoreAgentRuntimeLoadSnapshot | null): EntityRelationshipStep[] {
  const approved = normalizeCode(profile?.approvalStatus) === 'APPROVED' && profile?.enabled !== false;
  const runtimeStatus = normalizeCode(load?.status);
  const runtimeReady = Boolean(load) && !['OFFLINE', 'DISCONNECTED', 'AUTH_DENIED', 'REVOKED'].includes(runtimeStatus);
  return [
    { id: 'capability-policy', title: 'Required Profile', description: '先確認 Task 可解析到後台 Dispatch Flow Agent selection；Dispatch Policy 只留在 compatibility diagnostics。', status: 'info', href: '/dispatch-flows' },
    { id: 'agent', title: 'Agent 授權', description: profile ? approved ? 'Core Governance 已核准此 Agent。' : 'Agent 尚未通過治理或已停用。' : 'Core 尚未建立 Agent Profile。', status: profile ? approved ? 'done' : 'blocked' : 'current', code: profile?.approvalStatus, href: '/agents' },
    { id: 'runtime', title: 'Runtime facts', description: runtimeReady ? 'Runtime load snapshot 可用；仍需確認 required runtime features 與容量。' : 'Runtime 尚未可用或需要確認連線/授權。', status: runtimeReady ? 'done' : approved ? 'current' : 'waiting', code: load?.status },
    { id: 'task', title: 'Task 派工', description: '選擇 Task 場景後，用 Dispatch Flow evidence 檢查此 Agent 是否可接。', status: runtimeReady && approved ? 'current' : 'waiting', href: profile?.agentId ? `/dispatch-flows?agentId=${encodeURIComponent(profile.agentId)}` : '/dispatch-flows' },
    { id: 'result', title: 'Result / Issue', description: '收到 Agent RESULT / ERROR callback 後，才會進入結果與 Issue sync。', status: 'waiting' }
  ];
}

export function buildRecipeRelationshipSteps(options?: { scenarioCapability?: string; agentId?: string; readinessReady?: boolean; readinessChecked?: boolean }): EntityRelationshipStep[] {
  const capability = normalizeCode(options?.scenarioCapability) || 'INCIDENT_ANALYSIS';
  const hasAgent = Boolean(options?.agentId);
  const checked = Boolean(options?.readinessChecked);
  const ready = Boolean(options?.readinessReady);
  return [
    { id: 'capability-policy', title: 'Required Profile', description: `${beginnerCapabilityLabel(capability)} 會解析為後台 Dispatch Flow Agent selection / Dispatch Eligibility 需求。`, status: 'done', code: capability, href: '/dispatch-flows' },
    { id: 'agent', title: 'Agent Flow Agent approval', description: hasAgent ? `本方案將檢查 ${options?.agentId} 是否具備後台 Flow Agent approval。` : '請在精靈中選擇要測試的 Agent。', status: hasAgent ? 'done' : 'current', code: options?.agentId, href: '/agents' },
    { id: 'runtime', title: 'Runtime facts', description: checked ? 'Readiness check 會確認 Runtime 是否在線、支援必要 feature 且有容量。' : '執行 readiness 後才會知道 runtime 是否對齊。', status: checked ? ready ? 'done' : 'blocked' : 'waiting' },
    { id: 'task', title: 'Task 派工', description: ready ? '可以建立測試 Task。' : 'Readiness 通過前先不要送測試 Task。', status: ready ? 'current' : 'waiting', href: '/tasks' },
    { id: 'result', title: 'Result / Issue', description: 'Task 送出後到 Task Console 查看 callback 與 Issue sync。', status: 'waiting' }
  ];
}

// Phase 11 compatibility exports for renamed advanced screens.
// Operator-facing readiness uses capability wording; these wrappers avoid breaking older imports.
export function skillDecisionSummary(skill?: { skillCode?: string; enabled?: boolean; riskLevel?: string; requiresHumanApproval?: boolean; maskingRequired?: boolean } | null): DecisionSummary {
  return capabilityDecisionSummary(skill ? { capabilityCode: skill.skillCode, enabled: skill.enabled, riskLevel: skill.riskLevel, requiresHumanApproval: skill.requiresHumanApproval, maskingRequired: skill.maskingRequired } : null);
}

export function buildSkillRelationshipSteps(skill?: { skillCode?: string; enabled?: boolean } | null): EntityRelationshipStep[] {
  return buildCapabilityRelationshipSteps(skill ? { capabilityCode: skill.skillCode, enabled: skill.enabled } : null);
}
