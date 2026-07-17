import { PageHeader } from '@/components/common/PageHeader';
import { ReturnToAgentBanner } from '@/components/common/ReturnToAgentBanner';
import { RuntimeResourceConsole } from '@/components/runtime-resources/RuntimeResourceConsole';
import { AdminUiModeNotice } from '@/components/common/AdminUiModeNotice';

type PageProps = {
  searchParams?: Promise<Record<string, string | string[] | undefined>>;
};

export default async function RuntimeResourcesPage({ searchParams }: Readonly<PageProps>) {
  const resolvedSearchParams = await searchParams;
  return (
    <main className="space-y-6">
      <PageHeader
        title="Runtime Resources"
        description="Maintain gateway, connector, and execution resource records used by agent connection and dispatch setup."
      />
      <AdminUiModeNotice requiredMode="advanced" title="Advanced administration area" description="This page supports source data, migration, release, or readiness management. It is hidden from Basic Mode navigation so normal Agent setup stays simple." />
      <ReturnToAgentBanner searchParams={resolvedSearchParams} />
      <RuntimeResourceConsole />
    </main>
  );
}
