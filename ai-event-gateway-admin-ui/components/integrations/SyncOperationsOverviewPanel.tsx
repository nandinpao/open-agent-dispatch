'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { apiRequest } from '@/lib/api/client';
import { SyncOperationsConsole, type SyncOperationsSummary } from '@/components/integrations/SyncOperationsConsole';

type AdapterAction = { adapterType?: string; status?: string; lastError?: string; nextAttemptAt?: string };
type ProviderHealth = { status: string; lastFailureCode?: string; retryAfterAt?: string };
const circuitPriority = ['OPEN_CIRCUIT', 'SATURATED', 'THROTTLED', 'RECOVERING', 'DISABLED', 'HEALTHY'];

export function SyncOperationsOverviewPanel() {
  const [actions, setActions] = useState<AdapterAction[]>([]);
  const [health, setHealth] = useState<ProviderHealth[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true); setError('');
    try {
      const [actionRows, healthRows] = await Promise.all([
        apiRequest<AdapterAction[]>('/api/adapter-actions?limit=500'),
        apiRequest<ProviderHealth[]>('/api/integrations/projection-recovery/provider-health?limit=500')
      ]);
      setActions(actionRows.filter((item) => item.adapterType === 'ISSUE_TRACKING'));
      setHealth(healthRows);
    } catch (value) {
      setError(value instanceof Error ? value.message : 'Unable to load Issue Connector operations summary.');
    } finally { setLoading(false); }
  }, []);
  useEffect(() => { void load(); }, [load]);

  const summary = useMemo<SyncOperationsSummary>(() => {
    const pending = actions.filter((item) => ['PENDING', 'CLAIMED', 'EXECUTING'].includes(item.status || '')).length;
    const retryWaiting = actions.filter((item) => ['RETRY_WAITING', 'EXECUTOR_UNAVAILABLE'].includes(item.status || '')).length;
    const failed = actions.filter((item) => item.status === 'FAILED').length;
    const completed = actions.filter((item) => item.status === 'COMPLETED').length;
    const circuitState = circuitPriority.find((status) => health.some((item) => item.status === status)) ?? 'UNKNOWN';
    const lastProviderError = health.find((item) => item.lastFailureCode)?.lastFailureCode
      ?? actions.find((item) => item.lastError)?.lastError;
    const nextRetryAt = health.find((item) => item.retryAfterAt)?.retryAfterAt
      ?? actions.find((item) => item.nextAttemptAt)?.nextAttemptAt;
    return { pending, retryWaiting, failed, completed, circuitState, lastProviderError, nextRetryAt };
  }, [actions, health]);

  return <div className="space-y-3">
    <div className="flex justify-end"><button type="button" onClick={() => void load()} disabled={loading} className="rounded-xl border px-3 py-2 text-sm font-bold disabled:opacity-50">Refresh connector summary</button></div>
    {error ? <div role="alert" className="rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm font-semibold text-rose-900">{error}</div> : null}
    {loading ? <div role="status" className="rounded-xl border bg-slate-50 p-5 text-sm text-slate-600">Loading current Issue Connector actions and Provider health…</div> : <SyncOperationsConsole summary={summary}/>} 
  </div>;
}
