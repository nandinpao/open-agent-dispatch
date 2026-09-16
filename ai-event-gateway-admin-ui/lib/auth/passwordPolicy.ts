import { ApiError } from '@/lib/api/errors';

export const PASSWORD_MIN_LENGTH = 14;
export const PASSWORD_MAX_LENGTH = 256;

export interface PasswordRequirement {
  key: string;
  label: string;
  satisfied: boolean;
}

export function passwordRequirements(password: string, username?: string): PasswordRequirement[] {
  const normalizedUsername = username?.trim().toLowerCase() ?? '';
  const normalizedPassword = password.toLowerCase();

  return [
    {
      key: 'minimum-length',
      label: `At least ${PASSWORD_MIN_LENGTH} characters`,
      satisfied: password.length >= PASSWORD_MIN_LENGTH,
    },
    {
      key: 'maximum-length',
      label: `No more than ${PASSWORD_MAX_LENGTH} characters`,
      satisfied: password.length <= PASSWORD_MAX_LENGTH,
    },
    {
      key: 'uppercase',
      label: 'At least one uppercase letter',
      satisfied: /\p{Lu}/u.test(password),
    },
    {
      key: 'lowercase',
      label: 'At least one lowercase letter',
      satisfied: /\p{Ll}/u.test(password),
    },
    {
      key: 'number',
      label: 'At least one number',
      satisfied: /\p{Nd}/u.test(password),
    },
    {
      key: 'symbol',
      label: 'At least one symbol',
      satisfied: /[^\p{L}\p{N}]/u.test(password),
    },
    ...(normalizedUsername
      ? [{
          key: 'username',
          label: 'Does not contain the username',
          satisfied: !normalizedPassword.includes(normalizedUsername),
        }]
      : []),
  ];
}

export function passwordSatisfiesPolicy(password: string, username?: string): boolean {
  return passwordRequirements(password, username).every((requirement) => requirement.satisfied);
}

const VIOLATION_LABELS: Record<string, string> = {
  PASSWORD_REQUIRED: 'Enter a new password.',
  PASSWORD_TOO_SHORT: `Use at least ${PASSWORD_MIN_LENGTH} characters.`,
  PASSWORD_TOO_LONG: `Use no more than ${PASSWORD_MAX_LENGTH} characters.`,
  PASSWORD_UPPERCASE_REQUIRED: 'Add at least one uppercase letter.',
  PASSWORD_LOWERCASE_REQUIRED: 'Add at least one lowercase letter.',
  PASSWORD_NUMBER_REQUIRED: 'Add at least one number.',
  PASSWORD_SYMBOL_REQUIRED: 'Add at least one symbol.',
  PASSWORD_CONTAINS_USERNAME: 'Do not include the username in the password.',
};

function policyMessages(message: string): string[] {
  return message
    .split(',')
    .map((part) => part.trim())
    .filter(Boolean)
    .map((part) => VIOLATION_LABELS[part] ?? part);
}

export function passwordMutationErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return error instanceof Error ? error.message : 'Password change failed.';
  }

  switch (error.code) {
    case 'AUTH_PASSWORD_POLICY_VIOLATION': {
      const messages = policyMessages(error.message);
      return messages.length > 0
        ? `Password requirements were not met: ${messages.join(' ')}`
        : 'The new password does not satisfy the active password policy.';
    }
    case 'AUTH_PASSWORD_REUSED':
      return 'Choose a password that was not used recently.';
    case 'AUTH_PASSWORD_BREACHED':
      return 'Choose a different password. This password appears in a breach corpus.';
    case 'AUTH_INVALID_CREDENTIALS':
      return 'The current password is incorrect.';
    case 'VERSION_CONFLICT':
      return 'The password credential changed in another session. Sign in again and retry.';
    default:
      return error.message || 'Password change failed.';
  }
}
