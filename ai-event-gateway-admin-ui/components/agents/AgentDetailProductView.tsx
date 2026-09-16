'use client';

import Link from 'next/link';
import { useState } from 'react';
import { AdminTabLayout, type AdminTabItem } from '@/components/layout/AdminTabLayout';
import { AgentAdvancedWorkspace } from '@/components/agents/AgentAdvancedWorkspace';
import { AgentCapabilitiesWorkspace } from '@/components/agents/AgentCapabilitiesWorkspace';
import { AgentDispatchAccessPanel } from '@/components/agents/AgentDispatchAccessPanel';
import { AgentOverviewWorkspace, agentReadinessStatus, approvedCapabilityCount, runtimeIsConnected } from '@/components/agents/AgentOverviewWorkspace';
import { AgentPoolsWorkspace, AgentRecentWorkWorkspace } from '@/components/agents/AgentWorkspaces';
import { type AgentDetailTab, firstNonBlankValue, normalizeLifecycleStatus } from '@/components/agents/AgentDetailUi';
import { IssueTrackingContextCard } from '@/components/integrations/IssueTrackingContextCard';
import { CommandMessage } from '@/components/common/CommandMessage';
import { DataSourceBadge, dataSourceKindFromFlags } from '@/components/common/DataSourceBadge';
import { EmptyState } from '@/components/common/EmptyState';
import { ErrorBox } from '@/components/common/ErrorBox';
import { LiveDataUnavailable } from '@/components/common/LiveDataUnavailable';
import { LoadingBox } from '@/components/common/LoadingBox';
import { RefreshButton } from '@/components/common/RefreshButton';
import { ResourceGovernancePanel } from '@/components/resource-access/ResourceGovernancePanel';
import { StatusBadge } from '@/components/common/StatusBadge';
import { useAgentDetail } from '@/hooks/useAgentDetail';
import { useAuth } from '@/components/auth/AuthProvider';

interface AgentDetailProductViewProps {
  agentId: string;
}

function issueTrackingContexts(scopes: Array<{ enabled?: boolean; systemCode?: string | null; taskType?: string | null }>) {
  const seen = new Set<string>();
  return scopes
    .filter((scope) => scope.enabled !== false)
    .map((scope) => ({
      sourceSystemId: String(scope.systemCode ?? '').trim(),
      taskType: String(scope.taskType ?? '').trim(),
    }))
    .filter((context) => context.sourceSystemId && context.sourceSystemId !== '*')
    .map((context) => ({
      sourceSystemId: context.sourceSystemId,
      taskType: context.taskType && context.taskType !== '*' ? context.taskType : null,
    }))
    .filter((context) => {
      const key = `${context.sourceSystemId}|${context.taskType ?? ''}`;
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    });
}

export function AgentDetailProductView({ agentId }: AgentDetailProductViewProps) {
  const { status: authStatus, activeTenantId } = useAuth();
  const [activeTab, setActiveTab] = useState<AgentDetailTab>('overview');
  const {
    data,
    loading,
    refreshing,
    error,
    lastUpdatedAt,
    refresh,
    commandMessage,
    pingAgent,
    disconnectAgent,
    disconnectAllAgentSessions,
    createRuntimeBindingFromCurrentRuntime,
    activateRuntimeBinding,
    requestAgentCapability,
    approveAgentCapability,
    suspendAgentCapability,
    resumeAgentCapability,
    revokeAgentCapability,
    removeAgentCapability,
  } = useAgentDetail(
    agentId,
    activeTenantId,
    authStatus === 'AUTHENTICATED' && Boolean(activeTenantId),
    {
      advanced: activeTab === 'advanced',
      taskLineage: activeTab === 'work',
      capabilityDiagnostics: activeTab === 'capabilities',
    },
  );

  if (authStatus === 'CHECKING') return <LoadingBox label={`Loading agent ${agentId} workspace...`} />;
  if (authStatus === 'AUTHENTICATED' && !activeTenantId) return <EmptyState title="Workspace required" description="Select an administration workspace before loading tenant-scoped Agent data." />;
  if (loading) return <LoadingBox label={`Loading agent ${agentId}...`} />;
  if (error) return <ErrorBox message={error} />;
  if (!data) return <EmptyState title="Agent not found" description="Core and Gateway did not return this agent." />;

  const hasSourceErrors = Object.values(data.sourceErrors).some(Boolean);
  const dataSource = dataSourceKindFromFlags({
    hasLiveData: Boolean(data.profile || data.runtime || data.runtimes.length > 0 || data.tasks.length > 0),
    hasSourceErrors,
  });
  const liveDataUnavailable = Boolean(!data.profile && data.runtimes.length === 0 && data.sourceErrors.coreProfile && data.sourceErrors.nettyRuntimeAgents);

  if (liveDataUnavailable) {
    return (
      <LiveDataUnavailable
        title={`Live data is unavailable for ${agentId}`}
        description="The Agent workspace cannot verify the Core profile or Gateway runtime state. It will not infer readiness from missing data."
        details={Object.entries(data.sourceErrors).filter(([, value]) => value).map(([key, value]) => `${key}: ${value}`).join(' | ')}
        action={<button type="button" onClick={() => void refresh()} className="rounded-xl border border-rose-200 bg-white px-3 py-2 text-xs font-black text-rose-700 hover:bg-rose-100">Retry live load</button>}
      />
    );
  }

  const agentTenantId = firstNonBlankValue(
    data.profile?.tenantId,
    data.capabilityAssignments.find((item) => normalizeLifecycleStatus(item.status) !== 'REVOKED')?.tenantId,
  ) ?? activeTenantId ?? '';
  const contexts = issueTrackingContexts(data.profile?.authorizationScopes ?? []);

  const tabs: AdminTabItem[] = [
    { id: 'overview', label: 'Overview', badge: <StatusBadge status={agentReadinessStatus(data)} /> },
    { id: 'capabilities', label: 'Capabilities', badge: <span className="rounded-full bg-blue-100 px-2 py-0.5 text-xs font-bold text-blue-700">{approvedCapabilityCount(data)}</span> },
    { id: 'dispatch-access', label: 'Dispatch Access', badge: <StatusBadge status={(data.profile?.authorizationScopes ?? []).some((scope) => scope.enabled !== false) ? 'READY' : 'REQUIRED'} /> },
    { id: 'issue-tracking', label: 'Issue Tracking' },
    { id: 'pools', label: 'Agent Pools', badge: <span className="rounded-full bg-purple-100 px-2 py-0.5 text-xs font-bold text-purple-700">{data.agentPools?.length ?? 0}</span> },
    { id: 'work', label: 'Recent Work', badge: <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-bold text-slate-700">{data.tasks.length}</span> },
    { id: 'advanced', label: 'Advanced', badge: <StatusBadge status={runtimeIsConnected(data) ? 'ONLINE' : 'OFFLINE'} /> },
  ];

  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <Link href="/agents" className="text-sm font-semibold text-blue-600 hover:text-blue-700">← Back to Agents</Link>
          <h1 className="mt-2 text-2xl font-black text-slate-900">{data.profile?.agentName ?? agentId}</h1>
          <p className="mt-1 max-w-4xl text-sm leading-6 text-slate-500">Manage this Agent from one workspace. Start with Overview; configure Capabilities, Dispatch Access and Agent Pools nearby. Issue Tracking is inherited from Source Systems and is read-only here. Runtime internals, credentials and repair tools are under Advanced.</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <DataSourceBadge source={dataSource} detail={hasSourceErrors ? 'Some live API calls failed' : 'Core + Gateway runtime'} />
          <RefreshButton refreshing={refreshing} lastUpdatedAt={lastUpdatedAt} onRefresh={refresh} />
        </div>
      </div>

      <CommandMessage message={commandMessage} />

      {data.profile ? (
        <ResourceGovernancePanel resourceType="AGENT" resourceId={agentId} permissionCode="admin.agent.governance.get.agent" requestedVisibility="STANDARD" />
      ) : null}

      <AdminTabLayout tabs={tabs} activeTab={activeTab} onTabChange={(tabId) => setActiveTab(tabId as AgentDetailTab)}>
        {activeTab === 'overview' ? <AgentOverviewWorkspace data={data} setActiveTab={setActiveTab} /> : null}

        {activeTab === 'capabilities' ? (
          <AgentCapabilitiesWorkspace
            data={data}
            agentId={agentId}
            tenantId={agentTenantId}
            requestAgentCapability={requestAgentCapability}
            approveAgentCapability={approveAgentCapability}
            suspendAgentCapability={suspendAgentCapability}
            resumeAgentCapability={resumeAgentCapability}
            revokeAgentCapability={revokeAgentCapability}
            removeAgentCapability={removeAgentCapability}
            onRefresh={refresh}
          />
        ) : null}

        {activeTab === 'dispatch-access' ? (
          <AgentDispatchAccessPanel agentId={agentId} scopes={data.profile?.authorizationScopes ?? []} flows={data.dispatchFlows ?? []} onSaved={refresh} />
        ) : null}

        {activeTab === 'issue-tracking' ? (
          <div className="space-y-5">
            <IssueTrackingContextCard contexts={contexts} configure={false} title="Issue Tracking · inherited from Source Systems" />
            {!contexts.length ? (
              <div className="rounded-2xl border border-amber-200 bg-amber-50 p-5 text-sm leading-6 text-amber-950">
                <div className="font-black">No governed work context yet</div>
                <p className="mt-1">Configure Allowed Work first so OpenDispatch knows which Source Systems this Agent may execute. Redmine itself is configured once on each Source System, never on the Agent.</p>
                <div className="mt-3 flex flex-wrap gap-2"><button type="button" onClick={() => setActiveTab('dispatch-access')} className="rounded-xl bg-amber-800 px-4 py-2 text-sm font-black text-white hover:bg-amber-900">Configure Allowed Work</button><Link href="/source-systems" className="rounded-xl border border-amber-300 bg-white px-4 py-2 text-sm font-black text-amber-900 hover:bg-amber-100">Open Source Systems</Link></div>
              </div>
            ) : null}
          </div>
        ) : null}

        {activeTab === 'pools' ? <AgentPoolsWorkspace data={data} agentId={agentId} /> : null}
        {activeTab === 'work' ? <AgentRecentWorkWorkspace data={data} /> : null}

        {activeTab === 'advanced' ? (
          <AgentAdvancedWorkspace
            data={data}
            agentId={agentId}
            refreshing={refreshing}
            onRefresh={refresh}
            pingAgent={pingAgent}
            disconnectAgent={disconnectAgent}
            disconnectAllAgentSessions={disconnectAllAgentSessions}
            createRuntimeBindingFromCurrentRuntime={createRuntimeBindingFromCurrentRuntime}
            activateRuntimeBinding={activateRuntimeBinding}
          />
        ) : null}
      </AdminTabLayout>
    </div>
  );
}
