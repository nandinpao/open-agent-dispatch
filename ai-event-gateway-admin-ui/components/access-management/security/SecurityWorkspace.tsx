'use client';

import { useCallback, useEffect, useState } from 'react';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { useUiEntitlements } from '@/lib/navigation/useUiEntitlements';
import { actionAllowed } from '@/lib/navigation/uiEntitlements';
import { accessManagementApi } from '@/lib/api/accessManagementApi';
import { formatIamError } from '@/lib/iam/errorPresentation';
import type { SecurityWorkspaceSummary } from '@/lib/iam/types';
import { useAccessManagement } from '../AccessManagementProvider';
import { TenantRequired } from '../ui';
import { WorkspaceError } from '../shared/workspaceUi';
import { HumanReadableAuditFeed } from './HumanReadableAuditFeed';
import { IdentityProtectionWorkspace, normalizeIdentitySection } from './IdentityProtectionWorkspace';
import { MachineAccessWorkspace } from './MachineAccessWorkspace';
import { SecurityOverview } from './SecurityOverview';

type View = 'OVERVIEW' | 'IDENTITY' | 'MACHINE' | 'AUDIT';
const VIEWS: Array<{ value: View; label: string; description: string; actionIds?: string[] }> = [
  { value: 'OVERVIEW', label: 'Overview', description: 'Security posture and work requiring attention.' },
  { value: 'IDENTITY', label: 'Sign-in & Protection', description: 'Sessions, MFA/password safeguards and enterprise sign-in.', actionIds: ['access.security-session.view','access.security-policy.view','access.federation.view'] },
  { value: 'MACHINE', label: 'Machine Access', description: 'Service Accounts and client credentials for ERP, MES and integrations.', actionIds: ['access.security-token.view'] },
  { value: 'AUDIT', label: 'Audit', description: 'Human-readable activity and immutable evidence.', actionIds: ['access.audit.view'] },
];

export function SecurityWorkspace() {
  const { value: entitlements } = useUiEntitlements();
  const { scopeTenantId, tenants } = useAccessManagement();
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const [summary, setSummary] = useState<SecurityWorkspaceSummary | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [refreshKey, setRefreshKey] = useState(0);
  const requestedRaw = searchParams.get('view');
  const requested = normalizeView(requestedRaw);
  const visibleViews = VIEWS.filter((item) => !item.actionIds?.length || item.actionIds.some((actionId) => actionAllowed(entitlements, actionId)));
  const selected = visibleViews.some((item) => item.value === requested) ? requested : 'OVERVIEW';
  const tenantName = tenants.find((item) => item.tenantId === scopeTenantId)?.tenantName ?? scopeTenantId;
  const identitySection = normalizeIdentitySection(searchParams.get('section') ?? legacySection(requestedRaw));

  const load = useCallback(async () => {
    if (!scopeTenantId) return;
    setLoading(true); setError('');
    try { setSummary(await accessManagementApi.securityWorkspaceSummary(scopeTenantId)); }
    catch (cause) { setError(formatIamError(cause, 'Unable to load the Security workspace summary.')); }
    finally { setLoading(false); }
  }, [scopeTenantId]);
  useEffect(() => { void load(); }, [load, refreshKey]);

  function open(value: string) {
    const { view, section } = normalizeDestination(value);
    const next = new URLSearchParams(searchParams.toString());
    next.set('view', view);
    if (section) next.set('section', section); else next.delete('section');
    router.replace(`${pathname}?${next.toString()}`);
  }
  function openIdentitySection(section: 'SESSIONS' | 'POLICIES' | 'FEDERATION') {
    const next = new URLSearchParams(searchParams.toString());
    next.set('view', 'IDENTITY');
    next.set('section', section);
    router.replace(`${pathname}?${next.toString()}`);
  }
  function changed() { setRefreshKey((value) => value + 1); }
  if (!scopeTenantId) return <TenantRequired/>;

  return <div className="space-y-5">
    <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm"><div><p className="text-xs font-black uppercase tracking-[.18em] text-blue-700">Security & Access Protection</p><h1 className="mt-1 text-2xl font-black text-slate-950">Protect {tenantName}</h1><p className="mt-2 max-w-4xl text-sm leading-6 text-slate-600">Start from four security workspaces. Related tasks stay on the same page; secondary changes open in a popup so new administrators do not have to navigate through technical sub-pages.</p></div></section>
    <nav aria-label="Security workspace views" className="grid gap-2 rounded-3xl border border-slate-200 bg-white p-2 shadow-sm md:grid-cols-2 xl:grid-cols-4">{visibleViews.map((item) => <button key={item.value} type="button" onClick={() => open(item.value)} className={`rounded-2xl p-3 text-left ${selected === item.value ? 'bg-slate-950 text-white' : 'hover:bg-slate-50'}`}><span className="block text-sm font-black">{item.label}</span><span className={`mt-1 block text-xs leading-5 ${selected === item.value ? 'text-slate-300' : 'text-slate-500'}`}>{item.description}</span></button>)}</nav>
    {error ? <WorkspaceError message={error} onRetry={() => { void load(); }}/> : null}
    {selected === 'OVERVIEW' ? <SecurityOverview summary={summary} loading={loading} onOpen={open}/> : null}
    {selected === 'IDENTITY' ? <IdentityProtectionWorkspace tenantId={scopeTenantId} section={identitySection} entitlements={entitlements} onSectionChange={openIdentitySection} onChanged={changed}/> : null}
    {selected === 'MACHINE' ? <MachineAccessWorkspace tenantId={scopeTenantId} canManage={actionAllowed(entitlements, 'access.security-token.manage')} onChanged={changed}/> : null}
    {selected === 'AUDIT' ? <HumanReadableAuditFeed tenantId={scopeTenantId}/> : null}
  </div>;
}

function normalizeView(value: string | null): View {
  if (value === 'IDENTITY' || value === 'MACHINE' || value === 'AUDIT') return value;
  if (value === 'SESSIONS' || value === 'POLICIES' || value === 'FEDERATION') return 'IDENTITY';
  if (value === 'CREDENTIALS') return 'MACHINE';
  return 'OVERVIEW';
}
function legacySection(value: string | null): string | null { return value === 'SESSIONS' || value === 'POLICIES' || value === 'FEDERATION' ? value : null; }
function normalizeDestination(value: string): { view: View; section?: 'SESSIONS' | 'POLICIES' | 'FEDERATION' } {
  if (value === 'SESSIONS' || value === 'POLICIES' || value === 'FEDERATION') return { view: 'IDENTITY', section: value };
  if (value === 'CREDENTIALS') return { view: 'MACHINE' };
  return { view: normalizeView(value) };
}
