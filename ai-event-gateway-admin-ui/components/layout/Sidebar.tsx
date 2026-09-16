'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useI18n } from '@/hooks/useI18n';
import { WorkspaceTenantSelector } from '@/components/auth/WorkspaceTenantSelector';
import { useUiEntitlements } from '@/lib/navigation/useUiEntitlements';
import { useAuth } from '@/components/auth/AuthProvider';
import { resolveEntitlementRoute } from '@/lib/navigation/uiEntitlements';
import { presentNavigation, type PresentedNavigationItem } from '@/lib/navigation/adminNavigationPresentation';
import { releaseIdentity } from '@/lib/generated/releaseIdentity';

interface SidebarProps { open: boolean; onClose: () => void; }

function isActivePath(pathname: string, href: string): boolean {
  return pathname === href
    || (href === '/agents' && pathname.startsWith('/agents/') && !pathname.startsWith('/agents/runtime'))
    || (href !== '/agents' && pathname.startsWith(`${href}/`));
}

function NavigationLink({ presented, tenantId, onNavigate }: Readonly<{ presented: PresentedNavigationItem; tenantId?: string; onNavigate: () => void }>) {
  const { item } = presented;
  const pathname = usePathname();
  const href = resolveEntitlementRoute(item.route, tenantId);
  const active = isActivePath(pathname, href);
  return (
    <Link
      href={href}
      title={presented.purpose}
      onClick={onNavigate}
      aria-current={active ? 'page' : undefined}
      className={`block rounded-xl px-4 py-3 text-sm font-semibold transition ${active ? 'bg-blue-600 text-white shadow-sm' : 'text-slate-300 hover:bg-white/10 hover:text-white'}`}
    >
      <span className="flex items-center justify-between gap-2"><span>{presented.label}</span>{item.displayMode === 'READ_ONLY' ? <span className="rounded-full border border-white/20 px-1.5 py-0.5 text-xs font-black uppercase tracking-wide text-slate-300">Read</span> : null}</span>
    </Link>
  );
}

export function Sidebar({ open, onClose }: Readonly<SidebarProps>) {
  const { t } = useI18n();
  const pathname = usePathname();
  const entitlements = useUiEntitlements();
  const { administrationTenantId } = useAuth();
  const root = entitlements.value?.workspaceKind === 'INSTANCE_ROOT';
  const items = entitlements.value?.navigation ?? [];
  const groups = presentNavigation(items);

  return (
    <>
      <div className={`fixed inset-0 z-40 bg-slate-950/50 transition-opacity lg:hidden ${open ? 'opacity-100' : 'pointer-events-none opacity-0'}`} onClick={onClose} aria-hidden="true" />
      <aside className={`fixed inset-y-0 left-0 z-50 flex h-dvh w-72 flex-col overflow-hidden border-r border-slate-800 bg-slate-950 px-5 py-6 text-white transition-transform lg:translate-x-0 ${open ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'}`}>
        <div className="flex flex-none items-start justify-between gap-3">
          <Link href={root ? '/instance-administration' : '/dashboard'} onClick={onClose} className="block flex-1 rounded-2xl bg-white/10 p-4">
            <div className="text-xs font-semibold uppercase tracking-widest text-cyan-300">{root ? 'PLATFORM ADMINISTRATION' : t('app.workspace')}</div>
            <div className="mt-2 text-lg font-bold">{t('app.adminTitle')}</div>
            <div className="mt-1 text-xs text-slate-300">{root ? 'Instance governance and controlled platform operations' : t('app.adminSubtitle')}</div>
          </Link>
          <button type="button" onClick={onClose} className="rounded-xl border border-white/10 px-3 py-2 text-sm font-bold text-slate-200 hover:bg-white/10 lg:hidden" aria-label={t('nav.closeNavigation')}>×</button>
        </div>

        {!root ? <div className="mt-4 rounded-2xl border border-white/10 bg-white/5 p-3 md:hidden"><WorkspaceTenantSelector className="w-full" /></div> : null}

        <nav className="mt-6 min-h-0 flex-1 space-y-4 overflow-y-auto overscroll-contain pr-1" aria-label={t('nav.mainNavigation')} aria-busy={entitlements.loading}>
          {entitlements.loading ? <div className="rounded-xl border border-cyan-700/40 bg-cyan-950/30 p-3 text-xs leading-5 text-cyan-100" aria-live="polite">Loading your authorized workspace…</div> : null}
          {entitlements.error ? <div className="rounded-xl border border-amber-700/60 bg-amber-950/40 p-3 text-xs leading-5 text-amber-100"><div className="font-bold">Workspace access unavailable</div><div className="mt-1">{entitlements.error}</div><button type="button" onClick={entitlements.refresh} className="mt-2 rounded-lg border border-amber-400/50 px-2.5 py-1 font-bold hover:bg-amber-900/50">Retry</button></div> : null}
          {!entitlements.loading && !entitlements.error && items.length === 0 ? <div className="rounded-xl border border-slate-700 bg-slate-900 p-3 text-xs leading-5 text-slate-300"><div className="font-bold text-white">No workspace access</div><div className="mt-1">No administration area is available for the current workspace. Ask an administrator to review your responsibilities.</div></div> : null}
          {groups.map(group => group.collapsible ? (
            <details key={group.id} open={group.items.some(({ item }) => isActivePath(pathname, resolveEntitlementRoute(item.route, root ? administrationTenantId : entitlements.value?.tenantId))) || undefined} className="rounded-xl border border-white/5 bg-white/[0.02]">
              <summary title={group.description} className="cursor-pointer list-none rounded-xl px-2 py-2 text-xs font-black uppercase tracking-[.12em] text-slate-400 hover:bg-white/5 hover:text-slate-200">
                <span className="flex items-center justify-between gap-2"><span>{group.label}</span><span className="rounded-full bg-white/5 px-2 py-0.5 text-xs text-slate-500">{group.items.length}</span></span>
              </summary>
              <div className="space-y-2 px-1 pb-2 pt-1">{group.items.map(presented => <NavigationLink key={presented.item.featureId} presented={presented} tenantId={root ? administrationTenantId : entitlements.value?.tenantId} onNavigate={onClose} />)}</div>
            </details>
          ) : (
            <section key={group.id} aria-label={group.label}>
              <div className="mb-2 px-2 text-xs font-black uppercase tracking-[.12em] text-slate-400">{group.label}</div>
              <div className="space-y-2">{group.items.map(presented => <NavigationLink key={presented.item.featureId} presented={presented} tenantId={root ? administrationTenantId : entitlements.value?.tenantId} onNavigate={onClose} />)}</div>
            </section>
          ))}
        </nav>

        <div className="flex-none border-t border-white/10 pt-4 text-xs leading-5 text-slate-400">
          <div className="font-black text-cyan-200">{root ? 'Platform workspace' : 'Current workspace'}</div>
          <div className="mt-1">You only see areas available to your current workspace and responsibilities.</div>
          <div className="mt-3 rounded-xl border border-white/10 bg-white/5 px-3 py-2" title={releaseIdentity.artifactName}>
            <div className="font-bold text-slate-200">OpenDispatch {releaseIdentity.releaseLabel}</div>
            <div>{releaseIdentity.productVersion} · {releaseIdentity.artifactRevision}</div>
            {releaseIdentity.productionReady ? null : <div className="mt-1 text-amber-300">Release candidate · not production certified</div>}
          </div>
        </div>
      </aside>
    </>
  );
}
