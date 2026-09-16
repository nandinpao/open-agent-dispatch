'use client';
import type { PermissionCatalogRevision, PermissionCatalogValidation } from '@/lib/iam/types';

export function PermissionCatalogLifecyclePanel({ active, selected, validation }: Readonly<{ active: PermissionCatalogRevision | null; selected: PermissionCatalogRevision | null; validation: PermissionCatalogValidation | null }>) {
  const draft = selected?.status === 'DRAFT';
  const errors = validation?.issues.filter(issue=>issue.severity==='ERROR').length ?? 0;
  const warnings = validation?.issues.filter(issue=>issue.severity==='WARNING').length ?? 0;
  return <section className="grid gap-3 rounded-2xl border border-slate-200 bg-white p-4 md:grid-cols-3" aria-label="Permission Catalog lifecycle">
    <LifecycleFact label="Active revision" value={active?`${active.revisionCode} · #${active.revisionNumber}`:'No active revision'} hint="Read-only authority currently used by authorization."/>
    <LifecycleFact label="Selected revision" value={selected?`${selected.revisionCode} · ${selected.status}`:'No revision selected'} hint={draft?'Draft changes have no authority until validated and published.':'Published revisions are read-only.'}/>
    <LifecycleFact label="Validation" value={validation?`${errors} errors · ${warnings} warnings`:'Not required for this revision'} hint={errors?'Publication is blocked.':warnings?'Warnings require explicit review.':'No blocking validation issue is known.'}/>
  </section>;
}
function LifecycleFact({label,value,hint}:{label:string;value:string;hint:string}){return <div className="rounded-xl bg-slate-50 p-3"><div className="text-xs font-black uppercase tracking-wide text-slate-500">{label}</div><div className="mt-1 font-black text-slate-950">{value}</div><p className="mt-1 text-xs leading-5 text-slate-600">{hint}</p></div>}
