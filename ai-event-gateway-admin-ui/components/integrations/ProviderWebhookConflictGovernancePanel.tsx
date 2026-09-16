'use client';
import { createUuid } from '@/lib/utils/uuid';
import { useCallback, useEffect, useState } from 'react';
import { apiRequest } from '@/lib/api/client';

type Inbox = {
  inboxId: string; providerType: string; providerEventId: string; eventType: string; externalIssueId?: string;
  status: string; signatureVerified: boolean; timestampVerified: boolean; replayCount: number; retryCount: number;
  lastErrorCode?: string; receivedAt: string;
};
type Conflict = {
  conflictId: string; externalIssueId: string; classification: string; status: string; reasonCode: string;
  detectedAt: string; resolvedAt?: string;
};
type ConflictEvent = { eventId: string; eventType: string; actorId?: string; reasonCode: string; occurredAt: string; eventHash: string };

export function ProviderWebhookConflictGovernancePanel() {
  const [inbox, setInbox] = useState<Inbox[]>([]);
  const [conflicts, setConflicts] = useState<Conflict[]>([]);
  const [events, setEvents] = useState<Record<string, ConflictEvent[]>>({});
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [loading, setLoading] = useState(true);
  const [busyKey, setBusyKey] = useState('');

  const load = useCallback(async () => {
    setLoading(true); setError('');
    try {
      const [a, b] = await Promise.all([
        apiRequest<Inbox[]>('/api/integrations/provider-webhooks/inbox?limit=100'),
        apiRequest<Conflict[]>('/api/integrations/external-conflicts?limit=100')
      ]);
      setInbox(a); setConflicts(b);
    } catch (value) { setError(value instanceof Error ? value.message : 'Unable to load Provider observation evidence.'); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { void load(); }, [load]);

  async function replay(item: Inbox) {
    if (busyKey) return;
    setBusyKey(`replay:${item.inboxId}`); setError(''); setMessage('');
    try {
      await apiRequest(`/api/integrations/provider-webhooks/inbox/${encodeURIComponent(item.inboxId)}/replay`, {
        method: 'POST', headers: { 'Idempotency-Key': createUuid() }, body: { reason: 'Replay immutable Provider observation evidence after operator review.' }
      });
      setMessage('Webhook observation replay scheduled. I0-H replay records observation evidence only; it cannot create conflict arbitration or Provider Action Candidates.');
      await load();
    } catch (value) { setError(value instanceof Error ? value.message : 'Webhook observation replay failed.'); }
    finally { setBusyKey(''); }
  }

  async function loadEvents(conflictId: string) {
    if (events[conflictId]) { setEvents(current => { const next = { ...current }; delete next[conflictId]; return next; }); return; }
    setBusyKey(`events:${conflictId}`); setError('');
    try {
      const value = await apiRequest<ConflictEvent[]>(`/api/integrations/external-conflicts/${encodeURIComponent(conflictId)}/events?limit=200`);
      setEvents(current => ({ ...current, [conflictId]: value }));
    } catch (value) { setError(value instanceof Error ? value.message : 'Historical conflict timeline could not be loaded.'); }
    finally { setBusyKey(''); }
  }

  return <section className="space-y-5 rounded-2xl border border-slate-200 bg-white p-5" aria-labelledby="provider-observation-title">
    <div className="flex flex-wrap items-start justify-between gap-3"><div><h2 id="provider-observation-title" className="text-lg font-black text-slate-950">Provider Observation Evidence</h2><p className="mt-1 max-w-3xl text-sm leading-6 text-slate-600">Verified Redmine Webhooks are retained as immutable observations. Legacy OpenDispatch-versus-provider conflict arbitration is retired; historical conflict rows remain read-only evidence.</p></div><button type="button" onClick={() => void load()} disabled={loading || !!busyKey} className="rounded-lg border px-3 py-2 text-sm font-bold disabled:opacity-50">Refresh</button></div>
    {error ? <div role="alert" className="rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-900">{error}</div> : null}
    {message ? <div role="status" className="rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-900">{message}</div> : null}
    <div><h3 className="font-black text-slate-900">Webhook observations</h3><div className="mt-3 space-y-2">{inbox.map(item => <article key={item.inboxId} className="rounded-xl border p-3"><div className="flex flex-wrap items-center justify-between gap-2"><div><div className="font-bold">{item.providerType} · {item.eventType}</div><div className="mt-1 text-xs text-slate-500">Issue {item.externalIssueId || '—'} · Event {item.providerEventId} · {item.status}</div></div><button type="button" onClick={() => void replay(item)} disabled={!!busyKey} className="rounded-lg border px-3 py-2 text-xs font-bold disabled:opacity-50">Replay observation</button></div>{item.lastErrorCode ? <div className="mt-2 text-xs font-semibold text-rose-700">{item.lastErrorCode}</div> : null}</article>)}{!loading && inbox.length === 0 ? <p className="text-sm text-slate-500">No Provider Webhook observations.</p> : null}</div></div>
    {conflicts.length > 0 ? <details className="rounded-xl border border-amber-200 bg-amber-50 p-4"><summary className="cursor-pointer font-black text-amber-950">Historical conflict evidence · read-only ({conflicts.length})</summary><p className="mt-2 text-sm text-amber-900">These rows come from the retired bidirectional Issue authority model. They cannot be resolved, retried or used to change Redmine/Task authority.</p><div className="mt-3 space-y-2">{conflicts.map(item => <article key={item.conflictId} className="rounded-lg border border-amber-200 bg-white p-3"><div className="font-bold text-slate-900">Issue {item.externalIssueId} · {item.classification}</div><div className="mt-1 text-xs text-slate-500">{item.status} · {item.reasonCode} · {item.detectedAt}</div><button type="button" onClick={() => void loadEvents(item.conflictId)} disabled={!!busyKey} className="mt-2 rounded-lg border px-2 py-1 text-xs font-bold">{events[item.conflictId] ? 'Hide timeline' : 'View historical timeline'}</button>{events[item.conflictId] ? <ul className="mt-2 space-y-1 text-xs text-slate-600">{events[item.conflictId].map(event => <li key={event.eventId}>{event.occurredAt} · {event.eventType} · {event.reasonCode}</li>)}</ul> : null}</article>)}</div></details> : null}
  </section>;
}
