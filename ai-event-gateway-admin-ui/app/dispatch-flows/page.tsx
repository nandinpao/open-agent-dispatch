import { EntitlementPageGuard } from '@/components/auth/EntitlementPageGuard';
import { DispatchWorkspace } from '@/components/dispatch-workspace/DispatchWorkspace';

type PageProps = {
  searchParams?: Promise<Record<string, string | string[] | undefined>>;
};

export default async function DispatchFlowsPage({ searchParams }: Readonly<PageProps>) {
  const resolvedSearchParams = await searchParams;
  return (
    <EntitlementPageGuard featureId="dispatch">
      <main className="space-y-8">
      <DispatchWorkspace initialQuery={resolvedSearchParams} />
      </main>
    </EntitlementPageGuard>
  );
}
