import { EntitlementPageGuard } from '@/components/auth/EntitlementPageGuard';
import { AccessWorkspace } from '@/components/access-management/access/AccessWorkspace';
export default function Page() { return <EntitlementPageGuard featureId="access-governance"><AccessWorkspace /></EntitlementPageGuard>; }
