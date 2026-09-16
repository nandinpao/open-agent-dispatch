import { PageHeader } from '@/components/common/PageHeader';
import { DelegationGovernanceConsole } from '@/components/capabilities/DelegationGovernanceConsole';

export default function DelegationGovernancePage() {
  return 
    <main className="space-y-5">
      <PageHeader
        title="Delegation Governance"
        description="Define WHO MAY request or use a Canonical Capability. Authorization is a hard gate; this workspace never chooses a target Domain, Pool, Agent or execution protocol."
      />
      <DelegationGovernanceConsole />
    </main>
  ;
}
