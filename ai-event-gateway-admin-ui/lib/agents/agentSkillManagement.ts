import type { CoreAgentProfile, CoreAgentRuntimeCapabilityItem, CoreAgentRuntimeLoadSnapshot, CoreAgentSkillDefinition } from '@/lib/types/core';
import { beginnerSkillLabel, normalizeCode } from '@/lib/dispatch-readiness/labels';

export interface AgentSkillManagementSummary {
  status: 'ready' | 'blocked' | 'waiting';
  title: string;
  description: string;
  dispatchableCapabilities: string[];
  missingGovernanceCapabilities: string[];
  missingRuntimeCapabilities: string[];
  missingRegistryCapabilities: string[];
  recommendedRestartCapabilities: string[];
  nextActions: Array<{ title: string; description: string; tone: 'primary' | 'warning' | 'neutral' }>;
}

export interface SkillRegistryManagementSummary {
  status: 'ready' | 'blocked' | 'waiting';
  title: string;
  description: string;
  selectedSkillCode?: string;
  selectedTaskTypes: string[];
  enabledSkillCount: number;
  disabledSkillCount: number;
  highRiskSkillCount: number;
  nextActions: Array<{ title: string; description: string; tone: 'primary' | 'warning' | 'neutral' }>;
}

function unique(values: Array<string | undefined | null>): string[] {
  return Array.from(new Set(values.map((value) => normalizeCode(value ?? '')).filter(Boolean))).sort();
}

function profileApprovedCapabilities(profile?: CoreAgentProfile | null): string[] {
  return unique((profile?.capabilities ?? [])
    .filter((capability) => capability.enabled !== false)
    .map((capability) => capability.capabilityCode));
}

function runtimeReportedCapabilities(items?: CoreAgentRuntimeCapabilityItem[] | null, load?: CoreAgentRuntimeLoadSnapshot | null): string[] {
  const runtimeLoadRecord = load?.runtimeLoad && typeof load.runtimeLoad === 'object' && !Array.isArray(load.runtimeLoad) ? load.runtimeLoad : {};
  const embeddedCapabilities = Array.isArray(runtimeLoadRecord.capabilities) ? runtimeLoadRecord.capabilities.filter((value): value is string => typeof value === 'string') : [];
  return unique([
    ...((items ?? [])
      .filter((item) => ['FLAT', 'TASKTYPE', 'TASK_TYPE'].includes(normalizeCode(item.capabilityKind)) || Boolean(item.capabilityValue))
      .map((item) => item.capabilityValue)),
    ...embeddedCapabilities
  ]);
}

function enabledSkillCodes(skills?: CoreAgentSkillDefinition[] | null): string[] {
  return unique((skills ?? []).filter((skill) => skill.enabled !== false).map((skill) => skill.skillCode));
}


function subtract(left: string[], right: string[]): string[] {
  const rightSet = new Set(right);
  return left.filter((item) => !rightSet.has(item));
}

export function buildAgentSkillManagementSummary(input: {
  profile?: CoreAgentProfile | null;
  runtimeConnected?: boolean;
  runtimeCapabilityItems?: CoreAgentRuntimeCapabilityItem[] | null;
  runtimeLoad?: CoreAgentRuntimeLoadSnapshot | null;
  skillDefinitions?: CoreAgentSkillDefinition[] | null;
}): AgentSkillManagementSummary {
  const governance = profileApprovedCapabilities(input.profile);
  const runtime = runtimeReportedCapabilities(input.runtimeCapabilityItems, input.runtimeLoad);
  const registry = enabledSkillCodes(input.skillDefinitions);
  const registryKnown = Array.isArray(input.skillDefinitions);
  // Legacy profile/runtime capability values do not grant dispatch qualification.
  // Canonical qualification comes from Core APPROVED Agent Capability assignments; Agent Pool membership remains the candidate boundary.
  const dispatchableCapabilities = governance;
  const missingGovernanceCapabilities: string[] = [];
  const missingRuntimeCapabilities: string[] = [];
  const missingRegistryCapabilities = registryKnown ? subtract(governance, registry) : [];
  const recommendedRestartCapabilities: string[] = [];

  const profileApproved = normalizeCode(input.profile?.approvalStatus) === 'APPROVED';
  const profileEnabled = input.profile?.enabled !== false;
  const riskNormal = !normalizeCode(input.profile?.riskStatus) || normalizeCode(input.profile?.riskStatus) === 'NORMAL';
  const runtimeConnected = input.runtimeConnected === true;

  const nextActions: AgentSkillManagementSummary['nextActions'] = [];
  if (!input.profile) {
    nextActions.push({ title: 'Create Agent Profile', description: 'Core No data is currently available. profile Agent Dispatch information', tone: 'warning' });
  } else if (!profileApproved || !profileEnabled || !riskNormal) {
    nextActions.push({ title: ' Agent Status', description: 'Approval must is APPROVED,Enabled must is true,Risk must is NORMAL.', tone: 'warning' });
  }
  if (!runtimeConnected) {
    nextActions.push({ title: ' Agent runtime ', description: ' Core runtime Dispatch information', tone: 'warning' });
  }
  if (runtime.length === 0 && dispatchableCapabilities.length > 0) {
    nextActions.push({ title: 'Runtime Capability ', description: 'Runtime  Capability  Current Agent Pool dispatch.', tone: 'neutral' });
  }
  if (missingRegistryCapabilities.length) {
    nextActions.push({ title: ' Capability ', description: `${missingRegistryCapabilities.join(', ')}  Agent  Capability Registry.`, tone: 'neutral' });
  }
  if (dispatchableCapabilities.length) {
    nextActions.push({ title: 'View Agent Pool and Runtime Eligibility', description: 'Confirm that this Agent belongs to the target Agent Pool and that connection, capacity, credential, and backoff status are healthy.', tone: 'primary' });
  }
  if (nextActions.length === 0) {
    nextActions.push({ title: 'Check Agent Pool membership', description: 'Capability Review the configuration and try again. Agent Target Agent Pool.', tone: 'neutral' });
  }

  if (!input.profile || !profileApproved || !profileEnabled || !riskNormal) {
    return {
      status: 'blocked',
      title: 'this Agent Dispatch information',
      description: 'First, process Agent,Approval,Enabled,Risk or CredentialAgent Pool membership.',
      dispatchableCapabilities,
      missingGovernanceCapabilities,
      missingRuntimeCapabilities,
      missingRegistryCapabilities,
      recommendedRestartCapabilities,
      nextActions
    };
  }
  if (!runtimeConnected) {
    return {
      status: 'waiting',
      title: 'Agent  Runtime Not connected',
      description: 'Capability and Registry  Agent Runtime unavailable.',
      dispatchableCapabilities,
      missingGovernanceCapabilities,
      missingRuntimeCapabilities,
      missingRegistryCapabilities,
      recommendedRestartCapabilities,
      nextActions
    };
  }
  return {
    status: 'ready',
    title: 'Agent managementStatusand Runtime available',
    description: 'Capability  Agent Pool membership ',
    dispatchableCapabilities,
    missingGovernanceCapabilities,
    missingRuntimeCapabilities,
    missingRegistryCapabilities,
    recommendedRestartCapabilities,
    nextActions
  };
}

export function buildSkillRegistryManagementSummary(input: {
  skills?: CoreAgentSkillDefinition[] | null;
  selectedSkill?: CoreAgentSkillDefinition | null;
}): SkillRegistryManagementSummary {
  const skills = input.skills ?? [];
  const enabledSkills = skills.filter((skill) => skill.enabled !== false);
  const disabledSkills = skills.filter((skill) => skill.enabled === false);
  const highRiskSkills = skills.filter((skill) => ['HIGH', 'CRITICAL'].includes(normalizeCode(skill.riskLevel)) || skill.requiresHumanApproval || skill.maskingRequired);
  const selectedSkillCode = normalizeCode(input.selectedSkill?.skillCode);
  const selectedTaskTypes = unique([selectedSkillCode, ...(input.selectedSkill?.taskTypes ?? [])]);
  const nextActions: SkillRegistryManagementSummary['nextActions'] = [];

  if (!selectedSkillCode) {
    nextActions.push({ title: 'Review the configuration and try again. Dispatch Flows', description: ' Capability Registry DispatchPlease from Source Flow and Agent Pool ', tone: 'primary' });
  } else if (input.selectedSkill?.enabled === false) {
    nextActions.push({ title: 'Enablethis advanced policy definition', description: 'Disable Capability  Current routing.', tone: 'warning' });
  } else {
    nextActions.push({ title: ' Dispatch Flow', description: `${selectedSkillCode} is Capability Review the configuration and try again. Source Flow and Agent Pool.`, tone: 'primary' });
    nextActions.push({ title: 'Check Agent Pool membership', description: 'Capability  Agent addAgent Pool; Please in Agent Pool Members.', tone: 'warning' });
    nextActions.push({ title: 'View dispatch evidence', description: 'from Source Flow/Agent Pool or Task Evidence  Pool membership and Runtime Eligibility.', tone: 'neutral' });
  }

  if (skills.length === 0) {
    return {
      status: 'waiting',
      title: 'Not created yet Capability ',
      description: 'Create Agent Create Capability Registry data.',
      selectedSkillCode,
      selectedTaskTypes,
      enabledSkillCount: 0,
      disabledSkillCount: 0,
      highRiskSkillCount: 0,
      nextActions
    };
  }
  if (selectedSkillCode && input.selectedSkill?.enabled === false) {
    return {
      status: 'blocked',
      title: `${beginnerSkillLabel(selectedSkillCode)} currentDisable`,
      description: 'Disable Capability  Current Agent Pool dispatch.',
      selectedSkillCode,
      selectedTaskTypes,
      enabledSkillCount: enabledSkills.length,
      disabledSkillCount: disabledSkills.length,
      highRiskSkillCount: highRiskSkills.length,
      nextActions
    };
  }
  return {
    status: 'ready',
    title: selectedSkillCode ? `${beginnerSkillLabel(selectedSkillCode)} in Capability Registry ` : 'Capability Registry available',
    description: 'Capability Registry  Agent Dispatch information Source Flow,Agent Pool membership and Runtime Eligibility.',
    selectedSkillCode,
    selectedTaskTypes,
    enabledSkillCount: enabledSkills.length,
    disabledSkillCount: disabledSkills.length,
    highRiskSkillCount: highRiskSkills.length,
    nextActions
  };
}
