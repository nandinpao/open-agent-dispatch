import type { CoreAgentPoolView, CoreDispatchFlowRuleView, CoreDispatchFlowView, CoreSourceSystem } from '@/lib/types/core';

export type WorkspaceSectionState = 'loading' | 'ready' | 'empty' | 'error';

export type DispatchWorkspaceQuery = Record<string, string | string[] | undefined>;

export function queryValue(query: DispatchWorkspaceQuery | undefined, key: string): string | undefined {
  const value = query?.[key];
  return Array.isArray(value) ? value[0] : value;
}

export function isActiveStatus(status?: string | null): boolean {
  const normalized = String(status ?? '').trim().toUpperCase();
  return normalized === 'ACTIVE' || normalized === 'ENABLED';
}

export function activeRules(flow?: CoreDispatchFlowView | null): CoreDispatchFlowRuleView[] {
  return (flow?.rules ?? [])
    .filter((rule) => rule.enabled !== false)
    .sort((left, right) => (left.priority ?? 1000) - (right.priority ?? 1000));
}

export function defaultRule(flow?: CoreDispatchFlowView | null): CoreDispatchFlowRuleView | undefined {
  return activeRules(flow).find((rule) => String(rule.eventStage ?? 'EXTERNAL').toUpperCase() === 'EXTERNAL') ?? activeRules(flow)[0];
}

export function sourceDisplay(source: CoreSourceSystem): string {
  return source.displayName && source.displayName !== source.sourceSystemId
    ? `${source.displayName} (${source.sourceSystemId})`
    : source.sourceSystemId;
}

export function flowDisplay(flow?: CoreDispatchFlowView | null): string {
  if (!flow) return '尚未選擇 Source Flow';
  return flow.flowName ?? flow.flowCode ?? flow.flowId;
}

export function poolDisplay(pool?: CoreAgentPoolView | null, fallback?: string | null): string {
  if (!pool) return fallback || '尚未設定工作池';
  if (pool.poolName && pool.poolCode) return `${pool.poolName} (${pool.poolCode})`;
  return pool.poolName ?? pool.poolCode ?? pool.poolId;
}

export function ruleConditionSummary(rule?: CoreDispatchFlowRuleView | null): string {
  if (!rule) return '其他事件';
  const parts = [
    rule.eventType && rule.eventType !== '*' ? `eventType = ${rule.eventType}` : undefined,
    rule.objectType && rule.objectType !== '*' ? `objectType = ${rule.objectType}` : undefined,
    rule.errorCode && rule.errorCode !== '*' ? `errorCode = ${rule.errorCode}` : undefined,
    typeof rule.condition?.severity === 'string' ? `severity = ${String(rule.condition.severity)}` : undefined,
  ].filter(Boolean);
  return parts.length ? parts.join(' 且 ') : '其他事件';
}

export function flowHealthIssues(flow: CoreDispatchFlowView | null | undefined, pools: CoreAgentPoolView[]): string[] {
  if (!flow) return ['尚未選擇 Source Flow'];
  const issues: string[] = [];
  if (!flow.sourceSystem) issues.push('尚未指定來源系統');
  if (!flow.defaultPoolId) {
    issues.push('尚未指定預設工作池');
  } else if (!pools.some((pool) => pool.poolId === flow.defaultPoolId)) {
    issues.push('預設工作池不存在或未載入');
  }
  for (const rule of activeRules(flow)) {
    if (rule.targetPoolId && !pools.some((pool) => pool.poolId === rule.targetPoolId)) {
      issues.push(`規則 ${rule.ruleName ?? rule.ruleCode ?? rule.ruleId ?? '未命名'} 指向不存在的工作池`);
    }
  }
  return issues;
}

export function flowActivationIssues(flow: CoreDispatchFlowView | null | undefined): string[] {
  if (!flow) return ['尚未選擇 Source Flow'];
  if (!isActiveStatus(flow.status)) {
    return ['Source Flow 尚未啟用；正式事件只會命中 ACTIVE / ENABLED Flow'];
  }
  return [];
}

export function flowRuntimeReadinessIssues(flow: CoreDispatchFlowView | null | undefined, pools: CoreAgentPoolView[]): string[] {
  return [...flowHealthIssues(flow, pools), ...flowActivationIssues(flow)];
}

export function sourceHealthLabel(sourceId: string, flows: CoreDispatchFlowView[], pools: CoreAgentPoolView[]): string {
  const sourceFlows = flows.filter((flow) => flow.sourceSystem === sourceId);
  if (!sourceFlows.length) return '尚無 Flow';
  const incomplete = sourceFlows.filter((flow) => flowHealthIssues(flow, pools).length > 0).length;
  if (incomplete > 0) return `${incomplete} 條 Flow 需補設定`;
  const active = sourceFlows.filter((flow) => isActiveStatus(flow.status)).length;
  return active > 0 ? '正常' : '尚未啟用';
}

export function sectionState(loading: boolean, error: string | null, empty: boolean): WorkspaceSectionState {
  if (loading) return 'loading';
  if (error) return 'error';
  return empty ? 'empty' : 'ready';
}

export function statusTone(status?: string | null): 'READY' | 'NOT_READY' | string {
  return isActiveStatus(status) ? 'READY' : 'NOT_READY';
}
