export type GovernanceActionTone = 'danger' | 'warning' | 'primary' | 'neutral';

export type GovernanceActionKind =
  | 'approve'
  | 'reject'
  | 'remove'
  | 'suspend'
  | 'resume'
  | 'revoke'
  | 'verify'
  | 'trust'
  | 'retire'
  | 'execute';

export interface GovernanceActionSpec {
  kind: GovernanceActionKind;
  title: string;
  description: string;
  confirmLabel: string;
  tone: GovernanceActionTone;
  minReasonLength: number;
  reasonLabel: string;
  reasonPlaceholder: string;
  requiresEvidence?: boolean;
  evidenceLabel?: string;
  confirmationPhrase?: string;
}

const defaultReasonCopy: Record<GovernanceActionKind, string> = {
  approve: '請說明核准依據、檢查結果與責任歸屬。',
  reject: '請說明拒絕原因與後續處理建議。',
  remove: '請說明為何移除尚未生效的申請。',
  suspend: '請說明暫停原因、影響範圍與恢復條件。',
  resume: '請說明恢復依據與已完成的檢查。',
  revoke: '請說明撤銷原因、影響範圍與替代處理方式。',
  verify: '請說明驗證依據、probe 或人工檢查結果。',
  trust: '請說明信任依據、驗證結果與可接受風險。',
  retire: '請說明退役原因與影響處理。',
  execute: '請說明執行原因與風險確認。',
};

export function governanceActionSpec({
  kind,
  targetLabel,
  subjectLabel,
  confirmationPhrase,
  requiresEvidence = false,
}: Readonly<{
  kind: GovernanceActionKind;
  targetLabel: string;
  subjectLabel?: string;
  confirmationPhrase?: string;
  requiresEvidence?: boolean;
}>): GovernanceActionSpec {
  const normalizedTarget = targetLabel || 'governed item';
  const subject = subjectLabel ? `${subjectLabel} · ` : '';
  const base = `${subject}${normalizedTarget}`;
  switch (kind) {
    case 'approve':
      return {
        kind,
        title: `Approve ${base}?`,
        description: 'Approve 會讓此項目成為正式治理資料，後續 routing / eligibility 可能會使用它。',
        confirmLabel: 'Approve',
        tone: 'primary',
        minReasonLength: 12,
        reasonLabel: 'Approval reason',
        reasonPlaceholder: defaultReasonCopy[kind],
        requiresEvidence,
        evidenceLabel: 'Evidence reference',
      };
    case 'reject':
      return {
        kind,
        title: `Reject ${base}?`,
        description: 'Reject 會拒絕尚未生效的申請，保留審核脈絡。',
        confirmLabel: 'Reject',
        tone: 'warning',
        minReasonLength: 12,
        reasonLabel: 'Reject reason',
        reasonPlaceholder: defaultReasonCopy[kind],
        requiresEvidence,
        evidenceLabel: 'Evidence reference',
      };
    case 'remove':
      return {
        kind,
        title: `Remove ${base}?`,
        description: 'Remove 只適用尚未生效的 declared / pending / rejected record；已生效資料必須用 Suspend 或 Revoke 保留歷史。',
        confirmLabel: 'Remove Pending',
        tone: 'neutral',
        minReasonLength: 12,
        reasonLabel: 'Remove reason',
        reasonPlaceholder: defaultReasonCopy[kind],
        requiresEvidence,
        evidenceLabel: 'Evidence reference',
      };
    case 'suspend':
      return {
        kind,
        title: `Suspend ${base}?`,
        description: 'Suspend 是可逆的高風險治理操作；會暫停此項目對 dispatch eligibility 的有效性。',
        confirmLabel: 'Suspend',
        tone: 'warning',
        minReasonLength: 12,
        reasonLabel: 'Suspend reason',
        reasonPlaceholder: defaultReasonCopy[kind],
        requiresEvidence,
        evidenceLabel: 'Evidence reference',
      };
    case 'resume':
      return {
        kind,
        title: `Resume ${base}?`,
        description: 'Resume 會重新啟用先前 suspended / expired 的治理資料；請確認相關風險已排除。',
        confirmLabel: 'Resume',
        tone: 'primary',
        minReasonLength: 12,
        reasonLabel: 'Resume reason',
        reasonPlaceholder: defaultReasonCopy[kind],
        requiresEvidence,
        evidenceLabel: 'Evidence reference',
      };
    case 'verify':
      return {
        kind,
        title: `Verify ${base}?`,
        description: 'Verify 代表 observation 已經被 probe 或人工檢查確認，但仍不等同 TRUSTED。',
        confirmLabel: 'Verify',
        tone: 'primary',
        minReasonLength: 12,
        reasonLabel: 'Verification reason',
        reasonPlaceholder: defaultReasonCopy[kind],
        requiresEvidence: true,
        evidenceLabel: 'Probe / evidence reference',
      };
    case 'trust':
      return {
        kind,
        title: `Trust ${base}?`,
        description: 'Trust 會讓 runtime feature 成為可信治理資料，後續 routing contract 可使用。',
        confirmLabel: 'Trust Feature',
        tone: 'primary',
        minReasonLength: 12,
        reasonLabel: 'Trust reason',
        reasonPlaceholder: defaultReasonCopy[kind],
        requiresEvidence: true,
        evidenceLabel: 'Trust evidence reference',
      };
    case 'revoke':
      return {
        kind,
        title: `Revoke ${base}?`,
        description: 'Revoke 是不可用 delete 取代的高風險治理操作；會保留已生效授權的撤銷歷史。',
        confirmLabel: 'Revoke',
        tone: 'danger',
        minReasonLength: 12,
        reasonLabel: 'Revoke reason',
        reasonPlaceholder: defaultReasonCopy[kind],
        requiresEvidence,
        evidenceLabel: 'Evidence reference',
        confirmationPhrase,
      };
    case 'retire':
      return {
        kind,
        title: `Retire ${base}?`,
        description: 'Retire 會讓此定義不再可被新治理流程使用；請先完成 impact preview。',
        confirmLabel: 'Retire',
        tone: 'danger',
        minReasonLength: 12,
        reasonLabel: 'Retire reason',
        reasonPlaceholder: defaultReasonCopy[kind],
        requiresEvidence,
        evidenceLabel: 'Evidence reference',
        confirmationPhrase,
      };
    case 'execute':
      return {
        kind,
        title: `Execute ${base}?`,
        description: 'Execute 會改變 recovery 或 runtime 狀態；請確認 runbook 與 rollback 條件。',
        confirmLabel: 'Execute',
        tone: 'warning',
        minReasonLength: 12,
        reasonLabel: 'Execution reason',
        reasonPlaceholder: defaultReasonCopy[kind],
        requiresEvidence,
        evidenceLabel: 'Runbook / evidence reference',
        confirmationPhrase,
      };
  }
}
