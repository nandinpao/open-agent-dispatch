import { CapabilityResolutionMatrix } from '@/components/dispatch-evidence/StandardRelationshipComponents';
import type { CoreAgentCapability, CoreAgentProfile, CoreAgentRuntimeCapabilityItem, CoreAgentRuntimeLoadSnapshot, CoreAgentSkillDefinition } from '@/lib/types/core';
import { agentBeginnerHeadline, beginnerToneClass, buildCapabilityMatrix } from '@/lib/dispatch-readiness/beginnerWorkflow';

export function AgentCapabilityMatrix({
  profile,
  runtimeCapabilityItems,
  runtimeLoad,
  skillDefinitions,
  taskRequiredCapabilities
}: Readonly<{
  profile?: CoreAgentProfile | null;
  runtimeCapabilityItems?: CoreAgentRuntimeCapabilityItem[] | null;
  runtimeLoad?: CoreAgentRuntimeLoadSnapshot | null;
  skillDefinitions?: CoreAgentSkillDefinition[] | null;
  taskRequiredCapabilities?: string[] | null;
}>) {
  const headline = agentBeginnerHeadline(profile, runtimeLoad);
  const rows = buildCapabilityMatrix({
    taskRequiredCapabilities: taskRequiredCapabilities ?? [],
    profileCapabilities: profile?.capabilities as CoreAgentCapability[] | undefined,
    runtimeCapabilities: runtimeCapabilityItems,
    runtimeLoad,
    skillCodes: skillDefinitions?.map((skill) => skill.skillCode)
  });

  return (
    <div className="space-y-3">
      <div className={`rounded-2xl border px-4 py-3 text-sm font-bold ${beginnerToneClass(headline.tone)}`}>
        <div>{headline.title}</div>
        <div className="mt-1 text-xs font-normal leading-5 opacity-80">{headline.description}</div>
      </div>
      <CapabilityResolutionMatrix
        rows={rows}
        title="Agent × Capability × Task 能力矩陣"
        description="這裡用新手角度回答：Governance 核准是否允許此 Agent 接收明確 Capability 或 Source Baseline Task。Runtime 回報僅供診斷，不會自行擴大業務權限。"
      />
    </div>
  );
}
