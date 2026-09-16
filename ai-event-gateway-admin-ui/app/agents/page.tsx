import { Suspense } from 'react';
import { AgentGovernanceConsole } from '@/components/agents/AgentGovernanceConsole';
import { HubQuickLinks } from '@/components/common/HubQuickLinks';
import { LoadingBox } from '@/components/common/LoadingBox';
import { PageHeader } from '@/components/common/PageHeader';

export default function AgentsPage() {
  return (
    
    <main className="space-y-6">
      <PageHeader
        title="Agents"
        description="Create and approve Agents, review runtime readiness, and diagnose dispatch eligibility."
      />
      <HubQuickLinks
        title="Agent Actions"
        description="Most administrators only need Agents, Source Systems, Dispatch Flows, and Tasks. Low-level scope, profile, governance, and readiness tools are available under advanced administration."
        links={[
          { href: '/agents/setup', label: 'Create the First Agent', description: 'Create an Agent enrollment and prepare its runtime connection.' },
          { href: '/source-systems', label: 'View Source Systems', description: 'Manage systems that send events to OpenDispatch.' },
          { href: '/dispatch-flows', label: 'Open Dispatch', description: 'Configure Source Flows, Agent Pools, and dispatch rules.' },
          { href: '/tasks', label: 'View Tasks', description: 'Review Tasks, dispatch results, and operational failures.' },
        ]}
      />
      <Suspense fallback={<LoadingBox label="Loading agents..." />}>
        <AgentGovernanceConsole />
      </Suspense>
    </main>
    
  );
}
