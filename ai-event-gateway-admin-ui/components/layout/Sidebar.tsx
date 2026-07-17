'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { adminPrimaryNavigation, type AdminNavigationItem } from '@/lib/navigation/adminInformationArchitecture';
import { useI18n } from '@/hooks/useI18n';

interface SidebarProps {
  open: boolean;
  onClose: () => void;
}

function isActivePath(pathname: string, href: string): boolean {
  return pathname === href
    || (href === '/agents' && pathname.startsWith('/agents/') && !pathname.startsWith('/agents/runtime'))
    || (href !== '/agents' && pathname.startsWith(`${href}/`));
}

function NavigationLink({ item, onNavigate }: Readonly<{ item: AdminNavigationItem; onNavigate: () => void }>) {
  const pathname = usePathname();
  const active = isActivePath(pathname, item.href);
  return (
    <Link
      href={item.href}
      title={item.purpose}
      onClick={onNavigate}
      className={`block rounded-xl px-4 py-3 text-sm font-semibold transition ${active ? 'bg-blue-600 text-white shadow-sm' : 'text-slate-300 hover:bg-white/10 hover:text-white'}`}
    >
      <span className="block">{item.label}</span>
      <span className={`mt-0.5 block text-[11px] font-medium leading-4 ${active ? 'text-blue-100' : 'text-slate-500'}`}>{item.purpose}</span>
    </Link>
  );
}

export function Sidebar({ open, onClose }: Readonly<SidebarProps>) {
  const { t } = useI18n();
  return (
    <>
      <div
        className={`fixed inset-0 z-40 bg-slate-950/50 transition-opacity lg:hidden ${open ? 'opacity-100' : 'pointer-events-none opacity-0'}`}
        onClick={onClose}
        aria-hidden="true"
      />
      <aside className={`fixed inset-y-0 left-0 z-50 flex h-dvh w-72 flex-col overflow-hidden border-r border-slate-800 bg-slate-950 px-5 py-6 text-white transition-transform lg:translate-x-0 ${open ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'}`}>
        <div className="flex flex-none items-start justify-between gap-3">
          <Link href="/" onClick={onClose} className="block flex-1 rounded-2xl bg-white/10 p-4">
            <div className="text-xs font-semibold uppercase tracking-widest text-cyan-300">{t('app.workspace')}</div>
            <div className="mt-2 text-lg font-bold">{t('app.adminTitle')}</div>
            <div className="mt-1 text-xs text-slate-300">{t('app.adminSubtitle')}</div>
          </Link>
          <button type="button" onClick={onClose} className="rounded-xl border border-white/10 px-3 py-2 text-sm font-bold text-slate-200 hover:bg-white/10 lg:hidden" aria-label={t('nav.closeNavigation')}>×</button>
        </div>

        <nav className="mt-6 min-h-0 flex-1 space-y-2 overflow-y-auto overscroll-contain pr-1" aria-label={t('nav.mainNavigation')}>
          {adminPrimaryNavigation.map((item) => <NavigationLink key={item.href} item={item} onNavigate={onClose} />)}
        </nav>

        <div className="flex-none border-t border-white/10 pt-4 text-xs leading-5 text-slate-400">
          <div className="font-black text-cyan-200">標準操作流程</div>
          <div className="mt-1">來源系統 → 派工流程 → Agent → Task。所有派工設定只從派工流程進入。</div>
        </div>
      </aside>
    </>
  );
}
