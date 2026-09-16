export interface HelpEntry {
  label: string;
  description: string;
  example?: string;
  learnMoreHref?: string;
}

export const helpText = {
  assignmentProfile: {
    label: 'Supply Profile',
    description: 'Dispatch information sourceSystem,taskType,required policy,required capability  Agent Dispatch information',
    example: 'for example ERP_BUSINESS_REVIEWER  ERP business review Task Profile.',
    learnMoreHref: '/dispatch-flows'
  },
  policyBinding: {
    label: 'Policy Binding',
    description: 'Profile and Policy Configuration Profile ',
    example: 'for example ERP_BUSINESS_REVIEWER  REQUIRED  ERP_BUSINESS_REVIEW_POLICY.'
  },
  advancedPolicy: {
    label: 'Dispatch Policy Definition',
    description: 'Policy  Agent.',
    learnMoreHref: '/dispatch-flows'
  },
  agentQualification: {
    label: 'Agent Qualification',
    description: 'Agent  Supply Profile Status.onlyhas APPROVED qualification  routing eligibility.',
    example: 'PENDING can Remove;APPROVED / SUSPENDED  Revoke '
  },
  agentCapability: {
    label: 'Agent Capability',
    description: 'Agent Status.Capability  Capability Catalog;PENDING / DECLARED not candispatch, onlyhas APPROVED capability  routing eligibility.',
    example: 'for example ERP_BUSINESS_REVIEW capability  request, again byadministrator approve.'
  },
  runtimeEligibility: {
    label: 'Runtime Eligibility',
    description: 'Agent Dispatch information Profile  offline,capacity full,backoff,draining,credential or runtime feature  blocked.'
  },
  dispatchRecipe: {
    label: 'Dispatch Recipe',
    description: 'Dispatch informationRecipe DescriptionTasksource, TaskType,required profile,runtime features,fallback  payload.',
    learnMoreHref: '/testing/dispatch-recipes'
  },
  dispatchErrorCode: {
    label: 'Dispatch Error Code',
    description: 'Operator  DISPATCH_* Error routing / governance / runtime Operation failed.'
  },
  removeVsRevoke: {
    label: 'Remove vs Revoke',
    description: 'Remove  DRAFT / PENDING assignment;Revoke  APPROVED / SUSPENDED qualification audit history.'
  },
  suspendVsDisable: {
    label: 'Suspend vs Disable',
    description: 'Suspend  Agent qualification;Disable Disable Profile,Policy  Impact Preview.'
  },
  simulationVsDebug: {
    label: 'Simulation vs Debug',
    description: 'Simulation  payload Dispatch informationResult;Debug  raw JSON,trace,score breakdown'
  },
  impactPreview: {
    label: 'Impact Preview',
    description: 'ActionsDisable,Delete,Remove Binding,Suspend,Revoke  Policy,Agent,Task Type and routing eligibility.'
  },
  troubleshootingWizard: {
    label: 'Troubleshooting Wizard',
    description: ' Task Dispatch information Task Requirement,Supply Profile,Policy Binding,Agent Qualification,Runtime Eligibility and Suggested Fix.'
  }
} as const satisfies Record<string, HelpEntry>;

export type HelpTextKey = keyof typeof helpText;

export function getHelpText(key: HelpTextKey): HelpEntry {
  return helpText[key];
}
