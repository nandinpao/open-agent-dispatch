import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiError } from '@/lib/api/errors';
import {
  passwordMutationErrorMessage,
  passwordRequirements,
  passwordSatisfiesPolicy,
} from '@/lib/auth/passwordPolicy';

test('password policy requires uppercase lowercase number symbol length and username separation', () => {
  assert.equal(passwordSatisfiesPolicy('lowercase-and-number-123', 'root'), false);
  assert.equal(passwordSatisfiesPolicy('Valid-Rootless-Password9!', 'root'), false);
  assert.equal(passwordSatisfiesPolicy('Valid-Permanent-Password9!', 'root'), true);

  const missing = passwordRequirements('lowercase-and-number-123', 'root')
    .filter((requirement) => !requirement.satisfied)
    .map((requirement) => requirement.key);
  assert.deepEqual(missing, ['uppercase']);
});

test('password policy API error becomes actionable user copy', () => {
  const error = new ApiError(
    'PASSWORD_UPPERCASE_REQUIRED,PASSWORD_SYMBOL_REQUIRED',
    400,
    undefined,
    'AUTH_PASSWORD_POLICY_VIOLATION',
  );
  assert.equal(
    passwordMutationErrorMessage(error),
    'Password requirements were not met: Add at least one uppercase letter. Add at least one symbol.',
  );
});
