import { EntitlementPageGuard } from '@/components/auth/EntitlementPageGuard';
import { PageHeader } from '@/components/common/PageHeader';
import { SecurityEventTable } from '@/components/security/SecurityEventTable';

export default function SecurityEventsPage() {
  return (
    <EntitlementPageGuard featureId="engineering-tools"><main>
      <PageHeader title="Security Events" description="Review security-relevant Agent Events and the authoritative Core evidence behind each event." />
      <SecurityEventTable />
    </main></EntitlementPageGuard>
  );
}
