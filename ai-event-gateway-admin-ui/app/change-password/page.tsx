'use client';

import { FormEvent, useMemo, useState } from 'react';
import { useAuth } from '@/components/auth/AuthProvider';
import { PasswordRequirementList } from '@/components/auth/PasswordRequirementList';
import {
  PASSWORD_MAX_LENGTH,
  PASSWORD_MIN_LENGTH,
  passwordMutationErrorMessage,
  passwordRequirements,
} from '@/lib/auth/passwordPolicy';

export default function ChangePasswordPage() {
  const { changePassword, user, logout } = useAuth();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const requirements = useMemo(
    () => passwordRequirements(newPassword, user?.username ?? 'root'),
    [newPassword, user?.username],
  );
  const policySatisfied = requirements.every((requirement) => requirement.satisfied);
  const passwordsMatch = confirmPassword.length > 0 && newPassword === confirmPassword;
  const isRoot = user?.userId === 'root' || user?.username === 'root';
  const passwordChanged = currentPassword.length > 0 && newPassword !== currentPassword;
  const canSubmit = policySatisfied && passwordsMatch && passwordChanged && !busy;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError('');

    if (!policySatisfied) {
      setError('Complete every password requirement before submitting.');
      return;
    }
    if (!passwordsMatch) {
      setError('Passwords do not match.');
      return;
    }
    if (!passwordChanged) {
      setError(isRoot ? 'The new password must differ from the installation password.' : 'The new password must differ from the current password.');
      return;
    }

    setBusy(true);
    try {
      await changePassword(currentPassword, newPassword);
    } catch (reason) {
      setError(passwordMutationErrorMessage(reason));
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="mx-auto grid max-w-4xl gap-8 pt-10 lg:grid-cols-[1fr_.9fr]">
      <section className="rounded-3xl bg-slate-950 p-8 text-white shadow-xl">
        <p className="text-xs font-black uppercase tracking-[.2em] text-amber-300">Mandatory security action</p>
        <h1 className="mt-4 text-4xl font-black leading-tight">{isRoot ? 'Replace the installation password' : 'Choose a new password'}</h1>
        <p className="mt-5 text-sm leading-7 text-slate-300">
          Your current session is restricted to this security action. Changing the password revokes the session and requires a new sign-in before normal access resumes.
        </p>
        <div className="mt-6 rounded-2xl border border-white/10 bg-white/5 p-4 text-sm text-slate-300">
          <b className="text-white">Account:</b> {user?.username ?? 'root'}
          <br />
          <b className="text-white">Policy:</b> {PASSWORD_MIN_LENGTH}–{PASSWORD_MAX_LENGTH} characters with
          uppercase, lowercase, number, and symbol. The password must not contain the username.
        </div>
      </section>

      <section className="rounded-3xl border border-slate-200 bg-white p-7 shadow-sm">
        <p className="text-xs font-black uppercase tracking-[.18em] text-blue-600">First sign-in</p>
        <h2 className="mt-2 text-2xl font-black text-slate-950">{isRoot ? 'Choose a permanent Root password' : 'Choose a new sign-in password'}</h2>
        <form onSubmit={submit} className="mt-6 space-y-4">
          <label className="block text-sm font-bold text-slate-700">
            {isRoot ? 'Installation password' : 'Current password'}
            <input
              name="currentPassword"
              type="password"
              autoComplete="current-password"
              value={currentPassword}
              onChange={(event) => setCurrentPassword(event.target.value)}
              required
              className="mt-2 w-full rounded-xl border border-slate-300 px-3 py-2.5"
            />
          </label>
          <label className="block text-sm font-bold text-slate-700">
            New password
            <input
              name="newPassword"
              type="password"
              autoComplete="new-password"
              minLength={PASSWORD_MIN_LENGTH}
              maxLength={PASSWORD_MAX_LENGTH}
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
              required
              aria-describedby="password-requirements"
              className="mt-2 w-full rounded-xl border border-slate-300 px-3 py-2.5"
            />
          </label>
          <div id="password-requirements">
            <PasswordRequirementList requirements={requirements} active={newPassword.length > 0} />
          </div>
          <label className="block text-sm font-bold text-slate-700">
            Confirm new password
            <input
              name="confirmPassword"
              type="password"
              autoComplete="new-password"
              minLength={PASSWORD_MIN_LENGTH}
              maxLength={PASSWORD_MAX_LENGTH}
              value={confirmPassword}
              onChange={(event) => setConfirmPassword(event.target.value)}
              required
              className="mt-2 w-full rounded-xl border border-slate-300 px-3 py-2.5"
            />
          </label>
          {confirmPassword.length > 0 && !passwordsMatch ? (
            <p className="text-sm font-semibold text-rose-700">The confirmation does not match the new password.</p>
          ) : null}
          {newPassword.length > 0 && currentPassword.length > 0 && !passwordChanged ? (
            <p className="text-sm font-semibold text-rose-700">{isRoot ? 'The permanent password must differ from the installation password.' : 'The new password must differ from the current password.'}</p>
          ) : null}
          {error ? (
            <div role="alert" className="rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">
              {error}
            </div>
          ) : null}
          <button
            disabled={!canSubmit}
            className="w-full rounded-xl bg-blue-600 px-4 py-2.5 text-sm font-black text-white disabled:bg-slate-300"
          >
            {busy ? 'Changing password…' : 'Change password and revoke session'}
          </button>
        </form>
        <button
          type="button"
          onClick={() => void logout()}
          className="mt-4 w-full rounded-xl border border-slate-300 px-4 py-2.5 text-sm font-bold text-slate-700"
        >
          Sign out without changing
        </button>
      </section>
    </main>
  );
}
