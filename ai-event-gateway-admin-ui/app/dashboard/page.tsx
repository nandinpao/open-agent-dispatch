import { EntitlementPageGuard } from '@/components/auth/EntitlementPageGuard';
import { EnterpriseDashboardWorkspace } from '@/components/dashboard/EnterpriseDashboardWorkspace';
import { PageHeader } from '@/components/common/PageHeader';
import { SourceSystemFirstOnboarding } from '@/components/onboarding/SourceSystemFirstOnboarding';

export default function DashboardPage() {
  return (
    <EntitlementPageGuard featureId="dashboard">
      <main>
        <PageHeader title="Dashboard" description="Start with your accessible work, then switch to Enterprise Analytics for governed Tenant, Department, Group, Agent, Credential, workload and security trends." />
        <SourceSystemFirstOnboarding />
        <div className="mt-6"><EnterpriseDashboardWorkspace /></div>
      </main>
    </EntitlementPageGuard>
  );
}
