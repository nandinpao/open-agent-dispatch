import { PageHeader } from '@/components/common/PageHeader';
import { AgentOnboardingPanel } from '@/components/agents/AgentOnboardingPanel';

export default function AgentSetupPage() {
  return (
    <main className="space-y-6">
      <PageHeader
        title="Create First Agent"
        description="Create the first Agent from an empty database. Dispatch eligibility is completed later by selecting this Agent in a Dispatch Flow."
      />
      <AgentOnboardingPanel />
    </main>
  );
}
