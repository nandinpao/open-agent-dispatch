import type { UiDisplayMode, UiReasonCategory } from '@/lib/ui-capability/contracts';

const REASON_COPY: Record<UiReasonCategory, { title: string; message: string; action?: string }> = {
  NOT_ALLOWED: { title: 'Action unavailable', message: 'Your current access does not allow this action.', action: 'Contact an administrator or request access.' },
  OUTSIDE_SCOPE: { title: 'Outside your scope', message: 'This resource is outside your current administrative scope.', action: 'Request the smallest required scope.' },
  READ_ONLY_ACCESS: { title: 'Read-only access', message: 'You can review this information but cannot change it.' },
  APPROVAL_REQUIRED: { title: 'Approval required', message: 'This action must be approved before it can be executed.', action: 'Submit or review the approval request.' },
  STEP_UP_REQUIRED: { title: 'Reauthentication required', message: 'This action requires a stronger authentication assurance.', action: 'Continue through the secure verification flow.' },
  SEPARATION_OF_DUTIES: { title: 'Independent approval required', message: 'The initiator cannot approve or complete this action.' },
  RESOURCE_LOCKED: { title: 'Resource locked', message: 'Another protected operation currently holds this resource.' },
  RESOURCE_QUARANTINED: { title: 'Resource quarantined', message: 'Security controls have temporarily locked this resource.' },
  RESOURCE_CHANGED: { title: 'Information changed', message: 'This resource changed while you were working.', action: 'Review the latest information before continuing.' },
  TENANT_CONTEXT_CHANGED: { title: 'Workspace changed', message: 'Your active workspace or security context changed.', action: 'Reload the page in the current workspace.' },
  ACTION_NOT_AVAILABLE_IN_CURRENT_STATE: { title: 'Not available in this state', message: 'The resource state does not currently allow this action.' },
  BACKGROUND_OPERATION_IN_PROGRESS: { title: 'Operation in progress', message: 'A background operation must finish before this action becomes available.' },
  EXTERNAL_PROVIDER_UNAVAILABLE: { title: 'External service unavailable', message: 'The connected provider is not currently available.', action: 'Try again after provider health recovers.' },
  TEMPORARILY_UNAVAILABLE: { title: 'Temporarily unavailable', message: 'This action is temporarily unavailable.', action: 'Reload access or try again shortly.' },
};

export function reasonCopy(reason?: UiReasonCategory): { title: string; message: string; action?: string } {
  return reason ? REASON_COPY[reason] : { title: 'Action unavailable', message: 'This action is not currently available.' };
}

export function displayModeLabel(mode: UiDisplayMode): string {
  return ({
    HIDE: 'Hidden',
    DISABLE_WITH_REASON: 'Unavailable',
    READ_ONLY: 'Read only',
    ENABLED: 'Available',
    STEP_UP_REQUIRED: 'Reauthentication required',
    APPROVAL_REQUIRED: 'Approval required',
    REQUEST_ACCESS: 'Access request available',
    LOCKED_SECURITY: 'Security locked',
    STALE_RELOAD: 'Reload required',
  } satisfies Record<UiDisplayMode, string>)[mode];
}
