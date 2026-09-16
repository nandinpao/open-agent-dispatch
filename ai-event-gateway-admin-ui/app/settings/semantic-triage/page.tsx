import { PageHeader } from '@/components/common/PageHeader';
import { SemanticTriageConsole } from '@/components/capabilities/SemanticTriageConsole';

export default function SemanticTriagePage() {
  return <main className="space-y-5"><PageHeader title="Semantic Triage" description="Phase 6: keep known work deterministic; use semantic reasoning only to propose WHAT for unknown work."/><SemanticTriageConsole/></main>;
}
