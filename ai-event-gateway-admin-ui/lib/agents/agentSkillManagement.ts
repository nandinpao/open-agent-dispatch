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
  // Capability values are reference/search metadata in Current routing. They do not define
  // Pool membership and must not make an otherwise eligible Agent dispatchable or blocked.
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
    nextActions.push({ title: '先建立或核准 Agent Profile', description: 'Core 沒有 profile 時，Agent 不能成為治理上可派工對象。', tone: 'warning' });
  } else if (!profileApproved || !profileEnabled || !riskNormal) {
    nextActions.push({ title: '修正 Agent 治理狀態', description: 'Approval 必須為 APPROVED、Enabled 必須為 true、Risk 必須為 NORMAL。', tone: 'warning' });
  }
  if (!runtimeConnected) {
    nextActions.push({ title: '讓 Agent runtime 重新連線', description: '即使 Core 已授權，runtime 不在線也無法派工。', tone: 'warning' });
  }
  if (runtime.length === 0 && dispatchableCapabilities.length > 0) {
    nextActions.push({ title: 'Runtime Capability 僅供參考', description: 'Runtime 未回報 Capability 標籤不會阻擋 Current Agent Pool 派工。', tone: 'neutral' });
  }
  if (missingRegistryCapabilities.length) {
    nextActions.push({ title: '補齊 Capability 參考資料', description: `${missingRegistryCapabilities.join(', ')} 出現在 Agent 診斷資料，但尚未存在於 Capability Registry。`, tone: 'neutral' });
  }
  if (dispatchableCapabilities.length) {
    nextActions.push({ title: '查看工作池與 Runtime Eligibility', description: '確認此 Agent 已加入目標 Agent Pool，且連線、容量、Credential 與 Backoff 狀態可用。', tone: 'primary' });
  }
  if (nextActions.length === 0) {
    nextActions.push({ title: '檢查 Agent Pool membership', description: 'Capability 資料不是派工必要條件；請確認此 Agent 是否已加入目標工作池。', tone: 'neutral' });
  }

  if (!input.profile || !profileApproved || !profileEnabled || !riskNormal) {
    return {
      status: 'blocked',
      title: '此 Agent 尚未通過治理，不能派工',
      description: '請先處理 Agent、Approval、Enabled、Risk 或 Credential，再檢查工作池 membership。',
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
      title: 'Agent 已核准，但 Runtime 尚未連線',
      description: 'Capability 與 Registry 缺口只作參考；目前真正阻擋條件是 Agent Runtime 不可用。',
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
    title: 'Agent 管理狀態與 Runtime 可用',
    description: 'Capability 標籤僅供查詢與治理參考；正式候選範圍仍由 Agent Pool membership 決定。',
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
    nextActions.push({ title: '一般派工請先到 Dispatch Flows', description: '這裡是 Capability Registry 參考頁；正式派工設定請從 Source Flow 與 Agent Pool 開始。', tone: 'primary' });
  } else if (input.selectedSkill?.enabled === false) {
    nextActions.push({ title: '啟用此 advanced policy definition', description: '停用的 Capability 參考資料不再用於搜尋與治理呈現，但不影響 Current routing。', tone: 'warning' });
  } else {
    nextActions.push({ title: '檢查對應 Dispatch Flow', description: `${selectedSkillCode} 是 Capability 參考資料；正式派工請確認 Source Flow 與 Agent Pool。`, tone: 'primary' });
    nextActions.push({ title: '檢查 Agent Pool membership', description: 'Capability 不會自動把 Agent 加入工作池；請在 Agent Pool 明確加入成員。', tone: 'warning' });
    nextActions.push({ title: '查看派工證據', description: '從 Source Flow／Agent Pool 或 Task Evidence 檢查 Pool membership 與 Runtime Eligibility。', tone: 'neutral' });
  }

  if (skills.length === 0) {
    return {
      status: 'waiting',
      title: '尚未建立 Capability 參考資料',
      description: '一般派工不需要先建立此頁資料；若要補充 Agent 能力描述、搜尋與治理資訊，再建立 Capability Registry 資料。',
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
      title: `${beginnerSkillLabel(selectedSkillCode)} 目前停用`,
      description: '停用的 Capability 不再顯示為有效參考資料，但不會阻擋 Current Agent Pool 派工。',
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
    title: selectedSkillCode ? `${beginnerSkillLabel(selectedSkillCode)} 已在 Capability Registry 中` : 'Capability Registry 可用',
    description: 'Capability Registry 只補充 Agent 描述、搜尋與治理資訊；是否能派工仍要看 Source Flow、Agent Pool membership 與 Runtime Eligibility。',
    selectedSkillCode,
    selectedTaskTypes,
    enabledSkillCount: enabledSkills.length,
    disabledSkillCount: disabledSkills.length,
    highRiskSkillCount: highRiskSkills.length,
    nextActions
  };
}
