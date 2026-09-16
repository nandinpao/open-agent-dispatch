'use client';

import type { A2ARequestRecord } from '@/lib/phase7c/taskA2aUx';

export function A2AApprovalExperiencePanel({ request }: Readonly<{ request?: A2ARequestRecord }>) {
  if (!request) return <section className="rounded-2xl border border-slate-200 bg-white p-5"><h3 className="font-black">Approval evidence unavailable</h3><p className="mt-2 text-sm text-slate-600">The operational projection is visible, but the current user cannot read the full delegation approval record.</p></section>;
  const required = request.requiredApprovalCount ?? 0;
  const recorded = request.approvalCount ?? 0;
  const pending = required > recorded;
  return (
    <section className={`rounded-2xl border p-5 ${pending ? 'border-amber-200 bg-amber-50' : 'border-emerald-200 bg-emerald-50'}`} aria-label="A2A approval status">
      <div className="flex flex-wrap items-start justify-between gap-3"><div><p className="text-xs font-black uppercase tracking-[0.18em] text-slate-600">Approval</p><h3 className="mt-1 text-lg font-black text-slate-950">{request.approvalStatus || (pending ? 'PENDING' : 'NOT_REQUIRED')}</h3></div><span className="rounded-full bg-white px-3 py-1 text-xs font-black">{recorded}/{required} recorded</span></div>
      <p className="mt-3 text-sm leading-6 text-slate-800">The requester cannot approve their own request. Approval does not permanently authorize execution; OpenDispatch reauthorizes and checks the current resource version before creating or releasing work.</p>
      <dl className="mt-4 grid gap-3 sm:grid-cols-3"><div className="rounded-xl bg-white/80 p-3"><dt className="text-xs font-bold uppercase text-slate-400">Requester</dt><dd className="mt-1 break-all font-black">{request.requestedByType || 'UNKNOWN'} · {request.requestedById || 'Restricted'}</dd></div><div className="rounded-xl bg-white/80 p-3"><dt className="text-xs font-bold uppercase text-slate-400">Expiration</dt><dd className="mt-1 font-black">{request.expiresAt || 'Not provided'}</dd></div><div className="rounded-xl bg-white/80 p-3"><dt className="text-xs font-bold uppercase text-slate-400">Request version</dt><dd className="mt-1 font-black">{request.version}</dd></div></dl>
    </section>
  );
}
