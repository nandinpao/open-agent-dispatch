import { AccessRequestApprovalPanel } from '@/components/phase7e/AccessRequestApprovalPanel';
import { AccessRequestTemporaryGrantPanel } from '@/components/phase7e/AccessRequestTemporaryGrantPanel';
import { ResourceAccessShell } from '@/components/resource-access/ResourceAccessShell';

export default function Page() {
  return <ResourceAccessShell title="Access Requests" description="Prepare temporary least-privilege requests and perform independent review. A request is never active until the backend revalidates and approves its canonical Scope Grant.">
    <div className="space-y-6">
      <AccessRequestTemporaryGrantPanel/>
      <AccessRequestApprovalPanel/>
    </div>
  </ResourceAccessShell>;
}
