'use client';

import { useEffect, useState } from 'react';
import { TotpQrCode } from '@/components/iam/TotpQrCode';
import { useAuth } from '@/components/auth/AuthProvider';
import type { MfaEnrollment } from '@/lib/iam/types';

function downloadRecoveryCodes(codes: string[]) {
  const blob = new Blob([`OpenDispatch recovery codes\n\n${codes.join('\n')}\n`], { type: 'text/plain;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = 'opendispatch-recovery-codes.txt';
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

export default function MfaEnrollmentPage() {
  const { user, beginMfaEnrollment, confirmMfaEnrollment, logout } = useAuth();
  const [enrollment, setEnrollment] = useState<MfaEnrollment | null>(null);
  const [code, setCode] = useState('');
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (!user || enrollment) return;
    let cancelled = false;
    setBusy(true);
    const accountLabel = user.username?.trim() || user.displayName?.trim() || user.userId;
    void beginMfaEnrollment(accountLabel, 'OpenDispatch')
      .then((value) => { if (!cancelled) setEnrollment(value); })
      .catch((reason) => { if (!cancelled) setError(reason instanceof Error ? reason.message : 'Unable to start MFA enrollment.'); })
      .finally(() => { if (!cancelled) setBusy(false); });
    return () => { cancelled = true; };
  }, [beginMfaEnrollment, enrollment, user]);

  async function copyCodes() {
    if (!enrollment) return;
    await navigator.clipboard.writeText(enrollment.recoveryCodes.join('\n'));
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1800);
  }

  async function confirm() {
    if (!enrollment) return;
    setError('');
    if (!/^\d{6}$/.test(code)) {
      setError('Enter the 6-digit code shown by your authenticator app.');
      return;
    }
    if (!saved) {
      setError('Confirm that you saved the recovery codes before continuing.');
      return;
    }
    setBusy(true);
    try {
      await confirmMfaEnrollment(enrollment.methodId, code, true, enrollment.expectedVersion);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'The authenticator code could not be confirmed.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="mx-auto max-w-5xl pt-8">
      <div className="grid gap-6 lg:grid-cols-[.75fr_1.25fr]">
        <section className="rounded-3xl bg-slate-950 p-7 text-white shadow-xl">
          <p className="text-xs font-black uppercase tracking-[.2em] text-cyan-300">Required security setup</p>
          <h1 className="mt-4 text-3xl font-black">Protect your account with MFA</h1>
          <p className="mt-4 text-sm leading-7 text-slate-300">
            Open Google Authenticator, Microsoft Authenticator, or another TOTP-compatible app. Scan the QR code, save the recovery codes, then enter one 6-digit code.
          </p>
          <ol className="mt-6 space-y-3 text-sm font-semibold text-slate-200">
            <li className="rounded-xl bg-white/5 p-3"><b className="text-white">1.</b> Scan the QR code</li>
            <li className="rounded-xl bg-white/5 p-3"><b className="text-white">2.</b> Save recovery codes somewhere secure</li>
            <li className="rounded-xl bg-white/5 p-3"><b className="text-white">3.</b> Enter the current 6-digit code</li>
          </ol>
          <button type="button" onClick={() => void logout()} className="mt-6 w-full rounded-xl border border-white/20 px-4 py-2.5 text-sm font-black text-white">
            Sign out and finish later
          </button>
        </section>

        <section className="rounded-3xl border border-slate-200 bg-white p-7 shadow-sm">
          {busy && !enrollment ? <p className="rounded-xl bg-slate-50 p-4 text-sm font-semibold text-slate-600">Preparing secure authenticator enrollment…</p> : null}
          {enrollment ? (
            <div className="space-y-6">
              <section>
                <h2 className="text-xl font-black text-slate-950">1. Scan the authenticator QR code</h2>
                <p className="mt-2 text-sm text-slate-600">Account: <b>{user?.username}</b> · TOTP · 6 digits · 30 seconds</p>
                <div className="mt-4"><TotpQrCode value={enrollment.provisioningUri} label={user?.username ?? 'OpenDispatch account'} /></div>
                <details className="mt-4 rounded-xl border border-slate-200 bg-slate-50 p-4">
                  <summary className="cursor-pointer text-sm font-black text-slate-800">Can’t scan the QR code?</summary>
                  <p className="mt-3 text-xs leading-5 text-slate-600">Choose manual setup in your authenticator app and enter this secret:</p>
                  <code className="mt-2 block break-all rounded-lg bg-white p-3 text-sm font-black text-slate-900">{enrollment.secretDisplay}</code>
                </details>
              </section>

              <section className="border-t border-slate-200 pt-5">
                <h2 className="text-xl font-black text-slate-950">2. Save recovery codes</h2>
                <p className="mt-2 text-sm leading-6 text-slate-600">Each recovery code can be used once if you lose access to your authenticator.</p>
                <div className="mt-3 grid grid-cols-2 gap-2 rounded-2xl bg-slate-950 p-4 font-mono text-sm font-bold text-white sm:grid-cols-3">
                  {enrollment.recoveryCodes.map((item) => <span key={item} className="rounded-lg bg-white/10 px-2 py-2 text-center">{item}</span>)}
                </div>
                <div className="mt-3 flex flex-wrap gap-2">
                  <button type="button" onClick={() => void copyCodes()} className="rounded-xl border border-slate-300 px-3 py-2 text-sm font-black">{copied ? 'Copied' : 'Copy codes'}</button>
                  <button type="button" onClick={() => downloadRecoveryCodes(enrollment.recoveryCodes)} className="rounded-xl border border-slate-300 px-3 py-2 text-sm font-black">Save as file</button>
                </div>
                <label className="mt-4 flex cursor-pointer items-start gap-3 rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm font-semibold text-amber-950">
                  <input type="checkbox" checked={saved} onChange={(event) => setSaved(event.target.checked)} className="mt-1" />
                  <span>I saved the recovery codes in an approved secure location.</span>
                </label>
              </section>

              <section className="border-t border-slate-200 pt-5">
                <h2 className="text-xl font-black text-slate-950">3. Confirm your authenticator</h2>
                <label className="mt-3 block text-sm font-bold text-slate-700">
                  6-digit authenticator code
                  <input inputMode="numeric" autoComplete="one-time-code" maxLength={6} value={code} onChange={(event) => setCode(event.target.value.replace(/\D/g, '').slice(0, 6))} className="mt-2 w-full rounded-xl border border-slate-300 px-3 py-3 text-center text-2xl font-black tracking-[.35em]" />
                </label>
                {error ? <div role="alert" className="mt-3 rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">{error}</div> : null}
                <button type="button" disabled={busy || !saved || code.length !== 6} onClick={() => void confirm()} className="mt-4 w-full rounded-xl bg-blue-700 px-4 py-3 text-sm font-black text-white disabled:bg-slate-300">
                  {busy ? 'Confirming…' : 'Confirm MFA and finish setup'}
                </button>
              </section>
            </div>
          ) : null}
          {!enrollment && error ? <div role="alert" className="rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">{error}</div> : null}
        </section>
      </div>
    </main>
  );
}
