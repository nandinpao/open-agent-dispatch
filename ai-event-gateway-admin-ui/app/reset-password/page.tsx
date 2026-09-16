'use client';

import { FormEvent, useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { useAuth } from '@/components/auth/AuthProvider';
import { PasswordRequirementList } from '@/components/auth/PasswordRequirementList';
import {
  PASSWORD_MAX_LENGTH,
  PASSWORD_MIN_LENGTH,
  passwordMutationErrorMessage,
  passwordRequirements,
} from '@/lib/auth/passwordPolicy';

export default function ResetPasswordPage() {
  const { resetPassword } = useAuth();
  const [initialToken, setInitialToken] = useState('');
  const [tokenFromLink, setTokenFromLink] = useState(false);
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [done, setDone] = useState(false);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const requirements = useMemo(() => passwordRequirements(password), [password]);
  const policySatisfied = requirements.every((requirement) => requirement.satisfied);
  const passwordsMatch = confirm.length > 0 && password === confirm;

  useEffect(() => {
    const linkToken = new URLSearchParams(window.location.search).get('token') ?? '';
    setInitialToken(linkToken);
    setTokenFromLink(Boolean(linkToken));
  }, []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const token = String(data.get('token') ?? '');
    setError('');

    if (!policySatisfied) {
      setError('Complete every password requirement before submitting.');
      return;
    }
    if (!passwordsMatch) {
      setError('Passwords do not match.');
      return;
    }

    setBusy(true);
    try {
      await resetPassword(token, password);
      setDone(true);
    } catch (reason) {
      setError(passwordMutationErrorMessage(reason));
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="mx-auto max-w-md pt-10">
      <section className="rounded-3xl border border-slate-200 bg-white p-7 shadow-sm">
        <p className="text-xs font-black uppercase tracking-[.18em] text-blue-600">Account recovery</p>
        <h1 className="mt-2 text-2xl font-black">Choose a new password</h1>
        {done ? (
          <div className="mt-5 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-900">
            Password reset completed. Existing sessions are subject to the configured revocation policy.
          </div>
        ) : (
          <form onSubmit={submit} className="mt-6 space-y-4">
            {tokenFromLink ? (
              <>
                <input type="hidden" name="token" value={initialToken} />
                <div className="rounded-xl bg-blue-50 p-3 text-sm font-semibold text-blue-900">
                  ✓ Secure reset link recognized. You do not need to enter a reset token.
                </div>
              </>
            ) : (
              <label className="block text-sm font-bold">
                One-time reset token
                <input
                  name="token"
                  defaultValue={initialToken}
                  key={initialToken}
                  required
                  className="mt-2 w-full rounded-xl border border-slate-300 px-3 py-2.5"
                />
                <span className="mt-2 block text-xs font-medium text-slate-500">Normally this value is included in the reset link.</span>
              </label>
            )}
            <label className="block text-sm font-bold">
              New password
              <input
                name="password"
                type="password"
                minLength={PASSWORD_MIN_LENGTH}
                maxLength={PASSWORD_MAX_LENGTH}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                required
                className="mt-2 w-full rounded-xl border border-slate-300 px-3 py-2.5"
              />
            </label>
            <PasswordRequirementList requirements={requirements} active={password.length > 0} />
            <label className="block text-sm font-bold">
              Confirm password
              <input
                name="confirm"
                type="password"
                minLength={PASSWORD_MIN_LENGTH}
                maxLength={PASSWORD_MAX_LENGTH}
                value={confirm}
                onChange={(event) => setConfirm(event.target.value)}
                required
                className="mt-2 w-full rounded-xl border border-slate-300 px-3 py-2.5"
              />
            </label>
            {confirm.length > 0 && !passwordsMatch ? (
              <p className="text-sm font-semibold text-rose-700">The confirmation does not match the new password.</p>
            ) : null}
            {error ? (
              <div role="alert" className="rounded-xl bg-rose-50 p-3 text-sm text-rose-800">{error}</div>
            ) : null}
            <button
              disabled={busy || !policySatisfied || !passwordsMatch}
              className="w-full rounded-xl bg-blue-600 px-4 py-2.5 text-sm font-black text-white disabled:bg-slate-300"
            >
              {busy ? 'Resetting…' : 'Reset password'}
            </button>
          </form>
        )}
        <Link href="/login" className="mt-5 inline-block text-sm font-bold text-blue-700">
          Continue to sign in
        </Link>
      </section>
    </main>
  );
}
