import type {
  CoreAgentAuthorizationScope,
  CoreAgentCapability,
  CoreAgentProfile,
  CoreAgentRuntimeCapabilityItem,
  CoreAgentRuntimeCapabilityProfile,
  CoreAgentRuntimeLoadSnapshot
} from '@/lib/types/core';
import type { NettyAgentRuntime } from '@/lib/types/nettyRuntime';

export type DispatchGateStatus = 'PASS' | 'WARN' | 'BLOCK';

export interface DispatchPolicyGate {
  code: string;
  label: string;
  status: DispatchGateStatus;
  detail: string;
}

export interface DispatchPolicyProbe {
  taskType?: string;
  issueProvider?: string;
  siteCode?: string;
  requiredCapabilities?: string[];
  toolPolicy?: string;
}

export interface DispatchPolicyCapabilityGroups {
  flat: string[];
  taskType: string[];
  issueProvider: string[];
  toolPolicy: string[];
  executorMode: string[];
  other: Record<string, string[]>;
}

export interface EffectiveDispatchPolicy {
  status: 'ELIGIBLE' | 'DEGRADED' | 'BLOCKED';
  gates: DispatchPolicyGate[];
  approvedCapabilities: string[];
  approvedTaskTypes: string[];
  approvedProviders: string[];
  approvedSites: string[];
  reported: DispatchPolicyCapabilityGroups;
  effectiveCapabilities: string[];
  effectiveTaskTypes: string[];
  effectiveProviders: string[];
  effectiveToolPolicies: string[];
  effectiveExecutorModes: string[];
  scoreHints: Array<{ code: string; label: string; value: string; weight: 'positive' | 'negative' | 'neutral' }>;
  probe?: {
    matched: boolean;
    missing: string[];
    matchedCapabilities: string[];
  };
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

function enabledScopes(scopes?: CoreAgentAuthorizationScope[] | null): CoreAgentAuthorizationScope[] {
  return (scopes ?? []).filter((scope) => scope.enabled !== false);
}

function scopeValues(scopes: CoreAgentAuthorizationScope[], key: keyof CoreAgentAuthorizationScope): string[] {
  return uniqueSorted(scopes
    .map((scope) => scope[key])
    .map(normalizeKey)
    .filter((value) => value !== '*'));
}

function hasWildcardScope(scopes: CoreAgentAuthorizationScope[], key: keyof CoreAgentAuthorizationScope): boolean {
  return scopes.some((scope) => {
    const value = normalizeValue(scope[key]);
    return value === undefined || value === '*';
  });
}

function addGroup(groups: DispatchPolicyCapabilityGroups, kind: string, value: string): void {
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

function groupsFromItems(items?: CoreAgentRuntimeCapabilityItem[] | null): DispatchPolicyCapabilityGroups {
  const groups: DispatchPolicyCapabilityGroups = { flat: [], taskType: [], issueProvider: [], toolPolicy: [], executorMode: [], other: {} };
  (items ?? []).forEach((item) => addGroup(groups, item.capabilityKind, item.capabilityValue));
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

function allReportedValues(groups: DispatchPolicyCapabilityGroups): string[] {
  return uniqueSorted([
    ...groups.flat,
    ...groups.taskType,
    ...groups.issueProvider,
    ...groups.toolPolicy,
    ...groups.executorMode,
    ...Object.values(groups.other).flat()
  ]);
}

function gate(status: DispatchGateStatus, code: string, label: string, detail: string): DispatchPolicyGate {
  return { status, code, label, detail };
}

function addScoreHint(policy: EffectiveDispatchPolicy, code: string, label: string, value: string, weight: 'positive' | 'negative' | 'neutral'): void {
  policy.scoreHints.push({ code, label, value, weight });
}

function normalizeProbeCapabilities(probe?: DispatchPolicyProbe): string[] {
  return uniqueSorted(probe?.requiredCapabilities?.map(normalizeKey) ?? []);
}

export function buildScopeCsv(scopes?: CoreAgentAuthorizationScope[] | null): string {
  return (scopes ?? [])
    .filter((scope) => scope.enabled !== false)
    .map((scope) => [scope.systemCode ?? '*', scope.taskType ?? '*', scope.siteCode].filter(Boolean).join('/'))
    .join(',');
}

export function buildCapabilityCsv(capabilities?: CoreAgentCapability[] | null): string {
  return enabledCapabilities(capabilities).join(',');
}

export function deriveEffectiveDispatchPolicy(input: {
  profile?: CoreAgentProfile;
  runtime?: NettyAgentRuntime;
  runtimeCapabilityProfile?: CoreAgentRuntimeCapabilityProfile;
  runtimeCapabilityItems?: CoreAgentRuntimeCapabilityItem[];
  runtimeLoad?: CoreAgentRuntimeLoadSnapshot;
  probe?: DispatchPolicyProbe;
}): EffectiveDispatchPolicy {
  const reported = groupsFromItems(input.runtimeCapabilityItems);
  const scopes = enabledScopes(input.profile?.authorizationScopes);
  const approvedCapabilities = enabledCapabilities(input.profile?.capabilities);
  const approvedTaskTypes = scopeValues(scopes, 'taskType');
  const approvedProviders = scopeValues(scopes, 'systemCode');
  const approvedSites = scopeValues(scopes, 'siteCode');
  const reportedValues = allReportedValues(reported);

  // Admin UI / Core is the source of truth for business dispatch capabilities.
  // Runtime-reported capability values are optional observations only; they must not reduce
  // the set of approved capabilities or make an approved capability look blocked.
  const effectiveCapabilities = approvedCapabilities;
  const effectiveTaskTypes = approvedTaskTypes;
  const effectiveProviders = approvedProviders;
  const effectiveToolPolicies = reported.toolPolicy;
  const effectiveExecutorModes = reported.executorMode;

  const policy: EffectiveDispatchPolicy = {
    status: 'ELIGIBLE',
    gates: [],
    approvedCapabilities,
    approvedTaskTypes,
    approvedProviders,
    approvedSites,
    reported,
    effectiveCapabilities,
    effectiveTaskTypes,
    effectiveProviders,
    effectiveToolPolicies,
    effectiveExecutorModes,
    scoreHints: []
  };

  if (!input.profile) {
    policy.gates.push(gate('BLOCK', 'PROFILE', 'Core Profile', 'Core 尚未建立 Agent profile，不能納入治理式派工。'));
  } else {
    policy.gates.push(gate(input.profile.approvalStatus === 'APPROVED' ? 'PASS' : 'BLOCK', 'APPROVAL', 'Approval', `approvalStatus=${input.profile.approvalStatus}`));
    policy.gates.push(gate(input.profile.enabled ? 'PASS' : 'BLOCK', 'ENABLED', 'Enabled', `enabled=${input.profile.enabled}`));
    policy.gates.push(gate(!input.profile.riskStatus || input.profile.riskStatus === 'NORMAL' ? 'PASS' : 'BLOCK', 'RISK', 'Risk', `riskStatus=${input.profile.riskStatus ?? 'NORMAL'}`));
    const credentialStatus = input.profile.credential?.credentialStatus ?? 'MISSING';
    policy.gates.push(gate(credentialStatus === 'ACTIVE' ? 'PASS' : 'WARN', 'CREDENTIAL', 'Credential', `credentialStatus=${credentialStatus}`));
  }

  const runtimeConnected = input.runtime?.connected === true || Boolean(input.runtimeLoad?.heartbeatAt);
  policy.gates.push(gate(runtimeConnected ? 'PASS' : 'WARN', 'RUNTIME', 'Runtime', runtimeConnected ? 'runtime session/load observed' : '沒有 Netty runtime 或 Core runtime load snapshot'));

  const hasReportedSkill = reportedValues.length > 0 || Boolean(input.runtimeCapabilityProfile?.capabilityProfile);
  policy.gates.push(gate('PASS', 'CAPABILITY_SOURCE_OF_TRUTH', 'Capability authority', 'Admin UI/Core approved capabilities are the dispatch source of truth. Runtime capability reports are diagnostic only.'));
  if (!hasReportedSkill) {
    addScoreHint(policy, 'runtimeCapabilityObservation', 'Runtime capability observation', 'not observed / optional', 'neutral');
  }

  const availableSlots = input.runtimeLoad?.availableSlots ?? input.runtime?.availableSlots;
  if (availableSlots !== undefined) {
    policy.gates.push(gate(availableSlots > 0 ? 'PASS' : 'WARN', 'SLOTS', 'Available Slots', `availableSlots=${availableSlots}`));
    addScoreHint(policy, 'availableSlots', 'Available slots', String(availableSlots), availableSlots > 0 ? 'positive' : 'negative');
  } else {
    policy.gates.push(gate('WARN', 'SLOTS', 'Available Slots', 'availableSlots 尚未回報'));
  }

  const utilization = input.runtimeLoad?.capacityUtilization ?? input.runtime?.capacityUtilization;
  if (utilization !== undefined) {
    addScoreHint(policy, 'capacityUtilization', 'Capacity utilization', `${Math.round(utilization * 100)}%`, utilization >= 0.9 ? 'negative' : 'positive');
  }

  const draining = input.runtimeLoad?.draining ?? input.runtime?.draining;
  policy.gates.push(gate(draining ? 'BLOCK' : 'PASS', 'DRAINING', 'Draining', draining ? 'Agent 正在 draining，不應派新任務。' : 'not draining'));

  const outboxPending = input.runtimeLoad?.outboxPending ?? input.runtime?.outboxPending;
  if (outboxPending !== undefined) {
    addScoreHint(policy, 'outboxPending', 'Outbox pending', String(outboxPending), outboxPending > 0 ? 'negative' : 'positive');
  }

  const recoveryPending = input.runtimeLoad?.recoveryPendingAssignments ?? input.runtime?.recoveryPendingAssignments;
  if (recoveryPending !== undefined) {
    addScoreHint(policy, 'recoveryPendingAssignments', 'Recovery pending', String(recoveryPending), recoveryPending > 0 ? 'negative' : 'positive');
  }

  const probe = input.probe;
  if (probe) {
    const missing: string[] = [];
    const probeCapabilities = normalizeProbeCapabilities(probe);
    const effectiveCapabilitySet = new Set(uniqueSorted([...effectiveCapabilities, ...effectiveTaskTypes, ...effectiveProviders, ...effectiveToolPolicies, ...effectiveExecutorModes]));
    const matchedCapabilities: string[] = [];

    probeCapabilities.forEach((capability) => {
      if (effectiveCapabilitySet.has(capability)) matchedCapabilities.push(capability);
      else missing.push(`capability:${capability}`);
    });

    const taskType = normalizeKey(probe.taskType);
    if (taskType) {
      const wildcard = hasWildcardScope(scopes, 'taskType');
      const approved = wildcard || approvedTaskTypes.includes(taskType) || approvedTaskTypes.length === 0;
      if (!approved) missing.push(`taskType:${taskType}`);
    }

    const provider = normalizeKey(probe.issueProvider);
    if (provider) {
      const wildcard = hasWildcardScope(scopes, 'systemCode');
      const approved = wildcard || approvedProviders.includes(provider) || approvedProviders.length === 0;
      if (!approved) missing.push(`provider:${provider}`);
    }

    const siteCode = normalizeKey(probe.siteCode);
    if (siteCode) {
      const wildcard = hasWildcardScope(scopes, 'siteCode');
      const approved = wildcard || approvedSites.includes(siteCode) || approvedSites.length === 0;
      if (!approved) missing.push(`site:${siteCode}`);
    }

    const toolPolicy = normalizeKey(probe.toolPolicy);
    if (toolPolicy && reported.toolPolicy.length > 0 && !reported.toolPolicy.includes(toolPolicy)) {
      missing.push(`toolPolicy:${toolPolicy}`);
    }

    policy.probe = { matched: missing.length === 0, missing, matchedCapabilities };
    policy.gates.push(gate(missing.length === 0 ? 'PASS' : 'WARN', 'POLICY_PROBE', 'Policy Probe', missing.length === 0 ? 'probe matched Admin-managed effective policy' : `missing ${missing.join(', ')}`));
  }

  if (policy.gates.some((item) => item.status === 'BLOCK')) policy.status = 'BLOCKED';
  else if (policy.gates.some((item) => item.status === 'WARN') || policy.scoreHints.some((item) => item.weight === 'negative')) policy.status = 'DEGRADED';
  else policy.status = 'ELIGIBLE';

  return policy;
}
