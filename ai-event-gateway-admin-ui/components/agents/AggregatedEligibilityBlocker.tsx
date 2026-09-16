'use client';

export interface AggregatedEligibilityBlockerProps {
  totalCandidates: number;
  eligibleCandidates: number;
  blockedCounts: Record<string, number>;
  className?: string;
}

type BlockerGroup = 'offline' | 'capacity' | 'credential' | 'runtime' | 'policy' | 'other';

function groupFor(code: string): BlockerGroup {
  const normalized = code.toUpperCase();
  if (normalized.includes('OFFLINE') || normalized.includes('HEARTBEAT') || normalized.includes('DISCONNECTED')) return 'offline';
  if (normalized.includes('CAPACITY') || normalized.includes('WORKLOAD') || normalized.includes('BUSY')) return 'capacity';
  if (normalized.includes('CREDENTIAL') || normalized.includes('TOKEN') || normalized.includes('AUTH')) return 'credential';
  if (normalized.includes('RUNTIME') || normalized.includes('BINDING') || normalized.includes('SESSION')) return 'runtime';
  if (normalized.includes('POLICY') || normalized.includes('CAPABILITY') || normalized.includes('SCOPE')) return 'policy';
  return 'other';
}

const labels: Record<BlockerGroup, string> = {
  offline: 'Offline or missing heartbeat',
  capacity: 'At capacity',
  credential: 'Credential or authorization issue',
  runtime: 'Runtime not ready',
  policy: 'Blocked by policy',
  other: 'Other blockers',
};

export function AggregatedEligibilityBlocker({ totalCandidates, eligibleCandidates, blockedCounts, className = '' }: Readonly<AggregatedEligibilityBlockerProps>) {
  const grouped = Object.entries(blockedCounts).reduce<Record<BlockerGroup, number>>((accumulator, [code, count]) => {
    const group = groupFor(code);
    accumulator[group] += count;
    return accumulator;
  }, { offline: 0, capacity: 0, credential: 0, runtime: 0, policy: 0, other: 0 });
  const blocked = Math.max(0, totalCandidates - eligibleCandidates);
  const entries = (Object.entries(grouped) as Array<[BlockerGroup, number]>).filter(([, count]) => count > 0);

  return (
    <section className={`rounded-2xl border p-4 ${eligibleCandidates > 0 ? 'border-emerald-200 bg-emerald-50' : 'border-amber-200 bg-amber-50'} ${className}`} aria-labelledby="eligibility-blocker-title">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h3 id="eligibility-blocker-title" className="text-sm font-black text-slate-950">
            {eligibleCandidates > 0 ? `${eligibleCandidates} eligible Agent${eligibleCandidates === 1 ? '' : 's'}` : 'No eligible agent is currently available.'}
          </h3>
          <p className="mt-1 text-xs font-semibold text-slate-600">{eligibleCandidates} of {totalCandidates} candidates are eligible; {blocked} are blocked.</p>
        </div>
        <span className={`rounded-full px-3 py-1 text-xs font-black ${eligibleCandidates > 0 ? 'bg-emerald-100 text-emerald-800' : 'bg-amber-100 text-amber-900'}`}>{eligibleCandidates > 0 ? 'READY' : 'BLOCKED'}</span>
      </div>
      {entries.length > 0 ? (
        <ul className="mt-3 grid gap-2 sm:grid-cols-2" aria-label="Aggregated blocker counts">
          {entries.map(([group, count]) => (
            <li key={group} className="flex items-center justify-between rounded-xl border border-white/80 bg-white/80 px-3 py-2 text-xs font-bold text-slate-700">
              <span>{labels[group]}</span><span>{count}</span>
            </li>
          ))}
        </ul>
      ) : <p className="mt-3 text-xs text-slate-600">No structured blocker counts were returned. Review Runtime Verification and dispatch evidence.</p>}
    </section>
  );
}
