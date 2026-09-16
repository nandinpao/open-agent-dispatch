import { PageHeader } from '@/components/common/PageHeader';
import { IntegrationIdentityAdministration } from '@/components/integrations/IntegrationIdentityAdministration';
export default function IntegrationsPage(){return <main className="space-y-5"><PageHeader title="Redmine Integration" description="Configure Redmine connections, technical Service Accounts, credential references and Source System to project routing. Redmine remains the authority for project roles, workflow and issue permissions."/><IntegrationIdentityAdministration/></main>}
