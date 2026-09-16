import { PageHeader } from '@/components/common/PageHeader';
import { ExecutionPlanConsole } from '@/components/capabilities/ExecutionPlanConsole';

export default function ExecutionPlansPage() {
  return <main className="space-y-5"><PageHeader title="Execution Plans" description="Phase 7: validate multi-Capability WHAT + dependency plans without selecting Providers, Agents, Pools or protocols."/><ExecutionPlanConsole/></main>;
}
