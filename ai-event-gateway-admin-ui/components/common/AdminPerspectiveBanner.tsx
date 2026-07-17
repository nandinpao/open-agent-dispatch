'use client';

import Link from 'next/link';
import { ErrorBox } from '@/components/common/ErrorBox';
import { StatusBadge } from '@/components/common/StatusBadge';
import { useGatewayHealth } from '@/hooks/useGatewayHealth';
import { getPublicEnv } from '@/lib/constants/env';

export function AdminPerspectiveBanner({
  compact = false
}: Readonly<{
  compact?: boolean;
}>) {
  const env = getPublicEnv();
  const health = useGatewayHealth();

  if (health.error && !compact) return <ErrorBox message={`Admin perspective unavailable: ${health.error}`} />;

  const nodeId = health.data?.nodeId ?? '-';

  return (
    <div className={`rounded-2xl border border-blue-200 bg-blue-50 ${compact ? 'p-3' : 'p-4'} text-sm text-blue-800`}>
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <StatusBadge status="SELF" />
            <span className="font-semibold">Admin aggregation node: {nodeId}</span>
          </div>
          <p className="mt-1 leading-6">
            Core provides the control-plane source of truth. Gateway runtime APIs provide live transport observations. SELF identifies the node currently handling the Gateway Admin API request.
          </p>
          {!compact ? (
            <p className="mt-1 leading-6">
              Core control-plane state is not the same as a runtime session. Agent online does not mean approved. Runtime delivery status does not replace the authoritative task state.
            </p>
          ) : null}
        </div>
        <div className="shrink-0 text-xs text-blue-700 lg:text-right">
          <div>Core: {env.coreApiBaseUrl}<br />Gateway: {env.nettyApiBaseUrl}</div>
          <div>Runtime stream: {env.useMock ? 'mock' : env.nettyRuntimeWsUrl}</div>
          <Link href="/cluster/diagnostics" className="mt-2 inline-flex font-bold text-blue-700 hover:text-blue-900">
            Open diagnostics →
          </Link>
        </div>
      </div>
    </div>
  );
}
