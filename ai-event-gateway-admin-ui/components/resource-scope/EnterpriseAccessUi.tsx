'use client';

import Link from 'next/link';
import { useMemo, useState, type ReactNode } from 'react';
import { RightDrawer } from '@/components/ui/RightDrawer';
import { HelpTooltip } from '@/components/ui/Tooltip';
import { HierarchySelectField, SearchSelectField, type HierarchySelectOption, type SelectOption } from '@/components/access-management/shared/beginnerUi';
import {
  explainDecision,
  getResourceOverview,
  type AuthorizationDecision,
  type ResourceActionKind,
  type ResourceGovernanceOverview,
  type VisibilityLevel,
} from '@/lib/api/domains/resourceAccessApi';

export type EnterpriseScopeKind = 'TENANT' | 'DEPARTMENT' | 'DEPARTMENT_SUBTREE' | 'GROUP';

export interface EnterpriseScopeOption extends SelectOption {
  scopeKind: EnterpriseScopeKind;
  depth?: number;
}

export function FieldAssist({
  help,
  href,
  linkLabel,
}: Readonly<{ help: ReactNode; href?: string; linkLabel?: string }>) {
  return (
    <span className="inline-flex flex-wrap items-center gap-2 text-xs font-semibold normal-case tracking-normal">
      <HelpTooltip content={help} />
      {href && linkLabel ? <Link href={href} className="text-blue-700 hover:underline">{linkLabel}</Link> : null}
    </span>
  );
}

export function BeginnerGuideButton({
  title,
  description,
  steps,
}: Readonly<{ title: string; description: string; steps: Array<{ title: string; description: string }> }>) {
  const [open, setOpen] = useState(false);
  return <>
    <button type="button" onClick={() => setOpen(true)} className="rounded-xl border border-blue-200 bg-blue-50 px-3 py-2 text-sm font-black text-blue-800 hover:bg-blue-100">
      How this page works
    </button>
    <RightDrawer open={open} onClose={() => setOpen(false)} title={title} description={description} widthClassName="max-w-xl">
      <ol className="space-y-3">
        {steps.map((step, index) => <li key={`${step.title}-${index}`} className="flex gap-3 rounded-2xl border border-slate-200 bg-slate-50 p-4">
          <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-blue-700 text-sm font-black text-white">{index + 1}</span>
          <div><div className="font-black text-slate-900">{step.title}</div><p className="mt-1 text-sm leading-6 text-slate-600">{step.description}</p></div>
        </li>)}
      </ol>
    </RightDrawer>
  </>;
}

export function AccessibleScopeSelect({
  id,
  value,
  onChange,
  options,
  placeholder = 'Select an authorized scope',
  disabled = false,
}: Readonly<{
  id: string;
  value: string;
  onChange: (value: string) => void;
  options: EnterpriseScopeOption[];
  placeholder?: string;
  disabled?: boolean;
}>) {
  const decorated = useMemo(() => options.map((option) => ({
    ...option,
    label: option.scopeKind === 'TENANT' ? `Tenant-wide · ${option.label}` : option.label,
  })), [options]);
  const hierarchy = decorated.some((option) => option.scopeKind === 'DEPARTMENT' || option.scopeKind === 'DEPARTMENT_SUBTREE');
  if (hierarchy) {
    return <HierarchySelectField id={id} value={value} onChange={onChange} options={decorated as HierarchySelectOption[]} placeholder={placeholder} disabled={disabled} />;
  }
  return <SearchSelectField id={id} value={value} onChange={onChange} options={decorated} placeholder={placeholder} disabled={disabled} />;
}

export function CrossScopeNotice({
  active,
  sourceLabel,
  targetLabel,
  allowed,
  permissionLabel = 'Tenant-level cross-scope authority',
}: Readonly<{ active: boolean; sourceLabel: string; targetLabel: string; allowed: boolean; permissionLabel?: string }>) {
  if (!active) return null;
  return <div className={`rounded-2xl border p-4 ${allowed ? 'border-amber-200 bg-amber-50' : 'border-rose-200 bg-rose-50'}`}>
    <div className={`text-sm font-black ${allowed ? 'text-amber-950' : 'text-rose-950'}`}>Cross-scope relationship</div>
    <p className={`mt-1 text-sm leading-6 ${allowed ? 'text-amber-900' : 'text-rose-900'}`}>
      This operation connects <b>{sourceLabel}</b> with <b>{targetLabel}</b>. {allowed ? 'Your current Responsibility allows this cross-scope action.' : `${permissionLabel} is required before this can be saved.`}
    </p>
  </div>;
}

function scopeText(overview: ResourceGovernanceOverview | null): string[] {
  if (!overview) return [];
  const values: string[] = [];
  if (overview.ownerDepartmentId) values.push(`Primary Department: ${overview.ownerDepartmentId}`);
  if (overview.ownerGroupId) values.push(`Primary Group: ${overview.ownerGroupId}`);
  if (overview.requesterDepartmentId) values.push(`Requester Department: ${overview.requesterDepartmentId}`);
  if (overview.executorDepartmentId) values.push(`Executor Department: ${overview.executorDepartmentId}`);
  return values;
}

export function OwnershipAccessCard({
  resourceType,
  resourceId,
  permissionCode,
  actionKind = 'READ',
  requestedVisibility = 'STANDARD',
  purpose,
  primaryOwner,
  operationalScopes = [],
  provenance,
  managementHref,
  managementLabel,
  compact = false,
}: Readonly<{
  resourceType: string;
  resourceId: string;
  permissionCode: string;
  actionKind?: ResourceActionKind;
  requestedVisibility?: VisibilityLevel;
  purpose: string;
  primaryOwner: ReactNode;
  operationalScopes?: ReactNode[];
  provenance?: ReactNode;
  managementHref?: string;
  managementLabel?: string;
  compact?: boolean;
}>) {
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [overview, setOverview] = useState<ResourceGovernanceOverview | null>(null);
  const [decision, setDecision] = useState<AuthorizationDecision | null>(null);

  async function explain() {
    setOpen(true); setLoading(true); setError(null);
    try {
      const [nextOverview, nextDecision] = await Promise.all([
        getResourceOverview(resourceType, resourceId),
        explainDecision({ permissionCode, resourceType, resourceId, actionKind, sideEffecting: false, requestedVisibility, purpose }),
      ]);
      setOverview(nextOverview); setDecision(nextDecision);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Access explanation is unavailable.');
    } finally { setLoading(false); }
  }

  return <>
    <section className={`rounded-2xl border border-violet-200 bg-violet-50 ${compact ? 'p-3' : 'p-4'}`}>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="text-xs font-black uppercase tracking-wide text-violet-700">Ownership & Access</div>
          <div className="mt-1 text-sm font-black text-slate-950">{primaryOwner}</div>
          {operationalScopes.length ? <div className="mt-2 flex flex-wrap gap-2">{operationalScopes.map((scope, index) => <span key={index} className="rounded-full border border-violet-200 bg-white px-2.5 py-1 text-xs font-bold text-violet-900">{scope}</span>)}</div> : null}
          {provenance ? <div className="mt-2 text-xs leading-5 text-slate-600">{provenance}</div> : null}
        </div>
        <div className="flex flex-wrap gap-2">
          <button type="button" onClick={() => void explain()} className="rounded-xl border border-violet-200 bg-white px-3 py-2 text-xs font-black text-violet-800 hover:bg-violet-100">Why can I access this?</button>
          {managementHref && managementLabel ? <Link href={managementHref} className="rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-black text-slate-700 hover:bg-slate-50">{managementLabel}</Link> : null}
        </div>
      </div>
    </section>

    <RightDrawer open={open} onClose={() => setOpen(false)} title="Why can I access this?" description="OpenDispatch explains the effective Permission and Resource Scope used for this resource." widthClassName="max-w-2xl">
      {loading ? <div className="rounded-2xl bg-slate-50 p-5 text-sm font-semibold text-slate-600">Loading the current authorization decision…</div> : null}
      {error ? <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-bold text-rose-900">{error}</div> : null}
      {!loading && decision ? <div className="space-y-4">
        <div className={`rounded-2xl border p-4 ${decision.effect === 'ALLOW' ? 'border-emerald-200 bg-emerald-50' : 'border-rose-200 bg-rose-50'}`}>
          <div className="text-sm font-black">{decision.effect === 'ALLOW' ? 'Access allowed' : 'Access denied'}</div>
          <p className="mt-1 text-sm leading-6">Permission <b>{decision.permissionCode}</b> was evaluated for this resource.</p>
        </div>
        {decision.reasons?.length ? <section><h3 className="text-sm font-black text-slate-900">Decision explanation</h3><div className="mt-2 space-y-2">{decision.reasons.map((reason, index) => <div key={`${reason.code}-${index}`} className="rounded-xl border border-slate-200 bg-white p-3"><div className="text-sm font-black text-slate-800">{reason.safeMessage || reason.code}</div><div className="mt-1 text-xs text-slate-500">{reason.category} · {reason.code}</div></div>)}</div></section> : null}
        {scopeText(overview).length ? <section><h3 className="text-sm font-black text-slate-900">Resource scope</h3><div className="mt-2 flex flex-wrap gap-2">{scopeText(overview).map((value) => <span key={value} className="rounded-full bg-violet-50 px-3 py-1 text-xs font-bold text-violet-900">{value}</span>)}</div></section> : null}
        {overview?.participants?.length ? <section><h3 className="text-sm font-black text-slate-900">Operational participants</h3><div className="mt-2 space-y-2">{overview.participants.map((participant) => <div key={participant.participantId} className="rounded-xl border border-slate-200 p-3 text-sm"><b>{participant.participantRole}</b> · {participant.participantType}<div className="mt-1 text-xs text-slate-500">Permissions: {participant.allowedPermissions?.join(', ') || 'No permission is granted by participant membership alone.'}</div></div>)}</div></section> : null}
        <details className="rounded-2xl border border-slate-200 bg-slate-50 p-4 text-xs text-slate-600"><summary className="cursor-pointer font-black text-slate-800">Technical details for support</summary><dl className="mt-3 grid gap-2 sm:grid-cols-2"><div><dt className="font-black">Decision ID</dt><dd className="break-all">{decision.decisionId}</dd></div><div><dt className="font-black">Resource</dt><dd className="break-all">{resourceType} / {resourceId}</dd></div><div><dt className="font-black">Role bindings</dt><dd>{decision.matchedRoleBindingIds?.length ?? 0}</dd></div><div><dt className="font-black">Matched scopes</dt><dd>{decision.matchedScopeSources?.length ?? 0}</dd></div></dl></details>
      </div> : null}
    </RightDrawer>
  </>;
}
