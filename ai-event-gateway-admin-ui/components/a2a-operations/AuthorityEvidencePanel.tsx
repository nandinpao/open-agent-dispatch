import type { A2AAuthorityEvidence } from '@/lib/api/domains/a2aOperationsApi';
import { A2AStatusBadge } from './A2AStatusBadge';

interface Props {
  records: Array<A2AAuthorityEvidence | null | undefined>;
}

function formatTime(value?: string | null): string {
  if (!value) return '—';
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString();
}

export function AuthorityEvidencePanel({ records }: Props) {
  const visible = records.filter((record): record is A2AAuthorityEvidence => Boolean(record));
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <p className="text-xs font-black uppercase tracking-[0.18em] text-slate-500">Data-minimized evidence</p>
      <h2 className="mt-1 text-lg font-black text-slate-900">Authority records</h2>
      <p className="mt-2 text-sm leading-6 text-slate-600">Only operational identifiers and safe diagnostic facts are exposed. Payload references, dispatch tokens, fencing tokens, and token hashes remain server-side.</p>
      <div className="mt-4 grid gap-3 lg:grid-cols-2">
        {visible.map((record) => (
          <article key={`${record.evidenceType}:${record.evidenceId}`} className="rounded-xl border border-slate-200 bg-slate-50 p-4">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p className="text-xs font-black uppercase tracking-wide text-slate-500">{record.evidenceType}</p>
                <p className="mt-1 break-all text-sm font-black text-slate-900">{record.evidenceId}</p>
              </div>
              <A2AStatusBadge value={record.status} />
            </div>
            <div className="mt-3 grid gap-2 text-xs sm:grid-cols-2">
              <p><span className="font-black text-slate-500">Authority:</span> {record.authority}</p>
              <p><span className="font-black text-slate-500">Version:</span> {record.version ?? '—'}</p>
              <p className="sm:col-span-2"><span className="font-black text-slate-500">Occurred:</span> {formatTime(record.occurredAt)}</p>
            </div>
            {Object.keys(record.facts).length > 0 ? (
              <dl className="mt-3 divide-y divide-slate-200 rounded-lg border border-slate-200 bg-white px-3">
                {Object.entries(record.facts).map(([key, value]) => (
                  <div key={key} className="grid gap-1 py-2 text-xs sm:grid-cols-[145px_minmax(0,1fr)]">
                    <dt className="font-black text-slate-500">{key}</dt>
                    <dd className="break-all text-slate-700">{value}</dd>
                  </div>
                ))}
              </dl>
            ) : null}
          </article>
        ))}
      </div>
    </section>
  );
}
