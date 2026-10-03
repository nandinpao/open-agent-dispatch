import { PageHeader } from '@/components/common/PageHeader';
import { IntegrationIdentityAdministration } from '@/components/integrations/IntegrationIdentityAdministration';

type PageProps = { searchParams?: Promise<Record<string, string | string[] | undefined>> };
function scalar(value: string | string[] | undefined) { return Array.isArray(value) ? value[0] : value; }

export default async function IntegrationsPage({ searchParams }: Readonly<PageProps>) {
  const params = await searchParams;
  const sourceSystem = scalar(params?.sourceSystem);
  const taskType = scalar(params?.taskType);
  const mappingId = scalar(params?.mappingId);
  return <main className="space-y-5">
    <PageHeader
      title="Connect Issue Tracking"
      description="Connect Redmine or Jira, validate the technical identity, then decide which Source System and Task Type creates issues in which provider project. Each step shows its purpose, impact and safe next action before production routing is changed."
    />
    <IntegrationIdentityAdministration initialSourceSystemId={sourceSystem} initialTaskType={taskType} initialMappingId={mappingId}/>
  </main>;
}
