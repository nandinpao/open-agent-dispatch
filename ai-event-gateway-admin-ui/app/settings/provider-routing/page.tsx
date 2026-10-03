import { PageHeader } from '@/components/common/PageHeader';
import { ProviderRoutingConsole } from '@/components/capabilities/ProviderRoutingConsole';

export default function ProviderRoutingPage() {
  return (
    <main className="space-y-5">
      <PageHeader
        title="Agent Provider Routing"
        description="WHO SHOULD: rank already-authorized execution providers after Delegation Governance has passed. This is Agent/execution routing, not external Issue Tracking connection routing."
      />
      <ProviderRoutingConsole />
    </main>
  );
}
