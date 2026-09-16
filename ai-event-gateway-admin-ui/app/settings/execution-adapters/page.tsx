import { PageHeader } from '@/components/common/PageHeader';
import { ExecutionAdapterConsole } from '@/components/capabilities/ExecutionAdapterConsole';

export default function ExecutionAdaptersPage() {
  return <main className="space-y-5">
    <PageHeader title="Execution Adapters" description="Phase 5 HOW: resolve an already selected Capability Provider to Managed Netty, Remote A2A, MCP or Internal Service execution without re-routing the provider." />
    <ExecutionAdapterConsole />
  </main>;
}
