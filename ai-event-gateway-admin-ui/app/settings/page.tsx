import { PageHeader } from '@/components/common/PageHeader';
import { ReturnToAgentBanner } from '@/components/common/ReturnToAgentBanner';
import { AuthenticationSecurityPanel } from '@/components/settings/AuthenticationSecurityPanel';
import { SettingsGoalHub } from '@/components/settings/SettingsGoalHub';

type PageProps = { searchParams?: Promise<Record<string, string | string[] | undefined>> };

export default async function SettingsPage({ searchParams }: Readonly<PageProps>) {
  const resolvedSearchParams = await searchParams;
  return (
    <main className="space-y-5">
      <PageHeader
        title="Settings"
        description="Start from the outcome you need, not from an internal subsystem name. OpenDispatch shows what may need attention, what a change can affect, and the safest workspace for the next action."
      />
      <ReturnToAgentBanner searchParams={resolvedSearchParams} />
      <SettingsGoalHub />
      <AuthenticationSecurityPanel />
    </main>
  );
}
