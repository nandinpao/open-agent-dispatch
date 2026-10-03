import { EntitlementPageGuard } from '@/components/auth/EntitlementPageGuard';
import { PageHeader } from '@/components/common/PageHeader';
import { SyncOperationsWorkspace } from '@/components/integrations/SyncOperationsWorkspace';

export default function IntegrationSyncOperationsPage(){
  return <EntitlementPageGuard featureId="sync-operations"><main className="space-y-5">
    <PageHeader title="Integration Sync Operations" description="Diagnose Issue Tracking execution failures, repair governed Integration Configuration, run live Recovery Preflight, schedule governed retries, and inspect verified provider observations."/>
    <SyncOperationsWorkspace/>
  </main></EntitlementPageGuard>;
}
