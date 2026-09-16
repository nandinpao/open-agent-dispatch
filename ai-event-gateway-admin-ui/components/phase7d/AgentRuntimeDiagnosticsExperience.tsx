import type { CoreAgentProfile, CoreAgentRuntimeBinding, CoreAgentRuntimeDescriptor, CoreAgentRuntimeLoadSnapshot } from '@/lib/types/core';
import type { NettyAgentRuntime } from '@/lib/types/nettyRuntime';

function activeBinding(bindings: CoreAgentRuntimeBinding[]): CoreAgentRuntimeBinding | undefined {
  return bindings.find((binding) => String(binding.bindingStatus ?? '').toUpperCase() === 'ACTIVE');
}

export function AgentRuntimeDiagnosticsExperience({ profile, runtime, descriptor, load, bindings }: Readonly<{
  profile?: CoreAgentProfile;
  runtime?: NettyAgentRuntime;
  descriptor?: CoreAgentRuntimeDescriptor;
  load?: CoreAgentRuntimeLoadSnapshot;
  bindings: CoreAgentRuntimeBinding[];
}>) {
  const binding = activeBinding(bindings);
  const session = runtime?.sessionId ?? descriptor?.agentSessionId ?? 'Not connected';
  const node = runtime?.gatewayNodeId ?? runtime?.nodeId ?? descriptor?.ownerGatewayNodeId ?? 'Unknown';
  const availableSlots = load?.availableSlots ?? descriptor?.availableSlots ?? runtime?.availableSlots;
  const utilization = load?.capacityUtilization ?? descriptor?.capacityUtilization ?? runtime?.capacityUtilization;
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm" aria-labelledby="agent-runtime-diagnostics-title">
      <div><h3 id="agent-runtime-diagnostics-title" className="font-black text-slate-950">Agent runtime diagnostics</h3><p className="mt-1 text-sm leading-6 text-slate-600">Runtime identity, lease/binding, capacity, fencing evidence, and credential metadata are displayed separately. Tokens and raw runtime payloads are never shown.</p></div>
      <dl className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <Metric label="Agent" value={profile?.agentId ?? runtime?.agentId ?? 'Unknown'} />
        <Metric label="Session" value={session} />
        <Metric label="Gateway node" value={node} />
        <Metric label="Binding" value={binding?.bindingStatus ?? 'NOT ACTIVE'} />
        <Metric label="Binding ID" value={binding?.bindingId ?? '—'} />
        <Metric label="Available slots" value={String(availableSlots ?? '—')} />
        <Metric label="Capacity" value={utilization == null ? '—' : `${Math.round(utilization * 100)}%`} />
        <Metric label="Credential" value={profile?.credential?.credentialStatus ?? 'MISSING'} />
        <Metric label="Credential version" value={String(profile?.credential?.credentialVersion ?? '—')} />
        <Metric label="Capability revision" value={descriptor?.pluginVersion ?? runtime?.capabilityRevision ?? '—'} />
        <Metric label="Draining" value={(load?.draining ?? descriptor?.draining ?? runtime?.draining) ? 'YES' : 'NO'} />
        <Metric label="Last heartbeat" value={load?.heartbeatAt ?? descriptor?.lastHeartbeatAt ?? runtime?.lastHeartbeatAt ?? 'Not recorded'} />
      </dl>
    </section>
  );
}
function Metric({ label, value }: Readonly<{ label: string; value: string }>) { return <div className="rounded-xl border border-slate-200 bg-slate-50 p-3"><dt className="text-xs font-black uppercase tracking-wide text-slate-500">{label}</dt><dd className="mt-1 break-all text-sm font-black text-slate-900">{value}</dd></div>; }
