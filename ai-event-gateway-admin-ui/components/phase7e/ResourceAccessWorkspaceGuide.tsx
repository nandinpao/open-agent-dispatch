'use client';
import Link from 'next/link';
const areas=[
  ['Ownership & Participants','Canonical resource ownership and participant visibility evidence.','/resource-access'],
  ['Scope Grants','Temporary, least-privilege grants with visibility caps and lifecycle approval.','/resource-access/grants'],
  ['Explicit Denies','Deny evidence that overrides all allow sources.','/resource-access/denies'],
  ['Access Requests','Prepare temporary self-service requests without selecting raw permission codes.','/resource-access/access-requests'],
  ['Access Reviews','Keep, reduce, revoke, or request more information.','/resource-access/reviews'],
  ['Orphan Repair','Preview ownership blast radius and repair fail-closed resources.','/resource-access/orphans'],
] as const;
export function ResourceAccessWorkspaceGuide(){return <section className="rounded-2xl border border-indigo-200 bg-indigo-50/40 p-5"><h2 className="font-black text-slate-950">Unified Resource Access workspace</h2><p className="mt-2 text-sm leading-6 text-slate-600">Permission answers what action may exist. Resource Access still evaluates ownership, participant, organization scope, explicit grants, deny precedence, visibility, security state, and current policy epochs.</p><div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-3">{areas.map(([title,description,href])=><Link key={title} href={href} className="rounded-xl border border-slate-200 bg-white p-4 hover:border-indigo-400"><h3 className="font-black text-slate-900">{title}</h3><p className="mt-1 text-xs leading-5 text-slate-600">{description}</p></Link>)}</div></section>}
