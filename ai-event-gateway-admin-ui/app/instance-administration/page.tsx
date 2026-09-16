import { EntitlementPageGuard } from '@/components/auth/EntitlementPageGuard';
import Link from 'next/link';
import { RuntimeCapabilityStatusPanel } from '@/components/iam/RuntimeCapabilityStatusPanel';
import { AdminPageHeader } from '@/components/layout/AdminPageHeader';

const actions = [
  {
    href: '/admin/tenants',
    title: 'Tenant Access Management',
    description: 'Select a Tenant, then manage People, Organization, Responsibilities, Assignments, Effective Access, Security and Audit from its canonical workspace.',
  },
];

export default function Page() {
  return <EntitlementPageGuard featureId="instance-administration"><main className="space-y-6">
    <AdminPageHeader
      title="Platform Administration"
      eyebrow="Break-glass workspace"
      description="Root manages platform-wide Tenant lifecycle, canonical access governance and break-glass oversight from one IAM/RBAC control plane."
    />

    <section className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm leading-6 text-amber-950">
      <h2 className="font-black">Root recovery is not an online operation</h2>
      <p className="mt-1">Use the approved server-console or CLI recovery runbook. The web workspace does not expose recovery secrets, privilege grants or a remote root-reset action.</p>
    </section>

    <RuntimeCapabilityStatusPanel />

    <section className="rounded-2xl border-2 border-blue-300 bg-white p-6 shadow-sm">
      <p className="text-xs font-black uppercase tracking-[.18em] text-blue-600">Canonical administration</p>
      <h2 className="mt-2 text-2xl font-black text-slate-950">Access Management</h2>
      <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-600">
        Root uses the same canonical Subject, Session, Atomic Permissions and Role Binding model as delegated administrators. No migration console or compatibility administration surface remains online.
      </p>
      <div className="mt-5 grid gap-3 md:grid-cols-2 xl:grid-cols-3">
        {actions.map(action => <Link key={action.href} href={action.href} className="rounded-2xl border border-slate-200 bg-slate-50 p-4 transition hover:border-blue-300 hover:bg-blue-50">
          <span className="block font-black text-slate-950">{action.title}</span>
          <span className="mt-1 block text-sm leading-6 text-slate-600">{action.description}</span>
        </Link>)}
      </div>
    </section>

    <details className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <summary className="cursor-pointer text-sm font-black text-slate-800">Internal engineering diagnostics</summary>
      <p className="mt-2 max-w-3xl text-xs leading-5 text-slate-600">These tools support authorization rollout, catalog publication and release diagnostics. They are intentionally excluded from normal product and Tenant administration navigation.</p>
      <div className="mt-4 grid gap-3 md:grid-cols-3">
        <Link href="/platform-administration/permission-catalog" className="rounded-xl border border-slate-200 p-3 text-sm font-bold text-slate-700 hover:bg-slate-50">Authorization Registry</Link>
        <Link href="/platform-administration/enforcement-activation" className="rounded-xl border border-slate-200 p-3 text-sm font-bold text-slate-700 hover:bg-slate-50">Authorization Rollout</Link>
        <Link href="/platform-administration/permission-readiness" className="rounded-xl border border-slate-200 p-3 text-sm font-bold text-slate-700 hover:bg-slate-50">Authorization Readiness</Link>
      </div>
    </details>
  </main></EntitlementPageGuard>;
}
