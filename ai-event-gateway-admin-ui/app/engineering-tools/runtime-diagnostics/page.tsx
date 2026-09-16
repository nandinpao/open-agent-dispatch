'use client';

import { EntitlementPageGuard } from '@/components/auth/EntitlementPageGuard';
import { DataSourceBadge } from '@/components/common/DataSourceBadge';
import { PageHeader } from '@/components/common/PageHeader';
import { StatusBadge } from '@/components/common/StatusBadge';
import { useAdminRealtime } from '@/hooks/useAdminRealtime';
import { getPublicEnv } from '@/lib/constants/env';
import { formatDateTime } from '@/lib/utils/format';

export default function RuntimeDiagnosticsPage() {
  const env = getPublicEnv();
  const { connection } = useAdminRealtime();
  const rows = [
    ['Backend mode', env.adminBackendMode],
    ['Core API', env.coreApiBaseUrl],
    ['Gateway API', env.nettyApiBaseUrl],
    ['Runtime WebSocket', env.useMock ? 'mock event stream' : env.nettyRuntimeWsUrl],
    ['Last runtime message', connection.lastMessageAt ? formatDateTime(connection.lastMessageAt) : 'No runtime message observed'],
  ];
  return (
    <EntitlementPageGuard featureId="engineering-tools">
      <main className="space-y-5">
        <PageHeader title="Runtime Diagnostics" description="Engineering-only runtime endpoints and connection evidence. These details are intentionally excluded from the everyday operator header." />
        <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="flex flex-wrap items-center gap-3">
            <StatusBadge status={connection.status} />
            <DataSourceBadge source={env.useMock ? 'mock' : 'live'} label={env.useMock ? 'Mock runtime' : 'Live runtime'} detail={env.productionMode ? 'Production guard enabled' : undefined} />
          </div>
          <dl className="mt-5 divide-y divide-slate-100">
            {rows.map(([label, value]) => (
              <div key={label} className="grid gap-1 py-3 md:grid-cols-[14rem_1fr]">
                <dt className="text-xs font-black uppercase tracking-wide text-slate-500">{label}</dt>
                <dd className="break-all text-sm font-semibold text-slate-800">{value || '—'}</dd>
              </div>
            ))}
          </dl>
        </section>
      </main>
    </EntitlementPageGuard>
  );
}
