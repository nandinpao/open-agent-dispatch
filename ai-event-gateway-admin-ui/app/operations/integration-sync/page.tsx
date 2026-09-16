import { EntitlementPageGuard } from '@/components/auth/EntitlementPageGuard';
import { PageHeader } from '@/components/common/PageHeader';
import { SyncOperationsWorkspace } from '@/components/integrations/SyncOperationsWorkspace';

export default function IntegrationSyncOperationsPage(){
  return <EntitlementPageGuard featureId="sync-operations"><main className="space-y-5">
    <PageHeader title="Integration Sync Operations" description="Operate projection queues, verified Webhooks, external conflicts, Human Action Candidates, relay, reconciliation and Dead Letters."/>
    <SyncOperationsWorkspace/>
  </main></EntitlementPageGuard>;
}
