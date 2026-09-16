import { PageHeader } from '@/components/common/PageHeader';
import { InformationArchitectureGuide } from '@/components/common/InformationArchitectureGuide';
import { CapabilityDelegationWorkspace } from '@/components/a2a-operations/CapabilityDelegationWorkspace';

type PageProps = {
  searchParams?: Promise<Record<string, string | string[] | undefined>>;
};

function first(value: string | string[] | undefined): string {
  return Array.isArray(value) ? (value[0] ?? '') : (value ?? '');
}

export default async function A2AOperationsPage({ searchParams }: Readonly<PageProps>) {
  const query = await searchParams;
  const parentTaskId = first(query?.parentTaskId).trim();
  const delegationId = first(query?.delegationId).trim();
  return (
    <main>
      <PageHeader title="Delegations" description="Review capability-first delegated work and inspect Core's WHO CAN, WHO MAY, WHO SHOULD, HOW, and execution evidence without turning the UI into routing authority." />
      <InformationArchitectureGuide activeLayer="operations" compact />
      <CapabilityDelegationWorkspace
        initialDelegationId={delegationId || undefined}
        initialParentTaskId={parentTaskId || undefined}
      />
    </main>
  );
}
