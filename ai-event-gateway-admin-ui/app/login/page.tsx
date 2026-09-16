'use client';

import { FormEvent, useEffect, useState } from 'react';
import Link from 'next/link';
import { useAuth } from '@/components/auth/AuthProvider';
import { authApi } from '@/lib/api/authApi';
import type { FederationProviderPublic } from '@/lib/iam/types';

function signInErrorMessage(error: unknown): string {
  const message = error instanceof Error ? error.message : 'Sign-in failed.';
  if (message.toLowerCase().includes('invalid credentials')) {
    return 'Invalid username or password.';
  }
  return message;
}


function humanFederationError(code: string): string {
  if (code === 'AUTH_FEDERATION_IDENTITY_NOT_LINKED') return 'Your enterprise identity is valid, but it is not linked to an active Person in this workspace. Contact a workspace administrator.';
  if (code === 'AUTH_FEDERATION_UPSTREAM_MFA_REQUIRED') return 'This workspace requires MFA evidence from the enterprise identity provider. Complete provider MFA and sign in again.';
  if (code === 'AUTH_FEDERATION_ACCOUNT_NOT_ACTIVE') return 'The linked OpenDispatch Person is not active. Contact a workspace administrator.';
  if (code === 'AUTH_FEDERATION_STATE_EXPIRED' || code === 'AUTH_FEDERATION_STATE_INVALID') return 'The enterprise sign-in attempt expired or was already used. Start enterprise sign-in again.';
  if (code === 'AUTH_FEDERATION_PROVIDER_REJECTED') return 'The enterprise identity provider did not complete sign-in.';
  return 'Enterprise sign-in could not be completed. Start again or contact a workspace administrator with the correlation evidence from the server logs.';
}

export default function LoginPage() {
  const { login, verifyMfa, pendingLogin, status } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [mfaCode, setMfaCode] = useState('');
  const [recoveryCode, setRecoveryCode] = useState(false);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [passwordChanged, setPasswordChanged] = useState(false);
  const [accountActivated, setAccountActivated] = useState(false);
  const [mfaEnrolled, setMfaEnrolled] = useState(false);
  const [federatedSignIn, setFederatedSignIn] = useState(false);
  const [federationCallbackError, setFederationCallbackError] = useState('');
  const [enterpriseTenant, setEnterpriseTenant] = useState('');
  const [enterpriseProviders, setEnterpriseProviders] = useState<FederationProviderPublic[]>([]);
  const [enterpriseLoading, setEnterpriseLoading] = useState(false);
  const [enterpriseError, setEnterpriseError] = useState('');

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    setPasswordChanged(params.get('passwordChanged') === '1');
    setAccountActivated(params.get('accountActivated') === '1');
    setMfaEnrolled(params.get('mfaEnrolled') === '1');
    setFederatedSignIn(params.get('federatedSignIn') === '1');
    const federationError = params.get('federationError');
    setFederationCallbackError(federationError ? humanFederationError(federationError) : '');
  }, []);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      // The runtime chooses the temporary credential provider. The browser never chooses
      // between IAM and Legacy identities, sessions or authorization models.
      await login({ username, password });
    } catch (err) {
      setError(signInErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  }



  async function findEnterpriseProviders() {
    const tenant = enterpriseTenant.trim();
    if (!tenant) { setEnterpriseError('Enter the company workspace code supplied by your administrator.'); return; }
    setEnterpriseError(''); setEnterpriseLoading(true); setEnterpriseProviders([]);
    try {
      const providers = await authApi.federationProviders(tenant);
      setEnterpriseProviders(providers);
      if (!providers.length) setEnterpriseError('No active enterprise sign-in provider is available for that workspace.');
    } catch (err) {
      setEnterpriseError(signInErrorMessage(err));
    } finally { setEnterpriseLoading(false); }
  }

  async function startEnterpriseSignIn(provider: FederationProviderPublic) {
    setEnterpriseError(''); setEnterpriseLoading(true);
    try {
      const started = await authApi.startOidc(provider.tenantId, provider.providerId, '/dashboard');
      window.location.assign(started.authorizationUrl);
    } catch (err) {
      setEnterpriseError(signInErrorMessage(err));
      setEnterpriseLoading(false);
    }
  }

  async function submitMfa(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      await verifyMfa(mfaCode, recoveryCode);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'MFA verification failed.');
    } finally {
      setSubmitting(false);
    }
  }

  return <main className="mx-auto grid max-w-5xl gap-8 pt-10 lg:grid-cols-[1.1fr_.9fr]">
    <section className="rounded-3xl bg-slate-950 p-8 text-white shadow-xl">
      <p className="text-xs font-black uppercase tracking-[.2em] text-cyan-300">OpenDispatch</p>
      <h1 className="mt-4 text-4xl font-black leading-tight">Secure identity for enterprise dispatch operations</h1>
      <p className="mt-5 max-w-xl text-sm leading-7 text-slate-300">Sign in through the platform identity service. Tenant context, permissions, session security and audit decisions are resolved by one authoritative security plane.</p>
      <div className="mt-8 grid gap-3 text-sm">
        <div className="rounded-2xl border border-white/10 bg-white/5 p-4"><b>MFA-aware sessions</b><div className="mt-1 text-slate-400">Administrative identities can be required to verify TOTP or a one-time recovery code.</div></div>
        <div className="rounded-2xl border border-white/10 bg-white/5 p-4"><b>Hard Tenant boundary</b><div className="mt-1 text-slate-400">Tenant access is resolved from active memberships after authentication. Your home workspace is resolved automatically from the Tenant Membership configured by an administrator.</div></div>
      </div>
    </section>
    <section className="rounded-3xl border border-slate-200 bg-white p-7 shadow-sm" aria-live="polite">
      <p className="text-xs font-black uppercase tracking-[.18em] text-blue-600">{pendingLogin ? 'Additional verification' : 'Sign in'}</p>
      <h2 className="mt-2 text-2xl font-black text-slate-950">{pendingLogin ? 'Verify multi-factor authentication' : 'Access the administration workspace'}</h2>
      {passwordChanged && !pendingLogin ? <div className="mt-5 rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-900">Password changed. The restricted session was revoked; sign in again with the new password.</div> : null}
      {accountActivated && !pendingLogin ? <div className="mt-5 rounded-xl border border-blue-200 bg-blue-50 p-3 text-sm text-blue-900">Password setup completed. Sign in now; OpenDispatch will guide you to required MFA enrollment.</div> : null}
      {mfaEnrolled && !pendingLogin ? <div className="mt-5 rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-900">MFA enrollment completed. Sign in again to open your authorized Tenant workspace.</div> : null}
      {federatedSignIn && !pendingLogin ? <div className="mt-5 rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-900">Enterprise sign-in completed. OpenDispatch is validating the canonical Person, Tenant membership and authorization scope.</div> : null}
      {federationCallbackError && !pendingLogin ? <div role="alert" className="mt-5 rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-900">{federationCallbackError}</div> : null}
      {!pendingLogin ? <form onSubmit={onSubmit} className="mt-6 space-y-4">
        <label className="block text-sm font-bold text-slate-700">Username<input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" required className="mt-2 w-full rounded-xl border border-slate-300 px-3 py-2.5 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100" /></label>
        <label className="block text-sm font-bold text-slate-700">Password<input value={password} onChange={(e) => setPassword(e.target.value)} type="password" autoComplete="current-password" required className="mt-2 w-full rounded-xl border border-slate-300 px-3 py-2.5 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100" /></label>
        {error ? <div role="alert" className="rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">{error}</div> : null}
        <button disabled={submitting || status === 'CHECKING'} className="w-full rounded-xl bg-blue-600 px-4 py-2.5 text-sm font-black text-white hover:bg-blue-700 disabled:bg-slate-300">{submitting ? 'Signing in…' : 'Sign in'}</button>
      </form> : <form onSubmit={submitMfa} className="mt-6 space-y-4">
        <p className="rounded-xl border border-blue-200 bg-blue-50 p-3 text-sm text-blue-900">Enter a code from the authenticator or recovery codes previously enrolled for this account. Fresh installations are redirected to MFA enrollment instead of this verification step.</p>
        <label className="block text-sm font-bold text-slate-700">{recoveryCode ? 'Recovery code' : 'Authenticator code'}<input value={mfaCode} onChange={(e) => setMfaCode(e.target.value)} autoComplete="one-time-code" required className="mt-2 w-full rounded-xl border border-slate-300 px-3 py-2.5 tracking-widest outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100" /></label>
        <label className="flex items-center gap-2 text-sm font-bold text-slate-700"><input type="checkbox" checked={recoveryCode} onChange={(e) => setRecoveryCode(e.target.checked)} />Use a one-time recovery code</label>
        {error ? <div role="alert" className="rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">{error}</div> : null}
        <button disabled={submitting} className="w-full rounded-xl bg-blue-600 px-4 py-2.5 text-sm font-black text-white disabled:bg-slate-300">{submitting ? 'Verifying…' : 'Verify and continue'}</button>
      </form>}
      {!pendingLogin ? <>
        <div className="my-6 flex items-center gap-3 text-xs font-black uppercase tracking-[.16em] text-slate-400"><span className="h-px flex-1 bg-slate-200"/>Enterprise sign-in<span className="h-px flex-1 bg-slate-200"/></div>
        <section className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
          <h3 className="text-sm font-black text-slate-950">Use your company identity provider</h3>
          <p className="mt-1 text-xs leading-5 text-slate-600">Enter the workspace code supplied by your administrator. The external provider authenticates your identity only; OpenDispatch still decides workspace membership, Responsibilities, scopes, and data access. Standard username/password sign-in does not require a workspace code.</p>
          <div className="mt-3 flex gap-2">
            <input aria-label="Company workspace" value={enterpriseTenant} onChange={(event)=>{setEnterpriseTenant(event.target.value);setEnterpriseProviders([]);setEnterpriseError('');}} placeholder="Example: kuang-ho" className="min-w-0 flex-1 rounded-xl border border-slate-300 bg-white px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"/>
            <button type="button" onClick={()=>{void findEnterpriseProviders();}} disabled={enterpriseLoading||!enterpriseTenant.trim()} className="rounded-xl border border-slate-300 bg-white px-3 py-2 text-sm font-black text-slate-800 disabled:text-slate-400">{enterpriseLoading?'Checking…':'Continue'}</button>
          </div>
          {enterpriseProviders.length ? <div className="mt-3 space-y-2">{enterpriseProviders.map((provider)=><button key={provider.providerId} type="button" onClick={()=>{void startEnterpriseSignIn(provider);}} disabled={enterpriseLoading} className="w-full rounded-xl bg-slate-950 px-4 py-2.5 text-left text-sm font-black text-white disabled:bg-slate-400"><span className="block">Continue with {provider.displayName}</span><span className="mt-0.5 block text-xs font-medium text-slate-300">{provider.upstreamMfaRequired?'Provider MFA is required for this workspace.':'Provider authentication will be validated by OpenDispatch.'}</span></button>)}</div>:null}
          {enterpriseError ? <div role="alert" className="mt-3 rounded-xl border border-amber-200 bg-amber-50 p-3 text-xs text-amber-900">{enterpriseError}</div>:null}
        </section>
        <div className="mt-5 text-sm"><Link href="/forgot-password" className="font-bold text-blue-700">Forgot password?</Link></div>
      </> : null}
    </section>
  </main>;
}
