import type { A2APolicyView, CreateA2ARequestInput } from '@/lib/a2a/contracts';
export type { A2APolicyView, A2ARequestRecord, CreateA2ARequestInput } from '@/lib/a2a/contracts';

export const TASK_BLOCKING_REASONS = [
  'WAITING_FOR_AGENT',
  'NO_ELIGIBLE_AGENT',
  'NO_SERVICE_SCOPE',
  'WAITING_APPROVAL',
  'WAITING_CONTEXT',
  'PROJECTION_FAILED',
  'PERMISSION_REQUIRED',
  'RESOURCE_LOCKED',
  'MANUAL_REVIEW_REQUIRED',
] as const;
export type TaskBlockingReason = (typeof TASK_BLOCKING_REASONS)[number];

export const A2A_CONTEXT_DISPOSITIONS = ['INCLUDED', 'MASKED', 'OMITTED', 'REQUIRES_APPROVAL'] as const;
export type A2AContextDisposition = (typeof A2A_CONTEXT_DISPOSITIONS)[number];

export const A2A_RESULT_LANES = ['AGENT_RESULT', 'HUMAN_ACCEPTED_RESULT', 'EXTERNAL_ISSUE_PROJECTION'] as const;
export type A2AResultLane = (typeof A2A_RESULT_LANES)[number];

export interface TaskBlockingExperience {
  code: TaskBlockingReason;
  title: string;
  explanation: string;
  recommendedAction: string;
  settingsHref?: string;
  userCanFix: boolean;
  sourceCode?: string;
}

export interface TaskChainNodeView {
  taskId: string;
  taskKey?: string | null;
  title?: string | null;
  status?: string | null;
  rootTaskId?: string | null;
  parentTaskId?: string | null;
  ownerDepartmentId?: string | null;
  executorDepartmentId?: string | null;
  executorDomainId?: string | null;
  version: number;
  depth: number;
}
export interface TaskChainView {
  tenantId: string;
  requestedTaskId: string;
  rootTaskId: string;
  nodes: TaskChainNodeView[];
}

export interface A2AContextPreviewItem {
  key: string;
  label: string;
  value: string;
  disposition: A2AContextDisposition;
  explanation: string;
}

export interface A2AResultLaneView {
  lane: A2AResultLane;
  title: string;
  status: string;
  authority: string;
  summary: string;
  evidenceReference?: string;
}

const blockingCatalog: Record<TaskBlockingReason, Omit<TaskBlockingExperience, 'code' | 'sourceCode'>> = {
  WAITING_FOR_AGENT: {
    title: 'Waiting for an Agent',
    explanation: 'The Task is ready for dispatch but no Agent has accepted or started the assignment yet.',
    recommendedAction: 'Review the eligible Agent list and current runtime availability.',
    settingsHref: '/agents',
    userCanFix: true,
  },
  NO_ELIGIBLE_AGENT: {
    title: 'No eligible Agent',
    explanation: 'No Agent currently satisfies the required capability, runtime, policy, and workload constraints.',
    recommendedAction: 'Review the dispatch eligibility evidence before changing routing or Agent configuration.',
    settingsHref: '/agents',
    userCanFix: true,
  },
  NO_SERVICE_SCOPE: {
    title: 'Dispatch access is not configured',
    explanation: 'The selected Agent is connected but is not authorized for this Source System or Task type.',
    recommendedAction: 'Configure the required Dispatch Access for an approved Agent, then re-evaluate dispatch.',
    settingsHref: '/agents',
    userCanFix: true,
  },
  WAITING_APPROVAL: {
    title: 'Waiting for approval',
    explanation: 'A governed action or cross-domain request requires approval before execution can continue.',
    recommendedAction: 'Open the approval evidence and wait for an eligible approver. The requester cannot self-approve.',
    userCanFix: false,
  },
  WAITING_CONTEXT: {
    title: 'Waiting for approved context',
    explanation: 'Required handoff context is not ready, approved, or released to the target execution boundary.',
    recommendedAction: 'Review the context preview and resolve missing, masked, or approval-required fields.',
    userCanFix: true,
  },
  PROJECTION_FAILED: {
    title: 'External issue projection failed',
    explanation: 'OpenDispatch remains authoritative, but the external issue projection did not complete successfully.',
    recommendedAction: 'Review the projection evidence and retry or reconcile without changing the Task state manually.',
    settingsHref: '/issue-tracking',
    userCanFix: true,
  },
  PERMISSION_REQUIRED: {
    title: 'Additional access is required',
    explanation: 'The current user can see the Task but cannot perform the required operation in this resource scope.',
    recommendedAction: 'Request temporary access or ask an authorized owner to complete the action.',
    userCanFix: false,
  },
  RESOURCE_LOCKED: {
    title: 'Resource is locked',
    explanation: 'The Task or a related resource is locked, quarantined, or being changed by another governed operation.',
    recommendedAction: 'Review the active operation and latest resource version before retrying.',
    userCanFix: false,
  },
  MANUAL_REVIEW_REQUIRED: {
    title: 'Manual review is required',
    explanation: 'OpenDispatch cannot safely choose the next state automatically from the current evidence.',
    recommendedAction: 'Review the evidence summary and select only one of the allowed governed decisions.',
    userCanFix: true,
  },
};

function text(value: unknown): string {
  return typeof value === 'string' ? value.trim() : '';
}

export function normalizeTaskBlockingReason(input: Readonly<Record<string, unknown>>): TaskBlockingExperience | null {
  const source = [
    text(input.blockedReason), text(input.dispatchWaitReason), text(input.failureReason),
    text(input.reasonCategory), text(input.status), text(input.dispatchStatus),
  ].filter(Boolean).join(' ').toUpperCase();
  if (!source || /COMPLETED|CANCELLED|SUCCEEDED/.test(source)) return null;
  let code: TaskBlockingReason;
  if (/NO_SERVICE_SCOPE|SERVICE_SCOPE|DISPATCH_PROFILE_NOT_CONFIGURED/.test(source)) code = 'NO_SERVICE_SCOPE';
  else if (/NO_ELIGIBLE|NO_CANDIDATE|NO_AGENT|CAPABILITY_NOT/.test(source)) code = 'NO_ELIGIBLE_AGENT';
  else if (/WAIT.*APPROVAL|APPROVAL_REQUIRED|PENDING_APPROVAL/.test(source)) code = 'WAITING_APPROVAL';
  else if (/HANDOFF|CONTEXT|SNAPSHOT|WAITING_CONTEXT/.test(source)) code = 'WAITING_CONTEXT';
  else if (/PROJECTION|ISSUE_SYNC|PROVIDER|EXTERNAL_ISSUE/.test(source)) code = 'PROJECTION_FAILED';
  else if (/PERMISSION|FORBIDDEN|OUTSIDE_SCOPE|ACCESS_REQUIRED/.test(source)) code = 'PERMISSION_REQUIRED';
  else if (/LOCK|QUARANTINE|CONFLICT|STALE/.test(source)) code = 'RESOURCE_LOCKED';
  else if (/WAIT_HUMAN|MANUAL_REVIEW|REVIEW_REQUIRED|DEAD_LETTER/.test(source)) code = 'MANUAL_REVIEW_REQUIRED';
  else code = 'WAITING_FOR_AGENT';
  return { code, sourceCode: source.slice(0, 160), ...blockingCatalog[code] };
}

export function buildA2AContextPreview(input: Readonly<{
  taskId: string;
  policy?: A2APolicyView;
  request: CreateA2ARequestInput;
}>): A2AContextPreviewItem[] {
  const approvalRequired = Boolean(input.policy?.approvalMode && input.policy.approvalMode !== 'NONE');
  const items: A2AContextPreviewItem[] = [
    { key: 'source-task', label: 'Source Task reference', value: input.taskId, disposition: 'INCLUDED', explanation: 'The target receives the governed source Task reference.' },
    { key: 'capability', label: 'Requested capabilities', value: input.request.requestedCapabilityCodes.length ? input.request.requestedCapabilityCodes.join(', ') : 'Not specified', disposition: 'INCLUDED', explanation: 'Phase 0 no longer accepts a target Domain/Pool/Agent. Phase 1 resolves providers from canonical capability requirements.' },
    { key: 'purpose', label: 'Business purpose', value: input.request.reason || 'Not provided', disposition: 'INCLUDED', explanation: 'The business purpose is included in the A2A request audit trail.' },
    { key: 'payload-ref', label: 'Input payload reference', value: input.request.inputPayloadRef ? 'Reference supplied; value masked in preview' : 'No payload reference supplied', disposition: input.request.inputPayloadRef ? 'MASKED' : 'OMITTED', explanation: 'The UI never renders payload content. Backend policy controls whether the reference may be released.' },
    { key: 'attachments', label: 'Attachment content', value: 'Not included by this request form', disposition: 'OMITTED', explanation: 'Attachments require a separate governed snapshot and malware/sensitivity checks.' },
    { key: 'approval', label: 'Context release', value: approvalRequired ? input.policy?.approvalMode ?? 'Approval required' : 'No policy approval required', disposition: approvalRequired ? 'REQUIRES_APPROVAL' : 'INCLUDED', explanation: approvalRequired ? 'The request can be submitted, but execution waits for an eligible approver.' : 'The active policy does not require an approval stage.' },
  ];
  return items;
}

export function buildA2AResultLanes(input: Readonly<{
  result?: { status?: string; summary?: string; authority?: string; evidenceReference?: string } | null;
  aggregation?: { status?: string; summary?: string; authority?: string; evidenceReference?: string } | null;
  projection?: { status?: string; summary?: string; authority?: string; evidenceReference?: string } | null;
}>): A2AResultLaneView[] {
  return [
    {
      lane: 'AGENT_RESULT', title: 'Agent Result', status: input.result?.status ?? 'NOT_RECEIVED',
      authority: input.result?.authority ?? 'Agent callback evidence',
      summary: input.result?.summary ?? 'No accepted Agent result evidence is available.', evidenceReference: input.result?.evidenceReference,
    },
    {
      lane: 'HUMAN_ACCEPTED_RESULT', title: 'Human Accepted Result', status: input.aggregation?.status ?? 'NOT_DECIDED',
      authority: input.aggregation?.authority ?? 'OpenDispatch result governance',
      summary: input.aggregation?.summary ?? 'No human acceptance or aggregation decision has been recorded.', evidenceReference: input.aggregation?.evidenceReference,
    },
    {
      lane: 'EXTERNAL_ISSUE_PROJECTION', title: 'External Issue Projection', status: input.projection?.status ?? 'NOT_PROJECTED',
      authority: input.projection?.authority ?? 'External projection adapter',
      summary: input.projection?.summary ?? 'External issue state is display/projection evidence and does not complete the OpenDispatch Task.', evidenceReference: input.projection?.evidenceReference,
    },
  ];
}
