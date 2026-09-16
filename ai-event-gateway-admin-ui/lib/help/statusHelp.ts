export interface StatusHelpEntry {
  label: string;
  description: string;
  operatorAction?: string;
}

export const statusHelp = {
  ACTIVE: {
    label: 'ACTIVE',
    description: 'currentEnable',
    operatorAction: ' Impact Preview, again run Disable or Retire.'
  },
  DISABLED: {
    label: 'DISABLED',
    description: 'Disable routing ',
    operatorAction: 'No data is currently available. qualification / binding / audit '
  },
  PENDING: {
    label: 'PENDING',
    description: 'Not approvedRouting does notuse pending qualification.',
    operatorAction: 'can Approve  Remove Cancel assignment.'
  },
  APPROVED: {
    label: 'APPROVED',
    description: ' routing eligibility.',
    operatorAction: 'Disable Suspend Revoke.'
  },
  SUSPENDED: {
    label: 'SUSPENDED',
    description: 'Routing  suspended qualification.',
    operatorAction: 'can Resume  Revoke revoke.'
  },
  REVOKED: {
    label: 'REVOKED',
    description: 'Details',
    operatorAction: 'Create assignment / qualification.'
  },
  EXPIRED: {
    label: 'EXPIRED',
    description: ' routing.',
    operatorAction: ' Resume,Renew  assign.'
  },
  ELIGIBLE: {
    label: 'ELIGIBLE',
    description: 'Dispatch information routing '
  },
  BLOCKED: {
    label: 'BLOCKED',
    description: 'Dispatch information Profile,Policy,Qualification,Credential,Capacity or Runtime Feature.',
    operatorAction: ' Troubleshooting Wizard  FAILED item.'
  },
  RETRY_WAIT: {
    label: 'RETRY_WAIT',
    description: 'Waiting for the next state transition. dispatch recovery, not is terminal failure.',
    operatorAction: 'canwaiting scanner, or in Task Detail  Recovery / Manual Retry.'
  },
  DEAD_LETTER: {
    label: 'DEAD_LETTER',
    description: ' DLQ / dead letter'
  }
} as const satisfies Record<string, StatusHelpEntry>;

export type StatusHelpKey = keyof typeof statusHelp;

export function getStatusHelp(status: string | null | undefined): StatusHelpEntry | undefined {
  const normalized = (status ?? '').trim().toUpperCase();
  return statusHelp[normalized as StatusHelpKey];
}
