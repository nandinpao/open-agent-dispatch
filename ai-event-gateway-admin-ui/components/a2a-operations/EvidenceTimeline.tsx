import type { A2ATimelineEntry } from '@/lib/api/domains/a2aOperationsApi';
import { A2AStatusBadge } from './A2AStatusBadge';
import { humanizeA2ACode } from '@/lib/a2a/contracts';

function formatTime(value?: string | null): string {
  if (!value) return 'Time unavailable';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

export function EvidenceTimeline({ entries }: { entries: A2ATimelineEntry[] }) {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex items-center justify-between gap-3">
        <div>
          <p className="text-xs font-black uppercase tracking-[0.18em] text-slate-500">Evidence chain</p>
          <h2 className="mt-1 text-lg font-black text-slate-900">Canonical timeline</h2>
        </div>
        <span className="text-sm font-bold text-slate-500">{entries.length} events</span>
      </div>
      <div className="mt-5 space-y-0">
        {entries.length === 0 ? <p className="rounded-xl bg-slate-50 p-4 text-sm text-slate-600">No evidence events are available.</p> : entries.map((entry, index) => (
          <div key={`${entry.eventId}-${index}`} className="relative grid grid-cols-[24px_1fr] gap-3 pb-5 last:pb-0">
            <div className="flex flex-col items-center">
              <div className="mt-1 h-3 w-3 rounded-full border-2 border-indigo-600 bg-white" />
              {index < entries.length - 1 ? <div className="mt-1 w-px flex-1 bg-slate-200" /> : null}
            </div>
            <div className="rounded-xl border border-slate-100 bg-slate-50/70 p-4">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p className="text-xs font-black uppercase tracking-wide text-indigo-700">{humanizeA2ACode(entry.stage)} · {humanizeA2ACode(entry.eventType)}</p>
                  <p className="mt-1 text-sm font-bold text-slate-900">{entry.message || (entry.reasonCode ? humanizeA2ACode(entry.reasonCode) : 'Evidence recorded')}</p>
                </div>
                <A2AStatusBadge value={entry.status} />
              </div>
              <div className="mt-3 grid gap-2 text-xs text-slate-600 sm:grid-cols-2">
                <p><strong>Occurred:</strong> {formatTime(entry.occurredAt)}</p>
                <p><strong>Actor:</strong> {entry.actor || 'System'}</p>
                {entry.reasonCode ? <p><strong>Reason:</strong> {humanizeA2ACode(entry.reasonCode)}</p> : null}
                {entry.evidenceReference ? <p className="break-all"><strong>Reference:</strong> {entry.evidenceReference}</p> : null}
              </div>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
