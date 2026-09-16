import { createUuid } from '@/lib/utils/uuid';
import type {
  CoreAgentPoolMemberView,
  CoreAgentPoolView,
  CoreDispatchFlowAgentOptionView,
  CoreDispatchFlowRuleView,
  CoreDispatchFlowView,
  CoreSourceSystem,
} from '@/lib/types/core';
import { activeRules } from './dispatchWorkspaceModel';

export type EditablePoolMember = CoreAgentPoolMemberView;

const memberStatusOptions = ['ACTIVE', 'INACTIVE', 'DISABLED'] as const;

export type PoolEditorIntent = 'defaultPool' | 'ruleTargetPool' | 'memberDrawer';

export type PoolEditorState = {
  poolId?: string;
  poolCode: string;
  poolName: string;
  sourceSystem: string;
  poolType: string;
  selectionStrategy: string;
  status: string;
  description: string;
  members: EditablePoolMember[];
  metadata?: Record<string, unknown>;
  version?: number;
  updatedAt?: string;
  updatedBy?: string;
};

export type SimulationFormState = {
  eventType: string;
  objectType: string;
  errorCode: string;
  severity: string;
  attributesJson: string;
};

export type RuleEditorState = {
  ruleId?: string;
  ruleCode: string;
  ruleName: string;
  serviceCode: string;
  priority: number;
  eventType: string;
  objectType: string;
  errorCode: string;
  severity: string;
  targetPoolId: string;
  issueSyncPolicy: '' | 'NONE' | 'OPTIONAL' | 'REQUIRED' | 'MANUAL';
  enabled: boolean;
  version?: number;
};

export function normalizeCode(value: string | undefined | null): string {
  return String(value ?? '').trim().toUpperCase().replace(/[^A-Z0-9_\-.]/g, '_').replace(/^_+|_+$/g, '');
}

export function normalizePriority(value: number | string | undefined | null, fallback: number): number {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return fallback;
  return Math.max(0, Math.round(parsed));
}

export function normalizePositiveInteger(value: number | string | undefined | null, fallback: number): number {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return fallback;
  return Math.max(1, Math.round(parsed));
}

export function generateId(prefix: string): string {
  return `${prefix}-${createUuid()}`;
}

export function sourceDisplay(source: CoreSourceSystem): string {
  return source.displayName && source.displayName !== source.sourceSystemId
    ? `${source.displayName} (${source.sourceSystemId})`
    : source.sourceSystemId;
}

export function ruleTargetPool(rule: CoreDispatchFlowRuleView, pools: CoreAgentPoolView[]): CoreAgentPoolView | undefined {
  return pools.find((pool) => pool.poolId === rule.targetPoolId);
}

export function memberStatus(value?: string | null): string {
  const normalized = normalizeCode(value ?? 'ACTIVE');
  return memberStatusOptions.some((option) => option === normalized) ? normalized : 'ACTIVE';
}

export function poolEditorFromPool(pool: CoreAgentPoolView): PoolEditorState {
  return {
    poolId: pool.poolId,
    poolCode: pool.poolCode ?? '',
    poolName: pool.poolName ?? '',
    sourceSystem: pool.sourceSystem ?? '',
    poolType: pool.poolType ?? 'RESOLUTION',
    selectionStrategy: pool.selectionStrategy ?? 'LOWEST_LOAD',
    status: String(pool.status ?? 'ACTIVE').toUpperCase(),
    description: pool.description ?? '',
    metadata: pool.metadata ?? {},
    version: pool.version,
    updatedAt: pool.updatedAt,
    updatedBy: pool.updatedBy,
    members: (pool.members ?? [])
      .filter((member) => String(member.memberStatus ?? 'ACTIVE').toUpperCase() !== 'RETIRED')
      .map((member) => ({
        ...member,
        poolId: pool.poolId,
        poolCode: pool.poolCode,
        memberStatus: memberStatus(member.memberStatus),
        priority: normalizePriority(member.priority, 100),
        weight: normalizePositiveInteger(member.weight, 1),
        metadata: member.metadata ?? {},
      })),
  };
}

export function defaultPoolEditor(sourceSystem?: string | null): PoolEditorState {
  const normalizedSource = normalizeCode(sourceSystem);
  return {
    poolCode: normalizedSource ? `${normalizedSource}_DEFAULT_PROCESSING_POOL` : 'DEFAULT_PROCESSING_POOL',
    poolName: normalizedSource ? `${normalizedSource} Default Agent Pool` : 'Default Agent Pool',
    sourceSystem: normalizedSource,
    poolType: 'RESOLUTION',
    selectionStrategy: 'LOWEST_LOAD',
    status: 'ACTIVE',
    description: 'Classification rulesEvent detailsAgent Pool.',
    members: [],
    metadata: {
      memberSource: 'ADMIN_CONFIGURATION',
      routingModel: 'AGENT_POOL_FIRST',
    },
  };
}

export function newMemberForAgent(agent: CoreDispatchFlowAgentOptionView, editor: PoolEditorState): EditablePoolMember {
  return {
    poolId: editor.poolId ?? '',
    poolCode: normalizeCode(editor.poolCode),
    agentId: agent.agentId,
    agentName: agent.agentName ?? agent.agentId,
    memberStatus: 'ACTIVE',
    priority: 100,
    weight: 1,
    approvalStatus: agent.approvalStatus,
    runtimeStatus: agent.runtimeStatus,
    metadata: {
      memberSource: 'ADMIN_CONFIGURATION',
      routingModel: 'AGENT_POOL_FIRST',
    },
  };
}

export function ruleEditorFromRule(rule: CoreDispatchFlowRuleView): RuleEditorState {
  const condition = rule.condition ?? {};
  return {
    ruleId: rule.ruleId,
    ruleCode: rule.ruleCode ?? '',
    ruleName: rule.ruleName ?? '',
    serviceCode: rule.serviceCode ?? '',
    priority: normalizePriority(rule.priority, 100),
    eventType: rule.eventType && rule.eventType !== '*' ? rule.eventType : '',
    objectType: rule.objectType && rule.objectType !== '*' ? rule.objectType : '',
    errorCode: rule.errorCode && rule.errorCode !== '*' ? rule.errorCode : '',
    severity: typeof condition.severity === 'string' ? condition.severity : '',
    targetPoolId: rule.targetPoolId ?? '',
    issueSyncPolicy: (['NONE','OPTIONAL','REQUIRED','MANUAL'].includes(String(rule.issueSyncPolicy ?? '').toUpperCase()) ? String(rule.issueSyncPolicy).toUpperCase() : '') as RuleEditorState['issueSyncPolicy'],
    enabled: rule.enabled !== false,
  };
}

export function emptyRuleEditor(flow: CoreDispatchFlowView, pools: CoreAgentPoolView[]): RuleEditorState {
  const nextPriority = Math.max(0, ...activeRules(flow).map((rule) => normalizePriority(rule.priority, 0))) + 10;
  return {
    ruleCode: `${normalizeCode(flow.sourceSystem || flow.flowCode || 'FLOW')}_RULE_${nextPriority}`,
    ruleName: `Classification rules ${nextPriority}`,
    serviceCode: '',
    priority: nextPriority,
    eventType: '',
    objectType: '',
    errorCode: '',
    severity: '',
    targetPoolId: pools[0]?.poolId ?? flow.defaultPoolId ?? '',
    issueSyncPolicy: '',
    enabled: true,
  };
}

export function ruleViewFromEditor(editor: RuleEditorState, flow: CoreDispatchFlowView): CoreDispatchFlowRuleView {
  const normalizedSeverity = editor.severity.trim();
  const condition: Record<string, unknown> = {};
  if (normalizedSeverity) condition.severity = normalizedSeverity;
  return {
    tenantId: flow.tenantId,
    flowId: flow.flowId,
    ruleId: editor.ruleId ?? generateId('rule'),
    ruleCode: normalizeCode(editor.ruleCode) || generateId('RULE'),
    ruleName: editor.ruleName.trim() || normalizeCode(editor.ruleCode) || 'Classification rules',
    serviceCode: normalizeCode(editor.serviceCode) || undefined,
    ruleScope: 'SOURCE_FLOW',
    eventStage: 'EXTERNAL',
    sourceSystem: flow.sourceSystem,
    eventType: editor.eventType.trim() || '*',
    objectType: editor.objectType.trim() || '*',
    errorCode: editor.errorCode.trim() || '*',
    condition,
    matchMode: 'ALL',
    targetPoolId: editor.targetPoolId,
    candidatePoolMode: 'AGENT_POOL',
    routingStrategy: flow.defaultRoutingStrategy ?? 'LOWEST_LOAD',
    issueSyncPolicy: editor.issueSyncPolicy || undefined,
    priority: normalizePriority(editor.priority, 100),
    enabled: editor.enabled,
    legacyStatus: 'CURRENT',
  };
}

export function poolPayload(editor: PoolEditorState, tenantId: string, agents: CoreDispatchFlowAgentOptionView[]): CoreAgentPoolView {
  const poolId = editor.poolId ?? generateId('pool');
  const poolCode = normalizeCode(editor.poolCode);
  return {
    tenantId,
    poolId,
    poolCode,
    poolName: editor.poolName.trim(),
    sourceSystem: normalizeCode(editor.sourceSystem) || undefined,
    poolType: editor.poolType,
    selectionStrategy: editor.selectionStrategy,
    status: editor.status,
    description: editor.description.trim(),
    version: editor.version,
    updatedAt: editor.updatedAt,
    updatedBy: editor.updatedBy,
    metadata: {
      ...(editor.metadata ?? {}),
      routingModel: 'AGENT_POOL_FIRST',
      adminUiEditor: 'DISPATCH_WORKSPACE_POOL_MEMBER_PRESERVING',
    },
    members: editor.members.map((member) => {
      const agent = agents.find((candidate) => candidate.agentId === member.agentId);
      return {
        ...member,
        poolId,
        poolCode,
        agentId: member.agentId,
        agentName: member.agentName ?? agent?.agentName ?? member.agentId,
        memberStatus: memberStatus(member.memberStatus),
        priority: normalizePriority(member.priority, 100),
        weight: normalizePositiveInteger(member.weight, 1),
        approvalStatus: member.approvalStatus ?? agent?.approvalStatus,
        runtimeStatus: member.runtimeStatus ?? agent?.runtimeStatus,
        metadata: member.metadata ?? {},
      };
    }),
  };
}

