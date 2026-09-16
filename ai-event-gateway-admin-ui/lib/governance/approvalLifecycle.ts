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
  approve: 'PleaseDescriptionResult',
  reject: 'PleaseDescription',
  remove: 'PleaseDescriptionReview the configuration and try again.',
  suspend: 'PleaseDescription',
  resume: 'PleaseDescriptionCompleted',
  revoke: 'PleaseDescription',
  verify: 'PleaseDescriptionprobe Result.',
  trust: 'PleaseDescriptionResult',
  retire: 'PleaseDescription',
  execute: 'PleaseDescriptionrunreason and riskconfirm.',
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
        description: 'Approve  routing / eligibility ',
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
        description: 'Reject Review the configuration and try again.',
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
        description: 'Remove  declared / pending / rejected record Suspend or Revoke ',
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
        description: 'Suspend Actions dispatch eligibility ',
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
        description: 'Resume Enable suspended / expired Review the configuration and try again.',
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
        description: 'Verify  observation  probe  TRUSTED.',
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
        description: 'Trust  runtime feature  routing contract canuse.',
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
        description: 'Revoke is unavailable delete Actions',
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
        description: 'Retire Review the configuration and try again. impact preview.',
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
        description: 'Execute  recovery or runtime Status; Verify runbook and rollback condition.',
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
