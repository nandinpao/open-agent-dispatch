'use client';

import Link from 'next/link';
import { useAuth } from '@/components/auth/AuthProvider';

/** Beginner-facing pointer into the canonical Tenant Security & Audit workspace. */
export function AuthenticationSecurityPanel() {
  const { activeTenantId: selectedTenantId } = useAuth();
  const base = selectedTenantId ? `/admin/tenants/${encodeURIComponent(selectedTenantId)}/security` : '/admin/tenants';
  return (
    <section className="rounded-2xl border border-blue-200 bg-blue-50 p-5 shadow-sm">
      <div>
        <div className="text-sm font-bold text-slate-950">Sign-in security & audit</div>
        <p className="mt-1 max-w-3xl text-sm leading-6 text-slate-600">
          Password, MFA, sessions, credentials and audit are managed in the same People & Access workspace as the person or business unit you are reviewing.
        </p>
      </div>
      <div className="mt-4 flex flex-wrap gap-2">
        <Link href={base} className="rounded-xl bg-blue-700 px-4 py-2.5 text-sm font-black text-white">Open Security & Audit</Link>
        {selectedTenantId ? <Link href={`/admin/tenants/${encodeURIComponent(selectedTenantId)}/people`} className="rounded-xl border border-blue-300 bg-white px-4 py-2.5 text-sm font-black text-blue-800">Review person sign-in</Link> : null}
      </div>
    </section>
  );
}
