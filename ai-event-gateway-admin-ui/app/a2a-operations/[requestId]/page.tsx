import { PageHeader } from '@/components/common/PageHeader';
import { A2AOperationsWorkspace } from '@/components/a2a-operations/A2AOperationsWorkspace';
import { CapabilityDelegationWorkspace } from '@/components/a2a-operations/CapabilityDelegationWorkspace';

export default async function A2AOperationsDetailPage({ params }: { params: Promise<{ requestId: string }> }) {
  const { requestId } = await params;
  const decoded = decodeURIComponent(requestId);
  const capabilityFirst = decoded.startsWith('cap-delegation-');
  return (
    <main>
      <PageHeader
        title={capabilityFirst ? "Capability Delegation Evidence" : "Legacy A2A Evidence"}
        description={capabilityFirst
          ? "Review the provider-neutral WHO CAN, WHO MAY, WHO SHOULD, HOW, and execution evidence recorded by Core."
          : "Review archived directional A2A evidence. New delegated execution uses the capability-first operations workspace."}
      />
      {capabilityFirst
        ? <CapabilityDelegationWorkspace initialDelegationId={decoded} />
        : <A2AOperationsWorkspace initialRequestId={decoded} />}
    </main>
  );
}
