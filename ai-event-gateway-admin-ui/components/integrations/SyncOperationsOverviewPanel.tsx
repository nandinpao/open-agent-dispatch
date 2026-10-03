'use client';

import { useCallback, useEffect, useState } from 'react';
import { SyncOperationsConsole } from '@/components/integrations/SyncOperationsConsole';
import { loadIntegrationRecovery, type IntegrationRecoverySnapshot } from '@/lib/integrations/integrationRecovery';

export function SyncOperationsOverviewPanel() {
  const [snapshot, setSnapshot] = useState<IntegrationRecoverySnapshot | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true); setError('');
    try { setSnapshot(await loadIntegrationRecovery(300)); }
    catch (value) { setError(value instanceof Error ? value.message : 'Unable to load Integration Recovery summary.'); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { void load(); }, [load]);

  return <div className="space-y-3">
    <div className="flex justify-end"><button type="button" onClick={() => void load()} disabled={loading} className="rounded-xl border px-3 py-2 text-sm font-bold disabled:opacity-50">Refresh recovery summary</button></div>
    {error ? <div role="alert" className="rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm font-semibold text-rose-900">{error}</div> : null}
    {loading ? <div role="status" className="rounded-xl border bg-slate-50 p-5 text-sm text-slate-600">Loading canonical AdapterAction execution audit…</div> : snapshot ? <SyncOperationsConsole snapshot={snapshot}/> : null}
  </div>;
}
