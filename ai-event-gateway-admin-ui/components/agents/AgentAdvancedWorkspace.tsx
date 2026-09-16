'use client';

import { AgentRuntimeDiagnosticsExperience } from '@/components/phase7d/AgentRuntimeDiagnosticsExperience';
import type { AgentDetailBundle } from '@/hooks/useAgentDetail';
import { AdvancedAgentData, ConnectionPanel } from '@/components/agents/AgentAdvancedPanels';

export function AgentAdvancedWorkspace({
  data,
  agentId,
  refreshing,
  onRefresh,
  pingAgent,
  disconnectAgent,
  disconnectAllAgentSessions,
  createRuntimeBindingFromCurrentRuntime,
  activateRuntimeBinding,
}: Readonly<{
  data: AgentDetailBundle;
  agentId: string;
  refreshing: boolean;
  onRefresh: () => Promise<void> | void;
  pingAgent: () => Promise<unknown>;
  disconnectAgent: () => Promise<unknown>;
  disconnectAllAgentSessions: () => Promise<unknown>;
  createRuntimeBindingFromCurrentRuntime: () => Promise<unknown>;
  activateRuntimeBinding: (bindingId: string) => Promise<unknown>;
}>) {
  const confirmAction = (message: string, action: () => Promise<unknown> | unknown) => {
    if (window.confirm(message)) void action();
  };
  return (
    <div className="space-y-5">
      <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm leading-6 text-slate-700">
        <div className="font-black text-slate-900">Advanced Agent administration</div>
        <p className="mt-1">Runtime binding, credentials, startup commands, authentication failures, repair actions, security activity, and low-level runtime diagnostics live here. Normal Agent setup should use Overview, Capabilities, Dispatch Access, Issue Tracking, and Agent Pools first.</p>
      </div>
      <ConnectionPanel
        data={data}
        agentId={agentId}
        refreshing={refreshing}
        onRefresh={() => void onRefresh()}
        onPing={() => void pingAgent()}
        onDisconnect={() => confirmAction(`Disconnect the current runtime session for ${agentId}?`, disconnectAgent)}
        onDisconnectAll={() => confirmAction(`Disconnect all runtime sessions for ${agentId}?`, disconnectAllAgentSessions)}
        onCreateOrActivateRuntimeBinding={() => confirmAction(`Create or activate runtime binding for ${agentId}?`, createRuntimeBindingFromCurrentRuntime)}
        onActivateRuntimeBinding={(bindingId) => confirmAction(`Activate runtime binding ${bindingId} for ${agentId}?`, () => activateRuntimeBinding(bindingId))}
      />
      <details className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <summary className="cursor-pointer text-sm font-black text-slate-800">Runtime diagnostics</summary>
        <div className="mt-5"><AgentRuntimeDiagnosticsExperience profile={data.profile} runtime={data.runtime} descriptor={data.runtimeDescriptor} load={data.runtimeLoad} bindings={data.runtimeBindings ?? []} /></div>
      </details>
      <details className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <summary className="cursor-pointer text-sm font-black text-slate-800">Security and runtime activity</summary>
        <div className="mt-5"><AdvancedAgentData data={data} /></div>
      </details>
    </div>
  );
}

