export interface ActionHelpEntry {
  label: string;
  description: string;
  requiresConfirm?: boolean;
  requiresReason?: boolean;
  danger?: boolean;
}

export const actionHelp = {
  create: {
    label: 'Create',
    description: 'Create Drawer or Wizard List page.'
  },
  edit: {
    label: 'Edit',
    description: 'Detail  Edit  Drawer.'
  },
  delete: {
    label: 'Delete',
    description: 'Active or has audit trail ',
    requiresConfirm: true,
    danger: true
  },
  disable: {
    label: 'Disable',
    description: 'Disable Profile,Policy Configuration Impact Preview.',
    requiresConfirm: true,
    danger: true
  },
  remove: {
    label: 'Remove',
    description: ' assignment or binding.Pending qualification  Remove Revoke.',
    requiresConfirm: true
  },
  revoke: {
    label: 'Revoke',
    description: ' audit history.',
    requiresConfirm: true,
    requiresReason: true,
    danger: true
  },
  suspend: {
    label: 'Suspend',
    description: ' Resume',
    requiresConfirm: true,
    requiresReason: true
  },
  resume: {
    label: 'Resume',
    description: ' suspended / expired  credential,certification and runtime eligibility.',
    requiresConfirm: true
  },
  simulate: {
    label: 'Simulate',
    description: 'use sample payload  production state.'
  },
  debug: {
    label: 'Debug',
    description: ' raw payload,trace,score breakdown.Debug  Operator.'
  },
  retry: {
    label: 'Retry',
    description: 'Dispatch informationRetry ',
    requiresConfirm: true
  }
} as const satisfies Record<string, ActionHelpEntry>;

export type ActionHelpKey = keyof typeof actionHelp;

export function getActionHelp(key: ActionHelpKey): ActionHelpEntry {
  return actionHelp[key];
}
