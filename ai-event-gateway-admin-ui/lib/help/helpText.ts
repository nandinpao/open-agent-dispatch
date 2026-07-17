export interface HelpEntry {
  label: string;
  description: string;
  example?: string;
  learnMoreHref?: string;
}

export const helpText = {
  assignmentProfile: {
    label: 'Supply Profile',
    description: '派工角色模板。它把 sourceSystem、taskType、required policy、required capability 組成一個可被 Agent 取得資格的派工條件集合。',
    example: '例如 ERP_BUSINESS_REVIEWER 代表可處理 ERP business review 任務的 Profile。',
    learnMoreHref: '/dispatch-flows'
  },
  policyBinding: {
    label: 'Policy Binding',
    description: 'Profile 與 Policy 的關聯設定。它決定某個 Profile 必須、允許、拒絕或有條件套用哪些治理規則。',
    example: '例如 ERP_BUSINESS_REVIEWER 綁定 REQUIRED 的 ERP_BUSINESS_REVIEW_POLICY。'
  },
  advancedPolicy: {
    label: 'Dispatch Policy Definition',
    description: '治理規則本體。Policy 定義風險、資料保護、人工審核、能力限制或合規條件；它不直接指派 Agent。',
    learnMoreHref: '/dispatch-flows'
  },
  agentQualification: {
    label: 'Agent Qualification',
    description: 'Agent 取得某個 Supply Profile 的授權狀態。只有 APPROVED qualification 會進入 routing eligibility。',
    example: 'PENDING 可 Remove；APPROVED / SUSPENDED 才使用 Revoke 保留歷史。'
  },
  agentCapability: {
    label: 'Agent Capability',
    description: 'Agent 取得某項能力的治理狀態。Capability 必須來自 Capability Catalog；PENDING / DECLARED 不可派工，只有 APPROVED capability 可進入 routing eligibility。',
    example: '例如 ERP_BUSINESS_REVIEW capability 必須先 request，再由管理員 approve。'
  },
  runtimeEligibility: {
    label: 'Runtime Eligibility',
    description: 'Agent 在執行期是否真的可派工。即使 Profile 已核准，仍可能因 offline、capacity full、backoff、draining、credential 或 runtime feature 不足而 blocked。'
  },
  dispatchRecipe: {
    label: 'Dispatch Recipe',
    description: '派工規則與測試模板。Recipe 說明任務來源、任務類型、required profile、runtime features、fallback 與測試 payload。',
    learnMoreHref: '/testing/dispatch-recipes'
  },
  dispatchErrorCode: {
    label: 'Dispatch Error Code',
    description: 'Operator 可理解的 DISPATCH_* 錯誤碼。它把 routing / governance / runtime 的失敗原因轉成可篩選、可導向修復的分類。'
  },
  removeVsRevoke: {
    label: 'Remove vs Revoke',
    description: 'Remove 用於尚未生效的 DRAFT / PENDING assignment；Revoke 用於已生效或曾生效的 APPROVED / SUSPENDED qualification，必須保留 audit history。'
  },
  suspendVsDisable: {
    label: 'Suspend vs Disable',
    description: 'Suspend 通常是暫停單一 Agent qualification；Disable 通常是停用整個 Profile、Policy 或資源，影響範圍更大，應先看 Impact Preview。'
  },
  simulationVsDebug: {
    label: 'Simulation vs Debug',
    description: 'Simulation 是用範例 payload 或條件預演派工結果；Debug 是工程診斷資料，例如 raw JSON、trace、score breakdown，預設應收合。'
  },
  impactPreview: {
    label: 'Impact Preview',
    description: '操作前的影響預覽。Disable、Delete、Remove Binding、Suspend、Revoke 前應先確認會影響多少 Policy、Agent、Task Type 與 routing eligibility。'
  },
  troubleshootingWizard: {
    label: 'Troubleshooting Wizard',
    description: '針對 Task 無法派工的步驟化診斷流程，依序檢查 Task Requirement、Supply Profile、Policy Binding、Agent Qualification、Runtime Eligibility 與 Suggested Fix。'
  }
} as const satisfies Record<string, HelpEntry>;

export type HelpTextKey = keyof typeof helpText;

export function getHelpText(key: HelpTextKey): HelpEntry {
  return helpText[key];
}
