export interface StatusPresentation {
  label: string;
  description: string;
  tone: 'positive' | 'neutral' | 'warning' | 'danger';
}

const ACCOUNT_STATUS: Record<string, StatusPresentation> = {
  PENDING_ACTIVATION: { label: 'Activation pending', description: 'The account cannot sign in until its secure activation flow is completed.', tone: 'neutral' },
  ACTIVE: { label: 'Active', description: 'The account can sign in when its membership and security requirements are valid.', tone: 'positive' },
  LOCKED: { label: 'Temporarily locked', description: 'New sign-in attempts are blocked until an administrator reactivates the account.', tone: 'warning' },
  SUSPENDED: { label: 'Suspended by administrator', description: 'The identity remains auditable, but access is blocked until reactivation.', tone: 'warning' },
  DISABLED: { label: 'Sign-in disabled', description: 'The identity remains in the directory and cannot sign in until reactivated.', tone: 'danger' },
  PASSWORD_RESET_REQUIRED: { label: 'Password reset required', description: 'The user must replace the current password before continuing.', tone: 'warning' },
  MFA_ENROLLMENT_REQUIRED: { label: 'MFA enrollment required', description: 'The user must enroll an approved MFA method before protected access is granted.', tone: 'warning' },
  DELETED: { label: 'Closed', description: 'The identity cannot be reused; historical audit and business references remain preserved.', tone: 'danger' },
};

const MEMBERSHIP_STATUS: Record<string, StatusPresentation> = {
  ACTIVE: { label: 'Active membership', description: 'The identity can use this Tenant when account and policy requirements are satisfied.', tone: 'positive' },
  INVITED: { label: 'Invitation pending', description: 'Tenant access becomes active after the user completes invitation activation.', tone: 'neutral' },
  SUSPENDED: { label: 'Membership suspended', description: 'The global identity remains valid, but access to this Tenant is blocked.', tone: 'warning' },
  EXPIRED: { label: 'Membership expired', description: 'The configured Tenant access period has ended.', tone: 'warning' },
  REMOVED: { label: 'Membership removed', description: 'The identity no longer has access to this Tenant.', tone: 'danger' },
};

const INVITATION_STATUS: Record<string, StatusPresentation> = {
  NOT_ISSUED: { label: 'Not issued', description: 'No active invitation exists for this account.', tone: 'neutral' },
  PENDING: { label: 'Pending activation', description: 'A secure activation credential was issued and has not yet been consumed.', tone: 'neutral' },
  ACCEPTED: { label: 'Activation completed', description: 'The invitation was consumed and the account activation flow was completed.', tone: 'positive' },
  EXPIRED: { label: 'Invitation expired', description: 'The activation credential expired before it was used.', tone: 'warning' },
  REVOKED: { label: 'Invitation revoked', description: 'An administrator revoked the activation credential before use.', tone: 'danger' },
};

function fallback(value: string): StatusPresentation {
  return { label: value.replaceAll('_', ' ').toLowerCase().replace(/^./, (character) => character.toUpperCase()), description: 'No additional status guidance is available.', tone: 'neutral' };
}

export function accountStatusPresentation(status: string): StatusPresentation {
  return ACCOUNT_STATUS[status] ?? fallback(status);
}

export function membershipStatusPresentation(status: string): StatusPresentation {
  return MEMBERSHIP_STATUS[status] ?? fallback(status);
}

export function invitationStatusPresentation(status: string): StatusPresentation {
  return INVITATION_STATUS[status] ?? fallback(status);
}

export function formatAccountDate(value?: string | null): string {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.valueOf()) ? value : new Intl.DateTimeFormat('en', { dateStyle: 'medium', timeStyle: 'short' }).format(date);
}
