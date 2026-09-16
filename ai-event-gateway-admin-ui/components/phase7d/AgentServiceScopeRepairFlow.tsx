import Link from 'next/link';
import type { CoreAgentAuthorizationScope } from '@/lib/types/core';

export function AgentServiceScopeRepairFlow({ agentId, scopes }: Readonly<{ agentId: string; scopes: CoreAgentAuthorizationScope[] }>) {
  const enabled = scopes.filter((scope) => scope.enabled !== false);
  const taskTypes = Array.from(new Set(enabled.map((scope) => scope.taskType).filter(Boolean)));
  const systems = Array.from(new Set(enabled.map((scope) => scope.systemCode).filter(Boolean)));
  const missing = enabled.length === 0;
  return (
    <section className={`rounded-2xl border p-5 ${missing ? 'border-rose-200 bg-rose-50' : 'border-emerald-200 bg-emerald-50'}`} aria-labelledby="service-scope-repair-title">
      <h3 id="service-scope-repair-title" className={`font-black ${missing ? 'text-rose-950' : 'text-emerald-950'}`}>Dispatch access repair</h3>
      <p className={`mt-1 text-sm leading-6 ${missing ? 'text-rose-900' : 'text-emerald-900'}`}>{missing ? 'The Agent is connected, but no approved Dispatch Access is available for workload execution. Add only the Source System and Task types this Agent needs.' : 'The Agent has Dispatch Access evidence. Dispatch Flow selection, capability and runtime eligibility are still evaluated separately.'}</p>
      <div className="mt-3 grid gap-3 sm:grid-cols-3">
        <Info label="Access rules" value={String(enabled.length)} />
        <Info label="Task types" value={taskTypes.join(', ') || 'None'} />
        <Info label="Source systems" value={systems.join(', ') || 'None'} />
      </div>
      <ol className="mt-4 grid gap-2 text-sm">
        <li><strong>1.</strong> Identify the required Source System, Task type, site, and data-classification ceiling.</li>
        <li><strong>2.</strong> Assign the least-privilege Dispatch Access through the governed Agent administration workflow.</li>
        <li><strong>3.</strong> Re-read runtime readiness and verify that Dispatch Flow selection remains valid.</li>
      </ol>
      {missing ? <Link href={`/agents/${encodeURIComponent(agentId)}#dispatch-summary`} className="mt-4 inline-flex rounded-xl bg-rose-800 px-4 py-2 text-sm font-black text-white">Open governed repair context</Link> : null}
    </section>
  );
}
function Info({ label, value }: Readonly<{ label: string; value: string }>) { return <div className="rounded-xl border border-current/10 bg-white/80 p-3"><div className="text-xs font-black uppercase opacity-60">{label}</div><div className="mt-1 break-words text-sm font-black">{value}</div></div>; }
