import { PageHeader } from '@/components/common/PageHeader';
import { ExecutionSafetyAuthorityConsole } from '@/components/capabilities/ExecutionSafetyAuthorityConsole';

export default function ExecutionSafetyPage() {
  return <main className="space-y-5"><PageHeader title="Execution Safety Authority" description="A0-R7 controlled per-Flow cutover: canonical Assignment + ExecutionLease + fencing + durable DispatchIntent before network delivery."/><ExecutionSafetyAuthorityConsole/></main>;
}
