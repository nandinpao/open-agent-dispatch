import { PageHeader } from '@/components/common/PageHeader';
import { ProviderRoutingConsole } from '@/components/capabilities/ProviderRoutingConsole';

export default function ProviderRoutingPage() {
  return 
    <main className="space-y-5">
      <PageHeader title="Provider Routing" description="Phase 4 WHO SHOULD: apply runtime eligibility hard gates and explainable ranking only after Phase 3 WHO MAY has passed." />
      <ProviderRoutingConsole />
    </main>
  ;
}
