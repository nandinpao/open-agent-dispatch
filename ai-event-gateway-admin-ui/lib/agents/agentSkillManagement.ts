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

function intersect(left: string[], right: string[]): string[] {
  const rightSet = new Set(right);
  return left.filter((item) => rightSet.has(item));
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
  // Admin UI / Core approval is the dispatch source of truth. Runtime capability values are
  // diagnostics only and must not make approved capabilities non-dispatchable.
  const dispatchableCapabilities = registryKnown && registry.length > 0 ? intersect(governance, registry) : governance;
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
  if (missingGovernanceCapabilities.length) {
    nextActions.push({ title: '一鍵核准缺少的能力', description: `缺少 ${missingGovernanceCapabilities.join(', ')} 的 Governance 授權。`, tone: 'primary' });
  }
  if (runtime.length === 0 && dispatchableCapabilities.length > 0) {
    nextActions.push({ title: 'Runtime capability observation is optional', description: '能力由 Admin UI/Core 管理；runtime 未回報能力不會阻擋已核准能力派工。', tone: 'neutral' });
  }
  if (missingRegistryCapabilities.length) {
    nextActions.push({ title: '補齊進階政策定義', description: `${missingRegistryCapabilities.join(', ')} 出現在 Agent advanced diagnostics，但尚未成為啟用中的 advanced policy definition。`, tone: 'neutral' });
  }
  if (dispatchableCapabilities.length) {
    nextActions.push({ title: '執行 Dispatch Flow evidence', description: '選擇具體任務場景，檢查 Task requiredCapabilities 是否與此 Agent 對齊。', tone: 'primary' });
  }
  if (nextActions.length === 0) {
    nextActions.push({ title: '先建立能力與 Agent 授權', description: '目前沒有足夠資料判斷此 Agent 可接哪些任務。', tone: 'neutral' });
  }

  if (!input.profile || !profileApproved || !profileEnabled || !riskNormal) {
    return {
      status: 'blocked',
      title: '此 Agent 尚未通過治理，不能派工',
      description: '請先處理 Agent Profile、Approval、Enabled、Risk 或 Credential，再看能力授權。',
      dispatchableCapabilities,
      missingGovernanceCapabilities,
      missingRuntimeCapabilities,
      missingRegistryCapabilities,
      recommendedRestartCapabilities,
      nextActions
    };
  }
  if (!runtimeConnected || missingGovernanceCapabilities.length || missingRegistryCapabilities.length) {
    return {
      status: dispatchableCapabilities.length ? 'waiting' : 'blocked',
      title: dispatchableCapabilities.length ? '此 Agent 部分能力可派工，仍有缺口' : '此 Agent 目前還不能接指定技能任務',
      description: '請依下方缺口先修後台 Flow Agent approval 或進階政策定義；runtime capability observation 不作為業務能力阻擋。',
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
    title: '此 Agent 已有可派工能力',
    description: '下方列出的項目已由 Admin UI/Core 核准；runtime capability observation 僅供診斷，實際派工仍以 Dispatch Flow Agent assignment / Flow Agent approval / Dispatch Policy 為準。',
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
    nextActions.push({ title: '一般派工請先到 Dispatch Flows', description: '這裡是進階政策頁；新增可派工角色與 Agent 授權請從 Dispatch Flows 開始。', tone: 'primary' });
  } else if (input.selectedSkill?.enabled === false) {
    nextActions.push({ title: '啟用此 advanced policy definition', description: '停用的進階政策不應進入 policy-aware compatibility diagnostics。', tone: 'warning' });
  } else {
    nextActions.push({ title: '檢查對應 Dispatch Flow', description: `${selectedSkillCode} 只是進階政策定義；請確認 Task Type 已對應到後台 Dispatch Flow。`, tone: 'primary' });
    nextActions.push({ title: '檢查 Agent Flow Agent approval', description: 'Agent 必須由後台授予並核准 profile Flow Agent approval，不能只靠 runtime keyword。', tone: 'warning' });
    nextActions.push({ title: '執行 Dispatch Recipes / Readiness', description: '選擇任務場景與 Agent，讓系統確認 Task、Profile、Flow Agent approval、Runtime 是否對齊。', tone: 'neutral' });
  }

  if (skills.length === 0) {
    return {
      status: 'waiting',
      title: '尚未建立進階政策定義',
      description: '一般派工不需要先建立此頁資料；若要治理風險、資料類型、遮蔽或人工審核，再建立 advanced policy definition。',
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
      description: '停用的 advanced policy definition 不能作為 policy-aware compatibility diagnostics 的有效規則。',
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
    title: selectedSkillCode ? `${beginnerSkillLabel(selectedSkillCode)} 已在進階政策定義中` : '進階政策定義可用',
    description: 'Advanced policy definitions 只負責補充風險、資料類型、遮蔽、人工審核與相容性診斷；是否能派工仍要看 Dispatch Flow、Agent Flow Agent approval、Capability approval 與 Runtime facts。',
    selectedSkillCode,
    selectedTaskTypes,
    enabledSkillCount: enabledSkills.length,
    disabledSkillCount: disabledSkills.length,
    highRiskSkillCount: highRiskSkills.length,
    nextActions
  };
}
