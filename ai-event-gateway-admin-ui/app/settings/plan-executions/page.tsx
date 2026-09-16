import { PageHeader } from '@/components/common/PageHeader';
import { PlanExecutionConsole } from '@/components/capabilities/PlanExecutionConsole';
export default function PlanExecutionsPage(){return <main className="space-y-5"><PageHeader title="Plan Execution" description="Phase 8: execute frozen Plan revisions with dependency-driven fan-out/fan-in. READY is not authorization; every attempt still requires WHO MAY → WHO SHOULD → HOW."/><PlanExecutionConsole/></main>}
