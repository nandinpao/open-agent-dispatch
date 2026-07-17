'use client';

import { DataSourceBadge } from '@/components/common/DataSourceBadge';
import { StatusBadge } from '@/components/common/StatusBadge';
import { useAuth } from '@/components/auth/AuthProvider';
import { WorkspaceTenantSelector } from '@/components/auth/WorkspaceTenantSelector';
import { useAdminRealtime } from '@/hooks/useAdminRealtime';
import { useI18n } from '@/hooks/useI18n';
import { getPublicEnv } from '@/lib/constants/env';
import { formatDateTime } from '@/lib/utils/format';

interface TopbarProps {
  onMenuClick: () => void;
}

export function Topbar({ onMenuClick }: Readonly<TopbarProps>) {
  const env = getPublicEnv();
  const { t } = useI18n();
  const { connection } = useAdminRealtime();
  const { user, logout } = useAuth();

  return (
    <header className="sticky top-0 z-30 border-b border-slate-200 bg-white/90 px-4 py-4 backdrop-blur sm:px-6 lg:px-8">
      <div className="flex items-center justify-between gap-4">
        <div className="flex min-w-0 items-center gap-3">
          <button
            type="button"
            onClick={onMenuClick}
            className="rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50 lg:hidden"
            aria-label={t('nav.openNavigation')}
          >
            ☰
          </button>
          <div className="min-w-0">
            <div className="truncate text-sm font-semibold text-slate-900">{env.appName}</div>
            <div className="truncate text-xs text-slate-500">{t('topbar.modeSummary', { backendMode: env.adminBackendMode, coreUrl: env.coreApiBaseUrl, gatewayUrl: env.nettyApiBaseUrl })}</div>
          </div>
        </div>
        <div className="flex shrink-0 items-center gap-2 sm:gap-3">
          <div className="hidden text-right text-xs text-slate-500 lg:block">
            <div className="max-w-xl truncate">{t('topbar.runtimeStream', { stream: env.useMock ? t('topbar.mockEventStream') : env.nettyRuntimeWsUrl })}</div>
            <div>{t('topbar.lastMessage', { time: connection.lastMessageAt ? formatDateTime(connection.lastMessageAt) : t('topbar.noLastMessage') })}</div>
          </div>
          <WorkspaceTenantSelector compact className="hidden md:flex" />
          <StatusBadge status={connection.status} />
          <div className="hidden sm:block">
            <DataSourceBadge source={env.useMock ? 'mock' : 'live'} label={env.useMock ? t('topbar.mockMode') : t('topbar.liveApiMode')} detail={env.productionMode ? t('topbar.productionGuardOn') : undefined} />
          </div>
          <div className="hidden rounded-full border border-slate-200 bg-white px-3 py-1 text-xs font-medium text-slate-600 xl:block">
            {user?.displayName ?? t('topbar.administrator')}
          </div>
          <button
            type="button"
            onClick={() => void logout()}
            className="rounded-full border border-slate-200 bg-white px-3 py-1 text-xs font-semibold text-slate-700 hover:bg-slate-50"
          >
            {t('topbar.logout')}
          </button>
        </div>
      </div>
    </header>
  );
}
