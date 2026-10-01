import { PageHeader } from '@/components/common/PageHeader';
import { RuntimeConfigurationConsole } from '@/components/runtime-configuration/RuntimeConfigurationConsole';

export default function RuntimeConfigurationPage() {
  return <main className="space-y-5">
    <PageHeader title="Runtime Configuration" description="Tune approved platform runtime behavior by business impact. Technical keys and revision details stay available under Advanced without turning the product into a raw configuration editor." />
    <RuntimeConfigurationConsole />
  </main>;
}
