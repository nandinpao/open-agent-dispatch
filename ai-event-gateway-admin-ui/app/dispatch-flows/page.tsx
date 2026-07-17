import { AgentPoolManagementConsole } from '@/components/dispatch-contract-builder/AgentPoolManagementConsole';
import { DispatchContractBuilderConsole } from '@/components/dispatch-contract-builder/DispatchContractBuilderConsole';

type PageProps = {
  searchParams?: Promise<Record<string, string | string[] | undefined>>;
};

export default async function DispatchFlowsPage({ searchParams }: Readonly<PageProps>) {
  const resolvedSearchParams = await searchParams;
  return (
    <main className="space-y-8">
      <AgentPoolManagementConsole />
      <DispatchContractBuilderConsole initialQuery={resolvedSearchParams} />
    </main>
  );
}
