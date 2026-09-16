'use client';
import type { Permission } from '@/lib/iam/types';
import { summarizePermissionBundle } from '@/lib/phase7e/iamResourceAccessUx';

export function RolePermissionBundlePanel({ permissions, selectedCodes }: Readonly<{ permissions: Permission[]; selectedCodes: ReadonlySet<string> }>) {
  const selected = permissions.filter((permission) => selectedCodes.has(permission.permissionCode));
  const summary = summarizePermissionBundle(selected);
  const groups = new Map<string, Permission[]>();
  for (const permission of selected) {
    const domain = permission.permissionCode.split('.')[0]?.toUpperCase() || 'OTHER';
    groups.set(domain, [...(groups.get(domain) ?? []), permission]);
  }
  return <section className="rounded-2xl border border-indigo-200 bg-indigo-50/50 p-4" aria-label="Role permission bundle impact">
    <div className="flex flex-wrap items-start justify-between gap-3"><div><h3 className="font-black text-slate-950">Permission bundle impact</h3><p className="mt-1 text-sm text-slate-600">Review the complete Role bundle before replacement. The browser does not authorize the Role; the backend saves and revalidates the full matrix.</p></div><span className="rounded-full bg-white px-3 py-1 text-sm font-black text-indigo-800">{summary.total} selected</span></div>
    <dl className="mt-4 grid grid-cols-2 gap-2 text-sm sm:grid-cols-5"><Metric label="Read" value={summary.read}/><Metric label="Write" value={summary.write}/><Metric label="Export" value={summary.export}/><Metric label="Admin" value={summary.admin}/><Metric label="Critical" value={summary.critical}/></dl>
    {summary.requiresIndependentApproval?<p className="mt-3 rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm font-semibold text-amber-950">This bundle contains administrative or critical operations. Activation requires current backend authorization, separation of duties, and any required step-up assurance.</p>:null}
    <div className="mt-4 grid gap-3 md:grid-cols-2">{[...groups.entries()].sort(([a],[b])=>a.localeCompare(b)).map(([domain, entries])=><article key={domain} className="rounded-xl border border-slate-200 bg-white p-3"><div className="flex justify-between gap-2"><h4 className="font-black text-slate-900">{domain}</h4><span className="text-xs font-bold text-slate-500">{entries.length} actions</span></div><ul className="mt-2 space-y-1 text-xs text-slate-600">{entries.slice(0,5).map(permission=><li key={permission.permissionCode}><code>{permission.permissionCode}</code> · {permission.displayName}</li>)}{entries.length>5?<li>+ {entries.length-5} more</li>:null}</ul></article>)}</div>
  </section>;
}
function Metric({label,value}:{label:string;value:number}){return <div className="rounded-xl border border-slate-200 bg-white p-3"><dt className="text-xs font-bold uppercase tracking-wide text-slate-500">{label}</dt><dd className="mt-1 text-xl font-black text-slate-950">{value}</dd></div>}
