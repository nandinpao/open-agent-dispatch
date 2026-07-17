import Link from 'next/link';
import { DispatchUserFacingReason } from '@/components/common/DispatchUserFacingReason';
import { EmptyState } from '@/components/common/EmptyState';
import { JsonViewer } from '@/components/common/JsonViewer';
import { StatusBadge } from '@/components/common/StatusBadge';
import type { CoreAgentCandidateScore, CoreRoutingDecisionRecord } from '@/lib/types/core';
import { formatDateTime } from '@/lib/utils/format';

function numericBreakdown(candidate: CoreAgentCandidateScore, key: string): number | undefined {
  const value = candidate.scoreBreakdown?.[key];
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined;
}

function stringBreakdown(candidate: CoreAgentCandidateScore, key: string): string | undefined {
  const value = candidate.scoreBreakdown?.[key];
  return typeof value === 'string' && value.trim() ? value : undefined;
}

function runtimeBreakdown(candidate: CoreAgentCandidateScore): Record<string, unknown> | undefined {
  const value = candidate.scoreBreakdown?.runtime;
  return typeof value === 'object' && value !== null && !Array.isArray(value) ? value as Record<string, unknown> : undefined;
}

function extractBracketList(text: string | undefined, key: string): string[] {
  if (!text) return [];
  const match = text.match(new RegExp(`${key}=\\[([^\\]]*)\\]`));
  if (!match?.[1]) return [];
  return match[1].split(',').map((item) => item.trim()).filter(Boolean);
}

function skillVersionMismatches(decision: CoreRoutingDecisionRecord): string[] {
  const mismatches = new Set<string>();
  for (const candidate of decision.candidates ?? []) {
    for (const item of candidate.missingCapabilities ?? []) {
      if (String(item).toLowerCase().includes('skillversion')) mismatches.add(item);
    }
    const reason = stringBreakdown(candidate, 'skillVersionReason');
    if (reason && reason.toLowerCase().includes('incompatible')) mismatches.add(reason);
  }
  return Array.from(mismatches);
}


function RoutingScoreCell({ candidate }: Readonly<{ candidate: CoreAgentCandidateScore }>) {
  const keys = [
    ['capabilityScore', 'cap'],
    ['availabilityScore', 'avail'],
    ['slotScore', 'slot'],
    ['loadScore', 'load'],
    ['siteScore', 'site'],
    ['domainScore', 'domain'],
    ['healthScore', 'health'],
    ['policyBonus', 'policy'],
    ['runtimePenalty', 'runtime -'],
    ['skillScore', 'skill'],
    ['skillPenalty', 'skill -'],
    ['skillVersionScore', 'version'],
    ['skillVersionPenalty', 'version -']
  ] as const;
  return (
    <div className="flex flex-wrap gap-1.5 text-xs">
      {keys.map(([key, label]) => {
        const value = numericBreakdown(candidate, key);
        if (value === undefined || value === 0) return null;
        return <span key={key} className="rounded-full bg-slate-100 px-2 py-0.5 font-semibold text-slate-600">{label}: {value}</span>;
      })}
    </div>
  );
}

function CandidateExplainRow({ decision, candidate }: Readonly<{ decision: CoreRoutingDecisionRecord; candidate: CoreAgentCandidateScore }>) {
  const selected = candidate.agentId === decision.selectedAgentId;
  const runtime = runtimeBreakdown(candidate);
  const skillVersionReason = stringBreakdown(candidate, 'skillVersionReason');
  const hasSkillMismatch = (candidate.missingCapabilities ?? []).some((item) => item.toLowerCase().includes('skillversion'))
    || Boolean(skillVersionReason?.toLowerCase().includes('incompatible'));
  return (
    <tr className={`align-top hover:bg-slate-50 ${selected ? 'bg-emerald-50/60' : ''}`}>
      <td className="px-4 py-3">
        <div className="font-semibold text-slate-900">
          {candidate.agentId ? <Link href={`/agents/${encodeURIComponent(candidate.agentId)}`} className="text-blue-600 hover:text-blue-700">{candidate.agentId}</Link> : '-'}
        </div>
        <div className="mt-1 text-xs text-slate-400">{candidate.ownerGatewayNodeId ?? '-'} / {candidate.agentSessionId ?? '-'}</div>
        <div className="mt-1 text-xs text-slate-400">site: {candidate.siteId ?? '-'}</div>
      </td>
      <td className="px-4 py-3">
        <div className="text-lg font-bold text-slate-900">{candidate.score}</div>
        <div className="mt-1 flex flex-wrap gap-1">
          {selected ? <StatusBadge status="SELECTED" /> : <StatusBadge status="NOT_SELECTED" />}
          {hasSkillMismatch ? <StatusBadge status="SKILL_VERSION_MISMATCH" /> : null}
        </div>
      </td>
      <td className="max-w-sm px-4 py-3 text-slate-600">
        <RoutingScoreCell candidate={candidate} />
        <div className="mt-2 text-xs leading-5 text-slate-500">{candidate.reason ?? '-'}</div>
      </td>
      <td className="max-w-xs px-4 py-3 text-xs text-slate-600">
        <div className="font-semibold text-emerald-700">Matched</div>
        <div className="mt-1 break-words">{candidate.matchedCapabilities?.join(', ') || '-'}</div>
        <div className="mt-2 font-semibold text-rose-700">Missing</div>
        <div className="mt-1 break-words">{candidate.missingCapabilities?.join(', ') || '-'}</div>
      </td>
      <td className="max-w-xs px-4 py-3 text-xs text-slate-600">
        <div>policy: {stringBreakdown(candidate, 'routingPolicy') ?? decision.routingPolicy ?? '-'}</div>
        <div>domain: {stringBreakdown(candidate, 'taskDomain') ?? '-'}</div>
        <div>raw/final: {numericBreakdown(candidate, 'rawScore') ?? '-'} / {numericBreakdown(candidate, 'finalScore') ?? candidate.score}</div>
        {skillVersionReason ? <div className={`mt-1 font-semibold ${hasSkillMismatch ? 'text-rose-600' : 'text-emerald-700'}`}>skill version: {skillVersionReason}</div> : null}
        {runtime ? <details className="mt-2"><summary className="cursor-pointer font-semibold text-blue-600">runtime load</summary><div className="mt-2"><JsonViewer value={runtime} /></div></details> : null}
        {candidate.scoreBreakdown ? <details className="mt-2"><summary className="cursor-pointer font-semibold text-blue-600">score JSON</summary><div className="mt-2"><JsonViewer value={candidate.scoreBreakdown} /></div></details> : null}
      </td>
    </tr>
  );
}

export function RoutingExplainabilityPanel({ decisions, error }: Readonly<{ decisions?: CoreRoutingDecisionRecord[]; error?: string }>) {
  const latest = decisions?.[0];
  const poisonExcluded = extractBracketList(latest?.decisionReason, 'poisonAgentExcluded');
  const reservationExcluded = extractBracketList(latest?.decisionReason, 'excludedAfterReservationRace');
  const mismatches = latest ? skillVersionMismatches(latest) : [];
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="text-base font-bold text-slate-900">Routing Explainability</h2>
          <p className="mt-1 text-sm text-slate-500">顯示 Core routing decision 的選擇原因、score breakdown、domain policy、poison exclusion 與 skill version mismatch。</p>
        </div>
        <StatusBadge status={error ? 'ROUTING_EXPLAIN_UNAVAILABLE' : latest ? latest.status ?? 'ROUTING_READY' : 'NO_ROUTING_DECISION'} />
      </div>
      {error ? <p className="mt-4 rounded-xl bg-amber-50 p-4 text-sm font-semibold text-amber-700">{error}</p> : null}
      {!error && !latest ? <EmptyState title="尚無 routing decision" description="此 Task 可能尚未進入 assignment/routing，或 routing decision store 尚未啟用。" /> : null}
      {!error && latest ? (
        <>
          <div className="mt-4 grid gap-3 md:grid-cols-4">
            <div className="rounded-xl border border-slate-100 bg-slate-50 px-4 py-3">
              <div className="text-xs font-semibold uppercase tracking-wide text-slate-400">Decision</div>
              <div className="mt-1 break-all text-sm font-semibold text-slate-800">{latest.decisionId}</div>
              <div className="mt-1 text-xs text-slate-400">{latest.createdAt ? formatDateTime(latest.createdAt) : '-'}</div>
            </div>
            <div className="rounded-xl border border-slate-100 bg-slate-50 px-4 py-3">
              <div className="text-xs font-semibold uppercase tracking-wide text-slate-400">Domain Policy</div>
              <div className="mt-1 text-sm font-semibold text-slate-800">{latest.routingPolicy ?? '-'}</div>
              <div className="mt-1 text-xs text-slate-400">status: {latest.status ?? '-'}</div>
            </div>
            <div className="rounded-xl border border-slate-100 bg-slate-50 px-4 py-3">
              <div className="text-xs font-semibold uppercase tracking-wide text-slate-400">Selected Agent</div>
              <div className="mt-1 break-all text-sm font-semibold text-slate-800">{latest.selectedAgentId ?? '-'}</div>
              <div className="mt-1 text-xs text-slate-400">score: {latest.selectedScore ?? '-'}</div>
            </div>
            <div className="rounded-xl border border-slate-100 bg-slate-50 px-4 py-3">
              <div className="text-xs font-semibold uppercase tracking-wide text-slate-400">Candidate Diagnostics</div>
              <div className="mt-1 text-sm font-semibold text-slate-800">{latest.candidates?.length ?? 0} scored candidates</div>
              <div className="mt-1 text-xs text-slate-400">poison excluded: {poisonExcluded.length} · reservation excluded: {reservationExcluded.length}</div>
            </div>
          </div>

          <div className="mt-4 rounded-xl bg-blue-50 p-4 text-sm text-blue-800">
            <div className="font-semibold">Why Core made this routing decision</div>
            <div className="mt-1">
              <DispatchUserFacingReason
                value={latest.decisionReason}
                error={latest.userFacingError}
                showOperatorActions
                actionContext={{ taskId: latest.taskId, agentId: latest.selectedAgentId, includeRunbook: false }}
                codeClassName="mb-2 inline-flex rounded-full bg-blue-900 px-2.5 py-1 text-[11px] font-black uppercase tracking-wide text-white"
                detailsClassName="mt-3 rounded-lg bg-white/70 px-3 py-2 text-xs"
                technicalClassName="mt-2 break-words whitespace-pre-wrap font-mono leading-5 text-blue-900"
              />
            </div>
          </div>

          {poisonExcluded.length ? (
            <div className="mt-4 rounded-xl bg-rose-50 p-4 text-sm text-rose-800">
              <div className="font-semibold">Poison agents excluded from routing</div>
              <div className="mt-1 break-words">{poisonExcluded.join(', ')}</div>
            </div>
          ) : null}

          {reservationExcluded.length ? (
            <div className="mt-4 rounded-xl bg-amber-50 p-4 text-sm text-amber-800">
              <div className="font-semibold">Reservation race exclusions</div>
              <div className="mt-1 break-words">{reservationExcluded.join(', ')}</div>
            </div>
          ) : null}

          {mismatches.length ? (
            <div className="mt-4 rounded-xl bg-rose-50 p-4 text-sm text-rose-800">
              <div className="font-semibold">Policy version mismatch</div>
              <ul className="mt-1 list-disc space-y-1 pl-5">
                {mismatches.map((item) => <li key={item}>{item}</li>)}
              </ul>
            </div>
          ) : null}

          {latest.candidates?.length ? (
            <div className="mt-4 overflow-hidden rounded-xl border border-slate-100">
              <table className="min-w-full divide-y divide-slate-100 text-sm">
                <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                  <tr>
                    <th className="px-4 py-3">Agent</th>
                    <th className="px-4 py-3">Score</th>
                    <th className="px-4 py-3">Score Breakdown</th>
                    <th className="px-4 py-3">Capability / Policy Match</th>
                    <th className="px-4 py-3">Policy / Runtime Detail</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 bg-white">
                  {latest.candidates.map((candidate) => <CandidateExplainRow key={`${latest.decisionId}-${candidate.agentId}`} decision={latest} candidate={candidate} />)}
                </tbody>
              </table>
            </div>
          ) : null}

          {decisions && decisions.length > 1 ? (
            <details className="mt-4 rounded-xl border border-slate-100 bg-slate-50 p-4">
              <summary className="cursor-pointer text-sm font-bold text-slate-700">Earlier routing decisions for this task</summary>
              <div className="mt-3 space-y-2 text-sm text-slate-600">
                {decisions.slice(1).map((decision) => (
                  <div key={decision.decisionId} className="rounded-lg bg-white p-3 shadow-sm">
                    <div className="flex flex-wrap items-center gap-2">
                      <StatusBadge status={decision.status ?? 'UNKNOWN'} />
                      <span className="font-semibold">{decision.routingPolicy ?? '-'}</span>
                      <span className="text-slate-400">{decision.createdAt ? formatDateTime(decision.createdAt) : '-'}</span>
                    </div>
                    <div className="mt-1 break-words text-xs text-slate-500">
                      <DispatchUserFacingReason
                        value={decision.decisionReason}
                        error={decision.userFacingError}
                        showOperatorActions
                        actionContext={{ taskId: decision.taskId, agentId: decision.selectedAgentId, includeRunbook: false }}
                        codeClassName="inline-flex rounded-full bg-slate-800 px-2 py-0.5 text-[10px] font-black uppercase tracking-wide text-white"
                        detailsClassName="mt-2 rounded-lg bg-slate-50 px-3 py-2 text-xs"
                        technicalClassName="mt-2 break-words whitespace-pre-wrap font-mono leading-5 text-slate-600"
                      />
                    </div>
                  </div>
                ))}
              </div>
            </details>
          ) : null}
        </>
      ) : null}
    </section>
  );
}
