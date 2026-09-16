'use client';

import type { ReactNode } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useAccessManagement } from './AccessManagementProvider';
import { Breadcrumbs, WorkspaceError, WorkspaceLoading } from './shared/workspaceUi';
import { useUiEntitlements } from '@/lib/navigation/useUiEntitlements';
import { navigationChildren, resolveEntitlementRoute } from '@/lib/navigation/uiEntitlements';

function workspaceSection(pathname: string): string {
  const match = pathname.match(/^\/admin\/tenants\/[^/]+(?:\/([^/?#]+))?/);
  return match?.[1] ?? '';
}

function featureForSection(section: string): string {
  if (section === 'people') return 'access-people';
  if (section === 'organization') return 'access-organization';
  if (section === 'access') return 'access-governance';
  if (section === 'security') return 'access-security';
  return 'access-overview';
}

export function AccessManagementShell({ children }: Readonly<{ children: ReactNode }>) {
  const pathname = usePathname();
  const entitlements = useUiEntitlements();
  const {
    instanceRoot,
    tenants,
    scopeTenantId,
    preflightState,
    preflightError,
    retryPreflight,
    tenantError,
  } = useAccessManagement();

  const tenant = tenants.find((item) => item.tenantId === scopeTenantId);
  const directory = pathname === '/admin/tenants' || pathname === '/access-management';
  const section = workspaceSection(pathname);
  const workspaceNavigation = navigationChildren(entitlements.value, 'access-management');
  const selectedFeatureId = featureForSection(section);
  const sectionLabel = workspaceNavigation.find((link) => link.featureId === selectedFeatureId)?.label ?? 'Overview';

  const breadcrumbs = directory
    ? [{ label: 'People & Access' }, { label: 'Companies / business units' }]
    : [
        { label: 'Companies / business units', href: '/admin/tenants' },
        { label: tenant?.tenantName ?? (scopeTenantId || 'Workspace') },
        { label: sectionLabel },
      ];

  return (
    <main className="space-y-5">
      <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
        <Breadcrumbs items={breadcrumbs} />
        <div className="mt-4 flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
          <div>
            <p className="text-xs font-black uppercase tracking-[.18em] text-blue-700">People & access administration</p>
            <h1 className="mt-1 text-2xl font-black tracking-tight text-slate-950">{directory ? 'Choose a company or business unit' : tenant?.tenantName ?? 'Administration workspace'}</h1>
            <p className="mt-2 max-w-4xl text-sm leading-6 text-slate-600">
              {directory
                ? 'Open one workspace to manage people, organization, responsibilities, sign-in security and audit.'
                : 'The areas below come from your backend UI entitlement projection. Read-only areas remain visible for review; hidden areas are not granted to the active workspace.'}
            </p>
          </div>
          {!directory && scopeTenantId ? (
            <div className="rounded-2xl border border-blue-200 bg-blue-50 p-3 text-sm">
              <p className="text-xs font-black uppercase tracking-wide text-blue-900">Current workspace</p>
              <p className="mt-1 font-black text-slate-950">{tenant?.tenantName ?? scopeTenantId}</p>
              <p className="mt-1 text-xs text-blue-900">{tenant?.tenantCode ?? 'Company / business unit'}</p>
              {instanceRoot ? <Link href="/admin/tenants" className="mt-2 inline-flex font-black text-blue-700 hover:underline">Change workspace</Link> : null}
            </div>
          ) : null}
        </div>

        {!directory && scopeTenantId ? (
          <div className="mt-5 border-t border-slate-100 pt-4">
            <nav className="grid gap-2 sm:grid-cols-2 xl:grid-cols-5" aria-label="Administration workspace areas">
              {workspaceNavigation.map((link) => {
                const href = resolveEntitlementRoute(link.route, scopeTenantId);
                const active = link.featureId === selectedFeatureId;
                return <Link key={link.featureId} href={href} aria-current={active ? 'page' : undefined} title={link.purpose} className={`rounded-2xl border p-3 transition ${active ? 'border-slate-950 bg-slate-950 text-white' : 'border-slate-200 bg-slate-50 text-slate-800 hover:border-blue-300 hover:bg-blue-50'}`}><span className="flex items-center justify-between gap-2 text-sm font-black"><span>{link.label}</span>{link.displayMode === 'READ_ONLY' ? <span className={`rounded-full px-2 py-0.5 text-xs uppercase tracking-wide ${active ? 'bg-white/10 text-slate-200' : 'bg-slate-200 text-slate-600'}`}>Read only</span> : null}</span><span className={`mt-1 block text-xs leading-5 ${active ? 'text-slate-300' : 'text-slate-500'}`}>{link.purpose}</span></Link>;
              })}
            </nav>
          </div>
        ) : null}
      </section>

      {preflightState === 'CHECKING' ? (
        <WorkspaceLoading title="Verifying your administration session" description="Checking the active workspace and authorization context before loading administration data." />
      ) : preflightState === 'FAILED' ? (
        <WorkspaceError
          title="People & Access is unavailable"
          message={preflightError?.status === 401
            ? 'Your session has expired. Sign in again before continuing.'
            : preflightError?.status === 503
              ? 'Your identity was verified, but the authorization service could not complete the request.'
              : preflightError?.message ?? 'The administration security context could not be verified.'}
          status={preflightError?.status}
          code={preflightError?.code}
          correlationId={preflightError?.correlationId}
          onRetry={() => { void retryPreflight(); }}
        />
      ) : tenantError && instanceRoot && tenants.length === 0 ? (
        <WorkspaceError title="Workspace directory could not be loaded" message={tenantError} onRetry={() => { void retryPreflight(); }} />
      ) : children}
    </main>
  );
}
