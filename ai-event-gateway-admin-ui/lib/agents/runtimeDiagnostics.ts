import type {
  CoreAgentAuthorizationScope,
  CoreAgentCapability,
  CoreAgentProfile,
  CoreAgentRuntimeCapabilityItem,
  CoreAgentRuntimeCapabilityProfile,
  CoreAgentRuntimeLoadSnapshot
} from '@/lib/types/core';
import type { NettyAgentRuntime } from '@/lib/types/nettyRuntime';

export type RuntimeDiagnosticSeverity = 'INFO' | 'WARN' | 'ERROR';

export interface RuntimeDiagnosticIssue {
  severity: RuntimeDiagnosticSeverity;
  code: string;
  message: string;
}

export interface RuntimeCapabilityGroups {
  flat: string[];
  taskType: string[];
  issueProvider: string[];
  toolPolicy: string[];
  executorMode: string[];
  other: Record<string, string[]>;
}

export interface RuntimeDiagnosticsResult {
  status: 'OK' | 'WARN' | 'BLOCKED';
  issues: RuntimeDiagnosticIssue[];
  approvedCapabilityCodes: string[];
  approvedTaskTypes: string[];
  approvedSystemCodes: string[];
  reported: RuntimeCapabilityGroups;
  effectiveCapabilityCodes: string[];
  effectiveTaskTypes: string[];
  effectiveIssueProviders: string[];
}

function normalizeValue(value: unknown): string | undefined {
  if (typeof value === 'string' && value.trim()) return value.trim();
  if (typeof value === 'number' && Number.isFinite(value)) return String(value);
  return undefined;
}

function normalizeKey(value: unknown): string | undefined {
  return normalizeValue(value)?.toUpperCase();
}

function uniqueSorted(values: Array<string | undefined>): string[] {
  return Array.from(new Set(values.filter((value): value is string => Boolean(value)))).sort((left, right) => left.localeCompare(right));
}

function enabledCapabilities(capabilities?: CoreAgentCapability[] | null): string[] {
  return uniqueSorted((capabilities ?? [])
    .filter((capability) => capability.enabled !== false)
    .map((capability) => normalizeKey(capability.capabilityCode)));
}

function disabledCapabilities(capabilities?: CoreAgentCapability[] | null): string[] {
  return uniqueSorted((capabilities ?? [])
    .filter((capability) => capability.enabled === false)
    .map((capability) => normalizeKey(capability.capabilityCode)));
}

function enabledScopes(scopes?: CoreAgentAuthorizationScope[] | null): CoreAgentAuthorizationScope[] {
  return (scopes ?? []).filter((scope) => scope.enabled !== false);
}

function scopeValues(scopes: CoreAgentAuthorizationScope[], key: keyof CoreAgentAuthorizationScope): string[] {
  return uniqueSorted(scopes
    .map((scope) => scope[key])
    .map(normalizeKey)
    .filter((value) => value !== '*'));
}

function addGroup(groups: RuntimeCapabilityGroups, kind: string, value: string): void {
  const normalized = normalizeKey(value);
  if (!normalized) return;

  if (kind === 'flat') groups.flat.push(normalized);
  else if (kind === 'taskType') groups.taskType.push(normalized);
  else if (kind === 'issueProvider') groups.issueProvider.push(normalized);
  else if (kind === 'toolPolicy') groups.toolPolicy.push(normalized);
  else if (kind === 'executorMode') groups.executorMode.push(normalized);
  else {
    groups.other[kind] = groups.other[kind] ?? [];
    groups.other[kind].push(normalized);
  }
}

function finalizeGroups(groups: RuntimeCapabilityGroups): RuntimeCapabilityGroups {
  const other: Record<string, string[]> = {};
  Object.entries(groups.other).forEach(([kind, values]) => {
    other[kind] = uniqueSorted(values);
  });
  return {
    flat: uniqueSorted(groups.flat),
    taskType: uniqueSorted(groups.taskType),
    issueProvider: uniqueSorted(groups.issueProvider),
    toolPolicy: uniqueSorted(groups.toolPolicy),
    executorMode: uniqueSorted(groups.executorMode),
    other
  };
}

function groupsFromItems(items?: CoreAgentRuntimeCapabilityItem[] | null): RuntimeCapabilityGroups {
  const groups: RuntimeCapabilityGroups = { flat: [], taskType: [], issueProvider: [], toolPolicy: [], executorMode: [], other: {} };
  (items ?? []).forEach((item) => addGroup(groups, item.capabilityKind, item.capabilityValue));
  return finalizeGroups(groups);
}

function allReportedValues(groups: RuntimeCapabilityGroups): string[] {
  return uniqueSorted([
    ...groups.flat,
    ...groups.taskType,
    ...groups.issueProvider,
    ...groups.toolPolicy,
    ...groups.executorMode,
    ...Object.values(groups.other).flat()
  ]);
}

function intersection(left: string[], right: string[]): string[] {
  const rightSet = new Set(right);
  return left.filter((value) => rightSet.has(value));
}

function difference(left: string[], right: string[]): string[] {
  const rightSet = new Set(right);
  return left.filter((value) => !rightSet.has(value));
}

function hasWildcardScope(scopes?: CoreAgentAuthorizationScope[] | null, key?: keyof CoreAgentAuthorizationScope): boolean {
  return enabledScopes(scopes).some((scope) => {
    if (!key) return false;
    const value = normalizeValue(scope[key]);
    return value === undefined || value === '*';
  });
}

export function deriveRuntimeDiagnostics(input: {
  profile?: CoreAgentProfile;
  runtime?: NettyAgentRuntime;
  runtimeCapabilityProfile?: CoreAgentRuntimeCapabilityProfile;
  runtimeCapabilityItems?: CoreAgentRuntimeCapabilityItem[];
  runtimeLoad?: CoreAgentRuntimeLoadSnapshot;
  now?: Date;
}): RuntimeDiagnosticsResult {
  const issues: RuntimeDiagnosticIssue[] = [];
  const groups = groupsFromItems(input.runtimeCapabilityItems);
  const approvedCapabilityCodes = enabledCapabilities(input.profile?.capabilities);
  const disabledCapabilityCodes = disabledCapabilities(input.profile?.capabilities);
  const scopes = enabledScopes(input.profile?.authorizationScopes);
  const approvedTaskTypes = scopeValues(scopes, 'taskType');
  const approvedSystemCodes = scopeValues(scopes, 'systemCode');
  const reportedValues = allReportedValues(groups);

  if (!input.profile) {
    issues.push({ severity: 'ERROR', code: 'MISSING_CORE_PROFILE', message: 'Core Not created yet Agent profileDispatch information' });
  } else {
    if (input.profile.approvalStatus !== 'APPROVED') {
      issues.push({ severity: 'ERROR', code: 'NOT_APPROVED', message: `Core approvalStatus=${input.profile.approvalStatus}Dispatch information` });
    }
    if (!input.profile.enabled) {
      issues.push({ severity: 'ERROR', code: 'DISABLED_BY_CORE', message: 'Core enabled=false,Agent Task details' });
    }
    if (input.profile.riskStatus && input.profile.riskStatus !== 'NORMAL') {
      issues.push({ severity: 'ERROR', code: 'RISK_BLOCKED', message: `Core riskStatus=${input.profile.riskStatus}` });
    }
  }

  if (!input.runtime && !input.runtimeLoad) {
    issues.push({ severity: 'WARN', code: 'NO_RUNTIME_STATE', message: 'Not available yet Netty runtime or Core runtime load snapshot,dispatch score ' });
  }

  if (!input.runtimeCapabilityProfile && (input.runtimeCapabilityItems ?? []).length === 0) {
    issues.push({ severity: 'WARN', code: 'NO_REPORTED_CAPABILITY_PROFILE', message: 'Core Not available yet Agent runtime capability profile;Capability Dispatch information Source Flow,Agent Pool,Pool Member Agent and Runtime Eligibility ' });
  }

  const missingApproved = difference(approvedCapabilityCodes, reportedValues);
  if (approvedCapabilityCodes.length > 0 && reportedValues.length > 0 && missingApproved.length > 0) {
    issues.push({ severity: 'INFO', code: 'APPROVED_NOT_OBSERVED', message: `Admin approved,runtime Dispatch information${missingApproved.join(', ')}` });
  }

  const disabledButReported = intersection(disabledCapabilityCodes, reportedValues);
  if (disabledButReported.length > 0) {
    issues.push({ severity: 'INFO', code: 'DISABLED_CAPABILITY_REPORTED', message: `Core Disable runtime  Capability  Current dispatch):${disabledButReported.join(', ')}` });
  }

  if (approvedTaskTypes.length > 0) {
    const unapprovedTaskTypes = difference(groups.taskType, approvedTaskTypes);
    if (unapprovedTaskTypes.length > 0) {
      issues.push({ severity: 'WARN', code: 'UNAPPROVED_TASK_TYPE_REPORTED', message: `Runtime  authorization scope approve taskType:${unapprovedTaskTypes.join(', ')}` });
    }
  } else if (!hasWildcardScope(input.profile?.authorizationScopes, 'taskType') && groups.taskType.length > 0) {
    issues.push({ severity: 'WARN', code: 'NO_TASK_TYPE_SCOPE', message: 'Runtime  taskType Core authorization scope No data is currently available. taskType or wildcard.' });
  }

  if (approvedSystemCodes.length > 0) {
    const unapprovedProviders = difference(groups.issueProvider, approvedSystemCodes);
    if (unapprovedProviders.length > 0) {
      issues.push({ severity: 'WARN', code: 'UNAPPROVED_PROVIDER_REPORTED', message: `Runtime  system scope approve provider/system:${unapprovedProviders.join(', ')}` });
    }
  }

  const load = input.runtimeLoad;
  if (load?.draining || input.runtime?.draining) {
    issues.push({ severity: 'ERROR', code: 'AGENT_DRAINING', message: 'Agent  draining,Core dispatch Task details' });
  }
  const availableSlots = load?.availableSlots ?? input.runtime?.availableSlots;
  if (availableSlots !== undefined && availableSlots <= 0) {
    issues.push({ severity: 'WARN', code: 'NO_AVAILABLE_SLOTS', message: 'availableSlots=0Dispatch information' });
  }
  const utilization = load?.capacityUtilization ?? input.runtime?.capacityUtilization;
  if (utilization !== undefined && utilization >= 0.9) {
    issues.push({ severity: 'WARN', code: 'HIGH_CAPACITY_UTILIZATION', message: `capacityUtilization=${Math.round(utilization * 100)}%` });
  }
  const outboxPending = load?.outboxPending ?? input.runtime?.outboxPending;
  if (outboxPending !== undefined && outboxPending > 0) {
    issues.push({ severity: outboxPending >= 10 ? 'ERROR' : 'WARN', code: 'OUTBOX_BACKLOG', message: `result outbox  ${outboxPending} Dispatch information` });
  }
  const recoveryPending = load?.recoveryPendingAssignments ?? input.runtime?.recoveryPendingAssignments;
  if (recoveryPending !== undefined && recoveryPending > 0) {
    issues.push({ severity: 'WARN', code: 'RECOVERY_PENDING', message: ` ${recoveryPending} records assignment waiting recoveryDispatch information` });
  }

  const now = input.now ?? new Date();
  const heartbeatAt = load?.heartbeatAt ?? input.runtime?.lastHeartbeatAt;
  if (heartbeatAt) {
    const ageMs = now.getTime() - Date.parse(heartbeatAt);
    if (Number.isFinite(ageMs) && ageMs > 180_000) {
      issues.push({ severity: 'WARN', code: 'STALE_RUNTIME_LOAD', message: `runtime load heartbeat  ${Math.round(ageMs / 1000)} ` });
    }
  }

  const hasError = issues.some((issue) => issue.severity === 'ERROR');
  const hasWarn = issues.some((issue) => issue.severity === 'WARN');
  return {
    status: hasError ? 'BLOCKED' : hasWarn ? 'WARN' : 'OK',
    issues,
    approvedCapabilityCodes,
    approvedTaskTypes,
    approvedSystemCodes,
    reported: groups,
    effectiveCapabilityCodes: intersection(approvedCapabilityCodes, reportedValues),
    effectiveTaskTypes: approvedTaskTypes.length > 0 ? intersection(approvedTaskTypes, groups.taskType) : groups.taskType,
    effectiveIssueProviders: approvedSystemCodes.length > 0 ? intersection(approvedSystemCodes, groups.issueProvider) : groups.issueProvider
  };
}
