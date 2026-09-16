'use client';

import Link from 'next/link';
import { EntitlementPageGuard } from '@/components/auth/EntitlementPageGuard';
import { useAuth } from '@/components/auth/AuthProvider';

export default function MyAccountPage() {
  const { user, logout } = useAuth();
  return (
    <EntitlementPageGuard featureId="my-account">
      <main className="mx-auto w-full max-w-5xl space-y-5 p-5 sm:p-7">
        <section className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
          <p className="text-xs font-black uppercase tracking-[.18em] text-blue-700">Identity self-service</p>
          <h1 className="mt-2 text-2xl font-black text-slate-950">My Account</h1>
          <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-600">This area is available to every authenticated Person. It does not grant access to OpenDispatch business workspaces; those areas come only from assigned Responsibilities.</p>
        </section>
        <section className="grid gap-4 md:grid-cols-2">
          <div className="rounded-2xl border border-slate-200 bg-white p-5">
            <p className="text-xs font-black uppercase tracking-wide text-slate-500">Identity</p>
            <dl className="mt-3 space-y-3 text-sm">
              <Row label="Display name" value={user?.displayName || 'Unavailable'} />
              <Row label="Sign-in name" value={user?.username || 'Unavailable'} />
              <Row label="Workspace" value={user?.selectedTenantId || 'Instance scope'} />
              <Row label="Authentication" value={(user?.authenticationMethods ?? []).join(' + ') || 'Unavailable'} />
            </dl>
          </div>
          <div className="rounded-2xl border border-slate-200 bg-white p-5">
            <p className="text-xs font-black uppercase tracking-wide text-slate-500">Workspace access</p>
            {(user?.roles ?? []).length ? <><p className="mt-3 text-sm font-black text-emerald-800">Responsibilities are assigned</p><p className="mt-2 text-sm leading-6 text-slate-600">Business Navigator entries are projected from your effective permissions and scope. If an expected area is missing, ask an administrator to review Effective Access.</p></> : <div className="mt-3 rounded-2xl border border-amber-300 bg-amber-50 p-4 text-sm leading-6 text-amber-950"><b className="block">No business responsibility is assigned.</b>Your account is active, but no operational or administration workspace is available yet. An administrator must assign a Responsibility.</div>}
            <div className="mt-4 flex flex-wrap gap-2">
              <Link href="/change-password" className="rounded-xl border border-slate-300 px-3 py-2 text-xs font-black text-slate-800">Change password</Link>
              <button type="button" onClick={() => { void logout(); }} className="rounded-xl bg-slate-950 px-3 py-2 text-xs font-black text-white">Sign out</button>
            </div>
          </div>
        </section>
      </main>
    </EntitlementPageGuard>
  );
}

function Row({ label, value }: Readonly<{ label: string; value: string }>) {
  return <div className="flex items-start justify-between gap-4 border-b border-slate-100 pb-2 last:border-0 last:pb-0"><dt className="font-bold text-slate-600">{label}</dt><dd className="max-w-[60%] break-all text-right font-black text-slate-950">{value}</dd></div>;
}
