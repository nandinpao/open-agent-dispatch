import type { CoreAgentPoolView, CoreDispatchFlowRuleView, CoreDispatchFlowView, CoreDispatchSimulationResponse, CoreSourceSystem } from '@/lib/types/core';

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
  if (!flow) return 'No Source Flow selected';
  return flow.flowName ?? flow.flowCode ?? flow.flowId;
}

export function poolDisplay(pool?: CoreAgentPoolView | null, fallback?: string | null): string {
  if (!pool) return fallback || 'No Agent Pool is configured';
  if (pool.poolName && pool.poolCode) return `${pool.poolName} (${pool.poolCode})`;
  return pool.poolName ?? pool.poolCode ?? pool.poolId;
}

export function ruleConditionSummary(rule?: CoreDispatchFlowRuleView | null): string {
  if (!rule) return 'All other events';
  const parts = [
    rule.eventType && rule.eventType !== '*' ? `eventType = ${rule.eventType}` : undefined,
    rule.objectType && rule.objectType !== '*' ? `objectType = ${rule.objectType}` : undefined,
    rule.errorCode && rule.errorCode !== '*' ? `errorCode = ${rule.errorCode}` : undefined,
    typeof rule.condition?.severity === 'string' ? `severity = ${String(rule.condition.severity)}` : undefined,
  ].filter(Boolean);
  return parts.length ? parts.join(' and ') : 'All other events';
}

export function flowHealthIssues(flow: CoreDispatchFlowView | null | undefined, pools: CoreAgentPoolView[]): string[] {
  if (!flow) return ['No Source Flow selected'];
  const issues: string[] = [];
  if (!flow.sourceSystem) issues.push('Source System is not assigned.');
  if (!flow.defaultPoolId) {
    issues.push('Default Agent Pool is not assigned.');
  } else if (!pools.some((pool) => pool.poolId === flow.defaultPoolId)) {
    issues.push('Default Agent Pool does not exist in the current workspace.');
  }
  for (const rule of activeRules(flow)) {
    if (rule.targetPoolId && !pools.some((pool) => pool.poolId === rule.targetPoolId)) {
      issues.push(`Rule ${rule.ruleName ?? rule.ruleCode ?? rule.ruleId ?? 'Details'} targets an Agent Pool that does not exist.`);
    }
  }
  return issues;
}

export function flowActivationIssues(flow: CoreDispatchFlowView | null | undefined): string[] {
  if (!flow) return ['No Source Flow selected'];
  if (!isActiveStatus(flow.status)) {
    return ['Source Flow is not enabled. Set the Flow status to ACTIVE or ENABLED before runtime dispatch.'];
  }
  return [];
}

/** C7: Draft Simulation is configuration-gated, not activation-gated. */
export function flowSimulationIssues(flow: CoreDispatchFlowView | null | undefined, pools: CoreAgentPoolView[]): string[] {
  return flowHealthIssues(flow, pools);
}

export function simulationMatchesFlowVersion(
  flow: CoreDispatchFlowView | null | undefined,
  simulation: CoreDispatchSimulationResponse | null | undefined,
): boolean {
  if (!flow || !simulation || simulation.flowVersion == null || flow.version == null) return false;
  return String(simulation.flowVersion) === String(flow.version);
}

export function simulationPassed(simulation: CoreDispatchSimulationResponse | null | undefined): boolean {
  return Boolean(simulation && (simulation.dispatchable === true || simulation.manualOnly === true));
}

const STRUCTURAL_ACTIVATION_BLOCKERS = new Set([
  'TENANT_ID_REQUIRED',
  'SOURCE_SYSTEM_REQUIRED',
  'SOURCE_FLOW_NOT_MATCHED',
  'SOURCE_FLOW_HAS_NO_DEFAULT_POOL',
  'AGENT_POOL_REPOSITORY_UNAVAILABLE',
  'RULE_TARGET_POOL_NOT_FOUND',
  'POOL_HAS_NO_ACTIVE_MEMBER',
  'ASSIGNMENT_ROUTING_DISABLED',
]);

/**
 * Draft Simulation can prove durable Flow/Rule/Pool configuration even when no Agent runtime is
 * connected yet. Runtime availability, capacity and backoff are operational facts and remain
 * authoritative at real dispatch time; they are not durable activation prerequisites.
 */
export function simulationConfigurationPassed(simulation: CoreDispatchSimulationResponse | null | undefined): boolean {
  if (!simulation) return false;
  if (simulationPassed(simulation)) return true;
  const blocker = String(simulation.blockerCode ?? '').trim().toUpperCase();
  if (STRUCTURAL_ACTIVATION_BLOCKERS.has(blocker)) return false;
  return Boolean(simulation.targetPoolId && (simulation.poolMemberCount ?? 0) > 0);
}

/**
 * C7 activation evidence is intentionally UI advisory. Core re-runs production routing
 * transactionally and validates durable Flow/Rule/Pool configuration before accepting
 * ACTIVE/ENABLED; runtime readiness remains a separate operational gate.
 */
export function flowActivationReadinessIssues(
  flow: CoreDispatchFlowView | null | undefined,
  pools: CoreAgentPoolView[],
  simulation: CoreDispatchSimulationResponse | null | undefined,
): string[] {
  const issues = [...flowHealthIssues(flow, pools)];
  if (!flow) return issues;
  if (!simulation) {
    issues.push('Run Draft Simulation for the current Flow version before activation.');
    return issues;
  }
  if (simulation.evaluationMode && simulation.evaluationMode !== 'DRAFT_SIMULATION') {
    issues.push('Activation requires Draft Simulation evidence, not a runtime-readiness snapshot.');
  }
  if (!simulationMatchesFlowVersion(flow, simulation)) {
    issues.push('The saved Flow changed after the last Draft Simulation. Run Draft Simulation again.');
  }
  if (!simulationConfigurationPassed(simulation)) {
    issues.push(simulation.blockerReason || simulation.blockerCode || 'Draft Simulation found a structural routing configuration blocker.');
  }
  return Array.from(new Set(issues));
}

/**
 * Compatibility helper retained for callers that only need lifecycle prerequisites. It must not be
 * used to claim Agent/runtime eligibility; authoritative runtime readiness comes from Core simulation.
 */
export function flowRuntimeReadinessIssues(flow: CoreDispatchFlowView | null | undefined, pools: CoreAgentPoolView[]): string[] {
  return [...flowHealthIssues(flow, pools), ...flowActivationIssues(flow)];
}

export function sourceHealthLabel(sourceId: string, flows: CoreDispatchFlowView[], pools: CoreAgentPoolView[]): string {
  const sourceFlows = flows.filter((flow) => flow.sourceSystem === sourceId);
  if (!sourceFlows.length) return 'No Flow';
  const incomplete = sourceFlows.filter((flow) => flowHealthIssues(flow, pools).length > 0).length;
  if (incomplete > 0) return `${incomplete} flows need configuration`;
  const active = sourceFlows.filter((flow) => isActiveStatus(flow.status)).length;
  return active > 0 ? 'Healthy' : 'Not enabled';
}

export function sectionState(loading: boolean, error: string | null, empty: boolean): WorkspaceSectionState {
  if (loading) return 'loading';
  if (error) return 'error';
  return empty ? 'empty' : 'ready';
}

export function statusTone(status?: string | null): 'READY' | 'NOT_READY' | string {
  return isActiveStatus(status) ? 'READY' : 'NOT_READY';
}
