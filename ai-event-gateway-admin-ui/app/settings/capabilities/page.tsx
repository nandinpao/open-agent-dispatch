import { PageHeader } from '@/components/common/PageHeader';
import { CapabilityCatalogConsole } from '@/components/capabilities/CapabilityCatalogConsole';
import { CapabilityProviderRegistryConsole } from '@/components/capabilities/CapabilityProviderRegistryConsole';

export default function CapabilityCatalogPage() {
  return (
    <main className="space-y-5">
      <PageHeader
        title="Capability Catalog"
        description="Define system-neutral enterprise capabilities, register eligible providers, and qualify what each provider can supply. Authorization, ranking and execution transport remain separate governed decisions."
      />
      <CapabilityCatalogConsole />
      <CapabilityProviderRegistryConsole />
    </main>
  );
}
