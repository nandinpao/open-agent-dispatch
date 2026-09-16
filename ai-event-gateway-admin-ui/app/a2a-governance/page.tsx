import { A2AGovernanceWorkspace } from '@/components/a2a-governance/A2AGovernanceWorkspace';
import { EntitlementPageGuard } from '@/components/auth/EntitlementPageGuard';
import { PageHeader } from '@/components/common/PageHeader';

export default function A2AGovernancePage() {
  return <EntitlementPageGuard featureId="a2a-governance"><main className="space-y-5"><PageHeader title="Legacy A2A Archive" description="Directional Source → Target routing is retired. Existing policy records remain read-only for audit and reconciliation; current cross-Agent execution uses capability-first governed delegation."/><A2AGovernanceWorkspace/></main></EntitlementPageGuard>;
}
