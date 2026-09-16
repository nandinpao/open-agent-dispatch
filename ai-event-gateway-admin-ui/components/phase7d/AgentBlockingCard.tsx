import Link from 'next/link';
import type { AgentBlockingExperience } from '@/lib/phase7d/issueAgentUx';

const tone = {
  READY: 'border-emerald-200 bg-emerald-50 text-emerald-950',
  WARNING: 'border-amber-200 bg-amber-50 text-amber-950',
  BLOCKED: 'border-rose-200 bg-rose-50 text-rose-950',
} as const;

export function AgentBlockingCard({ experience, agentId, compact = false }: Readonly<{ experience: AgentBlockingExperience; agentId?: string; compact?: boolean }>) {
  const repairAnchor = experience.repairTarget === 'DISPATCH_ACCESS' ? 'dispatch-access' : 'dispatch-summary';
  return (
    <section className={`rounded-2xl border p-4 ${tone[experience.severity]}`} aria-label="Agent blocking status" data-agent-blocking-reason={experience.reason}>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <div className="text-xs font-black uppercase tracking-[.16em] opacity-70">{experience.severity === 'READY' ? 'Dispatch readiness' : 'Blocking reason'}</div>
          <h3 className="mt-1 font-black">{experience.title}</h3>
          {!compact ? <p className="mt-2 text-sm leading-6 opacity-90">{experience.explanation}</p> : null}
          <p className="mt-2 text-sm font-semibold">Safest next action: {experience.safestNextAction}</p>
        </div>
        {agentId && experience.repairTarget ? (
          <Link href={`/agents/${encodeURIComponent(agentId)}#${repairAnchor}`} className="shrink-0 rounded-xl border border-current/20 bg-white/80 px-3 py-2 text-xs font-black hover:bg-white">
            Open repair context
          </Link>
        ) : null}
      </div>
    </section>
  );
}
