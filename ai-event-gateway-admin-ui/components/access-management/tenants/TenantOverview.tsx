'use client';

import Link from 'next/link';
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { ApiError } from '@/lib/api/errors';
import { accessManagementApi } from '@/lib/api/accessManagementApi';
import type { Tenant, TenantWorkspaceSummary } from '@/lib/iam/types';
import { actionAllowed, featureAllowed } from '@/lib/navigation/uiEntitlements';
import { useUiEntitlements } from '@/lib/navigation/useUiEntitlements';
import { useAccessManagement } from '../AccessManagementProvider';
import { HumanStatus } from '../shared/beginnerUi';
import { WorkspaceError, WorkspaceLoading } from '../shared/workspaceUi';

export function TenantOverview() {
  const { scopeTenantId, tenants } = useAccessManagement();
  const entitlements = useUiEntitlements();
  const [tenant, setTenant] = useState<Tenant | null>(null);
  const [summary, setSummary] = useState<TenantWorkspaceSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | Error | null>(null);

  const load = useCallback(async () => {
    if (!scopeTenantId) return;
    setLoading(true); setError(null);
    try {
      const cached = tenants.find((item) => item.tenantId === scopeTenantId) ?? null;
      const [resolvedTenant, resolvedSummary] = await Promise.all([
        cached ? Promise.resolve(cached) : accessManagementApi.tenant(scopeTenantId),
        accessManagementApi.tenantWorkspaceSummary(scopeTenantId),
      ]);
      setTenant(resolvedTenant); setSummary(resolvedSummary);
    } catch (cause) {
      setError(cause instanceof Error ? cause : new Error('Unable to load this administration workspace.'));
    } finally { setLoading(false); }
  }, [scopeTenantId, tenants]);

  useEffect(() => { void load(); }, [load]);

  const base = `/admin/tenants/${encodeURIComponent(scopeTenantId)}`;
  const canOpenPeople = featureAllowed(entitlements.value, 'access-people');
  const canOpenOrganization = featureAllowed(entitlements.value, 'access-organization');
  const canOpenAccess = featureAllowed(entitlements.value, 'access-governance');
  const canOpenSecurity = featureAllowed(entitlements.value, 'access-security');
  const canAddPerson = actionAllowed(entitlements.value, 'access.people.create');
  const canManageDepartment = actionAllowed(entitlements.value, 'access.department.manage');
  const canManageGroup = actionAllowed(entitlements.value, 'access.group.manage');
  const canManageRole = actionAllowed(entitlements.value, 'access.role.manage');
  const canOpenSourceSystems = featureAllowed(entitlements.value, 'source-systems');
  const canOpenDispatch = featureAllowed(entitlements.value, 'dispatch');
  const canOpenA2AGovernance = featureAllowed(entitlements.value, 'a2a-governance');


  const setup = useMemo(() => {
    if (!summary) return [];
    return [
      { complete: summary.activePeople > 0, title: 'Add people', description: summary.activePeople > 0 ? `${summary.activePeople} active people are in this workspace.` : 'Add the first person so responsibilities and sign-in can be configured.', href: canOpenPeople ? `${base}/people${canAddPerson ? '?action=ADD_PERSON' : ''}` : '' },
      { complete: summary.tenantAdministratorCount > 0, title: 'Confirm an administrator', description: summary.tenantAdministratorCount > 0 ? `${summary.tenantAdministratorCount} active Tenant administrator${summary.tenantAdministratorCount === 1 ? '' : 's'}.` : 'Assign the Tenant Administrator responsibility to an approved person.', href: canOpenAccess ? `${base}/access?view=ASSIGNMENTS` : '' },
      { complete: summary.departmentCount > 0, title: 'Build the organization', description: summary.departmentCount > 0 ? `${summary.departmentCount} Departments and ${summary.groupCount} Groups are active.` : 'Create the first Department and place people where they work.', href: canOpenOrganization ? `${base}/organization` : '' },
      { complete: summary.activeBindingCount > 0, title: 'Assign responsibilities', description: summary.activeBindingCount > 0 ? `${summary.activeBindingCount} active access assignments can be explained.` : 'Assign a responsibility and confirm where it applies.', href: canOpenAccess ? `${base}/access` : '' },
      { complete: summary.activePeople > 0 && summary.signInSetupRequiredCount === 0, title: 'Verify sign-in readiness', description: summary.activePeople > 0 && summary.signInSetupRequiredCount === 0 ? 'No person is waiting for password or MFA setup.' : `${summary.signInSetupRequiredCount} person${summary.signInSetupRequiredCount === 1 ? '' : 's'} still need sign-in setup.`, href: canOpenPeople ? `${base}/people` : '' },
    ];
  }, [base, canAddPerson, canOpenAccess, canOpenOrganization, canOpenPeople, summary]);

  if (loading && !summary) return <WorkspaceLoading title="Loading administration overview" description="Building the People, Organization, Access and Security summary." />;
  if (error) {
    const api = error instanceof ApiError ? error : null;
    return <WorkspaceError message={error.message} status={api?.status} code={api?.code} correlationId={api?.correlationId} onRetry={() => { void load(); }} />;
  }
  if (!tenant || !summary) return <WorkspaceError message="The requested administration workspace is not available." onRetry={() => { void load(); }} />;

  const completed = setup.filter((item) => item.complete).length;
  const percent = setup.length ? Math.round((completed / setup.length) * 100) : 0;
  const recommendedSetupAction = setup.find((item) => !item.complete && item.href) ?? null;
  const attention = [
    summary.signInSetupRequiredCount && canOpenPeople ? { title: `${summary.signInSetupRequiredCount} person${summary.signInSetupRequiredCount === 1 ? '' : 's'} need sign-in setup`, href: `${base}/people`, text: 'Finish password or MFA setup before normal sign-in is ready.' } : null,
    summary.pendingInvitations && canOpenPeople ? { title: `${summary.pendingInvitations} invitation${summary.pendingInvitations === 1 ? '' : 's'} pending`, href: `${base}/people`, text: 'Review invitation delivery and expiry.' } : null,
    summary.peopleWithoutDepartment && canOpenOrganization ? { title: `${summary.peopleWithoutDepartment} active person${summary.peopleWithoutDepartment === 1 ? '' : 's'} without a Department`, href: `${base}/organization`, text: 'Place people in the organization so scoped administration is easier to understand.' } : null,
    summary.departmentsWithoutManager && canOpenOrganization ? { title: `${summary.departmentsWithoutManager} Department${summary.departmentsWithoutManager === 1 ? '' : 's'} without a Manager`, href: `${base}/organization`, text: 'Assign an eligible Official Manager.' } : null,
    summary.expiringBindingCount && canOpenAccess ? { title: `${summary.expiringBindingCount} access assignment${summary.expiringBindingCount === 1 ? '' : 's'} expiring`, href: `${base}/access?view=REVIEWS`, text: 'Review access expiring within 30 days.' } : null,
    summary.suspendedPeople && canOpenPeople ? { title: `${summary.suspendedPeople} suspended people`, href: `${base}/people?status=SUSPENDED`, text: 'Confirm account and membership status.' } : null,
  ].filter((item): item is NonNullable<typeof item> => Boolean(item));

  const operationalHandoff = [
    canOpenSourceSystems ? {
      step: '6',
      title: 'Connect a Source System',
      description: 'Register the business system that sends events. Keep credentials and protocol details inside the governed Source System setup.',
      href: '/source-systems',
      action: 'Open Source Systems',
    } : null,
    canOpenDispatch ? {
      step: '7',
      title: 'Configure Dispatch',
      description: 'Create the Source Flow, choose the default Agent Pool, add Pool Members, then run Test & Activate.',
      href: '/dispatch-flows',
      action: 'Open Dispatch',
    } : null,
    canOpenA2AGovernance ? {
      step: '8',
      title: 'Define A2A governance only when needed',
      description: 'Allow directional cross-Agent work with explicit Source/Target boundaries, approval rules, sensitivity limits and safety controls.',
      href: '/a2a-governance',
      action: 'Open legacy A2A archive',
    } : null,
  ].filter((item): item is NonNullable<typeof item> => Boolean(item));

  const cards = [
    canOpenPeople ? {
      title: 'People', value: summary.activePeople, valueLabel: 'active', description: `${summary.pendingInvitations} invitations pending · ${summary.signInSetupRequiredCount} need sign-in setup`, href: `${base}/people`, action: 'Manage people', secondary: canAddPerson ? <Link href={`${base}/people?action=ADD_PERSON`} className="text-xs font-black text-blue-700 hover:underline">Add person →</Link> : null,
    } : null,
    canOpenOrganization ? {
      title: 'Organization', value: summary.departmentCount, valueLabel: 'Departments', description: `${summary.groupCount} Groups · ${summary.peopleWithoutDepartment} people not placed · ${summary.departmentsWithoutManager} Departments without Manager`, href: `${base}/organization`, action: 'Manage organization', secondary: null,
    } : null,
    canOpenAccess ? {
      title: 'Access', value: summary.activeBindingCount, valueLabel: 'assignments', description: `${summary.activeRoleCount} responsibilities available · ${summary.expiringBindingCount} assignments expiring soon`, href: `${base}/access`, action: 'Manage access', secondary: <Link href={`${base}/access?view=EFFECTIVE`} className="text-xs font-black text-blue-700 hover:underline">Explain access →</Link>,
    } : null,
    canOpenSecurity ? {
      title: 'Security', value: summary.mfaEnrolledPeople, valueLabel: 'people with MFA', description: `${summary.signInSetupRequiredCount} need sign-in setup · ${summary.suspendedPeople} suspended`, href: `${base}/security`, action: 'Manage security & audit', secondary: canOpenPeople ? <Link href={`${base}/people`} className="text-xs font-black text-blue-700 hover:underline">Review person sign-in →</Link> : null,
    } : null,
  ].filter((card): card is NonNullable<typeof card> => Boolean(card));

  return (
    <div className="space-y-5">
      <section className="rounded-3xl border border-blue-200 bg-gradient-to-br from-blue-50 via-white to-cyan-50 p-6 shadow-sm">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <div className="flex flex-wrap items-center gap-3"><p className="text-xs font-black uppercase tracking-[.18em] text-blue-700">Administration home</p><HumanStatus value={tenant.status} /></div>
            <h2 className="mt-2 text-2xl font-black text-slate-950">{tenant.tenantName}</h2>
            <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-700">Start here. The four cards below show the people, organization, access and sign-in work that needs attention. Open the nearby action instead of searching through separate administration pages.</p>
          </div>
          <div className="flex max-w-2xl flex-wrap gap-2">
            {canAddPerson && canOpenPeople ? <Link href={`${base}/people?action=ADD_PERSON`} className="rounded-xl bg-blue-700 px-4 py-2.5 text-sm font-black text-white">+ Person</Link> : null}
            {canManageDepartment && canOpenOrganization ? <Link href={`${base}/organization?action=ADD_DEPARTMENT`} className="rounded-xl border border-blue-300 bg-white px-4 py-2.5 text-sm font-black text-blue-900">+ Department</Link> : null}
            {canManageGroup && canOpenOrganization ? <Link href={`${base}/organization?action=ADD_GROUP`} className="rounded-xl border border-blue-300 bg-white px-4 py-2.5 text-sm font-black text-blue-900">+ Group</Link> : null}
            {canManageRole && canOpenAccess ? <Link href={`${base}/access?view=TEMPLATES&action=ADD_RESPONSIBILITY`} className="rounded-xl border border-blue-300 bg-white px-4 py-2.5 text-sm font-black text-blue-900">+ Responsibility</Link> : null}
          </div>
        </div>
      </section>

      {recommendedSetupAction ? <section className="rounded-3xl border border-amber-200 bg-amber-50 p-5 shadow-sm" aria-labelledby="recommended-admin-action">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <p className="text-xs font-black uppercase tracking-[.16em] text-amber-800">Recommended next action</p>
            <h3 id="recommended-admin-action" className="mt-1 text-lg font-black text-slate-950">{recommendedSetupAction.title}</h3>
            <p className="mt-1 max-w-3xl text-sm leading-6 text-amber-950">{recommendedSetupAction.description}</p>
          </div>
          <Link href={recommendedSetupAction.href} className="shrink-0 rounded-xl bg-amber-800 px-4 py-2.5 text-sm font-black text-white">Continue setup →</Link>
        </div>
      </section> : completed === setup.length ? <section className="rounded-3xl border border-emerald-200 bg-emerald-50 p-5 shadow-sm">
        <p className="text-xs font-black uppercase tracking-[.16em] text-emerald-800">People & Access ready</p>
        <h3 className="mt-1 text-lg font-black text-emerald-950">Core administration setup is complete</h3>
        <p className="mt-1 text-sm leading-6 text-emerald-900">Continue with Source Systems and Dispatch only when this Tenant is ready to receive and route operational events. Legacy A2A archive is read-only; current cross-Agent execution uses capability-first governed delegation.</p>
      </section> : null}

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        {cards.map((card) => <WorkspaceCard key={card.title} {...card} />)}
      </div>

      <div className="grid gap-5 xl:grid-cols-[minmax(0,1.05fr)_minmax(340px,.95fr)]">
        <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="flex items-start justify-between gap-4"><div><h3 className="text-lg font-black text-slate-950">Getting started</h3><p className="mt-1 text-sm text-slate-600">Follow these steps in business order. Completed steps remain available for review.</p></div><span className="rounded-full bg-blue-50 px-3 py-1 text-sm font-black text-blue-800">{completed}/{setup.length}</span></div>
          <div className="mt-4 h-2 overflow-hidden rounded-full bg-slate-100" aria-label={`${percent}% of administration setup complete`}><div className="h-full rounded-full bg-blue-700" style={{ width: `${percent}%` }} /></div>
          <div className="mt-4 space-y-3">{setup.map((item) => item.href ? <Link key={item.title} href={item.href} className={`flex gap-3 rounded-2xl border p-4 transition hover:border-blue-400 ${item.complete ? 'border-emerald-200 bg-emerald-50' : 'border-amber-200 bg-amber-50'}`}><SetupIcon complete={item.complete} /><span><span className="block text-sm font-black text-slate-950">{item.title}</span><span className="mt-1 block text-xs leading-5 text-slate-600">{item.description}</span></span></Link> : <div key={item.title} className={`flex gap-3 rounded-2xl border p-4 ${item.complete ? 'border-emerald-200 bg-emerald-50' : 'border-slate-200 bg-slate-50'}`}><SetupIcon complete={item.complete} /><span><span className="block text-sm font-black text-slate-950">{item.title}</span><span className="mt-1 block text-xs leading-5 text-slate-600">{item.description}</span></span></div>)}</div>
        </section>

        <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
          <h3 className="text-lg font-black text-slate-950">Needs attention</h3><p className="mt-1 text-sm text-slate-600">Only items with a clear next action are shown.</p>
          <div className="mt-4 space-y-3">{attention.length ? attention.map((item) => <Link key={item.title} href={item.href} className="block rounded-2xl border border-amber-200 bg-amber-50 p-4 transition hover:border-amber-400"><span className="block text-sm font-black text-amber-950">{item.title}</span><span className="mt-1 block text-xs leading-5 text-amber-800">{item.text}</span><span className="mt-2 inline-block text-xs font-black text-amber-950">Review →</span></Link>) : <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-5"><p className="font-black text-emerald-900">No immediate action required</p><p className="mt-1 text-sm text-emerald-800">No sign-in setup, organization placement, manager or soon-expiring access issue requires attention.</p></div>}</div>
        </section>
      </div>

      {operationalHandoff.length ? <section className="rounded-3xl border border-indigo-200 bg-gradient-to-br from-indigo-50 via-white to-violet-50 p-5 shadow-sm" aria-labelledby="operational-handoff-title">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between"><div><p className="text-xs font-black uppercase tracking-[.16em] text-indigo-700">After People & Access</p><h3 id="operational-handoff-title" className="mt-1 text-lg font-black text-slate-950">Continue the first-time administrator journey</h3><p className="mt-2 max-w-3xl text-sm leading-6 text-slate-600">Identity and organization administration ends here. Operational routing stays in its own canonical workspaces so administrators do not confuse RBAC with Dispatch authority.</p></div><span className="rounded-full border border-indigo-200 bg-white px-3 py-1 text-xs font-black text-indigo-800">Business setup → runtime setup</span></div>
        <ol className="mt-5 grid gap-3 lg:grid-cols-3">{operationalHandoff.map((item) => <li key={item.title} className="rounded-2xl border border-indigo-100 bg-white p-4"><div className="flex items-start gap-3"><span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-indigo-700 text-xs font-black text-white">{item.step}</span><div><h4 className="text-sm font-black text-slate-950">{item.title}</h4><p className="mt-1 text-xs leading-5 text-slate-600">{item.description}</p><Link href={item.href} className="mt-3 inline-flex text-xs font-black text-indigo-700 hover:underline">{item.action} →</Link></div></div></li>)}</ol>
        <details className="mt-4 rounded-2xl border border-indigo-100 bg-white/80 p-4 text-xs leading-5 text-slate-600"><summary className="cursor-pointer font-black text-slate-800">Why these are separate workspaces</summary><p className="mt-2">People & Access decides who may administer or operate the platform. Dispatch decides how an event reaches an Agent. Legacy A2A directional routing is retired. The archive remains separate from Dispatch so historical evidence cannot silently become routing authority.</p></details>
      </section> : null}

      <section id="tenant-profile" className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between"><div><h3 className="text-lg font-black text-slate-950">Workspace profile</h3><p className="mt-1 text-sm text-slate-600">Business information used by People & Access administration.</p></div><Link href="/admin/tenants" className="text-sm font-black text-blue-700 hover:underline">Change workspace</Link></div>
        <dl className="mt-5 grid gap-4 sm:grid-cols-2 xl:grid-cols-4"><Profile label="Business code" value={tenant.tenantCode} /><Profile label="Legal name" value={tenant.legalName || 'Not recorded'} /><Profile label="Region" value={tenant.dataRegion} /><Profile label="Time zone" value={tenant.timezone} /><Profile label="Default language" value={tenant.locale} /></dl>
        <details className="mt-5 rounded-2xl border border-slate-200 bg-slate-50 p-4 text-xs text-slate-600"><summary className="cursor-pointer font-black text-slate-800">Technical details</summary><dl className="mt-3 grid gap-3 sm:grid-cols-2"><Profile label="Internal Tenant reference" value={tenant.tenantId} technical /><Profile label="Created" value={new Date(tenant.createdAt).toLocaleString()} /><Profile label="Last updated" value={new Date(tenant.updatedAt).toLocaleString()} /></dl></details>
      </section>
    </div>
  );
}

function WorkspaceCard({ title, value, valueLabel, description, href, action, secondary }: Readonly<{ title: string; value: number; valueLabel: string; description: string; href: string; action: string; secondary: ReactNode }>) {
  return <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm"><p className="text-xs font-black uppercase tracking-[.14em] text-blue-700">{title}</p><div className="mt-2 flex items-end gap-2"><span className="text-3xl font-black text-slate-950">{value}</span><span className="pb-1 text-xs font-bold text-slate-500">{valueLabel}</span></div><p className="mt-3 min-h-12 text-xs leading-5 text-slate-600">{description}</p><div className="mt-4 flex flex-wrap items-center justify-between gap-2"><Link href={href} className="rounded-xl bg-slate-950 px-3 py-2 text-xs font-black text-white">{action}</Link>{secondary}</div></section>;
}
function SetupIcon({ complete }: Readonly<{ complete: boolean }>) { return <span className={`flex size-8 shrink-0 items-center justify-center rounded-full text-sm font-black text-white ${complete ? 'bg-emerald-700' : 'bg-amber-600'}`}>{complete ? '✓' : '!'}</span>; }
function Profile({ label, value, technical = false }: Readonly<{ label: string; value: string; technical?: boolean }>) { return <div><dt className="text-xs font-black uppercase tracking-wide text-slate-500">{label}</dt><dd className={`mt-1 text-sm font-bold text-slate-900 ${technical ? 'break-all font-mono text-xs' : ''}`}>{value}</dd></div>; }
