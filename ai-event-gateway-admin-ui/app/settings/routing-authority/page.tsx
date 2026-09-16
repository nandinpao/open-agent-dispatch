import { PageHeader } from '@/components/common/PageHeader';
import { RoutingAuthorityCutoverConsole } from '@/components/capabilities/RoutingAuthorityCutoverConsole';

export default function RoutingAuthorityPage() {
  return 
    <main className="space-y-5">
      <PageHeader title="Routing Authority Cutover" description="A0-R6 shadow-only EligibilityDecision, RoutingFeatureSnapshot, RoutingDecision and ExecutionAssignment evidence. Legacy execution remains authoritative." />
      <RoutingAuthorityCutoverConsole />
    </main>
  ;
}
