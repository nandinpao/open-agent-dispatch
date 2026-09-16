import { PageHeader } from '@/components/common/PageHeader';
import { RuntimeStepAuthorityConsole } from '@/components/capabilities/RuntimeStepAuthorityConsole';

export default function RuntimeStepAuthorityPage() {
  return <main className="space-y-5"><PageHeader title="Runtime Step Authority" description="Automate current WHO CAN → WHO MAY → WHO SHOULD → HOW evidence for every READY runtime Step and Retry. Task provenance identifies the requester; current IAM/RBAC is re-resolved server-side before authority is attached."/><RuntimeStepAuthorityConsole/></main>;
}
