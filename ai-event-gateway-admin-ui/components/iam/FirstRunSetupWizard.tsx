'use client';

import {
  type FormEvent,
  useEffect,
  useMemo,
  useState,
} from 'react';
import { bootstrapApi } from '@/lib/api/bootstrapApi';
import { useAuth } from '@/components/auth/AuthProvider';
import type { BootstrapStatus, MfaEnrollment } from '@/lib/iam/types';
import { TotpQrCode } from '@/components/iam/TotpQrCode';

const steps = [
  'Set up MFA',
  'Create Tenant',
  'Create Tenant administrator',
  'Complete',
] as const;

interface FieldProps {
  label: string;
  name: string;
  type?: string;
  required?: boolean;
  defaultValue?: string;
  autoComplete?: string;
  inputMode?: 'text' | 'numeric' | 'email';
  maxLength?: number;
  pattern?: string;
}

function Field({
  label,
  name,
  type = 'text',
  required = true,
  defaultValue,
  autoComplete,
  inputMode,
  maxLength,
  pattern,
}: Readonly<FieldProps>) {
  return (
    <label className="block text-sm font-bold text-slate-700">
      {label}
      <input
        name={name}
        type={type}
        required={required}
        defaultValue={defaultValue}
        autoComplete={autoComplete}
        inputMode={inputMode}
        maxLength={maxLength}
        pattern={pattern}
        className="mt-2 w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
      />
    </label>
  );
}

function nextIncomplete(status: BootstrapStatus): number {
  const completed = new Set(status.completedSteps);
  if (!completed.has('MFA_CONFIGURED')) return 0;
  if (!completed.has('TENANT_CREATED')) return 1;
  if (!completed.has('TENANT_ADMIN_CREATED')) return 2;
  return 3;
}

export function FirstRunSetupWizard() {
  const { logout } = useAuth();
  const [status, setStatus] = useState<BootstrapStatus | null>(null);
  const [step, setStep] = useState(0);
  const [mfa, setMfa] = useState<MfaEnrollment | null>(null);
  const [mfaAccountLabel, setMfaAccountLabel] = useState('root');
  const [mfaIssuer, setMfaIssuer] = useState('OpenDispatch');
  const [recoveryCodesSaved, setRecoveryCodesSaved] = useState(false);
  const [tenantId, setTenantId] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  async function refresh() {
    const next = await bootstrapApi.status();
    setStatus(next);
    setStep(nextIncomplete(next));
    return next;
  }

  useEffect(() => {
    void refresh().catch((reason: unknown) => {
      setError(reason instanceof Error ? reason.message : 'Unable to load setup status.');
    });
  }, []);

  const complete = status?.status === 'COMPLETED' || status?.bootstrapApiOpen === false;
  const progress = useMemo(
    () => Math.round(((step + 1) / steps.length) * 100),
    [step],
  );

  async function execute(action: () => Promise<unknown>) {
    setBusy(true);
    setError('');
    setNotice('');
    try {
      await action();
      await refresh();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Setup action failed.');
    } finally {
      setBusy(false);
    }
  }

  async function generateMfaEnrollment(accountLabel: string, issuer: string) {
    setBusy(true);
    setError('');
    setNotice('');
    setRecoveryCodesSaved(false);
    try {
      setMfaAccountLabel(accountLabel);
      setMfaIssuer(issuer);
      setMfa(await bootstrapApi.beginRootMfa(accountLabel, issuer));
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'MFA enrollment failed.');
    } finally {
      setBusy(false);
    }
  }

  async function copyRecoveryCodes() {
    if (!mfa) return;
    try {
      await navigator.clipboard.writeText(mfa.recoveryCodes.join('\n'));
      setNotice('Recovery codes copied. Store them in an approved secure location.');
    } catch {
      setError('The browser could not copy the recovery codes. Select and copy them manually.');
    }
  }

  function downloadRecoveryCodes() {
    if (!mfa) return;
    const content = [
      'OpenDispatch Root MFA recovery codes',
      'Store these codes in an approved secure location.',
      '',
      ...mfa.recoveryCodes,
      '',
    ].join('\n');
    const href = URL.createObjectURL(new Blob([content], { type: 'text/plain;charset=utf-8' }));
    const anchor = document.createElement('a');
    anchor.href = href;
    anchor.download = 'opendispatch-root-recovery-codes.txt';
    anchor.click();
    URL.revokeObjectURL(href);
    setNotice('Recovery-code file created. Move it to an approved secure location.');
  }

  if (complete) {
    return (
      <section className="mx-auto max-w-2xl rounded-3xl border border-emerald-200 bg-emerald-50 p-8 text-emerald-950 shadow-sm">
        <h1 className="text-2xl font-black">Initial setup is complete</h1>
        <p className="mt-3 text-sm leading-6">
          Root installation, permanent password, MFA, the first Tenant, and its daily
          administrator are configured. The restricted bootstrap session must now be
          replaced by a normal MFA-verified Root session.
        </p>
        <button
          onClick={() => void logout()}
          className="mt-6 rounded-xl bg-emerald-700 px-5 py-2.5 text-sm font-black text-white"
        >
          Sign in again with MFA
        </button>
      </section>
    );
  }

  return (
    <main className="mx-auto max-w-4xl space-y-6" aria-labelledby="setup-title">
      <header>
        <p className="text-xs font-black uppercase tracking-[.18em] text-blue-600">
          Instance bootstrap
        </p>
        <h1 id="setup-title" className="mt-2 text-3xl font-black text-slate-950">
          Complete this OpenDispatch installation
        </h1>
        <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-600">
          The installer-created Root and its temporary password have already been
          replaced. Complete MFA, create the first Tenant, and establish a named daily
          administrator.
        </p>
      </header>

      <section className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex items-center justify-between gap-4">
          <span className="text-sm font-black text-slate-900">
            Step {step + 1} of {steps.length}: {steps[step]}
          </span>
          <span className="text-xs font-bold text-slate-500">{progress}%</span>
        </div>
        <div className="mt-3 h-2 overflow-hidden rounded-full bg-slate-100">
          <div
            className="h-full rounded-full bg-blue-600 transition-all"
            style={{ width: `${progress}%` }}
          />
        </div>

        {error ? (
          <div role="alert" className="mt-5 rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">
            {error}
          </div>
        ) : null}
        {notice ? (
          <div role="status" className="mt-5 rounded-xl border border-blue-200 bg-blue-50 p-3 text-sm text-blue-900">
            {notice}
          </div>
        ) : null}

        {step === 0 ? (
          <div className="mt-6 space-y-5">
            {!mfa ? (
              <form
                className="space-y-4"
                onSubmit={(event) => {
                  event.preventDefault();
                  const data = new FormData(event.currentTarget);
                  void generateMfaEnrollment(
                    String(data.get('accountLabel')),
                    String(data.get('issuer')),
                  );
                }}
              >
                <Field label="Authenticator account label" name="accountLabel" defaultValue="root" />
                <Field label="Issuer" name="issuer" defaultValue="OpenDispatch" />
                <button
                  disabled={busy}
                  className="rounded-xl bg-blue-600 px-5 py-2.5 text-sm font-black text-white disabled:bg-slate-300"
                >
                  Generate MFA enrollment
                </button>
              </form>
            ) : (
              <>
                <div className="grid gap-5 lg:grid-cols-[280px_1fr]">
                  <div className="rounded-2xl border border-blue-200 bg-blue-50 p-4">
                    <div className="font-black text-blue-950">Scan with an authenticator app</div>
                    <p className="mt-1 text-xs leading-5 text-blue-900">
                      Use Google Authenticator or Microsoft Authenticator. In Microsoft
                      Authenticator, choose <b>Other account</b>, then scan this QR code.
                    </p>
                    <div className="mt-4">
                      <TotpQrCode value={mfa.provisioningUri} label="OpenDispatch root" />
                    </div>
                    <div className="mt-3 flex flex-wrap gap-2 text-xs font-black uppercase tracking-wide text-blue-800">
                      <span className="rounded-full bg-white px-2.5 py-1">TOTP</span>
                      <span className="rounded-full bg-white px-2.5 py-1">SHA-1</span>
                      <span className="rounded-full bg-white px-2.5 py-1">6 digits</span>
                      <span className="rounded-full bg-white px-2.5 py-1">30 seconds</span>
                    </div>
                  </div>

                  <div className="space-y-5">
                    <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4">
                      <div className="font-black text-amber-950">Save these recovery codes now</div>
                      <p className="mt-1 text-xs text-amber-900">
                        They are displayed only once. Store them in an approved password vault.
                      </p>
                      <pre className="mt-3 overflow-auto rounded-xl bg-slate-950 p-4 text-xs text-slate-100">
                        {mfa.recoveryCodes.join('\n')}
                      </pre>
                      <div className="mt-3 flex flex-wrap gap-2">
                        <button
                          type="button"
                          onClick={() => void copyRecoveryCodes()}
                          className="rounded-lg border border-amber-300 bg-white px-3 py-2 text-xs font-black text-amber-950"
                        >
                          Copy recovery codes
                        </button>
                        <button
                          type="button"
                          onClick={downloadRecoveryCodes}
                          className="rounded-lg border border-amber-300 bg-white px-3 py-2 text-xs font-black text-amber-950"
                        >
                          Download recovery codes
                        </button>
                      </div>
                    </div>

                    <details className="rounded-2xl border border-slate-200 p-4 text-sm">
                      <summary className="cursor-pointer font-black text-slate-900">Manual setup key</summary>
                      <p className="mt-3 text-xs leading-5 text-slate-600">
                        Use this only when the device cannot scan the QR code. Treat the
                        key as a password and never send it through email or chat.
                      </p>
                      <div className="mt-3 rounded-xl bg-slate-100 p-3">
                        <b>Secret:</b> <code className="break-all">{mfa.secretDisplay}</code>
                      </div>
                      <div className="mt-2 break-all text-xs text-slate-500">{mfa.provisioningUri}</div>
                    </details>
                  </div>
                </div>

                <form
                  className="space-y-4"
                  onSubmit={(event) => {
                    event.preventDefault();
                    const data = new FormData(event.currentTarget);
                    void execute(() => bootstrapApi.confirmRootMfa(
                      mfa.methodId,
                      String(data.get('code')),
                      recoveryCodesSaved,
                      mfa.expectedVersion,
                    ));
                  }}
                >
                  <Field
                    label="Six-digit authenticator code"
                    name="code"
                    autoComplete="one-time-code"
                    inputMode="numeric"
                    maxLength={6}
                    pattern="[0-9]{6}"
                  />
                  <label className="flex items-start gap-3 rounded-xl border border-amber-300 bg-amber-50 p-4 text-sm font-bold text-amber-950">
                    <input
                      type="checkbox"
                      required
                      checked={recoveryCodesSaved}
                      onChange={(event) => setRecoveryCodesSaved(event.target.checked)}
                      className="mt-0.5 h-4 w-4"
                    />
                    <span>I saved the recovery codes in an approved secure location.</span>
                  </label>
                  <div className="flex flex-wrap gap-3">
                    <button
                      disabled={busy || !recoveryCodesSaved}
                      className="rounded-xl bg-blue-600 px-5 py-2.5 text-sm font-black text-white disabled:bg-slate-300"
                    >
                      Confirm MFA
                    </button>
                    <button
                      type="button"
                      disabled={busy}
                      onClick={() => void generateMfaEnrollment(mfaAccountLabel, mfaIssuer)}
                      className="rounded-xl border border-slate-300 bg-white px-5 py-2.5 text-sm font-black text-slate-700 disabled:text-slate-300"
                    >
                      Regenerate enrollment
                    </button>
                  </div>
                </form>
              </>
            )}
          </div>
        ) : null}

        {step === 1 ? (
          <form
            className="mt-6 grid gap-4 md:grid-cols-2"
            onSubmit={(event: FormEvent<HTMLFormElement>) => {
              event.preventDefault();
              const data = new FormData(event.currentTarget);
              void execute(async () => {
                const created = await bootstrapApi.createFirstTenant({
                  tenantId: null,
                  tenantCode: data.get('tenantCode'),
                  tenantName: data.get('tenantName'),
                  legalName: data.get('legalName'),
                  timezone: data.get('timezone'),
                  locale: data.get('locale'),
                  dataRegion: data.get('dataRegion'),
                });
                setTenantId(created.tenantId);
              });
            }}
          >
            <div className="rounded-xl border border-blue-200 bg-blue-50 p-4 text-sm text-blue-950 md:col-span-2">
              The internal Tenant ID is generated by OpenDispatch. Enter only the business code and display information.
            </div>
            <Field label="Tenant code" name="tenantCode" />
            <Field label="Tenant name" name="tenantName" />
            <Field label="Legal name" name="legalName" required={false} />
            <Field label="Time zone" name="timezone" defaultValue="Asia/Taipei" />
            <Field label="Locale" name="locale" defaultValue="en-US" />
            <Field label="Data region" name="dataRegion" defaultValue="TW" />
            <div className="md:col-span-2">
              <button disabled={busy} className="rounded-xl bg-blue-600 px-5 py-2.5 text-sm font-black text-white disabled:bg-slate-300">
                Create first Tenant
              </button>
            </div>
          </form>
        ) : null}

        {step === 2 ? (
          <form
            className="mt-6 grid gap-4 md:grid-cols-2"
            onSubmit={(event: FormEvent<HTMLFormElement>) => {
              event.preventDefault();
              const data = new FormData(event.currentTarget);
              void execute(async () => {
                if (!tenantId) throw new Error('The first Tenant identity is unavailable. Return to Step 2 and create the Tenant again.');
                await bootstrapApi.createFirstTenantAdmin({
                  tenantId,
                  username: data.get('username'),
                  email: data.get('email'),
                  displayName: data.get('displayName'),
                });
                setNotice('A one-time password-reset credential was delivered through the configured secure delivery adapter. Open the Reset Password page to set the administrator password.');
              });
            }}
          >
            <div className="md:col-span-2 rounded-2xl border border-blue-100 bg-blue-50 p-4 text-sm leading-6 text-blue-900">
              OpenDispatch generates the internal User ID. Enter only the administrator&apos;s business identity below.
            </div>
            <Field label="Username" name="username" autoComplete="username" />
            <Field label="Display name" name="displayName" />
            <Field label="Email" name="email" type="email" autoComplete="email" inputMode="email" />
            <div className="md:col-span-2">
              <button disabled={busy} className="rounded-xl bg-blue-600 px-5 py-2.5 text-sm font-black text-white disabled:bg-slate-300">
                Create Tenant administrator
              </button>
            </div>
          </form>
        ) : null}

        {step === 3 ? (
          <div className="mt-6 space-y-4">
            <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-5 text-sm leading-6 text-emerald-950">
              <b>Review before closing bootstrap.</b>
              <br />
              The permanent Root password and MFA are configured, the first Tenant exists,
              and a named daily Tenant administrator has been created. Completing setup closes
              the bootstrap mutation surface.
            </div>
            <button
              disabled={busy}
              onClick={() => void execute(() => bootstrapApi.complete(status?.version ?? 0))}
              className="rounded-xl bg-emerald-700 px-5 py-2.5 text-sm font-black text-white disabled:bg-slate-300"
            >
              Complete setup and close bootstrap
            </button>
          </div>
        ) : null}
      </section>
    </main>
  );
}
