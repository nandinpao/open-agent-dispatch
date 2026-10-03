import { AdminUiModeNotice } from '@/components/common/AdminUiModeNotice';
import { ProductionEnforceObservabilityPanel } from '@/components/enforce-observability/ProductionEnforceObservabilityPanel';

export default function EnforceObservabilityPage() {
  return (
    <main className="space-y-6">
      <AdminUiModeNotice requiredMode="developer" title="Developer engineering evidence" description="Dispatch Monitoring is an advanced production support page. Daily operators should start from Dashboard, Agents, and Tasks unless they are reviewing enforcement evidence." />
      <ProductionEnforceObservabilityPanel />
    </main>
  );
}
