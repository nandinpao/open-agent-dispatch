'use client';

import Link from 'next/link';
import { FormEvent, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/components/auth/AuthProvider';
import { PasswordRequirementList } from '@/components/auth/PasswordRequirementList';
import {
  PASSWORD_MAX_LENGTH,
  PASSWORD_MIN_LENGTH,
  passwordMutationErrorMessage,
  passwordRequirements,
} from '@/lib/auth/passwordPolicy';

export default function ActivateAccountPage() {
  const { activateInvitation } = useAuth();
  const [token, setToken] = useState('');
  const [tokenFromLink, setTokenFromLink] = useState(false);
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [done, setDone] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const requirements = useMemo(() => passwordRequirements(password), [password]);
  const policySatisfied = requirements.every((requirement) => requirement.satisfied);
  const passwordsMatch = confirm.length > 0 && password === confirm;

  useEffect(() => {
    const linkToken = new URLSearchParams(window.location.search).get('token') ?? '';
    setToken(linkToken);
    setTokenFromLink(Boolean(linkToken));
  }, []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError('');
    if (!token.trim()) {
      setError('This setup link is missing its one-time activation token. Ask an administrator to resend the invitation.');
      return;
    }
    if (!policySatisfied) {
      setError('Complete every password requirement before continuing.');
      return;
    }
    if (!passwordsMatch) {
      setError('Passwords do not match.');
      return;
    }
    setBusy(true);
    try {
      await activateInvitation(token.trim(), password);
      setDone(true);
    } catch (reason) {
      setError(passwordMutationErrorMessage(reason));
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="mx-auto max-w-xl pt-10">
      <section className="rounded-3xl border border-slate-200 bg-white p-7 shadow-sm">
        <p className="text-xs font-black uppercase tracking-[.18em] text-blue-600">Account setup</p>
        <h1 className="mt-2 text-3xl font-black text-slate-950">Create your sign-in password</h1>
        <p className="mt-3 text-sm leading-6 text-slate-600">
          This is the first step. After saving the password, sign in once and OpenDispatch will guide you through authenticator setup.
        </p>

        {done ? (
          <div className="mt-6 space-y-4">
            <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-5">
              <p className="font-black text-emerald-950">Password saved</p>
              <p className="mt-2 text-sm leading-6 text-emerald-900">
                Your account is not fully ready yet. Sign in with this password to complete MFA enrollment.
              </p>
            </div>
            <Link href="/login?accountActivated=1" className="block rounded-xl bg-blue-700 px-4 py-3 text-center text-sm font-black text-white">
              Continue to sign in
            </Link>
          </div>
        ) : (
          <form onSubmit={submit} className="mt-6 space-y-4">
            {!tokenFromLink ? (
              <label className="block text-sm font-bold text-slate-700">
                One-time setup token
                <input
                  value={token}
                  onChange={(event) => setToken(event.target.value)}
                  autoComplete="off"
                  required
                  className="mt-2 w-full rounded-xl border border-slate-300 px-3 py-2.5"
                />
                <span className="mt-2 block text-xs font-medium leading-5 text-slate-500">
                  Normally this value is included in the setup link. If it is missing, ask your administrator to resend the invitation.
                </span>
              </label>
            ) : (
              <div className="rounded-xl bg-blue-50 p-3 text-sm font-semibold text-blue-900">
                ✓ Secure setup link recognized. You do not need to enter an activation code.
              </div>
            )}

            <label className="block text-sm font-bold text-slate-700">
              New password
              <input
                type="password"
                autoComplete="new-password"
                minLength={PASSWORD_MIN_LENGTH}
                maxLength={PASSWORD_MAX_LENGTH}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                required
                className="mt-2 w-full rounded-xl border border-slate-300 px-3 py-2.5"
              />
            </label>
            <PasswordRequirementList requirements={requirements} active={password.length > 0} />
            <label className="block text-sm font-bold text-slate-700">
              Confirm password
              <input
                type="password"
                autoComplete="new-password"
                minLength={PASSWORD_MIN_LENGTH}
                maxLength={PASSWORD_MAX_LENGTH}
                value={confirm}
                onChange={(event) => setConfirm(event.target.value)}
                required
                className="mt-2 w-full rounded-xl border border-slate-300 px-3 py-2.5"
              />
            </label>
            {confirm.length > 0 && !passwordsMatch ? <p className="text-sm font-semibold text-rose-700">Passwords do not match.</p> : null}
            {error ? <div role="alert" className="rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">{error}</div> : null}
            <button disabled={busy || !policySatisfied || !passwordsMatch} className="w-full rounded-xl bg-blue-700 px-4 py-3 text-sm font-black text-white disabled:bg-slate-300">
              {busy ? 'Saving…' : 'Save password'}
            </button>
          </form>
        )}
      </section>
    </main>
  );
}
