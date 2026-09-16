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
      title: 'Create a Source System',
      description: 'CreateNameEvent details eventType,objectType or plantId  ERP,MES ',
      status: !hasResult ? 'current' : sourceReady ? 'done' : 'blocked',
      code: 'SOURCE_SYSTEM_CONFIGURED'
    },
    {
      id: 'agent',
      title: 'Create and approve an Agent',
      description: 'Agent Disable.Capability  Current Dispatch information',
      status: !hasResult ? 'waiting' : agentReady ? 'done' : sourceReady ? 'current' : 'waiting',
      code: 'AGENT_APPROVED'
    },
    {
      id: 'agent-pool',
      title: 'Create an Agent Pool and add Agents',
      description: 'Agent Pool  Agent Enable Pool Member Agent.',
      status: !hasResult ? 'waiting' : poolReady && memberReady ? 'done' : agentReady ? 'current' : 'waiting',
      code: 'AGENT_POOL_CONFIGURED'
    },
    {
      id: 'source-flow',
      title: 'Create a Source Flow and assigndefaultAgent Pool',
      description: 'Source Flow Event details Agent Pool;Classification rules',
      status: !hasResult ? 'waiting' : flowReady && poolReady ? 'done' : poolReady && memberReady ? 'current' : 'waiting',
      code: 'SOURCE_FLOW_RESOLVED'
    },
    {
      id: 'runtime',
      title: 'confirmAgent Pool Agent',
      description: 'Runtime Eligibility Backoff,Credential and managementStatus.',
      status: !hasResult ? 'waiting' : runtimeReady ? 'done' : flowReady ? 'current' : 'waiting',
      code: 'RUNTIME_AGENT_ONLINE'
    },
    {
      id: 'simulation',
      title: 'Dispatch information',
      description: ' Source Flow,Rule,Target Agent Pool Agent Create Task.',
      status: !hasResult ? 'waiting' : ready ? 'done' : runtimeReady ? 'current' : 'waiting'
    },
    {
      id: 'real-test',
      title: 'Send a test event',
      description: 'Create Task, and from Task pageconfirm Assignment,Delivery,ACK and Result.',
      status: ready ? 'current' : 'waiting'
    }
  ];
}

export function summarizeReadinessForBeginner(result?: CoreDispatchReadinessEvaluationResult | null): { title: string; description: string; tone: 'ready' | 'blocked' | 'waiting' } {
  if (!result) {
    return { title: 'Dispatch', description: 'Review the configuration and try again.Source Systems,Agent,Agent Pool,Source Flow and Runtime Eligibility.', tone: 'waiting' };
  }
  if (result.ready) {
    return { title: 'Dispatch', description: result.beginnerSummary ?? result.summary ?? 'Source Flow,Agent Pool,Pool Member Agent and Runtime Eligibility ', tone: 'ready' };
  }
  const failed = result.checks?.find((check) => normalizeCode(check.status) === 'FAIL');
  return {
    title: 'Dispatch',
    description: failed?.beginnerHint ?? failed?.message ?? result.beginnerSummary ?? ' Source Flow,Agent Pool,Pool Member or Runtime Eligibility ',
    tone: 'blocked'
  };
}

export function recommendedBeginnerActions(result?: CoreDispatchReadinessEvaluationResult | null): BeginnerAction[] {
  if (!result) {
    return [{ label: 'Dispatch', description: ' Core according to Current Source Flow and Agent Pool Configuration', tone: 'primary' }];
  }
  if (result.ready) {
    return [
      { label: 'Send a test event', description: 'Create Task, confirm Assignment → Delivery → ACK → Result.', tone: 'primary' },
      { label: 'open Task', description: 'from Task View detailsCurrent status.', tone: 'neutral' }
    ];
  }

  const actions: BeginnerAction[] = [];
  const failedKeys = new Set((result.checks ?? []).filter((check) => normalizeCode(check.status) === 'FAIL').map((check) => normalizeCode(check.key)));
  const failed = (...keys: string[]) => keys.some((key) => failedKeys.has(key));

  if (failed('SOURCE_SYSTEM_CONFIGURED', 'SOURCE_SYSTEM_RESOLVED')) {
    actions.push({ label: 'Create a Source System', description: 'Event details sourceSystemCreateSource Systems.', tone: 'safe' });
  }
  if (failed('SOURCE_FLOW_RESOLVED', 'FLOW_RESOLVED', 'DISPATCH_CONTRACT_RESOLVED', 'DISPATCH_RULE_MISSING')) {
    actions.push({ label: ' Source Flow', description: 'confirmSource SystemshasEnable Source Flow, and assign Default Agent Pool.', tone: 'safe' });
  }
  if (failed('DEFAULT_POOL_CONFIGURED', 'AGENT_POOL_CONFIGURED', 'MISSING_AGENT_POOL', 'POOL_HAS_NO_ACTIVE_MEMBER')) {
    actions.push({ label: ' Agent Pool', description: 'create or selectAgent PoolEnable Agent.', tone: 'safe' });
  }
  if (failed('GOVERNANCE_PROFILE', 'AGENT_APPROVED', 'GOVERNANCE_AGENT_APPROVED')) {
    actions.push({ label: 'approve Agent', description: 'confirm Agent CreateDisableor revoke.', tone: 'safe' });
  }
  if (failed('RUNTIME_AGENT_ONLINE', 'POOL_AGENT_RUNTIME_NOT_FOUND', 'POOL_AGENT_OFFLINE', 'AGENT_CAPACITY_AVAILABLE', 'POOL_AGENT_CAPACITY_FULL', 'POOL_AGENT_BACKOFF')) {
    actions.push({ label: ' Agent Runtime', description: 'confirm Pool Membersconnected, has capacity,Credential  Backoff.', tone: 'warning' });
  }
  if (failed('CAPABILITY_POLICY_DEFINED', 'SKILL_DEFINED', 'GOVERNANCE_APPROVED_CAPABILITY', 'TASK_REQUIRES_CAPABILITY', 'RUNTIME_REPORTED_CAPABILITY')) {
    actions.push({ label: ' Current Dispatch', description: 'Capability  readiness Review the configuration and try again. Source Flow,Agent Pool,Pool Member Agent and Runtime Evidence ', tone: 'neutral' });
  }
  if (actions.length === 0) {
    actions.push({ label: 'Details', description: 'View details Routing EvidenceOperation failed.', tone: 'neutral' });
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
  { key: 'DISPATCH_CONTRACT_RESOLVED', title: 'Source Flow ', description: 'Current Dispatch informationEnable Source Flow and Default Agent Pool Rule ' },
  { key: 'GOVERNANCE_PROFILE', title: 'Agent approved', description: ' Agent enabled' },
  { key: 'RUNTIME_AGENT_ONLINE', title: 'Agent Runtime ', description: 'Pool Member Agent  Gateway Runtime Eligibility.' },
  { key: 'AGENT_CAPACITY_AVAILABLE', title: 'Agent capacityavailable', description: 'Agent musthas available slot, and not in Backoff or draining Status.' },
  { key: 'CAPABILITY_POLICY_DEFINED', title: 'Legacy readiness ', description: ' Capability/Profile readiness Current Dispatch information Source Flow,Agent Pool and Runtime Evidence ' },
  { key: 'GOVERNANCE_APPROVED_CAPABILITY', title: 'Capability ', description: 'Capability is Agent  Current Agent Pool Dispatch information' },
  { key: 'RUNTIME_REPORTED_CAPABILITY', title: 'Runtime Capability ', description: 'Runtime capability observation TASK_ACK,TASK_RESULT etc. transport feature still by Runtime Evidence ' },
  { key: 'TASK_REQUIRES_CAPABILITY', title: 'Task Capability Metadata', description: 'requiredCapabilities  Source Flow and Agent Pool.' },
  { key: 'SKILL_AWARE_ROUTING_ELIGIBLE', title: 'Runtime Eligibility ', description: 'Source Flow,Agent Pool,Agent managementStatus,Runtime,Credential,Capacity and Backoff Dispatch information' }
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
  if (!result) return { title: 'Not created yetDispatch information', description: 'Review the configuration and try again. Source Flow,Default Agent Pool,Pool Member Agent and Runtime Eligibility.', tone: 'waiting' };
  if (result.ready) return { title: 'Dispatch informationAll', description: 'Source Flow,Agent Pool,Agent managementStatus,Runtime and Capacity ', tone: 'ready' };
  const failed = buildDispatchReadinessChain(result).find((step) => step.status === 'fail');
  return { title: 'Dispatch information', description: failed?.beginnerHint ?? failed?.message ?? ' Current Review the configuration and try again. Source Flow,Agent Pool,Pool Member Agent,Runtime,Credential,Capacity and Backoff.', tone: 'blocked' };
}

export function taskBeginnerHeadline(row: TaskDispatchDashboardRow): { title: string; nextAction: string; tone: 'ok' | 'waiting' | 'blocked' | 'done' } {
  const task = row.task;
  const status = normalizeCode(task.status);
  const dispatch = normalizeCode(task.dispatchExecutionStatus ?? task.dispatchStatus);
  const delivery = normalizeCode(task.dispatchDeliveryStatus);
  const callback = normalizeCode(task.callbackStatus);

  if (['COMPLETED', 'SUCCEEDED'].includes(status) || dispatch === 'COMPLETED') {
    return { title: 'Agent CompletedTask', nextAction: 'view Agent summary,Issue History ', tone: 'done' };
  }
  if (['CANCELLED', 'CANCELED'].includes(status)) {
    return { title: 'Task cancelled', nextAction: 'this Task  Operator Review the configuration and try again. Timeline / Issue.', tone: 'done' };
  }
  if (['FAILED', 'TIMEOUT', 'TIMED_OUT', 'DEAD_LETTER'].includes(status) || task.failureReason) {
    return { title: 'Task processing failed', nextAction: task.failureReason ?? task.nextAction ?? 'Operation failed.', tone: 'blocked' };
  }
  if (['ESCALATED', 'ORPHANED', 'RECONCILING'].includes(status)) {
    return { title: 'Taskrequireshumanprocess', nextAction: task.nextAction ?? task.lifecycleReason ?? 'view Failure Queue  Retry,Escalate or DLQ.', tone: 'blocked' };
  }
  if (task.blockedReason) {
    return { title: 'Dispatch information', nextAction: task.nextAction ?? task.blockedReason ?? 'Dispatch information', tone: 'blocked' };
  }
  if (task.dispatchWaitReason) {
    return { title: 'Waiting for the next state transition.', nextAction: task.nextAction ?? task.dispatchWaitReason, tone: 'waiting' };
  }
  if (callback === 'CALLBACK_RECEIVED' || status === 'RUNNING' || dispatch === 'RUNNING') {
    return { title: 'Agent Waiting for the next state transition.Result', nextAction: 'waiting RESULT / ERROR callback, or view Agent runtime ', tone: 'ok' };
  }
  if (delivery === 'DELIVERED_TO_GATEWAY' || ['DELIVERED', 'DISPATCHED', 'ACKED'].includes(dispatch)) {
    return { title: 'Gateway Waiting for the next state transition. Agent callback', nextAction: 'if Agent still display IDLE runtime  RUNNINGReview the configuration and try again. callback relay.', tone: 'waiting' };
  }
  if (task.assignedAgentId) {
    return { title: ' AgentWaiting for the next state transition.', nextAction: task.nextAction ?? 'waiting Core dispatch worker  retry.', tone: 'waiting' };
  }
  return { title: 'waitingcandispatch Agent', nextAction: ' Source Flow,Target Agent Pool,Pool Member Agent,Runtime, capacity and Backoff.', tone: 'waiting' };
}

export function agentBeginnerHeadline(profile?: CoreAgentProfile | null, load?: CoreAgentRuntimeLoadSnapshot | null): { title: string; description: string; tone: 'ok' | 'blocked' | 'waiting' } {
  if (!profile) return { title: 'Core Not created yet Agent Profile', description: 'this Agent  runtime observation enrollment / approval.', tone: 'blocked' };
  if (profile.enabled === false) return { title: 'Agent DisableDispatch information', description: 'First, Enable Agent runtime.', tone: 'blocked' };
  if (normalizeCode(profile.approvalStatus) !== 'APPROVED') return { title: 'Agent Not approved', description: `Statusis ${beginnerStatusLabel(profile.approvalStatus)}.`, tone: 'blocked' };
  if (normalizeCode(profile.riskStatus) && normalizeCode(profile.riskStatus) !== 'NORMAL') return { title: 'Agent riskStatus', description: `currentriskStatusis ${beginnerStatusLabel(profile.riskStatus)}.`, tone: 'blocked' };
  if (load && normalizeCode(load.status) && !['IDLE', 'READY', 'RUNNING'].includes(normalizeCode(load.status))) {
    return { title: 'Agent runtime Statusrequiresconfirm', description: `Runtime load status=${load.status}Review the configuration and try again.`, tone: 'waiting' };
  }
  return { title: 'Agent ', description: ' Agent addTarget Agent Pool, and Runtime, capacity and Credential Statusavailable.', tone: 'ok' };
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
      subtitle: 'This Task is complete. Review the Agent result, Issue operation evidence, or audit history.',
      statusCode,
      statusLabel: 'Completed',
      nextAction: 'Review the Agent result and any Issue operation evidence.',
      tone: 'success'
    };
  }
  if (headline.tone === 'blocked') {
    return {
      title: headline.title,
      subtitle: 'Operation failed.Status, requires Operator Cancel',
      statusCode,
      statusLabel: 'Action required',
      blockingReason: task.failureReason ?? task.blockedReason ?? task.lifecycleReason ?? 'Task or dispatch HealthyStatus.',
      nextAction: task.nextAction ?? 'view Recovery / Routing / Timeline Retry,Reassign or Cancel.',
      tone: 'danger'
    };
  }
  if (headline.tone === 'waiting') {
    return {
      title: headline.title,
      subtitle: 'Waiting for the next state transition. runtime event.First, confirm Gateway,Agent callback and runtime connectionStatus.',
      statusCode,
      statusLabel: 'Waiting',
      blockingReason: task.dispatchWaitReason ?? task.dispatchRetryReason ?? task.lifecycleReason ?? 'No failed, onlyisNot received yetDispatch information callback ',
      nextAction: task.nextAction ?? headline.nextAction,
      tone: 'warning'
    };
  }
  return {
    title: headline.title,
    subtitle: 'Dispatch information',
    statusCode,
    statusLabel: 'In progress',
    blockingReason: task.lifecycleReason ?? 'Details',
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
      subtitle: 'this Agent Adjust the status filter and try again..',
      statusCode,
      statusLabel: 'not candispatch',
      blockingReason: headline.description,
      nextAction: profile ? ' Approval,Enabled,Risk,Credential and Agent Pool membership.' : 'create or approve Agent.',
      tone: 'danger'
    };
  }
  if (headline.tone === 'waiting') {
    return {
      title: headline.title,
      subtitle: 'this Agent  runtime workload / backoff / slots.',
      statusCode,
      statusLabel: 'requiresconfirm',
      blockingReason: headline.description,
      nextAction: ' Runtime session,capacity  protocol feature.',
      tone: 'warning'
    };
  }
  return {
    title: headline.title,
    subtitle: 'StatusHealthy Agent Pool membership,Runtime,Capacity and Credential.',
    statusCode,
    statusLabel: 'Dispatch information',
    blockingReason: headline.description,
    nextAction: 'Review this Agent’s Agent Pool membership and runtime eligibility from the Task.',
    tone: 'success'
  };
}

export function capabilityDecisionSummary(capability?: { capabilityCode?: string; enabled?: boolean; riskLevel?: string; requiresHumanApproval?: boolean; maskingRequired?: boolean } | null): DecisionSummary {
  const code = normalizeCode(capability?.capabilityCode) || 'UNKNOWN';
  if (!capability?.capabilityCode) {
    return {
      title: 'Required Capability is missing',
      subtitle: 'A normal Dispatch Task must declare at least one Required Capability before Core can determine which Pool members are qualified.',
      statusCode: 'MISSING',
      statusLabel: 'Action required',
      blockingReason: 'The Task has no Required Capability, so capability eligibility cannot be evaluated.',
      nextAction: 'Add a Canonical Required Capability to the Task Definition or Assignment Profile, then re-evaluate Dispatch readiness.',
      tone: 'neutral'
    };
  }
  if (capability.enabled === false) {
    return {
      title: `${beginnerCapabilityLabel(code)} is disabled`,
      subtitle: 'A disabled Canonical Capability cannot satisfy the Task eligibility contract.',
      statusCode: 'DISABLED',
      statusLabel: 'Blocked',
      blockingReason: 'The required Canonical Capability is disabled.',
      nextAction: 'Enable the Canonical Capability or change the Task to an active required capability.',
      tone: 'warning'
    };
  }
  return {
    title: `${beginnerCapabilityLabel(code)} requirement`,
    subtitle: 'Core uses this capability to filter Agent Pool members before runtime eligibility and routing score are evaluated.',
    statusCode: capability.riskLevel ?? 'REQUIRED',
    statusLabel: 'Required',
    blockingReason: capability.requiresHumanApproval ? 'This capability requires governed approval before an Agent can satisfy the Task requirement.' : 'Only Pool members with this approved capability are eligible.',
    nextAction: 'Verify that at least one Pool member has this Canonical Capability approved, then review runtime and capacity evidence.',
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
  if (lane === 'needs-action') return 'Action required';
  if (lane === 'waiting') return 'Waiting';
  if (lane === 'done') return 'Completed';
  return 'All';
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
      description: hasFlow ? ` Source Flow:${task?.matchedFlowId}${task?.matchedRuleId ? `;Rule:${task.matchedRuleId}` : '; usedefaultAgent Poolor no Rule override'}.` : 'Not available yet Source Flow evidence; Verify sourceSystem andEnable Flow.',
      status: hasFlow ? 'done' : hasTask ? 'current' : 'waiting',
      code: task?.matchedFlowId,
      href: task?.matchedFlowId ? `/dispatch-flows?flowId=${encodeURIComponent(task.matchedFlowId)}` : '/dispatch-flows'
    },
    {
      id: 'agent-pool',
      title: 'Agent Pool',
      description: hasPool ? `Target Agent Pool:${poolId};Pool Member ${task?.poolMemberCount ?? 'did not return'}.` : 'Target Agent Pool.',
      status: hasPool ? 'done' : hasFlow ? 'current' : 'waiting',
      code: poolId,
      href: '/dispatch-flows'
    },
    {
      id: 'agent',
      title: 'Pool Member Agent',
      description: hasAgent ? ` ${task?.assignedAgentId} Pool membership and Runtime Eligibility ` : ` Agent; current eligible=${task?.eligibleAgentCount ?? 0}.`,
      status: hasAgent ? 'done' : hasPool ? 'current' : 'waiting',
      code: task?.assignedAgentId,
      href: task?.assignedAgentId ? `/agents/${encodeURIComponent(task.assignedAgentId)}` : '/agents'
    },
    {
      id: 'runtime',
      title: 'Runtime / Delivery',
      description: delivered ? 'Gateway Dispatch information delivery;Next stepWaiting for the next state transition. Agent ACK and Result.' : ' DeliveryReview the configuration and try again. Agent connection, capacity,Backoff,Credential and dispatch worker.',
      status: failed ? 'blocked' : delivered ? 'done' : hasAgent ? 'current' : 'waiting',
      code: task?.dispatchDeliveryStatus ?? task?.dispatchExecutionStatus ?? task?.dispatchStatus
    },
    {
      id: 'task',
      title: 'Task',
      description: hasTask ? 'Core Task created,Status Core runtime-view ' : 'Not created yet Task.',
      status: failed ? 'blocked' : hasTask ? 'done' : 'waiting',
      code: task?.status,
      href: task?.taskId ? `/tasks/${encodeURIComponent(task.taskId)}` : '/tasks'
    },
    {
      id: 'result',
      title: 'Result / Issue',
      description: callbackReceived ? 'Agent callback received. Review the Agent result and any Issue operation evidence.' : failed ? 'Operation failed. Review the blocker and recovery evidence.' : 'No Agent RESULT/ERROR callback has been received yet.',
      status: callbackReceived ? 'done' : failed ? 'blocked' : hasTask ? 'current' : 'waiting',
      code: task?.callbackStatus
    }
  ];
}

export function buildCapabilityRelationshipSteps(capability?: { capabilityCode?: string; enabled?: boolean } | null): EntityRelationshipStep[] {
  const code = normalizeCode(capability?.capabilityCode);
  const enabled = capability?.enabled !== false;
  return [
    { id: 'capability-reference', title: 'Capability ', description: code ? `${beginnerCapabilityLabel(code)} ` : 'Not yet select Capability Current Agent Pool dispatch.', status: code ? enabled ? 'done' : 'info' : 'current', code },
    { id: 'source-flow', title: 'Source Flow', description: 'Dispatch information Source Flow and Default/Rule Target Agent Pool.', status: 'info', href: '/dispatch-flows' },
    { id: 'agent-pool', title: 'Agent Pool', description: 'Pool membership  Agent Capability Create Pool membership.', status: 'info', href: '/dispatch-flows' },
    { id: 'runtime', title: 'Runtime Eligibility', description: 'Statusbyconnection, capacity,Backoff,Credential and managementStatus', status: 'info' },
    { id: 'result', title: 'Task Evidence', description: 'from Task view Flow,Pool,Agent,Delivery,ACK and Result evidence.', status: 'waiting', href: '/tasks' }
  ];
}

export function buildAgentRelationshipSteps(profile?: CoreAgentProfile | null, load?: CoreAgentRuntimeLoadSnapshot | null): EntityRelationshipStep[] {
  const approved = normalizeCode(profile?.approvalStatus) === 'APPROVED' && profile?.enabled !== false;
  const runtimeStatus = normalizeCode(load?.status);
  const runtimeReady = Boolean(load) && !['OFFLINE', 'DISCONNECTED', 'AUTH_DENIED', 'REVOKED'].includes(runtimeStatus);
  return [
    { id: 'agent', title: 'Agent managementStatus', description: profile ? approved ? 'Core approvedthis Agent, and Agent Disable.' : 'Agent Not approved,Disableor not candispatch.' : 'Core Not created yet Agent.', status: profile ? approved ? 'done' : 'blocked' : 'current', code: profile?.approvalStatus, href: '/agents' },
    { id: 'agent-pool', title: 'Agent Pool Membership', description: 'Agent Target Agent Pool;Capability  Agent addAgent Pool.', status: approved ? 'current' : 'waiting', href: '/dispatch-flows' },
    { id: 'runtime', title: 'Runtime Eligibility', description: runtimeReady ? 'Runtime snapshot Backoff and Credential.' : 'Runtime ', status: runtimeReady ? 'done' : approved ? 'current' : 'waiting', code: load?.status },
    { id: 'source-flow', title: 'Source Flow', description: 'Source Flow and Rule  Task  Agent Pool.', status: approved && runtimeReady ? 'info' : 'waiting', href: '/dispatch-flows' },
    { id: 'result', title: 'Task Evidence', description: 'dispatchafter from Task view Assignment,Delivery,ACK and Result.', status: 'waiting', href: '/tasks' }
  ];
}

export function buildRecipeRelationshipSteps(options?: { scenarioCapability?: string; agentId?: string; readinessReady?: boolean; readinessChecked?: boolean }): EntityRelationshipStep[] {
  const capability = normalizeCode(options?.scenarioCapability);
  const hasAgent = Boolean(options?.agentId);
  const checked = Boolean(options?.readinessChecked);
  const ready = Boolean(options?.readinessReady);
  return [
    { id: 'source-system', title: 'Source Systems', description: 'Event details sourceSystemEvent details', status: 'done' },
    { id: 'source-flow', title: 'Source Flow / Agent Pool', description: ' Source Flow,Rule override or Default Pool.', status: checked ? ready ? 'done' : 'blocked' : 'current', href: '/dispatch-flows' },
    { id: 'agent', title: 'Pool Member Agent', description: hasAgent ? `View details Agent ${options?.agentId} management and Runtime Status.` : ' Agent Agent Pool  selection strategy ', status: hasAgent ? 'done' : 'info', code: options?.agentId, href: '/agents' },
    { id: 'capability-reference', title: 'Capability ', description: capability ? `${beginnerCapabilityLabel(capability)} ` : ' Capability  Agent Pool dispatch.', status: 'info', code: capability },
    { id: 'runtime', title: 'Runtime Eligibility', description: checked ? ' Agent connection, capacity,Backoff,Credential and managementStatus.' : ' Runtime Eligibility.', status: checked ? ready ? 'done' : 'blocked' : 'waiting' },
    { id: 'task', title: 'Event details', description: ready ? 'Create Task.' : 'Create Task.', status: ready ? 'current' : 'waiting', href: '/tasks' }
  ];
}

// Compatibility exports for renamed advanced screens.
// These wrappers preserve older imports only. Current routing qualification is governed by Task Required Capability plus Core APPROVED Agent Capability assignments inside the selected Agent Pool.
export function skillDecisionSummary(skill?: { skillCode?: string; enabled?: boolean; riskLevel?: string; requiresHumanApproval?: boolean; maskingRequired?: boolean } | null): DecisionSummary {
  return capabilityDecisionSummary(skill ? { capabilityCode: skill.skillCode, enabled: skill.enabled, riskLevel: skill.riskLevel, requiresHumanApproval: skill.requiresHumanApproval, maskingRequired: skill.maskingRequired } : null);
}

export function buildSkillRelationshipSteps(skill?: { skillCode?: string; enabled?: boolean } | null): EntityRelationshipStep[] {
  return buildCapabilityRelationshipSteps(skill ? { capabilityCode: skill.skillCode, enabled: skill.enabled } : null);
}
