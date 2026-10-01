import { PageHeader } from '@/components/common/PageHeader';
import { ConfigurationMigrationGovernanceConsole } from '@/components/runtime-configuration/ConfigurationMigrationGovernanceConsole';

export default function ConfigurationMigrationGovernancePage() {
  return <main className="space-y-5">
    <PageHeader title="Configuration Migration Governance" description="Classify executable-source observations, record accountable owner and architecture review, and authorize only source-bound Migration Ready candidates. Automated classifications remain advisory." />
    <ConfigurationMigrationGovernanceConsole />
  </main>;
}
